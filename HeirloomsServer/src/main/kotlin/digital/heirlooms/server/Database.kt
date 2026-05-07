package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
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
                    captured_at, latitude, longitude, altitude, device_make, device_model)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
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
                stmt.executeUpdate()
            }
        }
    }

    fun findByContentHash(hash: String): UploadRecord? {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags
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
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags
                   FROM uploads WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    fun listUploads(tag: String? = null, excludeTag: String? = null): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
            val conditions = mutableListOf<String>()
            if (tag != null) conditions.add("tags @> ARRAY[?]::text[]")
            if (excludeTag != null) conditions.add("NOT (tags @> ARRAY[?]::text[])")
            val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          captured_at, latitude, longitude, altitude, device_make, device_model, rotation, tags
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
                      u.device_make, u.device_model, u.rotation, u.tags
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
            // Runs even on non-local returns from block; rolls back any uncommitted changes.
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

internal fun UploadRecord.toJson(): String = buildString {
    append("""{"id":"$id","storageKey":"$storageKey","mimeType":"$mimeType","fileSize":$fileSize,"uploadedAt":"$uploadedAt","rotation":$rotation,"thumbnailKey":${if (thumbnailKey != null) "\"$thumbnailKey\"" else "null"}""")
    val tagsJson = tags.joinToString(",") { "\"$it\"" }
    append(""","tags":[$tagsJson]""")
    if (capturedAt != null) append(""","capturedAt":"$capturedAt"""")
    if (latitude != null) append(""","latitude":$latitude""")
    if (longitude != null) append(""","longitude":$longitude""")
    if (altitude != null) append(""","altitude":$altitude""")
    if (deviceMake != null) append(""","deviceMake":"$deviceMake"""")
    if (deviceModel != null) append(""","deviceModel":"$deviceModel"""")
    append("}")
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
)
