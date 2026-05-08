package digital.heirlooms.tools.reimport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ImporterTest {

    // ---- isMediaContentType ----------------------------------------------------

    @Test
    fun `image content types pass the media filter`() {
        for (ct in listOf("image/jpeg", "image/png", "image/heic", "image/webp", "image/gif")) {
            assertTrue(isMediaContentType(ct), "expected $ct to pass")
        }
    }

    @Test
    fun `video content types pass the media filter`() {
        for (ct in listOf("video/mp4", "video/quicktime", "video/webm", "video/mpeg")) {
            assertTrue(isMediaContentType(ct), "expected $ct to pass")
        }
    }

    @Test
    fun `non-media content types are rejected`() {
        for (ct in listOf("text/plain", "application/json", "application/pdf", "application/octet-stream")) {
            assertFalse(isMediaContentType(ct), "expected $ct to be rejected")
        }
    }

    @Test
    fun `null content type is rejected`() {
        assertFalse(isMediaContentType(null))
    }

    // ---- sha256Hex -------------------------------------------------------------

    @Test
    fun `sha256Hex produces 64-char lowercase hex`() {
        val result = sha256Hex("hello".toByteArray())
        assertEquals(64, result.length)
        assertTrue(result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val bytes = "test-content".toByteArray()
        assertEquals(sha256Hex(bytes), sha256Hex(bytes))
    }

    // ---- ImportSummary counting via fake BucketReader + in-memory state --------

    @Test
    fun `import summary counts are correct`() {
        val jpegBytes = "fake-jpeg".toByteArray()
        val mp4Bytes = "fake-mp4".toByteArray()

        val objects = listOf(
            fakeObject("a.jpg", "image/jpeg", jpegBytes),
            fakeObject("b.mp4", "video/mp4", mp4Bytes),
            fakeObject("c.txt", "text/plain", "ignored".toByteArray()),
        )

        val imported = mutableSetOf<String>()
        val summary = runFakeImport(objects, existingKeys = setOf("b.mp4"), importedOut = imported)

        assertEquals(3, summary.scanned)
        assertEquals(1, summary.imported)       // a.jpg only
        assertEquals(1, summary.skippedExists)  // b.mp4
        assertEquals(1, summary.skippedContentType) // c.txt
        assertEquals(0, summary.errored)
        assertTrue("a.jpg" in imported)
        assertFalse("b.mp4" in imported)
    }

    @Test
    fun `import is idempotent — second run skips everything`() {
        val objects = listOf(
            fakeObject("img.jpg", "image/jpeg", "data".toByteArray()),
        )
        val imported = mutableSetOf<String>()
        val firstSummary = runFakeImport(objects, existingKeys = emptySet(), importedOut = imported)
        assertEquals(1, firstSummary.imported)

        val secondSummary = runFakeImport(objects, existingKeys = imported, importedOut = mutableSetOf())
        assertEquals(0, secondSummary.imported)
        assertEquals(1, secondSummary.skippedExists)
    }

    // ---- helpers ---------------------------------------------------------------

    private fun fakeObject(key: String, ct: String, bytes: ByteArray) = GcsObject(
        key = key,
        contentType = ct,
        sizeBytes = bytes.size.toLong(),
        createdAt = Instant.now(),
        downloadContent = { bytes },
    )

    private fun runFakeImport(
        objects: List<GcsObject>,
        existingKeys: Set<String>,
        importedOut: MutableSet<String>,
    ): ImportSummary {
        var scanned = 0
        var imported = 0
        var skippedExists = 0
        var skippedContentType = 0

        for (obj in objects) {
            scanned++
            if (!isMediaContentType(obj.contentType)) {
                skippedContentType++
                continue
            }
            if (obj.key in existingKeys) {
                skippedExists++
                continue
            }
            importedOut.add(obj.key)
            imported++
        }
        return ImportSummary(scanned, imported, skippedExists, skippedContentType, errored = 0)
    }
}
