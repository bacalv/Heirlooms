package digital.heirlooms.app

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UploaderTest {

    private lateinit var server: MockWebServer

    // Fast uploader: no real delays between retries in tests
    private lateinit var uploader: Uploader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        uploader = Uploader(
            httpClient = OkHttpClient(),
            maxAttempts = 3,
            initialDelayMs = 0L,   // No sleep in tests
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -------------------------------------------------------------------------
    // isValidEndpoint
    // -------------------------------------------------------------------------

    @Test
    fun `isValidEndpoint returns true for https URL`() {
        assertTrue(Uploader.isValidEndpoint("https://example.com/upload"))
    }

    @Test
    fun `isValidEndpoint returns true for http URL`() {
        assertTrue(Uploader.isValidEndpoint("http://localhost:8080/upload"))
    }

    @Test
    fun `isValidEndpoint returns false for null`() {
        assertFalse(Uploader.isValidEndpoint(null))
    }

    @Test
    fun `isValidEndpoint returns false for empty string`() {
        assertFalse(Uploader.isValidEndpoint(""))
    }

    @Test
    fun `isValidEndpoint returns false for whitespace only`() {
        assertFalse(Uploader.isValidEndpoint("   "))
    }

    @Test
    fun `isValidEndpoint returns false when scheme is missing`() {
        assertFalse(Uploader.isValidEndpoint("example.com/upload"))
    }

    @Test
    fun `isValidEndpoint returns false for ftp scheme`() {
        assertFalse(Uploader.isValidEndpoint("ftp://example.com/upload"))
    }

    // -------------------------------------------------------------------------
    // resolveMimeType
    // -------------------------------------------------------------------------

    @Test
    fun `resolveMimeType returns image-jpeg unchanged`() {
        assertEquals("image/jpeg", Uploader.resolveMimeType("image/jpeg"))
    }

    @Test
    fun `resolveMimeType returns video-mp4 unchanged`() {
        assertEquals("video/mp4", Uploader.resolveMimeType("video/mp4"))
    }

    @Test
    fun `resolveMimeType falls back to octet-stream for null`() {
        assertEquals("application/octet-stream", Uploader.resolveMimeType(null))
    }

    @Test
    fun `resolveMimeType falls back to octet-stream for empty string`() {
        assertEquals("application/octet-stream", Uploader.resolveMimeType(""))
    }

    @Test
    fun `resolveMimeType falls back to octet-stream for whitespace`() {
        assertEquals("application/octet-stream", Uploader.resolveMimeType("   "))
    }

    @Test
    fun `resolveMimeType trims trailing whitespace`() {
        assertEquals("image/png", Uploader.resolveMimeType("image/png  "))
    }

    // -------------------------------------------------------------------------
    // isRetryable
    // -------------------------------------------------------------------------

    @Test
    fun `isRetryable returns false for Success`() {
        assertFalse(Uploader.isRetryable(Uploader.UploadResult.Success("ok", 200, 1)))
    }

    @Test
    fun `isRetryable returns true for network error`() {
        assertTrue(Uploader.isRetryable(Uploader.UploadResult.Failure("error", Uploader.NO_HTTP_CODE)))
    }

    @Test
    fun `isRetryable returns true for HTTP 500`() {
        assertTrue(Uploader.isRetryable(Uploader.UploadResult.Failure("error", 500)))
    }

    @Test
    fun `isRetryable returns true for HTTP 503`() {
        assertTrue(Uploader.isRetryable(Uploader.UploadResult.Failure("error", 503)))
    }

    @Test
    fun `isRetryable returns false for HTTP 400`() {
        assertFalse(Uploader.isRetryable(Uploader.UploadResult.Failure("error", 400)))
    }

    @Test
    fun `isRetryable returns false for HTTP 401`() {
        assertFalse(Uploader.isRetryable(Uploader.UploadResult.Failure("error", 401)))
    }

    @Test
    fun `isRetryable returns false for HTTP 404`() {
        assertFalse(Uploader.isRetryable(Uploader.UploadResult.Failure("error", 404)))
    }

    // -------------------------------------------------------------------------
    // upload — input validation (no network required)
    // -------------------------------------------------------------------------

    @Test
    fun `upload fails with null endpoint without making any network call`() {
        val result = uploader.upload(null, byteArrayOf(1, 2, 3), "image/jpeg")
        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(Uploader.NO_HTTP_CODE, (result as Uploader.UploadResult.Failure).httpCode)
        assertTrue(result.message.contains("endpoint"))
    }

    @Test
    fun `upload fails with empty endpoint without making any network call`() {
        val result = uploader.upload("", byteArrayOf(1, 2, 3), "image/jpeg")
        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(Uploader.NO_HTTP_CODE, (result as Uploader.UploadResult.Failure).httpCode)
    }

    @Test
    fun `upload fails with empty byte array without making any network call`() {
        val result = uploader.upload(server.url("/upload").toString(), byteArrayOf(), "image/jpeg")
        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(Uploader.NO_HTTP_CODE, (result as Uploader.UploadResult.Failure).httpCode)
        assertTrue(result.message.lowercase().contains("empty"))
    }

    @Test
    fun `upload fails with null byte array without making any network call`() {
        val result = uploader.upload(server.url("/upload").toString(), null, "image/jpeg")
        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(Uploader.NO_HTTP_CODE, (result as Uploader.UploadResult.Failure).httpCode)
    }

    // -------------------------------------------------------------------------
    // upload — HTTP success paths
    // -------------------------------------------------------------------------

    @Test
    fun `upload returns Success for HTTP 200`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = uploader.upload(server.url("/upload").toString(), "fake-image".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Success)
        with(result as Uploader.UploadResult.Success) {
            assertEquals(200, httpCode)
            assertTrue(message.contains("200"))
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `upload returns Success for HTTP 201`() {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/png")

        assertTrue(result is Uploader.UploadResult.Success)
        assertEquals(201, (result as Uploader.UploadResult.Success).httpCode)
    }

    @Test
    fun `upload sends correct Content-Type header`() {
        server.enqueue(MockResponse().setResponseCode(200))

        uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "video/mp4")

        assertEquals("video/mp4", server.takeRequest().getHeader("Content-Type"))
    }

    @Test
    fun `upload sends exact file bytes in request body`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        uploader.upload(server.url("/upload").toString(), payload, "image/jpeg")

        assertTrue(payload.contentEquals(server.takeRequest().body.readByteArray()))
    }

    @Test
    fun `upload uses POST method`() {
        server.enqueue(MockResponse().setResponseCode(200))

        uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertEquals("POST", server.takeRequest().method)
    }

    // -------------------------------------------------------------------------
    // upload — HTTP failure paths (no retry for 4xx)
    // -------------------------------------------------------------------------

    @Test
    fun `upload returns Failure for HTTP 400 without retrying`() {
        server.enqueue(MockResponse().setResponseCode(400))

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Failure)
        with(result as Uploader.UploadResult.Failure) {
            assertEquals(400, httpCode)
            assertTrue(message.contains("400"))
            assertEquals(1, attempts)   // Exactly one attempt — 4xx not retried
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `upload returns Failure for HTTP 401 without retrying`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(1, server.requestCount)
    }

    // -------------------------------------------------------------------------
    // upload — retry behaviour for 5xx and network errors
    // -------------------------------------------------------------------------

    @Test
    fun `upload retries on HTTP 500 and succeeds on second attempt`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Success)
        assertEquals(2, (result as Uploader.UploadResult.Success).attempts)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `upload retries on HTTP 503 and succeeds on third attempt`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Success)
        assertEquals(3, (result as Uploader.UploadResult.Success).attempts)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `upload exhausts all attempts on persistent 500 and returns Failure`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(500, (result as Uploader.UploadResult.Failure).httpCode)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `upload retries on network error and succeeds on second attempt`() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200))

        val result = uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertTrue(result is Uploader.UploadResult.Success)
        assertEquals(2, (result as Uploader.UploadResult.Success).attempts)
    }

    @Test
    fun `upload stops retrying after maxAttempts`() {
        val singleAttemptUploader = Uploader(
            httpClient = OkHttpClient(),
            maxAttempts = 1,
            initialDelayMs = 0L,
        )
        server.enqueue(MockResponse().setResponseCode(500))

        singleAttemptUploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/jpeg")

        assertEquals(1, server.requestCount)
    }

    // -------------------------------------------------------------------------
    // upload — MIME type variations
    // -------------------------------------------------------------------------

    @Test
    fun `upload sets image-png Content-Type correctly`() {
        server.enqueue(MockResponse().setResponseCode(200))

        uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "image/png")

        assertEquals("image/png", server.takeRequest().getHeader("Content-Type"))
    }

    @Test
    fun `upload sets video-quicktime Content-Type correctly`() {
        server.enqueue(MockResponse().setResponseCode(200))

        uploader.upload(server.url("/upload").toString(), "data".toByteArray(), "video/quicktime")

        assertEquals("video/quicktime", server.takeRequest().getHeader("Content-Type"))
    }

    @Test
    fun `upload sets octet-stream when mime type falls back`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val mime = Uploader.resolveMimeType(null)
        uploader.upload(server.url("/upload").toString(), "data".toByteArray(), mime)

        assertEquals("application/octet-stream", server.takeRequest().getHeader("Content-Type"))
    }

    // -------------------------------------------------------------------------
    // parseJsonStringField
    // -------------------------------------------------------------------------

    @Test
    fun `parseJsonStringField extracts storageKey`() {
        val json = """{"storageKey":"uuid.mp4","uploadUrl":"https://gcs.example.com/signed"}"""
        assertEquals("uuid.mp4", Uploader.parseJsonStringField(json, "storageKey"))
    }

    @Test
    fun `parseJsonStringField extracts uploadUrl`() {
        val json = """{"storageKey":"uuid.mp4","uploadUrl":"https://gcs.example.com/signed?foo=bar"}"""
        assertEquals("https://gcs.example.com/signed?foo=bar", Uploader.parseJsonStringField(json, "uploadUrl"))
    }

    @Test
    fun `parseJsonStringField returns null for missing key`() {
        assertNull(Uploader.parseJsonStringField("{}", "storageKey"))
    }

    // -------------------------------------------------------------------------
    // uploadViaSigned
    // -------------------------------------------------------------------------

    @Test
    fun `uploadViaSigned succeeds when all three steps return 2xx`() {
        val prepareJson = """{"storageKey":"uuid.mp4","uploadUrl":"${server.url("/gcs-put")}"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(prepareJson))  // prepare
        server.enqueue(MockResponse().setResponseCode(200))                        // GCS PUT
        server.enqueue(MockResponse().setResponseCode(201))                        // confirm

        val result = uploader.uploadViaSigned(server.url("/").toString(), "data".toByteArray(), "video/mp4", "key")

        assertTrue(result is Uploader.UploadResult.Success)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `uploadViaSigned sends mimeType in prepare body`() {
        val prepareJson = """{"storageKey":"uuid.mp4","uploadUrl":"${server.url("/gcs-put")}"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(prepareJson))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(201))

        uploader.uploadViaSigned(server.url("/").toString(), "data".toByteArray(), "video/mp4", "key")

        val prepareRequest = server.takeRequest()
        assertTrue(prepareRequest.body.readUtf8().contains("video/mp4"))
    }

    @Test
    fun `uploadViaSigned sends X-Api-Key on prepare and confirm but not GCS PUT`() {
        val prepareJson = """{"storageKey":"uuid.mp4","uploadUrl":"${server.url("/gcs-put")}"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(prepareJson))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(201))

        uploader.uploadViaSigned(server.url("/").toString(), "data".toByteArray(), "video/mp4", "mykey")

        val prepareReq = server.takeRequest()
        val gcsReq = server.takeRequest()
        val confirmReq = server.takeRequest()

        assertEquals("mykey", prepareReq.getHeader("X-Api-Key"))
        assertNull(gcsReq.getHeader("X-Api-Key"))
        assertEquals("mykey", confirmReq.getHeader("X-Api-Key"))
    }

    @Test
    fun `uploadViaSigned returns Failure when prepare returns 4xx`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = uploader.uploadViaSigned(server.url("/").toString(), "data".toByteArray(), "video/mp4")

        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `uploadViaSigned returns Failure when GCS PUT fails`() {
        val prepareJson = """{"storageKey":"uuid.mp4","uploadUrl":"${server.url("/gcs-put")}"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(prepareJson))
        server.enqueue(MockResponse().setResponseCode(403))

        val result = uploader.uploadViaSigned(server.url("/").toString(), "data".toByteArray(), "video/mp4")

        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `uploadViaSigned fails fast on null endpoint`() {
        val result = uploader.uploadViaSigned(null, "data".toByteArray(), "video/mp4")
        assertTrue(result is Uploader.UploadResult.Failure)
        assertEquals(0, server.requestCount)
    }
}
