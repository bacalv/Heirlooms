package digital.heirlooms.server

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
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
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.lens.Path
import org.http4k.lens.uuid
import java.util.UUID
import com.fasterxml.jackson.databind.ObjectMapper

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
        description = "Returns all plots for the current user ordered by sort_order, with tag criteria embedded."
    } bindContract GET to { _: Request ->
        val plots = database.listPlots()
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
        description = "Creates a user-defined plot. Body: {\"name\": \"...\", \"tag_criteria\": [...]}."
    } bindContract POST to { request: Request ->
        val (name, tagCriteria, err) = parsePlotBody(request)
        if (err != null) {
            Response(BAD_REQUEST).body(err)
        } else if (name.isNullOrBlank()) {
            Response(BAD_REQUEST).body("name is required")
        } else {
            val plot = database.createPlot(name, tagCriteria ?: emptyList())
            Response(CREATED).header("Content-Type", "application/json").body(plot.toJson())
        }
    }

private fun updatePlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id meta {
        summary = "Update a plot"
        description = "Updates name, sort_order, or tag_criteria. Cannot modify system-defined plots."
    } bindContract PUT to { plotId: UUID ->
        { request: Request ->
            val node = try { plotMapper.readTree(request.bodyString()) } catch (_: Exception) { null }
            if (node == null) {
                Response(BAD_REQUEST).body("Invalid JSON")
            } else {
                val name = node.get("name")?.asText()?.takeIf { it.isNotBlank() }
                val sortOrder = node.get("sort_order")?.asInt()
                val tagCriteria = node.get("tag_criteria")
                    ?.takeIf { it.isArray }
                    ?.map { it.asText() }
                    ?.filter { it.isNotBlank() }
                when (val result = database.updatePlot(plotId, name, sortOrder, tagCriteria)) {
                    is Database.PlotUpdateResult.Success ->
                        Response(OK).header("Content-Type", "application/json").body(result.plot.toJson())
                    is Database.PlotUpdateResult.NotFound -> Response(NOT_FOUND)
                    is Database.PlotUpdateResult.SystemDefined ->
                        Response(FORBIDDEN).header("Content-Type", "application/json")
                            .body("""{"error":"Cannot modify a system-defined plot"}""")
                }
            }
        }
    }
}

private fun deletePlotRoute(database: Database): ContractRoute {
    val id = Path.uuid().of("id")
    return "/plots" / id meta {
        summary = "Delete a plot"
        description = "Deletes a user-defined plot. Cannot delete system-defined plots."
    } bindContract DELETE to { plotId: UUID ->
        { _: Request ->
            when (database.deletePlot(plotId)) {
                is Database.PlotDeleteResult.Success -> Response(NO_CONTENT)
                is Database.PlotDeleteResult.NotFound -> Response(NOT_FOUND)
                is Database.PlotDeleteResult.SystemDefined ->
                    Response(FORBIDDEN).header("Content-Type", "application/json")
                        .body("""{"error":"Cannot delete a system-defined plot"}""")
            }
        }
    }
}

private data class PlotBodyParsed(val name: String?, val tagCriteria: List<String>?, val error: String?)

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
                    try { java.util.UUID.fromString(idStr) to sortOrder } catch (_: Exception) { null }
                } else null
            }.toList()

            if (updates.isEmpty()) {
                Response(BAD_REQUEST).body("No valid {id, sort_order} entries found")
            } else {
                try {
                    when (database.batchReorderPlots(updates)) {
                        is Database.BatchReorderResult.Success ->
                            Response(NO_CONTENT)
                        is Database.BatchReorderResult.SystemDefined ->
                            Response(FORBIDDEN).header("Content-Type", "application/json")
                                .body("""{"error":"Cannot reorder system-defined plots"}""")
                        is Database.BatchReorderResult.NotFound ->
                            Response(NOT_FOUND)
                    }
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body("Failed to reorder plots: ${e.message}")
                }
            }
        }
    }

private fun parsePlotBody(request: Request): PlotBodyParsed {
    return try {
        val node = plotMapper.readTree(request.bodyString())
        val name = node?.get("name")?.asText()
        val criteria = node?.get("tag_criteria")
            ?.takeIf { it.isArray }
            ?.map { it.asText() }
            ?.filter { it.isNotBlank() }
        PlotBodyParsed(name, criteria, null)
    } catch (_: Exception) {
        PlotBodyParsed(null, null, "Invalid JSON")
    }
}

internal fun PlotRecord.toJson(): String {
    val factory = JsonNodeFactory.instance
    val node = factory.objectNode()
    node.put("id", id.toString())
    if (ownerUserId != null) node.put("owner_user_id", ownerUserId.toString()) else node.putNull("owner_user_id")
    node.put("name", name)
    node.put("sort_order", sortOrder)
    node.put("is_system_defined", isSystemDefined)
    node.put("created_at", createdAt.toString())
    node.put("updated_at", updatedAt.toString())
    val arr = node.putArray("tag_criteria")
    tagCriteria.forEach { arr.add(it) }
    return node.toString()
}
