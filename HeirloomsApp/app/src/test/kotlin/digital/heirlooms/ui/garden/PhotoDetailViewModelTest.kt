package digital.heirlooms.ui.garden

import androidx.lifecycle.SavedStateHandle
import digital.heirlooms.api.HeirloomsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val server = MockWebServer()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        server.start()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    // MockWebServer returns "http://localhost:PORT/" (trailing slash).
    // HeirloomsApi prepends baseUrl to "/api/..." paths, so strip the trailing slash.
    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    @Test
    fun tagInput_survives_savedstate_round_trip() {
        val handle = SavedStateHandle()
        val vm = PhotoDetailViewModel(handle)
        vm.tagInput = "family"

        val restored = PhotoDetailViewModel(handle)
        assertEquals("family", restored.tagInput)
    }

    @Test
    fun trackView_fires_exactly_once_for_multiple_calls() = runTest {
        repeat(5) { server.enqueue(MockResponse().setBody("{}").setResponseCode(200)) }
        val api = HeirloomsApi(baseUrl = baseUrl(), apiKey = "test")
        val vm = PhotoDetailViewModel(SavedStateHandle())

        // Call trackView three times — only the first should hit the server.
        vm.trackView(api, "upload-123")
        vm.trackView(api, "upload-123")
        vm.trackView(api, "upload-123")

        // Advance the test dispatcher to let the viewModelScope coroutine launch,
        // then wait for the real IO work to complete (MockWebServer is on a real thread).
        dispatcher.scheduler.advanceUntilIdle()
        val request = server.takeRequest(5, TimeUnit.SECONDS)

        // Exactly one request should have arrived.
        assertEquals(1, server.requestCount)
        // No second request — takeRequest with 0 timeout returns null if nothing arrived.
        assertEquals(null, server.takeRequest(0, TimeUnit.MILLISECONDS))
    }

    @Test
    fun trackView_targets_correct_endpoint() = runTest {
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        val api = HeirloomsApi(baseUrl = baseUrl(), apiKey = "test")
        val vm = PhotoDetailViewModel(SavedStateHandle())

        vm.trackView(api, "abc-def")
        dispatcher.scheduler.advanceUntilIdle()
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!

        assertEquals("/api/content/uploads/abc-def/view", request.path)
    }
}
