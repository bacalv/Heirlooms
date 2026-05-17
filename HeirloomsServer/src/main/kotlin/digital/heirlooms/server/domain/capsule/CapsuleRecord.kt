package digital.heirlooms.server.domain.capsule

import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class CapsuleRecord(
    val id: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdByUser: String,
    val shape: CapsuleShape,
    val state: CapsuleState,
    val unlockAt: OffsetDateTime,
    val cancelledAt: Instant?,
    val deliveredAt: Instant?,
    // M11 fields — null for capsules not yet sealed with M11 /seal endpoint
    /** base64url-encoded asymmetric envelope (capsule-ecdh-aes256gcm-v1). Null until sealed. */
    val wrappedCapsuleKey: ByteArray? = null,
    val capsuleKeyFormat: String? = null,
    val tlockRound: Long? = null,
    val tlockChainId: String? = null,
    /** IBE ciphertext bytes (safe to return; only decryptable after round publishes). */
    val tlockWrappedKey: ByteArray? = null,
    /** SHA-256(DEK_tlock) — safe to return; the preimage (DEK_tlock) is not. */
    val tlockKeyDigest: ByteArray? = null,
    val shamirThreshold: Int? = null,
    val shamirTotalShares: Int? = null,
) {
    // ByteArray fields require custom equals/hashCode to avoid identity comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapsuleRecord) return false
        return id == other.id &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            createdByUser == other.createdByUser &&
            shape == other.shape &&
            state == other.state &&
            unlockAt == other.unlockAt &&
            cancelledAt == other.cancelledAt &&
            deliveredAt == other.deliveredAt &&
            (wrappedCapsuleKey == null && other.wrappedCapsuleKey == null ||
                wrappedCapsuleKey != null && wrappedCapsuleKey.contentEquals(other.wrappedCapsuleKey ?: ByteArray(0))) &&
            capsuleKeyFormat == other.capsuleKeyFormat &&
            tlockRound == other.tlockRound &&
            tlockChainId == other.tlockChainId &&
            (tlockWrappedKey == null && other.tlockWrappedKey == null ||
                tlockWrappedKey != null && tlockWrappedKey.contentEquals(other.tlockWrappedKey ?: ByteArray(0))) &&
            (tlockKeyDigest == null && other.tlockKeyDigest == null ||
                tlockKeyDigest != null && tlockKeyDigest.contentEquals(other.tlockKeyDigest ?: ByteArray(0))) &&
            shamirThreshold == other.shamirThreshold &&
            shamirTotalShares == other.shamirTotalShares
    }

    override fun hashCode(): Int = id.hashCode()
}
