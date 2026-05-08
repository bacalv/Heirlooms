package digital.heirlooms.tools.reimport

import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

private const val PROGRESS_EVERY = 50

data class ImportSummary(
    val scanned: Int,
    val imported: Int,
    val skippedExists: Int,
    val skippedContentType: Int,
    val errored: Int,
)

data class VerifySummary(
    val gcsCount: Int,
    val dbCount: Int,
    val countParityOk: Boolean,
    val sampleChecked: Int,
    val samplePassed: Int,
)

class Importer(
    private val reader: BucketReader,
    private val dataSource: DataSource,
) {

    fun runImport(): ImportSummary {
        var scanned = 0
        var imported = 0
        var skippedExists = 0
        var skippedContentType = 0
        var errored = 0

        for (obj in reader.listObjects()) {
            scanned++
            if (scanned % PROGRESS_EVERY == 0) {
                log("progress: $scanned objects processed so far (imported=$imported, skippedExists=$skippedExists, skippedContentType=$skippedContentType, errored=$errored)")
            }

            val ct = obj.contentType ?: ""
            if (!ct.startsWith("image/") && !ct.startsWith("video/")) {
                log("skip [content-type]: ${obj.key} ($ct)")
                skippedContentType++
                continue
            }

            try {
                if (rowExistsForKey(obj.key)) {
                    log("skip [exists]: ${obj.key}")
                    skippedExists++
                    continue
                }

                val bytes = obj.downloadContent()
                val hash = sha256Hex(bytes)
                insertUpload(obj, hash)
                log("imported: ${obj.key}")
                imported++
            } catch (e: Exception) {
                log("ERROR: ${obj.key} — ${e.message}")
                errored++
            }
        }

        log("---")
        log("summary: scanned=$scanned imported=$imported skippedExists=$skippedExists skippedContentType=$skippedContentType errored=$errored")
        return ImportSummary(scanned, imported, skippedExists, skippedContentType, errored)
    }

    fun runVerify(): VerifySummary {
        var gcsCount = 0
        for (obj in reader.listObjects()) {
            val ct = obj.contentType ?: ""
            if (ct.startsWith("image/") || ct.startsWith("video/")) gcsCount++
        }

        val dbCount = dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM uploads WHERE composted_at IS NULL").use { stmt ->
                val rs = stmt.executeQuery()
                rs.next()
                rs.getInt(1)
            }
        }

        val parityOk = gcsCount == dbCount
        if (!parityOk) {
            log("WARNING: count mismatch — GCS has $gcsCount media objects, DB has $dbCount non-composted rows (delta: ${gcsCount - dbCount})")
        } else {
            log("count parity OK: $gcsCount GCS media objects, $dbCount DB rows")
        }

        val (sampleChecked, samplePassed) = runSampleIntegrity()
        log("sample integrity: checked=$sampleChecked passed=$samplePassed")

        return VerifySummary(gcsCount, dbCount, parityOk, sampleChecked, samplePassed)
    }

    private fun runSampleIntegrity(): Pair<Int, Int> {
        data class SampleRow(val storageKey: String, val contentHash: String?)
        val rows = dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT storage_key, content_hash FROM uploads WHERE composted_at IS NULL ORDER BY RANDOM() LIMIT 5"
            ).use { stmt ->
                val rs = stmt.executeQuery()
                val list = mutableListOf<SampleRow>()
                while (rs.next()) list.add(SampleRow(rs.getString("storage_key"), rs.getString("content_hash")))
                list
            }
        }

        var passed = 0
        for (row in rows) {
            try {
                if (!reader.objectExists(row.storageKey)) {
                    log("sample FAIL: ${row.storageKey} — object not found in GCS")
                    continue
                }
                if (row.contentHash != null) {
                    val obj = reader.listObjects().firstOrNull { it.key == row.storageKey }
                    val bytes = obj?.downloadContent?.invoke()
                    if (bytes == null) {
                        log("sample FAIL: ${row.storageKey} — could not download")
                        continue
                    }
                    val computed = sha256Hex(bytes)
                    if (computed == row.contentHash) {
                        log("sample OK: ${row.storageKey} (hash match)")
                        passed++
                    } else {
                        log("sample FAIL: ${row.storageKey} — hash mismatch (stored=${row.contentHash}, computed=$computed)")
                    }
                } else {
                    log("sample OK: ${row.storageKey} (object exists; no hash to verify)")
                    passed++
                }
            } catch (e: Exception) {
                log("sample ERROR: ${row.storageKey} — ${e.message}")
            }
        }
        return Pair(rows.size, passed)
    }

    private fun rowExistsForKey(storageKey: String): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM uploads WHERE storage_key = ? LIMIT 1").use { stmt ->
                stmt.setString(1, storageKey)
                stmt.executeQuery().next()
            }
        }

    private fun insertUpload(obj: GcsObject, contentHash: String) {
        dataSource.connection.use { conn: Connection ->
            conn.prepareStatement(
                "INSERT INTO uploads (id, storage_key, mime_type, file_size, uploaded_at, content_hash) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setString(2, obj.key)
                stmt.setString(3, obj.contentType ?: "application/octet-stream")
                stmt.setLong(4, obj.sizeBytes)
                stmt.setTimestamp(5, Timestamp.from(obj.createdAt))
                stmt.setString(6, contentHash)
                stmt.executeUpdate()
            }
        }
    }
}

internal fun isMediaContentType(contentType: String?): Boolean {
    val ct = contentType ?: return false
    return ct.startsWith("image/") || ct.startsWith("video/")
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun log(msg: String) = println("[reimport] $msg")
