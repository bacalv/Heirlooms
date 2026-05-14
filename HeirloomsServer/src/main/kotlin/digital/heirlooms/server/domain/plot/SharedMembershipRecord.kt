package digital.heirlooms.server.domain.plot

import java.time.Instant
import java.util.UUID

data class SharedMembershipRecord(
    val plotId: UUID,
    val plotName: String,
    val ownerUserId: UUID?,
    val ownerDisplayName: String?,
    val role: String,
    val status: String,
    val localName: String?,
    val joinedAt: Instant,
    val leftAt: Instant?,
    val plotStatus: String,
    val tombstonedAt: Instant?,
    val tombstonedBy: UUID?,
)
