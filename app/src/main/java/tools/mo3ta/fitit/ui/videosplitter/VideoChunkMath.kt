package tools.mo3ta.fitit.ui.videosplitter

import kotlin.math.ceil

const val CHUNK_STEP_MS = 30_000L
const val CHUNK_OVERLAP_MS = 2_000L
const val CHUNK_DURATION_MS = CHUNK_STEP_MS + CHUNK_OVERLAP_MS
const val MAX_DURATION_MS = 300_000L   // 5 minutes
const val CHUNK_SIZE_MIN_S = 10
const val CHUNK_SIZE_MAX_S = 60
const val CHUNK_SIZE_STEP_S = 5

data class ChunkRange(val startMs: Long, val endMs: Long, val index: Int)

fun calculateChunks(durationMs: Long, chunkStepMs: Long = CHUNK_STEP_MS): List<ChunkRange> {
    require(durationMs > 0) { "durationMs must be positive, got $durationMs" }
    val chunkDurationMs = chunkStepMs + CHUNK_OVERLAP_MS
    val count = ceil(durationMs.toDouble() / chunkStepMs).toInt().coerceAtLeast(1)
    return (0 until count).map { i ->
        ChunkRange(
            startMs = i * chunkStepMs,
            endMs = minOf(i * chunkStepMs + chunkDurationMs, durationMs),
            index = i + 1
        )
    }.filter { it.endMs > it.startMs }
}

fun formatFileSize(bytes: Long): String {
    return if (bytes < 1_048_576L) {
        String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }
}
