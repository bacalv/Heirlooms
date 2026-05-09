@file:OptIn(ExperimentalMaterial3Api::class)

package digital.heirlooms.ui.garden

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlantPreviewOverlay(
    uri: Uri,
    mimeType: String,
    isFile: Boolean,
    onPlant: () -> Unit,
    onRetake: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Parchment),
    ) {
        TopAppBar(
            title = { Text("Preview") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                mimeType.startsWith("image/") -> AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                mimeType.startsWith("video/") -> LocalVideoPlayer(
                    uri = uri,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> FilePreview(uri = uri)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onRetake != null) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest),
                ) {
                    Text("Retake")
                }
            }
            Button(
                onClick = onPlant,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
            ) {
                Text("Plant")
            }
        }
    }
}

@Composable
private fun LocalVideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().also {
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).also { pv -> pv.player = player } },
        modifier = modifier,
    )
}

@Composable
private fun FilePreview(uri: Uri) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf("") }
    LaunchedEffect(uri) {
        displayName = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: Exception) { null }
                ?: uri.lastPathSegment
                ?: "File"
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = Forest,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(displayName, color = Forest)
    }
}
