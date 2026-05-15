package digital.heirlooms.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import digital.heirlooms.ui.common.LocalImageLoader
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import digital.heirlooms.ui.share.IdleScreen
import digital.heirlooms.ui.share.ReceiveState
import digital.heirlooms.ui.share.RecentTagsStore
import digital.heirlooms.ui.share.ShareViewModel
import digital.heirlooms.ui.share.UploadProgressScreen
import digital.heirlooms.ui.share.isValidTag
import digital.heirlooms.ui.theme.HeirloomsTheme
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.UUID

class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()
    private var screenState by mutableStateOf<ReceiveState>(ReceiveState.Uploading(""))
    private var pendingUris: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SEC-009 Part 1 (A-05): ShareActivity shows a preview of decrypted media before upload.
        // FLAG_SECURE prevents screenshots and recent-apps thumbnails from capturing it.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (savedInstanceState == null) {
            pendingUris = resolveUris()
            if (pendingUris.isEmpty()) { finish(); return }
            val recentTags = RecentTagsStore(applicationContext).load()
            screenState = ReceiveState.Idle(pendingUris, recentTags = recentTags)
        } else {
            val existingSession = viewModel.sessionTag
            if (existingSession != null) {
                screenState = ReceiveState.Uploading(existingSession)
            }
        }

        setContent {
            HeirloomsTheme {
                val context = LocalContext.current
                val shareImageLoader = remember {
                    ImageLoader.Builder(context)
                        .components { add(VideoFrameDecoder.Factory()) }
                        .build()
                }
                CompositionLocalProvider(LocalImageLoader provides shareImageLoader) {
                val s = screenState
                if (s is ReceiveState.Idle) {
                    Box(Modifier.fillMaxSize().background(Parchment)) {
                        IdleScreen(
                            state = s,
                            onTagsChange = { newTags ->
                                (screenState as? ReceiveState.Idle)?.let {
                                    screenState = it.copy(tagsInProgress = newTags)
                                }
                            },
                            onPlant = {
                                (screenState as? ReceiveState.Idle)?.let { idle ->
                                    viewModel.pendingTags = idle.tagsInProgress
                                    RecentTagsStore(applicationContext).record(viewModel.pendingTags)
                                    enqueueUploads()
                                }
                            },
                            onCancel = { finish() },
                        )
                    }
                } else if (s is ReceiveState.Uploading && s.sessionTag.isNotEmpty()) {
                    UploadProgressScreen(
                        sessionTag = s.sessionTag,
                        fromShare = true,
                        onGoToGarden = {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            )
                            finish()
                        },
                        onContinueInBackground = { finish() },
                    )
                }
                } // CompositionLocalProvider
            }
        }
    }

    private fun enqueueUploads() {
        val sessionTag = "session:${UUID.randomUUID()}"
        viewModel.sessionTag = sessionTag
        // Show progress screen immediately — no blocking main-thread work.
        screenState = ReceiveState.Uploading(sessionTag)

        val store = EndpointStore.create(applicationContext)
        val apiKey = store.getSessionToken().takeIf { it.isNotEmpty() }
            ?: store.getApiKey().takeIf { it.isNotEmpty() } ?: ""
        val wifiOnly = store.getWifiOnly()
        val tags = viewModel.pendingTags
        val uris = pendingUris.toList()

        val constraints = Constraints.Builder()
            .apply { if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED) }
            .build()

        // Copy each file to temp storage and enqueue its worker on a background thread.
        // Files are processed sequentially so we never hold more than one copy in memory
        // at a time — important on low-RAM devices like the A02s.
        lifecycleScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                copyToTempFile(uri)?.let { (path, mimeType, fileName, fileSize) ->
                    val request = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(workDataOf(
                            UploadWorker.KEY_FILE_PATH to path,
                            UploadWorker.KEY_MIME_TYPE to mimeType,
                            UploadWorker.KEY_API_KEY to apiKey,
                            UploadWorker.KEY_TAGS to tags.toTypedArray(),
                            UploadWorker.KEY_FILE_NAME to fileName,
                            UploadWorker.KEY_TOTAL_BYTES to fileSize,
                        ))
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .addTag(UploadWorker.TAG)
                        .addTag(sessionTag)
                        .addTag("name:$fileName")
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(request)
                }
            }
        }
    }

    private data class TempFile(val path: String, val mimeType: String, val fileName: String, val fileSize: Long)

    private fun copyToTempFile(uri: Uri): TempFile? {
        val mimeType = Uploader.resolveMimeType(contentResolver.getType(uri))
        val fileName = resolveDisplayName(uri) ?: "upload-${UUID.randomUUID()}"
        val tempFile = File(cacheDir, "upload-${UUID.randomUUID()}.tmp")
        return try {
            val stream = openStream(uri) ?: return null
            stream.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            TempFile(tempFile.absolutePath, mimeType, fileName, tempFile.length())
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    private fun resolveDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (_: Exception) { null }

    private fun openStream(uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val stream = contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
                if (stream != null) return stream
            } catch (_: Exception) { }
        }
        return contentResolver.openInputStream(uri)
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
}
