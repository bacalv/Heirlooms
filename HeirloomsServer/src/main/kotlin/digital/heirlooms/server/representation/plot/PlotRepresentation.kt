package digital.heirlooms.server.representation.plot

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import digital.heirlooms.server.domain.plot.PlotRecord
import digital.heirlooms.server.representation.responseMapper
import java.time.Instant
import java.util.UUID

private data class PlotRecordResponse(
    val id: String,
    @JsonProperty("owner_user_id") val ownerUserId: String?,
    @JsonProperty("is_owner") val isOwner: Boolean?,
    val name: String,
    @JsonProperty("sort_order") val sortOrder: Int,
    @JsonProperty("is_system_defined") val isSystemDefined: Boolean,
    @JsonProperty("show_in_garden") val showInGarden: Boolean,
    val visibility: String,
    @JsonProperty("plot_status") val plotStatus: String,
    @JsonProperty("local_name") val localName: String?,
    @JsonProperty("tombstoned_at") val tombstonedAt: Instant?,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
    @JsonInclude(JsonInclude.Include.ALWAYS) val criteria: JsonNode?,
)

fun PlotRecord.toJson(authUserId: UUID? = null): String =
    responseMapper.writeValueAsString(
        PlotRecordResponse(
            id = id.toString(),
            ownerUserId = ownerUserId?.toString(),
            isOwner = if (authUserId != null) ownerUserId == authUserId else null,
            name = name,
            sortOrder = sortOrder,
            isSystemDefined = isSystemDefined,
            showInGarden = showInGarden,
            visibility = visibility,
            plotStatus = plotStatus,
            localName = localName,
            tombstonedAt = tombstonedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            criteria = criteria?.let { responseMapper.readTree(it) },
        )
    )
