package digital.heirlooms.server.service.plot

import digital.heirlooms.server.CriteriaCycleException
import digital.heirlooms.server.CriteriaValidationException
import digital.heirlooms.server.Database
import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.repository.plot.FlowRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import com.fasterxml.jackson.databind.JsonNode
import java.util.Base64
import java.util.UUID

/**
 * Encapsulates business logic for flow CRUD and staging operations:
 * criteria validation on flow create/update, DEK decoding for staging
 * approval, and plot-item management.
 */
class FlowService(
    private val database: Database,
) {
    // ---- Criteria validation (reused from PlotService) ----------------------

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

    // ---- Flow CRUD ----------------------------------------------------------

    fun listFlows(userId: UUID): List<FlowRecord> = database.listFlows(userId)

    fun getFlowById(id: UUID, userId: UUID): FlowRecord? = database.getFlowById(id, userId)

    sealed class CreateFlowResult {
        data class Created(val flow: FlowRecord) : CreateFlowResult()
        data class Invalid(val message: String) : CreateFlowResult()
    }

    fun createFlow(
        name: String,
        criteriaNode: JsonNode,
        targetPlotId: UUID,
        requiresStaging: Boolean,
        userId: UUID,
    ): CreateFlowResult {
        val criteriaJson = when (val r = validateAndSerializeCriteria(criteriaNode, userId)) {
            is CriteriaResult.Error -> return CreateFlowResult.Invalid(r.message)
            is CriteriaResult.Ok -> r.json
        }
        return when (val result = database.createFlow(name, criteriaJson, targetPlotId, requiresStaging, userId)) {
            is FlowRepository.FlowCreateResult.Success -> CreateFlowResult.Created(result.flow)
            is FlowRepository.FlowCreateResult.Error -> CreateFlowResult.Invalid(result.message)
        }
    }

    sealed class UpdateFlowResult {
        data class Updated(val flow: FlowRecord) : UpdateFlowResult()
        object NotFound : UpdateFlowResult()
        data class Invalid(val message: String) : UpdateFlowResult()
    }

    fun updateFlow(
        flowId: UUID,
        name: String?,
        criteriaNode: JsonNode?,
        requiresStaging: Boolean?,
        userId: UUID,
    ): UpdateFlowResult {
        val criteriaJson: String? = if (criteriaNode != null) {
            when (val r = validateAndSerializeCriteria(criteriaNode, userId)) {
                is CriteriaResult.Error -> return UpdateFlowResult.Invalid(r.message)
                is CriteriaResult.Ok -> r.json
            }
        } else null
        return when (database.updateFlow(flowId, name, criteriaJson, requiresStaging, userId)) {
            is FlowRepository.FlowUpdateResult.Success -> {
                val flow = database.getFlowById(flowId, userId) ?: return UpdateFlowResult.NotFound
                UpdateFlowResult.Updated(flow)
            }
            FlowRepository.FlowUpdateResult.NotFound -> UpdateFlowResult.NotFound
        }
    }

    fun deleteFlow(id: UUID, userId: UUID): Boolean = database.deleteFlow(id, userId)

    // ---- Staging operations ------------------------------------------------

    fun getStagingItems(flowId: UUID, userId: UUID): List<UploadRecord> =
        database.getStagingItems(flowId, userId)

    fun getStagingItemsForPlot(plotId: UUID, userId: UUID): List<UploadRecord> =
        database.getStagingItemsForPlot(plotId, userId)

    sealed class ApproveStagingResult {
        object Success : ApproveStagingResult()
        object DuplicateContent : ApproveStagingResult()
        object AlreadyApproved : ApproveStagingResult()
        object NotFound : ApproveStagingResult()
        object PlotNotOwned : ApproveStagingResult()
        object PlotClosed : ApproveStagingResult()
        data class Invalid(val message: String) : ApproveStagingResult()
    }

    fun approveStagingItem(
        plotId: UUID,
        uploadId: UUID,
        sourceFlowId: UUID?,
        userId: UUID,
        wrappedItemDekB64: String?,
        itemDekFormatRaw: String?,
        wrappedThumbnailDekB64: String?,
        thumbnailDekFormatRaw: String?,
    ): ApproveStagingResult {
        var dekBytes: ByteArray? = null
        var dekFormat: String? = null
        var thumbBytes: ByteArray? = null
        var thumbFormat: String? = null

        val plot = database.getPlotById(plotId) ?: return ApproveStagingResult.NotFound

        if (plot.visibility == "shared" && wrappedItemDekB64 != null) {
            dekFormat = itemDekFormatRaw
                ?: return ApproveStagingResult.Invalid("itemDekFormat required when wrappedItemDek is provided")
            dekBytes = try { Base64.getDecoder().decode(wrappedItemDekB64) }
                catch (_: Exception) { return ApproveStagingResult.Invalid("wrappedItemDek is not valid base64") }
            thumbFormat = thumbnailDekFormatRaw
            thumbBytes = wrappedThumbnailDekB64?.let {
                try { Base64.getDecoder().decode(it) } catch (_: Exception) { null }
            }
        }

        return when (database.approveStagingItem(plotId, uploadId, sourceFlowId, userId, dekBytes, dekFormat, thumbBytes, thumbFormat)) {
            PlotItemRepository.ApproveResult.Success          -> ApproveStagingResult.Success
            PlotItemRepository.ApproveResult.DuplicateContent -> ApproveStagingResult.DuplicateContent
            PlotItemRepository.ApproveResult.AlreadyApproved  -> ApproveStagingResult.AlreadyApproved
            PlotItemRepository.ApproveResult.NotFound         -> ApproveStagingResult.NotFound
            PlotItemRepository.ApproveResult.PlotNotOwned     -> ApproveStagingResult.PlotNotOwned
            PlotItemRepository.ApproveResult.PlotClosed       -> ApproveStagingResult.PlotClosed
        }
    }

    fun rejectStagingItem(plotId: UUID, uploadId: UUID, sourceFlowId: UUID?, userId: UUID): PlotItemRepository.RejectResult =
        database.rejectStagingItem(plotId, uploadId, sourceFlowId, userId)

    fun deleteDecision(plotId: UUID, uploadId: UUID, userId: UUID): Boolean =
        database.deleteDecision(plotId, uploadId, userId)

    fun getRejectedItems(plotId: UUID, userId: UUID): List<UploadRecord> =
        database.getRejectedItems(plotId, userId)

    // ---- Collection plot items ---------------------------------------------

    fun getPlotItems(plotId: UUID, userId: UUID): List<PlotItemWithUpload> =
        database.getPlotItems(plotId, userId)

    sealed class AddPlotItemResult {
        object Success : AddPlotItemResult()
        object AlreadyPresent : AddPlotItemResult()
        object PlotNotOwned : AddPlotItemResult()
        object UploadNotOwned : AddPlotItemResult()
        object PlotClosed : AddPlotItemResult()
        data class Invalid(val message: String) : AddPlotItemResult()
    }

    fun addPlotItem(
        plotId: UUID,
        uploadId: UUID,
        userId: UUID,
        wrappedItemDekB64: String?,
        itemDekFormatRaw: String?,
        wrappedThumbnailDekB64: String?,
        thumbnailDekFormatRaw: String?,
    ): AddPlotItemResult {
        val plot = database.getPlotById(plotId)
        if (plot?.visibility == "shared") {
            if (wrappedItemDekB64.isNullOrBlank())
                return AddPlotItemResult.Invalid("wrappedItemDek required for shared plots")
            if (itemDekFormatRaw.isNullOrBlank())
                return AddPlotItemResult.Invalid("itemDekFormat required for shared plots")
            val dekBytes = try { Base64.getDecoder().decode(wrappedItemDekB64) }
                catch (_: Exception) { return AddPlotItemResult.Invalid("wrappedItemDek is not valid base64") }
            val thumbBytes = wrappedThumbnailDekB64?.let {
                try { Base64.getDecoder().decode(it) } catch (_: Exception) { null }
            }
            return when (database.addPlotItem(plotId, uploadId, userId, dekBytes, itemDekFormatRaw, thumbBytes, thumbnailDekFormatRaw)) {
                PlotItemRepository.AddItemResult.Success        -> AddPlotItemResult.Success
                PlotItemRepository.AddItemResult.AlreadyPresent -> AddPlotItemResult.AlreadyPresent
                PlotItemRepository.AddItemResult.PlotNotOwned   -> AddPlotItemResult.PlotNotOwned
                PlotItemRepository.AddItemResult.UploadNotOwned -> AddPlotItemResult.UploadNotOwned
                PlotItemRepository.AddItemResult.PlotClosed     -> AddPlotItemResult.PlotClosed
                is PlotItemRepository.AddItemResult.Error       -> AddPlotItemResult.Invalid("Cannot add item")
            }
        }
        return when (database.addPlotItem(plotId, uploadId, userId)) {
            PlotItemRepository.AddItemResult.Success        -> AddPlotItemResult.Success
            PlotItemRepository.AddItemResult.AlreadyPresent -> AddPlotItemResult.AlreadyPresent
            PlotItemRepository.AddItemResult.PlotNotOwned   -> AddPlotItemResult.PlotNotOwned
            PlotItemRepository.AddItemResult.UploadNotOwned -> AddPlotItemResult.UploadNotOwned
            PlotItemRepository.AddItemResult.PlotClosed     -> AddPlotItemResult.PlotClosed
            is PlotItemRepository.AddItemResult.Error       -> AddPlotItemResult.Invalid("Cannot add item")
        }
    }

    fun removePlotItem(plotId: UUID, uploadId: UUID, userId: UUID): PlotItemRepository.RemoveItemResult =
        database.removePlotItem(plotId, uploadId, userId)
}
