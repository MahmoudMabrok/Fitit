package tools.mo3ta.fitit.ui.mediamerger

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

/** A single media file queued for merging. */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long
)

/** Result of a successful merge. */
data class MergedMedia(
    val file: File,
    val sizeBytes: Long,
    val durationMs: Long,
    val mediaType: MediaType
)

class MediaMergerViewModel(application: Application) : AndroidViewModel(application) {

    var mediaType by mutableStateOf(MediaType.VIDEO)
        private set
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var result by mutableStateOf<MergedMedia?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isSaved by mutableStateOf(false)
        private set

    val isMergeEnabled: Boolean
        get() = canMerge(items.size, isProcessing)

    fun selectMediaType(type: MediaType) {
        if (type == mediaType) return
        mediaType = type
        items = emptyList()
        result = null
        errorMessage = null
        isSaved = false
    }

    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        result = null
        isSaved = false
        errorMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val existing = items.map { it.uri }.toSet()
            val newItems = uris.filter { it !in existing }.map { uri -> readMediaItem(context, uri) }
            withContext(Dispatchers.Main) {
                items = (items + newItems).take(MEDIA_MERGER_MAX_ITEMS)
            }
        }
    }

    fun moveUp(index: Int) {
        items = items.movedUp(index)
        invalidateResult()
    }

    fun moveDown(index: Int) {
        items = items.movedDown(index)
        invalidateResult()
    }

    fun removeItem(index: Int) {
        items = items.removedAt(index)
        invalidateResult()
    }

    fun clearAll() {
        items = emptyList()
        invalidateResult()
    }

    private fun invalidateResult() {
        result = null
        isSaved = false
        errorMessage = null
    }

    fun merge() {
        if (!isMergeEnabled) return
        val context = getApplication<Application>()
        val sources = items.map { it.uri }
        val type = mediaType

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            result = null
            isSaved = false

            try { AnalyticsManager.trackMediaMergeStarted(type.name, sources.size) } catch (_: Exception) {}

            try {
                val merged = withContext(Dispatchers.IO) {
                    val outputDir = File(context.cacheDir, "merged_media").also { it.mkdirs() }
                    val extension = if (type == MediaType.VIDEO) "mp4" else "m4a"
                    val output = File(outputDir, "merged_${System.currentTimeMillis()}.$extension")
                    mergeMedia(context, sources, type, output) { p ->
                        viewModelScope.launch(Dispatchers.Main) { progress = p }
                    }
                    val duration = items.sumOf { it.durationMs }
                    MergedMedia(output, output.length(), duration, type)
                }
                result = merged
                try { AnalyticsManager.trackMediaMergeCompleted(type.name) } catch (_: Exception) {}
            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.media_merger_error_generic)
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveResult(context: Context) {
        val merged = result ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isVideo = merged.mediaType == MediaType.VIDEO
                val displayName = "fitit_merged_${System.currentTimeMillis()}." +
                        if (isVideo) "mp4" else "m4a"
                val mime = if (isVideo) "video/mp4" else "audio/mp4"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val collection = if (isVideo)
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isVideo) "Movies/Fitit" else "Music/Fitit"
                        )
                    }
                    val insertUri = context.contentResolver.insert(collection, values)
                        ?: throw IOException("Failed to create MediaStore entry")
                    context.contentResolver.openOutputStream(insertUri)?.use { os ->
                        merged.file.inputStream().use { it.copyTo(os) }
                    } ?: throw IOException("Failed to open output stream")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(
                            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
                        ),
                        "Fitit"
                    ).also { it.mkdirs() }
                    val dest = File(dir, displayName)
                    merged.file.copyTo(dest, overwrite = true)
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), arrayOf(mime), null
                    )
                }
                withContext(Dispatchers.Main) {
                    isSaved = true
                    try { AnalyticsManager.trackMediaMergeSaved() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message
                        ?: getApplication<Application>().getString(R.string.media_merger_error_generic)
                }
            }
        }
    }

    fun shareResult(context: Context) {
        val merged = result ?: return
        val mime = if (merged.mediaType == MediaType.VIDEO) "video/mp4" else "audio/mp4"
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", merged.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
        try { AnalyticsManager.trackMediaMergeShared() } catch (_: Exception) {}
    }

    fun previewResult(context: Context) {
        val merged = result ?: return
        val mime = if (merged.mediaType == MediaType.VIDEO) "video/mp4" else "audio/mp4"
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", merged.file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            errorMessage = getApplication<Application>().getString(R.string.media_merger_no_player)
        }
    }

    private fun readMediaItem(context: Context, uri: Uri): MediaItem {
        val name = readDisplayName(context, uri)
        val size = readFileSize(context, uri)
        val duration = readDurationMs(context, uri)
        return MediaItem(uri, name, duration, size)
    }

    private fun readDisplayName(context: Context, uri: Uri): String = try {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "media"
    } catch (_: Exception) { uri.lastPathSegment ?: "media" }

    private fun readFileSize(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0) cursor.getLong(idx) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }

    private fun readDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}

