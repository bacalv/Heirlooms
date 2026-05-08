package digital.heirlooms.ui.share

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import digital.heirlooms.app.UploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class FileUploadState(
    val workerId: UUID,
    val fileName: String,
    val bytesWritten: Long,
    val totalBytes: Long,
    val state: WorkInfo.State,
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesWritten * 100L) / totalBytes).toInt() else 0
    val isActive: Boolean get() = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
    val isDone: Boolean get() = state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED
}

data class SessionUploadState(
    val files: List<FileUploadState>,
) {
    val totalBytes: Long get() = files.sumOf { it.totalBytes }
    val bytesWritten: Long get() = files.sumOf {
        if (it.state == WorkInfo.State.SUCCEEDED) it.totalBytes else it.bytesWritten
    }
    val overallPercent: Int get() = if (totalBytes > 0) ((bytesWritten * 100L) / totalBytes).toInt() else 0
    val activeCount: Int get() = files.count { it.isActive }
    val allDone: Boolean get() = files.isNotEmpty() && files.all { it.isDone }
    val anyActive: Boolean get() = files.any { it.isActive }
}

class UploadProgressViewModel(app: Application) : AndroidViewModel(app) {

    private val workManager = WorkManager.getInstance(app)

    // Shows ALL upload jobs across all sessions, so the user sees a unified picture
    // regardless of which share action initiated them.
    fun allUploadsFlow(): Flow<SessionUploadState> =
        workManager.getWorkInfosByTagFlow(UploadWorker.TAG).map { infos ->
            SessionUploadState(infos.map { info ->
                val fileName = info.tags
                    .firstOrNull { it.startsWith("name:") }
                    ?.removePrefix("name:") ?: "file"
                val bytesWritten = info.progress.getLong(UploadWorker.KEY_BYTES_WRITTEN, 0L)
                val totalBytes = info.progress.getLong(UploadWorker.KEY_TOTAL_BYTES, 0L)
                FileUploadState(
                    workerId = info.id,
                    fileName = fileName,
                    bytesWritten = if (info.state == WorkInfo.State.SUCCEEDED) totalBytes else bytesWritten,
                    totalBytes = totalBytes,
                    state = info.state,
                )
            })
        }

    fun anyActiveFlow(): Flow<Boolean> =
        workManager.getWorkInfosByTagFlow(UploadWorker.TAG).map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }

    fun cancel(workerId: UUID) {
        workManager.cancelWorkById(workerId)
    }

    fun pruneFinished() {
        workManager.pruneWork()
    }
}
