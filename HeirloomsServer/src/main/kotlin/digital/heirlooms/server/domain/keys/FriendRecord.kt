package digital.heirlooms.server.domain.keys

import java.util.UUID

data class FriendRecord(
    val userId: UUID,
    val username: String,
    val displayName: String,
)
