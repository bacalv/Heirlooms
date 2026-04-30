package digital.heirlooms.server

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.util.UUID
import java.util.concurrent.CompletableFuture

class S3FileStoreTest {

    private val fixedUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val mockClient = mockk<S3AsyncClient>()

    private fun storeWithFixedUuid() = S3FileStore(
        bucket = "my-heirloom-bucket",
        client = mockClient,
        uuidProvider = { fixedUuid },
    )

    // Use typed any<AsyncRequestBody>() to disambiguate from the putObject(Request, Path) overload
    private fun stubSuccessfulPut() {
        every {
            mockClient.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>())
        } returns CompletableFuture.completedFuture(PutObjectResponse.builder().build())
    }

    // -------------------------------------------------------------------------
    // Storage key
    // -------------------------------------------------------------------------

    @Test
    fun `returns storage key with UUID and jpeg extension for image-jpeg`() {
        stubSuccessfulPut()
        val key = storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg", key.value)
    }

    @Test
    fun `returns storage key with mp4 extension for video-mp4`() {
        stubSuccessfulPut()
        val key = storeWithFixedUuid().save("data".toByteArray(), "video/mp4")
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.mp4", key.value)
    }

    @Test
    fun `each save call uses a different UUID`() {
        stubSuccessfulPut()
        val uuids = listOf(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
        )
        val iter = uuids.iterator()
        val store = S3FileStore("bucket", mockClient) { iter.next() }

        val key1 = store.save("a".toByteArray(), "image/jpeg")
        val key2 = store.save("b".toByteArray(), "image/jpeg")

        assertEquals("11111111-1111-1111-1111-111111111111.jpg", key1.value)
        assertEquals("22222222-2222-2222-2222-222222222222.jpg", key2.value)
    }

    // -------------------------------------------------------------------------
    // PutObject request shape
    // -------------------------------------------------------------------------

    @Test
    fun `put request targets the configured bucket`() {
        val capturedRequest = slot<PutObjectRequest>()
        every {
            mockClient.putObject(capture(capturedRequest), any<AsyncRequestBody>())
        } returns CompletableFuture.completedFuture(PutObjectResponse.builder().build())

        storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")

        assertEquals("my-heirloom-bucket", capturedRequest.captured.bucket())
    }

    @Test
    fun `put request uses UUID-based key`() {
        val capturedRequest = slot<PutObjectRequest>()
        every {
            mockClient.putObject(capture(capturedRequest), any<AsyncRequestBody>())
        } returns CompletableFuture.completedFuture(PutObjectResponse.builder().build())

        storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")

        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg", capturedRequest.captured.key())
    }

    @Test
    fun `put request sets Content-Type to the provided MIME type`() {
        val capturedRequest = slot<PutObjectRequest>()
        every {
            mockClient.putObject(capture(capturedRequest), any<AsyncRequestBody>())
        } returns CompletableFuture.completedFuture(PutObjectResponse.builder().build())

        storeWithFixedUuid().save("data".toByteArray(), "video/mp4")

        assertEquals("video/mp4", capturedRequest.captured.contentType())
    }

    @Test
    fun `put request sets Content-Length to byte array size`() {
        val capturedRequest = slot<PutObjectRequest>()
        every {
            mockClient.putObject(capture(capturedRequest), any<AsyncRequestBody>())
        } returns CompletableFuture.completedFuture(PutObjectResponse.builder().build())

        val payload = byteArrayOf(1, 2, 3, 4, 5)
        storeWithFixedUuid().save(payload, "image/jpeg")

        assertEquals(5L, capturedRequest.captured.contentLength())
    }

    @Test
    fun `putObject is called exactly once per save`() {
        stubSuccessfulPut()
        storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")
        verify(exactly = 1) { mockClient.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()) }
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    fun `S3 failure is wrapped in a RuntimeException`() {
        every {
            mockClient.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>())
        } returns CompletableFuture.failedFuture(RuntimeException("Access Denied"))

        val ex = runCatching {
            storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")
        }.exceptionOrNull()

        assertTrue(ex is RuntimeException)
        assertTrue(ex!!.message!!.contains("S3 upload failed"))
    }
}
