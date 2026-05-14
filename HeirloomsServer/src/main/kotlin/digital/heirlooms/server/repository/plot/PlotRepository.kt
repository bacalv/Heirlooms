package digital.heirlooms.server.repository.plot

import digital.heirlooms.server.CriteriaEvaluator
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.plot.PlotRecord
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface PlotRepository {
    sealed class PlotUpdateResult {
        data class Success(val plot: PlotRecord) : PlotUpdateResult()
        object NotFound : PlotUpdateResult()
        object SystemDefined : PlotUpdateResult()
    }
    sealed class PlotDeleteResult {
        object Success : PlotDeleteResult()
        object NotFound : PlotDeleteResult()
        object SystemDefined : PlotDeleteResult()
    }
    sealed class BatchReorderResult {
        object Success : BatchReorderResult()
        object NotFound : BatchReorderResult()
        object SystemDefined : BatchReorderResult()
    }

    fun listPlots(userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): List<PlotRecord>
    fun getPlotById(id: UUID): PlotRecord?
    fun getPlotByIdForUser(conn: java.sql.Connection, id: UUID, userId: UUID): PlotRecord?
    fun createPlot(name: String, criteria: String?, showInGarden: Boolean, visibility: String, wrappedPlotKeyB64: String? = null, plotKeyFormat: String? = null, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): PlotRecord
    fun updatePlot(id: UUID, name: String?, sortOrder: Int?, criteria: String?, showInGarden: Boolean?, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): PlotUpdateResult
    fun deletePlot(id: UUID, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): PlotDeleteResult
    fun batchReorderPlots(updates: List<Pair<UUID, Int>>, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): BatchReorderResult
    fun withCriteriaValidation(node: com.fasterxml.jackson.databind.JsonNode, userId: UUID)
    fun isMember(plotId: UUID, userId: UUID): Boolean
    fun fetchExpiredTombstonedPlots(): List<UUID>
    fun hardDeletePlot(plotId: UUID)
    fun createSystemPlot(userId: UUID)
}

class PostgresPlotRepository(private val dataSource: DataSource) : PlotRepository {

