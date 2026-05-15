package digital.heirlooms.ui.trellises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.Plot
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrellisesScreen(
    onBack: () -> Unit,
    onStagingTap: (trellisId: String, plotId: String, isSharedPlot: Boolean) -> Unit,
    vm: TrellisesViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load(api) }

    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        val plots = (state as? TrellisesState.Ready)?.plots?.filter { !it.isSystemDefined } ?: emptyList()
        CreateTrellisDialog(
            plots = plots,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, plotId, staging, criteria ->
                showCreateDialog = false
                vm.createTrellis(api, name, plotId, staging, criteria)
            },
        )
    }

    Column(Modifier.fillMaxSize().background(Parchment)) {
        TopAppBar(
            title = { Text("Trellises", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        Box(Modifier.weight(1f)) {
            when (val s = state) {
                is TrellisesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
                is TrellisesState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = TextMuted)
                }
                is TrellisesState.Ready -> {
                    if (s.trellises.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No trellises yet. Trellises route items to plots automatically.", color = TextMuted,
                                modifier = Modifier.padding(32.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.trellises, key = { it.trellis.id }) { twc ->
                                TrellisCard(
                                    trellisWithCount = twc,
                                    targetPlot = s.plots.find { it.id == twc.trellis.targetPlotId },
                                    onStagingTap = { onStagingTap(twc.trellis.id, twc.trellis.targetPlotId, s.plots.find { it.id == twc.trellis.targetPlotId }?.visibility == "shared") },
                                    onDelete = { vm.deleteTrellis(api, twc.trellis.id) },
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = Forest,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add trellis", tint = Parchment)
            }
        }
    }
}

@Composable
private fun TrellisCard(
    trellisWithCount: TrellisWithCount,
    targetPlot: Plot?,
    onStagingTap: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete trellis?") },
            text = { Text("\"${trellisWithCount.trellis.name}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = androidx.compose.ui.graphics.Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = Forest08,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(trellisWithCount.trellis.name, style = MaterialTheme.typography.bodyLarge, color = Forest)
                if (targetPlot != null) {
                    Text("→ ${targetPlot.name}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                if (trellisWithCount.trellis.requiresStaging) {
                    Text("Requires approval", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (trellisWithCount.trellis.requiresStaging && trellisWithCount.pendingCount > 0) {
                TextButton(onClick = onStagingTap) {
                    Badge(containerColor = Forest) {
                        Text("${trellisWithCount.pendingCount}", color = Parchment)
                    }
                    Text("  Review", color = Forest)
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

internal fun buildCriteria(
    tags: List<String>, mediaType: String?, fromDate: String, toDate: String,
    hasLocation: Boolean, isReceived: Boolean,
): String {
    val atoms = mutableListOf<String>()
    tags.forEach { atoms.add("""{"type":"tag","tag":"${it.replace("\"", "\\\"")}"}""") }
    if (mediaType != null) atoms.add("""{"type":"media_type","value":"$mediaType"}""")
    if (fromDate.isNotBlank()) atoms.add("""{"type":"taken_after","date":"$fromDate"}""")
    if (toDate.isNotBlank()) atoms.add("""{"type":"taken_before","date":"$toDate"}""")
    if (hasLocation) atoms.add("""{"type":"has_location"}""")
    if (isReceived) atoms.add("""{"type":"is_received"}""")
    return when (atoms.size) {
        0 -> """{"type":"just_arrived"}"""
        1 -> atoms[0]
        else -> """{"type":"and","operands":[${atoms.joinToString(",")}]}"""
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateTrellisDialog(
    plots: List<Plot>,
    onDismiss: () -> Unit,
    onCreate: (name: String, plotId: String, requiresStaging: Boolean, criteria: String) -> Unit,
    initialPlotId: String? = null,
) {
    var name by remember { mutableStateOf("") }
    var selectedPlotIndex by remember {
        mutableStateOf(
            if (initialPlotId != null) plots.indexOfFirst { it.id == initialPlotId }.coerceAtLeast(0) else 0
        )
    }
    var requiresStaging by remember { mutableStateOf(true) }
    // autoApprove is the UI-facing inversion: autoApprove=true means requiresStaging=false
    var autoApprove by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf<String?>(null) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var hasLocation by remember { mutableStateOf(false) }
    var isReceived by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New trellis", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trellis name") },
                    placeholder = { Text("e.g. Photos of Sadaar → Family", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (plots.isNotEmpty()) {
                    Text("Target plot", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    plots.forEachIndexed { i, plot ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedPlotIndex == i,
                                onClick = { selectedPlotIndex = i },
                            )
                            Text(plot.name, color = Forest, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                Text("Criteria — items matching these enter the trellis", style = MaterialTheme.typography.labelMedium, color = TextMuted)

                // Tags
                Text("Tags (all must match)", style = MaterialTheme.typography.bodySmall, color = Forest)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = { Text("Add tag…", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val t = tagInput.trim().lowercase()
                        if (t.isNotEmpty() && !tags.contains(t)) tags = tags + t
                        tagInput = ""
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add tag", tint = Forest)
                    }
                }
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { tags = tags - tag },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) },
                            )
                        }
                    }
                }

                // Media type
                Text("Media type", style = MaterialTheme.typography.bodySmall, color = Forest)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(null to "All", "image" to "Photos", "video" to "Videos").forEach { (value, label) ->
                        val selected = mediaType == value
                        if (selected) {
                            androidx.compose.material3.Button(
                                onClick = { mediaType = value },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Forest),
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        } else {
                            OutlinedButton(onClick = { mediaType = value }) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Forest)
                            }
                        }
                    }
                }

                // Dates
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        label = { Text("From date") },
                        placeholder = { Text("yyyy-mm-dd", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = toDate,
                        onValueChange = { toDate = it },
                        label = { Text("To date") },
                        placeholder = { Text("yyyy-mm-dd", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Has location
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Has location", style = MaterialTheme.typography.bodySmall, color = Forest)
                    Switch(checked = hasLocation, onCheckedChange = { hasLocation = it })
                }

                // Received only
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Received items only", style = MaterialTheme.typography.bodySmall, color = Forest)
                    Switch(checked = isReceived, onCheckedChange = { isReceived = it })
                }

                // Auto-approve toggle (inverted: autoApprove=true → requiresStaging=false)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-approve", style = MaterialTheme.typography.bodySmall, color = Forest)
                        Text(
                            "Items skip staging and go straight to the plot",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                        )
                    }
                    Switch(
                        checked = autoApprove,
                        onCheckedChange = { autoApprove = it; requiresStaging = !it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val plotId = plots.getOrNull(selectedPlotIndex)?.id ?: return@TextButton
                    if (name.isNotBlank()) {
                        val criteria = buildCriteria(tags, mediaType, fromDate, toDate, hasLocation, isReceived)
                        onCreate(name.trim(), plotId, requiresStaging, criteria)
                    }
                },
                enabled = name.isNotBlank() && plots.isNotEmpty(),
            ) { Text("Create", color = Forest) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = Parchment,
    )
}
