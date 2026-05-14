package digital.heirlooms.server.domain.keys

import java.time.Instant
import java.util.UUID

data class WrappedKeyRecord(
    val id: UUID,
    val deviceId: String,
    val deviceLabel: String,
    val deviceKind: String,
    val pubkeyFormat: String,
    val pubkey: ByteArray,
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val retiredAt: Instant?,
)

data class RecoveryPassphraseRecord(
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val argon2Params: String,
    val salt: ByteArray,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PendingDeviceLinkRecord(
    val id: UUID,
    val oneTimeCode: String,
    val expiresAt: Instant,
    val state: String,
    val newDeviceId: String?,
    val newDeviceLabel: String?,
    val newDeviceKind: String?,
    val newPubkeyFormat: String?,
    val newPubkey: ByteArray?,
    val wrappedMasterKey: ByteArray?,
    val wrapFormat: String?,
    val userId: UUID? = null,
    val webSessionId: String? = null,
    val rawSessionToken: String? = null,
    val sessionExpiresAt: Instant? = null,
)

data class AccountSharingKeyRecord(
    val userId: UUID,
    val pubkey: ByteArray,
    val wrappedPrivkey: ByteArray,
    val wrapFormat: String,
)

data class FriendRecord(
    val userId: UUID,
    val username: String,
    val displayName: String,
)
