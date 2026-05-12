package digital.heirlooms.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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

fun plotRoutes(database: Database): List<ContractRoute> = listOf(
    listPlotsRoute(database),
    createPlotRoute(database),
    updatePlotRoute(database),
    deletePlotRoute(database),
    batchReorderPlotsRoute(database),
)

private fun listPlotsRoute(database: Database): ContractRoute =
    "/plots" meta {
        summary = "List plots"
        description = "Returns all plots for the current user ordered by sort_order."
    } bindContract GET to { request: Request ->
        val plots = database.listPlots(request.authUserId())
        val json = buildString {
            append("[")
            plots.forEachIndexed { i, p -> if (i > 0) append(","); append(p.toJson()) }
            append("]")
        }
        Response(OK).header("Content-Type", "application/json").body(json)
    }

private fun createPlotRoute(database: Database): ContractRoute =
    "/plots" meta {
        summary = "Create a plot"
        description = "Creates a user-defined plot. Body: {\"name\": \"...\", \"criteria\": {...}, \"showInGarden\": true}."
    } bindContract POST to { request: Request ->
        val body = parsePlotBody(request)
        when {
            body.error != null -> Response(BAD_REQUEST).body(body.error)
            body.name.isNullOrBlank() -> Response(BAD_REQUEST).body("name is required")
            else -> {
                val vis = body.visibility ?: "private"
                if (vis == "shared") {
                    if (body.wrappedPlotKey.isNullOrBlank())
                        return@to Response(BAD_REQUEST).body("wrappedPlotKey required for shared plots")
                    if (body.plotKeyFormat.isNullOrBlank())
                        return@to Response(BAD_REQUEST).body("plotKeyFormat required for shared plots")
                }
                val criteriaJson = body.criteriaNode?.let { validateAndSerializeCriteria(it, request.authUserId(), database) }
                if (criteriaJson is CriteriaSerializeResult.Error)
                    return@to Response(BAD_REQUEST).body(criteriaJson.message)
                val plot = database.createPlot(
                    name = body.name,
                    criteria = (criteriaJson as? CriteriaSerializeResult.Ok)?.json,
                    showInGarden = body.showInGarden ?: true,
                    visibility = vis,
                    wrappedPlotKeyB64 = body.wrappedPlotKey,
                    plotKeyFormat = body.plotKeyFormat,
                    userId = request.authUserId(),
                )
                Response(CREATED).header("Content-Type", "application/json").body(plot.toJson())
            }
        }
    }

private fun updatePlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id meta {
        summary = "Update a plot"
        description = "Updates name, sort_order, criteria, or showInGarden. Cannot modify system-defined plots."
    } bindContract PUT to { plotId: UUID ->
        { request: Request -> handleUpdatePlot(plotId, request, database) }
    }
}

private fun handleUpdatePlot(plotId: UUID, request: Request, database: Database): Response {
    val node = try { plotMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
        ?: return Response(BAD_REQUEST).body("Invalid JSON")

    val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
    val sortOrder = node.get("sort_order")?.asInt()
    val showInGarden = node.get("show_in_garden")?.asBoolean()
        ?: node.get("showInGarden")?.asBoolean()
    val criteriaNode = node.get("criteria")?.takeIf { !it.isNull }

    val criteriaJson: String? = if (criteriaNode != null) {
        val result = validateAndSerializeCriteria(criteriaNode, request.authUserId(), database)
        if (result is CriteriaSerializeResult.Error)
            return Response(BAD_REQUEST).body(result.message)
        (result as CriteriaSerializeResult.Ok).json
    } else null

    return when (val result = database.updatePlot(
        plotId, name, sortOrder, criteriaJson, showInGarden, request.authUserId()
    )) {
        is Database.PlotUpdateResult.Success ->
            Response(OK).header("Content-Type", "application/json").body(result.plot.toJson())
        is Database.PlotUpdateResult.NotFound -> Response(NOT_FOUND)
        is Database.PlotUpdateResult.SystemDefined ->
            Response(FORBIDDEN).header("Content-Type", "application/json")
                .body("""{"error":"Cannot modify a system-defined plot"}""")
    }
}

private fun deletePlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id meta {
        summary = "Delete a plot"
        description = "Deletes a user-defined plot. Cannot delete system-defined plots."
    } bindContract DELETE to { plotId: UUID ->
        { request: Request ->
            when (database.deletePlot(plotId, request.authUserId())) {
                is Database.PlotDeleteResult.Success -> Response(NO_CONTENT)
                is Database.PlotDeleteResult.NotFound -> Response(NOT_FOUND)
                is Database.PlotDeleteResult.SystemDefined ->
                    Response(FORBIDDEN).header("Content-Type", "application/json")
                        .body("""{"error":"Cannot delete a system-defined plot"}""")
            }
        }
    }
}

private fun batchReorderPlotsRoute(database: Database): ContractRoute =
    "/plots" meta {
        summary = "Batch reorder plots"
        description = "Updates sort_order for multiple user-defined plots atomically. Body: [{\"id\":\"uuid\",\"sort_order\":0},...]."
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
                    when (database.batchReorderPlots(updates, request.authUserId())) {
                        is Database.BatchReorderResult.Success -> Response(NO_CONTENT)
                        is Database.BatchReorderResult.SystemDefined ->
                            Response(FORBIDDEN).header("Content-Type", "application/json")
                                .body("""{"error":"Cannot reorder system-defined plots"}""")
                        is Database.BatchReorderResult.NotFound -> Response(NOT_FOUND)
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

private sealed class CriteriaSerializeResult {
    data class Ok(val json: String) : CriteriaSerializeResult()
    data class Error(val message: String) : CriteriaSerializeResult()
}

private fun validateAndSerializeCriteria(
    node: JsonNode,
    userId: UUID,
    database: Database,
): CriteriaSerializeResult {
    return try {
        database.withCriteriaValidation(node, userId)
        CriteriaSerializeResult.Ok(node.toString())
    } catch (e: CriteriaValidationException) {
        CriteriaSerializeResult.Error(e.message ?: "Invalid criteria")
    } catch (e: CriteriaCycleException) {
        CriteriaSerializeResult.Error(e.message ?: "Circular plot_ref detected")
    } catch (e: Exception) {
        CriteriaSerializeResult.Error("Criteria validation failed: ${e.message}")
    }
}

internal fun PlotRecord.toJson(): String {
    val factory = JsonNodeFactory.instance
    val node = factory.objectNode()
    node.put("id", id.toString())
    if (ownerUserId != null) node.put("owner_user_id", ownerUserId.toString())
    else node.putNull("owner_user_id")
    node.put("name", name)
    node.put("sort_order", sortOrder)
    node.put("is_system_defined", isSystemDefined)
    node.put("show_in_garden", showInGarden)
    node.put("visibility", visibility)
    node.put("created_at", createdAt.toString())
    node.put("updated_at", updatedAt.toString())
    if (criteria != null) {
        node.set<JsonNode>("criteria", plotMapper.readTree(criteria))
    } else {
        node.putNull("criteria")
    }
    return node.toString()
}
