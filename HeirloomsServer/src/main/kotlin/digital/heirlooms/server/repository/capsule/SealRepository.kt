package digital.heirlooms.server.repository.capsule

import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Repository for the M11 /seal endpoint (DEV-005).
 *
 * Responsibilities:
 * - Load a capsule row for seal validation (shape, unlock_at).
 * - Check whether a connection is bound and belongs to the caller.
 * - Count accepted executor_nominations for the caller.
 * - Write all crypto columns + shape='sealed' + sealed_at atomically.
 *
 * All validation is performed in the service layer before any write.
 * This repository only performs DB operations.
 */
interface SealRepository {

    /**
     * Load minimal capsule fields needed for seal validation.
     * Returns null if the capsule does not exist or does not belong to [ownerUserId].
     */
    fun loadCapsuleForSeal(capsuleId: UUID, ownerUserId: UUID): CapsuleForSeal?

    /**
     * Check whether [connectionId] is a bound connection (contact_user_id IS NOT NULL)
     * and belongs to [ownerUserId].
     *
     * Returns true if the connection exists, belongs to the caller, and has a non-NULL
     * contact_user_id. Returns false otherwise.
     */
    fun isConnectionBoundAndOwned(connectionId: UUID, ownerUserId: UUID): Boolean

    /**
     * Count executor_nominations rows where status='accepted' and owner_user_id=[ownerUserId].
     */
    fun countAcceptedNominations(ownerUserId: UUID): Int

    /**
     * Write all crypto columns atomically in a single transaction:
     *   - capsules.wrapped_capsule_key, capsule_key_format
     *   - tlock columns (if [params].tlock != null)
     *   - shamir columns (if [params].shamir != null)
     *   - capsule_recipient_keys rows (upsert)
     *   - capsules.shape = 'sealed', capsules.sealed_at = now()
     *
     * Throws on any DB error; the caller wraps in HTTP 500.
     * SECURITY: tlock_dek_tlock must never be logged by this method.
     */
    fun writeSealAtomically(capsuleId: UUID, ownerUserId: UUID, params: SealWriteParams): Instant

    // ─── Data classes ──────────────────────────────────────────────────────────

    data class CapsuleForSeal(
        val id: UUID,
        val shape: String,
        val unlockAt: OffsetDateTime,
    )

    data class RecipientKeyEntry(
        val connectionId: UUID,
        /** base64url-encoded asymmetric envelope (capsule-ecdh-aes256gcm-v1). */
        val wrappedCapsuleKey: ByteArray,
        val capsuleKeyFormat: String,
        /** Non-null only for tlock capsules. base64url-encoded asymmetric envelope. */
        val wrappedBlindingMask: ByteArray?,
    )

    data class TlockWriteParams(
        val round: Long,
        val chainId: String,
        /** SECURITY: MUST NOT be logged at any level. */
        val wrappedKey: ByteArray,
        /** SECURITY: MUST NOT be logged at any level. */
        val dekTlock: ByteArray,
        val keyDigest: ByteArray,
    )

    data class ShamirWriteParams(
        val threshold: Int,
        val totalShares: Int,
    )

    data class SealWriteParams(
        val recipientKeys: List<RecipientKeyEntry>,
        val tlock: TlockWriteParams?,
        val shamir: ShamirWriteParams?,
    )
}

class PostgresSealRepository(private val dataSource: DataSource) : SealRepository {

