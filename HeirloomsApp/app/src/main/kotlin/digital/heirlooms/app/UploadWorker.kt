package digital.heirlooms.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        val filePaths = inputData.getStringArray(KEY_FILE_PATHS) ?: return Result.failure()
        val mimeTypes = inputData.getStringArray(KEY_MIME_TYPES) ?: return Result.failure()
        val apiKey = inputData.getString(KEY_API_KEY)
        val tags = inputData.getStringArray(KEY_TAGS)?.toList() ?: emptyList()

        var succeeded = 0
        var failed = 0
        val total = filePaths.size

        filePaths.zip(mimeTypes.toList()).forEach { (path, mimeType) ->
            val file = File(path)
            if (!file.exists()) { failed++; return@forEach }
            try {
                val result = uploader.uploadViaSigned(BASE_URL, file, mimeType, apiKey, tags = tags)
                if (result is Uploader.UploadResult.Success) succeeded++ else failed++
            } catch (_: Exception) {
                failed++
            } finally {
                file.delete()
            }
        }

        notifyResult(succeeded, failed, total)
        return Result.success()
    }

    private fun notifyResult(succeeded: Int, failed: Int, total: Int) {
        ensureChannels()
        val (title, text) = when {
            failed == 0 && total == 1 -> context.getString(R.string.notif_planted) to
                context.getString(R.string.upload_success_one)
            failed == 0 -> context.getString(R.string.notif_planted) to
                context.getString(R.string.upload_success_many, total)
            succeeded == 0 -> context.getString(R.string.notif_didnt_take) to
                context.getString(R.string.upload_failed)
            else -> context.getString(R.string.notif_planted) to
                context.getString(R.string.upload_success_many, succeeded)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    companion object {
        const val KEY_FILE_PATHS = "file_paths"
        const val KEY_MIME_TYPES = "mime_types"
        const val KEY_API_KEY = "api_key"
        const val KEY_TAGS = "tags"
        const val TAG = "heirloom_upload"
        const val TAG_COUNT_PREFIX = "count:"

        private const val BASE_URL = "https://api.heirlooms.digital"
        private const val CHANNEL_RESULT = "heirloom_upload_v2"
        private const val NOTIFICATION_RESULT = 2
    }
}
