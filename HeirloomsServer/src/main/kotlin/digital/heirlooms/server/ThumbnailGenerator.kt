package digital.heirlooms.server

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

val THUMBNAIL_SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
private const val THUMBNAIL_MAX_SIDE = 400

fun generateThumbnail(bytes: ByteArray, mimeType: String): ByteArray? {
    if (mimeType.substringBefore(";").trim().lowercase() !in THUMBNAIL_SUPPORTED_MIME_TYPES) return null
    return try {
        val original = ImageIO.read(bytes.inputStream()) ?: return null
        val w = original.width
        val h = original.height
        val scale = minOf(THUMBNAIL_MAX_SIDE.toDouble() / w, THUMBNAIL_MAX_SIDE.toDouble() / h, 1.0)
        val tw = maxOf(1, (w * scale).toInt())
        val th = maxOf(1, (h * scale).toInt())
        val thumb = BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB)
        val g = thumb.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(original, 0, 0, tw, th, null)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(thumb, "JPEG", out)
        out.toByteArray()
    } catch (_: Exception) {
        null
    }
}
