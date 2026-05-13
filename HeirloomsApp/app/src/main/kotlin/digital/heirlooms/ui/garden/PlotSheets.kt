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
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotCreateSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }

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
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
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
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit = {},
    onToggleStatus: ((String) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isShared = plot.visibility == "shared"
    val isMember = isShared && !plot.isOwner
    val isOwner = isShared && plot.isOwner
    val displayName = if (!isMember) plot.name else (plot.localName ?: plot.name)
    var name by remember { mutableStateOf(if (!isShared) plot.name else (plot.localName ?: plot.name)) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave plot?") },
            text = {
                Text(
                    if (isOwner)
                        "If you're the last member, \"$displayName\" will be removed (restorable within 90 days). Otherwise, transfer ownership first."
                    else
                        "You'll leave \"$displayName\". You can re-join from the Shared screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    if (isMember) onLeave() else onLeave()
                }) { Text("Leave", color = androidx.compose.ui.graphics.Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } },
        )
    }

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
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
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
            Text(if (isShared) "Shared plot" else "Edit plot", style = MaterialTheme.typography.titleMedium, color = Forest)
            if (!isShared) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plot name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isOwner && onToggleStatus != null) {
                val isClosed = plot.plotStatus == "closed"
                TextButton(
                    onClick = { onToggleStatus(if (isClosed) "open" else "closed") },
                ) {
                    Text(if (isClosed) "Reopen plot" else "Close plot", color = Forest)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    if (isShared) showLeaveConfirm = true else showDeleteConfirm = true
                }) {
                    Text(if (isShared) "Leave plot" else "Delete plot", color = androidx.compose.ui.graphics.Color.Red)
                }
                if (!isShared) {
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                        TextButton(
                            onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                            enabled = name.isNotBlank(),
                        ) { Text("Save", color = Forest) }
                    }
                } else {
                    TextButton(onClick = onDismiss) { Text("Done", color = TextMuted) }
                }
            }
        }
    }
}
