package digital.heirlooms.server

import digital.heirlooms.server.repository.plot.PlotMemberRepository
import digital.heirlooms.server.representation.plot.inviteResponseJson
import digital.heirlooms.server.representation.plot.joinInfoResponseJson
import digital.heirlooms.server.representation.plot.pendingJoinResponseJson
import digital.heirlooms.server.representation.plot.plotKeyResponseJson
import digital.heirlooms.server.representation.plot.toJson
import digital.heirlooms.server.service.plot.SharedPlotService
import com.fasterxml.jackson.databind.ObjectMapper
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

fun sharedPlotRoutes(sharedPlotService: SharedPlotService): List<ContractRoute> = listOf(
    getPlotKeyRoute(sharedPlotService),
    listMembersRoute(sharedPlotService),
    addMemberRoute(sharedPlotService),
    leavePlotRoute(sharedPlotService),
    leavePlotPostRoute(sharedPlotService),
    acceptInviteRoute(sharedPlotService),
    rejoinPlotRoute(sharedPlotService),
    restorePlotRoute(sharedPlotService),
    transferOwnershipRoute(sharedPlotService),
    setPlotStatusRoute(sharedPlotService),
    listSharedMembershipsRoute(sharedPlotService),
    createInviteRoute(sharedPlotService),
    joinInfoRoute(sharedPlotService),
    joinRoute(sharedPlotService),
    listPendingInvitesRoute(sharedPlotService),
    confirmInviteRoute(sharedPlotService),
)

// ---- Plot key ---------------------------------------------------------------

private fun getPlotKeyRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "plot-key" meta {
        summary = "Get own wrapped plot key"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            when (val result = sharedPlotService.getPlotKey(plotId, request.authUserId())) {
                is SharedPlotService.GetPlotKeyResult.Success -> {
                    Response(OK).header("Content-Type", "application/json")
                        .body(plotKeyResponseJson(result.wrappedKey, result.format))
                }
                SharedPlotService.GetPlotKeyResult.NotShared -> Response(FORBIDDEN)
                SharedPlotService.GetPlotKeyResult.NotFound -> Response(NOT_FOUND)
            }
        }
    }
}

// ---- Members ----------------------------------------------------------------

private fun listMembersRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" meta {
        summary = "List members of a shared plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val members = sharedPlotService.listMembers(plotId, request.authUserId())
            if (members == null) {
                Response(NOT_FOUND)
            } else {
                val json = "[${members.joinToString(",") { m -> m.toJson() }}]"
                Response(OK).header("Content-Type", "application/json").body(json)
            }
        }
    }
}

private fun addMemberRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" meta {
        summary = "Invite a friend to a shared plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAddMember(plotId, request, sharedPlotService) }
    }
}

private fun handleAddMember(plotId: UUID, request: Request, sharedPlotService: SharedPlotService): Response {
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
    return when (sharedPlotService.addMember(plotId, newUserId, keyBytes, plotKeyFormat, request.authUserId())) {
        PlotMemberRepository.AddMemberResult.Success       -> Response(CREATED)
        PlotMemberRepository.AddMemberResult.NotMember     -> Response(NOT_FOUND)
        PlotMemberRepository.AddMemberResult.NotFriends    -> Response(BAD_REQUEST).body("You can only invite friends")
        PlotMemberRepository.AddMemberResult.AlreadyMember -> Response(CONFLICT).body("User is already a member")
    }
}

// ---- Invite link flow -------------------------------------------------------

private fun createInviteRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "invites" meta {
        summary = "Generate a 48-hour invite token for a shared plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request ->
            val invite = sharedPlotService.createInvite(plotId, request.authUserId())
            if (invite == null) {
                Response(NOT_FOUND)
            } else {
                Response(CREATED).header("Content-Type", "application/json")
                    .body(inviteResponseJson(invite.token, invite.expiresAt))
            }
        }
    }
}

private fun joinInfoRoute(sharedPlotService: SharedPlotService): ContractRoute =
    "/plots/join-info" meta {
        summary = "Get plot info for an invite token"
    } bindContract GET to { request: Request ->
        val token = request.query("token")?.takeIf { it.isNotBlank() }
        if (token == null) {
            Response(BAD_REQUEST).body("token query param required")
        } else {
            val info = sharedPlotService.getInviteInfo(token)
            if (info == null) {
                Response(NOT_FOUND).body("Invite not found or expired")
            } else {
                Response(OK).header("Content-Type", "application/json")
                    .body(joinInfoResponseJson(info.plotId, info.plotName, info.inviterDisplayName, info.inviterUserId))
            }
        }
    }

