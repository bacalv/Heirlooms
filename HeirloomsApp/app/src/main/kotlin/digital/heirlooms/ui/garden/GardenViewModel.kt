package digital.heirlooms.ui.garden

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import digital.heirlooms.api.Upload
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // Silent refresh: fetches fresh data without clearing the current display
    // (no loading spinner). Called every time GardenScreen enters composition so the
    // data matches the server's current state after any activity on other platforms.
    fun refresh(api: HeirloomsApi) {
        if (_state.value is GardenLoadState.Loading) return  // initial load already in flight
        viewModelScope.launch {
            try {
                val (plots, justArrived) = coroutineScope {
                    val plotsDeferred = async { api.listPlots() }
                    val jaDeferred = async { api.listUploadsPage(justArrived = true) }
                    Pair(plotsDeferred.await(), jaDeferred.await())
                }
                val rows = buildList {
                    add(PlotRowState(
                        plot = null,
                        uploads = justArrived.uploads,
                        nextCursor = justArrived.nextCursor,
                        loading = false,
                        loadingMore = false,
                    ))
                    plots.filter { !it.isSystemDefined }.forEach { plot ->
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
                // Reset Just arrived scroll to top so the newest items are visible.
                knownJustArrivedIds = justArrived.uploads.map { it.id }.toSet()
                scrollPositions["__just_arrived__"] = 0
                _state.value = GardenLoadState.Ready(rows)
            } catch (_: Exception) { }
        }
    }

    fun load(api: HeirloomsApi) {
        viewModelScope.launch {
            _state.value = GardenLoadState.Loading
            try {
                val (plots, justArrived) = coroutineScope {
                    val plotsDeferred = async { api.listPlots() }
                    val justArrivedDeferred = async { api.listUploadsPage(justArrived = true) }
                    Pair(plotsDeferred.await(), justArrivedDeferred.await())
                }

                val rows = buildList {
                    add(PlotRowState(
                        plot = null,
                        uploads = justArrived.uploads,
                        nextCursor = justArrived.nextCursor,
                        loading = false,
                        loadingMore = false,
                    ))
                    plots.filter { !it.isSystemDefined }.forEach { plot ->
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
                knownJustArrivedIds = justArrived.uploads.map { it.id }.toSet()
                scrollPositions["__just_arrived__"] = 0
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

    // Set to true when polling detects IDs in Just arrived that weren't there before.
    // GardenScreen reads this, shows the arrival animation, then calls clearArrivalFlag().
    var newItemsArrived: Boolean = false
        private set

    fun clearArrivalFlag() { newItemsArrived = false }

    private var knownJustArrivedIds: Set<String> = emptySet()

    fun refreshJustArrived(api: HeirloomsApi) {
        viewModelScope.launch {
            try {
                val page = api.listUploadsPage(justArrived = true)
                val newIds = page.uploads.map { it.id }.toSet()
                val genuinelyNew = newIds - knownJustArrivedIds
                // Only trigger the animation if items appeared that weren't there before
                // (not on the very first load when knownIds is empty).
                if (knownJustArrivedIds.isNotEmpty() && genuinelyNew.isNotEmpty()) {
                    newItemsArrived = true
                }
                knownJustArrivedIds = newIds

                val current = _state.value as? GardenLoadState.Ready ?: return@launch
                val rows = current.rows.toMutableList()
                val jaIndex = rows.indexOfFirst { it.plot == null }
                if (jaIndex >= 0) {
                    rows[jaIndex] = rows[jaIndex].copy(
                        uploads = page.uploads,
                        nextCursor = page.nextCursor,
                        loadingMore = false,
                    )
                    if (genuinelyNew.isNotEmpty()) scrollPositions["__just_arrived__"] = 0
                    _state.value = GardenLoadState.Ready(rows)
                }
            } catch (_: Exception) { }
        }
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
