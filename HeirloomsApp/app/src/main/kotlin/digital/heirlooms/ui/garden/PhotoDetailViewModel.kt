package digital.heirlooms.ui.garden

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.CapsuleRef
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class PhotoDetailState {
    object Loading : PhotoDetailState()
    data class Ready(val upload: Upload, val capsuleRefs: List<CapsuleRef>) : PhotoDetailState()
    data class Error(val message: String) : PhotoDetailState()
}

class PhotoDetailViewModel(
    @Suppress("UNUSED_PARAMETER") savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<PhotoDetailState>(PhotoDetailState.Loading)
    val state: StateFlow<PhotoDetailState> = _state

    // All tags in the user's library — for TagInputField suggestions.
    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

    // Staged changes — null means not dirty (server value is authoritative).
    private val _stagedTags = MutableStateFlow<List<String>?>(null)
    val stagedTags: StateFlow<List<String>?> = _stagedTags.asStateFlow()

    private val _stagedRotation = MutableStateFlow<Int?>(null)

    val isDirty: StateFlow<Boolean> = combine(_stagedTags, _stagedRotation) { t, r ->
        t != null || r != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var viewTracked = false

    fun load(api: HeirloomsApi, uploadId: String) {
        viewModelScope.launch {
            _state.value = PhotoDetailState.Loading
            _stagedTags.value = null
            _stagedRotation.value = null
            try {
                val upload = api.getUpload(uploadId)
                val refs = api.getCapsulesForUpload(uploadId)
                _state.value = PhotoDetailState.Ready(upload, refs)
            } catch (e: Exception) {
                _state.value = PhotoDetailState.Error(e.message ?: "Couldn't load")
            }
        }
        viewModelScope.launch {
            try { _availableTags.value = api.listTags() } catch (_: Exception) {}
        }
    }

    fun trackView(api: HeirloomsApi, uploadId: String) {
        if (viewTracked) return
        viewTracked = true
        viewModelScope.launch { api.trackView(uploadId) }
    }

    fun stageTags(tags: List<String>) {
        _stagedTags.value = tags
    }

    fun stageRotate() {
        val current = _state.value as? PhotoDetailState.Ready ?: return
        val currentRotation = _stagedRotation.value ?: current.upload.rotation
        _stagedRotation.value = (currentRotation + 90) % 360
    }

    // Effective values to display — staged if present, server value otherwise.
    fun effectiveTags(): List<String> {
        val ready = _state.value as? PhotoDetailState.Ready ?: return emptyList()
        return _stagedTags.value ?: ready.upload.tags
    }

    fun effectiveRotation(): Int {
        val ready = _state.value as? PhotoDetailState.Ready ?: return 0
        return _stagedRotation.value ?: ready.upload.rotation
    }

    suspend fun saveChanges(api: HeirloomsApi, uploadId: String) {
        val tagsToSave = _stagedTags.value
        val rotationToSave = _stagedRotation.value
        if (tagsToSave == null && rotationToSave == null) return
        try {
            if (tagsToSave != null) {
                val updated = api.updateTags(uploadId, tagsToSave)
                (_state.value as? PhotoDetailState.Ready)?.let {
                    _state.value = it.copy(upload = updated)
                }
                _stagedTags.value = null
            }
            if (rotationToSave != null) {
                val updated = api.rotateUpload(uploadId, rotationToSave)
                (_state.value as? PhotoDetailState.Ready)?.let {
                    _state.value = it.copy(upload = updated)
                }
                _stagedRotation.value = null
            }
        } catch (_: Exception) {}
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
