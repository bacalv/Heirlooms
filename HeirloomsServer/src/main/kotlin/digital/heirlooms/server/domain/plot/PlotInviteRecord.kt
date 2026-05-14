package digital.heirlooms.server.domain.plot

import java.time.Instant
import java.util.UUID

data class PlotInviteRecord(
    val id: UUID,
    val plotId: UUID,
    val createdBy: UUID,
    val token: String,
    val recipientUserId: UUID?,
    val recipientPubkey: String?,
    val usedBy: UUID?,
    val expiresAt: Instant,
    val createdAt: Instant,
)
