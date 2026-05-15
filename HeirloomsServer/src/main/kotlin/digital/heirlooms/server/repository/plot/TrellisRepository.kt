package digital.heirlooms.server.repository.plot

import digital.heirlooms.server.service.plot.CriteriaEvaluator
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.plot.TrellisRecord
import digital.heirlooms.server.domain.plot.PlotRecord
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface TrellisRepository {
    sealed class TrellisCreateResult {
        data class Success(val trellis: TrellisRecord) : TrellisCreateResult()
        data class Error(val message: String) : TrellisCreateResult()
    }
    sealed class TrellisUpdateResult {
        data class Success(val trellis: TrellisRecord) : TrellisUpdateResult()
        object NotFound : TrellisUpdateResult()
    }

    fun listTrellises(userId: UUID = FOUNDING_USER_ID): List<TrellisRecord>
    fun getTrellisById(id: UUID, userId: UUID = FOUNDING_USER_ID): TrellisRecord?
    fun createTrellis(name: String, criteriaJson: String, targetPlotId: UUID, requiresStaging: Boolean, targetPlot: PlotRecord, userId: UUID = FOUNDING_USER_ID): TrellisCreateResult
    fun updateTrellis(id: UUID, name: String?, criteriaJson: String?, requiresStaging: Boolean?, targetPlot: PlotRecord?, userId: UUID = FOUNDING_USER_ID): TrellisUpdateResult
    fun deleteTrellis(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean
    fun runUnstagedTrellisesForUpload(conn: java.sql.Connection, uploadId: UUID, userId: UUID)
}

class PostgresTrellisRepository(private val dataSource: DataSource) : TrellisRepository {

    override fun listTrellises(userId: UUID): List<TrellisRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at
                   FROM trellises WHERE user_id = ? ORDER BY created_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<TrellisRecord>()
                while (rs.next()) results.add(rs.toTrellisRecord())
                return results
            }
        }
    }

    override fun getTrellisById(id: UUID, userId: UUID): TrellisRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at
                   FROM trellises WHERE id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toTrellisRecord()
            }
        }
    }

    override fun createTrellis(
        name: String,
        criteriaJson: String,
        targetPlotId: UUID,
        requiresStaging: Boolean,
        targetPlot: PlotRecord,
        userId: UUID,
    ): TrellisRepository.TrellisCreateResult {
        if (targetPlot.criteria != null) return TrellisRepository.TrellisCreateResult.Error("Target plot must be a collection plot (criteria IS NULL)")

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
                """INSERT INTO trellises (id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at)
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
            val trellis = getTrellisById(id, userId)!!
            if (!effectiveStaging) autoPopulateTrellis(conn, trellis, userId)
            return TrellisRepository.TrellisCreateResult.Success(trellis)
        }
    }

    override fun updateTrellis(
        id: UUID,
        name: String?,
        criteriaJson: String?,
        requiresStaging: Boolean?,
        targetPlot: PlotRecord?,
        userId: UUID,
    ): TrellisRepository.TrellisUpdateResult {
        // Enforce same staging policy as createTrellis
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
                "UPDATE trellises SET ${setClauses.joinToString(", ")} WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (name != null) stmt.setString(idx++, name.trim())
                if (criteriaJson != null) stmt.setString(idx++, criteriaJson)
                if (effectiveStaging != null) stmt.setBoolean(idx++, effectiveStaging)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, userId)
                val updated = stmt.executeUpdate()
                if (updated == 0) return TrellisRepository.TrellisUpdateResult.NotFound
            }
            val updatedTrellis = getTrellisById(id, userId) ?: return TrellisRepository.TrellisUpdateResult.NotFound
            // If trellis is now unstaged (newly or due to criteria change), populate missing items
            if (!updatedTrellis.requiresStaging) autoPopulateTrellis(conn, updatedTrellis, userId)
            return TrellisRepository.TrellisUpdateResult.Success(updatedTrellis)
        }
    }

    override fun deleteTrellis(id: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM trellises WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // Inserts upload into plot_items for every unstaged trellis whose criteria it satisfies.
    override fun runUnstagedTrellisesForUpload(conn: Connection, uploadId: UUID, userId: UUID) {
        val trellises = listTrellises(userId).filter { !it.requiresStaging }
        for (trellis in trellises) {
            try {
                val fragment = CriteriaEvaluator.evaluate(trellis.criteria, userId, conn)
                conn.prepareStatement(
                    """INSERT INTO plot_items (upload_id, plot_id, source_trellis_id, added_by)
                       SELECT id, ?, ?, ?
                       FROM uploads
                       WHERE id = ? AND user_id = ? AND (${fragment.sql})
                       ON CONFLICT (plot_id, upload_id) DO NOTHING"""
                ).use { stmt ->
                    var idx = 1
                    stmt.setObject(idx++, trellis.targetPlotId)
                    stmt.setObject(idx++, trellis.id)
                    stmt.setObject(idx++, userId)
                    stmt.setObject(idx++, uploadId)
                    stmt.setObject(idx++, userId)
                    for (setter in fragment.setters) idx = setter(stmt, idx)
                    stmt.executeUpdate()
                }
            } catch (_: Exception) { /* best-effort; bad criteria skipped */ }
        }
    }

    // Bulk-inserts all existing matching uploads into plot_items for a new unstaged trellis.
    private fun autoPopulateTrellis(conn: Connection, trellis: TrellisRecord, userId: UUID) {
        try {
            val fragment = CriteriaEvaluator.evaluate(trellis.criteria, userId, conn)
            conn.prepareStatement(
                """INSERT INTO plot_items (upload_id, plot_id, source_trellis_id, added_by)
                   SELECT id, ?, ?, ?
                   FROM uploads
                   WHERE user_id = ? AND composted_at IS NULL AND (${fragment.sql})
                   ON CONFLICT (plot_id, upload_id) DO NOTHING"""
            ).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, trellis.targetPlotId)
                stmt.setObject(idx++, trellis.id)
                stmt.setObject(idx++, userId)
                stmt.setObject(idx++, userId)
                for (setter in fragment.setters) idx = setter(stmt, idx)
                stmt.executeUpdate()
            }
        } catch (_: Exception) { /* bad criteria — skip */ }
    }

    private fun ResultSet.toTrellisRecord() = TrellisRecord(
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
