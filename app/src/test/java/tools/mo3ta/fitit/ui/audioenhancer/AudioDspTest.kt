package tools.mo3ta.fitit.ui.audioenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioDspTest {

    private val sampleRate = 16000

    private fun tone(freq: Double, n: Int, amp: Float = 0.5f): FloatArray =
        FloatArray(n) { (amp * sin(2 * PI * freq * it / sampleRate)).toFloat() }

    private fun rms(x: FloatArray): Float {
        var s = 0.0
        for (v in x) s += v.toDouble() * v
        return sqrt(s / x.size).toFloat()
    }

    @Test
    fun `fft followed by inverse recovers the signal`() {
        val n = 1024
        val signal = tone(440.0, n)
        val re = signal.copyOf()
        val im = FloatArray(n)
        AudioDsp.fft(re, im, false)
        AudioDsp.fft(re, im, true)
        var maxErr = 0f
        for (i in 0 until n) maxErr = maxOf(maxErr, abs(re[i] - signal[i]))
        assertTrue("round-trip error too large: $maxErr", maxErr < 1e-3f)
    }

    @Test
    fun `highpass attenuates a low-frequency tone`() {
        val low = tone(50.0, sampleRate)
        val out = AudioDsp.highPass(low, sampleRate, cutoffHz = 300f)
        assertTrue(rms(out) < 0.5f * rms(low))
    }

    @Test
    fun `highpass keeps a high-frequency tone`() {
        val high = tone(3000.0, sampleRate)
        val out = AudioDsp.highPass(high, sampleRate, cutoffHz = 300f)
        assertTrue(rms(out) > 0.7f * rms(high))
    }

    @Test
    fun `lowpass attenuates a high-frequency tone`() {
        val high = tone(7000.0, sampleRate)
        val out = AudioDsp.lowPass(high, sampleRate, cutoffHz = 1500f)
        assertTrue(rms(out) < 0.5f * rms(high))
    }

    @Test
    fun `noise reduction lowers the noise floor of a noisy tone`() {
        val clean = tone(440.0, sampleRate)
        val rng = java.util.Random(1)
        val noisy = FloatArray(clean.size) { clean[it] + (0.08 * rng.nextGaussian()).toFloat() }
        val out = AudioDsp.reduceNoise(noisy, strength = 1.5f)

        val before = rms(FloatArray(clean.size) { noisy[it] - clean[it] })
        val after = rms(FloatArray(clean.size) { out[it] - clean[it] })
        assertTrue("expected noise reduction, before=$before after=$after", after < before)
    }

    @Test
    fun `noise reduction with zero strength is a no-op`() {
        val signal = tone(440.0, sampleRate)
        val out = AudioDsp.reduceNoise(signal, strength = 0f)
        assertTrue(out === signal)
    }

    @Test
    fun `noise reduction passes through signals shorter than the fft`() {
        val signal = FloatArray(100) { 0.1f }
        val out = AudioDsp.reduceNoise(signal, fftSize = 1024)
        assertTrue(out === signal)
    }

    @Test
    fun `peak normalize reaches the target level`() {
        val quiet = arrayOf(tone(440.0, 4000, amp = 0.05f))
        AudioDsp.peakNormalize(quiet, targetDb = -1f)
        val target = Math.pow(10.0, -1.0 / 20.0).toFloat()
        assertEquals(target, AudioDsp.peak(quiet), 0.01f)
    }

    @Test
    fun `deinterleave then interleave round-trips stereo pcm`() {
        val pcm = shortArrayOf(100, -100, 200, -200, 300, -300)
        val channels = AudioDsp.deinterleave(pcm, 2)
        assertEquals(2, channels.size)
        assertEquals(3, channels[0].size)
        val back = AudioDsp.interleave(channels)
        for (i in pcm.indices) assertTrue(abs(pcm[i] - back[i]) <= 1)
    }

    @Test
    fun `interleave clips out-of-range samples`() {
        val channels = arrayOf(floatArrayOf(2.0f, -2.0f))
        val pcm = AudioDsp.interleave(channels)
        assertEquals(32767, pcm[0].toInt())
        assertEquals(-32768, pcm[1].toInt())
    }
}
