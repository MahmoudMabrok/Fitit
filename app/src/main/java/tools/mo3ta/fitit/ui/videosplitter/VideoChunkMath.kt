package tools.mo3ta.fitit.ui.videosplitter

import kotlin.math.ceil
import kotlin.math.roundToLong

const val CHUNK_OVERLAP_MS = 2_000L
const val MAX_DURATION_MS = 300_000L   // 5 minutes

// Fixed-duration mode clip-size bounds. Stored in milliseconds so fractional
// seconds (e.g. 0.5s, 2.5s) can be expressed exactly.
const val CHUNK_SIZE_MIN_MS = 500L     // 0.5 s
const val CHUNK_SIZE_MAX_MS = 60_000L  // 60 s
const val DEFAULT_CHUNK_SIZE_MS = 30_000L

data class ChunkRange(val startMs: Long, val endMs: Long, val index: Int)

/**
 * Fixed-duration mode: split a video of [durationMs] into clips that advance by
 * [chunkStepMs] and extend [CHUNK_OVERLAP_MS] past each step so consecutive
 * clips overlap. The last clip is clamped to the video duration.
 */
fun calculateChunks(durationMs: Long, chunkStepMs: Long = DEFAULT_CHUNK_SIZE_MS): List<ChunkRange> {
    require(durationMs > 0) { "durationMs must be positive, got $durationMs" }
    require(chunkStepMs > 0) { "chunkStepMs must be positive, got $chunkStepMs" }
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

/**
 * Parse a comma- (or whitespace-/semicolon-) separated list of split times,
 * expressed in seconds with optional fractional part (e.g. "5, 8.5, 12").
 * Returns the times in milliseconds, de-duplicated and sorted ascending, or
 * null when the input is blank or contains an unparseable / non-positive value.
 */
fun parseSplitTimes(input: String): List<Long>? {
    val tokens = input
        .split(',', ';', ' ', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    val timesMs = ArrayList<Long>(tokens.size)
    for (token in tokens) {
        val seconds = token.toDoubleOrNull() ?: return null
        if (seconds <= 0.0) return null
        timesMs += (seconds * 1000.0).roundToLong()
    }
    return timesMs.distinct().sorted()
}

/**
 * Custom-times mode: split a video of [durationMs] at the explicit cut points in
 * [timesMs] (ascending milliseconds). For times [t1, t2, … tN] the clips are
 * [0, t1], [t1, t2], … [t(N-1), tN] — N times produce N non-overlapping clips,
 * and any video past the last time is dropped. Times beyond [durationMs] are
 * clamped, and empty clips are removed.
 */
fun calculateChunksFromTimes(durationMs: Long, timesMs: List<Long>): List<ChunkRange> {
    require(durationMs > 0) { "durationMs must be positive, got $durationMs" }
    val boundaries = (listOf(0L) + timesMs.map { it.coerceIn(0L, durationMs) })
        .distinct()
        .sorted()
    val result = ArrayList<ChunkRange>()
    var index = 1
    for (i in 0 until boundaries.size - 1) {
        val start = boundaries[i]
        val end = boundaries[i + 1]
        if (end > start) {
            result += ChunkRange(start, end, index++)
        }
    }
    return result
}

/**
 * Format a millisecond duration as a seconds string, dropping the decimal when
 * the value is a whole number (30000 -> "30", 500 -> "0.5", 2500 -> "2.5").
 */
fun formatSeconds(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds == seconds.toLong().toDouble()) {
        seconds.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    return if (bytes < 1_048_576L) {
        String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }
}
