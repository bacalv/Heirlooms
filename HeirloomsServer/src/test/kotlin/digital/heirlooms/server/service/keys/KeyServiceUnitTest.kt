package digital.heirlooms.server.service.keys

import digital.heirlooms.server.domain.keys.PendingDeviceLinkRecord
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import digital.heirlooms.server.repository.keys.KeyRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for [KeyService] covering branches not exercised by [KeysHandlerTest].
 *
 * All tests use mockk — no database required.
 * SEC-002 Phase 2.
 */
class KeyServiceUnitTest {

    private val keyRepo = mockk<KeyRepository>(relaxed = true)
    private lateinit var svc: KeyService

    private val userId = UUID.randomUUID()
    private val enc = Base64.getEncoder()

    @BeforeEach
    fun setUp() {
        svc = KeyService(keyRepo)
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun makeKey(
        deviceId: String = "dev-1",
        kind: String = "android",
        retired: Boolean = false,
    ) = WrappedKeyRecord(
        id = UUID.randomUUID(),
        deviceId = deviceId,
        deviceLabel = "Test Device",
        deviceKind = kind,
        pubkeyFormat = "p256-spki",
        pubkey = ByteArray(65) { 1 },
        wrappedMasterKey = ByteArray(64) { 2 },
        wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
        createdAt = Instant.now(),
        lastUsedAt = Instant.now(),
        retiredAt = if (retired) Instant.now() else null,
    )

    private fun makeLink(
        state: String = "initiated",
        expiresAt: Instant = Instant.now().plusSeconds(900),
        code: String = "ABCD-1234",
        deviceId: String? = null,
        deviceLabel: String? = null,
        deviceKind: String? = null,
        pubkeyFormat: String? = null,
        pubkey: ByteArray? = null,
    ) = PendingDeviceLinkRecord(
        id = UUID.randomUUID(),
        oneTimeCode = code,
        expiresAt = expiresAt,
        state = state,
        newDeviceId = deviceId,
        newDeviceLabel = deviceLabel,
        newDeviceKind = deviceKind,
        newPubkeyFormat = pubkeyFormat,
        newPubkey = pubkey,
        wrappedMasterKey = null,
        wrapFormat = null,
        userId = userId,
    )

    // ─── generateLinkCode ─────────────────────────────────────────────────────

    @Test
    fun `generateLinkCode returns XXXX-XXXX format`() {
        val code = svc.generateLinkCode()
        assertEquals(9, code.length)
        assertEquals('-', code[4])
    }

    @Test
    fun `generateLinkCode uses only valid charset characters`() {
        val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()
        repeat(20) {
            val code = svc.generateLinkCode()
            code.filter { it != '-' }.forEach { ch ->
                assertTrue(ch in validChars, "Unexpected char '$ch' in code '$code'")
            }
        }
    }

    // ─── registerDevice ───────────────────────────────────────────────────────

    @Test
    fun `registerDevice returns Invalid for unknown deviceKind`() {
        val result = svc.registerDevice(
            "dev-1", "Phone", "tablet",
            "p256-spki", enc.encodeToString(ByteArray(65)),
            enc.encodeToString(ByteArray(64)), "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertTrue(result is KeyService.RegisterResult.Invalid)
    }

    @Test
    fun `registerDevice returns AlreadyRegistered when device exists for user`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-1", userId) } returns makeKey("dev-1")
        val result = svc.registerDevice(
            "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65)),
            enc.encodeToString(ByteArray(64)), "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertEquals(KeyService.RegisterResult.AlreadyRegistered, result)
    }

    @Test
    fun `registerDevice returns Invalid when pubkey is not valid base64`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser(any(), userId) } returns null
        val result = svc.registerDevice(
            "dev-1", "Phone", "android",
            "p256-spki", "!!!not-base64!!!",
            enc.encodeToString(ByteArray(64)), "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertTrue(result is KeyService.RegisterResult.Invalid)
        assertTrue((result as KeyService.RegisterResult.Invalid).message.contains("pubkey"))
    }

    @Test
    fun `registerDevice returns Invalid when wrappedMasterKey is not valid base64`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser(any(), userId) } returns null
        val result = svc.registerDevice(
            "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65)),
            "!!!not-base64!!!", "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertTrue(result is KeyService.RegisterResult.Invalid)
        assertTrue((result as KeyService.RegisterResult.Invalid).message.contains("wrappedMasterKey"))
    }

