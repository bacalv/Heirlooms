package digital.heirlooms.server

import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.Base64
import java.util.UUID

private val sharingMapper = ObjectMapper()
private val sharingEnc = Base64.getEncoder()
private val sharingDec = Base64.getDecoder()

fun sharingKeyRoutes(database: Database): List<ContractRoute> = listOf(
    putSharingKeyRoute(database),
    getMySharingKeyRoute(database),
    getFriendSharingKeyRoute(database),
)

// PUT /sharing — upload or replace own sharing keypair
private fun putSharingKeyRoute(database: Database): ContractRoute =
    "/sharing" meta {
        summary = "Upload account-level sharing public key and wrapped private key"
    } bindContract PUT to { request: Request ->
        try {
            val userId = request.authUserId()
                ?: return@to Response(FORBIDDEN)
            val node = sharingMapper.readTree(request.bodyString())
            val pubkeyB64 = node?.get("pubkey")?.asText()
            val wrappedPrivkeyB64 = node?.get("wrappedPrivkey")?.asText()
            val wrapFormat = node?.get("wrapFormat")?.asText()
            if (pubkeyB64.isNullOrBlank() || wrappedPrivkeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing required fields")
            val pubkey = runCatching { sharingDec.decode(pubkeyB64) }.getOrNull()
                ?: return@to Response(BAD_REQUEST).body("pubkey is not valid Base64")
            val wrappedPrivkey = runCatching { sharingDec.decode(wrappedPrivkeyB64) }.getOrNull()
                ?: return@to Response(BAD_REQUEST).body("wrappedPrivkey is not valid Base64")
            database.upsertSharingKey(userId, pubkey, wrappedPrivkey, wrapFormat)
            Response(NO_CONTENT)
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("putSharingKey failed: ${e.message}")
        }
    }

// GET /sharing/me — fetch own sharing key (pubkey + wrapped private key for vault unlock)
private fun getMySharingKeyRoute(database: Database): ContractRoute =
    "/sharing/me" meta {
        summary = "Fetch own sharing keypair (pubkey + wrapped private key)"
    } bindContract GET to { request: Request ->
        try {
            val userId = request.authUserId()
                ?: return@to Response(FORBIDDEN)
            val record = database.getSharingKey(userId)
                ?: return@to Response(NOT_FOUND)
            Response(OK).header("Content-Type", "application/json")
                .body(record.toJson())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("getSharingKeyMe failed: ${e.message}")
        }
    }

// GET /sharing/{userId} — fetch a friend's sharing public key (pubkey only, no private key)
private fun getFriendSharingKeyRoute(database: Database): ContractRoute {
    val userIdPath = Path.uuid().of("userId")
    return "/sharing" / userIdPath meta {
        summary = "Fetch a friend's sharing public key"
    } bindContract GET to { friendId: UUID ->
        { request: Request -> handleGetFriendSharingKey(friendId, request, database) }
    }
}

private fun handleGetFriendSharingKey(friendId: UUID, request: Request, database: Database): Response {
    return try {
        val requesterId = request.authUserId()
            ?: return Response(FORBIDDEN)
        if (!database.areFriends(requesterId, friendId))
            return Response(FORBIDDEN).body("Not friends")
        val record = database.getSharingKey(friendId)
            ?: return Response(NOT_FOUND)
        val node = JsonNodeFactory.instance.objectNode()
        node.put("pubkey", sharingEnc.encodeToString(record.pubkey))
        Response(OK).header("Content-Type", "application/json").body(node.toString())
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("getFriendSharingKey failed: ${e.message}")
    }
}

internal fun AccountSharingKeyRecord.toJson(): String {
    val enc = Base64.getEncoder()
    val node = JsonNodeFactory.instance.objectNode()
    node.put("pubkey", enc.encodeToString(pubkey))
    node.put("wrappedPrivkey", enc.encodeToString(wrappedPrivkey))
    node.put("wrapFormat", wrapFormat)
    return node.toString()
}
