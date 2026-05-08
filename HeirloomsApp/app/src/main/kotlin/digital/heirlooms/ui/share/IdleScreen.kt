@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package digital.heirlooms.ui.share

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import digital.heirlooms.app.R
import digital.heirlooms.ui.common.LocalImageLoader
import digital.heirlooms.ui.common.TagInputField
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun IdleScreen(
    state: ReceiveState.Idle,
    onTagsChange: (List<String>) -> Unit,
    onPlant: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Heirlooms",
            style = HeirloomsSerifItalic.copy(fontSize = 18.sp, color = Forest),
        )

        if (state.photos.size <= 6) {
            PhotoGrid(state.photos, modifier = Modifier.weight(1f, fill = false))
        } else {
            PhotoStrip(state.photos)
        }

        TagInputField(
            tags = state.tagsInProgress,
            onTagsChange = onTagsChange,
            availableTags = emptyList(),
            recentTags = state.recentTags.take(5),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        PlantButton(enabled = state.photos.isNotEmpty(), onClick = onPlant)
        CancelButton(onClick = onCancel)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PhotoGrid(photos: List<Uri>, modifier: Modifier = Modifier) {
    val columns = when (photos.size) {
        1 -> 1
        in 2..4 -> 2
        else -> 3
    }
    val imageLoader = LocalImageLoader.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(photos) { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, Forest15, RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<Uri>) {
    val imageLoader = LocalImageLoader.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${photos.size} photos",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(photos) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, Forest15, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun PlantButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Forest,
            contentColor = Parchment,
            disabledContainerColor = Forest25,
            disabledContentColor = TextMuted,
        ),
    ) {
        Text(
            text = stringResource(R.string.share_save_button),
            style = HeirloomsSerifItalic.copy(fontSize = 16.sp),
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.share_cancel_button),
            style = HeirloomsSerifItalic.copy(fontSize = 14.sp, color = TextMuted),
        )
    }
}
