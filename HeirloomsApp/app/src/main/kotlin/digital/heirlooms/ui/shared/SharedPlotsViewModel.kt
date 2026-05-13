package digital.heirlooms.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.PlotMember
import digital.heirlooms.api.SharedMembership
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SharedPlotsLoadState {
    object Loading : SharedPlotsLoadState()
    data class Ready(val memberships: List<SharedMembership>) : SharedPlotsLoadState()
    data class Error(val message: String) : SharedPlotsLoadState()
}

class SharedPlotsViewModel : ViewModel() {

    private val _state = MutableStateFlow<SharedPlotsLoadState>(SharedPlotsLoadState.Loading)
    val state: StateFlow<SharedPlotsLoadState> = _state.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() { _actionError.value = null }

    fun load(api: HeirloomsApi) {
        _state.value = SharedPlotsLoadState.Loading
        viewModelScope.launch {
            try {
                val memberships = api.listSharedMemberships()
                _state.value = SharedPlotsLoadState.Ready(memberships)
            } catch (e: Exception) {
                _state.value = SharedPlotsLoadState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun refresh(api: HeirloomsApi) {
        if (_state.value is SharedPlotsLoadState.Loading) return
        viewModelScope.launch {
            try {
                val memberships = api.listSharedMemberships()
                _state.value = SharedPlotsLoadState.Ready(memberships)
            } catch (_: Exception) { }
        }
    }

    fun acceptInvite(api: HeirloomsApi, plotId: String, localName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.acceptPlotInvite(plotId, localName)
                refresh(api)
                onDone()
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Failed to accept invitation"
            }
        }
    }

    fun leavePlot(api: HeirloomsApi, plotId: String) {
        viewModelScope.launch {
            try {
                api.leaveSharedPlot(plotId)
                refresh(api)
            } catch (e: Exception) {
                when (e.message) {
                    "must_transfer" -> _actionError.value = "Transfer ownership to another member before leaving."
                    else -> _actionError.value = e.message ?: "Failed to leave plot"
                }
            }
        }
    }

    fun rejoinPlot(api: HeirloomsApi, plotId: String, localName: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.rejoinPlot(plotId, localName)
                refresh(api)
                onDone()
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Failed to rejoin plot"
            }
        }
    }

    fun restorePlot(api: HeirloomsApi, plotId: String) {
        viewModelScope.launch {
            try {
                api.restorePlot(plotId)
                refresh(api)
            } catch (e: Exception) {
                when (e.message) {
                    "not_authorized" -> _actionError.value = "Only the member who removed the plot can restore it."
                    "window_expired" -> _actionError.value = "The restore window has expired."
                    else -> _actionError.value = e.message ?: "Failed to restore plot"
                }
            }
        }
    }

    fun transferOwnership(api: HeirloomsApi, plotId: String, newOwnerId: String) {
        viewModelScope.launch {
            try {
                api.transferOwnership(plotId, newOwnerId)
                refresh(api)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Failed to transfer ownership"
            }
        }
    }

    fun setPlotStatus(api: HeirloomsApi, plotId: String, status: String) {
        viewModelScope.launch {
            try {
                api.setPlotStatus(plotId, status)
                refresh(api)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Failed to update plot status"
            }
        }
    }

    // Fetches joined (non-owner) members for the transfer ownership picker.
    private val _transferMembers = MutableStateFlow<List<PlotMember>>(emptyList())
    val transferMembers: StateFlow<List<PlotMember>> = _transferMembers.asStateFlow()

    fun loadMembersForTransfer(api: HeirloomsApi, plotId: String) {
        viewModelScope.launch {
            try {
                val members = api.listPlotMembers(plotId)
                _transferMembers.value = members.filter { it.role != "owner" && it.status == "joined" }
            } catch (_: Exception) { }
        }
    }

    fun clearTransferMembers() { _transferMembers.value = emptyList() }
}
