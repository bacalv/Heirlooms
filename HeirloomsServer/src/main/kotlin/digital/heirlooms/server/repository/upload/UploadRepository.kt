package digital.heirlooms.server.repository.upload

import digital.heirlooms.server.service.plot.CriteriaEvaluator
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.upload.DecodedCursor
import digital.heirlooms.server.domain.upload.UploadPage
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.domain.upload.UploadSort
import digital.heirlooms.server.repository.plot.PlotRepository
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

interface UploadRepository {
    sealed class CompostResult {
        data class Success(val record: digital.heirlooms.server.domain.upload.UploadRecord) : CompostResult()
        object NotFound : CompostResult()
        object AlreadyComposted : CompostResult()
        object PreconditionFailed : CompostResult()
    }
    sealed class RestoreResult {
        data class Success(val record: digital.heirlooms.server.domain.upload.UploadRecord) : RestoreResult()
        object NotFound : RestoreResult()
        object NotComposted : RestoreResult()
    }

    fun recordUpload(record: digital.heirlooms.server.domain.upload.UploadRecord, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID)
    fun findByContentHash(hash: String, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): digital.heirlooms.server.domain.upload.UploadRecord?
    fun existsByContentHash(hash: String, userId: UUID): Boolean
    fun getUploadById(id: UUID): digital.heirlooms.server.domain.upload.UploadRecord?
    fun findUploadByIdForUser(id: UUID, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): digital.heirlooms.server.domain.upload.UploadRecord?
    fun findUploadByIdForSharedMember(id: UUID, userId: UUID): digital.heirlooms.server.domain.upload.UploadRecord?
    fun recordView(id: UUID, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): Boolean
    fun listUploads(tag: String? = null, excludeTag: String? = null, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): List<digital.heirlooms.server.domain.upload.UploadRecord>
    fun listCompostedUploads(userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): List<digital.heirlooms.server.domain.upload.UploadRecord>
    fun compostUpload(id: UUID, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): CompostResult
    fun restoreUpload(id: UUID, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): RestoreResult
    fun fetchExpiredCompostedUploads(): List<digital.heirlooms.server.domain.upload.UploadRecord>
    fun hardDeleteUpload(id: UUID)
    fun userAlreadyHasStorageKey(userId: UUID, storageKey: String): Boolean
    fun hasLiveSharedReference(storageKey: String, excludeUploadId: UUID): Boolean
    fun createSharedUpload(fromRecord: digital.heirlooms.server.domain.upload.UploadRecord, fromUserId: UUID, toUserId: UUID, wrappedDek: ByteArray, wrappedThumbnailDek: ByteArray?, dekFormat: String, rotationOverride: Int? = null): digital.heirlooms.server.domain.upload.UploadRecord
    fun updateRotation(id: UUID, rotation: Int, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): Boolean
    fun updateTags(id: UUID, tags: List<String>, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID, runFlows: (java.sql.Connection, UUID, UUID) -> Unit): Boolean
    fun uploadExists(id: UUID, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): Boolean
    fun listPendingExifIds(): List<UUID>
    fun updateExif(id: UUID, takenAt: java.time.Instant?, latitude: Double?, longitude: Double?, altitude: Double?, deviceMake: String?, deviceModel: String?)
    fun migrateUploadToEncrypted(id: UUID, newStorageKey: String, newContentHash: String?, envelopeVersion: Int?, wrappedDek: ByteArray, dekFormat: String, encryptedMetadata: ByteArray?, encryptedMetadataFormat: String?, thumbnailStorageKey: String?, wrappedThumbnailDek: ByteArray?, thumbnailDekFormat: String?): Boolean
    fun listUploadsPaginated(cursor: String? = null, limit: Int = 50, tags: List<String> = emptyList(), excludeTag: String? = null, fromDate: java.time.Instant? = null, toDate: java.time.Instant? = null, inCapsule: Boolean? = null, includeComposted: Boolean = false, hasLocation: Boolean? = null, sort: digital.heirlooms.server.domain.upload.UploadSort = digital.heirlooms.server.domain.upload.UploadSort.UPLOAD_NEWEST, justArrived: Boolean = false, mediaType: String? = null, isReceived: Boolean? = null, plotId: UUID? = null, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID, plotRepository: PlotRepository? = null): digital.heirlooms.server.domain.upload.UploadPage
    fun listCompostedUploadsPaginated(cursor: String? = null, limit: Int = 50, userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): digital.heirlooms.server.domain.upload.UploadPage
    fun listAllTags(userId: UUID = digital.heirlooms.server.domain.auth.FOUNDING_USER_ID): List<String>
}

