package digital.heirlooms.server.routes.social

import digital.heirlooms.server.authUserId
import digital.heirlooms.server.representation.social.toFriendsJson
import digital.heirlooms.server.service.social.SocialService
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK

fun friendsRoutes(socialService: SocialService): List<ContractRoute> = listOf(
    listFriendsRoute(socialService),
)

private fun listFriendsRoute(socialService: SocialService): ContractRoute =
    "/friends" meta {
        summary = "List friends of the authenticated user"
    } bindContract GET to { request: Request ->
        try {
            val userId = request.authUserId()
            val friends = socialService.listFriends(userId)
            Response(OK).header("Content-Type", "application/json").body(friends.toFriendsJson())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("listFriends failed: ${e.message}")
        }
    }
