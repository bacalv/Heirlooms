package digital.heirlooms.server.domain.auth

import java.time.Instant
import java.util.UUID

val FOUNDING_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

data class UserRecord(
    val id: UUID,
    val username: String,
    val displayName: String,
    val authVerifier: ByteArray?,
    val authSalt: ByteArray?,
    val createdAt: Instant,
)

data class UserSessionRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: ByteArray,
    val deviceKind: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Instant,
)

data class InviteRecord(
    val id: UUID,
    val token: String,
    val createdBy: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val usedBy: UUID?,
)
