package tools.mo3ta.fitit.ui.videosplitter

import kotlin.math.ceil

const val CHUNK_STEP_MS = 30_000L
const val CHUNK_DURATION_MS = 32_000L  // 30s content + 2s overlap
const val MAX_DURATION_MS = 300_000L   // 5 minutes

data class ChunkRange(val startMs: Long, val endMs: Long, val index: Int)

fun calculateChunks(durationMs: Long): List<ChunkRange> {
    val count = ceil(durationMs.toDouble() / CHUNK_STEP_MS).toInt().coerceAtLeast(1)
    return (0 until count).map { i ->
        ChunkRange(
            startMs = i * CHUNK_STEP_MS,
            endMs = minOf(i * CHUNK_STEP_MS + CHUNK_DURATION_MS, durationMs),
            index = i + 1
        )
    }
}
