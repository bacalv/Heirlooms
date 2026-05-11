package digital.heirlooms.ui.capsules

import digital.heirlooms.api.HeirloomsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CapsuleCreateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun makeApi() = HeirloomsApi(
        baseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "tok",
    )

    private fun makeCapsuleJson(id: String = "cap-1") = """
        {"id":"$id","shape":"open","state":"open","created_at":"2026-01-01T00:00:00Z",
         "updated_at":"2026-01-01T00:00:00Z","unlock_at":"2030-01-01T08:00:00+00:00",
         "recipients":["Alice"],"uploads":[],"message":""}
    """.trimIndent()

    // ── Test 9: Submit valid fields → capsule created ────────────────────────

    @Test
    fun submit_withValidFields_createsCapsuleAndEmitsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(makeCapsuleJson("cap-99")))

        val vm = CapsuleCreateViewModel()
        vm.recipient.value = "Alice"
        vm.unlockDate.value = LocalDate.now().plusYears(1)

        vm.submit(makeApi())
        dispatcher.scheduler.advanceUntilIdle()
        server.takeRequest(5, TimeUnit.SECONDS)
        Thread.sleep(300)  // allow OkHttp to process response on IO thread
        dispatcher.scheduler.advanceUntilIdle()

        val result = vm.result.value
        assertTrue("Expected Success but got $result", result is CapsuleCreateResult.Success)
        assertEquals("cap-99", (result as CapsuleCreateResult.Success).capsuleId)
    }

    // ── Test 10: Submit with no recipient → validation error ──────────────────

    @Test
    fun submit_withEmptyRecipient_emitsValidationErrorWithoutApiCalls() = runTest {
        val vm = CapsuleCreateViewModel()
        vm.recipient.value = ""
        vm.unlockDate.value = LocalDate.now().plusYears(1)

        vm.submit(makeApi())
        dispatcher.scheduler.advanceUntilIdle()

        val result = vm.result.value
        assertTrue("Expected ValidationError but got $result", result is CapsuleCreateResult.ValidationError)
        assertTrue((result as CapsuleCreateResult.ValidationError).message.contains("recipient"))
        assertEquals(0, server.requestCount)
    }

    // ── Test 11: Submit with past unlock date → validation error ──────────────

    @Test
    fun submit_withPastUnlockDate_emitsValidationError() = runTest {
        val vm = CapsuleCreateViewModel()
        vm.recipient.value = "Bob"
        vm.unlockDate.value = LocalDate.now().minusDays(1)

        vm.submit(makeApi())
        dispatcher.scheduler.advanceUntilIdle()

        val result = vm.result.value
        assertTrue("Expected ValidationError but got $result", result is CapsuleCreateResult.ValidationError)
        assertTrue((result as CapsuleCreateResult.ValidationError).message.contains("future"))
    }

    @Test
    fun submit_withNullUnlockDate_emitsValidationError() = runTest {
        val vm = CapsuleCreateViewModel()
        vm.recipient.value = "Carol"
        vm.unlockDate.value = null

        vm.submit(makeApi())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.result.value is CapsuleCreateResult.ValidationError)
    }

    // ── Test 12: Submit fails on 500 → SubmitError ───────────────────────────

    @Test
    fun submit_serverReturns500_emitsSubmitErrorAndClearsIsSubmitting() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal error"))

        val vm = CapsuleCreateViewModel()
        vm.recipient.value = "Dave"
        vm.unlockDate.value = LocalDate.now().plusYears(1)

        vm.submit(makeApi())
        dispatcher.scheduler.advanceUntilIdle()
        server.takeRequest(5, TimeUnit.SECONDS)
        Thread.sleep(300)
        dispatcher.scheduler.advanceUntilIdle()

        val result = vm.result.value
        assertTrue("Expected SubmitError but got $result", result is CapsuleCreateResult.SubmitError)
        assertEquals(false, vm.isSubmitting.value)
    }

    // ── Test 13: Pre-selected upload ID carried into submit ──────────────────

    @Test
    fun submit_withPreSelectedUploadId_includesItInRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(makeCapsuleJson()))

        val vm = CapsuleCreateViewModel()
        vm.recipient.value = "Eve"
        vm.unlockDate.value = LocalDate.now().plusYears(1)
        vm.setPreSelectedUpload("upload-from-share")

        vm.submit(makeApi())
        dispatcher.scheduler.advanceUntilIdle()
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        Thread.sleep(300)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("Request body should contain the pre-selected upload ID",
            request!!.body.readUtf8().contains("upload-from-share"))
    }
}
