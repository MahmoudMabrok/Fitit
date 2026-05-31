package tools.mo3ta.fitit.ui.videoenhancer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    var fastCapPx by mutableStateOf(MlSpeedMode.FAST.inputShortSideCap)
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

    init {
        // The actual work runs in VideoEnhancerService so it survives the user leaving the screen or
        // backgrounding the app; this ViewModel just mirrors the shared state into the UI. A freshly
        // created ViewModel (e.g. after navigating back) immediately reflects an in-flight run.
        viewModelScope.launch {
            VideoEnhanceManager.state.collect { state ->
                when (state) {
                    is EnhanceState.Idle -> isProcessing = false
                    is EnhanceState.Running -> {
                        isProcessing = true
                        progress = state.progress
                        errorMessage = null
                        enhancedFile = null
                        isSaved = false
                    }
                    is EnhanceState.Success -> {
                        isProcessing = false
                        progress = 1f
                        enhancedFile = state.outputFile
                        enhancedFileSizeBytes = state.outputSizeBytes
                        isSaved = state.savedToGallery
                        aiFellBackToGl = state.fellBackToGl
                    }
                    is EnhanceState.Failed -> {
                        isProcessing = false
                        errorMessage = state.message
                            ?: getApplication<Application>().getString(R.string.video_enhancer_error_generic)
                    }
                }
            }
        }
    }

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

    fun changeFastCap(px: Int) {
        if (isProcessing) return
        fastCapPx = px
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
        if (VideoEnhanceManager.isRunning) return
        val context = getApplication<Application>()

        val requestedEngine = if (useAiUpscale) EnhanceEngine.ML else EnhanceEngine.GL
        // Only Fast mode exposes a user-chosen resolution cap; other modes use their own.
        val capOverride = if (speedMode == MlSpeedMode.FAST) fastCapPx else null

        errorMessage = null
        aiFellBackToGl = false
        resetResult()

        try { AnalyticsManager.trackVideoEnhanceStarted(videoDurationMs, level.name) } catch (_: Exception) {}

        // Hand the heavy work to a foreground service so it keeps running if the user leaves the app;
        // progress and the result come back through VideoEnhanceManager, which this ViewModel observes.
        VideoEnhancerService.start(context, uri, level, requestedEngine, speedMode, capOverride)
    }

    /** Cancels the in-progress background run, if any. */
    fun cancelProcessing() {
        VideoEnhancerService.cancel(getApplication())
    }

    fun saveEnhanced(context: Context) {
        val file = enhancedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                saveVideoToGallery(context, file)
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
        // Drop any finished/failed result from a previous run (no-op while one is in progress).
        VideoEnhanceManager.reset()
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
