@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.brand.compostHeapEmptyStateLines
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.daysUntilDeletion
import digital.heirlooms.ui.common.formatInstantDate
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun CompostHeapScreen(onBack: () -> Unit) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var uploads by remember { mutableStateOf<List<Upload>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Pick once per session; stable for the lifetime of this composition.
    val emptyLine = remember { compostHeapEmptyStateLines.random() }

    fun load() {
        scope.launch {
            error = null
            try {
                uploads = api.listCompostedUploads()
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Compost heap", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Forest)
                }
                error != null -> DidntTake(onRetry = { loading = true; load() })
                uploads.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        emptyLine,
                        style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(uploads, key = { it.id }) { upload ->
                        HeapRow(
                            upload = upload,
                            onRestore = {
                                scope.launch {
                                    try {
                                        api.restoreUpload(upload.id)
                                        uploads = uploads.filter { it.id != upload.id }
                                    } catch (_: Exception) {}
                                }
                            },
                        )
                        HorizontalDivider(color = Forest15)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeapRow(upload: Upload, onRestore: () -> Unit) {
    val api = LocalHeirloomsApi.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeirloomsImage(
            url = api.thumbUrl(upload.id),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )

        Column(Modifier.weight(1f)) {
            Text(
                "Uploaded ${formatInstantDate(upload.uploadedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Forest,
            )
            Text(
                upload.storageKey.substringAfterLast("/"),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            upload.compostedAt?.let { compostedAt ->
                Text(
                    "Composted ${formatInstantDate(compostedAt)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                val days = daysUntilDeletion(compostedAt)
                Text(
                    "$days days left.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            OutlinedButton(
                onClick = onRestore,
                border = androidx.compose.foundation.BorderStroke(1.dp, Forest),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 4.dp,
                ),
            ) {
                Text("Restore", style = MaterialTheme.typography.bodySmall, color = Forest)
            }
        }
    }
}
