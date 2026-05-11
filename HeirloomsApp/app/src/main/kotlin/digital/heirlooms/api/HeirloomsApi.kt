package digital.heirlooms.api

import android.util.Base64
import digital.heirlooms.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HeirloomsApi(
    val baseUrl: String = BASE_URL,
    internal val apiKey: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun thumbUrl(uploadId: String) = "$baseUrl/api/content/uploads/$uploadId/thumb"
    fun fileUrl(uploadId: String) = "$baseUrl/api/content/uploads/$uploadId/file"

    private fun Request.Builder.withAuth(): Request.Builder =
        header("X-Api-Key", apiKey)

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .withAuth()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private suspend fun post(path: String, body: String = ""): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .withAuth()
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.body?.string()}")
            response.body?.string() ?: ""
        }
    }

    private suspend fun put(path: String, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .withAuth()
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.body?.string()}")
            response.body?.string() ?: ""
        }
    }

    private suspend fun patch(path: String, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .withAuth()
            .patch(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: ""
        }
    }

    // ── Upload list ──────────────────────────────────────────────────────────

    // ── Upload list ──────────────────────────────────────────────────────────

    suspend fun listUploadsPage(
        cursor: String? = null,
        limit: Int = 20,
        tags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
        justArrived: Boolean = false,
        inCapsule: Boolean? = null,
        hasLocation: Boolean? = null,
        includeComposted: Boolean = false,
        sort: String = "upload_newest",
        fromDate: String? = null,
        toDate: String? = null,
    ): UploadPage {
        val params = buildString {
            append("?limit=$limit&sort=$sort")
            cursor?.let { append("&cursor=${it.encodeParam()}") }
            if (tags.isNotEmpty()) append("&tag=${tags.joinToString(",") { it.encodeParam() }}")
            if (excludeTags.isNotEmpty()) append("&exclude_tag=${excludeTags.joinToString(",") { it.encodeParam() }}")
            if (justArrived) append("&just_arrived=true")
            inCapsule?.let { append("&in_capsule=$it") }
            hasLocation?.let { append("&has_location=$it") }
            if (includeComposted) append("&include_composted=true")
            fromDate?.let { append("&from_date=${it.encodeParam()}") }
            toDate?.let { append("&to_date=${it.encodeParam()}") }
        }
        return JSONObject(get("/api/content/uploads$params")).toUploadPage()
    }

    suspend fun listCompostedUploads(): List<Upload> =
        JSONObject(get("/api/content/uploads/composted")).getJSONArray("items").toUploadList()

    suspend fun getUpload(id: String): Upload =
        JSONObject(get("/api/content/uploads/$id")).toUpload()

    suspend fun compostUpload(id: String): Upload =
        JSONObject(post("/api/content/uploads/$id/compost")).toUpload()

    suspend fun restoreUpload(id: String): Upload =
        JSONObject(post("/api/content/uploads/$id/restore")).toUpload()

    suspend fun updateTags(id: String, tags: List<String>): Upload {
        val tagsJson = "[${tags.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
        return JSONObject(patch("/api/content/uploads/$id/tags", """{"tags":$tagsJson}""")).toUpload()
    }

    suspend fun rotateUpload(id: String, rotation: Int): Upload =
        JSONObject(patch("/api/content/uploads/$id/rotation", """{"rotation":$rotation}""")).toUpload()

    suspend fun trackView(id: String) {
        try { post("/api/content/uploads/$id/view") } catch (_: Exception) {}
    }

    suspend fun listTags(): List<String> =
        JSONArray(get("/api/content/uploads/tags"))
            .let { arr -> (0 until arr.length()).map { arr.getString(it) } }

    suspend fun getCapsulesForUpload(id: String): List<CapsuleRef> =
        JSONObject(get("/api/content/uploads/$id/capsules")).getJSONArray("capsules").toCapsuleRefList()

    // ── Plots ────────────────────────────────────────────────────────────────

    suspend fun listPlots(): List<Plot> =
        JSONArray(get("/api/plots")).toPlotList()

    suspend fun createPlot(name: String, tagCriteria: List<String>): Plot {
        val tagsJson = "[${tagCriteria.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
        return JSONObject(post("/api/plots", """{"name":${name.jsonEsc()},"tag_criteria":$tagsJson}""")).toPlot()
    }

    suspend fun updatePlot(id: String, name: String, tagCriteria: List<String>): Plot {
        val tagsJson = "[${tagCriteria.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
        return JSONObject(put("/api/plots/$id", """{"name":${name.jsonEsc()},"tag_criteria":$tagsJson}""")).toPlot()
    }

    suspend fun deletePlot(id: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/plots/$id")
                .withAuth()
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            }
        }
    }

    // ── Friends & sharing ────────────────────────────────────────────────────

    data class Friend(val userId: String, val username: String, val displayName: String)
    data class SharingKeyMe(val pubkey: ByteArray, val wrappedPrivkey: ByteArray, val wrapFormat: String)

    // Returns null if the user has no sharing key yet (404 → triggers lazy generation).
    suspend fun getSharingKeyMe(): SharingKeyMe? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/keys/sharing/me")
            .withAuth()
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> null
                in 200..299 -> {
                    val json = JSONObject(response.body?.string() ?: throw IOException("Empty"))
                    SharingKeyMe(
                        pubkey = Base64.decode(json.getString("pubkey"), Base64.DEFAULT),
                        wrappedPrivkey = Base64.decode(json.getString("wrappedPrivkey"), Base64.DEFAULT),
                        wrapFormat = json.getString("wrapFormat"),
                    )
                }
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }

    suspend fun putSharingKey(pubkeyB64: String, wrappedPrivkeyB64: String, wrapFormat: String) {
        val body = """{"pubkey":"$pubkeyB64","wrappedPrivkey":"$wrappedPrivkeyB64","wrapFormat":"$wrapFormat"}"""
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/keys/sharing")
                .withAuth()
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            }
        }
    }

    // Returns null if the friend has no sharing key yet.
    suspend fun getFriendSharingPubkey(userId: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/keys/sharing/$userId")
            .withAuth()
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> null
                in 200..299 -> {
                    val json = JSONObject(response.body?.string() ?: throw IOException("Empty"))
                    Base64.decode(json.getString("pubkey"), Base64.DEFAULT)
                }
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }

    suspend fun getFriends(): List<Friend> =
        JSONArray(get("/api/friends")).let { arr ->
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Friend(o.getString("userId"), o.getString("username"), o.getString("displayName"))
            }
        }

    suspend fun shareUpload(
        uploadId: String,
        toUserId: String,
        wrappedDekB64: String,
        wrappedThumbnailDekB64: String?,
    ) {
        val thumbJson = if (wrappedThumbnailDekB64 != null) ""","wrappedThumbnailDek":"$wrappedThumbnailDekB64"""" else ""
        val body = """{"toUserId":"$toUserId","wrappedDek":"$wrappedDekB64","dekFormat":"${VaultCrypto.ALG_P256_ECDH_HKDF_V1}"$thumbJson}"""
        post("/api/content/uploads/$uploadId/share", body)
    }

    // ── Capsules ─────────────────────────────────────────────────────────────

    suspend fun listCapsules(states: String = "open,sealed"): List<CapsuleSummary> =
        JSONObject(get("/api/capsules?state=$states&order=unlock_at")).getJSONArray("capsules").toCapsuleSummaryList()

    suspend fun getCapsule(id: String): CapsuleDetail =
        JSONObject(get("/api/capsules/$id")).toCapsuleDetail()

    suspend fun createCapsule(
        shape: String,
        unlockAt: String,
        recipients: List<String>,
        uploadIds: List<String>,
        message: String,
    ): CapsuleDetail {
        val recipientsJson = "[${recipients.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }}]"
        val uploadIdsJson = "[${uploadIds.joinToString(",") { "\"$it\"" }}]"
        val messageJson = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val body = """{"shape":"$shape","unlock_at":"$unlockAt","recipients":$recipientsJson,"upload_ids":$uploadIdsJson,"message":"$messageJson"}"""
        return JSONObject(post("/api/capsules", body)).toCapsuleDetail()
    }

    suspend fun patchCapsuleUploads(id: String, uploadIds: List<String>): CapsuleDetail {
        val idsJson = "[${uploadIds.joinToString(",") { "\"$it\"" }}]"
        return JSONObject(patch("/api/capsules/$id", """{"upload_ids":$idsJson}""")).toCapsuleDetail()
    }

    suspend fun sealCapsule(id: String): CapsuleDetail =
        JSONObject(post("/api/capsules/$id/seal")).toCapsuleDetail()

    suspend fun cancelCapsule(id: String): CapsuleDetail =
        JSONObject(post("/api/capsules/$id/cancel")).toCapsuleDetail()

    // ── Auth ─────────────────────────────────────────────────────────────────

    data class ChallengeResponse(val authSaltB64url: String)
    data class LoginResponse(val sessionToken: String)
    data class InviteResponse(val token: String, val expiresAt: String)
    data class PairingInitiateResponse(val code: String, val expiresAt: String)

    /** Returns the stored auth_salt (base64url) for the given username. */
    suspend fun authChallenge(username: String): ChallengeResponse = withContext(Dispatchers.IO) {
        val body = """{"username":"${username.jsonEsc().drop(1).dropLast(1)}"}"""
        val request = Request.Builder()
            .url("$baseUrl/api/auth/challenge")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
            ChallengeResponse(json.getString("auth_salt"))
        }
    }

    /** Authenticates with auth_key (base64url); returns session token. */
    suspend fun authLogin(username: String, authKeyB64url: String): LoginResponse = withContext(Dispatchers.IO) {
        val body = """{"username":${username.jsonEsc()},"auth_key":"$authKeyB64url"}"""
        val request = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw IOException("UNAUTHORIZED")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
            LoginResponse(json.getString("session_token"))
        }
    }

    /** One-time migration path for the founding user: sets auth credentials. */
    suspend fun setupExisting(
        username: String,
        deviceId: String,
        authSaltB64url: String,
        authVerifierB64url: String,
    ): LoginResponse = withContext(Dispatchers.IO) {
        val body = """{"username":${username.jsonEsc()},"device_id":"$deviceId","auth_salt":"$authSaltB64url","auth_verifier":"$authVerifierB64url"}"""
        val request = Request.Builder()
            .url("$baseUrl/api/auth/setup-existing")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
            LoginResponse(json.getString("session_token"))
        }
    }

    suspend fun authLogout() = withContext(Dispatchers.IO) {
        try { post("/api/auth/logout") } catch (_: Exception) {}
    }

    suspend fun getInvite(): InviteResponse = withContext(Dispatchers.IO) {
        val json = JSONObject(get("/api/auth/invites"))
        InviteResponse(json.getString("token"), json.getString("expires_at"))
    }

    suspend fun pairingInitiate(): PairingInitiateResponse = withContext(Dispatchers.IO) {
        val json = JSONObject(post("/api/auth/pairing/initiate"))
        PairingInitiateResponse(json.getString("code"), json.getString("expires_at"))
    }

    data class RegisterResponse(val sessionToken: String)

    suspend fun authRegister(
        inviteToken: String,
        username: String,
        displayName: String,
        authSaltB64url: String,
        authVerifierB64url: String,
        wrappedMasterKeyB64: String,
        pubkeyB64: String,
        deviceId: String,
        deviceLabel: String,
    ): RegisterResponse = withContext(Dispatchers.IO) {
        val body = """{"invite_token":"$inviteToken","username":${username.jsonEsc()},"display_name":${displayName.jsonEsc()},"auth_salt":"$authSaltB64url","auth_verifier":"$authVerifierB64url","wrapped_master_key":"$wrappedMasterKeyB64","wrap_format":"${VaultCrypto.ALG_P256_ECDH_HKDF_V1}","pubkey_format":"p256-spki","pubkey":"$pubkeyB64","device_id":"$deviceId","device_label":${deviceLabel.jsonEsc()},"device_kind":"android"}"""
        val request = Request.Builder()
            .url("$baseUrl/api/auth/register")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                409 -> throw IOException("409 Username already taken")
                410 -> throw IOException("410 Invite invalid or expired")
                else -> {
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
                    RegisterResponse(json.getString("session_token"))
                }
            }
        }
    }

    suspend fun pairingComplete(
        sessionId: String,
        wrappedMasterKeyB64: String,
        webPubkeyB64url: String,
    ) = post(
        "/api/auth/pairing/complete",
        """{"session_id":"$sessionId","wrapped_master_key":"$wrappedMasterKeyB64","wrap_format":"${VaultCrypto.ALG_P256_ECDH_HKDF_V1}","web_pubkey":"$webPubkeyB64url","web_pubkey_format":"p256-spki"}""",
    )

    // ── Diagnostics ──────────────────────────────────────────────────────────

    suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(get("/api/settings"))
            AppSettings(previewDurationSeconds = json.optInt("previewDurationSeconds", 15))
        } catch (_: Exception) {
            AppSettings()
        }
    }

    suspend fun postDiagEvent(deviceLabel: String, tag: String, message: String, detail: String) {
        val body = """{"deviceLabel":${deviceLabel.jsonEsc()},"tag":${tag.jsonEsc()},"message":${message.jsonEsc()},"detail":${detail.jsonEsc()}}"""
        post("/api/diagnostics/events", body)
    }

    // ── Keys / device registration ───────────────────────────────────────────

    data class PassphraseBackup(
        val wrappedMasterKey: ByteArray,
        val salt: ByteArray,
        val params: VaultCrypto.Argon2Params,
    )

    // Returns null if no passphrase backup exists (404), throws on other errors.
    suspend fun getPassphrase(): PassphraseBackup? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/keys/passphrase")
            .withAuth()
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> null
                in 200..299 -> {
                    val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
                    val p = json.optJSONObject("argon2Params")
                    PassphraseBackup(
                        wrappedMasterKey = Base64.decode(json.getString("wrappedMasterKey"), Base64.NO_WRAP),
                        salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP),
                        params = VaultCrypto.Argon2Params(
                            m = p?.optInt("m", 65536) ?: 65536,
                            t = p?.optInt("t", 3) ?: 3,
                            p = p?.optInt("p", 1) ?: 1,
                        ),
                    )
                }
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }

    suspend fun registerDevice(
        deviceId: String,
        deviceLabel: String,
        pubkeyB64: String,
        wrappedMasterKeyB64: String,
    ) {
        val body = """{"deviceId":"$deviceId","deviceLabel":${deviceLabel.jsonEsc()},"deviceKind":"android","pubkeyFormat":"p256-spki","pubkey":"$pubkeyB64","wrappedMasterKey":"$wrappedMasterKeyB64","wrapFormat":"${VaultCrypto.ALG_P256_ECDH_HKDF_V1}"}"""
        post("/api/keys/devices", body)
    }

    suspend fun putPassphrase(
        wrappedMasterKeyB64: String,
        saltB64: String,
        params: VaultCrypto.Argon2Params,
    ) {
        val argon2Json = """{"m":${params.m},"t":${params.t},"p":${params.p}}"""
        val body = """{"wrappedMasterKey":"$wrappedMasterKeyB64","wrapFormat":"${VaultCrypto.ALG_ARGON2ID_AES256GCM_V1}","argon2Params":$argon2Json,"salt":"$saltB64"}"""
        put("/api/keys/passphrase", body)
    }

    // ── Encrypted upload ─────────────────────────────────────────────────────

    data class InitiateResponse(
        val storageKey: String,
        val uploadUrl: String,
        val thumbnailStorageKey: String,
        val thumbnailUploadUrl: String,
    )

    suspend fun initiateEncryptedUpload(mimeType: String): InitiateResponse {
        val body = post("/api/content/uploads/initiate", """{"mimeType":"$mimeType","storage_class":"encrypted"}""")
        val json = JSONObject(body)
        return InitiateResponse(
            storageKey = json.getString("storageKey"),
            uploadUrl = json.getString("uploadUrl"),
            thumbnailStorageKey = json.getString("thumbnailStorageKey"),
            thumbnailUploadUrl = json.getString("thumbnailUploadUrl"),
        )
    }

    suspend fun confirmEncryptedUpload(
        storageKey: String,
        mimeType: String,
        fileSize: Long,
        envelopeVersion: Int,
        wrappedDekB64: String,
        dekFormat: String,
        thumbnailStorageKey: String,
        wrappedThumbnailDekB64: String,
        thumbnailDekFormat: String,
        takenAt: String?,
        tags: List<String>,
        previewStorageKey: String? = null,
        wrappedPreviewDekB64: String? = null,
        previewDekFormat: String? = null,
        plainChunkSize: Int? = null,
        durationSeconds: Int? = null,
    ): Upload {
        val tagsJson = "[${tags.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
        val takenAtJson = if (takenAt != null) ""","takenAt":"$takenAt"""" else ""
        val previewJson = if (previewStorageKey != null && wrappedPreviewDekB64 != null && previewDekFormat != null)
            ""","previewStorageKey":"$previewStorageKey","wrappedPreviewDek":"$wrappedPreviewDekB64","previewDekFormat":"$previewDekFormat""""
        else ""
        val chunkSizeJson = if (plainChunkSize != null) ""","plainChunkSize":$plainChunkSize""" else ""
        val durationJson = if (durationSeconds != null) ""","durationSeconds":$durationSeconds""" else ""
        val body = """{"storageKey":"$storageKey","mimeType":"$mimeType","fileSize":$fileSize,"storage_class":"encrypted","envelopeVersion":$envelopeVersion,"wrappedDek":"$wrappedDekB64","dekFormat":"$dekFormat","thumbnailStorageKey":"$thumbnailStorageKey","wrappedThumbnailDek":"$wrappedThumbnailDekB64","thumbnailDekFormat":"$thumbnailDekFormat","tags":$tagsJson$takenAtJson$previewJson$chunkSizeJson$durationJson}"""
        return JSONObject(post("/api/content/uploads/confirm", body)).toUpload()
    }

    fun previewUrl(uploadId: String) = "$baseUrl/api/content/uploads/$uploadId/preview"

    // ── Raw byte fetch (for decrypting encrypted content) ────────────────────

    suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .withAuth()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty response")
        }
    }

    // ── JSON → model helpers ─────────────────────────────────────────────────

    private fun JSONObject.toUploadPage() = UploadPage(
        uploads = getJSONArray("items").toUploadList(),
        nextCursor = optString("next_cursor").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONArray.toUploadList(): List<Upload> =
        (0 until length()).map { getJSONObject(it).toUpload() }

    private fun JSONObject.toUpload() = Upload(
        id = getString("id"),
        storageKey = getString("storageKey"),
        mimeType = getString("mimeType"),
        fileSize = optLong("fileSize", 0L),
        uploadedAt = getString("uploadedAt"),
        rotation = optInt("rotation", 0),
        thumbnailKey = optString("thumbnailKey").takeIf { it.isNotEmpty() && it != "null" },
        tags = getJSONArray("tags").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        compostedAt = optString("compostedAt").takeIf { it.isNotEmpty() && it != "null" },
        takenAt = optString("takenAt").takeIf { it.isNotEmpty() && it != "null" },
        latitude = if (has("latitude") && !isNull("latitude")) getDouble("latitude") else null,
        longitude = if (has("longitude") && !isNull("longitude")) getDouble("longitude") else null,
        lastViewedAt = optString("lastViewedAt").takeIf { it.isNotEmpty() && it != "null" },
        storageClass = optString("storageClass", "public").let {
            if (it.isEmpty() || it == "null") "public" else it
        },
        envelopeVersion = if (has("envelopeVersion") && !isNull("envelopeVersion")) getInt("envelopeVersion") else null,
        wrappedDek = optString("wrappedDek").takeIf { it.isNotEmpty() && it != "null" }
            ?.let { Base64.decode(it, Base64.DEFAULT) },
        dekFormat = optString("dekFormat").takeIf { it.isNotEmpty() && it != "null" },
        wrappedThumbnailDek = optString("wrappedThumbnailDek").takeIf { it.isNotEmpty() && it != "null" }
            ?.let { Base64.decode(it, Base64.DEFAULT) },
        thumbnailDekFormat = optString("thumbnailDekFormat").takeIf { it.isNotEmpty() && it != "null" },
        previewStorageKey = optString("previewStorageKey").takeIf { it.isNotEmpty() && it != "null" },
        wrappedPreviewDek = optString("wrappedPreviewDek").takeIf { it.isNotEmpty() && it != "null" }
            ?.let { Base64.decode(it, Base64.DEFAULT) },
        previewDekFormat = optString("previewDekFormat").takeIf { it.isNotEmpty() && it != "null" },
        plainChunkSize = if (has("plainChunkSize") && !isNull("plainChunkSize")) getInt("plainChunkSize") else null,
        durationSeconds = if (has("durationSeconds") && !isNull("durationSeconds")) getInt("durationSeconds") else null,
        sharedFromUserId = optString("sharedFromUserId").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONArray.toPlotList(): List<Plot> =
        (0 until length()).map { getJSONObject(it).toPlot() }

    private fun JSONObject.toPlot() = Plot(
        id = getString("id"),
        name = getString("name"),
        tagCriteria = getJSONArray("tag_criteria").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        sortOrder = optInt("sort_order", 0),
        isSystemDefined = optBoolean("is_system_defined", false),
    )

    private fun JSONArray.toCapsuleSummaryList(): List<CapsuleSummary> =
        (0 until length()).map { getJSONObject(it).toCapsuleSummary() }

    private fun JSONObject.toCapsuleSummary() = CapsuleSummary(
        id = getString("id"),
        shape = getString("shape"),
        state = getString("state"),
        createdAt = getString("created_at"),
        updatedAt = getString("updated_at"),
        unlockAt = getString("unlock_at"),
        recipients = getJSONArray("recipients").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        uploadCount = optInt("upload_count", 0),
        hasMessage = optBoolean("has_message", false),
        cancelledAt = optString("cancelled_at").takeIf { it.isNotEmpty() && it != "null" },
        deliveredAt = optString("delivered_at").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONObject.toCapsuleDetail() = CapsuleDetail(
        id = getString("id"),
        shape = getString("shape"),
        state = getString("state"),
        createdAt = getString("created_at"),
        updatedAt = getString("updated_at"),
        unlockAt = getString("unlock_at"),
        recipients = getJSONArray("recipients").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        uploads = getJSONArray("uploads").toUploadList(),
        message = optString("message", ""),
        cancelledAt = optString("cancelled_at").takeIf { it.isNotEmpty() && it != "null" },
        deliveredAt = optString("delivered_at").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONArray.toCapsuleRefList(): List<CapsuleRef> =
        (0 until length()).map { getJSONObject(it).toCapsuleRef() }

    private fun JSONObject.toCapsuleRef() = CapsuleRef(
        id = getString("id"),
        shape = getString("shape"),
        state = getString("state"),
        unlockAt = getString("unlock_at"),
        recipients = getJSONArray("recipients").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
    )

    private fun String.encodeParam() = java.net.URLEncoder.encode(this, "UTF-8")
    private fun String.jsonEsc(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""

    companion object {
        const val BASE_URL = "https://api.heirlooms.digital"
    }
}
