@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package digital.heirlooms.ui.garden

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import digital.heirlooms.app.DecryptingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import digital.heirlooms.api.CapsuleRef
import digital.heirlooms.api.CapsuleSummary
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.TagInputField
import digital.heirlooms.ui.common.formatInstantDate
import digital.heirlooms.ui.common.formatOffsetDate
import digital.heirlooms.ui.common.daysUntilDeletion
import digital.heirlooms.ui.share.RecentTagsStore
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun PhotoDetailScreen(
    uploadId: String,
    from: String,
    onBack: () -> Unit,
    onCapsuleTap: (String) -> Unit,
    onStartCapsuleWithPhoto: (String) -> Unit,
    vm: PhotoDetailViewModel = viewModel(key = uploadId),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()
    val availableTags by vm.availableTags.collectAsState()
    val stagedTags by vm.stagedTags.collectAsState()
    val stagedRotation by vm.stagedRotation.collectAsState()
    val isDirty by vm.isDirty.collectAsState()
    val decryptedBitmap by vm.decryptedBitmap.collectAsState()
    val decryptedVideoUri by vm.decryptedVideoUri.collectAsState()
    val contentDek by vm.contentDek.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(uploadId) {
        vm.load(api, uploadId, context)
        vm.trackView(api, uploadId)
    }

    // Auto-save staged changes on back navigation (system gesture or top-bar button).
    fun navigateBack() {
        if (isDirty) {
            scope.launch {
                stagedTags?.let { RecentTagsStore(context).record(it) }
                vm.saveChanges(api, uploadId)
                onBack()
            }
        } else {
            onBack()
        }
    }

    BackHandler { navigateBack() }

    val backLabel = when (from) {
        "explore" -> "← Explore"
        "compost" -> "← Compost heap"
        else -> "← Garden"
    }

    val recentTags = remember(context) { RecentTagsStore(context).load().take(5) }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            val overflowActions: @Composable () -> Unit = when (from) {
                "explore" -> {
                    {
                        var showMenu by remember { mutableStateOf(false) }
                        val upload = (state as? PhotoDetailState.Ready)?.upload
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Forest)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (upload != null && upload.compostedAt == null) {
                                val effectiveTags = stagedTags ?: upload.tags
                                val hasTagsOrCapsules = effectiveTags.isNotEmpty() ||
                                    (state as? PhotoDetailState.Ready)?.capsuleRefs
                                        ?.any { it.state == "open" || it.state == "sealed" } == true
                                DropdownMenuItem(
                                    text = { Text("Compost", color = if (hasTagsOrCapsules) TextMuted else Earth) },
                                    onClick = {
                                        if (!hasTagsOrCapsules) {
                                            showMenu = false
                                            scope.launch {
                                                try { api.compostUpload(uploadId); onBack() } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    enabled = !hasTagsOrCapsules,
                                )
                            }
                        }
                    }
                }
                else -> { {} }
            }

            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = ::navigateBack) {
                        Text(backLabel, color = Forest, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = { overflowActions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            is PhotoDetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
            is PhotoDetailState.Error -> DidntTake(onRetry = { vm.load(api, uploadId) })
            is PhotoDetailState.Ready -> {
                val u = s.upload
                val effectiveTags = stagedTags ?: u.tags
                // stagedRotation is collected as state so any rotate press triggers recomposition.
                val effectiveRotation = stagedRotation ?: u.rotation
                when (from) {
                    "compost" -> CompostFlavour(
                        upload = u,
                        onRestore = {
                            scope.launch {
                                try { api.restoreUpload(uploadId); onBack() } catch (_: Exception) {}
                            }
                        },
                        innerPadding = innerPadding,
                    )
                    "explore" -> ExploreFlavour(
                        upload = u,
                        capsuleRefs = s.capsuleRefs,
                        tags = effectiveTags,
                        rotation = effectiveRotation,
                        availableTags = availableTags,
                        recentTags = recentTags,
                        innerPadding = innerPadding,
                        onTagsChange = { vm.stageTags(it) },
                        onRotate = { vm.stageRotate() },
                        onCapsuleTap = onCapsuleTap,
                        decryptedBitmap = decryptedBitmap,
                        decryptedVideoUri = decryptedVideoUri,
                        contentDek = contentDek,
                    )
                    else -> GardenFlavour(
                        upload = u,
                        capsuleRefs = s.capsuleRefs,
                        tags = effectiveTags,
                        rotation = effectiveRotation,
                        availableTags = availableTags,
                        recentTags = recentTags,
                        innerPadding = innerPadding,
                        onTagsChange = { vm.stageTags(it) },
                        onRotate = { vm.stageRotate() },
                        onCapsuleTap = onCapsuleTap,
                        onStartCapsule = { onStartCapsuleWithPhoto(uploadId) },
                        decryptedBitmap = decryptedBitmap,
                        decryptedVideoUri = decryptedVideoUri,
                        contentDek = contentDek,
                        onCompost = {
                            scope.launch {
                                try { api.compostUpload(uploadId); onBack() } catch (_: Exception) {}
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaArea(
    upload: Upload,
    rotation: Int,
    modifier: Modifier = Modifier,
    decryptedBitmap: ImageBitmap? = null,
    decryptedVideoUri: Uri? = null,
    contentDek: ByteArray? = null,
) {
    val api = LocalHeirloomsApi.current
    when {
        upload.isEncrypted && upload.isVideo -> {
            when {
                upload.fileSize > 10L * 1024 * 1024 && contentDek != null -> {
                    // Large encrypted video: decrypt on the fly chunk by chunk.
                    // remember {} (no key) is safe here — this branch only enters composition
                    // once contentDek is non-null, and contentDek doesn't change after that.
                    val factory = remember {
                        DecryptingDataSource.Factory(
                            OkHttpClient.Builder()
                                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                                .addInterceptor { chain ->
                                    chain.proceed(chain.request().newBuilder().header("X-Api-Key", api.apiKey).build())
                                }
                                .build(),
                            contentDek,
                            api.apiKey,
                        )
                    }
                    VideoPlayer(videoUrl = api.fileUrl(upload.id), modifier = modifier, dataSourceFactory = factory)
                }
                upload.fileSize <= 10L * 1024 * 1024 && decryptedVideoUri != null -> {
                    VideoPlayer(videoUrl = decryptedVideoUri.toString(), modifier = modifier)
                }
                else -> Box(modifier.aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            }
        }
        upload.isEncrypted -> {
            if (decryptedBitmap != null) {
                Image(
                    bitmap = decryptedBitmap,
                    contentDescription = null,
                    modifier = if (rotation != 0) modifier.graphicsLayer { rotationZ = rotation.toFloat() } else modifier,
                    contentScale = ContentScale.FillWidth,
                )
            } else {
                Box(modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            }
        }
        upload.isVideo -> {
            VideoPlayer(videoUrl = api.fileUrl(upload.id), modifier = modifier)
        }
        else -> {
            HeirloomsImage(
                url = api.fileUrl(upload.id),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.FillWidth,
                rotation = rotation,
            )
        }
    }
}

@Composable
private fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    dataSourceFactory: DataSource.Factory? = null,
) {
    val context = LocalContext.current
    val apiKey = LocalHeirloomsApi.current.apiKey
    val player = remember {
        val factory = dataSourceFactory ?: OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("X-Api-Key", apiKey).build())
                }
                .build()
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .also { player ->
                player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                player.prepare()
                player.playWhenReady = true
            }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply { this.player = player }
        },
        modifier = modifier.aspectRatio(16f / 9f),
    )
}

@Composable
internal fun GardenFlavour(
    upload: Upload,
    capsuleRefs: List<CapsuleRef>,
    tags: List<String>,
    rotation: Int,
    availableTags: List<String>,
    recentTags: List<String>,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onTagsChange: (List<String>) -> Unit,
    onRotate: () -> Unit,
    onCapsuleTap: (String) -> Unit,
    onStartCapsule: () -> Unit,
    onCompost: () -> Unit,
    decryptedBitmap: ImageBitmap? = null,
    decryptedVideoUri: Uri? = null,
    contentDek: ByteArray? = null,
) {
    var showAddToCapsule by remember { mutableStateOf(false) }
    var showCompostConfirm by remember { mutableStateOf(false) }
    val hasTagsOrCapsules = tags.isNotEmpty() || capsuleRefs.any { it.state == "open" || it.state == "sealed" }

    Column(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Box {
            MediaArea(
                upload = upload, rotation = rotation, modifier = Modifier.fillMaxWidth(),
                decryptedBitmap = decryptedBitmap, decryptedVideoUri = decryptedVideoUri,
                contentDek = contentDek,
            )
            if (!upload.isVideo) {
                IconButton(
                    onClick = onRotate,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.RotateRight,
                        contentDescription = "Rotate",
                        tint = Parchment,
                        modifier = Modifier
                            .background(Forest.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(4.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            TagInputField(
                tags = tags,
                onTagsChange = onTagsChange,
                availableTags = availableTags,
                recentTags = recentTags,
            )

            Spacer(Modifier.height(12.dp))
            upload.takenAt?.let {
                Text("Taken ${formatInstantDate(it)}", style = MaterialTheme.typography.bodyMedium, color = Forest)
                Spacer(Modifier.height(4.dp))
            }
            Text("Uploaded ${formatInstantDate(upload.uploadedAt)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)

            val activeCapsules = capsuleRefs.filter { it.state == "open" || it.state == "sealed" }
            if (activeCapsules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "In capsules: " + activeCapsules.joinToString(", ") { it.recipients.firstOrNull() ?: "Capsule" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Forest,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showAddToCapsule = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Forest),
            ) {
                Text("Add this to a capsule", color = Forest)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showCompostConfirm = true },
                enabled = !hasTagsOrCapsules,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, if (hasTagsOrCapsules) Forest25 else Earth),
            ) {
                Text("Compost", color = if (hasTagsOrCapsules) TextMuted else Earth)
            }
            if (hasTagsOrCapsules) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Compost requires no tags and no active capsule memberships.",
                    style = HeirloomsSerifItalic.copy(fontSize = 12.sp, color = TextMuted),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCompostConfirm) {
        AlertDialog(
            onDismissRequest = { showCompostConfirm = false },
            containerColor = Parchment,
            title = { Text("Compost this photo?", style = HeirloomsSerifItalic.copy(fontSize = 18.sp)) },
            text = { Text("It will be removed from your garden. You can restore it within 90 days.") },
            confirmButton = {
                Button(
                    onClick = { showCompostConfirm = false; onCompost() },
                    colors = ButtonDefaults.buttonColors(containerColor = Earth, contentColor = Parchment),
                ) { Text("Compost") }
            },
            dismissButton = {
                TextButton(onClick = { showCompostConfirm = false }) { Text("Keep", color = Forest) }
            },
        )
    }

    if (showAddToCapsule) {
        AddToCapsuleDialog(
            uploadId = upload.id,
            onDismiss = { showAddToCapsule = false },
            onAdded = { showAddToCapsule = false },
            onStartCapsule = { showAddToCapsule = false; onStartCapsule() },
        )
    }
}

@Composable
internal fun ExploreFlavour(
    upload: Upload,
    capsuleRefs: List<CapsuleRef>,
    tags: List<String>,
    rotation: Int,
    availableTags: List<String>,
    recentTags: List<String>,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    onTagsChange: (List<String>) -> Unit,
    onRotate: () -> Unit,
    onCapsuleTap: (String) -> Unit,
    decryptedBitmap: ImageBitmap? = null,
    decryptedVideoUri: Uri? = null,
    contentDek: ByteArray? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Box {
            MediaArea(
                upload = upload, rotation = rotation, modifier = Modifier.fillMaxWidth(),
                decryptedBitmap = decryptedBitmap, decryptedVideoUri = decryptedVideoUri,
                contentDek = contentDek,
            )
            if (!upload.isVideo) {
                IconButton(
                    onClick = onRotate,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.RotateRight,
                        contentDescription = "Rotate",
                        tint = Parchment,
                        modifier = Modifier
                            .background(Forest.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(4.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            upload.takenAt?.let {
                Text("Taken ${formatInstantDate(it)}", style = MaterialTheme.typography.bodyMedium, color = Forest)
                Spacer(Modifier.height(4.dp))
            }
            Text("Uploaded ${formatInstantDate(upload.uploadedAt)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)

            if (upload.latitude != null && upload.longitude != null) {
                Spacer(Modifier.height(4.dp))
                Text("${upload.latitude}, ${upload.longitude}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            val activeCapsules = capsuleRefs.filter { it.state == "open" || it.state == "sealed" }
            if (activeCapsules.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("In ${activeCapsules.size} capsule(s)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            Spacer(Modifier.height(12.dp))

            TagInputField(
                tags = tags,
                onTagsChange = onTagsChange,
                availableTags = availableTags,
                recentTags = recentTags,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CompostFlavour(
    upload: Upload,
    onRestore: () -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.6f) })
    Column(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        HeirloomsImage(
            url = LocalHeirloomsApi.current.fileUrl(upload.id),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.85f),
            contentScale = ContentScale.FillWidth,
            colorFilter = colorFilter,
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Uploaded ${formatInstantDate(upload.uploadedAt)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            upload.compostedAt?.let { composted ->
                Spacer(Modifier.height(8.dp))
                val days = daysUntilDeletion(composted)
                Text(
                    "Composted on ${formatInstantDate(composted)}. Will be permanently deleted in $days days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Forest15)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Restore") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Add to capsule dialog (unchanged) ─────────────────────────────────────────

@Composable
private fun AddToCapsuleDialog(
    uploadId: String,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    onStartCapsule: () -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var capsules by remember { mutableStateOf<List<CapsuleSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { capsules = api.listCapsules("open") } catch (_: Exception) {}
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Parchment,
        title = { Text("Add this to a capsule", style = MaterialTheme.typography.titleMedium) },
        text = {
            if (loading) {
                CircularProgressIndicator(color = Forest)
            } else if (capsules.isEmpty()) {
                Column {
                    Text("No open capsules to add this to.", style = HeirloomsSerifItalic.copy(fontSize = 16.sp, color = Forest))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onDismiss(); onStartCapsule() },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                        shape = RoundedCornerShape(22.dp),
                    ) { Text("Start a capsule with this", style = HeirloomsSerifItalic.copy(fontSize = 14.sp)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capsules.forEach { c ->
                        val isSelected = selectedId == c.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Forest08 else Parchment, RoundedCornerShape(8.dp))
                                .clickable { selectedId = c.id }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(c.recipients.joinToString(", "), style = HeirloomsSerifItalic.copy(color = Forest))
                                Text("Opens ${formatOffsetDate(c.unlockAt)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (capsules.isNotEmpty()) {
                Button(
                    onClick = {
                        val id = selectedId ?: return@Button
                        scope.launch {
                            saving = true
                            try {
                                val current = api.getCapsule(id)
                                val newIds = (current.uploads.map { it.id } + uploadId).distinct()
                                api.patchCapsuleUploads(id, newIds)
                                onAdded()
                            } catch (_: Exception) {}
                            saving = false
                        }
                    },
                    enabled = selectedId != null && !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text(if (saving) "Adding…" else "Add") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}
