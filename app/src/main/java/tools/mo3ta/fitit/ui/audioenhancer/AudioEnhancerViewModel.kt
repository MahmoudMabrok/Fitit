package tools.mo3ta.fitit.ui.audioenhancer

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
    var aiFellBack by mutableStateOf(false)
        private set
    var isProcessing by mutableStateOf(false)
        private set
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

    fun enhance() {
        val uri = selectedAudioUri ?: return
        val context = getApplication<Application>()
        val selectedLevel = level
        val ai = useAiDenoise

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            aiFellBack = false
            resetResult()

            try { AnalyticsManager.trackAudioEnhanceStarted(selectedLevel.name, ai) } catch (_: Exception) {}

            try {
                val result = withContext(Dispatchers.IO) {
                    // mutableStateOf writes are snapshot-thread-safe, so the progress
                    // callback can update directly from this IO thread.
                    AudioEnhancer.enhance(context, uri, selectedLevel, useAi = ai) { p -> progress = p }
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

    private fun resetResult() {
        resultFile = null
        resultSizeBytes = 0L
        isSaved = false
        progress = 0f
        aiFellBack = false
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
}
