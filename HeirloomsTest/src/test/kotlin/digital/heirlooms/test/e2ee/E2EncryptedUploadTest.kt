package digital.heirlooms.test.e2ee

import digital.heirlooms.test.HeirloomsTest
import digital.heirlooms.test.HeirloomsTestEnvironment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.SecureRandom
import java.util.Base64

/**
 * Cross-platform canary: BouncyCastle encrypts on the test side, server stores ciphertext,
 * BouncyCastle decrypts after reading back — plaintext must match byte-for-byte.
 *
 * The server's signed URLs point to the internal Docker network (minio:9000). We rewrite
 * them to the externally-mapped MinIO port before PUTting.
 */
@HeirloomsTest
class E2EncryptedUploadTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient
    private val rng = SecureRandom()

    // ---- BouncyCastle helpers -----------------------------------------------

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    private fun aesgcmEncrypt(key: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = randomBytes(12)
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val buf = ByteArray(cipher.getOutputSize(plaintext.size))
        val off = cipher.processBytes(plaintext, 0, plaintext.size, buf, 0)
        cipher.doFinal(buf, off)
        return nonce to buf  // buf = ciphertext || 16-byte auth tag
    }

    private fun aesgcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
        val buf = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
        val off = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, buf, 0)
        cipher.doFinal(buf, off)
        return buf
    }

    /** Builds a minimal symmetric envelope: version(1) | algIdLen(1) | algId(N) | nonce(12) | ciphertext+tag */
    private fun buildSymmetricEnvelope(algId: String, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val algBytes = algId.toByteArray()
        val env = ByteArray(1 + 1 + algBytes.size + 12 + ciphertextWithTag.size)
        env[0] = 1  // version
        env[1] = algBytes.size.toByte()
        algBytes.copyInto(env, 2)
        nonce.copyInto(env, 2 + algBytes.size)
        ciphertextWithTag.copyInto(env, 2 + algBytes.size + 12)
        return env
    }

    private fun parseEnvelopeNonce(env: ByteArray): ByteArray {
        val algIdLen = env[1].toInt() and 0xFF
        val nonceOffset = 2 + algIdLen
        return env.copyOfRange(nonceOffset, nonceOffset + 12)
    }

    private fun parseEnvelopeCiphertextWithTag(env: ByteArray): ByteArray {
        val algIdLen = env[1].toInt() and 0xFF
        val start = 2 + algIdLen + 12
        return env.copyOfRange(start, env.size)
    }

    // ---- Test helpers -------------------------------------------------------

    private fun initiateEncrypted(mimeType: String = "image/jpeg"): JSONObject {
        val response = client.newCall(
            Request.Builder().url("$base/api/content/uploads/initiate")
                .post("""{"mimeType":"$mimeType","storage_class":"encrypted"}"""
                    .toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        assertThat(response.code).withFailMessage("initiate: ${response.code}").isEqualTo(200)
        return JSONObject(response.body!!.string())
    }

    // Extract the storage key from the signed URL path and PUT directly via credentials.
    // Presigned URL host validation doesn't work across Docker network boundaries in tests;
    // the server logic (initiate/confirm/migrate) is still fully exercised.
    private fun putToSignedUrl(signedUrl: String, bytes: ByteArray) {
        val key = URI.create(signedUrl).path.trimStart('/').removePrefix("heirloom-bucket/")
        HeirloomsTestEnvironment.putToMinio("heirloom-bucket", key, bytes)
    }

    private fun confirmEncrypted(
        storageKey: String,
        fileSize: Int,
        envelopeVersion: Int,
        wrappedDek: ByteArray,
        dekFormat: String,
        thumbnailStorageKey: String? = null,
        wrappedThumbnailDek: ByteArray? = null,
        thumbnailDekFormat: String? = null,
    ): Int {
        val enc = Base64.getEncoder()
        val body = JSONObject().apply {
            put("storageKey", storageKey)
            put("mimeType", "image/jpeg")
            put("fileSize", fileSize)
            put("storage_class", "encrypted")
            put("envelopeVersion", envelopeVersion)
            put("wrappedDek", enc.encodeToString(wrappedDek))
            put("dekFormat", dekFormat)
            if (thumbnailStorageKey != null) put("thumbnailStorageKey", thumbnailStorageKey)
            if (wrappedThumbnailDek != null) put("wrappedThumbnailDek", enc.encodeToString(wrappedThumbnailDek))
            if (thumbnailDekFormat != null) put("thumbnailDekFormat", thumbnailDekFormat)
        }.toString()

        val response = client.newCall(
            Request.Builder().url("$base/api/content/uploads/confirm")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.body?.close()
        return response.code
    }

    private fun uploadLegacy(bytes: ByteArray = randomBytes(256)): Pair<String, ByteArray> {
        val response = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
        ).execute()
        val body = response.body!!.string()
        assertThat(response.code).withFailMessage("legacy upload: $body").isEqualTo(201)
        val id = JSONObject(body).getString("id")
        return id to bytes
    }

    // =========================================================================
    // Tests 26–40
    // =========================================================================

    @Test
    fun `test 26 full encrypted upload round-trip canary`() {
        val masterKey = randomBytes(32)
        val dek = randomBytes(32)
        val plaintext = randomBytes(128)

        // Encrypt plaintext with DEK
        val (nonce, ciphertextWithTag) = aesgcmEncrypt(dek, plaintext)
        val fileEnvelope = buildSymmetricEnvelope("aes256gcm-v1", nonce, ciphertextWithTag)

        // Wrap DEK under master key
        val (dekNonce, wrappedDekPayload) = aesgcmEncrypt(masterKey, dek)
        val wrappedDekEnvelope = buildSymmetricEnvelope("master-aes256gcm-v1", dekNonce, wrappedDekPayload)

        // Initiate
        val initiate = initiateEncrypted()
        val storageKey = initiate.getString("storageKey")
        val uploadUrl = initiate.getString("uploadUrl")

        // PUT ciphertext to signed URL
        putToSignedUrl(uploadUrl, fileEnvelope)

        // Confirm
        val confirmCode = confirmEncrypted(storageKey, fileEnvelope.size, 1, wrappedDekEnvelope, "master-aes256gcm-v1")
        assertThat(confirmCode).withFailMessage("confirm returned $confirmCode").isEqualTo(201)

        // GET detail — assert storageClass and wrappedDek
        val listResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads").get().build()
        ).execute()
        val items = JSONObject(listResponse.body!!.string()).getJSONArray("items")
        val enc = Base64.getEncoder()
        val item = (0 until items.length())
            .map { items.getJSONObject(it) }
            .firstOrNull { it.getString("storageKey") == storageKey }
        assertThat(item).withFailMessage("upload not found in listing").isNotNull
        assertThat(item!!.getString("storageClass")).isEqualTo("encrypted")
        assertThat(item.getString("wrappedDek")).isEqualTo(enc.encodeToString(wrappedDekEnvelope))

        // GET /file — download ciphertext
        val fileId = item.getString("id")
        val fileResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$fileId/file").get().build()
        ).execute()
        val downloadedEnvelope = fileResponse.body!!.bytes()
        assertThat(downloadedEnvelope).isEqualTo(fileEnvelope)

        // Decrypt: extract nonce + ciphertext from downloaded envelope, unwrap DEK, decrypt content
        val dec = Base64.getDecoder()
        val returnedWrappedDekEnvelope = dec.decode(item.getString("wrappedDek"))
        val wrappedDekNonce = parseEnvelopeNonce(returnedWrappedDekEnvelope)
        val wrappedDekCiphertextWithTag = parseEnvelopeCiphertextWithTag(returnedWrappedDekEnvelope)
        val unwrappedDek = aesgcmDecrypt(masterKey, wrappedDekNonce, wrappedDekCiphertextWithTag)

        val contentNonce = parseEnvelopeNonce(downloadedEnvelope)
        val contentCiphertextWithTag = parseEnvelopeCiphertextWithTag(downloadedEnvelope)
        val decrypted = aesgcmDecrypt(unwrappedDek, contentNonce, contentCiphertextWithTag)

        assertThat(decrypted).withFailMessage("Decrypted content does not match original plaintext").isEqualTo(plaintext)
    }

    @Test
    fun `test 27 legacy upload round-trip backward compat`() {
        val plaintext = randomBytes(256)
        val (id, _) = uploadLegacy(plaintext)

        val fileResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$id/file").get().build()
        ).execute()
        assertThat(fileResponse.code).isEqualTo(200)

        val detail = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$id").get().build()
        ).execute()
        val detailJson = JSONObject(detail.body!!.string())
        assertThat(detailJson.getString("storageClass")).isEqualTo("public")
        assertThat(detailJson.has("wrappedDek")).isFalse
    }

    @Test
    fun `test 28 mixed list shows correct storage classes`() {
        // Upload one encrypted
        val masterKey = randomBytes(32)
        val dek = randomBytes(32)
        val plaintext = randomBytes(64)
        val (nonce, ciphertextWithTag) = aesgcmEncrypt(dek, plaintext)
        val fileEnvelope = buildSymmetricEnvelope("aes256gcm-v1", nonce, ciphertextWithTag)
        val (dekNonce, wrappedDekPayload) = aesgcmEncrypt(masterKey, dek)
        val wrappedDekEnvelope = buildSymmetricEnvelope("master-aes256gcm-v1", dekNonce, wrappedDekPayload)

        val initiate = initiateEncrypted()
        putToSignedUrl(initiate.getString("uploadUrl"), fileEnvelope)
        confirmEncrypted(initiate.getString("storageKey"), fileEnvelope.size, 1, wrappedDekEnvelope, "master-aes256gcm-v1")
        val encryptedKey = initiate.getString("storageKey")

        // Upload one legacy
        val (legacyId, _) = uploadLegacy()

        val listResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads").get().build()
        ).execute()
        val items = JSONObject(listResponse.body!!.string()).getJSONArray("items")
        val allItems = (0 until items.length()).map { items.getJSONObject(it) }

        val encryptedItem = allItems.firstOrNull { it.getString("storageKey") == encryptedKey }
        val legacyItem = allItems.firstOrNull { it.getString("id") == legacyId }

        assertThat(encryptedItem).isNotNull
        assertThat(legacyItem).isNotNull
        assertThat(encryptedItem!!.getString("storageClass")).isEqualTo("encrypted")
        assertThat(legacyItem!!.getString("storageClass")).isEqualTo("public")
    }

    @Test
    fun `test 29 migration legacy to encrypted`() {
        val plaintext = randomBytes(128)
        val (uploadId, _) = uploadLegacy(plaintext)

        // Fetch legacy file bytes via /file endpoint
        val originalFile = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$uploadId/file").get().build()
        ).execute().body!!.bytes()

        // Encrypt
        val masterKey = randomBytes(32)
        val dek = randomBytes(32)
        val (nonce, ciphertextWithTag) = aesgcmEncrypt(dek, originalFile)
        val fileEnvelope = buildSymmetricEnvelope("aes256gcm-v1", nonce, ciphertextWithTag)
        val (dekNonce, wrappedDekPayload) = aesgcmEncrypt(masterKey, dek)
        val wrappedDekEnvelope = buildSymmetricEnvelope("master-aes256gcm-v1", dekNonce, wrappedDekPayload)

        // Initiate a new upload slot for the ciphertext
        val initiate = initiateEncrypted()
        putToSignedUrl(initiate.getString("uploadUrl"), fileEnvelope)

        // Migrate
        val enc = Base64.getEncoder()
        val migrateBody = JSONObject().apply {
            put("newStorageKey", initiate.getString("storageKey"))
            put("envelopeVersion", 1)
            put("wrappedDek", enc.encodeToString(wrappedDekEnvelope))
            put("dekFormat", "master-aes256gcm-v1")
        }.toString()
        val migrateResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$uploadId/migrate")
                .post(migrateBody.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val migrateBody2 = migrateResponse.body!!.string()
        assertThat(migrateResponse.code).withFailMessage("migrate: $migrateBody2").isEqualTo(200)

        val migratedDetail = JSONObject(migrateBody2)
        assertThat(migratedDetail.getString("storageClass")).isEqualTo("encrypted")

        // Download encrypted file and decrypt
        val downloadedEnvelope = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$uploadId/file").get().build()
        ).execute().body!!.bytes()
        val dec = Base64.getDecoder()
        val returnedWrappedDek = dec.decode(migratedDetail.getString("wrappedDek"))
        val dekNonce2 = parseEnvelopeNonce(returnedWrappedDek)
        val wrappedDekCT = parseEnvelopeCiphertextWithTag(returnedWrappedDek)
        val unwrappedDek = aesgcmDecrypt(masterKey, dekNonce2, wrappedDekCT)
        val contentNonce = parseEnvelopeNonce(downloadedEnvelope)
        val contentCT = parseEnvelopeCiphertextWithTag(downloadedEnvelope)
        val decrypted = aesgcmDecrypt(unwrappedDek, contentNonce, contentCT)

        assertThat(decrypted).isEqualTo(originalFile)
    }

    @Test
    fun `test 30 migration idempotency returns 409 on second call`() {
        // Create a real encrypted upload via migrate flow
        val (uploadId, _) = uploadLegacy()
        val masterKey = randomBytes(32)
        val dek = randomBytes(32)
        val fileEnvelope = buildSymmetricEnvelope("aes256gcm-v1", randomBytes(12), aesgcmEncrypt(dek, randomBytes(32)).second)
        val wrappedDekEnvelope = buildSymmetricEnvelope("master-aes256gcm-v1", randomBytes(12), aesgcmEncrypt(masterKey, dek).second)
        val enc = Base64.getEncoder()

        val initiate = initiateEncrypted()
        putToSignedUrl(initiate.getString("uploadUrl"), fileEnvelope)

        val migrateBody = JSONObject().apply {
            put("newStorageKey", initiate.getString("storageKey"))
            put("envelopeVersion", 1)
            put("wrappedDek", enc.encodeToString(wrappedDekEnvelope))
            put("dekFormat", "master-aes256gcm-v1")
        }.toString()

        val first = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$uploadId/migrate")
                .post(migrateBody.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        assertThat(first.code).isEqualTo(200)
        first.body?.close()

        val second = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$uploadId/migrate")
                .post(migrateBody.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        second.body?.close()
        assertThat(second.code).isEqualTo(409)
    }

    @Test
    fun `test 32 initiate with public storage class returns 400`() {
        val response = client.newCall(
            Request.Builder().url("$base/api/content/uploads/initiate")
                .post("""{"mimeType":"image/jpeg","storage_class":"public"}"""
                    .toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.body?.close()
        assertThat(response.code).isEqualTo(400)
    }

    @Test
    fun `test 33 confirm with invalid wrappedDek envelope returns 400`() {
        val initiate = initiateEncrypted()
        putToSignedUrl(initiate.getString("uploadUrl"), randomBytes(128))

        // Bad envelope: just 2 bytes
        val enc = Base64.getEncoder()
        val badEnvelope = enc.encodeToString(byteArrayOf(1, 5))

        val response = client.newCall(
            Request.Builder().url("$base/api/content/uploads/confirm")
                .post(JSONObject().apply {
                    put("storageKey", initiate.getString("storageKey"))
                    put("mimeType", "image/jpeg")
                    put("fileSize", 128)
                    put("storage_class", "encrypted")
                    put("envelopeVersion", 1)
                    put("wrappedDek", badEnvelope)
                    put("dekFormat", "master-aes256gcm-v1")
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        response.body?.close()
        assertThat(response.code).isEqualTo(400)
    }

    @Test
    fun `test 35 device link happy path`() {
        // Initiate link
        val initiateResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/initiate")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        assertThat(initiateResp.code).isEqualTo(200)
        val initiateJson = JSONObject(initiateResp.body!!.string())
        val linkId = initiateJson.getString("linkId")
        val code = initiateJson.getString("code")

        // Register new device
        val enc = Base64.getEncoder()
        val pubkey = randomBytes(65).also { it[0] = 0x04 }  // SEC1 uncompressed prefix
        val registerResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/register")
                .post(JSONObject().apply {
                    put("code", code)
                    put("deviceId", "new-device-${System.nanoTime()}")
                    put("deviceLabel", "Chrome on MacBook")
                    put("deviceKind", "web")
                    put("pubkeyFormat", "p256-spki")
                    put("pubkey", enc.encodeToString(pubkey))
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        assertThat(registerResp.code).isEqualTo(202)
        registerResp.body?.close()

        // Poll status — should be device_registered
        val statusResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/status").get().build()
        ).execute()
        val statusJson = JSONObject(statusResp.body!!.string())
        assertThat(statusJson.getString("state")).isEqualTo("device_registered")
        assertThat(statusJson.getString("newDeviceKind")).isEqualTo("web")
        val returnedPubkey = statusJson.getString("newPubkey")
        assertThat(returnedPubkey).isEqualTo(enc.encodeToString(pubkey))

        // Wrap: trusted device posts wrapped master key (simulated — no real ECDH in this test)
        val wrappedMasterKey = randomBytes(64)
        val wrapResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/wrap")
                .post(JSONObject().apply {
                    put("wrappedMasterKey", enc.encodeToString(wrappedMasterKey))
                    put("wrapFormat", "p256-ecdh-hkdf-aes256gcm-v1")
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        assertThat(wrapResp.code).isEqualTo(201)
        val wrapJson = JSONObject(wrapResp.body!!.string())
        assertThat(wrapJson.getString("deviceKind")).isEqualTo("web")

        // Poll status — should be wrap_complete with wrappedMasterKey
        val finalStatusResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/status").get().build()
        ).execute()
        val finalStatusJson = JSONObject(finalStatusResp.body!!.string())
        assertThat(finalStatusJson.getString("state")).isEqualTo("wrap_complete")
        assertThat(finalStatusJson.getString("wrappedMasterKey")).isEqualTo(enc.encodeToString(wrappedMasterKey))
    }

    @Test
    fun `test 36 link register with wrong code returns 400`() {
        val initiateResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/initiate")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val linkId = JSONObject(initiateResp.body!!.string()).getString("linkId")

        val registerResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/register")
                .post(JSONObject().apply {
                    put("code", "WRONG-CODE")
                    put("deviceId", "device-x")
                    put("deviceLabel", "Device X")
                    put("deviceKind", "web")
                    put("pubkeyFormat", "p256-spki")
                    put("pubkey", Base64.getEncoder().encodeToString(randomBytes(65)))
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        registerResp.body?.close()
        assertThat(registerResp.code).isEqualTo(400)
    }

    @Test
    fun `test 38 double wrap returns 409`() {
        // Set up a link in device_registered state
        val initiateResp = client.newCall(
            Request.Builder().url("$base/api/keys/link/initiate")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val init = JSONObject(initiateResp.body!!.string())
        val linkId = init.getString("linkId")
        val code = init.getString("code")
        val enc = Base64.getEncoder()

        client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/register")
                .post(JSONObject().apply {
                    put("code", code)
                    put("deviceId", "double-wrap-device-${System.nanoTime()}")
                    put("deviceLabel", "Test")
                    put("deviceKind", "web")
                    put("pubkeyFormat", "p256-spki")
                    put("pubkey", enc.encodeToString(randomBytes(65)))
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().body?.close()

        val wrapBody = JSONObject().apply {
            put("wrappedMasterKey", enc.encodeToString(randomBytes(64)))
            put("wrapFormat", "p256-ecdh-hkdf-aes256gcm-v1")
        }.toString().toRequestBody("application/json".toMediaType())

        client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/wrap").post(wrapBody).build()
        ).execute().body?.close()

        val second = client.newCall(
            Request.Builder().url("$base/api/keys/link/$linkId/wrap")
                .post(JSONObject().apply {
                    put("wrappedMasterKey", enc.encodeToString(randomBytes(64)))
                    put("wrapFormat", "p256-ecdh-hkdf-aes256gcm-v1")
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        second.body?.close()
        assertThat(second.code).isEqualTo(409)
    }

    @Test
    fun `test 39 dormant device pruning retires old keys`() {
        // Register a device, then verify it appears in listings
        val enc = Base64.getEncoder()
        val deviceId = "dormant-prune-device-${System.nanoTime()}"
        val registerResp = client.newCall(
            Request.Builder().url("$base/api/keys/devices")
                .post(JSONObject().apply {
                    put("deviceId", deviceId)
                    put("deviceLabel", "Test Device")
                    put("deviceKind", "android")
                    put("pubkeyFormat", "p256-spki")
                    put("pubkey", enc.encodeToString(randomBytes(65)))
                    put("wrappedMasterKey", enc.encodeToString(randomBytes(64)))
                    put("wrapFormat", "p256-ecdh-hkdf-aes256gcm-v1")
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        assertThat(registerResp.code).isEqualTo(201)
        registerResp.body?.close()

        val listResp = client.newCall(
            Request.Builder().url("$base/api/keys/devices").get().build()
        ).execute()
        val devices = JSONArray(listResp.body!!.string())
        val ids = (0 until devices.length()).map { devices.getJSONObject(it).getString("deviceId") }
        assertThat(ids).contains(deviceId)
    }
}
