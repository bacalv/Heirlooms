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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.share.isValidTag
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch

private const val THUMBNAIL_SIZE_DP = 108

@Composable
fun GardenScreen(
    onPhotoTap: (String) -> Unit,
    onNavigateToExplore: (tags: List<String>, justArrived: Boolean) -> Unit,
    vm: GardenViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (state is GardenLoadState.Loading) vm.load(api) }

    fun refresh() {
        refreshing = true
        scope.launch { vm.load(api); refreshing = false }
    }

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
                                val rowLabel = row.plot?.name ?: "Just arrived"
                                val isJustArrived = row.plot == null
                                val tags = row.plot?.tagCriteria ?: emptyList()

                                PlotRowSection(
                                    label = rowLabel,
                                    uploads = row.uploads,
                                    nextCursor = row.nextCursor,
                                    loadingMore = row.loadingMore,
                                    savedScrollIndex = vm.scrollPositions[rowKey] ?: 0,
                                    onScrollIndex = { vm.saveScrollPosition(rowKey, it) },
                                    onPhotoTap = onPhotoTap,
                                    onTitleTap = { onNavigateToExplore(tags, isJustArrived) },
                                    onLoadMore = { vm.loadMoreForRow(api, index) },
                                    onExploreAll = { onNavigateToExplore(tags, isJustArrived) },
                                    isJustArrived = isJustArrived,
                                    onQuickRotate = { uploadId, currentRotation ->
                                        val newRotation = (currentRotation + 90) % 360
                                        vm.optimisticRotate(uploadId, newRotation)
                                        scope.launch {
                                            try { api.rotateUpload(uploadId, newRotation) }
                                            catch (_: Exception) { vm.optimisticRotate(uploadId, currentRotation) }
                                        }
                                    },
                                    onQuickTag = { uploadId, currentTags, newTag ->
                                        val updated = currentTags + newTag
                                        vm.optimisticTag(uploadId, updated)
                                        scope.launch {
                                            try { api.updateTags(uploadId, updated) }
                                            catch (_: Exception) { vm.optimisticTag(uploadId, currentTags) }
                                        }
                                    },
                                    emptyLabel = if (isJustArrived) "Nothing waiting." else "No items match this plot's tags.",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotRowSection(
    label: String,
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
    onQuickRotate: (uploadId: String, currentRotation: Int) -> Unit,
    onQuickTag: (uploadId: String, currentTags: List<String>, newTag: String) -> Unit,
    emptyLabel: String,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedScrollIndex)

    LaunchedEffect(listState.firstVisibleItemIndex) {
        onScrollIndex(listState.firstVisibleItemIndex)
    }

    Column(Modifier.padding(top = 16.dp)) {
        // Interactive row title
        Row(
            modifier = Modifier
                .clickable(onClick = onTitleTap)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(color = Forest),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Forest.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
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
                    var showAddTag by remember { mutableStateOf(false) }

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
                            HeirloomsImage(
                                url = LocalHeirloomsApi.current.thumbUrl(upload.id),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                rotation = upload.rotation,
                            )
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
                                onClick = { showMenu = false; showAddTag = true },
                            )
                        }
                        if (showAddTag) {
                            QuickTagDialog(
                                onDismiss = { showAddTag = false },
                                onAdd = { tag ->
                                    showAddTag = false
                                    onQuickTag(upload.id, upload.tags, tag)
                                },
                            )
                        }
                    }
                }

                // End-of-row affordances
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
private fun QuickTagDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val isValid = isValidTag(input.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Parchment,
        title = { Text("Add tag", style = MaterialTheme.typography.titleMedium.copy(color = Forest)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("e.g. family", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (isValid) onAdd(input.trim())
                }),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onAdd(input.trim()) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
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
        TextButton(onClick = onRetry) {
            Text("Try again", style = MaterialTheme.typography.bodyMedium.copy(color = Forest))
        }
    }
}
