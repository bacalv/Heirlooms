package digital.heirlooms.server.filters

import digital.heirlooms.server.FOUNDING_USER_ID
import digital.heirlooms.server.domain.auth.UserSessionRecord
import digital.heirlooms.server.repository.auth.AuthRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for [sessionAuthFilter] and the [authUserId] extension covering every
 * branch that the integration tests in [AuthHandlerTest] do not reach.
 *
 * SEC-002 Phase 2.
 */
class SessionAuthFilterUnitTest {

    private val authRepo = mockk<AuthRepository>(relaxed = true)

    private fun sha256(b: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(b)

    private fun makeSession(
        uid: UUID = UUID.randomUUID(),
        kind: String = "android",
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) = UserSessionRecord(
        id = UUID.randomUUID(), userId = uid, tokenHash = ByteArray(32),
        deviceKind = kind, createdAt = Instant.now(),
        lastUsedAt = Instant.now(), expiresAt = expiresAt,
    )

    /** Builds a filter + echo handler that echoes status 200. */
    private fun filter(staticApiKey: String = "") =
        sessionAuthFilter(authRepo, staticApiKey)

    /** Echo handler: returns 200 and writes the X-Auth-User-Id header back into the body for inspection. */
    private val echoHandler: HttpHandler = { req ->
        Response(OK).body(req.header("X-Auth-User-Id") ?: "NONE")
    }

    // ─── Unauthenticated paths ────────────────────────────────────────────────

    @Test
    fun `GET health bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/health"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `GET docs path bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/docs/openapi.json"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `pairing status path bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/pairing/status?session_id=abc"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `POST auth login bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/login"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `POST auth challenge bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/challenge"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `POST auth register bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/register"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `POST auth setup-existing bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/setup-existing"))
        assertEquals(OK, resp.status)
    }

    @Test
    fun `POST auth pairing qr bypasses auth`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/pairing/qr"))
        assertEquals(OK, resp.status)
    }

    // ─── Static API key bypass ────────────────────────────────────────────────

    @Test
    fun `static API key bypass injects FOUNDING_USER_ID and static device kind`() {
        val key = "super-secret-static-key"
        val captured = mutableMapOf<String, String?>()
        val capturingHandler: HttpHandler = { req ->
            captured["userId"] = req.header("X-Auth-User-Id")
            captured["kind"] = req.header("X-Auth-Device-Kind")
            Response(OK)
        }

        val resp = filter(staticApiKey = key).then(capturingHandler)(
            Request(GET, "/api/some/protected/endpoint")
                .header("X-Api-Key", key)
        )

        assertEquals(OK, resp.status)
        assertEquals(FOUNDING_USER_ID.toString(), captured["userId"])
        assertEquals("static", captured["kind"])
    }

    @Test
    fun `static API key bypass does not activate when key does not match`() {
        val correctKey = "correct-key"
        // When the header has the wrong value, the filter should NOT bypass and should
        // proceed to session resolution — which returns 401 because there's no real session.
        every { authRepo.findSessionByTokenHash(any()) } returns null

        val resp = filter(staticApiKey = correctKey).then(echoHandler)(
            Request(GET, "/api/some/protected/endpoint")
                .header("X-Api-Key", "wrong-key")
        )
        assertEquals(UNAUTHORIZED, resp.status)
    }

    @Test
    fun `static API key bypass does not activate when staticApiKey is empty`() {
        every { authRepo.findSessionByTokenHash(any()) } returns null

        val resp = filter(staticApiKey = "").then(echoHandler)(
            Request(GET, "/api/some/endpoint")
                .header("X-Api-Key", "some-value")
        )
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ─── Session token path ───────────────────────────────────────────────────

    @Test
    fun `missing X-Api-Key returns 401 for protected path`() {
        val resp = filter().then(echoHandler)(Request(GET, "/api/auth/me"))
        assertEquals(UNAUTHORIZED, resp.status)
    }

    @Test
    fun `valid session token injects user id and device kind`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = sha256(raw)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val uid = UUID.randomUUID()
        val session = makeSession(uid = uid, kind = "ios")
        every { authRepo.findSessionByTokenHash(hash) } returns session
        every { authRepo.refreshSession(session.id) } just runs

        val captured = mutableMapOf<String, String?>()
        val capturingHandler: HttpHandler = { req ->
            captured["userId"] = req.header("X-Auth-User-Id")
            captured["kind"] = req.header("X-Auth-Device-Kind")
            Response(OK)
        }

        val resp = filter().then(capturingHandler)(
            Request(GET, "/api/auth/me").header("X-Api-Key", token)
        )
        assertEquals(OK, resp.status)
        assertEquals(uid.toString(), captured["userId"])
        assertEquals("ios", captured["kind"])
        verify { authRepo.refreshSession(session.id) }
    }

    @Test
    fun `expired session token returns 401`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = sha256(raw)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val session = makeSession(expiresAt = Instant.now().minusSeconds(1))
        every { authRepo.findSessionByTokenHash(hash) } returns session

        val resp = filter().then(echoHandler)(
            Request(GET, "/api/auth/me").header("X-Api-Key", token)
        )
        assertEquals(UNAUTHORIZED, resp.status)
    }

    @Test
    fun `null session (token not found) returns 401`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        every { authRepo.findSessionByTokenHash(any()) } returns null

        val resp = filter().then(echoHandler)(
            Request(GET, "/api/auth/me").header("X-Api-Key", token)
        )
        assertEquals(UNAUTHORIZED, resp.status)
    }

    @Test
    fun `standard base64 token is accepted when url-safe decode fails`() {
        // Use a byte sequence that produces '+' in standard base64 (0xFB = '+' prefix in b64).
        // 0xFB, 0xEF, 0xBE ... → standard base64 contains '+' and '/' which url-decoder rejects.
        // Pad to 32 bytes so it's a plausible session token.
        val raw = ByteArray(32) { idx ->
            when (idx) {
                0 -> 0xFB.toByte()  // encodes to '+' region in std b64
                1 -> 0xEF.toByte()
                2 -> 0xBE.toByte()
                else -> idx.toByte()
            }
        }
        val hash = sha256(raw)
        // Standard base64 will contain '+' — url-safe decoder must fail, fallback must succeed.
        val stdToken = Base64.getEncoder().encodeToString(raw)
        // Verify our assumption: std token has '+' or '/'
        check('+' in stdToken || '/' in stdToken) { "Test setup: expected std-b64 chars in token" }

        val session = makeSession()
        every { authRepo.findSessionByTokenHash(hash) } returns session
        every { authRepo.refreshSession(session.id) } just runs

        val resp = filter().then(echoHandler)(
            Request(GET, "/api/auth/me").header("X-Api-Key", stdToken)
        )
        assertEquals(OK, resp.status)
    }

    // ─── authUserId() extension ───────────────────────────────────────────────

    @Test
    fun `authUserId returns parsed UUID when header is present and valid`() {
        val uid = UUID.randomUUID()
        val req = Request(GET, "/api/test").header("X-Auth-User-Id", uid.toString())
        assertEquals(uid, req.authUserId())
    }

    @Test
    fun `authUserId falls back to FOUNDING_USER_ID when header is absent`() {
        val req = Request(GET, "/api/test")
        // Should not throw; must return FOUNDING_USER_ID and log a warning
        assertEquals(FOUNDING_USER_ID, req.authUserId())
    }

    @Test
    fun `authUserId falls back to FOUNDING_USER_ID when header contains invalid UUID`() {
        val req = Request(GET, "/api/test").header("X-Auth-User-Id", "not-a-uuid")
        assertEquals(FOUNDING_USER_ID, req.authUserId())
    }
}
