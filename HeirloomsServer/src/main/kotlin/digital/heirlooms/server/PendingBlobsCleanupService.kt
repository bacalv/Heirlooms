package digital.heirlooms.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class PendingBlobsCleanupService(
    private val database: Database,
    private val storage: FileStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val blobTtlHours = 24L
    private val blobIntervalMs = 6 * 60 * 60 * 1_000L
    private val dormantDeviceDays = 180L
    private val deviceIntervalMs = 24 * 60 * 60 * 1_000L

    fun startPeriodicCleanup() {
        scope.launch {
            while (true) {
                runBlobCleanup()
                delay(blobIntervalMs)
            }
        }
        scope.launch {
            while (true) {
                runDevicePruning()
                delay(deviceIntervalMs)
            }
        }
        scope.launch {
            while (true) {
                runSessionCleanup()
                delay(deviceIntervalMs)
            }
        }
    }

    private fun runBlobCleanup() {
        try {
            val olderThan = Instant.now().minus(blobTtlHours, ChronoUnit.HOURS)
            val staleKeys = database.deleteStalePendingBlobs(olderThan)
            for (key in staleKeys) {
                try {
                    storage.delete(StorageKey(key))
                    println("[blob-cleanup] INFO: deleted stale blob $key")
                } catch (e: Exception) {
                    println("[blob-cleanup] WARNING: failed to delete blob $key: ${e.message}")
                }
            }
            if (staleKeys.isNotEmpty()) {
                println("[blob-cleanup] INFO: cleaned up ${staleKeys.size} stale blob(s)")
            }
        } catch (e: Exception) {
            println("[blob-cleanup] ERROR: cleanup failed: ${e.message}")
        }
    }

    private fun runSessionCleanup() {
        try {
            database.deleteExpiredSessions()
        } catch (e: Exception) {
            println("[session-cleanup] ERROR: ${e.message}")
        }
    }

    private fun runDevicePruning() {
        try {
            val dormantBefore = Instant.now().minus(dormantDeviceDays, ChronoUnit.DAYS)
            val count = database.retireDormantWrappedKeys(dormantBefore)
            if (count > 0) {
                println("[device-pruning] INFO: retired $count dormant device(s) (last used before $dormantBefore)")
            }
        } catch (e: Exception) {
            println("[device-pruning] ERROR: pruning failed: ${e.message}")
        }
    }
}
