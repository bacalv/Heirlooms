package digital.heirlooms.server.representation.auth

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.time.Instant
import java.util.Base64
import java.util.UUID

fun sessionTokenJson(token: String, userId: UUID, expiresAt: Instant): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("session_token", token)
    node.put("user_id", userId.toString())
    node.put("expires_at", expiresAt.toString())
    return node.toString()
}

fun challengeResponseJson(salt: ByteArray): String {
    val urlEnc = Base64.getUrlEncoder().withoutPadding()
    return """{"auth_salt":"${urlEnc.encodeToString(salt)}"}"""
}

fun inviteResponseJson(token: String, expiresAt: Instant): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("token", token)
    node.put("expires_at", expiresAt.toString())
    return node.toString()
}

fun pairingInitiateResponseJson(code: String, expiresAt: Instant): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("code", code)
    node.put("expires_at", expiresAt.toString())
    return node.toString()
}

fun pairingStatusCompleteJson(
    sessionToken: String,
    wrappedMasterKey: ByteArray,
    wrapFormat: String,
    expiresAt: Instant?,
): String {
    val urlEnc = Base64.getUrlEncoder().withoutPadding()
    val node = JsonNodeFactory.instance.objectNode()
    node.put("state", "complete")
    node.put("session_token", sessionToken)
    node.put("wrapped_master_key", urlEnc.encodeToString(wrappedMasterKey))
    node.put("wrap_format", wrapFormat)
    node.put("expires_at", expiresAt?.toString())
    return node.toString()
}
