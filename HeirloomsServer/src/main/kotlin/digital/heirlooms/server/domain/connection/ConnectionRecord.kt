package digital.heirlooms.server.domain.connection

import java.time.Instant
import java.util.UUID

data class ConnectionRecord(
    val id: UUID,
    val ownerUserId: UUID,
    val contactUserId: UUID?,
    val displayName: String,
    val email: String?,
    val sharingPubkey: String?,
    val roles: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
