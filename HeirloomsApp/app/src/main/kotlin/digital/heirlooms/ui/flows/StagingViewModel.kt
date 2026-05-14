package digital.heirlooms.ui.flows

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
import kotlinx.coroutines.launch

data class StagingState(
    val pending: List<Upload> = emptyList(),
    val rejected: List<Upload> = emptyList(),
    val loading: Boolean = true,
    val approveError: String? = null,
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

    fun approve(api: HeirloomsApi, flowId: String, plotId: String, uploadId: String, upload: Upload, isSharedPlot: Boolean) {
        _state.value = _state.value.copy(pending = _state.value.pending.filter { it.id != uploadId })
        viewModelScope.launch {
            try {
                var wrappedItemDek: String? = null
                var itemDekFormat: String? = null
                var wrappedThumbDek: String? = null
                var thumbDekFormat: String? = null

                if (isSharedPlot && upload.wrappedDek != null) {
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
                _state.value = _state.value.copy(approveError = null)
                load(api, flowId, plotId)
            } catch (e: Exception) {
                Log.e("StagingVM", "approve failed for $uploadId isShared=$isSharedPlot wrappedDek=${upload.wrappedDek != null} dekFmt=${upload.dekFormat}", e)
                _state.value = _state.value.copy(approveError = e.message ?: "Couldn't approve item")
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
