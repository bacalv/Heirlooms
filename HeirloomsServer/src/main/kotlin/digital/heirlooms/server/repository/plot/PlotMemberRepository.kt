package digital.heirlooms.server.repository.plot

import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.plot.PlotInviteRecord
import digital.heirlooms.server.domain.plot.PlotMemberRecord
import digital.heirlooms.server.domain.plot.SharedMembershipRecord
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PlotMemberRepository(private val dataSource: DataSource) {

    fun getPlotKey(plotId: UUID, userId: UUID = FOUNDING_USER_ID): Pair<ByteArray, String>? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT wrapped_plot_key, plot_key_format FROM plot_members WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                val key = rs.getBytes("wrapped_plot_key") ?: return null
                val fmt = rs.getString("plot_key_format") ?: return null
                return Pair(key, fmt)
            }
        }
    }

    fun listMembers(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<PlotMemberRecord>? {
        dataSource.connection.use { conn ->
            if (!isMemberConn(conn, plotId, userId)) return null
            conn.prepareStatement(
                """SELECT pm.plot_id, pm.user_id, u.display_name, u.username,
                          pm.role, pm.wrapped_plot_key, pm.plot_key_format, pm.joined_at,
                          pm.status, pm.local_name, pm.left_at
                   FROM plot_members pm
                   JOIN users u ON u.id = pm.user_id
                   WHERE pm.plot_id = ? AND pm.status IN ('joined', 'invited')
                   ORDER BY pm.joined_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<PlotMemberRecord>()
                while (rs.next()) results.add(PlotMemberRecord(
                    plotId      = rs.getObject("plot_id", UUID::class.java),
                    userId      = rs.getObject("user_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    username    = rs.getString("username"),
                    role        = rs.getString("role"),
                    wrappedPlotKey = rs.getBytes("wrapped_plot_key"),
                    plotKeyFormat  = rs.getString("plot_key_format"),
                    joinedAt    = rs.getTimestamp("joined_at").toInstant(),
                    status      = rs.getString("status"),
                    localName   = rs.getString("local_name"),
                    leftAt      = rs.getTimestamp("left_at")?.toInstant(),
                ))
                return results
            }
        }
    }

    sealed class AddMemberResult {
        object Success : AddMemberResult()
        object NotMember : AddMemberResult()
        object NotFriends : AddMemberResult()
        object AlreadyMember : AddMemberResult()
    }

    fun addMember(
        plotId: UUID,
        newUserId: UUID,
        wrappedPlotKey: ByteArray,
        plotKeyFormat: String,
        inviterUserId: UUID = FOUNDING_USER_ID,
    ): AddMemberResult {
        dataSource.connection.use { conn ->
            if (!isMemberConn(conn, plotId, inviterUserId)) return AddMemberResult.NotMember

            val areFriends = conn.prepareStatement(
                """SELECT 1 FROM friendships
                   WHERE (user_id_1 = LEAST(?, ?) AND user_id_2 = GREATEST(?, ?))"""
            ).use { stmt ->
                stmt.setObject(1, inviterUserId); stmt.setObject(2, newUserId)
                stmt.setObject(3, inviterUserId); stmt.setObject(4, newUserId)
                stmt.executeQuery().next()
            }
            if (!areFriends) return AddMemberResult.NotFriends

            val existingStatus = conn.prepareStatement(
                "SELECT status FROM plot_members WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, newUserId)
                val rs = stmt.executeQuery()
                if (!rs.next()) null else rs.getString("status")
            }
            if (existingStatus == "joined" || existingStatus == "invited") return AddMemberResult.AlreadyMember

            if (existingStatus == null) {
                conn.prepareStatement(
                    """INSERT INTO plot_members (plot_id, user_id, role, wrapped_plot_key, plot_key_format, status)
                       VALUES (?, ?, 'member', ?, ?, 'invited')"""
                ).use { stmt ->
                    stmt.setObject(1, plotId); stmt.setObject(2, newUserId)
                    stmt.setBytes(3, wrappedPlotKey); stmt.setString(4, plotKeyFormat)
                    stmt.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                    """UPDATE plot_members SET status = 'invited', wrapped_plot_key = ?,
                       plot_key_format = ?, left_at = NULL
                       WHERE plot_id = ? AND user_id = ?"""
                ).use { stmt ->
                    stmt.setBytes(1, wrappedPlotKey); stmt.setString(2, plotKeyFormat)
                    stmt.setObject(3, plotId); stmt.setObject(4, newUserId)
                    stmt.executeUpdate()
                }
            }
        }
        return AddMemberResult.Success
    }

    fun createInvite(plotId: UUID, userId: UUID = FOUNDING_USER_ID): PlotInviteRecord? {
        if (!isMember(plotId, userId)) return null
        val id = UUID.randomUUID()
        val token = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.security.SecureRandom().generateSeed(36))
        val now = Instant.now()
        val expiresAt = now.plus(48, java.time.temporal.ChronoUnit.HOURS)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO plot_invites (id, plot_id, created_by, token, expires_at)
                   VALUES (?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id); stmt.setObject(2, plotId); stmt.setObject(3, userId)
                stmt.setString(4, token); stmt.setTimestamp(5, Timestamp.from(expiresAt))
                stmt.executeUpdate()
            }
        }
        return PlotInviteRecord(id, plotId, userId, token, null, null, null, expiresAt, now)
    }

    data class InviteInfo(
        val plotId: UUID,
        val plotName: String,
        val inviterDisplayName: String,
        val inviterUserId: UUID,
    )

    fun getInviteInfo(token: String): InviteInfo? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT pi.plot_id, p.name AS plot_name, u.display_name, pi.created_by,
                          pi.used_at, pi.expires_at
                   FROM plot_invites pi
                   JOIN plots p ON p.id = pi.plot_id
                   JOIN users u ON u.id = pi.created_by
                   WHERE pi.token = ?"""
            ).use { stmt ->
                stmt.setString(1, token)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                if (rs.getTimestamp("used_at") != null) return null
                if (rs.getTimestamp("expires_at").toInstant().isBefore(Instant.now())) return null
                return InviteInfo(
                    plotId = rs.getObject("plot_id", UUID::class.java),
                    plotName = rs.getString("plot_name"),
                    inviterDisplayName = rs.getString("display_name"),
                    inviterUserId = rs.getObject("created_by", UUID::class.java),
                )
            }
        }
    }

    sealed class RedeemInviteResult {
        data class Pending(val inviteId: UUID, val inviterDisplayName: String) : RedeemInviteResult()
        object Invalid : RedeemInviteResult()
        object AlreadyMember : RedeemInviteResult()
    }

    fun redeemInvite(token: String, recipientUserId: UUID, recipientPubkey: String): RedeemInviteResult {
        dataSource.connection.use { conn ->
            val info = getInviteInfo(token) ?: return RedeemInviteResult.Invalid
            val existingStatus = conn.prepareStatement(
                "SELECT status FROM plot_members WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, info.plotId); stmt.setObject(2, recipientUserId)
                val rs = stmt.executeQuery()
                if (!rs.next()) null else rs.getString("status")
            }
            if (existingStatus == "joined" || existingStatus == "invited") return RedeemInviteResult.AlreadyMember

            val inviteId = conn.prepareStatement(
                """UPDATE plot_invites SET recipient_user_id = ?, recipient_pubkey = ?
                   WHERE token = ? AND used_at IS NULL
                   RETURNING id, (SELECT display_name FROM users WHERE id = created_by)"""
            ).use { stmt ->
                stmt.setObject(1, recipientUserId); stmt.setString(2, recipientPubkey)
                stmt.setString(3, token)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RedeemInviteResult.Invalid
                Pair(rs.getObject("id", UUID::class.java), rs.getString("display_name"))
            }
            return RedeemInviteResult.Pending(inviteId.first, inviteId.second)
        }
    }

    fun listPendingInvites(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<Map<String, String>> {
        if (!isMember(plotId, userId)) return emptyList()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT pi.id, pi.recipient_pubkey, u.display_name, u.username
                   FROM plot_invites pi
                   LEFT JOIN users u ON u.id = pi.recipient_user_id
                   WHERE pi.plot_id = ?
                     AND pi.recipient_pubkey IS NOT NULL
                     AND pi.used_at IS NULL
                     AND pi.expires_at > NOW()"""
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<Map<String, String>>()
                while (rs.next()) results.add(mapOf(
                    "inviteId" to rs.getObject("id", UUID::class.java).toString(),
                    "recipientPubkey" to (rs.getString("recipient_pubkey") ?: ""),
                    "displayName" to (rs.getString("display_name") ?: "Unknown"),
                    "username" to (rs.getString("username") ?: ""),
                ))
                return results
            }
        }
    }

    fun confirmInvite(
        inviteId: UUID,
        plotId: UUID,
        wrappedPlotKey: ByteArray,
        plotKeyFormat: String,
        confirmerUserId: UUID = FOUNDING_USER_ID,
    ): Boolean {
        if (!isMember(plotId, confirmerUserId)) return false
        withTransaction { conn ->
            val row = conn.prepareStatement(
                """SELECT recipient_user_id FROM plot_invites
                   WHERE id = ? AND plot_id = ? AND used_at IS NULL AND recipient_user_id IS NOT NULL"""
            ).use { stmt ->
                stmt.setObject(1, inviteId); stmt.setObject(2, plotId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return false
                rs.getObject("recipient_user_id", UUID::class.java)
            }
            conn.prepareStatement(
                """INSERT INTO plot_members (plot_id, user_id, role, wrapped_plot_key, plot_key_format, status)
                   VALUES (?, ?, 'member', ?, ?, 'invited')
                   ON CONFLICT (plot_id, user_id) DO UPDATE
                   SET status = 'invited', wrapped_plot_key = EXCLUDED.wrapped_plot_key,
                       plot_key_format = EXCLUDED.plot_key_format, left_at = NULL
                   WHERE plot_members.status != 'joined'"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, row)
                stmt.setBytes(3, wrappedPlotKey); stmt.setString(4, plotKeyFormat)
                stmt.executeUpdate()
            }
            conn.prepareStatement(
                "UPDATE plot_invites SET used_by = ?, used_at = NOW() WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, row); stmt.setObject(2, inviteId)
                stmt.executeUpdate()
            }
        }
        return true
    }

    fun listSharedMemberships(userId: UUID): List<SharedMembershipRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT pm.plot_id, p.name AS plot_name, p.owner_user_id,
                          u_owner.display_name AS owner_display_name,
                          pm.role, pm.status, pm.local_name, pm.joined_at, pm.left_at,
                          p.plot_status, p.tombstoned_at, p.tombstoned_by
                   FROM plot_members pm
                   JOIN plots p ON p.id = pm.plot_id
                   LEFT JOIN users u_owner ON u_owner.id = p.owner_user_id
                   WHERE pm.user_id = ?
                   ORDER BY pm.joined_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<SharedMembershipRecord>()
                while (rs.next()) results.add(SharedMembershipRecord(
                    plotId           = rs.getObject("plot_id", UUID::class.java),
                    plotName         = rs.getString("plot_name"),
                    ownerUserId      = rs.getObject("owner_user_id", UUID::class.java),
                    ownerDisplayName = rs.getString("owner_display_name"),
                    role             = rs.getString("role"),
                    status           = rs.getString("status"),
                    localName        = rs.getString("local_name"),
                    joinedAt         = rs.getTimestamp("joined_at").toInstant(),
                    leftAt           = rs.getTimestamp("left_at")?.toInstant(),
                    plotStatus       = rs.getString("plot_status") ?: "open",
                    tombstonedAt     = rs.getTimestamp("tombstoned_at")?.toInstant(),
                    tombstonedBy     = rs.getObject("tombstoned_by", UUID::class.java),
                ))
                return results
            }
        }
    }

    sealed class AcceptInviteResult {
        object Success : AcceptInviteResult()
        object NotInvited : AcceptInviteResult()
        object AlreadyJoined : AcceptInviteResult()
    }

    fun acceptInvite(plotId: UUID, userId: UUID, localName: String): AcceptInviteResult {
        dataSource.connection.use { conn ->
            val status = conn.prepareStatement(
                "SELECT status FROM plot_members WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return AcceptInviteResult.NotInvited
                rs.getString("status")
            }
            if (status == "joined") return AcceptInviteResult.AlreadyJoined
            if (status != "invited") return AcceptInviteResult.NotInvited
            conn.prepareStatement(
                "UPDATE plot_members SET status = 'joined', local_name = ? WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setString(1, localName.trim().takeIf { it.isNotBlank() })
                stmt.setObject(2, plotId); stmt.setObject(3, userId)
                stmt.executeUpdate()
            }
        }
        return AcceptInviteResult.Success
    }

    sealed class RejoinResult {
        object Success : RejoinResult()
        object NotLeft : RejoinResult()
        object PlotTombstoned : RejoinResult()
    }

    fun rejoinPlot(plotId: UUID, userId: UUID, localName: String?): RejoinResult {
        dataSource.connection.use { conn ->
            val row = conn.prepareStatement(
                """SELECT pm.status, pm.local_name, p.tombstoned_at
                   FROM plot_members pm
                   JOIN plots p ON p.id = pm.plot_id
                   WHERE pm.plot_id = ? AND pm.user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RejoinResult.NotLeft
                Triple(rs.getString("status"), rs.getString("local_name"), rs.getTimestamp("tombstoned_at"))
            }
            if (row.first != "left") return RejoinResult.NotLeft
            if (row.third != null) return RejoinResult.PlotTombstoned

            val resolvedName = localName?.trim()?.takeIf { it.isNotBlank() } ?: row.second
            conn.prepareStatement(
                "UPDATE plot_members SET status = 'joined', left_at = NULL, local_name = ? WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setString(1, resolvedName)
                stmt.setObject(2, plotId); stmt.setObject(3, userId)
                stmt.executeUpdate()
            }
        }
        return RejoinResult.Success
    }

    sealed class RestorePlotResult {
        object Success : RestorePlotResult()
        object NotTombstoned : RestorePlotResult()
        object NotAuthorized : RestorePlotResult()
        object WindowExpired : RestorePlotResult()
    }

    fun restorePlot(plotId: UUID, userId: UUID): RestorePlotResult {
        withTransaction { conn ->
            val row = conn.prepareStatement(
                "SELECT tombstoned_at, tombstoned_by FROM plots WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RestorePlotResult.NotTombstoned
                Pair(rs.getTimestamp("tombstoned_at")?.toInstant(), rs.getObject("tombstoned_by", UUID::class.java))
            }
            val (tombstonedAt, tombstonedBy) = row
            if (tombstonedAt == null) return RestorePlotResult.NotTombstoned
            if (tombstonedBy != userId) return RestorePlotResult.NotAuthorized
            if (tombstonedAt.isBefore(Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS)))
                return RestorePlotResult.WindowExpired

            conn.prepareStatement(
                "UPDATE plots SET tombstoned_at = NULL, tombstoned_by = NULL WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.executeUpdate()
            }
            conn.prepareStatement(
                "UPDATE plot_members SET status = 'joined', left_at = NULL WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId); stmt.executeUpdate()
            }
        }
        return RestorePlotResult.Success
    }

    sealed class TransferOwnershipResult {
        object Success : TransferOwnershipResult()
        object NotOwner : TransferOwnershipResult()
        object TargetNotMember : TransferOwnershipResult()
        object NotFound : TransferOwnershipResult()
    }

    fun transferOwnership(plotId: UUID, newOwnerId: UUID, currentOwnerId: UUID): TransferOwnershipResult {
        withTransaction { conn ->
            val ownerUserId = conn.prepareStatement(
                "SELECT owner_user_id FROM plots WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return TransferOwnershipResult.NotFound
                rs.getObject("owner_user_id", UUID::class.java)
            }
            if (ownerUserId != currentOwnerId) return TransferOwnershipResult.NotOwner

            val targetIsJoined = conn.prepareStatement(
                "SELECT 1 FROM plot_members WHERE plot_id = ? AND user_id = ? AND status = 'joined'"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, newOwnerId)
                stmt.executeQuery().next()
            }
            if (!targetIsJoined) return TransferOwnershipResult.TargetNotMember

            conn.prepareStatement(
                "UPDATE plot_members SET role = 'member' WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, currentOwnerId); stmt.executeUpdate()
            }
            conn.prepareStatement(
                "UPDATE plot_members SET role = 'owner' WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, newOwnerId); stmt.executeUpdate()
            }
            conn.prepareStatement(
                "UPDATE plots SET owner_user_id = ?, updated_at = NOW() WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, newOwnerId); stmt.setObject(2, plotId); stmt.executeUpdate()
            }
        }
        return TransferOwnershipResult.Success
    }

    sealed class SetPlotStatusResult {
        object Success : SetPlotStatusResult()
        object NotOwner : SetPlotStatusResult()
        object NotFound : SetPlotStatusResult()
        data class InvalidStatus(val status: String) : SetPlotStatusResult()
    }

    fun setPlotStatus(plotId: UUID, status: String, userId: UUID): SetPlotStatusResult {
        if (status != "open" && status != "closed") return SetPlotStatusResult.InvalidStatus(status)
        dataSource.connection.use { conn ->
            val updated = conn.prepareStatement(
                "UPDATE plots SET plot_status = ?, updated_at = NOW() WHERE id = ? AND owner_user_id = ?"
            ).use { stmt ->
                stmt.setString(1, status); stmt.setObject(2, plotId); stmt.setObject(3, userId)
                stmt.executeUpdate()
            }
            return when {
                updated > 0 -> SetPlotStatusResult.Success
                else -> {
                    val exists = conn.prepareStatement("SELECT 1 FROM plots WHERE id = ?").use { s ->
                        s.setObject(1, plotId); s.executeQuery().next()
                    }
                    if (exists) SetPlotStatusResult.NotOwner else SetPlotStatusResult.NotFound
                }
            }
        }
    }

    sealed class LeavePlotResult {
        object Success : LeavePlotResult()
        object NotFound : LeavePlotResult()
        object MustTransferFirst : LeavePlotResult()
    }

    fun leavePlot(plotId: UUID, userId: UUID): LeavePlotResult {
        withTransaction { conn ->
            val role = conn.prepareStatement(
                "SELECT role FROM plot_members WHERE plot_id = ? AND user_id = ? AND status = 'joined'"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return LeavePlotResult.NotFound
                rs.getString("role")
            }

            if (role == "owner") {
                val otherJoined = conn.prepareStatement(
                    "SELECT COUNT(*) FROM plot_members WHERE plot_id = ? AND user_id != ? AND status = 'joined'"
                ).use { stmt ->
                    stmt.setObject(1, plotId); stmt.setObject(2, userId)
                    val rs = stmt.executeQuery(); rs.next(); rs.getInt(1)
                }
                if (otherJoined > 0) return LeavePlotResult.MustTransferFirst
            }

            conn.prepareStatement(
                "UPDATE plot_members SET status = 'left', left_at = NOW() WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, userId)
                stmt.executeUpdate()
            }

            val remainingJoined = conn.prepareStatement(
                "SELECT COUNT(*) FROM plot_members WHERE plot_id = ? AND status = 'joined'"
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery(); rs.next(); rs.getInt(1)
            }

            if (remainingJoined == 0) {
                conn.prepareStatement(
                    "UPDATE plots SET tombstoned_at = NOW(), tombstoned_by = ? WHERE id = ?"
                ).use { stmt ->
                    stmt.setObject(1, userId); stmt.setObject(2, plotId)
                    stmt.executeUpdate()
                }
            }
        }
        return LeavePlotResult.Success
    }

    private fun isMember(plotId: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn -> return isMemberConn(conn, plotId, userId) }
    }

    private fun isMemberConn(conn: Connection, plotId: UUID, userId: UUID): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM plot_members WHERE plot_id = ? AND user_id = ? AND status = 'joined'"
        ).use { stmt ->
            stmt.setObject(1, plotId); stmt.setObject(2, userId)
            stmt.executeQuery().next()
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
}
