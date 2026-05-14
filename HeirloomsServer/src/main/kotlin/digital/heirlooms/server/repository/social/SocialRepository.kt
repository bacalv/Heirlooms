package digital.heirlooms.server.repository.social

import digital.heirlooms.server.domain.keys.AccountSharingKeyRecord
import digital.heirlooms.server.domain.keys.FriendRecord
import java.util.UUID
import javax.sql.DataSource

class SocialRepository(private val dataSource: DataSource) {

    fun upsertSharingKey(userId: UUID, pubkey: ByteArray, wrappedPrivkey: ByteArray, wrapFormat: String) {
        dataSource.connection.use { conn ->
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
        dataSource.connection.use { conn ->
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
        dataSource.connection.use { conn ->
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
        dataSource.connection.use { conn ->
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
        dataSource.connection.use { conn ->
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
}
