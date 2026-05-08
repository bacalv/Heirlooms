package digital.heirlooms.ui.share

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareViewModelTest {

    @Test
    fun pendingTags_survive_round_trip() {
        val handle = SavedStateHandle()
        val vm = ShareViewModel(handle)
        vm.pendingTags = listOf("family", "2025")

        val restored = ShareViewModel(handle)
        assertEquals(listOf("family", "2025"), restored.pendingTags)
    }

    @Test
    fun defaults_are_safe() {
        val vm = ShareViewModel(SavedStateHandle())
        assertNull(vm.sessionTag)
        assertEquals(emptyList<String>(), vm.pendingTags)
    }
}
