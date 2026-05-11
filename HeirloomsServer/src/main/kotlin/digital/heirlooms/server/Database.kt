package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

// ---- Capsule domain model -----------------------------------------------

enum class CapsuleShape { OPEN, SEALED }
enum class CapsuleState { OPEN, SEALED, DELIVERED, CANCELLED }

data class CapsuleRecord(
    val id: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdByUser: String,
    val shape: CapsuleShape,
    val state: CapsuleState,
    val unlockAt: OffsetDateTime,
    val cancelledAt: Instant?,
    val deliveredAt: Instant?,
)

data class CapsuleSummary(
    val record: CapsuleRecord,
    val recipients: List<String>,
    val uploadCount: Int,
    val hasMessage: Boolean,
)

data class CapsuleDetail(
    val record: CapsuleRecord,
    val recipients: List<String>,
    val uploads: List<UploadRecord>,
    val message: String,
)

// ---- Upload domain model ------------------------------------------------

data class UploadRecord(
    val id: UUID,
    val storageKey: String,
    val mimeType: String,
    val fileSize: Long,
    val uploadedAt: Instant = Instant.now(),
    val contentHash: String? = null,
    val thumbnailKey: String? = null,
    val takenAt: Instant? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val rotation: Int = 0,
    val tags: List<String> = emptyList(),
    val compostedAt: Instant? = null,
    val exifProcessedAt: Instant? = null,
    val lastViewedAt: Instant? = null,
    val storageClass: String = "public",
    val envelopeVersion: Int? = null,
    val wrappedDek: ByteArray? = null,
    val dekFormat: String? = null,
    val encryptedMetadata: ByteArray? = null,
    val encryptedMetadataFormat: String? = null,
    val thumbnailStorageKey: String? = null,
    val wrappedThumbnailDek: ByteArray? = null,
    val thumbnailDekFormat: String? = null,
    val previewStorageKey: String? = null,
    val wrappedPreviewDek: ByteArray? = null,
    val previewDekFormat: String? = null,
    val plainChunkSize: Int? = null,
    val durationSeconds: Int? = null,
)

data class UploadPage(val items: List<UploadRecord>, val nextCursor: String?)

data class WrappedKeyRecord(
    val id: UUID,
    val deviceId: String,
    val deviceLabel: String,
    val deviceKind: String,
    val pubkeyFormat: String,
    val pubkey: ByteArray,
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val retiredAt: Instant?,
)

