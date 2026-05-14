package digital.heirlooms.server.domain.auth

import java.time.Instant
import java.util.UUID

data class InviteRecord(
    val id: UUID,
    val token: String,
    val createdBy: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val usedBy: UUID?,
)
