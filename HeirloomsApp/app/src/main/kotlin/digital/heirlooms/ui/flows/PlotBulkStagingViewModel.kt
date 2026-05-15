package digital.heirlooms.ui.trellises

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlotBulkStagingState(
    val items: List<Upload> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val doneCount: Int = 0,
)

val PlotBulkStagingState.allSelected: Boolean
    get() = items.isNotEmpty() && selected.size == items.size

class PlotBulkStagingViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlotBulkStagingState())
    val state: StateFlow<PlotBulkStagingState> = _state.asStateFlow()

    fun load(api: HeirloomsApi, plotId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val items = api.getPlotStaging(plotId).map { it.upload }
                _state.value = _state.value.copy(items = items, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Couldn't load")
            }
        }
    }

    fun toggleItem(uploadId: String) {
        val current = _state.value
        val newSelected = if (uploadId in current.selected) {
            current.selected - uploadId
        } else {
            current.selected + uploadId
        }
        _state.value = current.copy(selected = newSelected)
    }

    fun toggleSelectAll() {
        val current = _state.value
        _state.value = current.copy(
            selected = if (current.allSelected) emptySet() else current.items.map { it.id }.toSet()
        )
    }

    fun approveSelected(api: HeirloomsApi, plotId: String) {
        val toApprove = _state.value.selected.toList()
        if (toApprove.isEmpty()) return
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            var successCount = 0
            val errors = mutableListOf<String>()
            for (uploadId in toApprove) {
                val upload = _state.value.items.find { it.id == uploadId } ?: continue
                try {
                    var wrappedItemDek: String? = null
                    var itemDekFormat: String? = null
                    var wrappedThumbDek: String? = null
                    var thumbDekFormat: String? = null

                    if (upload.wrappedDek != null) {
                        val plotKey = VaultSession.getPlotKey(plotId) ?: run {
                            val (wrappedKey, _) = api.getPlotKey(plotId)
                            val raw = VaultCrypto.unwrapPlotKey(
                                Base64.decode(wrappedKey, Base64.NO_WRAP),
                                VaultSession.sharingPrivkey ?: error("Sharing key not loaded"),
                            )
                            VaultSession.setPlotKey(plotId, raw)
                            raw
                        }
                        val masterKey = VaultSession.masterKey
                        val rawDek = VaultCrypto.unwrapDekWithMasterKey(upload.wrappedDek, masterKey)
                        val rewrappedDek = VaultCrypto.wrapDekWithPlotKey(rawDek, plotKey)
                        wrappedItemDek = Base64.encodeToString(rewrappedDek, Base64.NO_WRAP)
                        itemDekFormat = VaultCrypto.ALG_PLOT_AES256GCM_V1

                        if (upload.wrappedThumbnailDek != null) {
                            val rawThumb = VaultCrypto.unwrapDekWithMasterKey(upload.wrappedThumbnailDek, masterKey)
                            val rewrappedThumb = VaultCrypto.wrapDekWithPlotKey(rawThumb, plotKey)
                            wrappedThumbDek = Base64.encodeToString(rewrappedThumb, Base64.NO_WRAP)
                            thumbDekFormat = VaultCrypto.ALG_PLOT_AES256GCM_V1
                        }
                    }

                    api.approveItem(plotId, uploadId, wrappedItemDek, itemDekFormat, wrappedThumbDek, thumbDekFormat)
                    successCount++
                } catch (e: Exception) {
                    Log.e("PlotBulkStagingVM", "approve failed for $uploadId", e)
                    errors.add(uploadId)
                }
            }
            val errorMsg = if (errors.isNotEmpty()) "${errors.size} item(s) failed to approve" else null
            // Reload pending items
            try {
                val fresh = api.getPlotStaging(plotId).map { it.upload }
                _state.value = _state.value.copy(
                    items = fresh,
                    selected = emptySet(),
                    working = false,
                    error = errorMsg,
                    doneCount = _state.value.doneCount + successCount,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(working = false, error = errorMsg ?: e.message)
            }
        }
    }

    fun rejectSelected(api: HeirloomsApi, plotId: String) {
        val toReject = _state.value.selected.toList()
        if (toReject.isEmpty()) return
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            var successCount = 0
            val errors = mutableListOf<String>()
            for (uploadId in toReject) {
                try {
                    api.rejectItem(plotId, uploadId)
                    successCount++
                } catch (e: Exception) {
                    Log.e("PlotBulkStagingVM", "reject failed for $uploadId", e)
                    errors.add(uploadId)
                }
            }
            val errorMsg = if (errors.isNotEmpty()) "${errors.size} item(s) failed to reject" else null
            try {
                val fresh = api.getPlotStaging(plotId).map { it.upload }
                _state.value = _state.value.copy(
                    items = fresh,
                    selected = emptySet(),
                    working = false,
                    error = errorMsg,
                    doneCount = _state.value.doneCount + successCount,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(working = false, error = errorMsg ?: e.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