data class RecoveryPassphraseRecord(
    val wrappedMasterKey: ByteArray,
    val wrapFormat: String,
    val argon2Params: String,
    val salt: ByteArray,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PendingDeviceLinkRecord(
    val id: UUID,
    val oneTimeCode: String,
    val expiresAt: Instant,
    val state: String,
    val newDeviceId: String?,
    val newDeviceLabel: String?,
    val newDeviceKind: String?,
    val newPubkeyFormat: String?,
    val newPubkey: ByteArray?,
    val wrappedMasterKey: ByteArray?,
    val wrapFormat: String?,
)

// ---- Sort options for uploads -------------------------------------------

enum class UploadSort { UPLOAD_NEWEST, UPLOAD_OLDEST, TAKEN_NEWEST, TAKEN_OLDEST }

private data class DecodedCursor(val sort: UploadSort, val sortKeyMs: Long?, val id: UUID)

// ---- Plot domain model -----------------------------------------------

data class PlotRecord(
    val id: UUID,
    val ownerUserId: UUID?,
    val name: String,
    val sortOrder: Int,
    val isSystemDefined: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tagCriteria: List<String>,
)

class Database(private val dataSource: DataSource) {

    fun runMigrations() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun recordUpload(record: UploadRecord) {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """INSERT INTO uploads
                   (id, storage_key, mime_type, file_size, content_hash, thumbnail_key,
                    taken_at, latitude, longitude, altitude, device_make, device_model,
                    exif_processed_at, storage_class, envelope_version, wrapped_dek, dek_format,
                    encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                    wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                    wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
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
                stmt.executeUpdate()
            }
        }
    }

    fun findByContentHash(hash: String): UploadRecord? {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE content_hash = ? LIMIT 1"""
            ).use { stmt ->
                stmt.setString(1, hash)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    fun getUploadById(id: UUID): UploadRecord? {
        dataSource.connection.use { conn: Connection ->
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

    fun recordView(id: UUID): Boolean {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "UPDATE uploads SET last_viewed_at = NOW() WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun listUploads(tag: String? = null, excludeTag: String? = null): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
            val conditions = mutableListOf("composted_at IS NULL")
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
                if (tag != null) stmt.setString(idx++, tag)
                if (excludeTag != null) stmt.setString(idx, excludeTag)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    fun listCompostedUploads(): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at,
                          storage_class, envelope_version, wrapped_dek, dek_format,
                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds
                   FROM uploads WHERE composted_at IS NOT NULL ORDER BY composted_at DESC"""
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    sealed class CompostResult {
        data class Success(val record: UploadRecord) : CompostResult()
        object NotFound : CompostResult()
        object AlreadyComposted : CompostResult()
        object PreconditionFailed : CompostResult()
    }

    fun compostUpload(id: UUID): CompostResult {
        withTransaction { conn ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          taken_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at
                   FROM uploads WHERE id = ? FOR UPDATE"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return CompostResult.NotFound
                val compostedAt = rs.getTimestamp("composted_at")
                if (compostedAt != null) return CompostResult.AlreadyComposted
                if (!canCompost(id, conn)) return CompostResult.PreconditionFailed
            }
            conn.prepareStatement("UPDATE uploads SET composted_at = NOW() WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
        return getUploadById(id)?.let { CompostResult.Success(it) } ?: CompostResult.NotFound
    }

    sealed class RestoreResult {
        data class Success(val record: UploadRecord) : RestoreResult()
        object NotFound : RestoreResult()
        object NotComposted : RestoreResult()
    }

    fun restoreUpload(id: UUID): RestoreResult {
        withTransaction { conn ->
            conn.prepareStatement(
                "SELECT composted_at FROM uploads WHERE id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RestoreResult.NotFound
                val compostedAt = rs.getTimestamp("composted_at")
                if (compostedAt == null) return RestoreResult.NotComposted
            }
            conn.prepareStatement("UPDATE uploads SET composted_at = NULL WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
        return getUploadById(id)?.let { RestoreResult.Success(it) } ?: RestoreResult.NotFound
    }

    fun fetchExpiredCompostedUploads(): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
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

    fun hardDeleteUpload(id: UUID) {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("DELETE FROM uploads WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

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

    fun updateRotation(id: UUID, rotation: Int) {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("UPDATE uploads SET rotation = ? WHERE id = ?").use { stmt ->
                stmt.setInt(1, rotation)
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
    }

    fun updateTags(id: UUID, tags: List<String>): Boolean {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("UPDATE uploads SET tags = ? WHERE id = ?").use { stmt ->
                stmt.setArray(1, conn.createArrayOf("text", tags.toTypedArray()))
                stmt.setObject(2, id)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // ---- Capsule operations ------------------------------------------------

    fun uploadExists(id: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                return stmt.executeQuery().next()
            }
        }
    }

    fun createCapsule(
        id: UUID,
        createdByUser: String,
        shape: CapsuleShape,
        state: CapsuleState,
        unlockAt: OffsetDateTime,
        recipients: List<String>,
        uploadIds: List<UUID>,
        message: String,
    ): CapsuleDetail {
        val now = Instant.now()
        withTransaction { conn ->
            conn.prepareStatement(
                """INSERT INTO capsules (id, created_at, updated_at, created_by_user, shape, state, unlock_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setTimestamp(2, Timestamp.from(now))
                stmt.setTimestamp(3, Timestamp.from(now))
                stmt.setString(4, createdByUser)
                stmt.setString(5, shape.name.lowercase())
                stmt.setString(6, state.name.lowercase())
                stmt.setObject(7, unlockAt)
                stmt.executeUpdate()
            }
            insertRecipients(conn, id, recipients)
            insertContents(conn, id, uploadIds)
            if (message.isNotEmpty()) {
                insertMessage(conn, id, message, 1)
            }
        }
        return getCapsuleById(id)!!
    }

    fun getCapsuleById(id: UUID): CapsuleDetail? {
        dataSource.connection.use { conn ->
            val record = conn.prepareStatement(
                """SELECT id, created_at, updated_at, created_by_user, shape, state,
                          unlock_at, cancelled_at, delivered_at
                   FROM capsules WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
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

    fun listCapsules(states: List<CapsuleState>, orderBy: String): List<CapsuleSummary> {
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
                   WHERE c.state = ANY(?)
                   ORDER BY $orderCol DESC"""
            ).use { stmt ->
                stmt.setArray(1, stateArr)
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

    sealed class UpdateResult {
        data class Success(val detail: CapsuleDetail) : UpdateResult()
        object NotFound : UpdateResult()
        object TerminalState : UpdateResult()
        object SealedContents : UpdateResult()
        object UnknownUpload : UpdateResult()
        data class InvalidRecipients(val reason: String) : UpdateResult()
        data class MessageTooLong(val limit: Int) : UpdateResult()
    }

    fun updateCapsule(
        id: UUID,
        unlockAt: OffsetDateTime?,
        recipients: List<String>?,
        uploadIds: List<UUID>?,
        message: String?,
    ): UpdateResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return UpdateResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.state == CapsuleState.DELIVERED || record.state == CapsuleState.CANCELLED) {
                return UpdateResult.TerminalState
            }
            if (uploadIds != null && record.state == CapsuleState.SEALED) {
                return UpdateResult.SealedContents
            }
            if (uploadIds != null) {
                for (uid in uploadIds) {
                    val exists = conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ?").use { stmt ->
                        stmt.setObject(1, uid)
                        stmt.executeQuery().next()
                    }
                    if (!exists) return UpdateResult.UnknownUpload
                }
            }

            val setClauses = mutableListOf("updated_at = ?")
            if (unlockAt != null) setClauses.add("unlock_at = ?")

            conn.prepareStatement("UPDATE capsules SET ${setClauses.joinToString(", ")} WHERE id = ?").use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (unlockAt != null) stmt.setObject(idx++, unlockAt)
                stmt.setObject(idx, id)
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
        return getCapsuleById(id)?.let { UpdateResult.Success(it) } ?: UpdateResult.NotFound
    }

    sealed class SealResult {
        data class Success(val detail: CapsuleDetail) : SealResult()
        object NotFound : SealResult()
        object WrongState : SealResult()
        object Empty : SealResult()
    }

    fun sealCapsule(id: UUID): SealResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return SealResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.shape != CapsuleShape.OPEN || record.state != CapsuleState.OPEN) {
                return SealResult.WrongState
            }

            val hasContents = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM capsule_contents WHERE capsule_id = ?)"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().let { rs -> rs.next(); rs.getBoolean(1) }
            }

            if (!hasContents) return SealResult.Empty

            conn.prepareStatement(
                "UPDATE capsules SET state = 'sealed', updated_at = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(Instant.now()))
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
        return getCapsuleById(id)?.let { SealResult.Success(it) } ?: SealResult.NotFound
    }

    sealed class CancelResult {
        data class Success(val detail: CapsuleDetail) : CancelResult()
        object NotFound : CancelResult()
        object WrongState : CancelResult()
    }

    fun cancelCapsule(id: UUID): CancelResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return CancelResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.state != CapsuleState.OPEN && record.state != CapsuleState.SEALED) {
                return CancelResult.WrongState
            }

            val now = Timestamp.from(Instant.now())
            conn.prepareStatement(
                "UPDATE capsules SET state = 'cancelled', cancelled_at = ?, updated_at = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setTimestamp(1, now)
                stmt.setTimestamp(2, now)
                stmt.setObject(3, id)
                stmt.executeUpdate()
            }
        }
        return getCapsuleById(id)?.let { CancelResult.Success(it) } ?: CancelResult.NotFound
    }

    fun getCapsulesForUpload(uploadId: UUID): List<CapsuleSummary>? {
        dataSource.connection.use { conn ->
            val uploadExists = conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ?").use { stmt ->
                stmt.setObject(1, uploadId)
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

    // ---- Pagination -------------------------------------------------------

    fun listUploadsPaginated(
        cursor: String? = null,
        limit: Int = 50,
        tags: List<String> = emptyList(),
        excludeTag: String? = null,
        fromDate: Instant? = null,
        toDate: Instant? = null,
        inCapsule: Boolean? = null,
        includeComposted: Boolean = false,
        hasLocation: Boolean? = null,
        sort: UploadSort = UploadSort.UPLOAD_NEWEST,
        justArrived: Boolean = false,
    ): UploadPage {
        val effectiveLimit = limit.coerceIn(1, 200)
        val effectiveSort = if (justArrived) UploadSort.UPLOAD_NEWEST else sort
        val decoded = cursor?.let { decodeCursor(it) }
        // Discard cursor if its sort doesn't match (sort change always restarts pagination)
        val activeCursor = decoded?.takeIf { it.sort == effectiveSort }

        dataSource.connection.use { conn: Connection ->
            // Accumulate SQL condition fragments and parameter-setter lambdas in parallel.
            // Each setter receives (stmt, currentIdx) and returns the next available index.
            val conditions = mutableListOf<String>()
            val setters = mutableListOf<(PreparedStatement, Int) -> Int>()

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
                    // Use individual string placeholders — mirrors the existing @> pattern
                    // and avoids JDBC setArray / ::text[] cast incompatibility.
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
            }

            // Cursor condition appended last so its params bind after filter params
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
                val items = if (hasMore) results.dropLast(1) else results
                val nextCursor = if (hasMore) encodeCursor(items.last(), effectiveSort) else null
                return UploadPage(items, nextCursor)
            }
        }
    }

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

    fun listCompostedUploadsPaginated(cursor: String? = null, limit: Int = 50): UploadPage {
        val effectiveLimit = limit.coerceIn(1, 200)
        val decoded = cursor?.let { decodeCompostedCursor(it) }
        dataSource.connection.use { conn: Connection ->
            val where = if (decoded != null)
                "WHERE composted_at IS NOT NULL AND (composted_at < ? OR (composted_at = ? AND id < ?::uuid))"
            else
                "WHERE composted_at IS NOT NULL"
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

    fun listAllTags(): List<String> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "SELECT DISTINCT UNNEST(tags) AS tag FROM uploads WHERE composted_at IS NULL ORDER BY tag"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val tags = mutableListOf<String>()
                while (rs.next()) tags.add(rs.getString("tag"))
                return tags
            }
        }
    }

    // ---- EXIF recovery ----------------------------------------------------

    fun listPendingExifIds(): List<UUID> {
        dataSource.connection.use { conn: Connection ->
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

    fun updateExif(
        id: UUID,
        takenAt: Instant?,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        deviceMake: String?,
        deviceModel: String?,
    ) {
        dataSource.connection.use { conn: Connection ->
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

    // ---- Plot operations --------------------------------------------------

    fun listPlots(): List<PlotRecord> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT p.id, p.owner_user_id, p.name, p.sort_order, p.is_system_defined,
                          p.created_at, p.updated_at
                   FROM plots p
                   WHERE p.owner_user_id IS NULL
                   ORDER BY p.sort_order ASC, p.created_at ASC"""
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableListOf<PlotRecord>()
                while (rs.next()) {
                    val id = rs.getObject("id", UUID::class.java)
                    results.add(PlotRecord(
                        id = id,
                        ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
                        name = rs.getString("name"),
                        sortOrder = rs.getInt("sort_order"),
                        isSystemDefined = rs.getBoolean("is_system_defined"),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                        updatedAt = rs.getTimestamp("updated_at").toInstant(),
                        tagCriteria = queryPlotTagCriteria(conn, id),
                    ))
                }
                return results
            }
        }
    }

    fun getPlotById(id: UUID): PlotRecord? {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT id, owner_user_id, name, sort_order, is_system_defined, created_at, updated_at
                   FROM plots WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return PlotRecord(
                    id = id,
                    ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
                    name = rs.getString("name"),
                    sortOrder = rs.getInt("sort_order"),
                    isSystemDefined = rs.getBoolean("is_system_defined"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    updatedAt = rs.getTimestamp("updated_at").toInstant(),
                    tagCriteria = queryPlotTagCriteria(conn, id),
                )
            }
        }
    }

    fun createPlot(name: String, tagCriteria: List<String>): PlotRecord {
        val id = UUID.randomUUID()
        val now = Instant.now()
        withTransaction { conn ->
            conn.prepareStatement(
                "INSERT INTO plots (id, owner_user_id, name, created_at, updated_at) VALUES (?, NULL, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setString(2, name.trim())
                stmt.setTimestamp(3, Timestamp.from(now))
                stmt.setTimestamp(4, Timestamp.from(now))
                stmt.executeUpdate()
            }
            replacePlotTagCriteria(conn, id, tagCriteria)
        }
        return getPlotById(id)!!
    }

    sealed class PlotUpdateResult {
        data class Success(val plot: PlotRecord) : PlotUpdateResult()
        object NotFound : PlotUpdateResult()
        object SystemDefined : PlotUpdateResult()
    }

    fun updatePlot(id: UUID, name: String?, sortOrder: Int?, tagCriteria: List<String>?): PlotUpdateResult {
        withTransaction { conn ->
            val (isSystemDefined) = conn.prepareStatement(
                "SELECT is_system_defined FROM plots WHERE id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return PlotUpdateResult.NotFound
                listOf(rs.getBoolean("is_system_defined"))
            }
            if (isSystemDefined) return PlotUpdateResult.SystemDefined

            val setClauses = mutableListOf("updated_at = ?")
            if (name != null) setClauses.add("name = ?")
            if (sortOrder != null) setClauses.add("sort_order = ?")
            conn.prepareStatement("UPDATE plots SET ${setClauses.joinToString(", ")} WHERE id = ?").use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (name != null) stmt.setString(idx++, name.trim())
                if (sortOrder != null) stmt.setInt(idx++, sortOrder)
                stmt.setObject(idx, id)
                stmt.executeUpdate()
            }
            if (tagCriteria != null) replacePlotTagCriteria(conn, id, tagCriteria)
        }
        return getPlotById(id)?.let { PlotUpdateResult.Success(it) } ?: PlotUpdateResult.NotFound
    }

    sealed class PlotDeleteResult {
        object Success : PlotDeleteResult()
        object NotFound : PlotDeleteResult()
        object SystemDefined : PlotDeleteResult()
    }

    fun deletePlot(id: UUID): PlotDeleteResult {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("SELECT is_system_defined FROM plots WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return PlotDeleteResult.NotFound
                if (rs.getBoolean("is_system_defined")) return PlotDeleteResult.SystemDefined
            }
            conn.prepareStatement("DELETE FROM plots WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
        return PlotDeleteResult.Success
    }

    sealed class BatchReorderResult {
        object Success : BatchReorderResult()
        object NotFound : BatchReorderResult()
        object SystemDefined : BatchReorderResult()
    }

    fun batchReorderPlots(updates: List<Pair<UUID, Int>>): BatchReorderResult {
        withTransaction { conn ->
            for ((id, _) in updates) {
                conn.prepareStatement(
                    "SELECT is_system_defined FROM plots WHERE id = ? FOR UPDATE"
                ).use { stmt ->
                    stmt.setObject(1, id)
                    val rs = stmt.executeQuery()
                    if (!rs.next()) return BatchReorderResult.NotFound
                    if (rs.getBoolean("is_system_defined")) return BatchReorderResult.SystemDefined
                }
            }
            val now = Timestamp.from(Instant.now())
            for ((id, sortOrder) in updates) {
                conn.prepareStatement(
                    "UPDATE plots SET sort_order = ?, updated_at = ? WHERE id = ?"
                ).use { stmt ->
                    stmt.setInt(1, sortOrder)
                    stmt.setTimestamp(2, now)
                    stmt.setObject(3, id)
                    stmt.executeUpdate()
                }
            }
        }
        return BatchReorderResult.Success
    }

    private fun queryPlotTagCriteria(conn: Connection, plotId: UUID): List<String> =
        conn.prepareStatement("SELECT tag FROM plot_tag_criteria WHERE plot_id = ? ORDER BY tag").use { stmt ->
            stmt.setObject(1, plotId)
            val rs = stmt.executeQuery()
            val tags = mutableListOf<String>()
            while (rs.next()) tags.add(rs.getString("tag"))
            tags
        }

    private fun replacePlotTagCriteria(conn: Connection, plotId: UUID, tags: List<String>) {
        conn.prepareStatement("DELETE FROM plot_tag_criteria WHERE plot_id = ?").use { stmt ->
            stmt.setObject(1, plotId)
            stmt.executeUpdate()
        }
        for (tag in tags) {
            conn.prepareStatement("INSERT INTO plot_tag_criteria (plot_id, tag) VALUES (?, ?)").use { stmt ->
                stmt.setObject(1, plotId)
                stmt.setString(2, tag.trim())
                stmt.executeUpdate()
            }
        }
    }

    // ---- Cursor helpers ---------------------------------------------------

    // Active uploads cursor: encodes sort name + sort key + id.
    // Format: "<SORT_NAME>:<epochMs_or_null>:<id>"
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

    // Composted uploads cursor: legacy format "<composted_at_epochMs>:<id>"
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

    // ---- Pending blobs -------------------------------------------------------

    fun insertPendingBlob(storageKey: String): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO pending_blobs (id, storage_key) VALUES (?, ?)").use { stmt ->
                stmt.setObject(1, id)
                stmt.setString(2, storageKey)
                stmt.executeUpdate()
            }
        }
        return id
    }

    fun deletePendingBlob(storageKey: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM pending_blobs WHERE storage_key = ?").use { stmt ->
                stmt.setString(1, storageKey)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteStalePendingBlobs(olderThan: Instant): List<String> {
        dataSource.connection.use { conn ->
            val keys = mutableListOf<String>()
            conn.prepareStatement("SELECT storage_key FROM pending_blobs WHERE created_at < ?").use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(olderThan))
                val rs = stmt.executeQuery()
                while (rs.next()) keys.add(rs.getString("storage_key"))
            }
            if (keys.isNotEmpty()) {
                conn.prepareStatement("DELETE FROM pending_blobs WHERE created_at < ?").use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(olderThan))
                    stmt.executeUpdate()
                }
            }
            return keys
        }
    }

    // ---- Wrapped keys --------------------------------------------------------

    fun insertWrappedKey(record: WrappedKeyRecord) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO wrapped_keys (id, device_id, device_label, device_kind, pubkey_format,
                       pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, record.id)
                stmt.setString(2, record.deviceId)
                stmt.setString(3, record.deviceLabel)
                stmt.setString(4, record.deviceKind)
                stmt.setString(5, record.pubkeyFormat)
                stmt.setBytes(6, record.pubkey)
                stmt.setBytes(7, record.wrappedMasterKey)
                stmt.setString(8, record.wrapFormat)
                stmt.setTimestamp(9, Timestamp.from(record.createdAt))
                stmt.setTimestamp(10, Timestamp.from(record.lastUsedAt))
                stmt.setTimestamp(11, record.retiredAt?.let { Timestamp.from(it) })
                stmt.executeUpdate()
            }
        }
    }

    fun listWrappedKeys(includeRetired: Boolean = false): List<WrappedKeyRecord> {
        dataSource.connection.use { conn ->
            val sql = if (includeRetired)
                "SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at FROM wrapped_keys ORDER BY created_at DESC"
            else
                "SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at FROM wrapped_keys WHERE retired_at IS NULL ORDER BY created_at DESC"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val list = mutableListOf<WrappedKeyRecord>()
                while (rs.next()) list.add(rs.toWrappedKeyRecord())
                return list
            }
        }
    }

    fun getWrappedKeyByDeviceId(deviceId: String): WrappedKeyRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at FROM wrapped_keys WHERE device_id = ?"
            ).use { stmt ->
                stmt.setString(1, deviceId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toWrappedKeyRecord()
            }
        }
    }

    fun retireWrappedKey(id: UUID, retiredAt: Instant = Instant.now()) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE wrapped_keys SET retired_at = ? WHERE id = ?").use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(retiredAt))
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
    }

    fun touchWrappedKey(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE wrapped_keys SET last_used_at = NOW() WHERE id = ? AND retired_at IS NULL").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun retireDormantWrappedKeys(dormantBefore: Instant): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE wrapped_keys SET retired_at = NOW() WHERE retired_at IS NULL AND last_used_at < ?"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(dormantBefore))
                return stmt.executeUpdate()
            }
        }
    }

    // ---- Recovery passphrase -------------------------------------------------

    fun getRecoveryPassphrase(): RecoveryPassphraseRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT wrapped_master_key, wrap_format, argon2_params, salt, created_at, updated_at FROM recovery_passphrase WHERE id = 1"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toRecoveryPassphraseRecord()
            }
        }
    }

    fun upsertRecoveryPassphrase(record: RecoveryPassphraseRecord) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO recovery_passphrase (id, wrapped_master_key, wrap_format, argon2_params, salt, created_at, updated_at)
                   VALUES (1, ?, ?, ?::jsonb, ?, NOW(), NOW())
                   ON CONFLICT (id) DO UPDATE SET
                       wrapped_master_key = EXCLUDED.wrapped_master_key,
                       wrap_format = EXCLUDED.wrap_format,
                       argon2_params = EXCLUDED.argon2_params,
                       salt = EXCLUDED.salt,
                       updated_at = NOW()"""
            ).use { stmt ->
                stmt.setBytes(1, record.wrappedMasterKey)
                stmt.setString(2, record.wrapFormat)
                stmt.setString(3, record.argon2Params)
                stmt.setBytes(4, record.salt)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteRecoveryPassphrase(): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM recovery_passphrase WHERE id = 1").use { stmt ->
                return stmt.executeUpdate() > 0
            }
        }
    }

    // ---- Device links --------------------------------------------------------

    fun insertPendingDeviceLink(record: PendingDeviceLinkRecord) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO pending_device_links (id, one_time_code, expires_at, state) VALUES (?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, record.id)
                stmt.setString(2, record.oneTimeCode)
                stmt.setTimestamp(3, Timestamp.from(record.expiresAt))
                stmt.setString(4, record.state)
                stmt.executeUpdate()
            }
        }
    }

    fun getPendingDeviceLink(id: UUID): PendingDeviceLinkRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, one_time_code, expires_at, state, new_device_id, new_device_label,
                          new_device_kind, new_pubkey_format, new_pubkey, wrapped_master_key, wrap_format
                   FROM pending_device_links WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toPendingDeviceLinkRecord()
            }
        }
    }

    fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, one_time_code, expires_at, state, new_device_id, new_device_label,
                          new_device_kind, new_pubkey_format, new_pubkey, wrapped_master_key, wrap_format
                   FROM pending_device_links WHERE one_time_code = ?"""
            ).use { stmt ->
                stmt.setString(1, code)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toPendingDeviceLinkRecord()
            }
        }
    }

    fun registerNewDevice(
        id: UUID, deviceId: String, deviceLabel: String,
        deviceKind: String, pubkeyFormat: String, pubkey: ByteArray,
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """UPDATE pending_device_links SET
                       state = 'device_registered',
                       new_device_id = ?, new_device_label = ?, new_device_kind = ?,
                       new_pubkey_format = ?, new_pubkey = ?
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setString(1, deviceId)
                stmt.setString(2, deviceLabel)
                stmt.setString(3, deviceKind)
                stmt.setString(4, pubkeyFormat)
                stmt.setBytes(5, pubkey)
                stmt.setObject(6, id)
                stmt.executeUpdate()
            }
        }
    }

    fun completeDeviceLink(
        id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String,
        deviceId: String, deviceLabel: String, deviceKind: String,
        pubkeyFormat: String, pubkey: ByteArray,
    ) {
        withTransaction { conn ->
            conn.prepareStatement(
                "UPDATE pending_device_links SET state = 'wrap_complete', wrapped_master_key = ?, wrap_format = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setBytes(1, wrappedMasterKey)
                stmt.setString(2, wrapFormat)
                stmt.setObject(3, id)
                stmt.executeUpdate()
            }
            val now = Instant.now()
            conn.prepareStatement(
                """INSERT INTO wrapped_keys (id, device_id, device_label, device_kind, pubkey_format,
                       pubkey, wrapped_master_key, wrap_format, created_at, last_used_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setString(2, deviceId)
                stmt.setString(3, deviceLabel)
                stmt.setString(4, deviceKind)
                stmt.setString(5, pubkeyFormat)
                stmt.setBytes(6, pubkey)
                stmt.setBytes(7, wrappedMasterKey)
                stmt.setString(8, wrapFormat)
                stmt.setTimestamp(9, Timestamp.from(now))
                stmt.setTimestamp(10, Timestamp.from(now))
                stmt.executeUpdate()
            }
        }
    }

    fun deleteExpiredDeviceLinks(before: Instant): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM pending_device_links WHERE expires_at < ? AND state != 'wrap_complete'"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(before))
                return stmt.executeUpdate()
            }
        }
    }

    // ---- Upload migration ---------------------------------------------------

    fun migrateUploadToEncrypted(
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

    companion object {
        fun create(config: AppConfig): Database {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.dbUrl
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 10
                minimumIdle = 2
                connectionTimeout = 30_000
                idleTimeout = 600_000
            }
            return Database(HikariDataSource(hikari))
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────────

    fun insertDiagEvent(deviceLabel: String, tag: String, message: String, detail: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO diagnostic_events (device_label, tag, message, detail) VALUES (?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, deviceLabel)
                stmt.setString(2, tag)
                stmt.setString(3, message)
                stmt.setString(4, detail)
                stmt.executeUpdate()
            }
        }
    }

    fun listDiagEvents(limit: Int = 200): List<Map<String, String>> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, created_at, device_label, tag, message, detail FROM diagnostic_events ORDER BY created_at DESC LIMIT ?"
            ).use { stmt ->
                stmt.setInt(1, limit)
                val rs = stmt.executeQuery()
                val results = mutableListOf<Map<String, String>>()
                while (rs.next()) results.add(mapOf(
                    "id" to rs.getObject("id").toString(),
                    "createdAt" to rs.getTimestamp("created_at").toInstant().toString(),
                    "deviceLabel" to rs.getString("device_label"),
                    "tag" to rs.getString("tag"),
                    "message" to rs.getString("message"),
                    "detail" to rs.getString("detail"),
                ))
                return results
            }
        }
    }
}

internal fun UploadRecord.toJson(): String {
    val enc = java.util.Base64.getEncoder()
    val node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
    node.put("id", id.toString())
    node.put("storageKey", storageKey)
    node.put("storageClass", storageClass)
    node.put("mimeType", mimeType)
    node.put("fileSize", fileSize)
    node.put("uploadedAt", uploadedAt.toString())
    node.put("rotation", rotation)
    if (thumbnailKey != null) node.put("thumbnailKey", thumbnailKey) else node.putNull("thumbnailKey")
    val tagsNode = node.putArray("tags")
    tags.forEach { tagsNode.add(it) }
    if (takenAt != null) node.put("takenAt", takenAt.toString()) else node.putNull("takenAt")
    if (latitude != null) node.put("latitude", latitude) else node.putNull("latitude")
    if (longitude != null) node.put("longitude", longitude) else node.putNull("longitude")
    if (altitude != null) node.put("altitude", altitude) else node.putNull("altitude")
    if (deviceMake != null) node.put("deviceMake", deviceMake) else node.putNull("deviceMake")
    if (deviceModel != null) node.put("deviceModel", deviceModel) else node.putNull("deviceModel")
    if (compostedAt != null) node.put("compostedAt", compostedAt.toString()) else node.putNull("compostedAt")
    if (storageClass == "encrypted") {
        if (envelopeVersion != null) node.put("envelopeVersion", envelopeVersion)
        if (wrappedDek != null) node.put("wrappedDek", enc.encodeToString(wrappedDek))
        if (dekFormat != null) node.put("dekFormat", dekFormat)
        if (encryptedMetadata != null) node.put("encryptedMetadata", enc.encodeToString(encryptedMetadata))
        if (encryptedMetadataFormat != null) node.put("encryptedMetadataFormat", encryptedMetadataFormat)
        if (thumbnailStorageKey != null) node.put("thumbnailStorageKey", thumbnailStorageKey)
        if (wrappedThumbnailDek != null) node.put("wrappedThumbnailDek", enc.encodeToString(wrappedThumbnailDek))
        if (thumbnailDekFormat != null) node.put("thumbnailDekFormat", thumbnailDekFormat)
        if (previewStorageKey != null) node.put("previewStorageKey", previewStorageKey)
        if (wrappedPreviewDek != null) node.put("wrappedPreviewDek", enc.encodeToString(wrappedPreviewDek))
        if (previewDekFormat != null) node.put("previewDekFormat", previewDekFormat)
        if (plainChunkSize != null) node.put("plainChunkSize", plainChunkSize)
    }
    if (durationSeconds != null) node.put("durationSeconds", durationSeconds)
    return node.toString()
}

private fun java.sql.ResultSet.toCapsuleRecord() = CapsuleRecord(
    id = getObject("id", UUID::class.java),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
    createdByUser = getString("created_by_user"),
    shape = CapsuleShape.valueOf(getString("shape").uppercase()),
    state = CapsuleState.valueOf(getString("state").uppercase()),
    unlockAt = getObject("unlock_at", OffsetDateTime::class.java),
    cancelledAt = getTimestamp("cancelled_at")?.toInstant(),
    deliveredAt = getTimestamp("delivered_at")?.toInstant(),
)

private fun java.sql.ResultSet.toUploadRecord() = UploadRecord(
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
)

private fun java.sql.ResultSet.toWrappedKeyRecord() = WrappedKeyRecord(
    id = getObject("id", UUID::class.java),
    deviceId = getString("device_id"),
    deviceLabel = getString("device_label"),
    deviceKind = getString("device_kind"),
    pubkeyFormat = getString("pubkey_format"),
    pubkey = getBytes("pubkey"),
    wrappedMasterKey = getBytes("wrapped_master_key"),
    wrapFormat = getString("wrap_format"),
    createdAt = getTimestamp("created_at").toInstant(),
    lastUsedAt = getTimestamp("last_used_at").toInstant(),
    retiredAt = getTimestamp("retired_at")?.toInstant(),
)

private fun java.sql.ResultSet.toRecoveryPassphraseRecord() = RecoveryPassphraseRecord(
    wrappedMasterKey = getBytes("wrapped_master_key"),
    wrapFormat = getString("wrap_format"),
    argon2Params = getString("argon2_params"),
    salt = getBytes("salt"),
    createdAt = getTimestamp("created_at").toInstant(),
    updatedAt = getTimestamp("updated_at").toInstant(),
)

private fun java.sql.ResultSet.toPendingDeviceLinkRecord() = PendingDeviceLinkRecord(
    id = getObject("id", UUID::class.java),
    oneTimeCode = getString("one_time_code"),
    expiresAt = getTimestamp("expires_at").toInstant(),
    state = getString("state"),
    newDeviceId = getString("new_device_id"),
    newDeviceLabel = getString("new_device_label"),
    newDeviceKind = getString("new_device_kind"),
    newPubkeyFormat = getString("new_pubkey_format"),
    newPubkey = getBytes("new_pubkey"),
    wrappedMasterKey = getBytes("wrapped_master_key"),
    wrapFormat = getString("wrap_format"),
)
