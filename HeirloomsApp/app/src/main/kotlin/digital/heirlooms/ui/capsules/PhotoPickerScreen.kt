@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.capsules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.UploadThumbnail
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun PhotoPickerScreen(
    navController: NavHostController,
    onDismiss: () -> Unit,
) {
    val api = LocalHeirloomsApi.current

    var uploads by remember { mutableStateOf<List<Upload>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    // Read any pre-selected IDs passed from CapsuleCreateScreen
    val preSelected = navController.currentBackStackEntry
        ?.savedStateHandle?.get<List<String>>("pickerPreselected")
    LaunchedEffect(preSelected) {
        if (preSelected != null) selectedIds = preSelected.toSet()
    }

    val allTags = remember(uploads) { uploads.flatMap { it.tags }.distinct().sorted() }
    val filtered = remember(uploads, selectedTag) {
        if (selectedTag == null) uploads else uploads.filter { selectedTag in it.tags }
    }

    LaunchedEffect(Unit) {
        try {
            // Fetch first 200 non-composted uploads for the picker (no pagination UI here).
            uploads = api.listUploadsPage(limit = 200).uploads
        } catch (_: Exception) {}
        loading = false
    }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Choose what to include", style = MaterialTheme.typography.bodyLarge.copy(color = Forest)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Forest)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("pickerResult", selectedIds.toList())
                            navController.popBackStack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("Done (${selectedIds.size})", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), start = 2.dp, end = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (allTags.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(listOf<String?>(null) + allTags) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = if (selectedTag == tag && tag != null) null else tag },
                                label = { Text(tag ?: "All") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Forest,
                                    selectedLabelColor = Parchment,
                                ),
                            )
                        }
                    }
                }
            }

            items(filtered, key = { it.id }) { upload ->
                val isSelected = upload.id in selectedIds
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .clickable {
                            selectedIds = if (isSelected) selectedIds - upload.id
                            else selectedIds + upload.id
                        }
                ) {
                    UploadThumbnail(
                        upload = upload,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isSelected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Bloom.copy(alpha = 0.5f))
                        )
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Parchment,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
