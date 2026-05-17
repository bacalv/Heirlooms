package digital.heirlooms.server.routes.capsule

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.domain.capsule.ExecutorShareRecord
import digital.heirlooms.server.repository.capsule.ExecutorShareRepository
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.repository.connection.NominationRepository
import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.storage.LocalFileStore
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for executor share distribution endpoints (DEV-004 / M11 Wave 4).
 * All repository calls are mocked via MockK.
 *
 * Covers:
 * - POST /capsules/:id/executor-shares — happy path, wrong share count, invalid envelope,
 *   duplicate indices, invalid nomination
 * - GET  /capsules/:id/executor-shares/mine — happy path, 403, 404
 * - GET  /capsules/:id/executor-shares/collect — happy path, 403
 */
class ExecutorShareRoutesTest {

    private val mapper = ObjectMapper()
    private val rng = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    private val shareRepo = mockk<ExecutorShareRepository>()
    private val connectionRepo = mockk<ConnectionRepository>(relaxed = true)
    private val nominationRepo = mockk<NominationRepository>(relaxed = true)
    private val recipientLinkRepo = mockk<RecipientLinkRepository>(relaxed = true)
    private val storage = LocalFileStore(Files.createTempDirectory("exec-share-routes-test"))

    private val app = buildApp(
        storage = storage,
        uploadRepo = mockk(relaxed = true),
        authRepo = mockk(relaxed = true),
        capsuleRepo = mockk(relaxed = true),
        plotRepo = mockk(relaxed = true),
        flowRepo = mockk(relaxed = true),
        itemRepo = mockk(relaxed = true),
        memberRepo = mockk(relaxed = true),
        keyRepo = mockk(relaxed = true),
        socialRepo = mockk(relaxed = true),
        blobRepo = mockk(relaxed = true),
        diagRepo = mockk(relaxed = true),
        connectionRepo = connectionRepo,
        nominationRepo = nominationRepo,
        recipientLinkRepo = recipientLinkRepo,
        executorShareRepo = shareRepo,
    )

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val executorUserId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val capsuleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val nomId1 = UUID.fromString("00000000-0000-0000-0000-000000000011")
    private val nomId2 = UUID.fromString("00000000-0000-0000-0000-000000000012")
    private val nomId3 = UUID.fromString("00000000-0000-0000-0000-000000000013")

