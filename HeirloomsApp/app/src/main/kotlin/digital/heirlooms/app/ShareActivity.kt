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
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel()

        if (intent?.action != Intent.ACTION_SEND) {
            showToastAndFinish("Unsupported action.")
            return
        }

        val fileUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: run { showToastAndFinish("No file received."); return }

        val mimeType = Uploader.resolveMimeType(
            intent.type?.takeIf { it.isNotEmpty() }
                ?: contentResolver.getType(fileUri)
        )

        showToast("Uploading…")

        scope.launch {
            val store = EndpointStore.create(applicationContext)
            val endpoint = store.get()
            val apiKey = store.getApiKey().takeIf { it.isNotEmpty() }

            val fileBytes = withContext(Dispatchers.IO) {
                try { readBytes(fileUri) } catch (e: IOException) { null }
            }

            if (fileBytes == null) {
                notify("Upload failed", "Could not read the file.")
                finish()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                uploader.upload(endpoint, fileBytes, mimeType, apiKey)
            }

            when (result) {
                is Uploader.UploadResult.Success -> {
                    val attemptText = if (result.attempts > 1) " (${result.attempts} attempts)" else ""
                    notify("Upload complete", "File uploaded successfully$attemptText.")
                }
                is Uploader.UploadResult.Failure -> {
                    notify("Upload failed", result.message)
                }
            }

            finish()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Upload status",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Shows the result of an heirloom upload" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notify(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(applicationContext, "$title: $text", Toast.LENGTH_LONG).show()
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun readBytes(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open stream for URI")

    private fun showToast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    private fun showToastAndFinish(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "heirloom_upload"
        private const val NOTIFICATION_ID = 1
    }
}
