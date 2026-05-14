package digital.heirlooms.server.representation.plot

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.representation.responseMapper
import digital.heirlooms.server.representation.upload.toResponse
import java.time.Instant
import java.util.Base64

private val enc = Base64.getEncoder()

private data class FlowRecordResponse(
    val id: String,
    val name: String,
    val criteria: JsonNode,
    val targetPlotId: String,
    val requiresStaging: Boolean,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
)

// Flat DTO that mirrors all UploadRecord fields plus the PlotItemWithUpload overlay fields.
// Fields from UploadRecord are taken via toResponse() and copied here to preserve the
// same JSON shape as the original hand-rolled implementation.
private data class PlotItemWithUploadResponse(
    // --- upload fields (mirrors UploadRecordResponse) ---
    val id: String,
    val storageKey: String,
    val storageClass: String,
    val mimeType: String,
    val fileSize: Long,
    val uploadedAt: Instant,
    val rotation: Int,
    val thumbnailKey: String?,
    val tags: List<String>,
    val takenAt: Instant?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val deviceMake: String?,
    val deviceModel: String?,
    val compostedAt: Instant?,
    val envelopeVersion: Int?,
    val wrappedDek: String?,
    val dekFormat: String?,
    val encryptedMetadata: String?,
    val encryptedMetadataFormat: String?,
    val thumbnailStorageKey: String?,
    val wrappedThumbnailDek: String?,
    val thumbnailDekFormat: String?,
    val previewStorageKey: String?,
    val wrappedPreviewDek: String?,
    val previewDekFormat: String?,
    val plainChunkSize: Int?,
    val durationSeconds: Int?,
    val sharedFromUserId: String?,
    // --- plot-item overlay fields ---
    @JsonProperty("added_by") val addedBy: String,
    @JsonProperty("wrapped_item_dek") val wrappedItemDek: String?,
    @JsonProperty("item_dek_format") val itemDekFormat: String?,
    @JsonProperty("wrapped_thumbnail_dek") val wrappedPlotThumbnailDek: String?,
    @JsonProperty("thumbnail_dek_format") val plotThumbnailDekFormat: String?,
)

fun FlowRecord.toJson(): String =
    responseMapper.writeValueAsString(
        FlowRecordResponse(
            id = id.toString(),
            name = name,
            criteria = responseMapper.readTree(criteria),
            targetPlotId = targetPlotId.toString(),
            requiresStaging = requiresStaging,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    )

fun PlotItemWithUpload.toJson(): String {
    val u = upload.toResponse()
    return responseMapper.writeValueAsString(
        PlotItemWithUploadResponse(
            id = u.id,
            storageKey = u.storageKey,
            storageClass = u.storageClass,
            mimeType = u.mimeType,
            fileSize = u.fileSize,
            uploadedAt = u.uploadedAt,
            rotation = u.rotation,
            thumbnailKey = u.thumbnailKey,
            tags = u.tags,
            takenAt = u.takenAt,
            latitude = u.latitude,
            longitude = u.longitude,
            altitude = u.altitude,
            deviceMake = u.deviceMake,
            deviceModel = u.deviceModel,
            compostedAt = u.compostedAt,
            envelopeVersion = u.envelopeVersion,
            wrappedDek = u.wrappedDek,
            dekFormat = u.dekFormat,
            encryptedMetadata = u.encryptedMetadata,
            encryptedMetadataFormat = u.encryptedMetadataFormat,
            thumbnailStorageKey = u.thumbnailStorageKey,
            wrappedThumbnailDek = u.wrappedThumbnailDek,
            thumbnailDekFormat = u.thumbnailDekFormat,
            previewStorageKey = u.previewStorageKey,
            wrappedPreviewDek = u.wrappedPreviewDek,
            previewDekFormat = u.previewDekFormat,
            plainChunkSize = u.plainChunkSize,
            durationSeconds = u.durationSeconds,
            sharedFromUserId = u.sharedFromUserId,
            addedBy = addedBy.toString(),
            wrappedItemDek = wrappedItemDek?.let { enc.encodeToString(it) },
            itemDekFormat = itemDekFormat,
            wrappedPlotThumbnailDek = wrappedThumbnailDek?.let { enc.encodeToString(it) },
            plotThumbnailDekFormat = thumbnailDekFormat,
        )
    )
}
