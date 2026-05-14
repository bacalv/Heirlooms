package digital.heirlooms.ui.garden

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch

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
            if (isOwner) {
                var showInvite by remember { mutableStateOf(false) }
                if (showInvite) {
                    InviteMemberSheet(plot = plot, onDismiss = { showInvite = false })
                }
                TextButton(onClick = { showInvite = true }) {
                    Text("Invite member", color = Forest)
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMemberSheet(plot: Plot, onDismiss: () -> Unit) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    data class FriendItem(val userId: String, val displayName: String, val username: String)

    var friends by remember { mutableStateOf<List<FriendItem>?>(null) }
    var selectedIndex by remember { mutableStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            friends = api.getFriends().map { FriendItem(it.userId, it.displayName, it.username) }
        } catch (e: Exception) {
            error = "Couldn't load friends: ${e.message}"
        }
    }

    suspend fun loadPlotKey(api: HeirloomsApi): ByteArray {
        VaultSession.getPlotKey(plot.id)?.let { return it }
        val (wrappedKey, _) = api.getPlotKey(plot.id)
        val privkey = VaultSession.sharingPrivkey ?: error("Sharing key not loaded")
        val raw = VaultCrypto.unwrapPlotKey(Base64.decode(wrappedKey, Base64.NO_WRAP), privkey)
        VaultSession.setPlotKey(plot.id, raw)
        return raw
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Parchment,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Invite member", style = MaterialTheme.typography.titleMedium, color = Forest)
            when {
                done -> Text("Invitation sent.", color = Forest)
                error != null -> Text(error!!, color = androidx.compose.ui.graphics.Color.Red, style = MaterialTheme.typography.bodySmall)
                friends == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
                friends!!.isEmpty() -> Text("No friends yet.", color = TextMuted)
                else -> {
                    friends!!.forEachIndexed { i, friend ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedIndex == i, onClick = { selectedIndex = i })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(friend.displayName, color = Forest, style = MaterialTheme.typography.bodyMedium)
                                Text("@${friend.username}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                if (!done && friends?.isNotEmpty() == true) {
                    TextButton(
                        onClick = {
                            val friend = friends?.getOrNull(selectedIndex) ?: return@TextButton
                            working = true; error = null
                            scope.launch {
                                try {
                                    val plotKey = loadPlotKey(api)
                                    val pubkey = api.getFriendSharingPubkey(friend.userId)
                                        ?: error("${friend.displayName} hasn't set up sharing")
                                    val wrapped = VaultCrypto.wrapPlotKeyForMember(plotKey, pubkey)
                                    api.addPlotMember(
                                        plot.id, friend.userId,
                                        Base64.encodeToString(wrapped, Base64.NO_WRAP),
                                        VaultCrypto.ALG_P256_ECDH_HKDF_V1,
                                    )
                                    done = true
                                } catch (e: Exception) {
                                    error = e.message ?: "Couldn't send invite"
                                } finally { working = false }
                            }
                        },
                        enabled = !working,
                    ) { Text(if (working) "Sending…" else "Invite", color = Forest) }
                }
            }
        }
    }
}
