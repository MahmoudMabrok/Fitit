package tools.mo3ta.fitit.ui.audioenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tests for the loudness / clarity DSP additions: the presence peaking EQ,
 * loudness normalization with a true-peak ceiling, and the single-band
 * dynamic-range compressor. Pure JVM unit tests, no Android dependencies.
 */
class AudioDspLoudnessTest {

    private val sampleRate = 16000

    private fun tone(freq: Double, n: Int, amp: Float = 0.3f): FloatArray =
        FloatArray(n) { (amp * sin(2 * PI * freq * it / sampleRate)).toFloat() }

    private fun rms(x: FloatArray): Float {
        var s = 0.0
        for (v in x) s += v.toDouble() * v
        return sqrt(s / x.size).toFloat()
    }

    private fun tailPeak(x: FloatArray): Float {
        var p = 0f
        for (i in x.size / 2 until x.size) if (abs(x[i]) > p) p = abs(x[i])
        return p
    }

    @Test
    fun `peaking eq boosts a tone at the center frequency`() {
        val n = 8192
        val freq = 3000.0
        val input = tone(freq, n, amp = 0.3f)
        val out = AudioDsp.peaking(input, sampleRate, freq.toFloat(), gainDb = 6f)
        // +6 dB is ~ x1.995 gain at the center frequency (skip the filter transient).
        assertEquals(1.995f, tailPeak(out) / tailPeak(input), 0.35f)
    }

    @Test
    fun `peaking eq leaves a distant frequency roughly unchanged`() {
        val n = 8192
        val input = tone(200.0, n, amp = 0.3f)
        val out = AudioDsp.peaking(input, sampleRate, centerHz = 4000f, gainDb = 6f, q = 1f)
        assertEquals(1.0f, tailPeak(out) / tailPeak(input), 0.2f)
    }

    @Test
    fun `loudness normalize reaches the rms target without clipping`() {
        val quiet = arrayOf(tone(440.0, sampleRate, amp = 0.05f))
        AudioDsp.loudnessNormalize(quiet, targetRmsDb = -16f, ceilingDb = -1f)
        val targetRms = Math.pow(10.0, -16.0 / 20.0).toFloat()
        assertEquals(targetRms, rms(quiet[0]), 0.02f)
        val ceiling = Math.pow(10.0, -1.0 / 20.0).toFloat()
        assertTrue(AudioDsp.peak(quiet) <= ceiling + 1e-4f)
    }

    @Test
    fun `loudness normalize soft limits a hot signal below the ceiling`() {
        val hot = arrayOf(tone(440.0, sampleRate, amp = 0.5f))
        AudioDsp.loudnessNormalize(hot, targetRmsDb = 0f, ceilingDb = -1f)
        val ceiling = Math.pow(10.0, -1.0 / 20.0).toFloat()
        assertTrue(AudioDsp.peak(hot) < ceiling)
    }

    @Test
    fun `compression reduces the crest factor`() {
        val n = sampleRate
        val signal = FloatArray(n) { i ->
            val base = 0.1f * sin(2 * PI * 220.0 * i / sampleRate).toFloat()
            // Inject short loud transients to create a high crest factor.
            if (i % 2000 < 40) base + 0.8f else base
        }
        val before = AudioDsp.peak(arrayOf(signal)) / rms(signal)
        // Instantaneous ballistics (a static compression curve) so the test is
        // deterministic: peaks above the threshold are pulled down regardless of
        // attack timing, which is what lowers the crest factor.
        val out = AudioDsp.compress(
            signal, sampleRate, thresholdDb = -20f, ratio = 4f, attackMs = 0f, releaseMs = 0f,
        )
        val after = AudioDsp.peak(arrayOf(out)) / rms(out)
        assertTrue("crest should drop: before=$before after=$after", after < before)
    }

    @Test
    fun `presets scale loudness and clarity monotonically`() {
        val light = AudioEnhancementLevel.LIGHT
        val standard = AudioEnhancementLevel.STANDARD
        val strong = AudioEnhancementLevel.STRONG
        assertTrue(light.targetRmsDb < standard.targetRmsDb)
        assertTrue(standard.targetRmsDb < strong.targetRmsDb)
        assertTrue(light.presenceGainDb < standard.presenceGainDb)
        assertTrue(standard.presenceGainDb < strong.presenceGainDb)
        assertTrue(light.compRatio < standard.compRatio)
        assertTrue(standard.compRatio < strong.compRatio)
        assertTrue(strong.ceilingDb < 0f)
    }
}
