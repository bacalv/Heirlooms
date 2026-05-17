package digital.heirlooms.server.routes.capsule

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.crypto.tlock.DisabledTimeLockProvider
import digital.heirlooms.server.crypto.tlock.StubTimeLockProvider
import digital.heirlooms.server.crypto.tlock.TimeLockCiphertext
import digital.heirlooms.server.crypto.tlock.TimeLockProvider
import digital.heirlooms.server.repository.capsule.CapsuleRecipientKeyRepository
import digital.heirlooms.server.repository.capsule.ExecutorShareRepository
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import digital.heirlooms.server.repository.capsule.SealRepository
import digital.heirlooms.server.repository.capsule.TlockKeyRepository
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.repository.connection.NominationRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.service.capsule.TlockKeyService
import digital.heirlooms.server.storage.LocalFileStore
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for GET /api/capsules/:id/tlock-key (DEV-006 Wave 6).
 *
 * Covers all 7 gate steps from ARCH-010 §5.3:
 * [1] not_a_recipient → 403
 * [2] capsule not found / not sealed tlock → 404
 * [3] tlock_not_enabled → 503
 * [4] unlock_at not passed → 202 with retry_after
 * [5] round not published → 202 with retry_after=30
 * [6] tamper detected (SHA-256 mismatch) → 500
 * [7] gate open → 200 with dek_tlock
 *
 * Plus: asserts the 200 response body does NOT appear in captured log output (I-4).
 *
 * All repository calls are mocked via MockK.
 */
class TlockKeyRouteTest {

    private val mapper = ObjectMapper()
    private val rng    = SecureRandom()
    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64dec = Base64.getUrlDecoder()

    private val tlockKeyRepo         = mockk<TlockKeyRepository>()
    private val sealRepo             = mockk<SealRepository>(relaxed = true)
    private val connectionRepo       = mockk<ConnectionRepository>(relaxed = true)
    private val nominationRepo       = mockk<NominationRepository>(relaxed = true)
    private val recipientLinkRepo    = mockk<RecipientLinkRepository>(relaxed = true)
    private val executorShareRepo    = mockk<ExecutorShareRepository>(relaxed = true)
    private val capsuleRecipientRepo = mockk<CapsuleRecipientKeyRepository>(relaxed = true)
    private val storage              = LocalFileStore(Files.createTempDirectory("tlock-key-test"))

    private val stubSecret = ByteArray(32).also { rng.nextBytes(it) }

    // Shared test IDs
    private val capsuleId    = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val callerUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    // unlock_at in the past (2020-01-01) so gate [4] passes by default
    private val pastUnlockAt = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    // unlock_at far in the future so gate [4] fails
    private val futureUnlockAt = OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    private val dekTlock   = ByteArray(32).also { rng.nextBytes(it) }
    private val keyDigest  = sha256(dekTlock)

