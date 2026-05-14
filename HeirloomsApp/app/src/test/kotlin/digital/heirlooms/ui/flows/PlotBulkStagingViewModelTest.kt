package digital.heirlooms.ui.flows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PlotBulkStagingViewModel] — covers the selection state machine
 * entirely in memory without network I/O.
 *
 * Network path (load / approveSelected / rejectSelected) exercises real OkHttp
 * dispatch on background threads that the test dispatcher cannot drive; those
 * paths are covered by integration tests instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlotBulkStagingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun initial_state_is_loading_with_empty_selection() {
        val vm = PlotBulkStagingViewModel()
        assertTrue("should start in loading state", vm.state.value.loading)
        assertTrue("selection should be empty", vm.state.value.selected.isEmpty())
        assertTrue("items should be empty", vm.state.value.items.isEmpty())
        assertNull("no error on init", vm.state.value.error)
    }

    // ── toggleItem ────────────────────────────────────────────────────────────

    @Test
    fun toggleItem_selects_item_on_first_call() {
        val vm = withItems("u1", "u2", "u3")
        vm.toggleItem("u2")
        assertEquals(setOf("u2"), vm.state.value.selected)
    }

    @Test
    fun toggleItem_deselects_item_on_second_call() {
        val vm = withItems("u1", "u2")
        vm.toggleItem("u1")
        vm.toggleItem("u1")
        assertFalse("u1 should be deselected after round-trip", "u1" in vm.state.value.selected)
    }

    @Test
    fun toggleItem_multiple_independent_selections_accumulate() {
        val vm = withItems("a", "b", "c")
        vm.toggleItem("a")
        vm.toggleItem("c")
        assertEquals(setOf("a", "c"), vm.state.value.selected)
    }

    @Test
    fun toggleItem_only_affects_targeted_item() {
        val vm = withItems("x", "y", "z")
        vm.toggleItem("y")
        assertFalse("x should be unaffected", "x" in vm.state.value.selected)
        assertFalse("z should be unaffected", "z" in vm.state.value.selected)
    }

    // ── toggleSelectAll ───────────────────────────────────────────────────────

    @Test
    fun toggleSelectAll_selects_all_when_nothing_is_selected() {
        val vm = withItems("1", "2", "3")
        vm.toggleSelectAll()
        assertEquals(setOf("1", "2", "3"), vm.state.value.selected)
    }

    @Test
    fun toggleSelectAll_selects_all_when_only_partial_selection() {
        val vm = withItems("a", "b", "c")
        vm.toggleItem("b")
        vm.toggleSelectAll()
        assertEquals(setOf("a", "b", "c"), vm.state.value.selected)
    }

    @Test
    fun toggleSelectAll_deselects_all_when_all_are_selected() {
        val vm = withItems("p", "q")
        vm.toggleSelectAll() // select all
        vm.toggleSelectAll() // deselect all
        assertTrue("all items should be deselected", vm.state.value.selected.isEmpty())
    }

    // ── allSelected extension property ────────────────────────────────────────

    @Test
    fun allSelected_true_only_when_every_item_is_selected() {
        val vm = withItems("a", "b")
        assertFalse("not allSelected when empty selection", vm.state.value.allSelected)
        vm.toggleItem("a")
        assertFalse("not allSelected when partial selection", vm.state.value.allSelected)
        vm.toggleItem("b")
        assertTrue("allSelected when every item selected", vm.state.value.allSelected)
    }

    @Test
    fun allSelected_false_when_item_list_is_empty() {
        val vm = PlotBulkStagingViewModel()
        setVmState(vm, vm.state.value.copy(loading = false, items = emptyList()))
        assertFalse("allSelected requires non-empty list", vm.state.value.allSelected)
    }

    // ── clearError ────────────────────────────────────────────────────────────

    @Test
    fun clearError_removes_error_message() {
        val vm = PlotBulkStagingViewModel()
        setVmState(vm, vm.state.value.copy(error = "network timeout"))
        vm.clearError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun clearError_is_idempotent_when_no_error() {
        val vm = PlotBulkStagingViewModel()
        vm.clearError()
        assertNull(vm.state.value.error)
    }

    // ── doneCount ────────────────────────────────────────────────────────────

    @Test
    fun doneCount_starts_at_zero() {
        assertEquals(0, PlotBulkStagingViewModel().state.value.doneCount)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Create a VM with specified items already loaded (not in loading state). */
    private fun withItems(vararg ids: String): PlotBulkStagingViewModel {
        val vm = PlotBulkStagingViewModel()
        setVmState(vm, vm.state.value.copy(
            loading = false,
            items = ids.map { makeUpload(it) },
        ))
        return vm
    }

    private fun makeUpload(id: String) = digital.heirlooms.api.Upload(
        id = id,
        storageKey = "sk-$id",
        mimeType = "image/jpeg",
        fileSize = 1024L,
        uploadedAt = "2026-01-01T00:00:00Z",
        rotation = 0,
        thumbnailKey = "th-$id",
        tags = emptyList(),
        compostedAt = null,
        takenAt = null,
        latitude = null,
        longitude = null,
        lastViewedAt = null,
    )

    /** Directly set the private `_state` MutableStateFlow for test setup. */
    private fun setVmState(vm: PlotBulkStagingViewModel, state: PlotBulkStagingState) {
        val field = vm.javaClass.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sf = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<PlotBulkStagingState>
        sf.value = state
    }
}
