@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import digital.heirlooms.app.BuildConfig
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun SettingsScreen(onApiKeyReset: () -> Unit) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Parchment)
                .padding(innerPadding)
                .padding(top = 8.dp),
        ) {
            SettingsRow(
                label = "API key",
                value = "Reset",
                onClick = { showResetConfirm = true },
                showArrow = true,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)

            SettingsRow(
                label = "App version",
                value = "v${BuildConfig.VERSION_NAME}",
                onClick = null,
                showArrow = false,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Parchment,
            title = { Text("Reset API key?", style = HeirloomsSerifItalic.copy()) },
            text = { Text("You'll need to enter your API key again to continue.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = { showResetConfirm = false; onApiKeyReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = Earth, contentColor = Parchment),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel", color = Forest) }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    showArrow: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Forest, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        if (showArrow) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
        }
    }
}
