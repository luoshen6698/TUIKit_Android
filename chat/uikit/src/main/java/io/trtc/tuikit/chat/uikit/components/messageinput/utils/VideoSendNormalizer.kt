package io.trtc.tuikit.chat.uikit.components.messageinput.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** Removes a mis-muxed AAC configuration sample without re-encoding media. */
internal object VideoSendNormalizer {
    fun normalize(source: String, outputDir: File): String {
        val extractor = MediaExtractor()
        var output: File? = null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(source)
            val badTracks = mutableSetOf<Int>()
            val configurations = mutableMapOf<Int, ByteArray>()
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                if (format.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm") continue
                val config = format.getByteBuffer("csd-0")?.duplicate() ?: continue
                if (config.remaining() !in 2..64) continue
                val bytes = ByteArray(config.remaining()).also { config.get(it) }
                configurations[track] = bytes
                extractor.selectTrack(track)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val sample = ByteBuffer.allocate(64)
                val size = try { extractor.readSampleData(sample, 0) } catch (_: IllegalArgumentException) { -1 }
                if (size in 1..64 && AacSamplePolicy.isConfigurationSample(bytes, sample.array().copyOf(size))) badTracks.add(track)
                extractor.unselectTrack(track)
            }
            if (badTracks.isEmpty()) return source
            check(outputDir.isDirectory || outputDir.mkdirs())
            output = File(outputDir, "compatible_${UUID.randomUUID()}.mp4")
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(source)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let(writer::setOrientationHint)
            } finally { retriever.release() }
            val tracks = IntArray(extractor.trackCount)
            var bufferSize = 1024 * 1024
            for (track in tracks.indices) {
                val format = extractor.getTrackFormat(track)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                tracks[track] = writer.addTrack(format)
                extractor.selectTrack(track)
            }
            check(bufferSize <= 16 * 1024 * 1024)
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            writer.start()
            started = true
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val firstSamples = mutableSetOf<Int>()
            while (extractor.sampleTrackIndex >= 0) {
                buffer.clear()
                val track = extractor.sampleTrackIndex
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val first = firstSamples.add(track)
                val skip = first && track in badTracks && size <= 64 &&
                    AacSamplePolicy.isConfigurationSample(configurations.getValue(track), buffer.array().copyOf(size))
                if (!skip) {
                    check(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                    info.set(0, size, extractor.sampleTime,
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    writer.writeSampleData(tracks[track], buffer, info)
                }
                if (!extractor.advance()) break
            }
            writer.stop()
            started = false
            return output.absolutePath
        } catch (error: Exception) {
            // Never silently upload a known-invalid file after normalization fails.
            if (started) runCatching { muxer?.stop() }
            output?.delete()
            throw error
        } finally {
            muxer?.release()
            extractor.release()
        }
    }
}

internal object AacSamplePolicy {
    fun isConfigurationSample(configuration: ByteArray, sample: ByteArray): Boolean =
        configuration.size in 2..64 && sample.contentEquals(configuration)
}
