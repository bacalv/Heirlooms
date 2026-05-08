package digital.heirlooms.api

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
    private val apiKey: String,
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
        JSONObject(get("/api/content/uploads/composted")).getJSONArray("uploads").toUploadList()

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
        JSONObject(get("/api/content/uploads/tags")).getJSONArray("tags")
            .let { arr -> (0 until arr.length()).map { arr.getString(it) } }

    suspend fun getCapsulesForUpload(id: String): List<CapsuleRef> =
        JSONObject(get("/api/content/uploads/$id/capsules")).getJSONArray("capsules").toCapsuleRefList()

    // ── Plots ────────────────────────────────────────────────────────────────

    suspend fun listPlots(): List<Plot> =
        JSONObject(get("/api/plots")).getJSONArray("plots").toPlotList()

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

    // ── JSON → model helpers ─────────────────────────────────────────────────

    private fun JSONObject.toUploadPage() = UploadPage(
        uploads = getJSONArray("uploads").toUploadList(),
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
        capturedAt = optString("capturedAt").takeIf { it.isNotEmpty() && it != "null" },
        latitude = if (has("latitude") && !isNull("latitude")) getDouble("latitude") else null,
        longitude = if (has("longitude") && !isNull("longitude")) getDouble("longitude") else null,
        lastViewedAt = optString("lastViewedAt").takeIf { it.isNotEmpty() && it != "null" },
    )

    private fun JSONArray.toPlotList(): List<Plot> =
        (0 until length()).map { getJSONObject(it).toPlot() }

    private fun JSONObject.toPlot() = Plot(
        id = getString("id"),
        name = getString("name"),
        tagCriteria = getJSONArray("tag_criteria").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
        sortOrder = optInt("sort_order", 0),
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

    companion object {
        const val BASE_URL = "https://api.heirlooms.digital"
    }
}
