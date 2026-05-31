package tools.mo3ta.fitit.ui.videoenhancer

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Defines how aggressively a video is enhanced.
 *
 * The numbers here are intentionally pure data so they can be unit-tested on the JVM without an
 * Android device. [VideoEnhancer] reads them to drive the OpenGL shader and the encoder config.
 *
 *  - [targetShortSidePx] the resolution we try to upscale the *shorter* video edge to.
 *  - [maxScale] hard cap on upscaling, so a tiny clip is never blown up into a huge file.
 *  - [bitsPerPixel] used to derive the encoder bitrate from the output resolution and frame rate.
 *  - [sharpen] strength of the unsharp-mask kernel (0 = off).
 *  - [saturation] / [contrast] simple colour grading multipliers (1 = unchanged).
 */
enum class EnhancementLevel(
    val targetShortSidePx: Int,
    val maxScale: Double,
    val bitsPerPixel: Double,
    val sharpen: Float,
    val saturation: Float,
    val contrast: Float,
) {
    LIGHT(targetShortSidePx = 480, maxScale = 1.0, bitsPerPixel = 0.10, sharpen = 0.30f, saturation = 1.05f, contrast = 1.03f),
    STANDARD(targetShortSidePx = 720, maxScale = 1.5, bitsPerPixel = 0.14, sharpen = 0.60f, saturation = 1.12f, contrast = 1.06f),
    MAX(targetShortSidePx = 1080, maxScale = 2.0, bitsPerPixel = 0.20, sharpen = 1.00f, saturation = 1.20f, contrast = 1.10f),
}

/** Resolved encoder configuration for a single enhance run. */
data class OutputSpec(
    val width: Int,
    val height: Int,
    val bitrate: Int,
)

/**
 * Selects which engine processes the frames.
 *
 *  - [GL] the always-available OpenGL ES sharpen/grade/upscale pipeline.
 *  - [ML] a TensorFlow Lite super-resolution model (see [MlSuperResolution]). When the model asset
 *    is not bundled with the build, [VideoEnhancer] transparently falls back to [GL].
 */
enum class EnhanceEngine { GL, ML }

/**
 * Tunes the speed/quality trade-off of the [EnhanceEngine.ML] super-resolution engine.
 *
 * The model runs once per tile and the tile count grows roughly with the *square* of the input
 * resolution, so [inputShortSideCap] is the dominant speed lever: each frame is downscaled so its
 * short side is at most this many pixels before super-resolution (the model then upscales 4×).
 * [frameStride] processes only every Nth frame and repeats each upscaled frame to fill the gap,
 * trading temporal smoothness for a near-linear speed-up. Pure data so it can be unit-tested on the
 * JVM without an Android device.
 *
 *  - [FAST] lowest input resolution and skips every other frame — best for previews / long clips.
 *  - [BALANCED] the default; matches the engine's original full-frame behaviour.
 *  - [QUALITY] highest input resolution, every frame — slowest, sharpest result.
 */
enum class MlSpeedMode(val inputShortSideCap: Int, val frameStride: Int) {
    FAST(inputShortSideCap = 160, frameStride = 2),
    BALANCED(inputShortSideCap = 270, frameStride = 1),
    QUALITY(inputShortSideCap = 360, frameStride = 1),
}

const val MIN_OUTPUT_BITRATE = 1_000_000      // 1 Mbps floor so low-res clips still look clean
const val MAX_OUTPUT_BITRATE = 40_000_000     // 40 Mbps ceiling to keep file sizes sane
const val DEFAULT_FRAME_RATE = 30
const val ENHANCER_MAX_DURATION_MS = 300_000L // 5 minutes, matches the splitter limit

/**
 * Default slice length for the chunked ML enhance pipeline ([MlChunkedVideoEnhancer]): the source is
 * cut into 0.1s pieces, each enhanced independently, then merged back together. Exposed as a tunable
 * constant so the slice size can be raised (fewer slices, less per-slice overhead) without touching
 * the pipeline code.
 */
const val ML_CHUNK_MS = 100L

/** Rounds [value] to the nearest even integer (H.264 encoders require even dimensions). */
fun evenDimension(value: Int): Int {
    val rounded = value.coerceAtLeast(2)
    return if (rounded % 2 == 0) rounded else rounded + 1
}

/**
 * Computes the output resolution and bitrate for the given source dimensions and [level].
 *
 * Upscaling is only ever applied up to [EnhancementLevel.maxScale]; a clip that is already larger
 * than the target is kept at its native resolution (we only re-grade and re-encode it).
 */
fun computeOutputSpec(
    srcWidth: Int,
    srcHeight: Int,
    frameRate: Int,
    level: EnhancementLevel,
): OutputSpec {
    require(srcWidth > 0 && srcHeight > 0) { "Source dimensions must be positive ($srcWidth x $srcHeight)" }

    val shortSide = min(srcWidth, srcHeight)
    val scale = (level.targetShortSidePx.toDouble() / shortSide).coerceIn(1.0, level.maxScale)

    val width = evenDimension((srcWidth * scale).roundToInt())
    val height = evenDimension((srcHeight * scale).roundToInt())

    val bitrate = encoderBitrate(width, height, frameRate, level.bitsPerPixel)

    return OutputSpec(width, height, bitrate)
}

/**
 * Derives an H.264 target bitrate from the output resolution and frame rate, clamped to the
 * [MIN_OUTPUT_BITRATE]..[MAX_OUTPUT_BITRATE] range. Shared by the GL and ML engines so both produce
 * comparably-sized files for the same output dimensions.
 */
fun encoderBitrate(width: Int, height: Int, frameRate: Int, bitsPerPixel: Double): Int {
    val fps = frameRate.coerceIn(1, 60)
    val raw = width.toLong() * height.toLong() * fps * bitsPerPixel
    return raw.toLong().coerceIn(MIN_OUTPUT_BITRATE.toLong(), MAX_OUTPUT_BITRATE.toLong()).toInt()
}

fun formatFileSize(bytes: Long): String {
    return if (bytes < 1_048_576L) {
        String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }
}
