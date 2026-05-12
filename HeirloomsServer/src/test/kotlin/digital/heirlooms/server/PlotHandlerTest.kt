package digital.heirlooms.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PlotHandlerTest {

    private val mockStorage = mockk<FileStore>()
    private val mockDatabase = mockk<Database>()
    private val app = buildApp(mockStorage, mockDatabase)
    private val mapper = ObjectMapper()

    private val plotId = UUID.randomUUID()
    private val systemPlotId = UUID.randomUUID()

    private fun plot(
        id: UUID = plotId,
        name: String = "Summer",
        sortOrder: Int = 0,
        isSystem: Boolean = false,
        criteria: String? = """{"type":"tag","tag":"family"}""",
        showInGarden: Boolean = true,
        visibility: String = "private",
    ) = PlotRecord(
        id = id,
        ownerUserId = null,
        name = name,
        sortOrder = sortOrder,
        isSystemDefined = isSystem,
        createdAt = Instant.parse("2026-05-01T10:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T10:00:00Z"),
        criteria = criteria,
        showInGarden = showInGarden,
        visibility = visibility,
    )

    // ---- GET /api/plots -------------------------------------------------------

    @Test
    fun `GET plots returns 200 with array`() {
        every { mockDatabase.listPlots() } returns listOf(plot())

        val response = app(Request(GET, "/api/plots"))

        assertEquals(OK, response.status)
        val body = mapper.readTree(response.bodyString())
        assertTrue(body.isArray)
        assertEquals(1, body.size())
    }

    @Test
    fun `GET plots includes criteria in response`() {
        val c = """{"type":"tag","tag":"family"}"""
        every { mockDatabase.listPlots() } returns listOf(plot(criteria = c))

        val response = app(Request(GET, "/api/plots"))

        val first = mapper.readTree(response.bodyString()).first()
        assertTrue(first.has("criteria"))
        assertEquals("tag", first["criteria"]["type"].asText())
        assertEquals("family", first["criteria"]["tag"].asText())
    }

    @Test
    fun `GET plots includes null criteria when plot has no criteria`() {
        every { mockDatabase.listPlots() } returns listOf(plot(criteria = null))

        val first = mapper.readTree(app(Request(GET, "/api/plots")).bodyString()).first()
        assertTrue(first.has("criteria"))
        assertTrue(first["criteria"].isNull)
    }

    @Test
    fun `GET plots empty returns empty array`() {
        every { mockDatabase.listPlots() } returns emptyList()

        val body = mapper.readTree(app(Request(GET, "/api/plots")).bodyString())
        assertEquals(0, body.size())
    }

    // ---- POST /api/plots ------------------------------------------------------

    @Test
    fun `POST plots creates a plot and returns 201`() {
        every { mockDatabase.createPlot(any(), any(), any(), any(), any()) } returns plot()

        val response = app(
            Request(POST, "/api/plots").body("""{"name":"Summer"}""")
        )

        assertEquals(CREATED, response.status)
        val body = mapper.readTree(response.bodyString())
        assertEquals("Summer", body["name"].asText())
    }

    @Test
    fun `POST plots with criteria creates plot`() {
        justRun { mockDatabase.withCriteriaValidation(any(), any()) }
        every { mockDatabase.createPlot(any(), any(), any(), any(), any()) } returns plot()

        val response = app(
            Request(POST, "/api/plots").body("""{"name":"Test","criteria":{"type":"tag","tag":"family"}}""")
        )
        assertEquals(CREATED, response.status)
    }

    @Test
    fun `POST plots without name returns 400`() {
        val response = app(Request(POST, "/api/plots").body("""{}"""))
        assertEquals(BAD_REQUEST, response.status)
    }

    @Test
    fun `POST plots with invalid JSON returns 400`() {
        val response = app(Request(POST, "/api/plots").body("not-json"))
        assertEquals(BAD_REQUEST, response.status)
    }

    // ---- PUT /api/plots/:id ---------------------------------------------------

    @Test
    fun `PUT plots updates and returns 200`() {
        every { mockDatabase.updatePlot(plotId, any(), any(), any(), any(), any()) } returns
            Database.PlotUpdateResult.Success(plot(name = "Winter"))

        val response = app(
            Request(PUT, "/api/plots/$plotId").body("""{"name":"Winter"}""")
        )

        assertEquals(OK, response.status)
        val body = mapper.readTree(response.bodyString())
        assertEquals("Winter", body["name"].asText())
    }

    @Test
    fun `PUT system-defined plot returns 403`() {
        every { mockDatabase.updatePlot(systemPlotId, any(), any(), any(), any(), any()) } returns
            Database.PlotUpdateResult.SystemDefined

        val response = app(
            Request(PUT, "/api/plots/$systemPlotId").body("""{"name":"New name"}""")
        )

        assertEquals(FORBIDDEN, response.status)
    }

    @Test
    fun `PUT unknown plot returns 404`() {
        every { mockDatabase.updatePlot(plotId, any(), any(), any(), any(), any()) } returns
            Database.PlotUpdateResult.NotFound

        val response = app(Request(PUT, "/api/plots/$plotId").body("""{"name":"X"}"""))
        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `PUT plot with invalid JSON returns 400`() {
        val response = app(Request(PUT, "/api/plots/$plotId").body("not-json"))
        assertEquals(BAD_REQUEST, response.status)
    }

    // ---- DELETE /api/plots/:id ------------------------------------------------

    @Test
    fun `DELETE plot returns 204`() {
        every { mockDatabase.deletePlot(plotId) } returns Database.PlotDeleteResult.Success

        val response = app(Request(DELETE, "/api/plots/$plotId"))
        assertEquals(NO_CONTENT, response.status)
    }

    @Test
    fun `DELETE system-defined plot returns 403`() {
        every { mockDatabase.deletePlot(systemPlotId) } returns Database.PlotDeleteResult.SystemDefined

        val response = app(Request(DELETE, "/api/plots/$systemPlotId"))
        assertEquals(FORBIDDEN, response.status)
    }

    @Test
    fun `DELETE unknown plot returns 404`() {
        every { mockDatabase.deletePlot(plotId) } returns Database.PlotDeleteResult.NotFound

        val response = app(Request(DELETE, "/api/plots/$plotId"))
        assertEquals(NOT_FOUND, response.status)
    }

    // ---- JSON shape ----------------------------------------------------------

    @Test
    fun `plot JSON includes all required fields`() {
        every { mockDatabase.listPlots() } returns listOf(plot())

        val body = mapper.readTree(app(Request(GET, "/api/plots")).bodyString()).first()
        assertTrue(body.has("id"))
        assertTrue(body.has("name"))
        assertTrue(body.has("sort_order"))
        assertTrue(body.has("is_system_defined"))
        assertFalse(body["is_system_defined"].asBoolean())
        assertTrue(body.has("criteria"))
        assertTrue(body.has("show_in_garden"))
        assertTrue(body.has("visibility"))
        assertTrue(body.has("created_at"))
        assertTrue(body.has("updated_at"))
    }

    @Test
    fun `plot JSON does not include tag_criteria`() {
        every { mockDatabase.listPlots() } returns listOf(plot())

        val body = mapper.readTree(app(Request(GET, "/api/plots")).bodyString()).first()
        assertFalse(body.has("tag_criteria"))
    }
}
