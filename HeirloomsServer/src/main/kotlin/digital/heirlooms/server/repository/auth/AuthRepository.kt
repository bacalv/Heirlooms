package digital.heirlooms.server.repository.auth

import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.auth.InviteRecord
import digital.heirlooms.server.domain.auth.UserRecord
import digital.heirlooms.server.domain.auth.UserSessionRecord
import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

interface AuthRepository {
    fun createUser(id: UUID = UUID.randomUUID(), username: String, displayName: String, authVerifier: ByteArray? = null, authSalt: ByteArray? = null): UserRecord
    fun findUserByUsername(username: String): UserRecord?
    fun findUserById(id: UUID): UserRecord?
    fun setUserAuth(userId: UUID, authVerifier: ByteArray, authSalt: ByteArray)
    fun resetUserAuth(userId: UUID)
    fun setRequireBiometric(userId: UUID, requireBiometric: Boolean)
    fun createSession(userId: UUID, tokenHash: ByteArray, deviceKind: String): UserSessionRecord
    fun findSessionByTokenHash(tokenHash: ByteArray): UserSessionRecord?
    fun deleteSession(id: UUID)
    fun deleteAllSessionsForUser(userId: UUID)
    fun refreshSession(id: UUID)
    fun deleteExpiredSessions()
    fun createInvite(createdBy: UUID, rawToken: String): InviteRecord
    fun findInviteByToken(token: String): InviteRecord?
    fun markInviteUsed(id: UUID, usedBy: UUID)
    fun createPairingLink(userId: UUID, code: String): PendingDeviceLinkRecord
    fun setPairingWebSession(id: UUID, webSessionId: String)
    fun completePairingLink(id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String, rawSessionToken: String, sessionRecord: UserSessionRecord)
    fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord?
    fun getPendingDeviceLinkByWebSessionId(webSessionId: String): PendingDeviceLinkRecord?
}

class PostgresAuthRepository(private val dataSource: DataSource) : AuthRepository {

    // ── Users ─────────────────────────────────────────────────────────────────

    override fun createUser(
        id: UUID,
        username: String,
        displayName: String,
        authVerifier: ByteArray?,
        authSalt: ByteArray?,
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

    override fun findUserByUsername(username: String): UserRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, username, display_name, auth_verifier, auth_salt, created_at, require_biometric FROM users WHERE username = ?"
            ).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUserRecord()
            }
        }
    }

    override fun findUserById(id: UUID): UserRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, username, display_name, auth_verifier, auth_salt, created_at, require_biometric FROM users WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toUserRecord()
            }
        }
    }

    override fun setRequireBiometric(userId: UUID, requireBiometric: Boolean) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE users SET require_biometric = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setBoolean(1, requireBiometric)
                stmt.setObject(2, userId)
                stmt.executeUpdate()
            }
        }
    }

    override fun setUserAuth(userId: UUID, authVerifier: ByteArray, authSalt: ByteArray) {
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

    override fun resetUserAuth(userId: UUID) {
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

    override fun createSession(userId: UUID, tokenHash: ByteArray, deviceKind: String): UserSessionRecord {
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

    override fun findSessionByTokenHash(tokenHash: ByteArray): UserSessionRecord? {
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

    override fun deleteSession(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM user_sessions WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    override fun deleteAllSessionsForUser(userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM user_sessions WHERE user_id = ?").use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeUpdate()
            }
        }
    }

    override fun refreshSession(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE user_sessions SET last_used_at = NOW(), expires_at = NOW() + INTERVAL '90 days' WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    override fun deleteExpiredSessions() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM user_sessions WHERE expires_at < NOW()")
                stmt.execute("DELETE FROM pending_device_links WHERE expires_at < NOW() AND state != 'wrap_complete'")
                stmt.execute("DELETE FROM invites WHERE expires_at < NOW() AND used_at IS NULL")
            }
        }
    }

    // ── Invites ───────────────────────────────────────────────────────────────

    override fun createInvite(createdBy: UUID, rawToken: String): InviteRecord {
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

    override fun findInviteByToken(token: String): InviteRecord? {
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

    override fun markInviteUsed(id: UUID, usedBy: UUID) {
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

    override fun createPairingLink(userId: UUID, code: String): PendingDeviceLinkRecord {
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

    override fun setPairingWebSession(id: UUID, webSessionId: String) {
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

    override fun completePairingLink(
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

    override fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $PENDING_LINK_COLUMNS FROM pending_device_links WHERE one_time_code = ?"
            ).use { stmt ->
                stmt.setString(1, code)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toPendingDeviceLinkRecord()
            }
        }
    }

    override fun getPendingDeviceLinkByWebSessionId(webSessionId: String): PendingDeviceLinkRecord? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT $PENDING_LINK_COLUMNS FROM pending_device_links WHERE web_session_id = ?"
            ).use { stmt ->
                stmt.setString(1, webSessionId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                return rs.toPendingDeviceLinkRecord()
            }
        }
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun ResultSet.toUserRecord() = UserRecord(
        id = getObject("id", UUID::class.java),
        username = getString("username"),
        displayName = getString("display_name"),
        authVerifier = getBytes("auth_verifier"),
        authSalt = getBytes("auth_salt"),
        createdAt = getTimestamp("created_at").toInstant(),
        requireBiometric = getBoolean("require_biometric"),
    )

    private fun ResultSet.toUserSessionRecord() = UserSessionRecord(
        id = getObject("id", UUID::class.java),
        userId = getObject("user_id", UUID::class.java),
        tokenHash = getBytes("token_hash"),
        deviceKind = getString("device_kind"),
        createdAt = getTimestamp("created_at").toInstant(),
        lastUsedAt = getTimestamp("last_used_at").toInstant(),
        expiresAt = getTimestamp("expires_at").toInstant(),
    )

    private fun ResultSet.toInviteRecord() = InviteRecord(
        id = getObject("id", UUID::class.java),
        token = getString("token"),
        createdBy = getObject("created_by", UUID::class.java),
        createdAt = getTimestamp("created_at").toInstant(),
        expiresAt = getTimestamp("expires_at").toInstant(),
        usedAt = getTimestamp("used_at")?.toInstant(),
        usedBy = getObject("used_by", UUID::class.java),
    )

    private fun ResultSet.toPendingDeviceLinkRecord() = PendingDeviceLinkRecord(
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

    companion object {
        private val PENDING_LINK_COLUMNS = """
            id, one_time_code, expires_at, state, new_device_id, new_device_label,
            new_device_kind, new_pubkey_format, new_pubkey, wrapped_master_key, wrap_format,
            user_id, web_session_id, raw_session_token, session_expires_at
        """.trimIndent()
    }
}