private fun joinRoute(sharedPlotService: SharedPlotService): ContractRoute =
    "/plots/join" meta {
        summary = "Redeem an invite token (step 1: register recipient pubkey)"
    } bindContract POST to { request: Request -> handleJoin(request, sharedPlotService) }

private fun handleJoin(request: Request, sharedPlotService: SharedPlotService): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val token = node.get("token")?.asText()?.takeIf { it.isNotBlank() }
        ?: return Response(BAD_REQUEST).body("token is required")
    val recipientPubkey = node.get("recipientSharingPubkey")?.asText()?.takeIf { it.isNotBlank() }
        ?: return Response(BAD_REQUEST).body("recipientSharingPubkey is required")
    return when (val result = sharedPlotService.redeemInvite(token, request.authUserId(), recipientPubkey)) {
        is PlotMemberRepository.RedeemInviteResult.Pending -> {
            Response(OK).header("Content-Type", "application/json")
                .body(pendingJoinResponseJson("pending", result.inviteId, result.inviterDisplayName))
        }
        PlotMemberRepository.RedeemInviteResult.AlreadyMember ->
            Response(CONFLICT).body("You are already a member of this plot")
        PlotMemberRepository.RedeemInviteResult.Invalid ->
            Response(NOT_FOUND).body("Invite not found, expired, or already used")
    }
}

private fun listPendingInvitesRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" / "pending" meta {
        summary = "List pending joins awaiting key wrap confirmation"
    } bindContract GET to { plotId: UUID, _: String, _: String ->
        { request: Request ->
            val pending = sharedPlotService.listPendingInvites(plotId, request.authUserId())
            val arr = sharedMapper.writeValueAsString(pending)
            Response(OK).header("Content-Type", "application/json").body(arr)
        }
    }
}

private fun confirmInviteRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val inviteId = Path.uuid().of("inviteId")
    return "/plots" / plotId / "members" / "pending" / inviteId / "confirm" meta {
        summary = "Confirm a pending join by supplying the wrapped plot key for the recipient"
    } bindContract POST to { pId: UUID, _: String, _: String, iId: UUID, _: String ->
        { request: Request -> handleConfirmInvite(pId, iId, request, sharedPlotService) }
    }
}

private fun handleConfirmInvite(plotId: UUID, inviteId: UUID, request: Request, sharedPlotService: SharedPlotService): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val wrappedKey = node.get("wrappedPlotKey")?.asText()
        ?: return Response(BAD_REQUEST).body("wrappedPlotKey is required")
    val plotKeyFormat = node.get("plotKeyFormat")?.asText()
        ?: return Response(BAD_REQUEST).body("plotKeyFormat is required")
    val keyBytes = try { Base64.getDecoder().decode(wrappedKey) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("wrappedPlotKey is not valid base64") }
    return if (sharedPlotService.confirmInvite(inviteId, plotId, keyBytes, plotKeyFormat, request.authUserId()))
        Response(NO_CONTENT)
    else
        Response(NOT_FOUND)
}

// ---- Leave plot -------------------------------------------------------------

private fun leavePlotHandler(plotId: UUID, request: Request, sharedPlotService: SharedPlotService): Response =
    when (sharedPlotService.leavePlot(plotId, request.authUserId())) {
        PlotMemberRepository.LeavePlotResult.Success           -> Response(NO_CONTENT)
        PlotMemberRepository.LeavePlotResult.MustTransferFirst ->
            Response(FORBIDDEN).body("Owner must transfer ownership before leaving")
        PlotMemberRepository.LeavePlotResult.NotFound          -> Response(NOT_FOUND)
    }

private fun leavePlotRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "members" / "me" meta {
        summary = "Leave a shared plot (backward-compat DELETE alias)"
    } bindContract DELETE to { plotId: UUID, _: String, _: String ->
        { request: Request -> leavePlotHandler(plotId, request, sharedPlotService) }
    }
}

private fun leavePlotPostRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "leave" meta {
        summary = "Leave a shared plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> leavePlotHandler(plotId, request, sharedPlotService) }
    }
}

// ---- Accept invitation -------------------------------------------------------

private fun acceptInviteRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "accept" meta {
        summary = "Accept a plot invitation"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAcceptInvite(plotId, request, sharedPlotService) }
    }
}

private fun handleAcceptInvite(plotId: UUID, request: Request, sharedPlotService: SharedPlotService): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val localName = node.get("localName")?.takeIf { !it.isNull }?.asText()?.trim()
        ?: return Response(BAD_REQUEST).body("localName is required")
    if (localName.isBlank()) return Response(BAD_REQUEST).body("localName must not be blank")
    return when (sharedPlotService.acceptInvite(plotId, request.authUserId(), localName)) {
        PlotMemberRepository.AcceptInviteResult.Success      -> Response(NO_CONTENT)
        PlotMemberRepository.AcceptInviteResult.NotInvited   -> Response(NOT_FOUND).body("No pending invitation for this plot")
        PlotMemberRepository.AcceptInviteResult.AlreadyJoined -> Response(CONFLICT).body("Already a member of this plot")
    }
}

