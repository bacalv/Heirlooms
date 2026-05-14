package digital.heirlooms.server.representation.social

import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import digital.heirlooms.server.domain.keys.FriendRecord
import digital.heirlooms.server.representation.responseMapper
import java.util.Base64

private val enc = Base64.getEncoder()

private data class AccountSharingKeyResponse(
    val pubkey: String,
    val wrappedPrivkey: String,
    val wrapFormat: String,
)

private data class FriendResponse(
    val userId: String,
    val username: String,
    val displayName: String,
)

private data class FriendSharingKeyResponse(
    val pubkey: String,
)

fun AccountSharingKeyRecord.toJson(): String =
    responseMapper.writeValueAsString(
        AccountSharingKeyResponse(
            pubkey = enc.encodeToString(pubkey),
            wrappedPrivkey = enc.encodeToString(wrappedPrivkey),
            wrapFormat = wrapFormat,
        )
    )

fun List<FriendRecord>.toFriendsJson(): String =
    responseMapper.writeValueAsString(
        map { f ->
            FriendResponse(
                userId = f.userId.toString(),
                username = f.username,
                displayName = f.displayName,
            )
        }
    )

fun friendSharingKeyResponseJson(pubkeyBytes: ByteArray): String =
    responseMapper.writeValueAsString(FriendSharingKeyResponse(pubkey = enc.encodeToString(pubkeyBytes)))