    @Test
    fun `registerDevice returns Created and inserts record on success`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-new", userId) } returns null
        every { keyRepo.insertWrappedKey(any(), userId) } just runs

        val result = svc.registerDevice(
            "dev-new", "Pixel 9", "android",
            "p256-spki", enc.encodeToString(ByteArray(65) { 5 }),
            enc.encodeToString(ByteArray(64) { 6 }), "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertTrue(result is KeyService.RegisterResult.Created)
        verify { keyRepo.insertWrappedKey(any(), userId) }
    }

    @Test
    fun `registerDevice accepts ios deviceKind`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser(any(), userId) } returns null
        every { keyRepo.insertWrappedKey(any(), userId) } just runs

        val result = svc.registerDevice(
            "ios-dev", "iPhone", "ios",
            "p256-spki", enc.encodeToString(ByteArray(65)),
            enc.encodeToString(ByteArray(64)), "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertTrue(result is KeyService.RegisterResult.Created)
    }

    @Test
    fun `registerDevice accepts web deviceKind`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser(any(), userId) } returns null
        every { keyRepo.insertWrappedKey(any(), userId) } just runs

        val result = svc.registerDevice(
            "web-dev", "Firefox", "web",
            "p256-spki", enc.encodeToString(ByteArray(65)),
            enc.encodeToString(ByteArray(64)), "p256-ecdh-hkdf-aes256gcm-v1", userId,
        )
        assertTrue(result is KeyService.RegisterResult.Created)
    }

    // ─── retireDevice ─────────────────────────────────────────────────────────

    @Test
    fun `retireDevice returns false when device not found for user`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-x", userId) } returns null
        assertFalse(svc.retireDevice("dev-x", userId))
    }

    @Test
    fun `retireDevice returns false when device is already retired`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-1", userId) } returns makeKey("dev-1", retired = true)
        assertFalse(svc.retireDevice("dev-1", userId))
    }

    @Test
    fun `retireDevice returns true and retires key when device is active`() {
        val key = makeKey("dev-1")
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-1", userId) } returns key
        every { keyRepo.retireWrappedKey(key.id, any()) } just runs

        assertTrue(svc.retireDevice("dev-1", userId))
        verify { keyRepo.retireWrappedKey(key.id, any()) }
    }

    // ─── touchDevice ─────────────────────────────────────────────────────────

    @Test
    fun `touchDevice returns false when device not found`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-x", userId) } returns null
        assertFalse(svc.touchDevice("dev-x", userId))
    }

    @Test
    fun `touchDevice returns false when device is retired`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-1", userId) } returns makeKey("dev-1", retired = true)
        assertFalse(svc.touchDevice("dev-1", userId))
    }

    @Test
    fun `touchDevice returns true and calls touchWrappedKey when device is active`() {
        val key = makeKey("dev-1")
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-1", userId) } returns key
        every { keyRepo.touchWrappedKey(key.id) } just runs

        assertTrue(svc.touchDevice("dev-1", userId))
        verify { keyRepo.touchWrappedKey(key.id) }
    }

    // ─── putPassphrase ────────────────────────────────────────────────────────

    @Test
    fun `putPassphrase returns Invalid when wrappedMasterKey is not valid base64`() {
        val result = svc.putPassphrase("!!!bad!!!", "fmt", "{}", enc.encodeToString(ByteArray(32)), userId)
        assertTrue(result is KeyService.PutPassphraseResult.Invalid)
        assertTrue((result as KeyService.PutPassphraseResult.Invalid).message.contains("wrappedMasterKey"))
    }

    @Test
    fun `putPassphrase returns Invalid when salt is not valid base64`() {
        val result = svc.putPassphrase(
            enc.encodeToString(ByteArray(64)), "fmt", "{}", "!!!bad!!!", userId,
        )
        assertTrue(result is KeyService.PutPassphraseResult.Invalid)
        assertTrue((result as KeyService.PutPassphraseResult.Invalid).message.contains("salt"))
    }

    @Test
    fun `putPassphrase returns Updated when inputs are valid`() {
        every { keyRepo.upsertRecoveryPassphrase(any(), userId) } just runs
        val updated = RecoveryPassphraseRecord(
            wrappedMasterKey = ByteArray(64),
            wrapFormat = "argon2id-aes256gcm-v1",
            argon2Params = "{}",
            salt = ByteArray(32),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        every { keyRepo.getRecoveryPassphrase(userId) } returns updated

        val result = svc.putPassphrase(
            enc.encodeToString(ByteArray(64)), "argon2id-aes256gcm-v1", "{}",
            enc.encodeToString(ByteArray(32)), userId,
        )
        assertTrue(result is KeyService.PutPassphraseResult.Updated)
        verify { keyRepo.upsertRecoveryPassphrase(any(), userId) }
    }

    // ─── registerOnLink ───────────────────────────────────────────────────────

    @Test
    fun `registerOnLink returns NotFound when link id not found`() {
        val linkId = UUID.randomUUID()
        every { keyRepo.getPendingDeviceLink(linkId) } returns null
        val result = svc.registerOnLink(
            linkId, "ABCD-1234", "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65)),
        )
        assertEquals(KeyService.RegisterOnLinkResult.NotFound, result)
    }

    @Test
    fun `registerOnLink returns Expired when link is past expiry`() {
        val link = makeLink(expiresAt = Instant.now().minusSeconds(1))
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.registerOnLink(
            link.id, link.oneTimeCode, "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65)),
        )
        assertEquals(KeyService.RegisterOnLinkResult.Expired, result)
    }

    @Test
    fun `registerOnLink returns WrongState when state is not initiated`() {
        val link = makeLink(state = "device_registered")
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.registerOnLink(
            link.id, link.oneTimeCode, "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65)),
        )
        assertEquals(KeyService.RegisterOnLinkResult.WrongState, result)
    }

    @Test
    fun `registerOnLink returns Invalid when code does not match`() {
        val link = makeLink(code = "ABCD-1234")
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.registerOnLink(
            link.id, "ZZZZ-9999", "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65)),
        )
        assertTrue(result is KeyService.RegisterOnLinkResult.Invalid)
    }

    @Test
    fun `registerOnLink returns Invalid for invalid deviceKind`() {
        val link = makeLink()
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.registerOnLink(
            link.id, link.oneTimeCode, "dev-1", "Phone", "tablet",
            "p256-spki", enc.encodeToString(ByteArray(65)),
        )
        assertTrue(result is KeyService.RegisterOnLinkResult.Invalid)
    }

    @Test
    fun `registerOnLink returns Invalid for invalid pubkey base64`() {
        val link = makeLink()
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.registerOnLink(
            link.id, link.oneTimeCode, "dev-1", "Phone", "android",
            "p256-spki", "!!!not-base64!!!",
        )
        assertTrue(result is KeyService.RegisterOnLinkResult.Invalid)
        assertTrue((result as KeyService.RegisterOnLinkResult.Invalid).message.contains("pubkey"))
    }

    @Test
    fun `registerOnLink returns Accepted and calls registerNewDevice on success`() {
        val link = makeLink()
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        every { keyRepo.registerNewDevice(link.id, any(), any(), any(), any(), any()) } just runs

        val result = svc.registerOnLink(
            link.id, link.oneTimeCode, "dev-1", "Phone", "android",
            "p256-spki", enc.encodeToString(ByteArray(65) { 7 }),
        )
        assertEquals(KeyService.RegisterOnLinkResult.Accepted, result)
        verify { keyRepo.registerNewDevice(link.id, "dev-1", "Phone", "android", "p256-spki", any()) }
    }

    // ─── wrapLink ────────────────────────────────────────────────────────────

    @Test
    fun `wrapLink returns NotFound when link not found`() {
        val linkId = UUID.randomUUID()
        every { keyRepo.getPendingDeviceLink(linkId) } returns null
        val result = svc.wrapLink(linkId, enc.encodeToString(ByteArray(64)), "fmt", userId)
        assertEquals(KeyService.WrapLinkResult.NotFound, result)
    }

    @Test
    fun `wrapLink returns Expired when link is past expiry`() {
        val link = makeLink(
            state = "device_registered",
            expiresAt = Instant.now().minusSeconds(1),
            deviceId = "new-dev", deviceLabel = "New Phone",
            deviceKind = "android", pubkeyFormat = "p256-spki",
            pubkey = ByteArray(65),
        )
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.wrapLink(link.id, enc.encodeToString(ByteArray(64)), "fmt", userId)
        assertEquals(KeyService.WrapLinkResult.Expired, result)
    }

    @Test
    fun `wrapLink returns WrongState when state is not device_registered`() {
        val link = makeLink(state = "initiated")
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.wrapLink(link.id, enc.encodeToString(ByteArray(64)), "fmt", userId)
        assertEquals(KeyService.WrapLinkResult.WrongState, result)
    }

    @Test
    fun `wrapLink returns Invalid when wrappedMasterKey is not valid base64`() {
        val link = makeLink(state = "device_registered")
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        val result = svc.wrapLink(link.id, "!!!bad-base64!!!", "fmt", userId)
        assertTrue(result is KeyService.WrapLinkResult.Invalid)
        assertTrue((result as KeyService.WrapLinkResult.Invalid).message.contains("wrappedMasterKey"))
    }

    @Test
    fun `wrapLink returns Wrapped and calls completeDeviceLink on success`() {
        val newKey = makeKey("new-dev")
        val link = makeLink(
            state = "device_registered",
            deviceId = "new-dev", deviceLabel = "New Phone",
            deviceKind = "android", pubkeyFormat = "p256-spki",
            pubkey = ByteArray(65) { 3 },
        )
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        every { keyRepo.completeDeviceLink(link.id, any(), any(), any(), any(), any(), any(), any(), userId) } just runs
        every { keyRepo.getWrappedKeyByDeviceIdForUser("new-dev", userId) } returns newKey

        val result = svc.wrapLink(link.id, enc.encodeToString(ByteArray(64) { 9 }), "p256-ecdh-hkdf-aes256gcm-v1", userId)
        assertTrue(result is KeyService.WrapLinkResult.Wrapped)
        assertEquals("new-dev", (result as KeyService.WrapLinkResult.Wrapped).newDevice.deviceId)
        verify {
            keyRepo.completeDeviceLink(
                link.id, any(), "p256-ecdh-hkdf-aes256gcm-v1",
                "new-dev", "New Phone", "android",
                "p256-spki", any(), userId,
            )
        }
    }

    // ─── initiateLink ─────────────────────────────────────────────────────────

    @Test
    fun `initiateLink inserts a pending device link and returns its id and code`() {
        every { keyRepo.insertPendingDeviceLink(any()) } just runs

        val result = svc.initiateLink(userId)
        assertNotNull(result.linkId)
        assertTrue(result.code.length == 9)
        verify { keyRepo.insertPendingDeviceLink(any()) }
    }

    // ─── getLinkStatus ────────────────────────────────────────────────────────

    @Test
    fun `getLinkStatus returns null when link not found`() {
        val id = UUID.randomUUID()
        every { keyRepo.getPendingDeviceLink(id) } returns null
        assertEquals(null, svc.getLinkStatus(id))
    }

    @Test
    fun `getLinkStatus returns link when found`() {
        val link = makeLink()
        every { keyRepo.getPendingDeviceLink(link.id) } returns link
        assertEquals(link, svc.getLinkStatus(link.id))
    }

    // ─── getPassphrase ────────────────────────────────────────────────────────

    @Test
    fun `getPassphrase returns null when no record exists`() {
        every { keyRepo.getRecoveryPassphrase(userId) } returns null
        assertEquals(null, svc.getPassphrase(userId))
    }

    // ─── deletePassphrase ────────────────────────────────────────────────────

    @Test
    fun `deletePassphrase returns false when no record deleted`() {
        every { keyRepo.deleteRecoveryPassphrase(userId) } returns false
        assertFalse(svc.deletePassphrase(userId))
    }

    @Test
    fun `deletePassphrase returns true when record deleted`() {
        every { keyRepo.deleteRecoveryPassphrase(userId) } returns true
        assertTrue(svc.deletePassphrase(userId))
    }

    // ─── listDevices ──────────────────────────────────────────────────────────

    @Test
    fun `listDevices delegates to keyRepo and returns results`() {
        val keys = listOf(makeKey("dev-1"), makeKey("dev-2"))
        every { keyRepo.listWrappedKeys(userId) } returns keys
        assertEquals(keys, svc.listDevices(userId))
    }

    // ─── getDeviceForUser ─────────────────────────────────────────────────────

    @Test
    fun `getDeviceForUser returns null when device not found`() {
        every { keyRepo.getWrappedKeyByDeviceIdForUser("dev-x", userId) } returns null
        assertEquals(null, svc.getDeviceForUser("dev-x", userId))
    }
}
