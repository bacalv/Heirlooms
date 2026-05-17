package digital.heirlooms.server.service.capsule

import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.crypto.EnvelopeFormat
import digital.heirlooms.server.crypto.EnvelopeFormatException
import digital.heirlooms.server.domain.capsule.ExecutorShareRecord
import digital.heirlooms.server.repository.capsule.ExecutorShareRepository
import java.util.Base64
import java.util.UUID

/**
 * Business logic for Shamir executor share distribution (DEV-004 / M11 Wave 4).
 *
 * Enforces the 8-step validation sequence from ARCH-010 §5.2 before any DB writes.
 * All validation is all-or-nothing: if any share is invalid, no rows are written.
 *
 * SECURITY: No share plaintext values, DEK values, or tlock key material are logged.
 */
class ExecutorShareService(private val shareRepo: ExecutorShareRepository) {

    private val urlDecoder = Base64.getUrlDecoder()

    // ── Input model ───────────────────────────────────────────────────────────

    data class ShareInput(
        val nominationId: UUID,
        val shareIndex: Int,
        /** base64url of capsule-ecdh-aes256gcm-v1 envelope */
        val wrappedShare: String,
        val shareFormat: String,
    )

    // ── Result types ──────────────────────────────────────────────────────────

    sealed class SubmitResult {
        /** All shares accepted and written. */
        object Ok : SubmitResult()
        /** Caller is not the capsule owner, or capsule does not exist. */
        object Forbidden : SubmitResult()
        /** Capsule is not sealed or does not have shamir_total_shares set. */
        object NotSealedShamir : SubmitResult()
        /** shares.size != shamir_total_shares. */
        data class WrongShareCount(val expected: Int, val got: Int) : SubmitResult()
        /** share_index values are not exactly 1..N without gaps, or contain duplicates. */
        object InvalidShareIndices : SubmitResult()
        /** A nomination_id is not an accepted executor for this owner. */
        data class InvalidNominationId(val id: UUID) : SubmitResult()
        /** A wrapped_share envelope is not a valid capsule-ecdh-aes256gcm-v1 envelope. */
        data class InvalidWrappedShare(val shareIndex: Int) : SubmitResult()
        /** share_format is not "shamir-share-v1". */
        object InvalidShareFormat : SubmitResult()
    }

    sealed class MineResult {
        data class Found(val share: ExecutorShareRecord) : MineResult()
        /** Capsule is sealed but shares have not been submitted yet. */
        object NotYetDistributed : MineResult()
        /** Caller is not an accepted executor for this capsule. */
        object Forbidden : MineResult()
    }

    sealed class CollectResult {
        data class Found(val shares: List<ExecutorShareRecord>, val threshold: Int?, val total: Int?) : CollectResult()
        /** Caller is not the capsule author. */
        object Forbidden : CollectResult()
    }

    // ── Submit shares (POST /executor-shares) ─────────────────────────────────

