package digital.heirlooms.ui.social

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import digital.heirlooms.ui.common.LocalHeirloomsApi
import digital.heirlooms.ui.theme.Forest
import digital.heirlooms.ui.theme.Parchment
import digital.heirlooms.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    upload: Upload,
    onDismiss: () -> Unit,
) {
    val api = LocalHeirloomsApi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var friends by remember { mutableStateOf<List<HeirloomsApi.Friend>?>(null) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { friends = api.getFriends() } catch (_: Exception) { friends = emptyList() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Parchment,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Share with", style = MaterialTheme.typography.titleMedium, color = Forest)
            Spacer(Modifier.height(12.dp))

            when {
                sharing -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = Forest)
                }
                friends == null -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = Forest)
                }
                friends!!.isEmpty() -> Text(
                    "No friends yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> LazyColumn {
                    items(friends!!, key = { it.userId }) { friend ->
                        FriendPickerRow(friend) {
                            sharing = true
                            scope.launch {
                                val result = shareWithFriend(api, upload, friend)
                                withContext(Dispatchers.Main) {
                                    sharing = false
                                    if (result) {
                                        Toast.makeText(context, "Shared with ${friend.displayName}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Couldn't share. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendPickerRow(friend: HeirloomsApi.Friend, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(friend.displayName, style = MaterialTheme.typography.bodyLarge, color = Forest)
            Text("@${friend.username}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        TextButton(onClick = onClick) {
            Text("Share", color = Forest)
        }
    }
}

private suspend fun shareWithFriend(
    api: HeirloomsApi,
    upload: Upload,
    friend: HeirloomsApi.Friend,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val wrappedDek = upload.wrappedDek ?: return@withContext false
        val masterKey = VaultSession.masterKey
        val dek = VaultCrypto.unwrapDekWithMasterKey(wrappedDek, masterKey)

        val friendPubkey = api.getFriendSharingPubkey(friend.userId) ?: return@withContext false

        val reWrappedDek = VaultCrypto.wrapMasterKeyForRecipient(dek, friendPubkey)
        val reWrappedDekB64 = Base64.encodeToString(reWrappedDek, Base64.NO_WRAP)

        val reWrappedThumbDekB64 = upload.wrappedThumbnailDek?.let { wrappedThumb ->
            val thumbDek = VaultCrypto.unwrapDekWithMasterKey(wrappedThumb, masterKey)
            val reWrapped = VaultCrypto.wrapMasterKeyForRecipient(thumbDek, friendPubkey)
            Base64.encodeToString(reWrapped, Base64.NO_WRAP)
        }

        api.shareUpload(
            uploadId = upload.id,
            toUserId = friend.userId,
            wrappedDekB64 = reWrappedDekB64,
            wrappedThumbnailDekB64 = reWrappedThumbDekB64,
        )
        true
    } catch (_: Exception) {
        false
    }
}
