package digital.heirlooms.server.domain.connection

import java.time.Instant
import java.util.UUID

data class NominationRecord(
    val id: UUID,
    val connectionId: UUID,
    val ownerUserId: UUID,
    val status: String,
    val offeredAt: Instant,
    val respondedAt: Instant?,
    val revokedAt: Instant?,
    val message: String?,
)
