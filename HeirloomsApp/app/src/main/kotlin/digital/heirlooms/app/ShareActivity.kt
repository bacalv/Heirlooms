package digital.heirlooms.app

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

class ShareActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val uploader = Uploader(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannels()

        val uris = resolveUris()
        if (uris.isEmpty()) {
            showToastAndFinish("No files received.")
            return
        }

        scope.launch {
            val store = EndpointStore.create(applicationContext)
            val apiKey = store.getApiKey().takeIf { it.isNotEmpty() }
            val baseUrl = "https://api.heirlooms.digital"
            val total = uris.size
            var succeeded = 0
            var failed = 0

            uris.forEachIndexed { index, uri ->
                val fileNum = index + 1
                val mimeType = Uploader.resolveMimeType(contentResolver.getType(uri))

                val fileBytes = withContext(Dispatchers.IO) {
                    try { readBytes(uri) } catch (_: Exception) { null }
                }
                if (fileBytes == null) {
                    failed++
                    return@forEachIndexed
                }

                val result = withContext(Dispatchers.IO) {
                    uploader.uploadViaSigned(
                        baseUrl, fileBytes, mimeType, apiKey,
                        onProgress = { percent -> notifyProgress(fileNum, total, percent) },
                        onConfirming = { notifyProcessing(fileNum, total) },
                    )
                }

                if (result is Uploader.UploadResult.Success) succeeded++ else failed++
            }

            cancelProgress()
            val title = if (failed == 0) "Upload complete" else "Upload partially complete"
            val text = when {
                total == 1 && failed == 0 -> "File uploaded successfully."
                total == 1 -> "Upload failed."
                failed == 0 -> "$total files uploaded."
                succeeded == 0 -> "All $total uploads failed."
                else -> "$succeeded of $total files uploaded."
            }
            notifyResult(title, text)
            finish()
        }
    }

    private fun resolveUris(): List<Uri> {
        return when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Upload progress", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Silent progress bar during upload" }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, "Upload result", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Shows the result of an heirloom upload" }
            )
        }
    }

    private fun notifyProgress(fileNum: Int, total: Int, percent: Int) {
        if (!canNotify()) return
        val title = if (total == 1) "Uploading…" else "Uploading $fileNum of $total"
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_PROGRESS, n)
    }

    private fun notifyProcessing(fileNum: Int, total: Int) {
        if (!canNotify()) return
        val title = if (total == 1) "Processing…" else "Processing $fileNum of $total"
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_PROGRESS, n)
    }

    private fun cancelProgress() {
        NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_PROGRESS)
    }

    private fun notifyResult(title: String, text: String) {
        if (!canNotify()) {
            Toast.makeText(applicationContext, "$title: $text", Toast.LENGTH_LONG).show()
            return
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_RESULT)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_RESULT, n)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun readBytes(uri: Uri): ByteArray {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val originalUri = MediaStore.setRequireOriginal(uri)
                contentResolver.openInputStream(originalUri)?.use { return it.readBytes() }
            } catch (_: Exception) { }
        }
        return contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open stream for URI")
    }

    private fun showToastAndFinish(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_PROGRESS = "heirloom_progress"
        private const val CHANNEL_RESULT = "heirloom_upload_v2"
        private const val NOTIFICATION_PROGRESS = 1
        private const val NOTIFICATION_RESULT = 2
    }
}
