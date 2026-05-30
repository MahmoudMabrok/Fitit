package tools.mo3ta.fitit.ui.videoenhancer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Super-resolution video enhancer backed by a TensorFlow Lite model ([MlSuperResolution]).
 *
 * Unlike the GL pipeline, this path needs CPU bitmaps, so it pulls already-display-oriented frames
 * with [MediaMetadataRetriever.getFrameAtIndex] (API 28+), runs each through the model, and re-encodes
 * them. Because the frames are already upright the output carries no rotation hint, which sidesteps
 * the aspect-ratio pitfalls of the surface pipeline entirely.
 *
 * This is the heavier, experimental engine; it only runs when a model binary is bundled in assets and
 * the device is API 28+. Otherwise [VideoEnhancer] falls back to the GL engine.
 */
object MlVideoEnhancer {

    private const val VIDEO_MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L

    /**
     * How many decoded-and-downscaled frames the producer may stage ahead of inference. A small
     * buffer is enough to keep the inference/encode thread fed while bounding memory (each staged
     * bitmap is only ~a few hundred KB after downscaling).
     */
    private const val FRAME_PIPELINE_DEPTH = 3

    /** True when this engine can run: a model is bundled and the OS supports frame extraction. */
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && MlSuperResolution.isModelAvailable(context)

    /**
     * Downscales [frame] so its short side is at most [shortSideCap], recycling the original when a
     * smaller copy is produced. The model upscales by a fixed factor (4×), so a 270px input becomes
     * ~1080px output; capping the input bounds the per-frame tile count (the dominant cost) and is
     * what makes a clip finish in seconds rather than minutes. Returns [frame] unchanged when it is
     * already small enough. The caller owns (and must recycle) the returned bitmap.
     */
    private fun frameToInput(frame: Bitmap, shortSideCap: Int): Bitmap {
        val shortSide = min(frame.width, frame.height)
        if (shortSide <= shortSideCap) return frame
        val factor = shortSideCap.toFloat() / shortSide
        val w = (frame.width * factor).roundToInt().coerceAtLeast(1)
        val h = (frame.height * factor).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
        if (scaled !== frame) frame.recycle()
        return scaled
    }

