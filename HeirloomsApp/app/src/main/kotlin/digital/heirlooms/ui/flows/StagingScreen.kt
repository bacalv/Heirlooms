package digital.heirlooms.ui.flows

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
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
fun StagingScreen(
    flowId: String,
    plotId: String,
    onBack: () -> Unit,
    vm: StagingViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()

    LaunchedEffect(flowId, plotId) { vm.load(api, flowId, plotId) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Review", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.pending.isNotEmpty()) {
                    item(key = "pending_header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Text(
                            "Pending (${state.pending.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = Forest,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    items(state.pending, key = { "p_${it.id}" }) { upload ->
                        StagingTile(
                            upload = upload,
                            onApprove = { vm.approve(api, flowId, plotId, upload.id) },
                            onReject = { vm.reject(api, flowId, plotId, upload.id) },
                        )
                    }
                }

                if (state.rejected.isNotEmpty()) {
                    item(key = "rejected_header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Rejected (${state.rejected.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    items(state.rejected, key = { "r_${it.id}" }) { upload ->
                        RejectedTile(
                            upload = upload,
                            onRestore = { vm.restore(api, flowId, plotId, upload.id) },
                        )
                    }
                }

                if (state.pending.isEmpty() && state.rejected.isEmpty()) {
                    item(key = "empty", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Nothing to review.", color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StagingTile(upload: Upload, onApprove: () -> Unit, onReject: () -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = Forest08) {
        Box(Modifier.aspectRatio(1f)) {
            UploadThumbnail(
                upload = upload,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Row(
                Modifier.align(Alignment.BottomCenter).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = onApprove,
                    containerColor = Forest,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Approve", tint = Parchment, modifier = Modifier.size(16.dp))
                }
                SmallFloatingActionButton(
                    onClick = onReject,
                    containerColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.85f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Reject", tint = Parchment, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RejectedTile(upload: Upload, onRestore: () -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = Forest08.copy(alpha = 0.5f)) {
        Box(Modifier.aspectRatio(1f)) {
            UploadThumbnail(
                upload = upload,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver,
                ),
            )
            SmallFloatingActionButton(
                onClick = onRestore,
                containerColor = Parchment,
                modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp).size(32.dp),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = Forest, modifier = Modifier.size(16.dp))
            }
        }
    }
}
