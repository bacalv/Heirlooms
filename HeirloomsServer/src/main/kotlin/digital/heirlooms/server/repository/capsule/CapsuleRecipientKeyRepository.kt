package digital.heirlooms.server.repository.capsule

import java.util.UUID
import javax.sql.DataSource

/**
 * Repository for GET /api/capsule-recipient-keys/:capsuleId (DEV-006 Wave 7).
 *
 * Returns capsule_recipient_keys rows for a capsule, scoped by caller role:
 * - Owner: all rows.
 * - Authenticated recipient: only their own row.
 */
interface CapsuleRecipientKeyRepository {

    /**
     * Returns true if [callerUserId] is the owner of [capsuleId].
     */
    fun isCapsuleOwner(capsuleId: UUID, callerUserId: UUID): Boolean

    /**
     * Returns true if [callerUserId] is an authenticated recipient of [capsuleId].
     * (present in capsule_recipient_keys with a matching connection contact_user_id)
     */
    fun isAuthenticatedRecipient(capsuleId: UUID, callerUserId: UUID): Boolean

    /**
     * Returns true if [capsuleId] exists (any state, any owner).
     */
    fun capsuleExists(capsuleId: UUID): Boolean

    /**
     * Returns all capsule_recipient_keys rows for [capsuleId].
     * Called when the caller is the owner.
     */
    fun findAllRows(capsuleId: UUID): List<RecipientKeyRow>

    /**
     * Returns only the recipient key row belonging to [callerUserId] for [capsuleId].
     * Called when the caller is an authenticated recipient (not the owner).
     */
    fun findOwnRow(capsuleId: UUID, callerUserId: UUID): RecipientKeyRow?

    // ─── Data ──────────────────────────────────────────────────────────────────

    data class RecipientKeyRow(
        val connectionId: UUID,
        /** base64url-encoded asymmetric envelope */
        val wrappedCapsuleKey: ByteArray,
        /** base64url-encoded asymmetric envelope, null on non-tlock capsules */
        val wrappedBlindingMask: ByteArray?,
    ) {
        override fun equals(other: Any?) =
            other is RecipientKeyRow && connectionId == other.connectionId
        override fun hashCode() = connectionId.hashCode()
    }
}

class PostgresCapsuleRecipientKeyRepository(private val dataSource: DataSource) : CapsuleRecipientKeyRepository {

    override fun isCapsuleOwner(capsuleId: UUID, callerUserId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM capsules WHERE id = ? AND user_id = ?)"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, callerUserId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getBoolean(1)
            }
        }
    }

    override fun isAuthenticatedRecipient(capsuleId: UUID, callerUserId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT EXISTS(
                     SELECT 1
                     FROM capsule_recipient_keys crk
                     JOIN connections c ON c.id = crk.connection_id
                     WHERE crk.capsule_id = ?
                       AND c.contact_user_id = ?
                   )"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, callerUserId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getBoolean(1)
            }
        }
    }

    override fun capsuleExists(capsuleId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM capsules WHERE id = ?)"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getBoolean(1)
            }
        }
    }

    override fun findAllRows(capsuleId: UUID): List<CapsuleRecipientKeyRepository.RecipientKeyRow> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT connection_id, wrapped_capsule_key, wrapped_blinding_mask
                   FROM capsule_recipient_keys
                   WHERE capsule_id = ?
                   ORDER BY created_at"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                val rs = stmt.executeQuery()
                val rows = mutableListOf<CapsuleRecipientKeyRepository.RecipientKeyRow>()
                while (rs.next()) {
                    rows.add(
                        CapsuleRecipientKeyRepository.RecipientKeyRow(
                            connectionId      = rs.getObject("connection_id", UUID::class.java),
                            wrappedCapsuleKey = rs.getBytes("wrapped_capsule_key"),
                            wrappedBlindingMask = rs.getBytes("wrapped_blinding_mask"),
                        )
                    )
                }
                return rows
            }
        }
    }

    override fun findOwnRow(capsuleId: UUID, callerUserId: UUID): CapsuleRecipientKeyRepository.RecipientKeyRow? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT crk.connection_id, crk.wrapped_capsule_key, crk.wrapped_blinding_mask
                   FROM capsule_recipient_keys crk
                   JOIN connections c ON c.id = crk.connection_id
                   WHERE crk.capsule_id = ?
                     AND c.contact_user_id = ?
                   LIMIT 1"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, callerUserId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return CapsuleRecipientKeyRepository.RecipientKeyRow(
                    connectionId      = rs.getObject("connection_id", UUID::class.java),
                    wrappedCapsuleKey = rs.getBytes("wrapped_capsule_key"),
                    wrappedBlindingMask = rs.getBytes("wrapped_blinding_mask"),
                )
            }
        }
    }
}
