package digital.heirlooms.server

import digital.heirlooms.server.filters.sessionAuthFilter
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
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
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

/**
 * SEC-015: Integration tests for GET /api/auth/account and PATCH /api/auth/account.
 */
class BiometricAccountTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_test_bio")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private val mapper = ObjectMapper()
        private val urlEnc = Base64.getUrlEncoder().withoutPadding()
        private val stdEnc = Base64.getEncoder()

        private lateinit var dataSource: DataSource
        private lateinit var database: Database
        private lateinit var authRepo: PostgresAuthRepository
        private lateinit var keyRepo: PostgresKeyRepository
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
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_test_bio"
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
            authRepo = PostgresAuthRepository(dataSource)
            keyRepo = PostgresKeyRepository(dataSource)
            val serverSecret = ByteArray(32) { it.toByte() }
            val rawApp = buildApp(mockk(relaxed = true), database, authSecret = serverSecret)
            app = sessionAuthFilter(authRepo).then(rawApp)
        }

        private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

        private fun post(path: String, body: String, token: String? = null): Response {
            val req = Request(POST, path)
                .header("Content-Type", "application/json")
                .body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        private fun get(path: String, token: String? = null): Response {
            val req = Request(GET, path)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        private fun patch(path: String, body: String, token: String): Response {
            val req = Request(PATCH, path)
                .header("Content-Type", "application/json")
                .body(body)
                .header("X-Api-Key", token)
            return app(req)
        }

        /** Creates a session for the founding user and returns the session token. */
        private fun setupInviterSession(deviceId: String = "bio-inviter-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey = ByteArray(32) { 77 }
            val authSalt = ByteArray(16) { 22 }
            val authVerifier = sha256(authKey)
            keyRepo.insertWrappedKey(
                WrappedKeyRecord(
                    id = UUID.randomUUID(), deviceId = deviceId, deviceLabel = "Owner's Phone",
                    deviceKind = "android", pubkeyFormat = "p256-spki",
                    pubkey = ByteArray(65) { 7 }, wrappedMasterKey = ByteArray(64) { 8 },
                    wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
                    createdAt = Instant.now(), lastUsedAt = Instant.now(), retiredAt = null,
                ),
                userId = FOUNDING_USER_ID,
            )
            val body = """{
                "username": "bret",
                "device_id": "$deviceId",
                "auth_salt": "${urlEnc.encodeToString(authSalt)}",
                "auth_verifier": "${urlEnc.encodeToString(authVerifier)}"
            }"""
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing failed: ${resp.bodyString()}")
            return mapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        /** Registers a new user and returns a session token. */
        private fun registerUser(username: String, inviteToken: String): String {
            val authKey = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val authVerifier = sha256(authKey)
            val body = """{
                "invite_token": "$inviteToken",
                "username": "$username",
                "display_name": "${username.replaceFirstChar { it.uppercaseChar() }}",
                "auth_salt": "${urlEnc.encodeToString(authSalt)}",
                "auth_verifier": "${urlEnc.encodeToString(authVerifier)}",
                "wrapped_master_key": "${stdEnc.encodeToString(ByteArray(64) { 5 })}",
                "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
                "pubkey_format": "p256-spki",
                "pubkey": "${stdEnc.encodeToString(ByteArray(65) { 6 })}",
                "device_id": "${UUID.randomUUID()}",
                "device_label": "${username.replaceFirstChar { it.uppercaseChar() }}'s Phone",
                "device_kind": "android"
            }"""
            val resp = post("/api/auth/register", body)
            assertEquals(org.http4k.core.Status.Companion.CREATED, resp.status, "register failed: ${resp.bodyString()}")
            return mapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun generateInvite(sessionToken: String): String {
            val resp = get("/api/auth/invites", sessionToken)
            assertEquals(OK, resp.status)
            return mapper.readTree(resp.bodyString()).get("token").asText()
        }
    }

    // ---- SEC-015 T-1: GET /account returns require_biometric defaulting to false ----

    @Test
    fun `GET account returns require_biometric false by default`() {
        val inviterToken = setupInviterSession("bio-t1-${UUID.randomUUID()}")
        val invite = generateInvite(inviterToken)
        val token = registerUser("bio_t1_${UUID.randomUUID().toString().take(6)}", invite)

        val resp = get("/api/auth/account", token)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        val node = mapper.readTree(resp.bodyString())
        assertNotNull(node.get("user_id"))
        assertNotNull(node.get("username"))
        assertNotNull(node.get("require_biometric"))
        assertFalse(node.get("require_biometric").asBoolean(), "require_biometric should default to false")
    }

    // ---- SEC-015 T-2: PATCH /account sets require_biometric to true ----

    @Test
    fun `PATCH account sets require_biometric to true`() {
        val inviterToken = setupInviterSession("bio-t2-${UUID.randomUUID()}")
        val invite = generateInvite(inviterToken)
        val token = registerUser("bio_t2_${UUID.randomUUID().toString().take(6)}", invite)

        val patchResp = patch("/api/auth/account", """{"require_biometric":true}""", token)
        assertEquals(OK, patchResp.status, "Expected 200 on PATCH: ${patchResp.bodyString()}")

        val node = mapper.readTree(patchResp.bodyString())
        assertTrue(node.get("require_biometric").asBoolean(), "require_biometric should be true after PATCH")
    }

    // ---- SEC-015 T-3: GET /account after PATCH reflects updated value ----

    @Test
    fun `GET account after PATCH reflects updated require_biometric`() {
        val inviterToken = setupInviterSession("bio-t3-${UUID.randomUUID()}")
        val invite = generateInvite(inviterToken)
        val token = registerUser("bio_t3_${UUID.randomUUID().toString().take(6)}", invite)

        // Enable
        patch("/api/auth/account", """{"require_biometric":true}""", token)

        // Verify via GET
        val getResp = get("/api/auth/account", token)
        assertEquals(OK, getResp.status)
        val node = mapper.readTree(getResp.bodyString())
        assertTrue(node.get("require_biometric").asBoolean(), "GET should reflect true after PATCH")

        // Disable again
        patch("/api/auth/account", """{"require_biometric":false}""", token)
        val getResp2 = get("/api/auth/account", token)
        assertFalse(mapper.readTree(getResp2.bodyString()).get("require_biometric").asBoolean(),
            "GET should reflect false after second PATCH")
    }

    // ---- SEC-015 T-4: GET /account without token returns 401 ----

    @Test
    fun `GET account without token returns 401`() {
        val resp = get("/api/auth/account")
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ---- SEC-015 T-5: PATCH /account without token returns 401 ----

    @Test
    fun `PATCH account without token returns 401`() {
        val req = Request(PATCH, "/api/auth/account")
            .header("Content-Type", "application/json")
            .body("""{"require_biometric":true}""")
        val resp = app(req)
        assertEquals(UNAUTHORIZED, resp.status)
    }

    // ---- SEC-015 T-6: PATCH /account with no require_biometric field is a no-op ----

    @Test
    fun `PATCH account with no require_biometric field does not change value`() {
        val inviterToken = setupInviterSession("bio-t6-${UUID.randomUUID()}")
        val invite = generateInvite(inviterToken)
        val token = registerUser("bio_t6_${UUID.randomUUID().toString().take(6)}", invite)

        // First enable it
        patch("/api/auth/account", """{"require_biometric":true}""", token)

        // PATCH with unrelated field — should not reset require_biometric
        patch("/api/auth/account", """{}""", token)

        val getResp = get("/api/auth/account", token)
        val node = mapper.readTree(getResp.bodyString())
        assertTrue(node.get("require_biometric").asBoolean(),
            "require_biometric should still be true after a no-op PATCH")
    }
}
