package digital.heirlooms.server.repository.connection

import digital.heirlooms.server.domain.connection.ConnectionRecord
import java.sql.Array as SqlArray
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface ConnectionRepository {

    sealed class DeleteResult {
        object Deleted : DeleteResult()
        object NotFound : DeleteResult()
        object ActiveNominationsExist : DeleteResult()
    }

    fun listConnections(ownerUserId: UUID): List<ConnectionRecord>

    fun getConnection(id: UUID, ownerUserId: UUID): ConnectionRecord?

    fun createConnection(
        ownerUserId: UUID,
        contactUserId: UUID?,
        displayName: String,
        email: String?,
        sharingPubkey: String?,
        roles: List<String>,
    ): ConnectionRecord

    fun updateConnection(
        id: UUID,
        ownerUserId: UUID,
        displayName: String?,
        roles: List<String>?,
        sharingPubkey: String?,
        clearSharingPubkey: Boolean,
    ): ConnectionRecord?

    fun deleteConnection(id: UUID, ownerUserId: UUID): DeleteResult

    fun lookupSharingPubkey(contactUserId: UUID): String?
}

class PostgresConnectionRepository(private val dataSource: DataSource) : ConnectionRepository {

    override fun listConnections(ownerUserId: UUID): List<ConnectionRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, owner_user_id, contact_user_id, display_name, email,
                          sharing_pubkey, roles, created_at, updated_at
                   FROM connections
                   WHERE owner_user_id = ?
                   ORDER BY created_at"""
            ).use { stmt ->
                stmt.setObject(1, ownerUserId)
                val rs = stmt.executeQuery()
                val list = mutableListOf<ConnectionRecord>()
                while (rs.next()) list.add(rs.toConnectionRecord())
                return list
            }
        }
    }

    override fun getConnection(id: UUID, ownerUserId: UUID): ConnectionRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, owner_user_id, contact_user_id, display_name, email,
                          sharing_pubkey, roles, created_at, updated_at
                   FROM connections
                   WHERE id = ? AND owner_user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, ownerUserId)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.toConnectionRecord() else null
            }
        }
    }

    override fun createConnection(
        ownerUserId: UUID,
        contactUserId: UUID?,
        displayName: String,
        email: String?,
        sharingPubkey: String?,
        roles: List<String>,
    ): ConnectionRecord {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        dataSource.connection.use { conn ->
            val rolesArr = conn.createArrayOf("text", roles.toTypedArray())
            conn.prepareStatement(
                """INSERT INTO connections
                     (id, owner_user_id, contact_user_id, display_name, email, sharing_pubkey, roles, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, ownerUserId)
                stmt.setObject(3, contactUserId)
                stmt.setString(4, displayName)
                stmt.setString(5, email)
                stmt.setString(6, sharingPubkey)
                stmt.setArray(7, rolesArr)
                stmt.setTimestamp(8, now)
                stmt.setTimestamp(9, now)
                stmt.executeUpdate()
            }
        }
        return getConnection(id, ownerUserId)!!
    }

    override fun updateConnection(
        id: UUID,
        ownerUserId: UUID,
        displayName: String?,
        roles: List<String>?,
        sharingPubkey: String?,
        clearSharingPubkey: Boolean,
    ): ConnectionRecord? {
        dataSource.connection.use { conn ->
            // Verify ownership first
            val exists = conn.prepareStatement(
                "SELECT 1 FROM connections WHERE id = ? AND owner_user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, ownerUserId)
                stmt.executeQuery().next()
            }
            if (!exists) return null

            val setClauses = mutableListOf("updated_at = ?")
            if (displayName != null) setClauses.add("display_name = ?")
            if (roles != null) setClauses.add("roles = ?")
            if (sharingPubkey != null || clearSharingPubkey) setClauses.add("sharing_pubkey = ?")

            conn.prepareStatement(
                "UPDATE connections SET ${setClauses.joinToString(", ")} WHERE id = ? AND owner_user_id = ?"
            ).use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (displayName != null) stmt.setString(idx++, displayName)
                if (roles != null) stmt.setArray(idx++, conn.createArrayOf("text", roles.toTypedArray()))
                if (sharingPubkey != null) stmt.setString(idx++, sharingPubkey)
                else if (clearSharingPubkey) stmt.setNull(idx++, java.sql.Types.VARCHAR)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, ownerUserId)
                stmt.executeUpdate()
            }
        }
        return getConnection(id, ownerUserId)
    }

    override fun deleteConnection(id: UUID, ownerUserId: UUID): ConnectionRepository.DeleteResult {
        dataSource.connection.use { conn ->
            // Verify ownership
            val exists = conn.prepareStatement(
                "SELECT 1 FROM connections WHERE id = ? AND owner_user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, ownerUserId)
                stmt.executeQuery().next()
            }
            if (!exists) return ConnectionRepository.DeleteResult.NotFound

            // Check for active nominations
            val hasActiveNominations = conn.prepareStatement(
                """SELECT EXISTS(
                     SELECT 1 FROM executor_nominations
                     WHERE connection_id = ? AND status IN ('pending', 'accepted')
                   )"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().let { rs -> rs.next(); rs.getBoolean(1) }
            }
            if (hasActiveNominations) return ConnectionRepository.DeleteResult.ActiveNominationsExist

            conn.prepareStatement("DELETE FROM connections WHERE id = ? AND owner_user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, ownerUserId)
                stmt.executeUpdate()
            }
            return ConnectionRepository.DeleteResult.Deleted
        }
    }

    override fun lookupSharingPubkey(contactUserId: UUID): String? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT pubkey FROM account_sharing_keys WHERE user_id = ? LIMIT 1"
            ).use { stmt ->
                stmt.setObject(1, contactUserId)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getString("pubkey") else null
            }
        }
    }

    private fun ResultSet.toConnectionRecord() = ConnectionRecord(
        id = getObject("id", UUID::class.java),
        ownerUserId = getObject("owner_user_id", UUID::class.java),
        contactUserId = getObject("contact_user_id", UUID::class.java),
        displayName = getString("display_name"),
        email = getString("email"),
        sharingPubkey = getString("sharing_pubkey"),
        roles = getArray("roles")?.let { arr ->
            @Suppress("UNCHECKED_CAST")
            (arr.array as? Array<*>)?.filterIsInstance<String>() ?: emptyList()
        } ?: emptyList(),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
    )
}
