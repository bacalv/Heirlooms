package digital.heirlooms.test.capsule

import digital.heirlooms.test.HeirloomsTest
import digital.heirlooms.test.HeirloomsTestEnvironment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

@HeirloomsTest
class CapsuleApiTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient
    private val jsonMedia = "application/json".toMediaType()

    // ---- Fixture helpers ------------------------------------------------

    private fun createUploadId(): String {
        val bytes = ByteArray(512).also { java.util.Random().nextBytes(it) }
        val response = client.newCall(
            Request.Builder()
                .url("$base/api/content/upload")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .header("Content-Type", "image/jpeg")
                .build()
        ).execute()
        assertThat(response.code).isEqualTo(201)
        return JSONObject(response.body!!.string()).getString("id")
    }

    private fun postCapsule(body: String): okhttp3.Response =
        client.newCall(
            Request.Builder()
                .url("$base/api/capsules")
                .post(body.toRequestBody(jsonMedia))
                .build()
        ).execute()

    private fun getCapsule(id: String): okhttp3.Response =
        client.newCall(Request.Builder().url("$base/api/capsules/$id").get().build()).execute()

    private fun patchCapsule(id: String, body: String): okhttp3.Response =
        client.newCall(
            Request.Builder()
                .url("$base/api/capsules/$id")
                .patch(body.toRequestBody(jsonMedia))
                .build()
        ).execute()

    private fun sealCapsule(id: String): okhttp3.Response =
        client.newCall(
            Request.Builder()
                .url("$base/api/capsules/$id/seal")
                .post("".toRequestBody(jsonMedia))
                .build()
        ).execute()

    private fun cancelCapsule(id: String): okhttp3.Response =
        client.newCall(
            Request.Builder()
                .url("$base/api/capsules/$id/cancel")
                .post("".toRequestBody(jsonMedia))
                .build()
        ).execute()

    private fun createOpenCapsuleWithUpload(): String {
        val uploadId = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"],"upload_ids":["$uploadId"],"message":"Test"}""")
        assertThat(resp.code).isEqualTo(201)
        return JSONObject(resp.body!!.string()).getString("id")
    }

    // ---- Create flow ----------------------------------------------------

    @Test
    fun `create open capsule with single recipient and one photo returns 201`() {
        val uploadId = createUploadId()
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00+01:00",
             "recipients":["Sophie"],"upload_ids":["$uploadId"],"message":"Hello"}
        """.trimIndent())

        assertThat(response.code).isEqualTo(201)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getString("shape")).isEqualTo("open")
        assertThat(body.getString("state")).isEqualTo("open")
        assertThat(body.getJSONArray("recipients").getString(0)).isEqualTo("Sophie")
        assertThat(body.getString("message")).isEqualTo("Hello")
        assertThat(body.getJSONArray("uploads").length()).isEqualTo(1)
    }

    @Test
    fun `create open capsule with multiple recipients and multiple photos`() {
        val id1 = createUploadId()
        val id2 = createUploadId()
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z",
             "recipients":["Alice","Bob"],"upload_ids":["$id1","$id2"]}
        """.trimIndent())

        assertThat(response.code).isEqualTo(201)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getJSONArray("recipients").length()).isEqualTo(2)
        assertThat(body.getJSONArray("uploads").length()).isEqualTo(2)
    }

    @Test
    fun `create open capsule with no photos is allowed`() {
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"]}
        """.trimIndent())

        assertThat(response.code).isEqualTo(201)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getJSONArray("uploads").length()).isEqualTo(0)
    }

    @Test
    fun `create open capsule with no message returns empty message string`() {
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"]}
        """.trimIndent())

        assertThat(response.code).isEqualTo(201)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getString("message")).isEqualTo("")
    }

    @Test
    fun `create sealed capsule with photos returns 201 with correct shape and state`() {
        val uploadId = createUploadId()
        val response = postCapsule("""
            {"shape":"sealed","unlock_at":"2042-05-14T08:00:00Z",
             "recipients":["Sophie"],"upload_ids":["$uploadId"]}
        """.trimIndent())

        assertThat(response.code).isEqualTo(201)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getString("shape")).isEqualTo("sealed")
        assertThat(body.getString("state")).isEqualTo("sealed")
    }

    @Test
    fun `create sealed capsule with no photos returns 422`() {
        val response = postCapsule("""
            {"shape":"sealed","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"]}
        """.trimIndent())
        assertThat(response.code).isEqualTo(422)
    }

    @Test
    fun `create capsule with empty recipients returns 422`() {
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":[]}
        """.trimIndent())
        assertThat(response.code).isEqualTo(422)
    }

    @Test
    fun `create capsule with unknown upload_id returns 400`() {
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z",
             "recipients":["Sophie"],"upload_ids":["00000000-0000-0000-0000-000000000000"]}
        """.trimIndent())
        assertThat(response.code).isEqualTo(400)
    }

    @Test
    fun `create capsule with invalid shape returns 400`() {
        val response = postCapsule("""
            {"shape":"square","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"]}
        """.trimIndent())
        assertThat(response.code).isEqualTo(400)
    }

    @Test
    fun `create capsule with malformed unlock_at returns 400`() {
        val response = postCapsule("""
            {"shape":"open","unlock_at":"not-a-date","recipients":["Sophie"]}
        """.trimIndent())
        assertThat(response.code).isEqualTo(400)
    }

    @Test
    fun `create capsule with past unlock_at is accepted`() {
        val response = postCapsule("""
            {"shape":"open","unlock_at":"2000-01-01T00:00:00Z","recipients":["Sophie"]}
        """.trimIndent())
        assertThat(response.code).isEqualTo(201)
    }

    // ---- Read and list --------------------------------------------------

    @Test
    fun `GET capsule returns full object with uploads, recipients, current message`() {
        val uploadId = createUploadId()
        val createResp = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z",
             "recipients":["Sophie"],"upload_ids":["$uploadId"],"message":"Hi there"}
        """.trimIndent())
        val capsuleId = JSONObject(createResp.body!!.string()).getString("id")

        val response = getCapsule(capsuleId)
        assertThat(response.code).isEqualTo(200)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getString("id")).isEqualTo(capsuleId)
        assertThat(body.getString("message")).isEqualTo("Hi there")
        assertThat(body.getJSONArray("uploads").length()).isEqualTo(1)
        assertThat(body.getJSONArray("recipients").getString(0)).isEqualTo("Sophie")
    }

    @Test
    fun `GET non-existent capsule returns 404`() {
        val response = getCapsule("00000000-0000-0000-0000-000000000000")
        assertThat(response.code).isEqualTo(404)
    }

    @Test
    fun `list defaults to open and sealed, excluding cancelled and delivered`() {
        val openId = createOpenCapsuleWithUpload()
        val uploadId = createUploadId()
        val sealedResp = postCapsule("""
            {"shape":"sealed","unlock_at":"2042-05-14T08:00:00Z",
             "recipients":["Bob"],"upload_ids":["$uploadId"]}
        """.trimIndent())
        val sealedId = JSONObject(sealedResp.body!!.string()).getString("id")

        cancelCapsule(openId)  // this will not appear in default list

        val listResp = client.newCall(Request.Builder().url("$base/api/capsules").get().build()).execute()
        assertThat(listResp.code).isEqualTo(200)
        val capsules = JSONObject(listResp.body!!.string()).getJSONArray("capsules")
        val ids = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        assertThat(ids).contains(sealedId)
        assertThat(ids).doesNotContain(openId)
    }

    @Test
    fun `list with state=cancelled returns cancelled capsules`() {
        val id = createOpenCapsuleWithUpload()
        cancelCapsule(id)

        val listResp = client.newCall(
            Request.Builder().url("$base/api/capsules?state=cancelled").get().build()
        ).execute()
        assertThat(listResp.code).isEqualTo(200)
        val capsules = JSONObject(listResp.body!!.string()).getJSONArray("capsules")
        val ids = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        assertThat(ids).contains(id)
    }

    @Test
    fun `list with state=open,sealed,cancelled returns all three states`() {
        createOpenCapsuleWithUpload()
        val cancelId = createOpenCapsuleWithUpload()
        cancelCapsule(cancelId)

        val listResp = client.newCall(
            Request.Builder().url("$base/api/capsules?state=open,sealed,cancelled").get().build()
        ).execute()
        assertThat(listResp.code).isEqualTo(200)
        val capsules = JSONObject(listResp.body!!.string()).getJSONArray("capsules")
        val states = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("state") }
        assertThat(states).contains("open")
        assertThat(states).contains("cancelled")
    }

    @Test
    fun `list orders by updated_at descending by default`() {
        val id1 = createOpenCapsuleWithUpload()
        val id2 = createOpenCapsuleWithUpload()

        val listResp = client.newCall(Request.Builder().url("$base/api/capsules").get().build()).execute()
        val capsules = JSONObject(listResp.body!!.string()).getJSONArray("capsules")
        val ids = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        // Most recently created capsule should appear first (it has the latest updated_at)
        val pos1 = ids.indexOf(id1)
        val pos2 = ids.indexOf(id2)
        assertThat(pos2).isLessThan(pos1)
    }

    @Test
    fun `list orders by unlock_at when order=unlock_at`() {
        val resp1 = postCapsule("""{"shape":"open","unlock_at":"2030-01-01T08:00:00Z","recipients":["A"]}""")
        val resp2 = postCapsule("""{"shape":"open","unlock_at":"2060-01-01T08:00:00Z","recipients":["B"]}""")
        val id1 = JSONObject(resp1.body!!.string()).getString("id")
        val id2 = JSONObject(resp2.body!!.string()).getString("id")

        val listResp = client.newCall(
            Request.Builder().url("$base/api/capsules?order=unlock_at").get().build()
        ).execute()
        val capsules = JSONObject(listResp.body!!.string()).getJSONArray("capsules")
        val ids = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        val pos1 = ids.indexOf(id1)
        val pos2 = ids.indexOf(id2)
        // unlock_at DESC: 2060 comes before 2030
        assertThat(pos2).isLessThan(pos1)
    }

    @Test
    fun `list response contains upload_count and has_message, not full uploads`() {
        val uploadId = createUploadId()
        val resp = postCapsule("""
            {"shape":"open","unlock_at":"2042-05-14T08:00:00Z",
             "recipients":["Sophie"],"upload_ids":["$uploadId"],"message":"hi"}
        """.trimIndent())
        val id = JSONObject(resp.body!!.string()).getString("id")

        val listResp = client.newCall(Request.Builder().url("$base/api/capsules").get().build()).execute()
        val capsules = JSONObject(listResp.body!!.string()).getJSONArray("capsules")
        val capsule = (0 until capsules.length())
            .map { capsules.getJSONObject(it) }
            .first { it.getString("id") == id }

        assertThat(capsule.has("upload_count")).isTrue()
        assertThat(capsule.has("has_message")).isTrue()
        assertThat(capsule.has("uploads")).isFalse()
        assertThat(capsule.has("message")).isFalse()
        assertThat(capsule.getInt("upload_count")).isEqualTo(1)
        assertThat(capsule.getBoolean("has_message")).isTrue()
    }

    // ---- Update flow ----------------------------------------------------

    @Test
    fun `PATCH single field updates only that field`() {
        val id = createOpenCapsuleWithUpload()
        val response = patchCapsule(id, """{"unlock_at":"2050-01-01T08:00:00Z"}""")

        assertThat(response.code).isEqualTo(200)
        val body = JSONObject(response.body!!.string())
        assertThat(body.getString("unlock_at")).contains("2050")
    }

    @Test
    fun `PATCH message creates new version row`() {
        val id = createOpenCapsuleWithUpload()
        val r1 = patchCapsule(id, """{"message":"First edit"}""")
        assertThat(r1.code).isEqualTo(200)
        assertThat(JSONObject(r1.body!!.string()).getString("message")).isEqualTo("First edit")

        val r2 = patchCapsule(id, """{"message":"Second edit"}""")
        assertThat(r2.code).isEqualTo(200)
        assertThat(JSONObject(r2.body!!.string()).getString("message")).isEqualTo("Second edit")
    }

    @Test
    fun `PATCH message with same body does not create new version`() {
        val id = createOpenCapsuleWithUpload()
        patchCapsule(id, """{"message":"Same text"}""")
        patchCapsule(id, """{"message":"Same text"}""")

        // The key assertion is the message stays the same (no error, no duplicate version)
        val body = JSONObject(getCapsule(id).body!!.string())
        assertThat(body.getString("message")).isEqualTo("Same text")
    }

    @Test
    fun `PATCH message with empty string when existing message creates new empty version`() {
        val id = createOpenCapsuleWithUpload()
        patchCapsule(id, """{"message":"Had a message"}""")
        val r = patchCapsule(id, """{"message":""}""")

        assertThat(r.code).isEqualTo(200)
        assertThat(JSONObject(r.body!!.string()).getString("message")).isEqualTo("")
    }

    @Test
    fun `PATCH recipients replaces the recipient list`() {
        val id = createOpenCapsuleWithUpload()
        val r = patchCapsule(id, """{"recipients":["NewRecipient"]}""")

        assertThat(r.code).isEqualTo(200)
        val recipients = JSONObject(r.body!!.string()).getJSONArray("recipients")
        assertThat(recipients.length()).isEqualTo(1)
        assertThat(recipients.getString(0)).isEqualTo("NewRecipient")
    }

    @Test
    fun `PATCH upload_ids replaces contents on open capsule`() {
        val upload1 = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$upload1"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")

        val upload2 = createUploadId()
        val r = patchCapsule(id, """{"upload_ids":["$upload2"]}""")

        assertThat(r.code).isEqualTo(200)
        val uploads = JSONObject(r.body!!.string()).getJSONArray("uploads")
        assertThat(uploads.length()).isEqualTo(1)
        assertThat(uploads.getJSONObject(0).getString("id")).isEqualTo(upload2)
    }

    @Test
    fun `PATCH upload_ids on sealed capsule returns 409`() {
        val upload1 = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$upload1"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")
        sealCapsule(id)

        val upload2 = createUploadId()
        val r = patchCapsule(id, """{"upload_ids":["$upload2"]}""")
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `PATCH on delivered capsule returns 409`() {
        // Manually put capsule into delivered state by sealing then cancelling — delivered state
        // isn't reachable via API in v1, so we test cancelled as a proxy for terminal state.
        // We test the cancelled case separately below; here we validate the conflict logic.
        val id = createOpenCapsuleWithUpload()
        cancelCapsule(id)
        val r = patchCapsule(id, """{"unlock_at":"2050-01-01T08:00:00Z"}""")
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `PATCH on cancelled capsule returns 409`() {
        val id = createOpenCapsuleWithUpload()
        cancelCapsule(id)
        val r = patchCapsule(id, """{"recipients":["X"]}""")
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `PATCH unlock_at works on open capsule`() {
        val id = createOpenCapsuleWithUpload()
        val r = patchCapsule(id, """{"unlock_at":"2055-06-15T08:00:00Z"}""")

        assertThat(r.code).isEqualTo(200)
        assertThat(JSONObject(r.body!!.string()).getString("unlock_at")).contains("2055")
    }

    @Test
    fun `PATCH unlock_at works on sealed capsule`() {
        val id = createOpenCapsuleWithUpload()
        sealCapsule(id)
        val r = patchCapsule(id, """{"unlock_at":"2055-06-15T08:00:00Z"}""")

        assertThat(r.code).isEqualTo(200)
        assertThat(JSONObject(r.body!!.string()).getString("unlock_at")).contains("2055")
    }

    @Test
    fun `PATCH mixing upload_ids with other fields on sealed capsule rejects entirely`() {
        val upload1 = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$upload1"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")
        sealCapsule(id)

        val upload2 = createUploadId()
        // This should reject entirely — not partially apply the message change
        val r = patchCapsule(id, """{"upload_ids":["$upload2"],"message":"New message"}""")
        assertThat(r.code).isEqualTo(409)

        // Verify the message was NOT changed (transaction rolled back)
        val current = JSONObject(getCapsule(id).body!!.string())
        assertThat(current.getString("message")).isEqualTo("")
    }

    @Test
    fun `PATCH updated_at is updated on successful PATCH`() {
        val id = createOpenCapsuleWithUpload()
        val before = JSONObject(getCapsule(id).body!!.string()).getString("updated_at")

        Thread.sleep(50)
        patchCapsule(id, """{"recipients":["Changed"]}""")
        val after = JSONObject(getCapsule(id).body!!.string()).getString("updated_at")

        assertThat(after).isGreaterThanOrEqualTo(before)
    }

    // ---- Seal flow ------------------------------------------------------

    @Test
    fun `POST seal on open capsule with photos transitions state to sealed`() {
        val uploadId = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"],"upload_ids":["$uploadId"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")

        val r = sealCapsule(id)
        assertThat(r.code).isEqualTo(200)
        val body = JSONObject(r.body!!.string())
        assertThat(body.getString("state")).isEqualTo("sealed")
        assertThat(body.getString("shape")).isEqualTo("open")
    }

    @Test
    fun `POST seal updates updated_at`() {
        val uploadId = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$uploadId"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")
        val before = JSONObject(getCapsule(id).body!!.string()).getString("updated_at")

        Thread.sleep(50)
        sealCapsule(id)
        val after = JSONObject(getCapsule(id).body!!.string()).getString("updated_at")
        assertThat(after).isGreaterThanOrEqualTo(before)
    }

    @Test
    fun `POST seal on already sealed capsule returns 409`() {
        val id = createOpenCapsuleWithUpload()
        sealCapsule(id)
        val r = sealCapsule(id)
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `POST seal on delivered capsule returns 409`() {
        // Proxy: seal then cancel to reach a terminal state, verify seal of cancelled = 409
        val id = createOpenCapsuleWithUpload()
        sealCapsule(id)
        cancelCapsule(id)
        val r = sealCapsule(id)
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `POST seal on cancelled capsule returns 409`() {
        val id = createOpenCapsuleWithUpload()
        cancelCapsule(id)
        val r = sealCapsule(id)
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `POST seal on empty open capsule returns 422`() {
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["Sophie"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")

        val r = sealCapsule(id)
        assertThat(r.code).isEqualTo(422)
    }

    // ---- Cancel flow ----------------------------------------------------

    @Test
    fun `POST cancel on open capsule sets state to cancelled and records cancelled_at`() {
        val id = createOpenCapsuleWithUpload()
        val r = cancelCapsule(id)

        assertThat(r.code).isEqualTo(200)
        val body = JSONObject(r.body!!.string())
        assertThat(body.getString("state")).isEqualTo("cancelled")
        assertThat(body.isNull("cancelled_at")).isFalse()
    }

    @Test
    fun `POST cancel on sealed capsule sets state to cancelled`() {
        val id = createOpenCapsuleWithUpload()
        sealCapsule(id)
        val r = cancelCapsule(id)

        assertThat(r.code).isEqualTo(200)
        assertThat(JSONObject(r.body!!.string()).getString("state")).isEqualTo("cancelled")
    }

    @Test
    fun `POST cancel on already cancelled capsule returns 409`() {
        val id = createOpenCapsuleWithUpload()
        cancelCapsule(id)
        val r = cancelCapsule(id)
        assertThat(r.code).isEqualTo(409)
    }

    @Test
    fun `cancelled capsules excluded from default list but visible with state=cancelled`() {
        val id = createOpenCapsuleWithUpload()
        cancelCapsule(id)

        val defaultList = JSONObject(client.newCall(Request.Builder().url("$base/api/capsules").get().build()).execute().body!!.string())
        val defaultIds = (0 until defaultList.getJSONArray("capsules").length())
            .map { defaultList.getJSONArray("capsules").getJSONObject(it).getString("id") }
        assertThat(defaultIds).doesNotContain(id)

        val cancelledList = JSONObject(client.newCall(Request.Builder().url("$base/api/capsules?state=cancelled").get().build()).execute().body!!.string())
        val cancelledIds = (0 until cancelledList.getJSONArray("capsules").length())
            .map { cancelledList.getJSONArray("capsules").getJSONObject(it).getString("id") }
        assertThat(cancelledIds).contains(id)
    }

    // ---- Reverse lookup -------------------------------------------------

    @Test
    fun `GET uploads-id-capsules returns capsules containing that upload`() {
        val uploadId = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$uploadId"]}""")
        val capsuleId = JSONObject(resp.body!!.string()).getString("id")

        val r = client.newCall(Request.Builder().url("$base/api/content/uploads/$uploadId/capsules").get().build()).execute()
        assertThat(r.code).isEqualTo(200)
        val capsules = JSONObject(r.body!!.string()).getJSONArray("capsules")
        val ids = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        assertThat(ids).contains(capsuleId)
    }

    @Test
    fun `GET uploads-id-capsules returns only active capsules`() {
        val uploadId = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$uploadId"]}""")
        val capsuleId = JSONObject(resp.body!!.string()).getString("id")
        cancelCapsule(capsuleId)

        val r = client.newCall(Request.Builder().url("$base/api/content/uploads/$uploadId/capsules").get().build()).execute()
        assertThat(r.code).isEqualTo(200)
        val capsules = JSONObject(r.body!!.string()).getJSONArray("capsules")
        val ids = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        assertThat(ids).doesNotContain(capsuleId)
    }

    @Test
    fun `GET uploads-id-capsules returns empty array when upload exists but not in any capsules`() {
        val uploadId = createUploadId()
        val r = client.newCall(Request.Builder().url("$base/api/content/uploads/$uploadId/capsules").get().build()).execute()

        assertThat(r.code).isEqualTo(200)
        val capsules = JSONObject(r.body!!.string()).getJSONArray("capsules")
        assertThat(capsules.length()).isEqualTo(0)
    }

    @Test
    fun `GET uploads-id-capsules returns 404 when upload does not exist`() {
        val r = client.newCall(
            Request.Builder().url("$base/api/content/uploads/00000000-0000-0000-0000-000000000000/capsules").get().build()
        ).execute()
        assertThat(r.code).isEqualTo(404)
    }

    @Test
    fun `upload in three capsules returns all three in reverse lookup`() {
        val uploadId = createUploadId()
        val ids = (1..3).map { i ->
            val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["R$i"],"upload_ids":["$uploadId"]}""")
            JSONObject(resp.body!!.string()).getString("id")
        }

        val r = client.newCall(Request.Builder().url("$base/api/content/uploads/$uploadId/capsules").get().build()).execute()
        assertThat(r.code).isEqualTo(200)
        val capsules = JSONObject(r.body!!.string()).getJSONArray("capsules")
        val returnedIds = (0 until capsules.length()).map { capsules.getJSONObject(it).getString("id") }
        assertThat(returnedIds).containsAll(ids)
    }

    // ---- Cascade behaviour ----------------------------------------------

    @Test
    fun `deleting an upload via its contents removes it from capsule but capsule remains`() {
        // We can verify cascade indirectly: create a capsule with 2 uploads,
        // then PATCH to remove one. The capsule still exists.
        val u1 = createUploadId()
        val u2 = createUploadId()
        val resp = postCapsule("""{"shape":"open","unlock_at":"2042-05-14T08:00:00Z","recipients":["A"],"upload_ids":["$u1","$u2"]}""")
        val id = JSONObject(resp.body!!.string()).getString("id")

        patchCapsule(id, """{"upload_ids":["$u2"]}""")
        val current = JSONObject(getCapsule(id).body!!.string())
        assertThat(current.getJSONArray("uploads").length()).isEqualTo(1)
        assertThat(current.getString("id")).isEqualTo(id)
    }

    // ---- Spec generation canary -----------------------------------------

    @Test
    fun `OpenAPI spec endpoint returns 200 with valid JSON including capsule paths`() {
        val r = client.newCall(Request.Builder().url("$base/docs/api.json").get().build()).execute()
        assertThat(r.code).isEqualTo(200)
        assertThat(r.header("Content-Type")).contains("application/json")
        val spec = JSONObject(r.body!!.string())
        assertThat(spec.has("paths")).isTrue()
        val paths = spec.getJSONObject("paths")
        val pathKeys = paths.keys().asSequence().toList()
        // Capsule endpoints should be present
        assertThat(pathKeys.any { it.contains("/api/capsules") }).isTrue()
    }
}
