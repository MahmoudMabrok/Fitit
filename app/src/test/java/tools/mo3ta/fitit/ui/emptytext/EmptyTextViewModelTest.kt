package tools.mo3ta.fitit.ui.emptytext

import org.junit.Assert.*
import org.junit.Test

class EmptyTextViewModelTest {

    private fun vm(mode: TrickyContentType): EmptyTextViewModel =
        EmptyTextViewModel().apply { onModeSelected(mode) }

    @Test
    fun `every mode generates non-blank text`() {
        TrickyContentType.values().forEach { mode ->
            val text = vm(mode).generateText()
            assertTrue("Mode $mode produced blank text", text.isNotBlank())
        }
    }

    @Test
    fun `onModeSelected resets shared controls to mode defaults`() {
        val viewModel = EmptyTextViewModel()
        viewModel.onModeSelected(TrickyContentType.DOCUMENT)
        assertEquals(TrickyContentType.DOCUMENT, viewModel.selectedMode)
        assertEquals("Report.pdf", viewModel.textInput)
    }

    @Test
    fun `invisible mode length follows the slider`() {
        val viewModel = vm(TrickyContentType.INVISIBLE)
        viewModel.sliderValue = 7f
        assertEquals(7, viewModel.generateText().length)
    }

    @Test
    fun `voice message contains play icon and formatted duration`() {
        val viewModel = vm(TrickyContentType.VOICE_MESSAGE)
        viewModel.sliderValue = 75f
        val text = viewModel.generateText()
        assertTrue(text.contains("▶︎"))
        assertTrue(text.contains("1:15"))
    }

    @Test
    fun `typing uses the entered name`() {
        val viewModel = vm(TrickyContentType.TYPING)
        viewModel.textInput = "Sara"
        assertTrue(viewModel.generateText().startsWith("Sara is typing"))
    }

    @Test
    fun `typing falls back to placeholder when blank`() {
        val viewModel = vm(TrickyContentType.TYPING)
        viewModel.textInput = "   "
        assertTrue(viewModel.generateText().startsWith("Someone is typing"))
    }

    @Test
    fun `missed call switches between voice and video`() {
        val viewModel = vm(TrickyContentType.MISSED_CALL)
        viewModel.toggleVideo = false
        assertTrue(viewModel.generateText().contains("Missed voice call"))
        viewModel.toggleVideo = true
        assertTrue(viewModel.generateText().contains("Missed video call"))
    }

    @Test
    fun `download bar reflects the percentage`() {
        val viewModel = vm(TrickyContentType.DOWNLOAD)
        viewModel.sliderValue = 60f
        val text = viewModel.generateText()
        assertTrue(text.contains("60%"))
        assertEquals(6, text.count { it == '▰' })
    }

    @Test
    fun `poll derives options from an A-or-B question`() {
        val viewModel = vm(TrickyContentType.POLL)
        viewModel.textInput = "Pizza or Burger?"
        val text = viewModel.generateText()
        assertTrue(text.contains("Pizza"))
        assertTrue(text.contains("Burger"))
        assertTrue(text.contains("%"))
    }

    @Test
    fun `poll percentages are stable for the same question`() {
        val first = vm(TrickyContentType.POLL).apply { textInput = "Tea or Coffee?" }.generateText()
        val second = vm(TrickyContentType.POLL).apply { textInput = "Tea or Coffee?" }.generateText()
        assertEquals(first, second)
    }

    @Test
    fun `document shows pages and size`() {
        val viewModel = vm(TrickyContentType.DOCUMENT)
        viewModel.textInput = "Plan.pdf"
        val text = viewModel.generateText()
        assertTrue(text.contains("Plan.pdf"))
        assertTrue(text.contains("pages"))
        assertTrue(text.contains("KB"))
    }
}
