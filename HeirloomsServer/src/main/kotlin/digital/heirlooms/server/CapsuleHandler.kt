package digital.heirlooms.server

import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleShape
import digital.heirlooms.server.domain.capsule.CapsuleState
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.repository.capsule.CapsuleRepository
import digital.heirlooms.server.service.capsule.CapsuleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNPROCESSABLE_ENTITY
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

internal const val MESSAGE_MAX_BYTES = 50_000
private val mapper = ObjectMapper()

data class CreateCapsuleRequest(
    val shape: String? = null,
    val unlock_at: String? = null,
    val recipients: List<String>? = null,
    val upload_ids: List<String>? = null,
    val message: String? = null,
)

data class PatchCapsuleRequest(
    val unlock_at: String? = null,
    val recipients: List<String>? = null,
    val upload_ids: List<String>? = null,
    val message: String? = null,
)

fun capsuleRoutes(capsuleService: CapsuleService): List<ContractRoute> = listOf(
    createCapsuleRoute(capsuleService),
    listCapsulesRoute(capsuleService),
    getCapsuleRoute(capsuleService),
    patchCapsuleRoute(capsuleService),
    sealCapsuleRoute(capsuleService),
    cancelCapsuleRoute(capsuleService),
)

private fun createCapsuleRoute(capsuleService: CapsuleService): ContractRoute =
    "/capsules" meta {
        summary = "Create a capsule"
        description = "Creates a new time capsule. Shape must be 'open' or 'sealed'. Sealed capsules require at least one upload."
    } bindContract POST to createCapsuleHandler(capsuleService)

private fun listCapsulesRoute(capsuleService: CapsuleService): ContractRoute =
    "/capsules" meta {
        summary = "List capsules"
        description = "Returns capsules matching the given state filter."
    } bindContract GET to listCapsulesHandler(capsuleService)

private fun getCapsuleRoute(capsuleService: CapsuleService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id meta {
        summary = "Get capsule"
        description = "Returns the full detail for a capsule including all uploads, recipients, and current message."
    } bindContract GET to { capsuleId: UUID ->
        { request: Request ->
            val detail = capsuleService.getCapsule(capsuleId, request.authUserId())
            if (detail == null) Response(NOT_FOUND)
            else Response(OK).header("Content-Type", "application/json").body(detail.toDetailJson())
        }
    }
}

private fun patchCapsuleRoute(capsuleService: CapsuleService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id meta {
        summary = "Update capsule"
        description = "Updates editable fields on a capsule."
    } bindContract PATCH to { capsuleId: UUID ->
        { request: Request -> patchCapsuleHandler(capsuleService, capsuleId, request) }
    }
}

private fun sealCapsuleRoute(capsuleService: CapsuleService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "seal" meta {
        summary = "Seal a capsule"
        description = "Seals an open-shape capsule in state 'open'. The capsule must have at least one upload."
    } bindContract POST to { capsuleId: UUID, _: String ->
        { request: Request ->
            when (val result = capsuleService.sealCapsule(capsuleId, request.authUserId())) {
                is CapsuleRepository.SealResult.Success ->
                    Response(OK).header("Content-Type", "application/json").body(result.detail.toDetailJson())
                CapsuleRepository.SealResult.NotFound -> Response(NOT_FOUND)
                CapsuleRepository.SealResult.WrongState ->
                    Response(CONFLICT).body("""{"error":"capsule cannot be sealed in its current state"}""")
                CapsuleRepository.SealResult.Empty ->
                    Response(UNPROCESSABLE_ENTITY).body("""{"error":"Cannot seal an empty capsule"}""")
            }
        }
    }
}

private fun cancelCapsuleRoute(capsuleService: CapsuleService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "cancel" meta {
        summary = "Cancel a capsule"
        description = "Cancels a capsule in state 'open' or 'sealed'."
    } bindContract POST to { capsuleId: UUID, _: String ->
        { request: Request ->
            when (val result = capsuleService.cancelCapsule(capsuleId, request.authUserId())) {
                is CapsuleRepository.CancelResult.Success ->
                    Response(OK).header("Content-Type", "application/json").body(result.detail.toDetailJson())
                CapsuleRepository.CancelResult.NotFound -> Response(NOT_FOUND)
                CapsuleRepository.CancelResult.WrongState ->
                    Response(CONFLICT).body("""{"error":"capsule is already in a terminal state"}""")
            }
        }
    }
}

