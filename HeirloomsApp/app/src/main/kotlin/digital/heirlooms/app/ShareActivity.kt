package digital.heirlooms.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import digital.heirlooms.ui.share.IdleScreen
import digital.heirlooms.ui.share.RecentTagsStore
import digital.heirlooms.ui.share.ReceiveState
import digital.heirlooms.ui.share.ShareViewModel
import digital.heirlooms.ui.share.isValidTag
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

class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()
    private var screenState by mutableStateOf<ReceiveState>(ReceiveState.Uploading)
    private var pendingUris: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // Fresh launch — resolve URIs from the share intent.
            pendingUris = resolveUris()
            if (pendingUris.isEmpty()) { finish(); return }
            val recentTags = RecentTagsStore(applicationContext).load()
            screenState = ReceiveState.Idle(pendingUris, recentTags = recentTags)
        } else {
            // Recreation (rotation etc.) — restore state.
            // If there was an upload in flight, re-observe it; otherwise restore to Uploading
            // so the working-dots screen shows while we reconnect.
            val workerId = viewModel.pendingWorkerId
            if (workerId != null) {
                screenState = ReceiveState.Uploading
                lifecycleScope.launch { observeExistingWork(UUID.fromString(workerId)) }
            }
            // If no worker id, the ViewModel has no in-flight upload and we're in a terminal
            // state — the setContent below will render whatever screenState was last set to.
            // (For Idle, pendingUris is empty on recreation so we just stay in Uploading until
            // the observation resolves or the user dismisses.)
        }

        setContent {
            HeirloomsTheme {
                val s = screenState
                if (s is ReceiveState.Idle) {
                    Box(Modifier.fillMaxSize().background(Parchment)) {
                        IdleScreen(
                            state = s,
                            onTagInputChanged = { input ->
                                (screenState as? ReceiveState.Idle)?.let {
                                    screenState = it.copy(currentTagInput = input)
                                }
                            },
                            onTagCommit = { tag ->
                                (screenState as? ReceiveState.Idle)?.let {
                                    screenState = it.copy(
                                        tagsInProgress = it.tagsInProgress + tag,
                                        currentTagInput = "",
                                    )
                                }
                            },
                            onTagRemoved = { tag ->
                                (screenState as? ReceiveState.Idle)?.let {
                                    screenState = it.copy(tagsInProgress = it.tagsInProgress - tag)
                                }
                            },
                            onRecentTagTapped = { tag ->
                                (screenState as? ReceiveState.Idle)?.let {
                                    screenState = it.copy(
                                        tagsInProgress = it.tagsInProgress + tag,
                                        recentTags = it.recentTags - tag,
                                    )
                                }
                            },
                            onPlant = {
                                (screenState as? ReceiveState.Idle)?.let { idle ->
                                    val input = idle.currentTagInput.trim()
                                    viewModel.pendingTags = if (input.isNotEmpty() && isValidTag(input)) {
                                        idle.tagsInProgress + input
                                    } else {
                                        idle.tagsInProgress
                                    }
                                    startUpload()
                                }
                            },
                            onCancel = { finish() },
                        )
                    }
                } else {
                    ReceiveScreenContent(
                        state = screenState,
                        onArrivalComplete = { screenState = ReceiveState.Arrived(viewModel.uploadPhotoCount) },
                        onFailureAnimationComplete = { screenState = ReceiveState.Failed },
                        onDone = { finish() },
                        onViewGarden = { openGarden() },
                        onRetry = { startUpload() },
                        onDismiss = { finish() },
                    )
                }
            }
        }
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

            viewModel.uploadPhotoCount = tempFiles.size

            val data = workDataOf(
                UploadWorker.KEY_FILE_PATHS to tempFiles.map { it.first }.toTypedArray(),
                UploadWorker.KEY_MIME_TYPES to tempFiles.map { it.second }.toTypedArray(),
                UploadWorker.KEY_API_KEY to (apiKey ?: ""),
                UploadWorker.KEY_TAGS to viewModel.pendingTags.toTypedArray(),
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

            viewModel.pendingWorkerId = request.id.toString()
            WorkManager.getInstance(applicationContext).enqueue(request)
            observeWorkToCompletion(request.id)
        }
    }

    private suspend fun observeWorkToCompletion(id: UUID) {
        val result = awaitWork(id)
        viewModel.pendingWorkerId = null
        if (result.state == WorkInfo.State.SUCCEEDED) {
            RecentTagsStore(applicationContext).record(viewModel.pendingTags)
            screenState = ReceiveState.Arriving(viewModel.uploadPhotoCount)
        } else {
            screenState = ReceiveState.FailedAnimating
        }
    }

    private suspend fun observeExistingWork(id: UUID) {
        val info = WorkManager.getInstance(applicationContext)
            .getWorkInfoById(id).get()
        if (info == null || info.state.isFinished) {
            // Already done or unknown — check result.
            if (info?.state == WorkInfo.State.SUCCEEDED) {
                screenState = ReceiveState.Arriving(viewModel.uploadPhotoCount)
            } else {
                screenState = ReceiveState.FailedAnimating
            }
            viewModel.pendingWorkerId = null
            return
        }
        observeWorkToCompletion(id)
    }

    private suspend fun awaitWork(id: UUID): WorkInfo {
        val liveData = WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(id)
        return withContext(Dispatchers.Main) {
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

// ── Receive screen composables ────────────────────────────────────────────────

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
        modifier = Modifier.fillMaxSize().background(Parchment),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is ReceiveState.Uploading -> WorkingDots(
                size = WorkingDotsSize.Large,
                label = stringResource(R.string.upload_in_progress),
            )

            is ReceiveState.Arriving -> Column(
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

            is ReceiveState.Arrived -> Column(
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
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("view garden", style = HeirloomsSerifItalic.copy(fontSize = 14.sp))
                }
                TextButton(onClick = onDone) {
                    Text("done", style = HeirloomsSerifItalic.copy(fontSize = 14.sp), color = TextMuted)
                }
            }

            is ReceiveState.FailedAnimating -> OliveBranchDidntTake(
                onComplete = onFailureAnimationComplete,
                modifier = Modifier.size(200.dp),
            )

            is ReceiveState.Failed -> Column(
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
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text(text = stringResource(R.string.upload_retry), style = HeirloomsSerifItalic.copy(fontSize = 14.sp))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.upload_dismiss), style = HeirloomsSerifItalic.copy(fontSize = 14.sp), color = TextMuted)
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun photoCountString(count: Int): String =
    if (count == 1) stringResource(R.string.upload_success_one)
    else stringResource(R.string.upload_success_many, count)
