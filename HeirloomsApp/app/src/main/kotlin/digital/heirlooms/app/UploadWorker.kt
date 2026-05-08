package digital.heirlooms.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val uploader = Uploader(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    )

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: return Result.failure()
        val apiKey = inputData.getString(KEY_API_KEY)
        val tags = inputData.getStringArray(KEY_TAGS)?.toList() ?: emptyList()
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L)

        val file = File(filePath)
        if (!file.exists()) return Result.failure()

        val result = uploader.uploadViaSigned(
            baseUrl = BASE_URL,
            file = file,
            mimeType = mimeType,
            apiKey = apiKey,
            tags = tags,
            onProgress = { bytesWritten, fileTotal ->
                setProgressAsync(workDataOf(
                    KEY_BYTES_WRITTEN to bytesWritten,
                    KEY_TOTAL_BYTES to fileTotal,
                ))
            },
        )

        return when {
            result is Uploader.UploadResult.Success -> {
                file.delete()
                notifyResult(success = true)
                Result.success()
            }
            // Retry up to MAX_ATTEMPTS before giving up. WorkManager resets progress
            // data between attempts so the UI stays accurate.
            runAttemptCount < MAX_ATTEMPTS - 1 -> Result.retry()
            else -> {
                file.delete()
                notifyResult(success = false)
                Result.failure()
            }
        }
    }

    private fun notifyResult(success: Boolean) {
        ensureChannels()
        val (title, text) = if (success)
            context.getString(R.string.notif_planted) to context.getString(R.string.upload_success_one)
        else
            context.getString(R.string.notif_didnt_take) to context.getString(R.string.upload_failed)
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_RESULT, n)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RESULT,
                    context.getString(R.string.notif_channel_uploads),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    companion object {
        // Per-file input keys
        const val KEY_FILE_PATH = "file_path"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_API_KEY = "api_key"
        const val KEY_TAGS = "tags"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TOTAL_BYTES = "total_bytes"

        // Progress output keys
        const val KEY_BYTES_WRITTEN = "bytes_written"

        // WorkManager tags
        const val TAG = "heirloom_upload"
        const val MAX_ATTEMPTS = 3

        // Legacy keys kept for backwards compatibility with existing tests
        const val KEY_FILE_PATHS = "file_paths"
        const val KEY_MIME_TYPES = "mime_types"
        const val TAG_COUNT_PREFIX = "count:"

        private const val BASE_URL = "https://api.heirlooms.digital"
        private const val CHANNEL_RESULT = "heirloom_upload_v2"
        private const val NOTIFICATION_RESULT = 2
    }
}
