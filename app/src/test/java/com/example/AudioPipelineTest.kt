package com.example

import com.example.audio.AudioConstants
import com.example.audio.MixSettings
import com.example.audio.Mp3MixExporter
import com.example.audio.ReverbPreset
import com.example.audio.VocalDspProcessor
import com.example.data.model.LrcParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class AudioPipelineTest {

    @Test
    fun lrcParser_parsesTimestampsAndFindsActiveLine() {
        val raw = """
            [00:05.00] Baris satu
            [00:12.50] Baris dua
            [00:20.00] Baris tiga
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(3, lines.size)
        assertTrue(LrcParser.hasTimestamps(lines))
        assertEquals(0, LrcParser.findActiveIndex(lines, 5_100L))
        assertEquals(1, LrcParser.findActiveIndex(lines, 13_000L))
        assertEquals(2, LrcParser.findActiveIndex(lines, 25_000L))
    }

    @Test
    fun vocalDspProcessor_producesBoundedFiniteAudio() {
        val dsp = VocalDspProcessor(AudioConstants.SAMPLE_RATE)
        val settings = MixSettings(
            vocalGainDb = 4.0f,
            musicGainDb = -2.0f,
            reverbWet = 0.30f,
            reverbPreset = ReverbPreset.STUDIO,
            clarityBoost = 0.8f,
            warmthGain = 2.0f,
            noiseGateThresholdDb = -46f,
            compressorAmount = 0.7f
        )
        val out = FloatArray(2)
        var maxOut = 0f
        for (i in 0 until 4410) {
            val music = 0.6f * sin(2.0 * PI * 220.0 * i / 44100.0).toFloat()
            val vocal = 0.8f * sin(2.0 * PI * 440.0 * i / 44100.0).toFloat()
            dsp.processFrame(music, music, vocal, settings, out)
            assertFalse(out[0].isNaN() || out[0].isInfinite())
            assertFalse(out[1].isNaN() || out[1].isInfinite())
            maxOut = maxOf(maxOut, kotlin.math.abs(out[0]), kotlin.math.abs(out[1]))
        }
        assertTrue("Output should stay safely limited <= 1.0f", maxOut <= 1.0f)
        assertTrue("Output should have audible energy", maxOut > 0.2f)
    }

    @Test
    fun mp3MixExporter_encodesValidMp3File() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val tempDir = File(context.cacheDir, "mp3_test").apply { mkdirs() }
        val backingPcm = File(tempDir, "backing.pcm")
        val vocalPcm = File(tempDir, "vocal.pcm")
        val outMp3 = File(tempDir, "mixed.mp3")

        // Generate 1 second of 44.1kHz stereo backing + mono vocal PCM
        val frames = 44100
        val bBytes = ByteArray(frames * 4)
        val vBytes = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val bSample = (0.4f * sin(2.0 * PI * 261.63 * i / 44100.0) * 32767f).toInt()
            val bi = i * 4
            bBytes[bi] = (bSample and 0xFF).toByte()
            bBytes[bi + 1] = ((bSample ushr 8) and 0xFF).toByte()
            bBytes[bi + 2] = (bSample and 0xFF).toByte()
            bBytes[bi + 3] = ((bSample ushr 8) and 0xFF).toByte()

            val vSample = (0.5f * sin(2.0 * PI * 523.25 * i / 44100.0) * 32767f).toInt()
            val vi = i * 2
            vBytes[vi] = (vSample and 0xFF).toByte()
            vBytes[vi + 1] = ((vSample ushr 8) and 0xFF).toByte()
        }
        backingPcm.writeBytes(bBytes)
        vocalPcm.writeBytes(vBytes)

        val result = Mp3MixExporter.renderAndExportMp3(
            context = context,
            backingPcmFile = backingPcm,
            vocalPcmFile = vocalPcm,
            outputMp3File = outMp3,
            exportDisplayTitle = "Test Mix",
            settings = MixSettings(syncOffsetMs = 0),
            bitrateKbps = 192,
            onProgress = {}
        )

        assertTrue(result.internalMp3File.exists())
        assertTrue("MP3 should be non-empty", result.fileSizeBytes > 5000L)
        val header = result.internalMp3File.readBytes().take(2)
        // MPEG-1 Layer III frame sync starts with 0xFF and top 3 bits of second byte = 111
        assertEquals(0xFF.toByte(), header[0])
        assertEquals(0xE0, header[1].toInt() and 0xE0)
    }
}
