package tools.mo3ta.fitit.ui.audioenhancer

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Number of overlap-add blocks DTLN produces for a [signalLength]-sample signal,
 * given the [blockLen] window and [blockShift] hop. Mirrors the reference
 * implementation's `(len - (block_len - block_shift)) // block_shift`. Pure (no
 * Android dependencies) so the framing maths can be unit-tested on the JVM.
 */
fun dtlnBlockCount(signalLength: Int, blockLen: Int, blockShift: Int): Int {
    require(blockLen > 0 && blockShift > 0) { "block sizes must be positive" }
    if (signalLength < blockLen) return 0
    return (signalLength - (blockLen - blockShift)) / blockShift
}

/**
 * Wraps the two-stage **DTLN** (Dual-signal Transformation LSTM Network) speech
 * denoiser, exported as two stateful TensorFlow Lite models. It cleans a 16 kHz
 * mono signal in overlapping 512-sample blocks (128-sample hop, 75 % overlap):
 *
 *  1. rFFT the block; feed its magnitude (+ model_1's carried LSTM state) to
 *     `model_1`, which returns a spectral mask.
 *  2. Apply the mask to the magnitude, recombine with the original phase, inverse
 *     rFFT back to a 512-sample block.
 *  3. Feed that block (+ model_2's carried LSTM state) to `model_2`, which returns
 *     the cleaned block. Recombine all blocks with overlap-add.
 *
 * Both LSTM states are carried across blocks for causal, real-time operation.
 *
 * The class is built only via [create]; when the models are absent or fail to
 * load it returns null and the caller falls back to the DSP denoiser. Model
 * inference needs a device/emulator, so it is exercised only in instrumented
 * tests — the pure framing maths live in [dtlnBlockCount].
 */
