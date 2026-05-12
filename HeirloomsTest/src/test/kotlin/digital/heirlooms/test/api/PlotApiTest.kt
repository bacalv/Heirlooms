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

    private fun createPlot(name: String, criteria: JSONObject? = null, showInGarden: Boolean = true): JSONObject {
        val body = JSONObject().put("name", name)
        if (criteria != null) body.put("criteria", criteria)
        body.put("show_in_garden", showInGarden)
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

    private fun tagCriteria(vararg tags: String): JSONObject {
        return if (tags.size == 1) {
            JSONObject().put("type", "tag").put("tag", tags[0])
        } else {
            val operands = JSONArray()
            tags.forEach { operands.put(JSONObject().put("type", "tag").put("tag", it)) }
            JSONObject().put("type", "and").put("operands", operands)
        }
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

    @Test
    fun `GET plots response includes criteria show_in_garden and visibility fields`() {
        val p = createPlot("d3-schema-canary", tagCriteria("family"))
        try {
            val plots = listPlots()
            val found = (0 until plots.length()).map { plots.getJSONObject(it) }
                .first { it.getString("id") == p.getString("id") }
            assertThat(found.has("criteria")).isTrue()
            assertThat(found.has("show_in_garden")).isTrue()
            assertThat(found.has("visibility")).isTrue()
            assertThat(found.getString("visibility")).isEqualTo("private")
            assertThat(found.getBoolean("show_in_garden")).isTrue()
        } finally { deletePlot(p.getString("id")) }
    }

    @Test
    fun `GET plots response does not include tag_criteria`() {
        val plots = listPlots()
        (0 until plots.length()).forEach { i ->
            assertThat(plots.getJSONObject(i).has("tag_criteria")).isFalse()
        }
    }

    @Test
    fun `system just_arrived plot has criteria set to just_arrived type`() {
        val plots = listPlots()
        val system = (0 until plots.length()).map { plots.getJSONObject(it) }
            .first { it.getBoolean("is_system_defined") }
        assertThat(system.has("criteria")).isTrue()
        assertThat(system.getJSONObject("criteria").getString("type")).isEqualTo("just_arrived")
    }

    // ---- POST /api/plots with criteria -------------------------------------

    @Test
    fun `POST plots with tag criteria returns 201 and persists criteria`() {
        val criteria = tagCriteria("family")
        val p = createPlot("d3-tag-criteria", criteria)
        try {
            assertThat(p.has("criteria")).isTrue()
            assertThat(p.getJSONObject("criteria").getString("type")).isEqualTo("tag")
            assertThat(p.getJSONObject("criteria").getString("tag")).isEqualTo("family")
        } finally { deletePlot(p.getString("id")) }
    }

    @Test
    fun `POST plots with and criteria persists correctly`() {
        val criteria = tagCriteria("family", "2026")
        val p = createPlot("d3-and-criteria", criteria)
        try {
            assertThat(p.getJSONObject("criteria").getString("type")).isEqualTo("and")
        } finally { deletePlot(p.getString("id")) }
    }

    @Test
    fun `POST plots with media_type criteria returns 201`() {
        val criteria = JSONObject().put("type", "media_type").put("value", "image")
        val p = createPlot("d3-media-criteria", criteria)
        try {
            assertThat(p.getJSONObject("criteria").getString("type")).isEqualTo("media_type")
        } finally { deletePlot(p.getString("id")) }
    }

    @Test
    fun `POST plots with near criteria returns 400`() {
        val criteria = JSONObject().put("type", "near").put("lat", 51.5).put("lng", -0.1).put("radius_metres", 500)
        val body = JSONObject().put("name", "near-test").put("criteria", criteria)
        val resp = client.newCall(
            Request.Builder().url("$base/api/plots")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        ).execute()
        assertThat(resp.code).isEqualTo(400)
    }

    @Test
    fun `POST plots with show_in_garden false creates hidden plot`() {
        val p = createPlot("d3-hidden", showInGarden = false)
        try {
            assertThat(p.getBoolean("show_in_garden")).isFalse()
        } finally { deletePlot(p.getString("id")) }
    }

    // ---- GET /api/content/uploads?plot_id filters by criteria ---------------

    @Test
    fun `GET uploads with just_arrived plot_id returns same as just_arrived=true`() {
        val plots = listPlots()
        val systemPlot = (0 until plots.length()).map { plots.getJSONObject(it) }
            .first { it.getBoolean("is_system_defined") }

        val byPlotId = client.newCall(
            Request.Builder().url("$base/api/content/uploads?plot_id=${systemPlot.getString("id")}").build()
        ).execute()
        assertThat(byPlotId.code).isEqualTo(200)
        val byPlotData = JSONObject(byPlotId.body!!.string())

        val byJustArrived = client.newCall(
            Request.Builder().url("$base/api/content/uploads?just_arrived=true").build()
        ).execute()
        assertThat(byJustArrived.code).isEqualTo(200)
        val byJaData = JSONObject(byJustArrived.body!!.string())

        // Item counts should match — both evaluate the same predicate
        assertThat(byPlotData.getJSONArray("items").length())
            .isEqualTo(byJaData.getJSONArray("items").length())
    }

    @Test
    fun `GET uploads with unknown plot_id returns 404`() {
        val resp = client.newCall(
            Request.Builder().url("$base/api/content/uploads?plot_id=00000000-0000-0000-0000-000000000099").build()
        ).execute()
        assertThat(resp.code).isEqualTo(404)
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
