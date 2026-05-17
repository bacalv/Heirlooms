package digital.heirlooms.server.repository.capsule

import digital.heirlooms.server.domain.capsule.ExecutorShareRecord
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

interface ExecutorShareRepository {

    /**
     * Fetch the capsule row for share-upload validation.
     * Returns null if the capsule does not exist or does not belong to [ownerUserId].
     */
    fun getCapsuleShareConfig(
        capsuleId: UUID,
        ownerUserId: UUID,
    ): CapsuleShareConfig?

    /**
     * Verify that [nominationId] references an accepted executor_nominations row
     * whose owner_user_id matches [ownerUserId].
     */
    fun isAcceptedNominationForOwner(nominationId: UUID, ownerUserId: UUID): Boolean

    /**
     * Insert all shares in a single transaction. Caller must have validated all
     * shares before calling this method — it performs no re-validation.
     */
    fun insertSharesBatch(capsuleId: UUID, shares: List<ShareRow>)

    /**
     * Fetch the wrapped share for the calling executor on this capsule.
     * Joins executor_shares → executor_nominations → connections to find the share
     * belonging to [callerUserId].
     *
     * Returns null if no share exists yet; returns [MineResult.Forbidden] sentinel
     * if the caller is not an accepted executor.
     */
    fun findShareForExecutor(capsuleId: UUID, callerUserId: UUID): MineQueryResult

    /**
     * Fetch all shares for a capsule (for the /collect endpoint).
     * Returns empty list if no shares have been submitted yet.
     */
    fun findAllShares(capsuleId: UUID): List<ExecutorShareRecord>

    /**
     * Return the shamir_threshold and shamir_total_shares for a capsule.
     * Returns null if the capsule doesn't exist.
     */
    fun getCapsuleShamirConfig(capsuleId: UUID): ShamirConfig?

    data class CapsuleShareConfig(
        val capsuleId: UUID,
        val shape: String,
        val shamirTotalShares: Int?,
    )

    data class ShareRow(
        val nominationId: UUID,
        val shareIndex: Int,
        val wrappedShare: String,
        val shareFormat: String,
    )

    data class ShamirConfig(
        val threshold: Int?,
        val totalShares: Int?,
    )

    sealed class MineQueryResult {
        /** Caller is an accepted executor and the share exists. */
        data class Found(val share: ExecutorShareRecord) : MineQueryResult()
        /** Caller is an accepted executor but no share row exists yet. */
        object NotYetDistributed : MineQueryResult()
        /** Caller is not an accepted executor for this capsule. */
        object NotAnExecutor : MineQueryResult()
    }
}

class PostgresExecutorShareRepository(private val dataSource: DataSource) : ExecutorShareRepository {

    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    override fun getCapsuleShareConfig(
        capsuleId: UUID,
        ownerUserId: UUID,
    ): ExecutorShareRepository.CapsuleShareConfig? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, shape, shamir_total_shares
                   FROM capsules
                   WHERE id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, ownerUserId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                val totalSharesShort = rs.getShort("shamir_total_shares")
                val totalShares = if (rs.wasNull()) null else totalSharesShort.toInt()
                return ExecutorShareRepository.CapsuleShareConfig(
                    capsuleId = rs.getObject("id", UUID::class.java),
                    shape = rs.getString("shape"),
                    shamirTotalShares = totalShares,
                )
            }
        }
    }

    override fun isAcceptedNominationForOwner(nominationId: UUID, ownerUserId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT EXISTS(
                     SELECT 1 FROM executor_nominations
                     WHERE id = ? AND owner_user_id = ? AND status = 'accepted'
                   )"""
            ).use { stmt ->
                stmt.setObject(1, nominationId)
                stmt.setObject(2, ownerUserId)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getBoolean(1)
            }
        }
    }

    override fun insertSharesBatch(capsuleId: UUID, shares: List<ExecutorShareRepository.ShareRow>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """INSERT INTO executor_shares
                         (capsule_id, nomination_id, share_index, wrapped_share, share_format, distributed_at)
                       VALUES (?, ?, ?, ?, ?, ?)"""
                ).use { stmt ->
                    val now = Timestamp.from(Instant.now())
                    for (share in shares) {
                        stmt.setObject(1, capsuleId)
                        stmt.setObject(2, share.nominationId)
                        stmt.setShort(3, share.shareIndex.toShort())
                        stmt.setBytes(4, urlDecoder.decode(share.wrappedShare))
                        stmt.setString(5, share.shareFormat)
                        stmt.setTimestamp(6, now)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override fun findShareForExecutor(capsuleId: UUID, callerUserId: UUID): ExecutorShareRepository.MineQueryResult {
        dataSource.connection.use { conn ->
            // Check if the caller is an accepted executor for this capsule
            val isExecutor = conn.prepareStatement(
                """SELECT EXISTS(
                     SELECT 1
                     FROM executor_nominations en
                     JOIN connections c ON c.id = en.connection_id
                     WHERE en.owner_user_id = (SELECT user_id FROM capsules WHERE id = ?)
                       AND c.contact_user_id = ?
                       AND en.status = 'accepted'
                   )"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, callerUserId)
                val rs = stmt.executeQuery()
                rs.next()
                rs.getBoolean(1)
            }

            if (!isExecutor) return ExecutorShareRepository.MineQueryResult.NotAnExecutor

            // Now look for the actual share — consume ResultSet inside use block to avoid closed RS
            return conn.prepareStatement(
                """SELECT es.id, es.capsule_id, es.nomination_id, es.share_index,
                          es.wrapped_share, es.share_format, es.distributed_at
                   FROM executor_shares es
                   JOIN executor_nominations en ON en.id = es.nomination_id
                   JOIN connections c ON c.id = en.connection_id
                   WHERE es.capsule_id = ?
                     AND c.contact_user_id = ?
                     AND en.status = 'accepted'
                   LIMIT 1"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, callerUserId)
                val rs = stmt.executeQuery()
                if (!rs.next()) {
                    ExecutorShareRepository.MineQueryResult.NotYetDistributed
                } else {
                    ExecutorShareRepository.MineQueryResult.Found(rs.toShareRecord())
                }
            }
        }
    }

    override fun findAllShares(capsuleId: UUID): List<ExecutorShareRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, capsule_id, nomination_id, share_index,
                          wrapped_share, share_format, distributed_at
                   FROM executor_shares
                   WHERE capsule_id = ?
                   ORDER BY share_index"""
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                val rs = stmt.executeQuery()
                val list = mutableListOf<ExecutorShareRecord>()
                while (rs.next()) list.add(rs.toShareRecord())
                return list
            }
        }
    }

    override fun getCapsuleShamirConfig(capsuleId: UUID): ExecutorShareRepository.ShamirConfig? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT shamir_threshold, shamir_total_shares FROM capsules WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                val thresholdShort = rs.getShort("shamir_threshold")
                val threshold = if (rs.wasNull()) null else thresholdShort.toInt()
                val totalShort = rs.getShort("shamir_total_shares")
                val total = if (rs.wasNull()) null else totalShort.toInt()
                return ExecutorShareRepository.ShamirConfig(threshold, total)
            }
        }
    }

    private fun ResultSet.toShareRecord() = ExecutorShareRecord(
        id = getObject("id", UUID::class.java),
        capsuleId = getObject("capsule_id", UUID::class.java),
        nominationId = getObject("nomination_id", UUID::class.java),
        shareIndex = getShort("share_index").toInt(),
        wrappedShare = urlEncoder.encodeToString(getBytes("wrapped_share")),
        shareFormat = getString("share_format"),
        distributedAt = getTimestamp("distributed_at").toInstant(),
    )
}
