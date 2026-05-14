package digital.heirlooms.server.representation.upload

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import digital.heirlooms.server.domain.upload.UploadPage
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.Base64

private val enc = Base64.getEncoder()

internal data class UploadRecordResponse(
    val id: String,
    val storageKey: String,
    val storageClass: String,
    val mimeType: String,
    val fileSize: Long,
    val uploadedAt: Instant,
    val rotation: Int,
    @JsonInclude(JsonInclude.Include.ALWAYS) val thumbnailKey: String?,
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
)

private data class UploadPageResponse(
    val items: List<UploadRecordResponse>,
    @JsonProperty("next_cursor") @JsonInclude(JsonInclude.Include.ALWAYS) val nextCursor: String?,
)

internal fun UploadRecord.toResponse(): UploadRecordResponse {
    val encrypted = storageClass == "encrypted"
    return UploadRecordResponse(
        id = id.toString(),
        storageKey = storageKey,
        storageClass = storageClass,
        mimeType = mimeType,
        fileSize = fileSize,
        uploadedAt = uploadedAt,
        rotation = rotation,
        thumbnailKey = thumbnailKey,
        tags = tags,
        takenAt = takenAt,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        deviceMake = deviceMake,
        deviceModel = deviceModel,
        compostedAt = compostedAt,
        envelopeVersion = if (encrypted) envelopeVersion else null,
        wrappedDek = if (encrypted) wrappedDek?.let { enc.encodeToString(it) } else null,
        dekFormat = if (encrypted) dekFormat else null,
        encryptedMetadata = if (encrypted) encryptedMetadata?.let { enc.encodeToString(it) } else null,
        encryptedMetadataFormat = if (encrypted) encryptedMetadataFormat else null,
        thumbnailStorageKey = if (encrypted) thumbnailStorageKey else null,
        wrappedThumbnailDek = if (encrypted) wrappedThumbnailDek?.let { enc.encodeToString(it) } else null,
        thumbnailDekFormat = if (encrypted) thumbnailDekFormat else null,
        previewStorageKey = if (encrypted) previewStorageKey else null,
        wrappedPreviewDek = if (encrypted) wrappedPreviewDek?.let { enc.encodeToString(it) } else null,
        previewDekFormat = if (encrypted) previewDekFormat else null,
        plainChunkSize = if (encrypted) plainChunkSize else null,
        durationSeconds = durationSeconds,
        sharedFromUserId = sharedFromUserId?.toString(),
    )
}

fun UploadRecord.toJson(): String = responseMapper.writeValueAsString(toResponse())

fun UploadPage.toJson(): String =
    responseMapper.writeValueAsString(
        UploadPageResponse(
            items = items.map { it.toResponse() },
            nextCursor = nextCursor,
        )
    )
