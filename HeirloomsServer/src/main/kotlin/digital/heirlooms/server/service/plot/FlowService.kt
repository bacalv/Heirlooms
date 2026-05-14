package digital.heirlooms.server.service.plot

import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.repository.plot.FlowRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import com.fasterxml.jackson.databind.JsonNode
import java.util.Base64
import java.util.UUID

/**
 * Encapsulates business logic for flow CRUD and staging operations:
 * criteria validation on flow create/update, DEK decoding for staging
 * approval, and plot-item management.
 */
class FlowService(
    private val flowRepo: FlowRepository,
    private val plotRepo: PlotRepository,
    private val itemRepo: PlotItemRepository,
    private val uploadRepo: UploadRepository,
) {
    // ---- Criteria validation (reused from PlotService) ----------------------

    sealed class CriteriaResult {
        data class Ok(val json: String) : CriteriaResult()
        data class Error(val message: String) : CriteriaResult()
    }

    fun validateAndSerializeCriteria(node: JsonNode, userId: UUID): CriteriaResult {
        return try {
            plotRepo.withCriteriaValidation(node, userId)
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

    fun listFlows(userId: UUID): List<FlowRecord> = flowRepo.listFlows(userId)

    fun getFlowById(id: UUID, userId: UUID): FlowRecord? = flowRepo.getFlowById(id, userId)

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
        val targetPlot = plotRepo.getPlotById(targetPlotId)
            ?: return CreateFlowResult.Invalid("Target plot not found")
        if (targetPlot.ownerUserId != userId)
            return CreateFlowResult.Invalid("Target plot not found")
        return when (val result = flowRepo.createFlow(name, criteriaJson, targetPlotId, requiresStaging, targetPlot, userId)) {
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
        val existingFlow = flowRepo.getFlowById(flowId, userId) ?: return UpdateFlowResult.NotFound
        val targetPlot = plotRepo.getPlotById(existingFlow.targetPlotId)
        return when (flowRepo.updateFlow(flowId, name, criteriaJson, requiresStaging, targetPlot, userId)) {
            is FlowRepository.FlowUpdateResult.Success -> {
                val flow = flowRepo.getFlowById(flowId, userId) ?: return UpdateFlowResult.NotFound
                UpdateFlowResult.Updated(flow)
            }
            FlowRepository.FlowUpdateResult.NotFound -> UpdateFlowResult.NotFound
        }
    }

    fun deleteFlow(id: UUID, userId: UUID): Boolean = flowRepo.deleteFlow(id, userId)

    // ---- Staging operations ------------------------------------------------

    fun getStagingItems(flowId: UUID, userId: UUID): List<UploadRecord> {
        val flow = flowRepo.getFlowById(flowId, userId) ?: return emptyList()
        return itemRepo.getStagingItems(flowId, flow, userId)
    }

    fun getStagingItemsForPlot(plotId: UUID, userId: UUID): List<UploadRecord> {
        val stagingFlows = flowRepo.listFlows(userId).filter { it.targetPlotId == plotId && it.requiresStaging }
        return itemRepo.getStagingItemsForPlot(plotId, stagingFlows, userId)
    }

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

        val plot = plotRepo.getPlotById(plotId) ?: return ApproveStagingResult.NotFound

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

        return when (itemRepo.approveStagingItem(plot, uploadId, sourceFlowId, userId, dekBytes, dekFormat, thumbBytes, thumbFormat)) {
            PlotItemRepository.ApproveResult.Success          -> ApproveStagingResult.Success
            PlotItemRepository.ApproveResult.DuplicateContent -> ApproveStagingResult.DuplicateContent
            PlotItemRepository.ApproveResult.AlreadyApproved  -> ApproveStagingResult.AlreadyApproved
            PlotItemRepository.ApproveResult.NotFound         -> ApproveStagingResult.NotFound
            PlotItemRepository.ApproveResult.PlotNotOwned     -> ApproveStagingResult.PlotNotOwned
            PlotItemRepository.ApproveResult.PlotClosed       -> ApproveStagingResult.PlotClosed
        }
    }

    fun rejectStagingItem(plotId: UUID, uploadId: UUID, sourceFlowId: UUID?, userId: UUID): PlotItemRepository.RejectResult {
        val plot = plotRepo.getPlotById(plotId) ?: return PlotItemRepository.RejectResult.NotFound
        return itemRepo.rejectStagingItem(plot, uploadId, sourceFlowId, userId)
    }

    fun deleteDecision(plotId: UUID, uploadId: UUID, userId: UUID): Boolean {
        val plot = plotRepo.getPlotById(plotId) ?: return false
        return itemRepo.deleteDecision(plot, uploadId, userId)
    }

    fun getRejectedItems(plotId: UUID, userId: UUID): List<UploadRecord> {
        val plot = plotRepo.getPlotById(plotId) ?: return emptyList()
        return itemRepo.getRejectedItems(plot, userId)
    }

    // ---- Collection plot items ---------------------------------------------

    fun getPlotItems(plotId: UUID, userId: UUID): List<PlotItemWithUpload> {
        val plot = plotRepo.getPlotById(plotId)?.let { p ->
            when {
                p.ownerUserId == userId -> p
                p.visibility == "shared" && plotRepo.isMember(plotId, userId) -> p
                else -> null
            }
        }
        return itemRepo.getPlotItems(plotId, userId, plot)
    }

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
        val plot = plotRepo.getPlotById(plotId)
        val uploadExists = uploadRepo.uploadExists(uploadId, userId)
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
            return when (itemRepo.addPlotItem(plot, uploadId, userId, uploadExists, dekBytes, itemDekFormatRaw, thumbBytes, thumbnailDekFormatRaw)) {
                PlotItemRepository.AddItemResult.Success        -> AddPlotItemResult.Success
                PlotItemRepository.AddItemResult.AlreadyPresent -> AddPlotItemResult.AlreadyPresent
                PlotItemRepository.AddItemResult.PlotNotOwned   -> AddPlotItemResult.PlotNotOwned
                PlotItemRepository.AddItemResult.UploadNotOwned -> AddPlotItemResult.UploadNotOwned
                PlotItemRepository.AddItemResult.PlotClosed     -> AddPlotItemResult.PlotClosed
                is PlotItemRepository.AddItemResult.Error       -> AddPlotItemResult.Invalid("Cannot add item")
            }
        }
        return when (itemRepo.addPlotItem(plot, uploadId, userId, uploadExists)) {
            PlotItemRepository.AddItemResult.Success        -> AddPlotItemResult.Success
            PlotItemRepository.AddItemResult.AlreadyPresent -> AddPlotItemResult.AlreadyPresent
            PlotItemRepository.AddItemResult.PlotNotOwned   -> AddPlotItemResult.PlotNotOwned
            PlotItemRepository.AddItemResult.UploadNotOwned -> AddPlotItemResult.UploadNotOwned
            PlotItemRepository.AddItemResult.PlotClosed     -> AddPlotItemResult.PlotClosed
            is PlotItemRepository.AddItemResult.Error       -> AddPlotItemResult.Invalid("Cannot add item")
        }
    }

    fun removePlotItem(plotId: UUID, uploadId: UUID, userId: UUID): PlotItemRepository.RemoveItemResult =
        itemRepo.removePlotItem(plotId, uploadId, userId)
}
