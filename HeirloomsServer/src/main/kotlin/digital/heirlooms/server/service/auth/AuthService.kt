package digital.heirlooms.server.service.auth

import digital.heirlooms.server.domain.auth.InviteRecord
import digital.heirlooms.server.domain.auth.UserRecord
import digital.heirlooms.server.domain.auth.UserSessionRecord
import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.social.SocialRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource

/**
 * Encapsulates authentication and session business logic: token generation,
 * HMAC-based fake salt (anti-enumeration), session resolution, pairing flow,
 * invite and registration orchestration.
 */
class AuthService(
    private val authRepo: AuthRepository,
    private val keyRepo: KeyRepository,
    private val socialRepo: SocialRepository,
    private val plotRepo: PlotRepository,
    private val serverSecret: ByteArray,
    private val dataSource: DataSource? = null,
) {
    private val urlEnc: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDec: Base64.Decoder = Base64.getUrlDecoder()
    private val stdDec: Base64.Decoder = Base64.getDecoder()
    private val rng = SecureRandom()

    // ---- Token helpers ---------------------------------------------------------

    /** Encodes base64url or std base64, returning null on failure. */
    fun decodeBase64Url(value: String?): ByteArray? =
        if (value.isNullOrBlank()) null
        else runCatching { urlDec.decode(value) }.getOrNull()
            ?: runCatching { stdDec.decode(value) }.getOrNull()

    fun urlEncodeBytes(bytes: ByteArray): String = urlEnc.encodeToString(bytes)

    /** Issues a session token. Returns Triple(token, raw, hash). */
    fun issueToken(): Triple<String, ByteArray, ByteArray> {
        val raw = ByteArray(32).also { rng.nextBytes(it) }
        val token = urlEnc.encodeToString(raw)
        val hash = sha256(raw)
        return Triple(token, raw, hash)
    }

    /**
     * Returns a deterministic but unpredictable salt for an unknown username,
     * preventing user-enumeration via timing differences in the challenge flow.
     *
     * The HMAC key is [serverSecret] which must be a securely-generated random
     * byte array (set via AUTH_SECRET env var).  If AUTH_SECRET is not set in
     * production the secret will be all-zeros, which means an attacker who knows
     * the algorithm can predict fake salts.  A startup warning is logged in that
     * case by Main.kt.
     *
     * The result is constant-time relative to the HMAC computation itself;
     * no branching on the username value occurs after the MAC is computed.
     */
    fun fakeSalt(username: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(serverSecret, "HmacSHA256"))
        return mac.doFinal(username.toByteArray(Charsets.UTF_8)).copyOf(16)
    }

    /** Resolves the session from an X-Api-Key header value. */
    fun resolveSession(apiKey: String?): UserSessionRecord? {
        val raw = apiKey ?: return null
        val rawBytes = runCatching { urlDec.decode(raw) }.getOrElse {
            runCatching { stdDec.decode(raw) }.getOrNull()
        } ?: return null
        val hash = sha256(rawBytes)
        val session = authRepo.findSessionByTokenHash(hash) ?: return null
        if (session.expiresAt.isBefore(Instant.now())) return null
        return session
    }

    fun generateNumericCode(): String =
        (10_000_000 + rng.nextInt(90_000_000)).toString()

    fun generateRawToken(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }

    // ---- Me / logout -----------------------------------------------------------

    fun getMe(userId: UUID): digital.heirlooms.server.domain.auth.UserRecord? =
        authRepo.findUserById(userId)

    fun logout(apiKey: String?) {
        val session = resolveSession(apiKey) ?: return
        authRepo.deleteSession(session.id)
    }

    // ---- Challenge -------------------------------------------------------------

    fun getSaltForChallenge(username: String): ByteArray {
        val user = authRepo.findUserByUsername(username)
        return if (user?.authSalt != null) user.authSalt else fakeSalt(username)
    }

    // ---- Login -----------------------------------------------------------------

    sealed class LoginResult {
        data class Success(val token: String, val userId: UUID, val expiresAt: Instant) : LoginResult()
        object InvalidCredentials : LoginResult()
    }

    fun login(username: String, authKey: ByteArray): LoginResult {
        val user = authRepo.findUserByUsername(username)
        if (user == null || user.authVerifier == null || !sha256(authKey).contentEquals(user.authVerifier)) {
            return LoginResult.InvalidCredentials
        }
        val (token, _, hash) = issueToken()
        val session = authRepo.createSession(user.id, hash, "android")
        return LoginResult.Success(token, user.id, session.expiresAt)
    }

    // ---- Setup existing (founding user passphrase) -----------------------------

    sealed class SetupExistingResult {
        data class Success(val token: String, val userId: UUID, val expiresAt: Instant) : SetupExistingResult()
        object InvalidCredentials : SetupExistingResult()
        object PassphraseAlreadySet : SetupExistingResult()
        object NoDeviceKey : SetupExistingResult()
    }

    fun setupExisting(
        username: String,
        deviceId: String,
        authVerifier: ByteArray,
        authSalt: ByteArray,
        wrappedMasterKeyRecovery: ByteArray?,
        wrapFormatRecovery: String?,
    ): SetupExistingResult {
        val user = authRepo.findUserByUsername(username) ?: return SetupExistingResult.InvalidCredentials
        if (user.authVerifier != null) return SetupExistingResult.PassphraseAlreadySet
        val keyRecord = keyRepo.getWrappedKeyByDeviceIdAndUser(deviceId, user.id)
            ?: return SetupExistingResult.NoDeviceKey
        authRepo.setUserAuth(user.id, authVerifier, authSalt)
        // F-07: Invalidate all existing sessions so old tokens cannot be reused after passphrase change.
        authRepo.deleteAllSessionsForUser(user.id)
        if (wrappedMasterKeyRecovery != null && !wrapFormatRecovery.isNullOrBlank()) {
            keyRepo.upsertRecoveryPassphrase(
                RecoveryPassphraseRecord(
                    wrappedMasterKey = wrappedMasterKeyRecovery,
                    wrapFormat = wrapFormatRecovery,
                    argon2Params = "{}",
                    salt = ByteArray(0),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
                user.id,
            )
        }
        val (token, _, hash) = issueToken()
        val session = authRepo.createSession(user.id, hash, keyRecord.deviceKind)
        return SetupExistingResult.Success(token, user.id, session.expiresAt)
    }

    // ---- Registration ----------------------------------------------------------

    sealed class RegisterResult {
        data class Success(val token: String, val userId: UUID, val expiresAt: Instant) : RegisterResult()
        object InvalidInvite : RegisterResult()
        object UsernameTaken : RegisterResult()
        object DeviceIdTaken : RegisterResult()
    }

    fun register(
        inviteToken: String,
        username: String,
        displayName: String,
        authVerifier: ByteArray,
        authSalt: ByteArray,
        wrappedMasterKey: ByteArray,
        wrapFormat: String,
        pubkeyFormat: String,
        pubkey: ByteArray,
        deviceId: String,
        deviceLabel: String,
        deviceKind: String,
        wrappedMasterKeyRecovery: ByteArray?,
        wrapFormatRecovery: String?,
    ): RegisterResult {
        val invite = authRepo.findInviteByToken(inviteToken)
        if (invite == null || invite.usedAt != null || invite.expiresAt.isBefore(Instant.now()))
            return RegisterResult.InvalidInvite
        if (authRepo.findUserByUsername(username) != null) return RegisterResult.UsernameTaken
        // Pre-flight: reject a device that is already registered to any account.
        if (keyRepo.getWrappedKeyByDeviceId(deviceId) != null) return RegisterResult.DeviceIdTaken

        // Run all writes in a single transaction so no orphaned rows are left on failure.
        // If dataSource is null (unit tests with mock repos), fall back to non-transactional path.
        return registerInTransaction(
            dataSource, invite, username, displayName, authVerifier, authSalt,
            wrappedMasterKey, wrapFormat, pubkeyFormat, pubkey,
            deviceId, deviceLabel, deviceKind,
            wrappedMasterKeyRecovery, wrapFormatRecovery,
        )
    }

    private fun registerInTransaction(
        ds: DataSource?,
        invite: digital.heirlooms.server.domain.auth.InviteRecord,
        username: String,
        displayName: String,
        authVerifier: ByteArray,
        authSalt: ByteArray,
        wrappedMasterKey: ByteArray,
        wrapFormat: String,
        pubkeyFormat: String,
        pubkey: ByteArray,
        deviceId: String,
        deviceLabel: String,
        deviceKind: String,
        wrappedMasterKeyRecovery: ByteArray?,
        wrapFormatRecovery: String?,
    ): RegisterResult {
        if (ds != null) {
            // Transactional: inline all writes on the shared connection.
            val conn = ds.connection
            conn.autoCommit = false
            try {
                val userId = UUID.randomUUID()
                val now = Instant.now()

                // 1. Create user row.
                conn.prepareStatement(
                    "INSERT INTO users (id, username, display_name, auth_verifier, auth_salt) VALUES (?, ?, ?, ?, ?)"
                ).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setString(2, username)
                    stmt.setString(3, displayName)
                    stmt.setBytes(4, authVerifier)
                    stmt.setBytes(5, authSalt)
                    stmt.executeUpdate()
                }

                // 2. Create system plot for the new user.
                conn.prepareStatement(
                    "INSERT INTO plots (id, owner_user_id, name, sort_order, is_system_defined) VALUES (gen_random_uuid(), ?, '__just_arrived__', -1000, TRUE)"
                ).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.executeUpdate()
                }

                // 3. Optionally store recovery passphrase.
                if (wrappedMasterKeyRecovery != null && !wrapFormatRecovery.isNullOrBlank()) {
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
                        stmt.setBytes(2, wrappedMasterKeyRecovery)
                        stmt.setString(3, wrapFormatRecovery)
                        stmt.setString(4, "{}")
                        stmt.setBytes(5, ByteArray(0))
                        stmt.executeUpdate()
                    }
                }

                // 4. Insert device wrapped key (may throw on duplicate device_id — pre-flight above guards this, but belt-and-suspenders).
                conn.prepareStatement(
                    """INSERT INTO wrapped_keys (id, device_id, device_label, device_kind, pubkey_format,
                           pubkey, wrapped_master_key, wrap_format, created_at, last_used_at, retired_at, user_id)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
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
                    stmt.setNull(11, java.sql.Types.TIMESTAMP)
                    stmt.setObject(12, userId)
                    stmt.executeUpdate()
                }

                // 5. Mark invite used and create friendship.
                conn.prepareStatement(
                    "UPDATE invites SET used_at = NOW(), used_by = ? WHERE id = ?"
                ).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setObject(2, invite.id)
                    stmt.executeUpdate()
                }

                val u1 = if (invite.createdBy.toString() < userId.toString()) invite.createdBy else userId
                val u2 = if (invite.createdBy.toString() < userId.toString()) userId else invite.createdBy
                conn.prepareStatement(
                    "INSERT INTO friendships (user_id_1, user_id_2) VALUES (?, ?) ON CONFLICT DO NOTHING"
                ).use { stmt ->
                    stmt.setObject(1, u1)
                    stmt.setObject(2, u2)
                    stmt.executeUpdate()
                }

                // 6. Issue session token.
                val (token, _, hash) = issueToken()
                val sessionId = UUID.randomUUID()
                val sessionExpiresAt = now.plus(90, ChronoUnit.DAYS)
                conn.prepareStatement(
                    """INSERT INTO user_sessions (id, user_id, token_hash, device_kind, created_at, last_used_at, expires_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?)"""
                ).use { stmt ->
                    stmt.setObject(1, sessionId)
                    stmt.setObject(2, userId)
                    stmt.setBytes(3, hash)
                    stmt.setString(4, deviceKind)
                    stmt.setTimestamp(5, Timestamp.from(now))
                    stmt.setTimestamp(6, Timestamp.from(now))
                    stmt.setTimestamp(7, Timestamp.from(sessionExpiresAt))
                    stmt.executeUpdate()
                }

                conn.commit()
                return RegisterResult.Success(token, userId, sessionExpiresAt)
            } catch (e: org.postgresql.util.PSQLException) {
                try { conn.rollback() } catch (_: Exception) {}
                if (e.serverErrorMessage?.constraint == "wrapped_keys_device_id_key")
                    return RegisterResult.DeviceIdTaken
                throw e
            } catch (e: Exception) {
                try { conn.rollback() } catch (_: Exception) {}
                throw e
            } finally {
                try { conn.autoCommit = true } catch (_: Exception) {}
                conn.close()
            }
        }

        // Non-transactional fallback (unit tests with mock repos / no DataSource).
        val newUser = authRepo.createUser(
            username = username,
            displayName = displayName,
            authVerifier = authVerifier,
            authSalt = authSalt,
        )
        plotRepo.createSystemPlot(newUser.id)

        if (wrappedMasterKeyRecovery != null && !wrapFormatRecovery.isNullOrBlank()) {
            keyRepo.upsertRecoveryPassphrase(
                RecoveryPassphraseRecord(
                    wrappedMasterKey = wrappedMasterKeyRecovery,
                    wrapFormat = wrapFormatRecovery,
                    argon2Params = "{}",
                    salt = ByteArray(0),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
                newUser.id,
            )
        }

        val now = Instant.now()
        keyRepo.insertWrappedKey(
            WrappedKeyRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                deviceLabel = deviceLabel,
                deviceKind = deviceKind,
                pubkeyFormat = pubkeyFormat,
                pubkey = pubkey,
                wrappedMasterKey = wrappedMasterKey,
                wrapFormat = wrapFormat,
                createdAt = now,
                lastUsedAt = now,
                retiredAt = null,
            ),
            userId = newUser.id,
        )

        authRepo.markInviteUsed(invite.id, newUser.id)
        socialRepo.createFriendship(invite.createdBy, newUser.id)

        val (token, _, hash) = issueToken()
        val session = authRepo.createSession(newUser.id, hash, deviceKind)
        return RegisterResult.Success(token, newUser.id, session.expiresAt)
    }


    // ---- Invite connect (existing user → friend) --------------------------------

    sealed class ConnectViaInviteResult {
        data class Success(val inviterDisplayName: String) : ConnectViaInviteResult()
        object InvalidInvite : ConnectViaInviteResult()
        object AlreadyFriends : ConnectViaInviteResult()
        object SelfConnect : ConnectViaInviteResult()
    }

    fun connectViaInvite(requesterId: UUID, inviteToken: String): ConnectViaInviteResult {
        val invite = authRepo.findInviteByToken(inviteToken)
        if (invite == null || invite.usedAt != null || invite.expiresAt.isBefore(Instant.now()))
            return ConnectViaInviteResult.InvalidInvite
        if (invite.createdBy == requesterId)
            return ConnectViaInviteResult.SelfConnect
        if (socialRepo.areFriends(invite.createdBy, requesterId))
            return ConnectViaInviteResult.AlreadyFriends
        authRepo.markInviteUsed(invite.id, requesterId)
        socialRepo.createFriendship(invite.createdBy, requesterId)
        val inviter = authRepo.findUserById(invite.createdBy)
        return ConnectViaInviteResult.Success(inviterDisplayName = inviter?.displayName ?: "")
    }

    // ---- Invite generation -----------------------------------------------------

    data class InviteDetails(val token: String, val expiresAt: Instant)

    fun generateInvite(userId: UUID): InviteDetails {
        val rawToken = urlEnc.encodeToString(generateRawToken())
        val invite = authRepo.createInvite(userId, rawToken)
        return InviteDetails(invite.token, invite.expiresAt)
    }

    // ---- Pairing flow ----------------------------------------------------------

    data class PairingLink(val oneTimeCode: String, val expiresAt: Instant)

    fun initiatePairing(userId: UUID): PairingLink {
        val code = generateNumericCode()
        val link = authRepo.createPairingLink(userId, code)
        return PairingLink(link.oneTimeCode, link.expiresAt)
    }

    sealed class PairingQrResult {
        data class Ok(val sessionId: String) : PairingQrResult()
        object NotFound : PairingQrResult()
    }

    fun pairingQr(code: String): PairingQrResult {
        val link = authRepo.getPendingDeviceLinkByCode(code)
        if (link == null || link.state != "initiated" || link.expiresAt.isBefore(Instant.now()))
            return PairingQrResult.NotFound
        val sessionId = UUID.randomUUID().toString()
        authRepo.setPairingWebSession(link.id, sessionId)
        return PairingQrResult.Ok(sessionId)
    }

    sealed class PairingCompleteResult {
        object Ok : PairingCompleteResult()
        object Unauthorized : PairingCompleteResult()
        object NotFound : PairingCompleteResult()
        object WrongState : PairingCompleteResult()
    }

    fun completePairing(
        callerUserId: UUID,
        sessionId: String,
        wrappedMasterKey: ByteArray,
        wrapFormat: String,
    ): PairingCompleteResult {
        val link = authRepo.getPendingDeviceLinkByWebSessionId(sessionId)
        if (link == null || link.expiresAt.isBefore(Instant.now())) return PairingCompleteResult.NotFound
        if (link.state != "device_registered") return PairingCompleteResult.WrongState
        if (link.userId != callerUserId) return PairingCompleteResult.NotFound

        val (rawToken, _, hash) = issueToken()
        val webSession = authRepo.createSession(callerUserId, hash, "web")
        authRepo.completePairingLink(link.id, wrappedMasterKey, wrapFormat, rawToken, webSession)
        return PairingCompleteResult.Ok
    }

    sealed class PairingStatusResult {
        object Pending : PairingStatusResult()
        data class Complete(
            val sessionToken: String,
            val wrappedMasterKey: ByteArray,
            val wrapFormat: String,
            val expiresAt: Instant?,
        ) : PairingStatusResult()
        object NotFound : PairingStatusResult()
        object Expired : PairingStatusResult()
    }

    fun pairingStatus(sessionId: String): PairingStatusResult {
        val link = authRepo.getPendingDeviceLinkByWebSessionId(sessionId)
            ?: return PairingStatusResult.NotFound
        if (link.state != "wrap_complete" && link.expiresAt.isBefore(Instant.now()))
            return PairingStatusResult.Expired
        if (link.state != "wrap_complete") return PairingStatusResult.Pending
        return PairingStatusResult.Complete(
            sessionToken = link.rawSessionToken ?: "",
            wrappedMasterKey = link.wrappedMasterKey ?: ByteArray(0),
            wrapFormat = link.wrapFormat ?: "",
            expiresAt = link.sessionExpiresAt,
        )
    }

    // ---- Helpers ---------------------------------------------------------------

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
