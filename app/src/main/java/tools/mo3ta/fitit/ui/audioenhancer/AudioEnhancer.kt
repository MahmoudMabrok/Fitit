package tools.mo3ta.fitit.ui.audioenhancer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device audio enhancer. Decodes a compressed audio file to PCM with the
 * platform [MediaCodec], runs the [AudioDsp] enhancement pipeline, then
 * re-encodes to AAC inside an .m4a container. No native libraries (FFmpeg etc.)
 * are required.
 */
object AudioEnhancer {

    /** Reject very long clips: the whole signal is held in memory for the FFT. */
    const val MAX_DURATION_MS = 10 * 60 * 1000L

    private const val TIMEOUT_US = 10_000L
    private const val OUTPUT_BITRATE = 128_000

    private data class DecodedAudio(
        val pcm: ShortArray,
        val sampleRate: Int,
        val channels: Int,
    )

    /**
     * Result of an enhancement: the output .m4a [file] and whether AI noise
     * removal was requested but [aiFellBack] to the DSP denoiser (e.g. because the
     * DTLN models are not bundled or failed to load on this device).
     */
    data class EnhanceResult(val file: File, val aiFellBack: Boolean)

    /**
     * Enhance the audio at [uri] and return the resulting .m4a cache file.
     *
     * @param useAi when true, denoise with the DTLN AI engine (falling back to the
     *   DSP spectral-gating denoiser when it is unavailable). The clarity/loudness
     *   stages always run afterwards on the platform pipeline.
     * @param onProgress reported in [0, 1] across decode (0–0.5), processing
     *   (0.5–0.7) and encode (0.7–1.0).
     */
    fun enhance(
        context: Context,
        uri: Uri,
        level: AudioEnhancementLevel,
        useAi: Boolean = false,
        onProgress: (Float) -> Unit,
    ): EnhanceResult {
        val outputDir = File(context.cacheDir, "enhanced_audio").also { it.mkdirs() }
        val input = copyToCache(context, uri, outputDir)
        try {
            return enhance(context, input, level, useAi, onProgress)
        } finally {
            input.delete()
        }
    }

    /**
     * Enhance a local [file] directly and return the resulting .m4a cache file.
     *
     * Used by batch enhancement, where each pass feeds the previous pass's output
     * file back in as the next input. The input [file] is left intact; the caller
     * owns its lifecycle.
     */
    fun enhance(
        context: Context,
        file: File,
        level: AudioEnhancementLevel,
        useAi: Boolean = false,
        onProgress: (Float) -> Unit,
    ): EnhanceResult {
        val outputDir = File(context.cacheDir, "enhanced_audio").also { it.mkdirs() }
        val input = file
        onProgress(0f)
        run {
            val decoded = decode(input.absolutePath) { p -> onProgress(p * 0.5f) }
            if (decoded.pcm.isEmpty()) throw IOException("NO_AUDIO_TRACK")

            val channels = AudioDsp.deinterleave(decoded.pcm, decoded.channels)

            // AI denoise (DTLN) runs first, replacing the DSP denoise stage; the
            // EQ / compression / loudness stages always follow on the channels.
            var aiFellBack = false
            if (useAi) {
                val denoised = aiDenoise(context, channels, decoded.sampleRate) { p ->
                    onProgress(0.5f + p * 0.15f)
                }
                if (denoised != null) {
                    // The AI engine collapsed the audio to a mono clean signal;
                    // mirror it across every channel, then run the clarity /
                    // loudness stages while skipping the DSP denoise.
                    for (c in channels.indices) channels[c] = denoised.copyOf()
                    enhanceChannels(channels, decoded.sampleRate, level, skipDenoise = true) { p ->
                        onProgress(0.65f + p * 0.05f)
                    }
                } else {
                    aiFellBack = true
                }
            }
            if (!useAi || aiFellBack) {
                enhanceChannels(channels, decoded.sampleRate, level) { p ->
                    onProgress(0.5f + p * 0.2f)
                }
            }
            val processed = AudioDsp.interleave(channels)

            val output = File(outputDir, "enhanced_${System.currentTimeMillis()}.m4a")
            encode(processed, decoded.sampleRate, decoded.channels, output.absolutePath) { p ->
                onProgress(0.7f + p * 0.3f)
            }
            onProgress(1f)
            return EnhanceResult(output, aiFellBack)
        }
    }

