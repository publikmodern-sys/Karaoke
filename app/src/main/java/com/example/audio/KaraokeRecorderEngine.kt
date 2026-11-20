package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Synchronized karaoke recorder that plays back the decoded 44.1kHz stereo PCM backing track
 * while simultaneously capturing 44.1kHz mono 16-bit vocal PCM.
 */
class KaraokeRecorderEngine {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _vocalMeter = MutableStateFlow(0f)
    val vocalMeter: StateFlow<Float> = _vocalMeter.asStateFlow()

    private val _musicMeter = MutableStateFlow(0f)
    val musicMeter: StateFlow<Float> = _musicMeter.asStateFlow()

    private var recordJob: Job? = null
    private var playJob: Job? = null

    @Volatile
    private var activePaused = false

    @Volatile
    private var shouldStop = false

    @Volatile
    private var monitoringMusicGain = 0.85f

    fun setMonitoringMusicGain(gain: Float) {
        monitoringMusicGain = gain.coerceIn(0f, 1.2f)
    }

    @SuppressLint("MissingPermission")
    fun startSession(
        scope: CoroutineScope,
        backingPcmFile: File,
        outputVocalPcmFile: File,
        useHeadphoneMode: Boolean,
        onFinished: (recordedDurationMs: Long) -> Unit,
        onError: (String) -> Unit
    ) {
        stopInternal()
        outputVocalPcmFile.parentFile?.mkdirs()

        shouldStop = false
        activePaused = false
        _isPaused.value = false
        _isRecording.value = true
        _positionMs.value = 0L

        val sampleRate = AudioConstants.SAMPLE_RATE
        val minRecBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioSource = if (useHeadphoneMode) {
            MediaRecorder.AudioSource.MIC
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }

        val audioRecord = try {
            AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf * 2
            )
        } catch (e: Exception) {
            _isRecording.value = false
            onError("Gagal menginisialisasi mikrofon: ${e.message}")
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            _isRecording.value = false
            onError("Mikrofon tidak tersedia atau sedang digunakan aplikasi lain.")
            return
        }

        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        if (!useHeadphoneMode) {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(audioRecord.audioSessionId)?.apply { enabled = true }
            }
        }

        val minTrackBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minTrackBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val audioTrack = trackBuilder.build()

        var framesRecorded = 0L

        // Start playback coroutine
        playJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            var framesPlayed = 0L
            try {
                audioTrack.play()
                RandomAccessFile(backingPcmFile, "r").use { raf ->
                    while (!shouldStop && isActive) {
                        if (activePaused) {
                            Thread.sleep(25)
                            continue
                        }
                        val read = raf.read(buf)
                        if (read <= 0) {
                            // Backing track reached the end
                            shouldStop = true
                            break
                        }
                        val usable = read - (read % 4)
                        if (usable > 0) {
                            // Apply monitoring volume & compute music meter
                            val gain = monitoringMusicGain
                            var peak = 0f
                            var i = 0
                            while (i < usable) {
                                val l = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()
                                val r = ((buf[i + 3].toInt() shl 8) or (buf[i + 2].toInt() and 0xFF)).toShort()
                                val sl = (l * gain).toInt().coerceIn(-32768, 32767)
                                val sr = (r * gain).toInt().coerceIn(-32768, 32767)
                                buf[i] = (sl and 0xFF).toByte()
                                buf[i + 1] = ((sl ushr 8) and 0xFF).toByte()
                                buf[i + 2] = (sr and 0xFF).toByte()
                                buf[i + 3] = ((sr ushr 8) and 0xFF).toByte()
                                val p = max(abs(sl), abs(sr)) / 32768f
                                if (p > peak) peak = p
                                i += 4
                            }
                            _musicMeter.value = peak
                            audioTrack.write(buf, 0, usable)
                            framesPlayed += (usable / 4)
                            _positionMs.value = (framesPlayed * 1000L) / sampleRate
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {
                }
                _musicMeter.value = 0f
            }
        }

        // Start recording coroutine
        recordJob = scope.launch(Dispatchers.IO) {
            val recBuf = ByteArray(2048)
            try {
                audioRecord.startRecording()
                BufferedOutputStream(FileOutputStream(outputVocalPcmFile), 32768).use { out ->
                    while (!shouldStop && isActive) {
                        val read = audioRecord.read(recBuf, 0, recBuf.size)
                        if (read > 0) {
                            if (!activePaused) {
                                out.write(recBuf, 0, read)
                                framesRecorded += (read / 2)
                                // Compute RMS VU meter
                                var sumSq = 0.0
                                val count = read / 2
                                var i = 0
                                while (i < read - 1) {
                                    val s = ((recBuf[i + 1].toInt() shl 8) or (recBuf[i].toInt() and 0xFF)).toShort()
                                    val norm = s / 32768.0
                                    sumSq += norm * norm
                                    i += 2
                                }
                                val rms = sqrt(sumSq / count.coerceAtLeast(1)).toFloat()
                                _vocalMeter.value = (rms * 2.8f).coerceIn(0f, 1f)
                            } else {
                                _vocalMeter.value = 0f
                            }
                        }
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                onError("Kesalahan perekaman: ${e.message}")
            } finally {
                try {
                    audioRecord.stop()
                } catch (_: Exception) {
                }
                try {
                    aec?.release()
                    ns?.release()
                    audioRecord.release()
                } catch (_: Exception) {
                }
                _vocalMeter.value = 0f
                _isRecording.value = false
                _isPaused.value = false
                val recordedMs = (framesRecorded * 1000L) / sampleRate
                onFinished(recordedMs)
            }
        }
    }

    fun togglePause() {
        activePaused = !activePaused
        _isPaused.value = activePaused
    }

    fun stopRecording() {
        shouldStop = true
    }

    private fun stopInternal() {
        shouldStop = true
        playJob?.cancel()
        recordJob?.cancel()
        playJob = null
        recordJob = null
        _isRecording.value = false
        _isPaused.value = false
        _vocalMeter.value = 0f
        _musicMeter.value = 0f
    }

    fun release() {
        stopInternal()
    }
}
