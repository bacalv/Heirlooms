package digital.heirlooms.server.routes.capsule

import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.repository.capsule.CapsuleRecipientKeyRepository
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.Base64
import java.util.UUID

private val b64urlEncRk = Base64.getUrlEncoder().withoutPadding()

/**
 * GET /api/capsule-recipient-keys/:capsuleId
 *
 * Returns capsule_recipient_keys rows for a capsule.
 * - Owner: receives all rows.
 * - Authenticated recipient: receives only their own row.
 * - Others: HTTP 403.
 *
 * This endpoint does NOT return tlock_dek_tlock (which is only returned by
 * GET /api/capsules/:id/tlock-key after gate validation).
 */
fun capsuleRecipientKeysRoute(recipientKeyRepo: CapsuleRecipientKeyRepository): ContractRoute {
    val capsuleId = Path.uuid().of("capsuleId")
    return "/capsule-recipient-keys" / capsuleId meta {
        summary = "Get capsule recipient keys"
        description = """
            Returns capsule_recipient_keys rows for the given capsule.
            Owner receives all rows; authenticated recipient receives only their own row.
        """.trimIndent()
    } bindContract GET to { cId: UUID ->
        { request: Request -> capsuleRecipientKeysHandler(recipientKeyRepo, cId, request) }
    }
}

private fun capsuleRecipientKeysHandler(
    recipientKeyRepo: CapsuleRecipientKeyRepository,
    capsuleId: UUID,
    request: Request,
): Response {
    val callerUserId = request.authUserId()

    // Check capsule existence first (404 before 403 for non-existent capsules)
    if (!recipientKeyRepo.capsuleExists(capsuleId)) {
        return Response(NOT_FOUND)
            .header("Content-Type", "application/json")
            .body("""{"error":"not_found"}""")
    }

    val isOwner = recipientKeyRepo.isCapsuleOwner(capsuleId, callerUserId)
    val isRecipient = if (!isOwner) recipientKeyRepo.isAuthenticatedRecipient(capsuleId, callerUserId) else false

    if (!isOwner && !isRecipient) {
        return Response(FORBIDDEN)
            .header("Content-Type", "application/json")
            .body("""{"error":"forbidden"}""")
    }

    val rows: List<CapsuleRecipientKeyRepository.RecipientKeyRow> = if (isOwner) {
        recipientKeyRepo.findAllRows(capsuleId)
    } else {
        listOfNotNull(recipientKeyRepo.findOwnRow(capsuleId, callerUserId))
    }

    val json = buildString {
        append("""{"recipient_keys":[""")
        rows.forEachIndexed { i, row ->
            if (i > 0) append(",")
            append("{")
            append(""""connection_id":"${row.connectionId}"""")
            append(""","wrapped_capsule_key":"${b64urlEncRk.encodeToString(row.wrappedCapsuleKey)}"""")
            if (row.wrappedBlindingMask != null) {
                append(""","wrapped_blinding_mask":"${b64urlEncRk.encodeToString(row.wrappedBlindingMask)}"""")
            } else {
                append(""","wrapped_blinding_mask":null""")
            }
            append("}")
        }
        append("]}")
    }

    return Response(OK)
        .header("Content-Type", "application/json")
        .body(json)
}
