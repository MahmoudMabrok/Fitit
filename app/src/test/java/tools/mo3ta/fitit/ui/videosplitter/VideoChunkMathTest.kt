package tools.mo3ta.fitit.ui.videosplitter

import org.junit.Assert.*
import org.junit.Test

class VideoChunkMathTest {

    @Test
    fun `calculateChunks returns 2 chunks for 60s video`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(2, chunks.size)
    }

    @Test
    fun `calculateChunks first chunk starts at 0 and ends at 32s`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(32_000L, chunks[0].endMs)
    }

    @Test
    fun `calculateChunks second chunk starts at 30s`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(30_000L, chunks[1].startMs)
    }

    @Test
    fun `calculateChunks last chunk end clamped to video duration`() {
        val chunks = calculateChunks(55_000L)
        assertEquals(55_000L, chunks.last().endMs)
    }

    @Test
    fun `calculateChunks returns 10 chunks for 300s video`() {
        val chunks = calculateChunks(300_000L)
        assertEquals(10, chunks.size)
    }

    @Test
    fun `calculateChunks single chunk for video under 30s`() {
        val chunks = calculateChunks(20_000L)
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(20_000L, chunks[0].endMs)
    }

    @Test
    fun `calculateChunks chunk indices start at 1`() {
        val chunks = calculateChunks(60_000L)
        assertEquals(1, chunks[0].index)
        assertEquals(2, chunks[1].index)
    }

    @Test
    fun `calculateChunks consecutive chunks overlap by 2s`() {
        val chunks = calculateChunks(90_000L)
        // chunk[0].endMs - chunk[1].startMs = 32000 - 30000 = 2000
        assertEquals(2_000L, chunks[0].endMs - chunks[1].startMs)
    }

    @Test
    fun `calculateChunks throws for zero duration`() {
        try {
            calculateChunks(0L)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `calculateChunks does not produce zero length chunks`() {
        val chunks = calculateChunks(32_000L)
        chunks.forEach { chunk ->
            assertTrue("Chunk ${chunk.index} has zero or negative length", chunk.endMs > chunk.startMs)
        }
    }

    @Test
    fun `calculateChunks with 45s step produces correct count`() {
        val chunks = calculateChunks(90_000L, 45_000L)  // 90s / 45s = 2 chunks
        assertEquals(2, chunks.size)
    }

    @Test
    fun `calculateChunks with custom step overlaps by 2s`() {
        val chunks = calculateChunks(90_000L, 45_000L)
        // chunk[0] end - chunk[1] start = 47000 - 45000 = 2000
        assertEquals(2_000L, chunks[0].endMs - chunks[1].startMs)
    }

    @Test
    fun `formatFileSize returns KB for bytes under 1MB`() {
        assertEquals("512.0 KB", formatFileSize(524_288L))
    }

    @Test
    fun `formatFileSize returns MB for bytes at or over 1MB`() {
        assertEquals("1.0 MB", formatFileSize(1_048_576L))
    }

    @Test
    fun `formatFileSize rounds to one decimal`() {
        assertEquals("4.2 MB", formatFileSize(4_404_019L))
    }

    @Test
    fun `formatFileSize returns 0 point 0 KB for zero bytes`() {
        assertEquals("0.0 KB", formatFileSize(0L))
    }
}
