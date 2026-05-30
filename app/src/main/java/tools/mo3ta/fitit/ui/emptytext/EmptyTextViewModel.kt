package tools.mo3ta.fitit.ui.emptytext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class EmptyTextViewModel : ViewModel() {
    // Which kind of "tricky" content we generate.
    var selectedMode by mutableStateOf(TrickyContentType.INVISIBLE)
        private set

    // Generic numeric control, repurposed per mode (char count, duration,
    // percentage, minutes…). Each mode declares its own range + default.
    var sliderValue by mutableStateOf(TrickyContentType.INVISIBLE.defaultSlider)

    // Generic text control, repurposed per mode (a name, a file name, a poll
    // question, a quoted message…).
    var textInput by mutableStateOf(TrickyContentType.INVISIBLE.defaultText)

    // Generic toggle, repurposed per mode (voice/video call, photo/video media).
    var toggleVideo by mutableStateOf(false)

    /** Switches mode and resets the shared controls to that mode's defaults. */
    fun onModeSelected(mode: TrickyContentType) {
        selectedMode = mode
        sliderValue = mode.defaultSlider
        textInput = mode.defaultText
        toggleVideo = false
    }

    fun generateText(): String = when (selectedMode) {
        TrickyContentType.INVISIBLE ->
            HANGUL_FILLER.repeat(sliderValue.toInt().coerceAtLeast(1))
        TrickyContentType.VOICE_MESSAGE ->
            generateVoiceMessage(sliderValue.toInt().coerceAtLeast(1))
        TrickyContentType.TYPING ->
            generateTyping(textInput)
        TrickyContentType.RECORDING ->
            generateRecording(sliderValue.toInt().coerceAtLeast(1))
        TrickyContentType.MISSED_CALL ->
            generateMissedCall(toggleVideo)
        TrickyContentType.PHOTO ->
            generatePhoto()
        TrickyContentType.VIEW_ONCE ->
            generateViewOnce(toggleVideo)
        TrickyContentType.DOCUMENT ->
            generateDocument(textInput)
        TrickyContentType.LOCATION ->
            generateLocation(sliderValue.toInt().coerceAtLeast(1))
        TrickyContentType.CONTACT ->
            generateContact(textInput)
        TrickyContentType.DOWNLOAD ->
            generateDownload(sliderValue.toInt())
        TrickyContentType.SPINNER ->
            generateSpinner()
        TrickyContentType.POLL ->
            generatePoll(textInput)
        TrickyContentType.QUOTE ->
            generateQuote(textInput)
    }

    /**
     * Builds a string that mimics a voice/sound message:
     * a play icon, a fake waveform of "sound bars", then the duration.
     * The waveform is seeded by the duration so it stays stable across
     * recompositions (no flicker) while still looking varied.
     */
    private fun generateVoiceMessage(seconds: Int): String {
        val bars = fakeWaveform(seconds, seconds.toLong())
        // Wrap the waveform in bullet dots, like a real voice-note bubble:
        //   ▶︎ •၊၊||၊|။||||။၊|• 0:10
        return "$PLAY_ICON $BULLET$bars$BULLET ${formatDuration(seconds)}"
    }

    /** "Ahmed is typing  ●  ●  ●" — looks like a stuck typing indicator. */
    private fun generateTyping(name: String): String {
        val who = name.trim().ifBlank { "Someone" }
        return "$who is typing   ●   ●   ●"
    }

    /** "🔴 recording audio…  0:04" — the mic counterpart to the typing trick. */
    private fun generateRecording(seconds: Int): String =
        "$REC_DOT  recording audio…   ${formatDuration(seconds)}"

    /** Mimics a call-log row: "📞 Missed voice call · 10:42". */
    private fun generateMissedCall(video: Boolean): String {
        val icon = if (video) VIDEO_ICON else CALL_ICON
        val label = if (video) "Missed video call" else "Missed voice call"
        return "$icon  $label · ${currentClock()}"
    }

    /** A blurred block + label, like a still-loading image attachment. */
    private fun generatePhoto(): String {
        val row = BLUR.repeat(10)
        return "$row\n$row\n$row\n$CAMERA_ICON  Photo"
    }

    /** "👁 Photo · View once" — the disappearing-media bubble. */
    private fun generateViewOnce(video: Boolean): String {
        val label = if (video) "Video" else "Photo"
        return "$EYE_ICON  $label · View once"
    }

    /** A file-attachment bubble with deterministic page count + size. */
    private fun generateDocument(name: String): String {
        val safe = name.trim().ifBlank { "Document.pdf" }
        val rng = Random(safe.hashCode().toLong())
        val pages = rng.nextInt(1, 12)
        val sizeKb = rng.nextInt(80, 960)
        val ext = safe.substringAfterLast('.', "PDF").uppercase(Locale.US)
        return "$DOC_ICON  $safe\n$pages pages · $sizeKb KB · $ext"
    }

    /** "📍 Live location — shared for 15 min". */
    private fun generateLocation(minutes: Int): String =
        "$PIN_ICON  Live location\nshared for $minutes min"

    /** A shared-contact bubble with a masked phone number. */
    private fun generateContact(name: String): String {
        val safe = name.trim().ifBlank { "Unknown" }
        val rng = Random(safe.hashCode().toLong())
        val last2 = "%02d".format(rng.nextInt(0, 100))
        return "$CONTACT_ICON  $safe\n+20 100 ••• ••$last2 · Contact"
    }

    /** A progress bar that never finishes: "⬇ Downloading ▰▰▰▰▰▰▱▱▱▱ 60%". */
    private fun generateDownload(pct: Int): String {
        val p = pct.coerceIn(0, 100)
        return "$DOWNLOAD_ICON  Downloading   ${progressBar(p)}  $p%"
    }

    /** "⣾ Loading…" — looks like the app is buffering. */
    private fun generateSpinner(): String = "$SPINNER_GLYPH  Loading…"

    /**
     * A poll bubble. If the question reads like "A or B?" the options are
     * pulled from it; otherwise it falls back to Yes/No. Percentages are
     * seeded by the question so the bars stay stable across recompositions.
     */
    private fun generatePoll(question: String): String {
        val q = question.trim().ifBlank { "Yes or No?" }
        val core = q.removeSuffix("?").removeSuffix("؟")
        val parts = core.split(" or ", " أو ", limit = 2)
        val optA = parts.getOrNull(0)?.trim()?.ifBlank { "Yes" } ?: "Yes"
        val optB = parts.getOrNull(1)?.trim()?.ifBlank { "No" } ?: "No"
        val rng = Random(q.hashCode().toLong())
        val pctA = rng.nextInt(20, 81)
        val pctB = 100 - pctA
        return buildString {
            append("$POLL_ICON  $q\n")
            append(pollLine(optA, pctA)).append('\n')
            append(pollLine(optB, pctB))
        }
    }

    private fun pollLine(label: String, pct: Int): String =
        "$label  ${progressBar(pct)}  $pct%"

    /** A reply/quote strip: "▌ Ahmed / ▌ see you tomorrow". */
    private fun generateQuote(message: String): String {
        val safe = message.trim().ifBlank { "original message" }
        return "$QUOTE_BAR Ahmed\n$QUOTE_BAR $safe"
    }

    /** Renders a 10-segment ▰/▱ bar for the given percentage. */
    private fun progressBar(pct: Int): String {
        val filled = ((pct + 5) / 10).coerceIn(0, 10)
        return "▰".repeat(filled) + "▱".repeat(10 - filled)
    }

    /** Builds a stable fake waveform of varying-height bar glyphs. */
    private fun fakeWaveform(seconds: Int, seed: Long): String {
        val rng = Random(seed)
        val barCount = (seconds / 2 + 8).coerceIn(10, 28)
        return buildString {
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
    }

    /** Human-readable value for the active slider, shown next to it. */
    fun sliderDisplay(): String = when (selectedMode.sliderKind) {
        SliderKind.DURATION -> formatDuration(sliderValue.toInt().coerceAtLeast(1))
        SliderKind.PERCENT -> "${sliderValue.toInt()}%"
        SliderKind.MINUTES -> "${sliderValue.toInt()} min"
        SliderKind.COUNT -> "${sliderValue.toInt()}"
        SliderKind.NONE -> ""
    }

    private fun currentClock(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date())

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
        // gives short / tall / double-tall bars that read as a varied waveform.
        private val WAVE_BARS = listOf("၊", "|", "။")

        // Glyphs/emoji used to fake the various chat-UI bubbles.
        private const val REC_DOT = "🔴"
        private const val CALL_ICON = "📞"
        private const val VIDEO_ICON = "📹"
        private const val CAMERA_ICON = "📷"
        private const val EYE_ICON = "👁"
        private const val DOC_ICON = "📄"
        private const val PIN_ICON = "📍"
        private const val CONTACT_ICON = "👤"
        private const val DOWNLOAD_ICON = "⬇"
        private const val POLL_ICON = "📊"
        private const val SPINNER_GLYPH = "⣾"
        private const val QUOTE_BAR = "▌"
        // Medium-shade block used as a blurred image placeholder.
        private const val BLUR = "▒"
    }
}

