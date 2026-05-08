package digital.heirlooms.ui.explore

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreViewModelTest {

    @Test
    fun filters_survive_savedstate_round_trip() {
        val handle = SavedStateHandle()
        val vm = ExploreViewModel(handle)

        val filters = ExploreFilters(
            tags = listOf("family", "holidays"),
            justArrived = false,
            fromDate = "2025-01-01",
            toDate = "2025-12-31",
            inCapsule = true,
            hasLocation = false,
            includeComposted = true,
            sort = "taken_newest",
        )
        vm.filters = filters

        // Simulate process death: create a new ViewModel from the same SavedStateHandle.
        val restoredVm = ExploreViewModel(handle)
        val restored = restoredVm.filters

        assertEquals(listOf("family", "holidays"), restored.tags)
        assertFalse(restored.justArrived)
        assertEquals("2025-01-01", restored.fromDate)
        assertEquals("2025-12-31", restored.toDate)
        assertEquals(true, restored.inCapsule)
        assertEquals(false, restored.hasLocation)
        assertTrue(restored.includeComposted)
        assertEquals("taken_newest", restored.sort)
    }

    @Test
    fun initial_filters_are_empty_by_default() {
        val vm = ExploreViewModel(SavedStateHandle())
        val f = vm.filters
        assertTrue(f.tags.isEmpty())
        assertFalse(f.justArrived)
        assertEquals("upload_newest", f.sort)
        assertFalse(f.includeComposted)
    }

    @Test
    fun applyInitialFilters_sets_tags_and_justArrived() {
        val vm = ExploreViewModel(SavedStateHandle())
        vm.applyInitialFilters(listOf("garden", "spring"), justArrived = false)
        assertEquals(listOf("garden", "spring"), vm.filters.tags)
        assertFalse(vm.filters.justArrived)
    }

    @Test
    fun applyInitialFilters_sets_justArrived() {
        val vm = ExploreViewModel(SavedStateHandle())
        vm.applyInitialFilters(emptyList(), justArrived = true)
        assertTrue(vm.filters.justArrived)
    }

    @Test
    fun scroll_position_survives_round_trip() {
        val handle = SavedStateHandle()
        val vm = ExploreViewModel(handle)
        vm.scrollPosition = 42

        val restoredVm = ExploreViewModel(handle)
        assertEquals(42, restoredVm.scrollPosition)
    }
}
