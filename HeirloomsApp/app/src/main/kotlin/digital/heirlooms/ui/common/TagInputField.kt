package digital.heirlooms.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.heirlooms.ui.share.isValidTag
import digital.heirlooms.ui.theme.Earth
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Forest08
import digital.heirlooms.ui.theme.Forest15
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInputField(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    availableTags: List<String>,
    recentTags: List<String>,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val isInvalid = input.isNotEmpty() && !isValidTag(input)

    val suggestions = remember(input, tags, availableTags, recentTags) {
        if (input.isEmpty()) {
            recentTags.filter { it !in tags }.take(5)
        } else {
            val pool = availableTags.ifEmpty { recentTags }
            pool.filter { it.contains(input, ignoreCase = true) && it !in tags }.take(8)
        }
    }

    fun addTag(tag: String) {
        if (tag !in tags) onTagsChange(tags + tag)
        input = ""
    }

    Column(modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEach { tag ->
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
                                .clickable { onTagsChange(tags - tag) },
                        )
                    },
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Forest),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty() && isValidTag(trimmed)) addTag(trimmed)
                }),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .background(Forest08, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (input.isEmpty()) {
                            Text("+ tag", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                        }
                        inner()
                    }
                },
            )
        }

        if (isInvalid) {
            Text(
                "Letters, numbers, spaces, hyphens, and underscores only.",
                style = HeirloomsSerifItalic.copy(fontSize = 11.sp, color = Earth),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (suggestions.isNotEmpty()) {
            if (input.isEmpty()) {
                Text(
                    "recent",
                    style = HeirloomsSerifItalic.copy(fontSize = 11.sp, color = TextMuted),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (input.isNotEmpty()) 4.dp else 0.dp)
                    .background(Forest08, RoundedCornerShape(8.dp)),
            ) {
                suggestions.forEachIndexed { index, tag ->
                    if (index > 0) HorizontalDivider(color = Forest15)
                    Text(
                        tag,
                        style = MaterialTheme.typography.bodySmall.copy(color = Forest),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addTag(tag) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
