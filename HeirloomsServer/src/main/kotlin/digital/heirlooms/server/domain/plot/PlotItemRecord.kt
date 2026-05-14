package digital.heirlooms.server.domain.plot

import java.time.Instant
import java.util.UUID

data class PlotItemRecord(
    val id: UUID,
    val plotId: UUID,
    val uploadId: UUID,
    val addedBy: UUID,
    val sourceFlowId: UUID?,
    val addedAt: Instant,
)
