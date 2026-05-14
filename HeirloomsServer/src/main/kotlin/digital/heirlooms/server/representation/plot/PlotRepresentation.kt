package digital.heirlooms.server.representation.plot

import digital.heirlooms.server.domain.plot.PlotRecord
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.util.UUID

private val plotRepMapper = ObjectMapper()

fun PlotRecord.toJson(authUserId: UUID? = null): String {
    val factory = JsonNodeFactory.instance
    val node = factory.objectNode()
    node.put("id", id.toString())
    if (ownerUserId != null) node.put("owner_user_id", ownerUserId.toString())
    else node.putNull("owner_user_id")
    if (authUserId != null) node.put("is_owner", ownerUserId == authUserId)
    node.put("name", name)
    node.put("sort_order", sortOrder)
    node.put("is_system_defined", isSystemDefined)
    node.put("show_in_garden", showInGarden)
    node.put("visibility", visibility)
    node.put("plot_status", plotStatus)
    if (localName != null) node.put("local_name", localName) else node.putNull("local_name")
    if (tombstonedAt != null) node.put("tombstoned_at", tombstonedAt.toString()) else node.putNull("tombstoned_at")
    node.put("created_at", createdAt.toString())
    node.put("updated_at", updatedAt.toString())
    if (criteria != null) {
        node.set<JsonNode>("criteria", plotRepMapper.readTree(criteria))
    } else {
        node.putNull("criteria")
    }
    return node.toString()
}
