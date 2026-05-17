package digital.heirlooms.server

import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.filters.sessionAuthFilter
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.storage.LocalFileStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val connIntMapper = ObjectMapper()
private val connIntUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val connIntStdEnc = Base64.getEncoder()

private fun sha256ConnInt(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

/**
 * Integration test for the Connections CRUD API (DEV-002).
 *
 * Tests run against a real Postgres database via Testcontainers.
 * Exercises full CRUD lifecycle and the HTTP 409 DELETE-blocking condition.
 *
 * Test ordering is used to share state (connection id) across lifecycle steps.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConnectionIntegrationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_connections")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private lateinit var dataSource: DataSource
        private lateinit var database: Database
        private lateinit var authRepo: PostgresAuthRepository
        private lateinit var keyRepo: PostgresKeyRepository
        private lateinit var app: org.http4k.core.HttpHandler

        private val serverSecret = ByteArray(32) { it.toByte() }

        // Shared state across ordered tests
        private lateinit var ownerToken: String
        private lateinit var ownerId: String
        private lateinit var createdConnectionId: String

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
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_connections"
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

            val localFileStore = LocalFileStore(Files.createTempDirectory("heirlooms-conn-test-"))
            val rawApp = buildApp(localFileStore, database, authSecret = serverSecret)
            app = sessionAuthFilter(authRepo).then(rawApp)

            // Create a session for the founding user (owner)
            ownerToken = setupOwnerSession()
            ownerId = FOUNDING_USER_ID.toString()
        }

        private fun setupOwnerSession(deviceId: String = "conn-test-owner-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey = ByteArray(32) { 99 }
            val authSalt = ByteArray(16) { 11 }
            val authVerifier = sha256ConnInt(authKey)
            val pubkey = ByteArray(65) { 7 }
            val wrappedMasterKey = ByteArray(64) { 8 }

            keyRepo.insertWrappedKey(
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
                  "auth_salt": "${connIntUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${connIntUrlEnc.encodeToString(authVerifier)}"
                }
            """.trimIndent()
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing failed: ${resp.bodyString()}")
            return connIntMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        fun post(path: String, body: String, token: String? = null): Response {
            val req = Request(POST, path)
                .header("Content-Type", "application/json")
                .body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun get(path: String, token: String? = null): Response =
            app(Request(GET, path).let { if (token != null) it.header("X-Api-Key", token) else it })

        fun patch(path: String, body: String, token: String? = null): Response {
            val req = Request(PATCH, path)
                .header("Content-Type", "application/json")
                .body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun delete(path: String, token: String? = null): Response =
            app(Request(DELETE, path).let { if (token != null) it.header("X-Api-Key", token) else it })
    }

    // ─── 1. Create a connection ───────────────────────────────────────────────

    @Test
    @Order(1)
    fun `step1 create placeholder connection returns 201`() {
        val email = "integration-test-${UUID.randomUUID()}@example.com"
        val body = """{"display_name":"Integration Alice","email":"$email","roles":["recipient"]}"""
        val resp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")

        val node = connIntMapper.readTree(resp.bodyString())
        assertTrue(node.has("connection"))
        val conn = node.get("connection")
        assertEquals("Integration Alice", conn.get("display_name").asText())
        assertTrue(conn.get("contact_user_id").isNull, "placeholder should have null contact_user_id")
        assertNotNull(conn.get("id").asText())

        createdConnectionId = conn.get("id").asText()
    }

    // ─── 2. List connections ──────────────────────────────────────────────────

    @Test
    @Order(2)
    fun `step2 list connections contains created connection`() {
        val resp = get("/api/connections", ownerToken)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        val node = connIntMapper.readTree(resp.bodyString())
        assertTrue(node.has("connections"))
        val ids = (0 until node.get("connections").size()).map { node.get("connections")[it].get("id").asText() }
        assertTrue(ids.contains(createdConnectionId), "List should contain created connection id")
    }

    // ─── 3. Fetch by ID ───────────────────────────────────────────────────────

    @Test
    @Order(3)
    fun `step3 fetch connection by id returns correct record`() {
        val resp = get("/api/connections/$createdConnectionId", ownerToken)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        val node = connIntMapper.readTree(resp.bodyString())
        assertTrue(node.has("connection"))
        assertEquals(createdConnectionId, node.get("connection").get("id").asText())
        assertEquals("Integration Alice", node.get("connection").get("display_name").asText())
    }

    @Test
    @Order(3)
    fun `step3 fetch non-existent connection returns 404`() {
        val fakeId = UUID.randomUUID()
        val resp = get("/api/connections/$fakeId", ownerToken)
        assertEquals(NOT_FOUND, resp.status)
    }

    // ─── 4. Update display name ───────────────────────────────────────────────

    @Test
    @Order(4)
    fun `step4 patch connection updates display name`() {
        val body = """{"display_name":"Integration Alice Smith"}"""
        val resp = patch("/api/connections/$createdConnectionId", body, ownerToken)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        val node = connIntMapper.readTree(resp.bodyString())
        assertEquals("Integration Alice Smith", node.get("connection").get("display_name").asText())
    }

    // ─── 5. Delete connection (happy path) ────────────────────────────────────

    @Test
    @Order(10)
    fun `step10 delete connection without nominations returns 204`() {
        // Create a fresh connection to delete
        val email = "to-delete-${UUID.randomUUID()}@example.com"
        val body = """{"display_name":"To Delete","email":"$email","roles":[]}"""
        val createResp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, createResp.status)
        val deleteId = connIntMapper.readTree(createResp.bodyString()).get("connection").get("id").asText()

        val deleteResp = delete("/api/connections/$deleteId", ownerToken)
        assertEquals(NO_CONTENT, deleteResp.status)

        // Verify it's gone
        val getResp = get("/api/connections/$deleteId", ownerToken)
        assertEquals(NOT_FOUND, getResp.status)
    }

    // ─── 6. DELETE blocked by pending nomination (HTTP 409) ───────────────────

    @Test
    @Order(11)
    fun `step11 delete connection with pending nomination returns 409`() {
        // Create a fresh connection
        val email = "exec-${UUID.randomUUID()}@example.com"
        val body = """{"display_name":"Executor Candidate","email":"$email","roles":["executor"]}"""
        val createResp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, createResp.status)
        val nomineeConnId = connIntMapper.readTree(createResp.bodyString()).get("connection").get("id").asText()

        // Insert a pending executor nomination directly into the database
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO executor_nominations (id, owner_user_id, connection_id, status, offered_at)
                   VALUES (?, ?, ?, 'pending', NOW())"""
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, UUID.fromString(ownerId))
                stmt.setObject(3, UUID.fromString(nomineeConnId))
                stmt.executeUpdate()
            }
        }

        // Attempt to delete — must return 409
        val deleteResp = delete("/api/connections/$nomineeConnId", ownerToken)
        assertEquals(CONFLICT, deleteResp.status, "Expected 409 due to pending nomination: ${deleteResp.bodyString()}")

        val node = connIntMapper.readTree(deleteResp.bodyString())
        assertEquals("active_nominations_exist", node.get("error").asText())
    }
}
