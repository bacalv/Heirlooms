@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.brand.OliveBranchArrival
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.UploadThumbnail
import digital.heirlooms.ui.common.TagInputField
import digital.heirlooms.ui.share.RecentTagsStore
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import digital.heirlooms.app.UploadWorker
import digital.heirlooms.ui.main.DiagnosticsStore
import digital.heirlooms.ui.social.ShareSheet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val THUMBNAIL_SIZE_DP = 108

@Composable
fun GardenScreen(
    onPhotoTap: (String) -> Unit,
    onNavigateToExplore: (plotId: String?, justArrived: Boolean) -> Unit,
    vm: GardenViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()
    val availableTags by vm.availableTags.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    // ---- Plant flow -------------------------------------------------------
    var plantState by remember { mutableStateOf<PlantState>(PlantState.Idle) }
    var showPlantSheet by remember { mutableStateOf(false) }
    var captureFile by remember { mutableStateOf<File?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureType by remember { mutableStateOf(PlantType.Photo) }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            plantState = PlantState.Preview(uri, mime, isFile = true)
        }
    }

    fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
        openFileLauncher.launch(intent)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchFilePicker()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        plantState = if (success && captureUri != null)
            PlantState.Preview(captureUri!!, "image/jpeg")
        else PlantState.Idle
    }

    // Fire OS camera apps don't support EXTRA_OUTPUT for video — they save to their own
    // media store and return the URI in result.data?.data. Use StartActivityForResult
    // with a plain ACTION_VIDEO_CAPTURE intent (no output URI) and retrieve it from the result.
    val captureVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            plantState = PlantState.Preview(uri, mime, isFile = true)
        } else {
            plantState = PlantState.Idle
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        if (pendingCaptureType == PlantType.Video) {
            captureVideoLauncher.launch(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE))
            return@rememberLauncherForActivityResult
        }
        val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureFile = file
        captureUri = uri
        takePictureLauncher.launch(uri)
    }

    fun launchCamera(type: PlantType) {
        pendingCaptureType = type
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (type == PlantType.Video) {
            captureVideoLauncher.launch(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE))
            return
        }
        val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureFile = file
        captureUri = uri
        takePictureLauncher.launch(uri)
    }
    // -----------------------------------------------------------------------

    BackHandler(enabled = plantState != PlantState.Idle) { plantState = PlantState.Idle }

    LaunchedEffect(Unit) {
        if (state is GardenLoadState.Loading) vm.load(api) else vm.refresh(api)
        vm.ensureSharingKey(api)
        vm.loadFriends(api)
        vm.loadSharedMemberships(api)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            vm.refreshJustArrived(api)
        }
    }

    val newlyArrivedIds by vm.newlyArrivedIds.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val ownerNames by vm.ownerNames.collectAsStateWithLifecycle()
    val leaveError by vm.leaveError.collectAsStateWithLifecycle()
    var showCreatePlot by remember { mutableStateOf(false) }

    if (leaveError != null) {
        AlertDialog(
            onDismissRequest = { vm.clearLeaveError() },
            title = { Text("Can't leave") },
            text = { Text(leaveError!!) },
            confirmButton = { TextButton(onClick = { vm.clearLeaveError() }) { Text("OK") } },
        )
    }

    if (showCreatePlot) {
        PlotCreateSheet(
            onDismiss = { showCreatePlot = false },
            onCreate = { name ->
                showCreatePlot = false
                vm.createPlot(api, name)
            },
        )
    }

    fun refresh() {
        refreshing = true
        scope.launch { vm.load(api); refreshing = false }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Parchment)) {
        TopAppBar(
            title = { Text("Garden", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.weight(1f),
        ) {
            when (val s = state) {
                is GardenLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
                is GardenLoadState.Error -> DidntTake(onRetry = { vm.load(api) })
                is GardenLoadState.Ready -> {
                    val allEmpty = s.rows.all { it.uploads.isEmpty() }
                    if (allEmpty && s.rows.size <= 1) {
                        GardenEmptyState()
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 16.dp),
                        ) {
                            s.rows.forEachIndexed { index, row ->
                                val rowKey = row.plot?.id ?: "__just_arrived__"
                                val rowLabel = when {
                                    row.plot?.isSystemDefined == true || row.plot == null -> "Just arrived"
                                    row.plot.visibility == "shared" && !row.plot.isOwner && row.plot.localName != null -> row.plot.localName
                                    else -> row.plot.name
                                }
                                val isJustArrived = row.plot?.isSystemDefined == true || row.plot == null

                                PlotRowSection(
                                    label = rowLabel,
                                    plot = row.plot,
                                    uploads = row.uploads,
                                    nextCursor = row.nextCursor,
                                    loadingMore = row.loadingMore,
                                    savedScrollIndex = vm.scrollPositions[rowKey] ?: 0,
                                    onScrollIndex = { vm.saveScrollPosition(rowKey, it) },
                                    onPhotoTap = onPhotoTap,
                                    onTitleTap = { onNavigateToExplore(row.plot?.id, isJustArrived) },
                                    onLoadMore = { vm.loadMoreForRow(api, index) },
                                    onExploreAll = { onNavigateToExplore(row.plot?.id, isJustArrived) },
                                    isJustArrived = isJustArrived,
                                    newlyArrivedIds = if (isJustArrived) newlyArrivedIds else emptySet(),
                                    onClearNewlyArrived = { vm.clearNewlyArrived() },
                                    availableTags = availableTags,
                                    friends = friends,
                                    onQuickRotate = { uploadId, currentRotation ->
                                        val newRotation = (currentRotation + 90) % 360
                                        vm.optimisticRotate(uploadId, newRotation)
                                        scope.launch {
                                            try { api.rotateUpload(uploadId, newRotation) }
                                            catch (_: Exception) { vm.optimisticRotate(uploadId, currentRotation) }
                                        }
                                    },
                                    onTagsUpdated = { uploadId, oldTags, newTags ->
                                        val added = newTags.filter { it !in oldTags }
                                        if (added.isNotEmpty()) RecentTagsStore(context).record(added)
                                        vm.optimisticTag(uploadId, newTags)
                                        scope.launch {
                                            try { api.updateTags(uploadId, newTags) }
                                            catch (_: Exception) { vm.optimisticTag(uploadId, oldTags) }
                                        }
                                    },
                                    onRenamePlot = { newName ->
                                        row.plot?.let { vm.renamePlot(api, it, newName) }
                                    },
                                    onDeletePlot = {
                                        row.plot?.let { vm.leavePlot(api, it.id) }
                                    },
                                    ownerDisplayName = row.plot?.let { ownerNames[it.id] },
                                    emptyLabel = if (isJustArrived) "Nothing waiting." else "No items match this plot's tags.",
                                )
                            }
                            // "+ Add plot" row at the bottom of the garden scroll
                            AddPlotRow(onClick = { showCreatePlot = true })
                        }
                    }
                }
            }
        }
    }
        // Plant FAB — hidden while preview or queuing is showing
        if (plantState == PlantState.Idle) {
            FloatingActionButton(
                onClick = { showPlantSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Forest,
                contentColor = Parchment,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Plant")
            }
        }

        // Source picker sheet
        if (showPlantSheet) {
            PlantSheet(
                onDismiss = { showPlantSheet = false },
                onPhoto = { launchCamera(PlantType.Photo) },
                onVideo = { launchCamera(PlantType.Video) },
                onFile = {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else {
                        launchFilePicker()
                    }
                },
            )
        }

        // Preview / queuing overlay
        AnimatedVisibility(
            visible = plantState !is PlantState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val ps = plantState) {
                is PlantState.Preview -> PlantPreviewOverlay(
                    uri = ps.uri,
                    mimeType = ps.mimeType,
                    isFile = ps.isFile,
                    onPlant = {
                        val snapshot = ps
                        plantState = PlantState.Queuing
                        scope.launch(Dispatchers.IO) {
                            val (filePath, errorMsg) = if (!snapshot.isFile && captureFile?.exists() == true) {
                                Pair(captureFile!!.absolutePath, "")
                            } else {
                                copyContentUriToCache(context, snapshot.uri, snapshot.mimeType)
                            }
                            if (filePath == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                            if (filePath != null) {
                                val fileName = filePath.substringAfterLast("/")
                                val fileSize = File(filePath).length()
                                val sessionTag = "session:${UUID.randomUUID()}"
                                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                                    .setInputData(workDataOf(
                                        UploadWorker.KEY_FILE_PATH to filePath,
                                        UploadWorker.KEY_MIME_TYPE to snapshot.mimeType,
                                        UploadWorker.KEY_API_KEY to api.apiKey,
                                        UploadWorker.KEY_TAGS to emptyArray<String>(),
                                        UploadWorker.KEY_FILE_NAME to fileName,
                                        UploadWorker.KEY_TOTAL_BYTES to fileSize,
                                    ))
                                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                                    .addTag(UploadWorker.TAG)
                                    .addTag(sessionTag)
                                    .build()
                                WorkManager.getInstance(context).enqueue(request)
                            }
                            withContext(Dispatchers.Main) { plantState = PlantState.Idle }
                        }
                    },
                    onRetake = if (!ps.isFile) ({ launchCamera(pendingCaptureType) }) else null,
                    onCancel = { plantState = PlantState.Idle },
                )
                PlantState.Queuing -> Box(
                    Modifier.fillMaxSize().background(Parchment),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Forest)
                }
                else -> {}
            }
        }
    } // closes outer Box
}

