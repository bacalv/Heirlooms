package digital.heirlooms.test.journey

import digital.heirlooms.test.HeirloomsTest
import digital.heirlooms.test.HeirloomsTestEnvironment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * User journey tests using OkHttp to simulate what the Android app does.
 * Playwright browser tests are excluded because Chromium cannot reach the
 * Testcontainers socat proxy port from within its sandbox.
 */
@HeirloomsTest
class UploadJourneyTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient

    @Test
    fun `upload journey - file is stored and retrievable from listing`() {
        val imageBytes = ByteArray(1024) { (it % 256).toByte() }
        val uploadResponse = client.newCall(
            Request.Builder()
                .url("$base/api/content/upload")
                .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
        ).execute()

        val responseBody = uploadResponse.body?.string() ?: ""
        assertThat(uploadResponse.code)
            .withFailMessage("Upload should return 201 but got ${uploadResponse.code}: $responseBody")
            .isEqualTo(201)
        val storageKey = JSONObject(responseBody).getString("storageKey")
        assertThat(storageKey).isNotBlank()

        val listResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads").get().build()
        ).execute()

        val uploads = JSONObject(listResponse.body?.string() ?: "{}").getJSONArray("items")
        val keys = (0 until uploads.length()).map {
            uploads.getJSONObject(it).getString("storageKey")
        }

        assertThat(keys)
            .withFailMessage("Storage key '$storageKey' not found in listing: $keys")
            .contains(storageKey)
    }

    @Test
    fun `upload journey - multiple file types all appear in listing`() {
        val uploads = listOf(
            "image/jpeg" to "photo",
            "video/mp4"  to "video",
            "image/png"  to "screenshot",
        )

        val uploadedKeys = uploads.map { (mimeType, _) ->
            val bytes = ByteArray(256).also { java.util.Random().nextBytes(it) }
            val response = client.newCall(
                Request.Builder()
                    .url("$base/api/content/upload")
                    .post(bytes.toRequestBody(mimeType.toMediaType()))
                    .build()
            ).execute()
            val body = response.body?.string() ?: ""
            assertThat(response.code)
                .withFailMessage("Expected 201 but got ${response.code}: $body")
                .isEqualTo(201)
            JSONObject(body).getString("storageKey")
        }

        val listResponse = client.newCall(
            Request.Builder().url("$base/api/content/uploads").get().build()
        ).execute()

        val listed = JSONObject(listResponse.body?.string() ?: "{}").getJSONArray("items")
        val listedKeys = (0 until listed.length()).map {
            listed.getJSONObject(it).getString("storageKey")
        }

        uploadedKeys.forEach { key ->
            assertThat(listedKeys)
                .withFailMessage("Key '$key' missing from listing")
                .contains(key)
        }
    }

    @Test
    fun `health endpoint is reachable`() {
        val response = client.newCall(
            Request.Builder().url("$base/health").get().build()
        ).execute()
        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("ok")
    }
}