class PostgresUploadRepository(private val dataSource: DataSource) : UploadRepository {

    override fun recordUpload(record: UploadRecord, userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO uploads
                   (id, storage_key, mime_type, file_size, content_hash, thumbnail_key,
                    taken_at, latitude, longitude, altitude, device_make, device_model,
                    exif_processed_at, storage_class, envelope_version, wrapped_dek, dek_format,
                    encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                    wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                    wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds,
                    user_id)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, record.id)
                stmt.setString(2, record.storageKey)
                stmt.setString(3, record.mimeType)
                stmt.setLong(4, record.fileSize)
                stmt.setString(5, record.contentHash)
                stmt.setString(6, record.thumbnailKey)
                stmt.setTimestamp(7, record.takenAt?.let { Timestamp.from(it) })
                stmt.setObject(8, record.latitude)
                stmt.setObject(9, record.longitude)
                stmt.setObject(10, record.altitude)
                stmt.setString(11, record.deviceMake)
                stmt.setString(12, record.deviceModel)
                stmt.setTimestamp(13, if (record.storageClass == "public") Timestamp.from(Instant.now()) else null)
                stmt.setString(14, record.storageClass)
                stmt.setObject(15, record.envelopeVersion)
                stmt.setBytes(16, record.wrappedDek)
                stmt.setString(17, record.dekFormat)
                stmt.setBytes(18, record.encryptedMetadata)
                stmt.setString(19, record.encryptedMetadataFormat)
                stmt.setString(20, record.thumbnailStorageKey)
                stmt.setBytes(21, record.wrappedThumbnailDek)
                stmt.setString(22, record.thumbnailDekFormat)
                stmt.setString(23, record.previewStorageKey)
                stmt.setBytes(24, record.wrappedPreviewDek)
                stmt.setString(25, record.previewDekFormat)
                stmt.setObject(26, record.plainChunkSize)
                stmt.setObject(27, record.durationSeconds)
                stmt.setObject(28, userId)
                stmt.executeUpdate()
            }
        }
    }

    override fun findByContentHash(hash: String, userId: UUID): UploadRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE content_hash = ? AND user_id = ? LIMIT 1"""
            ).use { stmt ->
                stmt.setString(1, hash)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    override fun existsByContentHash(hash: String, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM uploads WHERE content_hash = ? AND user_id = ? AND composted_at IS NULL LIMIT 1"
            ).use { stmt ->
                stmt.setString(1, hash)
                stmt.setObject(2, userId)
                return stmt.executeQuery().next()
            }
        }
    }

    override fun getUploadById(id: UUID): UploadRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    override fun findUploadByIdForUser(id: UUID, userId: UUID): UploadRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    override fun findUploadByIdForSharedMember(id: UUID, userId: UUID): UploadRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT u.id, u.storage_key, u.mime_type, u.file_size, u.uploaded_at, u.content_hash,
                          u.thumbnail_key, u.taken_at, u.latitude, u.longitude, u.altitude,
                          u.device_make, u.device_model, u.rotation, u.tags, u.composted_at,
                          u.exif_processed_at, u.last_viewed_at, u.storage_class, u.envelope_version,
                          COALESCE(pi.wrapped_item_dek, u.wrapped_dek) AS wrapped_dek,
                          COALESCE(pi.item_dek_format, u.dek_format) AS dek_format,
                          u.encrypted_metadata, u.encrypted_metadata_format,
                          u.thumbnail_storage_key,
                          COALESCE(pi.wrapped_thumbnail_dek, u.wrapped_thumbnail_dek) AS wrapped_thumbnail_dek,
                          COALESCE(pi.thumbnail_dek_format, u.thumbnail_dek_format) AS thumbnail_dek_format,
                          u.preview_storage_key, u.wrapped_preview_dek, u.preview_dek_format,
                          u.plain_chunk_size, u.duration_seconds
                   FROM uploads u
                   JOIN plot_items pi ON pi.upload_id = u.id
                   JOIN plots p ON p.id = pi.plot_id AND p.visibility = 'shared'
                   JOIN plot_members pm ON pm.plot_id = p.id AND pm.user_id = ? AND pm.status = 'joined'
                   WHERE u.id = ?
                   LIMIT 1"""
            ).use { stmt ->
                stmt.setObject(1, userId); stmt.setObject(2, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    override fun recordView(id: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE uploads SET last_viewed_at = NOW() WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun listUploads(tag: String?, excludeTag: String?, userId: UUID): List<UploadRecord> {
        dataSource.connection.use { conn ->
            val conditions = mutableListOf("composted_at IS NULL", "user_id = ?")
            if (tag != null) conditions.add("tags @> ARRAY[?]::text[]")
            if (excludeTag != null) conditions.add("NOT (tags @> ARRAY[?]::text[])")
            val where = "WHERE ${conditions.joinToString(" AND ")}"
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads $where ORDER BY uploaded_at DESC"""
            ).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, userId)
                if (tag != null) stmt.setString(idx++, tag)
                if (excludeTag != null) stmt.setString(idx, excludeTag)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    override fun listCompostedUploads(userId: UUID): List<UploadRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE composted_at IS NOT NULL AND user_id = ? ORDER BY composted_at DESC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }


    override fun compostUpload(id: UUID, userId: UUID): UploadRepository.CompostResult {
        withTransaction { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at
                   FROM uploads WHERE id = ? AND user_id = ? FOR UPDATE"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return UploadRepository.CompostResult.NotFound
                val compostedAt = rs.getTimestamp("composted_at")
                if (compostedAt != null) return UploadRepository.CompostResult.AlreadyComposted
                if (!canCompost(id, conn)) return UploadRepository.CompostResult.PreconditionFailed
            }
            conn.prepareStatement("UPDATE uploads SET composted_at = NOW() WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }
        return findUploadByIdForUser(id, userId)?.let { UploadRepository.CompostResult.Success(it) } ?: UploadRepository.CompostResult.NotFound
    }


    override fun restoreUpload(id: UUID, userId: UUID): UploadRepository.RestoreResult {
        withTransaction { conn ->
            conn.prepareStatement(
                "SELECT composted_at FROM uploads WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return UploadRepository.RestoreResult.NotFound
                val compostedAt = rs.getTimestamp("composted_at")
                if (compostedAt == null) return UploadRepository.RestoreResult.NotComposted
            }
            conn.prepareStatement("UPDATE uploads SET composted_at = NULL WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }
        return findUploadByIdForUser(id, userId)?.let { UploadRepository.RestoreResult.Success(it) } ?: UploadRepository.RestoreResult.NotFound
    }

    override fun fetchExpiredCompostedUploads(): List<UploadRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE composted_at < NOW() - INTERVAL '90 days'"""
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    override fun hardDeleteUpload(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM uploads WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    override fun userAlreadyHasStorageKey(userId: UUID, storageKey: String): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM uploads WHERE user_id = ? AND storage_key = ? AND composted_at IS NULL)"
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, storageKey)
                val rs = stmt.executeQuery()
                return rs.next() && rs.getBoolean(1)
            }
        }
    }

    override fun hasLiveSharedReference(storageKey: String, excludeUploadId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM uploads WHERE storage_key = ? AND id != ? AND composted_at IS NULL)"
            ).use { stmt ->
                stmt.setString(1, storageKey)
                stmt.setObject(2, excludeUploadId)
                val rs = stmt.executeQuery()
                return rs.next() && rs.getBoolean(1)
            }
        }
    }

    override fun createSharedUpload(
        fromRecord: UploadRecord,
        fromUserId: UUID,
        toUserId: UUID,
        wrappedDek: ByteArray,
        wrappedThumbnailDek: ByteArray?,
        dekFormat: String,
        rotationOverride: Int?,
    ): UploadRecord {
        val newId = UUID.randomUUID()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO uploads
                   (id, storage_key, mime_type, file_size, content_hash, thumbnail_key,
                    storage_class, envelope_version, wrapped_dek, dek_format,
                    thumbnail_storage_key, wrapped_thumbnail_dek, thumbnail_dek_format,
                    preview_storage_key, wrapped_preview_dek, preview_dek_format,
                    plain_chunk_size, duration_seconds, exif_processed_at,
                    shared_from_upload_id, shared_from_user_id, user_id, rotation)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, newId)
                stmt.setString(2, fromRecord.storageKey)
                stmt.setString(3, fromRecord.mimeType)
                stmt.setLong(4, fromRecord.fileSize)
                stmt.setString(5, fromRecord.contentHash)
                stmt.setString(6, fromRecord.thumbnailKey)
                stmt.setString(7, "encrypted")
                stmt.setObject(8, fromRecord.envelopeVersion)
                stmt.setBytes(9, wrappedDek)
                stmt.setString(10, dekFormat)
                stmt.setString(11, fromRecord.thumbnailStorageKey)
                stmt.setBytes(12, wrappedThumbnailDek)
                stmt.setString(13, if (wrappedThumbnailDek != null) dekFormat else null)
                stmt.setString(14, fromRecord.previewStorageKey)
                stmt.setBytes(15, fromRecord.wrappedPreviewDek)
                stmt.setString(16, fromRecord.previewDekFormat)
                stmt.setObject(17, fromRecord.plainChunkSize)
                stmt.setObject(18, fromRecord.durationSeconds)
                stmt.setObject(19, fromRecord.id)
                stmt.setObject(20, fromUserId)
                stmt.setObject(21, toUserId)
                stmt.setInt(22, rotationOverride ?: fromRecord.rotation)
                stmt.executeUpdate()
            }
        }
        return fromRecord.copy(
            id = newId,
            wrappedDek = wrappedDek,
            wrappedThumbnailDek = wrappedThumbnailDek,
            dekFormat = dekFormat,
            thumbnailDekFormat = if (wrappedThumbnailDek != null) dekFormat else null,
            rotation = rotationOverride ?: fromRecord.rotation,
            tags = emptyList(),
            sharedFromUploadId = fromRecord.id,
            sharedFromUserId = fromUserId,
        )
    }

    override fun updateRotation(id: UUID, rotation: Int, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE uploads SET rotation = ? WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setInt(1, rotation)
                stmt.setObject(2, id)
                stmt.setObject(3, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun updateTags(id: UUID, tags: List<String>, userId: UUID, runFlows: (Connection, UUID, UUID) -> Unit): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE uploads SET tags = ? WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setArray(1, conn.createArrayOf("text", tags.toTypedArray()))
                stmt.setObject(2, id)
                stmt.setObject(3, userId)
                val updated = stmt.executeUpdate() > 0
                if (updated) runFlows(conn, id, userId)
                return updated
            }
        }
    }

    override fun uploadExists(id: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeQuery().next()
            }
        }
    }

    override fun listPendingExifIds(): List<UUID> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id FROM uploads WHERE exif_processed_at IS NULL ORDER BY uploaded_at"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val ids = mutableListOf<UUID>()
                while (rs.next()) ids.add(rs.getObject("id", UUID::class.java))
                return ids
            }
        }
    }

    override fun updateExif(
        id: UUID,
        takenAt: Instant?,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        deviceMake: String?,
        deviceModel: String?,
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """UPDATE uploads
                   SET taken_at = ?, latitude = ?, longitude = ?, altitude = ?,
                       device_make = ?, device_model = ?, exif_processed_at = NOW()
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setTimestamp(1, takenAt?.let { Timestamp.from(it) })
                stmt.setObject(2, latitude)
                stmt.setObject(3, longitude)
                stmt.setObject(4, altitude)
                stmt.setString(5, deviceMake)
                stmt.setString(6, deviceModel)
                stmt.setObject(7, id)
                stmt.executeUpdate()
            }
        }
    }

    override fun migrateUploadToEncrypted(
        id: UUID,
        newStorageKey: String,
        newContentHash: String?,
        envelopeVersion: Int?,
        wrappedDek: ByteArray,
        dekFormat: String,
        encryptedMetadata: ByteArray?,
        encryptedMetadataFormat: String?,
        thumbnailStorageKey: String?,
        wrappedThumbnailDek: ByteArray?,
        thumbnailDekFormat: String?,
    ): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """UPDATE uploads SET
                       storage_class = 'encrypted',
                       storage_key = ?,
                       content_hash = ?,
                       envelope_version = ?,
                       wrapped_dek = ?,
                       dek_format = ?,
                       encrypted_metadata = ?,
                       encrypted_metadata_format = ?,
                       thumbnail_storage_key = ?,
                       wrapped_thumbnail_dek = ?,
                       thumbnail_dek_format = ?,
                       latitude = NULL,
                       longitude = NULL,
                       altitude = NULL,
                       device_make = NULL,
                       device_model = NULL,
                       thumbnail_key = NULL
                   WHERE id = ? AND storage_class = 'public'"""
            ).use { stmt ->
                stmt.setString(1, newStorageKey)
                stmt.setString(2, newContentHash)
                stmt.setObject(3, envelopeVersion)
                stmt.setBytes(4, wrappedDek)
                stmt.setString(5, dekFormat)
                stmt.setBytes(6, encryptedMetadata)
                stmt.setString(7, encryptedMetadataFormat)
                stmt.setString(8, thumbnailStorageKey)
                stmt.setBytes(9, wrappedThumbnailDek)
                stmt.setString(10, thumbnailDekFormat)
                stmt.setObject(11, id)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // ── Pagination ─────────────────────────────────────────────────────────

    override fun listUploadsPaginated(
        cursor: String?,
        limit: Int,
        tags: List<String>,
        excludeTag: String?,
        fromDate: Instant?,
        toDate: Instant?,
        inCapsule: Boolean?,
        includeComposted: Boolean,
        hasLocation: Boolean?,
        sort: UploadSort,
        justArrived: Boolean,
        mediaType: String?,
        isReceived: Boolean?,
        plotId: UUID?,
        userId: UUID,
        plotRepository: PlotRepository?,
    ): UploadPage {
        val effectiveLimit = limit.coerceIn(1, 200)
        val effectiveSort = if (justArrived) UploadSort.UPLOAD_NEWEST else sort
        val decoded = cursor?.let { decodeCursor(it) }
        val activeCursor = decoded?.takeIf { it.sort == effectiveSort }

        dataSource.connection.use { conn ->
            val conditions = mutableListOf<String>()
            val setters = mutableListOf<(PreparedStatement, Int) -> Int>()

            conditions += "user_id = ?"
            setters += { stmt, idx -> stmt.setObject(idx, userId); idx + 1 }

            if (justArrived) {
                conditions += "tags = '{}'::text[]"
                conditions += "composted_at IS NULL"
                conditions += """NOT EXISTS (
                    SELECT 1 FROM capsule_contents cc
                    JOIN capsules c ON c.id = cc.capsule_id
                    WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
                )"""
            } else {
                if (!includeComposted) conditions += "composted_at IS NULL"

                if (tags.isNotEmpty()) {
                    val placeholders = tags.indices.joinToString(",") { "?" }
                    conditions += "tags && ARRAY[$placeholders]::text[]"
                    tags.forEach { tag ->
                        setters += { stmt, idx -> stmt.setString(idx, tag); idx + 1 }
                    }
                }
                if (excludeTag != null) {
                    conditions += "NOT (tags @> ARRAY[?]::text[])"
                    setters += { stmt, idx -> stmt.setString(idx, excludeTag); idx + 1 }
                }
                if (fromDate != null) {
                    conditions += "uploaded_at >= ?"
                    setters += { stmt, idx -> stmt.setTimestamp(idx, Timestamp.from(fromDate)); idx + 1 }
                }
                if (toDate != null) {
                    conditions += "uploaded_at < ?"
                    setters += { stmt, idx -> stmt.setTimestamp(idx, Timestamp.from(toDate)); idx + 1 }
                }
                if (inCapsule == true) {
                    conditions += """EXISTS (
                        SELECT 1 FROM capsule_contents cc
                        JOIN capsules c ON c.id = cc.capsule_id
                        WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
                    )"""
                } else if (inCapsule == false) {
                    conditions += """NOT EXISTS (
                        SELECT 1 FROM capsule_contents cc
                        JOIN capsules c ON c.id = cc.capsule_id
                        WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
                    )"""
                }
                if (hasLocation == true) conditions += "latitude IS NOT NULL"
                else if (hasLocation == false) conditions += "latitude IS NULL"

                if (mediaType == "image") conditions += "mime_type LIKE 'image/%'"
                else if (mediaType == "video") conditions += "mime_type LIKE 'video/%'"

                if (isReceived == true) conditions += "shared_from_user_id IS NOT NULL"
                else if (isReceived == false) conditions += "shared_from_user_id IS NULL"
            }

            var sharedCollectionPlotId: UUID? = null

            if (plotId != null) {
                val plot = plotRepository?.getPlotByIdForUser(conn, plotId, userId)
                    ?: throw IllegalArgumentException("Plot $plotId not found")
                if (plot.visibility == "shared") {
                    conditions[0] = "user_id IN (SELECT user_id FROM plot_members WHERE plot_id = ? AND status = 'joined')"
                    setters[0] = { stmt, idx -> stmt.setObject(idx, plotId); idx + 1 }
                }
                val criteriaJson = plot.criteria
                if (criteriaJson != null) {
                    val fragment = CriteriaEvaluator.evaluate(criteriaJson, userId, conn)
                    conditions += fragment.sql
                    setters += fragment.setters
                } else if (plot.isSystemDefined) {
                    // System plot ("just arrived"): show untagged uploads not in any open/sealed capsule
                    conditions += "tags = '{}'::text[]"
                    conditions += "composted_at IS NULL"
                    conditions += """NOT EXISTS (
                        SELECT 1 FROM capsule_contents cc
                        JOIN capsules c ON c.id = cc.capsule_id
                        WHERE cc.upload_id = uploads.id AND c.state IN ('open','sealed')
                    )"""
                } else {
                    conditions += "id IN (SELECT upload_id FROM plot_items WHERE plot_id = ?)"
                    setters += listOf { stmt, idx -> stmt.setObject(idx, plotId); idx + 1 }
                    if (plot.visibility == "shared") sharedCollectionPlotId = plotId
                }
            }

            if (activeCursor != null) {
                val (cursorSql, cursorSetter) = buildCursorCondition(effectiveSort, activeCursor)
                conditions += cursorSql
                setters += cursorSetter
            }

            val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
            val orderBy = when (effectiveSort) {
                UploadSort.UPLOAD_NEWEST -> "ORDER BY uploaded_at DESC, id DESC"
                UploadSort.UPLOAD_OLDEST -> "ORDER BY uploaded_at ASC, id ASC"
                UploadSort.TAKEN_NEWEST  -> "ORDER BY taken_at DESC NULLS LAST, id DESC"
                UploadSort.TAKEN_OLDEST  -> "ORDER BY taken_at ASC NULLS LAST, id ASC"
            }

            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                          thumbnail_key, taken_at, latitude, longitude, altitude,
                          device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                          last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads $where $orderBy LIMIT ?"""
            ).use { stmt ->
                var idx = 1
                for (setter in setters) idx = setter(stmt, idx)
                stmt.setInt(idx, effectiveLimit + 1)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                val hasMore = results.size > effectiveLimit
                var items = if (hasMore) results.dropLast(1) else results

                if (sharedCollectionPlotId != null && items.isNotEmpty()) {
                    val ids = items.map { it.id }
                    val placeholders = ids.indices.joinToString(",") { "?" }
                    val wrappedDeks = mutableMapOf<UUID, ByteArray>()
                    val dekFormats = mutableMapOf<UUID, String>()
                    val wrappedThumbDeks = mutableMapOf<UUID, ByteArray>()
                    val thumbFormats = mutableMapOf<UUID, String>()
                    conn.prepareStatement(
                        """SELECT upload_id, wrapped_item_dek, item_dek_format,
                                  wrapped_thumbnail_dek, thumbnail_dek_format
                           FROM plot_items WHERE plot_id = ? AND upload_id IN ($placeholders)"""
                    ).use { dekStmt ->
                        dekStmt.setObject(1, sharedCollectionPlotId)
                        ids.forEachIndexed { i, id -> dekStmt.setObject(i + 2, id) }
                        val rs2 = dekStmt.executeQuery()
                        while (rs2.next()) {
                            val uid = rs2.getObject("upload_id", UUID::class.java)
                            rs2.getBytes("wrapped_item_dek")?.let { wrappedDeks[uid] = it }
                            rs2.getString("item_dek_format")?.let { dekFormats[uid] = it }
                            rs2.getBytes("wrapped_thumbnail_dek")?.let { wrappedThumbDeks[uid] = it }
                            rs2.getString("thumbnail_dek_format")?.let { thumbFormats[uid] = it }
                        }
                    }
                    items = items.map { u ->
                        if (wrappedDeks.containsKey(u.id)) u.copy(
                            wrappedDek = wrappedDeks[u.id],
                            dekFormat = dekFormats[u.id] ?: u.dekFormat,
                            wrappedThumbnailDek = wrappedThumbDeks[u.id] ?: u.wrappedThumbnailDek,
                            thumbnailDekFormat = thumbFormats[u.id] ?: u.thumbnailDekFormat,
                        ) else u
                    }
                }

                val nextCursor = if (hasMore) encodeCursor(items.last(), effectiveSort) else null
                return UploadPage(items, nextCursor)
            }
        }
    }

    override fun listCompostedUploadsPaginated(cursor: String?, limit: Int, userId: UUID): UploadPage {
        val effectiveLimit = limit.coerceIn(1, 200)
        val decoded = cursor?.let { decodeCompostedCursor(it) }
        dataSource.connection.use { conn ->
            val where = if (decoded != null)
                "WHERE composted_at IS NOT NULL AND user_id = ? AND (composted_at < ? OR (composted_at = ? AND id < ?::uuid))"
            else
                "WHERE composted_at IS NOT NULL AND user_id = ?"
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                          thumbnail_key, taken_at, latitude, longitude, altitude,
                          device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                          last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads $where
                   ORDER BY composted_at DESC, id DESC
                   LIMIT ?"""
            ).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, userId)
                if (decoded != null) {
                    stmt.setTimestamp(idx++, Timestamp.from(decoded.first))
                    stmt.setTimestamp(idx++, Timestamp.from(decoded.first))
                    stmt.setString(idx++, decoded.second.toString())
                }
                stmt.setInt(idx, effectiveLimit + 1)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                val hasMore = results.size > effectiveLimit
                val items = if (hasMore) results.dropLast(1) else results
                val nextCursor = if (hasMore) encodeCompostedCursor(items.last()) else null
                return UploadPage(items, nextCursor)
            }
        }
    }

    override fun listAllTags(userId: UUID): List<String> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT DISTINCT UNNEST(tags) AS tag FROM uploads WHERE composted_at IS NULL AND user_id = ? ORDER BY tag"
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                val tags = mutableListOf<String>()
                while (rs.next()) tags.add(rs.getString("tag"))
                return tags
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    internal fun canCompost(uploadId: UUID, conn: Connection): Boolean {
        val hasTags = conn.prepareStatement(
            "SELECT tags FROM uploads WHERE id = ?"
        ).use { stmt ->
            stmt.setObject(1, uploadId)
            val rs = stmt.executeQuery()
            if (!rs.next()) return false
            val arr = rs.getArray("tags")
            val tags = arr?.let { (it.array as? Array<*>)?.filterIsInstance<String>() } ?: emptyList()
            tags.isNotEmpty()
        }
        if (hasTags) return false
        val inActiveCapsule = conn.prepareStatement(
            """SELECT EXISTS(
                SELECT 1 FROM capsule_contents cc
                JOIN capsules c ON c.id = cc.capsule_id
                WHERE cc.upload_id = ? AND c.state IN ('open', 'sealed')
               )"""
        ).use { stmt ->
            stmt.setObject(1, uploadId)
            stmt.executeQuery().let { rs -> rs.next(); rs.getBoolean(1) }
        }
        return !inActiveCapsule
    }

    // ── Cursor helpers ────────────────────────────────────────────────────────

    private fun encodeCursor(record: UploadRecord, sort: UploadSort): String {
        val sortKeyMs = when (sort) {
            UploadSort.UPLOAD_NEWEST, UploadSort.UPLOAD_OLDEST -> record.uploadedAt.toEpochMilli()
            UploadSort.TAKEN_NEWEST, UploadSort.TAKEN_OLDEST   -> record.takenAt?.toEpochMilli()
        }
        val raw = "${sort.name}:${sortKeyMs ?: "null"}:${record.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    private fun decodeCursor(cursor: String): DecodedCursor? = try {
        val raw = String(Base64.getUrlDecoder().decode(cursor))
        val firstColon = raw.indexOf(':')
        val lastColon = raw.lastIndexOf(':')
        if (firstColon < 0 || lastColon <= firstColon) null else {
            val sort = UploadSort.valueOf(raw.substring(0, firstColon))
            val sortKeyStr = raw.substring(firstColon + 1, lastColon)
            val sortKeyMs = if (sortKeyStr == "null") null else sortKeyStr.toLong()
            val uuid = UUID.fromString(raw.substring(lastColon + 1))
            DecodedCursor(sort, sortKeyMs, uuid)
        }
    } catch (_: Exception) { null }

    private fun encodeCompostedCursor(record: UploadRecord): String {
        val raw = "${record.compostedAt!!.toEpochMilli()}:${record.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    private fun decodeCompostedCursor(cursor: String): Pair<Instant, UUID>? = try {
        val raw = String(Base64.getUrlDecoder().decode(cursor))
        val colon = raw.lastIndexOf(':')
        if (colon < 0) null else {
            val epochMs = raw.substring(0, colon).toLong()
            val uuid = UUID.fromString(raw.substring(colon + 1))
            Instant.ofEpochMilli(epochMs) to uuid
        }
    } catch (_: Exception) { null }

    private fun buildCursorCondition(
        sort: UploadSort,
        cursor: DecodedCursor,
    ): Pair<String, (PreparedStatement, Int) -> Int> = when (sort) {
        UploadSort.UPLOAD_NEWEST -> {
            val ts = Timestamp.from(Instant.ofEpochMilli(cursor.sortKeyMs ?: 0))
            "(uploaded_at < ? OR (uploaded_at = ? AND id < ?::uuid))" to { stmt, idx ->
                stmt.setTimestamp(idx, ts); stmt.setTimestamp(idx + 1, ts)
                stmt.setString(idx + 2, cursor.id.toString()); idx + 3
            }
        }
        UploadSort.UPLOAD_OLDEST -> {
            val ts = Timestamp.from(Instant.ofEpochMilli(cursor.sortKeyMs ?: 0))
            "(uploaded_at > ? OR (uploaded_at = ? AND id > ?::uuid))" to { stmt, idx ->
                stmt.setTimestamp(idx, ts); stmt.setTimestamp(idx + 1, ts)
                stmt.setString(idx + 2, cursor.id.toString()); idx + 3
            }
        }
        UploadSort.TAKEN_NEWEST -> {
            if (cursor.sortKeyMs != null) {
                val ts = Timestamp.from(Instant.ofEpochMilli(cursor.sortKeyMs))
                "(taken_at < ? OR (taken_at = ? AND id < ?::uuid) OR taken_at IS NULL)" to { stmt, idx ->
                    stmt.setTimestamp(idx, ts); stmt.setTimestamp(idx + 1, ts)
                    stmt.setString(idx + 2, cursor.id.toString()); idx + 3
                }
            } else {
                "(taken_at IS NULL AND id < ?::uuid)" to { stmt, idx ->
                    stmt.setString(idx, cursor.id.toString()); idx + 1
                }
            }
        }
        UploadSort.TAKEN_OLDEST -> {
            if (cursor.sortKeyMs != null) {
                val ts = Timestamp.from(Instant.ofEpochMilli(cursor.sortKeyMs))
                "(taken_at > ? OR (taken_at = ? AND id > ?::uuid) OR taken_at IS NULL)" to { stmt, idx ->
                    stmt.setTimestamp(idx, ts); stmt.setTimestamp(idx + 1, ts)
                    stmt.setString(idx + 2, cursor.id.toString()); idx + 3
                }
            } else {
                "(taken_at IS NULL AND id > ?::uuid)" to { stmt, idx ->
                    stmt.setString(idx, cursor.id.toString()); idx + 1
                }
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
