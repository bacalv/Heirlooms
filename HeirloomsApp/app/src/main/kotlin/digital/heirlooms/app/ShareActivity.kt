package digital.heirlooms.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import digital.heirlooms.ui.brand.OliveBranchArrival
import digital.heirlooms.ui.brand.OliveBranchDidntTake
import digital.heirlooms.ui.brand.WorkingDots
import digital.heirlooms.ui.brand.WorkingDotsSize
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.HeirloomsTheme
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume

sealed class ReceiveState {
    object Idle : ReceiveState()
    object Uploading : ReceiveState()
    data class Arriving(val photoCount: Int) : ReceiveState()
    data class Arrived(val photoCount: Int) : ReceiveState()
    object FailedAnimating : ReceiveState()
    object Failed : ReceiveState()
}

class ShareActivity : ComponentActivity() {

    private var screenState by mutableStateOf<ReceiveState>(ReceiveState.Idle)
    private var pendingUris: List<Uri> = emptyList()
    private var uploadPhotoCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HeirloomsTheme {
                ReceiveScreenContent(
                    state = screenState,
                    onArrivalComplete = { screenState = ReceiveState.Arrived(uploadPhotoCount) },
                    onFailureAnimationComplete = { screenState = ReceiveState.Failed },
                    onDone = { finish() },
                    onViewGarden = { openGarden() },
                    onRetry = { startUpload() },
                    onDismiss = { finish() },
                )
            }
        }

        pendingUris = resolveUris()
        if (pendingUris.isEmpty()) { finish(); return }
        startUpload()
    }

    private fun startUpload() {
        screenState = ReceiveState.Uploading
        lifecycleScope.launch {
            val store = EndpointStore.create(applicationContext)
            val apiKey = store.getApiKey().takeIf { it.isNotEmpty() }
            val wifiOnly = store.getWifiOnly()

            val tempFiles = withContext(Dispatchers.IO) {
                pendingUris.mapNotNull { uri -> copyToTempFile(uri) }
            }

            if (tempFiles.isEmpty()) {
                screenState = ReceiveState.FailedAnimating
                return@launch
            }

            uploadPhotoCount = tempFiles.size

            val data = workDataOf(
                UploadWorker.KEY_FILE_PATHS to tempFiles.map { it.first }.toTypedArray(),
                UploadWorker.KEY_MIME_TYPES to tempFiles.map { it.second }.toTypedArray(),
                UploadWorker.KEY_API_KEY to (apiKey ?: ""),
            )
            val constraints = Constraints.Builder()
                .apply { if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED) }
                .build()
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(UploadWorker.TAG)
                .addTag("${UploadWorker.TAG_COUNT_PREFIX}${tempFiles.size}")
                .build()

            WorkManager.getInstance(applicationContext).enqueue(request)
            observeWorkToCompletion(request.id)
        }
    }

    // Suspend until the work request reaches a terminal state (SUCCEEDED, FAILED, CANCELLED).
    // Uses observeForever + suspendCancellableCoroutine to avoid adding lifecycle-livedata-ktx
    // as an explicit dependency (it's available transitively but not declared).
    private suspend fun observeWorkToCompletion(id: UUID) {
        val liveData = WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(id)
        val result: WorkInfo = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val observer = object : Observer<WorkInfo> {
                    override fun onChanged(value: WorkInfo) {
                        if (value.state.isFinished) {
                            liveData.removeObserver(this)
                            if (!cont.isCompleted) cont.resume(value)
                        }
                    }
                }
                liveData.observeForever(observer)
                cont.invokeOnCancellation { liveData.removeObserver(observer) }
            }
        }
        screenState = if (result.state == WorkInfo.State.SUCCEEDED) {
            ReceiveState.Arriving(uploadPhotoCount)
        } else {
            ReceiveState.FailedAnimating
        }
    }

    private fun openGarden() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://heirlooms.digital")))
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
            val stream = openStream(uri) ?: return null
            stream.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
            Pair(tempFile.absolutePath, mimeType)
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    // Try the unredacted URI first (preserves GPS EXIF on Android 10+).
    // SecurityException from openInputStream must be caught separately —
    // it bypasses the null-coalescing fallback if left in the outer try block.
    private fun openStream(uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val stream = contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
                if (stream != null) return stream
            } catch (_: Exception) { }
        }
        return contentResolver.openInputStream(uri)
    }
}

// ── Receive screen Composables ─────────────────────────────────────────────

@Composable
private fun ReceiveScreenContent(
    state: ReceiveState,
    onArrivalComplete: () -> Unit,
    onFailureAnimationComplete: () -> Unit,
    onDone: () -> Unit,
    onViewGarden: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Parchment),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is ReceiveState.Idle,
            is ReceiveState.Uploading -> {
                WorkingDots(
                    size = WorkingDotsSize.Large,
                    label = stringResource(R.string.upload_in_progress),
                )
            }

            is ReceiveState.Arriving -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    OliveBranchArrival(
                        withWordmark = false,
                        onComplete = onArrivalComplete,
                        modifier = Modifier.size(200.dp),
                    )
                    Text(
                        text = stringResource(R.string.upload_success),
                        style = HeirloomsSerifItalic.copy(fontSize = 18.sp),
                        color = Forest,
                    )
                    Text(
                        text = photoCountString(state.photoCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }

            is ReceiveState.Arrived -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.upload_success),
                        style = HeirloomsSerifItalic.copy(fontSize = 18.sp),
                        color = Forest,
                    )
                    Text(
                        text = photoCountString(state.photoCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onViewGarden,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Forest,
                            contentColor = Parchment,
                        ),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text("view garden", style = HeirloomsSerifItalic.copy(fontSize = 14.sp))
                    }
                    TextButton(onClick = onDone) {
                        Text(
                            text = "done",
                            style = HeirloomsSerifItalic.copy(fontSize = 14.sp),
                            color = TextMuted,
                        )
                    }
                }
            }

            is ReceiveState.FailedAnimating -> {
                OliveBranchDidntTake(
                    onComplete = onFailureAnimationComplete,
                    modifier = Modifier.size(200.dp),
                )
            }

            is ReceiveState.Failed -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.upload_failed),
                        style = HeirloomsSerifItalic.copy(fontSize = 18.sp),
                        color = Earth,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Forest,
                            contentColor = Parchment,
                        ),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.upload_retry),
                            style = HeirloomsSerifItalic.copy(fontSize = 14.sp),
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.upload_dismiss),
                            style = HeirloomsSerifItalic.copy(fontSize = 14.sp),
                            color = TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun photoCountString(count: Int): String =
    if (count == 1) stringResource(R.string.upload_success_one)
    else stringResource(R.string.upload_success_many, count)
