package tools.mo3ta.fitit.ui.videosplitter

import android.app.Application
import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class VideoSplitterViewModelTest {

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
}
