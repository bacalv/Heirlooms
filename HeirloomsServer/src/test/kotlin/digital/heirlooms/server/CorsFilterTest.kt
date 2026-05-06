package digital.heirlooms.server

import org.http4k.core.Method.GET
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CorsFilterTest {

    private val handler = corsFilter().then { Response(OK).body("ok") }

    @Test
    fun `OPTIONS preflight returns 200 without calling inner handler`() {
        val response = handler(Request(OPTIONS, "/api/content/uploads"))
        assertEquals(OK, response.status)
    }

    @Test
    fun `OPTIONS preflight includes Allow-Origin wildcard`() {
        val response = handler(Request(OPTIONS, "/api/content/uploads"))
        assertEquals("*", response.header("Access-Control-Allow-Origin"))
    }

    @Test
    fun `OPTIONS preflight includes Allow-Headers with X-Api-Key`() {
        val response = handler(Request(OPTIONS, "/api/content/uploads"))
        val header = response.header("Access-Control-Allow-Headers") ?: ""
        assert(header.contains("X-Api-Key")) { "Expected X-Api-Key in Allow-Headers but got: $header" }
    }

    @Test
    fun `OPTIONS preflight includes PATCH in Allow-Methods`() {
        val response = handler(Request(OPTIONS, "/api/content/uploads"))
        val header = response.header("Access-Control-Allow-Methods") ?: ""
        assert(header.contains("PATCH")) { "Expected PATCH in Allow-Methods but got: $header" }
    }

    @Test
    fun `GET response includes Allow-Origin wildcard`() {
        val response = handler(Request(GET, "/health"))
        assertEquals("*", response.header("Access-Control-Allow-Origin"))
    }

    @Test
    fun `GET response preserves inner handler body`() {
        val response = handler(Request(GET, "/health"))
        assertEquals("ok", response.bodyString())
    }
}
