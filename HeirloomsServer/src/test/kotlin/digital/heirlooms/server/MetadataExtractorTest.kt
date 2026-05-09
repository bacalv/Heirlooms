package digital.heirlooms.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class MetadataExtractorTest {

    private val extractor = MetadataExtractor()

    // A minimal JPEG + EXIF APP1 with GPS data:
    //   lat = 51.5074°N (51°30'26.64"N)  lon = 0.1278°W (0°7'40.08"W)  alt = 12.5m
    private fun createGpsJpegBytes(): ByteArray {
        val tiff = byteArrayOf(
            // TIFF header: II (little-endian), magic=42, IFD0 at offset 8
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,
            // IFD0: 1 entry
            0x01, 0x00,
            // GPS IFD pointer: tag=0x8825, type=LONG, count=1, value=26
            0x25.toByte(), 0x88.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x1A, 0x00, 0x00, 0x00,
            // IFD0 next IFD = 0
            0x00, 0x00, 0x00, 0x00,
            // GPS IFD at offset 26: 7 entries
            0x07, 0x00,
            // Entry 0: GPS Version (BYTE, 4, inline 2,3,0,0)
            0x00, 0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x02, 0x03, 0x00, 0x00,
            // Entry 1: GPS Lat Ref (ASCII, 2, "N")
            0x01, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x00, 0x00,
            // Entry 2: GPS Latitude (RATIONAL, 3, offset=116)
            0x02, 0x00, 0x05, 0x00, 0x03, 0x00, 0x00, 0x00, 0x74, 0x00, 0x00, 0x00,
            // Entry 3: GPS Lon Ref (ASCII, 2, "W")
            0x03, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00, 0x00, 0x57, 0x00, 0x00, 0x00,
            // Entry 4: GPS Longitude (RATIONAL, 3, offset=140)
            0x04, 0x00, 0x05, 0x00, 0x03, 0x00, 0x00, 0x00, 0x8C.toByte(), 0x00, 0x00, 0x00,
            // Entry 5: GPS Alt Ref (BYTE, 1, value=0 = above sea level)
            0x05, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // Entry 6: GPS Altitude (RATIONAL, 1, offset=164)
            0x06, 0x00, 0x05, 0x00, 0x01, 0x00, 0x00, 0x00, 0xA4.toByte(), 0x00, 0x00, 0x00,
            // GPS IFD next = 0
            0x00, 0x00, 0x00, 0x00,
            // Latitude rationals at offset 116: 51/1, 30/1, 2664/100
            0x33, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x1E, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x68, 0x0A, 0x00, 0x00, 0x64, 0x00, 0x00, 0x00,
            // Longitude rationals at offset 140: 0/1, 7/1, 4008/100
            0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x07, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0xA8.toByte(), 0x0F, 0x00, 0x00, 0x64, 0x00, 0x00, 0x00,
            // Altitude rational at offset 164: 125/10
            0x7D, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00,
        )
        val app1Body = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) + tiff // "Exif\0\0" + TIFF
        val app1Length = app1Body.size + 2
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE1.toByte(),
            (app1Length shr 8).toByte(), (app1Length and 0xFF).toByte(),
        ) + app1Body + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    private fun createPlainJpegBytes(): ByteArray {
        val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, 10, 10)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "JPEG", out)
        return out.toByteArray()
    }

    @Test
    fun `JPEG with GPS EXIF returns correct lat, lon, and altitude`() {
        val result = extractor.extract(createGpsJpegBytes(), "image/jpeg")

        assertEquals(51.5074, result.latitude!!, 0.001)
        assertEquals(-0.1278, result.longitude!!, 0.001)
        assertEquals(12.5, result.altitude!!, 0.1)
    }

    @Test
    fun `JPEG without GPS data returns null coordinates`() {
        val result = extractor.extract(createPlainJpegBytes(), "image/jpeg")

        assertNull(result.latitude)
        assertNull(result.longitude)
        assertNull(result.altitude)
    }

    @Test
    fun `unsupported MIME type returns all nulls`() {
        val result = extractor.extract(byteArrayOf(1, 2, 3), "audio/mpeg")

        assertNull(result.takenAt)
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertNull(result.altitude)
        assertNull(result.deviceMake)
        assertNull(result.deviceModel)
    }

    @Test
    fun `invalid image bytes return all nulls`() {
        val result = extractor.extract("not-an-image".toByteArray(), "image/jpeg")

        assertNull(result.latitude)
        assertNull(result.longitude)
    }
}