@Composable
private fun PlotRowSection(
    label: String,
    plot: digital.heirlooms.api.Plot?,
    uploads: List<Upload>,
    nextCursor: String?,
    loadingMore: Boolean,
    savedScrollIndex: Int,
    onScrollIndex: (Int) -> Unit,
    onPhotoTap: (String) -> Unit,
    onTitleTap: () -> Unit,
    onLoadMore: () -> Unit,
    onExploreAll: () -> Unit,
    isJustArrived: Boolean,
    newlyArrivedIds: Set<String> = emptySet(),
    onClearNewlyArrived: () -> Unit = {},
    availableTags: List<String> = emptyList(),
    friends: List<HeirloomsApi.Friend> = emptyList(),
    onQuickRotate: (uploadId: String, currentRotation: Int) -> Unit,
    onTagsUpdated: (uploadId: String, oldTags: List<String>, newTags: List<String>) -> Unit,
    onRenamePlot: (name: String) -> Unit = {},
    onDeletePlot: () -> Unit = {},
    ownerDisplayName: String? = null,
    emptyLabel: String,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedScrollIndex)
    var showEditPlot by remember { mutableStateOf(false) }

    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    if (showEditPlot && plot != null) {
        PlotEditSheet(
            plot = plot,
            onDismiss = { showEditPlot = false },
            onSave = { name -> showEditPlot = false; onRenamePlot(name) },
            onDelete = { showEditPlot = false; onDeletePlot() },
            onLeave = { showEditPlot = false; onDeletePlot() },
            onToggleStatus = { newStatus ->
                showEditPlot = false
                scope.launch { try { api.setPlotStatus(plot.id, newStatus) } catch (_: Exception) { } }
            },
        )
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        onScrollIndex(listState.firstVisibleItemIndex)
    }

    LaunchedEffect(newlyArrivedIds) {
        if (isJustArrived && newlyArrivedIds.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(Modifier.padding(top = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = onTitleTap)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (plot?.visibility == "shared") {
                        Icon(
                            Icons.Filled.PeopleAlt,
                            contentDescription = null,
                            tint = Forest.copy(alpha = 0.45f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(label, style = MaterialTheme.typography.titleSmall.copy(color = Forest))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Forest.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    if (plot?.plotStatus == "closed") {
                        Spacer(Modifier.width(4.dp))
                        Text("closed", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                    }
                }
                if (ownerDisplayName != null) {
                    Text(
                        "Shared by $ownerDisplayName",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                    )
                }
            }
            if (plot != null && !plot.isSystemDefined) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit plot",
                    tint = Forest.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp).clickable { showEditPlot = true },
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        if (uploads.isEmpty()) {
            Text(
                emptyLabel,
                style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = TextMuted),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(uploads, key = { _, u -> u.id }) { _, upload ->
                    var showMenu by remember { mutableStateOf(false) }
                    var showTagSheet by remember { mutableStateOf(false) }
                    var showShareSheet by remember { mutableStateOf(false) }

                    if (showShareSheet) {
                        ShareSheet(upload = upload, onDismiss = { showShareSheet = false })
                    }

                    Box {
                        Box(
                            Modifier
                                .size(THUMBNAIL_SIZE_DP.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { onPhotoTap(upload.id) },
                                        onLongPress = { showMenu = true },
                                    )
                                }
                        ) {
                            UploadThumbnail(
                                upload = upload,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                rotation = upload.rotation,
                            )
                            if (upload.isVideo) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Parchment,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Forest.copy(alpha = 0.65f), RoundedCornerShape(topStart = 4.dp))
                                        .padding(2.dp)
                                        .size(14.dp),
                                )
                            }
                        }
                        // Tag button — bottom-left
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .clickable { showTagSheet = true }
                                .background(Forest.copy(alpha = 0.65f), RoundedCornerShape(topEnd = 4.dp))
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = "Add tag",
                                tint = Parchment,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        // Share button — bottom-right (only for encrypted/owned items)
                        if (upload.isEncrypted && !upload.isShared) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .clickable { showShareSheet = true }
                                    .background(Forest.copy(alpha = 0.65f), RoundedCornerShape(topStart = 4.dp))
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share",
                                    tint = Parchment,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        // Friend indicator — top-right corner for received shared items
                        if (upload.isShared) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .background(Forest.copy(alpha = 0.75f), RoundedCornerShape(bottomStart = 4.dp))
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonAdd,
                                    contentDescription = "Shared from a friend",
                                    tint = Parchment,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        // Per-tile arrival animation for newly landed items.
                        if (upload.id in newlyArrivedIds) {
                            Box(
                                Modifier
                                    .size(THUMBNAIL_SIZE_DP.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Parchment.copy(alpha = 0.88f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                OliveBranchArrival(
                                    withWordmark = false,
                                    onComplete = onClearNewlyArrived,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rotate 90°") },
                                onClick = {
                                    showMenu = false
                                    onQuickRotate(upload.id, upload.rotation)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add tag…") },
                                onClick = { showMenu = false; showTagSheet = true },
                            )
                        }
                        if (showTagSheet) {
                            QuickTagSheet(
                                upload = upload,
                                availableTags = availableTags,
                                onDismiss = { showTagSheet = false },
                                onTagsUpdated = { newTags ->
                                    showTagSheet = false
                                    onTagsUpdated(upload.id, upload.tags, newTags)
                                },
                            )
                        }
                    }
                }

                if (loadingMore) {
                    item {
                        Box(
                            Modifier.size(THUMBNAIL_SIZE_DP.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (nextCursor != null) {
                    item { LoadMoreTile(onClick = onLoadMore) }
                } else {
                    item { ExploreAllTile(isJustArrived = isJustArrived, onClick = onExploreAll) }
                }
            }
        }
    }
}

@Composable
private fun QuickTagSheet(
    upload: Upload,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onTagsUpdated: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val recentTags = remember(context) { RecentTagsStore(context).load().take(5) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var stagedTags by remember { mutableStateOf(upload.tags) }

    ModalBottomSheet(
        onDismissRequest = {
            if (stagedTags != upload.tags) onTagsUpdated(stagedTags) else onDismiss()
        },
        sheetState = sheetState,
        containerColor = Parchment,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tags", style = MaterialTheme.typography.titleMedium, color = Forest)
            TagInputField(
                tags = stagedTags,
                onTagsChange = { stagedTags = it },
                availableTags = availableTags,
                recentTags = recentTags,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoadMoreTile(onClick: () -> Unit) {
    Box(
        Modifier
            .size(THUMBNAIL_SIZE_DP.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Parchment)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Load more",
            style = MaterialTheme.typography.bodySmall.copy(color = Forest),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun ExploreAllTile(isJustArrived: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(THUMBNAIL_SIZE_DP.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Parchment)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                if (isJustArrived) "See all just arrived in Explore" else "See all in Explore",
                style = MaterialTheme.typography.bodySmall.copy(color = Forest, fontSize = 11.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text("→", style = MaterialTheme.typography.bodyMedium.copy(color = Forest))
        }
    }
}

@Composable
private fun GardenEmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "A garden begins with a single seed.",
            style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Share a photo from your phone to plant your first.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AddPlotRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Forest.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add plot", style = MaterialTheme.typography.bodyMedium.copy(color = Forest.copy(alpha = 0.6f)))
    }
}

@Composable
internal fun DidntTake(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Couldn't load.",
            style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text("Try again", style = MaterialTheme.typography.bodyMedium.copy(color = Forest))
        }
    }
}

private suspend fun copyContentUriToCache(context: android.content.Context, uri: Uri, mimeType: String): Pair<String?, String> =
    withContext(Dispatchers.IO) {
        val diag = StringBuilder("uri=$uri mimeType=$mimeType\n")
        val ext = when {
            mimeType.startsWith("image/") -> "jpg"
            mimeType.startsWith("video/") -> "mp4"
            else -> "tmp"
        }
        val dest = File(context.cacheDir, "plant-${UUID.randomUUID()}.$ext")

        fun tryReadPath(path: String): Boolean = try {
            java.io.File(path).takeIf { it.exists() && it.canRead() }
                ?.inputStream()?.use { it.copyTo(dest.outputStream()) }
                ?.let { true } ?: false
        } catch (e: Exception) { diag.append("tryReadPath($path) threw ${e.javaClass.simpleName}: ${e.message}\n"); false }

        fun tryStream(src: Uri): Boolean = try {
            context.contentResolver.openInputStream(src)
                ?.use { it.copyTo(dest.outputStream()) }
                ?.let { true } ?: false
        } catch (e: Exception) { diag.append("tryStream($src) threw ${e.javaClass.simpleName}: ${e.message}\n"); false }

        // Attempt 1: Documents UI URI — resolve MediaStore row, get DATA path, read directly.
        // openInputStream on MediaStore URIs is broken on Fire OS even when the row exists.
        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            diag.append("isDocumentUri=true\n")
            try {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                diag.append("docId=$docId\n")
                val parts = docId.split(":")
                val rowId = parts.getOrNull(1)?.toLongOrNull()
                val mediaBase = when (parts.getOrNull(0)) {
                    "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                if (rowId != null && mediaBase != null) {
                    val mediaUri = android.content.ContentUris.withAppendedId(mediaBase, rowId)
                    diag.append("mediaUri=$mediaUri\n")
                    val filePath = context.contentResolver.query(
                        mediaUri,
                        arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                        null, null, null,
                    )?.use { if (it.moveToFirst()) it.getString(0) else null }
                    diag.append("DATA path=$filePath\n")
                    if (filePath != null && filePath.contains("securedStorageLocation", ignoreCase = true)) {
                        dest.delete()
                        return@withContext Pair(null, "This file belongs to a different account on this device and can't be accessed. Try taking a new photo directly in Heirlooms.")
                    }
                    if (filePath != null && tryReadPath(filePath)) return@withContext Pair(dest.absolutePath, "")
                    if (tryStream(mediaUri)) return@withContext Pair(dest.absolutePath, "")
                } else {
                    diag.append("rowId=$rowId mediaBase=$mediaBase — skipping MediaStore path\n")
                }
            } catch (e: Exception) {
                diag.append("DocumentsContract block threw ${e.javaClass.simpleName}: ${e.message}\n")
            }
        }

        // Attempt 2: direct stream from the original URI.
        if (tryStream(uri)) return@withContext Pair(dest.absolutePath, "")

        // Attempt 3: DATA column on the original URI.
        try {
            val filePath = context.contentResolver.query(
                uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
            diag.append("attempt3 DATA=$filePath\n")
            if (filePath != null && tryReadPath(filePath)) return@withContext Pair(dest.absolutePath, "")
        } catch (e: Exception) {
            diag.append("attempt3 threw ${e.javaClass.simpleName}: ${e.message}\n")
        }

        dest.delete()
        DiagnosticsStore.log(
            tag = "CopyUri",
            message = "Could not read file from picker",
            detail = diag.toString().trimEnd(),
        )
        Pair(null, "Couldn't read this file. See Diagnostics (burger menu) for details.")
    }
