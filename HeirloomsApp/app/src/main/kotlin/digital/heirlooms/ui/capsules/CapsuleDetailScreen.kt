@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package digital.heirlooms.ui.capsules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.CapsuleDetail
import digital.heirlooms.ui.brand.AnimatedWaxSeal
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.UploadThumbnail
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.formatInstantDate
import digital.heirlooms.ui.common.formatOffsetDate
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState

@Composable
fun CapsuleDetailScreen(
    capsuleId: String,
    onBack: () -> Unit,
    onPhotoTap: (String) -> Unit,
    vm: CapsuleDetailViewModel = viewModel(key = capsuleId),
) {
    val api = LocalHeirloomsApi.current
    val vmState by vm.state.collectAsState()

    var showSealDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var sealing by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var triggerSealAnimation by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // Adapt VM state to the local variables the existing body expects.
    val loading = vmState is CapsuleDetailState.Loading
    val error = (vmState as? CapsuleDetailState.Error)?.message
    val capsule = (vmState as? CapsuleDetailState.Ready)?.capsule

    LaunchedEffect(capsuleId) { vm.load(api, capsuleId) }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Capsules", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                    }
                },
                actions = {
                    val c = capsule
                    if (c != null && c.state == "open") {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Forest)
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Seal capsule") },
                                onClick = { showOverflow = false; showSealDialog = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
            error != null -> digital.heirlooms.ui.garden.LoadError(onRetry = { vm.load(api, capsuleId) })
            else -> {
                val c = capsule ?: return@Scaffold
                val bgColor = when (c.state) {
                    "delivered" -> Bloom.copy(alpha = 0.10f)
                    "cancelled" -> Earth.copy(alpha = 0.08f)
                    else -> Parchment
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(bgColor)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    // Identity block
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.recipients.joinToString(", "),
                                style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "To open on ${formatOffsetDate(c.unlockAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                            )
                        }
                        if (c.state == "sealed" || triggerSealAnimation) {
                            AnimatedWaxSeal(
                                sealed = triggerSealAnimation || c.state == "sealed",
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }

                    // State metadata
                    when (c.state) {
                        "delivered" -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Delivered on ${c.deliveredAt?.let { formatInstantDate(it) } ?: ""}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                        "cancelled" -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Cancelled on ${c.cancelledAt?.let { formatInstantDate(it) } ?: ""}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Forest15)
                    Spacer(Modifier.height(16.dp))

                    // Message
                    if (c.message.isNotBlank()) {
                        val msgStyle = when (c.state) {
                            "sealed", "delivered" -> HeirloomsSerifItalic.copy(fontSize = 16.sp, color = Forest)
                            else -> MaterialTheme.typography.bodyLarge.copy(color = Forest)
                        }
                        Text(c.message, style = msgStyle)
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = Forest15)
                        Spacer(Modifier.height(16.dp))
                    }

                    // Photo grid (2-col, non-lazy, inside scrollable parent)
                    if (c.uploads.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            maxItemsInEachRow = 2,
                        ) {
                            c.uploads.forEach { upload ->
                                UploadThumbnail(
                                    upload = upload,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onPhotoTap(upload.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = Forest15)
                        Spacer(Modifier.height(16.dp))
                    }

                    // Action region
                    when (c.state) {
                        "open" -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Earth),
                                ) {
                                    Text("Cancel capsule", color = Earth)
                                }
                            }
                        }
                        "sealed" -> {
                            OutlinedButton(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Earth),
                            ) {
                                Text("Cancel capsule", color = Earth)
                            }
                        }
                        // delivered and cancelled have no action region
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showSealDialog) {
        AlertDialog(
            onDismissRequest = { showSealDialog = false },
            containerColor = Parchment,
            title = {
                Text(
                    "Seal this capsule?",
                    style = HeirloomsSerifItalic.copy(fontSize = 18.sp),
                )
            },
            text = {
                Text(
                    "Once sealed, you can't add or remove items. You'll still be able to edit the message, recipients, and date until it opens.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSealDialog = false
                        sealing = true
                        vm.seal(api, capsuleId)
                        triggerSealAnimation = true
                        sealing = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text("Seal capsule") }
            },
            dismissButton = {
                TextButton(onClick = { showSealDialog = false }) { Text("Cancel", color = TextMuted) }
            },
        )
    }

    if (showCancelDialog) {
        val recipientLine = capsule?.recipients?.let { r ->
            if (r.size == 1) "${r.first()} won't receive it." else "They won't receive it."
        } ?: ""
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = Parchment,
            title = {
                Text(
                    "Cancel this capsule?",
                    style = HeirloomsSerifItalic.copy(fontSize = 18.sp),
                )
            },
            text = { Text("$recipientLine This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        cancelling = true
                        vm.cancel(api, capsuleId)
                        cancelling = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Earth, contentColor = Parchment),
                ) { Text("Cancel capsule") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep capsule", color = Forest) }
            },
        )
    }
}
