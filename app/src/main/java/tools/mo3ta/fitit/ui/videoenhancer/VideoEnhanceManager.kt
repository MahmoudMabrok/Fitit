package tools.mo3ta.fitit.ui.videoenhancer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * The state of a single background video-enhance run.
 *
 * This is intentionally process-global ([VideoEnhanceManager]) rather than owned by a ViewModel: the
 * heavy enhancement runs in [VideoEnhancerService] so it survives the user leaving the screen (or the
 * whole app), and any [VideoEnhancerViewModel] that is alive simply observes this flow to reflect the
 * work's progress and result.
 */
sealed interface EnhanceState {
    /** No run in progress and no fresh result to show. */
    data object Idle : EnhanceState

    /** A run is executing; [progress] is a 0f..1f completion fraction. */
    data class Running(val progress: Float) : EnhanceState

    /**
     * A run finished successfully.
     *
     * @param savedToGallery whether the service already persisted [outputFile] to the gallery, so the
     *   result is safe even if the user never returns to the app.
     * @param fellBackToGl true when AI was requested but the engine fell back to the GL pipeline.
     */
    data class Success(
        val outputFile: File,
        val outputSizeBytes: Long,
        val savedToGallery: Boolean,
        val fellBackToGl: Boolean,
    ) : EnhanceState

    /** A run failed; [message] is a user-facing reason when one is available. */
    data class Failed(val message: String?) : EnhanceState
}

/**
 * Process-wide hand-off point between [VideoEnhancerService] (the producer of progress/results) and
 * any [VideoEnhancerViewModel] (the consumer that renders them). Holding the state here — instead of
 * in the ViewModel — is what lets the user leave the enhancer screen, or background the whole app,
 * while a clip keeps processing.
 */
object VideoEnhanceManager {

    private val _state = MutableStateFlow<EnhanceState>(EnhanceState.Idle)
    val state: StateFlow<EnhanceState> = _state.asStateFlow()

    val isRunning: Boolean
        get() = _state.value is EnhanceState.Running

    /** Pushes a new state. Called by [VideoEnhancerService] as the run progresses. */
    fun update(state: EnhanceState) {
        _state.value = state
    }

    /**
     * Clears any finished/failed result back to [EnhanceState.Idle]. Ignored while a run is in
     * progress so the UI can't accidentally drop a live job by changing an option.
     */
    fun reset() {
        if (_state.value is EnhanceState.Running) return
        _state.value = EnhanceState.Idle
    }
}

/**
 * Writes an enhanced [file] into the device gallery (Movies/Zaki) and returns its media [Uri].
 *
 * Shared by [VideoEnhancerService] (auto-save when a background run completes) and
 * [VideoEnhancerViewModel] (the manual Save button), so both produce identical gallery entries.
 * Throws on failure; callers decide how to surface it.
 */
fun saveVideoToGallery(context: Context, file: File): Uri {
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
        return insertUri
    }

    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "Zaki",
    ).also { it.mkdirs() }
    val dest = File(dir, "zaki_enhanced_${file.name}")
    file.copyTo(dest, overwrite = true)
    @Suppress("DEPRECATION")
    MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
    return Uri.fromFile(dest)
}
