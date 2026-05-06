package digital.heirlooms.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        val result = generateThumbnail(byteArrayOf(1, 2, 3), "video/mp4")
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
}
