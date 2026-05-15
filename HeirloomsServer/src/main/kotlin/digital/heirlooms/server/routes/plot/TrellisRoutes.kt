package digital.heirlooms.server.routes.plot

import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.representation.plot.toJson
import digital.heirlooms.server.representation.upload.toJson
import digital.heirlooms.server.service.plot.TrellisService
import com.fasterxml.jackson.databind.ObjectMapper
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

private val trellisMapper = ObjectMapper()

fun trellisRoutes(trellisService: TrellisService): List<ContractRoute> = listOf(
    listTrellisesRoute(trellisService),
    createTrellisRoute(trellisService),
    updateTrellisRoute(trellisService),
    deleteTrellisRoute(trellisService),
    getTrellisStagingRoute(trellisService),
    // Deprecated aliases kept for client coordination — point to same handlers
    listTrellisesDeprecatedRoute(trellisService),
    createTrellisDeprecatedRoute(trellisService),
    updateTrellisDeprecatedRoute(trellisService),
    deleteTrellisDeprecatedRoute(trellisService),
    getTrellisStagingDeprecatedRoute(trellisService),
)

fun plotItemRoutes(trellisService: TrellisService): List<ContractRoute> = listOf(
    getPlotStagingRoute(trellisService),
    approveStagingRoute(trellisService),
    rejectStagingRoute(trellisService),
    deleteDecisionRoute(trellisService),
    getRejectedRoute(trellisService),
    getPlotItemsRoute(trellisService),
    addPlotItemRoute(trellisService),
    removePlotItemRoute(trellisService),
)

// ---- Trellis CRUD (new paths) ------------------------------------------------

private fun listTrellisesRoute(trellisService: TrellisService): ContractRoute =
    "/trellises" meta { summary = "List trellises" } bindContract GET to { request: Request ->
        val trellises = trellisService.listTrellises(request.authUserId())
        val json = "[${trellises.joinToString(",") { it.toJson() }}]"
        Response(OK).header("Content-Type", "application/json").body(json)
    }

private fun createTrellisRoute(trellisService: TrellisService): ContractRoute =
    "/trellises" meta { summary = "Create a trellis" } bindContract POST to { request: Request ->
        handleCreateTrellis(request, trellisService)
    }

private fun handleCreateTrellis(request: Request, trellisService: TrellisService): Response {
    val node = try { trellisMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
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

    return when (val result = trellisService.createTrellis(name, criteriaNode, targetPlotId, requiresStaging, request.authUserId())) {
        is TrellisService.CreateTrellisResult.Created ->
            Response(CREATED).header("Content-Type", "application/json").body(result.trellis.toJson())
        is TrellisService.CreateTrellisResult.Invalid ->
            Response(BAD_REQUEST).body(result.message)
    }
}

private fun updateTrellisRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/trellises" / id meta { summary = "Update a trellis" } bindContract PUT to { trellisId: UUID ->
        { request: Request -> handleUpdateTrellis(trellisId, request, trellisService) }
    }
}

