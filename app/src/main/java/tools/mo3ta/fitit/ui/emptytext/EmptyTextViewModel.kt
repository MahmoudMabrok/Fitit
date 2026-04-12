package tools.mo3ta.fitit.ui.emptytext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class EmptyTextViewModel : ViewModel() {
    var charCount by mutableStateOf(10f)
    var selectedType by mutableStateOf(InvisibleCharType.HANGUL_FILLER)

    fun generateText(): String {
        val count = charCount.toInt()
        val char = selectedType.unicode
        return char.repeat(count)
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Invisible Text", generateText())
        clipboard.setPrimaryClip(clip)
    }
}

enum class InvisibleCharType(val displayName: String, val unicode: String, val description: String) {
    HANGUL_FILLER("Hangul Filler", "\u3164", "U+3164"),
    ZERO_WIDTH_SPACE("Zero-Width Space", "\u200B", "U+200B"),
    BRAILLE_BLANK("Braille Blank", "\u2800", "U+2800"),
    ZERO_WIDTH_JOINER("Zero-Width Joiner", "\u200D", "U+200D")
}
