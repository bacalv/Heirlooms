package digital.heirlooms.server.service.social

import digital.heirlooms.server.Database
import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import digital.heirlooms.server.domain.keys.FriendRecord
import java.util.Base64
import java.util.UUID

/**
 * Encapsulates social graph operations: listing friends, and sharing-key
 * registration and retrieval. The friend-access guard for the friend key
 * lookup lives here so handlers stay free of authorization logic.
 */
class SocialService(
    private val database: Database,
) {
    fun listFriends(userId: UUID): List<FriendRecord> = database.listFriends(userId)

    // ---- Sharing key -----------------------------------------------------------

    sealed class PutSharingKeyResult {
        object Ok : PutSharingKeyResult()
        data class Invalid(val message: String) : PutSharingKeyResult()
    }

    fun putSharingKey(
        userId: UUID,
        pubkeyB64: String,
        wrappedPrivkeyB64: String,
        wrapFormat: String,
    ): PutSharingKeyResult {
        val dec = Base64.getDecoder()
        val pubkey = runCatching { dec.decode(pubkeyB64) }.getOrNull()
            ?: return PutSharingKeyResult.Invalid("pubkey is not valid Base64")
        val wrappedPrivkey = runCatching { dec.decode(wrappedPrivkeyB64) }.getOrNull()
            ?: return PutSharingKeyResult.Invalid("wrappedPrivkey is not valid Base64")
        database.upsertSharingKey(userId, pubkey, wrappedPrivkey, wrapFormat)
        return PutSharingKeyResult.Ok
    }

    fun getMySharingKey(userId: UUID): AccountSharingKeyRecord? = database.getSharingKey(userId)

    sealed class GetFriendSharingKeyResult {
        data class Ok(val pubkeyBytes: ByteArray) : GetFriendSharingKeyResult()
        object NotFriends : GetFriendSharingKeyResult()
        object NotFound : GetFriendSharingKeyResult()
    }

    fun getFriendSharingKey(requesterId: UUID, friendId: UUID): GetFriendSharingKeyResult {
        if (!database.areFriends(requesterId, friendId)) return GetFriendSharingKeyResult.NotFriends
        val record = database.getSharingKey(friendId) ?: return GetFriendSharingKeyResult.NotFound
        return GetFriendSharingKeyResult.Ok(record.pubkey)
    }
}
