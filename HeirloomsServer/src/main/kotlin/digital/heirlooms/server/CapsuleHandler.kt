package digital.heirlooms.server

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
private const val RECIPIENT_MAX_LENGTH = 200
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

fun capsuleRoutes(database: Database): List<ContractRoute> = listOf(
    createCapsuleRoute(database),
    listCapsulesRoute(database),
    getCapsuleRoute(database),
    patchCapsuleRoute(database),
    sealCapsuleRoute(database),
    cancelCapsuleRoute(database),
)

private fun createCapsuleRoute(database: Database): ContractRoute =
    "/capsules" meta {
        summary = "Create a capsule"
        description = "Creates a new time capsule. Shape must be 'open' or 'sealed'. Sealed capsules require at least one upload."
    } bindContract POST to createCapsuleHandler(database)

private fun listCapsulesRoute(database: Database): ContractRoute =
    "/capsules" meta {
        summary = "List capsules"
        description = "Returns capsules matching the given state filter. Defaults to open and sealed (excludes cancelled and delivered). Order by 'updated_at' (default) or 'unlock_at'."
    } bindContract GET to listCapsulesHandler(database)

private fun getCapsuleRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id meta {
        summary = "Get capsule"
        description = "Returns the full detail for a capsule including all uploads, recipients, and current message."
    } bindContract GET to { capsuleId: UUID ->
        { _: Request -> getCapsuleHandler(database, capsuleId) }
    }
}

private fun patchCapsuleRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id meta {
        summary = "Update capsule"
        description = "Updates editable fields on a capsule. All fields optional. Sealed capsules reject upload_ids. Terminal-state capsules reject all changes."
    } bindContract PATCH to { capsuleId: UUID ->
        { request: Request -> patchCapsuleHandler(database, capsuleId, request) }
    }
}

private fun sealCapsuleRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "seal" meta {
        summary = "Seal a capsule"
        description = "Seals an open-shape capsule in state 'open'. The capsule must have at least one upload."
    } bindContract POST to { capsuleId: UUID, _: String ->
        { _: Request -> sealCapsuleHandler(database, capsuleId) }
    }
}

private fun cancelCapsuleRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/capsules" / id / "cancel" meta {
        summary = "Cancel a capsule"
        description = "Cancels a capsule in state 'open' or 'sealed'. Sets state to 'cancelled' and records cancelled_at."
    } bindContract POST to { capsuleId: UUID, _: String ->
        { _: Request -> cancelCapsuleHandler(database, capsuleId) }
    }
}

internal fun capsuleReverseLookupRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/uploads" / id / "capsules" meta {
        summary = "Capsules containing an upload"
        description = "Returns active (open + sealed) capsules that contain the given upload. Returns 404 if the upload does not exist."
    } bindContract GET to { uploadId: UUID, _: String ->
        { _: Request -> capsuleReverseLookupHandler(database, uploadId) }
    }
}

