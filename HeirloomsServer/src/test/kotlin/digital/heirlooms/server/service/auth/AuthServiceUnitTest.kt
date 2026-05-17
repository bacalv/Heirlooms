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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for [AuthService] covering branches not exercised by the integration
 * suite in [AuthHandlerTest].  No database required — all repositories are mocked.
 *
 * SEC-002 Phase 2.
 */
class AuthServiceUnitTest {

    private val authRepo   = mockk<AuthRepository>(relaxed = true)
    private val keyRepo    = mockk<KeyRepository>(relaxed = true)
    private val socialRepo = mockk<SocialRepository>(relaxed = true)
    private val plotRepo   = mockk<PlotRepository>(relaxed = true)
    private val serverSecret = ByteArray(32) { it.toByte() }

    private lateinit var svc: AuthService

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        svc = AuthService(authRepo, keyRepo, socialRepo, plotRepo, serverSecret)
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun sha256(b: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(b)

    private fun makeSession(
        uid: UUID = userId,
        kind: String = "android",
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) = UserSessionRecord(
        id = UUID.randomUUID(), userId = uid, tokenHash = ByteArray(32),
        deviceKind = kind, createdAt = Instant.now(),
        lastUsedAt = Instant.now(), expiresAt = expiresAt,
    )

    private fun makeUser(
        id: UUID = userId,
        authVerifier: ByteArray? = sha256(ByteArray(32) { 1 }),
        authSalt: ByteArray? = ByteArray(16) { 2 },
    ) = UserRecord(
        id = id, username = "alice", displayName = "Alice",
        authVerifier = authVerifier, authSalt = authSalt,
        createdAt = Instant.now(), requireBiometric = false,
    )

    private fun makeWrappedKey(deviceId: String = "dev-1", kind: String = "android", retired: Boolean = false) =
        WrappedKeyRecord(
            id = UUID.randomUUID(), deviceId = deviceId,
            deviceLabel = "Test Device", deviceKind = kind,
            pubkeyFormat = "p256-spki", pubkey = ByteArray(65),
            wrappedMasterKey = ByteArray(64), wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
            createdAt = Instant.now(), lastUsedAt = Instant.now(),
            retiredAt = if (retired) Instant.now() else null,
        )

    private fun makeInvite(
        usedAt: Instant? = null,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        createdBy: UUID = UUID.randomUUID(),
    ) = InviteRecord(
        id = UUID.randomUUID(), token = "tok-${UUID.randomUUID()}",
        createdBy = createdBy, createdAt = Instant.now(),
        expiresAt = expiresAt, usedAt = usedAt, usedBy = null,
    )

    private fun makePendingLink(
        state: String = "initiated",
        expiresAt: Instant = Instant.now().plusSeconds(600),
        uid: UUID = userId,
        webSessionId: String? = null,
    ) = PendingDeviceLinkRecord(
        id = UUID.randomUUID(), oneTimeCode = "12345678",
        expiresAt = expiresAt, state = state, userId = uid,
        newDeviceId = null, newDeviceLabel = null, newDeviceKind = null,
        newPubkeyFormat = null, newPubkey = null,
        wrappedMasterKey = null, wrapFormat = null,
        webSessionId = webSessionId,
    )

    // ─── decodeBase64Url ──────────────────────────────────────────────────────

    @Test
    fun `decodeBase64Url returns null for null input`() {
        assertNull(svc.decodeBase64Url(null))
    }

    @Test
    fun `decodeBase64Url returns null for blank input`() {
        assertNull(svc.decodeBase64Url("   "))
    }

    @Test
    fun `decodeBase64Url decodes url-safe base64`() {
        val bytes = ByteArray(16) { it.toByte() }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertArrayEquals(bytes, svc.decodeBase64Url(encoded))
    }

    @Test
    fun `decodeBase64Url falls back to standard base64`() {
        val bytes = ByteArray(16) { (it + 100).toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        // Standard base64 uses + and / which url decoder rejects; fallback must handle it.
        assertArrayEquals(bytes, svc.decodeBase64Url(encoded))
    }

    @Test
    fun `decodeBase64Url returns null for truly invalid input`() {
        assertNull(svc.decodeBase64Url("!!!not-base64!!!"))
    }

    // ─── resolveSession ───────────────────────────────────────────────────────

    @Test
    fun `resolveSession returns null for null apiKey`() {
        assertNull(svc.resolveSession(null))
    }

    @Test
    fun `resolveSession returns null when token is not decodable as base64`() {
        // Not valid url-safe OR std base64
        assertNull(svc.resolveSession("!!!INVALID!!!"))
    }

    @Test
    fun `resolveSession returns null when no session found for hash`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        every { authRepo.findSessionByTokenHash(any()) } returns null
        assertNull(svc.resolveSession(apiKey))
    }

    @Test
    fun `resolveSession returns null for expired session`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = sha256(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val expired = makeSession(expiresAt = Instant.now().minusSeconds(1))
        every { authRepo.findSessionByTokenHash(hash) } returns expired
        assertNull(svc.resolveSession(apiKey))
    }

    @Test
    fun `resolveSession returns session when valid`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = sha256(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val session = makeSession()
        every { authRepo.findSessionByTokenHash(hash) } returns session
        assertEquals(session, svc.resolveSession(apiKey))
    }

    // ─── getSaltForChallenge ──────────────────────────────────────────────────

    @Test
    fun `getSaltForChallenge returns fake salt when user not found`() {
        every { authRepo.findUserByUsername("ghost") } returns null
        val salt = svc.getSaltForChallenge("ghost")
        assertEquals(16, salt.size)
        // Must be deterministic
        val salt2 = svc.getSaltForChallenge("ghost")
        assertArrayEquals(salt, salt2)
    }

    @Test
    fun `getSaltForChallenge returns fake salt when user has null authSalt`() {
        val user = makeUser(authSalt = null, authVerifier = null)
        every { authRepo.findUserByUsername("alice") } returns user
        val salt = svc.getSaltForChallenge("alice")
        assertEquals(16, salt.size)
    }

    @Test
    fun `getSaltForChallenge returns stored authSalt when user has one`() {
        val storedSalt = ByteArray(16) { 42 }
        val user = makeUser(authSalt = storedSalt)
        every { authRepo.findUserByUsername("alice") } returns user
        assertArrayEquals(storedSalt, svc.getSaltForChallenge("alice"))
    }

    // ─── fakeSalt ─────────────────────────────────────────────────────────────

    @Test
    fun `fakeSalt is deterministic for same username`() {
        val s1 = svc.fakeSalt("test@example.com")
        val s2 = svc.fakeSalt("test@example.com")
        assertArrayEquals(s1, s2)
    }

    @Test
    fun `fakeSalt differs for different usernames`() {
        val s1 = svc.fakeSalt("alice")
        val s2 = svc.fakeSalt("bob")
        assertTrue(!s1.contentEquals(s2))
    }

    @Test
    fun `fakeSalt returns 16 bytes`() {
        assertEquals(16, svc.fakeSalt("whoever").size)
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    fun `login returns InvalidCredentials when user not found`() {
        every { authRepo.findUserByUsername("nobody") } returns null
        assertEquals(AuthService.LoginResult.InvalidCredentials, svc.login("nobody", ByteArray(32)))
    }

    @Test
    fun `login returns InvalidCredentials when authVerifier is null`() {
        val user = makeUser(authVerifier = null)
        every { authRepo.findUserByUsername("alice") } returns user
        assertEquals(AuthService.LoginResult.InvalidCredentials, svc.login("alice", ByteArray(32)))
    }

    @Test
    fun `login returns InvalidCredentials when authKey hash does not match`() {
        val correctAuthKey = ByteArray(32) { 1 }
        val user = makeUser(authVerifier = sha256(correctAuthKey))
        every { authRepo.findUserByUsername("alice") } returns user
        val wrongKey = ByteArray(32) { 2 }
        assertEquals(AuthService.LoginResult.InvalidCredentials, svc.login("alice", wrongKey))
    }

    @Test
    fun `login returns Success when credentials are correct`() {
        val authKey = ByteArray(32) { 9 }
        val user = makeUser(authVerifier = sha256(authKey))
        every { authRepo.findUserByUsername("alice") } returns user
        val session = makeSession(uid = user.id)
        every { authRepo.createSession(user.id, any(), "android") } returns session

        val result = svc.login("alice", authKey)
        assertTrue(result is AuthService.LoginResult.Success)
    }

    // ─── setupExisting ────────────────────────────────────────────────────────

    @Test
    fun `setupExisting returns InvalidCredentials when user not found`() {
        every { authRepo.findUserByUsername("ghost") } returns null
        val result = svc.setupExisting("ghost", "dev", ByteArray(32), ByteArray(16), null, null)
        assertEquals(AuthService.SetupExistingResult.InvalidCredentials, result)
    }

    @Test
    fun `setupExisting returns PassphraseAlreadySet when authVerifier is already set`() {
        val user = makeUser(authVerifier = ByteArray(32))
        every { authRepo.findUserByUsername("alice") } returns user
        val result = svc.setupExisting("alice", "dev", ByteArray(32), ByteArray(16), null, null)
        assertEquals(AuthService.SetupExistingResult.PassphraseAlreadySet, result)
    }

    @Test
    fun `setupExisting returns NoDeviceKey when device not found`() {
        val user = makeUser(authVerifier = null)
        every { authRepo.findUserByUsername("alice") } returns user
        every { keyRepo.getWrappedKeyByDeviceIdAndUser("dev", user.id) } returns null
        val result = svc.setupExisting("alice", "dev", ByteArray(32), ByteArray(16), null, null)
        assertEquals(AuthService.SetupExistingResult.NoDeviceKey, result)
    }

    @Test
    fun `setupExisting skips recovery upsert when wrappedMasterKeyRecovery is null`() {
        val user = makeUser(authVerifier = null)
        every { authRepo.findUserByUsername("alice") } returns user
        val key = makeWrappedKey()
        every { keyRepo.getWrappedKeyByDeviceIdAndUser("dev", user.id) } returns key
        every { authRepo.setUserAuth(user.id, any(), any()) } just runs
        every { authRepo.deleteAllSessionsForUser(user.id) } just runs
        val session = makeSession(uid = user.id)
        every { authRepo.createSession(user.id, any(), key.deviceKind) } returns session

        val result = svc.setupExisting("alice", "dev", ByteArray(32), ByteArray(16), null, null)
        assertTrue(result is AuthService.SetupExistingResult.Success)
        // Recovery passphrase must NOT have been upserted
        verify(exactly = 0) { keyRepo.upsertRecoveryPassphrase(any(), any()) }
    }

    @Test
    fun `setupExisting skips recovery upsert when wrapFormatRecovery is blank`() {
        val user = makeUser(authVerifier = null)
        every { authRepo.findUserByUsername("alice") } returns user
        val key = makeWrappedKey()
        every { keyRepo.getWrappedKeyByDeviceIdAndUser("dev", user.id) } returns key
        every { authRepo.setUserAuth(user.id, any(), any()) } just runs
        every { authRepo.deleteAllSessionsForUser(user.id) } just runs
        val session = makeSession(uid = user.id)
        every { authRepo.createSession(user.id, any(), key.deviceKind) } returns session

        // wrappedMasterKeyRecovery non-null but wrapFormatRecovery blank → skip
        val result = svc.setupExisting("alice", "dev", ByteArray(32), ByteArray(16), ByteArray(64), "")
        assertTrue(result is AuthService.SetupExistingResult.Success)
        verify(exactly = 0) { keyRepo.upsertRecoveryPassphrase(any(), any()) }
    }

    @Test
    fun `setupExisting stores recovery passphrase when both fields present`() {
        val user = makeUser(authVerifier = null)
        every { authRepo.findUserByUsername("alice") } returns user
        val key = makeWrappedKey()
        every { keyRepo.getWrappedKeyByDeviceIdAndUser("dev", user.id) } returns key
        every { authRepo.setUserAuth(user.id, any(), any()) } just runs
        every { authRepo.deleteAllSessionsForUser(user.id) } just runs
        every { keyRepo.upsertRecoveryPassphrase(any(), user.id) } just runs
        val session = makeSession(uid = user.id)
        every { authRepo.createSession(user.id, any(), key.deviceKind) } returns session

        val result = svc.setupExisting("alice", "dev", ByteArray(32), ByteArray(16), ByteArray(64), "argon2id-aes256gcm-v1")
        assertTrue(result is AuthService.SetupExistingResult.Success)
        verify(exactly = 1) { keyRepo.upsertRecoveryPassphrase(any(), user.id) }
    }

    // ─── register (non-transactional path, dataSource == null) ───────────────

    @Test
    fun `register returns InvalidInvite when invite not found`() {
        every { authRepo.findInviteByToken("bad") } returns null
        val result = svc.register(
            "bad", "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android", null, null,
        )
        assertEquals(AuthService.RegisterResult.InvalidInvite, result)
    }

    @Test
    fun `register returns InvalidInvite when invite is already used`() {
        val invite = makeInvite(usedAt = Instant.now().minusSeconds(60))
        every { authRepo.findInviteByToken("tok") } returns invite
        val result = svc.register(
            "tok", "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android", null, null,
        )
        assertEquals(AuthService.RegisterResult.InvalidInvite, result)
    }

    @Test
    fun `register returns InvalidInvite when invite is expired`() {
        val invite = makeInvite(expiresAt = Instant.now().minusSeconds(1))
        every { authRepo.findInviteByToken("tok") } returns invite
        val result = svc.register(
            "tok", "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android", null, null,
        )
        assertEquals(AuthService.RegisterResult.InvalidInvite, result)
    }

    @Test
    fun `register returns UsernameTaken when username already exists`() {
        val invite = makeInvite()
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { authRepo.findUserByUsername("alice") } returns makeUser()
        val result = svc.register(
            invite.token, "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android", null, null,
        )
        assertEquals(AuthService.RegisterResult.UsernameTaken, result)
    }

    @Test
    fun `register returns DeviceIdTaken when device already registered`() {
        val invite = makeInvite()
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { authRepo.findUserByUsername("alice") } returns null
        every { keyRepo.getWrappedKeyByDeviceId("dev-1") } returns makeWrappedKey("dev-1")
        val result = svc.register(
            invite.token, "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android", null, null,
        )
        assertEquals(AuthService.RegisterResult.DeviceIdTaken, result)
    }

    @Test
    fun `register non-transactional path succeeds without recovery passphrase`() {
        val invite = makeInvite()
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { authRepo.findUserByUsername("alice") } returns null
        every { keyRepo.getWrappedKeyByDeviceId("dev-1") } returns null
        val newUser = makeUser()
        // authRepo is relaxed, so createUser returns a mock UserRecord; we capture its id via
        // answers to return the same newUser consistently.
        every { authRepo.createUser(any(), any(), any(), any(), any()) } returns newUser
        every { plotRepo.createSystemPlot(any()) } just runs
        every { keyRepo.insertWrappedKey(any(), any()) } just runs
        every { authRepo.markInviteUsed(any(), any()) } just runs
        every { socialRepo.createFriendship(any(), any()) } just runs
        every { authRepo.createSession(any(), any(), any()) } returns makeSession(uid = newUser.id)

        // dataSource == null → non-transactional path
        val result = svc.register(
            invite.token, "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android", null, null,
        )
        assertTrue(result is AuthService.RegisterResult.Success)
        verify(exactly = 0) { keyRepo.upsertRecoveryPassphrase(any(), any()) }
    }

    @Test
    fun `register non-transactional path stores recovery passphrase when provided`() {
        val invite = makeInvite()
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { authRepo.findUserByUsername("alice") } returns null
        every { keyRepo.getWrappedKeyByDeviceId("dev-1") } returns null
        val newUser = makeUser()
        every { authRepo.createUser(any(), any(), any(), any(), any()) } returns newUser
        every { plotRepo.createSystemPlot(any()) } just runs
        every { keyRepo.upsertRecoveryPassphrase(any(), any()) } just runs
        every { keyRepo.insertWrappedKey(any(), any()) } just runs
        every { authRepo.markInviteUsed(any(), any()) } just runs
        every { socialRepo.createFriendship(any(), any()) } just runs
        every { authRepo.createSession(any(), any(), any()) } returns makeSession(uid = newUser.id)

        val result = svc.register(
            invite.token, "alice", "Alice", ByteArray(32), ByteArray(16),
            ByteArray(64), "fmt", "p256-spki", ByteArray(65),
            "dev-1", "Phone", "android",
            ByteArray(80), "argon2id-aes256gcm-v1",
        )
        assertTrue(result is AuthService.RegisterResult.Success)
        verify(exactly = 1) { keyRepo.upsertRecoveryPassphrase(any(), any()) }
    }

    // ─── connectViaInvite ─────────────────────────────────────────────────────

    @Test
    fun `connectViaInvite returns InvalidInvite when invite not found`() {
        every { authRepo.findInviteByToken("bad") } returns null
        assertEquals(
            AuthService.ConnectViaInviteResult.InvalidInvite,
            svc.connectViaInvite(userId, "bad"),
        )
    }

    @Test
    fun `connectViaInvite returns InvalidInvite when invite is expired`() {
        val invite = makeInvite(expiresAt = Instant.now().minusSeconds(1))
        every { authRepo.findInviteByToken(invite.token) } returns invite
        assertEquals(
            AuthService.ConnectViaInviteResult.InvalidInvite,
            svc.connectViaInvite(userId, invite.token),
        )
    }

    @Test
    fun `connectViaInvite returns InvalidInvite when invite is already used`() {
        val invite = makeInvite(usedAt = Instant.now().minusSeconds(60))
        every { authRepo.findInviteByToken(invite.token) } returns invite
        assertEquals(
            AuthService.ConnectViaInviteResult.InvalidInvite,
            svc.connectViaInvite(userId, invite.token),
        )
    }

    @Test
    fun `connectViaInvite returns SelfConnect when requester is invite creator`() {
        val invite = makeInvite(createdBy = userId)
        every { authRepo.findInviteByToken(invite.token) } returns invite
        assertEquals(
            AuthService.ConnectViaInviteResult.SelfConnect,
            svc.connectViaInvite(userId, invite.token),
        )
    }

    @Test
    fun `connectViaInvite returns AlreadyFriends when friendship exists`() {
        val creatorId = UUID.randomUUID()
        val invite = makeInvite(createdBy = creatorId)
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { socialRepo.areFriends(creatorId, userId) } returns true
        assertEquals(
            AuthService.ConnectViaInviteResult.AlreadyFriends,
            svc.connectViaInvite(userId, invite.token),
        )
    }

    @Test
    fun `connectViaInvite returns Success and creates friendship`() {
        val creatorId = UUID.randomUUID()
        val invite = makeInvite(createdBy = creatorId)
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { socialRepo.areFriends(creatorId, userId) } returns false
        every { authRepo.markInviteUsed(invite.id, userId) } just runs
        every { socialRepo.createFriendship(creatorId, userId) } just runs
        val creator = makeUser(id = creatorId)
        every { authRepo.findUserById(creatorId) } returns creator

        val result = svc.connectViaInvite(userId, invite.token)
        assertTrue(result is AuthService.ConnectViaInviteResult.Success)
        assertEquals(
            creator.displayName,
            (result as AuthService.ConnectViaInviteResult.Success).inviterDisplayName,
        )
    }

    @Test
    fun `connectViaInvite returns Success with empty displayName when inviter not found`() {
        val creatorId = UUID.randomUUID()
        val invite = makeInvite(createdBy = creatorId)
        every { authRepo.findInviteByToken(invite.token) } returns invite
        every { socialRepo.areFriends(creatorId, userId) } returns false
        every { authRepo.markInviteUsed(invite.id, userId) } just runs
        every { socialRepo.createFriendship(creatorId, userId) } just runs
        every { authRepo.findUserById(creatorId) } returns null

        val result = svc.connectViaInvite(userId, invite.token)
        assertTrue(result is AuthService.ConnectViaInviteResult.Success)
        assertEquals("", (result as AuthService.ConnectViaInviteResult.Success).inviterDisplayName)
    }

    // ─── pairingQr ────────────────────────────────────────────────────────────

    @Test
    fun `pairingQr returns NotFound when code not found`() {
        every { authRepo.getPendingDeviceLinkByCode("00000000") } returns null
        assertEquals(AuthService.PairingQrResult.NotFound, svc.pairingQr("00000000"))
    }

    @Test
    fun `pairingQr returns NotFound when link is expired`() {
        val link = makePendingLink(state = "initiated", expiresAt = Instant.now().minusSeconds(1))
        every { authRepo.getPendingDeviceLinkByCode(link.oneTimeCode) } returns link
        assertEquals(AuthService.PairingQrResult.NotFound, svc.pairingQr(link.oneTimeCode))
    }

    @Test
    fun `pairingQr returns NotFound when link state is not initiated`() {
        val link = makePendingLink(state = "device_registered")
        every { authRepo.getPendingDeviceLinkByCode(link.oneTimeCode) } returns link
        assertEquals(AuthService.PairingQrResult.NotFound, svc.pairingQr(link.oneTimeCode))
    }

    @Test
    fun `pairingQr returns Ok and sets web session`() {
        val link = makePendingLink(state = "initiated")
        every { authRepo.getPendingDeviceLinkByCode(link.oneTimeCode) } returns link
        every { authRepo.setPairingWebSession(link.id, any()) } just runs

        val result = svc.pairingQr(link.oneTimeCode)
        assertTrue(result is AuthService.PairingQrResult.Ok)
    }

    // ─── completePairing ──────────────────────────────────────────────────────

    @Test
    fun `completePairing returns NotFound when session not found`() {
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns null
        assertEquals(
            AuthService.PairingCompleteResult.NotFound,
            svc.completePairing(userId, "sid", ByteArray(64), "fmt"),
        )
    }

    @Test
    fun `completePairing returns NotFound when link is expired`() {
        val link = makePendingLink(state = "device_registered", expiresAt = Instant.now().minusSeconds(1), webSessionId = "sid")
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link
        assertEquals(
            AuthService.PairingCompleteResult.NotFound,
            svc.completePairing(userId, "sid", ByteArray(64), "fmt"),
        )
    }

    @Test
    fun `completePairing returns WrongState when link state is not device_registered`() {
        val link = makePendingLink(state = "initiated", webSessionId = "sid")
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link
        assertEquals(
            AuthService.PairingCompleteResult.WrongState,
            svc.completePairing(userId, "sid", ByteArray(64), "fmt"),
        )
    }

    @Test
    fun `completePairing returns NotFound when link belongs to different user`() {
        val otherUserId = UUID.randomUUID()
        val link = makePendingLink(state = "device_registered", uid = otherUserId, webSessionId = "sid")
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link
        assertEquals(
            AuthService.PairingCompleteResult.NotFound,
            svc.completePairing(userId, "sid", ByteArray(64), "fmt"),
        )
    }

    @Test
    fun `completePairing returns Ok when all conditions are met`() {
        val link = makePendingLink(state = "device_registered", uid = userId, webSessionId = "sid")
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link
        val session = makeSession(uid = userId, kind = "web")
        every { authRepo.createSession(userId, any(), "web") } returns session
        every { authRepo.completePairingLink(link.id, any(), any(), any(), session) } just runs

        assertEquals(
            AuthService.PairingCompleteResult.Ok,
            svc.completePairing(userId, "sid", ByteArray(64), "fmt"),
        )
    }

    // ─── pairingStatus ────────────────────────────────────────────────────────

    @Test
    fun `pairingStatus returns NotFound when no link found`() {
        every { authRepo.getPendingDeviceLinkByWebSessionId("unknown") } returns null
        assertEquals(AuthService.PairingStatusResult.NotFound, svc.pairingStatus("unknown"))
    }

    @Test
    fun `pairingStatus returns Expired when not wrap_complete and past expiry`() {
        val link = makePendingLink(state = "initiated", expiresAt = Instant.now().minusSeconds(1), webSessionId = "sid")
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link
        assertEquals(AuthService.PairingStatusResult.Expired, svc.pairingStatus("sid"))
    }

    @Test
    fun `pairingStatus returns Pending when state is not wrap_complete and not expired`() {
        val link = makePendingLink(state = "device_registered", webSessionId = "sid")
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link
        assertEquals(AuthService.PairingStatusResult.Pending, svc.pairingStatus("sid"))
    }

    @Test
    fun `pairingStatus returns Complete when state is wrap_complete`() {
        val link = makePendingLink(state = "wrap_complete", webSessionId = "sid").copy(
            rawSessionToken = "tok",
            wrappedMasterKey = ByteArray(64),
            wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
            sessionExpiresAt = Instant.now().plusSeconds(3600),
        )
        every { authRepo.getPendingDeviceLinkByWebSessionId("sid") } returns link

        val result = svc.pairingStatus("sid")
        assertTrue(result is AuthService.PairingStatusResult.Complete)
        assertEquals("tok", (result as AuthService.PairingStatusResult.Complete).sessionToken)
    }

    // ─── issueToken ───────────────────────────────────────────────────────────

    @Test
    fun `issueToken produces a 32-byte raw token and a 32-byte sha256 hash`() {
        val (token, raw, hash) = svc.issueToken()
        assertTrue(token.isNotBlank())
        assertEquals(32, raw.size)
        assertEquals(32, hash.size)
        assertArrayEquals(sha256(raw), hash)
    }

    // ─── generateNumericCode / generateRawToken ───────────────────────────────

    @Test
    fun `generateNumericCode returns an 8-digit string`() {
        val code = svc.generateNumericCode()
        assertEquals(8, code.length)
        assertTrue(code.all { it.isDigit() })
        val n = code.toInt()
        assertTrue(n in 10_000_000..99_999_999)
    }

    @Test
    fun `generateRawToken returns 32 random bytes`() {
        val t1 = svc.generateRawToken()
        val t2 = svc.generateRawToken()
        assertEquals(32, t1.size)
        assertEquals(32, t2.size)
        // Vanishingly unlikely to be equal
        assertTrue(!t1.contentEquals(t2))
    }

    // ─── revokeDevice edge: callerDeviceKindHint fallback ────────────────────

    @Test
    fun `revokeDevice uses callerDeviceKindHint when session resolves to null`() {
        // apiKey that decodes fine but finds no session in the repo
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        every { authRepo.findSessionByTokenHash(any()) } returns null

        val webKey = makeWrappedKey("web-dev", "web")
        every { keyRepo.getWrappedKeyByDeviceIdForUser("web-dev", userId) } returns webKey
        every { keyRepo.listWrappedKeys(userId) } returns listOf(webKey, makeWrappedKey("android-dev", "android"))
        every { keyRepo.deleteWrappedKeyByDeviceId("web-dev", userId) } just runs
        every { authRepo.deleteSessionsByDeviceKind(userId, "web") } just runs

        // Hint = "android" so the web device is not the caller device — revocation is allowed
        val result = svc.revokeDevice(userId, "web-dev", apiKey, callerDeviceKindHint = "android")
        assertEquals(AuthService.RevokeDeviceResult.Success, result)
    }

    @Test
    fun `revokeDevice returns Forbidden when no session and no hint`() {
        every { authRepo.findSessionByTokenHash(any()) } returns null
        val result = svc.revokeDevice(userId, "dev", null, callerDeviceKindHint = null)
        assertEquals(AuthService.RevokeDeviceResult.Forbidden, result)
    }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Test
    fun `logout does nothing when session cannot be resolved`() {
        // Calling logout with a null apiKey should be a no-op
        svc.logout(null)
        verify(exactly = 0) { authRepo.deleteSession(any()) }
    }

    @Test
    fun `logout deletes session when apiKey resolves`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = sha256(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val session = makeSession()
        every { authRepo.findSessionByTokenHash(hash) } returns session
        every { authRepo.deleteSession(session.id) } just runs

        svc.logout(apiKey)
        verify(exactly = 1) { authRepo.deleteSession(session.id) }
    }

    // ─── getMe / getAccount ───────────────────────────────────────────────────

    @Test
    fun `getMe returns null when user not found`() {
        every { authRepo.findUserById(userId) } returns null
        assertNull(svc.getMe(userId))
    }

    @Test
    fun `getAccount delegates to findUserById`() {
        val user = makeUser()
        every { authRepo.findUserById(userId) } returns user
        assertEquals(user, svc.getAccount(userId))
    }

    // ─── generateInvite ───────────────────────────────────────────────────────

    @Test
    fun `generateInvite creates invite via repo and returns token and expiresAt`() {
        val fakeInvite = makeInvite()
        every { authRepo.createInvite(userId, any()) } returns fakeInvite
        val details = svc.generateInvite(userId)
        assertNotNull(details.token)
        assertNotNull(details.expiresAt)
        assertEquals(fakeInvite.token, details.token)
    }

    // ─── initiatePairing ─────────────────────────────────────────────────────

    @Test
    fun `initiatePairing returns a PairingLink with code and expiresAt`() {
        val link = makePendingLink()
        every { authRepo.createPairingLink(userId, any()) } returns link
        val result = svc.initiatePairing(userId)
        assertNotNull(result.oneTimeCode)
        assertNotNull(result.expiresAt)
    }
}
