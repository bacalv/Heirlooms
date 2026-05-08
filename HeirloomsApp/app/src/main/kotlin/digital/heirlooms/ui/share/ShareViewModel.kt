package digital.heirlooms.ui.share

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

sealed class ReceiveState {
    data class Idle(
        val photos: List<Uri>,
        val tagsInProgress: List<String> = emptyList(),
        val currentTagInput: String = "",
        val recentTags: List<String> = emptyList(),
    ) : ReceiveState()
    object Uploading : ReceiveState()
    data class Arriving(val photoCount: Int) : ReceiveState()
    data class Arrived(val photoCount: Int) : ReceiveState()
    object FailedAnimating : ReceiveState()
    object Failed : ReceiveState()
}

class ShareViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    // Worker request ID survives process death so we can re-observe on restore.
    var pendingWorkerId: String?
        get() = savedState["pending_worker_id"]
        set(v) { savedState["pending_worker_id"] = v }

    // Photo count survives process death so we can show the right arrival message.
    var uploadPhotoCount: Int
        get() = savedState["upload_photo_count"] ?: 0
        set(v) { savedState["upload_photo_count"] = v }

    // Pending tags survive process death — the user set them before upload started.
    var pendingTags: List<String>
        get() = savedState.get<Array<String>>("pending_tags")?.toList() ?: emptyList()
        set(v) { savedState["pending_tags"] = v.toTypedArray() }
}
