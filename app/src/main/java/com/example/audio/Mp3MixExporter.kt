package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.roundToInt

data class Mp3ExportResult(
    val internalMp3File: File,
    val mediaStoreUri: Uri?,
    val displayName: String,
    val fileSizeBytes: Long
)

object Mp3MixExporter {

    private fun createConfiguredLame(
        sampleRate: Int,
        bitrateKbps: Int
    ): Pair<Lame, de.sciss.jump3r.mp3.LameGlobalFlags> {
        val lame = Lame()
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        parse.setModules(ver, id3, p)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        val gfp = lame.lame_init()
        gfp.num_channels = 2
        gfp.in_samplerate = sampleRate
        gfp.out_samplerate = sampleRate
        gfp.mode = if (bitrateKbps >= 256) MPEGMode.STEREO else MPEGMode.JOINT_STEREO
        gfp.VBR = VbrMode.vbr_off
        gfp.brate = bitrateKbps
        gfp.quality = 2 // High studio quality
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false

        id3.id3tag_init(gfp)
        val res = lame.lame_init_params(gfp)
        require(res >= 0) { "LAME gagal diinisialisasi (kode $res)" }
        return lame to gfp
    }

    suspend fun renderAndExportMp3(
        context: Context,
        backingPcmFile: File,
        vocalPcmFile: File,
        outputMp3File: File,
        exportDisplayTitle: String,
        settings: MixSettings,
        bitrateKbps: Int,
        onProgress: (Float) -> Unit
    ): Mp3ExportResult = withContext(Dispatchers.IO) {
        outputMp3File.parentFile?.mkdirs()

        val sampleRate = AudioConstants.SAMPLE_RATE
        val totalFrames = backingPcmFile.length() / AudioConstants.STEREO_FRAME_BYTES
        require(totalFrames > 0L) { "Audio pengiring kosong." }

        val (lame, gfp) = createConfiguredLame(sampleRate, bitrateKbps)
        val dsp = VocalDspProcessor(sampleRate)

        val chunkFrames = 4096
        val backingBytes = ByteArray(chunkFrames * 4)
        val vocalBytes = ByteArray(chunkFrames * 2)
        val leftInt = IntArray(chunkFrames)
        val rightInt = IntArray(chunkFrames)
        val mp3Buffer = ByteArray((chunkFrames * 1.25 + 8192).toInt())
        val outPair = FloatArray(2)

        val offsetFrames = (-settings.syncOffsetMs.toLong() * sampleRate) / 1000L

        RandomAccessFile(backingPcmFile, "r").use { backingRaf ->
            val hasVocal = vocalPcmFile.exists() && vocalPcmFile.length() > 0L
            val vocalRaf = if (hasVocal) RandomAccessFile(vocalPcmFile, "r") else null
            val vocalTotalFrames = vocalRaf?.length()?.div(2L) ?: 0L

            try {
                BufferedOutputStream(FileOutputStream(outputMp3File), 65536).use { mp3Out ->
                    var currentFrame = 0L
                    while (currentFrame < totalFrames) {
                        val framesToRead = minOf(chunkFrames.toLong(), totalFrames - currentFrame).toInt()
                        backingRaf.seek(currentFrame * 4L)
                        val bRead = readFully(backingRaf, backingBytes, framesToRead * 4)
                        val actualFrames = bRead / 4
                        if (actualFrames <= 0) break

                        vocalBytes.fill(0)
                        if (vocalRaf != null && vocalTotalFrames > 0L) {
                            val vocalStartFrame = currentFrame + offsetFrames
                            val vEnd = vocalStartFrame + actualFrames
                            if (vEnd > 0L && vocalStartFrame < vocalTotalFrames) {
                                val srcStart = max(0L, vocalStartFrame)
                                val dstStartFrame = (srcStart - vocalStartFrame).toInt()
                                val availFrames = minOf(
                                    (vocalTotalFrames - srcStart).toInt(),
                                    actualFrames - dstStartFrame
                                )
                                if (availFrames > 0) {
                                    vocalRaf.seek(srcStart * 2L)
                                    val temp = ByteArray(availFrames * 2)
                                    val vRead = readFully(vocalRaf, temp, availFrames * 2)
                                    System.arraycopy(temp, 0, vocalBytes, dstStartFrame * 2, vRead)
                                }
                            }
                        }

                        for (f in 0 until actualFrames) {
                            val bi = f * 4
                            val bL = ((backingBytes[bi + 1].toInt() shl 8) or (backingBytes[bi].toInt() and 0xFF)).toShort() / 32768f
                            val bR = ((backingBytes[bi + 3].toInt() shl 8) or (backingBytes[bi + 2].toInt() and 0xFF)).toShort() / 32768f

                            val vi = f * 2
                            val vSample = ((vocalBytes[vi + 1].toInt() shl 8) or (vocalBytes[vi].toInt() and 0xFF)).toShort() / 32768f

                            dsp.processFrame(bL, bR, vSample, settings.copy(soloVocal = false), outPair)

                            // LAME's lame_encode_buffer_int multiplies ints by (1 / 65536f),
                            // expecting signed 16-bit values in the upper 16 bits (shl 16).
                            val sL = (outPair[0].coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
                            val sR = (outPair[1].coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
                            leftInt[f] = sL shl 16
                            rightInt[f] = sR shl 16
                        }

                        val encodedBytes = lame.lame_encode_buffer_int(
                            gfp,
                            leftInt,
                            rightInt,
                            actualFrames,
                            mp3Buffer,
                            0,
                            mp3Buffer.size
                        )
                        if (encodedBytes > 0) {
                            mp3Out.write(mp3Buffer, 0, encodedBytes)
                        }

                        currentFrame += actualFrames
                        onProgress((currentFrame.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.96f))
                    }

                    val flushed = lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
                    if (flushed > 0) {
                        mp3Out.write(mp3Buffer, 0, flushed)
                    }
                    mp3Out.flush()
                }
            } finally {
                try {
                    vocalRaf?.close()
                } catch (_: Exception) {
                }
                try {
                    lame.lame_close(gfp)
                } catch (_: Exception) {
                }
            }
        }

        val mediaUri = saveToMediaStoreAudio(context, outputMp3File, exportDisplayTitle)
        onProgress(1f)

        Mp3ExportResult(
            internalMp3File = outputMp3File,
            mediaStoreUri = mediaUri,
            displayName = outputMp3File.name,
            fileSizeBytes = outputMp3File.length()
        )
    }

    private fun saveToMediaStoreAudio(
        context: Context,
        mp3File: File,
        displayTitle: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, mp3File.name)
                put(MediaStore.Audio.Media.TITLE, displayTitle)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/VocaStage")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                mp3File.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            null
        }
    }

    suspend fun copyMp3ToUri(context: Context, mp3File: File, destUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    mp3File.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun readFully(raf: RandomAccessFile, dest: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val r = raf.read(dest, total, length - total)
            if (r <= 0) break
            total += r
        }
        return total
    }
}
