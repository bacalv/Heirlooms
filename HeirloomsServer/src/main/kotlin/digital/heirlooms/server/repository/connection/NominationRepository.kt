package digital.heirlooms.server.repository.connection

import digital.heirlooms.server.domain.connection.NominationRecord
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface NominationRepository {

    /** Create a new pending nomination. Caller has already validated the connection ownership. */
    fun createNomination(
        ownerUserId: UUID,
        connectionId: UUID,
        message: String?,
    ): NominationRecord

    /** List all nominations issued by the owner (all statuses). */
    fun listByOwner(ownerUserId: UUID): List<NominationRecord>

    /**
     * List all nominations where the authenticated user is the nominee.
     * Nominee is identified via connections.contact_user_id = nomineeUserId.
     */
    fun listReceived(nomineeUserId: UUID): List<NominationRecord>

    /** Fetch a single nomination by id regardless of role. Returns null if not found. */
    fun getById(id: UUID): NominationRecord?

    /**
     * Fetch a connection's contact_user_id given a connection_id.
     * Returns null if connection does not exist or has no contact_user_id.
     */
    fun getContactUserId(connectionId: UUID): UUID?

    /**
     * Returns true if there is already a pending or accepted nomination for the given connection.
     */
    fun hasActiveNomination(connectionId: UUID): Boolean

    /**
     * Atomically update status to the given value and set responded_at.
     * Returns the updated record, or null if not found.
     */
    fun setRespondedStatus(id: UUID, status: String): NominationRecord?

    /**
     * Atomically update status to 'revoked' and set revoked_at.
     * Returns the updated record, or null if not found.
     */
    fun setRevoked(id: UUID): NominationRecord?

    /**
     * Return the owner_user_id of the connection that owns this nomination.
     * Used to verify the connection still belongs to the caller.
     */
    fun getConnectionOwnerUserId(connectionId: UUID): UUID?
}

class PostgresNominationRepository(private val dataSource: DataSource) : NominationRepository {

    override fun createNomination(
        ownerUserId: UUID,
        connectionId: UUID,
        message: String?,
    ): NominationRecord {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO executor_nominations
                     (id, owner_user_id, connection_id, status, offered_at, message)
                   VALUES (?, ?, ?, 'pending', ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, ownerUserId)
                stmt.setObject(3, connectionId)
                stmt.setTimestamp(4, now)
                stmt.setString(5, message)
                stmt.executeUpdate()
            }
        }
        return getById(id)!!
    }

    override fun listByOwner(ownerUserId: UUID): List<NominationRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, owner_user_id, connection_id, status, offered_at,
                          responded_at, revoked_at, message
                   FROM executor_nominations
                   WHERE owner_user_id = ?
                   ORDER BY offered_at"""
            ).use { stmt ->
                stmt.setObject(1, ownerUserId)
                val rs = stmt.executeQuery()
                val list = mutableListOf<NominationRecord>()
                while (rs.next()) list.add(rs.toNominationRecord())
                return list
            }
        }
    }

    override fun listReceived(nomineeUserId: UUID): List<NominationRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT en.id, en.owner_user_id, en.connection_id, en.status, en.offered_at,
                          en.responded_at, en.revoked_at, en.message
                   FROM executor_nominations en
                   JOIN connections c ON c.id = en.connection_id
                   WHERE c.contact_user_id = ?
                   ORDER BY en.offered_at"""
            ).use { stmt ->
                stmt.setObject(1, nomineeUserId)
                val rs = stmt.executeQuery()
                val list = mutableListOf<NominationRecord>()
                while (rs.next()) list.add(rs.toNominationRecord())
                return list
            }
        }
    }

    override fun getById(id: UUID): NominationRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, owner_user_id, connection_id, status, offered_at,
                          responded_at, revoked_at, message
                   FROM executor_nominations
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.toNominationRecord() else null
            }
        }
    }

    override fun getContactUserId(connectionId: UUID): UUID? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT contact_user_id FROM connections WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, connectionId)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getObject("contact_user_id", UUID::class.java) else null
            }
        }
    }

    override fun hasActiveNomination(connectionId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT EXISTS(
                     SELECT 1 FROM executor_nominations
                     WHERE connection_id = ? AND status IN ('pending', 'accepted')
                   )"""
            ).use { stmt ->
                stmt.setObject(1, connectionId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getBoolean(1)
            }
        }
    }

    override fun setRespondedStatus(id: UUID, status: String): NominationRecord? {
        val now = Timestamp.from(Instant.now())
        dataSource.connection.use { conn ->
            val updated = conn.prepareStatement(
                """UPDATE executor_nominations
                   SET status = ?, responded_at = ?
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setString(1, status)
                stmt.setTimestamp(2, now)
                stmt.setObject(3, id)
                stmt.executeUpdate()
            }
            return if (updated > 0) getById(id) else null
        }
    }

    override fun setRevoked(id: UUID): NominationRecord? {
        val now = Timestamp.from(Instant.now())
        dataSource.connection.use { conn ->
            val updated = conn.prepareStatement(
                """UPDATE executor_nominations
                   SET status = 'revoked', revoked_at = ?
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setTimestamp(1, now)
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
            return if (updated > 0) getById(id) else null
        }
    }

    override fun getConnectionOwnerUserId(connectionId: UUID): UUID? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT owner_user_id FROM connections WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, connectionId)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getObject("owner_user_id", UUID::class.java) else null
            }
        }
    }

    private fun ResultSet.toNominationRecord() = NominationRecord(
        id = getObject("id", UUID::class.java),
        ownerUserId = getObject("owner_user_id", UUID::class.java),
        connectionId = getObject("connection_id", UUID::class.java),
        status = getString("status"),
        offeredAt = getTimestamp("offered_at").toInstant(),
        respondedAt = getTimestamp("responded_at")?.toInstant(),
        revokedAt = getTimestamp("revoked_at")?.toInstant(),
        message = getString("message"),
    )
}