internal fun capsuleReverseLookupRoute(capsuleService: CapsuleService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "capsules" meta {
        summary = "Capsules containing an upload"
        description = "Returns active (open + sealed) capsules that contain the given upload."
    } bindContract GET to { uploadId: UUID, _: String ->
        { request: Request ->
            val capsules = capsuleService.getCapsulesForUpload(uploadId, request.authUserId())
            if (capsules == null) Response(NOT_FOUND)
            else {
                val json = buildString {
                    append("""{"capsules":[""")
                    capsules.forEachIndexed { i, s ->
                        if (i > 0) append(",")
                        append(s.toReverseLookupJson())
                    }
                    append("]}")
                }
                Response(OK).header("Content-Type", "application/json").body(json)
            }
        }
    }
}

private fun createCapsuleHandler(capsuleService: CapsuleService): HttpHandler = { request ->
    val node = try { mapper.readTree(request.bodyString()) } catch (_: Exception) { null }
    if (node == null) {
        Response(BAD_REQUEST).body("Malformed JSON")
    } else {
        val shapeStr = node.get("shape")?.asText()
        val unlockAtStr = node.get("unlock_at")?.asText()
        val recipientsNode = node.get("recipients")
        val uploadIdsNode = node.get("upload_ids")
        val messageStr = node.get("message")?.asText() ?: ""

        val shape = when (shapeStr?.lowercase()) {
            "open" -> CapsuleShape.OPEN
            "sealed" -> CapsuleShape.SEALED
            else -> null
        }

        val unlockAt = if (unlockAtStr != null) {
            try { OffsetDateTime.parse(unlockAtStr) } catch (_: DateTimeParseException) { null }
        } else null

        val recipients = if (recipientsNode != null && recipientsNode.isArray) {
            (0 until recipientsNode.size()).map { recipientsNode[it].asText() }
        } else null

        val uploadIds = if (uploadIdsNode != null && uploadIdsNode.isArray) {
            try {
                (0 until uploadIdsNode.size()).map { UUID.fromString(uploadIdsNode[it].asText()) }
            } catch (_: Exception) { null }
        } else emptyList<UUID>()

        when {
            shapeStr == null -> Response(BAD_REQUEST).body("Missing required field: shape")
            shape == null -> Response(BAD_REQUEST).body("shape must be 'open' or 'sealed'")
            unlockAtStr == null -> Response(BAD_REQUEST).body("Missing required field: unlock_at")
            unlockAt == null -> Response(BAD_REQUEST).body("unlock_at must be a valid ISO-8601 timestamp with timezone")
            recipientsNode == null -> Response(UNPROCESSABLE_ENTITY).body("""{"error":"recipients is required and must be non-empty"}""")
            recipients == null -> Response(BAD_REQUEST).body("recipients must be an array")
            uploadIds == null -> Response(BAD_REQUEST).body("upload_ids must be an array of UUIDs")
            else -> {
                val userId = request.authUserId()
                when (val result = capsuleService.createCapsule(shape, unlockAt, recipients, uploadIds, messageStr, userId)) {
                    is CapsuleService.CreateResult.Created ->
                        Response(CREATED).header("Content-Type", "application/json").body(result.detail.toDetailJson())
                    is CapsuleService.CreateResult.Invalid ->
                        Response(UNPROCESSABLE_ENTITY).body("""{"error":"${result.message}"}""")
                    is CapsuleService.CreateResult.UnknownUpload ->
                        Response(BAD_REQUEST).body("""{"error":"unknown upload_id","id":"${result.id}"}""")
                }
            }
        }
    }
}

private fun listCapsulesHandler(capsuleService: CapsuleService): HttpHandler = { request ->
    val stateParam = request.query("state") ?: "open,sealed"
    val orderParam = request.query("order") ?: "updated_at"
    val states = stateParam.split(",").mapNotNull { s ->
        when (s.trim().lowercase()) {
            "open" -> CapsuleState.OPEN
            "sealed" -> CapsuleState.SEALED
            "delivered" -> CapsuleState.DELIVERED
            "cancelled" -> CapsuleState.CANCELLED
            else -> null
        }
    }
    val orderBy = if (orderParam == "unlock_at") "unlock_at" else "updated_at"
    val summaries = capsuleService.listCapsules(states, orderBy, request.authUserId())
    val json = buildString {
        append("""{"capsules":[""")
        summaries.forEachIndexed { i, s ->
            if (i > 0) append(",")
            append(s.toSummaryJson())
        }
        append("]}")
    }
    Response(OK).header("Content-Type", "application/json").body(json)
}

