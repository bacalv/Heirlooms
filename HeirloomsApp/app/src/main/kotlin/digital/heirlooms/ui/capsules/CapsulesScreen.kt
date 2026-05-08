@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.capsules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.CapsuleSummary
import digital.heirlooms.ui.brand.WaxSealOlive
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.formatOffsetDate
import digital.heirlooms.ui.garden.DidntTake
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch

private enum class StateFilter(val label: String, val stateParam: String) {
    Active("Active", "open,sealed"),
    Delivered("Delivered", "delivered"),
    Cancelled("Cancelled", "cancelled"),
    All("All", "open,sealed,delivered,cancelled"),
}

@Composable
fun CapsulesScreen(
    onCapsuleTap: (String) -> Unit,
    onStartCapsule: () -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var capsules by remember { mutableStateOf<List<CapsuleSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(StateFilter.Active) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            error = null
            try {
                capsules = api.listCapsules(filter.stateParam)
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(filter) { loading = true; load() }

    Column(Modifier.fillMaxSize().background(Parchment)) {
        TopAppBar(
            title = { Text("Capsules", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            actions = {
                TextButton(onClick = { showFilterMenu = true }) {
                    Text("${filter.label} ▾", style = MaterialTheme.typography.bodyMedium, color = Forest)
                }
                DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                    StateFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label) },
                            onClick = { filter = f; showFilterMenu = false },
                        )
                    }
                }
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = Forest)
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    // placeholder for future overflow items
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; load() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Forest)
                }
                error != null -> DidntTake(onRetry = { loading = true; load() })
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Button(
                            onClick = onStartCapsule,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Forest,
                                contentColor = Parchment,
                            ),
                        ) {
                            Text(
                                "+ Start a capsule",
                                style = HeirloomsSerifItalic.copy(fontSize = 16.sp),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    if (capsules.isEmpty()) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "A garden grows things to keep.\nA capsule grows things to give.",
                                    style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(capsules, key = { it.id }) { capsule ->
                            CapsuleCard(capsule = capsule, onTap = { onCapsuleTap(capsule.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleCard(capsule: CapsuleSummary, onTap: () -> Unit) {
    val bgColor = when (capsule.state) {
        "delivered" -> Bloom.copy(alpha = 0.15f)
        "cancelled" -> Earth.copy(alpha = 0.10f)
        else -> Parchment
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = bgColor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    capsule.recipients.joinToString(", "),
                    style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "To open on ${formatOffsetDate(capsule.unlockAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (capsule.shape == "sealed") {
                    WaxSealOlive(
                        modifier = Modifier.size(24.dp),
                        color = Bloom,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${capsule.uploadCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}
