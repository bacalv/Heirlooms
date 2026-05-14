package digital.heirlooms.server.routes.plot

import digital.heirlooms.server.authUserId
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.representation.plot.toJson
import digital.heirlooms.server.service.plot.PlotService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID

private val plotMapper = ObjectMapper()

fun plotRoutes(plotService: PlotService): List<ContractRoute> = listOf(
    listPlotsRoute(plotService),
    createPlotRoute(plotService),
    updatePlotRoute(plotService),
    deletePlotRoute(plotService),
    batchReorderPlotsRoute(plotService),
)

private fun listPlotsRoute(plotService: PlotService): ContractRoute =
    "/plots" meta {
        summary = "List plots"
        description = "Returns all plots for the current user ordered by sort_order."
    } bindContract GET to { request: Request ->
        val userId = request.authUserId()
        val plots = plotService.listPlots(userId)
        val json = buildString {
            append("[")
            plots.forEachIndexed { i, p -> if (i > 0) append(","); append(p.toJson(userId)) }
            append("]")
        }
        Response(OK).header("Content-Type", "application/json").body(json)
    }

private fun createPlotRoute(plotService: PlotService): ContractRoute =
    "/plots" meta {
        summary = "Create a plot"
        description = "Creates a user-defined plot."
    } bindContract POST to { request: Request ->
        val body = parsePlotBody(request)
        when {
            body.error != null -> Response(BAD_REQUEST).body(body.error)
            body.name.isNullOrBlank() -> Response(BAD_REQUEST).body("name is required")
            else -> {
                val vis = body.visibility ?: "private"
                when (val result = plotService.createPlot(
                    name = body.name,
                    criteriaNode = body.criteriaNode,
                    showInGarden = body.showInGarden ?: true,
                    visibility = vis,
                    wrappedPlotKey = body.wrappedPlotKey,
                    plotKeyFormat = body.plotKeyFormat,
                    userId = request.authUserId(),
                )) {
                    is PlotService.CreateResult.Created ->
                        Response(CREATED).header("Content-Type", "application/json").body(result.plot.toJson())
                    is PlotService.CreateResult.Invalid ->
                        Response(BAD_REQUEST).body(result.message)
                }
            }
        }
    }

private fun updatePlotRoute(plotService: PlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id meta {
        summary = "Update a plot"
        description = "Updates name, sort_order, criteria, or showInGarden."
    } bindContract PUT to { plotId: UUID ->
        { request: Request -> handleUpdatePlot(plotId, request, plotService) }
    }
}

private fun handleUpdatePlot(plotId: UUID, request: Request, plotService: PlotService): Response {
    val node = try { plotMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")

    val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
    val sortOrder = node.get("sort_order")?.asInt()
    val showInGarden = node.get("show_in_garden")?.asBoolean()
        ?: node.get("showInGarden")?.asBoolean()
    val criteriaNode = node.get("criteria")?.takeIf { !it.isNull }

    val (result, validationError) = plotService.updatePlotWithValidation(
        plotId, name, sortOrder, criteriaNode, showInGarden, request.authUserId()
    )
    if (validationError != null) return Response(BAD_REQUEST).body(validationError)
    return when (result) {
        is PlotRepository.PlotUpdateResult.Success ->
            Response(OK).header("Content-Type", "application/json").body(result.plot.toJson())
        is PlotRepository.PlotUpdateResult.NotFound -> Response(NOT_FOUND)
        is PlotRepository.PlotUpdateResult.SystemDefined ->
            Response(FORBIDDEN).header("Content-Type", "application/json")
                .body("""{"error":"Cannot modify a system-defined plot"}""")
        null -> Response(NOT_FOUND)
    }
}

private fun deletePlotRoute(plotService: PlotService): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id meta {
        summary = "Delete a plot"
        description = "Deletes a user-defined plot."
    } bindContract DELETE to { plotId: UUID ->
        { request: Request ->
            when (plotService.deletePlot(plotId, request.authUserId())) {
                is PlotRepository.PlotDeleteResult.Success -> Response(NO_CONTENT)
                is PlotRepository.PlotDeleteResult.NotFound -> Response(NOT_FOUND)
                is PlotRepository.PlotDeleteResult.SystemDefined ->
                    Response(FORBIDDEN).header("Content-Type", "application/json")
                        .body("""{"error":"Cannot delete a system-defined plot"}""")
            }
        }
    }
}

private fun batchReorderPlotsRoute(plotService: PlotService): ContractRoute =
    "/plots" meta {
        summary = "Batch reorder plots"
        description = "Updates sort_order for multiple user-defined plots atomically."
    } bindContract PATCH to { request: Request ->
        val node = try { plotMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        if (node == null || !node.isArray) {
            Response(BAD_REQUEST).body("Expected JSON array of {id, sort_order} objects")
        } else {
            val updates = node.elements().asSequence().mapNotNull { el ->
                val idStr = el.get("id")?.asText()
                val sortOrder = el.get("sort_order")?.asInt()
                if (idStr != null && sortOrder != null) {
                    try { UUID.fromString(idStr) to sortOrder } catch (_: Exception) { null }
                } else null
            }.toList()

            if (updates.isEmpty()) {
                Response(BAD_REQUEST).body("No valid {id, sort_order} entries found")
            } else {
                try {
                    when (plotService.batchReorderPlots(updates, request.authUserId())) {
                        is PlotRepository.BatchReorderResult.Success -> Response(NO_CONTENT)
                        is PlotRepository.BatchReorderResult.SystemDefined ->
                            Response(FORBIDDEN).header("Content-Type", "application/json")
                                .body("""{"error":"Cannot reorder system-defined plots"}""")
                        is PlotRepository.BatchReorderResult.NotFound -> Response(NOT_FOUND)
                    }
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body("Failed to reorder plots: ${e.message}")
                }
            }
        }
    }

private data class PlotBodyParsed(
    val name: String?,
    val criteriaNode: JsonNode?,
    val showInGarden: Boolean?,
    val visibility: String?,
    val wrappedPlotKey: String?,
    val plotKeyFormat: String?,
    val error: String?,
)

private fun parsePlotBody(request: Request): PlotBodyParsed {
    return try {
        val node = plotMapper.readTree(request.bodyString())
        val name = node?.get("name")?.asText()
        val criteriaNode = node?.get("criteria")?.takeIf { !it.isNull }
        val showInGarden = node?.get("show_in_garden")?.asBoolean()
            ?: node?.get("showInGarden")?.asBoolean()
        val visibility = node?.get("visibility")?.asText()
        val wrappedPlotKey = node?.get("wrappedPlotKey")?.asText()
        val plotKeyFormat = node?.get("plotKeyFormat")?.asText()
        PlotBodyParsed(name, criteriaNode, showInGarden, visibility, wrappedPlotKey, plotKeyFormat, null)
    } catch (_: Exception) {
        PlotBodyParsed(null, null, null, null, null, null, "Invalid JSON")
    }
}
