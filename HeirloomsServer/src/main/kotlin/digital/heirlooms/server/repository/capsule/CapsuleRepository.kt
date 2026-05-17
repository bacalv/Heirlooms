package digital.heirlooms.server.repository.capsule

import digital.heirlooms.server.domain.capsule.CapsuleDetail
import digital.heirlooms.server.domain.capsule.CapsuleRecord
import digital.heirlooms.server.domain.capsule.CapsuleShape
import digital.heirlooms.server.domain.capsule.CapsuleState
import digital.heirlooms.server.domain.capsule.CapsuleSummary
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

interface CapsuleRepository {
    sealed class UpdateResult {
        data class Success(val detail: CapsuleDetail) : UpdateResult()
        object NotFound : UpdateResult()
        object TerminalState : UpdateResult()
        object SealedContents : UpdateResult()
        object UnknownUpload : UpdateResult()
        data class InvalidRecipients(val reason: String) : UpdateResult()
        data class MessageTooLong(val limit: Int) : UpdateResult()
    }
    sealed class SealResult {
        data class Success(val detail: CapsuleDetail) : SealResult()
        object NotFound : SealResult()
        object WrongState : SealResult()
        object Empty : SealResult()
    }
    sealed class CancelResult {
        data class Success(val detail: CapsuleDetail) : CancelResult()
        object NotFound : CancelResult()
        object WrongState : CancelResult()
    }

    fun uploadExists(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean
    fun createCapsule(id: UUID, createdByUser: String, shape: CapsuleShape, state: CapsuleState, unlockAt: OffsetDateTime, recipients: List<String>, uploadIds: List<UUID>, message: String, userId: UUID = FOUNDING_USER_ID): CapsuleDetail
    fun getCapsuleById(id: UUID, userId: UUID = FOUNDING_USER_ID): CapsuleDetail?
    fun listCapsules(states: List<CapsuleState>, orderBy: String, userId: UUID = FOUNDING_USER_ID): List<CapsuleSummary>
    fun updateCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID, unlockAt: OffsetDateTime?, recipients: List<String>?, uploadIds: List<UUID>?, message: String?): UpdateResult
    fun sealCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID): SealResult
    fun cancelCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID): CancelResult
    fun getCapsulesForUpload(uploadId: UUID, userId: UUID = FOUNDING_USER_ID): List<CapsuleSummary>?
}

class PostgresCapsuleRepository(private val dataSource: DataSource) : CapsuleRepository {

