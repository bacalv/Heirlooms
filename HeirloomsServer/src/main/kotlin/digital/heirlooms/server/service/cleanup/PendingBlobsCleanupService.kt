package digital.heirlooms.server.service.cleanup

import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.storage.BlobRepository
import digital.heirlooms.server.storage.FileStore
import digital.heirlooms.server.storage.StorageKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = LoggerFactory.getLogger(PendingBlobsCleanupService::class.java)

class PendingBlobsCleanupService(
    private val blobRepository: BlobRepository,
    private val authRepository: AuthRepository,
    private val keyRepository: KeyRepository,
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
            val staleKeys = blobRepository.deleteStalePendingBlobs(olderThan)
            for (key in staleKeys) {
                try {
                    storage.delete(StorageKey(key))
                    logger.debug("Deleted stale blob {}", key)
                } catch (e: Exception) {
                    logger.warn("Failed to delete stale blob {}: {}", key, e.message)
                }
            }
            if (staleKeys.isNotEmpty()) logger.info("Cleaned up {} stale blob(s)", staleKeys.size)
        } catch (e: Exception) {
            logger.error("Blob cleanup failed", e)
        }
    }

    private fun runSessionCleanup() {
        try {
            authRepository.deleteExpiredSessions()
        } catch (e: Exception) {
            logger.error("Session cleanup failed", e)
        }
    }

    private fun runDevicePruning() {
        try {
            val dormantBefore = Instant.now().minus(dormantDeviceDays, ChronoUnit.DAYS)
            val count = keyRepository.retireDormantWrappedKeys(dormantBefore)
            if (count > 0) logger.info("Retired {} dormant device(s) (inactive since {})", count, dormantBefore)
        } catch (e: Exception) {
            logger.error("Device pruning failed", e)
        }
    }
}
