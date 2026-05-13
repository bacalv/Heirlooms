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

// ---- Auth domain model --------------------------------------------------

val FOUNDING_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

data class UserRecord(
    val id: UUID,
    val username: String,
    val displayName: String,
    val authVerifier: ByteArray?,
    val authSalt: ByteArray?,
    val createdAt: java.time.Instant,
)

data class UserSessionRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: ByteArray,
    val deviceKind: String,
    val createdAt: java.time.Instant,
    val lastUsedAt: java.time.Instant,
    val expiresAt: java.time.Instant,
)

data class InviteRecord(
    val id: UUID,
    val token: String,
    val createdBy: UUID,
    val createdAt: java.time.Instant,
    val expiresAt: java.time.Instant,
    val usedAt: java.time.Instant?,
    val usedBy: UUID?,
)

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
    val sharedFromUploadId: UUID? = null,
    val sharedFromUserId: UUID? = null,
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
    val userId: UUID? = null,
    val webSessionId: String? = null,
    val rawSessionToken: String? = null,
    val sessionExpiresAt: Instant? = null,
)

// ---- Sort options for uploads -------------------------------------------

enum class UploadSort { UPLOAD_NEWEST, UPLOAD_OLDEST, TAKEN_NEWEST, TAKEN_OLDEST }

private data class DecodedCursor(val sort: UploadSort, val sortKeyMs: Long?, val id: UUID)

// ---- Sharing / social domain model --------------------------------------

data class AccountSharingKeyRecord(
    val userId: UUID,
    val pubkey: ByteArray,
    val wrappedPrivkey: ByteArray,
    val wrapFormat: String,
)

data class FriendRecord(
    val userId: UUID,
    val username: String,
    val displayName: String,
)

// ---- Plot domain model -----------------------------------------------

data class PlotRecord(
    val id: UUID,
    val ownerUserId: UUID?,
    val name: String,
    val sortOrder: Int,
    val isSystemDefined: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val criteria: String?,
    val showInGarden: Boolean,
    val visibility: String,
)

// ---- Flow domain model -----------------------------------------------

