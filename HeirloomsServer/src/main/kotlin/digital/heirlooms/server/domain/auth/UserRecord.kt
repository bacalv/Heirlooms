package digital.heirlooms.server.domain.auth

import java.time.Instant
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val username: String,
    val displayName: String,
    val authVerifier: ByteArray?,
    val authSalt: ByteArray?,
    val createdAt: Instant,
)
