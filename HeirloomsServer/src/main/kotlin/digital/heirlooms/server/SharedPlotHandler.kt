package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.Base64
import java.util.UUID

private val sharedMapper = ObjectMapper()

fun sharedPlotRoutes(database: Database): List<ContractRoute> = listOf(
    getPlotKeyRoute(database),
    listMembersRoute(database),
    addMemberRoute(database),
    createInviteRoute(database),
    joinInfoRoute(database),
    joinRoute(database),
    listPendingInvitesRoute(database),
    confirmInviteRoute(database),
)

// ---- Plot key ---------------------------------------------------------------

private fun getPlotKeyRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "plot-key" meta {
        summary = "Get own wrapped plot key"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request -> handleGetPlotKey(plotId, request, database) }
    }
}

private fun handleGetPlotKey(plotId: UUID, request: Request, database: Database): Response {
    val plot = database.getPlotById(plotId) ?: return Response(NOT_FOUND)
    if (plot.visibility != "shared") return Response(FORBIDDEN).body("Plot is not a shared plot")
    val (keyBytes, fmt) = database.getPlotKey(plotId, request.authUserId())
        ?: return Response(NOT_FOUND)
    val node = JsonNodeFactory.instance.objectNode()
    node.put("wrappedPlotKey", Base64.getEncoder().encodeToString(keyBytes))
    node.put("plotKeyFormat", fmt)
    return Response(OK).header("Content-Type", "application/json").body(node.toString())
}

// ---- Members ----------------------------------------------------------------

private fun listMembersRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" meta {
        summary = "List members of a shared plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request -> handleListMembers(plotId, request, database) }
    }
}

private fun handleListMembers(plotId: UUID, request: Request, database: Database): Response {
    val members = database.listMembers(plotId, request.authUserId())
        ?: return Response(NOT_FOUND)
    val json = "[${members.joinToString(",") { m ->
        val node = JsonNodeFactory.instance.objectNode()
        node.put("userId", m.userId.toString())
        node.put("displayName", m.displayName)
        node.put("username", m.username)
        node.put("role", m.role)
        node.put("joinedAt", m.joinedAt.toString())
        node.toString()
    }}]"
    return Response(OK).header("Content-Type", "application/json").body(json)
}

private fun addMemberRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" meta {
        summary = "Invite a friend to a shared plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAddMember(plotId, request, database) }
    }
}

private fun handleAddMember(plotId: UUID, request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val userIdStr = node.get("userId")?.asText()
        ?: return Response(BAD_REQUEST).body("userId is required")
    val newUserId = try { UUID.fromString(userIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("userId is not a valid UUID") }
    val wrappedKey = node.get("wrappedPlotKey")?.asText()
        ?: return Response(BAD_REQUEST).body("wrappedPlotKey is required")
    val plotKeyFormat = node.get("plotKeyFormat")?.asText()
        ?: return Response(BAD_REQUEST).body("plotKeyFormat is required")
    val keyBytes = try { Base64.getDecoder().decode(wrappedKey) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("wrappedPlotKey is not valid base64") }

    return when (database.addMember(plotId, newUserId, keyBytes, plotKeyFormat, request.authUserId())) {
        Database.AddMemberResult.Success       -> Response(CREATED)
        Database.AddMemberResult.NotMember     -> Response(NOT_FOUND)
        Database.AddMemberResult.NotFriends    -> Response(BAD_REQUEST).body("You can only invite friends")
        Database.AddMemberResult.AlreadyMember -> Response(CONFLICT).body("User is already a member")
    }
}

// ---- Invite link flow -------------------------------------------------------

private fun createInviteRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "invites" meta {
        summary = "Generate a 48-hour invite token for a shared plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleCreateInvite(plotId, request, database) }
    }
}

private fun handleCreateInvite(plotId: UUID, request: Request, database: Database): Response {
    val invite = database.createInvite(plotId, request.authUserId())
        ?: return Response(NOT_FOUND)
    val node = JsonNodeFactory.instance.objectNode()
    node.put("token", invite.token)
    node.put("expiresAt", invite.expiresAt.toString())
    return Response(CREATED).header("Content-Type", "application/json").body(node.toString())
}

