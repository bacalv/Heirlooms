package digital.heirlooms.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ShareActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = resolveUris()
        if (uris.isEmpty()) {
            showToastAndFinish("No files received.")
            return
        }

        scope.launch {
            val store = EndpointStore.create(applicationContext)
            val apiKey = store.getApiKey().takeIf { it.isNotEmpty() }
            val wifiOnly = store.getWifiOnly()

            // Stream each URI directly into a temp cache file — avoids holding
            // the full bytes in memory, which matters for large videos.
            val tempFiles = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyToTempFile(uri) }
            }

            if (tempFiles.isEmpty()) {
                showToastAndFinish("Could not read files.")
                return@launch
            }

            val data = workDataOf(
                UploadWorker.KEY_FILE_PATHS to tempFiles.map { it.first }.toTypedArray(),
                UploadWorker.KEY_MIME_TYPES to tempFiles.map { it.second }.toTypedArray(),
                UploadWorker.KEY_API_KEY to (apiKey ?: ""),
            )

            val constraints = Constraints.Builder()
                .apply { if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED) }
                .build()

            WorkManager.getInstance(applicationContext)
                .enqueue(OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag(UploadWorker.TAG)
                    .addTag("${UploadWorker.TAG_COUNT_PREFIX}${tempFiles.size}")
                    .build())

            val count = tempFiles.size
            val queued = if (count == 1) "Upload queued." else "$count uploads queued."
            val waiting = if (count == 1) "Queued — waiting for WiFi." else "$count files queued — waiting for WiFi."
            val msg = if (wifiOnly && !isOnWifi()) waiting else queued
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun resolveUris(): List<Uri> = when (intent?.action) {
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

    private fun copyToTempFile(uri: Uri): Pair<String, String>? {
        val mimeType = Uploader.resolveMimeType(contentResolver.getType(uri))
        val tempFile = File(cacheDir, "upload-${UUID.randomUUID()}.tmp")
        return try {
            val inputStream = openStream(uri) ?: return null
            inputStream.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            Pair(tempFile.absolutePath, mimeType)
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    // Try the unredacted URI first (preserves GPS EXIF on Android 10+).
    // SecurityException from openInputStream must be caught separately —
    // it bypasses the null-coalescing fallback if left in the outer try block.
    private fun openStream(uri: Uri): java.io.InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val stream = contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
                if (stream != null) return stream
            } catch (_: Exception) { }
        }
        return contentResolver.openInputStream(uri)
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun showToastAndFinish(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
