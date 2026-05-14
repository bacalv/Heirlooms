package digital.heirlooms.server.service.upload

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import java.io.File
import java.time.Instant
import java.util.TimeZone
import java.util.concurrent.TimeUnit

val METADATA_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/heic", "image/webp")
private val METADATA_VIDEO_MIME_TYPES = setOf("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm")
val METADATA_SUPPORTED_MIME_TYPES = METADATA_IMAGE_MIME_TYPES + METADATA_VIDEO_MIME_TYPES

data class MediaMetadata(
    val takenAt: Instant? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
)

class MetadataExtractor {

    private val mapper = ObjectMapper()

    fun extract(bytes: ByteArray, mimeType: String): MediaMetadata {
        val normalized = mimeType.substringBefore(";").trim().lowercase()
        return when (normalized) {
            in METADATA_IMAGE_MIME_TYPES -> extractFromImage(bytes)
            in METADATA_VIDEO_MIME_TYPES -> extractFromVideo(bytes, normalized)
            else -> MediaMetadata()
        }
    }

    private fun extractFromImage(bytes: ByteArray): MediaMetadata {
        return try {
            val metadata = ImageMetadataReader.readMetadata(bytes.inputStream())

            val gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
            val geoLocation = gpsDir?.geoLocation
            val rawLat = geoLocation?.latitude?.takeIf { !it.isNaN() }
            val rawLon = geoLocation?.longitude?.takeIf { !it.isNaN() }
            // (0.0, 0.0) is a Samsung placeholder written when the GPS fix isn't yet acquired
            val (latitude, longitude) = if (rawLat == 0.0 && rawLon == 0.0) Pair(null, null) else Pair(rawLat, rawLon)
            val altitude = if (latitude != null) {
                gpsDir?.getRational(GpsDirectory.TAG_ALTITUDE)?.let { rational ->
                    val altRef = gpsDir.getByteArray(GpsDirectory.TAG_ALTITUDE_REF)
                    val sign = if (altRef != null && altRef.isNotEmpty() && altRef[0].toInt() == 1) -1.0 else 1.0
                    sign * rational.toDouble()
                }
            } else null

            val takenAt = extractTakenAt(metadata)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            val make = ifd0?.getString(ExifIFD0Directory.TAG_MAKE)?.trim()?.takeIf { it.isNotBlank() }
            val model = ifd0?.getString(ExifIFD0Directory.TAG_MODEL)?.trim()?.takeIf { it.isNotBlank() }

            MediaMetadata(takenAt, latitude, longitude, altitude, make, model)
        } catch (_: Exception) {
            MediaMetadata()
        }
    }

    private fun extractFromVideo(bytes: ByteArray, mimeType: String): MediaMetadata {
        val ext = when (mimeType) {
            "video/quicktime" -> ".mov"
            "video/x-msvideo" -> ".avi"
            "video/webm" -> ".webm"
            else -> ".mp4"
        }
        var tempFile: File? = null
        return try {
            tempFile = File.createTempFile("heirloom-meta-", ext)
            tempFile.writeBytes(bytes)
            val proc = ProcessBuilder(
                "ffprobe", "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", tempFile.absolutePath
            ).redirectErrorStream(true).start()
            val json = proc.inputStream.bufferedReader().readText()
            val finished = proc.waitFor(30, TimeUnit.SECONDS)
            if (!finished || proc.exitValue() != 0) return MediaMetadata()
            parseFFprobeOutput(json)
        } catch (_: Exception) {
            MediaMetadata()
        } finally {
            tempFile?.delete()
        }
    }

    private fun parseFFprobeOutput(json: String): MediaMetadata {
        return try {
            val root = mapper.readTree(json)
            val tags = root.path("format").path("tags")

            val takenAt = tags.path("creation_time").takeIf { !it.isMissingOrNull() }?.asText()?.let { parseInstant(it) }

            val locationStr = tags.path("location").takeIf { !it.isMissingOrNull() }?.asText()
                ?: tags.path("com.apple.quicktime.location.ISO6709").takeIf { !it.isMissingOrNull() }?.asText()
            val (latitude, longitude, altitude) = parseISO6709(locationStr)

            val make = tags.path("com.apple.quicktime.make").takeIf { !it.isMissingOrNull() }?.asText()?.trim()?.takeIf { it.isNotBlank() }
            val model = tags.path("com.apple.quicktime.model").takeIf { !it.isMissingOrNull() }?.asText()?.trim()?.takeIf { it.isNotBlank() }

            MediaMetadata(takenAt, latitude, longitude, altitude, make, model)
        } catch (_: Exception) {
            MediaMetadata()
        }
    }

    private fun extractTakenAt(metadata: com.drew.metadata.Metadata): Instant? {
        val utc = TimeZone.getTimeZone("UTC")
        // 1. SubIFD DateTimeOriginal — standard capture time tag
        metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            ?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, utc)
            ?.toInstant()
            ?.let { return it }
        // 2. IFD0 DateTime — Samsung and some Android cameras write here instead of SubIFD
        metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.getDate(ExifDirectoryBase.TAG_DATETIME, utc)
            ?.toInstant()
            ?.let { return it }
        // 3. SubIFD DateTimeDigitized — last resort
        metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            ?.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED, utc)
            ?.toInstant()
            ?.let { return it }
        return null
    }

    private fun JsonNode.isMissingOrNull() = this is MissingNode || this is NullNode

    private fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: Exception) {
        null
    }

    private fun parseISO6709(location: String?): Triple<Double?, Double?, Double?> {
        if (location.isNullOrBlank()) return Triple(null, null, null)
        val regex = Regex("""([+-]\d+(?:\.\d*)?)([+-]\d+(?:\.\d*)?)([+-]\d+(?:\.\d*)?)?""")
        val match = regex.find(location) ?: return Triple(null, null, null)
        val lat = match.groupValues[1].toDoubleOrNull() ?: return Triple(null, null, null)
        val lon = match.groupValues[2].toDoubleOrNull() ?: return Triple(null, null, null)
        val alt = match.groupValues[3].toDoubleOrNull()
        return Triple(lat, lon, alt)
    }
}
