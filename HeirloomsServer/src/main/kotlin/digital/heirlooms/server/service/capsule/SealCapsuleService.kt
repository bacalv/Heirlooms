package digital.heirlooms.server.service.capsule

import digital.heirlooms.server.crypto.AlgorithmIds
import digital.heirlooms.server.crypto.EnvelopeFormat
import digital.heirlooms.server.crypto.EnvelopeFormatException
import digital.heirlooms.server.crypto.tlock.DisabledTimeLockProvider
import digital.heirlooms.server.crypto.tlock.StubTimeLockProvider
import digital.heirlooms.server.crypto.tlock.TimeLockCiphertext
import digital.heirlooms.server.crypto.tlock.TimeLockProvider
import digital.heirlooms.server.repository.capsule.SealRepository
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Service implementing the 16-step validation sequence for PUT /api/capsules/:id/seal.
 * Reference: ARCH-010 §5.1 (sole implementation source).
 *
 * LOGGING PROHIBITION:
 * - tlock.dekTlock MUST NOT be logged at any level.
 * - tlock.wrappedKey MUST NOT be logged at any level.
 * - Log only: capsule ID, caller user ID, HTTP status, latency.
 */
class SealCapsuleService(
    private val sealRepo: SealRepository,
    private val timeLockProvider: TimeLockProvider,
) {
    private val logger = LoggerFactory.getLogger(SealCapsuleService::class.java)
    private val b64url = Base64.getUrlDecoder()
    private val b64urlEnc = Base64.getUrlEncoder().withoutPadding()
    private val sha256 = { data: ByteArray -> MessageDigest.getInstance("SHA-256").digest(data) }

    // ─── Request data model ────────────────────────────────────────────────────

    data class RecipientKeyRequest(
        val connectionId: UUID,
        /** base64url-encoded asymmetric envelope */
        val wrappedCapsuleKey: String,
        val capsuleKeyFormat: String,
        /** null on non-tlock capsules */
        val wrappedBlindingMask: String?,
    )

    data class TlockRequest(
        val round: Long,
        val chainId: String,
        /** base64url-encoded IBE ciphertext (DEK_client) */
        val wrappedKey: String,
        /**
         * base64url-encoded 32-byte DEK_tlock = DEK XOR DEK_client.
         * SECURITY: MUST NOT be logged at any level.
         */
        val dekTlock: String,
        /** base64url-encoded SHA-256(DEK_tlock) */
        val tlockKeyDigest: String,
    )

    data class ShamirRequest(
        val threshold: Int,
        val totalShares: Int,
    )

    data class SealRequest(
        val recipientKeys: List<RecipientKeyRequest>,
        val tlock: TlockRequest?,
        val shamir: ShamirRequest?,
    )

    // ─── Result types ──────────────────────────────────────────────────────────

    sealed class SealResult {
        /** HTTP 200 response body fields */
        data class Success(
            val capsuleId: UUID,
            val sealedAt: Instant,
        ) : SealResult()

        data class Forbidden(val detail: String? = null) : SealResult()
        data class NotFound(val detail: String? = null) : SealResult()
        data class CapsuleNotSealable(val detail: String) : SealResult()

        /** HTTP 422 */
        data class ValidationError(
            val error: String,
            val detail: String? = null,
        ) : SealResult()

        /** HTTP 500 */
        data class InternalError(val detail: String) : SealResult()
    }

    // ─── Main validation + write ───────────────────────────────────────────────

    /**
     * Execute the 16-step validation sequence, then write atomically.
     * Steps [0]–[15] are pure validation; step [16] is the atomic write.
     *
     * Caller supplies authenticated [userId] from the auth header.
     *
     * INVARIANTS (ARCH-010 §6 / ARCH-003 §6):
     * I-1: wrapped_capsule_key wraps full DEK — enforced by client, not inspected here.
     * I-2: wrapped_blinding_mask wraps DEK_client — enforced structurally (non-null iff tlock).
     * I-3: Shamir shares computed over DEK — not verified here (shares uploaded after seal).
     * I-4: dekTlock MUST NOT be logged — enforced throughout.
     * I-5: tlock_key_digest = SHA-256(DEK_tlock) — verified in step [12].
     * I-7: Multi-path fallback rule — verified in step [15].
     * I-8: shape='open' required — verified in step [2].
     */
    fun sealCapsule(capsuleId: UUID, userId: UUID, req: SealRequest): SealResult {
        logger.info(
            "Seal request received for capsule {} [tlock={}, shamir={}]",
            capsuleId, req.tlock != null, req.shamir != null,
        )

        // ── [0] Auth is already confirmed by the route via authUserId(). ──────
        // (auth=caller identity is passed in as userId from X-Auth-User-Id)

        // ── [1] Load capsule — must exist and belong to caller. ───────────────
        val capsule = sealRepo.loadCapsuleForSeal(capsuleId, userId)
            ?: return SealResult.NotFound()

        // ── [2] Shape must be 'open' (I-8). ───────────────────────────────────
        if (capsule.shape != "open") {
            return SealResult.CapsuleNotSealable("capsule is not in 'open' shape")
        }

        // ── [3] At least one recipient key. ───────────────────────────────────
        if (req.recipientKeys.isEmpty()) {
            return SealResult.ValidationError(
                error  = "missing_recipient_keys",
                detail = "at least one recipient key is required",
            )
        }

        // ── [4]–[6] Per-recipient validation. ─────────────────────────────────
        var allBound = true
        for (entry in req.recipientKeys) {
            // [4] Validate wrapped_capsule_key envelope
            val wck = try {
                b64url.decode(entry.wrappedCapsuleKey)
            } catch (_: Exception) {
                return SealResult.ValidationError(
                    error  = "invalid_wrapped_capsule_key",
                    detail = "recipient ${entry.connectionId}: invalid envelope",
                )
            }
            try {
                EnvelopeFormat.validateAsymmetric(wck, AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1)
            } catch (ex: EnvelopeFormatException) {
                return SealResult.ValidationError(
                    error  = "invalid_wrapped_capsule_key",
                    detail = "recipient ${entry.connectionId}: invalid envelope",
                )
            }

            // [5] If tlock fields present, wrapped_blinding_mask must be present + valid
            if (req.tlock != null) {
                val wbm = entry.wrappedBlindingMask
                if (wbm.isNullOrEmpty()) {
                    return SealResult.ValidationError(
                        error  = "missing_wrapped_blinding_mask",
                        detail = "recipient ${entry.connectionId}: tlock capsules require wrapped_blinding_mask",
                    )
                }
                val wbmBytes = try {
                    b64url.decode(wbm)
                } catch (_: Exception) {
                    return SealResult.ValidationError(
                        error  = "missing_wrapped_blinding_mask",
                        detail = "recipient ${entry.connectionId}: tlock capsules require wrapped_blinding_mask",
                    )
                }
                try {
                    EnvelopeFormat.validateAsymmetric(wbmBytes, AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1)
                } catch (_: EnvelopeFormatException) {
                    return SealResult.ValidationError(
                        error  = "missing_wrapped_blinding_mask",
                        detail = "recipient ${entry.connectionId}: tlock capsules require wrapped_blinding_mask",
                    )
                }
            }

            // [6] Validate connection_id is bound and belongs to this user
            val bound = sealRepo.isConnectionBoundAndOwned(entry.connectionId, userId)
            if (!bound) {
                return SealResult.ValidationError(
                    error  = "invalid_connection_id",
                    detail = "connection ${entry.connectionId} is not bound or does not belong to this user",
                )
            }
            // allBound tracking: step [6] already ensures each connection has contact_user_id IS NOT NULL.
            // If any is deferred, isConnectionBoundAndOwned returns false and we return above.
            // So if we reach here, allBound = true for this entry.
        }
        // All entries passed step [6], so allBound = true (deferred connections fail step [6]).
        // allBound tracks whether the *client-declared* set has any unbound; since
        // step [6] enforces it, allBound = true iff all entries passed step [6].
        // This variable is used in step [15].
        allBound = true  // all passed step [6]

        // ── [7]–[12] tlock-specific validation ────────────────────────────────
        var tlockWriteParams: SealRepository.TlockWriteParams? = null
        if (req.tlock != null) {
            val t = req.tlock

            // [7] wrapped_key non-null and non-empty
            if (t.wrappedKey.isBlank()) {
                return SealResult.ValidationError(error = "missing_tlock_wrapped_key")
            }
            val wrappedKeyBytes = try {
                b64url.decode(t.wrappedKey)
            } catch (_: Exception) {
                return SealResult.ValidationError(error = "missing_tlock_wrapped_key")
            }
            if (wrappedKeyBytes.isEmpty()) {
                return SealResult.ValidationError(error = "missing_tlock_wrapped_key")
            }

            // [8] provider.validate() — or reject if disabled
            if (timeLockProvider is DisabledTimeLockProvider) {
                return SealResult.ValidationError(
                    error  = "tlock_not_enabled",
                    detail = "tlock provider is disabled on this server",
                )
            }

            val ciphertext = TimeLockCiphertext(
                chainId = t.chainId,
                round   = t.round,
                blob    = wrappedKeyBytes,
            )
            val valid = try {
                timeLockProvider.validate(ciphertext)
            } catch (_: Exception) {
                false
            }
            if (!valid) {
                return SealResult.ValidationError(
                    error  = "tlock_blob_invalid",
                    detail = "tlock blob failed structural validation",
                )
            }

            // [9] chain_id is a known chain
            if (t.chainId != TimeLockCiphertext.STUB_CHAIN_ID) {
                // For stub: only STUB_CHAIN_ID is accepted. For sidecar (M12): validate against registry.
                return SealResult.ValidationError(
                    error  = "unknown_tlock_chain",
                    detail = "chain_id is not recognised by the configured tlock provider",
                )
            }

            // [10] Validate round timing: expected_publish <= unlock_at + 1 hour
            val expectedPublish = StubTimeLockProvider.publishTimeForRound(t.round)
            val unlockAtInstant = capsule.unlockAt.toInstant()
            val upperBound = unlockAtInstant.plusSeconds(3600L)  // unlock_at + 1 hour
            if (expectedPublish.isAfter(upperBound)) {
                return SealResult.ValidationError(
                    error  = "tlock_round_mismatch",
                    detail = "tlock round expected publish time is more than 1 hour after unlock_at",
                )
            }

            // [11] tlock_key_digest present and exactly 32 bytes after base64url decode
            if (t.tlockKeyDigest.isBlank()) {
                return SealResult.ValidationError(error = "missing_tlock_key_digest")
            }
            val keyDigestBytes = try {
                b64url.decode(t.tlockKeyDigest)
            } catch (_: Exception) {
                return SealResult.ValidationError(error = "missing_tlock_key_digest")
            }
            if (keyDigestBytes.size != 32) {
                return SealResult.ValidationError(error = "missing_tlock_key_digest")
            }

            // [12] SHA-256(dek_tlock) == tlock_key_digest
            // SECURITY: dekTlock is key material. DO NOT log its value.
            val dekTlockBytes = try {
                b64url.decode(t.dekTlock)
            } catch (_: Exception) {
                // Cannot even decode — log only the capsule ID, not the value
                logger.warn("tlock digest validation failed for capsule {}", capsuleId)
                return SealResult.ValidationError(
                    error  = "tlock_digest_mismatch",
                    detail = "SHA-256(dek_tlock) does not match tlock_key_digest",
                )
            }
            val computedDigest = sha256(dekTlockBytes)
            if (!MessageDigest.isEqual(computedDigest, keyDigestBytes)) {
                logger.warn("tlock digest validation failed for capsule {}", capsuleId)
                return SealResult.ValidationError(
                    error  = "tlock_digest_mismatch",
                    detail = "SHA-256(dek_tlock) does not match tlock_key_digest",
                )
            }

            tlockWriteParams = SealRepository.TlockWriteParams(
                round      = t.round,
                chainId    = t.chainId,
                wrappedKey = wrappedKeyBytes,
                dekTlock   = dekTlockBytes,     // SECURITY: stored, never logged
                keyDigest  = keyDigestBytes,
            )
        }

        // ── [13]–[14] Shamir-specific validation ──────────────────────────────
        var shamirWriteParams: SealRepository.ShamirWriteParams? = null
        if (req.shamir != null) {
            val s = req.shamir

            // [13] threshold >= 1, total_shares >= 1, threshold <= total_shares
            when {
                s.threshold < 1 -> return SealResult.ValidationError(
                    error  = "invalid_shamir_config",
                    detail = "threshold must be >= 1",
                )
                s.totalShares < 1 -> return SealResult.ValidationError(
                    error  = "invalid_shamir_config",
                    detail = "total_shares must be >= 1",
                )
                s.threshold > s.totalShares -> return SealResult.ValidationError(
                    error  = "invalid_shamir_config",
                    detail = "threshold must be <= total_shares",
                )
            }

            // [14] accepted nominations count >= total_shares
            val accepted = sealRepo.countAcceptedNominations(userId)
            if (accepted < s.totalShares) {
                return SealResult.ValidationError(
                    error  = "insufficient_accepted_nominations",
                    detail = "need ${s.totalShares} accepted nominations; only $accepted found",
                )
            }

            shamirWriteParams = SealRepository.ShamirWriteParams(
                threshold   = s.threshold,
                totalShares = s.totalShares,
            )
        }

        // ── [15] Multi-path fallback rule ──────────────────────────────────────
        // allBound = true (all passed step [6], so all have contact_user_id IS NOT NULL)
        // has_tlock = req.tlock != null
        // has_executor = req.shamir != null && req.shamir.total_shares >= 1
        val hasTlock    = req.tlock != null
        val hasExecutor = req.shamir != null && req.shamir.totalShares >= 1
        if (!allBound && !(hasTlock || hasExecutor)) {
            return SealResult.ValidationError(
                error  = "sealing_validation_failed",
                detail = "one or more recipients lack a sharing pubkey and no tlock or executor fallback is configured",
            )
        }

        // ── [16] Atomic write ──────────────────────────────────────────────────
        val recipientKeyEntries = req.recipientKeys.map { entry ->
            SealRepository.RecipientKeyEntry(
                connectionId      = entry.connectionId,
                wrappedCapsuleKey = b64url.decode(entry.wrappedCapsuleKey),
                capsuleKeyFormat  = entry.capsuleKeyFormat.ifEmpty { AlgorithmIds.CAPSULE_ECDH_AES256GCM_V1 },
                wrappedBlindingMask = if (req.tlock != null && entry.wrappedBlindingMask != null)
                    b64url.decode(entry.wrappedBlindingMask) else null,
            )
        }

        val writeParams = SealRepository.SealWriteParams(
            recipientKeys = recipientKeyEntries,
            tlock         = tlockWriteParams,
            shamir        = shamirWriteParams,
        )

        val sealedAt = try {
            sealRepo.writeSealAtomically(capsuleId, userId, writeParams)
        } catch (e: Exception) {
            logger.error("Seal write failed for capsule {}: {}", capsuleId, e.message)
            return SealResult.InternalError("Failed to write seal transaction: ${e.message}")
        }

        logger.info("Capsule {} sealed successfully", capsuleId)
        return SealResult.Success(capsuleId = capsuleId, sealedAt = sealedAt)
    }
}
