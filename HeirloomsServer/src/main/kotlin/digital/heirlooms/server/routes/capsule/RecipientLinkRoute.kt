package digital.heirlooms.server.routes.capsule

import com.fasterxml.jackson.databind.ObjectMapper
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.PATCH
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

private val linkMapper = ObjectMapper()

/**
 * Endpoint 12: PATCH /capsules/:capsuleId/recipients/:recipientId/link
 *
 * Links a capsule_recipients row to a connections row by setting connection_id.
 */
fun recipientLinkRoute(recipientLinkRepo: RecipientLinkRepository): ContractRoute {
    val capsuleIdLens = Path.uuid().of("id")
    val recipientIdLens = Path.uuid().of("recipientId")
    val spec = "/capsules" / capsuleIdLens / "recipients" / recipientIdLens / "link"
    return spec meta {
        summary = "Link capsule recipient to connection"
        description = "Sets capsule_recipients.connection_id to a validated connection owned by the caller."
    } bindContract PATCH to { capsuleId: UUID, _: String, recipientId: UUID, _: String ->
        { request: Request -> recipientLinkHandler(recipientLinkRepo, capsuleId, recipientId, request) }
    }
}

private fun recipientLinkHandler(
    repo: RecipientLinkRepository,
    capsuleId: UUID,
    recipientId: UUID,
    request: Request,
): Response {
    val node = try { linkMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Malformed JSON")

    val connectionIdStr = node.get("connection_id")?.takeUnless { it.isNull }?.asText()
        ?: return Response(BAD_REQUEST).body("Missing required field: connection_id")

    val connectionId = runCatching { UUID.fromString(connectionIdStr) }.getOrElse {
        return Response(BAD_REQUEST).body("connection_id must be a valid UUID")
    }

    return when (val result = repo.linkRecipient(capsuleId, recipientId, connectionId, request.authUserId())) {
        is RecipientLinkRepository.LinkResult.Linked ->
            Response(OK)
                .header("Content-Type", "application/json")
                .body(
                    """{"recipient":{"id":"${result.recipientId}","capsule_id":"${result.capsuleId}","recipient":"${result.recipient}","connection_id":"${result.connectionId}"}}"""
                )
        RecipientLinkRepository.LinkResult.CapsuleNotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"capsule_not_found","detail":"capsule does not exist or does not belong to caller"}""")
        RecipientLinkRepository.LinkResult.RecipientNotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"recipient_not_found","detail":"recipient row does not exist on this capsule"}""")
        RecipientLinkRepository.LinkResult.ConnectionNotFound ->
            Response(NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"connection_not_found","detail":"connection does not exist or does not belong to caller"}""")
        RecipientLinkRepository.LinkResult.DuplicateConnection ->
            Response(CONFLICT)
                .header("Content-Type", "application/json")
                .body("""{"error":"connection_already_linked","detail":"this connection is already linked to another recipient on this capsule"}""")
    }
}
