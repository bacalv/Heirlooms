package digital.heirlooms.ui.share

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

sealed class ReceiveState {
    data class Idle(
        val photos: List<Uri>,
        val tagsInProgress: List<String> = emptyList(),
        val recentTags: List<String> = emptyList(),
    ) : ReceiveState()
    data class Uploading(val sessionTag: String) : ReceiveState()
}

class ShareViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    // Session tag groups all work requests from one share action. Survives process death
    // so the progress screen can re-observe the same WorkManager jobs on recreation.
    var sessionTag: String?
        get() = savedState["session_tag"]
        set(v) { savedState["session_tag"] = v }

    var pendingTags: List<String>
        get() = savedState.get<Array<String>>("pending_tags")?.toList() ?: emptyList()
        set(v) { savedState["pending_tags"] = v.toTypedArray() }
}
