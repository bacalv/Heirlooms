package digital.heirlooms.server.service.upload

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

private val THUMBNAIL_VIDEO_MIME_TYPES = setOf("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm")
val THUMBNAIL_SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp") + THUMBNAIL_VIDEO_MIME_TYPES
private const val THUMBNAIL_MAX_SIDE = 400

fun generateThumbnail(bytes: ByteArray, mimeType: String): ByteArray? {
    val normalizedMime = mimeType.substringBefore(";").trim().lowercase()
    if (normalizedMime !in THUMBNAIL_SUPPORTED_MIME_TYPES) return null
    return if (normalizedMime in THUMBNAIL_VIDEO_MIME_TYPES) {
        extractVideoThumbnail(bytes, normalizedMime)
    } else {
        extractImageThumbnail(bytes)
    }
}

private fun extractImageThumbnail(bytes: ByteArray): ByteArray? {
    return try {
        val original = ImageIO.read(bytes.inputStream()) ?: return null
        val orientation = readExifOrientation(bytes)
        val normalised = applyOrientation(original, orientation)
        scaleAndEncode(normalised)
    } catch (_: Exception) { null }
}

/**
 * Reads the EXIF Orientation tag from the raw image bytes.
 * Returns 1 (normal) if the tag is absent or unreadable.
 */
internal fun readExifOrientation(bytes: ByteArray): Int {
    return try {
        val metadata = ImageMetadataReader.readMetadata(bytes.inputStream())
        val dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        dir?.getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
    } catch (_: Exception) { 1 }
}

/**
 * Applies EXIF orientation to a BufferedImage, returning a new image with
 * the rotation/flip baked into the pixels. EXIF orientation values:
 *   1 = normal, 2 = mirror-H, 3 = rotate-180, 4 = mirror-V,
 *   5 = mirror-H + rotate-270, 6 = rotate-90-CW, 7 = mirror-H + rotate-90-CW,
 *   8 = rotate-270-CW
 */
internal fun applyOrientation(image: BufferedImage, orientation: Int): BufferedImage {
    if (orientation == 1) return image

    val needsSwap = orientation in 5..8
    val destW = if (needsSwap) image.height else image.width
    val destH = if (needsSwap) image.width else image.height

    val result = BufferedImage(destW, destH, BufferedImage.TYPE_INT_RGB)
    val g = result.createGraphics()
    val tx = AffineTransform()

    when (orientation) {
        2 -> { tx.scale(-1.0, 1.0); tx.translate(-image.width.toDouble(), 0.0) }
        3 -> { tx.translate(image.width.toDouble(), image.height.toDouble()); tx.rotate(Math.PI) }
        4 -> { tx.scale(1.0, -1.0); tx.translate(0.0, -image.height.toDouble()) }
        5 -> { tx.rotate(-Math.PI / 2); tx.scale(-1.0, 1.0) }
        6 -> { tx.translate(image.height.toDouble(), 0.0); tx.rotate(Math.PI / 2) }
        7 -> { tx.rotate(Math.PI / 2); tx.translate(0.0, -image.height.toDouble()); tx.scale(-1.0, 1.0) }
        8 -> { tx.translate(0.0, image.width.toDouble()); tx.rotate(-Math.PI / 2) }
    }

    g.drawImage(image, tx, null)
    g.dispose()
    return result
}

private fun extractVideoThumbnail(bytes: ByteArray, mimeType: String): ByteArray? {
    val ext = when (mimeType) {
        "video/quicktime" -> ".mov"
        "video/x-msvideo" -> ".avi"
        "video/webm" -> ".webm"
        else -> ".mp4"
    }
    var inputFile: File? = null
    var outputFile: File? = null
    try {
        inputFile = File.createTempFile("heirloom-video-", ext)
        outputFile = File.createTempFile("heirloom-thumb-", ".jpg")
        inputFile.writeBytes(bytes)
        val process = ProcessBuilder(
            "ffmpeg", "-y", "-i", inputFile.absolutePath,
            "-vframes", "1", "-f", "image2", outputFile.absolutePath
        ).redirectErrorStream(true).start()
        val exited = process.waitFor(30, TimeUnit.SECONDS)
        if (!exited || process.exitValue() != 0) return null
        if (outputFile.length() == 0L) return null
        val frameBytes = outputFile.readBytes()
        val original = ImageIO.read(frameBytes.inputStream()) ?: return null
        return scaleAndEncode(original)
    } catch (_: Exception) {
        return null
    } finally {
        inputFile?.delete()
        outputFile?.delete()
    }
}

// Extracts video duration in whole seconds using ffprobe. Returns null on any error.
fun extractVideoDuration(bytes: ByteArray, mimeType: String): Int? {
    val ext = when (mimeType.substringBefore(";").trim().lowercase()) {
        "video/quicktime" -> ".mov"
        "video/x-msvideo" -> ".avi"
        "video/webm"      -> ".webm"
        else              -> ".mp4"
    }
    var inputFile: File? = null
    try {
        inputFile = File.createTempFile("heirloom-dur-", ext)
        inputFile.writeBytes(bytes)
        val process = ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            inputFile.absolutePath,
        ).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exited = process.waitFor(30, TimeUnit.SECONDS)
        if (!exited || process.exitValue() != 0) return null
        return output.toDoubleOrNull()?.let { Math.round(it).toInt() }
    } catch (_: Exception) {
        return null
    } finally {
        inputFile?.delete()
    }
}

private fun scaleAndEncode(original: BufferedImage): ByteArray? = try {
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
} catch (_: Exception) { null }
