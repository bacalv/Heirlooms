package digital.heirlooms.server.representation.plot

import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotItemWithUpload
import digital.heirlooms.server.representation.upload.toJson
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

private val flowRepMapper = ObjectMapper()

fun FlowRecord.toJson(): String {
    val factory = JsonNodeFactory.instance
    val node = factory.objectNode()
    node.put("id", id.toString())
    node.put("name", name)
    node.set<JsonNode>("criteria", flowRepMapper.readTree(criteria))
    node.put("targetPlotId", targetPlotId.toString())
    node.put("requiresStaging", requiresStaging)
    node.put("created_at", createdAt.toString())
    node.put("updated_at", updatedAt.toString())
    return node.toString()
}

fun PlotItemWithUpload.toJson(): String {
    val node = flowRepMapper.readTree(upload.toJson()).deepCopy<ObjectNode>()
    node.put("added_by", addedBy.toString())
    if (wrappedItemDek != null) node.put("wrapped_item_dek", java.util.Base64.getEncoder().encodeToString(wrappedItemDek))
    if (itemDekFormat != null) node.put("item_dek_format", itemDekFormat)
    if (wrappedThumbnailDek != null) node.put("wrapped_thumbnail_dek", java.util.Base64.getEncoder().encodeToString(wrappedThumbnailDek))
    if (thumbnailDekFormat != null) node.put("thumbnail_dek_format", thumbnailDekFormat)
    return node.toString()
}