    /**
     * Validates and stores all Shamir shares for a sealed capsule.
     * Implements the 8-step validation sequence from ARCH-010 §5.2.
     */
    fun submitShares(
        callerUserId: UUID,
        capsuleId: UUID,
        shares: List<ShareInput>,
    ): SubmitResult {
        // Step 1: Caller must be the capsule owner (and capsule must exist)
        val config = shareRepo.getCapsuleShareConfig(capsuleId, callerUserId)
            ?: return SubmitResult.Forbidden

        // Step 2: Capsule must be sealed with shamir_total_shares set
        if (config.shape != "sealed" || config.shamirTotalShares == null) {
            return SubmitResult.NotSealedShamir
        }

        val n = config.shamirTotalShares

        // Step 3: shares array length must equal shamir_total_shares
        if (shares.size != n) {
            return SubmitResult.WrongShareCount(expected = n, got = shares.size)
        }

        // Step 4: share_index values must be unique, 1-based, covering 1..N without gaps
        validateShareIndices(shares)?.let { return it }

        // Steps 5–7: validate each share before writing any rows
        for (share in shares) {
            // Step 5: nomination_id must reference an accepted executor nomination for this owner
            if (!shareRepo.isAcceptedNominationForOwner(share.nominationId, callerUserId)) {
                return SubmitResult.InvalidNominationId(share.nominationId)
            }

            // Step 6: wrapped_share must be a valid capsule-ecdh-aes256gcm-v1 asymmetric envelope
            val blobResult = runCatching { urlDecoder.decode(share.wrappedShare) }
            val blob = blobResult.getOrElse { return SubmitResult.InvalidWrappedShare(share.shareIndex) }
            try {
                EnvelopeFormat.validateAsymmetric(blob, AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1)
            } catch (_: EnvelopeFormatException) {
                return SubmitResult.InvalidWrappedShare(share.shareIndex)
            }

            // Step 7: share_format must be "shamir-share-v1"
            if (share.shareFormat != "shamir-share-v1") {
                return SubmitResult.InvalidShareFormat
            }
        }

        // Step 8: atomic insert of all rows
        val rows = shares.map { s ->
            ExecutorShareRepository.ShareRow(
                nominationId = s.nominationId,
                shareIndex = s.shareIndex,
                wrappedShare = s.wrappedShare,
                shareFormat = s.shareFormat,
            )
        }
        shareRepo.insertSharesBatch(capsuleId, rows)
        return SubmitResult.Ok
    }

    /**
     * Validates that share indices are 1-based, unique, and cover exactly 1..N without gaps.
     * Returns the appropriate error result, or null if valid.
     *
     * This validation is done in the service layer and does not rely on DB constraints.
     */
    internal fun validateShareIndices(shares: List<ShareInput>): SubmitResult? {
        val n = shares.size
        val indices = shares.map { it.shareIndex }

        // Check for uniqueness
        if (indices.toSet().size != n) return SubmitResult.InvalidShareIndices

        // Check for 1-based, no-gap coverage: sorted must be exactly [1, 2, ..., N]
        val sorted = indices.sorted()
        for (i in sorted.indices) {
            if (sorted[i] != i + 1) return SubmitResult.InvalidShareIndices
        }

        return null
    }

    // ── Fetch own share (GET /executor-shares/mine) ───────────────────────────

    fun getMineShare(capsuleId: UUID, callerUserId: UUID): MineResult {
        return when (val result = shareRepo.findShareForExecutor(capsuleId, callerUserId)) {
            is ExecutorShareRepository.MineQueryResult.Found ->
                MineResult.Found(result.share)
            ExecutorShareRepository.MineQueryResult.NotYetDistributed ->
                MineResult.NotYetDistributed
            ExecutorShareRepository.MineQueryResult.NotAnExecutor ->
                MineResult.Forbidden
        }
    }

    // ── Collect all shares (GET /executor-shares/collect) ─────────────────────

    /**
     * Author-authenticated: collect all shares for a capsule.
     *
     * In M11 the caller must be the capsule author. Death-verification-gated
     * collection (executor-initiated quorum with M13 death verification) is out
     * of scope for M11 — see M13.
     */
    fun collectShares(capsuleId: UUID, callerUserId: UUID): CollectResult {
        // Verify the caller is the capsule author by checking the config lookup
        // (returns null if capsule doesn't exist or caller isn't the owner)
        val config = shareRepo.getCapsuleShareConfig(capsuleId, callerUserId)
            ?: return CollectResult.Forbidden

        val shamirConfig = shareRepo.getCapsuleShamirConfig(capsuleId)
        val shares = shareRepo.findAllShares(capsuleId)
        return CollectResult.Found(
            shares = shares,
            threshold = shamirConfig?.threshold,
            total = shamirConfig?.totalShares ?: config.shamirTotalShares,
        )
    }
}
