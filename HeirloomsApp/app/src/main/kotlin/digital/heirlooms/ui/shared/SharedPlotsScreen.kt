package digital.heirlooms.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.PlotMember
import digital.heirlooms.api.SharedMembership
import digital.heirlooms.ui.brand.WorkingDots
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// ---- Name prompt dialog (accept / rejoin) ------------------------------------

@Composable
private fun NamePromptDialog(
    title: String,
    subtitle: String? = null,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(color = Forest)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name for this plot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Confirm", color = Forest) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
    )
}

// ---- Transfer ownership dialog -----------------------------------------------

@Composable
private fun TransferOwnershipDialog(
    members: List<PlotMember>,
    onConfirm: (PlotMember) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<PlotMember?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer ownership", style = MaterialTheme.typography.titleMedium.copy(color = Forest)) },
        text = {
            if (members.isEmpty()) {
                Text("No other joined members.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Choose a member to become the new owner.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                    )
                    Spacer(Modifier.height(8.dp))
                    members.forEach { member ->
                        val isSelected = selected?.userId == member.userId
                        OutlinedButton(
                            onClick = { selected = member },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) Forest15 else Color.Transparent,
                            ),
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(member.displayName, color = Forest, style = MaterialTheme.typography.bodyMedium)
                                Text("@${member.username}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it) } },
                enabled = selected != null && members.isNotEmpty(),
            ) { Text("Transfer", color = Forest) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
    )
}

// ---- Section header ----------------------------------------------------------

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(color = Forest, fontStyle = FontStyle.Italic),
        )
        if (count > 0) {
            Text(
                " ($count)",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
            )
        }
    }
}

// ---- Invitation card ---------------------------------------------------------

