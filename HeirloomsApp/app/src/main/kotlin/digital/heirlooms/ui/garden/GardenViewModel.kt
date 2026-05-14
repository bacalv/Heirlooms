package digital.heirlooms.ui.garden

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import digital.heirlooms.api.HeirloomsApi
import digital.heirlooms.api.Plot
import digital.heirlooms.api.Upload
import digital.heirlooms.api.isShared
import digital.heirlooms.crypto.VaultCrypto
import digital.heirlooms.crypto.VaultSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

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
                val allPlots = api.listPlots()
                val systemPlot = allPlots.firstOrNull { it.isSystemDefined }
                val userPlots = allPlots.filter { !it.isSystemDefined && it.showInGarden }
                val (justArrived) = coroutineScope {
                    val ja = async { systemPlot?.let { api.listUploadsPage(plotId = it.id) }
                        ?: api.listUploadsPage(justArrived = true) }
                    listOf(ja.await())
                }
                val rows = buildList {
                    add(PlotRowState(
                        plot = systemPlot,
                        uploads = justArrived.uploads,
                        nextCursor = justArrived.nextCursor,
                        loading = false,
                        loadingMore = false,
                    ))
                    userPlots.forEach { plot ->
                        val page = api.listUploadsPage(plotId = plot.id)
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
            } catch (_: Exception) { }
        }
    }

    fun load(api: HeirloomsApi) {
        viewModelScope.launch {
            _state.value = GardenLoadState.Loading
            try {
                val allPlots = api.listPlots()
                val systemPlot = allPlots.firstOrNull { it.isSystemDefined }
                val userPlots = allPlots.filter { !it.isSystemDefined && it.showInGarden }
                val justArrived = systemPlot?.let { api.listUploadsPage(plotId = it.id) }
                    ?: api.listUploadsPage(justArrived = true)

                val rows = buildList {
                    add(PlotRowState(
                        plot = systemPlot,
                        uploads = justArrived.uploads,
                        nextCursor = justArrived.nextCursor,
                        loading = false,
                        loadingMore = false,
                    ))
                    userPlots.forEach { plot ->
                        val page = api.listUploadsPage(plotId = plot.id)
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
                val enriched = rows.map { row ->
                    row.copy(uploads = enrichWithFriendNames(row.uploads, _friends.value))
                }
                _state.value = GardenLoadState.Ready(enriched)
            } catch (e: Exception) {
                _state.value = GardenLoadState.Error(e.message ?: "Couldn't load")
            }
        }
        viewModelScope.launch {
            try { _availableTags.value = api.listTags() } catch (_: Exception) {}
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
                val page = if (row.plot == null || row.plot.isSystemDefined) {
                    api.listUploadsPage(cursor = cursor, plotId = row.plot?.id, justArrived = row.plot == null)
                } else {
                    api.listUploadsPage(cursor = cursor, plotId = row.plot.id)
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

    // The IDs that just landed in Just arrived from the latest poll — non-empty triggers
    // the per-tile arrival animation. Cleared by GardenScreen once animations start.
    private val _newlyArrivedIds = MutableStateFlow<Set<String>>(emptySet())
    val newlyArrivedIds: StateFlow<Set<String>> = _newlyArrivedIds.asStateFlow()

    fun clearNewlyArrived() { _newlyArrivedIds.value = emptySet() }

    private var knownJustArrivedIds: Set<String> = emptySet()

    fun refreshJustArrived(api: HeirloomsApi) {
        viewModelScope.launch {
            try {
                val page = api.listUploadsPage(justArrived = true)
                val newIds = page.uploads.map { it.id }.toSet()
                val genuinelyNew = newIds - knownJustArrivedIds
                // Fire the animation whenever new items appear — including the first item
                // arriving into a previously empty Just arrived row. The poll only runs
                // after a 30s delay so load() has always set knownJustArrivedIds first.
                if (genuinelyNew.isNotEmpty()) {
                    _newlyArrivedIds.value = genuinelyNew
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

    // ---- Sharing key lazy init ---------------------------------------------

    // Called once after vault unlock. Checks for a sharing key on the server;
    // generates and uploads one if missing, then stores it in VaultSession.
    // Also triggers plot key loading so encrypted shared-plot thumbnails can decrypt.
    fun ensureSharingKey(api: HeirloomsApi) {
        if (VaultSession.sharingPrivkey != null) return
        viewModelScope.launch {
            try {
                val existing = api.getSharingKeyMe()
                if (existing != null) {
                    val masterKey = VaultSession.masterKey
                    val privkeyBytes = VaultCrypto.unwrapDekWithMasterKey(existing.wrappedPrivkey, masterKey)
                    VaultSession.setSharingPrivkey(privkeyBytes)
                } else {
                    val masterKey = VaultSession.masterKey
                    val (privkeyPkcs8, pubkeySpki) = VaultCrypto.generateSharingKeypair()
                    val wrappedPrivkey = VaultCrypto.wrapDekUnderMasterKey(privkeyPkcs8, masterKey)
                    val pubkeyB64 = Base64.encodeToString(pubkeySpki, Base64.NO_WRAP)
                    val wrappedPrivkeyB64 = Base64.encodeToString(wrappedPrivkey, Base64.NO_WRAP)
                    api.putSharingKey(pubkeyB64, wrappedPrivkeyB64, VaultCrypto.ALG_MASTER_AES256GCM_V1)
                    VaultSession.setSharingPrivkey(privkeyPkcs8)
                }
                ensurePlotKeys(api)
            } catch (_: Exception) { /* best-effort; retry on next load */ }
        }
    }

    // Loads and caches raw plot keys for all shared plots the user is a member of.
    // Requires the sharing private key to be set in VaultSession first.
    fun ensurePlotKeys(api: HeirloomsApi) {
        viewModelScope.launch {
            try {
                val privkey = VaultSession.sharingPrivkey ?: return@launch
                val plots = api.listPlots()
                plots.filter { it.visibility == "shared" && VaultSession.getPlotKey(it.id) == null }.forEach { plot ->
                    try {
                        val (wrappedKey, _) = api.getPlotKey(plot.id)
                        val rawKey = VaultCrypto.unwrapPlotKey(
                            android.util.Base64.decode(wrappedKey, android.util.Base64.DEFAULT),
                            privkey,
                        )
                        VaultSession.setPlotKey(plot.id, rawKey)
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }
    }

    // ---- Plot management ---------------------------------------------------

    fun createPlot(api: HeirloomsApi, name: String, criteria: String? = null, showInGarden: Boolean = true) {
        viewModelScope.launch {
            try {
                api.createPlot(name, criteria = criteria, showInGarden = showInGarden)
                load(api)
            } catch (_: Exception) { }
        }
    }

    fun createSharedPlot(api: HeirloomsApi, name: String, wrappedPlotKey: String, plotKeyFormat: String) {
        viewModelScope.launch {
            try {
                api.createPlot(name, visibility = "shared", wrappedPlotKey = wrappedPlotKey, plotKeyFormat = plotKeyFormat)
                load(api)
            } catch (_: Exception) { }
        }
    }

    fun renamePlot(api: HeirloomsApi, plot: Plot, newName: String) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        _state.value = GardenLoadState.Ready(current.rows.map { row ->
            if (row.plot?.id == plot.id) row.copy(plot = plot.copy(name = newName)) else row
        })
        viewModelScope.launch {
            try {
                api.updatePlot(plot.id, name = newName)
                load(api)
            } catch (_: Exception) {
                _state.value = GardenLoadState.Ready(current.rows)
            }
        }
    }

    fun deletePlot(api: HeirloomsApi, plotId: String) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        _state.value = GardenLoadState.Ready(current.rows.filter { it.plot?.id != plotId })
        viewModelScope.launch {
            try {
                api.deletePlot(plotId)
            } catch (_: Exception) {
                _state.value = GardenLoadState.Ready(current.rows)
            }
        }
    }

    private val _leaveError = MutableStateFlow<String?>(null)
    val leaveError: StateFlow<String?> = _leaveError.asStateFlow()

    fun clearLeaveError() { _leaveError.value = null }

    fun leavePlot(api: HeirloomsApi, plotId: String) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        _state.value = GardenLoadState.Ready(current.rows.filter { it.plot?.id != plotId })
        viewModelScope.launch {
            try {
                api.leaveSharedPlot(plotId)
            } catch (e: Exception) {
                _state.value = GardenLoadState.Ready(current.rows)
                if (e.message == "must_transfer") {
                    _leaveError.value = "Transfer ownership to another member before leaving."
                }
            }
        }
    }

    // ---- Cached friends list (loaded lazily for display name resolution) ---

    // Maps plotId → ownerDisplayName for non-owner shared plots shown in the garden.
    private val _ownerNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val ownerNames: StateFlow<Map<String, String>> = _ownerNames.asStateFlow()

    fun loadSharedMemberships(api: HeirloomsApi) {
        viewModelScope.launch {
            try {
                val memberships = api.listSharedMemberships()
                _ownerNames.value = memberships
                    .filter { it.role == "member" && it.ownerDisplayName != null }
                    .associate { it.plotId to it.ownerDisplayName!! }
            } catch (_: Exception) { }
        }
    }

    private val _friends = MutableStateFlow<List<HeirloomsApi.Friend>>(emptyList())
    val friends: StateFlow<List<HeirloomsApi.Friend>> = _friends.asStateFlow()

    fun loadFriends(api: HeirloomsApi) {
        if (_friends.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _friends.value = api.getFriends()
                // Re-enrich any already-loaded shared uploads with display names.
                val current = _state.value as? GardenLoadState.Ready ?: return@launch
                _state.value = GardenLoadState.Ready(current.rows.map { row ->
                    row.copy(uploads = enrichWithFriendNames(row.uploads, _friends.value))
                })
            } catch (_: Exception) { }
        }
    }

    private fun enrichWithFriendNames(uploads: List<Upload>, friendList: List<HeirloomsApi.Friend>): List<Upload> {
        if (friendList.isEmpty()) return uploads
        val byId = friendList.associateBy { it.userId }
        return uploads.map { u ->
            if (u.sharedFromUserId != null && u.sharedFromDisplayName == null) {
                val name = byId[u.sharedFromUserId]?.displayName ?: byId[u.sharedFromUserId]?.username
                if (name != null) u.copy(sharedFromDisplayName = name) else u
            } else u
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

    // ---- Shared-plot staging counts ----------------------------------------

    private val _sharedStagingCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sharedStagingCounts: StateFlow<Map<String, Int>> = _sharedStagingCounts.asStateFlow()

    /** Polls the pending staging count for each shared plot visible in the garden. */
    fun refreshSharedStagingCounts(api: HeirloomsApi) {
        val current = _state.value as? GardenLoadState.Ready ?: return
        val sharedPlots = current.rows.mapNotNull { it.plot }.filter { it.isShared }
        if (sharedPlots.isEmpty()) return
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            sharedPlots.forEach { plot ->
                try {
                    val items = api.getPlotStaging(plot.id)
                    counts[plot.id] = items.size
                } catch (_: Exception) {}
            }
            _sharedStagingCounts.value = counts
        }
    }
}
