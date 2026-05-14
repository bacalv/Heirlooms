package digital.heirlooms.server

import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.service.plot.FlowService
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

fun flowRoutes(flowService: FlowService): List<ContractRoute> = listOf(
    listFlowsRoute(flowService),
    createFlowRoute(flowService),
    updateFlowRoute(flowService),
    deleteFlowRoute(flowService),
    getFlowStagingRoute(flowService),
)

fun plotItemRoutes(flowService: FlowService): List<ContractRoute> = listOf(
    getPlotStagingRoute(flowService),
    approveStagingRoute(flowService),
    rejectStagingRoute(flowService),
    deleteDecisionRoute(flowService),
    getRejectedRoute(flowService),
    getPlotItemsRoute(flowService),
    addPlotItemRoute(flowService),
    removePlotItemRoute(flowService),
)

// ---- Flow CRUD ------------------------------------------------------------

private fun listFlowsRoute(flowService: FlowService): ContractRoute =
    "/flows" meta { summary = "List flows" } bindContract GET to { request: Request ->
        val flows = flowService.listFlows(request.authUserId())
        val json = "[${flows.joinToString(",") { it.toJson() }}]"
        Response(OK).header("Content-Type", "application/json").body(json)
    }

private fun createFlowRoute(flowService: FlowService): ContractRoute =
    "/flows" meta { summary = "Create a flow" } bindContract POST to { request: Request ->
        handleCreateFlow(request, flowService)
    }

private fun handleCreateFlow(request: Request, flowService: FlowService): Response {
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

    return when (val result = flowService.createFlow(name, criteriaNode, targetPlotId, requiresStaging, request.authUserId())) {
        is FlowService.CreateFlowResult.Created ->
            Response(CREATED).header("Content-Type", "application/json").body(result.flow.toJson())
        is FlowService.CreateFlowResult.Invalid ->
            Response(BAD_REQUEST).body(result.message)
    }
}

private fun updateFlowRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id meta { summary = "Update a flow" } bindContract PUT to { flowId: UUID ->
        { request: Request -> handleUpdateFlow(flowId, request, flowService) }
    }
}

private fun handleUpdateFlow(flowId: UUID, request: Request, flowService: FlowService): Response {
    val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
    val requiresStaging = node.get("requiresStaging")?.asBoolean()
        ?: node.get("requires_staging")?.asBoolean()
    val criteriaNode = node.get("criteria")?.takeIf { !it.isNull }

    return when (val result = flowService.updateFlow(flowId, name, criteriaNode, requiresStaging, request.authUserId())) {
        is FlowService.UpdateFlowResult.Updated ->
            Response(OK).header("Content-Type", "application/json").body(result.flow.toJson())
        FlowService.UpdateFlowResult.NotFound -> Response(NOT_FOUND)
        is FlowService.UpdateFlowResult.Invalid -> Response(BAD_REQUEST).body(result.message)
    }
}

