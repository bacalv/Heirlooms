package digital.heirlooms.server.service.capsule

import digital.heirlooms.server.crypto.tlock.DisabledTimeLockProvider
import digital.heirlooms.server.crypto.tlock.TimeLockCiphertext
import digital.heirlooms.server.crypto.tlock.TimeLockProvider
import digital.heirlooms.server.repository.capsule.TlockKeyRepository
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Service implementing the 7-step gate logic for GET /api/capsules/:id/tlock-key.
 * Reference: ARCH-010 §5.3 (sole implementation source).
 *
 * LOGGING PROHIBITION (CRITICAL — ARCH-010 §5.3, ARCH-006 §6.2, Invariant I-4):
 * - tlock_dek_tlock MUST NOT appear in any SLF4J log at any level.
 * - The response body (dek_tlock field) MUST NOT be logged.
 * - Log only: capsule ID, caller user ID, HTTP status, latency.
 * - Every log call in this class has been reviewed to confirm no key material is included.
 */
class TlockKeyService(
    private val tlockKeyRepo: TlockKeyRepository,
    private val timeLockProvider: TimeLockProvider,
) {
    private val logger = LoggerFactory.getLogger(TlockKeyService::class.java)

    // ─── Result sealed class ───────────────────────────────────────────────────

    sealed class TlockKeyResult {
        /** Step [7] — gate fully open, key delivered. */
        data class Success(
            /** SECURITY: base64url-encoded tlock_dek_tlock — MUST NOT be logged. */
            val dekTlockB64: String,
            val chainId: String,
            val round: Long,
        ) : TlockKeyResult()

        /** Step [1] — caller not in capsule_recipient_keys. */
        object NotARecipient : TlockKeyResult()

        /** Step [2] — capsule not found or not a sealed tlock capsule. */
        object NotFound : TlockKeyResult()

        /** Step [3] — TLOCK_PROVIDER is disabled. */
        object TlockNotEnabled : TlockKeyResult()

        /**
         * Steps [4] and [5] — gates not yet open.
         * [detail] is a human-readable reason (safe to surface; contains no key material).
         */
        data class GateNotOpen(val detail: String, val retryAfterSeconds: Long) : TlockKeyResult()

        /** Step [6] — SHA-256(tlock_dek_tlock) != tlock_key_digest. Internal tamper. */
        object TamperDetected : TlockKeyResult()
    }

    // ─── Gate logic ────────────────────────────────────────────────────────────

    fun getTlockKey(capsuleId: UUID, callerUserId: UUID): TlockKeyResult {
        // [1] Caller must be in capsule_recipient_keys for this capsule
        val isRecipient = tlockKeyRepo.isRecipient(capsuleId, callerUserId)
        if (!isRecipient) {
            logger.info("tlock-key: not_a_recipient capsule={} caller={}", capsuleId, callerUserId)
            return TlockKeyResult.NotARecipient
        }

        // [2] Capsule must exist and be sealed with non-null tlock fields
        val fields = tlockKeyRepo.loadTlockFields(capsuleId)
        if (fields == null) {
            logger.info("tlock-key: not_found capsule={} caller={}", capsuleId, callerUserId)
            return TlockKeyResult.NotFound
        }

        // [3] TLOCK_PROVIDER must not be disabled
        if (timeLockProvider is DisabledTimeLockProvider) {
            logger.info("tlock-key: tlock_not_enabled capsule={} caller={}", capsuleId, callerUserId)
            return TlockKeyResult.TlockNotEnabled
        }

        // [4] now() >= capsules.unlock_at
        val now = Instant.now()
        val unlockAtInstant = fields.unlockAt.toInstant()
        if (now.isBefore(unlockAtInstant)) {
            val retryAfter = unlockAtInstant.epochSecond - now.epochSecond
            logger.info(
                "tlock-key: gate_not_open (unlock_at not passed) capsule={} caller={} retryAfter={}",
                capsuleId, callerUserId, retryAfter,
            )
            return TlockKeyResult.GateNotOpen("unlock_at has not passed", retryAfter)
        }

        // [5] TimeLockProvider.decrypt() — null means round not yet published
        val ciphertext = TimeLockCiphertext(
            chainId = fields.chainId,
            round   = fields.round,
            blob    = fields.wrappedKey,
        )
        val decrypted = try {
            timeLockProvider.decrypt(ciphertext)
        } catch (e: Exception) {
            // Structural corruption — treat as gate not open with retry
            logger.warn(
                "tlock-key: provider.decrypt threw for capsule={} caller={} — treating as gate_not_open",
                capsuleId, callerUserId,
            )
            null
        }
        if (decrypted == null) {
            logger.info(
                "tlock-key: gate_not_open (round not yet published) capsule={} caller={}",
                capsuleId, callerUserId,
            )
            return TlockKeyResult.GateNotOpen("round not yet published", 30L)
        }

        // [6] SHA-256(tlock_dek_tlock) == tlock_key_digest
        // SECURITY: dekTlock is key material — do NOT log it. Log only capsule ID.
        val computedDigest = MessageDigest.getInstance("SHA-256").digest(fields.dekTlock)
        if (!MessageDigest.isEqual(computedDigest, fields.keyDigest)) {
            logger.error("tlock tamper detected for capsule {}", capsuleId)
            // SECURITY: do not log fields.dekTlock or fields.keyDigest values
            return TlockKeyResult.TamperDetected
        }

        // [7] Return dek_tlock — SECURITY: we encode it here but MUST NOT log it
        val dekTlockB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(fields.dekTlock)
        logger.info(
            "tlock-key: success capsule={} caller={} chain={} round={}",
            capsuleId, callerUserId, fields.chainId, fields.round,
        )
        // SECURITY: dekTlockB64 is NOT included in the log line above — only metadata
        return TlockKeyResult.Success(
            dekTlockB64 = dekTlockB64,  // MUST NOT be logged by the caller
            chainId     = fields.chainId,
            round       = fields.round,
        )
    }
}
