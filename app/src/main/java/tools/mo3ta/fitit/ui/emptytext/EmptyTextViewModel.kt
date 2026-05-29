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
            var previous = -1
            repeat(barCount) {
                // Pick a bar glyph different from the previous one so the result
                // reads as a waveform of varying heights rather than a flat run.
                var level = rng.nextInt(WAVE_BARS.size)
                if (level == previous) level = (level + 1) % WAVE_BARS.size
                previous = level
                append(WAVE_BARS[level])
            }
        }
        // Wrap the waveform in bullet dots, like a real voice-note bubble:
        //   ▶︎ •၊၊||၊|။||||။၊|• 0:10
        return "$PLAY_ICON $BULLET$bars$BULLET ${formatDuration(seconds)}"
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

        // ▶ play triangle, with U+FE0E text variation selector so it renders
        // as a flat glyph rather than a colorful emoji.
        private const val PLAY_ICON = "▶︎"

        // • bullet dots that bracket the waveform, like a real voice-note bubble.
        private const val BULLET = "•"

        // Vertical stroke glyphs of differing visual heights, used as fake
        // waveform bars. Mixing Myanmar section signs (၊ ။) with the ASCII pipe
        // gives short / tall / double-tall bars that read as a varied waveform:
        //   ▶︎ •၊၊||၊|။||||။၊|• 0:10
        private val WAVE_BARS = listOf("၊", "|", "။")
    }
}

enum class TrickyContentType {
    INVISIBLE,
    VOICE_MESSAGE
}
