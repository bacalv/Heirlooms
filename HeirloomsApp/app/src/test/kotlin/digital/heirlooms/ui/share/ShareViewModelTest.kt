package digital.heirlooms.ui.share

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareViewModelTest {

    @Test
    fun pendingWorkerId_survives_round_trip() {
        val handle = SavedStateHandle()
        val vm = ShareViewModel(handle)
        vm.pendingWorkerId = "550e8400-e29b-41d4-a716-446655440000"

        val restored = ShareViewModel(handle)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", restored.pendingWorkerId)
    }

    @Test
    fun uploadPhotoCount_survives_round_trip() {
        val handle = SavedStateHandle()
        val vm = ShareViewModel(handle)
        vm.uploadPhotoCount = 7

        val restored = ShareViewModel(handle)
        assertEquals(7, restored.uploadPhotoCount)
    }

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
        assertNull(vm.pendingWorkerId)
        assertEquals(0, vm.uploadPhotoCount)
        assertEquals(emptyList<String>(), vm.pendingTags)
    }
}
