package digital.heirlooms.server.representation.capsule

import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.representation.upload.toJson
import com.fasterxml.jackson.databind.ObjectMapper

private val capsuleMapper = ObjectMapper()

fun CapsuleDetail.toDetailJson(): String {
    val r = record
    val node = capsuleMapper.createObjectNode()
    node.put("id", r.id.toString())
    node.put("shape", r.shape.name.lowercase())
    node.put("state", r.state.name.lowercase())
    node.put("created_at", r.createdAt.toString())
    node.put("updated_at", r.updatedAt.toString())
    node.put("unlock_at", r.unlockAt.toString())
    node.putArray("recipients").also { arr -> recipients.forEach { arr.add(it) } }
    node.putArray("uploads").also { arr -> uploads.forEach { arr.add(capsuleMapper.readTree(it.toJson())) } }
    node.put("message", message)
    if (r.cancelledAt != null) node.put("cancelled_at", r.cancelledAt.toString()) else node.putNull("cancelled_at")
    if (r.deliveredAt != null) node.put("delivered_at", r.deliveredAt.toString()) else node.putNull("delivered_at")
    return capsuleMapper.writeValueAsString(node)
}

fun CapsuleSummary.toSummaryJson(): String {
    val r = record
    val node = capsuleMapper.createObjectNode()
    node.put("id", r.id.toString())
    node.put("shape", r.shape.name.lowercase())
    node.put("state", r.state.name.lowercase())
    node.put("created_at", r.createdAt.toString())
    node.put("updated_at", r.updatedAt.toString())
    node.put("unlock_at", r.unlockAt.toString())
    node.putArray("recipients").also { arr -> recipients.forEach { arr.add(it) } }
    node.put("upload_count", uploadCount)
    node.put("has_message", hasMessage)
    if (r.cancelledAt != null) node.put("cancelled_at", r.cancelledAt.toString()) else node.putNull("cancelled_at")
    if (r.deliveredAt != null) node.put("delivered_at", r.deliveredAt.toString()) else node.putNull("delivered_at")
    return capsuleMapper.writeValueAsString(node)
}

fun CapsuleSummary.toReverseLookupJson(): String {
    val r = record
    val node = capsuleMapper.createObjectNode()
    node.put("id", r.id.toString())
    node.put("shape", r.shape.name.lowercase())
    node.put("state", r.state.name.lowercase())
    node.put("unlock_at", r.unlockAt.toString())
    node.putArray("recipients").also { arr -> recipients.forEach { arr.add(it) } }
    return capsuleMapper.writeValueAsString(node)
}
