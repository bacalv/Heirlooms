package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

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
                          captured_at, latitude, longitude, altitude, device_make, device_model
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
                          captured_at, latitude, longitude, altitude, device_make, device_model
                   FROM uploads WHERE id = ?"""
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUploadRecord()
            }
        }
    }

    fun listUploads(): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                """SELECT id, storage_key, mime_type, file_size, uploaded_at, content_hash, thumbnail_key,
                          captured_at, latitude, longitude, altitude, device_make, device_model
                   FROM uploads ORDER BY uploaded_at DESC"""
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) results.add(rs.toUploadRecord())
                return results
            }
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
)
