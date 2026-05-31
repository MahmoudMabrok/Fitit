package tools.mo3ta.fitit.ui.audioenhancer

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import java.io.File
import java.io.IOException

class AudioEnhancerViewModel(application: Application) : AndroidViewModel(application) {

    var selectedAudioUri by mutableStateOf<Uri?>(null)
        private set
    var audioDisplayName by mutableStateOf<String?>(null)
        private set
    var audioFileSizeBytes by mutableStateOf(0L)
        private set
    var level by mutableStateOf(AudioEnhancementLevel.STANDARD)
        private set
    var useAiDenoise by mutableStateOf(false)
        private set
    /**
     * How many enhancement passes to run. Each pass feeds its output back in as the
     * input of the next one, so a batch size of 3 enhances the audio three times in a row.
     */
    var batchSize by mutableStateOf(1)
        private set
    /** 1-based index of the pass currently running (0 when idle). Drives the batch progress label. */
    var batchCurrentPass by mutableStateOf(0)
        private set
    var aiFellBack by mutableStateOf(false)
        private set
    var isPreviewPlaying by mutableStateOf(false)
        private set
    var previewSource by mutableStateOf(PreviewSource.ENHANCED)
        private set
    var isProcessing by mutableStateOf(false)
        private set

    private var previewPlayer: MediaPlayer? = null
    var progress by mutableStateOf(0f)
        private set
    var resultFile by mutableStateOf<File?>(null)
        private set
    var resultSizeBytes by mutableStateOf(0L)
        private set
    var isSaved by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isEnhanceEnabled: Boolean
        get() = selectedAudioUri != null && !isProcessing

    /** Whether the bundled DTLN models make the AI denoise engine usable here. */
    val isAiEngineAvailable: Boolean
        get() = MlAudioDenoiser.isAvailable(getApplication<Application>())

    fun changeAiDenoise(enabled: Boolean) {
        if (isProcessing) return
        useAiDenoise = enabled
        resetResult()
    }

    fun onAudioSelected(uri: Uri) {
        selectedAudioUri = uri
        resultFile = null
        resultSizeBytes = 0L
        isSaved = false
        errorMessage = null
        progress = 0f
        audioDisplayName = null
        audioFileSizeBytes = 0L
        viewModelScope.launch(Dispatchers.IO) {
            val (name, size) = readFileMeta(uri)
            withContext(Dispatchers.Main) {
                audioDisplayName = name
                audioFileSizeBytes = size
            }
        }
    }

    fun changeLevel(newLevel: AudioEnhancementLevel) {
        if (isProcessing || newLevel == level) return
        level = newLevel
        resetResult()
    }

    fun changeBatchSize(newSize: Int) {
        if (isProcessing) return
        val clamped = newSize.coerceIn(1, BATCH_SIZES.last())
        if (clamped == batchSize) return
        batchSize = clamped
        resetResult()
    }