    // Stub provider with a past unlock — round will always be published
    private val pastStubProvider = StubTimeLockProvider(
        stubSecret,
        Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    // Stub provider with a future unlock — round never published (decrypt returns null)
    private val futureStubProvider = StubTimeLockProvider(
        stubSecret,
        Clock.fixed(Instant.parse("2001-01-01T00:00:00Z"), ZoneOffset.UTC),
    )

    private lateinit var app: org.http4k.core.HttpHandler

    @BeforeEach
    fun setup() {
        io.mockk.clearMocks(tlockKeyRepo, sealRepo)

        // Default: caller IS a recipient
        every { tlockKeyRepo.isRecipient(any(), any()) } returns true
        // Default: capsule exists with past unlock_at and valid tlock fields
        every { tlockKeyRepo.loadTlockFields(any()) } returns buildTlockFields(pastUnlockAt)

        buildAppWithProvider(pastStubProvider)
    }

    private fun buildAppWithProvider(provider: TimeLockProvider) {
        app = buildApp(
            storage              = storage,
            uploadRepo           = mockk(relaxed = true),
            authRepo             = mockk(relaxed = true),
            capsuleRepo          = mockk(relaxed = true),
            plotRepo             = mockk(relaxed = true),
            flowRepo             = mockk(relaxed = true),
            itemRepo             = mockk(relaxed = true),
            memberRepo           = mockk(relaxed = true),
            keyRepo              = mockk(relaxed = true),
            socialRepo           = mockk(relaxed = true),
            blobRepo             = mockk(relaxed = true),
            diagRepo             = mockk(relaxed = true),
            sealRepo             = sealRepo,
            connectionRepo       = connectionRepo,
            nominationRepo       = nominationRepo,
            recipientLinkRepo    = recipientLinkRepo,
            executorShareRepo    = executorShareRepo,
            tlockKeyRepo         = tlockKeyRepo,
            capsuleRecipientKeyRepo = capsuleRecipientRepo,
            timeLockProvider     = provider,
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    /** Build stub tlock fields. The wrappedKey must be a valid 64-byte stub blob. */
    private fun buildTlockFields(
        unlockAt: OffsetDateTime = pastUnlockAt,
        customKeyDigest: ByteArray = keyDigest,
    ): TlockKeyRepository.TlockCapsuleFields {
        val round = StubTimeLockProvider.roundForInstant(unlockAt.toInstant())
        val ct = StubTimeLockProvider(stubSecret).seal(dekTlock, unlockAt.toInstant())
        return TlockKeyRepository.TlockCapsuleFields(
            capsuleId  = capsuleId,
            unlockAt   = unlockAt,
            dekTlock   = dekTlock,
            keyDigest  = customKeyDigest,
            chainId    = TimeLockCiphertext.STUB_CHAIN_ID,
            round      = round,
            wrappedKey = ct.blob,
        )
    }

    private fun get(path: String, userId: UUID = callerUserId): org.http4k.core.Response =
        app(
            Request(GET, path)
                .header("X-Auth-User-Id", userId.toString())
                .header("X-Auth-Device-Kind", "android"),
        )

    // ── Step [1]: not a recipient → 403 ──────────────────────────────────────

    @Test
    fun `step 1 caller not a recipient returns 403`() {
        every { tlockKeyRepo.isRecipient(capsuleId, callerUserId) } returns false

        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(FORBIDDEN, resp.status)
        assertEquals("not_a_recipient", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ── Step [2]: capsule not found → 404 ────────────────────────────────────

    @Test
    fun `step 2 capsule not found or not sealed tlock returns 404`() {
        every { tlockKeyRepo.loadTlockFields(capsuleId) } returns null

        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(NOT_FOUND, resp.status)
        assertEquals("not_found", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ── Step [3]: tlock provider disabled → 503 ──────────────────────────────

    @Test
    fun `step 3 tlock provider disabled returns 503`() {
        buildAppWithProvider(DisabledTimeLockProvider)

        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(SERVICE_UNAVAILABLE, resp.status)
        assertEquals("tlock_not_enabled", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ── Step [4]: unlock_at not passed → 202 with retry_after ────────────────

    @Test
    fun `step 4 unlock_at not passed returns 202 with retry_after`() {
        // Capsule has a future unlock_at; provider clock is in the past
        every { tlockKeyRepo.loadTlockFields(capsuleId) } returns buildTlockFields(futureUnlockAt)
        buildAppWithProvider(futureStubProvider)

        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(ACCEPTED, resp.status)
        val body = mapper.readTree(resp.bodyString())
        assertEquals("tlock_gate_not_open", body.get("error").asText())
        assertEquals("unlock_at has not passed", body.get("detail").asText())
        assertTrue(body.get("retry_after_seconds").asLong() > 0)
    }

    // ── Step [5]: round not published → 202 with retry_after=30 ──────────────

    @Test
    fun `step 5 round not yet published returns 202 with retry_after 30`() {
        // unlock_at is in the past (gate [4] passes), but provider clock is 2001 so
        // the round's epoch (≥ 2020) is in the future — decrypt() returns null
        every { tlockKeyRepo.loadTlockFields(capsuleId) } returns buildTlockFields(pastUnlockAt)
        buildAppWithProvider(futureStubProvider)

        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(ACCEPTED, resp.status)
        val body = mapper.readTree(resp.bodyString())
        assertEquals("tlock_gate_not_open", body.get("error").asText())
        assertEquals("round not yet published", body.get("detail").asText())
        assertEquals(30L, body.get("retry_after_seconds").asLong())
    }

    // ── Step [6]: tamper detected → 500 ──────────────────────────────────────

    @Test
    fun `step 6 tamper detected SHA-256 mismatch returns 500`() {
        // Provide a wrong key_digest (all zeros) so SHA-256(dekTlock) != keyDigest
        val wrongDigest = ByteArray(32) // all zeros
        every { tlockKeyRepo.loadTlockFields(capsuleId) } returns buildTlockFields(
            unlockAt       = pastUnlockAt,
            customKeyDigest = wrongDigest,
        )
        buildAppWithProvider(pastStubProvider)

        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(INTERNAL_SERVER_ERROR, resp.status)
    }

    // ── Step [7]: gate open → 200 with correct body ───────────────────────────

    @Test
    fun `step 7 gate open returns 200 with dek_tlock chain_id and round`() {
        val resp = get("/api/capsules/$capsuleId/tlock-key")
        assertEquals(OK, resp.status)

        val body = mapper.readTree(resp.bodyString())
        val dekTlockB64 = body.get("dek_tlock").asText()
        assertNotNull(dekTlockB64)
        assertTrue(dekTlockB64.isNotEmpty())

        // Verify dek_tlock decodes to 32 bytes
        val decoded = b64dec.decode(dekTlockB64)
        assertEquals(32, decoded.size)

        // chain_id and round present
        val chainId = body.get("chain_id").asText()
        assertEquals(TimeLockCiphertext.STUB_CHAIN_ID, chainId)
        assertTrue(body.get("round").asLong() > 0)
    }

    // ── Logging prohibition: response body MUST NOT appear in logs (I-4) ──────

    @Test
    fun `logging prohibition - response body dek_tlock does not appear in log output`() {
        // Install a capturing log appender on the TlockKeyService logger
        val loggerName = "digital.heirlooms.server.service.capsule.TlockKeyService"
        val slf4jLogger = LoggerFactory.getLogger(loggerName) as? Logger
            ?: error("Cannot cast logger to Logback Logger for test")

        val capturedEvents = mutableListOf<ILoggingEvent>()
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                capturedEvents.add(event)
            }
        }
        appender.start()
        slf4jLogger.addAppender(appender)

        try {
            val resp = get("/api/capsules/$capsuleId/tlock-key")
            assertEquals(OK, resp.status)

            // Get the actual dek_tlock value from the response body
            val dekTlockB64 = mapper.readTree(resp.bodyString()).get("dek_tlock").asText()
            assertTrue(dekTlockB64.isNotEmpty(), "Response must contain dek_tlock")

            // Assert the dek_tlock value does NOT appear in any captured log message
            for (event in capturedEvents) {
                val msg = event.formattedMessage
                assertTrue(
                    !msg.contains(dekTlockB64),
                    "SECURITY VIOLATION: dek_tlock value appeared in log: $msg",
                )
                // Also check raw bytes don't appear as hex
                val dekTlockHex = dekTlock.joinToString("") { "%02x".format(it) }
                assertTrue(
                    !msg.contains(dekTlockHex, ignoreCase = true),
                    "SECURITY VIOLATION: dek_tlock hex appeared in log: $msg",
                )
            }
        } finally {
            slf4jLogger.detachAppender(appender)
            appender.stop()
        }
    }
}
