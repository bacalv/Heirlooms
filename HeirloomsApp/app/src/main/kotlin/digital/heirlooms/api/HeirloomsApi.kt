package digital.heirlooms.api

import android.util.Base64
import digital.heirlooms.app.BuildConfig
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
        plotId: String? = null,
    ): UploadPage {
        val params = buildString {
            append("?limit=$limit&sort=$sort")
            cursor?.let { append("&cursor=${it.encodeParam()}") }
            if (plotId != null) {
                append("&plot_id=${plotId.encodeParam()}")
            } else {
                if (tags.isNotEmpty()) append("&tag=${tags.joinToString(",") { it.encodeParam() }}")
                if (justArrived) append("&just_arrived=true")
            }
            if (excludeTags.isNotEmpty()) append("&exclude_tag=${excludeTags.joinToString(",") { it.encodeParam() }}")
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

    /**
     * Pre-wrapped plot DEK for client-side DEK re-wrap on shared-plot trellis routing.
     * When the client holds the plot key it can include these to bypass staging (BUG-020).
     */
    data class PrewrappedPlotDek(
        val plotId: String,
        val wrappedItemDek: String,       // base64
        val itemDekFormat: String,
        val wrappedThumbnailDek: String?, // base64, nullable
        val thumbnailDekFormat: String?,
    )

    suspend fun updateTags(
        id: String,
        tags: List<String>,
        prewrappedPlotDeks: List<PrewrappedPlotDek> = emptyList(),
    ): Upload {
        val tagsJson = "[${tags.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
        val body = buildString {
            append("""{"tags":$tagsJson""")
            if (prewrappedPlotDeks.isNotEmpty()) {
                append(""","prewrappedPlotDeks":[""")
                prewrappedPlotDeks.forEachIndexed { i, dek ->
                    if (i > 0) append(",")
                    append("""{"plotId":${dek.plotId.jsonEsc()}""")
                    append(""","wrappedItemDek":${dek.wrappedItemDek.jsonEsc()}""")
                    append(""","itemDekFormat":${dek.itemDekFormat.jsonEsc()}""")
                    if (dek.wrappedThumbnailDek != null) append(""","wrappedThumbnailDek":${dek.wrappedThumbnailDek.jsonEsc()}""")
                    if (dek.thumbnailDekFormat != null) append(""","thumbnailDekFormat":${dek.thumbnailDekFormat.jsonEsc()}""")
                    append("}")
                }
                append("]")
            }
            append("}")
        }
        return JSONObject(patch("/api/content/uploads/$id/tags", body)).toUpload()
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

    suspend fun createPlot(
        name: String,
        criteria: String? = null,
        showInGarden: Boolean = true,
        visibility: String = "private",
        wrappedPlotKey: String? = null,
        plotKeyFormat: String? = null,
    ): Plot {
        val body = buildString {
            append("""{"name":${name.jsonEsc()}""")
            if (criteria != null) append(""","criteria":$criteria""")
            else append(""","criteria":null""")
            append(""","show_in_garden":$showInGarden""")
            append(""","visibility":${visibility.jsonEsc()}""")
            if (wrappedPlotKey != null) append(""","wrappedPlotKey":${wrappedPlotKey.jsonEsc()}""")
            if (plotKeyFormat != null) append(""","plotKeyFormat":${plotKeyFormat.jsonEsc()}""")
            append("}")
        }
        return JSONObject(post("/api/plots", body)).toPlot()
    }

    suspend fun updatePlot(
        id: String,
        name: String? = null,
        criteria: String? = null,
        showInGarden: Boolean? = null,
    ): Plot {
        val parts = mutableListOf<String>()
        name?.let { parts.add(""""name":${it.jsonEsc()}""") }
        criteria?.let { parts.add(""""criteria":$it""") }
        showInGarden?.let { parts.add(""""show_in_garden":$it""") }
        return JSONObject(put("/api/plots/$id", "{${parts.joinToString(",")}}")).toPlot()
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

    // Throws IOException("must_transfer") when the server returns 403 (owner has other members).
    suspend fun leaveSharedPlot(id: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/plots/$id/leave")
                .withAuth()
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    403 -> throw IOException("must_transfer")
                    else -> if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun listSharedMemberships(): List<SharedMembership> {
        val arr = JSONArray(get("/api/plots/shared"))
        return (0 until arr.length()).map { arr.getJSONObject(it).toSharedMembership() }
    }

    suspend fun acceptPlotInvite(plotId: String, localName: String) {
        post("/api/plots/$plotId/accept", """{"localName":${localName.jsonEsc()}}""")
    }

    suspend fun rejoinPlot(plotId: String, localName: String? = null) {
        val body = if (localName != null) """{"localName":${localName.jsonEsc()}}""" else "{}"
        post("/api/plots/$plotId/rejoin", body)
    }

    suspend fun restorePlot(plotId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/plots/$plotId/restore")
                .withAuth()
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    403 -> throw IOException("not_authorized")
                    410 -> throw IOException("window_expired")
                    else -> if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun transferOwnership(plotId: String, newOwnerId: String) {
        post("/api/plots/$plotId/transfer", """{"newOwnerId":${newOwnerId.jsonEsc()}}""")
    }

    suspend fun setPlotStatus(plotId: String, status: String) {
        patch("/api/plots/$plotId/status", """{"status":${status.jsonEsc()}}""")
    }

    // ── Plot key + members ───────────────────────────────────────────────────

    suspend fun getPlotKey(plotId: String): Pair<String, String> {
        val obj = JSONObject(get("/api/plots/$plotId/plot-key"))
        return Pair(obj.getString("wrappedPlotKey"), obj.getString("plotKeyFormat"))
    }

    suspend fun listPlotMembers(plotId: String): List<PlotMember> {
        val arr = JSONArray(get("/api/plots/$plotId/members"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PlotMember(
                userId = o.getString("userId"),
                displayName = o.getString("displayName"),
                username = o.getString("username"),
                role = o.getString("role"),
                status = o.optString("status", "joined"),
                localName = o.optString("localName").takeIf { s -> s.isNotEmpty() && s != "null" },
            )
        }
    }

    suspend fun addPlotMember(plotId: String, userId: String, wrappedPlotKey: String, plotKeyFormat: String) {
        post("/api/plots/$plotId/members",
            """{"userId":${userId.jsonEsc()},"wrappedPlotKey":${wrappedPlotKey.jsonEsc()},"plotKeyFormat":${plotKeyFormat.jsonEsc()}}""")
    }

    suspend fun generatePlotInvite(plotId: String): Pair<String, String> {
        val obj = JSONObject(post("/api/plots/$plotId/invites", "{}"))
        return Pair(obj.getString("token"), obj.getString("expiresAt"))
    }

    suspend fun listPendingInvites(plotId: String): List<Map<String, String>> {
        val arr = JSONArray(get("/api/plots/$plotId/members/pending"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            mapOf(
                "inviteId" to o.optString("inviteId", ""),
                "recipientPubkey" to o.optString("recipientPubkey", ""),
                "displayName" to o.optString("displayName", ""),
            )
        }
    }

    suspend fun confirmInvite(plotId: String, inviteId: String, wrappedPlotKey: String, plotKeyFormat: String) {
        post("/api/plots/$plotId/members/pending/$inviteId/confirm",
            """{"wrappedPlotKey":${wrappedPlotKey.jsonEsc()},"plotKeyFormat":${plotKeyFormat.jsonEsc()}}""")
    }

    // ── Collection plot items ────────────────────────────────────────────────

    suspend fun listPlotItems(plotId: String): List<PlotItem> {
        val arr = JSONArray(get("/api/plots/$plotId/items"))
        return (0 until arr.length()).map { arr.getJSONObject(it).toPlotItem() }
    }

    suspend fun addPlotItem(
        plotId: String,
        uploadId: String,
        wrappedItemDek: String,
        itemDekFormat: String,
        wrappedThumbnailDek: String? = null,
        thumbnailDekFormat: String? = null,
    ) {
        val parts = mutableListOf(
            """"uploadId":${uploadId.jsonEsc()}""",
            """"wrappedItemDek":${wrappedItemDek.jsonEsc()}""",
            """"itemDekFormat":${itemDekFormat.jsonEsc()}""",
        )
        wrappedThumbnailDek?.let { parts.add(""""wrappedThumbnailDek":${it.jsonEsc()}""") }
        thumbnailDekFormat?.let { parts.add(""""thumbnailDekFormat":${it.jsonEsc()}""") }
        post("/api/plots/$plotId/items", "{${parts.joinToString(",")}}")
    }

    suspend fun removePlotItem(plotId: String, uploadId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/plots/$plotId/items/$uploadId")
                .withAuth().delete().build()
            client.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("HTTP ${it.code}") }
        }
    }

    // ── Trellises ─────────────────────────────────────────────────────────────

    suspend fun listTrellises(): List<Trellis> {
        val arr = JSONArray(get("/api/trellises"))
        return (0 until arr.length()).map { arr.getJSONObject(it).toTrellis() }
    }

    suspend fun createTrellis(name: String, criteria: String, targetPlotId: String, requiresStaging: Boolean): Trellis =
        JSONObject(post("/api/trellises",
            """{"name":${name.jsonEsc()},"criteria":$criteria,"targetPlotId":${targetPlotId.jsonEsc()},"requiresStaging":$requiresStaging}"""
        )).toTrellis()

    suspend fun updateTrellis(id: String, name: String, criteria: String, requiresStaging: Boolean): Trellis =
        JSONObject(put("/api/trellises/$id",
            """{"name":${name.jsonEsc()},"criteria":$criteria,"requiresStaging":$requiresStaging}"""
        )).toTrellis()

    suspend fun deleteTrellis(id: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/trellises/$id")
                .withAuth().delete().build()
            client.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("HTTP ${it.code}") }
        }
    }

    // Backward-compat aliases
    suspend fun listFlows(): List<Trellis> = listTrellises()
    suspend fun createFlow(name: String, criteria: String, targetPlotId: String, requiresStaging: Boolean): Trellis = createTrellis(name, criteria, targetPlotId, requiresStaging)
    suspend fun updateFlow(id: String, name: String, criteria: String, requiresStaging: Boolean): Trellis = updateTrellis(id, name, criteria, requiresStaging)
    suspend fun deleteFlow(id: String) = deleteTrellis(id)

    // ── Staging ──────────────────────────────────────────────────────────────

    suspend fun getTrellisStaging(trellisId: String): List<StagingItem> {
        val arr = JSONArray(get("/api/trellises/$trellisId/staging"))
        return (0 until arr.length()).map { StagingItem(arr.getJSONObject(it).toUpload()) }
    }

    // Backward-compat alias
    suspend fun getFlowStaging(trellisId: String): List<StagingItem> = getTrellisStaging(trellisId)

    suspend fun getPlotStaging(plotId: String): List<StagingItem> {
        val arr = JSONArray(get("/api/plots/$plotId/staging"))
        return (0 until arr.length()).map { StagingItem(arr.getJSONObject(it).toUpload()) }
    }

    suspend fun getRejectedItems(plotId: String): List<StagingItem> {
        val arr = JSONArray(get("/api/plots/$plotId/staging/rejected"))
        return (0 until arr.length()).map { StagingItem(arr.getJSONObject(it).toUpload()) }
    }

    suspend fun approveItem(
        plotId: String,
        uploadId: String,
        wrappedItemDek: String? = null,
        itemDekFormat: String? = null,
        wrappedThumbnailDek: String? = null,
        thumbnailDekFormat: String? = null,
    ) {
        val parts = mutableListOf<String>()
        wrappedItemDek?.let { parts.add(""""wrappedItemDek":${it.jsonEsc()}""") }
        itemDekFormat?.let { parts.add(""""itemDekFormat":${it.jsonEsc()}""") }
        wrappedThumbnailDek?.let { parts.add(""""wrappedThumbnailDek":${it.jsonEsc()}""") }
        thumbnailDekFormat?.let { parts.add(""""thumbnailDekFormat":${it.jsonEsc()}""") }
        post("/api/plots/$plotId/staging/$uploadId/approve", "{${parts.joinToString(",")}}")
    }

    suspend fun rejectItem(plotId: String, uploadId: String) =
        post("/api/plots/$plotId/staging/$uploadId/reject", "{}")

    suspend fun unrejectItem(plotId: String, uploadId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/plots/$plotId/staging/$uploadId/decision")
                .withAuth().delete().build()
            client.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("HTTP ${it.code}") }
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
        rotation: Int = 0,
    ) {
        val thumbJson = if (wrappedThumbnailDekB64 != null) ""","wrappedThumbnailDek":"$wrappedThumbnailDekB64"""" else ""
        val body = """{"toUserId":"$toUserId","wrappedDek":"$wrappedDekB64","dekFormat":"${VaultCrypto.ALG_P256_ECDH_HKDF_V1}","rotation":$rotation$thumbJson}"""
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
    data class MeResponse(val userId: String, val username: String, val displayName: String)
    data class InviteResponse(val token: String, val expiresAt: String)
    data class PairingInitiateResponse(val code: String, val expiresAt: String)

    /** Returns the authenticated user's profile. */
    suspend fun authMe(): MeResponse {
        val body = get("/api/auth/me")
        val json = JSONObject(body)
        return MeResponse(json.getString("user_id"), json.getString("username"), json.getString("display_name"))
    }

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

    /** Lists all active (non-retired) devices for the authenticated user. */
    suspend fun listDevices(): List<DeviceRecord> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("/api/keys/devices"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DeviceRecord(
                deviceId = o.getString("deviceId"),
                deviceLabel = o.getString("deviceLabel"),
                deviceKind = o.getString("deviceKind"),
                createdAt = o.getString("createdAt"),
                lastUsedAt = o.getString("lastUsedAt"),
            )
        }
    }

    /** Revokes a device. Throws IOException on error; caller must handle 403 (current device). */
    suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth/devices/${deviceId.encodeParam()}")
            .withAuth()
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                403 -> throw IOException("403 Cannot remove the current device")
                404 -> throw IOException("404 Device not found")
                else -> if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            }
        }
    }

    data class RegisterResponse(val sessionToken: String)

    data class DeviceRecord(
        val deviceId: String,
        val deviceLabel: String,
        val deviceKind: String,
        val createdAt: String,
        val lastUsedAt: String,
    )

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
                409 -> {
                    val body = response.body?.string() ?: ""
                    // BUG-019: distinguish duplicate device_id from duplicate username.
                    if (body.contains("device", ignoreCase = true)) {
                        throw IOException("409_DEVICE_ID")
                    } else {
                        throw IOException("409_USERNAME")
                    }
                }
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
        criteria = if (isNull("criteria")) null else optString("criteria", null)?.takeIf { it.isNotEmpty() }
            ?: opt("criteria")?.let { it.toString().takeIf { s -> s != "null" } },
        showInGarden = optBoolean("show_in_garden", true),
        visibility = optString("visibility", "private"),
        sortOrder = optInt("sort_order", 0),
        isSystemDefined = optBoolean("is_system_defined", false),
        isOwner = optBoolean("is_owner", true),
        plotStatus = optString("plot_status", "open").let { if (it.isEmpty() || it == "null") "open" else it },
        localName = optString("local_name").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONObject.toSharedMembership() = SharedMembership(
        plotId = getString("plotId"),
        plotName = getString("plotName"),
        ownerUserId = optString("ownerUserId").takeIf { it.isNotEmpty() && it != "null" },
        ownerDisplayName = optString("ownerDisplayName").takeIf { it.isNotEmpty() && it != "null" },
        role = getString("role"),
        status = getString("status"),
        localName = optString("localName").takeIf { it.isNotEmpty() && it != "null" },
        joinedAt = getString("joinedAt"),
        leftAt = optString("leftAt").takeIf { it.isNotEmpty() && it != "null" },
        plotStatus = optString("plotStatus", "open"),
        tombstonedAt = optString("tombstonedAt").takeIf { it.isNotEmpty() && it != "null" },
        tombstonedBy = optString("tombstonedBy").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONObject.toTrellis() = Trellis(
        id = getString("id"),
        name = getString("name"),
        criteria = opt("criteria")?.toString() ?: "{}",
        targetPlotId = getString("targetPlotId"),
        requiresStaging = optBoolean("requiresStaging", true),
    )

    // Backward-compat alias
    private fun JSONObject.toFlow() = toTrellis()

    private fun JSONObject.toPlotItem() = PlotItem(
        upload = toUpload(),
        addedBy = optString("added_by", ""),
        wrappedItemDek = optString("wrapped_item_dek", null)?.takeIf { it.isNotEmpty() },
        itemDekFormat = optString("item_dek_format", null)?.takeIf { it.isNotEmpty() },
        wrappedThumbnailDek = optString("wrapped_thumbnail_dek", null)?.takeIf { it.isNotEmpty() },
        thumbnailDekFormat = optString("thumbnail_dek_format", null)?.takeIf { it.isNotEmpty() },
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
        val BASE_URL: String = if (BuildConfig.BASE_URL_OVERRIDE.isNotEmpty())
            BuildConfig.BASE_URL_OVERRIDE
        else
            "https://api.heirlooms.digital"
    }
}