private fun createCapsuleHandler(database: Database): HttpHandler = { request ->
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
            null -> null
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
            recipients.isEmpty() -> Response(UNPROCESSABLE_ENTITY).body("""{"error":"recipients must not be empty"}""")
            recipients.any { it.isBlank() } -> Response(UNPROCESSABLE_ENTITY).body("""{"error":"recipients must not contain blank strings"}""")
            recipients.any { it.length > RECIPIENT_MAX_LENGTH } -> Response(UNPROCESSABLE_ENTITY).body("""{"error":"each recipient must be $RECIPIENT_MAX_LENGTH characters or fewer"}""")
            uploadIds == null -> Response(BAD_REQUEST).body("upload_ids must be an array of UUIDs")
            messageStr.toByteArray().size > MESSAGE_MAX_BYTES -> Response(UNPROCESSABLE_ENTITY).body("""{"error":"message exceeds maximum size of $MESSAGE_MAX_BYTES bytes"}""")
            else -> {
                // Validate upload IDs exist
                val unknownId = uploadIds.firstOrNull { !database.uploadExists(it) }
                if (unknownId != null) {
                    Response(BAD_REQUEST).body("""{"error":"unknown upload_id","id":"$unknownId"}""")
                } else if (shape == CapsuleShape.SEALED && uploadIds.isEmpty()) {
                    Response(UNPROCESSABLE_ENTITY).body("""{"error":"sealed capsules must have at least one upload"}""")
                } else {
                    val initialState = if (shape == CapsuleShape.SEALED) CapsuleState.SEALED else CapsuleState.OPEN
                    val detail = database.createCapsule(
                        id = UUID.randomUUID(),
                        createdByUser = "api-user",
                        shape = shape,
                        state = initialState,
                        unlockAt = unlockAt,
                        recipients = recipients,
                        uploadIds = uploadIds,
                        message = messageStr,
                    )
                    Response(CREATED)
                        .header("Content-Type", "application/json")
                        .body(detail.toDetailJson())
                }
            }
        }
    }
}

private fun listCapsulesHandler(database: Database): HttpHandler = { request ->
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
    val summaries = database.listCapsules(states, orderBy)
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

private fun getCapsuleHandler(database: Database, capsuleId: UUID): Response {
    val detail = database.getCapsuleById(capsuleId)
        ?: return Response(NOT_FOUND)
    return Response(OK).header("Content-Type", "application/json").body(detail.toDetailJson())
}

private fun patchCapsuleHandler(database: Database, capsuleId: UUID, request: Request): Response {
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
        val list = (0 until arr.size()).map { arr[it].asText() }
        if (list.isEmpty()) return Response(UNPROCESSABLE_ENTITY).body("""{"error":"recipients must not be empty"}""")
        if (list.any { it.isBlank() }) return Response(UNPROCESSABLE_ENTITY).body("""{"error":"recipients must not contain blank strings"}""")
        if (list.any { it.length > RECIPIENT_MAX_LENGTH }) return Response(UNPROCESSABLE_ENTITY).body("""{"error":"each recipient must be $RECIPIENT_MAX_LENGTH characters or fewer"}""")
        list
    } else null

    val uploadIds = if (node.has("upload_ids")) {
        val arr = node.get("upload_ids")
        if (!arr.isArray) return Response(BAD_REQUEST).body("upload_ids must be an array")
        try { (0 until arr.size()).map { UUID.fromString(arr[it].asText()) } }
        catch (_: Exception) { return Response(BAD_REQUEST).body("upload_ids must be an array of UUIDs") }
    } else null

    val message = if (node.has("message")) node.get("message").asText() else null

    if (message != null && message.toByteArray().size > MESSAGE_MAX_BYTES) {
        return Response(UNPROCESSABLE_ENTITY).body("""{"error":"message exceeds maximum size of $MESSAGE_MAX_BYTES bytes"}""")
    }

    return when (val result = database.updateCapsule(capsuleId, unlockAt, recipients, uploadIds, message)) {
        is Database.UpdateResult.Success ->
            Response(OK).header("Content-Type", "application/json").body(result.detail.toDetailJson())
        Database.UpdateResult.NotFound -> Response(NOT_FOUND)
        Database.UpdateResult.TerminalState ->
            Response(CONFLICT).body("""{"error":"capsule is in a terminal state and cannot be modified"}""")
        Database.UpdateResult.SealedContents ->
            Response(CONFLICT).body("""{"error":"cannot edit upload contents of a sealed capsule"}""")
        Database.UpdateResult.UnknownUpload ->
            Response(BAD_REQUEST).body("""{"error":"one or more upload_ids do not exist"}""")
        is Database.UpdateResult.InvalidRecipients ->
            Response(UNPROCESSABLE_ENTITY).body("""{"error":"${result.reason}"}""")
        is Database.UpdateResult.MessageTooLong ->
            Response(UNPROCESSABLE_ENTITY).body("""{"error":"message exceeds maximum size of ${result.limit} bytes"}""")
    }
}