@Composable
private fun InvitationCard(
    membership: SharedMembership,
    onAccept: (localName: String) -> Unit,
) {
    var showPrompt by remember { mutableStateOf(false) }

    if (showPrompt) {
        NamePromptDialog(
            title = "Join \"${membership.plotName}\"",
            subtitle = "Choose a name for this plot in your garden.",
            onConfirm = { name -> showPrompt = false; onAccept(name) },
            onDismiss = { showPrompt = false },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Forest15),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = Forest.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(membership.plotName, style = MaterialTheme.typography.bodyMedium.copy(color = Forest))
            }
            if (membership.ownerDisplayName != null) {
                Text(
                    "From ${membership.ownerDisplayName}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                )
            }
            Button(
                onClick = { showPrompt = true },
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                modifier = Modifier.height(32.dp),
            ) { Text("Accept", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

// ---- Joined card -------------------------------------------------------------

@Composable
private fun JoinedCard(
    membership: SharedMembership,
    onLeave: () -> Unit,
    onToggleStatus: (String) -> Unit,
    onTransfer: () -> Unit,
) {
    val isOwner = membership.role == "owner"
    val isClosed = membership.plotStatus == "closed"
    val displayName = membership.localName ?: membership.plotName
    var showLeaveConfirm by remember { mutableStateOf(false) }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave plot?") },
            text = {
                Text(
                    if (isOwner)
                        "If you're the last member, \"$displayName\" will be removed (restorable within 90 days). Transfer ownership first if others are present."
                    else
                        "You'll leave \"$displayName\". You can re-join later from this screen."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLeaveConfirm = false; onLeave() }) {
                    Text("Leave", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") } },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Parchment),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = Forest.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(displayName, style = MaterialTheme.typography.bodyMedium.copy(color = Forest))
                if (isClosed) {
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("closed", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                }
                if (isOwner) {
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("owner", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                }
            }
            if (!isOwner && membership.ownerDisplayName != null) {
                Text(
                    "Shared by ${membership.ownerDisplayName}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOwner) {
                    OutlinedButton(
                        onClick = { onToggleStatus(if (isClosed) "open" else "closed") },
                        modifier = Modifier.height(32.dp),
                    ) { Text(if (isClosed) "Reopen" else "Close", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(
                        onClick = onTransfer,
                        modifier = Modifier.height(32.dp),
                    ) { Text("Transfer", style = MaterialTheme.typography.labelMedium) }
                }
                OutlinedButton(
                    onClick = { showLeaveConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.height(32.dp),
                ) { Text("Leave", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

// ---- Left card (re-join) ----------------------------------------------------

@Composable
private fun LeftCard(
    membership: SharedMembership,
    onRejoin: (localName: String?) -> Unit,
) {
    val displayName = membership.localName ?: membership.plotName
    var showPrompt by remember { mutableStateOf(false) }

    if (showPrompt) {
        NamePromptDialog(
            title = "Re-join \"${membership.plotName}\"",
            subtitle = if (membership.localName != null) "Previous name: \"${membership.localName}\"" else null,
            initialName = membership.localName ?: "",
            onConfirm = { name -> showPrompt = false; onRejoin(name) },
            onDismiss = { showPrompt = false },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Parchment),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth().then(Modifier.then(Modifier)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = Forest.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(displayName, style = MaterialTheme.typography.bodyMedium.copy(color = Forest.copy(alpha = 0.6f)))
            }
            if (membership.ownerDisplayName != null) {
                Text(
                    "Shared by ${membership.ownerDisplayName}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted.copy(alpha = 0.7f)),
                )
            }
            OutlinedButton(
                onClick = { showPrompt = true },
                modifier = Modifier.height(32.dp),
            ) { Text("Re-join", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

// ---- Tombstoned card (restore) ----------------------------------------------

@Composable
private fun TombstonedCard(
    membership: SharedMembership,
    onRestore: () -> Unit,
) {
    val displayName = membership.localName ?: membership.plotName

    val daysLeft = membership.tombstonedAt?.let {
        try {
            val tombstonedInstant = Instant.parse(it)
            val expireAt = tombstonedInstant.plus(90, ChronoUnit.DAYS)
            val remaining = Instant.now().until(expireAt, ChronoUnit.HOURS)
            ceil(remaining / 24.0).toInt().coerceAtLeast(0)
        } catch (_: Exception) { null }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Parchment),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = Forest.copy(alpha = 0.25f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Forest.copy(alpha = 0.4f),
                        fontStyle = FontStyle.Italic,
                    ),
                )
            }
            if (daysLeft != null) {
                Text(
                    if (daysLeft > 0) "$daysLeft days to restore" else "Restore window expired",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                )
            }
            if (daysLeft == null || daysLeft > 0) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.height(32.dp),
                ) { Text("Restore", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

// ---- Main screen -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPlotsScreen() {
    val api = LocalHeirloomsApi.current
    val vm: SharedPlotsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val transferMembers by vm.transferMembers.collectAsStateWithLifecycle()
    var transferPlotId by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state is SharedPlotsLoadState.Loading) vm.load(api) else vm.refresh(api)
    }

    if (actionError != null) {
        AlertDialog(
            onDismissRequest = { vm.clearActionError() },
            title = { Text("Action failed") },
            text = { Text(actionError!!) },
            confirmButton = { TextButton(onClick = { vm.clearActionError() }) { Text("OK") } },
        )
    }

    if (transferPlotId != null) {
        LaunchedEffect(transferPlotId) { vm.loadMembersForTransfer(api, transferPlotId!!) }
        TransferOwnershipDialog(
            members = transferMembers,
            onConfirm = { member ->
                vm.transferOwnership(api, transferPlotId!!, member.userId)
                transferPlotId = null
                vm.clearTransferMembers()
            },
            onDismiss = { transferPlotId = null; vm.clearTransferMembers() },
        )
    }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Shared plots",
                        style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic, color = Forest),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                vm.refresh(api)
                refreshing = false
            },
            modifier = Modifier.padding(innerPadding),
        ) {
            when (val s = state) {
                SharedPlotsLoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WorkingDots()
                    }
                }
                is SharedPlotsLoadState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextMuted,
                                fontStyle = FontStyle.Italic,
                            ),
                        )
                    }
                }
                is SharedPlotsLoadState.Ready -> {
                    val memberships = s.memberships
                    val invitations = memberships.filter { it.status == "invited" }
                    val joined = memberships.filter { it.status == "joined" && it.tombstonedAt == null }
                    val left = memberships.filter { it.status == "left" && it.tombstonedAt == null }
                    val tombstoned = memberships.filter { it.tombstonedAt != null }

                    if (memberships.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                Text(
                                    "No shared plots yet.",
                                    style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted, fontStyle = FontStyle.Italic),
                                )
                                Text(
                                    "Shared plots appear here when you're invited or when you create one from the garden.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (invitations.isNotEmpty()) {
                                item { SectionHeader("Invitations", invitations.size) }
                                items(invitations, key = { it.plotId }) { m ->
                                    InvitationCard(
                                        membership = m,
                                        onAccept = { name -> vm.acceptInvite(api, m.plotId, name) {} },
                                    )
                                }
                            }
                            if (joined.isNotEmpty()) {
                                item { SectionHeader("Joined", joined.size) }
                                items(joined, key = { it.plotId }) { m ->
                                    JoinedCard(
                                        membership = m,
                                        onLeave = { vm.leavePlot(api, m.plotId) },
                                        onToggleStatus = { newStatus -> vm.setPlotStatus(api, m.plotId, newStatus) },
                                        onTransfer = { transferPlotId = m.plotId },
                                    )
                                }
                            }
                            if (left.isNotEmpty()) {
                                item { SectionHeader("Left", left.size) }
                                items(left, key = { it.plotId }) { m ->
                                    LeftCard(
                                        membership = m,
                                        onRejoin = { name -> vm.rejoinPlot(api, m.plotId, name) {} },
                                    )
                                }
                            }
                            if (tombstoned.isNotEmpty()) {
                                item { SectionHeader("Recently removed", tombstoned.size) }
                                items(tombstoned, key = { it.plotId }) { m ->
                                    TombstonedCard(
                                        membership = m,
                                        onRestore = { vm.restorePlot(api, m.plotId) },
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}
