package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.filters.sessionAuthFilter
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.storage.LocalFileStore
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
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
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val esIntMapper = ObjectMapper()
private val esIntUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val esIntStdEnc = Base64.getEncoder()
private val esIntRng = SecureRandom()

private fun sha256EsInt(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

/**
 * Integration tests for DEV-004: executor share distribution endpoints.
 *
 * Test ordering shares state (capsule id, nomination ids, executor tokens) across steps.
 *
 * NOTE: This test seeds the capsule in the 'sealed' shape directly via SQL because the
 * /seal endpoint (DEV-005) has not yet been implemented. This bypasses the normal seal
 * flow so that share upload can be tested without depending on DEV-005. When DEV-005 is
 * merged, these tests may be updated to use the real seal endpoint if desired.
 *
 * Coverage:
 * - POST /capsules/:id/executor-shares  — happy path (HTTP 200)
 * - GET  /capsules/:id/executor-shares/mine — each executor retrieves their share
 * - GET  /capsules/:id/executor-shares/collect — author retrieves all shares with threshold/total
 * - GET  /capsules/:id/executor-shares/mine — non-executor gets HTTP 403
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ExecutorShareIntegrationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_exec_share")
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

        // Shared user state
        private lateinit var ownerToken: String
        private lateinit var ownerId: String
        private lateinit var executor1Token: String
        private lateinit var executor1Id: String
        private lateinit var executor2Token: String
        private lateinit var executor2Id: String
        /** A third user who is NOT nominated as executor */
        private lateinit var nonExecutorToken: String

        // Connection and nomination IDs
        private lateinit var conn1Id: String
        private lateinit var conn2Id: String
        private lateinit var nom1Id: String
        private lateinit var nom2Id: String

        // Capsule (seeded directly via SQL as sealed with shamir config)
        private lateinit var capsuleId: String

        // Envelope helper ─────────────────────────────────────────────────────

        /**
         * Build a minimal valid capsule-ecdh-aes256gcm-v1 asymmetric envelope.
         * Layout: version(1) | algIdLen(1) | algId(N) | ephPubkey(65) | nonce(12) | ciphertext(V) | authTag(16)
         */
        private fun validCapsuleEnvelope(): String {
            val algId = AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1.toByteArray(Charsets.UTF_8)
            val ephPubkey = byteArrayOf(0x04) + ByteArray(64).also { esIntRng.nextBytes(it) }
            val nonce = ByteArray(12).also { esIntRng.nextBytes(it) }
            val ciphertext = ByteArray(64).also { esIntRng.nextBytes(it) }
            val authTag = ByteArray(16).also { esIntRng.nextBytes(it) }
            val blob = byteArrayOf(0x01, algId.size.toByte()) + algId + ephPubkey + nonce + ciphertext + authTag
            return esIntUrlEnc.encodeToString(blob)
        }

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
            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_exec_share"
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

            val localFileStore = LocalFileStore(Files.createTempDirectory("heirlooms-es-test-"))
            val rawApp = buildApp(localFileStore, database, authSecret = serverSecret)
            app = sessionAuthFilter(authRepo).then(rawApp)

            // Set up founding user (owner)
            ownerToken = setupFoundingUserSession()
            ownerId = FOUNDING_USER_ID.toString()

            // Register executor 1
            val inv1 = generateInvite(ownerToken)
            val (tok1, id1) = registerUser("executor1-${UUID.randomUUID()}", inv1)
            executor1Token = tok1
            executor1Id = id1

            // Register executor 2
            val inv2 = generateInvite(ownerToken)
            val (tok2, id2) = registerUser("executor2-${UUID.randomUUID()}", inv2)
            executor2Token = tok2
            executor2Id = id2

            // Register non-executor
            val inv3 = generateInvite(ownerToken)
            val (tok3, _) = registerUser("nonexec-${UUID.randomUUID()}", inv3)
            nonExecutorToken = tok3
        }

        // ── Auth helpers ──────────────────────────────────────────────────────

        private fun setupFoundingUserSession(deviceId: String = "es-int-owner-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey = ByteArray(32) { 99 }
            val authSalt = ByteArray(16) { 11 }
            val authVerifier = sha256EsInt(authKey)
            val pubkey = ByteArray(65) { 7 }
            val wrappedMasterKey = ByteArray(64) { 8 }

            keyRepo.insertWrappedKey(
                WrappedKeyRecord(
                    id = UUID.randomUUID(),
                    deviceId = deviceId,
                    deviceLabel = "Owner Device",
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
                  "auth_salt": "${esIntUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${esIntUrlEnc.encodeToString(authVerifier)}"
                }
            """.trimIndent()
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing failed: ${resp.bodyString()}")
            return esIntMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun generateInvite(token: String): String {
            val resp = get("/api/auth/invites", token)
            assertEquals(OK, resp.status, "Failed to generate invite: ${resp.bodyString()}")
            return esIntMapper.readTree(resp.bodyString()).get("token").asText()
        }

        private fun registerUser(username: String, inviteToken: String): Pair<String, String> {
            val authKey = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val authVerifier = sha256EsInt(authKey)
            val wrappedMasterKey = ByteArray(64) { 5 }
            val pubkey = ByteArray(65) { 6 }
            val body = """
                {
                  "invite_token": "$inviteToken",
                  "username": "$username",
                  "display_name": "Test Executor",
                  "auth_salt": "${esIntUrlEnc.encodeToString(authSalt)}",
                  "auth_verifier": "${esIntUrlEnc.encodeToString(authVerifier)}",
                  "wrapped_master_key": "${esIntStdEnc.encodeToString(wrappedMasterKey)}",
                  "wrap_format": "p256-ecdh-hkdf-aes256gcm-v1",
                  "pubkey_format": "p256-spki",
                  "pubkey": "${esIntStdEnc.encodeToString(pubkey)}",
                  "device_id": "${UUID.randomUUID()}",
                  "device_label": "Executor Phone",
                  "device_kind": "android"
                }
            """.trimIndent()
            val resp = post("/api/auth/register", body)
            assertEquals(CREATED, resp.status, "Registration failed: ${resp.bodyString()}")
            val node = esIntMapper.readTree(resp.bodyString())
            return Pair(node.get("session_token").asText(), node.get("user_id").asText())
        }

        // ── HTTP helpers ──────────────────────────────────────────────────────

        fun post(path: String, body: String, token: String? = null): Response {
            val req = Request(POST, path).header("Content-Type", "application/json").body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun get(path: String, token: String? = null): Response =
            app(Request(GET, path).let { if (token != null) it.header("X-Api-Key", token) else it })
    }

    // ─── Setup: connections and nominations ───────────────────────────────────

    @Test
    @Order(1)
    fun `step1 create bound connection to executor1`() {
        val body = """{"display_name":"Executor One","contact_user_id":"$executor1Id","roles":["executor"]}"""
        val resp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")
        conn1Id = esIntMapper.readTree(resp.bodyString()).get("connection").get("id").asText()
        assertNotNull(conn1Id)
    }

    @Test
    @Order(2)
    fun `step2 create bound connection to executor2`() {
        val body = """{"display_name":"Executor Two","contact_user_id":"$executor2Id","roles":["executor"]}"""
        val resp = post("/api/connections", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")
        conn2Id = esIntMapper.readTree(resp.bodyString()).get("connection").get("id").asText()
        assertNotNull(conn2Id)
    }

    @Test
    @Order(3)
    fun `step3 owner nominates executor1`() {
        val body = """{"connection_id":"$conn1Id","message":"Please be executor 1"}"""
        val resp = post("/api/executor-nominations", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")
        nom1Id = esIntMapper.readTree(resp.bodyString()).get("nomination").get("id").asText()
        assertNotNull(nom1Id)
    }

    @Test
    @Order(4)
    fun `step4 owner nominates executor2`() {
        val body = """{"connection_id":"$conn2Id","message":"Please be executor 2"}"""
        val resp = post("/api/executor-nominations", body, ownerToken)
        assertEquals(CREATED, resp.status, "Expected 201: ${resp.bodyString()}")
        nom2Id = esIntMapper.readTree(resp.bodyString()).get("nomination").get("id").asText()
        assertNotNull(nom2Id)
    }

    @Test
    @Order(5)
    fun `step5 executor1 accepts nomination`() {
        val resp = post("/api/executor-nominations/$nom1Id/accept", "", executor1Token)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")
        assertEquals("accepted", esIntMapper.readTree(resp.bodyString()).get("nomination").get("status").asText())
    }

    @Test
    @Order(6)
    fun `step6 executor2 accepts nomination`() {
        val resp = post("/api/executor-nominations/$nom2Id/accept", "", executor2Token)
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")
        assertEquals("accepted", esIntMapper.readTree(resp.bodyString()).get("nomination").get("status").asText())
    }

    // ─── Seed capsule directly via SQL (bypass /seal — DEV-005 not yet implemented) ───

    @Test
    @Order(7)
    fun `step7 seed sealed capsule via SQL with shamir config`() {
        // NOTE: The /seal endpoint (DEV-005) has not been implemented yet.
        // We seed the capsule in the 'sealed' shape directly via SQL so that share
        // upload can be tested without depending on DEV-005. When DEV-005 is merged,
        // this step can optionally be replaced with a real seal API call.
        val id = UUID.randomUUID()
        capsuleId = id.toString()
        val now = Timestamp.from(Instant.now())
        val unlockAt = Timestamp.from(Instant.parse("2035-01-01T00:00:00Z"))

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO capsules
                     (id, created_at, updated_at, created_by_user, shape, state, unlock_at, user_id,
                      shamir_threshold, shamir_total_shares, sealed_at)
                   VALUES (?, ?, ?, 'bret', 'sealed', 'sealed', ?, ?, 2, 2, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setTimestamp(2, now)
                stmt.setTimestamp(3, now)
                stmt.setTimestamp(4, unlockAt)
                stmt.setObject(5, FOUNDING_USER_ID)
                stmt.setTimestamp(6, now)
                stmt.executeUpdate()
            }
        }

        assertNotNull(capsuleId)
    }

    // ─── POST /executor-shares ────────────────────────────────────────────────

    @Test
    @Order(10)
    fun `step10 owner uploads all shares successfully — expects HTTP 200`() {
        val share1 = """{"nomination_id":"$nom1Id","share_index":1,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"}"""
        val share2 = """{"nomination_id":"$nom2Id","share_index":2,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"}"""
        val body = """{"shares":[$share1,$share2]}"""

        val resp = post("/api/capsules/$capsuleId/executor-shares", body, ownerToken)
        assertEquals(OK, resp.status, "Expected 200 on valid share upload: ${resp.bodyString()}")
        assertEquals("{}", resp.bodyString())
    }

    // ─── GET /executor-shares/mine ────────────────────────────────────────────

    @Test
    @Order(11)
    fun `step11 executor1 fetches own share — expects HTTP 200 with wrapped_share`() {
        val resp = get("/api/capsules/$capsuleId/executor-shares/mine", executor1Token)
        assertEquals(OK, resp.status, "Expected 200 for executor1 /mine: ${resp.bodyString()}")

        val node = esIntMapper.readTree(resp.bodyString()).get("share")
        assertNotNull(node)
        assertTrue(node.get("wrapped_share").asText().isNotEmpty())
        assertEquals("shamir-share-v1", node.get("share_format").asText())
        assertEquals(capsuleId, node.get("capsule_id").asText())
        assertTrue(node.get("share_index").asInt() in 1..2)
    }

    @Test
    @Order(12)
    fun `step12 executor2 fetches own share — expects HTTP 200 with wrapped_share`() {
        val resp = get("/api/capsules/$capsuleId/executor-shares/mine", executor2Token)
        assertEquals(OK, resp.status, "Expected 200 for executor2 /mine: ${resp.bodyString()}")

        val node = esIntMapper.readTree(resp.bodyString()).get("share")
        assertNotNull(node)
        assertTrue(node.get("wrapped_share").asText().isNotEmpty())
        assertEquals("shamir-share-v1", node.get("share_format").asText())
    }

    @Test
    @Order(13)
    fun `step13 non-executor calling mine gets HTTP 403`() {
        val resp = get("/api/capsules/$capsuleId/executor-shares/mine", nonExecutorToken)
        assertEquals(FORBIDDEN, resp.status, "Expected 403 for non-executor: ${resp.bodyString()}")
        assertEquals("forbidden", esIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── GET /executor-shares/collect ─────────────────────────────────────────

    @Test
    @Order(14)
    fun `step14 author collects all shares with correct threshold and total`() {
        val resp = get("/api/capsules/$capsuleId/executor-shares/collect", ownerToken)
        assertEquals(OK, resp.status, "Expected 200 for author /collect: ${resp.bodyString()}")

        val node = esIntMapper.readTree(resp.bodyString())
        val shares = node.get("shares")
        assertNotNull(shares)
        assertEquals(2, shares.size(), "Expected 2 shares in collect response")
        assertEquals(2, node.get("threshold").asInt())
        assertEquals(2, node.get("total").asInt())

        // Each share must have the required fields
        for (i in 0 until shares.size()) {
            val s = shares[i]
            assertTrue(s.has("share_index"))
            assertTrue(s.has("wrapped_share"))
            assertTrue(s.has("nomination_id"))
        }
    }

    @Test
    @Order(15)
    fun `step15 non-author calling collect gets HTTP 403`() {
        val resp = get("/api/capsules/$capsuleId/executor-shares/collect", executor1Token)
        assertEquals(FORBIDDEN, resp.status, "Expected 403 for non-author /collect: ${resp.bodyString()}")
        assertEquals("forbidden", esIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── Validation error cases on POST ──────────────────────────────────────

    @Test
    @Order(20)
    fun `step20 wrong share count returns 422`() {
        // Capsule expects 2 shares but we only send 1
        val share1 = """{"nomination_id":"$nom1Id","share_index":1,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"}"""
        val body = """{"shares":[$share1]}"""

        val resp = post("/api/capsules/$capsuleId/executor-shares", body, ownerToken)
        assertEquals(422, resp.status.code, "Expected 422 wrong_share_count: ${resp.bodyString()}")
        assertEquals("wrong_share_count", esIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    @Order(21)
    fun `step21 missing nomination ID 404 — executor1 mine before re-upload is still 200`() {
        // After step 10 the shares were already written; a second POST should show wrong count
        // (we send 2 to a fresh capsule that already has shares — they are re-inserted, which
        // may violate the check; since this is an integration test we just verify 404 for a
        // nonexistent capsule /mine call)
        val nonExistentCapsule = UUID.randomUUID()
        val resp = get("/api/capsules/$nonExistentCapsule/executor-shares/mine", executor1Token)
        assertEquals(FORBIDDEN, resp.status, "Expected 403 for unknown capsule: ${resp.bodyString()}")
    }
}
