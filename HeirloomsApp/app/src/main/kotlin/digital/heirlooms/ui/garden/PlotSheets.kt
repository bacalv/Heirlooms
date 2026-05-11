package digital.heirlooms.ui.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import digital.heirlooms.api.Plot
import digital.heirlooms.ui.common.TagInputField
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotCreateSheet(
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, tagCriteria: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New plot", style = MaterialTheme.typography.titleMedium, color = Forest)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Plot name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Show items tagged with:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            TagInputField(
                tags = tags,
                onTagsChange = { tags = it },
                availableTags = availableTags,
                recentTags = emptyList(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), tags) },
                    enabled = name.isNotBlank(),
                ) { Text("Create", color = Forest) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotEditSheet(
    plot: Plot,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, tagCriteria: List<String>) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(plot.name) }
    var tags by remember { mutableStateOf(plot.tagCriteria) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete plot?") },
            text = { Text("\"${plot.name}\" will be removed. Items in this plot won't be deleted.") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit plot", style = MaterialTheme.typography.titleMedium, color = Forest)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Plot name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Show items tagged with:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            TagInputField(
                tags = tags,
                onTagsChange = { tags = it },
                availableTags = availableTags,
                recentTags = emptyList(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete plot", color = androidx.compose.ui.graphics.Color.Red)
                }
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                    TextButton(
                        onClick = { if (name.isNotBlank()) onSave(name.trim(), tags) },
                        enabled = name.isNotBlank(),
                    ) { Text("Save", color = Forest) }
                }
            }
        }
    }
}