private fun deleteFlowRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id meta { summary = "Delete a flow" } bindContract DELETE to { flowId: UUID ->
        { request: Request ->
            if (flowService.deleteFlow(flowId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

// ---- Staging: flow-level --------------------------------------------------

private fun getFlowStagingRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id / "staging" meta {
        summary = "Get staging items for a flow"
    } bindContract GET to { flowId: UUID, _: String ->
        { request: Request ->
            val items = flowService.getStagingItems(flowId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Staging: plot-level --------------------------------------------------

private fun getPlotStagingRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "staging" meta {
        summary = "Get all pending staging items for a plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val items = flowService.getStagingItemsForPlot(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

private fun approveStagingRoute(flowService: FlowService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "approve" meta {
        summary = "Approve a staging item"
    } bindContract POST to { pId: UUID, _: String, uId: UUID, _: String ->
        { request: Request ->
            val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
            val sourceFlowId = try { node?.get("sourceFlowId")?.asText()?.let { UUID.fromString(it) } } catch (_: Exception) { null }
            when (val result = flowService.approveStagingItem(
                plotId = pId,
                uploadId = uId,
                sourceFlowId = sourceFlowId,
                userId = request.authUserId(),
                wrappedItemDekB64 = node?.get("wrappedItemDek")?.asText(),
                itemDekFormatRaw = node?.get("itemDekFormat")?.asText(),
                wrappedThumbnailDekB64 = node?.get("wrappedThumbnailDek")?.asText(),
                thumbnailDekFormatRaw = node?.get("thumbnailDekFormat")?.asText(),
            )) {
                FlowService.ApproveStagingResult.Success          -> Response(NO_CONTENT)
                FlowService.ApproveStagingResult.DuplicateContent -> Response(NO_CONTENT)
                FlowService.ApproveStagingResult.AlreadyApproved  -> Response(CONFLICT).body("Item is already in the collection")
                FlowService.ApproveStagingResult.NotFound         -> Response(NOT_FOUND)
                FlowService.ApproveStagingResult.PlotNotOwned     -> Response(NOT_FOUND)
                FlowService.ApproveStagingResult.PlotClosed       -> Response(FORBIDDEN).body("Plot is closed")
                is FlowService.ApproveStagingResult.Invalid       -> Response(BAD_REQUEST).body(result.message)
            }
        }
    }
}

private fun rejectStagingRoute(flowService: FlowService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "reject" meta {
        summary = "Reject a staging item"
    } bindContract POST to { pId: UUID, _: String, uId: UUID, _: String ->
        { request: Request ->
            val sourceFlowId = try {
                flowMapper.readTree(request.bodyString())?.get("sourceFlowId")?.asText()
                    ?.let { UUID.fromString(it) }
            } catch (_: Exception) { null }
            when (flowService.rejectStagingItem(pId, uId, sourceFlowId, request.authUserId())) {
                PlotItemRepository.RejectResult.Success         -> Response(NO_CONTENT)
                PlotItemRepository.RejectResult.AlreadyApproved -> Response(CONFLICT).body("Item is already approved — remove it from the collection first")
                PlotItemRepository.RejectResult.NotFound        -> Response(NOT_FOUND)
                PlotItemRepository.RejectResult.PlotNotOwned    -> Response(NOT_FOUND)
            }
        }
    }
}

private fun deleteDecisionRoute(flowService: FlowService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "decision" meta {
        summary = "Remove a staging decision (un-reject)"
    } bindContract DELETE to { pId: UUID, _: String, uId: UUID, _: String ->
        { request: Request ->
            if (flowService.deleteDecision(pId, uId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

private fun getRejectedRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "staging" / "rejected" meta {
        summary = "List rejected staging items for a plot"
    } bindContract GET to { plotId: UUID, _: String, _: String ->
        { request: Request ->
            val items = flowService.getRejectedItems(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Collection plot items ------------------------------------------------

private fun getPlotItemsRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "items" meta {
        summary = "List items in a collection plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val items = flowService.getPlotItems(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

private fun PlotItemWithUpload.toJson(): String {
    val node = flowMapper.readTree(upload.toJson()).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
    node.put("added_by", addedBy.toString())
    if (wrappedItemDek != null) node.put("wrapped_item_dek", java.util.Base64.getEncoder().encodeToString(wrappedItemDek))
    if (itemDekFormat != null) node.put("item_dek_format", itemDekFormat)
    if (wrappedThumbnailDek != null) node.put("wrapped_thumbnail_dek", java.util.Base64.getEncoder().encodeToString(wrappedThumbnailDek))
    if (thumbnailDekFormat != null) node.put("thumbnail_dek_format", thumbnailDekFormat)
    return node.toString()
}

private fun addPlotItemRoute(flowService: FlowService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "items" meta {
        summary = "Manually add an item to a collection plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAddPlotItem(plotId, request, flowService) }
    }
}

private fun handleAddPlotItem(plotId: UUID, request: Request, flowService: FlowService): Response {
    val node = try { flowMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val uploadIdStr = node.get("uploadId")?.asText() ?: node.get("upload_id")?.asText()
        ?: return Response(BAD_REQUEST).body("uploadId is required")
    val uploadId = try { UUID.fromString(uploadIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("uploadId is not a valid UUID") }

    return when (val result = flowService.addPlotItem(
        plotId = plotId,
        uploadId = uploadId,
        userId = request.authUserId(),
        wrappedItemDekB64 = node.get("wrappedItemDek")?.asText(),
        itemDekFormatRaw = node.get("itemDekFormat")?.asText(),
        wrappedThumbnailDekB64 = node.get("wrappedThumbnailDek")?.asText(),
        thumbnailDekFormatRaw = node.get("thumbnailDekFormat")?.asText(),
    )) {
        FlowService.AddPlotItemResult.Success        -> Response(CREATED)
        FlowService.AddPlotItemResult.AlreadyPresent -> Response(CONFLICT).body("Item already in collection")
        FlowService.AddPlotItemResult.PlotNotOwned   -> Response(NOT_FOUND)
        FlowService.AddPlotItemResult.UploadNotOwned -> Response(NOT_FOUND)
        FlowService.AddPlotItemResult.PlotClosed     -> Response(FORBIDDEN).body("Plot is closed")
        is FlowService.AddPlotItemResult.Invalid     -> Response(BAD_REQUEST).body(result.message)
    }
}

private fun removePlotItemRoute(flowService: FlowService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "items" / uploadId meta {
        summary = "Remove an item from a collection plot"
    } bindContract DELETE to { pId: UUID, _: String, uId: UUID ->
        { request: Request ->
            when (flowService.removePlotItem(pId, uId, request.authUserId())) {
                PlotItemRepository.RemoveItemResult.Success   -> Response(NO_CONTENT)
                PlotItemRepository.RemoveItemResult.NotFound  -> Response(NOT_FOUND)
                PlotItemRepository.RemoveItemResult.Forbidden -> Response(FORBIDDEN)
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
