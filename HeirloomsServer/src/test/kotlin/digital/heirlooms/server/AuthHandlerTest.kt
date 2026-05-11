package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.GONE
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val mapper = ObjectMapper()
private val urlEnc = Base64.getUrlEncoder().withoutPadding()
private val stdEnc = Base64.getEncoder()

private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

class AuthHandlerTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_test")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private lateinit var dataSource: DataSource
        private lateinit var database: Database
        private lateinit var app: org.http4k.core.HttpHandler

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
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_test"
            dataSource = HikariDataSource(HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = "test"
                password = "test"
                maximumPoolSize = 5
            })
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            database = Database(dataSource)
            val serverSecret = ByteArray(32) { it.toByte() }
            app = buildApp(mockk(relaxed = true), database, authSecret = serverSecret)
        }

        private fun post(path: String, body: String, token: String? = null): org.http4k.core.Response {
            val req = Request(POST, path)
                .header("Content-Type", "application/json")
                .body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        private fun get(path: String, token: String? = null): org.http4k.core.Response {
            val req = Request(GET, path)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        /** Creates a user + session via the register endpoint and returns the session token. */
        private fun registerUser(username: String, inviteToken: String, deviceId: String = UUID.randomUUID().toString()): String {
            val authKey = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val authVerifier = sha256(authKey)
            val wrappedMasterKey = ByteArray(64) { 5 }
            val pubkey = ByteArray(65) { 6 }
            val body = """
                {
                  "invite_token": "$inviteToken",
                  "username": "$username",
                  "display_name": "${username.replaceFirstChar { it.uppercaseChar() }}",
                  "auth_salt": "${urlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${urlEnc.encodeToString(authVerifier)}",
                  "wrapped_master_key": "${stdEnc.encodeToString(wrappedMasterKey)}",
                  "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
                  "pubkey_format": "p256-spki",
                  "pubkey": "${stdEnc.encodeToString(pubkey)}",
                  "device_id": "$deviceId",
                  "device_label": "${username.replaceFirstChar { it.uppercaseChar() }}'s Phone",
                  "device_kind": "android"
                }
            """.trimIndent()
            val resp = post("/api/auth/register", body)
            assertEquals(CREATED, resp.status, "Expected 201 for register: ${resp.bodyString()}")
            return mapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        /** Generates an invite via the founding user's session. */
        private fun generateInvite(sessionToken: String): String {
            val resp = get("/api/auth/invites", sessionToken)
            assertEquals(OK, resp.status, "Expected 200 for invite: ${resp.bodyString()}")
            return mapper.readTree(resp.bodyString()).get("token").asText()
        }

        /**
         * Creates a session for the founding user via setup-existing (simulates the M8 migration flow).
         * Resets the founding user's auth state first for test isolation. Returns the raw session token.
         */
        private fun setupInviterSession(deviceId: String = "inviter-device-${UUID.randomUUID()}"): String {
            database.resetUserAuth(FOUNDING_USER_ID)
            // Register the founding user's wrapped_keys row
            val authKey = ByteArray(32) { 99 }
            val authSalt = ByteArray(16) { 11 }
            val authVerifier = sha256(authKey)
            val pubkey = ByteArray(65) { 7 }
            val wrappedMasterKey = ByteArray(64) { 8 }

            database.insertWrappedKey(
                WrappedKeyRecord(
                    id = UUID.randomUUID(),
                    deviceId = deviceId,
                    deviceLabel = "Owner's Phone",
                    deviceKind = "android",
                    pubkeyFormat = "p256-spki",
                    pubkey = pubkey,
                    wrappedMasterKey = wrappedMasterKey,
                    wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
                    createdAt = Instant.now(),
                    lastUsedAt = Instant.now(),
                    retiredAt = null,
                ),
                userId = FOUNDING_USER_ID,
            )

            val body = """
                {
                  "username": "bret",
                  "device_id": "$deviceId",
                  "auth_salt": "${urlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${urlEnc.encodeToString(authVerifier)}"
                }
            """.trimIndent()
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing failed: ${resp.bodyString()}")
            return mapper.readTree(resp.bodyString()).get("session_token").asText()
        }
    }

    // ---- 9. Challenge — known username returns correct salt ----------------

    @Test
    fun `challenge known username returns salt`() {
        setupInviterSession("inviter-challenge-${UUID.randomUUID()}")
        // The founding user's auth_salt was set; a challenge should return it (as 16 bytes base64url)
        val resp = post("/api/auth/challenge", """{"username":"bret"}""")
        assertEquals(OK, resp.status)
        val salt = mapper.readTree(resp.bodyString()).get("auth_salt").asText()
        assertNotNull(salt)
        assertTrue(salt.isNotBlank())
    }

    // ---- 10. Challenge — unknown username returns fake deterministic salt --

    @Test
    fun `challenge unknown username returns fake deterministic salt`() {
        val resp1 = post("/api/auth/challenge", """{"username":"ghost_user_xyz"}""")
        val resp2 = post("/api/auth/challenge", """{"username":"ghost_user_xyz"}""")
        assertEquals(OK, resp1.status)
        assertEquals(OK, resp2.status)
        val salt1 = mapper.readTree(resp1.bodyString()).get("auth_salt").asText()
        val salt2 = mapper.readTree(resp2.bodyString()).get("auth_salt").asText()
        assertEquals(salt1, salt2, "Fake salt must be deterministic for the same username")
    }

    // ---- 11. Login — correct auth_key returns session token ----------------

    @Test
    fun `login correct auth_key returns session token`() {
        val uniqueId = "inviter-login-${UUID.randomUUID()}"
        setupInviterSession(uniqueId)

        val authKey = ByteArray(32) { 99 }
        val resp = post(
            "/api/auth/login",
            """{"username":"bret","auth_key":"${urlEnc.encodeToString(authKey)}"}"""
        )
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertNotNull(node.get("session_token"))
        assertNotNull(node.get("expires_at"))
        // Reset
    }

    // ---- 12. Login — wrong auth_key returns 401 ----------------------------

    @Test
    fun `login wrong auth_key returns 401`() {
        val uniqueId = "inviter-login-wrong-${UUID.randomUUID()}"
        setupInviterSession(uniqueId)
        val wrongKey = ByteArray(32) { 42 }
        val resp = post(
            "/api/auth/login",
            """{"username":"bret","auth_key":"${urlEnc.encodeToString(wrongKey)}"}"""
        )
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ---- 13. Login — unknown username returns 401 --------------------------

    @Test
    fun `login unknown username returns 401`() {
        val resp = post(
            "/api/auth/login",
            """{"username":"nobody_999","auth_key":"${urlEnc.encodeToString(ByteArray(32))}"}"""
        )
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ---- 14. setup-existing — valid device returns session token -----------

    @Test
    fun `setup-existing valid device and null auth_verifier returns session token`() {
        val deviceId = "inviter-setup-${UUID.randomUUID()}"
        val token = setupInviterSession(deviceId)
        assertNotNull(token)
        assertTrue(token.isNotBlank())
    }

    // ---- 15. setup-existing — rejected if auth_verifier already set -------

    @Test
    fun `setup-existing rejected if auth_verifier already set`() {
        val deviceId = "inviter-setup-twice-${UUID.randomUUID()}"
        setupInviterSession(deviceId)
        // Second call should 409
        val authSalt = ByteArray(16) { 11 }
        val authVerifier = sha256(ByteArray(32) { 99 })
        val body = """
            {
              "username": "bret",
              "device_id": "$deviceId",
              "auth_salt": "${urlEnc.encodeToString(authSalt)}",
              "auth_verifier": "${urlEnc.encodeToString(authVerifier)}"
            }
        """.trimIndent()
        val resp = post("/api/auth/setup-existing", body)
        assertEquals(CONFLICT, resp.status)
    }

    // ---- 16. setup-existing — rejected if device_id not in wrapped_keys ---

    @Test
    fun `setup-existing rejected if device_id not in wrapped_keys`() {
        database.resetUserAuth(FOUNDING_USER_ID)
        val body = """
            {
              "username": "bret",
              "device_id": "non-existent-device-${UUID.randomUUID()}",
              "auth_salt": "${urlEnc.encodeToString(ByteArray(16))}",
              "auth_verifier": "${urlEnc.encodeToString(ByteArray(32))}"
            }
        """.trimIndent()
        val resp = post("/api/auth/setup-existing", body)
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ---- 17. invites — authenticated returns token with 48-hour expiry ----

    @Test
    fun `invites authenticated returns invite token`() {
        val inviterToken = setupInviterSession("inviter-invite-${UUID.randomUUID()}")
        val resp = get("/api/auth/invites", inviterToken)
        assertEquals(OK, resp.status)
        val node = mapper.readTree(resp.bodyString())
        assertNotNull(node.get("token"))
        assertNotNull(node.get("expires_at"))
        assertTrue(node.get("token").asText().isNotBlank())
    }

    // ---- 18. invites — unauthenticated returns 401 -------------------------

    @Test
    fun `invites unauthenticated returns 401`() {
        val resp = get("/api/auth/invites")
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ---- 19. register — valid invite creates user and issues session token -

    @Test
    fun `register valid invite token creates user and returns session token`() {
        val inviterToken = setupInviterSession("inviter-reg-${UUID.randomUUID()}")
        val inviteToken = generateInvite(inviterToken)
        val username = "alice_${UUID.randomUUID().toString().take(8)}"
        val token = registerUser(username, inviteToken)
        assertNotNull(token)
        assertTrue(token.isNotBlank())
        // Verify user was created
        val user = database.findUserByUsername(username)
        assertNotNull(user)
    }

    // ---- 20. register — expired invite returns 410 -------------------------

    @Test
    fun `register expired invite returns 410`() {
        // Create an invite and manually expire it
        val inviterToken = setupInviterSession("inviter-exp-${UUID.randomUUID()}")
        val inviteToken = generateInvite(inviterToken)
        val invite = database.findInviteByToken(inviteToken)!!
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE invites SET expires_at = NOW() - INTERVAL '1 hour' WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, invite.id)
                stmt.executeUpdate()
            }
        }
        val body = """{
            "invite_token": "$inviteToken",
            "username": "ghost2_${UUID.randomUUID().toString().take(8)}",
            "display_name": "Ghost",
            "auth_salt": "${urlEnc.encodeToString(ByteArray(16))}",
            "auth_verifier": "${urlEnc.encodeToString(ByteArray(32))}",
            "wrapped_master_key": "${stdEnc.encodeToString(ByteArray(64))}",
            "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
            "pubkey_format": "p256-spki",
            "pubkey": "${stdEnc.encodeToString(ByteArray(65))}",
            "device_id": "${UUID.randomUUID()}",
            "device_label": "Phone",
            "device_kind": "android"
        }"""
        val resp = post("/api/auth/register", body)
        assertEquals(GONE, resp.status)
    }

    // ---- 21. register — used invite returns 410 ----------------------------

    @Test
    fun `register already used invite returns 410`() {
        val inviterToken = setupInviterSession("inviter-used-${UUID.randomUUID()}")
        val inviteToken = generateInvite(inviterToken)
        val u1 = "firstuser_${UUID.randomUUID().toString().take(6)}"
        registerUser(u1, inviteToken)

        val body = """{
            "invite_token": "$inviteToken",
            "username": "seconduser_${UUID.randomUUID().toString().take(6)}",
            "display_name": "Second",
            "auth_salt": "${urlEnc.encodeToString(ByteArray(16))}",
            "auth_verifier": "${urlEnc.encodeToString(ByteArray(32))}",
            "wrapped_master_key": "${stdEnc.encodeToString(ByteArray(64))}",
            "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
            "pubkey_format": "p256-spki",
            "pubkey": "${stdEnc.encodeToString(ByteArray(65))}",
            "device_id": "${UUID.randomUUID()}",
            "device_label": "Phone",
            "device_kind": "android"
        }"""
        val resp = post("/api/auth/register", body)
        assertEquals(GONE, resp.status)
    }

    // ---- 22. register — duplicate username returns 409 ---------------------

    @Test
    fun `register duplicate username returns 409`() {
        val inviterToken = setupInviterSession("inviter-dup-${UUID.randomUUID()}")
        val invite1 = generateInvite(inviterToken)
        val invite2 = generateInvite(inviterToken)
        val username = "dupuser_${UUID.randomUUID().toString().take(6)}"
        registerUser(username, invite1)

        val body = """{
            "invite_token": "$invite2",
            "username": "$username",
            "display_name": "Dup",
            "auth_salt": "${urlEnc.encodeToString(ByteArray(16))}",
            "auth_verifier": "${urlEnc.encodeToString(ByteArray(32))}",
            "wrapped_master_key": "${stdEnc.encodeToString(ByteArray(64))}",
            "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
            "pubkey_format": "p256-spki",
            "pubkey": "${stdEnc.encodeToString(ByteArray(65))}",
            "device_id": "${UUID.randomUUID()}",
            "device_label": "Phone",
            "device_kind": "android"
        }"""
        val resp = post("/api/auth/register", body)
        assertEquals(CONFLICT, resp.status)
    }

    // ---- 23. logout — deletes session; subsequent requests return 401 ------

    @Test
    fun `logout deletes session and subsequent requests return 401`() {
        val inviterToken = setupInviterSession("inviter-logout-${UUID.randomUUID()}")
        val logoutResp = post("/api/auth/logout", "", inviterToken)
        assertEquals(NO_CONTENT, logoutResp.status)
        // Using the same token again should 401
        val inviteResp = get("/api/auth/invites", inviterToken)
        assertEquals(UNAUTHORIZED, inviteResp.status)
    }

    // ---- 24. Full pairing flow: initiate → qr → complete → status complete -

    @Test
    fun `full pairing flow completes successfully`() {
        val inviterToken = setupInviterSession("inviter-pair-${UUID.randomUUID()}")

        // 1. Android initiates
        val initiateResp = post("/api/auth/pairing/initiate", "", inviterToken)
        assertEquals(OK, initiateResp.status)
        val code = mapper.readTree(initiateResp.bodyString()).get("code").asText()
        assertNotNull(code)

        // 2. Web enters code
        val qrResp = post("/api/auth/pairing/qr", """{"code":"$code"}""")
        assertEquals(OK, qrResp.status)
        val sessionId = mapper.readTree(qrResp.bodyString()).get("session_id").asText()
        assertNotNull(sessionId)

        // 3. Check status is pending
        val pendingResp = get("/api/auth/pairing/status?session_id=$sessionId")
        assertEquals(OK, pendingResp.status)
        assertEquals("pending", mapper.readTree(pendingResp.bodyString()).get("state").asText())

        // 4. Android completes
        val wrappedKey = stdEnc.encodeToString(ByteArray(64) { 9 })
        val completeBody = """{
            "session_id": "$sessionId",
            "wrapped_master_key": "$wrappedKey",
            "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
            "web_pubkey": "${stdEnc.encodeToString(ByteArray(65))}",
            "web_pubkey_format": "p256-spki"
        }"""
        val completeResp = post("/api/auth/pairing/complete", completeBody, inviterToken)
        assertEquals(OK, completeResp.status)

        // 5. Status is now complete with session token
        val completeStatusResp = get("/api/auth/pairing/status?session_id=$sessionId")
        assertEquals(OK, completeStatusResp.status)
        val statusNode = mapper.readTree(completeStatusResp.bodyString())
        assertEquals("complete", statusNode.get("state").asText())
        assertNotNull(statusNode.get("session_token"))
        assertNotNull(statusNode.get("wrapped_master_key"))

    }

    // ---- 25. pairing/status returns pending before complete is called ------

    @Test
    fun `pairing status returns pending before complete`() {
        val inviterToken = setupInviterSession("inviter-pair2-${UUID.randomUUID()}")
        val initiateResp = post("/api/auth/pairing/initiate", "", inviterToken)
        val code = mapper.readTree(initiateResp.bodyString()).get("code").asText()
        val qrResp = post("/api/auth/pairing/qr", """{"code":"$code"}""")
        val sessionId = mapper.readTree(qrResp.bodyString()).get("session_id").asText()

        val statusResp = get("/api/auth/pairing/status?session_id=$sessionId")
        assertEquals(OK, statusResp.status)
        assertEquals("pending", mapper.readTree(statusResp.bodyString()).get("state").asText())
    }

    // ---- 26. pairing/status returns 404 for unknown session_id ------------

    @Test
    fun `pairing status returns 404 for unknown session_id`() {
        val resp = get("/api/auth/pairing/status?session_id=${UUID.randomUUID()}")
        assertEquals(NOT_FOUND, resp.status)
    }

    // ---- 27. pairing/qr returns 404 for expired or unknown code -----------

    @Test
    fun `pairing qr returns 404 for unknown code`() {
        val resp = post("/api/auth/pairing/qr", """{"code":"00000000"}""")
        assertEquals(NOT_FOUND, resp.status)
    }

    // ---- 28. Two users same username via register returns 409 --------------

    @Test
    fun `two users same username via register returns 409`() {
        val inviterToken = setupInviterSession("inviter-2user-${UUID.randomUUID()}")
        val invite1 = generateInvite(inviterToken)
        val invite2 = generateInvite(inviterToken)
        val username = "clashuser_${UUID.randomUUID().toString().take(6)}"
        registerUser(username, invite1)

        val body = """{
            "invite_token": "$invite2",
            "username": "$username",
            "display_name": "Clash",
            "auth_salt": "${urlEnc.encodeToString(ByteArray(16))}",
            "auth_verifier": "${urlEnc.encodeToString(ByteArray(32))}",
            "wrapped_master_key": "${stdEnc.encodeToString(ByteArray(64))}",
            "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
            "pubkey_format": "p256-spki",
            "pubkey": "${stdEnc.encodeToString(ByteArray(65))}",
            "device_id": "${UUID.randomUUID()}",
            "device_label": "Phone",
            "device_kind": "android"
        }"""
        val resp = post("/api/auth/register", body)
        assertEquals(CONFLICT, resp.status)
    }
}
