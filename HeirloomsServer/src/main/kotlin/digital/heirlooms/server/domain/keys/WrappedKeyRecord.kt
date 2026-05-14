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
