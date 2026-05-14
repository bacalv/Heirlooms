package digital.heirlooms.server.repository.keys

import digital.heirlooms.server.domain.auth.UserSessionRecord
import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

interface KeyRepository {
    fun insertWrappedKey(record: WrappedKeyRecord, userId: UUID = FOUNDING_USER_ID)
    fun listWrappedKeys(userId: UUID = FOUNDING_USER_ID, includeRetired: Boolean = false): List<WrappedKeyRecord>
    fun getWrappedKeyByDeviceId(deviceId: String): WrappedKeyRecord?
    fun getWrappedKeyByDeviceIdForUser(deviceId: String, userId: UUID): WrappedKeyRecord?
    fun getWrappedKeyByDeviceIdAndUser(deviceId: String, userId: UUID): WrappedKeyRecord?
    fun retireWrappedKey(id: UUID, retiredAt: Instant = Instant.now())
    fun touchWrappedKey(id: UUID)
    fun retireDormantWrappedKeys(dormantBefore: Instant): Int
    fun getRecoveryPassphrase(userId: UUID = FOUNDING_USER_ID): RecoveryPassphraseRecord?
    fun upsertRecoveryPassphrase(record: RecoveryPassphraseRecord, userId: UUID = FOUNDING_USER_ID)
    fun deleteRecoveryPassphrase(userId: UUID = FOUNDING_USER_ID): Boolean
    fun insertPendingDeviceLink(record: PendingDeviceLinkRecord)
    fun getPendingDeviceLink(id: UUID): PendingDeviceLinkRecord?
    fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord?
    fun getPendingDeviceLinkByWebSessionId(webSessionId: String): PendingDeviceLinkRecord?
    fun registerNewDevice(id: UUID, deviceId: String, deviceLabel: String, deviceKind: String, pubkeyFormat: String, pubkey: ByteArray)
    fun completeDeviceLink(id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String, deviceId: String, deviceLabel: String, deviceKind: String, pubkeyFormat: String, pubkey: ByteArray, userId: UUID = FOUNDING_USER_ID)
    fun deleteExpiredDeviceLinks(before: Instant): Int
}

class PostgresKeyRepository(private val dataSource: DataSource) : KeyRepository {

    // ── Wrapped keys ──────────────────────────────────────────────────────────

    override fun insertWrappedKey(record: WrappedKeyRecord, userId: UUID) {
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

    override fun listWrappedKeys(userId: UUID, includeRetired: Boolean): List<WrappedKeyRecord> {
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

    override fun getWrappedKeyByDeviceId(deviceId: String): WrappedKeyRecord? {
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

    override fun getWrappedKeyByDeviceIdForUser(deviceId: String, userId: UUID): WrappedKeyRecord? {
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

    override fun getWrappedKeyByDeviceIdAndUser(deviceId: String, userId: UUID): WrappedKeyRecord? {
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

    override fun retireWrappedKey(id: UUID, retiredAt: Instant) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE wrapped_keys SET retired_at = ? WHERE id = ?").use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(retiredAt))
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
    }

    override fun touchWrappedKey(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE wrapped_keys SET last_used_at = NOW() WHERE id = ? AND retired_at IS NULL").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    override fun retireDormantWrappedKeys(dormantBefore: Instant): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE wrapped_keys SET retired_at = NOW() WHERE retired_at IS NULL AND last_used_at < ?"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(dormantBefore))
                return stmt.executeUpdate()
            }
        }
    }

    // ── Recovery passphrase ───────────────────────────────────────────────────

    override fun getRecoveryPassphrase(userId: UUID): RecoveryPassphraseRecord? {
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

    override fun upsertRecoveryPassphrase(record: RecoveryPassphraseRecord, userId: UUID) {
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

    override fun deleteRecoveryPassphrase(userId: UUID): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM recovery_passphrase WHERE user_id = ?").use { stmt ->
                stmt.setObject(1, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    // ── Device links ──────────────────────────────────────────────────────────

    private val pendingLinkColumns = """
        id, one_time_code, expires_at, state, new_device_id, new_device_label,
        new_device_kind, new_pubkey_format, new_pubkey, wrapped_master_key, wrap_format,
        user_id, web_session_id, raw_session_token, session_expires_at
    """.trimIndent()

    override fun insertPendingDeviceLink(record: PendingDeviceLinkRecord) {
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

    override fun getPendingDeviceLink(id: UUID): PendingDeviceLinkRecord? {
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

    override fun getPendingDeviceLinkByCode(code: String): PendingDeviceLinkRecord? {
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

    override fun getPendingDeviceLinkByWebSessionId(webSessionId: String): PendingDeviceLinkRecord? {
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

    override fun registerNewDevice(
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

    override fun completeDeviceLink(
        id: UUID, wrappedMasterKey: ByteArray, wrapFormat: String,
        deviceId: String, deviceLabel: String, deviceKind: String,
        pubkeyFormat: String, pubkey: ByteArray,
        userId: UUID,
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

    override fun deleteExpiredDeviceLinks(before: Instant): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "DELETE FROM pending_device_links WHERE expires_at < ? AND state != 'wrap_complete'"
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(before))
                return stmt.executeUpdate()
            }
        }
    }

    // ── Transaction helper ────────────────────────────────────────────────────

    private inline fun <T> withTransaction(block: (java.sql.Connection) -> T): T {
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

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ResultSet.toWrappedKeyRecord() = WrappedKeyRecord(
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

    private fun ResultSet.toRecoveryPassphraseRecord() = RecoveryPassphraseRecord(
        wrappedMasterKey = getBytes("wrapped_master_key"),
        wrapFormat = getString("wrap_format"),
        argon2Params = getString("argon2_params"),
        salt = getBytes("salt"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
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
}