private fun joinInfoRoute(database: Database): ContractRoute =
    "/plots/join-info" meta {
        summary = "Get plot info for an invite token"
    } bindContract GET to { request: Request ->
        val token = request.query("token")?.takeIf { it.isNotBlank() }
            ?: return@to Response(BAD_REQUEST).body("token query param required")
        val info = database.getInviteInfo(token)
            ?: return@to Response(NOT_FOUND).body("Invite not found or expired")
        val node = JsonNodeFactory.instance.objectNode()
        node.put("plotId", info.plotId.toString())
        node.put("plotName", info.plotName)
        node.put("inviterDisplayName", info.inviterDisplayName)
        node.put("inviterUserId", info.inviterUserId.toString())
        Response(OK).header("Content-Type", "application/json").body(node.toString())
    }

private fun joinRoute(database: Database): ContractRoute =
    "/plots/join" meta {
        summary = "Redeem an invite token (step 1: register recipient pubkey)"
    } bindContract POST to { request: Request -> handleJoin(request, database) }

private fun handleJoin(request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val token = node.get("token")?.asText()?.takeIf { it.isNotBlank() }
        ?: return Response(BAD_REQUEST).body("token is required")
    val recipientPubkey = node.get("recipientSharingPubkey")?.asText()?.takeIf { it.isNotBlank() }
        ?: return Response(BAD_REQUEST).body("recipientSharingPubkey is required")

    return when (val result = database.redeemInvite(token, request.authUserId(), recipientPubkey)) {
        is Database.RedeemInviteResult.Pending -> {
            val resp = JsonNodeFactory.instance.objectNode()
            resp.put("status", "pending")
            resp.put("inviteId", result.inviteId.toString())
            resp.put("inviterDisplayName", result.inviterDisplayName)
            Response(OK).header("Content-Type", "application/json").body(resp.toString())
        }
        Database.RedeemInviteResult.AlreadyMember ->
            Response(CONFLICT).body("You are already a member of this plot")
        Database.RedeemInviteResult.Invalid ->
            Response(NOT_FOUND).body("Invite not found, expired, or already used")
    }
}

private fun listPendingInvitesRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" / "pending" meta {
        summary = "List pending joins awaiting key wrap confirmation"
    } bindContract GET to { plotId: UUID, _s1: String, _s2: String ->
        { request: Request ->
            val pending = database.listPendingInvites(plotId, request.authUserId())
            val arr = sharedMapper.writeValueAsString(pending)
            Response(OK).header("Content-Type", "application/json").body(arr)
        }
    }
}

private fun confirmInviteRoute(database: Database): ContractRoute {
    val plotId = Path.uuid().of("id")
    val inviteId = Path.uuid().of("inviteId")
    return "/plots" / plotId / "members" / "pending" / inviteId / "confirm" meta {
        summary = "Confirm a pending join by supplying the wrapped plot key for the recipient"
    } bindContract POST to { pId: UUID, _s1: String, _s2: String, iId: UUID, _s3: String ->
        { request: Request -> handleConfirmInvite(pId, iId, request, database) }
    }
}

private fun handleConfirmInvite(plotId: UUID, inviteId: UUID, request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val wrappedKey = node.get("wrappedPlotKey")?.asText()
        ?: return Response(BAD_REQUEST).body("wrappedPlotKey is required")
    val plotKeyFormat = node.get("plotKeyFormat")?.asText()
        ?: return Response(BAD_REQUEST).body("plotKeyFormat is required")
    val keyBytes = try { Base64.getDecoder().decode(wrappedKey) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("wrappedPlotKey is not valid base64") }

    return if (database.confirmInvite(inviteId, plotId, keyBytes, plotKeyFormat, request.authUserId()))
        Response(NO_CONTENT)
    else
        Response(NOT_FOUND)
}
