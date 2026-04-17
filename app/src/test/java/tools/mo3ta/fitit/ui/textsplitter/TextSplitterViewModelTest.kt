package tools.mo3ta.fitit.ui.textsplitter

import android.app.Application
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class TextSplitterViewModelTest {

    private fun createViewModel(): TextSplitterViewModel {
        val application = mockk<Application>(relaxed = true)
        return TextSplitterViewModel(application)
    }

    @Test
    fun `splitText returns multiple chunks for long text`() {
        val text = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10"
        val result = splitText(text, 20)
        assertTrue("Expected more than 1 chunk", result.size > 1)
    }

    @Test
    fun `splitText does not cut mid-word`() {
        val text = "hello world goodbye"
        val result = splitText(text, 10)
        result.forEach { chunk ->
            assertFalse("Chunk should not start or end with partial word fragment",
                chunk.startsWith(" ") || chunk.endsWith(" "))
        }
    }

    @Test
    fun `splitText preserves all words across chunks`() {
        val text = "one two three four five six seven eight nine ten"
        val result = splitText(text, 15)
        val rejoined = result.joinToString(" ")
        assertEquals(text, rejoined)
    }

    @Test
    fun `splitText hard-splits a word longer than chunkSize`() {
        val longWord = "a".repeat(50)
        val result = splitText(longWord, 20)
        assertEquals(3, result.size)
        assertEquals("a".repeat(20), result[0])
        assertEquals("a".repeat(20), result[1])
        assertEquals("a".repeat(10), result[2])
    }

    @Test
    fun `splitText returns single chunk when text fits`() {
        val text = "short text"
        val result = splitText(text, 100)
        assertEquals(1, result.size)
        assertEquals("short text", result[0])
    }

    @Test
    fun `isSplitEnabled is false for blank input`() {
        val vm = createViewModel()
        vm.inputText = "   "
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `isSplitEnabled is false for custom preset with zero size`() {
        val vm = createViewModel()
        vm.inputText = "some text"
        vm.selectedPreset = SplitPreset.CUSTOM
        vm.customSizeInput = "0"
        assertFalse(vm.isSplitEnabled)
    }

    @Test
    fun `split sets errorMessage when text fits in one chunk`() {
        val vm = createViewModel()
        vm.inputText = "short"
        vm.selectedPreset = SplitPreset.TWITTER  // 280 chars
        vm.split()
        assertNotNull(vm.errorMessage)
        assertTrue(vm.chunks.isEmpty())
    }

    @Test
    fun `split produces chunks and clears errorMessage for long text`() {
        val vm = createViewModel()
        vm.inputText = "word ".repeat(60).trim()  // ~300 chars
        vm.selectedPreset = SplitPreset.WHATSAPP  // 200 chars
        vm.split()
        assertNull(vm.errorMessage)
        assertTrue(vm.chunks.size > 1)
    }
}
