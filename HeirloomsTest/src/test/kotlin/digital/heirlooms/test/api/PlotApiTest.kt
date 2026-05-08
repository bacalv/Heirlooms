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
class PlotApiTest {

    private val base get() = HeirloomsTestEnvironment.baseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient

    private fun listPlots(): JSONArray {
        val resp = client.newCall(Request.Builder().url("$base/api/plots").get().build()).execute()
        assertThat(resp.code).isEqualTo(200)
        return JSONArray(resp.body!!.string())
    }

    private fun createPlot(name: String, tagCriteria: List<String> = emptyList()): JSONObject {
        val body = JSONObject().put("name", name).put("tag_criteria", JSONArray(tagCriteria))
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(201)
        return JSONObject(resp.body!!.string())
    }

    private fun deletePlot(id: String): Int {
        val resp = client.newCall(Request.Builder().url("$base/api/plots/$id").delete().build()).execute()
        return resp.code
    }

    // ---- GET /api/plots ----------------------------------------------------

    @Test
    fun `GET plots includes the system Just arrived plot`() {
        val plots = listPlots()
        val names = (0 until plots.length()).map { plots.getJSONObject(it).getString("name") }
        assertThat(names).contains("__just_arrived__")
    }

    @Test
    fun `GET plots orders system plot first (sort_order -1000)`() {
        val plots = listPlots()
        assertThat(plots.length()).isGreaterThanOrEqualTo(1)
        val first = plots.getJSONObject(0)
        assertThat(first.getBoolean("is_system_defined")).isTrue()
    }

    // ---- PATCH /api/plots (batch reorder) ----------------------------------

    @Test
    fun `PATCH plots reorders user plots atomically`() {
        val p1 = createPlot("d3-reorder-alpha")
        val p2 = createPlot("d3-reorder-beta")
        try {
            val reorderBody = JSONArray()
                .put(JSONObject().put("id", p1.getString("id")).put("sort_order", 10))
                .put(JSONObject().put("id", p2.getString("id")).put("sort_order", 5))
            val resp = client.newCall(
                Request.Builder().url("$base/api/plots")
                    .patch(reorderBody.toString().toRequestBody("application/json".toMediaType())).build()
            ).execute()
            assertThat(resp.code).isEqualTo(204)

            // Verify new ordering
            val plots = listPlots()
            val userPlots = (0 until plots.length())
                .map { plots.getJSONObject(it) }
                .filter { !it.getBoolean("is_system_defined") }
            val betaIdx = userPlots.indexOfFirst { it.getString("id") == p2.getString("id") }
            val alphaIdx = userPlots.indexOfFirst { it.getString("id") == p1.getString("id") }
            assertThat(betaIdx).isLessThan(alphaIdx)
        } finally {
            deletePlot(p1.getString("id"))
            deletePlot(p2.getString("id"))
        }
    }

    @Test
    fun `PATCH plots returns 403 when attempting to reorder system plot`() {
        val plots = listPlots()
        val systemPlot = (0 until plots.length())
            .map { plots.getJSONObject(it) }
            .first { it.getBoolean("is_system_defined") }

        val reorderBody = JSONArray()
            .put(JSONObject().put("id", systemPlot.getString("id")).put("sort_order", 99))
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .patch(reorderBody.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(403)
    }

    @Test
    fun `PATCH plots returns 400 on invalid body`() {
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .patch("not-json".toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(400)
    }

    @Test
    fun `PATCH plots returns 404 on unknown plot id`() {
        val reorderBody = JSONArray()
            .put(JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("sort_order", 0))
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .patch(reorderBody.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(404)
    }
}
