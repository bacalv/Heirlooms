@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package digital.heirlooms.ui.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.CapsuleRef
import digital.heirlooms.api.CapsuleSummary
import digital.heirlooms.api.Upload
import digital.heirlooms.ui.common.HeirloomsImage
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.common.formatInstantDate
import digital.heirlooms.ui.common.formatOffsetDate
import digital.heirlooms.ui.common.daysUntilDeletion
import digital.heirlooms.ui.share.isValidTag
import digital.heirlooms.ui.theme.Bloom
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun PhotoDetailScreen(
    uploadId: String,
    onBack: () -> Unit,
    onCapsuleTap: (String) -> Unit,
    onStartCapsuleWithPhoto: (String) -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var upload by remember { mutableStateOf<Upload?>(null) }
    var capsuleRefs by remember { mutableStateOf<List<CapsuleRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tagInput by remember { mutableStateOf("") }
    var savingTags by remember { mutableStateOf(false) }
    var showAddToCapsule by remember { mutableStateOf(false) }
    var showCompostConfirm by remember { mutableStateOf(false) }
    var composting by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            error = null
            try {
                upload = api.getUpload(uploadId)
                capsuleRefs = api.getCapsulesForUpload(uploadId)
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(uploadId) { reload() }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
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
            error != null -> DidntTake(onRetry = { loading = true; reload() })
            else -> {
                val u = upload ?: return@Scaffold
                val isComposted = u.compostedAt != null

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Photo
                    val colorFilter = if (isComposted) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.6f) })
                    } else null

                    HeirloomsImage(
                        url = api.fileUrl(uploadId),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isComposted) 0.85f else 1f),
                        contentScale = ContentScale.FillWidth,
                        colorFilter = colorFilter,
                    )

                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        if (!isComposted) {
                            // Tags
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                u.tags.forEach { tag ->
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        label = { Text(tag, style = MaterialTheme.typography.bodySmall) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remove tag",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable {
                                                        scope.launch {
                                                            savingTags = true
                                                            try {
                                                                upload = api.updateTags(uploadId, u.tags - tag)
                                                            } catch (_: Exception) {}
                                                            savingTags = false
                                                        }
                                                    },
                                            )
                                        },
                                    )
                                }
                                // + add tag inline
                                BasicTextField(
                                    value = tagInput,
                                    onValueChange = { tagInput = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Forest),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (tagInput.isNotBlank() && isValidTag(tagInput.trim())) {
                                            scope.launch {
                                                val newTag = tagInput.trim()
                                                tagInput = ""
                                                try {
                                                    upload = api.updateTags(uploadId, u.tags + newTag)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }),
                                    decorationBox = { inner ->
                                        Box(
                                            Modifier
                                                .background(Forest08, RoundedCornerShape(50))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            if (tagInput.isEmpty()) {
                                                Text("+ tag", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                                            }
                                            inner()
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Upload date
                        Text(
                            "Uploaded ${formatInstantDate(u.uploadedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )

                        if (isComposted) {
                            Spacer(Modifier.height(12.dp))
                            val days = daysUntilDeletion(u.compostedAt!!)
                            Text(
                                "Composted on ${formatInstantDate(u.compostedAt)}. Will be permanently deleted in $days days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }

                        if (!isComposted && capsuleRefs.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Forest15)
                            Spacer(Modifier.height(16.dp))
                            val activeCapsules = capsuleRefs.filter { it.state == "open" || it.state == "sealed" }
                            if (activeCapsules.isNotEmpty()) {
                                Text(
                                    "In capsules: " + activeCapsules.joinToString(", ") { c ->
                                        c.recipients.firstOrNull() ?: "Capsule"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Forest,
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            OutlinedButton(
                                onClick = { showAddToCapsule = true },
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Forest),
                            ) {
                                Text("Add this to a capsule", color = Forest)
                            }
                        } else if (!isComposted) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Forest15)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { showAddToCapsule = true },
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Forest),
                            ) {
                                Text("Add this to a capsule", color = Forest)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Forest15)
                        Spacer(Modifier.height(16.dp))

                        if (isComposted) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        restoring = true
                                        try {
                                            upload = api.restoreUpload(uploadId)
                                        } catch (_: Exception) {}
                                        restoring = false
                                    }
                                },
                                enabled = !restoring,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                                shape = RoundedCornerShape(22.dp),
                            ) {
                                Text(if (restoring) "Restoring…" else "Restore")
                            }
                        } else {
                            val hasTagsOrCapsules = u.tags.isNotEmpty() || capsuleRefs.any { it.state == "open" || it.state == "sealed" }
                            OutlinedButton(
                                onClick = { showCompostConfirm = true },
                                enabled = !hasTagsOrCapsules,
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (hasTagsOrCapsules) Forest25 else Earth),
                            ) {
                                Text("Compost", color = if (hasTagsOrCapsules) TextMuted else Earth)
                            }
                            if (hasTagsOrCapsules) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Compost requires no tags and no active capsule memberships.",
                                    style = HeirloomsSerifItalic.copy(fontSize = 12.sp, color = TextMuted),
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showCompostConfirm) {
        AlertDialog(
            onDismissRequest = { showCompostConfirm = false },
            title = { Text("Compost this photo?", style = HeirloomsSerifItalic.copy(fontSize = 18.sp)) },
            text = { Text("It will be removed from your garden. You can restore it within 90 days.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCompostConfirm = false
                        scope.launch {
                            composting = true
                            try {
                                upload = api.compostUpload(uploadId)
                                capsuleRefs = emptyList()
                            } catch (_: Exception) {}
                            composting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Earth, contentColor = Parchment),
                ) { Text("Compost") }
            },
            dismissButton = {
                TextButton(onClick = { showCompostConfirm = false }) { Text("Keep", color = Forest) }
            },
            containerColor = Parchment,
        )
    }

    if (showAddToCapsule) {
        AddToCapsuleDialog(
            uploadId = uploadId,
            onDismiss = { showAddToCapsule = false },
            onAdded = { showAddToCapsule = false; reload() },
            onStartCapsule = { onStartCapsuleWithPhoto(uploadId) },
        )
    }
}