    /**
     * Runs the DTLN AI denoiser on a mono down-mix at 16 kHz, then resamples the
     * cleaned signal back to [sampleRate]. Returns null (so the caller can fall
     * back to the DSP denoiser) when the models are unavailable or fail to load.
     */
    private fun aiDenoise(
        context: Context,
        channels: Array<FloatArray>,
        sampleRate: Int,
        onProgress: (Float) -> Unit,
    ): FloatArray? {
        if (!MlAudioDenoiser.isAvailable(context)) return null
        val denoiser = MlAudioDenoiser.create(context) ?: return null
        return try {
            val mono = AudioDsp.mixToMono(channels)
            val model = AudioDsp.resampleLinear(mono, sampleRate, MlAudioDenoiser.SAMPLE_RATE)
            val cleaned = denoiser.denoise(model, onProgress)
            AudioDsp.resampleLinear(cleaned, MlAudioDenoiser.SAMPLE_RATE, sampleRate)
        } catch (_: Exception) {
            null
        } finally {
            denoiser.close()
        }
    }

    // ------------------------------------------------------------------
    // Decode -> interleaved 16-bit PCM.
    // ------------------------------------------------------------------

    private fun decode(path: String, onProgress: (Float) -> Unit): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val track = selectAudioTrack(extractor)
        if (track < 0) throw IOException("NO_AUDIO_TRACK")
        extractor.selectTrack(track)

        val inputFormat = extractor.getTrackFormat(track)
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION))
            inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs > MAX_DURATION_MS * 1000) throw IOException("TOO_LONG")

        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IOException("NO_AUDIO_TRACK")
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        // Ask the decoder for 16-bit PCM so the bytes we read back always match.
        inputFormat.setInteger(
            MediaFormat.KEY_PCM_ENCODING,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val pcmStream = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            if (durationUs > 0) {
                                onProgress((extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                            }
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            val chunk = ByteArray(info.size)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.get(chunk)
                            pcmStream.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        onProgress(1f)

        val bytes = pcmStream.toByteArray()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return DecodedAudio(shorts, sampleRate, channels.coerceAtLeast(1))
    }

    // ------------------------------------------------------------------
    // Encode interleaved 16-bit PCM -> AAC (.m4a).
    // ------------------------------------------------------------------

    private fun encode(
        pcm: ShortArray,
        sampleRate: Int,
        channels: Int,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ) {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false

        // Interleaved PCM as little-endian bytes for feeding the encoder.
        val pcmBytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) pcmBytes.putShort(s)
        val data = pcmBytes.array()

        val bytesPerFrame = channels * 2
        val totalFrames = if (bytesPerFrame > 0) data.size / bytesPerFrame else 0
        var bytesFed = 0
        var framesFed = 0L
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = encoder.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val capacity = inBuf.capacity()
                        // Keep the chunk aligned to whole frames.
                        var chunk = minOf(capacity, data.size - bytesFed)
                        if (bytesPerFrame > 0) chunk -= chunk % bytesPerFrame
                        if (chunk <= 0) {
                            encoder.queueInputBuffer(
                                inIndex, 0, 0, ptsForFrame(framesFed, sampleRate),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            inBuf.put(data, bytesFed, chunk)
                            val pts = ptsForFrame(framesFed, sampleRate)
                            encoder.queueInputBuffer(inIndex, 0, chunk, pts, 0)
                            bytesFed += chunk
                            framesFed += chunk / bytesPerFrame
                        }
                    }
                }
                val outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIndex)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0 && muxerStarted) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(muxerTrack, outBuf, info)
                            if (totalFrames > 0) {
                                onProgress((framesFed.toFloat() / totalFrames).coerceIn(0f, 1f))
                            }
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }
        } finally {
            encoder.stop()
            encoder.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
        onProgress(1f)
    }

    private fun ptsForFrame(frame: Long, sampleRate: Int): Long =
        if (sampleRate > 0) frame * 1_000_000L / sampleRate else 0L

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun copyToCache(context: Context, uri: Uri, dir: File): File {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input")
        val temp = File(dir, "src_${System.currentTimeMillis()}")
        input.use { ins -> temp.outputStream().use { outs -> ins.copyTo(outs) } }
        return temp
    }
}
