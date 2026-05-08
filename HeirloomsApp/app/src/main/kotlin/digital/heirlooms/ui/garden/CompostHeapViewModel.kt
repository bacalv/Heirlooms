package digital.heirlooms.ui.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CompostState {
    object Loading : CompostState()
    data class Ready(val uploads: List<Upload>) : CompostState()
    data class Error(val message: String) : CompostState()
}

class CompostHeapViewModel : ViewModel() {

    private val _state = MutableStateFlow<CompostState>(CompostState.Loading)
    val state: StateFlow<CompostState> = _state

    fun load(api: HeirloomsApi) {
        viewModelScope.launch {
            _state.value = CompostState.Loading
            try {
                _state.value = CompostState.Ready(api.listCompostedUploads())
            } catch (e: Exception) {
                _state.value = CompostState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun restore(api: HeirloomsApi, uploadId: String) {
        viewModelScope.launch {
            try {
                api.restoreUpload(uploadId)
                val current = _state.value as? CompostState.Ready ?: return@launch
                _state.value = current.copy(uploads = current.uploads.filter { it.id != uploadId })
            } catch (_: Exception) {}
        }
    }
}
