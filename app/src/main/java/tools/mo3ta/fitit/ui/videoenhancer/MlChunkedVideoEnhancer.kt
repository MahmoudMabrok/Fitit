package tools.mo3ta.fitit.ui.videoenhancer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tools.mo3ta.fitit.ui.mediamerger.MediaType
import tools.mo3ta.fitit.ui.mediamerger.mergeMedia
import tools.mo3ta.fitit.ui.videosplitter.extractSegment
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil

/** One slice of the source timeline (default 0.1s) handed to the ML enhancer. */
data class MlChunkWindow(val startMs: Long, val endMs: Long, val index: Int)

/**
 * Splits [durationMs] into consecutive, non-overlapping windows of [chunkMs] each; the final window
 * is clamped to the clip end. Pure (no Android dependencies) so the windowing maths can be unit-tested
 * on the JVM.
 */
fun planMlChunks(durationMs: Long, chunkMs: Long = ML_CHUNK_MS): List<MlChunkWindow> {
    require(durationMs > 0) { "durationMs must be positive, got $durationMs" }
    require(chunkMs > 0) { "chunkMs must be positive, got $chunkMs" }
    val count = ceil(durationMs.toDouble() / chunkMs).toInt().coerceAtLeast(1)
    return (0 until count).map { i ->
        MlChunkWindow(
            startMs = i * chunkMs,
            endMs = minOf((i + 1) * chunkMs, durationMs),
            index = i,
        )
    }.filter { it.endMs > it.startMs }
}

/**
 * Chunked driver around [MlVideoEnhancer] that reuses the app's existing split and merge logic:
 *
 *  1. **Split** — the source is cut into [chunkMs] slices (default [ML_CHUNK_MS], 0.1s) with the video
 *     splitter's [extractSegment].
 *  2. **Work** — every slice is super-resolved by [MlVideoEnhancer], sharing one TFLite model and one
 *     encode thread across all slices so the (expensive) model load happens only once.
 *  3. **Merge** — the enhanced slices are stitched back into one file with the media merger's
 *     [mergeMedia], using a zero gap so the slices concatenate without inflating the duration.
 *
 * Known limitation: [extractSegment] stream-copies starting from the previous keyframe, so when the
 * source's keyframe interval is longer than [chunkMs] consecutive slices overlap and the merged
 * result can repeat a few frames at slice boundaries. Acceptable for this proof-of-concept engine.
 */
object MlChunkedVideoEnhancer {

    /** Fraction of the progress bar devoted to slicing + enhancing; the remainder covers the merge. */
    private const val ENHANCE_FRACTION = 0.95f

    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun enhance(
        context: Context,
        uri: Uri,
        output: File,
        level: EnhancementLevel,
        speedMode: MlSpeedMode = MlSpeedMode.BALANCED,
        chunkMs: Long = ML_CHUNK_MS,
        onProgress: (Float) -> Unit = {},
    ) = coroutineScope {
        val durationMs = readDurationMs(context, uri)
        val windows = if (durationMs > 0) planMlChunks(durationMs, chunkMs) else emptyList()

        // Unknown duration, or a clip no longer than a single slice: nothing to split, so enhance the
        // whole thing in one pass (this also owns the model load itself).
        if (windows.size <= 1) {
            MlVideoEnhancer.enhance(context, uri, output, level, speedMode, onProgress = onProgress)
            return@coroutineScope
        }

        val workDir = File(context.cacheDir, "ml_chunks").apply {
            deleteRecursively()
            mkdirs()
        }
        // One encode thread + one model reused by every slice. The interpreter is thread-affine, so it
        // must be created on the same thread it will run on — hence the explicit dispatcher hop.
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ml-chunk-encode") }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val model = withContext(dispatcher) { MlSuperResolution.create(context) }
                ?: error("ML super-resolution model unavailable")
            val enhancedSlices = ArrayList<File>(windows.size)
            try {
                windows.forEachIndexed { i, window ->
                    // 1. Split this slice out of the source with the video splitter's logic.
                    val rawSlice = File(workDir, "raw_${window.index}.mp4")
                    extractSegment(context, uri, window.startMs, window.endMs, rawSlice)

                    // 2. Super-resolve the slice, reusing the shared model + encode thread.
                    val enhancedSlice = File(workDir, "enh_${window.index}.mp4")
                    MlVideoEnhancer.enhance(
                        context = context,
                        uri = Uri.fromFile(rawSlice),
                        output = enhancedSlice,
                        level = level,
                        speedMode = speedMode,
                        sharedModel = model,
                        sharedEncodeDispatcher = dispatcher,
                    ) { sliceProgress ->
                        val overall = (i + sliceProgress) / windows.size * ENHANCE_FRACTION
                        onProgress(overall.coerceIn(0f, ENHANCE_FRACTION))
                    }

                    rawSlice.delete()
                    enhancedSlices += enhancedSlice
                }
            } finally {
                withContext(dispatcher) { runCatching { model.close() } }
            }

            // 3. Stitch the enhanced slices together with the media merger's logic (gapless).
            mergeMedia(
                context = context,
                uris = enhancedSlices.map { Uri.fromFile(it) },
                mediaType = MediaType.VIDEO,
                output = output,
                gapUs = 0L,
            ) { mergeProgress ->
                onProgress((ENHANCE_FRACTION + mergeProgress * (1f - ENHANCE_FRACTION)).coerceIn(0f, 1f))
            }
        } finally {
            executor.shutdown()
            runCatching { workDir.deleteRecursively() }
        }
        onProgress(1f)
    }

    private fun readDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
