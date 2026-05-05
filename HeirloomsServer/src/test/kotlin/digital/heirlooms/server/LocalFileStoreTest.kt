package digital.heirlooms.server

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class LocalFileStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private val fixedUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun storeWithFixedUuid() = LocalFileStore(tempDir) { fixedUuid }

    @Test
    fun `saved file has UUID as base name`() {
        val key = storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")
        assertEquals("11111111-1111-1111-1111-111111111111.jpg", key.value)
    }

    @Test
    fun `saved file exists on disk inside the storage directory`() {
        val key = storeWithFixedUuid().save("data".toByteArray(), "image/jpeg")
        assertTrue(Files.exists(tempDir.resolve(key.value)))
    }

    @Test
    fun `saved file contains exact bytes that were passed in`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val key = storeWithFixedUuid().save(payload, "video/mp4")
        assertArrayEquals(payload, Files.readAllBytes(tempDir.resolve(key.value)))
    }

    @Test
    fun `mp4 content type produces mp4 extension`() {
        val key = storeWithFixedUuid().save("video".toByteArray(), "video/mp4")
        assertTrue(key.value.endsWith(".mp4"))
    }

    @Test
    fun `quicktime content type produces mov extension`() {
        val key = storeWithFixedUuid().save("video".toByteArray(), "video/quicktime")
        assertTrue(key.value.endsWith(".mov"))
    }

    @Test
    fun `png content type produces png extension`() {
        val key = storeWithFixedUuid().save("image".toByteArray(), "image/png")
        assertTrue(key.value.endsWith(".png"))
    }

    @Test
    fun `octet-stream content type produces bin extension`() {
        val key = storeWithFixedUuid().save("raw".toByteArray(), "application/octet-stream")
        assertTrue(key.value.endsWith(".bin"))
    }

    @Test
    fun `each save call uses a different UUID`() {
        val uuids = listOf(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        )
        val iter = uuids.iterator()
        val store = LocalFileStore(tempDir) { iter.next() }

        val key1 = store.save("a".toByteArray(), "image/jpeg")
        val key2 = store.save("b".toByteArray(), "image/jpeg")

        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg", key1.value)
        assertEquals("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg", key2.value)
    }

    @Test
    fun `storage directory is created if it does not exist`() {
        val nested = tempDir.resolve("a/b/c")
        LocalFileStore(nested) { fixedUuid }
        assertTrue(Files.isDirectory(nested))
    }

    @Test
    fun `get returns bytes that were previously saved`() {
        val payload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val key = storeWithFixedUuid().save(payload, "image/jpeg")
        assertArrayEquals(payload, storeWithFixedUuid().get(key))
    }

    @Test
    fun `get retrieves file by storage key`() {
        val store = storeWithFixedUuid()
        val payload = "hello".toByteArray()
        val key = store.save(payload, "image/png")
        assertArrayEquals(payload, store.get(key))
    }
}
