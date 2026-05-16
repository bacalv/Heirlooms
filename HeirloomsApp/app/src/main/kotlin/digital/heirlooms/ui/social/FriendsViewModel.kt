package digital.heirlooms.ui.social

import androidx.lifecycle.ViewModel
import digital.heirlooms.api.HeirloomsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * State for the invite-a-friend flow triggered from the Friends screen.
 *
 * - [Idle]      — no invite in flight
 * - [Loading]   — POST /api/auth/invites in progress
 * - [Ready]     — invite token available; URL and expiry are shown before sharing
 * - [Error]     — the API call failed; [message] is shown to the user
 */
sealed interface InviteState {
    data object Idle : InviteState
    data object Loading : InviteState
    data class Ready(val inviteUrl: String, val expiryLabel: String) : InviteState
    data class Error(val message: String) : InviteState
}

class FriendsViewModel : ViewModel() {

    private val _inviteState = MutableStateFlow<InviteState>(InviteState.Idle)
    val inviteState: StateFlow<InviteState> = _inviteState

    /** Call from a coroutine scope (e.g. rememberCoroutineScope in the composable). */
    suspend fun generateInvite(api: HeirloomsApi, webBaseUrl: String) {
        _inviteState.value = InviteState.Loading
        try {
            val resp = api.createFriendInvite()
            val url = "$webBaseUrl/invite?token=${resp.token}"
            val expiry = formatExpiry(resp.expiresAt)
            _inviteState.value = InviteState.Ready(inviteUrl = url, expiryLabel = expiry)
        } catch (e: Exception) {
            _inviteState.value = InviteState.Error(e.message ?: "Could not generate invite.")
        }
    }

    fun resetInvite() {
        _inviteState.value = InviteState.Idle
    }
}

/**
 * Formats an ISO-8601 expiry timestamp into a human-readable label, e.g. "Expires 18 May, 14:30".
 * Returns an empty string if the timestamp cannot be parsed.
 */
internal fun formatExpiry(iso: String): String = try {
    val instant = java.time.Instant.parse(iso)
    val dt = instant.atZone(java.time.ZoneId.systemDefault())
    "Expires ${java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm").format(dt)}"
} catch (_: Exception) {
    ""
}
