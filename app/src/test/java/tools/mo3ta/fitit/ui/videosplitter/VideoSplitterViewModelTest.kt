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
        vm.setChunkSize(30)               // 30s chunk
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is true when video equals chunk size`() {
        val vm = createViewModel()
        val uri = mockk<Uri>()
        vm.onVideoSelected(uri, 30_000L)  // 30s video
        vm.setChunkSize(30)               // 30s chunk
        assertTrue(vm.isSplitEnabled)
    }

    @Test
    fun `setChunkSize clamps to min and max`() {
        val vm = createViewModel()
        vm.setChunkSize(5)
        assertEquals(CHUNK_SIZE_MIN_S, vm.chunkSizeSeconds)
        vm.setChunkSize(99)
        assertEquals(CHUNK_SIZE_MAX_S, vm.chunkSizeSeconds)
    }

    @Test
    fun `setChunkSize resets chunks`() {
        val vm = createViewModel()
        // chunks list should be empty after setChunkSize
        vm.setChunkSize(45)
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
