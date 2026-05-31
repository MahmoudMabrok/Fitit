package tools.mo3ta.fitit.ui.videoenhancer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * On-device video quality enhancer.
 *
 * Pipeline: [MediaExtractor] feeds the encoded video into a [MediaCodec] decoder that renders onto a
 * [SurfaceTexture]. Each decoded frame is drawn through [FrameRenderer]'s sharpen/grade shader onto
 * the input surface of a [MediaCodec] encoder configured at a higher resolution/bitrate, and the
 * result is written to an mp4 via [MediaMuxer]. The audio track (if any) is copied through untouched.
 *
 * This is a proof-of-concept: it does a single synchronous pass and must be called off the main
 * thread.
 */
object VideoEnhancer {

    private const val VIDEO_MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L

    /**
     * Enhances the video at [uri] into [output].
     *
     * @param engine which processing engine to use; [EnhanceEngine.ML] transparently falls back to
     *   [EnhanceEngine.GL] when no TensorFlow Lite model is bundled.
     * @param speedMode speed/quality trade-off for the [EnhanceEngine.ML] engine (ignored by GL).
     * @param inputShortSideCapOverride optional exact ML input-resolution cap; overrides [speedMode]'s.
     * @param onProgress invoked with a 0f..1f completion fraction.
     * @return the engine that actually ran, so callers can surface an ML→GL fallback to the user.
     */
    suspend fun enhance(
        context: Context,
        uri: Uri,
        output: File,
        level: EnhancementLevel,
        engine: EnhanceEngine = EnhanceEngine.GL,
        speedMode: MlSpeedMode = MlSpeedMode.BALANCED,
        inputShortSideCapOverride: Int? = null,
        onProgress: (Float) -> Unit = {},
    ): EnhanceEngine {
        if (engine == EnhanceEngine.ML &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            MlVideoEnhancer.isAvailable(context)
        ) {
            MlVideoEnhancer.enhance(context, uri, output, level, speedMode, inputShortSideCapOverride, onProgress)
            return EnhanceEngine.ML
        }

        val (srcWidth, srcHeight, rotation, durationUs, frameRate) = readVideoInfo(context, uri)

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var inputSurface: InputSurface? = null
        var outputSurface: OutputSurface? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = selectTrack(extractor, "video/")
            check(videoTrack >= 0) { "No video track found" }
            extractor.selectTrack(videoTrack)
            val inputFormat = extractor.getTrackFormat(videoTrack)

            // The decoder emits frames at the *coded* dimensions carried by the track format. For
            // rotated clips (most phone-shot portrait video) these differ from the display-oriented
            // dimensions reported by MediaMetadataRetriever, because rotation is stored separately.
            // Driving the encoder + renderer from the coded dimensions — and reproducing the
            // orientation purely through the muxer's orientation hint — keeps the output aspect ratio
            // identical to the source instead of squashing the frame into a swapped resolution.
            val codedWidth = inputFormat.intOrDefault(MediaFormat.KEY_WIDTH, srcWidth)
            val codedHeight = inputFormat.intOrDefault(MediaFormat.KEY_HEIGHT, srcHeight)
            val spec = computeOutputSpec(codedWidth, codedHeight, frameRate, level)

            // Encoder first, so we can hand its input surface to the EGL setup.
            val outFormat = MediaFormat.createVideoFormat(VIDEO_MIME, spec.width, spec.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, spec.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType(VIDEO_MIME).apply {
                configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            inputSurface = InputSurface(encoder.createInputSurface())
            inputSurface.makeCurrent()
            encoder.start()

            // Renderer + decoder output surface share the encoder's EGL context.
            val renderer = FrameRenderer(
                sharpen = level.sharpen,
                saturation = level.saturation,
                contrast = level.contrast,
                sourceWidth = codedWidth,
                sourceHeight = codedHeight,
                outputWidth = spec.width,
                outputHeight = spec.height,
            )
            outputSurface = OutputSurface(renderer)

            val decoderMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Video track has no MIME type")
            decoder = MediaCodec.createDecoderByType(decoderMime).apply {
                configure(inputFormat, outputSurface.surface, null, 0)
                start()
            }

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(rotation)

            transcodeVideo(
                extractor = extractor,
                decoder = decoder,
                encoder = encoder,
                inputSurface = inputSurface,
                outputSurface = outputSurface,
                muxer = muxer,
                context = context,
                uri = uri,
                durationUs = durationUs,
                onProgress = onProgress,
            )
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { outputSurface?.release() }
            runCatching { inputSurface?.release() }
            runCatching { extractor.release() }
        }
        onProgress(1f)
        return EnhanceEngine.GL
    }

    private fun transcodeVideo(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        inputSurface: InputSurface,
        outputSurface: OutputSurface,
        muxer: MediaMuxer,
        context: Context,
        uri: Uri,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ) {
        // Audio is copied verbatim; its track must be added before the muxer starts.
        val audioCopier = AudioCopier.create(context, uri)

        val bufferInfo = MediaCodec.BufferInfo()
        var videoMuxerTrack = -1
        var muxerStarted = false
        var inputDone = false
        var decoderDone = false
        var lastProgress = 0f

        while (true) {
            // 1. Feed encoded samples into the decoder.
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // 2. Drain the decoder, render through the shader into the encoder surface.
            if (!decoderDone) {
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val render = bufferInfo.size != 0
                    decoder.releaseOutputBuffer(outIndex, render)
                    if (render) {
                        outputSurface.awaitNewImage()
                        outputSurface.drawImage()
                        inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                        inputSurface.swapBuffers()
                        if (durationUs > 0) {
                            val p = (bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.99f)
                            if (p - lastProgress >= 0.01f) {
                                lastProgress = p
                                onProgress(p)
                            }
                        }
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderDone = true
                        encoder.signalEndOfInputStream()
                    }
                }
            }

            // 3. Drain the encoder into the muxer.
            val encIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Encoder format changed twice" }
                    videoMuxerTrack = muxer.addTrack(encoder.outputFormat)
                    audioCopier?.addTrack(muxer)
                    muxer.start()
                    muxerStarted = true
                }
                encIndex >= 0 -> {
                    val encBuf = encoder.getOutputBuffer(encIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0) {
                        check(muxerStarted) { "Muxer received data before being started" }
                        encBuf.position(bufferInfo.offset)
                        encBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoMuxerTrack, encBuf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        audioCopier?.copyTo(muxer)
    }

    /** Reads an integer key from a [MediaFormat], returning [default] when the key is absent. */
    private fun MediaFormat.intOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private data class VideoInfo(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val durationUs: Long,
        val frameRate: Int,
    )

    private fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameRate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?.toInt()
                ?.takeIf { it in 1..60 } ?: DEFAULT_FRAME_RATE
            check(width > 0 && height > 0) { "Could not read video dimensions" }
            return VideoInfo(width, height, rotation, durationMs * 1000, frameRate)
        } finally {
            retriever.release()
        }
    }

    /** Copies a source audio track straight into the output muxer with no re-encoding. */
    private class AudioCopier private constructor(
        private val extractor: MediaExtractor,
        private val sourceTrack: Int,
        private val format: MediaFormat,
    ) {
        private var muxerTrack = -1

        fun addTrack(muxer: MediaMuxer) {
            muxerTrack = muxer.addTrack(format)
        }

        fun copyTo(muxer: MediaMuxer) {
            extractor.selectTrack(sourceTrack)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val maxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).takeIf { it > 0 } ?: (256 * 1024)
            val buffer = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrack, buffer, info)
                extractor.advance()
            }
            extractor.release()
        }

        companion object {
            fun create(context: Context, uri: Uri): AudioCopier? {
                val extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) return AudioCopier(extractor, i, fmt)
                }
                extractor.release()
                return null
            }
        }
    }
}
