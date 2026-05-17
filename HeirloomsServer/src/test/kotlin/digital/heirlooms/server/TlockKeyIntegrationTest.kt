package digital.heirlooms.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.crypto.tlock.StubTimeLockProvider
import digital.heirlooms.server.crypto.tlock.TimeLockCiphertext
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
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

private val tlockKeyMapper = ObjectMapper()
private val tlockKeyUrlEnc = Base64.getUrlEncoder().withoutPadding()
private val tlockKeyUrlDec = Base64.getUrlDecoder()
private val tlockKeyRng    = SecureRandom()

private fun sha256TlockKey(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

/**
 * Integration tests for DEV-006:
 * - GET /api/capsules/:id/tlock-key (Wave 6)
 * - GET /api/capsules/:id (amended, Wave 7) — M11 fields
 * - GET /api/capsule-recipient-keys/:capsuleId (Wave 7)
 *
 * Uses Testcontainers (PostgreSQL) + LocalFileStore + StubTimeLockProvider.
 *
 * LOGGING PROHIBITION: dek_tlock values are asserted present but never logged.
 *
 * Test ordering: capsule sealed in step 10, subsequent steps depend on that state.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TlockKeyIntegrationTest {

    companion object {
        private val postgres = GenericContainer<Nothing>("postgres:16").apply {
            withExposedPorts(5432)
            withEnv("POSTGRES_DB", "heirlooms_tlock")
            withEnv("POSTGRES_USER", "test")
            withEnv("POSTGRES_PASSWORD", "test")
            waitingFor(Wait.forListeningPort())
        }

        private lateinit var dataSource: DataSource
        private lateinit var database: Database
        private lateinit var authRepo: PostgresAuthRepository
        private lateinit var keyRepo: PostgresKeyRepository

        // App with past clock — all tlock rounds already published
        private lateinit var appPast: org.http4k.core.HttpHandler
        // App with future clock — tlock rounds not published yet
        private lateinit var appFuture: org.http4k.core.HttpHandler

        private val serverSecret = ByteArray(32) { it.toByte() }
        private val tlockStubSecret = ByteArray(32).also { tlockKeyRng.nextBytes(it) }

        // Owner (founding user) and a recipient user
        private lateinit var ownerToken: String
        private lateinit var recipientToken: String
        private lateinit var recipientUserId: String
        private lateinit var boundConnId: String

        // The sealed tlock capsule created in step 10
        private var tlockCapsuleId: String = ""
        // dek_tlock bytes stored for assertions (never logged)
        private lateinit var dekTlock: ByteArray

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

            val jdbcUrl =
                "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/heirlooms_tlock"
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

            // Past clock: all rounds already published (epoch 2099)
            val pastClock  = Clock.fixed(Instant.parse("2099-06-01T00:00:00Z"), ZoneOffset.UTC)
            val futureClock = Clock.fixed(Instant.parse("2001-01-01T00:00:00Z"), ZoneOffset.UTC)

            val localFs = LocalFileStore(Files.createTempDirectory("heirlooms-tlock-int-"))

            val rawPast = buildApp(
                localFs, database,
                authSecret = serverSecret,
                timeLockProvider = StubTimeLockProvider(tlockStubSecret, pastClock),
            )
            val rawFuture = buildApp(
                localFs, database,
                authSecret = serverSecret,
                timeLockProvider = StubTimeLockProvider(tlockStubSecret, futureClock),
            )

            val authFilter = sessionAuthFilter(authRepo)
            appPast   = authFilter.then(rawPast)
            appFuture = authFilter.then(rawFuture)

            ownerToken = setupFoundingUserSession()

            // Create a recipient user
            val invite = generateInvite(ownerToken)
            val (rTok, rId) = registerUser("tlock-recipient-${UUID.randomUUID()}", invite)
            recipientToken = rTok; recipientUserId = rId

            // Create a bound connection from owner to recipient
            boundConnId = createBoundConnection(ownerToken, recipientUserId)
        }

        // ── Auth helpers ──────────────────────────────────────────────────────

        private fun setupFoundingUserSession(deviceId: String = "tlock-int-owner-${UUID.randomUUID()}"): String {
            authRepo.resetUserAuth(FOUNDING_USER_ID)
            val authKey      = ByteArray(32) { 99 }
            val authSalt     = ByteArray(16) { 11 }
            val authVerifier = sha256TlockKey(authKey)
            val pubkey       = ByteArray(65) { 7 }
            val wrapKey      = ByteArray(64) { 8 }

            keyRepo.insertWrappedKey(
                WrappedKeyRecord(
                    id = UUID.randomUUID(), deviceId = deviceId, deviceLabel = "Owner",
                    deviceKind = "android", pubkeyFormat = "p256-spki", pubkey = pubkey,
                    wrappedMasterKey = wrapKey, wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
                    createdAt = Instant.now(), lastUsedAt = Instant.now(), retiredAt = null,
                ),
                userId = FOUNDING_USER_ID,
            )
            val body = """{"username":"bret","device_id":"$deviceId","auth_salt":"${tlockKeyUrlEnc.encodeToString(authSalt)}","auth_verifier":"${tlockKeyUrlEnc.encodeToString(authVerifier)}"}"""
            val resp = post("/api/auth/setup-existing", body)
            assertEquals(OK, resp.status, "setup-existing: ${resp.bodyString()}")
            return tlockKeyMapper.readTree(resp.bodyString()).get("session_token").asText()
        }

        private fun generateInvite(token: String): String {
            val resp = get("/api/auth/invites", token)
            assertEquals(OK, resp.status)
            return tlockKeyMapper.readTree(resp.bodyString()).get("token").asText()
        }

        private fun registerUser(username: String, invite: String): Pair<String, String> {
            val authKey  = ByteArray(32) { (username.length + it).toByte() }
            val authSalt = ByteArray(16) { it.toByte() }
            val body = """
              {"invite_token":"$invite","username":"$username","display_name":"Recipient",
               "auth_salt":"${tlockKeyUrlEnc.encodeToString(authSalt)}",
               "auth_verifier":"${tlockKeyUrlEnc.encodeToString(sha256TlockKey(authKey))}",
               "wrapped_master_key":"${Base64.getEncoder().encodeToString(ByteArray(64) { 5 })}",
               "wrap_format":"p256-ecdh-hkdf-aes256gcm-v1","pubkey_format":"p256-spki",
               "pubkey":"${Base64.getEncoder().encodeToString(ByteArray(65) { 6 })}",
               "device_id":"${UUID.randomUUID()}","device_label":"Phone","device_kind":"android"}
            """.trimIndent().replace("\n", "")
            val resp = post("/api/auth/register", body)
            assertEquals(CREATED, resp.status, "register: ${resp.bodyString()}")
            val n = tlockKeyMapper.readTree(resp.bodyString())
            return Pair(n.get("session_token").asText(), n.get("user_id").asText())
        }

        private fun createBoundConnection(ownerToken: String, contactUserId: String): String {
            val body = """{"display_name":"Recipient","contact_user_id":"$contactUserId","roles":["recipient"]}"""
            val resp = post("/api/connections", body, ownerToken)
            assertEquals(CREATED, resp.status, "Create connection: ${resp.bodyString()}")
            return tlockKeyMapper.readTree(resp.bodyString()).get("connection").get("id").asText()
        }

        fun post(path: String, body: String, token: String? = null): Response {
            val req = Request(POST, path).header("Content-Type", "application/json").body(body)
            return appPast(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun put(path: String, body: String, token: String? = null): Response {
            val req = Request(PUT, path).header("Content-Type", "application/json").body(body)
            return appPast(if (token != null) req.header("X-Api-Key", token) else req)
        }

        fun get(path: String, token: String? = null, useFutureClock: Boolean = false): Response {
            val handler = if (useFutureClock) appFuture else appPast
            val req = Request(GET, path)
            return handler(if (token != null) req.header("X-Api-Key", token) else req)
        }

        private fun validEnvelope(): String {
            val algId  = AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1.toByteArray(Charsets.UTF_8)
            val ephPub = byteArrayOf(0x04) + ByteArray(64).also { tlockKeyRng.nextBytes(it) }
            val nonce  = ByteArray(12).also { tlockKeyRng.nextBytes(it) }
            val cipher = ByteArray(32).also { tlockKeyRng.nextBytes(it) }
            val tag    = ByteArray(16).also { tlockKeyRng.nextBytes(it) }
            val blob   = byteArrayOf(0x01, algId.size.toByte()) + algId + ephPub + nonce + cipher + tag
            return tlockKeyUrlEnc.encodeToString(blob)
        }

        private fun createCapsule(token: String, unlockAt: String): String {
            val body = """{"shape":"open","unlock_at":"$unlockAt","recipients":["test@example.com"],"upload_ids":[]}"""
            val resp = post("/api/capsules", body, token)
            assertEquals(CREATED, resp.status, "Create capsule: ${resp.bodyString()}")
            return tlockKeyMapper.readTree(resp.bodyString()).get("id").asText()
        }
    }

    // ─── Step 10: seal a tlock capsule with a past unlock_at ──────────────────

    @Test
    @Order(10)
    fun `setup - seal a tlock capsule with past unlock_at`() {
        // Use an unlock_at after the stub genesis (2023-11-14 = epoch 1700000000)
        // so the derived round publish time is close to unlock_at (within 1 hour check).
        // Using 2024-01-01 — well after genesis; the round is derived from this date.
        val unlockAt = "2024-01-01T00:00:00Z"
        tlockCapsuleId = createCapsule(ownerToken, unlockAt)

        val provider = StubTimeLockProvider(tlockStubSecret)
        val unlockInstant = Instant.parse("2024-01-01T00:00:00Z")
        val ct = provider.seal(ByteArray(32).also { tlockKeyRng.nextBytes(it) }, unlockInstant)

        // dekTlock: random 32 bytes (SECURITY: never logged)
        dekTlock = ByteArray(32).also { tlockKeyRng.nextBytes(it) }
        val keyDigest = sha256TlockKey(dekTlock)

        val sealBody = """
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
                "wrapped_key": "${tlockKeyUrlEnc.encodeToString(ct.blob)}",
                "dek_tlock": "${tlockKeyUrlEnc.encodeToString(dekTlock)}",
                "tlock_key_digest": "${tlockKeyUrlEnc.encodeToString(keyDigest)}"
              }
            }
        """.trimIndent()

        val resp = put("/api/capsules/$tlockCapsuleId/seal", sealBody, ownerToken)
        assertEquals(OK, resp.status, "tlock seal: ${resp.bodyString()}")
        assertEquals("sealed", tlockKeyMapper.readTree(resp.bodyString()).get("shape").asText())
    }

    // ─── Step 20: call /tlock-key before unlock_at (future clock) → 202 ───────

    @Test
    @Order(20)
    fun `tlock-key before unlock_at returns 202 with gate_not_open`() {
        // Use future clock app — the round won't be published yet
        // Note: unlock_at was 2020-01-01 (past), but provider clock is 2001 so round unpublished
        val req = Request(GET, "/api/capsules/$tlockCapsuleId/tlock-key")
            .header("X-Api-Key", recipientToken)
        val resp = appFuture(req)

        assertEquals(ACCEPTED, resp.status, "Expected 202 before gate open: ${resp.bodyString()}")
        val body = tlockKeyMapper.readTree(resp.bodyString())
        assertEquals("tlock_gate_not_open", body.get("error").asText())
        assertNotNull(body.get("retry_after_seconds"))
    }

    // ─── Step 30: call /tlock-key after unlock_at → 200 with dek_tlock ────────

    @Test
    @Order(30)
    fun `tlock-key after both gates open returns 200 with 32-byte dek_tlock`() {
        // appPast uses clock 2099 — unlock_at (2020) passed, round published
        val req = Request(GET, "/api/capsules/$tlockCapsuleId/tlock-key")
            .header("X-Api-Key", recipientToken)
        val resp = appPast(req)

        assertEquals(OK, resp.status, "Expected 200 gate open: ${resp.bodyString()}")

        val body = tlockKeyMapper.readTree(resp.bodyString())
        val dekTlockB64 = body.get("dek_tlock").asText()
        assertNotNull(dekTlockB64)

        // Verify dek_tlock decodes to exactly 32 bytes
        val decoded = tlockKeyUrlDec.decode(dekTlockB64)
        assertEquals(32, decoded.size, "dek_tlock must be 32 bytes after base64url decode")

        // chain_id and round present
        assertEquals(TimeLockCiphertext.STUB_CHAIN_ID, body.get("chain_id").asText())
        assertTrue(body.get("round").asLong() > 0)
    }

    // ─── Step 35: assert dek_tlock value does not appear in log output ─────────

    @Test
    @Order(35)
    fun `tlock-key success response body dek_tlock does not appear in logs`() {
        val loggerName = "digital.heirlooms.server.service.capsule.TlockKeyService"
        val slf4jLogger = LoggerFactory.getLogger(loggerName) as? Logger
            ?: return // If logback not available, skip

        val capturedEvents = mutableListOf<ILoggingEvent>()
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) { capturedEvents.add(event) }
        }
        appender.start()
        slf4jLogger.addAppender(appender)

        try {
            val req = Request(GET, "/api/capsules/$tlockCapsuleId/tlock-key")
                .header("X-Api-Key", recipientToken)
            val resp = appPast(req)
            assertEquals(OK, resp.status)

            val dekTlockB64 = tlockKeyMapper.readTree(resp.bodyString()).get("dek_tlock").asText()
            assertTrue(dekTlockB64.isNotEmpty())

            // SECURITY: verify dek_tlock never appeared in any log message
            for (event in capturedEvents) {
                val msg = event.formattedMessage
                assertFalse(
                    msg.contains(dekTlockB64),
                    "SECURITY VIOLATION: dek_tlock base64 appeared in log: $msg",
                )
            }
        } finally {
            slf4jLogger.detachAppender(appender)
            appender.stop()
        }
    }

    // ─── Step 40: GET /api/capsules/:id — M11 fields in response ──────────────

    @Test
    @Order(40)
    fun `capsule detail includes M11 fields for sealed tlock capsule`() {
        val resp = get("/api/capsules/$tlockCapsuleId", ownerToken)
        assertEquals(OK, resp.status, "Get capsule: ${resp.bodyString()}")

        val body = tlockKeyMapper.readTree(resp.bodyString())
        // M11 fields present
        assertNotNull(body.get("wrapped_capsule_key"), "wrapped_capsule_key must be present")
        assertEquals("capsule-ecdh-aes256gcm-v1", body.get("capsule_key_format").asText())
        assertNotNull(body.get("tlock_round"))
        assertNotNull(body.get("tlock_chain_id"))
        assertNotNull(body.get("tlock_wrapped_key"))
        assertNotNull(body.get("tlock_key_digest"))

        // tlock_dek_tlock must NOT be present (I-4)
        assertTrue(
            body.get("tlock_dek_tlock") == null || body.get("tlock_dek_tlock").isNull,
            "tlock_dek_tlock must NEVER appear in capsule detail response",
        )
    }

    // ─── Step 50: GET /api/capsule-recipient-keys — owner receives all rows ───

    @Test
    @Order(50)
    fun `owner receives all recipient_keys rows`() {
        val resp = get("/api/capsule-recipient-keys/$tlockCapsuleId", ownerToken)
        assertEquals(OK, resp.status, "Recipient keys as owner: ${resp.bodyString()}")

        val body = tlockKeyMapper.readTree(resp.bodyString())
        val rows = body.get("recipient_keys")
        assertNotNull(rows)
        assertTrue(rows.size() >= 1, "Owner should see at least 1 recipient row")

        val row = rows[0]
        assertNotNull(row.get("connection_id"))
        assertNotNull(row.get("wrapped_capsule_key"))
        // tlock capsule: wrapped_blinding_mask should be present (not null)
        assertFalse(
            row.get("wrapped_blinding_mask")?.isNull ?: true,
            "tlock capsule recipient should have wrapped_blinding_mask",
        )
    }

    // ─── Step 60: GET /api/capsule-recipient-keys — non-recipient → 403 ───────

    @Test
    @Order(60)
    fun `non-recipient caller receives 403 on capsule-recipient-keys`() {
        // Create a second user who is not a recipient of this capsule
        val invite2 = generateInvite(ownerToken)
        val (otherToken, _) = registerUser("tlock-other-${UUID.randomUUID()}", invite2)

        val resp = get("/api/capsule-recipient-keys/$tlockCapsuleId", otherToken)
        assertEquals(FORBIDDEN, resp.status, "Non-recipient should get 403: ${resp.bodyString()}")
    }

    // ─── Step 70: GET /api/capsule-recipient-keys — non-existent capsule → 404

    @Test
    @Order(70)
    fun `non-existent capsule returns 404 on capsule-recipient-keys`() {
        val resp = get("/api/capsule-recipient-keys/${UUID.randomUUID()}", ownerToken)
        assertEquals(NOT_FOUND, resp.status)
    }
}
