package digital.heirlooms.server.domain.keys

import java.time.Instant
import java.util.UUID

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
