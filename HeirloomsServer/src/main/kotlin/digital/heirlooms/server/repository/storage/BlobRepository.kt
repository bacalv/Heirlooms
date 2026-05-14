package digital.heirlooms.server.repository.storage

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface BlobRepository {
    fun insertPendingBlob(storageKey: String): UUID
    fun deletePendingBlob(storageKey: String)
    fun deleteStalePendingBlobs(olderThan: java.time.Instant): List<String>
}

class PostgresBlobRepository(private val dataSource: DataSource) : BlobRepository {

    override fun insertPendingBlob(storageKey: String): UUID {
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

    override fun deletePendingBlob(storageKey: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM pending_blobs WHERE storage_key = ?").use { stmt ->
                stmt.setString(1, storageKey)
                stmt.executeUpdate()
            }
        }
    }

    override fun deleteStalePendingBlobs(olderThan: Instant): List<String> {
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
}
