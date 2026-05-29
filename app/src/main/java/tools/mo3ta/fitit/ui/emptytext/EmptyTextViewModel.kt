package tools.mo3ta.fitit.ui.emptytext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.random.Random

class EmptyTextViewModel : ViewModel() {
    // Which kind of "tricky" content we generate.
    var selectedMode by mutableStateOf(TrickyContentType.INVISIBLE)

    // Number of invisible characters (Invisible mode).
    var charCount by mutableStateOf(10f)

    // Length of the fake voice note in seconds (Voice Message mode).
    var durationSeconds by mutableStateOf(15f)

    fun generateText(): String = when (selectedMode) {
        TrickyContentType.INVISIBLE ->
            HANGUL_FILLER.repeat(charCount.toInt().coerceAtLeast(1))
        TrickyContentType.VOICE_MESSAGE ->
            generateVoiceMessage(durationSeconds.toInt().coerceAtLeast(1))
    }

    /**
     * Builds a string that mimics a voice/sound message:
     * a play icon, a fake waveform of "sound bars", then the duration.
     * The waveform is seeded by the duration so it stays stable across
     * recompositions (no flicker) while still looking varied.
     */
    private fun generateVoiceMessage(seconds: Int): String {
        val rng = Random(seconds.toLong())
        val barCount = (seconds / 2 + 8).coerceIn(10, 28)
        val bars = buildString {
            repeat(barCount) { append(WAVE_LEVELS[rng.nextInt(WAVE_LEVELS.length)]) }
        }
        return "$PLAY_ICON $bars ${formatDuration(seconds)}"
    }

    /** Formats the voice-note length as m:ss (e.g. 75 -> "1:15"). */
    fun formattedDuration(): String = formatDuration(durationSeconds.toInt().coerceAtLeast(1))

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(minutes, secs)
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Tricky Content", generateText())
        clipboard.setPrimaryClip(clip)
    }

    companion object {
        // Hangul Filler (U+3164) — renders as blank but is treated as a real character,
        // so it passes "non-empty" filters on many social apps.
        private const val HANGUL_FILLER = "ㅤ"

        // ▶ play triangle.
        private const val PLAY_ICON = "▶"

        // ▁▂▃▄▅▆▇ block elements used as fake waveform bars.
        private const val WAVE_LEVELS = "▁▂▃▄▅▆▇"
    }
}

enum class TrickyContentType {
    INVISIBLE,
    VOICE_MESSAGE
}
