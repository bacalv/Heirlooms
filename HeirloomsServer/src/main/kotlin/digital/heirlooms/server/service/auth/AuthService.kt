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
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
     */
    fun fakeSalt(username: String): ByteArray {
        val secret = if (serverSecret.isEmpty()) ByteArray(32) else serverSecret
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
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
        androidSession: UserSessionRecord,
        sessionId: String,
        wrappedMasterKey: ByteArray,
        wrapFormat: String,
    ): PairingCompleteResult {
        val link = authRepo.getPendingDeviceLinkByWebSessionId(sessionId)
        if (link == null || link.expiresAt.isBefore(Instant.now())) return PairingCompleteResult.NotFound
        if (link.state != "device_registered") return PairingCompleteResult.WrongState
        if (link.userId != androidSession.userId) return PairingCompleteResult.NotFound

        val (rawToken, _, hash) = issueToken()
        val webSession = authRepo.createSession(androidSession.userId, hash, "web")
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
