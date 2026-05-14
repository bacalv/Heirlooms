package digital.heirlooms.server.representation.social

import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import digital.heirlooms.server.domain.keys.FriendRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.util.Base64

private val socialRepMapper = ObjectMapper()
private val socialRepEnc = Base64.getEncoder()

fun AccountSharingKeyRecord.toJson(): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("pubkey", socialRepEnc.encodeToString(pubkey))
    node.put("wrappedPrivkey", socialRepEnc.encodeToString(wrappedPrivkey))
    node.put("wrapFormat", wrapFormat)
    return node.toString()
}

fun List<FriendRecord>.toFriendsJson(): String {
    val arr = socialRepMapper.createArrayNode()
    forEach { f ->
        val node = socialRepMapper.createObjectNode()
        node.put("userId", f.userId.toString())
        node.put("username", f.username)
        node.put("displayName", f.displayName)
        arr.add(node)
    }
    return arr.toString()
}

fun friendSharingKeyResponseJson(pubkeyBytes: ByteArray): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("pubkey", socialRepEnc.encodeToString(pubkeyBytes))
    return node.toString()
}
