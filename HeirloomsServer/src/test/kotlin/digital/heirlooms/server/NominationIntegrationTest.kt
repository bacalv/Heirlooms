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
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
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

private val nomIntMapper = ObjectMapper()
private val nomIntUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val nomIntStdEnc = Base64.getEncoder()

private fun sha256NomInt(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

/**
 * Integration tests for DEV-003: executor nomination lifecycle + capsule recipient linking.
 *
 * Test ordering is used to share state (connection id, nomination id) across lifecycle steps.
 *
 * Coverage:
 * - Nomination lifecycle: create → accept → revoke → re-nominate (same connection allowed after revoke)
 * - Recipient link: link → duplicate attempt → 409
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NominationIntegrationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_nomination")
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

        // Shared state
        private lateinit var ownerToken: String
        private lateinit var ownerId: String
        private lateinit var nomineeToken: String
        private lateinit var nomineeId: String

        // Connection and nomination IDs populated as tests run
        private lateinit var connectionId: String
        private lateinit var nominationId: String

        // Capsule-recipient link state
        private lateinit var capsuleId: String
        private lateinit var recipientRowId: String
        private lateinit var linkConnectionId: String
        private lateinit var recipient2RowId: String

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
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_nomination"
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

            val localFileStore = LocalFileStore(Files.createTempDirectory("heirlooms-nom-test-"))
            val rawApp = buildApp(localFileStore, database, authSecret = serverSecret)
            app = sessionAuthFilter(authRepo).then(rawApp)

            // Set up the founding user (owner) session
            ownerToken = setupFoundingUserSession()
            ownerId = FOUNDING_USER_ID.toString()

            // Register a second user (nominee) using an invite from owner
            val inviteToken = generateInvite(ownerToken)
            val (token2, id2) = registerUser("nominee-${UUID.randomUUID()}", inviteToken)
            nomineeToken = token2
            nomineeId = id2
        }

        // ── Auth helpers ──────────────────────────────────────────────────────

        private fun setupFoundingUserSession(deviceId: String = "nom-int-owner-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey = ByteArray(32) { 99 }
            val authSalt = ByteArray(16) { 11 }
            val authVerifier = sha256NomInt(authKey)
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
                  "auth_salt": "${nomIntUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${nomIntUrlEnc.encodeToString(authVerifier)}"
                }
            """.trimIndent()
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing failed: ${resp.bodyString()}")
            return nomIntMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun generateInvite(token: String): String {
            val resp = get("/api/auth/invites", token)
            assertEquals(OK, resp.status, "Failed to generate invite: ${resp.bodyString()}")
            return nomIntMapper.readTree(resp.bodyString()).get("token").asText()
        }

        private fun registerUser(username: String, inviteToken: String): Pair<String, String> {
            val authKey = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val authVerifier = sha256NomInt(authKey)
            val wrappedMasterKey = ByteArray(64) { 5 }
            val pubkey = ByteArray(65) { 6 }
            val body = """
                {
                  "invite_token": "$inviteToken",
                  "username": "$username",
                  "display_name": "Nominee User",
                  "auth_salt": "${nomIntUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${nomIntUrlEnc.encodeToString(authVerifier)}",
                  "wrapped_master_key": "${nomIntStdEnc.encodeToString(wrappedMasterKey)}",
                  "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
                  "pubkey_format": "p256-spki",
                  "pubkey": "${nomIntStdEnc.encodeToString(pubkey)}",
                  "device_id": "${UUID.randomUUID()}",
                  "device_label": "Nominee Phone",
                  "device_kind": "android"
                }
            """.trimIndent()
            val resp = post("/api/auth/register", body)
            assertEquals(CREATED, resp.status, "Registration failed: ${resp.bodyString()}")
            val node = nomIntMapper.readTree(resp.bodyString())
            return Pair(node.get("session_token").asText(), node.get("user_id").asText())
        }

        // ── HTTP helpers ──────────────────────────────────────────────────────

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
    }

    // ─── Nomination lifecycle ─────────────────────────────────────────────────

    @Test
    @Order(1)
    fun `step1 create connection for the nominee (bound connection via friend)`() {
        // After registration, there should be a backfilled connection from owner to nominee.
        // But the migration only backfills friendships — we need to create the connection explicitly.
        // Create a bound connection using the nominee's userId
        val body = """{"display_name":"Nominee User","contact_user_id":"$nomineeId","roles":["executor"]}"""
        val resp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")

        val node = nomIntMapper.readTree(resp.bodyString())
        assertTrue(node.has("connection"))
        connectionId = node.get("connection").get("id").asText()
        assertNotNull(connectionId)
    }

    @Test
    @Order(2)
    fun `step2 owner creates nomination for nominee - status is pending`() {
        val body = """{"connection_id":"$connectionId","message":"Please be my executor"}"""
        val resp = post("/api/executor-nominations", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")

        val node = nomIntMapper.readTree(resp.bodyString())
        val nom = node.get("nomination")
        assertEquals("pending", nom.get("status").asText())
        assertEquals(connectionId, nom.get("connection_id").asText())
        nominationId = nom.get("id").asText()
        assertNotNull(nominationId)
    }

    @Test
    @Order(3)
    fun `step3 owner list shows the pending nomination`() {
        val resp = get("/api/executor-nominations", ownerToken)
        assertEquals(OK, resp.status)

        val nominations = nomIntMapper.readTree(resp.bodyString()).get("nominations")
        val ids = (0 until nominations.size()).map { nominations[it].get("id").asText() }
        assertTrue(ids.contains(nominationId), "Owner list should contain the nomination")
    }

    @Test
    @Order(4)
    fun `step4 nominee receives the nomination`() {
        val resp = get("/api/executor-nominations/received", nomineeToken)
        assertEquals(OK, resp.status)

        val nominations = nomIntMapper.readTree(resp.bodyString()).get("nominations")
        val ids = (0 until nominations.size()).map { nominations[it].get("id").asText() }
        assertTrue(ids.contains(nominationId), "Nominee should see the received nomination")
    }

    @Test
    @Order(5)
    fun `step5 duplicate nomination returns 409`() {
        // A second nomination for the same connection while first is pending → 409
        val body = """{"connection_id":"$connectionId"}"""
        val resp = post("/api/executor-nominations", body, ownerToken)
        assertEquals(CONFLICT, resp.status, "Expected 409 for duplicate: ${resp.bodyString()}")
        assertEquals("active_nomination_exists", nomIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    @Order(6)
    fun `step6 nominee accepts the pending nomination`() {
        val resp = post("/api/executor-nominations/$nominationId/accept", "", nomineeToken)
        assertEquals(OK, resp.status, "Expected 200 accept: ${resp.bodyString()}")

        val nom = nomIntMapper.readTree(resp.bodyString()).get("nomination")
        assertEquals("accepted", nom.get("status").asText())
        assertNotNull(nom.get("responded_at"))
    }

    @Test
    @Order(7)
    fun `step7 owner revokes the accepted nomination`() {
        val resp = post("/api/executor-nominations/$nominationId/revoke", "", ownerToken)
        assertEquals(OK, resp.status, "Expected 200 revoke: ${resp.bodyString()}")

        val nom = nomIntMapper.readTree(resp.bodyString()).get("nomination")
        assertEquals("revoked", nom.get("status").asText())
        assertNotNull(nom.get("revoked_at"))
    }

    @Test
    @Order(8)
    fun `step8 re-nomination for same connection is allowed after revoke`() {
        // After revoke, there is no longer an active nomination — a new one should be allowed
        val body = """{"connection_id":"$connectionId","message":"Please reconsider"}"""
        val resp = post("/api/executor-nominations", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201 re-nomination: ${resp.bodyString()}")

        val nom = nomIntMapper.readTree(resp.bodyString()).get("nomination")
        assertEquals("pending", nom.get("status").asText())
        assertEquals("Please reconsider", nom.get("message").asText())
    }

    // ─── Capsule recipient linking ─────────────────────────────────────────────

    @Test
    @Order(20)
    fun `step20 create a capsule with two recipients`() {
        val unlockAt = "2030-01-01T00:00:00Z"
        val body = """
            {
              "shape": "open",
              "unlock_at": "$unlockAt",
              "recipients": ["alice@example.com", "bob@example.com"],
              "upload_ids": []
            }
        """.trimIndent()
        val resp = post("/api/capsules", body, ownerToken)
        // Capsule creation may return 201; check for a 2xx
        assertTrue(resp.status.successful, "Expected 2xx creating capsule: ${resp.bodyString()}")

        val node = nomIntMapper.readTree(resp.bodyString())
        capsuleId = (node.get("capsule") ?: node).get("id").asText()
        assertNotNull(capsuleId)
    }

    @Test
    @Order(21)
    fun `step21 retrieve recipient row IDs from capsule`() {
        val resp = get("/api/capsules/$capsuleId", ownerToken)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        nomIntMapper.readTree(resp.bodyString())
        // recipients in capsule are stored as strings; we need the row IDs from DB
        // We get the row IDs by querying the database directly for this integration test
        dataSource.connection.use { conn ->
            val rows = conn.prepareStatement(
                "SELECT id, recipient FROM capsule_recipients WHERE capsule_id = ? ORDER BY added_at"
            ).use { stmt ->
                stmt.setObject(1, UUID.fromString(capsuleId))
                val rs = stmt.executeQuery()
                val list = mutableListOf<Pair<String, String>>()
                while (rs.next()) {
                    list.add(Pair(rs.getString("id"), rs.getString("recipient")))
                }
                list
            }
            assertTrue(rows.size >= 2, "Expected at least 2 recipient rows, got ${rows.size}")
            recipientRowId = rows[0].first
            recipient2RowId = rows[1].first
        }
    }

    @Test
    @Order(22)
    fun `step22 create a connection to link to the capsule recipient`() {
        val email = "link-target-${UUID.randomUUID()}@example.com"
        val body = """{"display_name":"Link Target","email":"$email","roles":["recipient"]}"""
        val resp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, resp.status)
        linkConnectionId = nomIntMapper.readTree(resp.bodyString()).get("connection").get("id").asText()
    }

    @Test
    @Order(23)
    fun `step23 link the recipient row to the connection returns 200`() {
        val body = """{"connection_id":"$linkConnectionId"}"""
        val resp = patch("/api/capsules/$capsuleId/recipients/$recipientRowId/link", body, ownerToken)
        assertEquals(OK, resp.status, "Expected 200 link: ${resp.bodyString()}")

        val node = nomIntMapper.readTree(resp.bodyString())
        val recipient = node.get("recipient")
        assertEquals(linkConnectionId, recipient.get("connection_id").asText())
        assertEquals(recipientRowId, recipient.get("id").asText())
    }

    @Test
    @Order(24)
    fun `step24 linking same connection to second recipient on same capsule returns 409`() {
        // Attempt to link the same connection_id to recipient2RowId → should 409
        val body = """{"connection_id":"$linkConnectionId"}"""
        val resp = patch("/api/capsules/$capsuleId/recipients/$recipient2RowId/link", body, ownerToken)
        assertEquals(CONFLICT, resp.status, "Expected 409 duplicate link: ${resp.bodyString()}")
        assertEquals(
            "connection_already_linked",
            nomIntMapper.readTree(resp.bodyString()).get("error").asText()
        )
    }
}