private fun patchCapsuleHandler(capsuleService: CapsuleService, capsuleId: UUID, request: Request): Response {
    val node = try { mapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Malformed JSON")

    val unlockAt = if (node.has("unlock_at")) {
        val str = node.get("unlock_at").asText()
        try { OffsetDateTime.parse(str) }
        catch (_: DateTimeParseException) {
            return Response(BAD_REQUEST).body("unlock_at must be a valid ISO-8601 timestamp with timezone")
        }
    } else null

    val recipients = if (node.has("recipients")) {
        val arr = node.get("recipients")
        if (!arr.isArray) return Response(BAD_REQUEST).body("recipients must be an array")
        (0 until arr.size()).map { arr[it].asText() }
    } else null

    val uploadIds = if (node.has("upload_ids")) {
        val arr = node.get("upload_ids")
        if (!arr.isArray) return Response(BAD_REQUEST).body("upload_ids must be an array")
        try { (0 until arr.size()).map { UUID.fromString(arr[it].asText()) } }
        catch (_: Exception) { return Response(BAD_REQUEST).body("upload_ids must be an array of UUIDs") }
    } else null

    val message = if (node.has("message")) node.get("message").asText() else null

    return when (val result = capsuleService.updateCapsule(capsuleId, request.authUserId(), unlockAt, recipients, uploadIds, message)) {
        is CapsuleRepository.UpdateResult.Success ->
            Response(OK).header("Content-Type", "application/json").body(result.detail.toDetailJson())
        CapsuleRepository.UpdateResult.NotFound -> Response(NOT_FOUND)
        CapsuleRepository.UpdateResult.TerminalState ->
            Response(CONFLICT).body("""{"error":"capsule is in a terminal state and cannot be modified"}""")
        CapsuleRepository.UpdateResult.SealedContents ->
            Response(CONFLICT).body("""{"error":"cannot edit upload contents of a sealed capsule"}""")
        CapsuleRepository.UpdateResult.UnknownUpload ->
            Response(BAD_REQUEST).body("""{"error":"one or more upload_ids do not exist"}""")
        is CapsuleRepository.UpdateResult.InvalidRecipients ->
            Response(UNPROCESSABLE_ENTITY).body("""{"error":"${result.reason}"}""")
        is CapsuleRepository.UpdateResult.MessageTooLong ->
            Response(UNPROCESSABLE_ENTITY).body("""{"error":"message exceeds maximum size of ${result.limit} bytes"}""")
    }
}

// ---- JSON serialisation helpers ----------------------------------------

internal fun CapsuleDetail.toDetailJson(): String {
    val r = record
    val node = mapper.createObjectNode()
    node.put("id", r.id.toString())
    node.put("shape", r.shape.name.lowercase())
    node.put("state", r.state.name.lowercase())
    node.put("created_at", r.createdAt.toString())
    node.put("updated_at", r.updatedAt.toString())
    node.put("unlock_at", r.unlockAt.toString())
    node.putArray("recipients").also { arr -> recipients.forEach { arr.add(it) } }
    node.putArray("uploads").also { arr -> uploads.forEach { arr.add(mapper.readTree(it.toJson())) } }
    node.put("message", message)
    if (r.cancelledAt != null) node.put("cancelled_at", r.cancelledAt.toString()) else node.putNull("cancelled_at")
    if (r.deliveredAt != null) node.put("delivered_at", r.deliveredAt.toString()) else node.putNull("delivered_at")
    return mapper.writeValueAsString(node)
}

internal fun CapsuleSummary.toSummaryJson(): String {
    val r = record
    val node = mapper.createObjectNode()
    node.put("id", r.id.toString())
    node.put("shape", r.shape.name.lowercase())
    node.put("state", r.state.name.lowercase())
    node.put("created_at", r.createdAt.toString())
    node.put("updated_at", r.updatedAt.toString())
    node.put("unlock_at", r.unlockAt.toString())
    node.putArray("recipients").also { arr -> recipients.forEach { arr.add(it) } }
    node.put("upload_count", uploadCount)
    node.put("has_message", hasMessage)
    if (r.cancelledAt != null) node.put("cancelled_at", r.cancelledAt.toString()) else node.putNull("cancelled_at")
    if (r.deliveredAt != null) node.put("delivered_at", r.deliveredAt.toString()) else node.putNull("delivered_at")
    return mapper.writeValueAsString(node)
}

internal fun CapsuleSummary.toReverseLookupJson(): String {
    val r = record
    val node = mapper.createObjectNode()
    node.put("id", r.id.toString())
    node.put("shape", r.shape.name.lowercase())
    node.put("state", r.state.name.lowercase())
    node.put("unlock_at", r.unlockAt.toString())
    node.putArray("recipients").also { arr -> recipients.forEach { arr.add(it) } }
    return mapper.writeValueAsString(node)
}
