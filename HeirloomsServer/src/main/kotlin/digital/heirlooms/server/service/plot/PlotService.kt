package digital.heirlooms.server.service.plot

import digital.heirlooms.server.CriteriaCycleException
import digital.heirlooms.server.CriteriaValidationException
import digital.heirlooms.server.Database
import digital.heirlooms.server.domain.plot.PlotRecord
import digital.heirlooms.server.repository.plot.PlotRepository
import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/**
 * Encapsulates business logic for plot CRUD: criteria validation/serialisation,
 * visibility guards for shared plots, reorder, and delete.
 */
class PlotService(
    private val database: Database,
) {
    // ---- Criteria validation ---------------------------------------------------

    sealed class CriteriaResult {
        data class Ok(val json: String) : CriteriaResult()
        data class Error(val message: String) : CriteriaResult()
    }

    fun validateAndSerializeCriteria(node: JsonNode, userId: UUID): CriteriaResult {
        return try {
            database.withCriteriaValidation(node, userId)
            CriteriaResult.Ok(node.toString())
        } catch (e: CriteriaValidationException) {
            CriteriaResult.Error(e.message ?: "Invalid criteria")
        } catch (e: CriteriaCycleException) {
            CriteriaResult.Error(e.message ?: "Circular plot_ref detected")
        } catch (e: Exception) {
            CriteriaResult.Error("Criteria validation failed: ${e.message}")
        }
    }

    // ---- CRUD ------------------------------------------------------------------

    fun listPlots(userId: UUID): List<PlotRecord> = database.listPlots(userId)

    sealed class CreateResult {
        data class Created(val plot: PlotRecord) : CreateResult()
        data class Invalid(val message: String) : CreateResult()
    }

    fun createPlot(
        name: String,
        criteriaNode: JsonNode?,
        showInGarden: Boolean,
        visibility: String,
        wrappedPlotKey: String?,
        plotKeyFormat: String?,
        userId: UUID,
    ): CreateResult {
        if (visibility == "shared") {
            if (wrappedPlotKey.isNullOrBlank()) return CreateResult.Invalid("wrappedPlotKey required for shared plots")
            if (plotKeyFormat.isNullOrBlank()) return CreateResult.Invalid("plotKeyFormat required for shared plots")
        }
        val criteriaJson = if (criteriaNode != null) {
            when (val r = validateAndSerializeCriteria(criteriaNode, userId)) {
                is CriteriaResult.Error -> return CreateResult.Invalid(r.message)
                is CriteriaResult.Ok -> r.json
            }
        } else null
        val plot = database.createPlot(
            name = name,
            criteria = criteriaJson,
            showInGarden = showInGarden,
            visibility = visibility,
            wrappedPlotKeyB64 = wrappedPlotKey,
            plotKeyFormat = plotKeyFormat,
            userId = userId,
        )
        return CreateResult.Created(plot)
    }

    fun updatePlot(
        plotId: UUID,
        name: String?,
        sortOrder: Int?,
        criteriaNode: JsonNode?,
        showInGarden: Boolean?,
        userId: UUID,
    ): PlotRepository.PlotUpdateResult {
        val criteriaJson: String? = if (criteriaNode != null) {
            when (val result = validateAndSerializeCriteria(criteriaNode, userId)) {
                is CriteriaResult.Error ->
                    return PlotRepository.PlotUpdateResult.NotFound // wrapped as validation error in handler
                is CriteriaResult.Ok -> result.json
            }
        } else null
        return database.updatePlot(plotId, name, sortOrder, criteriaJson, showInGarden, userId)
    }

    fun updatePlotWithValidation(
        plotId: UUID,
        name: String?,
        sortOrder: Int?,
        criteriaNode: JsonNode?,
        showInGarden: Boolean?,
        userId: UUID,
    ): Pair<PlotRepository.PlotUpdateResult?, String?> {
        val criteriaJson: String? = if (criteriaNode != null) {
            when (val result = validateAndSerializeCriteria(criteriaNode, userId)) {
                is CriteriaResult.Error -> return Pair(null, result.message)
                is CriteriaResult.Ok -> result.json
            }
        } else null
        return Pair(database.updatePlot(plotId, name, sortOrder, criteriaJson, showInGarden, userId), null)
    }

    fun deletePlot(plotId: UUID, userId: UUID): PlotRepository.PlotDeleteResult =
        database.deletePlot(plotId, userId)

    fun batchReorderPlots(updates: List<Pair<UUID, Int>>, userId: UUID): PlotRepository.BatchReorderResult =
        database.batchReorderPlots(updates, userId)
}
