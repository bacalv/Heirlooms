package digital.heirlooms.server

import digital.heirlooms.server.service.social.SocialService
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK

private val friendsMapper = ObjectMapper()

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
            val arr = friendsMapper.createArrayNode()
            friends.forEach { f ->
                val node = friendsMapper.createObjectNode()
                node.put("userId", f.userId.toString())
                node.put("username", f.username)
                node.put("displayName", f.displayName)
                arr.add(node)
            }
            Response(OK).header("Content-Type", "application/json").body(arr.toString())
        } catch (e: Exception) {
            Response(INTERNAL_SERVER_ERROR).body("listFriends failed: ${e.message}")
        }
    }
