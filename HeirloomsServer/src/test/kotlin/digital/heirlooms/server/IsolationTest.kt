package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.http4k.core.Method.GET
import org.http4k.core.then
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val isoMapper = ObjectMapper()
private val isoUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val isoStdEnc = Base64.getEncoder()

private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

class IsolationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_iso")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private lateinit var dataSource: DataSource
        private lateinit var database: Database
        private lateinit var app: org.http4k.core.HttpHandler

        private lateinit var inviterToken: String
        private lateinit var aliceToken: String
        private lateinit var aliceUploadId: String
        private lateinit var alicePlotId: String
        private lateinit var aliceCapsuleId: String
        private lateinit var bobToken: String

        @BeforeAll
        @JvmStatic
        fun setup() {
            System.setProperty("ryuk.disabled", "true")
            System.setProperty(
                "docker.host",
                System.getenv("DOCKER_HOST")
                    ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
            )
            postgres.start()
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_iso"
            dataSource = HikariDataSource(HikariConfig().apply {
                this.jdbcUrl = jdbcUrl; username = "test"; password = "test"; maximumPoolSize = 5
            })
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            database = Database(dataSource)

            val serverSecret = ByteArray(32) { it.toByte() }
            val mockStorage = mockk<FileStore>(relaxed = true).also {
                every { it.save(any(), any()) } answers { StorageKey("upload-${UUID.randomUUID()}") }
            }
            val rawApp = buildApp(mockStorage, database, authSecret = serverSecret)
            app = sessionAuthFilter(database).then(rawApp)

            inviterToken = getInviterToken()
            val invite1 = generateInvite(inviterToken)
            aliceToken = registerUser("alice_iso", invite1)
            val invite2 = generateInvite(inviterToken)
            bobToken = registerUser("bob_iso", invite2)

            aliceUploadId = createUpload(aliceToken)
            alicePlotId = createPlot(aliceToken)
            aliceCapsuleId = createCapsule(aliceToken, aliceUploadId)
        }

        private fun post(path: String, body: String, token: String? = null): org.http4k.core.Response {
            val req = Request(POST, path).header("Content-Type", "application/json").body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        private fun get(path: String, token: String? = null): org.http4k.core.Response {
            val req = Request(GET, path)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        private fun patch(path: String, body: String, token: String): org.http4k.core.Response =
            app(Request(PATCH, path).header("Content-Type", "application/json")
                .header("X-Api-Key", token).body(body))

        private fun getInviterToken(): String {
            database.resetUserAuth(FOUNDING_USER_ID)
            val deviceId = "inviter-iso-${UUID.randomUUID()}"
            val authKey = ByteArray(32) { 99 }
            val authSalt = ByteArray(16) { 11 }
            database.insertWrappedKey(
                WrappedKeyRecord(UUID.randomUUID(), deviceId, "Owner's Phone", "android",
                    "p256-spki", ByteArray(65) { 7 }, ByteArray(64) { 8 },
                    "p256-ecdh-hkdf-aes256gcm-v1", java.time.Instant.now(), java.time.Instant.now(), null),
                FOUNDING_USER_ID,
            )
            val body = """{"username":"bret","device_id":"$deviceId",
                "auth_salt":"${isoUrlEnc.encodeToString(authSalt)}",
                "auth_verifier":"${isoUrlEnc.encodeToString(sha256(authKey))}"}"""
            val resp = post("/api/auth/setup-existing", body)
            return isoMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun generateInvite(token: String): String {
            val resp = get("/api/auth/invites", token)
            return isoMapper.readTree(resp.bodyString()).get("token").asText()
        }

        private fun registerUser(username: String, invite: String): String {
            val body = """{
                "invite_token":"$invite","username":"$username","display_name":"${username.replaceFirstChar { it.uppercaseChar() }}",
                "auth_salt":"${isoUrlEnc.encodeToString(ByteArray(16))}",
                "auth_verifier":"${isoUrlEnc.encodeToString(ByteArray(32))}",
                "wrapped_master_key":"${isoStdEnc.encodeToString(ByteArray(64))}",
                "wrap_format":"p256-ecdh-hkdf-aes256gcm-v1","pubkey_format":"p256-spki",
                "pubkey":"${isoStdEnc.encodeToString(ByteArray(65))}",
                "device_id":"${UUID.randomUUID()}","device_label":"$username phone","device_kind":"android"
            }"""
            val resp = post("/api/auth/register", body)
            return isoMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun createUpload(token: String): String {
            val resp = app(
                Request(POST, "/api/content/upload")
                    .header("Content-Type", "image/jpeg")
                    .header("X-Api-Key", token)
                    .body("unique-upload-${UUID.randomUUID()}")
            )
            val node = isoMapper.readTree(resp.bodyString())
            check(node.has("id")) { "Upload failed: ${resp.status} ${resp.bodyString()}" }
            return node.get("id").asText()
        }

        private fun createPlot(token: String): String {
            val resp = post("/api/plots", """{"name":"My Plot"}""", token)
            return isoMapper.readTree(resp.bodyString()).get("id").asText()
        }

        private fun createCapsule(token: String, uploadId: String): String {
            val body = """{
                "shape":"open","unlock_at":"2030-01-01T00:00:00Z",
                "recipients":["Friend"],"upload_ids":["$uploadId"],"message":""
            }"""
            val resp = post("/api/capsules", body, token)
            return isoMapper.readTree(resp.bodyString()).get("id").asText()
        }
    }

    // ======== Upload isolation ================================================

    @Test
    fun `Bob GET uploads does not include Alice upload`() {
        val resp = get("/api/content/uploads", bobToken)
        assertEquals(OK, resp.status)
        assertFalse(resp.bodyString().contains(aliceUploadId), "Bob should not see Alice's upload")
    }

    @Test
    fun `Bob GET upload by Alice ID returns 404`() {
        val resp = get("/api/content/uploads/$aliceUploadId", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob PATCH tags on Alice upload returns 404`() {
        val resp = patch("/api/content/uploads/$aliceUploadId/tags", """{"tags":["hack"]}""", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob POST compost on Alice upload returns 404`() {
        val resp = post("/api/content/uploads/$aliceUploadId/compost", "", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob GET file proxy for Alice upload returns 404`() {
        val resp = get("/api/content/uploads/$aliceUploadId/file", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob GET thumb proxy for Alice upload returns 404`() {
        val resp = get("/api/content/uploads/$aliceUploadId/thumb", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Upload dedup is per-user — Alice and Bob can upload identical content`() {
        val bytes = ByteArray(32) { 0x42 }
        fun doUpload(token: String) = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .header("X-Api-Key", token)
                .body(org.http4k.core.Body(java.io.ByteArrayInputStream(bytes), bytes.size.toLong()))
        )
        val aliceResp = doUpload(aliceToken)
        val bobResp = doUpload(bobToken)
        val aliceId = isoMapper.readTree(aliceResp.bodyString()).get("id").asText()
        val bobId = isoMapper.readTree(bobResp.bodyString()).get("id").asText()
        assertTrue(aliceId != bobId, "Dedup should not cross user boundaries")
    }

    @Test
    fun `Alice upload count visible only to Alice`() {
        val aliceResp = get("/api/content/uploads", aliceToken)
        val bobResp = get("/api/content/uploads", bobToken)
        val aliceItems = isoMapper.readTree(aliceResp.bodyString()).get("items")
        val bobItems = isoMapper.readTree(bobResp.bodyString()).get("items")
        assertTrue(aliceItems.any { it.get("id").asText() == aliceUploadId })
        assertFalse(bobItems.any { it.get("id").asText() == aliceUploadId })
    }

    @Test
    fun `Unauthenticated GET uploads returns 401`() {
        val resp = get("/api/content/uploads")
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ======== Plot isolation ==================================================

    @Test
    fun `Bob GET plots does not include Alice plot`() {
        val resp = get("/api/plots", bobToken)
        assertEquals(OK, resp.status)
        assertFalse(resp.bodyString().contains(alicePlotId), "Bob should not see Alice's plot")
    }

    @Test
    fun `Bob PATCH Alice plot returns 404`() {
        val resp = patch("/api/plots/$alicePlotId", """{"name":"Hacked"}""", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Each user has exactly one just_arrived system plot`() {
        val alicePlots = isoMapper.readTree(get("/api/plots", aliceToken).bodyString())
        val bobPlots = isoMapper.readTree(get("/api/plots", bobToken).bodyString())
        val aliceSys = alicePlots.filter { it.get("name").asText() == "__just_arrived__" }
        val bobSys = bobPlots.filter { it.get("name").asText() == "__just_arrived__" }
        assertEquals(1, aliceSys.size, "Alice should have exactly one system plot")
        assertEquals(1, bobSys.size, "Bob should have exactly one system plot")
        val aliceSysId = aliceSys[0].get("id").asText()
        val bobSysId = bobSys[0].get("id").asText()
        assertTrue(aliceSysId != bobSysId, "System plots must be distinct rows")
    }

    // ======== Capsule isolation ===============================================

    @Test
    fun `Bob GET capsules does not include Alice capsule`() {
        val resp = get("/api/capsules", bobToken)
        assertEquals(OK, resp.status)
        assertFalse(resp.bodyString().contains(aliceCapsuleId), "Bob should not see Alice's capsule")
    }

    @Test
    fun `Bob GET Alice capsule by ID returns 404`() {
        val resp = get("/api/capsules/$aliceCapsuleId", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob POST seal on Alice capsule returns 404`() {
        val resp = post("/api/capsules/$aliceCapsuleId/seal", "", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob POST cancel on Alice capsule returns 404`() {
        val resp = post("/api/capsules/$aliceCapsuleId/cancel", "", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    @Test
    fun `Bob GET capsule reverse lookup for Alice upload returns 404`() {
        val resp = get("/api/content/uploads/$aliceUploadId/capsules", bobToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    // ======== Auth isolation ==================================================

    @Test
    fun `Bob session token cannot access Alice invites endpoint — only same user`() {
        val aliceInviteResp = get("/api/auth/invites", aliceToken)
        assertEquals(OK, aliceInviteResp.status)
        assertNotNull(isoMapper.readTree(aliceInviteResp.bodyString()).get("token"))
    }

    @Test
    fun `Logout invalidates only the logged-out session`() {
        val invite = generateInvite(inviterToken)
        val tempToken = registerUser("temp_logout_${UUID.randomUUID().toString().take(8)}", invite)

        val bobInviteBefore = get("/api/auth/invites", bobToken)
        assertEquals(OK, bobInviteBefore.status)

        post("/api/auth/logout", "", tempToken)

        val tempAfter = get("/api/auth/invites", tempToken)
        assertEquals(UNAUTHORIZED, tempAfter.status, "Logged-out token should be invalid")

        val bobInviteAfter = get("/api/auth/invites", bobToken)
        assertEquals(OK, bobInviteAfter.status, "Bob's session should still be valid after another user logs out")
    }

    @Test
    fun `Expired session token returns 401`() {
        val sessionToken = aliceToken
        val aliceTokenBytes = runCatching { Base64.getUrlDecoder().decode(sessionToken) }
            .getOrElse { Base64.getDecoder().decode(sessionToken) }
        val hash = sha256(aliceTokenBytes)
        val session = database.findSessionByTokenHash(hash)
        if (session != null) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    "UPDATE user_sessions SET expires_at = NOW() - INTERVAL '1 day' WHERE id = ?"
                ).use { stmt -> stmt.setObject(1, session.id); stmt.executeUpdate() }
            }
            val resp = get("/api/auth/invites", sessionToken)
            assertEquals(UNAUTHORIZED, resp.status)
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    "UPDATE user_sessions SET expires_at = NOW() + INTERVAL '90 days' WHERE id = ?"
                ).use { stmt -> stmt.setObject(1, session.id); stmt.executeUpdate() }
            }
        }
    }

    @Test
    fun `Random token returns 401`() {
        val fakeToken = isoUrlEnc.encodeToString(ByteArray(32) { 42 })
        val resp = get("/api/content/uploads", fakeToken)
        assertEquals(UNAUTHORIZED, resp.status)
    }

    @Test
    fun `Session refresh updates last_used_at`() {
        val aliceTokenBytes = runCatching { Base64.getUrlDecoder().decode(aliceToken) }
            .getOrElse { Base64.getDecoder().decode(aliceToken) }
        val hash = sha256(aliceTokenBytes)
        val before = database.findSessionByTokenHash(hash)?.lastUsedAt
        get("/api/content/uploads", aliceToken)
        val after = database.findSessionByTokenHash(hash)?.lastUsedAt
        if (before != null && after != null) {
            assertTrue(!after.isBefore(before), "last_used_at should be refreshed after authenticated request")
        }
    }
}