    override fun loadCapsuleForSeal(capsuleId: UUID, ownerUserId: UUID): SealRepository.CapsuleForSeal? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, shape, unlock_at FROM capsules WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, ownerUserId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return SealRepository.CapsuleForSeal(
                    id       = rs.getObject("id", UUID::class.java),
                    shape    = rs.getString("shape"),
                    unlockAt = rs.getObject("unlock_at", OffsetDateTime::class.java),
                )
            }
        }
    }

    override fun isConnectionBoundAndOwned(connectionId: UUID, ownerUserId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT EXISTS(
                     SELECT 1 FROM connections
                     WHERE id = ? AND owner_user_id = ? AND contact_user_id IS NOT NULL
                   )"""
            ).use { stmt ->
                stmt.setObject(1, connectionId)
                stmt.setObject(2, ownerUserId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getBoolean(1)
            }
        }
    }

    override fun countAcceptedNominations(ownerUserId: UUID): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT COUNT(*) FROM executor_nominations
                   WHERE owner_user_id = ? AND status = 'accepted'"""
            ).use { stmt ->
                stmt.setObject(1, ownerUserId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    override fun writeSealAtomically(
        capsuleId: UUID,
        ownerUserId: UUID,
        params: SealRepository.SealWriteParams,
    ): Instant {
        val sealedAt = Instant.now()
        val sealedAtTs = Timestamp.from(sealedAt)

        val conn = dataSource.connection
        conn.autoCommit = false
        try {
            // (a)+(b)+(c) — update capsules row
            val setClauses = mutableListOf(
                "wrapped_capsule_key = ?",
                "capsule_key_format = ?",
                "shape = 'sealed'",
                "state = 'sealed'",
                "sealed_at = ?",
                "updated_at = ?",
            )
            val tlock = params.tlock
            if (tlock != null) {
                setClauses += "tlock_round = ?"
                setClauses += "tlock_chain_id = ?"
                setClauses += "tlock_wrapped_key = ?"
                setClauses += "tlock_dek_tlock = ?"     // SECURITY: value not logged
                setClauses += "tlock_key_digest = ?"
            }
            val shamir = params.shamir
            if (shamir != null) {
                setClauses += "shamir_threshold = ?"
                setClauses += "shamir_total_shares = ?"
            }

            // Primary recipient is the first entry in recipientKeys
            val primaryKey = params.recipientKeys.first()

            conn.prepareStatement(
                "UPDATE capsules SET ${setClauses.joinToString(", ")} WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                var idx = 1
                stmt.setBytes(idx++, primaryKey.wrappedCapsuleKey)
                stmt.setString(idx++, primaryKey.capsuleKeyFormat)
                stmt.setTimestamp(idx++, sealedAtTs)
                stmt.setTimestamp(idx++, sealedAtTs)

                if (tlock != null) {
                    stmt.setLong(idx++, tlock.round)
                    stmt.setString(idx++, tlock.chainId)
                    stmt.setBytes(idx++, tlock.wrappedKey)
                    stmt.setBytes(idx++, tlock.dekTlock)   // SECURITY: not logged
                    stmt.setBytes(idx++, tlock.keyDigest)
                }
                if (shamir != null) {
                    stmt.setShort(idx++, shamir.threshold.toShort())
                    stmt.setShort(idx++, shamir.totalShares.toShort())
                }

                stmt.setObject(idx++, capsuleId)
                stmt.setObject(idx, ownerUserId)
                stmt.executeUpdate()
            }

            // (d) — upsert capsule_recipient_keys for each entry
            conn.prepareStatement(
                """INSERT INTO capsule_recipient_keys
                     (capsule_id, connection_id, wrapped_capsule_key, capsule_key_format, wrapped_blinding_mask, created_at)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (capsule_id, connection_id) DO UPDATE
                     SET wrapped_capsule_key   = EXCLUDED.wrapped_capsule_key,
                         capsule_key_format    = EXCLUDED.capsule_key_format,
                         wrapped_blinding_mask = EXCLUDED.wrapped_blinding_mask"""
            ).use { stmt ->
                val now = Timestamp.from(sealedAt)
                for (entry in params.recipientKeys) {
                    stmt.setObject(1, capsuleId)
                    stmt.setObject(2, entry.connectionId)
                    stmt.setBytes(3, entry.wrappedCapsuleKey)
                    stmt.setString(4, entry.capsuleKeyFormat)
                    stmt.setBytes(5, entry.wrappedBlindingMask)   // null for non-tlock
                    stmt.setTimestamp(6, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            conn.commit()
        } catch (e: Exception) {
            try { conn.rollback() } catch (_: Exception) {}
            throw e
        } finally {
            conn.autoCommit = true
            conn.close()
        }

        return sealedAt
    }
}
