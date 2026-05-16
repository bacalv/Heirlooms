package digital.heirlooms.server.service.auth

import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.domain.auth.UserSessionRecord
import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.social.SocialRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for [AuthService.revokeDevice] (SEC-011).
 *
 * Uses mockk to stub out repositories — no database required.
 */
class DeviceRevocationServiceTest {

    private val authRepo = mockk<AuthRepository>(relaxed = true)
    private val keyRepo  = mockk<KeyRepository>(relaxed = true)
    private val socialRepo = mockk<SocialRepository>(relaxed = true)
    private val plotRepo   = mockk<PlotRepository>(relaxed = true)
    private val serverSecret = ByteArray(32)

    private lateinit var authService: AuthService

    private val userId   = UUID.randomUUID()
    private val deviceId = "device-abc"
    private val otherId  = "device-xyz"

    private fun makeKey(dId: String, kind: String = "android", retired: Boolean = false) = WrappedKeyRecord(
        id = UUID.randomUUID(),
        deviceId = dId,
        deviceLabel = "Test Device",
        deviceKind = kind,
        pubkeyFormat = "p256-spki",
        pubkey = ByteArray(65),
        wrappedMasterKey = ByteArray(100),
        wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
        createdAt = Instant.now(),
        lastUsedAt = Instant.now(),
        retiredAt = if (retired) Instant.now() else null,
    )

    @BeforeEach
    fun setUp() {
        authService = AuthService(authRepo, keyRepo, socialRepo, plotRepo, serverSecret)
    }

    @Test
    fun `revokeDevice returns NotFound when device does not exist for user`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val sessionRecord = UserSessionRecord(
            id = UUID.randomUUID(), userId = userId, tokenHash = hash,
            deviceKind = "android", createdAt = Instant.now(),
            lastUsedAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600),
        )
        every { authRepo.findSessionByTokenHash(hash) } returns sessionRecord
        every { keyRepo.getWrappedKeyByDeviceIdForUser(deviceId, userId) } returns null

        val result = authService.revokeDevice(userId, deviceId, apiKey)

        assertEquals(AuthService.RevokeDeviceResult.NotFound, result)
    }

    @Test
    fun `revokeDevice returns Forbidden when caller has only one android device and tries to remove it`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val sessionRecord = UserSessionRecord(
            id = UUID.randomUUID(), userId = userId, tokenHash = hash,
            deviceKind = "android", createdAt = Instant.now(),
            lastUsedAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600),
        )
        every { authRepo.findSessionByTokenHash(hash) } returns sessionRecord

        val targetKey = makeKey(deviceId, "android")
        every { keyRepo.getWrappedKeyByDeviceIdForUser(deviceId, userId) } returns targetKey
        // Only one android device — this is the current device
        every { keyRepo.listWrappedKeys(userId) } returns listOf(targetKey)

        val result = authService.revokeDevice(userId, deviceId, apiKey)

        assertEquals(AuthService.RevokeDeviceResult.Forbidden, result)
    }

    @Test
    fun `revokeDevice succeeds when removing a different android device`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val sessionRecord = UserSessionRecord(
            id = UUID.randomUUID(), userId = userId, tokenHash = hash,
            deviceKind = "android", createdAt = Instant.now(),
            lastUsedAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600),
        )
        every { authRepo.findSessionByTokenHash(hash) } returns sessionRecord

        val targetKey = makeKey(otherId, "android")
        val callerKey = makeKey(deviceId, "android")
        every { keyRepo.getWrappedKeyByDeviceIdForUser(otherId, userId) } returns targetKey
        // Two android devices — removing the non-current one is allowed
        every { keyRepo.listWrappedKeys(userId) } returns listOf(targetKey, callerKey)

        val result = authService.revokeDevice(userId, otherId, apiKey)

        assertEquals(AuthService.RevokeDeviceResult.Success, result)
        verify { keyRepo.deleteWrappedKeyByDeviceId(otherId, userId) }
        verify { authRepo.deleteSessionsByDeviceKind(userId, "android") }
    }

    @Test
    fun `revokeDevice succeeds when removing a web device from an android session`() {
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        val apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val sessionRecord = UserSessionRecord(
            id = UUID.randomUUID(), userId = userId, tokenHash = hash,
            deviceKind = "android", createdAt = Instant.now(),
            lastUsedAt = Instant.now(), expiresAt = Instant.now().plusSeconds(3600),
        )
        every { authRepo.findSessionByTokenHash(hash) } returns sessionRecord

        val webKey = makeKey("web-device-1", "web")
        every { keyRepo.getWrappedKeyByDeviceIdForUser("web-device-1", userId) } returns webKey
        every { keyRepo.listWrappedKeys(userId) } returns listOf(webKey, makeKey(deviceId, "android"))

        val result = authService.revokeDevice(userId, "web-device-1", apiKey)

        assertEquals(AuthService.RevokeDeviceResult.Success, result)
        verify { keyRepo.deleteWrappedKeyByDeviceId("web-device-1", userId) }
        verify { authRepo.deleteSessionsByDeviceKind(userId, "web") }
    }

    @Test
    fun `revokeDevice returns Forbidden when session cannot be resolved`() {
        // apiKey that doesn't resolve to any session
        every { authRepo.findSessionByTokenHash(any()) } returns null

        val result = authService.revokeDevice(userId, deviceId, "bad-api-key")

        assertEquals(AuthService.RevokeDeviceResult.Forbidden, result)
    }
}
