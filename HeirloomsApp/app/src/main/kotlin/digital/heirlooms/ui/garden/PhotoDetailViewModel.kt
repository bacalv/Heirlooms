package digital.heirlooms.ui.garden

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.CapsuleRef
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PhotoDetailState {
    object Loading : PhotoDetailState()
    data class Ready(val upload: Upload, val capsuleRefs: List<CapsuleRef>) : PhotoDetailState()
    data class Error(val message: String) : PhotoDetailState()
}

class PhotoDetailViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    private val _state = MutableStateFlow<PhotoDetailState>(PhotoDetailState.Loading)
    val state: StateFlow<PhotoDetailState> = _state

    // Persist tag-edit state across rotation.
    var tagInput: String
        get() = savedState["tag_input"] ?: ""
        set(v) { savedState["tag_input"] = v }

    private var viewTracked = false

    fun load(api: HeirloomsApi, uploadId: String) {
        viewModelScope.launch {
            _state.value = PhotoDetailState.Loading
            try {
                val upload = api.getUpload(uploadId)
                val refs = api.getCapsulesForUpload(uploadId)
                _state.value = PhotoDetailState.Ready(upload, refs)
            } catch (e: Exception) {
                _state.value = PhotoDetailState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun trackView(api: HeirloomsApi, uploadId: String) {
        if (viewTracked) return
        viewTracked = true
        viewModelScope.launch { api.trackView(uploadId) }
    }

    fun updateTags(api: HeirloomsApi, uploadId: String, newTags: List<String>) {
        viewModelScope.launch {
            try {
                val updated = api.updateTags(uploadId, newTags)
                val current = _state.value as? PhotoDetailState.Ready ?: return@launch
                _state.value = current.copy(upload = updated)
            } catch (_: Exception) {}
        }
    }

    fun rotate(api: HeirloomsApi, uploadId: String) {
        val current = _state.value as? PhotoDetailState.Ready ?: return
        val newRotation = (current.upload.rotation + 90) % 360
        _state.value = current.copy(upload = current.upload.copy(rotation = newRotation))
        viewModelScope.launch {
            try {
                val updated = api.rotateUpload(uploadId, newRotation)
                val fresh = _state.value as? PhotoDetailState.Ready ?: return@launch
                _state.value = fresh.copy(upload = updated)
            } catch (_: Exception) {
                // Roll back optimistic update on failure.
                val fresh = _state.value as? PhotoDetailState.Ready ?: return@launch
                _state.value = fresh.copy(upload = current.upload)
            }
        }
    }

    fun reload(api: HeirloomsApi, uploadId: String) {
        viewModelScope.launch {
            try {
                val upload = api.getUpload(uploadId)
                val refs = api.getCapsulesForUpload(uploadId)
                _state.value = PhotoDetailState.Ready(upload, refs)
            } catch (_: Exception) {}
        }
    }
}