// ---- Rejoin plot -------------------------------------------------------------

private fun rejoinPlotRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "rejoin" meta {
        summary = "Rejoin a plot after leaving"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request ->
            val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
            val localName = node?.get("localName")?.takeIf { !it.isNull }?.asText()?.trim()
            when (sharedPlotService.rejoinPlot(plotId, request.authUserId(), localName)) {
                PlotMemberRepository.RejoinResult.Success        -> Response(NO_CONTENT)
                PlotMemberRepository.RejoinResult.NotLeft        -> Response(NOT_FOUND).body("No prior membership in this plot")
                PlotMemberRepository.RejoinResult.PlotTombstoned -> Response(GONE).body("Plot has been removed")
            }
        }
    }
}

// ---- Restore tombstoned plot ------------------------------------------------

private fun restorePlotRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "restore" meta {
        summary = "Restore a tombstoned plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request ->
            when (sharedPlotService.restorePlot(plotId, request.authUserId())) {
                PlotMemberRepository.RestorePlotResult.Success       -> Response(NO_CONTENT)
                PlotMemberRepository.RestorePlotResult.NotTombstoned -> Response(NOT_FOUND).body("Plot is not tombstoned")
                PlotMemberRepository.RestorePlotResult.NotAuthorized -> Response(FORBIDDEN).body("Only the member who triggered removal can restore")
                PlotMemberRepository.RestorePlotResult.WindowExpired -> Response(GONE).body("Restore window has expired")
            }
        }
    }
}

// ---- Transfer ownership ------------------------------------------------------

private fun transferOwnershipRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "transfer" meta {
        summary = "Transfer ownership to another member"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleTransferOwnership(plotId, request, sharedPlotService) }
    }
}

private fun handleTransferOwnership(plotId: UUID, request: Request, sharedPlotService: SharedPlotService): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val newOwnerIdStr = node.get("newOwnerId")?.asText()
        ?: return Response(BAD_REQUEST).body("newOwnerId is required")
    val newOwnerId = try { UUID.fromString(newOwnerIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("newOwnerId is not a valid UUID") }
    return when (sharedPlotService.transferOwnership(plotId, newOwnerId, request.authUserId())) {
        PlotMemberRepository.TransferOwnershipResult.Success        -> Response(NO_CONTENT)
        PlotMemberRepository.TransferOwnershipResult.NotOwner       -> Response(FORBIDDEN).body("Only the owner can transfer ownership")
        PlotMemberRepository.TransferOwnershipResult.TargetNotMember -> Response(BAD_REQUEST).body("Target user is not an active member")
        PlotMemberRepository.TransferOwnershipResult.NotFound       -> Response(NOT_FOUND)
    }
}

// ---- Open / close plot ------------------------------------------------------

private fun setPlotStatusRoute(sharedPlotService: SharedPlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "status" meta {
        summary = "Set plot open/closed"
    } bindContract PATCH to { plotId: UUID, _: String ->
        { request: Request -> handleSetPlotStatus(plotId, request, sharedPlotService) }
    }
}

private fun handleSetPlotStatus(plotId: UUID, request: Request, sharedPlotService: SharedPlotService): Response {
    val node = try { sharedMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val status = node.get("status")?.asText()
        ?: return Response(BAD_REQUEST).body("status is required")
    return when (val result = sharedPlotService.setPlotStatus(plotId, status, request.authUserId())) {
        PlotMemberRepository.SetPlotStatusResult.Success              -> Response(NO_CONTENT)
        PlotMemberRepository.SetPlotStatusResult.NotOwner             -> Response(FORBIDDEN).body("Only the owner can change plot status")
        PlotMemberRepository.SetPlotStatusResult.NotFound             -> Response(NOT_FOUND)
        is PlotMemberRepository.SetPlotStatusResult.InvalidStatus     -> Response(BAD_REQUEST).body("Invalid status: ${result.status}")
    }
}

// ---- List all shared memberships for current user ---------------------------

private fun listSharedMembershipsRoute(sharedPlotService: SharedPlotService): ContractRoute =
    "/plots/shared" meta {
        summary = "List all shared plot memberships for current user (all statuses)"
    } bindContract GET to { request: Request ->
        val memberships = sharedPlotService.listSharedMemberships(request.authUserId())
        val json = "[${memberships.joinToString(",") { m -> m.toJson() }}]"
        Response(OK).header("Content-Type", "application/json").body(json)
    }
