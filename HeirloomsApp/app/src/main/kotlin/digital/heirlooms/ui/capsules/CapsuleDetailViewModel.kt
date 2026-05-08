package digital.heirlooms.ui.capsules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.CapsuleDetail
import digital.heirlooms.api.HeirloomsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CapsuleDetailState {
    object Loading : CapsuleDetailState()
    data class Ready(val capsule: CapsuleDetail) : CapsuleDetailState()
    data class Error(val message: String) : CapsuleDetailState()
}

class CapsuleDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<CapsuleDetailState>(CapsuleDetailState.Loading)
    val state: StateFlow<CapsuleDetailState> = _state

    fun load(api: HeirloomsApi, capsuleId: String) {
        viewModelScope.launch {
            _state.value = CapsuleDetailState.Loading
            try {
                _state.value = CapsuleDetailState.Ready(api.getCapsule(capsuleId))
            } catch (e: Exception) {
                _state.value = CapsuleDetailState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun reload(api: HeirloomsApi, capsuleId: String) {
        viewModelScope.launch {
            try {
                _state.value = CapsuleDetailState.Ready(api.getCapsule(capsuleId))
            } catch (_: Exception) {}
        }
    }

    fun seal(api: HeirloomsApi, capsuleId: String) {
        viewModelScope.launch {
            try {
                _state.value = CapsuleDetailState.Ready(api.sealCapsule(capsuleId))
            } catch (_: Exception) {}
        }
    }

    fun cancel(api: HeirloomsApi, capsuleId: String) {
        viewModelScope.launch {
            try {
                _state.value = CapsuleDetailState.Ready(api.cancelCapsule(capsuleId))
            } catch (_: Exception) {}
        }
    }

    fun patchUploads(api: HeirloomsApi, capsuleId: String, uploadIds: List<String>) {
        viewModelScope.launch {
            try {
                _state.value = CapsuleDetailState.Ready(api.patchCapsuleUploads(capsuleId, uploadIds))
            } catch (_: Exception) {}
        }
    }
}
