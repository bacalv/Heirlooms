package digital.heirlooms.server.storage

/**
 * Maps a MIME type string to a file extension (without the leading dot).
 * Falls back to "bin" for unknown types so files are always written with
 * some extension rather than none.
 */
fun mimeTypeToExtension(mimeType: String): String {
    val base = mimeType.substringBefore(";").trim().lowercase()
    return MIME_TO_EXT[base] ?: run {
        // Best-effort: use the subtype if it looks like a plain word (e.g. "png" from "image/png")
        val subtype = base.substringAfter("/")
        if (subtype.matches(Regex("[a-z0-9]+"))) subtype else "bin"
    }
}

private val MIME_TO_EXT = mapOf(
    // Images
    "image/jpeg"    to "jpg",
    "image/jpg"     to "jpg",
    "image/png"     to "png",
    "image/gif"     to "gif",
    "image/webp"    to "webp",
    "image/heic"    to "heic",
    "image/heif"    to "heif",
    "image/bmp"     to "bmp",
    "image/tiff"    to "tiff",
    "image/svg+xml" to "svg",

    // Videos
    "video/mp4"         to "mp4",
    "video/quicktime"   to "mov",
    "video/x-msvideo"   to "avi",
    "video/x-matroska"  to "mkv",
    "video/webm"        to "webm",
    "video/3gpp"        to "3gp",
    "video/3gpp2"       to "3g2",
    "video/mpeg"        to "mpeg",
    "video/ogg"         to "ogv",

    // Fallback
    "application/octet-stream" to "bin",
)
