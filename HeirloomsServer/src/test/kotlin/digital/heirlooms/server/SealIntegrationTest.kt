package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.crypto.tlock.StubTimeLockProvider
import digital.heirlooms.server.crypto.tlock.TimeLockCiphertext
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.filters.sessionAuthFilter
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.capsule.PostgresSealRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.storage.LocalFileStore
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
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

private val sealIntMapper = ObjectMapper()
private val sealIntUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val sealIntRng    = SecureRandom()

private fun sha256SealInt(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

/**
 * Integration tests for DEV-005: PUT/POST /api/capsules/:id/seal
 *
 * Uses Testcontainers (PostgreSQL) + LocalFileStore + StubTimeLockProvider.
 * All capsules are created via the API; the seal endpoint is exercised end-to-end.
 *
 * LOGGING PROHIBITION: dek_tlock values are asserted present but never logged
 * or printed to test output. The assertion verifies SHA-256 correctness without
 * revealing key material.
 *
 * Test ordering matters — capsule IDs and connection state flow between steps.
 *
 * Coverage:
 * 1. Happy path: plain ECDH seal — shape='sealed', wrapped_capsule_key written, sealed_at set
 * 2. Happy path: tlock seal — tlock columns written, tlock_key_digest = SHA-256(dek_tlock)
 * 3. Happy path: Shamir seal — shamir columns written, nominations count validated
 * 4. POST verb works (backwards compat)
 * 5. Error: seal already-sealed capsule → HTTP 409
 * 6. Error: invalid envelope → HTTP 422
 * 7. Error: tlock disabled (no tlock in request, but we test with the provider set to disabled)
 * 8. Error: deferred-pubkey connection (step [6] triggers before step [15])
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SealIntegrationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_seal")
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
        // Known stub secret for tlock
        private val tlockStubSecret = ByteArray(32).also { sealIntRng.nextBytes(it) }

        // User tokens
        private lateinit var ownerToken: String
        private lateinit var executor1Token: String
        private lateinit var executor1Id: String
        private lateinit var executor2Token: String
        private lateinit var executor2Id: String

        // Bound connection ID (created in setup)
        private lateinit var boundConnId: String
        // Nomination IDs (for Shamir test)
        private lateinit var nom1Id: String
        private lateinit var nom2Id: String

        // Capsule IDs created during tests (shared across ordered test steps)
        private var plainCapsuleId: String = ""
        private var tlockCapsuleId: String = ""
        private var shamirCapsuleId: String = ""
        private var postCapsuleId: String  = ""
        private var alreadySealedCapsuleId: String = ""

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

            val jdbcUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_seal"
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
            keyRepo  = PostgresKeyRepository(dataSource)

            val tlockProvider = StubTimeLockProvider(tlockStubSecret)
            val localFileStore = LocalFileStore(Files.createTempDirectory("heirlooms-seal-int-"))
            val rawApp = buildApp(
                localFileStore,
                database,
                authSecret = serverSecret,
                timeLockProvider = tlockProvider,
            )
            app = sessionAuthFilter(authRepo).then(rawApp)

            ownerToken = setupFoundingUserSession()

            // Executor 1
            val inv1 = generateInvite(ownerToken)
            val (tok1, id1) = registerUser("seal-exec1-${UUID.randomUUID()}", inv1)
            executor1Token = tok1; executor1Id = id1

            // Executor 2
            val inv2 = generateInvite(ownerToken)
            val (tok2, id2) = registerUser("seal-exec2-${UUID.randomUUID()}", inv2)
            executor2Token = tok2; executor2Id = id2
        }

        // ── Auth helpers (copied from ExecutorShareIntegrationTest pattern) ────

        private fun setupFoundingUserSession(deviceId: String = "seal-int-owner-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey      = ByteArray(32) { 99 }
            val authSalt     = ByteArray(16) { 11 }
            val authVerifier = sha256SealInt(authKey)
            val pubkey       = ByteArray(65) { 7 }
            val wrapKey      = ByteArray(64) { 8 }

            keyRepo.insertWrappedKey(
                WrappedKeyRecord(
                    id = UUID.randomUUID(), deviceId = deviceId, deviceLabel = "Owner", deviceKind = "android",
                    pubkeyFormat = "p256-spki", pubkey = pubkey, wrappedMasterKey = wrapKey,
                    wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1", createdAt = Instant.now(), lastUsedAt = Instant.now(), retiredAt = null,
                ),
                userId = FOUNDING_USER_ID,
            )
            val body = """{"username":"bret","device_id":"$deviceId","auth_salt":"${sealIntUrlEnc.encodeToString(authSalt)}","auth_verifier":"${sealIntUrlEnc.encodeToString(authVerifier)}"}"""
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing: ${resp.bodyString()}")
            return sealIntMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun generateInvite(token: String): String {
            val resp = get("/api/auth/invites", token)
            assertEquals(OK, resp.status)
            return sealIntMapper.readTree(resp.bodyString()).get("token").asText()
        }

        private fun registerUser(username: String, invite: String): Pair<String, String> {
            val authKey  = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val body = """
              {"invite_token":"$invite","username":"$username","display_name":"Executor","auth_salt":"${sealIntUrlEnc.encodeToString(authSalt)}","auth_verifier":"${sealIntUrlEnc.encodeToString(sha256SealInt(authKey))}","wrapped_master_key":"${Base64.getEncoder().encodeToString(ByteArray(64) { 5 })}","wrap_format":"p256-ecdh-hkdf-aes256gcm-v1","pubkey_format":"p256-spki","pubkey":"${Base64.getEncoder().encodeToString(ByteArray(65) { 6 })}","device_id":"${UUID.randomUUID()}","device_label":"Exec","device_kind":"android"}
            """.trimIndent()
            val resp = post("/api/auth/register", body)
            assertEquals(CREATED, resp.status, "register: ${resp.bodyString()}")
            val n = sealIntMapper.readTree(resp.bodyString())
            return Pair(n.get("session_token").asText(), n.get("user_id").asText())
        }

        fun post(path: String, body: String, token: String? = null): Response {
            val req = Request(POST, path).header("Content-Type", "application/json").body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun put(path: String, body: String, token: String? = null): Response {
            val req = Request(PUT, path).header("Content-Type", "application/json").body(body)
            return app(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun get(path: String, token: String? = null): Response =
            app(Request(GET, path).let { if (token != null) it.header("X-Api-Key", token) else it })

        // ── Envelope builder ──────────────────────────────────────────────────

        private fun validEnvelope(): String {
            val algId   = AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1.toByteArray(Charsets.UTF_8)
            val ephPub  = byteArrayOf(0x04) + ByteArray(64).also { sealIntRng.nextBytes(it) }
            val nonce   = ByteArray(12).also { sealIntRng.nextBytes(it) }
            val cipher  = ByteArray(32).also { sealIntRng.nextBytes(it) }
            val tag     = ByteArray(16).also { sealIntRng.nextBytes(it) }
            val blob    = byteArrayOf(0x01, algId.size.toByte()) + algId + ephPub + nonce + cipher + tag
            return sealIntUrlEnc.encodeToString(blob)
        }

        private fun createCapsule(token: String, unlockAt: String = "2050-01-01T00:00:00Z"): String {
            val body = """{"shape":"open","unlock_at":"$unlockAt","recipients":["test@example.com"],"upload_ids":[]}"""
            val resp = post("/api/capsules", body, token)
            assertEquals(CREATED, resp.status, "Create capsule: ${resp.bodyString()}")
            return sealIntMapper.readTree(resp.bodyString()).get("id").asText()
        }

        private fun createBoundConnection(ownerToken: String, contactUserId: String): String {
            val body = """{"display_name":"Executor","contact_user_id":"$contactUserId","roles":["executor"]}"""
            val resp = post("/api/connections", body, ownerToken)
            assertEquals(CREATED, resp.status, "Create connection: ${resp.bodyString()}")
            return sealIntMapper.readTree(resp.bodyString()).get("connection").get("id").asText()
        }
    }

    // ─── Setup: connections and nominations ───────────────────────────────────

    @Test
    @Order(1)
    fun `setup1 create bound connections and nominations for Shamir tests`() {
        boundConnId = createBoundConnection(ownerToken, executor1Id)
        val conn2Id = createBoundConnection(ownerToken, executor2Id)

        // Nominate both executors
        val n1 = sealIntMapper.readTree(
            post("/api/executor-nominations", """{"connection_id":"$boundConnId"}""", ownerToken).bodyString()
        ).get("nomination").get("id").asText()
        nom1Id = n1

        val n2 = sealIntMapper.readTree(
            post("/api/executor-nominations", """{"connection_id":"$conn2Id"}""", ownerToken).bodyString()
        ).get("nomination").get("id").asText()
        nom2Id = n2

        // Both accept
        post("/api/executor-nominations/$nom1Id/accept", "", executor1Token)
        post("/api/executor-nominations/$nom2Id/accept", "", executor2Token)
    }

    // ─── Happy path: plain ECDH seal ──────────────────────────────────────────

    @Test
    @Order(10)
    fun `happy path plain ECDH seal - asserts shape sealed and sealed_at`() {
        plainCapsuleId = createCapsule(ownerToken)
        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
              }]
            }
        """.trimIndent()

        val resp = put("/api/capsules/$plainCapsuleId/seal", body, ownerToken)
        assertEquals(OK, resp.status, "Plain ECDH seal: ${resp.bodyString()}")

        val node = sealIntMapper.readTree(resp.bodyString())
        assertEquals(plainCapsuleId, node.get("capsule_id").asText())
        assertEquals("sealed", node.get("shape").asText())
        assertEquals("sealed", node.get("state").asText())
        assertNotNull(node.get("sealed_at"))
        assertTrue(node.get("sealed_at").asText().isNotEmpty())

        // Verify DB columns: wrapped_capsule_key and sealed_at were written
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT wrapped_capsule_key, shape, sealed_at FROM capsules WHERE id = ?::uuid").use { stmt ->
                stmt.setString(1, plainCapsuleId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertTrue(rs.getBytes("wrapped_capsule_key") != null, "wrapped_capsule_key must be non-null")
                assertEquals("sealed", rs.getString("shape"))
                assertNotNull(rs.getTimestamp("sealed_at"))
            }
        }
    }

    // ─── Happy path: tlock seal ────────────────────────────────────────────────

    @Test
    @Order(20)
    fun `happy path tlock seal - tlock columns written, digest correct`() {
        tlockCapsuleId = createCapsule(ownerToken, unlockAt = "2050-06-01T00:00:00Z")

        val provider = StubTimeLockProvider(tlockStubSecret)
        val dekClient = ByteArray(32).also { sealIntRng.nextBytes(it) }
        val unlockInstant = Instant.parse("2050-06-01T00:00:00Z")
        val ct = provider.seal(dekClient, unlockInstant)

        // SECURITY: dekTlock is key material — not logged
        val dekTlock  = ByteArray(32).also { sealIntRng.nextBytes(it) }
        val keyDigest = sha256SealInt(dekTlock)

        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1",
                "wrapped_blinding_mask": "${validEnvelope()}"
              }],
              "tlock": {
                "round": ${ct.round},
                "chain_id": "${ct.chainId}",
                "wrapped_key": "${sealIntUrlEnc.encodeToString(ct.blob)}",
                "dek_tlock": "${sealIntUrlEnc.encodeToString(dekTlock)}",
                "tlock_key_digest": "${sealIntUrlEnc.encodeToString(keyDigest)}"
              }
            }
        """.trimIndent()

        val resp = put("/api/capsules/$tlockCapsuleId/seal", body, ownerToken)
        assertEquals(OK, resp.status, "tlock seal: ${resp.bodyString()}")

        val node = sealIntMapper.readTree(resp.bodyString())
        assertEquals("sealed", node.get("shape").asText())

        // Verify DB: tlock columns written and digest is correct
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT tlock_round, tlock_chain_id, tlock_wrapped_key, tlock_dek_tlock, tlock_key_digest FROM capsules WHERE id = ?::uuid"
            ).use { stmt ->
                stmt.setString(1, tlockCapsuleId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(ct.round, rs.getLong("tlock_round"))
                assertEquals(TimeLockCiphertext.STUB_CHAIN_ID, rs.getString("tlock_chain_id"))
                assertNotNull(rs.getBytes("tlock_wrapped_key"))
                // Verify digest without logging dek_tlock value
                val storedDekTlock  = rs.getBytes("tlock_dek_tlock")
                val storedKeyDigest = rs.getBytes("tlock_key_digest")
                assertNotNull(storedDekTlock,  "tlock_dek_tlock must be stored")
                assertNotNull(storedKeyDigest, "tlock_key_digest must be stored")
                // Verify invariant I-5: SHA-256(stored_dek_tlock) == stored_key_digest
                val computedDigest = sha256SealInt(storedDekTlock)
                assertTrue(
                    computedDigest.contentEquals(storedKeyDigest),
                    "I-5 violated: SHA-256(tlock_dek_tlock) != tlock_key_digest",
                )
            }
        }
    }

    // ─── Happy path: Shamir seal ──────────────────────────────────────────────

    @Test
    @Order(30)
    fun `happy path Shamir seal - shamir columns written and accepted nominations validated`() {
        shamirCapsuleId = createCapsule(ownerToken)
        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
              }],
              "shamir": {
                "threshold": 2,
                "total_shares": 2
              }
            }
        """.trimIndent()

        val resp = put("/api/capsules/$shamirCapsuleId/seal", body, ownerToken)
        assertEquals(OK, resp.status, "Shamir seal: ${resp.bodyString()}")
        assertEquals("sealed", sealIntMapper.readTree(resp.bodyString()).get("shape").asText())

        // Verify DB: shamir columns
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT shamir_threshold, shamir_total_shares FROM capsules WHERE id = ?::uuid"
            ).use { stmt ->
                stmt.setString(1, shamirCapsuleId)
                val rs = stmt.executeQuery()
                assertTrue(rs.next())
                assertEquals(2, rs.getShort("shamir_threshold").toInt())
                assertEquals(2, rs.getShort("shamir_total_shares").toInt())
            }
        }
    }

    // ─── POST verb works ──────────────────────────────────────────────────────

    @Test
    @Order(40)
    fun `POST verb seal works same as PUT`() {
        postCapsuleId = createCapsule(ownerToken)
        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
              }]
            }
        """.trimIndent()

        val resp = post("/api/capsules/$postCapsuleId/seal", body, ownerToken)
        assertEquals(OK, resp.status, "POST seal: ${resp.bodyString()}")
        assertEquals("sealed", sealIntMapper.readTree(resp.bodyString()).get("shape").asText())
    }

    // ─── Error: seal already-sealed capsule ───────────────────────────────────

    @Test
    @Order(50)
    fun `error path attempt to seal already-sealed capsule returns HTTP 409`() {
        // Use the plainCapsuleId which was sealed in step 10
        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
              }]
            }
        """.trimIndent()

        val resp = put("/api/capsules/$plainCapsuleId/seal", body, ownerToken)
        assertEquals(CONFLICT, resp.status, "Re-seal should be 409: ${resp.bodyString()}")
        assertEquals("capsule_not_sealable", sealIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── Error: invalid envelope ──────────────────────────────────────────────

    @Test
    @Order(60)
    fun `error path invalid envelope returns HTTP 422`() {
        val capsuleId = createCapsule(ownerToken)
        val invalidEnvBlob = sealIntUrlEnc.encodeToString(ByteArray(10) { it.toByte() })
        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "$invalidEnvBlob",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
              }]
            }
        """.trimIndent()

        val resp = put("/api/capsules/$capsuleId/seal", body, ownerToken)
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Invalid envelope: ${resp.bodyString()}")
        assertEquals("invalid_wrapped_capsule_key", sealIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── Error: tlock fields sent but provider is disabled ────────────────────
    // (The main app uses the stub provider; we test this at the unit test level.
    //  Here we verify that sending tlock fields with a valid stub provider succeeds,
    //  which confirms the provider wiring is correct.)

    @Test
    @Order(70)
    fun `error path tlock blob structurally invalid returns HTTP 422`() {
        val capsuleId = createCapsule(ownerToken)
        val dekTlk    = ByteArray(32).also { sealIntRng.nextBytes(it) }
        val digest    = sha256SealInt(dekTlk)
        // Pass a blob that is too short for StubTimeLockProvider.validate()
        val badBlob   = sealIntUrlEnc.encodeToString(ByteArray(10))

        val body = """
            {
              "recipient_keys": [{
                "connection_id": "$boundConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1",
                "wrapped_blinding_mask": "${validEnvelope()}"
              }],
              "tlock": {
                "round": 100,
                "chain_id": "${TimeLockCiphertext.STUB_CHAIN_ID}",
                "wrapped_key": "$badBlob",
                "dek_tlock": "${sealIntUrlEnc.encodeToString(dekTlk)}",
                "tlock_key_digest": "${sealIntUrlEnc.encodeToString(digest)}"
              }
            }
        """.trimIndent()

        val resp = put("/api/capsules/$capsuleId/seal", body, ownerToken)
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Bad tlock blob: ${resp.bodyString()}")
        assertEquals("tlock_blob_invalid", sealIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── Error: deferred-pubkey connection (no contact_user_id) ──────────────

    @Test
    @Order(80)
    fun `error path deferred-pubkey connection with no fallback returns HTTP 422`() {
        // Create a connection with no contact_user_id (deferred-pubkey)
        val body1 = """{"display_name":"Deferred","email":"nobody@test.example","roles":["recipient"]}"""
        val connResp = post("/api/connections", body1, ownerToken)
        assertEquals(CREATED, connResp.status, "Create deferred conn: ${connResp.bodyString()}")
        val deferredConnId = sealIntMapper.readTree(connResp.bodyString()).get("connection").get("id").asText()

        val capsuleId = createCapsule(ownerToken)
        val sealBody = """
            {
              "recipient_keys": [{
                "connection_id": "$deferredConnId",
                "wrapped_capsule_key": "${validEnvelope()}",
                "capsule_key_format": "capsule-ecdh-aes256gcm-v1"
              }]
            }
        """.trimIndent()

        val resp = put("/api/capsules/$capsuleId/seal", sealBody, ownerToken)
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Deferred conn: ${resp.bodyString()}")
        // Step [6] fires: "invalid_connection_id" (deferred = not bound)
        assertEquals("invalid_connection_id", sealIntMapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── Verify tlock_dek_tlock does not appear in any log sink ───────────────
    // Note: we verify the invariant (I-5) via DB assertion in step 20.
    // The logging prohibition (I-4) is enforced at the code level; no assertions
    // on log output are possible in this test framework without a custom appender.
    // The SealCapsuleService and SealRepository explicitly avoid logging dek_tlock.
}
