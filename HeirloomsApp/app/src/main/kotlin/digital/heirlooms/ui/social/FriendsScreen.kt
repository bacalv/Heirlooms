package digital.heirlooms.ui.social

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.app.BuildConfig
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.garden.LoadError
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.HeirloomsSerifItalic
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Web base URL derived from the flavor's API base URL (BuildConfig.BASE_URL_OVERRIDE).
 *
 * - Staging API:  https://test.api.heirlooms.digital  → https://test.heirlooms.digital
 * - Prod API:     "" (empty — no override)             → https://heirlooms.digital
 *
 * Mirrors the approach used in DevicesAccessScreen (BUG-008 fix).
 */
private val friendsWebBaseUrl: String
    get() = if (BuildConfig.BASE_URL_OVERRIDE.isNotEmpty())
        BuildConfig.BASE_URL_OVERRIDE
            .replace("test.api.", "test.")
            .replace("api.", "")
            .trimEnd('/')
    else "https://heirlooms.digital"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    vm: FriendsViewModel = viewModel(),
) {
    val api = LocalHeirloomsApi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var friends by remember { mutableStateOf<List<HeirloomsApi.Friend>?>(null) }
    var error by remember { mutableStateOf(false) }

    val inviteState by vm.inviteState.collectAsState()

    LaunchedEffect(Unit) {
        try {
            friends = api.getFriends()
        } catch (_: Exception) {
            error = true
        }
    }

    // When the invite becomes Ready, immediately open the OS share sheet so the user can
    // send the link via Messages, WhatsApp, email, etc.  The confirmation dialog shown
    // inside the screen (expiry label + "Share" button) handles the case where the user
    // wants to review the link before sharing.
    val readyState = inviteState as? InviteState.Ready
    if (readyState != null) {
        AlertDialog(
            onDismissRequest = { vm.resetInvite() },
            title = { Text("Invite a friend", color = Forest) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Share the link below. The recipient will be connected to you " +
                            "as a friend when they register or sign in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Forest,
                    )
                    if (readyState.expiryLabel.isNotEmpty()) {
                        Text(
                            readyState.expiryLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, readyState.inviteUrl)
                            putExtra(Intent.EXTRA_SUBJECT, "Join me on Heirlooms")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite link"))
                        vm.resetInvite()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.resetInvite() }) {
                    Text("Cancel", color = Forest)
                }
            },
            containerColor = Parchment,
        )
    }

    // Error dialog for invite generation failures.
    val errorState = inviteState as? InviteState.Error
    if (errorState != null) {
        AlertDialog(
            onDismissRequest = { vm.resetInvite() },
            title = { Text("Couldn't generate invite", color = Forest) },
            text = { Text(errorState.message, color = Forest) },
            confirmButton = {
                TextButton(onClick = { vm.resetInvite() }) { Text("OK", color = Forest) }
            },
            containerColor = Parchment,
        )
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

        // ── "Invite a friend" button ──────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isLoading = inviteState is InviteState.Loading
            Button(
                onClick = {
                    scope.launch { vm.generateInvite(api, friendsWebBaseUrl) }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Parchment),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Parchment,
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (isLoading) "Generating…" else "Invite a friend")
            }
            Text(
                "Invite expires after 48 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        HorizontalDivider(color = Forest.copy(alpha = 0.08f))

        // ── Friends list ──────────────────────────────────────────────────────
        when {
            error -> LoadError(onRetry = {
                error = false
                friends = null
                scope.launch {
                    try {
                        friends = api.getFriends()
                    } catch (_: Exception) {
                        error = true
                    }
                }
            })
            friends == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
            friends!!.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No friends yet. Tap \"Invite a friend\" to get started.",
                    style = HeirloomsSerifItalic,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
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
