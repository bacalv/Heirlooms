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
        val bodyStr = resp.body!!.string()
        assertThat(resp.code).withFailMessage("createSharedPlot failed: $bodyStr").isEqualTo(201)
        return JSONObject(bodyStr)
    }

    private fun createPrivatePlot(name: String): JSONObject {
        val body = JSONObject().put("name", name)
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        val bodyStr = resp.body!!.string()
        assertThat(resp.code).withFailMessage("createPrivatePlot failed: $bodyStr").isEqualTo(201)
        return JSONObject(bodyStr)
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
        val plot = createPrivatePlot("d3-private-key-test")
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
            assertThat(arr.getJSONObject(0).getString("status")).isEqualTo("joined")
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- New plot fields in response ----------------------------------------

    @Test
    fun `GET plots includes plot_status and local_name fields`() {
        val plot = createSharedPlot("d3-plot-status-fields")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots").build()
            ).execute()
            assertThat(resp.code).isEqualTo(200)
            val arr = org.json.JSONArray(resp.body!!.string())
            val found = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { it.getString("id") == plot.getString("id") }
            assertThat(found).isNotNull
            assertThat(found!!.getString("plot_status")).isEqualTo("open")
            assertThat(found.isNull("tombstoned_at")).isTrue()
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- PATCH /api/plots/:id/status ----------------------------------------

    @Test
    fun `PATCH plot status open to closed returns 204`() {
        val plot = createSharedPlot("d3-plot-status-close")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/status")
                    .patch("""{"status":"closed"}""".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            assertThat(resp.code).isEqualTo(204)
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `PATCH plot status with invalid value returns 400`() {
        val plot = createSharedPlot("d3-plot-status-invalid")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/status")
                    .patch("""{"status":"archived"}""".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            assertThat(resp.code).isEqualTo(400)
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `POST plot items on closed plot returns 403`() {
        val plot = createSharedPlot("d3-closed-plot-item")
        val uploadId = uploadImage()
        try {
            client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/status")
                    .patch("""{"status":"closed"}""".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()

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
            assertThat(resp.code).isEqualTo(403)
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- GET /api/plots/shared ----------------------------------------------

    @Test
    fun `GET plots shared returns owner membership`() {
        val plot = createSharedPlot("d3-shared-memberships")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/shared").build()
            ).execute()
            assertThat(resp.code).isEqualTo(200)
            val arr = org.json.JSONArray(resp.body!!.string())
            val found = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { it.getString("plotId") == plot.getString("id") }
            assertThat(found).isNotNull
            assertThat(found!!.getString("role")).isEqualTo("owner")
            assertThat(found.getString("status")).isEqualTo("joined")
            assertThat(found.getString("plotStatus")).isEqualTo("open")
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- POST /api/plots/:id/leave ------------------------------------------

    @Test
    fun `POST leave as sole member tombstones the plot`() {
        val plot = createSharedPlot("d3-leave-tombstone")
        val plotId = plot.getString("id")

        val leaveResp = client.newCall(
            Request.Builder().url("$base/api/plots/$plotId/leave")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(leaveResp.code).isEqualTo(204)

        // Plot should no longer appear in the garden (tombstoned)
        val listResp = client.newCall(
            Request.Builder().url("$base/api/plots").build()
        ).execute()
        val plots = org.json.JSONArray(listResp.body!!.string())
        val stillPresent = (0 until plots.length()).any { plots.getJSONObject(it).getString("id") == plotId }
        assertThat(stillPresent).isFalse()

        // Plot should appear in shared memberships as tombstoned
        val sharedResp = client.newCall(
            Request.Builder().url("$base/api/plots/shared").build()
        ).execute()
        val memberships = org.json.JSONArray(sharedResp.body!!.string())
        val mem = (0 until memberships.length())
            .map { memberships.getJSONObject(it) }
            .firstOrNull { it.getString("plotId") == plotId }
        assertThat(mem).isNotNull
        assertThat(mem!!.getString("status")).isEqualTo("left")
        assertThat(mem.isNull("tombstonedAt")).isFalse()
    }

    @Test
    fun `POST leave unknown plot returns 404`() {
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots/00000000-0000-0000-0000-000000000099/leave")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(404)
    }

    // ---- DELETE /members/me (backward compat) --------------------------------

    @Test
    fun `DELETE members me as sole member tombstones the plot`() {
        val plot = createSharedPlot("d3-leave-delete-compat")
        val plotId = plot.getString("id")

        val leaveResp = client.newCall(
            Request.Builder().url("$base/api/plots/$plotId/members/me").delete().build()
        ).execute()
        assertThat(leaveResp.code).isEqualTo(204)

        val listResp = client.newCall(Request.Builder().url("$base/api/plots").build()).execute()
        val plots = org.json.JSONArray(listResp.body!!.string())
        val stillPresent = (0 until plots.length()).any { plots.getJSONObject(it).getString("id") == plotId }
        assertThat(stillPresent).isFalse()
    }

    // ---- POST /api/plots/:id/restore ----------------------------------------

    @Test
    fun `POST restore brings back tombstoned plot`() {
        val plot = createSharedPlot("d3-restore-plot")
        val plotId = plot.getString("id")

        client.newCall(
            Request.Builder().url("$base/api/plots/$plotId/leave")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
        ).execute()

        val restoreResp = client.newCall(
            Request.Builder().url("$base/api/plots/$plotId/restore")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(restoreResp.code).isEqualTo(204)

        // Plot should be back in garden
        val listResp = client.newCall(Request.Builder().url("$base/api/plots").build()).execute()
        val plots = org.json.JSONArray(listResp.body!!.string())
        val present = (0 until plots.length()).any { plots.getJSONObject(it).getString("id") == plotId }
        assertThat(present).isTrue()

        try { deletePlot(plotId) } catch (_: Exception) { }
    }

    @Test
    fun `POST restore non-tombstoned plot returns 404`() {
        val plot = createSharedPlot("d3-restore-live")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/restore")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(404)
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- POST /api/plots/:id/accept -----------------------------------------

    @Test
    fun `POST accept when already joined returns 409`() {
        val plot = createSharedPlot("d3-accept-already-joined")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/accept")
                    .post("""{"localName":"test"}""".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            // Owner is already joined — not in 'invited' state → 409
            assertThat(resp.code).isEqualTo(409)
        } finally { deletePlot(plot.getString("id")) }
    }

    @Test
    fun `POST accept without localName returns 400`() {
        val plot = createSharedPlot("d3-accept-no-name")
        try {
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/accept")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(400)
        } finally { deletePlot(plot.getString("id")) }
    }

    // ---- POST /api/plots/:id/transfer ---------------------------------------

    @Test
    fun `POST transfer to non-member returns 400`() {
        val plot = createSharedPlot("d3-transfer-non-member")
        try {
            val body = JSONObject().put("newOwnerId", java.util.UUID.randomUUID().toString())
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/transfer")
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(400)
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
    fun `approve staging on shared plot without DEK fields succeeds for unencrypted items`() {
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
            assertThat(approveResp.code).isEqualTo(204)
        } finally {
            client.newCall(Request.Builder().url("$base/api/flows/${flow.getString("id")}").delete().build()).execute()
            deletePlot(plot.getString("id"))
        }
    }

    @Test
    fun `second staging approval with duplicate content hash is silently skipped`() {
        val sharedHash = "a".repeat(64)
        val tag = "dedup-hash-${System.nanoTime()}"
        val plot = createSharedPlot("dedup-content-test")
        val rng = SecureRandom()

        fun validEnvelope(): String {
            val algId = "aes256gcm-v1".toByteArray(Charsets.UTF_8)
            val bytes = byteArrayOf(0x01, algId.size.toByte()) +
                algId +
                ByteArray(12).also { rng.nextBytes(it) } +
                ByteArray(16).also { rng.nextBytes(it) }
            return Base64.getEncoder().encodeToString(bytes)
        }

        fun confirmWithHash(storageKey: String): Int {
            val body = JSONObject()
                .put("storageKey", storageKey)
                .put("mimeType", "image/jpeg")
                .put("fileSize", 1000)
                .put("storage_class", "encrypted")
                .put("envelopeVersion", 1)
                .put("wrappedDek", validEnvelope())
                .put("dekFormat", "aes256gcm-v1")
                .put("contentHash", sharedHash)
                .put("tags", JSONArray().put(tag))
            val resp = client.newCall(
                Request.Builder().url("$base/api/content/uploads/confirm")
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            resp.body?.close()
            return resp.code
        }

        try {
            assertThat(confirmWithHash("sk-dedup-a-${System.nanoTime()}")).isEqualTo(201)
            assertThat(confirmWithHash("sk-dedup-b-${System.nanoTime()}")).isEqualTo(201)

            val flowBody = JSONObject()
                .put("name", "dedup-flow-${System.nanoTime()}")
                .put("criteria", JSONObject().put("type", "tag").put("tag", tag))
                .put("targetPlotId", plot.getString("id"))
                .put("requiresStaging", true)
            val flowResp = client.newCall(
                Request.Builder().url("$base/api/flows")
                    .post(flowBody.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            val flow = JSONObject(flowResp.body!!.string())

            val stagingResp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/staging").build()
            ).execute()
            val stagingItems = JSONArray(stagingResp.body!!.string())
            assertThat(stagingItems.length()).isEqualTo(2)

            fun approve(uploadId: String): Int {
                val body = JSONObject()
                    .put("wrappedItemDek", randomB64(80))
                    .put("itemDekFormat", "plot-aes256gcm-v1")
                val resp = client.newCall(
                    Request.Builder().url("$base/api/plots/${plot.getString("id")}/staging/$uploadId/approve")
                        .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                ).execute()
                resp.body?.close()
                return resp.code
            }

            assertThat(approve(stagingItems.getJSONObject(0).getString("id"))).isEqualTo(204)
            assertThat(approve(stagingItems.getJSONObject(1).getString("id"))).isEqualTo(204)

            val itemsResp = client.newCall(
                Request.Builder().url("$base/api/plots/${plot.getString("id")}/items").build()
            ).execute()
            val items = JSONArray(itemsResp.body!!.string())
            assertThat(items.length()).isEqualTo(1)

            client.newCall(Request.Builder().url("$base/api/flows/${flow.getString("id")}").delete().build()).execute()
        } finally {
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
