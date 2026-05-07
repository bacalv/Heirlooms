package digital.heirlooms.test.api

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
class UploadApiTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient

    @Test
    fun `health endpoint returns 200`() {
        val response = client.newCall(Request.Builder().url("$base/health").get().build()).execute()
        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("ok")
    }

    @Test
    fun `POST image returns 201 and a storage key`() {
        val bytes = ByteArray(256).also { java.util.Random().nextBytes(it) }
        val response = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        ).execute()

        val responseBody = response.body?.string() ?: ""
        assertThat(response.code)
            .withFailMessage("Expected 201 but got ${response.code}: $responseBody")
            .isEqualTo(201)
        assertThat(JSONObject(responseBody).getString("storageKey")).endsWith(".jpg")
    }

    @Test
    fun `POST video returns 201 and mp4 key`() {
        val bytes = ByteArray(512).also { java.util.Random().nextBytes(it) }
        val response = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("video/mp4".toMediaType())).build()
        ).execute()

        val responseBody = response.body?.string() ?: ""
        assertThat(response.code)
            .withFailMessage("Expected 201 but got ${response.code}: $responseBody")
            .isEqualTo(201)
        assertThat(JSONObject(responseBody).getString("storageKey")).endsWith(".mp4")
    }

    @Test
    fun `POST with empty body returns 400`() {
        val body = ByteArray(0).toRequestBody("image/jpeg".toMediaType())

        val response = client.newCall(
            Request.Builder().url("$base/api/content/upload").post(body).build()
        ).execute()

        assertThat(response.code).isEqualTo(400)
    }

    @Test
    fun `POST without Content-Type still returns 201`() {
        val bytes = ByteArray(128).also { java.util.Random().nextBytes(it) }
        val response = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("application/octet-stream".toMediaType())).build()
        ).execute()

        val responseBody = response.body?.string() ?: ""
        assertThat(response.code)
            .withFailMessage("Expected 201 but got ${response.code}: $responseBody")
            .isEqualTo(201)
        assertThat(JSONObject(responseBody).getString("storageKey")).endsWith(".bin")
    }

    @Test
    fun `GET uploads returns JSON array`() {
        val response = client.newCall(
            Request.Builder().url("$base/api/content/uploads").get().build()
        ).execute()

        val responseBody = response.body?.string() ?: ""
        assertThat(response.code)
            .withFailMessage("Expected 200 but got ${response.code}: $responseBody")
            .isEqualTo(200)
        assertThat(response.header("Content-Type")).contains("application/json")
        assertThat(responseBody).startsWith("[")
        assertThat(responseBody).endsWith("]")
        JSONArray(responseBody)
    }

    @Test
    fun `uploaded file appears in listing`() {
        val bytes = ByteArray(64).also { java.util.Random().nextBytes(it) }
        val uploadResponse = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("image/png".toMediaType())).build()
        ).execute()

        val uploadResponseBody = uploadResponse.body?.string() ?: ""
        assertThat(uploadResponse.code)
            .withFailMessage("Expected 201 but got ${uploadResponse.code}: $uploadResponseBody")
            .isEqualTo(201)
        val storageKey = JSONObject(uploadResponseBody).getString("storageKey")

        val listResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads").get().build()
        ).execute()

        val uploads = JSONArray(listResponse.body?.string() ?: "[]")
        val keys = (0 until uploads.length()).map { uploads.getJSONObject(it).getString("storageKey") }
        assertThat(keys).contains(storageKey)
    }
}
