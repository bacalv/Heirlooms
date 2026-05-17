package digital.heirlooms.server.repository.capsule

import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Repository for the GET /api/capsules/:id/tlock-key gate logic (DEV-006 Wave 6).
 *
 * Responsibilities:
 * - Check whether a caller is an authenticated recipient (present in capsule_recipient_keys).
 * - Load the tlock fields from a sealed capsule.
 * - Load the capsule owner's user ID (for access control on other endpoints).
 *
 * SECURITY: tlock_dek_tlock is read from the DB and returned as raw bytes.
 * The caller (service layer) MUST NOT log this value at any level.
 */
interface TlockKeyRepository {

    /**
     * Returns true if [callerUserId] is a recipient of [capsuleId] — i.e., there is
     * a row in capsule_recipient_keys where the connection's contact_user_id matches
     * the caller.
     */
    fun isRecipient(capsuleId: UUID, callerUserId: UUID): Boolean

    /**
     * Load the tlock fields for a sealed capsule with non-null tlock fields.
     * Returns null if the capsule does not exist, is not sealed, or has null tlock fields.
     *
     * SECURITY: tlock_dek_tlock is included in the returned record and MUST NOT be logged.
     */
    fun loadTlockFields(capsuleId: UUID): TlockCapsuleFields?

    /**
     * Returns the owner user_id of the capsule, or null if the capsule does not exist.
     */
    fun getCapsuleOwnerId(capsuleId: UUID): UUID?

    // ─── Data classes ──────────────────────────────────────────────────────────

    data class TlockCapsuleFields(
        val capsuleId: UUID,
        val unlockAt: OffsetDateTime,
        /** SECURITY: MUST NOT be logged at any level. */
        val dekTlock: ByteArray,
        val keyDigest: ByteArray,
        val chainId: String,
        val round: Long,
        val wrappedKey: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is TlockCapsuleFields &&
                capsuleId == other.capsuleId &&
                unlockAt == other.unlockAt &&
                dekTlock.contentEquals(other.dekTlock) &&
                keyDigest.contentEquals(other.keyDigest) &&
                chainId == other.chainId &&
                round == other.round &&
                wrappedKey.contentEquals(other.wrappedKey)

        override fun hashCode(): Int = capsuleId.hashCode()
    }
}

class PostgresTlockKeyRepository(private val dataSource: DataSource) : TlockKeyRepository {

    override fun isRecipient(capsuleId: UUID, callerUserId: UUID): Boolean {
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

    override fun loadTlockFields(capsuleId: UUID): TlockKeyRepository.TlockCapsuleFields? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, unlock_at,
                          tlock_dek_tlock, tlock_key_digest,
                          tlock_chain_id, tlock_round, tlock_wrapped_key
                   FROM capsules
                   WHERE id = ?
                     AND shape = 'sealed'
                     AND tlock_dek_tlock IS NOT NULL"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return TlockKeyRepository.TlockCapsuleFields(
                    capsuleId   = rs.getObject("id", UUID::class.java),
                    unlockAt    = rs.getObject("unlock_at", java.time.OffsetDateTime::class.java),
                    dekTlock    = rs.getBytes("tlock_dek_tlock"),   // SECURITY: not logged
                    keyDigest   = rs.getBytes("tlock_key_digest"),
                    chainId     = rs.getString("tlock_chain_id"),
                    round       = rs.getLong("tlock_round"),
                    wrappedKey  = rs.getBytes("tlock_wrapped_key"),
                )
            }
        }
    }

    override fun getCapsuleOwnerId(capsuleId: UUID): UUID? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT user_id FROM capsules WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.getObject("user_id", UUID::class.java)
            }
        }
    }
}
