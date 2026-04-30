package digital.heirlooms.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

data class UploadRecord(
    val id: UUID,
    val storageKey: String,
    val mimeType: String,
    val fileSize: Long,
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
                "INSERT INTO uploads (id, storage_key, mime_type, file_size) VALUES (?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, record.id)
                stmt.setString(2, record.storageKey)
                stmt.setString(3, record.mimeType)
                stmt.setLong(4, record.fileSize)
                stmt.executeUpdate()
            }
        }
    }

    fun listUploads(): List<UploadRecord> {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "SELECT id, storage_key, mime_type, file_size FROM uploads ORDER BY uploaded_at DESC"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableListOf<UploadRecord>()
                while (rs.next()) {
                    results.add(
                        UploadRecord(
                            id = rs.getObject("id", UUID::class.java),
                            storageKey = rs.getString("storage_key"),
                            mimeType = rs.getString("mime_type"),
                            fileSize = rs.getLong("file_size"),
                        )
                    )
                }
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
