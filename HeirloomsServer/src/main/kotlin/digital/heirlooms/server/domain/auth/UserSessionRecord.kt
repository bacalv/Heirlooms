package digital.heirlooms.server.domain.auth

import java.time.Instant
import java.util.UUID

data class UserSessionRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: ByteArray,
    val deviceKind: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Instant,
)
