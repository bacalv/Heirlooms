package digital.heirlooms.server.service

import digital.heirlooms.server.service.capsule.ExecutorShareService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for [ExecutorShareService.validateShareIndices].
 *
 * Tests the service-layer validation that share_index values must be
 * 1-based, unique, and cover 1..N without gaps.
 */
class ExecutorShareServiceTest {

    // No-op repo; we only test the pure validation method here
    private val svc = ExecutorShareService(
        object : digital.heirlooms.server.repository.capsule.ExecutorShareRepository {
            override fun getCapsuleShareConfig(capsuleId: UUID, ownerUserId: UUID) = null
            override fun isAcceptedNominationForOwner(nominationId: UUID, ownerUserId: UUID) = false
            override fun insertSharesBatch(capsuleId: UUID, shares: List<digital.heirlooms.server.repository.capsule.ExecutorShareRepository.ShareRow>) {}
            override fun findShareForExecutor(capsuleId: UUID, callerUserId: UUID) =
                digital.heirlooms.server.repository.capsule.ExecutorShareRepository.MineQueryResult.NotAnExecutor
            override fun findAllShares(capsuleId: UUID) = emptyList<digital.heirlooms.server.domain.capsule.ExecutorShareRecord>()
            override fun getCapsuleShamirConfig(capsuleId: UUID) = null
        }
    )

    private val nomId1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val nomId2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val nomId3 = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private fun share(index: Int, nomId: UUID = nomId1) = ExecutorShareService.ShareInput(
        nominationId = nomId,
        shareIndex = index,
        wrappedShare = "dummyBase64",
        shareFormat = "shamir-share-v1",
    )

    // ─── Valid cases ──────────────────────────────────────────────────────────

    @Test
    fun `single share with index 1 is valid`() {
        val shares = listOf(share(1))
        assertNull(svc.validateShareIndices(shares), "Expected no validation error for [1]")
    }

    @Test
    fun `indices 1 to 3 in order are valid`() {
        val shares = listOf(share(1, nomId1), share(2, nomId2), share(3, nomId3))
        assertNull(svc.validateShareIndices(shares))
    }

    @Test
    fun `indices 1 to 3 out of order are still valid`() {
        val shares = listOf(share(3, nomId3), share(1, nomId1), share(2, nomId2))
        assertNull(svc.validateShareIndices(shares))
    }

    // ─── Duplicate index ──────────────────────────────────────────────────────

    @Test
    fun `duplicate share_index returns InvalidShareIndices`() {
        val shares = listOf(share(1, nomId1), share(1, nomId2), share(3, nomId3))
        assertEquals(ExecutorShareService.SubmitResult.InvalidShareIndices, svc.validateShareIndices(shares))
    }

    @Test
    fun `all same index returns InvalidShareIndices`() {
        val shares = listOf(share(2, nomId1), share(2, nomId2), share(2, nomId3))
        assertEquals(ExecutorShareService.SubmitResult.InvalidShareIndices, svc.validateShareIndices(shares))
    }

    // ─── Gap in indices ───────────────────────────────────────────────────────

    @Test
    fun `indices starting at 0 return InvalidShareIndices`() {
        val shares = listOf(share(0, nomId1), share(1, nomId2), share(2, nomId3))
        assertEquals(ExecutorShareService.SubmitResult.InvalidShareIndices, svc.validateShareIndices(shares))
    }

    @Test
    fun `indices with gap return InvalidShareIndices`() {
        val shares = listOf(share(1, nomId1), share(3, nomId2))  // missing 2
        assertEquals(ExecutorShareService.SubmitResult.InvalidShareIndices, svc.validateShareIndices(shares))
    }

    @Test
    fun `indices starting at 2 return InvalidShareIndices`() {
        val shares = listOf(share(2, nomId1), share(3, nomId2))
        assertEquals(ExecutorShareService.SubmitResult.InvalidShareIndices, svc.validateShareIndices(shares))
    }

    // ─── N=1 edge case ────────────────────────────────────────────────────────

    @Test
    fun `single share with index 2 is invalid (not 1-based)`() {
        val shares = listOf(share(2))
        assertEquals(ExecutorShareService.SubmitResult.InvalidShareIndices, svc.validateShareIndices(shares))
    }
}
