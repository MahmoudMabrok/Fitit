package tools.mo3ta.fitit.ui.textsplitter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tools.mo3ta.fitit.analytics.AnalyticsManager

enum class SplitPreset(val fixedSize: Int?) {
    WHATSAPP(200),
    TWITTER(280),
    CUSTOM(null)
}

class TextSplitterViewModel : ViewModel() {

    var inputText by mutableStateOf("")
    var selectedPreset by mutableStateOf(SplitPreset.WHATSAPP)
    var customSizeInput by mutableStateOf("100")
    var chunks by mutableStateOf<List<String>>(emptyList())
    var errorMessage by mutableStateOf<String?>(null)

    val chunkSize: Int
        get() = selectedPreset.fixedSize ?: (customSizeInput.toIntOrNull() ?: 0)

    val isSplitEnabled: Boolean
        get() = inputText.isNotBlank() && chunkSize > 0

    fun split() {
        val size = chunkSize
        if (size <= 0) return
        val trimmed = inputText.trim()
        if (trimmed.length <= size) {
            errorMessage = "النص قصير جداً، لا حاجة للتقسيم"
            chunks = emptyList()
            return
        }
        errorMessage = null
        chunks = splitText(trimmed, size)
        try {
            AnalyticsManager.trackTextSplitterUsed(
                preset = selectedPreset.name.lowercase(),
                chunkCount = chunks.size
            )
        } catch (_: Exception) {
            // Analytics not available in test environment
        }
    }

    fun copyChunk(context: Context, chunk: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("chunk", chunk))
    }

    fun copyAll(context: Context) {
        try {
            AnalyticsManager.trackTextSplitterCopyAll(chunkCount = chunks.size)
        } catch (_: Exception) { }
        viewModelScope.launch {
            chunks.reversed().forEach { chunk ->
                val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("chunk", chunk))
                delay(150)
            }
        }
    }
}

/**
 * Word-aware text splitter. Breaks at last space before [chunkSize].
 * Hard-splits any single word longer than [chunkSize].
 */
fun splitText(text: String, chunkSize: Int): List<String> {
    val words = text.split(" ")
    val chunks = mutableListOf<String>()
    val current = StringBuilder()

    for (word in words) {
        if (word.length > chunkSize) {
            // Flush current buffer first
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trimEnd())
                current.clear()
            }
            // Hard-split the oversized word
            var remaining = word
            while (remaining.length > chunkSize) {
                chunks.add(remaining.substring(0, chunkSize))
                remaining = remaining.substring(chunkSize)
            }
            if (remaining.isNotEmpty()) {
                current.append(remaining).append(" ")
            }
        } else {
            val proposed = current.toString().trimEnd().let {
                if (it.isEmpty()) word else "$it $word"
            }
            if (proposed.length > chunkSize) {
                chunks.add(current.toString().trimEnd())
                current.clear()
                current.append(word).append(" ")
            } else {
                current.append(word).append(" ")
            }
        }
    }

    if (current.isNotEmpty()) {
        chunks.add(current.toString().trimEnd())
    }

    return chunks
}