    /**
     * Super-resolves [uri] into [output] using the bundled TensorFlow Lite model.
     *
     * The pipeline runs two stages concurrently with coroutines: a producer on [Dispatchers.IO]
     * decodes frames (the slow [MediaMetadataRetriever.getFrameAtIndex] path) and downscales them,
     * while a dedicated single-thread consumer runs inference and feeds the encoder. Overlapping
     * decode with inference hides the decode latency, which is otherwise serialised behind every
     * frame. The consumer is pinned to one thread because the TFLite interpreter (and the encoder's
     * EGL surface) are thread-affine and must not migrate across coroutine suspension points.
     *
     * [speedMode] trades quality for throughput by lowering the inference input resolution and,
     * for [MlSpeedMode.FAST], by skipping frames and repeating each upscaled frame to fill the gap.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun enhance(
        context: Context,
        uri: Uri,
        output: File,
        level: EnhancementLevel,
        speedMode: MlSpeedMode = MlSpeedMode.BALANCED,
        onProgress: (Float) -> Unit = {},
    ) = coroutineScope {
        val stride = speedMode.frameStride.coerceAtLeast(1)
        val cap = speedMode.inputShortSideCap

        // Read metadata up front so the producer knows its frame range; this retriever is then done.
        val meta = MediaMetadataRetriever()
        val frameRate: Int
        val frameCount: Int
        try {
            meta.setDataSource(context, uri)
            frameRate = meta
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.toInt()?.takeIf { it in 1..60 } ?: DEFAULT_FRAME_RATE
            val durationMs = meta
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val metaFrameCount = meta
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            frameCount = (metaFrameCount?.takeIf { it > 0 }
                ?: (durationMs / 1000.0 * frameRate).toInt()).coerceAtLeast(1)
        } finally {
            runCatching { meta.release() }
        }

        // Throttled progress: tile callbacks fire many times per frame, so only forward real steps.
        var lastReported = -1f
        fun report(frameIndex: Int, tileFraction: Float) {
            val p = ((frameIndex + tileFraction) / frameCount * 0.99f).coerceIn(0f, 0.99f)
            if (p - lastReported >= 0.001f) {
                lastReported = p
                onProgress(p)
            }
        }

        // Producer stage: decode + downscale on the IO pool, staging inputs for the consumer.
        val inputs = Channel<Bitmap>(capacity = FRAME_PIPELINE_DEPTH)
        val producer = launch(Dispatchers.IO) {
            val decoder = MediaMetadataRetriever()
            try {
                decoder.setDataSource(context, uri)
                var i = 0
                while (i < frameCount && isActive) {
                    val frame = runCatching { decoder.getFrameAtIndex(i) }.getOrNull()
                    if (frame != null) inputs.send(frameToInput(frame, cap))
                    i += stride
                }
            } finally {
                inputs.close()
                runCatching { decoder.release() }
            }
        }

        // Consumer stage: inference + encode pinned to a single thread (EGL/TFLite are thread-affine).
        val encodeExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "ml-video-encode") }
        try {
            withContext(encodeExecutor.asCoroutineDispatcher()) {
                val model = MlSuperResolution.create(context) ?: error("ML super-resolution model unavailable")
                var encoder: MediaCodec? = null
                var inputSurface: InputSurface? = null
                var muxer: MediaMuxer? = null
                val audio = AudioTrackCopier.create(context, uri)
                try {
                    val firstInput = inputs.receiveCatching().getOrNull()
                        ?: error("Could not read the first video frame")
                    val firstUpscaled = model.upscale(firstInput) { tf -> report(0, tf) }
                    firstInput.recycle()
                    val outWidth = evenDimension(firstUpscaled.width)
                    val outHeight = evenDimension(firstUpscaled.height)

                    val outFormat = MediaFormat.createVideoFormat(VIDEO_MIME, outWidth, outHeight).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, encoderBitrate(outWidth, outHeight, frameRate, level.bitsPerPixel))
                        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    }
                    val enc = MediaCodec.createEncoderByType(VIDEO_MIME).apply {
                        configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    }
                    encoder = enc
                    val surface = InputSurface(enc.createInputSurface())
                    inputSurface = surface
                    surface.makeCurrent()
                    val renderer = BitmapRenderer(outWidth, outHeight)
                    enc.start()

                    // Frames are already display-oriented, so no orientation hint is needed.
                    val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxer = mux

                    val bufferInfo = MediaCodec.BufferInfo()
                    var muxerStarted = false
                    var videoTrack = -1
                    val frameDurationUs = 1_000_000L / frameRate
                    var outIndex = 0

                    fun drainEncoder(endOfStream: Boolean) {
                        while (true) {
                            val index = enc.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                            when {
                                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    check(!muxerStarted) { "Encoder format changed twice" }
                                    videoTrack = mux.addTrack(enc.outputFormat)
                                    audio?.addTrack(mux)
                                    mux.start()
                                    muxerStarted = true
                                }
                                index >= 0 -> {
                                    val encoded = enc.getOutputBuffer(index)!!
                                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                                    if (bufferInfo.size != 0) {
                                        check(muxerStarted) { "Muxer received data before being started" }
                                        encoded.position(bufferInfo.offset)
                                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                                        mux.writeSampleData(videoTrack, encoded, bufferInfo)
                                    }
                                    enc.releaseOutputBuffer(index, false)
                                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                                }
                            }
                        }
                    }

                    // Draws one upscaled frame into the encoder, repeating it [stride] times so a
                    // strided (FAST) pass keeps the source frame rate and duration intact.
                    fun emitFrame(upscaled: Bitmap) {
                        val repeats = min(stride, frameCount - outIndex)
                        for (r in 0 until repeats) {
                            // The GPU delegate may have switched the current EGL context during
                            // inference, so re-bind the encoder surface before drawing into it.
                            surface.makeCurrent()
                            renderer.draw(upscaled)
                            surface.setPresentationTime(outIndex * frameDurationUs * 1000)
                            surface.swapBuffers()
                            outIndex++
                            drainEncoder(endOfStream = false)
                        }
                        upscaled.recycle()
                        report(outIndex, 0f)
                    }

                    emitFrame(firstUpscaled)
                    while (outIndex < frameCount) {
                        val input = inputs.receiveCatching().getOrNull() ?: break
                        val upscaled = model.upscale(input) { tf -> report(outIndex, tf) }
                        input.recycle()
                        emitFrame(upscaled)
                    }

                    enc.signalEndOfInputStream()
                    drainEncoder(endOfStream = true)
                    audio?.copyTo(mux)
                } finally {
                    runCatching { encoder?.stop() }
                    runCatching { encoder?.release() }
                    runCatching { muxer?.stop() }
                    runCatching { muxer?.release() }
                    runCatching { inputSurface?.release() }
                    runCatching { model.close() }
                }
            }
        } finally {
            // Stop the producer (it may be blocked on send) and drain any staged bitmaps it leaves.
            producer.cancel()
            for (leftover in inputs) runCatching { leftover.recycle() }
            encodeExecutor.shutdown()
        }
        onProgress(1f)
    }

    /** Copies a source audio track straight into the output muxer with no re-encoding. */
    private class AudioTrackCopier private constructor(
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
            val maxSize = format.getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
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

        private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
            if (containsKey(key) && getInteger(key) > 0) getInteger(key) else default

        companion object {
            fun create(context: Context, uri: Uri): AudioTrackCopier? {
                val extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) return AudioTrackCopier(extractor, i, fmt)
                }
                extractor.release()
                return null
            }
        }
    }
}
