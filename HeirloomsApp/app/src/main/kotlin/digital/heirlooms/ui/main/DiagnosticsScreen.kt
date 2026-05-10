@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(apiKey: String, onBack: () -> Unit) {
    val events = DiagnosticsStore.events
    val api = remember(apiKey) { HeirloomsApi(apiKey = apiKey) }
    val deviceLabel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (events.isNotEmpty()) {
                        TextButton(onClick = { DiagnosticsStore.clear() }) {
                            Text("Clear", color = Earth)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { padding ->
        if (events.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No events this session.",
                    color = Forest.copy(alpha = 0.4f),
                    fontStyle = FontStyle.Italic,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(events, key = { it.id }) { event ->
                    DiagEventRow(
                        event = event,
                        timeLabel = timeFmt.format(Date(event.id)),
                        onReport = {
                            api.postDiagEvent(
                                deviceLabel = deviceLabel,
                                tag = event.tag,
                                message = event.message,
                                detail = event.detail,
                            )
                        },
                    )
                    HorizontalDivider(color = Forest.copy(alpha = 0.08f))
                }
            }
        }
    }
}

private enum class ReportState { Idle, Sending, Sent, Failed }

@Composable
private fun DiagEventRow(event: DiagEvent, timeLabel: String, onReport: suspend () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var reportState by remember { mutableStateOf(ReportState.Idle) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "[$timeLabel] ${event.tag}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Forest,
        )
        Text(
            text = event.message,
            fontSize = 12.sp,
            color = Earth,
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = event.detail,
                fontSize = 11.sp,
                color = Forest.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Forest.copy(alpha = 0.05f))
                    .padding(8.dp),
            )
            Spacer(Modifier.height(4.dp))
            when (reportState) {
                ReportState.Idle -> TextButton(
                    onClick = {
                        reportState = ReportState.Sending
                        scope.launch {
                            try {
                                onReport()
                                reportState = ReportState.Sent
                            } catch (_: Exception) {
                                reportState = ReportState.Failed
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Report to server", color = Forest, fontSize = 12.sp)
                }
                ReportState.Sending -> Text(
                    "Sending…",
                    fontSize = 12.sp,
                    color = Forest.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
                )
                ReportState.Sent -> Text(
                    "Sent ✓",
                    fontSize = 12.sp,
                    color = Forest,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
                )
                ReportState.Failed -> TextButton(
                    onClick = { reportState = ReportState.Idle },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Failed — tap to retry", color = Earth, fontSize = 12.sp)
                }
            }
        }
    }
}
