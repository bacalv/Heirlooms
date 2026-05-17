package digital.heirlooms.server.routes.capsule

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.crypto.tlock.DisabledTimeLockProvider
import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleRecord
import digital.heirlooms.server.domain.capsule.CapsuleShape
import digital.heirlooms.server.domain.capsule.CapsuleState
import digital.heirlooms.server.repository.capsule.CapsuleRecipientKeyRepository
import digital.heirlooms.server.repository.capsule.CapsuleRepository
import digital.heirlooms.server.repository.capsule.ExecutorShareRepository
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import digital.heirlooms.server.repository.capsule.SealRepository
import digital.heirlooms.server.repository.capsule.TlockKeyRepository
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.repository.connection.NominationRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.storage.LocalFileStore
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for:
 * 1. GET /api/capsules/:id — M11 fields in the amended detail response (Wave 7).
 * 2. GET /api/capsule-recipient-keys/:capsuleId — new endpoint (Wave 7).
 *
 * All repository calls are mocked via MockK.
 */
class CapsuleRecipientKeysRouteTest {

    private val mapper = ObjectMapper()
    private val rng    = SecureRandom()
    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64dec = Base64.getUrlDecoder()

    private val sealRepo             = mockk<SealRepository>(relaxed = true)
    private val capsuleRepo          = mockk<CapsuleRepository>(relaxed = true)
    private val capsuleRecipientRepo = mockk<CapsuleRecipientKeyRepository>()
    private val tlockKeyRepo         = mockk<TlockKeyRepository>(relaxed = true)
    private val connectionRepo       = mockk<ConnectionRepository>(relaxed = true)
    private val nominationRepo       = mockk<NominationRepository>(relaxed = true)
    private val recipientLinkRepo    = mockk<RecipientLinkRepository>(relaxed = true)
    private val executorShareRepo    = mockk<ExecutorShareRepository>(relaxed = true)
    private val storage              = LocalFileStore(Files.createTempDirectory("crk-route-test"))

    private val ownerId     = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val recipientId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val otherId     = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val capsuleId   = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val connId1     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val connId2     = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    private val wrappedCapsuleKey1 = ByteArray(48).also { rng.nextBytes(it) }
    private val wrappedCapsuleKey2 = ByteArray(48).also { rng.nextBytes(it) }
    private val wrappedBlindingMask = ByteArray(48).also { rng.nextBytes(it) }

    private val wrappedCapsuleKeyDb = ByteArray(80).also { rng.nextBytes(it) }
    private val tlockWrappedKey     = ByteArray(64).also { rng.nextBytes(it) }
    private val tlockKeyDigest      = ByteArray(32).also { rng.nextBytes(it) }

    private lateinit var app: org.http4k.core.HttpHandler

