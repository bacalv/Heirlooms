package digital.heirlooms.server.service.social

import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import digital.heirlooms.server.domain.keys.FriendRecord
import digital.heirlooms.server.repository.social.SocialRepository
import java.util.Base64
import java.util.UUID

/**
 * Encapsulates social graph operations: listing friends, and sharing-key
 * registration and retrieval. The friend-access guard for the friend key
 * lookup lives here so handlers stay free of authorization logic.
 */
class SocialService(
    private val socialRepo: SocialRepository,
) {
    fun listFriends(userId: UUID): List<FriendRecord> = socialRepo.listFriends(userId)

    // ---- Sharing key -----------------------------------------------------------

    sealed class PutSharingKeyResult {
        object Ok : PutSharingKeyResult()
        /** A sharing key is already registered for this user; the upload was rejected. */
        object AlreadyExists : PutSharingKeyResult()
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
        val inserted = socialRepo.insertSharingKeyIfAbsent(userId, pubkey, wrappedPrivkey, wrapFormat)
        return if (inserted) PutSharingKeyResult.Ok else PutSharingKeyResult.AlreadyExists
    }

    fun getMySharingKey(userId: UUID): AccountSharingKeyRecord? = socialRepo.getSharingKey(userId)

    sealed class GetFriendSharingKeyResult {
        data class Ok(val pubkeyBytes: ByteArray) : GetFriendSharingKeyResult()
        object NotFriends : GetFriendSharingKeyResult()
        object NotFound : GetFriendSharingKeyResult()
    }

    fun getFriendSharingKey(requesterId: UUID, friendId: UUID): GetFriendSharingKeyResult {
        if (!socialRepo.areFriends(requesterId, friendId)) return GetFriendSharingKeyResult.NotFriends
        val record = socialRepo.getSharingKey(friendId) ?: return GetFriendSharingKeyResult.NotFound
        return GetFriendSharingKeyResult.Ok(record.pubkey)
    }
}
