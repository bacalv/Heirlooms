package digital.heirlooms.ui.garden

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import digital.heirlooms.api.Upload
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlotRowState(
    val plot: Plot?,            // null = Just arrived system row
    val uploads: List<Upload>,
    val nextCursor: String?,
    val loading: Boolean,
    val loadingMore: Boolean,
)

sealed class GardenLoadState {
    object Loading : GardenLoadState()
    data class Ready(val rows: List<PlotRowState>) : GardenLoadState()
    data class Error(val message: String) : GardenLoadState()
}

class GardenViewModel(
    @Suppress("UNUSED_PARAMETER") savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<GardenLoadState>(GardenLoadState.Loading)
    val state: StateFlow<GardenLoadState> = _state

    // Scroll positions survive config changes because the ViewModel does.
    // Process-death restoration isn't needed for scroll position.
    val scrollPositions: MutableMap<String, Int> = mutableMapOf()

    fun load(api: HeirloomsApi) {
        viewModelScope.launch {
            _state.value = GardenLoadState.Loading
            try {
                val plotsDeferred = async { api.listPlots() }
                val justArrivedDeferred = async { api.listUploadsPage(justArrived = true) }

                val plots = plotsDeferred.await()
                val justArrived = justArrivedDeferred.await()

                val rows = buildList {
                    add(PlotRowState(
                        plot = null,
                        uploads = justArrived.uploads,
                        nextCursor = justArrived.nextCursor,
                        loading = false,
                        loadingMore = false,
                    ))
                    plots.forEach { plot ->
                        val page = api.listUploadsPage(tags = plot.tagCriteria)
                        add(PlotRowState(
                            plot = plot,
                            uploads = page.uploads,
                            nextCursor = page.nextCursor,
                            loading = false,
                            loadingMore = false,
                        ))
                    }
                }
                _state.value = GardenLoadState.Ready(rows)
            } catch (e: Exception) {
                _state.value = GardenLoadState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun loadMoreForRow(api: HeirloomsApi, rowIndex: Int) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        val row = current.rows.getOrNull(rowIndex) ?: return
        val cursor = row.nextCursor ?: return
        if (row.loadingMore) return

        val updatedRows = current.rows.toMutableList()
        updatedRows[rowIndex] = row.copy(loadingMore = true)
        _state.value = GardenLoadState.Ready(updatedRows)

        viewModelScope.launch {
            try {
                val page = if (row.plot == null) {
                    api.listUploadsPage(cursor = cursor, justArrived = true)
                } else {
                    api.listUploadsPage(cursor = cursor, tags = row.plot.tagCriteria)
                }
                val fresh = (_state.value as? GardenLoadState.Ready)?.rows?.toMutableList() ?: return@launch
                fresh[rowIndex] = fresh[rowIndex].copy(
                    uploads = fresh[rowIndex].uploads + page.uploads,
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                )
                _state.value = GardenLoadState.Ready(fresh)
            } catch (_: Exception) {
                val fresh = (_state.value as? GardenLoadState.Ready)?.rows?.toMutableList() ?: return@launch
                fresh[rowIndex] = fresh[rowIndex].copy(loadingMore = false)
                _state.value = GardenLoadState.Ready(fresh)
            }
        }
    }

    fun saveScrollPosition(rowKey: String, index: Int) {
        scrollPositions[rowKey] = index
    }

    fun optimisticRotate(uploadId: String, newRotation: Int) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        _state.value = GardenLoadState.Ready(current.rows.map { row ->
            row.copy(uploads = row.uploads.map { u ->
                if (u.id == uploadId) u.copy(rotation = newRotation) else u
            })
        })
    }

    fun optimisticTag(uploadId: String, newTags: List<String>) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        _state.value = GardenLoadState.Ready(current.rows.map { row ->
            row.copy(uploads = row.uploads.map { u ->
                if (u.id == uploadId) u.copy(tags = newTags) else u
            })
        })
    }

    fun removeFromJustArrived(uploadId: String) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        val rows = current.rows.toMutableList()
        val jaIndex = rows.indexOfFirst { it.plot == null }
        if (jaIndex >= 0) {
            rows[jaIndex] = rows[jaIndex].copy(
                uploads = rows[jaIndex].uploads.filter { it.id != uploadId }
            )
        }
        _state.value = GardenLoadState.Ready(rows)
    }
}
