package tools.mo3ta.fitit.ui.videoenhancer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A single tile of a frame to be super-resolved, expressed in source-pixel coordinates. Border tiles
 * may be smaller than the model's input size; only [srcW] x [srcH] pixels of their output are valid.
 */
data class SrTile(val srcX: Int, val srcY: Int, val srcW: Int, val srcH: Int)

/**
 * Splits a [width] x [height] frame into a grid of square tiles of side [tile]. Tiles along the
 * right/bottom edges are clamped to the frame bounds. Pure (no Android dependencies) so the tiling
 * maths can be unit-tested on the JVM.
 */
fun planSrTiles(width: Int, height: Int, tile: Int): List<SrTile> {
    require(width > 0 && height > 0) { "Frame dimensions must be positive ($width x $height)" }
    require(tile > 0) { "Tile size must be positive ($tile)" }
    val tiles = ArrayList<SrTile>()
    var y = 0
    while (y < height) {
        val h = minOf(tile, height - y)
        var x = 0
        while (x < width) {
            val w = minOf(tile, width - x)
            tiles.add(SrTile(x, y, w, h))
            x += tile
        }
        y += tile
    }
    return tiles
}

/**
 * Wraps a TensorFlow Lite super-resolution model (e.g. an ESRGAN export) and upscales [Bitmap]s by a
 * fixed integer [scale] factor.
 *
 * The class is engine-agnostic about the exact model: it reads the input/output tensor shapes to
 * derive the tile size and scale, tiles each frame so arbitrarily-sized video frames fit the model's
 * fixed input, and stitches the upscaled tiles back together. Float models are fed normalised RGB in
 * `[0, 1]`; quantised (uint8) models are fed raw bytes.
 *
 * The model file is expected at `assets/[MODEL_ASSET]`. It is intentionally *not* bundled with the
 * repo (multi-MB binary); when it is absent the video enhancer falls back to the GL pipeline. See the
 * KDoc on [VideoEnhancer] and the feature README for how to add one.
 */
class MlSuperResolution private constructor(
    private val interpreter: Interpreter,
    val tileSize: Int,
    val scale: Int,
    private val inputIsFloat: Boolean,
    private val outputIsFloat: Boolean,
) : AutoCloseable {

    private val inputPixels = IntArray(tileSize * tileSize)
    private val outTileSize = tileSize * scale
    private val outputPixels = IntArray(outTileSize * outTileSize)

    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(tileSize * tileSize * CHANNELS * if (inputIsFloat) 4 else 1)
        .order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(outTileSize * outTileSize * CHANNELS * if (outputIsFloat) 4 else 1)
        .order(ByteOrder.nativeOrder())

    /** Returns a new bitmap that is [scale]x the size of [source]. The caller owns the result. */
    fun upscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width * scale, source.height * scale, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        val tileBitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tileBitmap)

        for (t in planSrTiles(source.width, source.height, tileSize)) {
            // Copy the (possibly partial) tile into a full tileSize square, extending the edge pixels
            // so the model never sees black padding that would bleed into the stitched result.
            tileCanvas.drawBitmap(
                source,
                Rect(t.srcX, t.srcY, t.srcX + t.srcW, t.srcY + t.srcH),
                Rect(0, 0, t.srcW, t.srcH),
                null,
            )
            if (t.srcW < tileSize) {
                tileCanvas.drawBitmap(
                    tileBitmap,
                    Rect(t.srcW - 1, 0, t.srcW, t.srcH),
                    Rect(t.srcW, 0, tileSize, t.srcH),
                    null,
                )
            }
            if (t.srcH < tileSize) {
                tileCanvas.drawBitmap(
                    tileBitmap,
                    Rect(0, t.srcH - 1, tileSize, t.srcH),
                    Rect(0, t.srcH, tileSize, tileSize),
                    null,
                )
            }

            writeInput(tileBitmap)
            outputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            readOutput()

            // Only the valid (non-padded) portion of the upscaled tile is blitted into the result.
            val validW = t.srcW * scale
            val validH = t.srcH * scale
            val dstX = t.srcX * scale
            val dstY = t.srcY * scale
            resultCanvas.drawBitmap(
                Bitmap.createBitmap(outputPixels, outTileSize, outTileSize, Bitmap.Config.ARGB_8888),
                Rect(0, 0, validW, validH),
                Rect(dstX, dstY, dstX + validW, dstY + validH),
                null,
            )
        }
        tileBitmap.recycle()
        return result
    }

    private fun writeInput(bitmap: Bitmap) {
        inputBuffer.rewind()
        bitmap.getPixels(inputPixels, 0, tileSize, 0, 0, tileSize, tileSize)
        for (pixel in inputPixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (inputIsFloat) {
                inputBuffer.putFloat(r / 255f)
                inputBuffer.putFloat(g / 255f)
                inputBuffer.putFloat(b / 255f)
            } else {
                inputBuffer.put(r.toByte())
                inputBuffer.put(g.toByte())
                inputBuffer.put(b.toByte())
            }
        }
    }

    private fun readOutput() {
        outputBuffer.rewind()
        for (i in outputPixels.indices) {
            val r = nextChannel()
            val g = nextChannel()
            val b = nextChannel()
            outputPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun nextChannel(): Int {
        val value = if (outputIsFloat) (outputBuffer.float * 255f).toInt() else outputBuffer.get().toInt() and 0xFF
        return value.coerceIn(0, 255)
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "models/super_resolution.tflite"
        private const val CHANNELS = 3
        private const val DEFAULT_TILE = 64

        /** True when the model binary is present in assets (and can therefore be loaded). */
        fun isModelAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_ASSET).close()
            true
        }.getOrDefault(false)

        /**
         * Loads the model from assets, resolving its tile size and scale from the tensor shapes.
         * Returns null when the model is absent or cannot be interpreted.
         */
        fun create(context: Context): MlSuperResolution? = runCatching {
            val modelBuffer = context.assets.openFd(MODEL_ASSET).use { afd ->
                java.io.FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }
            val interpreter = Interpreter(modelBuffer, Interpreter.Options().apply { setNumThreads(4) })

            val inputTensor = interpreter.getInputTensor(0)
            val inputIsFloat = inputTensor.dataType() == DataType.FLOAT32
            // Fixed-shape models report their side length directly; dynamic models report <= 0, in
            // which case we resize the input to a default tile and let TFLite resolve the rest.
            var tile = inputTensor.shape().getOrElse(1) { -1 }
            if (tile <= 0) {
                interpreter.resizeInput(0, intArrayOf(1, DEFAULT_TILE, DEFAULT_TILE, CHANNELS))
                interpreter.allocateTensors()
                tile = DEFAULT_TILE
            }

            val outputTensor = interpreter.getOutputTensor(0)
            val outputIsFloat = outputTensor.dataType() == DataType.FLOAT32
            val outputSide = outputTensor.shape().getOrElse(1) { tile }
            val scale = (outputSide / tile).coerceAtLeast(1)

            MlSuperResolution(interpreter, tile, scale, inputIsFloat, outputIsFloat)
        }.getOrNull()
    }
}
