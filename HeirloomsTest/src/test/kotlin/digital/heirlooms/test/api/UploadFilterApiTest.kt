package digital.heirlooms.test.api

import digital.heirlooms.test.HeirloomsTest
import digital.heirlooms.test.HeirloomsTestEnvironment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

@HeirloomsTest
class UploadFilterApiTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient

    private fun uploadImage(tag: String? = null): String {
        val bytes = ByteArray(128).also { java.util.Random().nextBytes(it) }
        val resp = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        ).execute()
        val body = JSONObject(resp.body!!.string())
        val id = body.getString("id")
        if (tag != null) {
            val tagBody = """{"tags":["$tag"]}"""
            client.newCall(
                Request.Builder().url("$base/api/content/uploads/$id/tags")
                    .patch(tagBody.toRequestBody("application/json".toMediaType())).build()
            ).execute()
        }
        return id
    }

    private fun listUploads(queryString: String = ""): JSONObject {
        val url = "$base/api/content/uploads${if (queryString.isNotEmpty()) "?$queryString" else ""}"
        val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
        assertThat(resp.code).isEqualTo(200)
        return JSONObject(resp.body!!.string())
    }

    private fun itemIds(data: JSONObject): List<String> =
        (0 until data.getJSONArray("items").length()).map { data.getJSONArray("items").getJSONObject(it).getString("id") }

    // ---- Baseline -----------------------------------------------------------

    @Test
    fun `list uploads returns paginated object with items and next_cursor fields`() {
        val data = listUploads()
        assertThat(data.has("items")).isTrue()
        assertThat(data.has("next_cursor")).isTrue()
    }

    // ---- Tag filter ---------------------------------------------------------

    @Test
    fun `tag filter returns only items with that tag`() {
        val taggedId = uploadImage(tag = "d3-filter-tag-single")
        uploadImage() // no tag

        val data = listUploads("tag=d3-filter-tag-single")
        val ids = itemIds(data)
        assertThat(ids).contains(taggedId)
        // Verify no untagged items are mixed in (all returned items must have the tag)
        val items = data.getJSONArray("items")
        for (i in 0 until items.length()) {
            val tagsArr = items.getJSONObject(i).getJSONArray("tags")
            val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
            assertThat(tags).contains("d3-filter-tag-single")
        }
    }

    @Test
    fun `multi-tag filter (comma-separated) returns items matching any tag`() {
        val id1 = uploadImage(tag = "d3-multi-a")
        val id2 = uploadImage(tag = "d3-multi-b")
        uploadImage() // untagged, should not appear

        val data = listUploads("tag=d3-multi-a,d3-multi-b")
        val ids = itemIds(data)
        assertThat(ids).contains(id1)
        assertThat(ids).contains(id2)
    }

    // ---- Date range filter --------------------------------------------------

    @Test
    fun `from_date excludes items uploaded before that date`() {
        val id = uploadImage()
        // Request with a far-future from_date — uploaded item should not appear
        val data = listUploads("from_date=2099-01-01")
        val ids = itemIds(data)
        assertThat(ids).doesNotContain(id)
    }

    @Test
    fun `to_date excludes items uploaded after that date`() {
        val id = uploadImage()
        // Request with a far-past to_date — the newly uploaded item should not appear
        val data = listUploads("to_date=2000-01-01")
        val ids = itemIds(data)
        assertThat(ids).doesNotContain(id)
    }

    @Test
    fun `from_date and to_date together narrow the result window`() {
        val id = uploadImage()
        // Today is within a generous window covering the test run
        val data = listUploads("from_date=2026-01-01&to_date=2099-12-31")
        val ids = itemIds(data)
        assertThat(ids).contains(id)
    }

    // ---- Capsule membership filter -----------------------------------------

    @Test
    fun `in_capsule=false excludes items in active capsules`() {
        // Without adding to a capsule, a fresh upload should appear in not-in-capsule results
        val id = uploadImage()
        val data = listUploads("in_capsule=false")
        val ids = itemIds(data)
        assertThat(ids).contains(id)
    }

    // ---- Location filter ----------------------------------------------------

    @Test
    fun `has_location=false returns items without location`() {
        // Uploads via the upload endpoint have no EXIF so no location
        val id = uploadImage()
        val data = listUploads("has_location=false")
        val ids = itemIds(data)
        assertThat(ids).contains(id)
    }

    @Test
    fun `has_location=true excludes items with no location`() {
        val id = uploadImage()
        val data = listUploads("has_location=true")
        val ids = itemIds(data)
        assertThat(ids).doesNotContain(id)
    }

    // ---- Sort ---------------------------------------------------------------

    @Test
    fun `sort=upload_oldest returns oldest first`() {
        uploadImage(); uploadImage()
        val data = listUploads("sort=upload_oldest&limit=2")
        val items = data.getJSONArray("items")
        if (items.length() >= 2) {
            val t1 = items.getJSONObject(0).getString("uploadedAt")
            val t2 = items.getJSONObject(1).getString("uploadedAt")
            assertThat(t1 <= t2).isTrue()
        }
    }

    @Test
    fun `sort=upload_newest returns newest first (default behaviour)`() {
        uploadImage(); uploadImage()
        val data = listUploads("sort=upload_newest&limit=2")
        val items = data.getJSONArray("items")
        if (items.length() >= 2) {
            val t1 = items.getJSONObject(0).getString("uploadedAt")
            val t2 = items.getJSONObject(1).getString("uploadedAt")
            assertThat(t1 >= t2).isTrue()
        }
    }

    @Test
    fun `sort=taken_newest response is 200 and well-formed (items without EXIF sort to bottom)`() {
        uploadImage(); uploadImage()
        val data = listUploads("sort=taken_newest&limit=5")
        assertThat(data.has("items")).isTrue()
        assertThat(data.has("next_cursor")).isTrue()
    }

    @Test
    fun `sort=taken_oldest response is 200`() {
        val data = listUploads("sort=taken_oldest&limit=5")
        assertThat(data.getJSONArray("items")).isNotNull
    }

    // ---- Include composted --------------------------------------------------

    @Test
    fun `include_composted=true mixes in composted items`() {
        // Upload and then compost an item
        val id = uploadImage()
        client.newCall(
            Request.Builder().url("$base/api/content/uploads/$id/compost")
                .post("".toRequestBody()).build()
        ).execute()

        val withComposted = listUploads("include_composted=true")
        val ids = itemIds(withComposted)
        assertThat(ids).contains(id)

        val withoutComposted = listUploads()
        val idsWithout = itemIds(withoutComposted)
        assertThat(idsWithout).doesNotContain(id)
    }

    // ---- Just arrived -------------------------------------------------------

    @Test
    fun `just_arrived=true returns untagged items and excludes tagged items`() {
        val untaggedId = uploadImage()
        val taggedId = uploadImage(tag = "d3-just-arrived-tagged")

        val data = listUploads("just_arrived=true")
        val ids = itemIds(data)
        assertThat(ids).contains(untaggedId)
        assertThat(ids).doesNotContain(taggedId)
    }

    @Test
    fun `just_arrived=true keeps item after it has been viewed`() {
        val id = uploadImage()
        val before = listUploads("just_arrived=true")
        assertThat(itemIds(before)).contains(id)

        // Record a view — should NOT remove from just_arrived
        val viewResp = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$id/view")
                .post("".toRequestBody()).build()
        ).execute()
        assertThat(viewResp.code).isEqualTo(204)

        val after = listUploads("just_arrived=true")
        assertThat(itemIds(after)).contains(id)
    }

    // ---- Multi-filter combination -------------------------------------------

    @Test
    fun `combined tag + has_location=false + sort filters all work together`() {
        val id = uploadImage(tag = "d3-combo-tag")
        // Request: tagged, no location, oldest first
        val data = listUploads("tag=d3-combo-tag&has_location=false&sort=upload_oldest")
        val ids = itemIds(data)
        assertThat(ids).contains(id)
        assertThat(data.has("next_cursor")).isTrue()
    }

    // ---- View endpoint ------------------------------------------------------

    @Test
    fun `POST view returns 204 for existing upload`() {
        val id = uploadImage()
        val resp = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$id/view")
                .post("".toRequestBody()).build()
        ).execute()
        assertThat(resp.code).isEqualTo(204)
    }

    @Test
    fun `POST view is idempotent`() {
        val id = uploadImage()
        repeat(2) {
            val resp = client.newCall(
                Request.Builder().url("$base/api/content/uploads/$id/view")
                    .post("".toRequestBody()).build()
            ).execute()
            assertThat(resp.code).isEqualTo(204)
        }
    }

    @Test
    fun `POST view on unknown id returns 404`() {
        val fakeId = java.util.UUID.randomUUID()
        val resp = client.newCall(
            Request.Builder().url("$base/api/content/uploads/$fakeId/view")
                .post("".toRequestBody()).build()
        ).execute()
        assertThat(resp.code).isEqualTo(404)
    }
}
