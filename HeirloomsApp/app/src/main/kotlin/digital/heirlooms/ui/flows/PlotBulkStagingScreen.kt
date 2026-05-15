package digital.heirlooms.ui.trellises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.UploadThumbnail
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotBulkStagingScreen(
    plotId: String,
    plotName: String,
    onBack: () -> Unit,
    vm: PlotBulkStagingViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()

    LaunchedEffect(plotId) { vm.load(api, plotId) }

    Column(Modifier.fillMaxSize().background(Parchment)) {
        TopAppBar(
            title = { Text(plotName, style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        if (state.error != null) {
            Text(
                state.error!!,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Red),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            state.loading -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            }
            state.items.isEmpty() -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.doneCount > 0) {
                            Text(
                                "All done — ${state.doneCount} item(s) processed.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Forest),
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        } else {
                            Text("Nothing pending.", color = TextMuted)
                        }
                    }
                }
            }
            else -> {
                // Header row: "Select all" checkbox + count
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.allSelected,
                        onCheckedChange = { vm.toggleSelectAll() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Forest,
                            checkmarkColor = Parchment,
                        ),
                    )
                    Text(
                        if (state.allSelected) "Deselect all" else "Select all (${state.items.size})",
                        style = MaterialTheme.typography.bodySmall.copy(color = Forest),
                        modifier = Modifier.clickable { vm.toggleSelectAll() },
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.selected.isNotEmpty()) {
                        Text(
                            "${state.selected.size} selected",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.items, key = { it.id }) { upload ->
                        BulkStagingTile(
                            upload = upload,
                            isSelected = upload.id in state.selected,
                            onToggle = { vm.toggleItem(upload.id) },
                        )
                    }
                }
            }
        }

        // Bottom action bar
        if (!state.loading && state.items.isNotEmpty()) {
            Surface(
                shadowElevation = 4.dp,
                color = Parchment,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val hasSelection = state.selected.isNotEmpty()
                    OutlinedButton(
                        onClick = { vm.rejectSelected(api, plotId) },
                        enabled = hasSelection && !state.working,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    ) {
                        if (state.working) {
                            CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Reject selected")
                        }
                    }
                    Button(
                        onClick = { vm.approveSelected(api, plotId) },
                        enabled = hasSelection && !state.working,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest),
                    ) {
                        if (state.working) {
                            CircularProgressIndicator(color = Parchment, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Approve selected", color = Parchment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkStagingTile(
    upload: Upload,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Forest08,
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onToggle)
            .then(
                if (isSelected) Modifier.border(2.dp, Forest, RoundedCornerShape(8.dp))
                else Modifier
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            UploadThumbnail(
                upload = upload,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            // Selection checkmark overlay
            Box(
                Modifier
                    .padding(4.dp)
                    .size(22.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(if (isSelected) Forest else Parchment.copy(alpha = 0.75f))
                    .border(1.5.dp, if (isSelected) Forest else Forest.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Parchment,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
