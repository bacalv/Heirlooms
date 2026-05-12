package digital.heirlooms.ui.flows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StagingState(
    val pending: List<Upload> = emptyList(),
    val rejected: List<Upload> = emptyList(),
    val loading: Boolean = true,
)

class StagingViewModel : ViewModel() {

    private val _state = MutableStateFlow(StagingState())
    val state: StateFlow<StagingState> = _state

    fun load(api: HeirloomsApi, flowId: String, plotId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val pending = api.getFlowStaging(flowId).map { it.upload }
                val rejected = api.getRejectedItems(plotId).map { it.upload }
                _state.value = StagingState(pending = pending, rejected = rejected, loading = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun approve(api: HeirloomsApi, flowId: String, plotId: String, uploadId: String) {
        _state.value = _state.value.copy(pending = _state.value.pending.filter { it.id != uploadId })
        viewModelScope.launch {
            try {
                api.approveItem(plotId, uploadId)
                load(api, flowId, plotId)
            } catch (_: Exception) {
                load(api, flowId, plotId)
            }
        }
    }

    fun reject(api: HeirloomsApi, flowId: String, plotId: String, uploadId: String) {
        _state.value = _state.value.copy(pending = _state.value.pending.filter { it.id != uploadId })
        viewModelScope.launch {
            try {
                api.rejectItem(plotId, uploadId)
                load(api, flowId, plotId)
            } catch (_: Exception) {
                load(api, flowId, plotId)
            }
        }
    }

    fun restore(api: HeirloomsApi, flowId: String, plotId: String, uploadId: String) {
        _state.value = _state.value.copy(rejected = _state.value.rejected.filter { it.id != uploadId })
        viewModelScope.launch {
            try {
                api.unrejectItem(plotId, uploadId)
                load(api, flowId, plotId)
            } catch (_: Exception) {
                load(api, flowId, plotId)
            }
        }
    }
}
