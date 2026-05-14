package digital.heirlooms.server.repository.diag

import java.util.UUID
import javax.sql.DataSource

interface DiagRepository {
    fun insertDiagEvent(deviceLabel: String, tag: String, message: String, detail: String, userId: UUID)
    fun listDiagEvents(userId: UUID, limit: Int = 200): List<Map<String, String>>
}

class PostgresDiagRepository(private val dataSource: DataSource) : DiagRepository {

    override fun insertDiagEvent(
        deviceLabel: String,
        tag: String,
        message: String,
        detail: String,
        userId: UUID,
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

    override fun listDiagEvents(userId: UUID, limit: Int): List<Map<String, String>> {
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
