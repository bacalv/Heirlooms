package digital.heirlooms.ui.trellises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.Trellis
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TrellisWithCount(val trellis: Trellis, val pendingCount: Int)

sealed class TrellisesState {
    object Loading : TrellisesState()
    data class Ready(val trellises: List<TrellisWithCount>, val plots: List<Plot>) : TrellisesState()
    data class Error(val message: String) : TrellisesState()
}

class TrellisesViewModel : ViewModel() {

    private val _state = MutableStateFlow<TrellisesState>(TrellisesState.Loading)
    val state: StateFlow<TrellisesState> = _state

    fun load(api: HeirloomsApi) {
        viewModelScope.launch {
            _state.value = TrellisesState.Loading
            try {
                val trellises = api.listTrellises()
                val plots = api.listPlots()
                val trellisesWithCounts = coroutineScope {
                    trellises.map { trellis ->
                        async {
                            val count = runCatching { api.getTrellisStaging(trellis.id).size }.getOrDefault(0)
                            TrellisWithCount(trellis, count)
                        }
                    }.map { it.await() }
                }
                _state.value = TrellisesState.Ready(trellisesWithCounts, plots)
            } catch (e: Exception) {
                _state.value = TrellisesState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun createTrellis(api: HeirloomsApi, name: String, targetPlotId: String, requiresStaging: Boolean, criteria: String) {
        viewModelScope.launch {
            try {
                api.createTrellis(name, criteria = criteria, targetPlotId = targetPlotId, requiresStaging = requiresStaging)
                load(api)
            } catch (_: Exception) { }
        }
    }

    fun deleteTrellis(api: HeirloomsApi, trellisId: String) {
        val current = _state.value as? TrellisesState.Ready ?: return
        _state.value = TrellisesState.Ready(current.trellises.filter { it.trellis.id != trellisId }, current.plots)
        viewModelScope.launch {
            try {
                api.deleteTrellis(trellisId)
                load(api)
            } catch (_: Exception) {
                _state.value = current
            }
        }
    }
}
