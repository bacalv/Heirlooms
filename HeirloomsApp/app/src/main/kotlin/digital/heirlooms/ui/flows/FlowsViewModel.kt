package digital.heirlooms.ui.flows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.Flow
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FlowWithCount(val flow: Flow, val pendingCount: Int)

sealed class FlowsState {
    object Loading : FlowsState()
    data class Ready(val flows: List<FlowWithCount>, val plots: List<Plot>) : FlowsState()
    data class Error(val message: String) : FlowsState()
}

class FlowsViewModel : ViewModel() {

    private val _state = MutableStateFlow<FlowsState>(FlowsState.Loading)
    val state: StateFlow<FlowsState> = _state

    fun load(api: HeirloomsApi) {
        viewModelScope.launch {
            _state.value = FlowsState.Loading
            try {
                val flows = api.listFlows()
                val plots = api.listPlots()
                val flowsWithCounts = coroutineScope {
                    flows.map { flow ->
                        async {
                            val count = runCatching { api.getFlowStaging(flow.id).size }.getOrDefault(0)
                            FlowWithCount(flow, count)
                        }
                    }.map { it.await() }
                }
                _state.value = FlowsState.Ready(flowsWithCounts, plots)
            } catch (e: Exception) {
                _state.value = FlowsState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun createFlow(api: HeirloomsApi, name: String, targetPlotId: String, requiresStaging: Boolean) {
        viewModelScope.launch {
            try {
                api.createFlow(name, criteria = """{"type":"just_arrived"}""", targetPlotId = targetPlotId, requiresStaging = requiresStaging)
                load(api)
            } catch (_: Exception) { }
        }
    }

    fun deleteFlow(api: HeirloomsApi, flowId: String) {
        val current = _state.value as? FlowsState.Ready ?: return
        _state.value = FlowsState.Ready(current.flows.filter { it.flow.id != flowId }, current.plots)
        viewModelScope.launch {
            try {
                api.deleteFlow(flowId)
                load(api)
            } catch (_: Exception) {
                _state.value = current
            }
        }
    }
}
