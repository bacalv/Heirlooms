package digital.heirlooms.server

import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

private val flowMapper = ObjectMapper()

fun flowRoutes(database: Database): List<ContractRoute> = listOf(
    listFlowsRoute(database),
    createFlowRoute(database),
    updateFlowRoute(database),
    deleteFlowRoute(database),
    getFlowStagingRoute(database),
)

fun plotItemRoutes(database: Database): List<ContractRoute> = listOf(
    getPlotStagingRoute(database),
    approveStagingRoute(database),
    rejectStagingRoute(database),
    deleteDecisionRoute(database),
    getRejectedRoute(database),
    getPlotItemsRoute(database),
    addPlotItemRoute(database),
    removePlotItemRoute(database),
)

// ---- Flow CRUD ------------------------------------------------------------

private fun listFlowsRoute(database: Database): ContractRoute =
    "/flows" meta { summary = "List flows" } bindContract GET to { request: Request ->
        val flows = database.listFlows(request.authUserId())
        val json = "[${flows.joinToString(",") { it.toJson() }}]"
        Response(OK).header("Content-Type", "application/json").body(json)
    }

private fun createFlowRoute(database: Database): ContractRoute =
    "/flows" meta { summary = "Create a flow" } bindContract POST to { request: Request ->
        handleCreateFlow(request, database)
    }

private fun handleCreateFlow(request: Request, database: Database): Response {
    val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")

    val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
        ?: return Response(BAD_REQUEST).body("name is required")
    val criteriaNode = node.get("criteria")?.takeIf { !it.isNull }
        ?: return Response(BAD_REQUEST).body("criteria is required")
    val targetPlotIdStr = node.get("targetPlotId")?.asText()
        ?: node.get("target_plot_id")?.asText()
        ?: return Response(BAD_REQUEST).body("targetPlotId is required")
    val targetPlotId = try { UUID.fromString(targetPlotIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("targetPlotId is not a valid UUID") }
    val requiresStaging = node.get("requiresStaging")?.asBoolean()
        ?: node.get("requires_staging")?.asBoolean()
        ?: true

    val criteriaJson = try {
        database.withCriteriaValidation(criteriaNode, request.authUserId())
        criteriaNode.toString()
    } catch (e: CriteriaValidationException) {
        return Response(BAD_REQUEST).body(e.message ?: "Invalid criteria")
    } catch (e: CriteriaCycleException) {
        return Response(BAD_REQUEST).body(e.message ?: "Circular plot_ref detected")
    }

    return when (val result = database.createFlow(name, criteriaJson, targetPlotId, requiresStaging, request.authUserId())) {
        is Database.FlowCreateResult.Success ->
            Response(CREATED).header("Content-Type", "application/json").body(result.flow.toJson())
        is Database.FlowCreateResult.Error ->
            Response(BAD_REQUEST).body(result.message)
    }
}

private fun updateFlowRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id meta { summary = "Update a flow" } bindContract PUT to { flowId: UUID ->
        { request: Request -> handleUpdateFlow(flowId, request, database) }
    }
}

private fun handleUpdateFlow(flowId: UUID, request: Request, database: Database): Response {
    val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")

    val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
    val requiresStaging = node.get("requiresStaging")?.asBoolean()
        ?: node.get("requires_staging")?.asBoolean()
    val criteriaNode = node.get("criteria")?.takeIf { !it.isNull }

    val criteriaJson: String? = if (criteriaNode != null) {
        try {
            database.withCriteriaValidation(criteriaNode, request.authUserId())
            criteriaNode.toString()
        } catch (e: CriteriaValidationException) {
            return Response(BAD_REQUEST).body(e.message ?: "Invalid criteria")
        } catch (e: CriteriaCycleException) {
            return Response(BAD_REQUEST).body(e.message ?: "Circular plot_ref detected")
        }
    } else null

    return when (database.updateFlow(flowId, name, criteriaJson, requiresStaging, request.authUserId())) {
        is Database.FlowUpdateResult.Success ->
            Response(OK).header("Content-Type", "application/json")
                .body((database.getFlowById(flowId, request.authUserId()) ?: return Response(NOT_FOUND)).toJson())
        Database.FlowUpdateResult.NotFound -> Response(NOT_FOUND)
    }
}

