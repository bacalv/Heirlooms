package digital.heirlooms.server.domain.capsule

import java.time.Instant
import java.util.UUID

/**
 * Represents a single Shamir share row from executor_shares.
 * The server stores only the wrapped (opaque) share blob — it never
 * decrypts or inspects the plaintext 64-byte Shamir share structure.
 */
data class ExecutorShareRecord(
    val id: UUID,
    val capsuleId: UUID,
    val nominationId: UUID,
    val shareIndex: Int,
    /** base64url-encoded capsule-ecdh-aes256gcm-v1 asymmetric envelope */
    val wrappedShare: String,
    val shareFormat: String,
    val distributedAt: Instant,
)
