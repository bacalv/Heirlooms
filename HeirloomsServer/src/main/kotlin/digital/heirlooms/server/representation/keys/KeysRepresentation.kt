package digital.heirlooms.server.representation.keys

import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.util.Base64

private val keysRepMapper = ObjectMapper()
private val keysRepEnc = Base64.getEncoder()

fun WrappedKeyRecord.toJson(): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("id", id.toString())
    node.put("deviceId", deviceId)
    node.put("deviceLabel", deviceLabel)
    node.put("deviceKind", deviceKind)
    node.put("pubkeyFormat", pubkeyFormat)
    node.put("pubkey", keysRepEnc.encodeToString(pubkey))
    node.put("wrappedMasterKey", keysRepEnc.encodeToString(wrappedMasterKey))
    node.put("wrapFormat", wrapFormat)
    node.put("createdAt", createdAt.toString())
    node.put("lastUsedAt", lastUsedAt.toString())
    if (retiredAt != null) node.put("retiredAt", retiredAt.toString()) else node.putNull("retiredAt")
    return node.toString()
}

fun RecoveryPassphraseRecord.toJson(): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("wrappedMasterKey", keysRepEnc.encodeToString(wrappedMasterKey))
    node.put("wrapFormat", wrapFormat)
    node.set<com.fasterxml.jackson.databind.JsonNode>("argon2Params", keysRepMapper.readTree(argon2Params))
    node.put("salt", keysRepEnc.encodeToString(salt))
    node.put("createdAt", createdAt.toString())
    node.put("updatedAt", updatedAt.toString())
    return node.toString()
}
