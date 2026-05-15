package digital.heirlooms.server

import digital.heirlooms.server.service.upload.applyOrientation
import digital.heirlooms.server.service.upload.generateThumbnail
import digital.heirlooms.server.service.upload.readExifOrientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class ThumbnailGeneratorTest {

    private fun createJpegBytes(width: Int, height: Int, color: Color = Color.BLUE): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "JPEG", out)
        return out.toByteArray()
    }

    /**
     * Creates a JPEG with a minimal EXIF APP1 segment that encodes the given
     * orientation value (1–8). The APP1 is injected immediately after the SOI
     * marker so that metadata-extractor can read it.
     *
     * TIFF/EXIF layout (little-endian):
     *   "II" (0x49 0x49) + 0x2A 0x00 + offset-to-IFD0 (0x08 0x00 0x00 0x00)
     *   IFD0: 1 entry count, TAG_ORIENTATION (0x0112), type SHORT (3),
     *         count 1, value = orientation, next-IFD offset 0
     */
    private fun createJpegWithExifOrientation(width: Int, height: Int, orientation: Int): ByteArray {
        val jpegBytes = createJpegBytes(width, height)

        // Build TIFF/EXIF block (little-endian)
        val tiff = ByteBuffer.allocate(8 + 2 + 12 + 4).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put('I'.code.toByte()); tiff.put('I'.code.toByte()) // byte order: little-endian
        tiff.putShort(0x002A)                                    // TIFF magic
        tiff.putInt(8)                                           // offset to IFD0

        // IFD0
        tiff.putShort(1)                                         // entry count
        tiff.putShort(0x0112)                                    // TAG_ORIENTATION
        tiff.putShort(3)                                         // type SHORT
        tiff.putInt(1)                                           // count
        tiff.putShort(orientation.toShort())                     // value (packed in value field)
        tiff.putShort(0)                                         // padding to 4 bytes
        tiff.putInt(0)                                           // next IFD offset (none)

        val tiffBytes = tiff.array()

        // Build APP1 segment: marker + length (2 bytes) + "Exif\0\0" + TIFF
        val exifHeader = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // "Exif\0\0"
        val app1Data = exifHeader + tiffBytes
        val app1Length = (app1Data.size + 2).toShort() // length field includes itself

        val app1 = ByteBuffer.allocate(2 + 2 + app1Data.size).order(ByteOrder.BIG_ENDIAN)
        app1.put(0xFF.toByte()); app1.put(0xE1.toByte())         // APP1 marker
        app1.putShort(app1Length)
        app1.put(app1Data)
        val app1Bytes = app1.array()

        // Insert APP1 after SOI (first 2 bytes: FF D8)
        return jpegBytes.copyOfRange(0, 2) + app1Bytes + jpegBytes.copyOfRange(2, jpegBytes.size)
    }

    @Test
    fun `returns non-null for valid JPEG`() {
        val result = generateThumbnail(createJpegBytes(200, 200), "image/jpeg")
        assertNotNull(result)
    }

    @Test
    fun `output is valid JPEG`() {
        val result = generateThumbnail(createJpegBytes(200, 200), "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertNotNull(decoded)
    }

    @Test
    fun `returns null for unsupported MIME type`() {
        val result = generateThumbnail(byteArrayOf(1, 2, 3), "audio/mpeg")
        assertNull(result)
    }

    @Test
    fun `returns null for invalid image bytes`() {
        val result = generateThumbnail("not-an-image".toByteArray(), "image/jpeg")
        assertNull(result)
    }

    @Test
    fun `thumbnail fits within 400x400 bounding box`() {
        val result = generateThumbnail(createJpegBytes(1200, 800), "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertTrue(decoded.width <= 400)
        assertTrue(decoded.height <= 400)
    }

    @Test
    fun `thumbnail preserves aspect ratio for wide image`() {
        // 800x400 — 2:1 ratio — expected 400x200
        val result = generateThumbnail(createJpegBytes(800, 400), "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        val ratio = decoded.width.toDouble() / decoded.height
        assertEquals(2.0, ratio, 0.05)
    }

    @Test
    fun `image smaller than 400x400 is not scaled up`() {
        val result = generateThumbnail(createJpegBytes(100, 100), "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(100, decoded.width)
        assertEquals(100, decoded.height)
    }

    @Test
    fun `returns null for application-octet-stream`() {
        assertNull(generateThumbnail(createJpegBytes(100, 100), "application/octet-stream"))
    }

    @Test
    fun `valid MP4 produces non-null thumbnail`() {
        assumeTrue(isFFmpegAvailable(), "ffmpeg not available — skipping video thumbnail test")
        val mp4Bytes = createTestMp4() ?: return
        val result = generateThumbnail(mp4Bytes, "video/mp4")
        assertNotNull(result)
    }

    @Test
    fun `corrupt video returns null gracefully`() {
        val result = generateThumbnail("not-a-video".toByteArray(), "video/mp4")
        assertNull(result)
    }

    // ---- EXIF orientation tests ----

    @Test
    fun `readExifOrientation returns 1 for plain JPEG without EXIF`() {
        val bytes = createJpegBytes(100, 100)
        assertEquals(1, readExifOrientation(bytes))
    }

    @Test
    fun `readExifOrientation reads embedded orientation tag`() {
        for (orientation in 1..8) {
            val bytes = createJpegWithExifOrientation(100, 100, orientation)
            assertEquals(orientation, readExifOrientation(bytes),
                "Expected orientation $orientation to round-trip via EXIF")
        }
    }

    @Test
    fun `applyOrientation returns same image for orientation 1`() {
        val img = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val result = applyOrientation(img, 1)
        assertEquals(200, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun `applyOrientation rotates 90 CW (orientation 6) swaps dimensions`() {
        // A 200x100 image rotated 90° CW becomes 100x200
        val img = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val result = applyOrientation(img, 6)
        assertEquals(100, result.width)
        assertEquals(200, result.height)
    }

    @Test
    fun `applyOrientation rotates 270 CW (orientation 8) swaps dimensions`() {
        val img = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val result = applyOrientation(img, 8)
        assertEquals(100, result.width)
        assertEquals(200, result.height)
    }

    @Test
    fun `applyOrientation rotates 180 (orientation 3) preserves dimensions`() {
        val img = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val result = applyOrientation(img, 3)
        assertEquals(200, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun `thumbnail honours EXIF orientation 6 — rotated image has portrait dimensions`() {
        // Landscape JPEG (200x100) with orientation=6 (rotate 90° CW) should produce
        // a portrait thumbnail (height > width)
        val bytes = createJpegWithExifOrientation(200, 100, 6)
        val result = generateThumbnail(bytes, "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertTrue(decoded.height > decoded.width,
            "Expected portrait thumbnail (h>w) for landscape JPEG with orientation=6, " +
            "got ${decoded.width}x${decoded.height}")
    }

    @Test
    fun `thumbnail honours EXIF orientation 8 — rotated image has portrait dimensions`() {
        val bytes = createJpegWithExifOrientation(200, 100, 8)
        val result = generateThumbnail(bytes, "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertTrue(decoded.height > decoded.width,
            "Expected portrait thumbnail (h>w) for landscape JPEG with orientation=8, " +
            "got ${decoded.width}x${decoded.height}")
    }

    @Test
    fun `thumbnail honours EXIF orientation 3 — 180 degree rotation preserves aspect ratio`() {
        // 200x100 + orientation 3 (180°) → still 200x100 (2:1 ratio)
        val bytes = createJpegWithExifOrientation(200, 100, 3)
        val result = generateThumbnail(bytes, "image/jpeg")!!
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        val ratio = decoded.width.toDouble() / decoded.height
        assertEquals(2.0, ratio, 0.05)
    }

    private fun isFFmpegAvailable(): Boolean = try {
        ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0
    } catch (_: Exception) { false }

    private fun createTestMp4(): ByteArray? {
        val output = File.createTempFile("test-video", ".mp4")
        return try {
            val proc = ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "lavfi", "-i", "color=c=black:size=16x16:rate=1:duration=1",
                "-vframes", "1",
                output.absolutePath
            ).redirectErrorStream(true).start()
            val exited = proc.waitFor(10, TimeUnit.SECONDS)
            if (!exited || proc.exitValue() != 0) null
            else output.readBytes().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            output.delete()
        }
    }
}