private fun sealCapsuleHandler(database: Database, capsuleId: UUID): Response =
    when (val result = database.sealCapsule(capsuleId)) {
        is Database.SealResult.Success ->
            Response(OK).header("Content-Type", "application/json").body(result.detail.toDetailJson())
        Database.SealResult.NotFound -> Response(NOT_FOUND)
        Database.SealResult.WrongState ->
            Response(CONFLICT).body("""{"error":"capsule cannot be sealed in its current state"}""")
        Database.SealResult.Empty ->
            Response(UNPROCESSABLE_ENTITY).body("""{"error":"Cannot seal an empty capsule"}""")
    }

private fun cancelCapsuleHandler(database: Database, capsuleId: UUID): Response =
    when (val result = database.cancelCapsule(capsuleId)) {
        is Database.CancelResult.Success ->
            Response(OK).header("Content-Type", "application/json").body(result.detail.toDetailJson())
        Database.CancelResult.NotFound -> Response(NOT_FOUND)
        Database.CancelResult.WrongState ->
            Response(CONFLICT).body("""{"error":"capsule is already in a terminal state"}""")
    }

internal fun capsuleReverseLookupHandler(database: Database, uploadId: UUID): Response {
    val capsules = database.getCapsulesForUpload(uploadId)
        ?: return Response(NOT_FOUND)
    val json = buildString {
        append("""{"capsules":[""")
        capsules.forEachIndexed { i, s ->
            if (i > 0) append(",")
            append(s.toReverseLookupJson())
        }
        append("]}")
    }
    return Response(OK).header("Content-Type", "application/json").body(json)
}

// ---- JSON serialisation helpers -----------------------------------------

private fun CapsuleDetail.toDetailJson(): String = buildString {
    val r = record
    append("""{"id":"${r.id}","shape":"${r.shape.name.lowercase()}","state":"${r.state.name.lowercase()}"""")
    append(""","created_at":"${r.createdAt}","updated_at":"${r.updatedAt}","unlock_at":"${r.unlockAt}"""")
    append(""","recipients":${recipients.toJsonArray()}""")
    append(""","uploads":[""")
    uploads.forEachIndexed { i, u ->
        if (i > 0) append(",")
        append(u.toJson())
    }
    append("]")
    append(""","message":${jsonString(message)}""")
    append(""","cancelled_at":${r.cancelledAt?.let { "\"$it\"" } ?: "null"}""")
    append(""","delivered_at":${r.deliveredAt?.let { "\"$it\"" } ?: "null"}""")
    append("}")
}

private fun CapsuleSummary.toSummaryJson(): String = buildString {
    val r = record
    append("""{"id":"${r.id}","shape":"${r.shape.name.lowercase()}","state":"${r.state.name.lowercase()}"""")
    append(""","created_at":"${r.createdAt}","updated_at":"${r.updatedAt}","unlock_at":"${r.unlockAt}"""")
    append(""","recipients":${recipients.toJsonArray()}""")
    append(""","upload_count":$uploadCount,"has_message":$hasMessage""")
    append(""","cancelled_at":${r.cancelledAt?.let { "\"$it\"" } ?: "null"}""")
    append(""","delivered_at":${r.deliveredAt?.let { "\"$it\"" } ?: "null"}""")
    append("}")
}

internal fun CapsuleSummary.toReverseLookupJson(): String = buildString {
    val r = record
    append("""{"id":"${r.id}","shape":"${r.shape.name.lowercase()}","state":"${r.state.name.lowercase()}"""")
    append(""","unlock_at":"${r.unlockAt}","recipients":${recipients.toJsonArray()}""")
    append("}")
}

private fun List<String>.toJsonArray(): String =
    "[${joinToString(",") { jsonString(it) }}]"

private fun jsonString(s: String): String =
    "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
