package tools.mo3ta.fitit.ui.videosplitter

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.roundToLong

data class VideoChunk(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val index: Int,
    val fileSizeBytes: Long = 0L
)

/** How the video is divided into clips. */
enum class SplitMode {
    /** Every clip has the same fixed duration (with a small overlap). */
    FIXED,
    /** The user supplies explicit split points, e.g. 5, 8, 12. */
    CUSTOM
}

class VideoSplitterViewModel(application: Application) : AndroidViewModel(application) {

    var selectedVideoUri by mutableStateOf<Uri?>(null)
        private set
    var videoDurationMs by mutableStateOf(0L)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var chunks by mutableStateOf<List<VideoChunk>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var savedChunkIndices by mutableStateOf<Set<Int>>(emptySet())
        private set
    var videoFileSizeBytes by mutableStateOf(0L)
        private set
    var splitMode by mutableStateOf(SplitMode.FIXED)
        private set
    // Fixed-duration mode: clip length in seconds (decimals allowed), held as the
    // raw text the user typed so values like "2.5" round-trip cleanly.
    var fixedSizeInput by mutableStateOf(formatSeconds(DEFAULT_CHUNK_SIZE_MS))
        private set
    // Custom mode: comma-separated split points in seconds, e.g. "5, 8, 12".
    var customTimesInput by mutableStateOf("")
        private set

    val isDurationValid: Boolean
        get() = videoDurationMs in 1..MAX_DURATION_MS

    /** Parsed fixed clip length in milliseconds, or null when the input is invalid. */
    val parsedChunkSizeMs: Long?
        get() {
            val seconds = fixedSizeInput.trim().toDoubleOrNull() ?: return null
            if (seconds <= 0.0) return null
            return (seconds * 1000.0).roundToLong()
        }

    /** Parsed custom split points in milliseconds, or null when the input is invalid. */
    val parsedCustomTimesMs: List<Long>?
        get() = parseSplitTimes(customTimesInput)

    /** True when the fixed-size field holds text that cannot produce a valid clip length. */
    val fixedSizeFieldError: Boolean
        get() {
            if (fixedSizeInput.isBlank()) return false
            val ms = parsedChunkSizeMs ?: return true
            return ms !in CHUNK_SIZE_MIN_MS..CHUNK_SIZE_MAX_MS
        }

    /** True when the custom-times field holds text that cannot produce valid split points. */
    val customTimesFieldError: Boolean
        get() {
            if (customTimesInput.isBlank()) return false
            val times = parseSplitTimes(customTimesInput) ?: return true
            if (times.isEmpty()) return true
            return videoDurationMs > 0 && times.any { it > videoDurationMs }
        }

    val isSplitEnabled: Boolean
        get() = selectedVideoUri != null && isDurationValid && !isProcessing && when (splitMode) {
            SplitMode.FIXED -> {
                val ms = parsedChunkSizeMs
                ms != null && ms in CHUNK_SIZE_MIN_MS..CHUNK_SIZE_MAX_MS && videoDurationMs >= ms
            }
            SplitMode.CUSTOM -> {
                val times = parsedCustomTimesMs
                times != null && times.isNotEmpty() && times.all { it in 1..videoDurationMs }
            }
        }

    fun updateSplitMode(mode: SplitMode) {
        if (splitMode != mode) {
            splitMode = mode
            chunks = emptyList()
            errorMessage = null
        }
    }

    fun updateFixedSizeInput(input: String) {
        fixedSizeInput = input
        chunks = emptyList()
    }

    fun updateCustomTimesInput(input: String) {
        customTimesInput = input
        chunks = emptyList()
    }

    fun onVideoSelected(uri: Uri, durationMs: Long) {
        selectedVideoUri = uri
        videoDurationMs = durationMs
        chunks = emptyList()
        errorMessage = null
        savedChunkIndices = emptySet()
        videoFileSizeBytes = 0L
        viewModelScope.launch(Dispatchers.IO) {
            val size = readFileSize(uri)
            withContext(Dispatchers.Main) { videoFileSizeBytes = size }
        }
    }

    fun split() {
        val uri = selectedVideoUri ?: return
        val context = getApplication<Application>()

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            chunks = emptyList()

            try {
                AnalyticsManager.trackVideoSplitStarted(videoDurationMs)
            } catch (_: Exception) {}

            try {
                val outputDir = File(context.cacheDir, "video_chunks").also { it.mkdirs() }
                val ranges = when (splitMode) {
                    SplitMode.FIXED ->
                        calculateChunks(videoDurationMs, parsedChunkSizeMs ?: DEFAULT_CHUNK_SIZE_MS)
                    SplitMode.CUSTOM ->
                        calculateChunksFromTimes(videoDurationMs, parsedCustomTimesMs ?: emptyList())
                }
                chunks = withContext(Dispatchers.IO) {
                    ranges.mapIndexed { i, range ->
                        val outputFile = File(outputDir, "chunk_${range.index}.mp4")
                        extractSegment(context, uri, range.startMs, range.endMs, outputFile)
                        withContext(Dispatchers.Main) {
                            progress = (i + 1).toFloat() / ranges.size
                        }
                        VideoChunk(outputFile, range.startMs, range.endMs, range.index, outputFile.length())
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveChunk(context: Context, chunk: VideoChunk) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "fitit_chunk_${chunk.index}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Fitit")
                    }
                    val insertUri = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: throw IOException("Failed to create MediaStore entry")
                    context.contentResolver.openOutputStream(insertUri)?.use { os ->
                        chunk.file.inputStream().use { it.copyTo(os) }
                    } ?: throw IOException("Failed to open output stream")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "Fitit"
                    ).also { it.mkdirs() }
                    val dest = File(dir, "fitit_chunk_${chunk.index}.mp4")
                    chunk.file.copyTo(dest, overwrite = true)
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), null, null
                    )
                }
                withContext(Dispatchers.Main) {
                    savedChunkIndices = savedChunkIndices + chunk.index
                    try { AnalyticsManager.trackVideoChunkSaved() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: getApplication<Application>().getString(R.string.video_splitter_error_generic)
                }
            }
        }
    }

    fun shareChunk(context: Context, chunk: VideoChunk) {
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", chunk.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
        try { AnalyticsManager.trackVideoChunkShared() } catch (_: Exception) {}
    }

    fun previewChunk(context: Context, chunk: VideoChunk) {
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", chunk.file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            try { AnalyticsManager.trackVideoChunkPreviewed() } catch (_: Exception) {}
        } catch (_: android.content.ActivityNotFoundException) {
            errorMessage = getApplication<Application>().getString(R.string.video_splitter_no_player)
        }
    }

    private fun readFileSize(uri: Uri): Long = try {
        getApplication<Application>().contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0) cursor.getLong(idx) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }
}

private fun extractSegment(
    context: Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    output: File
) {
    val rotation: Int
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        rotation = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )?.toIntOrNull() ?: 0
    } finally {
        retriever.release()
    }

    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }

            muxer.setOrientationHint(rotation)
            muxer.start()
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val muxerTrack = trackMap[trackIndex] ?: run { extractor.advance(); continue }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endMs * 1000L) break

                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.offset = 0
                bufferInfo.presentationTimeUs = sampleTimeUs - (startMs * 1000L)
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
        } finally {
            muxer.release()
        }
    } finally {
        extractor.release()
    }
}