class MlAudioDenoiser private constructor(
    private val model1: Interpreter,
    private val model2: Interpreter,
    private val delegate1: AutoCloseable?,
    private val delegate2: AutoCloseable?,
    private val dataIn1: Int,
    private val stateIn1: Int,
    private val dataOut1: Int,
    private val stateOut1: Int,
    private val dataIn2: Int,
    private val stateIn2: Int,
    private val dataOut2: Int,
    private val stateOut2: Int,
) : AutoCloseable {

    private val fftBins = BLOCK_LEN / 2 + 1

    // Carried LSTM states (model_1 and model_2), sized from the model tensors.
    private val state1 = directFloatBuffer(model1.getInputTensor(stateIn1).numBytes())
    private val state2 = directFloatBuffer(model2.getInputTensor(stateIn2).numBytes())

    private val magBuffer = directFloatBuffer(fftBins * 4)
    private val maskBuffer = directFloatBuffer(fftBins * 4)
    private val blockBuffer = directFloatBuffer(BLOCK_LEN * 4)
    private val cleanedBuffer = directFloatBuffer(BLOCK_LEN * 4)

    private val phase = FloatArray(fftBins)
    private val mag = FloatArray(fftBins)
    private val re = FloatArray(BLOCK_LEN)
    private val im = FloatArray(BLOCK_LEN)

    /**
     * Denoise a 16 kHz mono [signal], returning a new buffer the same length.
     * [onProgress] reports a 0f..1f fraction as blocks complete.
     */
    fun denoise(signal: FloatArray, onProgress: (Float) -> Unit = {}): FloatArray {
        val blocks = dtlnBlockCount(signal.size, BLOCK_LEN, BLOCK_SHIFT)
        val out = FloatArray(signal.size)
        if (blocks <= 0) return signal.copyOf()

        val inBuffer = FloatArray(BLOCK_LEN)
        val outBuffer = FloatArray(BLOCK_LEN)
        for (idx in 0 until blocks) {
            // Slide the input buffer left by one hop and append the next hop of
            // samples at the end (zero-padding past the signal's end).
            System.arraycopy(inBuffer, BLOCK_SHIFT, inBuffer, 0, BLOCK_LEN - BLOCK_SHIFT)
            val start = idx * BLOCK_SHIFT
            for (i in 0 until BLOCK_SHIFT) {
                val src = start + (BLOCK_LEN - BLOCK_SHIFT) + i
                inBuffer[BLOCK_LEN - BLOCK_SHIFT + i] = if (src < signal.size) signal[src] else 0f
            }

            val estimated = runBlock(inBuffer)

            // Overlap-add: shift the output buffer, then accumulate the new block.
            System.arraycopy(outBuffer, BLOCK_SHIFT, outBuffer, 0, BLOCK_LEN - BLOCK_SHIFT)
            for (i in BLOCK_LEN - BLOCK_SHIFT until BLOCK_LEN) outBuffer[i] = 0f
            for (i in 0 until BLOCK_LEN) outBuffer[i] += estimated[i]

            // Emit the first hop of the output buffer.
            for (i in 0 until BLOCK_SHIFT) {
                val o = start + i
                if (o < out.size) out[o] = outBuffer[i]
            }
            if (idx % 16 == 0) onProgress((idx + 1).toFloat() / blocks)
        }
        onProgress(1f)
        return out
    }

    /** Runs one 512-sample block through both DTLN stages, carrying LSTM state. */
    private fun runBlock(block: FloatArray): FloatArray {
        // Stage 1: rFFT magnitude + phase.
        for (i in 0 until BLOCK_LEN) {
            re[i] = block[i]
            im[i] = 0f
        }
        AudioDsp.fft(re, im, false)
        for (k in 0 until fftBins) {
            val r = re[k]
            val i = im[k]
            mag[k] = sqrt(r * r + i * i)
            phase[k] = atan2(i, r)
        }

        magBuffer.rewind()
        for (k in 0 until fftBins) magBuffer.putFloat(mag[k])

        // model_1: magnitude (+ state) -> mask (+ state).
        state1.rewind(); magBuffer.rewind(); maskBuffer.rewind()
        val out1 = HashMap<Int, Any>()
        out1[dataOut1] = maskBuffer
        out1[stateOut1] = state1.duplicateForOutput()
        val in1 = arrayOfNulls<Any>(maxOf(dataIn1, stateIn1) + 1)
        in1[dataIn1] = magBuffer
        in1[stateIn1] = state1
        model1.runForMultipleInputsOutputs(in1, out1)
        copyState(out1[stateOut1] as ByteBuffer, state1)

        // Apply mask, recombine with original phase, inverse rFFT.
        maskBuffer.rewind()
        for (k in 0 until fftBins) {
            val masked = mag[k] * maskBuffer.float
            re[k] = masked * cos(phase[k])
            im[k] = masked * sin(phase[k])
        }
        // Hermitian-symmetric mirror for the negative frequencies.
        for (k in fftBins until BLOCK_LEN) {
            re[k] = re[BLOCK_LEN - k]
            im[k] = -im[BLOCK_LEN - k]
        }
        AudioDsp.fft(re, im, true)

        blockBuffer.rewind()
        for (i in 0 until BLOCK_LEN) blockBuffer.putFloat(re[i])

        // model_2: time-domain block (+ state) -> cleaned block (+ state).
        state2.rewind(); blockBuffer.rewind(); cleanedBuffer.rewind()
        val out2 = HashMap<Int, Any>()
        out2[dataOut2] = cleanedBuffer
        out2[stateOut2] = state2.duplicateForOutput()
        val in2 = arrayOfNulls<Any>(maxOf(dataIn2, stateIn2) + 1)
        in2[dataIn2] = blockBuffer
        in2[stateIn2] = state2
        model2.runForMultipleInputsOutputs(in2, out2)
        copyState(out2[stateOut2] as ByteBuffer, state2)

        cleanedBuffer.rewind()
        val cleaned = FloatArray(BLOCK_LEN)
        for (i in 0 until BLOCK_LEN) cleaned[i] = cleanedBuffer.float
        return cleaned
    }

    private fun ByteBuffer.duplicateForOutput(): ByteBuffer =
        ByteBuffer.allocateDirect(capacity()).order(ByteOrder.nativeOrder())

    private fun copyState(from: ByteBuffer, into: ByteBuffer) {
        from.rewind(); into.rewind()
        into.put(from)
        into.rewind()
    }

    override fun close() {
        runCatching { model1.close() }
        runCatching { model2.close() }
        runCatching { delegate1?.close() }
        runCatching { delegate2?.close() }
    }

    companion object {
        const val MODEL_1_ASSET = "models/model_1.tflite"
        const val MODEL_2_ASSET = "models/model_2.tflite"

        /** DTLN's fixed block geometry and sample rate. */
        const val SAMPLE_RATE = 16_000
        const val BLOCK_LEN = 512
        const val BLOCK_SHIFT = 128

        /** True when both DTLN model binaries are present in assets. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(MODEL_1_ASSET).close()
            context.assets.open(MODEL_2_ASSET).close()
            true
        }.getOrDefault(false)

        /**
         * Loads both DTLN models from assets, resolving the data/state tensor
         * indices from their shapes (the state tensors are rank 4, the data
         * tensors rank 3). Returns null when a model is absent or cannot load.
         */
        fun create(context: Context): MlAudioDenoiser? = runCatching {
            val (m1, d1) = buildInterpreter(loadModel(context, MODEL_1_ASSET))
            val (m2, d2) = buildInterpreter(loadModel(context, MODEL_2_ASSET))

            val (dataIn1, stateIn1) = resolveDataStateIndices(m1.inputTensorCount) { m1.getInputTensor(it).shape().size }
            val (dataOut1, stateOut1) = resolveDataStateIndices(m1.outputTensorCount) { m1.getOutputTensor(it).shape().size }
            val (dataIn2, stateIn2) = resolveDataStateIndices(m2.inputTensorCount) { m2.getInputTensor(it).shape().size }
            val (dataOut2, stateOut2) = resolveDataStateIndices(m2.outputTensorCount) { m2.getOutputTensor(it).shape().size }

            MlAudioDenoiser(
                m1, m2, d1, d2,
                dataIn1, stateIn1, dataOut1, stateOut1,
                dataIn2, stateIn2, dataOut2, stateOut2,
            )
        }.getOrNull()

        /** The rank-3 tensor carries the data; the rank-4 tensor carries the LSTM state. */
        private fun resolveDataStateIndices(count: Int, rank: (Int) -> Int): Pair<Int, Int> {
            var data = 0
            var state = if (count > 1) 1 else 0
            for (i in 0 until count) {
                if (rank(i) >= 4) state = i else data = i
            }
            return data to state
        }

        private fun loadModel(context: Context, asset: String): ByteBuffer =
            context.assets.openFd(asset).use { afd ->
                java.io.FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }

        /**
         * Builds an [Interpreter], preferring the GPU delegate when supported and
         * falling back to a multi-threaded CPU interpreter. Returns the interpreter
         * with the delegate to close (or null when running on CPU).
         */
        private fun buildInterpreter(model: ByteBuffer): Pair<Interpreter, AutoCloseable?> {
            try {
                CompatibilityList().use { compat ->
                    if (compat.isDelegateSupportedOnThisDevice) {
                        val gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                        try {
                            val options = Interpreter.Options().apply { addDelegate(gpu) }
                            val closer = object : AutoCloseable {
                                override fun close() { gpu.close() }
                            }
                            return Interpreter(model, options) to closer
                        } catch (t: Throwable) {
                            runCatching { gpu.close() }
                            throw t
                        }
                    }
                }
            } catch (_: Throwable) {
                // GPU not usable on this device/model — fall through to CPU.
            }
            return Interpreter(model, Interpreter.Options().apply { setNumThreads(2) }) to null
        }

        private fun directFloatBuffer(numBytes: Int): ByteBuffer =
            ByteBuffer.allocateDirect(numBytes).order(ByteOrder.nativeOrder())
    }
}
