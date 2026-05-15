package digital.heirlooms.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.garden.LoadError
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(onBack: () -> Unit) {
    val api = LocalHeirloomsApi.current
    var friends by remember { mutableStateOf<List<HeirloomsApi.Friend>?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            friends = api.getFriends()
        } catch (_: Exception) {
            error = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Friends", style = MaterialTheme.typography.titleLarge.copy(color = Forest)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment),
        )

        when {
            error -> LoadError(onRetry = {
                error = false
                friends = null
            })
            friends == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
            friends!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No friends yet. Share an invite to get started.",
                    style = HeirloomsSerifItalic,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(friends!!, key = { it.userId }) { friend ->
                    FriendRow(friend)
                    HorizontalDivider(color = Forest.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: HeirloomsApi.Friend) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(friend.displayName, style = MaterialTheme.typography.bodyLarge.copy(color = Forest))
        Text("@${friend.username}", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
    }
}