private fun handleUpdateTrellis(trellisId: UUID, request: Request, trellisService: TrellisService): Response {
    val node = try { trellisMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
    val requiresStaging = node.get("requiresStaging")?.asBoolean()
        ?: node.get("requires_staging")?.asBoolean()
    val criteriaNode = node.get("criteria")?.takeIf { !it.isNull }

    return when (val result = trellisService.updateTrellis(trellisId, name, criteriaNode, requiresStaging, request.authUserId())) {
        is TrellisService.UpdateTrellisResult.Updated ->
            Response(OK).header("Content-Type", "application/json").body(result.trellis.toJson())
        TrellisService.UpdateTrellisResult.NotFound -> Response(NOT_FOUND)
        is TrellisService.UpdateTrellisResult.Invalid -> Response(BAD_REQUEST).body(result.message)
    }
}

private fun deleteTrellisRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/trellises" / id meta { summary = "Delete a trellis" } bindContract DELETE to { trellisId: UUID ->
        { request: Request ->
            if (trellisService.deleteTrellis(trellisId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

// ---- Staging: trellis-level -------------------------------------------------

private fun getTrellisStagingRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/trellises" / id / "staging" meta {
        summary = "Get staging items for a trellis"
    } bindContract GET to { trellisId: UUID, _: String ->
        { request: Request ->
            val items = trellisService.getStagingItems(trellisId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Deprecated aliases (/flows → /trellises) --------------------------------

private fun listTrellisesDeprecatedRoute(trellisService: TrellisService): ContractRoute =
    "/flows" meta { summary = "List trellises (deprecated — use /trellises)" } bindContract GET to { request: Request ->
        val trellises = trellisService.listTrellises(request.authUserId())
        val json = "[${trellises.joinToString(",") { it.toJson() }}]"
        Response(OK).header("Content-Type", "application/json").body(json)
    }

private fun createTrellisDeprecatedRoute(trellisService: TrellisService): ContractRoute =
    "/flows" meta { summary = "Create a trellis (deprecated — use /trellises)" } bindContract POST to { request: Request ->
        handleCreateTrellis(request, trellisService)
    }

private fun updateTrellisDeprecatedRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id meta { summary = "Update a trellis (deprecated — use /trellises)" } bindContract PUT to { trellisId: UUID ->
        { request: Request -> handleUpdateTrellis(trellisId, request, trellisService) }
    }
}

private fun deleteTrellisDeprecatedRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id meta { summary = "Delete a trellis (deprecated — use /trellises)" } bindContract DELETE to { trellisId: UUID ->
        { request: Request ->
            if (trellisService.deleteTrellis(trellisId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

private fun getTrellisStagingDeprecatedRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/flows" / id / "staging" meta {
        summary = "Get staging items for a trellis (deprecated — use /trellises)"
    } bindContract GET to { trellisId: UUID, _: String ->
        { request: Request ->
            val items = trellisService.getStagingItems(trellisId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Staging: plot-level ----------------------------------------------------

private fun getPlotStagingRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "staging" meta {
        summary = "Get all pending staging items for a plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val items = trellisService.getStagingItemsForPlot(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

private fun approveStagingRoute(trellisService: TrellisService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "approve" meta {
        summary = "Approve a staging item"
    } bindContract POST to { pId: UUID, _: String, uId: UUID, _: String ->
        { request: Request ->
            val node = try { trellisMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
            val sourceTrellisId = try { node?.get("sourceTrellisId")?.asText()?.let { UUID.fromString(it) }
                ?: node?.get("sourceFlowId")?.asText()?.let { UUID.fromString(it) } } catch (_: Exception) { null }
            when (val result = trellisService.approveStagingItem(
                plotId = pId,
                uploadId = uId,
                sourceTrellisId = sourceTrellisId,
                userId = request.authUserId(),
                wrappedItemDekB64 = node?.get("wrappedItemDek")?.asText(),
                itemDekFormatRaw = node?.get("itemDekFormat")?.asText(),
                wrappedThumbnailDekB64 = node?.get("wrappedThumbnailDek")?.asText(),
                thumbnailDekFormatRaw = node?.get("thumbnailDekFormat")?.asText(),
            )) {
                TrellisService.ApproveStagingResult.Success          -> Response(NO_CONTENT)
                TrellisService.ApproveStagingResult.DuplicateContent -> Response(NO_CONTENT)
                TrellisService.ApproveStagingResult.AlreadyApproved  -> Response(CONFLICT).body("Item is already in the collection")
                TrellisService.ApproveStagingResult.NotFound         -> Response(NOT_FOUND)
                TrellisService.ApproveStagingResult.PlotNotOwned     -> Response(NOT_FOUND)
                TrellisService.ApproveStagingResult.PlotClosed       -> Response(FORBIDDEN).body("Plot is closed")
                is TrellisService.ApproveStagingResult.Invalid       -> Response(BAD_REQUEST).body(result.message)
            }
        }
    }
}

private fun rejectStagingRoute(trellisService: TrellisService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "reject" meta {
        summary = "Reject a staging item"
    } bindContract POST to { pId: UUID, _: String, uId: UUID, _: String ->
        { request: Request ->
            val sourceTrellisId = try {
                val node = trellisMapper.readTree(request.bodyString())
                node?.get("sourceTrellisId")?.asText()?.let { UUID.fromString(it) }
                    ?: node?.get("sourceFlowId")?.asText()?.let { UUID.fromString(it) }
            } catch (_: Exception) { null }
            when (trellisService.rejectStagingItem(pId, uId, sourceTrellisId, request.authUserId())) {
                PlotItemRepository.RejectResult.Success         -> Response(NO_CONTENT)
                PlotItemRepository.RejectResult.AlreadyApproved -> Response(CONFLICT).body("Item is already approved — remove it from the collection first")
                PlotItemRepository.RejectResult.NotFound        -> Response(NOT_FOUND)
                PlotItemRepository.RejectResult.PlotNotOwned    -> Response(NOT_FOUND)
            }
        }
    }
}

private fun deleteDecisionRoute(trellisService: TrellisService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "staging" / uploadId / "decision" meta {
        summary = "Remove a staging decision (un-reject)"
    } bindContract DELETE to { pId: UUID, _: String, uId: UUID, _: String ->
        { request: Request ->
            if (trellisService.deleteDecision(pId, uId, request.authUserId())) Response(NO_CONTENT)
            else Response(NOT_FOUND)
        }
    }
}

private fun getRejectedRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "staging" / "rejected" meta {
        summary = "List rejected staging items for a plot"
    } bindContract GET to { plotId: UUID, _: String, _: String ->
        { request: Request ->
            val items = trellisService.getRejectedItems(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

// ---- Collection plot items --------------------------------------------------

private fun getPlotItemsRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "items" meta {
        summary = "List items in a collection plot"
    } bindContract GET to { plotId: UUID, _: String ->
        { request: Request ->
            val items = trellisService.getPlotItems(plotId, request.authUserId())
            val json = "[${items.joinToString(",") { it.toJson() }}]"
            Response(OK).header("Content-Type", "application/json").body(json)
        }
    }
}

private fun addPlotItemRoute(trellisService: TrellisService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id / "items" meta {
        summary = "Manually add an item to a collection plot"
    } bindContract POST to { plotId: UUID, _: String ->
        { request: Request -> handleAddPlotItem(plotId, request, trellisService) }
    }
}

private fun handleAddPlotItem(plotId: UUID, request: Request, trellisService: TrellisService): Response {
    val node = try { trellisMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")
    val uploadIdStr = node.get("uploadId")?.asText() ?: node.get("upload_id")?.asText()
        ?: return Response(BAD_REQUEST).body("uploadId is required")
    val uploadId = try { UUID.fromString(uploadIdStr) }
        catch (_: Exception) { return Response(BAD_REQUEST).body("uploadId is not a valid UUID") }

    return when (val result = trellisService.addPlotItem(
        plotId = plotId,
        uploadId = uploadId,
        userId = request.authUserId(),
        wrappedItemDekB64 = node.get("wrappedItemDek")?.asText(),
        itemDekFormatRaw = node.get("itemDekFormat")?.asText(),
        wrappedThumbnailDekB64 = node.get("wrappedThumbnailDek")?.asText(),
        thumbnailDekFormatRaw = node.get("thumbnailDekFormat")?.asText(),
    )) {
        TrellisService.AddPlotItemResult.Success        -> Response(CREATED)
        TrellisService.AddPlotItemResult.AlreadyPresent -> Response(CONFLICT).body("Item already in collection")
        TrellisService.AddPlotItemResult.PlotNotOwned   -> Response(NOT_FOUND)
        TrellisService.AddPlotItemResult.UploadNotOwned -> Response(NOT_FOUND)
        TrellisService.AddPlotItemResult.PlotClosed     -> Response(FORBIDDEN).body("Plot is closed")
        is TrellisService.AddPlotItemResult.Invalid     -> Response(BAD_REQUEST).body(result.message)
    }
}

private fun removePlotItemRoute(trellisService: TrellisService): ContractRoute {
    val plotId = Path.uuid().of("id")
    val uploadId = Path.uuid().of("uploadId")
    return "/plots" / plotId / "items" / uploadId meta {
        summary = "Remove an item from a collection plot"
    } bindContract DELETE to { pId: UUID, _: String, uId: UUID ->
        { request: Request ->
            when (trellisService.removePlotItem(pId, uId, request.authUserId())) {
                PlotItemRepository.RemoveItemResult.Success   -> Response(NO_CONTENT)
                PlotItemRepository.RemoveItemResult.NotFound  -> Response(NOT_FOUND)
                PlotItemRepository.RemoveItemResult.Forbidden -> Response(FORBIDDEN)
            }
        }
    }
}
