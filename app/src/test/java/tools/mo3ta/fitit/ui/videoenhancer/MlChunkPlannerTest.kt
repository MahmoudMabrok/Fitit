package tools.mo3ta.fitit.ui.videoenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlChunkPlannerTest {

    @Test
    fun `default chunk size is 0_1s`() {
        assertEquals(100L, ML_CHUNK_MS)
    }

    @Test
    fun `one second splits into ten 0_1s windows`() {
        val windows = planMlChunks(1_000L)
        assertEquals(10, windows.size)
        assertEquals(0L, windows.first().startMs)
        assertEquals(1_000L, windows.last().endMs)
    }

    @Test
    fun `windows are consecutive and non-overlapping`() {
        val windows = planMlChunks(1_000L)
        for (i in 1 until windows.size) {
            assertEquals(windows[i - 1].endMs, windows[i].startMs)
        }
    }

    @Test
    fun `last window is clamped to the clip duration`() {
        val windows = planMlChunks(250L)
        assertEquals(3, windows.size)
        assertEquals(250L, windows.last().endMs)
        assertTrue(windows.last().endMs - windows.last().startMs <= ML_CHUNK_MS)
    }

    @Test
    fun `indices are contiguous from zero`() {
        val windows = planMlChunks(500L)
        windows.forEachIndexed { i, window -> assertEquals(i, window.index) }
    }

    @Test
    fun `clip shorter than one chunk yields a single window`() {
        val windows = planMlChunks(40L)
        assertEquals(1, windows.size)
        assertEquals(0L, windows.first().startMs)
        assertEquals(40L, windows.first().endMs)
    }

    @Test
    fun `custom chunk size is honoured`() {
        val windows = planMlChunks(1_000L, chunkMs = 500L)
        assertEquals(2, windows.size)
        assertEquals(500L, windows[0].endMs)
        assertEquals(500L, windows[1].startMs)
    }

    @Test
    fun `no window has zero or negative length`() {
        val windows = planMlChunks(1_050L)
        windows.forEach { assertTrue(it.endMs > it.startMs) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero duration throws`() {
        planMlChunks(0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero chunk size throws`() {
        planMlChunks(1_000L, chunkMs = 0L)
    }
}
