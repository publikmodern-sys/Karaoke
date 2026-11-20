package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImportedAudioMetadata(
    val title: String,
    val artist: String,
    val durationMs: Long
)

data class DecodedAudioResult(
    val pcmFile: File,
    val durationMs: Long,
    val waveformPeaks: List<Float>
)

object AudioDecoder {

    fun extractMetadata(context: Context, uri: Uri): ImportedAudioMetadata {
        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = ""
        var durationMs = 0L
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty()
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (title.isBlank()) {
            title = queryDisplayName(context, uri)
                ?.substringBeforeLast('.')
                ?.replace('_', ' ')
                ?.trim()
                ?.ifBlank { "Lagu Baru" } ?: "Lagu Baru"
        }
        if (artist.isBlank()) {
            artist = "Artis Tidak Diketahui"
        }
        return ImportedAudioMetadata(title = title, artist = artist, durationMs = durationMs)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decodeUriToStereoPcm44100(
        context: Context,
        uri: Uri,
        destPcmFile: File,
        onProgress: (Float) -> Unit = {}
    ): DecodedAudioResult = withContext(Dispatchers.IO) {
        destPcmFile.parentFile?.mkdirs()

        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var audioTrackIndex = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                trackFormat = fmt
                break
            }
        }
        require(audioTrackIndex >= 0 && trackFormat != null) {
            "File tidak memiliki jalur audio yang valid."
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
        val estimatedDurationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
            trackFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(trackFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        var srcSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else AudioConstants.SAMPLE_RATE
        var srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 2
        var isFloatPcm = false

        // Resampler state for linear interpolation to 44100 Hz
        val targetRate = AudioConstants.SAMPLE_RATE
        var resamplePos = 0.0
        var prevL = 0f
        var prevR = 0f
        var hasPrev = false

        val waveformBuckets = FloatArray(80)
        val bucketCounts = IntArray(80)
        var totalOutputFrames = 0L
        val estimatedTotalFrames = if (estimatedDurationUs > 0) {
            (estimatedDurationUs * targetRate / 1_000_000L).coerceAtLeast(1L)
        } else 1L

        val outBytes = ByteArray(16384)
        var outByteIdx = 0

        BufferedOutputStream(FileOutputStream(destPcmFile), 65536).use { outStream ->
            fun flushOutBytes() {
                if (outByteIdx > 0) {
                    outStream.write(outBytes, 0, outByteIdx)
                    outByteIdx = 0
                }
            }

            fun writeStereoSample(lFloat: Float, rFloat: Float) {
                val lShort = (lFloat.coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
                val rShort = (rFloat.coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)

                outBytes[outByteIdx++] = (lShort and 0xFF).toByte()
                outBytes[outByteIdx++] = ((lShort ushr 8) and 0xFF).toByte()
                outBytes[outByteIdx++] = (rShort and 0xFF).toByte()
                outBytes[outByteIdx++] = ((rShort ushr 8) and 0xFF).toByte()
                if (outByteIdx >= outBytes.size - 4) {
                    flushOutBytes()
                }

                val peak = max(abs(lFloat), abs(rFloat))
                val bucketIdx = ((totalOutputFrames * 80L) / estimatedTotalFrames).toInt().coerceIn(0, 79)
                if (peak > waveformBuckets[bucketIdx]) {
                    waveformBuckets[bucketIdx] = peak
                }
                bucketCounts[bucketIdx]++
                totalOutputFrames++
            }

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuffer = codec.getInputBuffer(inIdx)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                                if (estimatedDurationUs > 0) {
                                    onProgress((pts.toFloat() / estimatedDurationUs.toFloat()).coerceIn(0f, 0.98f))
                                }
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        isFloatPcm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                            newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        } else false
                    }
                    outIdx >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIdx)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = outBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)

                            val frameCount = if (isFloatPcm) {
                                bufferInfo.size / (4 * srcChannels)
                            } else {
                                bufferInfo.size / (2 * srcChannels)
                            }

                            val ratio = srcSampleRate.toDouble() / targetRate.toDouble()
                            for (f in 0 until frameCount) {
                                val (curL, curR) = readFrameStereo(chunk, srcChannels, isFloatPcm)
                                if (srcSampleRate == targetRate) {
                                    writeStereoSample(curL, curR)
                                } else {
                                    if (!hasPrev) {
                                        prevL = curL
                                        prevR = curR
                                        hasPrev = true
                                    }
                                    while (resamplePos < 1.0) {
                                        val frac = resamplePos.toFloat()
                                        val interpL = prevL + (curL - prevL) * frac
                                        val interpR = prevR + (curR - prevR) * frac
                                        writeStereoSample(interpL, interpR)
                                        resamplePos += ratio
                                    }
                                    resamplePos -= 1.0
                                    prevL = curL
                                    prevR = curR
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            flushOutBytes()
        }

        try {
            codec.stop()
            codec.release()
        } catch (_: Exception) {
        }
        try {
            extractor.release()
        } catch (_: Exception) {
        }

        onProgress(1f)
        val exactDurationMs = (totalOutputFrames * 1000L) / targetRate
        val maxPeak = waveformBuckets.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
        val normalizedPeaks = waveformBuckets.map { (it / maxPeak).coerceIn(0.06f, 1f) }

        DecodedAudioResult(
            pcmFile = destPcmFile,
            durationMs = exactDurationMs,
            waveformPeaks = normalizedPeaks
        )
    }

    private fun readFrameStereo(
        buffer: ByteBuffer,
        channels: Int,
        isFloat: Boolean
    ): Pair<Float, Float> {
        return if (isFloat) {
            val ch0 = if (buffer.remaining() >= 4) buffer.float else 0f
            val ch1 = if (channels >= 2 && buffer.remaining() >= 4) buffer.float else ch0
            for (c in 2 until channels) {
                if (buffer.remaining() >= 4) buffer.float
            }
            ch0 to ch1
        } else {
            val ch0 = if (buffer.remaining() >= 2) buffer.short / 32768f else 0f
            val ch1 = if (channels >= 2 && buffer.remaining() >= 2) buffer.short / 32768f else ch0
            for (c in 2 until channels) {
                if (buffer.remaining() >= 2) buffer.short
            }
            ch0 to ch1
        }
    }

    fun computeWaveformFromPcm(pcmFile: File, bars: Int = 80): List<Float> {
        if (!pcmFile.exists() || pcmFile.length() < 4L) return List(bars) { 0.1f }
        val totalFrames = pcmFile.length() / AudioConstants.STEREO_FRAME_BYTES
        val peaks = FloatArray(bars)
        val buffer = ByteArray(16384)
        var frameIdx = 0L
        pcmFile.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val usable = read - (read % 4)
                var i = 0
                while (i < usable) {
                    val l = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                    val r = ((buffer[i + 3].toInt() shl 8) or (buffer[i + 2].toInt() and 0xFF)).toShort()
                    val amp = max(abs(l.toInt()), abs(r.toInt())) / 32768f
                    val b = min(bars - 1, ((frameIdx * bars) / totalFrames.coerceAtLeast(1L)).toInt())
                    if (amp > peaks[b]) peaks[b] = amp
                    frameIdx++
                    i += 4
                }
            }
        }
        val maxVal = peaks.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
        return peaks.map { (it / maxVal).coerceIn(0.08f, 1f) }
    }
}