    override fun listPlots(userId: UUID): List<PlotRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT p.id, p.owner_user_id, p.name, p.sort_order, p.is_system_defined,
                          p.created_at, p.updated_at, p.criteria, p.show_in_garden, p.visibility,
                          p.plot_status, p.tombstoned_at, p.tombstoned_by, p.created_by,
                          pm_me.local_name
                   FROM plots p
                   LEFT JOIN plot_members pm_me
                          ON pm_me.plot_id = p.id AND pm_me.user_id = ? AND pm_me.status = 'joined'
                   WHERE (
                       (p.owner_user_id = ? AND p.visibility NOT IN ('shared', 'public'))
                       OR (p.visibility IN ('shared', 'public') AND pm_me.user_id IS NOT NULL)
                   )
                   AND p.tombstoned_at IS NULL
                   ORDER BY p.sort_order ASC, p.created_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<PlotRecord>()
                while (rs.next()) {
                    results.add(rs.toPlotRecord().copy(localName = rs.getString("local_name")))
                }
                return results
            }
        }
    }

    override fun getPlotById(id: UUID): PlotRecord? {
        dataSource.connection.use { conn ->
            return getPlotByIdConn(conn, id)
        }
    }

    internal fun getPlotByIdConn(conn: Connection, id: UUID): PlotRecord? =
        conn.prepareStatement(
            """SELECT id, owner_user_id, name, sort_order, is_system_defined,
                      created_at, updated_at, criteria, show_in_garden, visibility,
                      plot_status, tombstoned_at, tombstoned_by, created_by
               FROM plots WHERE id = ?"""
        ).use { stmt ->
            stmt.setObject(1, id)
            val rs = stmt.executeQuery()
            if (!rs.next()) null else rs.toPlotRecord()
        }

    override fun getPlotByIdForUser(conn: Connection, id: UUID, userId: UUID): PlotRecord? {
        val plot = getPlotByIdConn(conn, id) ?: return null
        return when {
            plot.ownerUserId == userId -> plot
            plot.visibility == "shared" && isMemberConn(conn, id, userId) -> plot
            else -> null
        }
    }

    override fun createPlot(
        name: String,
        criteria: String?,
        showInGarden: Boolean,
        visibility: String,
        wrappedPlotKeyB64: String?,
        plotKeyFormat: String?,
        userId: UUID,
    ): PlotRecord {
        val id = UUID.randomUUID()
        val now = Instant.now()
        withTransaction { conn ->
            conn.prepareStatement(
                """INSERT INTO plots (id, owner_user_id, name, criteria, show_in_garden, visibility,
                                     created_by, created_at, updated_at)
                   VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.setString(3, name.trim())
                stmt.setString(4, criteria)
                stmt.setBoolean(5, showInGarden)
                stmt.setString(6, visibility)
                stmt.setObject(7, userId)
                stmt.setTimestamp(8, Timestamp.from(now))
                stmt.setTimestamp(9, Timestamp.from(now))
                stmt.executeUpdate()
            }
            if (visibility == "shared" && wrappedPlotKeyB64 != null && plotKeyFormat != null) {
                val keyBytes = java.util.Base64.getDecoder().decode(wrappedPlotKeyB64)
                conn.prepareStatement(
                    """INSERT INTO plot_members (plot_id, user_id, role, wrapped_plot_key, plot_key_format, status)
                       VALUES (?, ?, 'owner', ?, ?, 'joined')"""
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, userId)
                    stmt.setBytes(3, keyBytes)
                    stmt.setString(4, plotKeyFormat)
                    stmt.executeUpdate()
                }
            }
        }
        return getPlotById(id)!!
    }


    override fun updatePlot(
        id: UUID,
        name: String?,
        sortOrder: Int?,
        criteria: String?,
        showInGarden: Boolean?,
        userId: UUID,
    ): PlotRepository.PlotUpdateResult {
        withTransaction { conn ->
            val (isSystemDefined) = conn.prepareStatement(
                "SELECT is_system_defined FROM plots WHERE id = ? AND owner_user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return PlotRepository.PlotUpdateResult.NotFound
                listOf(rs.getBoolean("is_system_defined"))
            }
            if (isSystemDefined) return PlotRepository.PlotUpdateResult.SystemDefined

            val setClauses = mutableListOf("updated_at = ?")
            if (name != null) setClauses.add("name = ?")
            if (sortOrder != null) setClauses.add("sort_order = ?")
            if (criteria != null) setClauses.add("criteria = ?::jsonb")
            if (showInGarden != null) setClauses.add("show_in_garden = ?")
            conn.prepareStatement("UPDATE plots SET ${setClauses.joinToString(", ")} WHERE id = ? AND owner_user_id = ?").use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (name != null) stmt.setString(idx++, name.trim())
                if (sortOrder != null) stmt.setInt(idx++, sortOrder)
                if (criteria != null) stmt.setString(idx++, criteria)
                if (showInGarden != null) stmt.setBoolean(idx++, showInGarden)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, userId)
                stmt.executeUpdate()
            }
        }
        return getPlotById(id)?.let { PlotRepository.PlotUpdateResult.Success(it) } ?: PlotRepository.PlotUpdateResult.NotFound
    }


    override fun deletePlot(id: UUID, userId: UUID): PlotRepository.PlotDeleteResult {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT is_system_defined FROM plots WHERE id = ? AND owner_user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return PlotRepository.PlotDeleteResult.NotFound
                if (rs.getBoolean("is_system_defined")) return PlotRepository.PlotDeleteResult.SystemDefined
            }
            conn.prepareStatement("DELETE FROM plots WHERE id = ? AND owner_user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }
        return PlotRepository.PlotDeleteResult.Success
    }


    override fun batchReorderPlots(updates: List<Pair<UUID, Int>>, userId: UUID): PlotRepository.BatchReorderResult {
        withTransaction { conn ->
            for ((id, _) in updates) {
                conn.prepareStatement(
                    "SELECT is_system_defined FROM plots WHERE id = ? AND owner_user_id = ? FOR UPDATE"
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, userId)
                    val rs = stmt.executeQuery()
                    if (!rs.next()) return PlotRepository.BatchReorderResult.NotFound
                    if (rs.getBoolean("is_system_defined")) return PlotRepository.BatchReorderResult.SystemDefined
                }
            }
            val now = Timestamp.from(Instant.now())
            for ((id, sortOrder) in updates) {
                conn.prepareStatement(
                    "UPDATE plots SET sort_order = ?, updated_at = ? WHERE id = ? AND owner_user_id = ?"
                ).use { stmt ->
                    stmt.setInt(1, sortOrder)
                    stmt.setTimestamp(2, now)
                    stmt.setObject(3, id)
                    stmt.setObject(4, userId)
                    stmt.executeUpdate()
                }
            }
        }
        return PlotRepository.BatchReorderResult.Success
    }

    override fun withCriteriaValidation(node: com.fasterxml.jackson.databind.JsonNode, userId: UUID) {
        dataSource.connection.use { conn ->
            CriteriaEvaluator.validate(node, userId, conn)
        }
    }

    override fun isMember(plotId: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn -> return isMemberConn(conn, plotId, userId) }
    }

    internal fun isMemberConn(conn: Connection, plotId: UUID, userId: UUID): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM plot_members WHERE plot_id = ? AND user_id = ? AND status = 'joined'"
        ).use { stmt ->
            stmt.setObject(1, plotId); stmt.setObject(2, userId)
            stmt.executeQuery().next()
        }

    override fun fetchExpiredTombstonedPlots(): List<UUID> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id FROM plots WHERE tombstoned_at < NOW() - INTERVAL '90 days'"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableListOf<UUID>()
                while (rs.next()) results.add(rs.getObject("id", UUID::class.java))
                return results
            }
        }
    }

    override fun hardDeletePlot(plotId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM plots WHERE id = ?").use { stmt ->
                stmt.setObject(1, plotId); stmt.executeUpdate()
            }
        }
    }

    override fun createSystemPlot(userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO plots (id, owner_user_id, name, sort_order, is_system_defined)
                   VALUES (gen_random_uuid(), ?, '__just_arrived__', -1000, TRUE)"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeUpdate()
            }
        }
    }

    private inline fun <T> withTransaction(block: (Connection) -> T): T {
        val conn = dataSource.connection
        conn.autoCommit = false
        var committed = false
        try {
            val result = block(conn)
            conn.commit()
            committed = true
            return result
        } catch (e: Exception) {
            try { conn.rollback() } catch (_: Exception) {}
            throw e
        } finally {
            if (!committed) try { conn.rollback() } catch (_: Exception) {}
            conn.autoCommit = true
            conn.close()
        }
    }

    private fun ResultSet.toPlotRecord() = PlotRecord(
        id = getObject("id", UUID::class.java),
        ownerUserId = getObject("owner_user_id", UUID::class.java),
        name = getString("name"),
        sortOrder = getInt("sort_order"),
        isSystemDefined = getBoolean("is_system_defined"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
        criteria = getString("criteria"),
        showInGarden = getBoolean("show_in_garden"),
        visibility = getString("visibility"),
        plotStatus = getString("plot_status") ?: "open",
        tombstonedAt = getTimestamp("tombstoned_at")?.toInstant(),
        tombstonedBy = getObject("tombstoned_by", UUID::class.java),
        createdBy = getObject("created_by", UUID::class.java),
    )
}