    override fun uploadExists(id: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeQuery().next()
            }
        }
    }

    override fun createCapsule(
        id: UUID,
        createdByUser: String,
        shape: CapsuleShape,
        state: CapsuleState,
        unlockAt: OffsetDateTime,
        recipients: List<String>,
        uploadIds: List<UUID>,
        message: String,
        userId: UUID,
    ): CapsuleDetail {
        val now = Instant.now()
        withTransaction { conn ->
            conn.prepareStatement(
                """INSERT INTO capsules (id, created_at, updated_at, created_by_user, shape, state, unlock_at, user_id)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setTimestamp(2, Timestamp.from(now))
                stmt.setTimestamp(3, Timestamp.from(now))
                stmt.setString(4, createdByUser)
                stmt.setString(5, shape.name.lowercase())
                stmt.setString(6, state.name.lowercase())
                stmt.setObject(7, unlockAt)
                stmt.setObject(8, userId)
                stmt.executeUpdate()
            }
            insertRecipients(conn, id, recipients)
            insertContents(conn, id, uploadIds)
            if (message.isNotEmpty()) {
                insertMessage(conn, id, message, 1)
            }
        }
        return getCapsuleById(id, userId)!!
    }

    override fun getCapsuleById(id: UUID, userId: UUID): CapsuleDetail? {
        dataSource.connection.use { conn ->
            val record = conn.prepareStatement(
                """SELECT id, created_at, updated_at, created_by_user, shape, state,
                          unlock_at, cancelled_at, delivered_at,
                          wrapped_capsule_key, capsule_key_format,
                          tlock_round, tlock_chain_id, tlock_wrapped_key, tlock_key_digest,
                          shamir_threshold, shamir_total_shares
                   FROM capsules WHERE id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                rs.toCapsuleRecord()
            }
            val recipients = queryRecipients(conn, id)
            val uploads = queryUploadsForCapsule(conn, id)
            val message = queryCurrentMessage(conn, id)
            return CapsuleDetail(record, recipients, uploads, message)
        }
    }

    override fun listCapsules(states: List<CapsuleState>, orderBy: String, userId: UUID): List<CapsuleSummary> {
        if (states.isEmpty()) return emptyList()
        dataSource.connection.use { conn ->
            val stateArr = conn.createArrayOf("text", states.map { it.name.lowercase() }.toTypedArray())
            val orderCol = if (orderBy == "unlock_at") "c.unlock_at" else "c.updated_at"
            conn.prepareStatement(
                """SELECT c.id, c.created_at, c.updated_at, c.created_by_user, c.shape, c.state,
                          c.unlock_at, c.cancelled_at, c.delivered_at,
                          (SELECT COUNT(*) FROM capsule_contents cc WHERE cc.capsule_id = c.id) AS upload_count,
                          (SELECT EXISTS(SELECT 1 FROM capsule_messages cm WHERE cm.capsule_id = c.id)) AS has_message
                   FROM capsules c
                   WHERE c.state = ANY(?) AND c.user_id = ?
                   ORDER BY $orderCol DESC"""
            ).use { stmt ->
                stmt.setArray(1, stateArr)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<CapsuleSummary>()
                while (rs.next()) {
                    val record = rs.toCapsuleRecord()
                    val uploadCount = rs.getInt("upload_count")
                    val hasMessage = rs.getBoolean("has_message")
                    val recipients = queryRecipients(conn, record.id)
                    results.add(CapsuleSummary(record, recipients, uploadCount, hasMessage))
                }
                return results
            }
        }
    }

    override fun updateCapsule(
        id: UUID,
        userId: UUID,
        unlockAt: OffsetDateTime?,
        recipients: List<String>?,
        uploadIds: List<UUID>?,
        message: String?,
    ): CapsuleRepository.UpdateResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return CapsuleRepository.UpdateResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.state == CapsuleState.DELIVERED || record.state == CapsuleState.CANCELLED) {
                return CapsuleRepository.UpdateResult.TerminalState
            }
            if (uploadIds != null && record.state == CapsuleState.SEALED) {
                return CapsuleRepository.UpdateResult.SealedContents
            }
            if (uploadIds != null) {
                for (uid in uploadIds) {
                    val exists = conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ? AND user_id = ?").use { stmt ->
                        stmt.setObject(1, uid)
                        stmt.setObject(2, userId)
                        stmt.executeQuery().next()
                    }
                    if (!exists) return CapsuleRepository.UpdateResult.UnknownUpload
                }
            }

            val setClauses = mutableListOf("updated_at = ?")
            if (unlockAt != null) setClauses.add("unlock_at = ?")

            conn.prepareStatement("UPDATE capsules SET ${setClauses.joinToString(", ")} WHERE id = ? AND user_id = ?").use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (unlockAt != null) stmt.setObject(idx++, unlockAt)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, userId)
                stmt.executeUpdate()
            }

            if (recipients != null) {
                conn.prepareStatement("DELETE FROM capsule_recipients WHERE capsule_id = ?").use { stmt ->
                    stmt.setObject(1, id)
                    stmt.executeUpdate()
                }
                insertRecipients(conn, id, recipients)
            }

            if (uploadIds != null) {
                conn.prepareStatement("DELETE FROM capsule_contents WHERE capsule_id = ?").use { stmt ->
                    stmt.setObject(1, id)
                    stmt.executeUpdate()
                }
                insertContents(conn, id, uploadIds)
            }

            if (message != null) {
                val currentVersion = conn.prepareStatement(
                    "SELECT COALESCE(MAX(version), 0) FROM capsule_messages WHERE capsule_id = ?"
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.executeQuery().let { rs -> rs.next(); rs.getInt(1) }
                }
                val currentBody = if (currentVersion > 0) {
                    conn.prepareStatement(
                        "SELECT body FROM capsule_messages WHERE capsule_id = ? AND version = ?"
                    ).use { stmt ->
                        stmt.setObject(1, id)
                        stmt.setInt(2, currentVersion)
                        val rs = stmt.executeQuery()
                        if (rs.next()) rs.getString("body") else null
                    }
                } else null

                if (message != currentBody) {
                    insertMessage(conn, id, message, currentVersion + 1)
                }
            }
        }
        return getCapsuleById(id, userId)?.let { CapsuleRepository.UpdateResult.Success(it) } ?: CapsuleRepository.UpdateResult.NotFound
    }

    override fun sealCapsule(id: UUID, userId: UUID): CapsuleRepository.SealResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return CapsuleRepository.SealResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.shape != CapsuleShape.OPEN || record.state != CapsuleState.OPEN) {
                return CapsuleRepository.SealResult.WrongState
            }

            val hasContents = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM capsule_contents WHERE capsule_id = ?)"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().let { rs -> rs.next(); rs.getBoolean(1) }
            }

            if (!hasContents) return CapsuleRepository.SealResult.Empty

            conn.prepareStatement(
                "UPDATE capsules SET state = 'sealed', updated_at = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(Instant.now()))
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
        return getCapsuleById(id, userId)?.let { CapsuleRepository.SealResult.Success(it) } ?: CapsuleRepository.SealResult.NotFound
    }

    override fun cancelCapsule(id: UUID, userId: UUID): CapsuleRepository.CancelResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return CapsuleRepository.CancelResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.state != CapsuleState.OPEN && record.state != CapsuleState.SEALED) {
                return CapsuleRepository.CancelResult.WrongState
            }

            val now = Timestamp.from(Instant.now())
            conn.prepareStatement(
                "UPDATE capsules SET state = 'cancelled', cancelled_at = ?, updated_at = ? WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setTimestamp(1, now)
                stmt.setTimestamp(2, now)
                stmt.setObject(3, id)
                stmt.setObject(4, userId)
                stmt.executeUpdate()
            }
        }
        return getCapsuleById(id, userId)?.let { CapsuleRepository.CancelResult.Success(it) } ?: CapsuleRepository.CancelResult.NotFound
    }

    override fun getCapsulesForUpload(uploadId: UUID, userId: UUID): List<CapsuleSummary>? {
        dataSource.connection.use { conn ->
            val uploadExists = conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, uploadId)
                stmt.setObject(2, userId)
                stmt.executeQuery().next()
            }
            if (!uploadExists) return null

            conn.prepareStatement(
                """SELECT c.id, c.created_at, c.updated_at, c.created_by_user, c.shape, c.state,
                          c.unlock_at, c.cancelled_at, c.delivered_at
                   FROM capsules c
                   JOIN capsule_contents cc ON c.id = cc.capsule_id
                   WHERE cc.upload_id = ? AND c.state IN ('open', 'sealed')"""
            ).use { stmt ->
                stmt.setObject(1, uploadId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<CapsuleSummary>()
                while (rs.next()) {
                    val record = rs.toCapsuleRecord()
                    val uploadCount = queryUploadCount(conn, record.id)
                    val hasMessage = queryHasMessage(conn, record.id)
                    val recipients = queryRecipients(conn, record.id)
                    results.add(CapsuleSummary(record, recipients, uploadCount, hasMessage))
                }
                return results
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun insertRecipients(conn: Connection, capsuleId: UUID, recipients: List<String>) {
        val now = Timestamp.from(Instant.now())
        for (recipient in recipients) {
            conn.prepareStatement(
                "INSERT INTO capsule_recipients (id, capsule_id, recipient, added_at) VALUES (?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, capsuleId)
                stmt.setString(3, recipient)
                stmt.setTimestamp(4, now)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertContents(conn: Connection, capsuleId: UUID, uploadIds: List<UUID>) {
        val now = Timestamp.from(Instant.now())
        for (uploadId in uploadIds) {
            conn.prepareStatement(
                "INSERT INTO capsule_contents (capsule_id, upload_id, added_at) VALUES (?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, capsuleId)
                stmt.setObject(2, uploadId)
                stmt.setTimestamp(3, now)
                stmt.executeUpdate()
            }
        }
    }

    private fun insertMessage(conn: Connection, capsuleId: UUID, body: String, version: Int) {
        conn.prepareStatement(
            "INSERT INTO capsule_messages (id, capsule_id, body, version, created_at) VALUES (?, ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, capsuleId)
            stmt.setString(3, body)
            stmt.setInt(4, version)
            stmt.setTimestamp(5, Timestamp.from(Instant.now()))
            stmt.executeUpdate()
        }
    }

    private fun queryRecipients(conn: Connection, capsuleId: UUID): List<String> =
        conn.prepareStatement(
            "SELECT recipient FROM capsule_recipients WHERE capsule_id = ? ORDER BY added_at"
        ).use { stmt ->
            stmt.setObject(1, capsuleId)
            val rs = stmt.executeQuery()
            val list = mutableListOf<String>()
            while (rs.next()) list.add(rs.getString("recipient"))
            list
        }

    private fun queryUploadsForCapsule(conn: Connection, capsuleId: UUID): List<UploadRecord> =
        conn.prepareStatement(
            """SELECT u.id, u.storage_key, u.mime_type, u.file_size, u.uploaded_at, u.content_hash,
                      u.thumbnail_key, u.taken_at, u.latitude, u.longitude, u.altitude,
                      u.device_make, u.device_model, u.rotation, u.tags, u.composted_at,
                      u.exif_processed_at, u.last_viewed_at,
                      u.storage_class, u.envelope_version, u.wrapped_dek, u.dek_format,
                      u.encrypted_metadata, u.encrypted_metadata_format, u.thumbnail_storage_key,
                      u.wrapped_thumbnail_dek, u.thumbnail_dek_format, u.preview_storage_key,
                      u.wrapped_preview_dek, u.preview_dek_format, u.plain_chunk_size, u.duration_seconds
               FROM uploads u
               JOIN capsule_contents cc ON u.id = cc.upload_id
               WHERE cc.capsule_id = ?
               ORDER BY cc.added_at"""
        ).use { stmt ->
            stmt.setObject(1, capsuleId)
            val rs = stmt.executeQuery()
            val list = mutableListOf<UploadRecord>()
            while (rs.next()) list.add(rs.toUploadRecord())
            list
        }

    private fun queryCurrentMessage(conn: Connection, capsuleId: UUID): String =
        conn.prepareStatement(
            "SELECT body FROM capsule_messages WHERE capsule_id = ? ORDER BY version DESC LIMIT 1"
        ).use { stmt ->
            stmt.setObject(1, capsuleId)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getString("body") else ""
        }

    private fun queryUploadCount(conn: Connection, capsuleId: UUID): Int =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM capsule_contents WHERE capsule_id = ?"
        ).use { stmt ->
            stmt.setObject(1, capsuleId)
            stmt.executeQuery().let { rs -> rs.next(); rs.getInt(1) }
        }

    private fun queryHasMessage(conn: Connection, capsuleId: UUID): Boolean =
        conn.prepareStatement(
            "SELECT EXISTS(SELECT 1 FROM capsule_messages WHERE capsule_id = ?)"
        ).use { stmt ->
            stmt.setObject(1, capsuleId)
            stmt.executeQuery().let { rs -> rs.next(); rs.getBoolean(1) }
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

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ResultSet.toCapsuleRecord() = CapsuleRecord(
        id = getObject("id", UUID::class.java),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
        createdByUser = getString("created_by_user"),
        shape = CapsuleShape.valueOf(getString("shape").uppercase()),
        state = CapsuleState.valueOf(getString("state").uppercase()),
        unlockAt = getObject("unlock_at", OffsetDateTime::class.java),
        cancelledAt = getTimestamp("cancelled_at")?.toInstant(),
        deliveredAt = getTimestamp("delivered_at")?.toInstant(),
        // M11 fields — may be null if the column does not exist in the ResultSet
        // (e.g. for list queries that do not select them)
        wrappedCapsuleKey  = tryGetBytes("wrapped_capsule_key"),
        capsuleKeyFormat   = tryGetString("capsule_key_format"),
        tlockRound         = tryGetLong("tlock_round"),
        tlockChainId       = tryGetString("tlock_chain_id"),
        tlockWrappedKey    = tryGetBytes("tlock_wrapped_key"),
        tlockKeyDigest     = tryGetBytes("tlock_key_digest"),
        shamirThreshold    = tryGetInt("shamir_threshold"),
        shamirTotalShares  = tryGetInt("shamir_total_shares"),
    )

    /** Helper: returns null if the column is not in the ResultSet or is SQL NULL. */
    private fun ResultSet.tryGetBytes(col: String): ByteArray? =
        try { getBytes(col) } catch (_: Exception) { null }

    private fun ResultSet.tryGetString(col: String): String? =
        try { getString(col) } catch (_: Exception) { null }

    private fun ResultSet.tryGetLong(col: String): Long? =
        try { getLong(col).takeUnless { wasNull() } } catch (_: Exception) { null }

    private fun ResultSet.tryGetInt(col: String): Int? =
        try { getInt(col).takeUnless { wasNull() } } catch (_: Exception) { null }

    private fun ResultSet.toUploadRecord() = UploadRecord(
        id = getObject("id", UUID::class.java),
        storageKey = getString("storage_key"),
        mimeType = getString("mime_type"),
        fileSize = getLong("file_size"),
        uploadedAt = getTimestamp("uploaded_at").toInstant(),
        contentHash = getString("content_hash"),
        thumbnailKey = getString("thumbnail_key"),
        takenAt = getTimestamp("taken_at")?.toInstant(),
        latitude = getDouble("latitude").takeUnless { wasNull() },
        longitude = getDouble("longitude").takeUnless { wasNull() },
        altitude = getDouble("altitude").takeUnless { wasNull() },
        deviceMake = getString("device_make"),
        deviceModel = getString("device_model"),
        rotation = getInt("rotation"),
        tags = getArray("tags")?.let { arr -> (arr.array as? Array<*>)?.filterIsInstance<String>() } ?: emptyList(),
        compostedAt = getTimestamp("composted_at")?.toInstant(),
        exifProcessedAt = try { getTimestamp("exif_processed_at")?.toInstant() } catch (_: Exception) { null },
        lastViewedAt = try { getTimestamp("last_viewed_at")?.toInstant() } catch (_: Exception) { null },
        storageClass = try { getString("storage_class") ?: "public" } catch (_: Exception) { "public" },
        envelopeVersion = try { getObject("envelope_version") as? Int } catch (_: Exception) { null },
        wrappedDek = try { getBytes("wrapped_dek") } catch (_: Exception) { null },
        dekFormat = try { getString("dek_format") } catch (_: Exception) { null },
        encryptedMetadata = try { getBytes("encrypted_metadata") } catch (_: Exception) { null },
        encryptedMetadataFormat = try { getString("encrypted_metadata_format") } catch (_: Exception) { null },
        thumbnailStorageKey = try { getString("thumbnail_storage_key") } catch (_: Exception) { null },
        wrappedThumbnailDek = try { getBytes("wrapped_thumbnail_dek") } catch (_: Exception) { null },
        thumbnailDekFormat = try { getString("thumbnail_dek_format") } catch (_: Exception) { null },
        previewStorageKey = try { getString("preview_storage_key") } catch (_: Exception) { null },
        wrappedPreviewDek = try { getBytes("wrapped_preview_dek") } catch (_: Exception) { null },
        previewDekFormat = try { getString("preview_dek_format") } catch (_: Exception) { null },
        plainChunkSize = try { getObject("plain_chunk_size") as? Int } catch (_: Exception) { null },
        durationSeconds = try { getObject("duration_seconds") as? Int } catch (_: Exception) { null },
        sharedFromUploadId = try { getObject("shared_from_upload_id", UUID::class.java) } catch (_: Exception) { null },
        sharedFromUserId = try { getObject("shared_from_user_id", UUID::class.java) } catch (_: Exception) { null },
    )
}
