package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.GONE
import org.http4k.core.Status.Companion.NOT_FOUND
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
    leavePlotRoute(database),
    leavePlotPostRoute(database),
    acceptInviteRoute(database),
    rejoinPlotRoute(database),
    restorePlotRoute(database),
    transferOwnershipRoute(database),
    setPlotStatusRoute(database),
    listSharedMembershipsRoute(database),
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
        node.put("status", m.status)
        if (m.localName != null) node.put("localName", m.localName) else node.putNull("localName")
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

// ---- Leave plot -------------------------------------------------------------

private fun leavePlotHandler(plotId: UUID, request: Request, database: Database): Response =
    when (database.leavePlot(plotId, request.authUserId())) {
        Database.LeavePlotResult.Success           -> Response(NO_CONTENT)
        Database.LeavePlotResult.MustTransferFirst ->
            Response(FORBIDDEN).body("Owner must transfer ownership before leaving")
        Database.LeavePlotResult.NotFound          -> Response(NOT_FOUND)
    }

private fun leavePlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" / "me" meta {
        summary = "Leave a shared plot (backward-compat DELETE alias)"
    } bindContract DELETE to { plotId: UUID, _s1: String, _s2: String ->
        { request: Request -> leavePlotHandler(plotId, request, database) }
    }
}

private fun leavePlotPostRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "leave" meta {
        summary = "Leave a shared plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> leavePlotHandler(plotId, request, database) }
    }
}

// ---- Accept invitation -------------------------------------------------------

private fun acceptInviteRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "accept" meta {
        summary = "Accept a plot invitation; body: {\"localName\": \"...\"}"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAcceptInvite(plotId, request, database) }
    }
}

private fun handleAcceptInvite(plotId: UUID, request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val localName = node.get("localName")?.takeIf { !it.isNull }?.asText()?.trim()
        ?: return Response(BAD_REQUEST).body("localName is required")
    if (localName.isBlank()) return Response(BAD_REQUEST).body("localName must not be blank")

    return when (database.acceptInvite(plotId, request.authUserId(), localName)) {
        Database.AcceptInviteResult.Success      -> Response(NO_CONTENT)
        Database.AcceptInviteResult.NotInvited   -> Response(NOT_FOUND).body("No pending invitation for this plot")
        Database.AcceptInviteResult.AlreadyJoined -> Response(CONFLICT).body("Already a member of this plot")
    }
}

// ---- Rejoin plot -------------------------------------------------------------

private fun rejoinPlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "rejoin" meta {
        summary = "Rejoin a plot after leaving; body: {\"localName\": \"...\"} (optional)"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleRejoin(plotId, request, database) }
    }
}

private fun handleRejoin(plotId: UUID, request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
    val localName = node?.get("localName")?.takeIf { !it.isNull }?.asText()?.trim()

    return when (database.rejoinPlot(plotId, request.authUserId(), localName)) {
        Database.RejoinResult.Success       -> Response(NO_CONTENT)
        Database.RejoinResult.NotLeft       -> Response(NOT_FOUND).body("No prior membership in this plot")
        Database.RejoinResult.PlotTombstoned -> Response(GONE).body("Plot has been removed")
    }
}

// ---- Restore tombstoned plot ------------------------------------------------

private fun restorePlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "restore" meta {
        summary = "Restore a tombstoned plot (only for the member who triggered the tombstone)"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request ->
            when (database.restorePlot(plotId, request.authUserId())) {
                Database.RestorePlotResult.Success       -> Response(NO_CONTENT)
                Database.RestorePlotResult.NotTombstoned -> Response(NOT_FOUND).body("Plot is not tombstoned")
                Database.RestorePlotResult.NotAuthorized -> Response(FORBIDDEN).body("Only the member who triggered removal can restore")
                Database.RestorePlotResult.WindowExpired -> Response(GONE).body("Restore window has expired")
            }
        }
    }
}

// ---- Transfer ownership ------------------------------------------------------

private fun transferOwnershipRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "transfer" meta {
        summary = "Transfer ownership to another member; body: {\"newOwnerId\": \"uuid\"}"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleTransferOwnership(plotId, request, database) }
    }
}

private fun handleTransferOwnership(plotId: UUID, request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val newOwnerIdStr = node.get("newOwnerId")?.asText()
        ?: return Response(BAD_REQUEST).body("newOwnerId is required")
    val newOwnerId = try { UUID.fromString(newOwnerIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("newOwnerId is not a valid UUID") }

    return when (database.transferOwnership(plotId, newOwnerId, request.authUserId())) {
        Database.TransferOwnershipResult.Success        -> Response(NO_CONTENT)
        Database.TransferOwnershipResult.NotOwner       -> Response(FORBIDDEN).body("Only the owner can transfer ownership")
        Database.TransferOwnershipResult.TargetNotMember -> Response(BAD_REQUEST).body("Target user is not an active member")
        Database.TransferOwnershipResult.NotFound       -> Response(NOT_FOUND)
    }
}

// ---- Open / close plot ------------------------------------------------------

private fun setPlotStatusRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "status" meta {
        summary = "Set plot open/closed; body: {\"status\": \"open\"|\"closed\"}"
    } bindContract PATCH to { plotId: UUID, _: String ->
        { request: Request -> handleSetPlotStatus(plotId, request, database) }
    }
}

private fun handleSetPlotStatus(plotId: UUID, request: Request, database: Database): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val status = node.get("status")?.asText()
        ?: return Response(BAD_REQUEST).body("status is required")

    return when (val result = database.setPlotStatus(plotId, status, request.authUserId())) {
        Database.SetPlotStatusResult.Success              -> Response(NO_CONTENT)
        Database.SetPlotStatusResult.NotOwner             -> Response(FORBIDDEN).body("Only the owner can change plot status")
        Database.SetPlotStatusResult.NotFound             -> Response(NOT_FOUND)
        is Database.SetPlotStatusResult.InvalidStatus     -> Response(BAD_REQUEST).body("Invalid status: ${result.status}")
    }
}

// ---- List all shared memberships for current user ---------------------------

private fun listSharedMembershipsRoute(database: Database): ContractRoute =
    "/plots/shared" meta {
        summary = "List all shared plot memberships for current user (all statuses)"
    } bindContract GET to { request: Request ->
        val memberships = database.listSharedMemberships(request.authUserId())
        val factory = JsonNodeFactory.instance
        val arr = factory.arrayNode()
        memberships.forEach { m ->
            val node = factory.objectNode()
            node.put("plotId", m.plotId.toString())
            node.put("plotName", m.plotName)
            if (m.ownerUserId != null) node.put("ownerUserId", m.ownerUserId.toString())
            else node.putNull("ownerUserId")
            if (m.ownerDisplayName != null) node.put("ownerDisplayName", m.ownerDisplayName)
            else node.putNull("ownerDisplayName")
            node.put("role", m.role)
            node.put("status", m.status)
            if (m.localName != null) node.put("localName", m.localName) else node.putNull("localName")
            node.put("joinedAt", m.joinedAt.toString())
            if (m.leftAt != null) node.put("leftAt", m.leftAt.toString()) else node.putNull("leftAt")
            node.put("plotStatus", m.plotStatus)
            if (m.tombstonedAt != null) node.put("tombstonedAt", m.tombstonedAt.toString()) else node.putNull("tombstonedAt")
            if (m.tombstonedBy != null) node.put("tombstonedBy", m.tombstonedBy.toString()) else node.putNull("tombstonedBy")
            arr.add(node)
        }
        Response(OK).header("Content-Type", "application/json").body(sharedMapper.writeValueAsString(arr))
    }
