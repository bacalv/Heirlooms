package digital.heirlooms.ui.garden

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment

enum class PlantType { Photo, Video }

sealed class PlantState {
    object Idle : PlantState()
    data class Preview(val uri: Uri, val mimeType: String, val isFile: Boolean = false) : PlantState()
    object Queuing : PlantState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantSheet(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onFile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Parchment,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            PlantOption(
                icon = { Icon(Icons.Filled.PhotoCamera, null, tint = Forest, modifier = Modifier.size(24.dp)) },
                label = "Photo",
                onClick = { onDismiss(); onPhoto() },
            )
            PlantOption(
                icon = { Icon(Icons.Filled.Videocam, null, tint = Forest, modifier = Modifier.size(24.dp)) },
                label = "Video",
                onClick = { onDismiss(); onVideo() },
            )
            PlantOption(
                icon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Forest, modifier = Modifier.size(24.dp)) },
                label = "File",
                onClick = { onDismiss(); onFile() },
            )
        }
    }
}

@Composable
private fun PlantOption(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, color = Forest)
    }
}
