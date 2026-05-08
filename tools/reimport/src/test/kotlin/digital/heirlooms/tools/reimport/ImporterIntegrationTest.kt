package digital.heirlooms.tools.reimport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import javax.sql.DataSource

@Testcontainers
class ImporterIntegrationTest {

    companion object {
        @Container
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var dataSource: DataSource

        @BeforeAll
        @JvmStatic
        fun setup() {
            val hikari = HikariConfig().apply {
                jdbcUrl      = postgres.jdbcUrl
                username     = postgres.username
                password     = postgres.password
                maximumPoolSize = 3
            }
            dataSource = HikariDataSource(hikari)
            createSchema()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            (dataSource as HikariDataSource).close()
        }

        private fun createSchema() {
            dataSource.connection.use { conn ->
                conn.createStatement().execute(
                    """
                    CREATE TABLE IF NOT EXISTS uploads (
                        id            UUID PRIMARY KEY,
                        storage_key   VARCHAR(512)   NOT NULL,
                        mime_type     VARCHAR(128)   NOT NULL,
                        file_size     BIGINT         NOT NULL,
                        uploaded_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                        content_hash  VARCHAR(64),
                        thumbnail_key VARCHAR(512),
                        captured_at   TIMESTAMPTZ,
                        latitude      DOUBLE PRECISION,
                        longitude     DOUBLE PRECISION,
                        altitude      DOUBLE PRECISION,
                        device_make   VARCHAR(128),
                        device_model  VARCHAR(128),
                        rotation      INT            NOT NULL DEFAULT 0,
                        tags          TEXT[]         NOT NULL DEFAULT '{}',
                        composted_at  TIMESTAMPTZ
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun clearUploads() {
        dataSource.connection.use { conn ->
            conn.createStatement().execute("DELETE FROM uploads")
        }
    }

    private fun countUploads(): Int =
        dataSource.connection.use { conn ->
            val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM uploads")
            rs.next()
            rs.getInt(1)
        }

    private fun fakeBucketReader(vararg objects: GcsObject): BucketReader = object : BucketReader {
        override fun listObjects(): Sequence<GcsObject> = objects.asSequence()
        override fun objectExists(key: String): Boolean = objects.any { it.key == key }
    }

    private fun fakeObject(key: String, ct: String, content: String = "fake-content-$key") =
        GcsObject(
            key = key,
            contentType = ct,
            sizeBytes = content.length.toLong(),
            createdAt = Instant.now(),
            downloadContent = { content.toByteArray() },
        )

    // ---- import phase ----------------------------------------------------------

    @Test
    fun `import inserts rows for image blobs`() {
        clearUploads()
        val reader = fakeBucketReader(
            fakeObject("photo1.jpg", "image/jpeg"),
            fakeObject("photo2.png", "image/png"),
        )
        val summary = Importer(reader, dataSource).runImport()

        assertEquals(2, summary.imported)
        assertEquals(2, countUploads())
    }

    @Test
    fun `import inserts rows for video blobs`() {
        clearUploads()
        val reader = fakeBucketReader(fakeObject("clip.mp4", "video/mp4"))
        val summary = Importer(reader, dataSource).runImport()

        assertEquals(1, summary.imported)
        assertEquals(1, countUploads())
    }

    @Test
    fun `import skips blobs with non-media content types`() {
        clearUploads()
        val reader = fakeBucketReader(
            fakeObject("doc.pdf",  "application/pdf"),
            fakeObject("data.json", "application/json"),
        )
        val summary = Importer(reader, dataSource).runImport()

        assertEquals(0, summary.imported)
        assertEquals(2, summary.skippedContentType)
        assertEquals(0, countUploads())
    }

    @Test
    fun `import skips blobs that already have a DB row`() {
        clearUploads()
        val reader = fakeBucketReader(fakeObject("existing.jpg", "image/jpeg"))

        // First run populates the row
        Importer(reader, dataSource).runImport()
        assertEquals(1, countUploads())

        // Second run must skip it
        val secondSummary = Importer(reader, dataSource).runImport()
        assertEquals(0, secondSummary.imported)
        assertEquals(1, secondSummary.skippedExists)
        assertEquals(1, countUploads())
    }

    @Test
    fun `import stores correct metadata`() {
        clearUploads()
        val content = "jpeg-bytes".toByteArray()
        val expectedHash = sha256Hex(content)
        val expectedSize = content.size.toLong()

        val reader = fakeBucketReader(
            GcsObject("meta.jpg", "image/jpeg", expectedSize, Instant.parse("2026-01-15T10:00:00Z")) { content }
        )
        Importer(reader, dataSource).runImport()

        dataSource.connection.use { conn ->
            val rs = conn.createStatement()
                .executeQuery("SELECT storage_key, mime_type, file_size, content_hash FROM uploads LIMIT 1")
            assertTrue(rs.next())
            assertEquals("meta.jpg",   rs.getString("storage_key"))
            assertEquals("image/jpeg", rs.getString("mime_type"))
            assertEquals(expectedSize, rs.getLong("file_size"))
            assertEquals(expectedHash, rs.getString("content_hash"))
        }
    }

    // ---- verify phase ----------------------------------------------------------

    @Test
    fun `verify reports count parity when import is complete`() {
        clearUploads()
        val reader = fakeBucketReader(
            fakeObject("v1.jpg", "image/jpeg"),
            fakeObject("v2.mp4", "video/mp4"),
        )
        Importer(reader, dataSource).runImport()
        val summary = Importer(reader, dataSource).runVerify()

        assertEquals(2, summary.gcsCount)
        assertEquals(2, summary.dbCount)
        assertTrue(summary.countParityOk)
    }

    @Test
    fun `verify detects count mismatch when DB has fewer rows`() {
        clearUploads()
        val reader = fakeBucketReader(
            fakeObject("a.jpg", "image/jpeg"),
            fakeObject("b.jpg", "image/jpeg"),
        )
        // Only import one of the two
        val partialReader = fakeBucketReader(fakeObject("a.jpg", "image/jpeg"))
        Importer(partialReader, dataSource).runImport()

        val summary = Importer(reader, dataSource).runVerify()
        assertEquals(2, summary.gcsCount)
        assertEquals(1, summary.dbCount)
        assertTrue(!summary.countParityOk)
    }

    @Test
    fun `verify sample integrity passes for imported rows`() {
        clearUploads()
        val reader = fakeBucketReader(
            fakeObject("s1.jpg", "image/jpeg", "unique-content-s1"),
            fakeObject("s2.jpg", "image/jpeg", "unique-content-s2"),
            fakeObject("s3.jpg", "image/jpeg", "unique-content-s3"),
        )
        Importer(reader, dataSource).runImport()
        val summary = Importer(reader, dataSource).runVerify()

        assertTrue(summary.sampleChecked > 0)
        assertEquals(summary.sampleChecked, summary.samplePassed)
    }
}
