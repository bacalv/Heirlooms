@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.TagInputField
import digital.heirlooms.ui.garden.DidntTake
import digital.heirlooms.ui.share.RecentTagsStore
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    initialTags: List<String> = emptyList(),
    initialJustArrived: Boolean = false,
    onPhotoTap: (String) -> Unit,
    vm: ExploreViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()
    val availableTags by vm.availableTags.collectAsState()

    // Apply incoming route filters only on first composition (avoids overwriting
    // the user's edits when they navigate back and the screen recomposes).
    val filtersApplied = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!filtersApplied.value) {
            filtersApplied.value = true
            if (initialTags.isNotEmpty() || initialJustArrived) {
                vm.applyInitialFilters(initialTags, initialJustArrived)
            }
            vm.load(api)
        }
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = vm.scrollPosition)

    LaunchedEffect(gridState.firstVisibleItemIndex) {
        vm.scrollPosition = gridState.firstVisibleItemIndex
    }

    val filters = vm.filters
    val activeFilterCount = listOfNotNull(
        filters.tags.takeIf { it.isNotEmpty() },
        filters.justArrived.takeIf { it },
        filters.fromDate,
        filters.toDate,
        filters.inCapsule,
        filters.hasLocation,
        filters.includeComposted.takeIf { it },
    ).size

    Column(Modifier.fillMaxSize().background(Parchment)) {
        TopAppBar(
            title = { Text("Explore", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            actions = {
                TextButton(onClick = { showFilterSheet = true }) {
                    Text(
                        if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters",
                        color = Forest,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        // Active filter chips summary row.
        if (activeFilterCount > 0) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (filters.justArrived) {
                    ActiveChip(label = "Just arrived") {
                        vm.updateFilters(api, filters.copy(justArrived = false))
                    }
                }
                filters.tags.forEach { tag ->
                    ActiveChip(label = tag) {
                        vm.updateFilters(api, filters.copy(tags = filters.tags - tag))
                    }
                }
                filters.fromDate?.let { ActiveChip(label = "From $it") { vm.updateFilters(api, filters.copy(fromDate = null)) } }
                filters.toDate?.let { ActiveChip(label = "To $it") { vm.updateFilters(api, filters.copy(toDate = null)) } }
                filters.inCapsule?.let { v ->
                    ActiveChip(label = if (v) "In capsule" else "Not in capsule") {
                        vm.updateFilters(api, filters.copy(inCapsule = null))
                    }
                }
                filters.hasLocation?.let { v ->
                    ActiveChip(label = if (v) "Has location" else "No location") {
                        vm.updateFilters(api, filters.copy(hasLocation = null))
                    }
                }
                if (filters.includeComposted) {
                    ActiveChip(label = "Including composted") {
                        vm.updateFilters(api, filters.copy(includeComposted = false))
                    }
                }
            }
        }

        when (val s = state) {
            is ExploreState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
            is ExploreState.Error -> DidntTake(onRetry = { vm.load(api) })
            is ExploreState.Ready -> {
                if (s.uploads.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nothing here yet.",
                            style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest),
                        )
                    }
                } else {
                    val sortByTaken = filters.sort.startsWith("taken")
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(s.uploads, key = { it.id }) { upload ->
                            UploadThumbnail(
                                upload = upload,
                                showNoDateBadge = sortByTaken && upload.takenAt == null,
                                onClick = { onPhotoTap(upload.id) },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            when {
                                s.loadingMore -> Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp))
                                }
                                s.nextCursor != null -> OutlinedButton(
                                    onClick = { vm.loadMore(api) },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Forest),
                                ) {
                                    Text("Load more", color = Forest)
                                }
                                else -> Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = filters,
            availableTags = availableTags,
            sheetState = filterSheetState,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilters ->
                showFilterSheet = false
                vm.updateFilters(api, newFilters)
            },
        )
    }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        trailingIcon = {
            Text("×", style = MaterialTheme.typography.bodySmall, color = Forest)
        },
    )
}

@Composable
private fun UploadThumbnail(upload: Upload, showNoDateBadge: Boolean, onClick: () -> Unit) {
    val desaturate = upload.compostedAt != null
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
    ) {
        HeirloomsImage(
            url = LocalHeirloomsApi.current.thumbUrl(upload.id),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            rotation = upload.rotation,
            colorFilter = if (desaturate) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        )
        if (showNoDateBadge) {
            Text(
                "no date",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = Parchment,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Forest.copy(alpha = 0.65f), RoundedCornerShape(topEnd = 4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: ExploreFilters,
    availableTags: List<String>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onApply: (ExploreFilters) -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    val context = LocalContext.current
    val recentTags = remember(context) { RecentTagsStore(context).load().take(5) }

    val sortOptions = listOf(
        "upload_newest" to "Newest upload",
        "upload_oldest" to "Oldest upload",
        "taken_newest" to "Newest taken",
        "taken_oldest" to "Oldest taken",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Parchment,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleMedium, color = Forest)

            // Sort
            FilterSection("Sort") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sortOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = draft.sort == value,
                            onClick = { draft = draft.copy(sort = value) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Forest,
                                selectedLabelColor = Parchment,
                            ),
                        )
                    }
                }
            }

            // Tags
            FilterSection("Tags") {
                TagInputField(
                    tags = draft.tags,
                    onTagsChange = { draft = draft.copy(tags = it) },
                    availableTags = availableTags,
                    recentTags = recentTags,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // In capsule
            FilterSection("Capsule") {
                SingleChoiceSegmentedButtonRow {
                    listOf(null to "Any", true to "In capsule", false to "Not in capsule").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = draft.inCapsule == v,
                            onClick = { draft = draft.copy(inCapsule = v) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            // Has location
            FilterSection("Location") {
                SingleChoiceSegmentedButtonRow {
                    listOf(null to "Any", true to "Has location", false to "No location").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = draft.hasLocation == v,
                            onClick = { draft = draft.copy(hasLocation = v) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(label, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            // Composted toggle
            FilterChip(
                selected = draft.includeComposted,
                onClick = { draft = draft.copy(includeComposted = !draft.includeComposted) },
                label = { Text("Include composted") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Forest,
                    selectedLabelColor = Parchment,
                ),
            )

            HorizontalDivider(color = Forest15)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { draft = ExploreFilters(); onApply(ExploreFilters()) },
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Forest15),
                ) { Text("Clear all", color = TextMuted) }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun FilterSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        content()
    }
}