    @BeforeEach
    fun setup() {
        io.mockk.clearMocks(capsuleRepo, capsuleRecipientRepo, tlockKeyRepo)

        app = buildApp(
            storage              = storage,
            uploadRepo           = mockk(relaxed = true),
            authRepo             = mockk(relaxed = true),
            capsuleRepo          = capsuleRepo,
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
            timeLockProvider     = DisabledTimeLockProvider,
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun get(path: String, userId: UUID = ownerId): org.http4k.core.Response =
        app(
            Request(GET, path)
                .header("X-Auth-User-Id", userId.toString())
                .header("X-Auth-Device-Kind", "android"),
        )

    private fun buildSealedCapsuleRecord(
        includeM11: Boolean = true,
        includeTlock: Boolean = true,
        includeShamir: Boolean = false,
    ): CapsuleRecord = CapsuleRecord(
        id            = capsuleId,
        createdAt     = Instant.parse("2024-01-01T00:00:00Z"),
        updatedAt     = Instant.parse("2024-01-01T00:00:00Z"),
        createdByUser = "api-user",
        shape         = CapsuleShape.SEALED,
        state         = CapsuleState.SEALED,
        unlockAt      = OffsetDateTime.of(2050, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        cancelledAt   = null,
        deliveredAt   = null,
        wrappedCapsuleKey = if (includeM11) wrappedCapsuleKeyDb else null,
        capsuleKeyFormat  = if (includeM11) "capsule-ecdh-aes256gcm-v1" else null,
        tlockRound        = if (includeTlock && includeM11) 12345L else null,
        tlockChainId      = if (includeTlock && includeM11) "stub-chain-abc" else null,
        tlockWrappedKey   = if (includeTlock && includeM11) tlockWrappedKey else null,
        tlockKeyDigest    = if (includeTlock && includeM11) tlockKeyDigest else null,
        shamirThreshold   = if (includeShamir && includeM11) 2 else null,
        shamirTotalShares = if (includeShamir && includeM11) 3 else null,
    )

    private fun buildOpenCapsuleRecord(): CapsuleRecord = CapsuleRecord(
        id            = capsuleId,
        createdAt     = Instant.parse("2024-01-01T00:00:00Z"),
        updatedAt     = Instant.parse("2024-01-01T00:00:00Z"),
        createdByUser = "api-user",
        shape         = CapsuleShape.OPEN,
        state         = CapsuleState.OPEN,
        unlockAt      = OffsetDateTime.of(2050, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        cancelledAt   = null,
        deliveredAt   = null,
    )

    private fun buildDetail(record: CapsuleRecord): CapsuleDetail =
        CapsuleDetail(record, listOf("recipient@example.com"), emptyList(), "")

    // ── GET /api/capsules/:id — M11 fields in detail response ─────────────────

    @Test
    fun `capsule detail includes M11 fields for sealed tlock capsule`() {
        val detail = buildDetail(buildSealedCapsuleRecord(includeM11 = true, includeTlock = true))
        every { capsuleRepo.getCapsuleById(capsuleId, ownerId) } returns detail

        val resp = get("/api/capsules/$capsuleId")
        assertEquals(OK, resp.status)

        val body = mapper.readTree(resp.bodyString())
        // Base M11 fields
        assertNotNull(body.get("wrapped_capsule_key"), "wrapped_capsule_key must be present")
        assertTrue(body.get("wrapped_capsule_key").asText().isNotEmpty())
        assertEquals("capsule-ecdh-aes256gcm-v1", body.get("capsule_key_format").asText())

        // tlock fields
        assertEquals(12345L, body.get("tlock_round").asLong())
        assertEquals("stub-chain-abc", body.get("tlock_chain_id").asText())
        assertNotNull(body.get("tlock_wrapped_key"))
        assertNotNull(body.get("tlock_key_digest"))

        // Verify tlock_dek_tlock is NOT in the response (I-4)
        assertNull(body.get("tlock_dek_tlock"), "tlock_dek_tlock must NEVER be in capsule detail response")

        // shamir absent (not set)
        assertTrue(body.get("shamir_threshold") == null || body.get("shamir_threshold").isNull,
            "shamir_threshold should be absent for non-shamir capsule")
    }

    @Test
    fun `capsule detail M11 fields null for unsealed capsule`() {
        val detail = buildDetail(buildOpenCapsuleRecord())
        every { capsuleRepo.getCapsuleById(capsuleId, ownerId) } returns detail

        val resp = get("/api/capsules/$capsuleId")
        assertEquals(OK, resp.status)

        val body = mapper.readTree(resp.bodyString())
        // M11 fields should be absent (NON_NULL serialisation means they are omitted)
        assertTrue(body.get("wrapped_capsule_key") == null || body.get("wrapped_capsule_key").isNull,
            "wrapped_capsule_key should be absent for unsealed capsule")
        assertTrue(body.get("tlock_round") == null || body.get("tlock_round").isNull)
        assertTrue(body.get("tlock_dek_tlock") == null, "tlock_dek_tlock must NEVER appear in detail response")
    }

    @Test
    fun `capsule detail includes shamir fields when shamir capsule`() {
        val detail = buildDetail(buildSealedCapsuleRecord(includeM11 = true, includeTlock = false, includeShamir = true))
        every { capsuleRepo.getCapsuleById(capsuleId, ownerId) } returns detail

        val resp = get("/api/capsules/$capsuleId")
        assertEquals(OK, resp.status)

        val body = mapper.readTree(resp.bodyString())
        assertEquals(2, body.get("shamir_threshold").asInt())
        assertEquals(3, body.get("shamir_total_shares").asInt())
        // tlock fields absent
        assertTrue(body.get("tlock_round") == null || body.get("tlock_round").isNull)
    }

    // ── GET /api/capsule-recipient-keys/:capsuleId — owner receives all rows ──

    @Test
    fun `owner receives all recipient key rows`() {
        every { capsuleRecipientRepo.capsuleExists(capsuleId) } returns true
        every { capsuleRecipientRepo.isCapsuleOwner(capsuleId, ownerId) } returns true
        every { capsuleRecipientRepo.findAllRows(capsuleId) } returns listOf(
            CapsuleRecipientKeyRepository.RecipientKeyRow(connId1, wrappedCapsuleKey1, wrappedBlindingMask),
            CapsuleRecipientKeyRepository.RecipientKeyRow(connId2, wrappedCapsuleKey2, null),
        )

        val resp = get("/api/capsule-recipient-keys/$capsuleId", ownerId)
        assertEquals(OK, resp.status)

        val body = mapper.readTree(resp.bodyString())
        val rows = body.get("recipient_keys")
        assertEquals(2, rows.size())

        // Row 1: has blinding mask
        val row1 = rows[0]
        assertEquals(connId1.toString(), row1.get("connection_id").asText())
        assertNotNull(row1.get("wrapped_capsule_key"))
        assertNotNull(row1.get("wrapped_blinding_mask"))
        assertFalse(row1.get("wrapped_blinding_mask").isNull)

        // Row 2: no blinding mask
        val row2 = rows[1]
        assertEquals(connId2.toString(), row2.get("connection_id").asText())
        assertTrue(row2.get("wrapped_blinding_mask").isNull)
    }

    // ── Recipient receives only their own row ─────────────────────────────────

    @Test
    fun `authenticated recipient receives only their own row`() {
        every { capsuleRecipientRepo.capsuleExists(capsuleId) } returns true
        every { capsuleRecipientRepo.isCapsuleOwner(capsuleId, recipientId) } returns false
        every { capsuleRecipientRepo.isAuthenticatedRecipient(capsuleId, recipientId) } returns true
        every { capsuleRecipientRepo.findOwnRow(capsuleId, recipientId) } returns
            CapsuleRecipientKeyRepository.RecipientKeyRow(connId1, wrappedCapsuleKey1, wrappedBlindingMask)

        val resp = get("/api/capsule-recipient-keys/$capsuleId", recipientId)
        assertEquals(OK, resp.status)

        val rows = mapper.readTree(resp.bodyString()).get("recipient_keys")
        assertEquals(1, rows.size())
        assertEquals(connId1.toString(), rows[0].get("connection_id").asText())
    }

    // ── Unauthorized caller receives 403 ──────────────────────────────────────

    @Test
    fun `unauthorized caller receives 403`() {
        every { capsuleRecipientRepo.capsuleExists(capsuleId) } returns true
        every { capsuleRecipientRepo.isCapsuleOwner(capsuleId, otherId) } returns false
        every { capsuleRecipientRepo.isAuthenticatedRecipient(capsuleId, otherId) } returns false

        val resp = get("/api/capsule-recipient-keys/$capsuleId", otherId)
        assertEquals(FORBIDDEN, resp.status)
        assertEquals("forbidden", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ── Non-existent capsule returns 404 ─────────────────────────────────────

    @Test
    fun `non-existent capsule returns 404`() {
        val nonExistentId = UUID.randomUUID()
        every { capsuleRecipientRepo.capsuleExists(nonExistentId) } returns false

        val resp = get("/api/capsule-recipient-keys/$nonExistentId", ownerId)
        assertEquals(NOT_FOUND, resp.status)
    }
}
