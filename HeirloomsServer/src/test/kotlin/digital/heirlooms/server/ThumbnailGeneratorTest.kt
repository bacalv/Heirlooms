package digital.heirlooms.server

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