/** Small gap inserted between consecutive clips so timestamps stay strictly increasing. */
private const val CLIP_GAP_US = 30_000L

/**
 * Concatenates [uris] (all of the same [mediaType]) into a single MP4/M4A container at [output]
 * using stream-copy (no re-encoding). Inputs must share the same codec/format for the muxer to
 * accept their samples; mismatched inputs surface as an exception.
 */
private fun mergeMedia(
    context: Context,
    uris: List<Uri>,
    mediaType: MediaType,
    output: File,
    onProgress: (Float) -> Unit
) {
    val isVideo = mediaType == MediaType.VIDEO

    var rotation = 0
    if (isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uris.first())
            rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            rotation = 0
        } finally {
            retriever.release()
        }
    }

    // Find the largest sample across all inputs so the read buffer never overflows.
    var bufferSize = 1 * 1024 * 1024
    for (uri in uris) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val mis = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (mis > bufferSize) bufferSize = mis
                }
            }
        } catch (_: Exception) {
            // ignore; fall back to default buffer size
        } finally {
            extractor.release()
        }
    }

    val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var videoMuxTrack = -1
    var audioMuxTrack = -1

    try {
        // Register tracks once, using the first input's formats.
        val firstExtractor = MediaExtractor()
        try {
            firstExtractor.setDataSource(context, uris.first(), null)
            for (i in 0 until firstExtractor.trackCount) {
                val format = firstExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (isVideo && mime.startsWith("video/") && videoMuxTrack < 0) {
                    videoMuxTrack = muxer.addTrack(format)
                } else if (mime.startsWith("audio/") && audioMuxTrack < 0) {
                    audioMuxTrack = muxer.addTrack(format)
                }
            }
        } finally {
            firstExtractor.release()
        }

        if (videoMuxTrack < 0 && audioMuxTrack < 0) {
            throw IOException("No compatible tracks found")
        }

        if (isVideo) muxer.setOrientationHint(rotation)
        muxer.start()

        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        // A single timeline offset is applied to both audio and video tracks so they stay in
        // sync clip-to-clip instead of drifting apart.
        var offsetUs = 0L

        uris.forEachIndexed { fileIndex, uri ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)

                val trackToMux = mutableMapOf<Int, Int>()
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    val dest = when {
                        isVideo && mime.startsWith("video/") -> videoMuxTrack
                        mime.startsWith("audio/") -> audioMuxTrack
                        else -> -1
                    }
                    if (dest >= 0) {
                        trackToMux[i] = dest
                        extractor.selectTrack(i)
                    }
                }

                var maxPtsUs = 0L

                while (true) {
                    val trackIndex = extractor.sampleTrackIndex
                    if (trackIndex < 0) break
                    val muxTrack = trackToMux[trackIndex] ?: run { extractor.advance(); continue }

                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val sampleTimeUs = extractor.sampleTime

                    bufferInfo.offset = 0
                    bufferInfo.size = size
                    bufferInfo.presentationTimeUs = sampleTimeUs + offsetUs
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxTrack, buffer, bufferInfo)

                    if (sampleTimeUs > maxPtsUs) maxPtsUs = sampleTimeUs
                    extractor.advance()
                }

                // Push the timeline forward so the next clip starts after this one.
                offsetUs += maxPtsUs + CLIP_GAP_US
            } finally {
                extractor.release()
            }
            onProgress((fileIndex + 1).toFloat() / uris.size)
        }

        muxer.stop()
    } finally {
        muxer.release()
    }
}
