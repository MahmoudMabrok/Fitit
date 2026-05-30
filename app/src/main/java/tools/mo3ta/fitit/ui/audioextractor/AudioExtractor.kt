package tools.mo3ta.fitit.ui.audioextractor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Output formats supported by the audio extractor. */
enum class AudioFormat(val extension: String, val mimeType: String) {
    /** Uncompressed 16-bit PCM in a WAV container (decoded on-device). */
    WAV("wav", "audio/wav"),

    /** Compressed AAC audio copied into an MP4/M4A container (no re-encoding). */
    M4A("m4a", "audio/mp4")
}

private const val DEQUEUE_TIMEOUT_US = 10_000L
private const val MUX_BUFFER_SIZE = 1 * 1024 * 1024

/**
 * Finds the first audio track in [extractor], selects it, and returns its index,
 * or -1 if the source has no audio track.
 */
private fun selectAudioTrack(extractor: MediaExtractor): Int {
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
            extractor.selectTrack(i)
            return i
        }
    }
    return -1
}

/**
 * Extracts the audio track from [uri] into [output] using the requested [format].
 *
 * [onProgress] is invoked with a value in 0f..1f as work proceeds.
 * Throws [IOException] when the source has no audio track or extraction fails.
 */
fun extractAudio(
    context: Context,
    uri: Uri,
    format: AudioFormat,
    output: File,
    onProgress: (Float) -> Unit
) {
    when (format) {
        AudioFormat.M4A -> extractToM4a(context, uri, output, onProgress)
        AudioFormat.WAV -> extractToWav(context, uri, output, onProgress)
    }
}

/** Copies the compressed AAC audio stream straight into an MP4/M4A container. */
private fun extractToM4a(
    context: Context,
    uri: Uri,
    output: File,
    onProgress: (Float) -> Unit
) {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        val trackIndex = selectAudioTrack(extractor)
        if (trackIndex < 0) throw IOException("NO_AUDIO_TRACK")

        val trackFormat = extractor.getTrackFormat(trackIndex)
        val durationUs = trackFormat.getLongOrZero(MediaFormat.KEY_DURATION)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val dstTrack = muxer.addTrack(trackFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(MUX_BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.offset = 0
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buffer, bufferInfo)

                if (durationUs > 0) {
                    onProgress((bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                }
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
    } finally {
        extractor.release()
    }
    onProgress(1f)
}

/** Decodes the audio track to 16-bit PCM and wraps it in a WAV container. */
private fun extractToWav(
    context: Context,
    uri: Uri,
    output: File,
    onProgress: (Float) -> Unit
) {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        val trackIndex = selectAudioTrack(extractor)
        if (trackIndex < 0) throw IOException("NO_AUDIO_TRACK")

        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IOException("NO_AUDIO_TRACK")
        val durationUs = inputFormat.getLongOrZero(MediaFormat.KEY_DURATION)

        var sampleRate = inputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
        var channelCount = inputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)

        // Ask the decoder for 16-bit PCM so the WAV header we write always matches the
        // produced samples (some devices would otherwise emit float PCM).
        inputFormat.setInteger(
            MediaFormat.KEY_PCM_ENCODING,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )

        val codec = MediaCodec.createDecoderByType(mime)

        // Write a placeholder 44-byte WAV header up front, stream PCM after it, then
        // rewrite the header once the final data size is known.
        RandomAccessFile(output, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(WAV_HEADER_SIZE))

            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false
                var dataSize = 0L

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuffer = codec.getInputBuffer(inIndex)!!
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(
                                    inIndex, 0, sampleSize, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            val outBuffer = codec.getOutputBuffer(outIndex)!!
                            if (bufferInfo.size > 0) {
                                val chunk = ByteArray(bufferInfo.size)
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outBuffer.get(chunk)
                                raf.write(chunk)
                                dataSize += chunk.size

                                if (durationUs > 0) {
                                    onProgress(
                                        (bufferInfo.presentationTimeUs.toFloat() / durationUs)
                                            .coerceIn(0f, 1f)
                                    )
                                }
                            }
                            outBuffer.clear()
                            codec.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEOS = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            sampleRate = newFormat.getIntegerOrDefault(
                                MediaFormat.KEY_SAMPLE_RATE, sampleRate
                            )
                            channelCount = newFormat.getIntegerOrDefault(
                                MediaFormat.KEY_CHANNEL_COUNT, channelCount
                            )
                        }
                    }
                }

                // Backfill the real WAV header now that the PCM size is known.
                raf.seek(0)
                raf.write(buildWavHeader(dataSize, sampleRate, channelCount))
            } finally {
                codec.stop()
                codec.release()
            }
        }
    } finally {
        extractor.release()
    }
    onProgress(1f)
}

private const val WAV_HEADER_SIZE = 44
private const val PCM_BITS_PER_SAMPLE = 16

/** Builds a 44-byte canonical WAV (RIFF/PCM) header for the given parameters. */
private fun buildWavHeader(dataSize: Long, sampleRate: Int, channels: Int): ByteArray {
    val byteRate = sampleRate * channels * PCM_BITS_PER_SAMPLE / 8
    val blockAlign = channels * PCM_BITS_PER_SAMPLE / 8
    return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((dataSize + 36).toInt())          // ChunkSize
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)                                // Subchunk1Size (PCM)
        putShort(1)                               // AudioFormat = PCM
        putShort(channels.toShort())
        putInt(sampleRate)
        putInt(byteRate)
        putShort(blockAlign.toShort())
        putShort(PCM_BITS_PER_SAMPLE.toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataSize.toInt())                  // Subchunk2Size
    }.array()
}

private fun MediaFormat.getLongOrZero(key: String): Long =
    if (containsKey(key)) getLong(key) else 0L

private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
    if (containsKey(key)) getInteger(key) else default
