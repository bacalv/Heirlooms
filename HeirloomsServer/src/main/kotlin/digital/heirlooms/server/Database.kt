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
    val capturedAt: Instant? = null,
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
)

data class UploadPage(val items: List<UploadRecord>, val nextCursor: String?)

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
                    captured_at, latitude, longitude, altitude, device_make, device_model,
                    exif_processed_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.setObject(1, record.id)
                stmt.setString(2, record.storageKey)
                stmt.setString(3, record.mimeType)
                stmt.setLong(4, record.fileSize)
                stmt.setString(5, record.contentHash)
                stmt.setString(6, record.thumbnailKey)
                stmt.setTimestamp(7, record.capturedAt?.let { Timestamp.from(it) })
                stmt.setObject(8, record.latitude)
                stmt.setObject(9, record.longitude)
                stmt.setObject(10, record.altitude)
                stmt.setString(11, record.deviceMake)
                stmt.setString(12, record.deviceModel)
                stmt.setTimestamp(13, Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
        }
    }

    fun findByContentHash(hash: String): UploadRecord? {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at
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
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at
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
                "UPDATE uploads SET last_viewed_at = NOW() WHERE id = ? AND last_viewed_at IS NULL"
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
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at
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
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at
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
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
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
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags,
                          composted_at, exif_processed_at, last_viewed_at
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
                      u.thumbnail_key, u.captured_at, u.latitude, u.longitude, u.altitude,
                      u.device_make, u.device_model, u.rotation, u.tags, u.composted_at,
                      u.exif_processed_at, u.last_viewed_at
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
                conditions += "last_viewed_at IS NULL"
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
                UploadSort.TAKEN_NEWEST  -> "ORDER BY captured_at DESC NULLS LAST, id DESC"
                UploadSort.TAKEN_OLDEST  -> "ORDER BY captured_at ASC NULLS LAST, id ASC"
            }

            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash,
                          thumbnail_key, captured_at, latitude, longitude, altitude,
                          device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                          last_viewed_at
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
                "(captured_at < ? OR (captured_at = ? AND id < ?::uuid) OR captured_at IS NULL)" to { stmt, idx ->
                    stmt.setTimestamp(idx, ts); stmt.setTimestamp(idx + 1, ts)
                    stmt.setString(idx + 2, cursor.id.toString()); idx + 3
                }
            } else {
                "(captured_at IS NULL AND id < ?::uuid)" to { stmt, idx ->
                    stmt.setString(idx, cursor.id.toString()); idx + 1
                }
            }
        }
        UploadSort.TAKEN_OLDEST -> {
            if (cursor.sortKeyMs != null) {
                val ts = Timestamp.from(Instant.ofEpochMilli(cursor.sortKeyMs))
                "(captured_at > ? OR (captured_at = ? AND id > ?::uuid) OR captured_at IS NULL)" to { stmt, idx ->
                    stmt.setTimestamp(idx, ts); stmt.setTimestamp(idx + 1, ts)
                    stmt.setString(idx + 2, cursor.id.toString()); idx + 3
                }
            } else {
                "(captured_at IS NULL AND id > ?::uuid)" to { stmt, idx ->
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
                          thumbnail_key, captured_at, latitude, longitude, altitude,
                          device_make, device_model, rotation, tags, composted_at, exif_processed_at,
                          last_viewed_at
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
        capturedAt: Instant?,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        deviceMake: String?,
        deviceModel: String?,
    ) {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """UPDATE uploads
                   SET captured_at = ?, latitude = ?, longitude = ?, altitude = ?,
                       device_make = ?, device_model = ?, exif_processed_at = NOW()
                   WHERE id = ?"""
            ).use { stmt ->
                stmt.setTimestamp(1, capturedAt?.let { Timestamp.from(it) })
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
            UploadSort.TAKEN_NEWEST, UploadSort.TAKEN_OLDEST   -> record.capturedAt?.toEpochMilli()
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
}

internal fun UploadRecord.toJson(): String {
    val node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
    node.put("id", id.toString())
    node.put("storageKey", storageKey)
    node.put("mimeType", mimeType)
    node.put("fileSize", fileSize)
    node.put("uploadedAt", uploadedAt.toString())
    node.put("rotation", rotation)
    if (thumbnailKey != null) node.put("thumbnailKey", thumbnailKey) else node.putNull("thumbnailKey")
    val tagsNode = node.putArray("tags")
    tags.forEach { tagsNode.add(it) }
    if (capturedAt != null) node.put("capturedAt", capturedAt.toString())
    if (latitude != null) node.put("latitude", latitude)
    if (longitude != null) node.put("longitude", longitude)
    if (altitude != null) node.put("altitude", altitude)
    if (deviceMake != null) node.put("deviceMake", deviceMake)
    if (deviceModel != null) node.put("deviceModel", deviceModel)
    if (compostedAt != null) node.put("compostedAt", compostedAt.toString()) else node.putNull("compostedAt")
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
    capturedAt = getTimestamp("captured_at")?.toInstant(),
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
)
