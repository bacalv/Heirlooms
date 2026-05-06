package digital.heirlooms.server

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NOT_IMPLEMENTED
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UploadHandlerTest {

    private val mockStorage = mockk<FileStore>()
    private val mockDatabase = mockk<Database>()
    private val app = buildApp(mockStorage, mockDatabase)

    // Stub the database record call used by the upload handler
    private fun stubDatabase() {
        every { mockDatabase.recordUpload(any()) } just runs
    }

    // -------------------------------------------------------------------------
    // Success cases
    // -------------------------------------------------------------------------

    @Test
    fun `POST with jpeg body returns 201`() {
        stubDatabase()
        every { mockStorage.save(any(), any()) } returns StorageKey("some-uuid.jpg")

        val response = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("fake-image-bytes")
        )

        assertEquals(CREATED, response.status)
    }

    @Test
    fun `response body contains the storage key`() {
        stubDatabase()
        every { mockStorage.save(any(), any()) } returns StorageKey("some-uuid.jpg")

        val response = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("fake-image-bytes")
        )

        assertTrue(response.bodyString().contains("some-uuid.jpg"))
    }

    @Test
    fun `storage is called with the correct MIME type`() {
        stubDatabase()
        val capturedMime = slot<String>()
        every { mockStorage.save(any(), capture(capturedMime)) } returns StorageKey("uuid.mp4")

        app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "video/mp4")
                .body("fake-video-bytes")
        )

        assertEquals("video/mp4", capturedMime.captured)
    }

    @Test
    fun `storage is called with the correct bytes`() {
        stubDatabase()
        val capturedBytes = slot<ByteArray>()
        every { mockStorage.save(capture(capturedBytes), any()) } returns StorageKey("uuid.jpg")

        val payload = "exact-payload"
        app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body(payload)
        )

        assertEquals(payload, String(capturedBytes.captured))
    }

    @Test
    fun `Content-Type with charset parameter passes clean MIME type to storage`() {
        stubDatabase()
        val capturedMime = slot<String>()
        every { mockStorage.save(any(), capture(capturedMime)) } returns StorageKey("uuid.jpg")

        app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg; charset=utf-8")
                .body("bytes")
        )

        assertEquals("image/jpeg", capturedMime.captured)
    }

    @Test
    fun `missing Content-Type header defaults to octet-stream`() {
        stubDatabase()
        val capturedMime = slot<String>()
        every { mockStorage.save(any(), capture(capturedMime)) } returns StorageKey("uuid.bin")

        app(Request(POST, "/api/content/upload").body("bytes"))

        assertEquals("application/octet-stream", capturedMime.captured)
    }

    @Test
    fun `upload records metadata to the database`() {
        val capturedRecord = slot<UploadRecord>()
        every { mockStorage.save(any(), any()) } returns StorageKey("some-uuid.jpg")
        every { mockDatabase.recordUpload(capture(capturedRecord)) } just runs

        app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("bytes")
        )

        assertEquals("some-uuid.jpg", capturedRecord.captured.storageKey)
        assertEquals("image/jpeg", capturedRecord.captured.mimeType)
    }

    // -------------------------------------------------------------------------
    // Failure cases
    // -------------------------------------------------------------------------

    @Test
    fun `POST with empty body returns 400`() {
        val response = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
        )

        assertEquals(BAD_REQUEST, response.status)
        verify(exactly = 0) { mockStorage.save(any(), any()) }
        verify(exactly = 0) { mockDatabase.recordUpload(any()) }
    }

    @Test
    fun `storage exception returns 500`() {
        every { mockStorage.save(any(), any()) } throws RuntimeException("disk full")

        val response = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("bytes")
        )

        assertEquals(INTERNAL_SERVER_ERROR, response.status)
        assertTrue(response.bodyString().contains("disk full"))
    }

    // -------------------------------------------------------------------------
    // List endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `GET uploads returns 200 with JSON`() {
        every { mockDatabase.listUploads() } returns listOf(
            UploadRecord(UUID.randomUUID(), "some-uuid.jpg", "image/jpeg", 1024),
        )

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.header("Content-Type")!!.contains("application/json"))
        assertTrue(response.bodyString().contains("some-uuid.jpg"))
    }

    @Test
    fun `GET uploads returns empty array when no uploads`() {
        every { mockDatabase.listUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertEquals("[]", response.bodyString())
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    @Test
    fun `health endpoint returns 200`() {
        assertEquals(OK, app(Request(GET, "/health")).status)
    }

    @Test
    fun `GET to upload endpoint returns 404`() {
        assertEquals(NOT_FOUND, app(Request(GET, "/api/content/upload")).status)
    }

    @Test
    fun `unknown path returns 404`() {
        assertEquals(NOT_FOUND, app(Request(POST, "/api/other")).status)
    }

    // -------------------------------------------------------------------------
    // File proxy endpoint
    // -------------------------------------------------------------------------

    private val knownId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val knownRecord = UploadRecord(knownId, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg", "image/jpeg", 512, Instant.EPOCH)

    @Test
    fun `GET uploads id file returns 200 with file bytes`() {
        every { mockDatabase.getUploadById(knownId) } returns knownRecord
        every { mockStorage.get(StorageKey(knownRecord.storageKey)) } returns byteArrayOf(1, 2, 3)

        val response = app(Request(GET, "/api/content/uploads/$knownId/file"))

        assertEquals(OK, response.status)
        assertArrayEquals(byteArrayOf(1, 2, 3), response.body.payload.array())
    }

    @Test
    fun `GET uploads id file sets correct Content-Type`() {
        every { mockDatabase.getUploadById(knownId) } returns knownRecord
        every { mockStorage.get(StorageKey(knownRecord.storageKey)) } returns ByteArray(0)

        val response = app(Request(GET, "/api/content/uploads/$knownId/file"))

        assertEquals("image/jpeg", response.header("Content-Type"))
    }

    @Test
    fun `GET uploads id file returns 404 when upload not found`() {
        every { mockDatabase.getUploadById(knownId) } returns null

        val response = app(Request(GET, "/api/content/uploads/$knownId/file"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `GET uploads id file returns 404 for malformed UUID`() {
        val response = app(Request(GET, "/api/content/uploads/not-a-uuid/file"))
        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `GET uploads id file returns 500 when storage throws`() {
        every { mockDatabase.getUploadById(knownId) } returns knownRecord
        every { mockStorage.get(any()) } throws RuntimeException("GCS error")

        val response = app(Request(GET, "/api/content/uploads/$knownId/file"))

        assertEquals(INTERNAL_SERVER_ERROR, response.status)
        assertTrue(response.bodyString().contains("GCS error"))
    }

    // -------------------------------------------------------------------------
    // Read URL endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `GET uploads id url returns signed URL for known upload`() {
        every { mockDatabase.getUploadById(knownId) } returns knownRecord
        every { (mockDirectStorage as DirectUploadSupport).generateReadUrl(StorageKey(knownRecord.storageKey)) } returns "https://gcs.example.com/signed-read"

        val response = appWithDirectUpload(Request(GET, "/api/content/uploads/$knownId/url"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("https://gcs.example.com/signed-read"))
    }

    @Test
    fun `GET uploads id url returns 404 when upload not found`() {
        every { mockDatabase.getUploadById(knownId) } returns null

        val response = appWithDirectUpload(Request(GET, "/api/content/uploads/$knownId/url"))

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `GET uploads id url returns 501 when storage does not support signed URLs`() {
        val response = app(Request(GET, "/api/content/uploads/$knownId/url"))
        assertEquals(NOT_IMPLEMENTED, response.status)
    }

    // -------------------------------------------------------------------------
    // Prepare endpoint — storage supports direct upload
    // -------------------------------------------------------------------------

    private val mockDirectStorage = mockk<FileStore>(moreInterfaces = arrayOf(DirectUploadSupport::class))
    private val appWithDirectUpload = buildApp(mockDirectStorage, mockDatabase)

    @Test
    fun `POST uploads prepare returns 200 with storageKey and uploadUrl`() {
        every { (mockDirectStorage as DirectUploadSupport).prepareUpload("video/mp4") } returns
            PreparedUpload(StorageKey("uuid.mp4"), "https://gcs.example.com/signed")

        val response = appWithDirectUpload(
            Request(POST, "/api/content/uploads/prepare")
                .header("Content-Type", "application/json")
                .body("""{"mimeType":"video/mp4"}""")
        )

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("uuid.mp4"))
        assertTrue(response.bodyString().contains("https://gcs.example.com/signed"))
    }

    @Test
    fun `POST uploads prepare returns 400 when mimeType missing`() {
        val response = appWithDirectUpload(
            Request(POST, "/api/content/uploads/prepare")
                .header("Content-Type", "application/json")
                .body("""{}""")
        )
        assertEquals(BAD_REQUEST, response.status)
    }

    @Test
    fun `POST uploads prepare returns 501 when storage does not support direct upload`() {
        val response = app(
            Request(POST, "/api/content/uploads/prepare")
                .header("Content-Type", "application/json")
                .body("""{"mimeType":"video/mp4"}""")
        )
        assertEquals(NOT_IMPLEMENTED, response.status)
    }

    // -------------------------------------------------------------------------
    // Confirm endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `POST uploads confirm records upload and returns 201`() {
        val capturedRecord = slot<UploadRecord>()
        every { mockDatabase.recordUpload(capture(capturedRecord)) } just runs

        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uuid.mp4","mimeType":"video/mp4","fileSize":34000000}""")
        )

        assertEquals(CREATED, response.status)
        assertEquals("uuid.mp4", capturedRecord.captured.storageKey)
        assertEquals("video/mp4", capturedRecord.captured.mimeType)
        assertEquals(34000000L, capturedRecord.captured.fileSize)
    }

    @Test
    fun `POST uploads confirm returns 400 when body is incomplete`() {
        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uuid.mp4"}""")
        )
        assertEquals(BAD_REQUEST, response.status)
    }
}