    @BeforeEach
    fun resetMocks() {
        io.mockk.clearMocks(shareRepo, connectionRepo, nominationRepo, recipientLinkRepo)
        every { connectionRepo.listConnections(any()) } returns emptyList()
        every { nominationRepo.listByOwner(any()) } returns emptyList()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    /**
     * Build a minimal valid capsule-ecdh-aes256gcm-v1 asymmetric envelope.
     * Layout: version(1) | algIdLen(1) | algId(N) | ephPubkey(65) | nonce(12) | ciphertext(V) | authTag(16)
     */
    private fun validCapsuleEnvelope(): String {
        val algId = AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1.toByteArray(Charsets.UTF_8)
        val ephPubkey = byteArrayOf(0x04) + randomBytes(64)  // SEC1 uncompressed
        val nonce = randomBytes(12)
        val ciphertext = randomBytes(32)
        val authTag = randomBytes(16)
        val blob = byteArrayOf(0x01, algId.size.toByte()) + algId + ephPubkey + nonce + ciphertext + authTag
        return urlEncoder.encodeToString(blob)
    }

    /**
     * Build an invalid (truncated) envelope that will fail validateAsymmetric().
     */
    private fun invalidEnvelope(): String = urlEncoder.encodeToString(ByteArray(10) { it.toByte() })

    private fun authedPost(path: String, body: String, userId: UUID = ownerId) =
        Request(POST, path)
            .header("X-Auth-User-Id", userId.toString())
            .header("Content-Type", "application/json")
            .body(body)

    private fun authedGet(path: String, userId: UUID = ownerId) =
        Request(GET, path).header("X-Auth-User-Id", userId.toString())

    private fun makeShareRecord(
        capsuleId: UUID = this.capsuleId,
        nomId: UUID = nomId1,
        index: Int = 1,
    ) = ExecutorShareRecord(
        id = UUID.randomUUID(),
        capsuleId = capsuleId,
        nominationId = nomId,
        shareIndex = index,
        wrappedShare = validCapsuleEnvelope(),
        shareFormat = "shamir-share-v1",
        distributedAt = Instant.now(),
    )

    private fun shareBodyN(n: Int): String {
        val shares = (1..n).joinToString(",") { i ->
            val nomId = when (i) {
                1 -> nomId1; 2 -> nomId2; else -> nomId3
            }
            """{"nomination_id":"$nomId","share_index":$i,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"}"""
        }
        return """{"shares":[$shares]}"""
    }

    // ─── POST /capsules/:id/executor-shares — Endpoint 13 ────────────────────

    @Test
    fun `POST executor-shares happy path returns 200`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 2)
        every { shareRepo.isAcceptedNominationForOwner(nomId1, ownerId) } returns true
        every { shareRepo.isAcceptedNominationForOwner(nomId2, ownerId) } returns true
        every { shareRepo.insertSharesBatch(capsuleId, any()) } returns Unit

        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", shareBodyN(2)))
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")
        assertEquals("{}", resp.bodyString())
    }

    @Test
    fun `POST executor-shares returns 422 when share count does not match shamir_total_shares`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 3)

        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", shareBodyN(2)))
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Expected 422: ${resp.bodyString()}")
        val node = mapper.readTree(resp.bodyString())
        assertEquals("wrong_share_count", node.get("error").asText())
        // detail should mention expected=3 and got=2
        val detail = node.get("detail").asText()
        assert(detail.contains("3")) { "Expected 3 in detail: $detail" }
        assert(detail.contains("2")) { "Expected 2 in detail: $detail" }
    }

    @Test
    fun `POST executor-shares returns 422 for duplicate share indices`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 2)

        // Both shares have share_index=1
        val body = """{"shares":[
            {"nomination_id":"$nomId1","share_index":1,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"},
            {"nomination_id":"$nomId2","share_index":1,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"}
        ]}"""
        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", body))
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Expected 422: ${resp.bodyString()}")
        assertEquals("invalid_share_indices", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `POST executor-shares returns 422 when nomination_id is not accepted`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 1)
        every { shareRepo.isAcceptedNominationForOwner(nomId1, ownerId) } returns false

        val body = """{"shares":[
            {"nomination_id":"$nomId1","share_index":1,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"shamir-share-v1"}
        ]}"""
        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", body))
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Expected 422: ${resp.bodyString()}")
        assertEquals("invalid_nomination_id", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `POST executor-shares returns 422 when wrapped_share envelope is invalid`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 1)
        every { shareRepo.isAcceptedNominationForOwner(nomId1, ownerId) } returns true

        val body = """{"shares":[
            {"nomination_id":"$nomId1","share_index":1,"wrapped_share":"${invalidEnvelope()}","share_format":"shamir-share-v1"}
        ]}"""
        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", body))
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Expected 422: ${resp.bodyString()}")
        val node = mapper.readTree(resp.bodyString())
        assertEquals("invalid_wrapped_share", node.get("error").asText())
        // detail must reference the failing share index
        assert(node.get("detail").asText().contains("1")) { "Detail should mention share index 1" }
    }

    @Test
    fun `POST executor-shares returns 403 when caller is not the capsule owner`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns null

        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", shareBodyN(1)))
        assertEquals(FORBIDDEN, resp.status, "Expected 403: ${resp.bodyString()}")
        assertEquals("forbidden", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `POST executor-shares returns 422 when capsule is not sealed`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "open", null)

        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", shareBodyN(1)))
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Expected 422: ${resp.bodyString()}")
        assertEquals("capsule_not_sealed_shamir", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `POST executor-shares returns 422 for invalid share_format`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 1)
        every { shareRepo.isAcceptedNominationForOwner(nomId1, ownerId) } returns true

        val body = """{"shares":[
            {"nomination_id":"$nomId1","share_index":1,"wrapped_share":"${validCapsuleEnvelope()}","share_format":"INVALID-FORMAT"}
        ]}"""
        val resp = app(authedPost("/api/capsules/$capsuleId/executor-shares", body))
        assertEquals(UNPROCESSABLE_ENTITY, resp.status, "Expected 422: ${resp.bodyString()}")
        assertEquals("invalid_share_format", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── GET /capsules/:id/executor-shares/mine — Endpoint 14 ────────────────

    @Test
    fun `GET executor-shares-mine returns 200 with wrapped share for executor`() {
        val record = makeShareRecord(index = 2)
        every { shareRepo.findShareForExecutor(capsuleId, executorUserId) } returns
            ExecutorShareRepository.MineQueryResult.Found(record)

        val resp = app(authedGet("/api/capsules/$capsuleId/executor-shares/mine", executorUserId))
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        val node = mapper.readTree(resp.bodyString()).get("share")
        assertEquals(record.wrappedShare, node.get("wrapped_share").asText())
        assertEquals("shamir-share-v1", node.get("share_format").asText())
        assertEquals(2, node.get("share_index").asInt())
        assertEquals(capsuleId.toString(), node.get("capsule_id").asText())
    }

    @Test
    fun `GET executor-shares-mine returns 403 when caller is not an accepted executor`() {
        every { shareRepo.findShareForExecutor(capsuleId, ownerId) } returns
            ExecutorShareRepository.MineQueryResult.NotAnExecutor

        val resp = app(authedGet("/api/capsules/$capsuleId/executor-shares/mine"))
        assertEquals(FORBIDDEN, resp.status, "Expected 403: ${resp.bodyString()}")
        assertEquals("forbidden", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    @Test
    fun `GET executor-shares-mine returns 404 when shares not yet distributed`() {
        every { shareRepo.findShareForExecutor(capsuleId, executorUserId) } returns
            ExecutorShareRepository.MineQueryResult.NotYetDistributed

        val resp = app(authedGet("/api/capsules/$capsuleId/executor-shares/mine", executorUserId))
        assertEquals(NOT_FOUND, resp.status, "Expected 404: ${resp.bodyString()}")
        assertEquals("share_not_found", mapper.readTree(resp.bodyString()).get("error").asText())
    }

    // ─── GET /capsules/:id/executor-shares/collect — Endpoint 15 ─────────────

    @Test
    fun `GET executor-shares-collect returns 200 with all shares and threshold info`() {
        val records = listOf(makeShareRecord(index = 1, nomId = nomId1), makeShareRecord(index = 2, nomId = nomId2))
        every { shareRepo.getCapsuleShareConfig(capsuleId, ownerId) } returns
            ExecutorShareRepository.CapsuleShareConfig(capsuleId, "sealed", 2)
        every { shareRepo.getCapsuleShamirConfig(capsuleId) } returns
            ExecutorShareRepository.ShamirConfig(threshold = 2, totalShares = 2)
        every { shareRepo.findAllShares(capsuleId) } returns records

        val resp = app(authedGet("/api/capsules/$capsuleId/executor-shares/collect"))
        assertEquals(OK, resp.status, "Expected 200: ${resp.bodyString()}")

        val node = mapper.readTree(resp.bodyString())
        assertEquals(2, node.get("shares").size())
        assertEquals(2, node.get("threshold").asInt())
        assertEquals(2, node.get("total").asInt())
    }

    @Test
    fun `GET executor-shares-collect returns 403 when caller is not the capsule author`() {
        every { shareRepo.getCapsuleShareConfig(capsuleId, executorUserId) } returns null

        val resp = app(authedGet("/api/capsules/$capsuleId/executor-shares/collect", executorUserId))
        assertEquals(FORBIDDEN, resp.status, "Expected 403: ${resp.bodyString()}")
        assertEquals("forbidden", mapper.readTree(resp.bodyString()).get("error").asText())
    }
}
