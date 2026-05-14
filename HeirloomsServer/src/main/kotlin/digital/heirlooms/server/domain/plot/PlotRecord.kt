package digital.heirlooms.server.domain.plot

import java.time.Instant
import java.util.UUID

data class PlotRecord(
    val id: UUID,
    val ownerUserId: UUID?,
    val name: String,
    val sortOrder: Int,
    val isSystemDefined: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val criteria: String?,
    val showInGarden: Boolean,
    val visibility: String,
    val plotStatus: String = "open",
    val tombstonedAt: Instant? = null,
    val tombstonedBy: UUID? = null,
    val createdBy: UUID? = null,
    val localName: String? = null,
)
