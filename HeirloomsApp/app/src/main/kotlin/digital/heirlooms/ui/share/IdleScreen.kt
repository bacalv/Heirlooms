package digital.heirlooms.ui.share

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import digital.heirlooms.app.R
import digital.heirlooms.app.ReceiveState
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.Forest25
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@Composable
fun IdleScreen(
    state: ReceiveState.Idle,
    onTagInputChanged: (String) -> Unit,
    onTagCommit: (String) -> Unit,
    onTagRemoved: (String) -> Unit,
    onRecentTagTapped: (String) -> Unit,
    onPlant: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plantEnabled = state.photos.isNotEmpty() &&
        (state.currentTagInput.isEmpty() || isValidTag(state.currentTagInput))

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

        TagInputRow(
            tagsInProgress = state.tagsInProgress,
            currentInput = state.currentTagInput,
            onInputChanged = onTagInputChanged,
            onCommit = onTagCommit,
            onTagRemoved = onTagRemoved,
        )
        if (state.recentTags.isNotEmpty()) {
            RecentTagChips(state.recentTags, onTap = onRecentTagTapped)
        }

        Spacer(Modifier.weight(1f))

        PlantButton(enabled = plantEnabled, onClick = onPlant)
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
private fun TagInputRow(
    tagsInProgress: List<String>,
    currentInput: String,
    onInputChanged: (String) -> Unit,
    onCommit: (String) -> Unit,
    onTagRemoved: (String) -> Unit,
) {
    val inputIsInvalid = currentInput.isNotEmpty() && !isValidTag(currentInput)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tagsInProgress.forEach { tag ->
                CommittedTagChip(tag, onRemove = { onTagRemoved(tag) })
            }
            BasicTextField(
                value = currentInput,
                onValueChange = onInputChanged,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (currentInput.isNotEmpty() && isValidTag(currentInput)) {
                            onCommit(currentInput)
                        }
                    },
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = if (inputIsInvalid) Earth else Forest,
                ),
                modifier = Modifier.widthIn(min = 80.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .drawBehind {
                                drawLine(
                                    color = if (inputIsInvalid) Earth else Forest25,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = if (inputIsInvalid) 1.5.dp.toPx() else 0.5.dp.toPx(),
                                )
                            },
                    ) {
                        if (currentInput.isEmpty() && tagsInProgress.isEmpty()) {
                            Text(
                                text = stringResource(R.string.share_tag_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (inputIsInvalid) {
            Text(
                text = stringResource(R.string.share_tag_invalid),
                style = HeirloomsSerifItalic.copy(fontSize = 12.sp, color = Earth),
            )
        }
    }
}

@Composable
private fun RecentTagChips(recent: List<String>, onTap: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "recent",
            style = HeirloomsSerifItalic.copy(fontSize = 11.sp, color = TextMuted),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            recent.forEach { tag ->
                RecentChip(tag, onClick = { onTap(tag) })
            }
        }
    }
}

@Composable
private fun CommittedTagChip(tag: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Forest08, RoundedCornerShape(50))
            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(tag, style = MaterialTheme.typography.bodySmall.copy(color = Forest))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", style = MaterialTheme.typography.bodySmall.copy(color = Forest))
        }
    }
}

@Composable
private fun RecentChip(tag: String, onClick: () -> Unit) {
    Text(
        text = tag,
        style = MaterialTheme.typography.bodySmall.copy(color = Forest),
        modifier = Modifier
            .background(Forest08, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
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
