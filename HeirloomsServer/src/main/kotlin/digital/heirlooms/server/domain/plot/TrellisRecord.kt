package digital.heirlooms.server.domain.plot

import java.time.Instant
import java.util.UUID

data class TrellisRecord(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val criteria: String,
    val targetPlotId: UUID,
    val requiresStaging: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
