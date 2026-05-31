package tools.mo3ta.fitit.ui.audioenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the DTLN denoiser's framing maths and the mono / resample
 * pre-processing. Model inference itself needs a device, so it is excluded here
 * and covered by instrumented tests.
 */
class MlAudioDenoiserTest {

    @Test
    fun `block count matches the dtln overlap-add geometry`() {
        // (len - (blockLen - blockShift)) / blockShift
        assertEquals(1, dtlnBlockCount(512, 512, 128))
        assertEquals(5, dtlnBlockCount(512 + 4 * 128, 512, 128))
        // One extra hop yields one extra block.
        assertEquals(6, dtlnBlockCount(512 + 5 * 128, 512, 128))
    }

    @Test
    fun `signals shorter than one block produce no blocks`() {
        assertEquals(0, dtlnBlockCount(100, 512, 128))
        assertEquals(0, dtlnBlockCount(511, 512, 128))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive block sizes throw`() {
        dtlnBlockCount(1024, 0, 128)
    }

    @Test
    fun `mix to mono averages channels`() {
        val left = floatArrayOf(1.0f, -0.5f, 0.0f)
        val right = floatArrayOf(0.0f, 0.5f, 1.0f)
        val mono = AudioDsp.mixToMono(arrayOf(left, right))
        assertEquals(0.5f, mono[0], 1e-6f)
        assertEquals(0.0f, mono[1], 1e-6f)
        assertEquals(0.5f, mono[2], 1e-6f)
        // Inputs are not mutated.
        assertEquals(1.0f, left[0], 1e-6f)
    }

    @Test
    fun `mix to mono on a single channel returns a copy`() {
        val only = floatArrayOf(0.1f, 0.2f)
        val mono = AudioDsp.mixToMono(arrayOf(only))
        assertEquals(listOf(0.1f, 0.2f), mono.toList())
        assertTrue(mono !== only)
    }

    @Test
    fun `resample to a lower rate shortens the signal proportionally`() {
        val n = 1600
        val signal = FloatArray(n) { it.toFloat() }
        val out = AudioDsp.resampleLinear(signal, 48000, 16000)
        // 48k -> 16k is a 1/3 length ratio.
        assertEquals(n / 3, out.size)
        // Linear ramp stays roughly a ramp after resampling.
        assertEquals(0f, out.first(), 1e-3f)
        assertTrue(out.last() > out.first())
    }

    @Test
    fun `resample with matching rates returns a copy`() {
        val signal = floatArrayOf(0.1f, 0.2f, 0.3f)
        val out = AudioDsp.resampleLinear(signal, 16000, 16000)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), out.toList())
        assertTrue(out !== signal)
    }

    @Test
    fun `resample round-trips the sample rate without large error`() {
        val n = 8000
        val signal = FloatArray(n) { kotlin.math.sin(2 * Math.PI * 200.0 * it / 16000).toFloat() }
        val down = AudioDsp.resampleLinear(signal, 16000, 8000)
        val up = AudioDsp.resampleLinear(down, 8000, 16000)
        // Length is preserved within rounding.
        assertTrue(kotlin.math.abs(up.size - n) <= 2)
    }
}
