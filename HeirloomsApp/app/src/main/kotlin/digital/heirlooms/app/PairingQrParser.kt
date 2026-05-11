package digital.heirlooms.app

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses the JSON payload encoded in the web client's pairing QR code.
 * Format: {"session_id":"<uuid>","pubkey":"<base64url spki>"}
 */
object PairingQrParser {

    data class PairingQrPayload(val sessionId: String, val pubkey: String)

    sealed class ParseResult {
        data class Success(val payload: PairingQrPayload) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    fun parse(json: String): ParseResult = try {
        val obj = JSONObject(json)
        val sessionId = obj.optString("session_id").takeIf { it.isNotBlank() }
            ?: return ParseResult.Error("Missing session_id")
        val pubkey = obj.optString("pubkey").takeIf { it.isNotBlank() }
            ?: return ParseResult.Error("Missing pubkey")
        ParseResult.Success(PairingQrPayload(sessionId = sessionId, pubkey = pubkey))
    } catch (_: JSONException) {
        ParseResult.Error("Invalid JSON")
    }
}
