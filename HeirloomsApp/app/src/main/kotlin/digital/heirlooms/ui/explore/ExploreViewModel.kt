package digital.heirlooms.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Upload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExploreFilters(
    val tags: List<String> = emptyList(),
    val justArrived: Boolean = false,
    val fromDate: String? = null,
    val toDate: String? = null,
    val inCapsule: Boolean? = null,
    val hasLocation: Boolean? = null,
    val includeComposted: Boolean = false,
    val sort: String = "upload_newest",
)

sealed class ExploreState {
    object Loading : ExploreState()
    data class Ready(val uploads: List<Upload>, val nextCursor: String?, val loadingMore: Boolean = false) : ExploreState()
    data class Error(val message: String) : ExploreState()
}

class ExploreViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Loading)
    val state: StateFlow<ExploreState> = _state

    // Filters survive configuration changes and process death.
    var filters: ExploreFilters
        get() = ExploreFilters(
            tags = savedState.get<Array<String>>("filter_tags")?.toList() ?: emptyList(),
            justArrived = savedState["filter_just_arrived"] ?: false,
            fromDate = savedState["filter_from_date"],
            toDate = savedState["filter_to_date"],
            inCapsule = savedState["filter_in_capsule"],
            hasLocation = savedState["filter_has_location"],
            includeComposted = savedState["filter_include_composted"] ?: false,
            sort = savedState["filter_sort"] ?: "upload_newest",
        )
        set(v) {
            savedState["filter_tags"] = v.tags.toTypedArray()
            savedState["filter_just_arrived"] = v.justArrived
            savedState["filter_from_date"] = v.fromDate
            savedState["filter_to_date"] = v.toDate
            savedState["filter_in_capsule"] = v.inCapsule
            savedState["filter_has_location"] = v.hasLocation
            savedState["filter_include_composted"] = v.includeComposted
            savedState["filter_sort"] = v.sort
        }

    var scrollPosition: Int
        get() = savedState["scroll_position"] ?: 0
        set(v) { savedState["scroll_position"] = v }

    fun applyInitialFilters(tags: List<String>, justArrived: Boolean) {
        filters = ExploreFilters(tags = tags, justArrived = justArrived)
    }

    fun load(api: HeirloomsApi) {
        val f = filters
        viewModelScope.launch {
            _state.value = ExploreState.Loading
            try {
                val page = api.listUploadsPage(
                    tags = f.tags,
                    justArrived = f.justArrived,
                    fromDate = f.fromDate,
                    toDate = f.toDate,
                    inCapsule = f.inCapsule,
                    hasLocation = f.hasLocation,
                    includeComposted = f.includeComposted,
                    sort = f.sort,
                )
                _state.value = ExploreState.Ready(page.uploads, page.nextCursor)
            } catch (e: Exception) {
                _state.value = ExploreState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    fun loadMore(api: HeirloomsApi) {
        val current = _state.value as? ExploreState.Ready ?: return
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return

        _state.value = current.copy(loadingMore = true)
        val f = filters
        viewModelScope.launch {
            try {
                val page = api.listUploadsPage(
                    cursor = cursor,
                    tags = f.tags,
                    justArrived = f.justArrived,
                    fromDate = f.fromDate,
                    toDate = f.toDate,
                    inCapsule = f.inCapsule,
                    hasLocation = f.hasLocation,
                    includeComposted = f.includeComposted,
                    sort = f.sort,
                )
                val fresh = _state.value as? ExploreState.Ready ?: return@launch
                _state.value = fresh.copy(
                    uploads = fresh.uploads + page.uploads,
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                )
            } catch (_: Exception) {
                val fresh = _state.value as? ExploreState.Ready ?: return@launch
                _state.value = fresh.copy(loadingMore = false)
            }
        }
    }

    fun updateFilters(api: HeirloomsApi, newFilters: ExploreFilters) {
        filters = newFilters
        load(api)
    }
}
