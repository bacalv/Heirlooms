package digital.heirlooms.server.routes.social

import digital.heirlooms.server.authUserId
import digital.heirlooms.server.representation.social.friendSharingKeyResponseJson
import digital.heirlooms.server.representation.social.toJson
import digital.heirlooms.server.service.social.SocialService
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
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

private val sharingMapper = ObjectMapper()

fun sharingKeyRoutes(socialService: SocialService): List<ContractRoute> = listOf(
    putSharingKeyRoute(socialService),
    getMySharingKeyRoute(socialService),
    getFriendSharingKeyRoute(socialService),
)

// PUT /sharing — upload or replace own sharing keypair
private fun putSharingKeyRoute(socialService: SocialService): ContractRoute =
    "/sharing" meta {
        summary = "Upload account-level sharing public key and wrapped private key"
    } bindContract PUT to { request: Request ->
        try {
            val userId = request.authUserId() ?: return@to Response(FORBIDDEN)
            val node = sharingMapper.readTree(request.bodyString())
            val pubkeyB64 = node?.get("pubkey")?.asText()
            val wrappedPrivkeyB64 = node?.get("wrappedPrivkey")?.asText()
            val wrapFormat = node?.get("wrapFormat")?.asText()
            if (pubkeyB64.isNullOrBlank() || wrappedPrivkeyB64.isNullOrBlank() || wrapFormat.isNullOrBlank())
                return@to Response(BAD_REQUEST).body("Missing required fields")
            when (val result = socialService.putSharingKey(userId, pubkeyB64, wrappedPrivkeyB64, wrapFormat)) {
                SocialService.PutSharingKeyResult.Ok -> Response(NO_CONTENT)
                is SocialService.PutSharingKeyResult.Invalid -> Response(BAD_REQUEST).body(result.message)
            }
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("putSharingKey failed: ${e.message}")
        }
    }

// GET /sharing/me — fetch own sharing key
private fun getMySharingKeyRoute(socialService: SocialService): ContractRoute =
    "/sharing/me" meta {
        summary = "Fetch own sharing keypair (pubkey + wrapped private key)"
    } bindContract GET to { request: Request ->
        try {
            val userId = request.authUserId() ?: return@to Response(FORBIDDEN)
            val record = socialService.getMySharingKey(userId)
                ?: return@to Response(NOT_FOUND)
            Response(OK).header("Content-Type", "application/json").body(record.toJson())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("getSharingKeyMe failed: ${e.message}")
        }
    }

// GET /sharing/{userId} — fetch a friend's sharing public key
private fun getFriendSharingKeyRoute(socialService: SocialService): ContractRoute {
    val userIdPath = Path.uuid().of("userId")
    return "/sharing" / userIdPath meta {
        summary = "Fetch a friend's sharing public key"
    } bindContract GET to { friendId: UUID ->
        { request: Request -> handleGetFriendSharingKey(friendId, request, socialService) }
    }
}

private fun handleGetFriendSharingKey(friendId: UUID, request: Request, socialService: SocialService): Response {
    return try {
        val requesterId = request.authUserId() ?: return Response(FORBIDDEN)
        when (val result = socialService.getFriendSharingKey(requesterId, friendId)) {
            is SocialService.GetFriendSharingKeyResult.Ok -> {
                Response(OK).header("Content-Type", "application/json")
                    .body(friendSharingKeyResponseJson(result.pubkeyBytes))
            }
            SocialService.GetFriendSharingKeyResult.NotFriends ->
                Response(FORBIDDEN).body("Not friends")
            SocialService.GetFriendSharingKeyResult.NotFound ->
                Response(NOT_FOUND)
        }
    } catch (e: Exception) {
        Response(INTERNAL_SERVER_ERROR).body("getFriendSharingKey failed: ${e.message}")
    }
}
