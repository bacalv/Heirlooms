package digital.heirlooms.server.routes.connection

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.representation.connection.toNominationListJson
import digital.heirlooms.server.representation.connection.toSingleJson
import digital.heirlooms.server.service.connection.NominationService
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

private val nominationMapper = ObjectMapper()

fun nominationRoutes(nominationService: NominationService): List<ContractRoute> = listOf(
    createNominationRoute(nominationService),
    listNominationsRoute(nominationService),
    listReceivedNominationsRoute(nominationService),
    acceptNominationRoute(nominationService),
    declineNominationRoute(nominationService),
    revokeNominationRoute(nominationService),
)

// ─── Endpoint 6: POST /executor-nominations ──────────────────────────────────

private fun createNominationRoute(svc: NominationService): ContractRoute =
    "/executor-nominations" meta {
        summary = "Create executor nomination"
        description = "Owner extends a nomination offer to a connection. Creates a pending row."
    } bindContract POST to createNominationHandler(svc)

private fun createNominationHandler(svc: NominationService): HttpHandler = handler@{ request ->
    val node = try { nominationMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
    if (node == null) return@handler Response(BAD_REQUEST).body("Malformed JSON")

    val connectionIdStr = node.get("connection_id")?.takeUnless { it.isNull }?.asText()
    if (connectionIdStr == null) return@handler Response(BAD_REQUEST).body("Missing required field: connection_id")

    val connectionId = runCatching { UUID.fromString(connectionIdStr) }.getOrElse {
        return@handler Response(BAD_REQUEST).body("connection_id must be a valid UUID")
    }
    val message = node.get("message")?.takeUnless { it.isNull }?.asText()

    when (val result = svc.createNomination(request.authUserId(), connectionId, message)) {
        is NominationService.CreateResult.Created ->
            Response(CREATED)
                .header("Content-Type", "application/json")
                .body(result.nomination.toSingleJson())
        NominationService.CreateResult.ConnectionNotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"connection_not_found","detail":"connection does not exist or does not belong to caller"}""")
        NominationService.CreateResult.Conflict ->
            Response(CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"error":"active_nomination_exists","detail":"a pending or accepted nomination already exists for this connection"}""")
    }
}

// ─── Endpoint 7: GET /executor-nominations ───────────────────────────────────

private fun listNominationsRoute(svc: NominationService): ContractRoute =
    "/executor-nominations" meta {
        summary = "List issued nominations"
        description = "Owner lists all nominations they have issued (all statuses)."
    } bindContract GET to { request: Request ->
        val nominations = svc.listByOwner(request.authUserId())
        Response(OK)
            .header("Content-Type", "application/json")
            .body(nominations.toNominationListJson())
    }

// ─── Endpoint 8: GET /executor-nominations/received ──────────────────────────

private fun listReceivedNominationsRoute(svc: NominationService): ContractRoute =
    "/executor-nominations/received" meta {
        summary = "List received nominations"
        description = "Nominee lists all nominations extended to them (all statuses)."
    } bindContract GET to { request: Request ->
        val nominations = svc.listReceived(request.authUserId())
        Response(OK)
            .header("Content-Type", "application/json")
            .body(nominations.toNominationListJson())
    }

// ─── Endpoint 9: POST /executor-nominations/:id/accept ───────────────────────

private fun acceptNominationRoute(svc: NominationService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/executor-nominations" / id / "accept" meta {
        summary = "Accept nomination"
        description = "Nominee accepts a pending nomination."
    } bindContract POST to { nominationId: UUID, _: String ->
        { request: Request -> transitionHandler(svc.accept(nominationId, request.authUserId())) }
    }
}

// ─── Endpoint 10: POST /executor-nominations/:id/decline ─────────────────────

private fun declineNominationRoute(svc: NominationService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/executor-nominations" / id / "decline" meta {
        summary = "Decline nomination"
        description = "Nominee declines a pending nomination."
    } bindContract POST to { nominationId: UUID, _: String ->
        { request: Request -> transitionHandler(svc.decline(nominationId, request.authUserId())) }
    }
}

// ─── Endpoint 11: POST /executor-nominations/:id/revoke ──────────────────────

private fun revokeNominationRoute(svc: NominationService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/executor-nominations" / id / "revoke" meta {
        summary = "Revoke nomination"
        description = "Owner revokes a pending or accepted nomination. No automatic share rotation in M11."
    } bindContract POST to { nominationId: UUID, _: String ->
        { request: Request -> transitionHandler(svc.revoke(nominationId, request.authUserId())) }
    }
}

// ─── Shared transition response helper ───────────────────────────────────────

private fun transitionHandler(result: NominationService.TransitionResult): Response =
    when (result) {
        is NominationService.TransitionResult.Updated ->
            Response(OK)
                .header("Content-Type", "application/json")
                .body(result.nomination.toSingleJson())
        NominationService.TransitionResult.NotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"nomination_not_found"}""")
        NominationService.TransitionResult.Forbidden ->
            Response(FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("""{"error":"forbidden","detail":"caller does not have the required role for this transition"}""")
        NominationService.TransitionResult.StateConflict ->
            Response(CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"error":"invalid_state_transition","detail":"nomination is not in a valid state for this action"}""")
    }
