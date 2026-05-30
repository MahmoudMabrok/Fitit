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
import androidx.annotation.RequiresApi
import java.io.File
import java.nio.ByteBuffer

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

    /** True when this engine can run: a model is bundled and the OS supports frame extraction. */
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && MlSuperResolution.isModelAvailable(context)

    @RequiresApi(Build.VERSION_CODES.P)
    fun enhance(
        context: Context,
        uri: Uri,
        output: File,
        level: EnhancementLevel,
        onProgress: (Float) -> Unit = {},
    ) {
        val model = MlSuperResolution.create(context) ?: error("ML super-resolution model unavailable")
        val retriever = MediaMetadataRetriever()
        var encoder: MediaCodec? = null
        var inputSurface: InputSurface? = null
        var muxer: MediaMuxer? = null
        val audio = AudioTrackCopier.create(context, uri)

        try {
            retriever.setDataSource(context, uri)
            val frameRate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.toInt()?.takeIf { it in 1..60 } ?: DEFAULT_FRAME_RATE
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val metaFrameCount = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            val frameCount = (metaFrameCount?.takeIf { it > 0 }
                ?: (durationMs / 1000.0 * frameRate).toInt()).coerceAtLeast(1)

            val firstFrame = retriever.getFrameAtIndex(0) ?: error("Could not read the first video frame")
            val firstUpscaled = model.upscale(firstFrame)
            val outWidth = evenDimension(firstUpscaled.width)
            val outHeight = evenDimension(firstUpscaled.height)
            firstFrame.recycle()

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

            for (i in 0 until frameCount) {
                val upscaled = if (i == 0) {
                    firstUpscaled
                } else {
                    val frame = retriever.getFrameAtIndex(i) ?: continue
                    model.upscale(frame).also { frame.recycle() }
                }
                renderer.draw(upscaled)
                surface.setPresentationTime(i * frameDurationUs * 1000)
                surface.swapBuffers()
                upscaled.recycle()
                drainEncoder(endOfStream = false)
                onProgress((i + 1).toFloat() / frameCount * 0.99f)
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
            runCatching { retriever.release() }
            runCatching { model.close() }
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
