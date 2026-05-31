package tools.mo3ta.fitit.ui.audioenhancer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure-Kotlin DSP used by the Audio Enhancer. Everything here operates on plain
 * float sample buffers (one channel, samples in roughly [-1, 1]) and has no
 * Android dependencies, so it is fully unit-testable on the JVM.
 *
 * The noise reducer is a spectral-gating denoiser (a spectral-subtraction
 * variant): it estimates a per-frequency noise floor from the quietest frames of
 * the clip and softly attenuates time-frequency bins that sit at or below that
 * floor. The STFT uses a periodic Hann window with 75% overlap, which gives
 * unity-gain overlap-add reconstruction.
 */
object AudioDsp {

    private const val EPS = 1e-10f

    // ---------------------------------------------------------------------
    // Conversions between 16-bit PCM and float, and channel (de)interleaving.
    // ---------------------------------------------------------------------

    /** Split interleaved 16-bit PCM into [channels] float buffers in [-1, 1]. */
    fun deinterleave(pcm: ShortArray, channels: Int): Array<FloatArray> {
        require(channels >= 1) { "channels must be >= 1" }
        val frames = pcm.size / channels
        val out = Array(channels) { FloatArray(frames) }
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                out[c][f] = pcm[i++] / 32768f
            }
        }
        return out
    }

    /** Interleave per-channel float buffers back into clipped 16-bit PCM. */
    fun interleave(channels: Array<FloatArray>): ShortArray {
        val numCh = channels.size
        require(numCh >= 1) { "need at least one channel" }
        val frames = channels[0].size
        val out = ShortArray(frames * numCh)
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until numCh) {
                val v = channels[c][f] * 32768f
                out[i++] = v.coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }
        return out
    }

    // ---------------------------------------------------------------------
    // FFT: iterative radix-2 Cooley-Tukey, in place. Length must be a power of 2.
    // ---------------------------------------------------------------------

    fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        require(n == im.size) { "re/im length mismatch" }
        require(n > 0 && (n and (n - 1)) == 0) { "FFT length must be a power of 2" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var length = 2
        while (length <= n) {
            val ang = (2.0 * PI / length) * (if (inverse) 1.0 else -1.0)
            val wre = cos(ang).toFloat()
            val wim = sin(ang).toFloat()
            val half = length / 2
            var i = 0
            while (i < n) {
                var cre = 1f
                var cim = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val tre = cre * re[b] - cim * im[b]
                    val tim = cre * im[b] + cim * re[b]
                    re[b] = re[a] - tre
                    im[b] = im[a] - tim
                    re[a] += tre
                    im[a] += tim
                    val ncre = cre * wre - cim * wim
                    cim = cre * wim + cim * wre
                    cre = ncre
                }
                i += length
            }
            length = length shl 1
        }

        if (inverse) {
            val inv = 1f / n
            for (i in 0 until n) {
                re[i] *= inv
                im[i] *= inv
            }
        }
    }

    private fun hann(n: Int): FloatArray =
        FloatArray(n) { 0.5f - 0.5f * cos(2.0 * PI * it / n).toFloat() }

    // ---------------------------------------------------------------------
    // Spectral-gating noise reduction.
    // ---------------------------------------------------------------------

    /**
     * Reduce stationary background noise in [signal].
     *
     * @param strength scales the suppression threshold; >1 removes more noise
     *        (and more signal), <1 is gentler. 0 disables the stage.
     * @param nStd how many std-devs above the mean noise magnitude to gate.
     * @param noisePercentile percentage of quietest frames treated as noise.
     * @param fftSize STFT window size (power of 2).
     * @param floorDb residual gain left in gated bins, to avoid musical-noise.
     */
    fun reduceNoise(
        signal: FloatArray,
        strength: Float = 1f,
        nStd: Float = 1.5f,
        noisePercentile: Float = 10f,
        fftSize: Int = 1024,
        floorDb: Float = -18f,
    ): FloatArray {
        if (strength <= 0f || signal.size < fftSize) return signal
        val hop = fftSize / 4
        val win = hann(fftSize)
        val numFrames = 1 + (signal.size - fftSize) / hop

        // First pass: per-frame magnitude spectra.
        val mags = Array(numFrames) { FloatArray(fftSize) }
        val frameEnergy = FloatArray(numFrames)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        for (f in 0 until numFrames) {
            val start = f * hop
            for (k in 0 until fftSize) {
                re[k] = signal[start + k] * win[k]
                im[k] = 0f
            }
            fft(re, im, false)
            var energy = 0f
            for (k in 0 until fftSize) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                mags[f][k] = m
                energy += m
            }
            frameEnergy[f] = energy
        }

        // Estimate per-bin noise floor from the quietest frames.
        val keep = max(1, (numFrames * noisePercentile / 100f).toInt())
        val quietFrames = frameEnergy.indices.sortedBy { frameEnergy[it] }.take(keep)
        val noiseMean = FloatArray(fftSize)
        val noiseStd = FloatArray(fftSize)
        for (k in 0 until fftSize) {
            var sum = 0f
            for (f in quietFrames) sum += mags[f][k]
            val mean = sum / quietFrames.size
            var varSum = 0f
            for (f in quietFrames) {
                val d = mags[f][k] - mean
                varSum += d * d
            }
            noiseMean[k] = mean
            noiseStd[k] = sqrt(varSum / quietFrames.size)
        }
        val floor = pow10(floorDb / 20f)
        val threshold = FloatArray(fftSize) { (noiseMean[it] + nStd * noiseStd[it]) * strength }

        // Second pass: apply soft mask and overlap-add.
        val out = FloatArray(signal.size + fftSize)
        val wsum = FloatArray(signal.size + fftSize)
        for (f in 0 until numFrames) {
            val start = f * hop
            for (k in 0 until fftSize) {
                re[k] = signal[start + k] * win[k]
                im[k] = 0f
            }
            fft(re, im, false)
            for (k in 0 until fftSize) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                var m = (mag - threshold[k]) / (mag + EPS)
                m = m.coerceIn(0f, 1f)
                m = floor + (1f - floor) * m
                re[k] *= m
                im[k] *= m
            }
            fft(re, im, true)
            for (k in 0 until fftSize) {
                out[start + k] += re[k] * win[k]
                wsum[start + k] += win[k] * win[k]
            }
        }
        val result = FloatArray(signal.size)
        for (i in signal.indices) {
            val w = if (wsum[i] < 1e-8f) 1e-8f else wsum[i]
            result[i] = out[i] / w
        }
        return result
    }

    // ---------------------------------------------------------------------
    // Biquad filters (Robert Bristow-Johnson cookbook), Direct Form I.
    // ---------------------------------------------------------------------

    fun highPass(signal: FloatArray, sampleRate: Int, cutoffHz: Float, q: Float = 0.707f): FloatArray {
        if (cutoffHz <= 0f || cutoffHz >= sampleRate / 2f) return signal
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val b0 = (1.0 + cosw) / 2.0
        val b1 = -(1.0 + cosw)
        val b2 = (1.0 + cosw) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosw
        val a2 = 1.0 - alpha
        return applyBiquad(signal, b0, b1, b2, a0, a1, a2)
    }

    fun lowPass(signal: FloatArray, sampleRate: Int, cutoffHz: Float, q: Float = 0.707f): FloatArray {
        if (cutoffHz <= 0f || cutoffHz >= sampleRate / 2f) return signal
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val b0 = (1.0 - cosw) / 2.0
        val b1 = 1.0 - cosw
        val b2 = (1.0 - cosw) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosw
        val a2 = 1.0 - alpha
        return applyBiquad(signal, b0, b1, b2, a0, a1, a2)
    }

    /**
     * Peaking EQ that boosts (or cuts) a band of [gainDb] dB centred on [centerHz].
     * Used to add a gentle "presence" lift for speech clarity. [q] sets the
     * bandwidth (lower = wider). Uses the RBJ cookbook formulas.
     */
    fun peaking(
        signal: FloatArray,
        sampleRate: Int,
        centerHz: Float,
        gainDb: Float,
        q: Float = 0.707f,
    ): FloatArray {
        if (centerHz <= 0f || centerHz >= sampleRate / 2f || gainDb == 0f) return signal
        val a = pow10(gainDb / 40f).toDouble()
        val w0 = 2.0 * PI * centerHz / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosw
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosw
        val a2 = 1.0 - alpha / a
        return applyBiquad(signal, b0, b1, b2, a0, a1, a2)
    }

    private fun applyBiquad(
        x: FloatArray,
        b0: Double, b1: Double, b2: Double,
        a0: Double, a1: Double, a2: Double,
    ): FloatArray {
        val nb0 = (b0 / a0).toFloat()
        val nb1 = (b1 / a0).toFloat()
        val nb2 = (b2 / a0).toFloat()
        val na1 = (a1 / a0).toFloat()
        val na2 = (a2 / a0).toFloat()
        val y = FloatArray(x.size)
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        for (i in x.indices) {
            val xi = x[i]
            val yi = nb0 * xi + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            y[i] = yi
            x2 = x1; x1 = xi
            y2 = y1; y1 = yi
        }
        return y
    }

    // ---------------------------------------------------------------------
    // Levels.
    // ---------------------------------------------------------------------

    fun rms(x: FloatArray): Float {
        if (x.isEmpty()) return 0f
        var sum = 0.0
        for (v in x) sum += v.toDouble() * v
        return sqrt(sum / x.size).toFloat()
    }

    fun peak(channels: Array<FloatArray>): Float {
        var p = 0f
        for (ch in channels) for (v in ch) {
            val a = if (v < 0) -v else v
            if (a > p) p = a
        }
        return p
    }

    /**
     * Peak-normalize a set of channels in place so the loudest sample across all
     * channels reaches [targetDb] dBFS. Returns the gain applied.
     */
    fun peakNormalize(channels: Array<FloatArray>, targetDb: Float = -1f): Float {
        val p = peak(channels)
        if (p < EPS) return 1f
        val gain = pow10(targetDb / 20f) / p
        for (ch in channels) for (i in ch.indices) ch[i] *= gain
        return gain
    }

    /**
     * Single-band feed-forward compressor. A linear-domain envelope follower with
     * [attackMs] / [releaseMs] ballistics applies soft gain reduction once the
     * level rises above [thresholdDb] (dBFS) at the given [ratio], followed by
     * [makeupDb] make-up gain. Lowers the crest factor so quiet passages come
     * forward. Returns a new buffer; [signal] is left untouched.
     */
    fun compress(
        signal: FloatArray,
        sampleRate: Int,
        thresholdDb: Float,
        ratio: Float,
        attackMs: Float = 10f,
        releaseMs: Float = 100f,
        makeupDb: Float = 0f,
    ): FloatArray {
        if (ratio <= 1f || signal.isEmpty() || sampleRate <= 0) return signal
        val attackCoef = exp(-1.0 / (sampleRate * attackMs / 1000.0))
        val releaseCoef = exp(-1.0 / (sampleRate * releaseMs / 1000.0))
        val threshold = pow10(thresholdDb / 20f).toDouble()
        val makeup = pow10(makeupDb / 20f).toDouble()
        val invRatio = 1.0 / ratio
        val out = FloatArray(signal.size)
        var env = 0.0
        for (i in signal.indices) {
            val x = signal[i].toDouble()
            val a = if (x < 0) -x else x
            env = if (a > env) attackCoef * env + (1.0 - attackCoef) * a
            else releaseCoef * env + (1.0 - releaseCoef) * a
            val gain = if (env <= threshold || env < 1e-9) 1.0
            else (threshold * Math.pow(env / threshold, invRatio)) / env
            out[i] = (x * gain * makeup).toFloat()
        }
        return out
    }

    /**
     * Loudness-normalize [channels] in place towards an RMS target of [targetRmsDb]
     * (an EBU-R128-style loudness target), then engage a smooth true-peak soft
     * limiter so no sample exceeds [ceilingDb]. Unlike [peakNormalize] this raises
     * the perceived volume of quiet recordings; the `tanh` limiter only kicks in
     * when the gained signal would otherwise clip, so clean material keeps its
     * exact target level. Returns the loudness gain applied.
     */
    fun loudnessNormalize(
        channels: Array<FloatArray>,
        targetRmsDb: Float = -16f,
        ceilingDb: Float = -1f,
    ): Float {
        var sumSq = 0.0
        var count = 0L
        for (ch in channels) for (v in ch) {
            sumSq += v.toDouble() * v
            count++
        }
        if (count == 0L) return 1f
        val rms = sqrt(sumSq / count).toFloat()
        if (rms < EPS) return 1f
        val gain = pow10(targetRmsDb / 20f) / rms
        val ceiling = pow10(ceilingDb / 20f)
        // Apply the linear loudness gain and track the resulting true peak.
        var peak = 0f
        for (ch in channels) for (i in ch.indices) {
            val g = ch[i] * gain
            ch[i] = g
            val abs = if (g < 0) -g else g
            if (abs > peak) peak = abs
        }
        // Only saturate if the gained signal would exceed the ceiling.
        if (peak > ceiling) {
            for (ch in channels) for (i in ch.indices) {
                ch[i] = (ceiling * tanh(ch[i] / ceiling)).toFloat()
            }
        }
        return gain
    }

    private fun pow10(x: Float): Float = Math.exp((x * ln(10f)).toDouble()).toFloat()

    fun clamp(v: Float, lo: Float, hi: Float): Float = min(max(v, lo), hi)
}
