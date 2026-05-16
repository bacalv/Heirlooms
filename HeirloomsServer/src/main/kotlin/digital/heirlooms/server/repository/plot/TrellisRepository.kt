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

/**
 * Pre-wrapped plot DEK supplied by the Android client when it already holds
 * the plot key and can perform the DEK re-wrap locally before upload confirmation.
 * Enables direct plot_items insertion without requiring the staging flow.
 */
data class PrewrappedPlotDek(
    val wrappedItemDek: ByteArray,
    val itemDekFormat: String,
    val wrappedThumbnailDek: ByteArray?,
    val thumbnailDekFormat: String?,
)

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

    /**
     * Same as [runUnstagedTrellisesForUpload] but also handles shared-plot trellises when
     * the client has supplied pre-wrapped plot DEKs.  For each trellis whose target plot has
     * a matching entry in [prewrappedDeks], the item is inserted directly into plot_items
     * (no staging row is written), bypassing the BUG-018 defensive guard for that plot only.
     * Trellises targeting plots not present in [prewrappedDeks] keep their existing behaviour.
     */
    fun runUnstagedTrellisesForUploadWithPrewrappedDeks(
        conn: java.sql.Connection,
        uploadId: UUID,
        userId: UUID,
        prewrappedDeks: Map<UUID, PrewrappedPlotDek>,
    )
}

class PostgresTrellisRepository(private val dataSource: DataSource) : TrellisRepository {

    override fun listTrellises(userId: UUID): List<TrellisRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT t.id, t.user_id, t.name, t.criteria, t.target_plot_id, t.requires_staging,
                          t.created_at, t.updated_at, COALESCE(p.visibility, '') AS target_plot_visibility
                   FROM trellises t
                   LEFT JOIN plots p ON p.id = t.target_plot_id
                   WHERE t.user_id = ? ORDER BY t.created_at ASC"""
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
                """SELECT t.id, t.user_id, t.name, t.criteria, t.target_plot_id, t.requires_staging,
                          t.created_at, t.updated_at, COALESCE(p.visibility, '') AS target_plot_visibility
                   FROM trellises t
                   LEFT JOIN plots p ON p.id = t.target_plot_id
                   WHERE t.id = ? AND t.user_id = ?"""
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
        // public plots always require staging; shared plots default to staging but the
        // user may override (auto-approve performs client-side DEK re-wrap — BUG-018/BUG-020).
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
        // Enforce same staging policy as createTrellis (BUG-018: shared plots also forced)
        val effectiveStaging = requiresStaging?.let {
            when (targetPlot?.visibility) {
                "private"          -> false
                "public", "shared" -> true
                else               -> it
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
        // BUG-018: defensive guard — skip any trellis targeting a shared plot even if
        // requires_staging is falsely stored as false (guards legacy rows and future bypasses).
        val trellises = listTrellises(userId)
            .filter { !it.requiresStaging }
            .filter { it.targetPlotVisibility != "shared" }
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

    /**
     * Inserts upload into plot_items for every unstaged trellis whose criteria it satisfies,
     * and additionally handles shared-plot trellises when the client has provided pre-wrapped
     * DEKs for those plots (BUG-020 fix).
     *
     * For non-shared (private/public) trellises: same behaviour as [runUnstagedTrellisesForUpload].
     * For shared-plot trellises: only inserted directly when a [PrewrappedPlotDek] is present for
     * that plot in [prewrappedDeks]; otherwise the trellis is skipped (item stays in staging).
     */
    override fun runUnstagedTrellisesForUploadWithPrewrappedDeks(
        conn: Connection,
        uploadId: UUID,
        userId: UUID,
        prewrappedDeks: Map<UUID, PrewrappedPlotDek>,
    ) {
        val allUnstaged = listTrellises(userId).filter { !it.requiresStaging }

        // Non-shared trellises: same as original runUnstagedTrellisesForUpload.
        val nonSharedTrellises = allUnstaged.filter { it.targetPlotVisibility != "shared" }
        for (trellis in nonSharedTrellises) {
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

        // Shared-plot trellises: only when caller supplied pre-wrapped DEKs for that plot.
        val sharedTrellises = allUnstaged.filter { it.targetPlotVisibility == "shared" }
        for (trellis in sharedTrellises) {
            val dek = prewrappedDeks[trellis.targetPlotId] ?: continue  // no key → skip (stays in staging)
            try {
                val fragment = CriteriaEvaluator.evaluate(trellis.criteria, userId, conn)
                conn.prepareStatement(
                    """INSERT INTO plot_items (upload_id, plot_id, source_trellis_id, added_by,
                       wrapped_item_dek, item_dek_format, wrapped_thumbnail_dek, thumbnail_dek_format)
                       SELECT id, ?, ?, ?, ?, ?, ?, ?
                       FROM uploads
                       WHERE id = ? AND user_id = ? AND (${fragment.sql})
                       ON CONFLICT (plot_id, upload_id) DO NOTHING"""
                ).use { stmt ->
                    var idx = 1
                    stmt.setObject(idx++, trellis.targetPlotId)
                    stmt.setObject(idx++, trellis.id)
                    stmt.setObject(idx++, userId)
                    stmt.setBytes(idx++, dek.wrappedItemDek)
                    stmt.setString(idx++, dek.itemDekFormat)
                    stmt.setBytes(idx++, dek.wrappedThumbnailDek)
                    stmt.setString(idx++, dek.thumbnailDekFormat)
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
        id                   = getObject("id", UUID::class.java),
        userId               = getObject("user_id", UUID::class.java),
        name                 = getString("name"),
        criteria             = getString("criteria"),
        targetPlotId         = getObject("target_plot_id", UUID::class.java),
        requiresStaging      = getBoolean("requires_staging"),
        createdAt            = getTimestamp("created_at").toInstant(),
        updatedAt            = getTimestamp("updated_at").toInstant(),
        targetPlotVisibility = getString("target_plot_visibility") ?: "",
    )
}
