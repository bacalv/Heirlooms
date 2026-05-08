@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems

/**
 * Garden tab: 2-column photo grid with tag filter chrome and compost-heap link.
 * Using 2 columns rather than the spec's 1 column: on a phone screen single-column
 * full-width thumbnails look sparse for a photo gallery. This matches the "authorised
 * revision" note in the brief.
 */
@Composable
fun GardenScreen(
    onPhotoTap: (String) -> Unit,
    onCompostHeapTap: () -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var uploads by remember { mutableStateOf<List<Upload>>(emptyList()) }
    var composteCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val allTags = remember(uploads) { uploads.flatMap { it.tags }.distinct().sorted() }
    val filtered = remember(uploads, selectedTag) {
        if (selectedTag == null) uploads else uploads.filter { selectedTag in it.tags }
    }

    fun load() {
        scope.launch {
            error = null
            try {
                val result = api.listUploads()
                uploads = result
                val composted = api.listCompostedUploads()
                composteCount = composted.size
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Parchment)) {
        TopAppBar(
            title = { Text("Garden", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = Forest)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    // placeholder for future items
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        if (allTags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lazyRowItems(listOf<String?>(null) + allTags) { tag ->
                    val isSelected = selectedTag == tag
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTag = if (isSelected && tag != null) null else tag },
                        label = { Text(tag ?: "All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Forest,
                            selectedLabelColor = Parchment,
                        ),
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; load() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                loading -> SkeletonGrid()
                error != null -> DidntTake(onRetry = { loading = true; load() })
                filtered.isEmpty() -> GardenEmptyState()
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { upload ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onPhotoTap(upload.id) }
                        ) {
                            HeirloomsImage(
                                url = api.thumbUrl(upload.id),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Compost heap ($composteCount)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 14.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCompostHeapTap() }
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    }
                }
            }
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
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(8) {
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Forest15)
            )
        }
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
            "didn't take",
            style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text("try again", style = MaterialTheme.typography.bodyMedium.copy(color = Forest))
        }
    }
}
