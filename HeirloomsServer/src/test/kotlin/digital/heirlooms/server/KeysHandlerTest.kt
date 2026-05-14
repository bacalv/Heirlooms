package digital.heirlooms.server

import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.domain.keys.RecoveryPassphraseRecord
import digital.heirlooms.server.domain.keys.WrappedKeyRecord
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val enc = Base64.getEncoder()

class KeysHandlerTest {

    private val mockDatabase = mockk<Database>()
    private val app = buildApp(mockk<FileStore>(relaxed = true), mockDatabase)

    private fun makeWrappedKeyRecord(
        deviceId: String = "device-1",
        retired: Boolean = false,
    ) = WrappedKeyRecord(
        id = UUID.randomUUID(),
        deviceId = deviceId,
        deviceLabel = "Test Device",
        deviceKind = "android",
        pubkeyFormat = "p256-spki",
        pubkey = ByteArray(65) { 1 },
        wrappedMasterKey = ByteArray(64) { 2 },
        wrapFormat = "p256-ecdh-hkdf-aes256gcm-v1",
        createdAt = Instant.EPOCH,
        lastUsedAt = Instant.EPOCH,
        retiredAt = if (retired) Instant.EPOCH else null,
    )

    private fun makePassphraseRecord() = RecoveryPassphraseRecord(
        wrappedMasterKey = ByteArray(64) { 3 },
        wrapFormat = "argon2id-aes256gcm-v1",
        argon2Params = """{"m":65536,"t":3,"p":1}""",
        salt = ByteArray(32) { 4 },
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun validDeviceBody(deviceId: String = "device-1") = """
        {
          "deviceId": "$deviceId",
          "deviceLabel": "Pixel 8",
          "deviceKind": "android",
          "pubkeyFormat": "p256-spki",
          "pubkey": "${enc.encodeToString(ByteArray(65) { 1 })}",
          "wrappedMasterKey": "${enc.encodeToString(ByteArray(64) { 2 })}",
          "wrapFormat": "p256-ecdh-hkdf-aes256gcm-v1"
        }
    """.trimIndent()

    // ---- Register device ----

    @Test
    fun `register device returns 201 with record`() {
        every { mockDatabase.getWrappedKeyByDeviceIdForUser(any(), any()) } returns null
        every { mockDatabase.insertWrappedKey(any()) } just runs

        val response = app(
            Request(POST, "/api/keys/devices")
                .header("Content-Type", "application/json")
                .body(validDeviceBody())
        )

        assertEquals(CREATED, response.status)
        assertTrue(response.bodyString().contains("device-1"))
        verify { mockDatabase.insertWrappedKey(any()) }
    }

    @Test
    fun `register duplicate deviceId returns 409`() {
        every { mockDatabase.getWrappedKeyByDeviceIdForUser("device-1", any()) } returns makeWrappedKeyRecord("device-1")

        val response = app(
            Request(POST, "/api/keys/devices")
                .header("Content-Type", "application/json")
                .body(validDeviceBody("device-1"))
        )

        assertEquals(CONFLICT, response.status)
    }

    @Test
    fun `register with invalid deviceKind returns 400`() {
        every { mockDatabase.getWrappedKeyByDeviceIdForUser(any(), any()) } returns null

        val response = app(
            Request(POST, "/api/keys/devices")
                .header("Content-Type", "application/json")
                .body(validDeviceBody().replace("\"android\"", "\"desktop\""))
        )

        assertEquals(BAD_REQUEST, response.status)
    }

    // ---- List devices ----

    @Test
    fun `list devices returns active rows only`() {
        val active = makeWrappedKeyRecord("device-active")
        every { mockDatabase.listWrappedKeys() } returns listOf(active)

        val response = app(Request(GET, "/api/keys/devices"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("device-active"))
    }

    // ---- Retire device ----

    @Test
    fun `retire device returns 204`() {
        val record = makeWrappedKeyRecord("device-1")
        every { mockDatabase.getWrappedKeyByDeviceIdForUser("device-1", any()) } returns record
        every { mockDatabase.retireWrappedKey(record.id, any()) } just runs

        val response = app(Request(DELETE, "/api/keys/devices/device-1"))

        assertEquals(NO_CONTENT, response.status)
        verify { mockDatabase.retireWrappedKey(record.id, any()) }
    }

    @Test
    fun `retire already-retired device returns 409`() {
        every { mockDatabase.getWrappedKeyByDeviceIdForUser("device-1", any()) } returns makeWrappedKeyRecord("device-1", retired = true)

        val response = app(Request(DELETE, "/api/keys/devices/device-1"))

        assertEquals(CONFLICT, response.status)
    }

    // ---- Touch device ----

    @Test
    fun `touch device returns 204 and updates last_used_at`() {
        val record = makeWrappedKeyRecord("device-1")
        every { mockDatabase.getWrappedKeyByDeviceIdForUser("device-1", any()) } returns record
        every { mockDatabase.touchWrappedKey(record.id) } just runs

        val response = app(Request(PATCH, "/api/keys/devices/device-1/used"))

        assertEquals(NO_CONTENT, response.status)
        verify { mockDatabase.touchWrappedKey(record.id) }
    }

    @Test
    fun `touch unknown device returns 404`() {
        every { mockDatabase.getWrappedKeyByDeviceIdForUser("unknown", any()) } returns null

        val response = app(Request(PATCH, "/api/keys/devices/unknown/used"))

        assertEquals(NOT_FOUND, response.status)
    }

    // ---- Passphrase ----

    @Test
    fun `get passphrase returns 200 with record`() {
        every { mockDatabase.getRecoveryPassphrase() } returns makePassphraseRecord()

        val response = app(Request(GET, "/api/keys/passphrase"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("argon2id-aes256gcm-v1"))
    }

    @Test
    fun `get passphrase when none returns 404`() {
        every { mockDatabase.getRecoveryPassphrase() } returns null

        val response = app(Request(GET, "/api/keys/passphrase"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `put passphrase creates record and returns 200`() {
        val capturedRecord = slot<RecoveryPassphraseRecord>()
        every { mockDatabase.upsertRecoveryPassphrase(capture(capturedRecord)) } just runs
        every { mockDatabase.getRecoveryPassphrase() } returns makePassphraseRecord()

        val response = app(
            Request(PUT, "/api/keys/passphrase")
                .header("Content-Type", "application/json")
                .body("""{"wrappedMasterKey":"${enc.encodeToString(ByteArray(64){3})}","wrapFormat":"argon2id-aes256gcm-v1","argon2Params":{"m":65536,"t":3,"p":1},"salt":"${enc.encodeToString(ByteArray(32){4})}"}""")
        )

        assertEquals(OK, response.status)
        verify { mockDatabase.upsertRecoveryPassphrase(any()) }
    }

    @Test
    fun `put passphrase replace updates record`() {
        val capturedSlot = slot<RecoveryPassphraseRecord>()
        every { mockDatabase.upsertRecoveryPassphrase(capture(capturedSlot)) } just runs
        val updatedRecord = makePassphraseRecord().copy(wrapFormat = "argon2id-aes256gcm-v1")
        every { mockDatabase.getRecoveryPassphrase() } returns updatedRecord

        val response = app(
            Request(PUT, "/api/keys/passphrase")
                .header("Content-Type", "application/json")
                .body("""{"wrappedMasterKey":"${enc.encodeToString(ByteArray(64){3})}","wrapFormat":"argon2id-aes256gcm-v1","argon2Params":{"m":65536,"t":3,"p":1},"salt":"${enc.encodeToString(ByteArray(32){4})}"}""")
        )

        assertEquals(OK, response.status)
    }

    @Test
    fun `delete passphrase returns 204`() {
        every { mockDatabase.deleteRecoveryPassphrase() } returns true

        val response = app(Request(DELETE, "/api/keys/passphrase"))

        assertEquals(NO_CONTENT, response.status)
    }

    @Test
    fun `delete passphrase when none returns 404`() {
        every { mockDatabase.deleteRecoveryPassphrase() } returns false

        val response = app(Request(DELETE, "/api/keys/passphrase"))

        assertEquals(NOT_FOUND, response.status)
    }
}
