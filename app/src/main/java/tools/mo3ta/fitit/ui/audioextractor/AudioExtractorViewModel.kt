package tools.mo3ta.fitit.ui.audioextractor

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import java.io.File
import java.io.IOException

data class ExtractedAudio(
    val file: File,
    val format: AudioFormat,
    val fileSizeBytes: Long
)

class AudioExtractorViewModel(application: Application) : AndroidViewModel(application) {

    var selectedVideoUri by mutableStateOf<Uri?>(null)
        private set
    var videoDisplayName by mutableStateOf<String?>(null)
        private set
    var videoFileSizeBytes by mutableStateOf(0L)
        private set
    var selectedFormat by mutableStateOf(AudioFormat.M4A)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var result by mutableStateOf<ExtractedAudio?>(null)
        private set
    var isSaved by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Preview playback state for the extracted audio.
    var isPlaying by mutableStateOf(false)
        private set
    var playbackPositionMs by mutableStateOf(0)
        private set
    var playbackDurationMs by mutableStateOf(0)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var positionJob: Job? = null

    val isExtractEnabled: Boolean
        get() = selectedVideoUri != null && !isProcessing

    fun onVideoSelected(uri: Uri) {
        selectedVideoUri = uri
        releasePlayer()
        result = null
        isSaved = false
        errorMessage = null
        videoDisplayName = null
        videoFileSizeBytes = 0L
        viewModelScope.launch(Dispatchers.IO) {
            val (name, size) = readFileMeta(uri)
            withContext(Dispatchers.Main) {
                videoDisplayName = name
                videoFileSizeBytes = size
            }
        }
    }

    fun setFormat(format: AudioFormat) {
        if (format == selectedFormat) return
        selectedFormat = format
        releasePlayer()
        result = null
        isSaved = false
        errorMessage = null
    }

    fun extract() {
        val uri = selectedVideoUri ?: return
        val context = getApplication<Application>()
        val format = selectedFormat

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            releasePlayer()
            result = null
            isSaved = false

            try { AnalyticsManager.trackAudioExtractStarted(format.name) } catch (_: Exception) {}

            try {
                val extracted = withContext(Dispatchers.IO) {
                    val outputDir = File(context.cacheDir, "extracted_audio").also { it.mkdirs() }
                    val outputFile = File(outputDir, "audio.${format.extension}")
                    // mutableStateOf writes are snapshot-thread-safe, so the progress
                    // callback can update directly from this IO thread.
                    extractAudio(context, uri, format, outputFile) { p -> progress = p }
                    ExtractedAudio(outputFile, format, outputFile.length())
                }
                result = extracted
                try { AnalyticsManager.trackAudioExtractCompleted(format.name) } catch (_: Exception) {}
            } catch (e: Exception) {
                errorMessage = mapError(e)
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveResult(context: Context) {
        val audio = result ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val displayName = "fitit_audio_${System.currentTimeMillis()}.${audio.format.extension}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Audio.Media.MIME_TYPE, audio.format.mimeType)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Fitit")
                    }
                    val insertUri = context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: throw IOException("Failed to create MediaStore entry")
                    context.contentResolver.openOutputStream(insertUri)?.use { os ->
                        audio.file.inputStream().use { it.copyTo(os) }
                    } ?: throw IOException("Failed to open output stream")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        "Fitit"
                    ).also { it.mkdirs() }
                    val dest = File(dir, displayName)
                    audio.file.copyTo(dest, overwrite = true)
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), arrayOf(audio.format.mimeType), null
                    )
                }
                withContext(Dispatchers.Main) {
                    isSaved = true
                    try { AnalyticsManager.trackAudioSaved(audio.format.name) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message
                        ?: getApplication<Application>().getString(R.string.audio_extractor_error_generic)
                }
            }
        }
    }

    fun shareResult(context: Context) {
        val audio = result ?: return
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", audio.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = audio.format.mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
        try { AnalyticsManager.trackAudioShared(audio.format.name) } catch (_: Exception) {}
    }

    /** Toggles preview playback of the extracted audio, preparing the player lazily. */
    fun togglePlayback() {
        val audio = result ?: return
        val player = mediaPlayer ?: try {
            MediaPlayer().apply {
                setDataSource(audio.file.absolutePath)
                setOnCompletionListener {
                    this@AudioExtractorViewModel.isPlaying = false
                    this@AudioExtractorViewModel.playbackPositionMs = 0
                    positionJob?.cancel()
                    seekTo(0)
                }
                prepare()
                this@AudioExtractorViewModel.playbackDurationMs = duration
            }.also { mediaPlayer = it }
        } catch (e: Exception) {
            errorMessage = mapError(e)
            return
        }

        if (player.isPlaying) {
            player.pause()
            isPlaying = false
            positionJob?.cancel()
        } else {
            player.start()
            isPlaying = true
            startPositionUpdates()
        }
    }

    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        player.seekTo(positionMs)
        playbackPositionMs = positionMs
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                playbackPositionMs = mediaPlayer?.currentPosition ?: 0
                delay(100)
            }
        }
    }

    private fun releasePlayer() {
        positionJob?.cancel()
        positionJob = null
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        playbackPositionMs = 0
        playbackDurationMs = 0
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }

    private fun mapError(e: Exception): String {
        val context = getApplication<Application>()
        return if (e.message == "NO_AUDIO_TRACK") {
            context.getString(R.string.audio_extractor_no_audio)
        } else {
            e.message ?: context.getString(R.string.audio_extractor_error_generic)
        }
    }

    private fun readFileMeta(uri: Uri): Pair<String?, Long> = try {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                name to size
            } else null to 0L
        } ?: (null to 0L)
    } catch (_: Exception) {
        null to 0L
    }
}
