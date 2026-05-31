package tools.mo3ta.fitit.ui.videoenhancer

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

class VideoEnhancerViewModel(application: Application) : AndroidViewModel(application) {

    var selectedVideoUri by mutableStateOf<Uri?>(null)
        private set
    var videoDurationMs by mutableStateOf(0L)
        private set
    var videoFileSizeBytes by mutableStateOf(0L)
        private set
    var level by mutableStateOf(EnhancementLevel.STANDARD)
        private set
    var useAiUpscale by mutableStateOf(false)
        private set
    var speedMode by mutableStateOf(MlSpeedMode.BALANCED)
        private set
    var aiFellBackToGl by mutableStateOf(false)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var enhancedFile by mutableStateOf<File?>(null)
        private set
    var enhancedFileSizeBytes by mutableStateOf(0L)
        private set
    var isSaved by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isDurationValid: Boolean
        get() = videoDurationMs in 1..ENHANCER_MAX_DURATION_MS

    val isEnhanceEnabled: Boolean
        get() = selectedVideoUri != null && isDurationValid && !isProcessing

    /** Whether a bundled TensorFlow Lite model makes the AI engine usable on this device. */
    val isAiEngineAvailable: Boolean
        get() = MlVideoEnhancer.isAvailable(getApplication<Application>())

    fun changeLevel(newLevel: EnhancementLevel) {
        if (isProcessing) return
        level = newLevel
        resetResult()
    }

    fun changeAiUpscale(enabled: Boolean) {
        if (isProcessing) return
        useAiUpscale = enabled
        resetResult()
    }

    fun changeSpeedMode(mode: MlSpeedMode) {
        if (isProcessing) return
        speedMode = mode
        resetResult()
    }

    fun onVideoSelected(uri: Uri, durationMs: Long) {
        selectedVideoUri = uri
        videoDurationMs = durationMs
        videoFileSizeBytes = 0L
        resetResult()
        errorMessage = null
        viewModelScope.launch(Dispatchers.IO) {
            val size = readFileSize(uri)
            withContext(Dispatchers.Main) { videoFileSizeBytes = size }
        }
    }

    fun enhance() {
        val uri = selectedVideoUri ?: return
        val context = getApplication<Application>()

        val requestedEngine = if (useAiUpscale) EnhanceEngine.ML else EnhanceEngine.GL

        viewModelScope.launch {
            isProcessing = true
            progress = 0f
            errorMessage = null
            aiFellBackToGl = false
            resetResult()

            try { AnalyticsManager.trackVideoEnhanceStarted(videoDurationMs, level.name) } catch (_: Exception) {}

            try {
                val outputDir = File(context.cacheDir, "enhanced_videos").also { it.mkdirs() }
                val outputFile = File(outputDir, "enhanced_${System.currentTimeMillis()}.mp4")
                val usedEngine = withContext(Dispatchers.IO) {
                    VideoEnhancer.enhance(context, uri, outputFile, level, requestedEngine, speedMode) { p ->
                        viewModelScope.launch(Dispatchers.Main) { progress = p }
                    }
                }
                aiFellBackToGl = requestedEngine == EnhanceEngine.ML && usedEngine == EnhanceEngine.GL
                enhancedFile = outputFile
                enhancedFileSizeBytes = outputFile.length()
                try { AnalyticsManager.trackVideoEnhanceCompleted(level.name) } catch (_: Exception) {}
            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.video_enhancer_error_generic)
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveEnhanced(context: Context) {
        val file = enhancedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "zaki_enhanced_${file.nameWithoutExtension}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Zaki")
                    }
                    val insertUri = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                    ) ?: throw IOException("Failed to create MediaStore entry")
                    context.contentResolver.openOutputStream(insertUri)?.use { os ->
                        file.inputStream().use { it.copyTo(os) }
                    } ?: throw IOException("Failed to open output stream")
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "Zaki",
                    ).also { it.mkdirs() }
                    val dest = File(dir, "zaki_enhanced_${file.name}")
                    file.copyTo(dest, overwrite = true)
                    @Suppress("DEPRECATION")
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), null, null,
                    )
                }
                withContext(Dispatchers.Main) {
                    isSaved = true
                    try { AnalyticsManager.trackVideoEnhanceSaved() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: context.getString(R.string.video_enhancer_error_generic)
                }
            }
        }
    }

    fun shareEnhanced(context: Context) {
        val file = enhancedFile ?: return
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null))
        try { AnalyticsManager.trackVideoEnhanceShared() } catch (_: Exception) {}
    }

    fun previewEnhanced(context: Context) {
        val file = enhancedFile ?: return
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            errorMessage = getApplication<Application>().getString(R.string.video_enhancer_no_player)
        }
    }

    private fun resetResult() {
        enhancedFile = null
        enhancedFileSizeBytes = 0L
        isSaved = false
        progress = 0f
    }

    private fun readFileSize(uri: Uri): Long = try {
        getApplication<Application>().contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null,
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0) cursor.getLong(idx) else 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }
}
