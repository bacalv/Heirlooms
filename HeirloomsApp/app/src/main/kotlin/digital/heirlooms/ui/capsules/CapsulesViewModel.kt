package digital.heirlooms.ui.capsules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.CapsuleSummary
import digital.heirlooms.api.HeirloomsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CapsulesState {
    object Loading : CapsulesState()
    data class Ready(val capsules: List<CapsuleSummary>) : CapsulesState()
    data class Error(val message: String) : CapsulesState()
}

class CapsulesViewModel : ViewModel() {

    private val _state = MutableStateFlow<CapsulesState>(CapsulesState.Loading)
    val state: StateFlow<CapsulesState> = _state

    fun load(api: HeirloomsApi, states: String = "open,sealed") {
        viewModelScope.launch {
            _state.value = CapsulesState.Loading
            try {
                _state.value = CapsulesState.Ready(api.listCapsules(states))
            } catch (e: Exception) {
                _state.value = CapsulesState.Error(e.message ?: "Couldn't load")
            }
        }
    }
}
