package tools.mo3ta.fitit.ui.videosplitter

import android.app.Application
import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoSplitterViewModelTest {

    @Before
    fun setup() {
        // viewModelScope is backed by Dispatchers.Main, which is unavailable in
        // plain JVM unit tests unless we install a test dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): VideoSplitterViewModel {
        val application = mockk<Application>(relaxed = true)
        return VideoSplitterViewModel(application)
    }

    @Test
    fun `isSplitEnabled is false when no video selected`() {
        val vm = createViewModel()
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is false when duration exceeds 5 minutes`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 360_000L)  // 6 min
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is true for valid video under 5 minutes`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 120_000L)  // 2 min
        assertTrue(vm.isSplitEnabled)
    }

    @Test
    fun `isDurationValid is false for 0ms`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 0L)
        assertFalse(vm.isDurationValid)
    }

    @Test
    fun `isDurationValid is true for exactly 300s`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 300_000L)
        assertTrue(vm.isDurationValid)
    }

    @Test
    fun `onVideoSelected resets previous chunks and error`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 60_000L)
        vm.onVideoSelected(uri, 90_000L)
        assertTrue(vm.chunks.isEmpty())
        assertNull(vm.errorMessage)
    }

    @Test
    fun `isSplitEnabled is false when video shorter than chunk size`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 20_000L)  // 20s video
        vm.updateFixedSizeInput("30")        // 30s chunk
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is true when video equals chunk size`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 30_000L)  // 30s video
        vm.updateFixedSizeInput("30")        // 30s chunk
        assertTrue(vm.isSplitEnabled)
    }

    @Test
    fun `fixed size accepts fractional seconds`() {
        val vm = createViewModel()
        vm.updateFixedSizeInput("2.5")
        assertEquals(2_500L, vm.parsedChunkSizeMs)
        assertFalse(vm.fixedSizeFieldError)
    }

    @Test
    fun `fixed size flags out-of-range and unparseable input`() {
        val vm = createViewModel()
        vm.updateFixedSizeInput("999")       // above 60s max
        assertTrue(vm.fixedSizeFieldError)
        vm.updateFixedSizeInput("abc")
        assertTrue(vm.fixedSizeFieldError)
        assertNull(vm.parsedChunkSizeMs)
    }

    @Test
    fun `fixed size enables split for half-second clips`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 10_000L)
        vm.updateFixedSizeInput("0.5")
        assertTrue(vm.isSplitEnabled)
    }

    @Test
    fun `setFixedSizeInput resets chunks`() {
        val vm = createViewModel()
        vm.updateFixedSizeInput("45")
        assertTrue(vm.chunks.isEmpty())
    }

    @Test
    fun `custom mode enables split for valid times within duration`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 20_000L)  // 20s video
        vm.updateSplitMode(SplitMode.CUSTOM)
        vm.updateCustomTimesInput("5, 8, 12")
        assertTrue(vm.isSplitEnabled)
        assertEquals(listOf(5_000L, 8_000L, 12_000L), vm.parsedCustomTimesMs)
    }

    @Test
    fun `custom mode disables split when a time exceeds duration`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 10_000L)  // 10s video
        vm.updateSplitMode(SplitMode.CUSTOM)
        vm.updateCustomTimesInput("5, 12")   // 12s is past the video end
        assertTrue(vm.customTimesFieldError)
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `custom mode flags unparseable times`() {
        val vm = createViewModel()
        vm.updateSplitMode(SplitMode.CUSTOM)
        vm.updateCustomTimesInput("5, abc")
        assertTrue(vm.customTimesFieldError)
    }

    @Test
    fun `setSplitMode resets chunks and switches mode`() {
        val vm = createViewModel()
        vm.updateSplitMode(SplitMode.CUSTOM)
        assertEquals(SplitMode.CUSTOM, vm.splitMode)
        assertTrue(vm.chunks.isEmpty())
    }

    @Test
    fun `onVideoSelected resets videoFileSizeBytes`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 60_000L)
        // After selection videoFileSizeBytes is reset to 0 (ContentResolver unavailable in unit test)
        assertEquals(0L, vm.videoFileSizeBytes)
    }
}
