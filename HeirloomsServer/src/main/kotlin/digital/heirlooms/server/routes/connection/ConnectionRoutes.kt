package digital.heirlooms.server.routes.connection

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.representation.connection.toListJson
import digital.heirlooms.server.representation.connection.toSingleJson
import digital.heirlooms.server.service.connection.ConnectionService
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

private val mapper = ObjectMapper()

fun connectionRoutes(connectionService: ConnectionService): List<ContractRoute> = listOf(
    listConnectionsRoute(connectionService),
    createConnectionRoute(connectionService),
    getConnectionRoute(connectionService),
    patchConnectionRoute(connectionService),
    deleteConnectionRoute(connectionService),
)

private fun listConnectionsRoute(svc: ConnectionService): ContractRoute =
    "/connections" meta {
        summary = "List connections"
        description = "Returns all connections owned by the authenticated user."
    } bindContract GET to listConnectionsHandler(svc)

private fun createConnectionRoute(svc: ConnectionService): ContractRoute =
    "/connections" meta {
        summary = "Create a connection"
        description = "Creates a new connection (bound or placeholder). Returns HTTP 201 with the created connection."
    } bindContract POST to createConnectionHandler(svc)

private fun getConnectionRoute(svc: ConnectionService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/connections" / id meta {
        summary = "Get a connection"
        description = "Returns a single connection by ID. Returns HTTP 404 if not found or not owned by caller."
    } bindContract GET to { connectionId: UUID ->
        { request: Request ->
            val connection = svc.getConnection(connectionId, request.authUserId())
            if (connection == null) Response(NOT_FOUND)
            else Response(OK).header("Content-Type", "application/json").body(connection.toSingleJson())
        }
    }
}

private fun patchConnectionRoute(svc: ConnectionService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/connections" / id meta {
        summary = "Update a connection"
        description = "Partially updates display_name, roles, or sharing_pubkey. Absent fields are unchanged."
    } bindContract PATCH to { connectionId: UUID ->
        { request: Request -> patchConnectionHandler(svc, connectionId, request) }
    }
}

private fun deleteConnectionRoute(svc: ConnectionService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/connections" / id meta {
        summary = "Delete a connection"
        description = "Deletes a connection. Returns HTTP 409 if active executor nominations exist."
    } bindContract DELETE to { connectionId: UUID ->
        { request: Request ->
            when (svc.deleteConnection(connectionId, request.authUserId())) {
                ConnectionService.DeleteResult.Deleted -> Response(NO_CONTENT)
                ConnectionService.DeleteResult.NotFound -> Response(NOT_FOUND)
                ConnectionService.DeleteResult.ActiveNominationsExist ->
                    Response(CONFLICT)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"active_nominations_exist","detail":"connection has active executor nominations and cannot be deleted"}""")
            }
        }
    }
}

// ─── Handlers ────────────────────────────────────────────────────────────────

private fun listConnectionsHandler(svc: ConnectionService): HttpHandler = { request ->
    val connections = svc.listConnections(request.authUserId())
    Response(OK).header("Content-Type", "application/json").body(connections.toListJson())
}

private fun createConnectionHandler(svc: ConnectionService): HttpHandler = handler@{ request ->
    val node = try { mapper.readTree(request.bodyString()) } catch (_: Exception) { null }
    if (node == null) {
        return@handler Response(BAD_REQUEST).body("Malformed JSON")
    }

    val displayName = node.get("display_name")?.asText()
    val contactUserIdStr = node.get("contact_user_id")?.takeUnless { it.isNull }?.asText()
    val email = node.get("email")?.takeUnless { it.isNull }?.asText()
    val rolesNode = node.get("roles")

    val contactUserId: UUID? = if (contactUserIdStr != null) {
        runCatching { UUID.fromString(contactUserIdStr) }.getOrElse {
            return@handler Response(BAD_REQUEST).body("contact_user_id must be a valid UUID")
        }
    } else null

    val roles: List<String> = if (rolesNode != null && rolesNode.isArray) {
        (0 until rolesNode.size()).map { rolesNode[it].asText() }
    } else emptyList()

    if (displayName == null) {
        return@handler Response(BAD_REQUEST).body("Missing required field: display_name")
    }

    when (val result = svc.createConnection(
        ownerUserId = request.authUserId(),
        contactUserId = contactUserId,
        displayName = displayName,
        email = email,
        roles = roles,
    )) {
        is ConnectionService.CreateResult.Created ->
            Response(CREATED)
                .header("Content-Type", "application/json")
                .body(result.connection.toSingleJson())
        is ConnectionService.CreateResult.Invalid ->
            Response(BAD_REQUEST).body("""{"error":"${result.message}"}""")
        ConnectionService.CreateResult.Conflict ->
            Response(CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"error":"connection_already_exists","detail":"a connection for this contact already exists"}""")
    }
}

private fun patchConnectionHandler(svc: ConnectionService, connectionId: UUID, request: Request): Response {
    val node = try { mapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Malformed JSON")

    val displayName = if (node.has("display_name")) node.get("display_name").asText() else null

    val roles: List<String>? = if (node.has("roles")) {
        val arr = node.get("roles")
        if (!arr.isArray) return Response(BAD_REQUEST).body("roles must be an array")
        (0 until arr.size()).map { arr[it].asText() }
    } else null

    // sharing_pubkey may be explicitly set to null (clear) or a string (update)
    val sharingPubkey: String?
    val clearSharingPubkey: Boolean
    if (node.has("sharing_pubkey")) {
        val spNode = node.get("sharing_pubkey")
        if (spNode.isNull) {
            sharingPubkey = null
            clearSharingPubkey = true
        } else {
            sharingPubkey = spNode.asText()
            clearSharingPubkey = false
        }
    } else {
        sharingPubkey = null
        clearSharingPubkey = false
    }

    return when (val result = svc.updateConnection(
        id = connectionId,
        ownerUserId = request.authUserId(),
        displayName = displayName,
        roles = roles,
        sharingPubkey = sharingPubkey,
        clearSharingPubkey = clearSharingPubkey,
    )) {
        is ConnectionService.UpdateResult.Updated ->
            Response(OK).header("Content-Type", "application/json").body(result.connection.toSingleJson())
        ConnectionService.UpdateResult.NotFound -> Response(NOT_FOUND)
    }
}
