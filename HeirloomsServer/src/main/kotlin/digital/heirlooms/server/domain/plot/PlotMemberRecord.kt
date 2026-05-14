package digital.heirlooms.server.domain.plot

import java.time.Instant
import java.util.UUID

data class PlotMemberRecord(
    val plotId: UUID,
    val userId: UUID,
    val displayName: String,
    val username: String,
    val role: String,
    val wrappedPlotKey: ByteArray?,
    val plotKeyFormat: String?,
    val joinedAt: Instant,
    val status: String = "joined",
    val localName: String? = null,
    val leftAt: Instant? = null,
)
