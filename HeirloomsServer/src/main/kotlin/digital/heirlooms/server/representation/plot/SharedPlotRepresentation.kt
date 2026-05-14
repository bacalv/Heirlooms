package digital.heirlooms.server.representation.plot

import digital.heirlooms.server.domain.plot.PlotMemberRecord
import digital.heirlooms.server.domain.plot.SharedMembershipRecord
import digital.heirlooms.server.domain.plot.PlotInviteRecord
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.util.UUID

fun PlotMemberRecord.toJson(): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("userId", userId.toString())
    node.put("displayName", displayName)
    node.put("username", username)
    node.put("role", role)
    node.put("status", status)
    if (localName != null) node.put("localName", localName) else node.putNull("localName")
    node.put("joinedAt", joinedAt.toString())
    return node.toString()
}

fun SharedMembershipRecord.toJson(): String {
    val factory = JsonNodeFactory.instance
    val node = factory.objectNode()
    node.put("plotId", plotId.toString())
    node.put("plotName", plotName)
    if (ownerUserId != null) node.put("ownerUserId", ownerUserId.toString())
    else node.putNull("ownerUserId")
    if (ownerDisplayName != null) node.put("ownerDisplayName", ownerDisplayName)
    else node.putNull("ownerDisplayName")
    node.put("role", role)
    node.put("status", status)
    if (localName != null) node.put("localName", localName) else node.putNull("localName")
    node.put("joinedAt", joinedAt.toString())
    if (leftAt != null) node.put("leftAt", leftAt.toString()) else node.putNull("leftAt")
    node.put("plotStatus", plotStatus)
    if (tombstonedAt != null) node.put("tombstonedAt", tombstonedAt.toString()) else node.putNull("tombstonedAt")
    if (tombstonedBy != null) node.put("tombstonedBy", tombstonedBy.toString()) else node.putNull("tombstonedBy")
    return node.toString()
}

fun plotKeyResponseJson(wrappedKey: ByteArray, format: String): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("wrappedPlotKey", java.util.Base64.getEncoder().encodeToString(wrappedKey))
    node.put("plotKeyFormat", format)
    return node.toString()
}

fun inviteResponseJson(token: String, expiresAt: java.time.Instant): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("token", token)
    node.put("expiresAt", expiresAt.toString())
    return node.toString()
}

fun joinInfoResponseJson(plotId: UUID, plotName: String, inviterDisplayName: String, inviterUserId: UUID): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("plotId", plotId.toString())
    node.put("plotName", plotName)
    node.put("inviterDisplayName", inviterDisplayName)
    node.put("inviterUserId", inviterUserId.toString())
    return node.toString()
}

fun pendingJoinResponseJson(status: String, inviteId: UUID, inviterDisplayName: String): String {
    val node = JsonNodeFactory.instance.objectNode()
    node.put("status", status)
    node.put("inviteId", inviteId.toString())
    node.put("inviterDisplayName", inviterDisplayName)
    return node.toString()
}
