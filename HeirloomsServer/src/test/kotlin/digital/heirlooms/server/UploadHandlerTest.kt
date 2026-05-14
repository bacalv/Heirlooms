package digital.heirlooms.server

import digital.heirlooms.server.routes.buildApp
import digital.heirlooms.server.domain.upload.UploadPage
import digital.heirlooms.server.domain.upload.UploadRecord
import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.capsule.CapsuleRepository
import digital.heirlooms.server.repository.diag.DiagRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.plot.FlowRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.repository.plot.PlotMemberRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.social.SocialRepository
import digital.heirlooms.server.repository.storage.BlobRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.http4k.core.Method.GET
import org.http4k.core.Method.PATCH
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.NOT_IMPLEMENTED
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class FinUploadHandlerTest {

    private val mockStorage = mockk<FileStore>()
    private val mockUploadRepo = mockk<UploadRepository>()
    private val mockBlobRepo = mockk<BlobRepository>(relaxed = true)
    private val mockAuthRepo = mockk<AuthRepository>(relaxed = true)
    private val mockCapsuleRepo = mockk<CapsuleRepository>(relaxed = true)
    private val mockDiagRepo = mockk<DiagRepository>(relaxed = true)
    private val mockKeyRepo = mockk<KeyRepository>(relaxed = true)
    private val mockPlotRepo = mockk<PlotRepository>(relaxed = true)
    private val mockFlowRepo = mockk<FlowRepository>(relaxed = true)
    private val mockItemRepo = mockk<PlotItemRepository>(relaxed = true)
    private val mockMemberRepo = mockk<PlotMemberRepository>(relaxed = true)
    private val mockSocialRepo = mockk<SocialRepository>(relaxed = true)

    private val app = buildApp(
        storage = mockStorage,
        uploadRepo = mockUploadRepo,
        authRepo = mockAuthRepo,
        capsuleRepo = mockCapsuleRepo,
        plotRepo = mockPlotRepo,
        flowRepo = mockFlowRepo,
        itemRepo = mockItemRepo,
        memberRepo = mockMemberRepo,
        keyRepo = mockKeyRepo,
        socialRepo = mockSocialRepo,
        blobRepo = mockBlobRepo,
        diagRepo = mockDiagRepo,
    )

    // Stub the repo calls used by UploadService
    private fun stubUploadRepo() {
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()
    }

    // -------------------------------------------------------------------------
    // Success cases
    // -------------------------------------------------------------------------

    @Test
    fun `POST with jpeg body returns 201`() {
        stubUploadRepo()
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
        stubUploadRepo()
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
        stubUploadRepo()
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
        stubUploadRepo()
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
        stubUploadRepo()
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
        stubUploadRepo()
        val capturedMime = slot<String>()
        every { mockStorage.save(any(), capture(capturedMime)) } returns StorageKey("uuid.bin")

        app(Request(POST, "/api/content/upload").body("bytes"))

        assertEquals("application/octet-stream", capturedMime.captured)
    }

    @Test
    fun `upload records metadata to the database`() {
        val capturedRecord = slot<UploadRecord>()
        every { mockStorage.save(any(), any()) } returns StorageKey("some-uuid.jpg")
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.recordUpload(capture(capturedRecord)) } just runs
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

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
        verify(exactly = 0) { mockUploadRepo.recordUpload(any()) }
    }

    @Test
    fun `storage exception returns 500`() {
        every { mockUploadRepo.findByContentHash(any()) } returns null
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
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(
            UploadRecord(UUID.randomUUID(), "some-uuid.jpg", "image/jpeg", 1024),
        ), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.header("Content-Type")!!.contains("application/json"))
        assertTrue(response.bodyString().contains("some-uuid.jpg"))
    }

    @Test
    fun `GET uploads returns empty items when no uploads`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(emptyList(), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("\"items\":[]"))
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    @Test
    fun `health endpoint returns 200`() {
        assertEquals(OK, app(Request(GET, "/health")).status)
    }

    @Test
    fun `OpenAPI spec endpoint returns 200 with valid JSON`() {
        val response = app(Request(GET, "/docs/api.json"))
        assertEquals(OK, response.status)
        assertTrue(response.header("Content-Type")!!.contains("application/json"))
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
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns knownRecord
        every { mockStorage.get(StorageKey(knownRecord.storageKey)) } returns byteArrayOf(1, 2, 3)

        val response = app(Request(GET, "/api/content/uploads/$knownId/file"))

        assertEquals(OK, response.status)
        assertArrayEquals(byteArrayOf(1, 2, 3), response.body.payload.array())
    }

    @Test
    fun `GET uploads id file sets correct Content-Type`() {
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns knownRecord
        every { mockStorage.get(StorageKey(knownRecord.storageKey)) } returns ByteArray(0)

        val response = app(Request(GET, "/api/content/uploads/$knownId/file"))

        assertEquals("image/jpeg", response.header("Content-Type"))
    }

    @Test
    fun `GET uploads id file returns 404 when upload not found`() {
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns null
        every { mockUploadRepo.findUploadByIdForSharedMember(knownId, any()) } returns null

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
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns knownRecord
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
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns knownRecord
        every { (mockDirectStorage as DirectUploadSupport).generateReadUrl(StorageKey(knownRecord.storageKey)) } returns "https://gcs.example.com/signed-read"

        val response = appWithDirectUpload(Request(GET, "/api/content/uploads/$knownId/url"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("https://gcs.example.com/signed-read"))
    }

    @Test
    fun `GET uploads id url returns 404 when upload not found`() {
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns null
        every { mockUploadRepo.findUploadByIdForSharedMember(knownId, any()) } returns null

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
    private val appWithDirectUpload = buildApp(
        storage = mockDirectStorage,
        uploadRepo = mockUploadRepo,
        authRepo = mockAuthRepo,
        capsuleRepo = mockCapsuleRepo,
        plotRepo = mockPlotRepo,
        flowRepo = mockFlowRepo,
        itemRepo = mockItemRepo,
        memberRepo = mockMemberRepo,
        keyRepo = mockKeyRepo,
        socialRepo = mockSocialRepo,
        blobRepo = mockBlobRepo,
        diagRepo = mockDiagRepo,
    )

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
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.recordUpload(capture(capturedRecord)) } just runs
        every { mockStorage.get(any()) } returns ByteArray(0)

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

    // -------------------------------------------------------------------------
    // Duplicate detection — direct upload (/upload)
    // -------------------------------------------------------------------------

    @Test
    fun `uploading a new file succeeds with 201`() {
        stubUploadRepo()
        every { mockStorage.save(any(), any()) } returns StorageKey("new-file.jpg")

        val response = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("unique-file-bytes")
        )

        assertEquals(CREATED, response.status)
    }

    @Test
    fun `uploading the same file twice returns 409 with storageKey of the first`() {
        every { mockStorage.save(any(), any()) } returns StorageKey("first-upload.jpg")
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockUploadRepo.findByContentHash(any()) } returnsMany listOf(
            null,
            UploadRecord(UUID.randomUUID(), "first-upload.jpg", "image/jpeg", 5L),
        )
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("same-bytes")
        )
        val second = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("same-bytes")
        )

        assertEquals(CONFLICT, second.status)
        assertTrue(second.bodyString().contains("first-upload.jpg"))
    }

    @Test
    fun `uploading two different files both succeed with 201`() {
        every { mockStorage.save(any(), any()) } returnsMany listOf(
            StorageKey("file-a.jpg"),
            StorageKey("file-b.jpg"),
        )
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val first = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("bytes-for-file-a")
        )
        val second = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("bytes-for-file-b")
        )

        assertEquals(CREATED, first.status)
        assertEquals(CREATED, second.status)
    }

    @Test
    fun `uploading when existing rows have null hashes does not cause false duplicate`() {
        every { mockStorage.save(any(), any()) } returns StorageKey("new-file.jpg")
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("some-bytes")
        )

        assertEquals(CREATED, response.status)
    }

    // -------------------------------------------------------------------------
    // Duplicate detection — confirm flow (/uploads/confirm)
    // -------------------------------------------------------------------------

    @Test
    fun `confirm with new contentHash succeeds with 201`() {
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockStorage.get(any()) } returns ByteArray(0)

        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uuid.mp4","mimeType":"video/mp4","fileSize":1000,"contentHash":"abcd1234"}""")
        )

        assertEquals(CREATED, response.status)
    }

    @Test
    fun `confirm with duplicate contentHash returns 409 with existing storageKey`() {
        every { mockUploadRepo.findByContentHash("abcd1234") } returns
            UploadRecord(UUID.randomUUID(), "original.mp4", "video/mp4", 1000L)

        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"new.mp4","mimeType":"video/mp4","fileSize":1000,"contentHash":"abcd1234"}""")
        )

        assertEquals(CONFLICT, response.status)
        assertTrue(response.bodyString().contains("original.mp4"))
    }

    @Test
    fun `confirm without contentHash records upload normally`() {
        val capturedRecord = slot<UploadRecord>()
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.recordUpload(capture(capturedRecord)) } just runs
        every { mockStorage.get(any()) } returns ByteArray(0)

        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uuid.mp4","mimeType":"video/mp4","fileSize":1000}""")
        )

        assertEquals(CREATED, response.status)
        assertEquals(null, capturedRecord.captured.contentHash)
    }

    // -------------------------------------------------------------------------
    // Thumbnail generation — upload (/upload)
    // -------------------------------------------------------------------------

    @Test
    fun `thumbnail is generated and stored for supported image type`() {
        val thumbBytes = byteArrayOf(1, 2, 3)
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockStorage.save(any(), any()) } returns StorageKey("some-uuid.jpg")
        every { mockStorage.saveWithKey(any(), any(), any()) } just runs
        val capturedRecord = slot<UploadRecord>()
        every { mockUploadRepo.recordUpload(capture(capturedRecord)) } just runs
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val appWithThumb = buildApp(
            storage = mockStorage,
            uploadRepo = mockUploadRepo,
            authRepo = mockAuthRepo,
            capsuleRepo = mockCapsuleRepo,
            plotRepo = mockPlotRepo,
            flowRepo = mockFlowRepo,
            itemRepo = mockItemRepo,
            memberRepo = mockMemberRepo,
            keyRepo = mockKeyRepo,
            socialRepo = mockSocialRepo,
            blobRepo = mockBlobRepo,
            diagRepo = mockDiagRepo,
            thumbnailGenerator = { _, _ -> thumbBytes },
        )
        appWithThumb(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("fake-image-bytes")
        )

        verify { mockStorage.saveWithKey(thumbBytes, StorageKey("some-uuid-thumb.jpg"), "image/jpeg") }
        assertEquals("some-uuid-thumb.jpg", capturedRecord.captured.thumbnailKey)
    }

    @Test
    fun `no thumbnail stored when video bytes are invalid`() {
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockStorage.save(any(), any()) } returns StorageKey("uuid.mp4")
        val capturedRecord = slot<UploadRecord>()
        every { mockUploadRepo.recordUpload(capture(capturedRecord)) } just runs
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        app(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "video/mp4")
                .body("fake-video-bytes")
        )

        verify(exactly = 0) { mockStorage.saveWithKey(any(), any(), any()) }
        assertNull(capturedRecord.captured.thumbnailKey)
    }

    @Test
    fun `upload succeeds even when thumbnail generation throws`() {
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockStorage.save(any(), any()) } returns StorageKey("uuid.jpg")
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val appWithFailingThumb = buildApp(
            storage = mockStorage,
            uploadRepo = mockUploadRepo,
            authRepo = mockAuthRepo,
            capsuleRepo = mockCapsuleRepo,
            plotRepo = mockPlotRepo,
            flowRepo = mockFlowRepo,
            itemRepo = mockItemRepo,
            memberRepo = mockMemberRepo,
            keyRepo = mockKeyRepo,
            socialRepo = mockSocialRepo,
            blobRepo = mockBlobRepo,
            diagRepo = mockDiagRepo,
            thumbnailGenerator = { _, _ -> throw RuntimeException("out of memory") },
        )
        val response = appWithFailingThumb(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("bytes")
        )

        assertEquals(CREATED, response.status)
    }

    // -------------------------------------------------------------------------
    // Thumbnail key — list response
    // -------------------------------------------------------------------------

    @Test
    fun `thumbnailKey is null in list response for non-image uploads`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(
            UploadRecord(UUID.randomUUID(), "uuid.mp4", "video/mp4", 10000L),
        ), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertTrue(response.bodyString().contains(""""thumbnailKey":null"""))
    }

    @Test
    fun `thumbnailKey appears in list response when thumbnail exists`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(
            UploadRecord(UUID.randomUUID(), "uuid.jpg", "image/jpeg", 1024L, thumbnailKey = "uuid-thumb.jpg"),
        ), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertTrue(response.bodyString().contains("uuid-thumb.jpg"))
    }

    // -------------------------------------------------------------------------
    // Thumb proxy endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `GET uploads id thumb returns thumbnail when thumbnail key exists`() {
        val recordWithThumb = knownRecord.copy(thumbnailKey = "thumb-key.jpg")
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns recordWithThumb
        every { mockStorage.get(StorageKey("thumb-key.jpg")) } returns byteArrayOf(1, 2, 3)

        val response = app(Request(GET, "/api/content/uploads/$knownId/thumb"))

        assertEquals(OK, response.status)
        assertEquals("image/jpeg", response.header("Content-Type"))
        assertArrayEquals(byteArrayOf(1, 2, 3), response.body.payload.array())
    }

    @Test
    fun `GET uploads id thumb falls back to full file when no thumbnail key`() {
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns knownRecord
        every { mockStorage.get(StorageKey(knownRecord.storageKey)) } returns byteArrayOf(4, 5, 6)

        val response = app(Request(GET, "/api/content/uploads/$knownId/thumb"))

        assertEquals(OK, response.status)
        assertEquals("image/jpeg", response.header("Content-Type"))
        assertArrayEquals(byteArrayOf(4, 5, 6), response.body.payload.array())
    }

    @Test
    fun `GET uploads id thumb returns 404 when upload not found`() {
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns null
        every { mockUploadRepo.findUploadByIdForSharedMember(knownId, any()) } returns null

        val response = app(Request(GET, "/api/content/uploads/$knownId/thumb"))

        assertEquals(NOT_FOUND, response.status)
    }

    // -------------------------------------------------------------------------
    // Metadata extraction
    // -------------------------------------------------------------------------

    @Test
    fun `upload succeeds even when metadata extraction throws`() {
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockStorage.save(any(), any()) } returns StorageKey("uuid.jpg")
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val appWithFailingMeta = buildApp(
            storage = mockStorage,
            uploadRepo = mockUploadRepo,
            authRepo = mockAuthRepo,
            capsuleRepo = mockCapsuleRepo,
            plotRepo = mockPlotRepo,
            flowRepo = mockFlowRepo,
            itemRepo = mockItemRepo,
            memberRepo = mockMemberRepo,
            keyRepo = mockKeyRepo,
            socialRepo = mockSocialRepo,
            blobRepo = mockBlobRepo,
            diagRepo = mockDiagRepo,
            metadataExtractor = { _, _ -> throw RuntimeException("extractor failed") },
        )
        val response = appWithFailingMeta(
            Request(POST, "/api/content/upload")
                .header("Content-Type", "image/jpeg")
                .body("bytes")
        )

        assertEquals(CREATED, response.status)
    }

    // -------------------------------------------------------------------------
    // Rotation endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH uploads id rotation returns 200 for valid value`() {
        every { mockUploadRepo.updateRotation(knownId, 90, any()) } returns true

        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/rotation")
                .header("Content-Type", "application/json")
                .body("""{"rotation":90}""")
        )

        assertEquals(OK, response.status)
    }

    @Test
    fun `PATCH uploads id rotation returns 400 for invalid value`() {
        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/rotation")
                .header("Content-Type", "application/json")
                .body("""{"rotation":45}""")
        )
        assertEquals(BAD_REQUEST, response.status)
    }

    @Test
    fun `PATCH uploads id rotation returns 404 when upload not found`() {
        every { mockUploadRepo.updateRotation(knownId, 90, any()) } returns false

        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/rotation")
                .header("Content-Type", "application/json")
                .body("""{"rotation":90}""")
        )
        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `rotation field appears in list response`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(
            UploadRecord(UUID.randomUUID(), "uuid.jpg", "image/jpeg", 1024L, rotation = 90),
        ), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains(""""rotation":90"""))
    }

    @Test
    fun `rotation defaults to 0 in list response`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(
            UploadRecord(UUID.randomUUID(), "uuid.jpg", "image/jpeg", 1024L),
        ), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertTrue(response.bodyString().contains(""""rotation":0"""))
    }

    // -------------------------------------------------------------------------
    // Tags endpoint
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH valid tags returns 200 with updated tags in body`() {
        val updated = knownRecord.copy(tags = listOf("family", "2026-summer"))
        every { mockUploadRepo.updateTags(knownId, listOf("family", "2026-summer"), any(), any()) } returns true
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns updated

        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/tags")
                .header("Content-Type", "application/json")
                .body("""{"tags":["family","2026-summer"]}""")
        )

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("family"))
        assertTrue(response.bodyString().contains("2026-summer"))
        verify { mockUploadRepo.updateTags(knownId, listOf("family", "2026-summer"), any(), any()) }
    }

    @Test
    fun `PATCH empty tags array clears tags and shows empty array in response`() {
        val updated = knownRecord.copy(tags = emptyList())
        every { mockUploadRepo.updateTags(knownId, emptyList(), any(), any()) } returns true
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns updated

        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/tags")
                .header("Content-Type", "application/json")
                .body("""{"tags":[]}""")
        )

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains(""""tags":[]"""))
    }

    @Test
    fun `PATCH invalid tag returns 400 with offending tag in body`() {
        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/tags")
                .header("Content-Type", "application/json")
                .body("""{"tags":["My Children"]}""")
        )

        assertEquals(BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("My Children"))
    }

    @Test
    fun `PATCH multiple tags where one is invalid returns 400 naming that tag`() {
        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/tags")
                .header("Content-Type", "application/json")
                .body("""{"tags":["valid-tag","Invalid_Tag"]}""")
        )

        assertEquals(BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("Invalid_Tag"))
    }

    @Test
    fun `PATCH non-existent upload returns 404`() {
        every { mockUploadRepo.updateTags(knownId, any(), any(), any()) } returns false

        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/tags")
                .header("Content-Type", "application/json")
                .body("""{"tags":["family"]}""")
        )

        assertEquals(NOT_FOUND, response.status)
    }

    @Test
    fun `PATCH malformed JSON returns 400`() {
        val response = app(
            Request(PATCH, "/api/content/uploads/$knownId/tags")
                .header("Content-Type", "application/json")
                .body("""not json""")
        )

        assertEquals(BAD_REQUEST, response.status)
    }

    @Test
    fun `tags field present as empty array in list response for untagged upload`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(knownRecord), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains(""""tags":[]"""))
    }

    @Test
    fun `tags field present and populated after tagging`() {
        val tagged = knownRecord.copy(tags = listOf("vacation"))
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(tagged), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains(""""tags":["vacation"]"""))
    }

    // -------------------------------------------------------------------------
    // Tag filtering — GET /uploads?tag= and ?exclude_tag=
    // -------------------------------------------------------------------------

    @Test
    fun `GET uploads with tag param passes tag to database`() {
        val tagged = knownRecord.copy(tags = listOf("family"))
        every { mockUploadRepo.listUploadsPaginated(any(), any(), tags = listOf("family"), excludeTag = null, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(tagged), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads?tag=family"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("family"))
        verify { mockUploadRepo.listUploadsPaginated(any(), any(), tags = listOf("family"), excludeTag = null, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GET uploads with exclude_tag param passes excludeTag to database`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), tags = emptyList(), excludeTag = "trash", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(knownRecord), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads?exclude_tag=trash"))

        assertEquals(OK, response.status)
        verify { mockUploadRepo.listUploadsPaginated(any(), any(), tags = emptyList(), excludeTag = "trash", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GET uploads with both tag and exclude_tag passes both to database`() {
        val tagged = knownRecord.copy(tags = listOf("family"))
        every { mockUploadRepo.listUploadsPaginated(any(), any(), tags = listOf("family"), excludeTag = "trash", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(tagged), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads?tag=family&exclude_tag=trash"))

        assertEquals(OK, response.status)
        verify { mockUploadRepo.listUploadsPaginated(any(), any(), tags = listOf("family"), excludeTag = "trash", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GET uploads with unknown tag returns empty items`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), tags = listOf("nonexistent"), excludeTag = null, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(emptyList(), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads?tag=nonexistent"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("\"items\":[]"))
    }

    @Test
    fun `GET uploads with no params uses default pagination`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(knownRecord), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains(knownRecord.storageKey))
    }

    // -------------------------------------------------------------------------
    // Initiate endpoint (E2EE)
    // -------------------------------------------------------------------------

    @Test
    fun `POST uploads initiate with encrypted storage_class returns two signed URLs`() {
        every { (mockDirectStorage as DirectUploadSupport).prepareUpload("image/jpeg") } returns
            PreparedUpload(StorageKey("uploads/uuid-content.bin"), "https://minio/content-url")
        every { (mockDirectStorage as DirectUploadSupport).prepareUpload("application/octet-stream") } returns
            PreparedUpload(StorageKey("uploads/uuid-thumb.bin"), "https://minio/thumb-url")
        every { mockBlobRepo.insertPendingBlob(any()) } returns UUID.randomUUID()

        val response = appWithDirectUpload(
            Request(POST, "/api/content/uploads/initiate")
                .header("Content-Type", "application/json")
                .body("""{"mimeType":"image/jpeg","storage_class":"encrypted"}""")
        )

        assertEquals(OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("storageKey"))
        assertTrue(body.contains("uploadUrl"))
        assertTrue(body.contains("thumbnailStorageKey"))
        assertTrue(body.contains("thumbnailUploadUrl"))
        verify(exactly = 2) { mockBlobRepo.insertPendingBlob(any()) }
    }

    @Test
    fun `POST uploads initiate with public storage_class returns 400`() {
        val response = appWithDirectUpload(
            Request(POST, "/api/content/uploads/initiate")
                .header("Content-Type", "application/json")
                .body("""{"mimeType":"image/jpeg","storage_class":"public"}""")
        )

        assertEquals(BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("public storage class"))
    }

    @Test
    fun `POST uploads initiate with legacy body returns single signed URL`() {
        every { (mockDirectStorage as DirectUploadSupport).prepareUpload("video/mp4") } returns
            PreparedUpload(StorageKey("uploads/uuid.mp4"), "https://minio/video-url")
        every { mockBlobRepo.insertPendingBlob(any()) } returns UUID.randomUUID()

        val response = appWithDirectUpload(
            Request(POST, "/api/content/uploads/initiate")
                .header("Content-Type", "application/json")
                .body("""{"mimeType":"video/mp4"}""")
        )

        assertEquals(OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("storageKey"))
        assertTrue(body.contains("uploadUrl"))
        assertTrue(!body.contains("thumbnailStorageKey"))
        verify(exactly = 1) { mockBlobRepo.insertPendingBlob(any()) }
    }

    // -------------------------------------------------------------------------
    // Confirm endpoint — encrypted path
    // -------------------------------------------------------------------------

    private fun makeSymmetricEnvelope(algId: String = AlgorithmIds.MASTER_AES256GCM_V1): ByteArray {
        val algBytes = algId.toByteArray()
        val buf = ByteArray(1 + 1 + algBytes.size + 12 + 0 + 16)
        buf[0] = 1  // version
        buf[1] = algBytes.size.toByte()
        algBytes.copyInto(buf, 2)
        // nonce at offset 2+algBytes.size, then 0-byte ciphertext, then 16-byte auth tag — all zeros is fine for validation
        return buf
    }

    @Test
    fun `POST uploads confirm with encrypted storage_class and valid envelopes returns 201`() {
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockBlobRepo.deletePendingBlob(any()) } just runs

        val wrappedDek = makeSymmetricEnvelope(AlgorithmIds.MASTER_AES256GCM_V1)
        val enc = java.util.Base64.getEncoder()

        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uploads/uuid.bin","mimeType":"image/jpeg","fileSize":1000,"storage_class":"encrypted","envelopeVersion":1,"wrappedDek":"${enc.encodeToString(wrappedDek)}","dekFormat":"master-aes256gcm-v1"}""")
        )

        assertEquals(CREATED, response.status)
        verify { mockUploadRepo.recordUpload(match { it.storageClass == "encrypted" }) }
    }

    @Test
    fun `POST uploads confirm encrypted with invalid wrappedDek envelope returns 400`() {
        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uploads/uuid.bin","mimeType":"image/jpeg","fileSize":1000,"storage_class":"encrypted","envelopeVersion":1,"wrappedDek":"${java.util.Base64.getEncoder().encodeToString(byteArrayOf(99, 5))}","dekFormat":"master-aes256gcm-v1"}""")
        )

        assertEquals(BAD_REQUEST, response.status)
        assertTrue(response.bodyString().contains("wrappedDek envelope invalid"))
    }

    @Test
    fun `POST uploads confirm encrypted with missing wrappedDek returns 400`() {
        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uploads/uuid.bin","mimeType":"image/jpeg","fileSize":1000,"storage_class":"encrypted","envelopeVersion":1}""")
        )

        assertEquals(BAD_REQUEST, response.status)
    }

    @Test
    fun `POST uploads confirm with legacy body behaves as today`() {
        val capturedRecord = slot<UploadRecord>()
        every { mockUploadRepo.findByContentHash(any()) } returns null
        every { mockUploadRepo.recordUpload(capture(capturedRecord)) } just runs
        every { mockStorage.get(any()) } returns ByteArray(0)
        every { mockBlobRepo.deletePendingBlob(any()) } just runs

        val response = app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uuid.mp4","mimeType":"video/mp4","fileSize":1000}""")
        )

        assertEquals(CREATED, response.status)
        assertEquals("public", capturedRecord.captured.storageClass)
    }

    @Test
    fun `POST uploads confirm encrypted skips dedup check`() {
        every { mockUploadRepo.recordUpload(any()) } just runs
        every { mockBlobRepo.deletePendingBlob(any()) } just runs

        val wrappedDek = makeSymmetricEnvelope(AlgorithmIds.MASTER_AES256GCM_V1)
        val enc = java.util.Base64.getEncoder()

        app(
            Request(POST, "/api/content/uploads/confirm")
                .header("Content-Type", "application/json")
                .body("""{"storageKey":"uploads/uuid.bin","mimeType":"image/jpeg","fileSize":1000,"storage_class":"encrypted","envelopeVersion":1,"wrappedDek":"${enc.encodeToString(wrappedDek)}","dekFormat":"master-aes256gcm-v1","contentHash":"abcd1234"}""")
        )

        verify(exactly = 0) { mockUploadRepo.findByContentHash(any()) }
    }

    // -------------------------------------------------------------------------
    // Upload detail — E2EE fields in response
    // -------------------------------------------------------------------------

    @Test
    fun `GET uploads id returns E2EE fields for encrypted row`() {
        val enc = java.util.Base64.getEncoder()
        val wrappedDek = ByteArray(64) { 5 }
        val encryptedRecord = knownRecord.copy(
            storageClass = "encrypted",
            envelopeVersion = 1,
            wrappedDek = wrappedDek,
            dekFormat = "master-aes256gcm-v1",
        )
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns encryptedRecord

        val response = app(Request(GET, "/api/content/uploads/$knownId"))

        assertEquals(OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("\"storageClass\":\"encrypted\""))
        assertTrue(body.contains("\"wrappedDek\":\"${enc.encodeToString(wrappedDek)}\""))
        assertTrue(body.contains("\"dekFormat\":\"master-aes256gcm-v1\""))
    }

    @Test
    fun `GET uploads id for legacy row has no E2EE fields`() {
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns knownRecord

        val response = app(Request(GET, "/api/content/uploads/$knownId"))

        assertEquals(OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("\"storageClass\":\"public\""))
        assertTrue(!body.contains("wrappedDek"))
        assertTrue(!body.contains("dekFormat"))
    }

    @Test
    fun `GET uploads list includes storageClass on all items`() {
        every { mockUploadRepo.listUploadsPaginated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadPage(listOf(knownRecord), null)
        every { mockUploadRepo.fetchExpiredCompostedUploads() } returns emptyList()

        val response = app(Request(GET, "/api/content/uploads"))

        assertEquals(OK, response.status)
        assertTrue(response.bodyString().contains("storageClass"))
    }

    @Test
    fun `GET uploads id thumb for encrypted row serves thumbnailStorageKey bytes`() {
        val encryptedRecord = knownRecord.copy(
            storageClass = "encrypted",
            thumbnailStorageKey = "uploads/uuid-thumb.bin",
        )
        every { mockUploadRepo.findUploadByIdForUser(knownId, any()) } returns encryptedRecord
        every { mockStorage.get(StorageKey("uploads/uuid-thumb.bin")) } returns byteArrayOf(7, 8, 9)

        val response = app(Request(GET, "/api/content/uploads/$knownId/thumb"))

        assertEquals(OK, response.status)
        assertTrue(response.body.payload.array().contentEquals(byteArrayOf(7, 8, 9)))
        verify { mockStorage.get(StorageKey("uploads/uuid-thumb.bin")) }
    }
}
