package digital.heirlooms.server.service.plot

import digital.heirlooms.server.domain.plot.TrellisRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.repository.plot.TrellisRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import com.fasterxml.jackson.databind.JsonNode
import java.util.Base64
import java.util.UUID

/**
 * Encapsulates business logic for trellis CRUD and staging operations:
 * criteria validation on trellis create/update, DEK decoding for staging
 * approval, and plot-item management.
 */
class TrellisService(
    private val trellisRepo: TrellisRepository,
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

    // ---- Trellis CRUD -------------------------------------------------------

    fun listTrellises(userId: UUID): List<TrellisRecord> = trellisRepo.listTrellises(userId)

    fun getTrellisById(id: UUID, userId: UUID): TrellisRecord? = trellisRepo.getTrellisById(id, userId)

    sealed class CreateTrellisResult {
        data class Created(val trellis: TrellisRecord) : CreateTrellisResult()
        data class Invalid(val message: String) : CreateTrellisResult()
    }

    fun createTrellis(
        name: String,
        criteriaNode: JsonNode,
        targetPlotId: UUID,
        requiresStaging: Boolean,
        userId: UUID,
    ): CreateTrellisResult {
        val criteriaJson = when (val r = validateAndSerializeCriteria(criteriaNode, userId)) {
            is CriteriaResult.Error -> return CreateTrellisResult.Invalid(r.message)
            is CriteriaResult.Ok -> r.json
        }
        val targetPlot = plotRepo.getPlotById(targetPlotId)
            ?: return CreateTrellisResult.Invalid("Target plot not found")
        if (targetPlot.ownerUserId != userId)
            return CreateTrellisResult.Invalid("Target plot not found")
        return when (val result = trellisRepo.createTrellis(name, criteriaJson, targetPlotId, requiresStaging, targetPlot, userId)) {
            is TrellisRepository.TrellisCreateResult.Success -> CreateTrellisResult.Created(result.trellis)
            is TrellisRepository.TrellisCreateResult.Error -> CreateTrellisResult.Invalid(result.message)
        }
    }

    sealed class UpdateTrellisResult {
        data class Updated(val trellis: TrellisRecord) : UpdateTrellisResult()
        object NotFound : UpdateTrellisResult()
        data class Invalid(val message: String) : UpdateTrellisResult()
    }

    fun updateTrellis(
        trellisId: UUID,
        name: String?,
        criteriaNode: JsonNode?,
        requiresStaging: Boolean?,
        userId: UUID,
    ): UpdateTrellisResult {
        val criteriaJson: String? = if (criteriaNode != null) {
            when (val r = validateAndSerializeCriteria(criteriaNode, userId)) {
                is CriteriaResult.Error -> return UpdateTrellisResult.Invalid(r.message)
                is CriteriaResult.Ok -> r.json
            }
        } else null
        val existingTrellis = trellisRepo.getTrellisById(trellisId, userId) ?: return UpdateTrellisResult.NotFound
        val targetPlot = plotRepo.getPlotById(existingTrellis.targetPlotId)
        return when (trellisRepo.updateTrellis(trellisId, name, criteriaJson, requiresStaging, targetPlot, userId)) {
            is TrellisRepository.TrellisUpdateResult.Success -> {
                val trellis = trellisRepo.getTrellisById(trellisId, userId) ?: return UpdateTrellisResult.NotFound
                UpdateTrellisResult.Updated(trellis)
            }
            TrellisRepository.TrellisUpdateResult.NotFound -> UpdateTrellisResult.NotFound
        }
    }

    fun deleteTrellis(id: UUID, userId: UUID): Boolean = trellisRepo.deleteTrellis(id, userId)

    // ---- Staging operations ------------------------------------------------

    fun getStagingItems(trellisId: UUID, userId: UUID): List<UploadRecord> {
        val trellis = trellisRepo.getTrellisById(trellisId, userId) ?: return emptyList()
        return itemRepo.getStagingItems(trellisId, trellis, userId)
    }

    fun getStagingItemsForPlot(plotId: UUID, userId: UUID): List<UploadRecord> {
        val stagingTrellises = trellisRepo.listTrellises(userId).filter { it.targetPlotId == plotId && it.requiresStaging }
        return itemRepo.getStagingItemsForPlot(plotId, stagingTrellises, userId)
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
        sourceTrellisId: UUID?,
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

        return when (itemRepo.approveStagingItem(plot, uploadId, sourceTrellisId, userId, dekBytes, dekFormat, thumbBytes, thumbFormat)) {
            PlotItemRepository.ApproveResult.Success          -> ApproveStagingResult.Success
            PlotItemRepository.ApproveResult.DuplicateContent -> ApproveStagingResult.DuplicateContent
            PlotItemRepository.ApproveResult.AlreadyApproved  -> ApproveStagingResult.AlreadyApproved
            PlotItemRepository.ApproveResult.NotFound         -> ApproveStagingResult.NotFound
            PlotItemRepository.ApproveResult.PlotNotOwned     -> ApproveStagingResult.PlotNotOwned
            PlotItemRepository.ApproveResult.PlotClosed       -> ApproveStagingResult.PlotClosed
        }
    }

    fun rejectStagingItem(plotId: UUID, uploadId: UUID, sourceTrellisId: UUID?, userId: UUID): PlotItemRepository.RejectResult {
        val plot = plotRepo.getPlotById(plotId) ?: return PlotItemRepository.RejectResult.NotFound
        return itemRepo.rejectStagingItem(plot, uploadId, sourceTrellisId, userId)
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
