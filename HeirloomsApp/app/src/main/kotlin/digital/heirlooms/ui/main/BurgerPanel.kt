@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.app.BuildConfig
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun BurgerPanel(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSettingsTap: () -> Unit,
    onCompostHeapTap: () -> Unit,
    uploadsInProgress: Boolean = false,
    onUploadsTap: () -> Unit = {},
    onDiagnosticsTap: () -> Unit = {},
    onDevicesAccessTap: () -> Unit = {},
    onFriendsTap: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Parchment,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            BurgerRow(label = "Settings", onClick = { onDismiss(); onSettingsTap() })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)
            BurgerRow(label = "Friends", onClick = { onDismiss(); onFriendsTap() })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)
            BurgerRow(label = "Devices & Access", onClick = { onDismiss(); onDevicesAccessTap() })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)
            BurgerRow(label = "Compost heap", onClick = { onDismiss(); onCompostHeapTap() })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)
            if (uploadsInProgress) {
                BurgerRow(label = "Uploads in progress", onClick = { onDismiss(); onUploadsTap() })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)
            }
            BurgerRow(label = "Diagnostics", onClick = { onDismiss(); onDiagnosticsTap() })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Forest15)

            Spacer(Modifier.weight(1f, fill = false))

            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = HeirloomsSerifItalic.copy(fontSize = 12.sp, color = TextMuted),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BurgerRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Parchment)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Forest, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}