private fun deleteFlowRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id meta { summary = "Delete a flow" } bindContract DELETE to { flowId: UUID ->
        { request: Request ->
            if (database.deleteFlow(flowId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

// ---- Staging: flow-level --------------------------------------------------

private fun getFlowStagingRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id / "staging" meta {
        summary = "Get staging items for a flow"
    } bindContract GET to { flowId: UUID, _: String ->
        { request: Request ->
            val items = database.getStagingItems(flowId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Staging: plot-level --------------------------------------------------

private fun getPlotStagingRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "staging" meta {
        summary = "Get all pending staging items for a plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val items = database.getStagingItemsForPlot(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

private fun approveStagingRoute(database: Database): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "approve" meta {
        summary = "Approve a staging item"
    } bindContract POST to { pId: UUID, _s1: String, uId: UUID, _s2: String ->
        { request: Request -> handleApproveStagingItem(pId, uId, request, database) }
    }
}

private fun handleApproveStagingItem(plotId: UUID, uploadId: UUID, request: Request, database: Database): Response {
    val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
    val sourceFlowId = try { node?.get("sourceFlowId")?.asText()?.let { UUID.fromString(it) } } catch (_: Exception) { null }

    val plot = database.getPlotById(plotId)
    var dekBytes: ByteArray? = null
    var dekFormat: String? = null
    var thumbBytes: ByteArray? = null
    var thumbFormat: String? = null

    if (plot?.visibility == "shared") {
        val wrappedItemDek = node?.get("wrappedItemDek")?.asText()
        if (wrappedItemDek != null) {
            // Encrypted item: accept re-wrapped DEK
            dekFormat = node?.get("itemDekFormat")?.asText()
                ?: return Response(BAD_REQUEST).body("itemDekFormat required when wrappedItemDek is provided")
            dekBytes = try { java.util.Base64.getDecoder().decode(wrappedItemDek) }
                catch (_: Exception) { return Response(BAD_REQUEST).body("wrappedItemDek is not valid base64") }
            val wrappedThumb = node?.get("wrappedThumbnailDek")?.asText()
            thumbFormat = node?.get("thumbnailDekFormat")?.asText()
            thumbBytes = wrappedThumb?.let {
                try { java.util.Base64.getDecoder().decode(it) } catch (_: Exception) { null }
            }
        }
        // Unencrypted (public) items have no DEK to wrap — allow through without DEK fields
    }

    return when (database.approveStagingItem(plotId, uploadId, sourceFlowId, request.authUserId(), dekBytes, dekFormat, thumbBytes, thumbFormat)) {
        Database.ApproveResult.Success          -> Response(NO_CONTENT)
        Database.ApproveResult.DuplicateContent -> Response(NO_CONTENT)
        Database.ApproveResult.AlreadyApproved  -> Response(CONFLICT).body("Item is already in the collection")
        Database.ApproveResult.NotFound         -> Response(NOT_FOUND)
        Database.ApproveResult.PlotNotOwned     -> Response(NOT_FOUND)
        Database.ApproveResult.PlotClosed       -> Response(FORBIDDEN).body("Plot is closed")
    }
}

private fun rejectStagingRoute(database: Database): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "reject" meta {
        summary = "Reject a staging item"
    } bindContract POST to { pId: UUID, _s1: String, uId: UUID, _s2: String ->
        { request: Request -> handleRejectStagingItem(pId, uId, request, database) }
    }
}

private fun handleRejectStagingItem(plotId: UUID, uploadId: UUID, request: Request, database: Database): Response {
    val sourceFlowId = try {
        flowMapper.readTree(request.bodyString())?.get("sourceFlowId")?.asText()
            ?.let { UUID.fromString(it) }
    } catch (_: Exception) { null }
    return when (database.rejectStagingItem(plotId, uploadId, sourceFlowId, request.authUserId())) {
        Database.RejectResult.Success         -> Response(NO_CONTENT)
        Database.RejectResult.AlreadyApproved -> Response(CONFLICT).body("Item is already approved — remove it from the collection first")
        Database.RejectResult.NotFound        -> Response(NOT_FOUND)
        Database.RejectResult.PlotNotOwned    -> Response(NOT_FOUND)
    }
}

private fun deleteDecisionRoute(database: Database): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "decision" meta {
        summary = "Remove a staging decision (un-reject)"
    } bindContract DELETE to { pId: UUID, _s1: String, uId: UUID, _s2: String ->
        { request: Request ->
            if (database.deleteDecision(pId, uId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

private fun getRejectedRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "staging" / "rejected" meta {
        summary = "List rejected staging items for a plot"
    } bindContract GET to { plotId: UUID, _s1: String, _s2: String ->
        { request: Request ->
            val items = database.getRejectedItems(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Collection plot items ------------------------------------------------

private fun getPlotItemsRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "items" meta {
        summary = "List items in a collection plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val items = database.getPlotItems(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

private fun PlotItemWithUpload.toJson(): String {
    val factory = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
    val node = flowMapper.readTree(upload.toJson()).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
    node.put("added_by", addedBy.toString())
    if (wrappedItemDek != null) node.put("wrapped_item_dek", java.util.Base64.getEncoder().encodeToString(wrappedItemDek))
    if (itemDekFormat != null) node.put("item_dek_format", itemDekFormat)
    if (wrappedThumbnailDek != null) node.put("wrapped_thumbnail_dek", java.util.Base64.getEncoder().encodeToString(wrappedThumbnailDek))
    if (thumbnailDekFormat != null) node.put("thumbnail_dek_format", thumbnailDekFormat)
    return node.toString()
}

private fun addPlotItemRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "items" meta {
        summary = "Manually add an item to a collection plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAddPlotItem(plotId, request, database) }
    }
}

private fun handleAddPlotItem(plotId: UUID, request: Request, database: Database): Response {
    val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val uploadIdStr = node.get("uploadId")?.asText() ?: node.get("upload_id")?.asText()
        ?: return Response(BAD_REQUEST).body("uploadId is required")
    val uploadId = try { UUID.fromString(uploadIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("uploadId is not a valid UUID") }

    val plot = database.getPlotById(plotId)
    if (plot?.visibility == "shared") {
        val wrappedItemDek = node.get("wrappedItemDek")?.asText()
            ?: return Response(BAD_REQUEST).body("wrappedItemDek required for shared plots")
        val itemDekFormat = node.get("itemDekFormat")?.asText()
            ?: return Response(BAD_REQUEST).body("itemDekFormat required for shared plots")
        val wrappedThumbnailDek = node.get("wrappedThumbnailDek")?.asText()
        val thumbnailDekFormat = node.get("thumbnailDekFormat")?.asText()
        val dekBytes = try { java.util.Base64.getDecoder().decode(wrappedItemDek) }
            catch (_: Exception) { return Response(BAD_REQUEST).body("wrappedItemDek is not valid base64") }
        val thumbBytes = wrappedThumbnailDek?.let {
            try { java.util.Base64.getDecoder().decode(it) } catch (_: Exception) { null }
        }
        return when (database.addPlotItem(plotId, uploadId, request.authUserId(), dekBytes, itemDekFormat, thumbBytes, thumbnailDekFormat)) {
            Database.AddItemResult.Success        -> Response(CREATED)
            Database.AddItemResult.AlreadyPresent -> Response(CONFLICT).body("Item already in collection")
            Database.AddItemResult.PlotNotOwned   -> Response(NOT_FOUND)
            Database.AddItemResult.UploadNotOwned -> Response(NOT_FOUND)
            Database.AddItemResult.PlotClosed     -> Response(FORBIDDEN).body("Plot is closed")
            is Database.AddItemResult.Error       -> Response(BAD_REQUEST).body("Cannot add item")
        }
    }

    return when (database.addPlotItem(plotId, uploadId, request.authUserId())) {
        Database.AddItemResult.Success        -> Response(CREATED)
        Database.AddItemResult.AlreadyPresent -> Response(CONFLICT).body("Item already in collection")
        Database.AddItemResult.PlotNotOwned   -> Response(NOT_FOUND)
        Database.AddItemResult.UploadNotOwned -> Response(NOT_FOUND)
        Database.AddItemResult.PlotClosed     -> Response(FORBIDDEN).body("Plot is closed")
        is Database.AddItemResult.Error       -> Response(BAD_REQUEST).body("Cannot add item")
    }
}

private fun removePlotItemRoute(database: Database): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "items" / uploadId meta {
        summary = "Remove an item from a collection plot"
    } bindContract DELETE to { pId: UUID, _: String, uId: UUID ->
        { request: Request ->
            when (database.removePlotItem(pId, uId, request.authUserId())) {
                Database.RemoveItemResult.Success   -> Response(NO_CONTENT)
                Database.RemoveItemResult.NotFound  -> Response(NOT_FOUND)
                Database.RemoveItemResult.Forbidden -> Response(FORBIDDEN)
            }
        }
    }
}

// ---- Serialisation --------------------------------------------------------

internal fun FlowRecord.toJson(): String {
    val factory = JsonNodeFactory.instance
    val node = factory.objectNode()
    node.put("id", id.toString())
    node.put("name", name)
    node.set<JsonNode>("criteria", flowMapper.readTree(criteria))
    node.put("targetPlotId", targetPlotId.toString())
    node.put("requiresStaging", requiresStaging)
    node.put("created_at", createdAt.toString())
    node.put("updated_at", updatedAt.toString())
    return node.toString()
}
