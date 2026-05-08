package digital.heirlooms.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun UploadProgressScreen(
    sessionTag: String,  // kept for future use; filtering is now global across all uploads
    fromShare: Boolean,
    onGoToGarden: () -> Unit,
    onContinueInBackground: () -> Unit,
    vm: UploadProgressViewModel = viewModel(),
) {
    val session by vm.allUploadsFlow().collectAsState(initial = SessionUploadState(emptyList()))

    Column(
        Modifier
            .fillMaxSize()
            .background(Parchment)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        if (session.allDone || session.files.isEmpty()) {
            // Auto-prune terminal records when we reach the done state so they don't
            // accumulate and reappear in the next session's progress list.
            LaunchedEffect(Unit) { vm.pruneFinished() }
            DoneState(
                fromShare = fromShare,
                onGoToGarden = onGoToGarden,
                onGoBack = onContinueInBackground,
            )
        } else {
            InProgressState(
                session = session,
                onCancel = { vm.cancel(it) },
                onPruneFinished = { vm.pruneFinished() },
                onContinueInBackground = onContinueInBackground,
            )
        }
    }
}

@Composable
private fun InProgressState(
    session: SessionUploadState,
    onCancel: (java.util.UUID) -> Unit,
    onPruneFinished: () -> Unit,
    onContinueInBackground: () -> Unit,
) {
    Text(
        "Your upload is in progress",
        style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "${session.activeCount} uploading · ${session.overallPercent}% overall",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )
    Spacer(Modifier.height(12.dp))

    // Overall progress bar
    LinearProgressIndicator(
        progress = { session.overallPercent / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = Forest,
        trackColor = Forest15,
    )

    Spacer(Modifier.height(20.dp))

    val hasFinished = session.files.any { it.isDone }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(color = Forest15, modifier = Modifier.weight(1f))
        if (hasFinished) {
            TextButton(
                onClick = onPruneFinished,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Clear finished", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    LazyColumn(modifier = Modifier.fillMaxHeight(0.55f)) {
        items(session.files, key = { it.workerId }) { file ->
            FileRow(file = file, onCancel = { onCancel(file.workerId) })
            HorizontalDivider(color = Forest15)
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = onContinueInBackground,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, Forest),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text("Continue in background", color = Forest)
    }
}

@Composable
private fun FileRow(file: FileUploadState, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = Forest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            when (file.state) {
                WorkInfo.State.ENQUEUED -> Text("Queued", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                WorkInfo.State.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { file.percent / 100f },
                        modifier = Modifier.fillMaxWidth(0.85f),
                        color = Forest,
                        trackColor = Forest15,
                    )
                    Text("${file.percent}%", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                WorkInfo.State.SUCCEEDED -> Text("Planted ✓", style = MaterialTheme.typography.bodySmall, color = Forest)
                WorkInfo.State.FAILED -> Text("Couldn't upload", style = MaterialTheme.typography.bodySmall, color = Earth)
                WorkInfo.State.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                else -> {}
            }
        }

        if (file.isActive) {
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = TextMuted)
            }
        }
    }
}

@Composable
private fun DoneState(
    fromShare: Boolean,
    onGoToGarden: () -> Unit,
    onGoBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                "No uploads in progress.",
                style = HeirloomsSerifItalic.copy(fontSize = 22.sp, color = Forest),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onGoToGarden,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text("Go to Garden", style = HeirloomsSerifItalic.copy(fontSize = 16.sp))
            }
            if (fromShare) {
                OutlinedButton(
                    onClick = onGoBack,
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Forest15),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("← Back", color = TextMuted)
                }
            }
        }
    }
}
