package digital.heirlooms.server.repository.plot

import digital.heirlooms.server.CriteriaEvaluator
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.plot.FlowRecord
import digital.heirlooms.server.domain.plot.PlotRecord
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface FlowRepository {
    sealed class FlowCreateResult {
        data class Success(val flow: FlowRecord) : FlowCreateResult()
        data class Error(val message: String) : FlowCreateResult()
    }
    sealed class FlowUpdateResult {
        data class Success(val flow: FlowRecord) : FlowUpdateResult()
        object NotFound : FlowUpdateResult()
    }

    fun listFlows(userId: UUID = FOUNDING_USER_ID): List<FlowRecord>
    fun getFlowById(id: UUID, userId: UUID = FOUNDING_USER_ID): FlowRecord?
    fun createFlow(name: String, criteriaJson: String, targetPlotId: UUID, requiresStaging: Boolean, targetPlot: PlotRecord, userId: UUID = FOUNDING_USER_ID): FlowCreateResult
    fun updateFlow(id: UUID, name: String?, criteriaJson: String?, requiresStaging: Boolean?, targetPlot: PlotRecord?, userId: UUID = FOUNDING_USER_ID): FlowUpdateResult
    fun deleteFlow(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean
    fun runUnstagedFlowsForUpload(conn: java.sql.Connection, uploadId: UUID, userId: UUID)
}

class PostgresFlowRepository(private val dataSource: DataSource) : FlowRepository {

    override fun listFlows(userId: UUID): List<FlowRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at
                   FROM flows WHERE user_id = ? ORDER BY created_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<FlowRecord>()
                while (rs.next()) results.add(rs.toFlowRecord())
                return results
            }
        }
    }

    override fun getFlowById(id: UUID, userId: UUID): FlowRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at
                   FROM flows WHERE id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toFlowRecord()
            }
        }
    }

    override fun createFlow(
        name: String,
        criteriaJson: String,
        targetPlotId: UUID,
        requiresStaging: Boolean,
        targetPlot: PlotRecord,
        userId: UUID,
    ): FlowRepository.FlowCreateResult {
        if (targetPlot.criteria != null) return FlowRepository.FlowCreateResult.Error("Target plot must be a collection plot (criteria IS NULL)")

        // Staging policy: private plots never need staging (your own content);
        // public plots always require staging; shared plots respect the caller's preference.
        val effectiveStaging = when (targetPlot.visibility) {
            "private" -> false
            "public"  -> true
            else      -> requiresStaging
        }

        val id = UUID.randomUUID()
        val now = Instant.now()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO flows (id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at)
                   VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.setString(3, name.trim())
                stmt.setString(4, criteriaJson)
                stmt.setObject(5, targetPlotId)
                stmt.setBoolean(6, effectiveStaging)
                stmt.setTimestamp(7, Timestamp.from(now))
                stmt.setTimestamp(8, Timestamp.from(now))
                stmt.executeUpdate()
            }
            val flow = getFlowById(id, userId)!!
            if (!effectiveStaging) autoPopulateFlow(conn, flow, userId)
            return FlowRepository.FlowCreateResult.Success(flow)
        }
    }

    override fun updateFlow(
        id: UUID,
        name: String?,
        criteriaJson: String?,
        requiresStaging: Boolean?,
        targetPlot: PlotRecord?,
        userId: UUID,
    ): FlowRepository.FlowUpdateResult {
        // Enforce same staging policy as createFlow
        val effectiveStaging = requiresStaging?.let {
            when (targetPlot?.visibility) {
                "private" -> false
                "public"  -> true
                else      -> it
            }
        }

        val setClauses = mutableListOf("updated_at = ?")
        if (name != null) setClauses.add("name = ?")
        if (criteriaJson != null) setClauses.add("criteria = ?::jsonb")
        if (effectiveStaging != null) setClauses.add("requires_staging = ?")

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE flows SET ${setClauses.joinToString(", ")} WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (name != null) stmt.setString(idx++, name.trim())
                if (criteriaJson != null) stmt.setString(idx++, criteriaJson)
                if (effectiveStaging != null) stmt.setBoolean(idx++, effectiveStaging)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, userId)
                val updated = stmt.executeUpdate()
                if (updated == 0) return FlowRepository.FlowUpdateResult.NotFound
            }
            val updatedFlow = getFlowById(id, userId) ?: return FlowRepository.FlowUpdateResult.NotFound
            // If flow is now unstaged (newly or due to criteria change), populate missing items
            if (!updatedFlow.requiresStaging) autoPopulateFlow(conn, updatedFlow, userId)
            return FlowRepository.FlowUpdateResult.Success(updatedFlow)
        }
    }

    override fun deleteFlow(id: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM flows WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // Inserts upload into plot_items for every unstaged flow whose criteria it satisfies.
    override fun runUnstagedFlowsForUpload(conn: Connection, uploadId: UUID, userId: UUID) {
        val flows = listFlows(userId).filter { !it.requiresStaging }
        for (flow in flows) {
            try {
                val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
                conn.prepareStatement(
                    """INSERT INTO plot_items (upload_id, plot_id, source_flow_id, added_by)
                       SELECT id, ?, ?, ?
                       FROM uploads
                       WHERE id = ? AND user_id = ? AND (${fragment.sql})
                       ON CONFLICT (plot_id, upload_id) DO NOTHING"""
                ).use { stmt ->
                    var idx = 1
                    stmt.setObject(idx++, flow.targetPlotId)
                    stmt.setObject(idx++, flow.id)
                    stmt.setObject(idx++, userId)
                    stmt.setObject(idx++, uploadId)
                    stmt.setObject(idx++, userId)
                    for (setter in fragment.setters) idx = setter(stmt, idx)
                    stmt.executeUpdate()
                }
            } catch (_: Exception) { /* best-effort; bad criteria skipped */ }
        }
    }

    // Bulk-inserts all existing matching uploads into plot_items for a new unstaged flow.
    private fun autoPopulateFlow(conn: Connection, flow: FlowRecord, userId: UUID) {
        try {
            val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
            conn.prepareStatement(
                """INSERT INTO plot_items (upload_id, plot_id, source_flow_id, added_by)
                   SELECT id, ?, ?, ?
                   FROM uploads
                   WHERE user_id = ? AND composted_at IS NULL AND (${fragment.sql})
                   ON CONFLICT (plot_id, upload_id) DO NOTHING"""
            ).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, flow.targetPlotId)
                stmt.setObject(idx++, flow.id)
                stmt.setObject(idx++, userId)
                stmt.setObject(idx++, userId)
                for (setter in fragment.setters) idx = setter(stmt, idx)
                stmt.executeUpdate()
            }
        } catch (_: Exception) { /* bad criteria — skip */ }
    }

    private fun ResultSet.toFlowRecord() = FlowRecord(
        id             = getObject("id", UUID::class.java),
        userId         = getObject("user_id", UUID::class.java),
        name           = getString("name"),
        criteria       = getString("criteria"),
        targetPlotId   = getObject("target_plot_id", UUID::class.java),
        requiresStaging = getBoolean("requires_staging"),
        createdAt      = getTimestamp("created_at").toInstant(),
        updatedAt      = getTimestamp("updated_at").toInstant(),
    )
}