data class FlowRecord(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val criteria: String,
    val targetPlotId: UUID,
    val requiresStaging: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PlotItemRecord(
    val id: UUID,
    val plotId: UUID,
    val uploadId: UUID,
    val addedBy: UUID,
    val sourceFlowId: UUID?,
    val addedAt: Instant,
)

data class PlotItemWithUpload(
    val upload: UploadRecord,
    val addedBy: UUID,
    val wrappedItemDek: ByteArray?,
    val itemDekFormat: String?,
    val wrappedThumbnailDek: ByteArray?,
    val thumbnailDekFormat: String?,
)

data class PlotMemberRecord(
    val plotId: UUID,
    val userId: UUID,
    val displayName: String,
    val username: String,
    val role: String,
    val wrappedPlotKey: ByteArray?,
    val plotKeyFormat: String?,
    val joinedAt: Instant,
)

data class PlotInviteRecord(
    val id: UUID,
    val plotId: UUID,
    val createdBy: UUID,
    val token: String,
    val recipientUserId: UUID?,
    val recipientPubkey: String?,
    val usedBy: UUID?,
    val expiresAt: Instant,
    val createdAt: Instant,
)

class Database(private val dataSource: DataSource) {

    fun runMigrations() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun recordUpload(record: UploadRecord, userId: UUID = FOUNDING_USER_ID) {
        dataSource.connection.use { conn: Connection ->
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

    fun findByContentHash(hash: String, userId: UUID = FOUNDING_USER_ID): UploadRecord? {
        dataSource.connection.use { conn: Connection ->
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

    fun existsByContentHash(hash: String, userId: UUID): Boolean {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "SELECT 1 FROM uploads WHERE content_hash = ? AND user_id = ? AND composted_at IS NULL LIMIT 1"
            ).use { stmt ->
                stmt.setString(1, hash)
                stmt.setObject(2, userId)
                return stmt.executeQuery().next()
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

    fun findUploadByIdForUser(id: UUID, userId: UUID = FOUNDING_USER_ID): UploadRecord? {
        dataSource.connection.use { conn: Connection ->
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

    fun recordView(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "UPDATE uploads SET last_viewed_at = NOW() WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun listUploads(tag: String? = null, excludeTag: String? = null, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
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

    fun listCompostedUploads(userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
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

    sealed class CompostResult {
        data class Success(val record: UploadRecord) : CompostResult()
        object NotFound : CompostResult()
        object AlreadyComposted : CompostResult()
        object PreconditionFailed : CompostResult()
    }

    fun compostUpload(id: UUID, userId: UUID = FOUNDING_USER_ID): CompostResult {
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
                if (!rs.next()) return CompostResult.NotFound
                val compostedAt = rs.getTimestamp("composted_at")
                if (compostedAt != null) return CompostResult.AlreadyComposted
                if (!canCompost(id, conn)) return CompostResult.PreconditionFailed
            }
            conn.prepareStatement("UPDATE uploads SET composted_at = NOW() WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }
        return findUploadByIdForUser(id, userId)?.let { CompostResult.Success(it) } ?: CompostResult.NotFound
    }

    sealed class RestoreResult {
        data class Success(val record: UploadRecord) : RestoreResult()
        object NotFound : RestoreResult()
        object NotComposted : RestoreResult()
    }

    fun restoreUpload(id: UUID, userId: UUID = FOUNDING_USER_ID): RestoreResult {
        withTransaction { conn ->
            conn.prepareStatement(
                "SELECT composted_at FROM uploads WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RestoreResult.NotFound
                val compostedAt = rs.getTimestamp("composted_at")
                if (compostedAt == null) return RestoreResult.NotComposted
            }
            conn.prepareStatement("UPDATE uploads SET composted_at = NULL WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }
        return findUploadByIdForUser(id, userId)?.let { RestoreResult.Success(it) } ?: RestoreResult.NotFound
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

    // Returns true if any active (non-composted) upload for any user references the same storage_key.
    // Used by compost cleanup to skip GCS deletion when a shared copy is still alive.
    fun userAlreadyHasStorageKey(userId: UUID, storageKey: String): Boolean {
        dataSource.connection.use { conn: Connection ->
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

    fun hasLiveSharedReference(storageKey: String, excludeUploadId: UUID): Boolean {
        dataSource.connection.use { conn: Connection ->
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

    // ---- Sharing / social operations ---------------------------------------

    fun upsertSharingKey(userId: UUID, pubkey: ByteArray, wrappedPrivkey: ByteArray, wrapFormat: String) {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """INSERT INTO account_sharing_keys (user_id, pubkey, wrapped_privkey, wrap_format)
                   VALUES (?, ?, ?, ?)
                   ON CONFLICT (user_id) DO UPDATE
                   SET pubkey = EXCLUDED.pubkey, wrapped_privkey = EXCLUDED.wrapped_privkey,
                       wrap_format = EXCLUDED.wrap_format"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, java.util.Base64.getEncoder().encodeToString(pubkey))
                stmt.setString(3, java.util.Base64.getEncoder().encodeToString(wrappedPrivkey))
                stmt.setString(4, wrapFormat)
                stmt.executeUpdate()
            }
        }
    }

    fun getSharingKey(userId: UUID): AccountSharingKeyRecord? {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "SELECT user_id, pubkey, wrapped_privkey, wrap_format FROM account_sharing_keys WHERE user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                val dec = java.util.Base64.getDecoder()
                return AccountSharingKeyRecord(
                    userId = rs.getObject("user_id", UUID::class.java),
                    pubkey = dec.decode(rs.getString("pubkey")),
                    wrappedPrivkey = dec.decode(rs.getString("wrapped_privkey")),
                    wrapFormat = rs.getString("wrap_format"),
                )
            }
        }
    }

    fun listFriends(userId: UUID): List<FriendRecord> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT u.id, u.username, u.display_name
                   FROM friendships f
                   JOIN users u ON u.id = CASE WHEN f.user_id_1 = ? THEN f.user_id_2 ELSE f.user_id_1 END
                   WHERE f.user_id_1 = ? OR f.user_id_2 = ?
                   ORDER BY u.display_name"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setObject(2, userId)
                stmt.setObject(3, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<FriendRecord>()
                while (rs.next()) results.add(
                    FriendRecord(
                        userId = rs.getObject("id", UUID::class.java),
                        username = rs.getString("username"),
                        displayName = rs.getString("display_name"),
                    )
                )
                return results
            }
        }
    }

    fun createFriendship(a: UUID, b: UUID) {
        val u1 = if (a.toString() < b.toString()) a else b
        val u2 = if (a.toString() < b.toString()) b else a
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "INSERT INTO friendships (user_id_1, user_id_2) VALUES (?, ?) ON CONFLICT DO NOTHING"
            ).use { stmt ->
                stmt.setObject(1, u1)
                stmt.setObject(2, u2)
                stmt.executeUpdate()
            }
        }
    }

    fun areFriends(a: UUID, b: UUID): Boolean {
        val u1 = if (a.toString() < b.toString()) a else b
        val u2 = if (a.toString() < b.toString()) b else a
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM friendships WHERE user_id_1 = ? AND user_id_2 = ?)"
            ).use { stmt ->
                stmt.setObject(1, u1)
                stmt.setObject(2, u2)
                val rs = stmt.executeQuery()
                return rs.next() && rs.getBoolean(1)
            }
        }
    }

    fun createSharedUpload(
        fromRecord: UploadRecord,
        fromUserId: UUID,
        toUserId: UUID,
        wrappedDek: ByteArray,
        wrappedThumbnailDek: ByteArray?,
        dekFormat: String,
        rotationOverride: Int? = null,
    ): UploadRecord {
        val newId = UUID.randomUUID()
        dataSource.connection.use { conn: Connection ->
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

    fun updateRotation(id: UUID, rotation: Int, userId: UUID = FOUNDING_USER_ID): Boolean {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("UPDATE uploads SET rotation = ? WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setInt(1, rotation)
                stmt.setObject(2, id)
                stmt.setObject(3, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun updateTags(id: UUID, tags: List<String>, userId: UUID = FOUNDING_USER_ID): Boolean {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("UPDATE uploads SET tags = ? WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setArray(1, conn.createArrayOf("text", tags.toTypedArray()))
                stmt.setObject(2, id)
                stmt.setObject(3, userId)
                val updated = stmt.executeUpdate() > 0
                if (updated) runUnstagedFlowsForUpload(conn, id, userId)
                return updated
            }
        }
    }

    // Inserts upload into plot_items for every unstaged flow whose criteria it satisfies.
    private fun runUnstagedFlowsForUpload(conn: Connection, uploadId: UUID, userId: UUID) {
        val flows = listFlows(userId).filter { !it.requiresStaging }
        for (flow in flows) {
            try {
                val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
                conn.prepareStatement(
                    """INSERT INTO plot_items (upload_id, plot_id, source_flow_id, added_by)
                       SELECT id, ?, ?, ?
                       FROM uploads
                       WHERE id = ? AND user_id = ? AND (${fragment.sql})
                       ON CONFLICT (plot_id, upload_id) DO NOTHING"""
                ).use { stmt ->
                    var idx = 1
                    stmt.setObject(idx++, flow.targetPlotId)
                    stmt.setObject(idx++, flow.id)
                    stmt.setObject(idx++, userId)
                    stmt.setObject(idx++, uploadId)
                    stmt.setObject(idx++, userId)
                    for (setter in fragment.setters) idx = setter(stmt, idx)
                    stmt.executeUpdate()
                }
            } catch (_: Exception) { /* best-effort; bad criteria skipped */ }
        }
    }

    // Bulk-inserts all existing matching uploads into plot_items for a new unstaged flow.
    private fun autoPopulateFlow(conn: Connection, flow: FlowRecord, userId: UUID) {
        try {
            val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
            conn.prepareStatement(
                """INSERT INTO plot_items (upload_id, plot_id, source_flow_id, added_by)
                   SELECT id, ?, ?, ?
                   FROM uploads
                   WHERE user_id = ? AND composted_at IS NULL AND (${fragment.sql})
                   ON CONFLICT (plot_id, upload_id) DO NOTHING"""
            ).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, flow.targetPlotId)
                stmt.setObject(idx++, flow.id)
                stmt.setObject(idx++, userId)
                stmt.setObject(idx++, userId)
                for (setter in fragment.setters) idx = setter(stmt, idx)
                stmt.executeUpdate()
            }
        } catch (_: Exception) { /* bad criteria — skip */ }
    }

    // ---- Capsule operations ------------------------------------------------

    fun uploadExists(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
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
        userId: UUID = FOUNDING_USER_ID,
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

    fun getCapsuleById(id: UUID, userId: UUID = FOUNDING_USER_ID): CapsuleDetail? {
        dataSource.connection.use { conn ->
            val record = conn.prepareStatement(
                """SELECT id, created_at, updated_at, created_by_user, shape, state,
                          unlock_at, cancelled_at, delivered_at
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

    fun listCapsules(states: List<CapsuleState>, orderBy: String, userId: UUID = FOUNDING_USER_ID): List<CapsuleSummary> {
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
        userId: UUID = FOUNDING_USER_ID,
        unlockAt: OffsetDateTime?,
        recipients: List<String>?,
        uploadIds: List<UUID>?,
        message: String?,
    ): UpdateResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
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
                    val exists = conn.prepareStatement("SELECT 1 FROM uploads WHERE id = ? AND user_id = ?").use { stmt ->
                        stmt.setObject(1, uid)
                        stmt.setObject(2, userId)
                        stmt.executeQuery().next()
                    }
                    if (!exists) return UpdateResult.UnknownUpload
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
        return getCapsuleById(id, userId)?.let { UpdateResult.Success(it) } ?: UpdateResult.NotFound
    }

    sealed class SealResult {
        data class Success(val detail: CapsuleDetail) : SealResult()
        object NotFound : SealResult()
        object WrongState : SealResult()
        object Empty : SealResult()
    }

    fun sealCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID): SealResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
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
        return getCapsuleById(id, userId)?.let { SealResult.Success(it) } ?: SealResult.NotFound
    }

    sealed class CancelResult {
        data class Success(val detail: CapsuleDetail) : CancelResult()
        object NotFound : CancelResult()
        object WrongState : CancelResult()
    }

    fun cancelCapsule(id: UUID, userId: UUID = FOUNDING_USER_ID): CancelResult {
        withTransaction { conn ->
            val record = conn.prepareStatement(
                "SELECT id, created_at, updated_at, created_by_user, shape, state, unlock_at, cancelled_at, delivered_at FROM capsules WHERE id = ? AND user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return CancelResult.NotFound
                rs.toCapsuleRecord()
            }

            if (record.state != CapsuleState.OPEN && record.state != CapsuleState.SEALED) {
                return CancelResult.WrongState
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
        return getCapsuleById(id, userId)?.let { CancelResult.Success(it) } ?: CancelResult.NotFound
    }

    fun getCapsulesForUpload(uploadId: UUID, userId: UUID = FOUNDING_USER_ID): List<CapsuleSummary>? {
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
        mediaType: String? = null,
        isReceived: Boolean? = null,
        plotId: UUID? = null,
        userId: UUID = FOUNDING_USER_ID,
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

            // Always scope to the authenticated user
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

                if (mediaType == "image") conditions += "mime_type LIKE 'image/%'"
                else if (mediaType == "video") conditions += "mime_type LIKE 'video/%'"

                if (isReceived == true) conditions += "shared_from_user_id IS NOT NULL"
                else if (isReceived == false) conditions += "shared_from_user_id IS NULL"
            }

            if (plotId != null) {
                val plot = getPlotByIdForUser(conn, plotId, userId)
                    ?: throw IllegalArgumentException("Plot $plotId not found")
                val criteriaJson = plot.criteria
                if (criteriaJson != null) {
                    val fragment = CriteriaEvaluator.evaluate(criteriaJson, userId, conn)
                    conditions += fragment.sql
                    setters += fragment.setters
                } else {
                    // Collection plot: only return items explicitly added via plot_items
                    conditions += "id IN (SELECT upload_id FROM plot_items WHERE plot_id = ?)"
                    setters += listOf { stmt, idx -> stmt.setObject(idx, plotId); idx + 1 }
                }
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

    fun listCompostedUploadsPaginated(cursor: String? = null, limit: Int = 50, userId: UUID = FOUNDING_USER_ID): UploadPage {
        val effectiveLimit = limit.coerceIn(1, 200)
        val decoded = cursor?.let { decodeCompostedCursor(it) }
        dataSource.connection.use { conn: Connection ->
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

    fun listAllTags(userId: UUID = FOUNDING_USER_ID): List<String> {
        dataSource.connection.use { conn: Connection ->
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

    fun listPlots(userId: UUID = FOUNDING_USER_ID): List<PlotRecord> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT DISTINCT p.id, p.owner_user_id, p.name, p.sort_order, p.is_system_defined,
                          p.created_at, p.updated_at, p.criteria, p.show_in_garden, p.visibility
                   FROM plots p
                   WHERE p.owner_user_id = ?
                      OR (p.visibility IN ('shared', 'public') AND EXISTS (
                          SELECT 1 FROM plot_members pm WHERE pm.plot_id = p.id AND pm.user_id = ?
                      ))
                   ORDER BY p.sort_order ASC, p.created_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<PlotRecord>()
                while (rs.next()) {
                    results.add(rs.toPlotRecord())
                }
                return results
            }
        }
    }

    fun getPlotById(id: UUID): PlotRecord? {
        dataSource.connection.use { conn: Connection ->
            return getPlotByIdConn(conn, id)
        }
    }

    private fun getPlotByIdConn(conn: Connection, id: UUID): PlotRecord? =
        conn.prepareStatement(
            """SELECT id, owner_user_id, name, sort_order, is_system_defined,
                      created_at, updated_at, criteria, show_in_garden, visibility
               FROM plots WHERE id = ?"""
        ).use { stmt ->
            stmt.setObject(1, id)
            val rs = stmt.executeQuery()
            if (!rs.next()) null else rs.toPlotRecord()
        }

    private fun java.sql.ResultSet.toPlotRecord() = PlotRecord(
        id = getObject("id", UUID::class.java),
        ownerUserId = getObject("owner_user_id", UUID::class.java),
        name = getString("name"),
        sortOrder = getInt("sort_order"),
        isSystemDefined = getBoolean("is_system_defined"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
        criteria = getString("criteria"),
        showInGarden = getBoolean("show_in_garden"),
        visibility = getString("visibility"),
    )

    fun createPlot(
        name: String,
        criteria: String?,
        showInGarden: Boolean,
        visibility: String,
        wrappedPlotKeyB64: String? = null,
        plotKeyFormat: String? = null,
        userId: UUID = FOUNDING_USER_ID,
    ): PlotRecord {
        val id = UUID.randomUUID()
        val now = Instant.now()
        withTransaction { conn ->
            conn.prepareStatement(
                """INSERT INTO plots (id, owner_user_id, name, criteria, show_in_garden, visibility, created_at, updated_at)
                   VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.setString(3, name.trim())
                stmt.setString(4, criteria)
                stmt.setBoolean(5, showInGarden)
                stmt.setString(6, visibility)
                stmt.setTimestamp(7, Timestamp.from(now))
                stmt.setTimestamp(8, Timestamp.from(now))
                stmt.executeUpdate()
            }
            if (visibility == "shared" && wrappedPlotKeyB64 != null && plotKeyFormat != null) {
                val keyBytes = java.util.Base64.getDecoder().decode(wrappedPlotKeyB64)
                conn.prepareStatement(
                    """INSERT INTO plot_members (plot_id, user_id, role, wrapped_plot_key, plot_key_format)
                       VALUES (?, ?, 'owner', ?, ?)"""
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, userId)
                    stmt.setBytes(3, keyBytes)
                    stmt.setString(4, plotKeyFormat)
                    stmt.executeUpdate()
                }
            }
        }
        return getPlotById(id)!!
    }

    sealed class PlotUpdateResult {
        data class Success(val plot: PlotRecord) : PlotUpdateResult()
        object NotFound : PlotUpdateResult()
        object SystemDefined : PlotUpdateResult()
    }

    fun updatePlot(
        id: UUID,
        name: String?,
        sortOrder: Int?,
        criteria: String?,
        showInGarden: Boolean?,
        userId: UUID = FOUNDING_USER_ID,
    ): PlotUpdateResult {
        withTransaction { conn ->
            val (isSystemDefined) = conn.prepareStatement(
                "SELECT is_system_defined FROM plots WHERE id = ? AND owner_user_id = ? FOR UPDATE"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return PlotUpdateResult.NotFound
                listOf(rs.getBoolean("is_system_defined"))
            }
            if (isSystemDefined) return PlotUpdateResult.SystemDefined

            val setClauses = mutableListOf("updated_at = ?")
            if (name != null) setClauses.add("name = ?")
            if (sortOrder != null) setClauses.add("sort_order = ?")
            if (criteria != null) setClauses.add("criteria = ?::jsonb")
            if (showInGarden != null) setClauses.add("show_in_garden = ?")
            conn.prepareStatement("UPDATE plots SET ${setClauses.joinToString(", ")} WHERE id = ? AND owner_user_id = ?").use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (name != null) stmt.setString(idx++, name.trim())
                if (sortOrder != null) stmt.setInt(idx++, sortOrder)
                if (criteria != null) stmt.setString(idx++, criteria)
                if (showInGarden != null) stmt.setBoolean(idx++, showInGarden)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, userId)
                stmt.executeUpdate()
            }
        }
        return getPlotById(id)?.let { PlotUpdateResult.Success(it) } ?: PlotUpdateResult.NotFound
    }

    sealed class PlotDeleteResult {
        object Success : PlotDeleteResult()
        object NotFound : PlotDeleteResult()
        object SystemDefined : PlotDeleteResult()
    }

    sealed class LeavePlotResult {
        object Success : LeavePlotResult()
        object NotFound : LeavePlotResult()
        object IsOwner : LeavePlotResult()
    }

    fun leavePlot(plotId: UUID, userId: UUID): LeavePlotResult {
        val plot = getPlotById(plotId) ?: return LeavePlotResult.NotFound
        if (plot.ownerUserId == userId) return LeavePlotResult.IsOwner
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM plot_members WHERE plot_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId)
                stmt.setObject(2, userId)
                return if (stmt.executeUpdate() > 0) LeavePlotResult.Success else LeavePlotResult.NotFound
            }
        }
    }

    fun deletePlot(id: UUID, userId: UUID = FOUNDING_USER_ID): PlotDeleteResult {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement("SELECT is_system_defined FROM plots WHERE id = ? AND owner_user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return PlotDeleteResult.NotFound
                if (rs.getBoolean("is_system_defined")) return PlotDeleteResult.SystemDefined
            }
            conn.prepareStatement("DELETE FROM plots WHERE id = ? AND owner_user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
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

    fun batchReorderPlots(updates: List<Pair<UUID, Int>>, userId: UUID = FOUNDING_USER_ID): BatchReorderResult {
        withTransaction { conn ->
            for ((id, _) in updates) {
                conn.prepareStatement(
                    "SELECT is_system_defined FROM plots WHERE id = ? AND owner_user_id = ? FOR UPDATE"
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, userId)
                    val rs = stmt.executeQuery()
                    if (!rs.next()) return BatchReorderResult.NotFound
                    if (rs.getBoolean("is_system_defined")) return BatchReorderResult.SystemDefined
                }
            }
            val now = Timestamp.from(Instant.now())
            for ((id, sortOrder) in updates) {
                conn.prepareStatement(
                    "UPDATE plots SET sort_order = ?, updated_at = ? WHERE id = ? AND owner_user_id = ?"
                ).use { stmt ->
                    stmt.setInt(1, sortOrder)
                    stmt.setTimestamp(2, now)
                    stmt.setObject(3, id)
                    stmt.setObject(4, userId)
                    stmt.executeUpdate()
                }
            }
        }
        return BatchReorderResult.Success
    }

    fun withCriteriaValidation(node: com.fasterxml.jackson.databind.JsonNode, userId: UUID) {
        dataSource.connection.use { conn: Connection ->
            CriteriaEvaluator.validate(node, userId, conn)
        }
    }

    private fun getPlotByIdForUser(conn: Connection, id: UUID, userId: UUID): PlotRecord? {
        val plot = getPlotByIdConn(conn, id) ?: return null
        return when {
            plot.ownerUserId == userId -> plot
            plot.visibility == "shared" && isMemberConn(conn, id, userId) -> plot
            else -> null
        }
    }

    fun isMember(plotId: UUID, userId: UUID): Boolean {
        dataSource.connection.use { conn -> return isMemberConn(conn, plotId, userId) }
    }

    private fun isMemberConn(conn: Connection, plotId: UUID, userId: UUID): Boolean =
        conn.prepareStatement(
            "SELECT 1 FROM plot_members WHERE plot_id = ? AND user_id = ?"
        ).use { stmt ->
            stmt.setObject(1, plotId); stmt.setObject(2, userId)
            stmt.executeQuery().next()
        }

    // ---- Flow operations --------------------------------------------------

    fun listFlows(userId: UUID = FOUNDING_USER_ID): List<FlowRecord> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at
                   FROM flows WHERE user_id = ? ORDER BY created_at ASC"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<FlowRecord>()
                while (rs.next()) results.add(rs.toFlowRecord())
                return results
            }
        }
    }

    fun getFlowById(id: UUID, userId: UUID = FOUNDING_USER_ID): FlowRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at
                   FROM flows WHERE id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toFlowRecord()
            }
        }
    }

    sealed class FlowCreateResult {
        data class Success(val flow: FlowRecord) : FlowCreateResult()
        data class Error(val message: String) : FlowCreateResult()
    }

    fun createFlow(
        name: String,
        criteriaJson: String,
        targetPlotId: UUID,
        requiresStaging: Boolean,
        userId: UUID = FOUNDING_USER_ID,
    ): FlowCreateResult {
        val targetPlot = getPlotById(targetPlotId) ?: return FlowCreateResult.Error("Target plot not found")
        if (targetPlot.ownerUserId != userId) return FlowCreateResult.Error("Target plot not found")
        if (targetPlot.criteria != null) return FlowCreateResult.Error("Target plot must be a collection plot (criteria IS NULL)")

        // Staging policy: private plots never need staging (your own content);
        // public plots always require staging; shared plots respect the caller's preference.
        val effectiveStaging = when (targetPlot.visibility) {
            "private" -> false
            "public"  -> true
            else      -> requiresStaging
        }

        val id = UUID.randomUUID()
        val now = Instant.now()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO flows (id, user_id, name, criteria, target_plot_id, requires_staging, created_at, updated_at)
                   VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.setString(3, name.trim())
                stmt.setString(4, criteriaJson)
                stmt.setObject(5, targetPlotId)
                stmt.setBoolean(6, effectiveStaging)
                stmt.setTimestamp(7, Timestamp.from(now))
                stmt.setTimestamp(8, Timestamp.from(now))
                stmt.executeUpdate()
            }
            val flow = getFlowById(id, userId)!!
            if (!effectiveStaging) autoPopulateFlow(conn, flow, userId)
            return FlowCreateResult.Success(flow)
        }
    }

    sealed class FlowUpdateResult {
        data class Success(val flow: FlowRecord) : FlowUpdateResult()
        object NotFound : FlowUpdateResult()
    }

    fun updateFlow(
        id: UUID,
        name: String?,
        criteriaJson: String?,
        requiresStaging: Boolean?,
        userId: UUID = FOUNDING_USER_ID,
    ): FlowUpdateResult {
        val existingFlow = getFlowById(id, userId) ?: return FlowUpdateResult.NotFound
        val targetPlot = getPlotById(existingFlow.targetPlotId)

        // Enforce same staging policy as createFlow
        val effectiveStaging = requiresStaging?.let {
            when (targetPlot?.visibility) {
                "private" -> false
                "public"  -> true
                else      -> it
            }
        }

        val setClauses = mutableListOf("updated_at = ?")
        if (name != null) setClauses.add("name = ?")
        if (criteriaJson != null) setClauses.add("criteria = ?::jsonb")
        if (effectiveStaging != null) setClauses.add("requires_staging = ?")

        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE flows SET ${setClauses.joinToString(", ")} WHERE id = ? AND user_id = ?"
            ).use { stmt ->
                var idx = 1
                stmt.setTimestamp(idx++, Timestamp.from(Instant.now()))
                if (name != null) stmt.setString(idx++, name.trim())
                if (criteriaJson != null) stmt.setString(idx++, criteriaJson)
                if (effectiveStaging != null) stmt.setBoolean(idx++, effectiveStaging)
                stmt.setObject(idx++, id)
                stmt.setObject(idx, userId)
                val updated = stmt.executeUpdate()
                if (updated == 0) return FlowUpdateResult.NotFound
            }
            val updatedFlow = getFlowById(id, userId) ?: return FlowUpdateResult.NotFound
            // If flow is now unstaged (newly or due to criteria change), populate missing items
            if (!updatedFlow.requiresStaging) autoPopulateFlow(conn, updatedFlow, userId)
            return FlowUpdateResult.Success(updatedFlow)
        }
    }

    fun deleteFlow(id: UUID, userId: UUID = FOUNDING_USER_ID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM flows WHERE id = ? AND user_id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // ---- Staging operations -----------------------------------------------

    fun getStagingItems(flowId: UUID, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        val flow = getFlowById(flowId, userId) ?: return emptyList()
        val plotId = flow.targetPlotId

        dataSource.connection.use { conn ->
            val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
            val sql = buildString {
                append("""SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                                  thumbnail_key, taken_at, latitude, longitude, altitude,
                                  device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                                  last_viewed_at, storage_class, envelope_version, wrapped_dek, dek_format,
                                  encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                                  wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                                  wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds,
                                  shared_from_upload_id, shared_from_user_id
                           FROM uploads
                           WHERE user_id = ?
                             AND composted_at IS NULL
                             AND (""")
                append(fragment.sql)
                append(""")
                             AND NOT EXISTS (SELECT 1 FROM plot_staging_decisions psd
                                            WHERE psd.plot_id = ? AND psd.upload_id = uploads.id)
                             AND NOT EXISTS (SELECT 1 FROM plot_items pi
                                            WHERE pi.plot_id = ? AND pi.upload_id = uploads.id)
                           ORDER BY uploaded_at DESC, id DESC""")
            }
            conn.prepareStatement(sql).use { stmt ->
                var idx = 1
                stmt.setObject(idx++, userId)
                for (setter in fragment.setters) idx = setter(stmt, idx)
                stmt.setObject(idx++, plotId)
                stmt.setObject(idx, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    fun getStagingItemsForPlot(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        val flows = listFlows(userId).filter { it.targetPlotId == plotId && it.requiresStaging }
        if (flows.isEmpty()) return emptyList()

        dataSource.connection.use { conn ->
            val allItems = mutableMapOf<UUID, UploadRecord>()
            for (flow in flows) {
                try {
                    val fragment = CriteriaEvaluator.evaluate(flow.criteria, userId, conn)
                    val sql = buildString {
                        append("""SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                                          thumbnail_key, taken_at, latitude, longitude, altitude,
                                          device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                                          last_viewed_at, storage_class, envelope_version, wrapped_dek, dek_format,
                                          encrypted_metadata, encrypted_metadata_format, thumbnail_storage_key,
                                          wrapped_thumbnail_dek, thumbnail_dek_format, preview_storage_key,
                                          wrapped_preview_dek, preview_dek_format, plain_chunk_size, duration_seconds,
                                          shared_from_upload_id, shared_from_user_id
                                   FROM uploads
                                   WHERE user_id = ?
                                     AND composted_at IS NULL
                                     AND (""")
                        append(fragment.sql)
                        append(""")
                                     AND NOT EXISTS (SELECT 1 FROM plot_staging_decisions psd
                                                    WHERE psd.plot_id = ? AND psd.upload_id = uploads.id)
                                     AND NOT EXISTS (SELECT 1 FROM plot_items pi
                                                    WHERE pi.plot_id = ? AND pi.upload_id = uploads.id)
                                   ORDER BY uploaded_at DESC, id DESC""")
                    }
                    conn.prepareStatement(sql).use { stmt ->
                        var idx = 1
                        stmt.setObject(idx++, userId)
                        for (setter in fragment.setters) idx = setter(stmt, idx)
                        stmt.setObject(idx++, plotId)
                        stmt.setObject(idx, plotId)
                        val rs = stmt.executeQuery()
                        while (rs.next()) {
                            val u = rs.toUploadRecord()
                            allItems[u.id] = u
                        }
                    }
                } catch (_: CriteriaValidationException) { /* skip invalid flow */ }
            }
            return allItems.values.sortedByDescending { it.uploadedAt }
        }
    }

    sealed class ApproveResult {
        object Success : ApproveResult()
        object NotFound : ApproveResult()
        object AlreadyApproved : ApproveResult()
        object PlotNotOwned : ApproveResult()
    }

    fun approveStagingItem(
        plotId: UUID,
        uploadId: UUID,
        sourceFlowId: UUID?,
        userId: UUID = FOUNDING_USER_ID,
        wrappedItemDekBytes: ByteArray? = null,
        itemDekFormat: String? = null,
        wrappedThumbnailDekBytes: ByteArray? = null,
        thumbnailDekFormat: String? = null,
    ): ApproveResult {
        val plot = getPlotById(plotId) ?: return ApproveResult.NotFound
        val isOwner = plot.ownerUserId == userId
        val isMember = plot.visibility == "shared" && isMember(plotId, userId)
        if (!isOwner && !isMember) return ApproveResult.PlotNotOwned

        withTransaction { conn ->
            val exists = conn.prepareStatement(
                "SELECT 1 FROM plot_items WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.executeQuery().next()
            }
            if (exists) return ApproveResult.AlreadyApproved

            conn.prepareStatement(
                """INSERT INTO plot_items (plot_id, upload_id, added_by, source_flow_id,
                   wrapped_item_dek, item_dek_format, wrapped_thumbnail_dek, thumbnail_dek_format, added_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.setObject(3, userId); stmt.setObject(4, sourceFlowId)
                stmt.setBytes(5, wrappedItemDekBytes); stmt.setString(6, itemDekFormat)
                stmt.setBytes(7, wrappedThumbnailDekBytes); stmt.setString(8, thumbnailDekFormat)
                stmt.executeUpdate()
            }

            conn.prepareStatement(
                """INSERT INTO plot_staging_decisions (plot_id, upload_id, decision, source_flow_id)
                   VALUES (?, ?, 'approved', ?)
                   ON CONFLICT (plot_id, upload_id) DO UPDATE SET decision = 'approved', decided_at = NOW()"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId); stmt.setObject(3, sourceFlowId)
                stmt.executeUpdate()
            }
        }
        return ApproveResult.Success
    }

    sealed class RejectResult {
        object Success : RejectResult()
        object NotFound : RejectResult()
        object AlreadyApproved : RejectResult()
        object PlotNotOwned : RejectResult()
    }

    fun rejectStagingItem(plotId: UUID, uploadId: UUID, sourceFlowId: UUID?, userId: UUID = FOUNDING_USER_ID): RejectResult {
        val plot = getPlotById(plotId) ?: return RejectResult.NotFound
        if (plot.ownerUserId != userId) return RejectResult.PlotNotOwned

        dataSource.connection.use { conn ->
            // Check not already approved (in plot_items)
            val approved = conn.prepareStatement(
                "SELECT 1 FROM plot_items WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.executeQuery().next()
            }
            if (approved) return RejectResult.AlreadyApproved

            conn.prepareStatement(
                """INSERT INTO plot_staging_decisions (plot_id, upload_id, decision, source_flow_id)
                   VALUES (?, ?, 'rejected', ?)
                   ON CONFLICT (plot_id, upload_id) DO UPDATE SET decision = 'rejected', decided_at = NOW()"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId); stmt.setObject(3, sourceFlowId)
                stmt.executeUpdate()
            }
        }
        return RejectResult.Success
    }

    fun deleteDecision(plotId: UUID, uploadId: UUID, userId: UUID = FOUNDING_USER_ID): Boolean {
        val plot = getPlotById(plotId) ?: return false
        if (plot.ownerUserId != userId) return false
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM plot_staging_decisions WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun getRejectedItems(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<UploadRecord> {
        val plot = getPlotById(plotId) ?: return emptyList()
        if (plot.ownerUserId != userId) return emptyList()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT u.id, u.storage_key, u.mime_type, u.file_size, u.uploaded_at, u.content_hash,
                          u.thumbnail_key, u.taken_at, u.latitude, u.longitude, u.altitude,
                          u.device_make, u.device_model, u.rotation, u.tags, u.composted_at, u.exif_processed_at,
                          u.last_viewed_at, u.storage_class, u.envelope_version, u.wrapped_dek, u.dek_format,
                          u.encrypted_metadata, u.encrypted_metadata_format, u.thumbnail_storage_key,
                          u.wrapped_thumbnail_dek, u.thumbnail_dek_format, u.preview_storage_key,
                          u.wrapped_preview_dek, u.preview_dek_format, u.plain_chunk_size, u.duration_seconds,
                          u.shared_from_upload_id, u.shared_from_user_id
                   FROM uploads u
                   JOIN plot_staging_decisions psd ON psd.upload_id = u.id
                   WHERE psd.plot_id = ? AND psd.decision = 'rejected'
                   ORDER BY psd.decided_at DESC"""
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
        }
    }

    // ---- Collection plot item operations ----------------------------------

    fun getPlotItems(plotId: UUID, userId: UUID = FOUNDING_USER_ID): List<PlotItemWithUpload> {
        dataSource.connection.use { conn ->
            val plot = getPlotByIdForUser(conn, plotId, userId) ?: return emptyList()
            val isShared = plot.visibility == "shared"
            conn.prepareStatement(
                """SELECT u.id, u.storage_key, u.mime_type, u.file_size, u.uploaded_at, u.content_hash,
                          u.thumbnail_key, u.taken_at, u.latitude, u.longitude, u.altitude,
                          u.device_make, u.device_model, u.rotation, u.tags, u.composted_at, u.exif_processed_at,
                          u.last_viewed_at, u.storage_class, u.envelope_version, u.wrapped_dek, u.dek_format,
                          u.encrypted_metadata, u.encrypted_metadata_format, u.thumbnail_storage_key,
                          u.wrapped_thumbnail_dek, u.thumbnail_dek_format, u.preview_storage_key,
                          u.wrapped_preview_dek, u.preview_dek_format, u.plain_chunk_size, u.duration_seconds,
                          u.shared_from_upload_id, u.shared_from_user_id,
                          pi.added_by, pi.wrapped_item_dek, pi.item_dek_format,
                          pi.wrapped_thumbnail_dek AS pi_wrapped_thumbnail_dek,
                          pi.thumbnail_dek_format AS pi_thumbnail_dek_format
                   FROM uploads u
                   JOIN plot_items pi ON pi.upload_id = u.id
                   WHERE pi.plot_id = ?
                   ORDER BY pi.added_at DESC"""
            ).use { stmt ->
                stmt.setObject(1, plotId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<PlotItemWithUpload>()
                while (rs.next()) {
                    results.add(PlotItemWithUpload(
                        upload = rs.toUploadRecord(),
                        addedBy = rs.getObject("added_by", UUID::class.java),
                        wrappedItemDek = rs.getBytes("wrapped_item_dek"),
                        itemDekFormat = rs.getString("item_dek_format"),
                        wrappedThumbnailDek = rs.getBytes("pi_wrapped_thumbnail_dek"),
                        thumbnailDekFormat = rs.getString("pi_thumbnail_dek_format"),
                    ))
                }
                return results
            }
        }
    }

    sealed class AddItemResult {
        object Success : AddItemResult()
        object AlreadyPresent : AddItemResult()
        object PlotNotOwned : AddItemResult()
        object UploadNotOwned : AddItemResult()
        data class Error(val message: String) : AddItemResult()
    }

    fun addPlotItem(
        plotId: UUID,
        uploadId: UUID,
        userId: UUID = FOUNDING_USER_ID,
        wrappedItemDekBytes: ByteArray? = null,
        itemDekFormat: String? = null,
        wrappedThumbnailDekBytes: ByteArray? = null,
        thumbnailDekFormat: String? = null,
    ): AddItemResult {
        val plot = getPlotById(plotId) ?: return AddItemResult.PlotNotOwned
        val isOwner = plot.ownerUserId == userId
        val isMember = plot.visibility == "shared" && isMember(plotId, userId)
        if (!isOwner && !isMember) return AddItemResult.PlotNotOwned
        if (plot.criteria != null) return AddItemResult.Error("Plot is a query plot, not a collection plot")
        if (!uploadExists(uploadId, userId)) return AddItemResult.UploadNotOwned

        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """INSERT INTO plot_items (plot_id, upload_id, added_by, wrapped_item_dek, item_dek_format,
                       wrapped_thumbnail_dek, thumbnail_dek_format) VALUES (?, ?, ?, ?, ?, ?, ?)"""
                ).use { stmt ->
                    stmt.setObject(1, plotId); stmt.setObject(2, uploadId); stmt.setObject(3, userId)
                    stmt.setBytes(4, wrappedItemDekBytes); stmt.setString(5, itemDekFormat)
                    stmt.setBytes(6, wrappedThumbnailDekBytes); stmt.setString(7, thumbnailDekFormat)
                    stmt.executeUpdate()
                }
            }
            AddItemResult.Success
        } catch (_: java.sql.SQLIntegrityConstraintViolationException) {
            AddItemResult.AlreadyPresent
        } catch (e: java.sql.SQLException) {
            if (e.sqlState?.startsWith("23") == true) AddItemResult.AlreadyPresent
            else AddItemResult.Error(e.message ?: "DB error")
        }
    }

    sealed class RemoveItemResult {
        object Success : RemoveItemResult()
        object NotFound : RemoveItemResult()
        object Forbidden : RemoveItemResult()
    }

    fun removePlotItem(plotId: UUID, uploadId: UUID, userId: UUID = FOUNDING_USER_ID): RemoveItemResult {
        dataSource.connection.use { conn ->
            // Check the item exists and whether this user added it or owns the plot
            val row = conn.prepareStatement(
                """SELECT pi.added_by, p.owner_user_id
                   FROM plot_items pi
                   JOIN plots p ON p.id = pi.plot_id
                   WHERE pi.plot_id = ? AND pi.upload_id = ?"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return RemoveItemResult.NotFound
                Pair(
                    rs.getObject("added_by", UUID::class.java),
                    rs.getObject("owner_user_id", UUID::class.java)
                )
            }
            val (addedBy, ownerUserId) = row
            if (ownerUserId != userId && addedBy != userId) return RemoveItemResult.Forbidden

            conn.prepareStatement(
                "DELETE FROM plot_items WHERE plot_id = ? AND upload_id = ?"
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, uploadId)
                stmt.executeUpdate()
            }
        }
        return RemoveItemResult.Success
    }

    // ---- Plot members / invites -------------------------------------------

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
                          pm.role, pm.wrapped_plot_key, pm.plot_key_format, pm.joined_at
                   FROM plot_members pm
                   JOIN users u ON u.id = pm.user_id
                   WHERE pm.plot_id = ?
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

            if (isMemberConn(conn, plotId, newUserId)) return AddMemberResult.AlreadyMember

            conn.prepareStatement(
                """INSERT INTO plot_members (plot_id, user_id, role, wrapped_plot_key, plot_key_format)
                   VALUES (?, ?, 'member', ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, plotId); stmt.setObject(2, newUserId)
                stmt.setBytes(3, wrappedPlotKey); stmt.setString(4, plotKeyFormat)
                stmt.executeUpdate()
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
            if (isMemberConn(conn, info.plotId, recipientUserId)) return RedeemInviteResult.AlreadyMember

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
                """INSERT INTO plot_members (plot_id, user_id, role, wrapped_plot_key, plot_key_format)
                   VALUES (?, ?, 'member', ?, ?) ON CONFLICT DO NOTHING"""
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

    private fun java.sql.ResultSet.toFlowRecord() = FlowRecord(
        id             = getObject("id", UUID::class.java),
        userId         = getObject("user_id", UUID::class.java),
        name           = getString("name"),
        criteria       = getString("criteria"),
        targetPlotId   = getObject("target_plot_id", UUID::class.java),
        requiresStaging = getBoolean("requires_staging"),
        createdAt      = getTimestamp("created_at").toInstant(),
        updatedAt      = getTimestamp("updated_at").toInstant(),
    )

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

    fun insertWrappedKey(record: WrappedKeyRecord, userId: UUID = FOUNDING_USER_ID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO wrapped_keys (id, device_id, device_label, device_kind, pubkey_format,
                       pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at, user_id)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
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
                stmt.setObject(12, userId)
                stmt.executeUpdate()
            }
        }
    }

    fun listWrappedKeys(userId: UUID = FOUNDING_USER_ID, includeRetired: Boolean = false): List<WrappedKeyRecord> {
        dataSource.connection.use { conn ->
            val sql = if (includeRetired)
                "SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at FROM wrapped_keys WHERE user_id = ? ORDER BY created_at DESC"
            else
                "SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at FROM wrapped_keys WHERE user_id = ? AND retired_at IS NULL ORDER BY created_at DESC"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
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

    fun getWrappedKeyByDeviceIdForUser(deviceId: String, userId: UUID): WrappedKeyRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at FROM wrapped_keys WHERE device_id = ? AND user_id = ?"
            ).use { stmt ->
                stmt.setString(1, deviceId)
                stmt.setObject(2, userId)
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

    fun getRecoveryPassphrase(userId: UUID = FOUNDING_USER_ID): RecoveryPassphraseRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT wrapped_master_key, wrap_format, argon2_params, salt, created_at, updated_at FROM recovery_passphrase WHERE user_id = ?"
            ).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toRecoveryPassphraseRecord()
            }
        }
    }

    fun upsertRecoveryPassphrase(record: RecoveryPassphraseRecord, userId: UUID = FOUNDING_USER_ID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO recovery_passphrase (user_id, wrapped_master_key, wrap_format, argon2_params, salt, created_at, updated_at)
                   VALUES (?, ?, ?, ?::jsonb, ?, NOW(), NOW())
                   ON CONFLICT (user_id) DO UPDATE SET
                       wrapped_master_key = EXCLUDED.wrapped_master_key,
                       wrap_format = EXCLUDED.wrap_format,
                       argon2_params = EXCLUDED.argon2_params,
                       salt = EXCLUDED.salt,
                       updated_at = NOW()"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setBytes(2, record.wrappedMasterKey)
                stmt.setString(3, record.wrapFormat)
                stmt.setString(4, record.argon2Params)
                stmt.setBytes(5, record.salt)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteRecoveryPassphrase(userId: UUID = FOUNDING_USER_ID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM recovery_passphrase WHERE user_id = ?").use { stmt ->
                stmt.setObject(1, userId)
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

    private val pendingLinkColumns = """
        id, one_time_code, expires_at, state, new_device_id, new_device_label,
        new_device_kind, new_pubkey_format, new_pubkey, wrapped_master_key, wrap_format,
        user_id, web_session_id, raw_session_token, session_expires_at
    """.trimIndent()

    fun getPendingDeviceLink(id: UUID): PendingDeviceLinkRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $pendingLinkColumns FROM pending_device_links WHERE id = ?"
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
                "SELECT $pendingLinkColumns FROM pending_device_links WHERE one_time_code = ?"
            ).use { stmt ->
                stmt.setString(1, code)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toPendingDeviceLinkRecord()
            }
        }
    }

    fun getPendingDeviceLinkByWebSessionId(webSessionId: String): PendingDeviceLinkRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $pendingLinkColumns FROM pending_device_links WHERE web_session_id = ?"
            ).use { stmt ->
                stmt.setString(1, webSessionId)
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
        userId: UUID = FOUNDING_USER_ID,
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
                       pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, user_id)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
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
                stmt.setObject(11, userId)
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

    // ── Users ─────────────────────────────────────────────────────────────────

    fun createUser(
        id: UUID = UUID.randomUUID(),
        username: String,
        displayName: String,
        authVerifier: ByteArray? = null,
        authSalt: ByteArray? = null,
    ): UserRecord {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO users (id, username, display_name, auth_verifier, auth_salt)
                   VALUES (?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setString(2, username)
                stmt.setString(3, displayName)
                stmt.setBytes(4, authVerifier)
                stmt.setBytes(5, authSalt)
                stmt.executeUpdate()
            }
        }
        return findUserById(id)!!
    }

    fun findUserByUsername(username: String): UserRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, username, display_name, auth_verifier, auth_salt, created_at FROM users WHERE username = ?"
            ).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUserRecord()
            }
        }
    }

    fun findUserById(id: UUID): UserRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, username, display_name, auth_verifier, auth_salt, created_at FROM users WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUserRecord()
            }
        }
    }

    fun setUserAuth(userId: UUID, authVerifier: ByteArray, authSalt: ByteArray) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE users SET auth_verifier = ?, auth_salt = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setBytes(1, authVerifier)
                stmt.setBytes(2, authSalt)
                stmt.setObject(3, userId)
                stmt.executeUpdate()
            }
        }
    }

    fun resetUserAuth(userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE users SET auth_verifier = NULL, auth_salt = NULL WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeUpdate()
            }
        }
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    fun createSession(userId: UUID, tokenHash: ByteArray, deviceKind: String): UserSessionRecord {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val expiresAt = now.plus(90, ChronoUnit.DAYS)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO user_sessions (id, user_id, token_hash, device_kind, created_at, last_used_at, expires_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setObject(2, userId)
                stmt.setBytes(3, tokenHash)
                stmt.setString(4, deviceKind)
                stmt.setTimestamp(5, Timestamp.from(now))
                stmt.setTimestamp(6, Timestamp.from(now))
                stmt.setTimestamp(7, Timestamp.from(expiresAt))
                stmt.executeUpdate()
            }
        }
        return UserSessionRecord(id, userId, tokenHash, deviceKind, now, now, expiresAt)
    }

    fun findSessionByTokenHash(tokenHash: ByteArray): UserSessionRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, user_id, token_hash, device_kind, created_at, last_used_at, expires_at
                   FROM user_sessions WHERE token_hash = ?"""
            ).use { stmt ->
                stmt.setBytes(1, tokenHash)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUserSessionRecord()
            }
        }
    }

    fun deleteSession(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM user_sessions WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun refreshSession(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE user_sessions SET last_used_at = NOW(), expires_at = NOW() + INTERVAL '90 days' WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteExpiredSessions() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM user_sessions WHERE expires_at < NOW()")
                stmt.execute("DELETE FROM pending_device_links WHERE expires_at < NOW() AND state != 'wrap_complete'")
                stmt.execute("DELETE FROM invites WHERE expires_at < NOW() AND used_at IS NULL")
            }
        }
    }

    // ── Invites ───────────────────────────────────────────────────────────────

    fun createInvite(createdBy: UUID, rawToken: String): InviteRecord {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val expiresAt = now.plus(48, ChronoUnit.HOURS)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO invites (id, token, created_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setString(2, rawToken)
                stmt.setObject(3, createdBy)
                stmt.setTimestamp(4, Timestamp.from(now))
                stmt.setTimestamp(5, Timestamp.from(expiresAt))
                stmt.executeUpdate()
            }
        }
        return InviteRecord(id, rawToken, createdBy, now, expiresAt, null, null)
    }

    fun findInviteByToken(token: String): InviteRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, token, created_by, created_at, expires_at, used_at, used_by FROM invites WHERE token = ?"
            ).use { stmt ->
                stmt.setString(1, token)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toInviteRecord()
            }
        }
    }

    fun markInviteUsed(id: UUID, usedBy: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE invites SET used_at = NOW(), used_by = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, usedBy)
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
    }

    // ── M8 Pairing ────────────────────────────────────────────────────────────

    fun createPairingLink(userId: UUID, code: String): PendingDeviceLinkRecord {
        val id = UUID.randomUUID()
        val expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO pending_device_links (id, one_time_code, expires_at, state, user_id)
                   VALUES (?, ?, ?, 'initiated', ?)"""
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.setString(2, code)
                stmt.setTimestamp(3, Timestamp.from(expiresAt))
                stmt.setObject(4, userId)
                stmt.executeUpdate()
            }
        }
        return PendingDeviceLinkRecord(
            id = id, oneTimeCode = code, expiresAt = expiresAt, state = "initiated",
            newDeviceId = null, newDeviceLabel = null, newDeviceKind = null,
            newPubkeyFormat = null, newPubkey = null, wrappedMasterKey = null, wrapFormat = null,
            userId = userId,
        )
    }

    fun setPairingWebSession(id: UUID, webSessionId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE pending_device_links SET state = 'device_registered', web_session_id = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setString(1, webSessionId)
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
    }

    fun completePairingLink(
        id: UUID,
        wrappedMasterKey: ByteArray,
        wrapFormat: String,
        rawSessionToken: String,
        sessionRecord: UserSessionRecord,
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """UPDATE pending_device_links SET
                       state = 'wrap_complete',
                       wrapped_master_key = ?,
                       wrap_format = ?,
                       raw_session_token = ?,
                       session_expires_at = ?
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setBytes(1, wrappedMasterKey)
                stmt.setString(2, wrapFormat)
                stmt.setString(3, rawSessionToken)
                stmt.setTimestamp(4, Timestamp.from(sessionRecord.expiresAt))
                stmt.setObject(5, id)
                stmt.executeUpdate()
            }
        }
    }

    fun getWrappedKeyByDeviceIdAndUser(deviceId: String, userId: UUID): WrappedKeyRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """SELECT id, device_id, device_label, device_kind, pubkey_format, pubkey,
                          wrapped_master_key, wrap_format, created_at, last_used_at, retired_at
                   FROM wrapped_keys WHERE device_id = ? AND user_id = ?"""
            ).use { stmt ->
                stmt.setString(1, deviceId)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toWrappedKeyRecord()
            }
        }
    }

    fun createSystemPlot(userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """INSERT INTO plots (id, owner_user_id, name, sort_order, is_system_defined)
                   VALUES (gen_random_uuid(), ?, '__just_arrived__', -1000, TRUE)"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeUpdate()
            }
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────────

    fun insertDiagEvent(
        deviceLabel: String,
        tag: String,
        message: String,
        detail: String,
        userId: UUID = FOUNDING_USER_ID,
    ) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO diagnostic_events (device_label, tag, message, detail, user_id) VALUES (?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, deviceLabel)
                stmt.setString(2, tag)
                stmt.setString(3, message)
                stmt.setString(4, detail)
                stmt.setObject(5, userId)
                stmt.executeUpdate()
            }
        }
    }

    fun listDiagEvents(userId: UUID = FOUNDING_USER_ID, limit: Int = 200): List<Map<String, String>> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, created_at, device_label, tag, message, detail FROM diagnostic_events WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setInt(2, limit)
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
    if (sharedFromUserId != null) node.put("sharedFromUserId", sharedFromUserId.toString())
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
    sharedFromUploadId = try { getObject("shared_from_upload_id", UUID::class.java) } catch (_: Exception) { null },
    sharedFromUserId = try { getObject("shared_from_user_id", UUID::class.java) } catch (_: Exception) { null },
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

private fun java.sql.ResultSet.toUserRecord() = UserRecord(
    id = getObject("id", UUID::class.java),
    username = getString("username"),
    displayName = getString("display_name"),
    authVerifier = getBytes("auth_verifier"),
    authSalt = getBytes("auth_salt"),
    createdAt = getTimestamp("created_at").toInstant(),
)

private fun java.sql.ResultSet.toUserSessionRecord() = UserSessionRecord(
    id = getObject("id", UUID::class.java),
    userId = getObject("user_id", UUID::class.java),
    tokenHash = getBytes("token_hash"),
    deviceKind = getString("device_kind"),
    createdAt = getTimestamp("created_at").toInstant(),
    lastUsedAt = getTimestamp("last_used_at").toInstant(),
    expiresAt = getTimestamp("expires_at").toInstant(),
)

private fun java.sql.ResultSet.toInviteRecord() = InviteRecord(
    id = getObject("id", UUID::class.java),
    token = getString("token"),
    createdBy = getObject("created_by", UUID::class.java),
    createdAt = getTimestamp("created_at").toInstant(),
    expiresAt = getTimestamp("expires_at").toInstant(),
    usedAt = getTimestamp("used_at")?.toInstant(),
    usedBy = getObject("used_by", UUID::class.java),
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
    userId = try { getObject("user_id", UUID::class.java) } catch (_: Exception) { null },
    webSessionId = try { getString("web_session_id") } catch (_: Exception) { null },
    rawSessionToken = try { getString("raw_session_token") } catch (_: Exception) { null },
    sessionExpiresAt = try { getTimestamp("session_expires_at")?.toInstant() } catch (_: Exception) { null },
)
