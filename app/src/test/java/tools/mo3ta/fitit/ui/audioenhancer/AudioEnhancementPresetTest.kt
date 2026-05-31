package tools.mo3ta.fitit.ui.audioenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioEnhancementPresetTest {

    private val sampleRate = 16000

    private fun tone(freq: Double, n: Int, amp: Float = 0.3f): FloatArray =
        FloatArray(n) { (amp * sin(2 * PI * freq * it / sampleRate)).toFloat() }

    @Test
    fun `levels increase noise strength from light to strong`() {
        assertTrue(AudioEnhancementLevel.LIGHT.noiseStrength < AudioEnhancementLevel.STANDARD.noiseStrength)
        assertTrue(AudioEnhancementLevel.STANDARD.noiseStrength < AudioEnhancementLevel.STRONG.noiseStrength)
    }

    @Test
    fun `only the strong preset engages the low-pass filter`() {
        assertEquals(0f, AudioEnhancementLevel.LIGHT.lowPassHz)
        assertEquals(0f, AudioEnhancementLevel.STANDARD.lowPassHz)
        assertTrue(AudioEnhancementLevel.STRONG.lowPassHz > 0f)
    }

    @Test
    fun `enhance preserves channel length and stays finite`() {
        val channels = arrayOf(tone(440.0, sampleRate), tone(660.0, sampleRate))
        val out = enhanceChannels(channels, sampleRate, AudioEnhancementLevel.STANDARD)
        assertEquals(2, out.size)
        assertEquals(sampleRate, out[0].size)
        for (ch in out) for (v in ch) assertTrue(v.isFinite())
    }

    @Test
    fun `enhance never clips the output`() {
        val rng = java.util.Random(7)
        val clean = tone(440.0, sampleRate, amp = 0.1f)
        val noisy = arrayOf(FloatArray(clean.size) { clean[it] + (0.05 * rng.nextGaussian()).toFloat() })
        enhanceChannels(noisy, sampleRate, AudioEnhancementLevel.STANDARD)
        assertTrue(AudioDsp.peak(noisy) <= 1.0f)
    }

    @Test
    fun `enhance reports progress for every channel`() {
        val channels = arrayOf(tone(440.0, sampleRate), tone(660.0, sampleRate))
        val reported = mutableListOf<Float>()
        enhanceChannels(channels, sampleRate, AudioEnhancementLevel.LIGHT) { reported.add(it) }
        assertEquals(listOf(0.5f, 1.0f), reported)
    }
}
