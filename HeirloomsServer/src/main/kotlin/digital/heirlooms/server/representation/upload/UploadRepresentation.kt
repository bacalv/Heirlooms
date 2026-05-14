package digital.heirlooms.server.representation.upload

import digital.heirlooms.server.domain.upload.UploadPage
import digital.heirlooms.server.domain.upload.UploadRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory

private val uploadMapper = ObjectMapper()

fun UploadRecord.toJson(): String {
    val enc = java.util.Base64.getEncoder()
    val node = JsonNodeFactory.instance.objectNode()
    node.put("id", id.toString())
    node.put("storageKey", storageKey)
    node.put("storageClass", storageClass)
    node.put("mimeType", mimeType)
    node.put("fileSize", fileSize)
    node.put("uploadedAt", uploadedAt.toString())
    node.put("rotation", rotation)
    if (thumbnailKey != null) node.put("thumbnailKey", thumbnailKey) else node.putNull("thumbnailKey")
    val tagsNode = node.putArray("tags")
    tags.forEach { tagsNode.add(it) }
    if (takenAt != null) node.put("takenAt", takenAt.toString()) else node.putNull("takenAt")
    if (latitude != null) node.put("latitude", latitude) else node.putNull("latitude")
    if (longitude != null) node.put("longitude", longitude) else node.putNull("longitude")
    if (altitude != null) node.put("altitude", altitude) else node.putNull("altitude")
    if (deviceMake != null) node.put("deviceMake", deviceMake) else node.putNull("deviceMake")
    if (deviceModel != null) node.put("deviceModel", deviceModel) else node.putNull("deviceModel")
    if (compostedAt != null) node.put("compostedAt", compostedAt.toString()) else node.putNull("compostedAt")
    if (storageClass == "encrypted") {
        if (envelopeVersion != null) node.put("envelopeVersion", envelopeVersion)
        if (wrappedDek != null) node.put("wrappedDek", enc.encodeToString(wrappedDek))
        if (dekFormat != null) node.put("dekFormat", dekFormat)
        if (encryptedMetadata != null) node.put("encryptedMetadata", enc.encodeToString(encryptedMetadata))
        if (encryptedMetadataFormat != null) node.put("encryptedMetadataFormat", encryptedMetadataFormat)
        if (thumbnailStorageKey != null) node.put("thumbnailStorageKey", thumbnailStorageKey)
        if (wrappedThumbnailDek != null) node.put("wrappedThumbnailDek", enc.encodeToString(wrappedThumbnailDek))
        if (thumbnailDekFormat != null) node.put("thumbnailDekFormat", thumbnailDekFormat)
        if (previewStorageKey != null) node.put("previewStorageKey", previewStorageKey)
        if (wrappedPreviewDek != null) node.put("wrappedPreviewDek", enc.encodeToString(wrappedPreviewDek))
        if (previewDekFormat != null) node.put("previewDekFormat", previewDekFormat)
        if (plainChunkSize != null) node.put("plainChunkSize", plainChunkSize)
    }
    if (durationSeconds != null) node.put("durationSeconds", durationSeconds)
    if (sharedFromUserId != null) node.put("sharedFromUserId", sharedFromUserId.toString())
    return node.toString()
}

fun UploadPage.toJson(): String {
    val node = uploadMapper.createObjectNode()
    val arr = node.putArray("items")
    items.forEach { arr.add(uploadMapper.readTree(it.toJson())) }
    if (nextCursor != null) node.put("next_cursor", nextCursor) else node.putNull("next_cursor")
    return node.toString()
}
