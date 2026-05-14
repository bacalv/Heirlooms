package digital.heirlooms.server.domain.keys

import java.util.UUID

data class AccountSharingKeyRecord(
    val userId: UUID,
    val pubkey: ByteArray,
    val wrappedPrivkey: ByteArray,
    val wrapFormat: String,
)
