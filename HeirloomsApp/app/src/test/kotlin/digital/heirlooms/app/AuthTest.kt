package digital.heirlooms.app

import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.crypto.VaultCrypto
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AuthTest {

    private class InMemStore : PreferenceStore {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String, default: String) = map.getOrDefault(key, default)
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private fun makeStore() = EndpointStore(InMemStore())

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiForServer() = HeirloomsApi(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = "")

    // ── Test 1: deriveAuthAndMasterKeys ──────────────────────────────────────

    @Test
    fun deriveAuthAndMasterKeys_returns32ByteKeys() {
        val salt = ByteArray(16) { it.toByte() }
        val (authKey, masterKeySeed) = VaultCrypto.deriveAuthAndMasterKeys("test-passphrase", salt)
        assertEquals(32, authKey.size)
        assertEquals(32, masterKeySeed.size)
    }

    @Test
    fun deriveAuthAndMasterKeys_isDeterministic() {
        val salt = ByteArray(16) { 42 }
        val (ak1, mk1) = VaultCrypto.deriveAuthAndMasterKeys("hello", salt)
        val (ak2, mk2) = VaultCrypto.deriveAuthAndMasterKeys("hello", salt)
        assertTrue(ak1.contentEquals(ak2))
        assertTrue(mk1.contentEquals(mk2))
    }

    @Test
    fun deriveAuthAndMasterKeys_authKeyAndSeedDiffer() {
        val salt = ByteArray(16) { 1 }
        val (authKey, masterKeySeed) = VaultCrypto.deriveAuthAndMasterKeys("passphrase", salt)
        assertTrue(!authKey.contentEquals(masterKeySeed))
    }

    // ── Test 2: computeAuthVerifier ──────────────────────────────────────────

    @Test
    fun computeAuthVerifier_returns32ByteSHA256() {
        val authKey = ByteArray(32) { 7 }
        val verifier = VaultCrypto.computeAuthVerifier(authKey)
        assertEquals(32, verifier.size)
    }

    @Test
    fun computeAuthVerifier_isDeterministic() {
        val authKey = ByteArray(32) { 99 }
        val v1 = VaultCrypto.computeAuthVerifier(authKey)
        val v2 = VaultCrypto.computeAuthVerifier(authKey)
        assertTrue(v1.contentEquals(v2))
    }

    // ── Test 3: Login flow (mocked server) ───────────────────────────────────

    @Test
    fun loginFlow_callsChallengeAndLogin() {
        val saltB64url = VaultCrypto.toBase64Url(ByteArray(16) { it.toByte() })
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"auth_salt":"$saltB64url"}"""))
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"session_token":"tok-abc","user_id":"u1","expires_at":"2030-01-01T00:00:00Z"}"""))

        val api = apiForServer()
        runBlocking {
            val challengeResp = api.authChallenge("alice")
            assertEquals(saltB64url, challengeResp.authSaltB64url)

            val salt = VaultCrypto.fromBase64Url(challengeResp.authSaltB64url)
            val (authKey, _) = VaultCrypto.deriveAuthAndMasterKeys("passphrase", salt)
            val loginResp = api.authLogin("alice", VaultCrypto.toBase64Url(authKey))
            assertEquals("tok-abc", loginResp.sessionToken)
        }
    }

    @Test
    fun authLogin_401_throwsWithUnauthorizedMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        val api = apiForServer()
        try {
            runBlocking { api.authLogin("alice", "wrong-key-b64url") }
            fail("Expected exception on 401")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("UNAUTHORIZED") == true)
        }
    }

    // ── Test 4: Session detection — expired session ──────────────────────────

    @Test
    fun clearSessionToken_removesTokenFromStore() {
        val store = makeStore()
        store.setSessionToken("valid-token")
        assertEquals("valid-token", store.getSessionToken())
        store.clearSessionToken()
        assertEquals("", store.getSessionToken())
    }

    @Test
    fun afterClearSessionToken_neitherSessionNorMigrationDetected() {
        val store = makeStore()
        store.setSessionToken("tok")
        store.clearSessionToken()
        assertTrue(store.getSessionToken().isEmpty() && store.getApiKey().isEmpty())
    }

    // ── Test 5: Bret migration detection ─────────────────────────────────────

    @Test
    fun apiKeyPresent_noSessionToken_detectsMigrationPath() {
        val store = makeStore()
        store.setApiKey("old-api-key")
        val hasSession = store.getSessionToken().isNotEmpty()
        val hasLegacy = store.getApiKey().isNotEmpty()
        assertTrue(!hasSession && hasLegacy)
    }

    @Test
    fun neitherApiKeyNorSessionToken_detectsFirstRunPath() {
        val store = makeStore()
        assertTrue(store.getSessionToken().isEmpty() && store.getApiKey().isEmpty())
    }

    // ── Test 6: Pairing QR parse — valid JSON ────────────────────────────────

    @Test
    fun pairingQrParser_validJson_extractsSessionIdAndPubkey() {
        val json = """{"session_id":"550e8400-e29b-41d4-a716-446655440000","pubkey":"AAAA"}"""
        val result = PairingQrParser.parse(json)
        assertTrue(result is PairingQrParser.ParseResult.Success)
        val payload = (result as PairingQrParser.ParseResult.Success).payload
        assertEquals("550e8400-e29b-41d4-a716-446655440000", payload.sessionId)
        assertEquals("AAAA", payload.pubkey)
    }

    // ── Test 7: Pairing QR parse — malformed JSON ────────────────────────────

    @Test
    fun pairingQrParser_invalidJson_returnsErrorWithoutCrash() {
        val result = PairingQrParser.parse("not-json{{{")
        assertTrue(result is PairingQrParser.ParseResult.Error)
    }

    @Test
    fun pairingQrParser_missingSessionId_returnsError() {
        val result = PairingQrParser.parse("""{"pubkey":"AAAA"}""")
        assertTrue(result is PairingQrParser.ParseResult.Error)
        assertTrue((result as PairingQrParser.ParseResult.Error).message.contains("session_id"))
    }

    // ── Test 8: Logout — session token cleared ───────────────────────────────

    @Test
    fun logout_clearsSessionTokenFromStore() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val store = makeStore()
        store.setSessionToken("session-to-clear")
        assertEquals("session-to-clear", store.getSessionToken())

        val api = apiForServer()
        runBlocking { api.authLogout() }

        store.clearSessionToken()
        assertEquals("", store.getSessionToken())
    }

    @Test
    fun authLogout_doesNotThrowOn500() {
        server.enqueue(MockResponse().setResponseCode(500))
        val api = apiForServer()
        runBlocking { api.authLogout() }
    }
}
