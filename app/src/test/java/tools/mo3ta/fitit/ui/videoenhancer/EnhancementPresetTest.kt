package tools.mo3ta.fitit.ui.videoenhancer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancementPresetTest {

    @Test
    fun `evenDimension keeps even values`() {
        assertEquals(1080, evenDimension(1080))
    }

    @Test
    fun `evenDimension rounds odd values up`() {
        assertEquals(1082, evenDimension(1081))
    }

    @Test
    fun `evenDimension never returns below 2`() {
        assertEquals(2, evenDimension(0))
        assertEquals(2, evenDimension(1))
    }

    @Test
    fun `standard upscales 480p toward 720 within max scale`() {
        // 854x480, short side 480 -> target 720 would be 1.5x, capped at maxScale 1.5.
        val spec = computeOutputSpec(854, 480, 30, EnhancementLevel.STANDARD)
        assertEquals(720, spec.height)
        assertEquals(evenDimension((854 * 1.5).toInt()), spec.width)
    }

    @Test
    fun `light never upscales`() {
        val spec = computeOutputSpec(640, 360, 30, EnhancementLevel.LIGHT)
        assertEquals(640, spec.width)
        assertEquals(360, spec.height)
    }

    @Test
    fun `already-large video is not upscaled beyond source`() {
        // 1920x1080 with MAX (target 1080) -> scale clamps to 1.0.
        val spec = computeOutputSpec(1920, 1080, 30, EnhancementLevel.MAX)
        assertEquals(1920, spec.width)
        assertEquals(1080, spec.height)
    }

    @Test
    fun `output dimensions are always even`() {
        val spec = computeOutputSpec(641, 481, 30, EnhancementLevel.MAX)
        assertEquals(0, spec.width % 2)
        assertEquals(0, spec.height % 2)
    }

    @Test
    fun `bitrate respects floor`() {
        val spec = computeOutputSpec(160, 120, 24, EnhancementLevel.LIGHT)
        assertTrue(spec.bitrate >= MIN_OUTPUT_BITRATE)
    }

    @Test
    fun `bitrate respects ceiling`() {
        val spec = computeOutputSpec(3840, 2160, 60, EnhancementLevel.MAX)
        assertTrue(spec.bitrate <= MAX_OUTPUT_BITRATE)
    }

    @Test
    fun `higher levels never produce lower bitrate for same source`() {
        val light = computeOutputSpec(1280, 720, 30, EnhancementLevel.LIGHT)
        val max = computeOutputSpec(1280, 720, 30, EnhancementLevel.MAX)
        assertTrue(max.bitrate >= light.bitrate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero dimension throws`() {
        computeOutputSpec(0, 720, 30, EnhancementLevel.STANDARD)
    }

    @Test
    fun `encoderBitrate respects floor and ceiling`() {
        assertTrue(encoderBitrate(160, 120, 24, 0.10) >= MIN_OUTPUT_BITRATE)
        assertTrue(encoderBitrate(3840, 2160, 60, 0.20) <= MAX_OUTPUT_BITRATE)
    }

    @Test
    fun `encoderBitrate matches computeOutputSpec for the same dimensions`() {
        // A 1080p clip at MAX is never upscaled, so the spec bitrate must equal the shared helper's.
        val spec = computeOutputSpec(1920, 1080, 30, EnhancementLevel.MAX)
        assertEquals(encoderBitrate(1920, 1080, 30, EnhancementLevel.MAX.bitsPerPixel), spec.bitrate)
    }

    @Test
    fun `ml speed modes trade resolution for speed in order`() {
        // Higher quality must never use a lower input cap; FAST must also skip frames.
        assertTrue(MlSpeedMode.FAST.inputShortSideCap < MlSpeedMode.BALANCED.inputShortSideCap)
        assertTrue(MlSpeedMode.BALANCED.inputShortSideCap < MlSpeedMode.QUALITY.inputShortSideCap)
        assertTrue(MlSpeedMode.FAST.frameStride > 1)
        assertEquals(1, MlSpeedMode.BALANCED.frameStride)
        assertEquals(1, MlSpeedMode.QUALITY.frameStride)
    }

    @Test
    fun `balanced mode preserves the original input cap`() {
        // BALANCED is the default and must match the engine's previous fixed 270px behaviour.
        assertEquals(270, MlSpeedMode.BALANCED.inputShortSideCap)
    }
}
