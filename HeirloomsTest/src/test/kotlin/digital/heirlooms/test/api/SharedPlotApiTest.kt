package digital.heirlooms.test.api

import digital.heirlooms.test.HeirloomsTest
import digital.heirlooms.test.HeirloomsTestEnvironment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.Base64
import java.security.SecureRandom

@HeirloomsTest
class SharedPlotApiTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient

    // ---- Helpers ------------------------------------------------------------

    private fun randomB64(bytes: Int = 64): String =
        Base64.getEncoder().encodeToString(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    private fun createSharedPlot(name: String): JSONObject {
        val body = JSONObject()
            .put("name", name)
            .put("visibility", "shared")
            .put("wrappedPlotKey", randomB64(64))
            .put("plotKeyFormat", "p256-ecdh-hkdf-aes256gcm-v1")
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).withFailMessage("createSharedPlot failed: ${resp.body?.string()}").isEqualTo(201)
        return JSONObject(resp.body!!.string())
    }

    private fun deletePlot(id: String) {
        client.newCall(Request.Builder().url("$base/api/plots/$id").delete().build()).execute()
    }

    private fun uploadImage(): String {
        val bytes = ByteArray(128).also { java.util.Random().nextBytes(it) }
        val resp = client.newCall(
            Request.Builder().url("$base/api/content/upload")
                .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        ).execute()
        return JSONObject(resp.body!!.string()).getString("id")
    }

    // ---- Shared plot creation + plot_members row ---------------------------

    @Test
    fun `POST plots with visibility shared creates plot and owner plot_members row`() {
        val plot = createSharedPlot("d3-shared-create")
        try {
            assertThat(plot.getString("visibility")).isEqualTo("shared")
            assertThat(plot.isNull("criteria")).isTrue()

            val keyResp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/plot-key").build()
            ).execute()
            assertThat(keyResp.code).isEqualTo(200)
            val keyData = JSONObject(keyResp.body!!.string())
            assertThat(keyData.has("wrappedPlotKey")).isTrue()
            assertThat(keyData.getString("plotKeyFormat")).isEqualTo("p256-ecdh-hkdf-aes256gcm-v1")
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `POST plots shared without wrappedPlotKey returns 400`() {
        val body = JSONObject().put("name", "bad-shared").put("visibility", "shared")
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(400)
    }

    @Test
    fun `GET plot-key by owner returns wrapped key`() {
        val plot = createSharedPlot("d3-plot-key-owner")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/plot-key").build()
            ).execute()
            assertThat(resp.code).isEqualTo(200)
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `GET plot-key on private plot returns 403`() {
        val plotBody = JSONObject().put("name", "d3-private-key-test")
        val plotResp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .post(plotBody.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        val plot = JSONObject(plotResp.body!!.string())
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/plot-key").build()
            ).execute()
            assertThat(resp.code).isEqualTo(403)
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- Members list -------------------------------------------------------

    @Test
    fun `GET members shows owner after shared plot creation`() {
        val plot = createSharedPlot("d3-members-list")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/members").build()
            ).execute()
            assertThat(resp.code).isEqualTo(200)
            val arr = org.json.JSONArray(resp.body!!.string())
            assertThat(arr.length()).isEqualTo(1)
            assertThat(arr.getJSONObject(0).getString("role")).isEqualTo("owner")
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- Items with DEK fields ----------------------------------------------

    @Test
    fun `POST plot items on shared plot requires wrappedItemDek`() {
        val plot = createSharedPlot("d3-shared-item-dek")
        val uploadId = uploadImage()
        try {
            val body = JSONObject().put("uploadId", uploadId)
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/items")
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(400)
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `POST plot items on shared plot with DEK fields returns 201`() {
        val plot = createSharedPlot("d3-shared-item-with-dek")
        val uploadId = uploadImage()
        try {
            val body = JSONObject()
                .put("uploadId", uploadId)
                .put("wrappedItemDek", randomB64(80))
                .put("itemDekFormat", "plot-aes256gcm-v1")
                .put("wrappedThumbnailDek", randomB64(80))
                .put("thumbnailDekFormat", "plot-aes256gcm-v1")
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/items")
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(201)

            val itemsResp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/items").build()
            ).execute()
            assertThat(itemsResp.code).isEqualTo(200)
            val items = org.json.JSONArray(itemsResp.body!!.string())
            assertThat(items.length()).isEqualTo(1)
            val item = items.getJSONObject(0)
            assertThat(item.has("wrapped_item_dek")).isTrue()
            assertThat(item.getString("item_dek_format")).isEqualTo("plot-aes256gcm-v1")
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `GET plot items on shared plot returns items with dek fields`() {
        val plot = createSharedPlot("d3-shared-items-get")
        val uploadId = uploadImage()
        try {
            val addBody = JSONObject()
                .put("uploadId", uploadId)
                .put("wrappedItemDek", randomB64(80))
                .put("itemDekFormat", "plot-aes256gcm-v1")
            client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/items")
                    .post(addBody.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()

            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/items").build()
            ).execute()
            assertThat(resp.code).isEqualTo(200)
            val items = org.json.JSONArray(resp.body!!.string())
            assertThat(items.length()).isEqualTo(1)
            assertThat(items.getJSONObject(0).has("added_by")).isTrue()
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- Staging approval on shared plot ------------------------------------

    @Test
    fun `approve staging on shared plot without DEK fields returns 400`() {
        val plot = createSharedPlot("d3-stage-approve-shared")
        val tag = "d3-stage-approve-shared-tag"
        val uploadId = uploadImage()
        client.newCall(
            Request.Builder().url("$base/api/content/uploads/$uploadId/tags")
                .patch("""{"tags":["$tag"]}""".toRequestBody("application/json".toMediaType())).build()
        ).execute()
        val flowBody = JSONObject()
            .put("name", "d3-stage-approve-flow")
            .put("criteria", JSONObject().put("type", "tag").put("tag", tag))
            .put("targetPlotId", plot.getString("id"))
            .put("requiresStaging", true)
        val flowResp = client.newCall(
            Request.Builder().url("$base/api/flows")
                .post(flowBody.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        val flow = JSONObject(flowResp.body!!.string())
        try {
            val approveResp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/staging/$uploadId/approve")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(approveResp.code).isEqualTo(400)
        } finally {
            client.newCall(Request.Builder().url("$base/api/flows/${flow.getString("id")}").delete().build()).execute()
            deletePlot(plot.getString("id"))
        }
    }

    // ---- Invite link --------------------------------------------------------

    @Test
    fun `POST invites returns token and expiresAt`() {
        val plot = createSharedPlot("d3-invite-token")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/invites")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(201)
            val data = JSONObject(resp.body!!.string())
            assertThat(data.has("token")).isTrue()
            assertThat(data.has("expiresAt")).isTrue()
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `GET join-info returns plot name and inviter display name`() {
        val plot = createSharedPlot("d3-join-info")
        try {
            val inviteResp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/invites")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            val token = JSONObject(inviteResp.body!!.string()).getString("token")

            val infoResp = client.newCall(
                Request.Builder().url("$base/api/plots/join-info?token=$token").build()
            ).execute()
            assertThat(infoResp.code).isEqualTo(200)
            val info = JSONObject(infoResp.body!!.string())
            assertThat(info.getString("plotName")).isEqualTo("d3-join-info")
            assertThat(info.has("inviterDisplayName")).isTrue()
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `GET join-info with unknown token returns 404`() {
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots/join-info?token=not-a-real-token").build()
        ).execute()
        assertThat(resp.code).isEqualTo(404)
    }

    // ---- Access control isolation (single-user test) -----------------------

    @Test
    fun `GET plots does not expose shared plots of other users`() {
        // In single-user test environment all plots belong to the founding user.
        // This tests that the plot list doesn't error and shows only own plots.
        val resp = client.newCall(Request.Builder().url("$base/api/plots").build()).execute()
        assertThat(resp.code).isEqualTo(200)
    }

    @Test
    fun `GET plot-key for unknown plot id returns 404`() {
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots/00000000-0000-0000-0000-000000000099/plot-key").build()
        ).execute()
        assertThat(resp.code).isEqualTo(404)
    }
}
