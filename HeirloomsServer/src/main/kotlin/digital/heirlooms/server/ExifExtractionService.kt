package digital.heirlooms.server

import digital.heirlooms.server.domain.upload.UploadRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

private const val EXIF_PARTIAL_BYTES = 65_536

class ExifExtractionService(
    private val database: Database,
    private val storage: FileStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val extractor = MetadataExtractor()

    fun recoverPending() {
        val ids = database.listPendingExifIds()
        if (ids.isEmpty()) return
        println("[exif] recovering ${ids.size} pending row(s)")
        ids.forEach { id -> scope.launch { processOne(id) } }
    }

    private fun processOne(id: UUID) {
        try {
            val record = database.getUploadById(id) ?: return
            if (record.exifProcessedAt != null) return

            val bytes = fetchBytes(record)
            val meta = try { extractor.extract(bytes, record.mimeType) } catch (_: Exception) { MediaMetadata() }

            database.updateExif(
                id,
                takenAt  = meta.takenAt,
                latitude    = meta.latitude,
                longitude   = meta.longitude,
                altitude    = meta.altitude,
                deviceMake  = meta.deviceMake,
                deviceModel = meta.deviceModel,
            )
        } catch (e: Exception) {
            println("[exif] WARNING: failed to process $id: ${e.message}")
            // Mark processed anyway to prevent infinite retry on a permanently broken row.
            try {
                database.updateExif(id, null, null, null, null, null, null)
            } catch (_: Exception) {}
        }
    }

    private fun fetchBytes(record: UploadRecord): ByteArray {
        val key = StorageKey(record.storageKey)
        return if (record.mimeType in METADATA_IMAGE_MIME_TYPES) {
            storage.getFirst(key, EXIF_PARTIAL_BYTES)
        } else {
            storage.get(key)
        }
    }
}
