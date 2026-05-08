package digital.heirlooms.server

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class PaginationTest {

    private val mockStorage = mockk<FileStore>()
    private val mockDatabase = mockk<Database>()
    private val app = buildApp(mockStorage, mockDatabase)
    private val mapper = ObjectMapper()

    private fun upload(id: String = UUID.randomUUID().toString()) = UploadRecord(
        id = UUID.fromString(id),
        storageKey = "$id.jpg",
        mimeType = "image/jpeg",
        fileSize = 1024,
        uploadedAt = Instant.parse("2026-05-01T10:00:00Z"),
    )

    // ---- active list ----------------------------------------------------------

    @Test
    fun `GET uploads returns items and null next_cursor on last page`() {
        every { mockDatabase.listUploadsPaginated(any(), any(), any(), any()) } returns
            UploadPage(listOf(upload()), null)
        every { mockDatabase.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        val body = mapper.readTree(response.bodyString())
        assertTrue(body.has("items"))
        assertTrue(body.has("next_cursor"))
        assertTrue(body["items"].isArray)
        assertTrue(body["next_cursor"].isNull)
    }

    @Test
    fun `GET uploads returns next_cursor when more pages exist`() {
        every { mockDatabase.listUploadsPaginated(any(), any(), any(), any()) } returns
            UploadPage(listOf(upload()), "some-cursor-value")
        every { mockDatabase.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        val body = mapper.readTree(response.bodyString())
        assertEquals("some-cursor-value", body["next_cursor"].asText())
    }

    @Test
    fun `GET uploads with cursor param passes it to database`() {
        val cursorSlot = slot<String?>()
        every { mockDatabase.listUploadsPaginated(captureNullable(cursorSlot), any(), any(), any()) } returns
            UploadPage(emptyList(), null)
        every { mockDatabase.fetchExpiredCompostedUploads() } returns emptyList()

        app(Request(GET, "/api/content/uploads?cursor=abc123"))

        assertEquals("abc123", cursorSlot.captured)
    }

    @Test
    fun `GET uploads with limit param passes it to database`() {
        val limitSlot = slot<Int>()
        every { mockDatabase.listUploadsPaginated(any(), capture(limitSlot), any(), any()) } returns
            UploadPage(emptyList(), null)
        every { mockDatabase.fetchExpiredCompostedUploads() } returns emptyList()

        app(Request(GET, "/api/content/uploads?limit=10"))

        assertEquals(10, limitSlot.captured)
    }

    @Test
    fun `GET uploads limit is clamped to 200`() {
        val limitSlot = slot<Int>()
        every { mockDatabase.listUploadsPaginated(any(), capture(limitSlot), any(), any()) } returns
            UploadPage(emptyList(), null)
        every { mockDatabase.fetchExpiredCompostedUploads() } returns emptyList()

        app(Request(GET, "/api/content/uploads?limit=9999"))

        assertEquals(200, limitSlot.captured)
    }

    @Test
    fun `GET uploads returns multiple items`() {
        val uploads = listOf(upload(), upload(), upload())
        every { mockDatabase.listUploadsPaginated(any(), any(), any(), any()) } returns
            UploadPage(uploads, null)
        every { mockDatabase.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        val body = mapper.readTree(response.bodyString())
        assertEquals(3, body["items"].size())
    }

    // ---- composted list -------------------------------------------------------

    @Test
    fun `GET uploads composted returns items and null next_cursor`() {
        every { mockDatabase.listCompostedUploadsPaginated(any(), any()) } returns
            UploadPage(emptyList(), null)

        val response = app(Request(GET, "/api/content/uploads/composted"))

        assertEquals(OK, response.status)
        val body = mapper.readTree(response.bodyString())
        assertTrue(body.has("items"))
        assertTrue(body["next_cursor"].isNull)
    }

    @Test
    fun `GET uploads composted empty state returns empty items array`() {
        every { mockDatabase.listCompostedUploadsPaginated(any(), any()) } returns
            UploadPage(emptyList(), null)

        val response = app(Request(GET, "/api/content/uploads/composted"))

        val body = mapper.readTree(response.bodyString())
        assertEquals(0, body["items"].size())
    }
}