/** What kind of generic slider a mode needs, so the UI can label it. */
enum class SliderKind { NONE, COUNT, DURATION, MINUTES, PERCENT }

/**
 * Every "tricky content" mode, plus which shared controls it needs.
 * Modes either fake an invisible payload or mimic a native chat-UI bubble
 * (voice note, typing indicator, missed call, attachment, poll…).
 */
enum class TrickyContentType(
    val usesText: Boolean = false,
    val usesVideoToggle: Boolean = false,
    val sliderKind: SliderKind = SliderKind.NONE,
    val sliderRange: ClosedFloatingPointRange<Float> = 1f..100f,
    val defaultSlider: Float = 10f,
    val defaultText: String = ""
) {
    INVISIBLE(sliderKind = SliderKind.COUNT, sliderRange = 1f..200f, defaultSlider = 10f),
    VOICE_MESSAGE(sliderKind = SliderKind.DURATION, sliderRange = 1f..300f, defaultSlider = 15f),
    TYPING(usesText = true, defaultText = "Ahmed"),
    RECORDING(sliderKind = SliderKind.DURATION, sliderRange = 1f..300f, defaultSlider = 4f),
    MISSED_CALL(usesVideoToggle = true),
    PHOTO,
    VIEW_ONCE(usesVideoToggle = true),
    DOCUMENT(usesText = true, defaultText = "Report.pdf"),
    LOCATION(sliderKind = SliderKind.MINUTES, sliderRange = 1f..120f, defaultSlider = 15f),
    CONTACT(usesText = true, defaultText = "Ahmed Ali"),
    DOWNLOAD(sliderKind = SliderKind.PERCENT, sliderRange = 0f..100f, defaultSlider = 60f),
    SPINNER,
    POLL(usesText = true, defaultText = "Pizza or Burger?"),
    QUOTE(usesText = true, defaultText = "see you tomorrow 👋");

    val usesSlider: Boolean get() = sliderKind != SliderKind.NONE
}