    fun enhance() {
        val uri = selectedAudioUri ?: return
        val context = getApplication<Application>()
        val selectedLevel = level
        val ai = useAiDenoise
        val passes = batchSize.coerceAtLeast(1)

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            aiFellBack = false
            resetResult()
            batchCurrentPass = 1

            try { AnalyticsManager.trackAudioEnhanceStarted(selectedLevel.name, ai) } catch (_: Exception) {}

            try {
                val result = withContext(Dispatchers.IO) {
                    // mutableStateOf writes are snapshot-thread-safe, so the progress
                    // callback can update directly from this IO thread.
                    var lastFile: File? = null
                    var fellBack = false
                    for (pass in 0 until passes) {
                        batchCurrentPass = pass + 1
                        // First pass reads the picked source; later passes feed the
                        // previous pass's output back in as the new input.
                        val cycle = if (pass == 0) {
                            AudioEnhancer.enhance(context, uri, selectedLevel, useAi = ai) { p ->
                                progress = (pass + p) / passes
                            }
                        } else {
                            AudioEnhancer.enhance(context, lastFile!!, selectedLevel, useAi = ai) { p ->
                                progress = (pass + p) / passes
                            }
                        }
                        // Drop the now-superseded intermediate output to keep the cache tidy.
                        lastFile?.let { prev -> runCatching { prev.delete() } }
                        lastFile = cycle.file
                        if (cycle.aiFellBack) fellBack = true
                    }
                    AudioEnhancer.EnhanceResult(lastFile!!, fellBack)
                }
                resultFile = result.file
                resultSizeBytes = result.file.length()
                aiFellBack = result.aiFellBack
                try { AnalyticsManager.trackAudioEnhanceCompleted(selectedLevel.name, ai && !result.aiFellBack) } catch (_: Exception) {}
            } catch (e: Exception) {
                errorMessage = mapError(e)
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveResult(context: Context) {
        val file = resultFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val displayName = "zaki_enhanced_${System.currentTimeMillis()}.m4a"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Zaki")
                    }
                    val insertUri = context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: throw IOException("Failed to create MediaStore entry")
                    context.contentResolver.openOutputStream(insertUri)?.use { os ->
                        file.inputStream().use { it.copyTo(os) }
                    } ?: throw IOException("Failed to open output stream")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        "Zaki"
                    ).also { it.mkdirs() }
                    val dest = File(dir, displayName)
                    file.copyTo(dest, overwrite = true)
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), arrayOf("audio/mp4"), null
                    )
                }
                withContext(Dispatchers.Main) {
                    isSaved = true
                    try { AnalyticsManager.trackAudioEnhanceSaved() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message
                        ?: getApplication<Application>().getString(R.string.audio_enhancer_error_generic)
                }
            }
        }
    }

    fun shareResult(context: Context) {
        val file = resultFile ?: return
        val fileUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
        try { AnalyticsManager.trackAudioEnhanceShared() } catch (_: Exception) {}
    }

    /**
     * Switch the A/B preview between the original source and the enhanced result.
     * If something is already playing, the newly-selected track starts playing so
     * users can compare them back-to-back.
     */
    fun selectPreviewSource(context: Context, source: PreviewSource) {
        if (source == previewSource) return
        val wasPlaying = isPreviewPlaying
        releasePreview()
        previewSource = source
        if (wasPlaying) togglePreview(context)
    }

    /** Toggle in-app playback of the currently-selected A/B preview track. */
    fun togglePreview(context: Context) {
        val player = previewPlayer
        if (player != null && player.isPlaying) {
            player.pause()
            isPreviewPlaying = false
            return
        }
        if (player != null) {
            player.start()
            isPreviewPlaying = true
            return
        }
        try {
            previewPlayer = MediaPlayer().apply {
                when (previewSource) {
                    PreviewSource.ENHANCED -> {
                        val file = resultFile ?: return
                        setDataSource(file.absolutePath)
                    }
                    PreviewSource.ORIGINAL -> {
                        val uri = selectedAudioUri ?: return
                        setDataSource(context, uri)
                    }
                }
                setOnCompletionListener {
                    isPreviewPlaying = false
                    it.seekTo(0)
                }
                prepare()
                start()
            }
            isPreviewPlaying = true
            try { AnalyticsManager.trackAudioEnhancePreviewed(previewSource.name) } catch (_: Exception) {}
        } catch (_: Exception) {
            releasePreview()
        }
    }

    private fun releasePreview() {
        previewPlayer?.let { runCatching { it.release() } }
        previewPlayer = null
        isPreviewPlaying = false
    }

    override fun onCleared() {
        super.onCleared()
        releasePreview()
    }

    private fun resetResult() {
        releasePreview()
        previewSource = PreviewSource.ENHANCED
        resultFile = null
        resultSizeBytes = 0L
        isSaved = false
        progress = 0f
        aiFellBack = false
        batchCurrentPass = 0
    }

    private fun mapError(e: Exception): String {
        val context = getApplication<Application>()
        return when (e.message) {
            "NO_AUDIO_TRACK" -> context.getString(R.string.audio_enhancer_error_no_audio)
            "TOO_LONG" -> context.getString(R.string.audio_enhancer_error_too_long)
            else -> context.getString(R.string.audio_enhancer_error_generic)
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

    companion object {
        /** Selectable batch sizes (number of chained enhancement passes). */
        val BATCH_SIZES = listOf(1, 2, 3, 4, 5)
    }
}

/** Which track the A/B preview is playing: the original source or the enhanced result. */
enum class PreviewSource { ORIGINAL, ENHANCED }