@Composable
private fun AddToCapsuleDialog(
    uploadId: String,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    onStartCapsule: () -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val scope = rememberCoroutineScope()

    var capsules by remember { mutableStateOf<List<CapsuleSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            capsules = api.listCapsules("open")
        } catch (_: Exception) {}
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Parchment,
        title = { Text("Add this to a capsule", style = MaterialTheme.typography.titleMedium) },
        text = {
            if (loading) {
                CircularProgressIndicator(color = Forest)
            } else if (capsules.isEmpty()) {
                Column {
                    Text(
                        "No open capsules to add this to.",
                        style = HeirloomsSerifItalic.copy(fontSize = 16.sp, color = Forest),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onDismiss(); onStartCapsule() },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text("Start a capsule with this", style = HeirloomsSerifItalic.copy(fontSize = 14.sp))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capsules.forEach { c ->
                        val isSelected = selectedId == c.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Forest08 else Parchment, RoundedCornerShape(8.dp))
                                .clickable { selectedId = c.id }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(c.recipients.joinToString(", "), style = HeirloomsSerifItalic.copy(color = Forest))
                                formatOffsetDate(c.unlockAt).let {
                                    Text("Opens $it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (capsules.isNotEmpty()) {
                Button(
                    onClick = {
                        val id = selectedId ?: return@Button
                        val capsule = capsules.first { it.id == id }
                        scope.launch {
                            saving = true
                            try {
                                val current = api.getCapsule(id)
                                val newIds = (current.uploads.map { it.id } + uploadId).distinct()
                                api.patchCapsuleUploads(id, newIds)
                                onAdded()
                            } catch (_: Exception) {}
                            saving = false
                        }
                    },
                    enabled = selectedId != null && !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) { Text(if (saving) "Adding…" else "Add") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}
