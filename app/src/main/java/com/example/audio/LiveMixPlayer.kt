package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max

/**
 * Real-time dual-track DSP preview engine and backing/MP3 player.
 * Streams backing PCM + vocal PCM (aligned with syncOffsetMs) through VocalDspProcessor
 * so slider adjustments are heard instantaneously.
 */
class LiveMixPlayer {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _levelMeter = MutableStateFlow(0f)
    val levelMeter: StateFlow<Float> = _levelMeter.asStateFlow()

    @Volatile
    private var currentSettings = MixSettings()

    @Volatile
    private var pendingSeekFrame: Long = -1L

    @Volatile
    private var shouldStop = false

    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mp3ProgressJob: Job? = null

    fun updateSettings(settings: MixSettings) {
        currentSettings = settings
    }

    fun seekToMs(ms: Long) {
        val mp = mediaPlayer
        if (mp != null) {
            try {
                mp.seekTo(ms.toInt().coerceAtLeast(0))
                _positionMs.value = ms.coerceAtLeast(0L)
            } catch (_: Exception) {
            }
            return
        }
        val targetFrame = (ms.coerceAtLeast(0L) * AudioConstants.SAMPLE_RATE) / 1000L
        pendingSeekFrame = targetFrame
        _positionMs.value = ms.coerceAtLeast(0L)
    }

    /**
     * Real-time preview of backing PCM + vocal PCM with live DSP effects.
     * Pass vocalPcmFile = null to play backing track alone (e.g., for Tap-to-Sync Lyrics).
     */
    fun startPcmMixPreview(
        scope: CoroutineScope,
        backingPcmFile: File,
        vocalPcmFile: File?,
        initialSettings: MixSettings = MixSettings(),
        startPositionMs: Long = _positionMs.value
    ) {
        stop()
        currentSettings = initialSettings
        shouldStop = false

        val totalFrames = backingPcmFile.length() / AudioConstants.STEREO_FRAME_BYTES
        _durationMs.value = (totalFrames * 1000L) / AudioConstants.SAMPLE_RATE
        var currentFrame = ((startPositionMs.coerceAtLeast(0L) * AudioConstants.SAMPLE_RATE) / 1000L)
            .coerceIn(0L, totalFrames)
        _positionMs.value = (currentFrame * 1000L) / AudioConstants.SAMPLE_RATE
        _isPlaying.value = true

        playbackJob = scope.launch(Dispatchers.IO) {
            val sampleRate = AudioConstants.SAMPLE_RATE
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)

            val track = AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val chunkFrames = 1024 // ~23ms ultra-responsive DSP updates
            val backingBytes = ByteArray(chunkFrames * 4)
            val vocalBytes = ByteArray(chunkFrames * 2)
            val outBytes = ByteArray(chunkFrames * 4)
            val outPair = FloatArray(2)
            val dsp = VocalDspProcessor(sampleRate)

            var backingRaf: RandomAccessFile? = null
            var vocalRaf: RandomAccessFile? = null

            try {
                backingRaf = RandomAccessFile(backingPcmFile, "r")
                val hasVocal = vocalPcmFile != null && vocalPcmFile.exists() && vocalPcmFile.length() > 0L
                if (hasVocal) {
                    vocalRaf = RandomAccessFile(vocalPcmFile, "r")
                }
                val vocalTotalFrames = if (vocalRaf != null) vocalRaf.length() / 2L else 0L

                track.play()

                while (!shouldStop && isActive && currentFrame < totalFrames) {
                    val seekTarget = pendingSeekFrame
                    if (seekTarget >= 0L) {
                        currentFrame = seekTarget.coerceIn(0L, totalFrames)
                        pendingSeekFrame = -1L
                        dsp.resetState()
                    }

                    val settings = currentSettings
                    val framesToRead = minOf(chunkFrames.toLong(), totalFrames - currentFrame).toInt()
                    if (framesToRead <= 0) break

                    backingRaf.seek(currentFrame * 4L)
                    val bRead = readFully(backingRaf, backingBytes, framesToRead * 4)
                    val actualFrames = bRead / 4
                    if (actualFrames <= 0) break

                    // Vocal frame offset: negative syncOffsetMs means vocal was recorded late,
                    // so we advance vocal read position forward to align with backing track!
                    val offsetFrames = (-settings.syncOffsetMs.toLong() * sampleRate) / 1000L
                    val vocalStartFrame = currentFrame + offsetFrames

                    vocalBytes.fill(0)
                    if (vocalRaf != null && vocalTotalFrames > 0L) {
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

                    var chunkPeak = 0f
                    for (f in 0 until actualFrames) {
                        val bi = f * 4
                        val bL = ((backingBytes[bi + 1].toInt() shl 8) or (backingBytes[bi].toInt() and 0xFF)).toShort() / 32768f
                        val bR = ((backingBytes[bi + 3].toInt() shl 8) or (backingBytes[bi + 2].toInt() and 0xFF)).toShort() / 32768f

                        val vi = f * 2
                        val vSample = ((vocalBytes[vi + 1].toInt() shl 8) or (vocalBytes[vi].toInt() and 0xFF)).toShort() / 32768f

                        if (vocalRaf == null) {
                            // Pure backing playback (e.g. Lyrics Tap-to-Sync)
                            outPair[0] = bL
                            outPair[1] = bR
                        } else {
                            dsp.processFrame(bL, bR, vSample, settings, outPair)
                        }

                        val oL = (outPair[0].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
                        val oR = (outPair[1].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
                        outBytes[bi] = (oL and 0xFF).toByte()
                        outBytes[bi + 1] = ((oL ushr 8) and 0xFF).toByte()
                        outBytes[bi + 2] = (oR and 0xFF).toByte()
                        outBytes[bi + 3] = ((oR ushr 8) and 0xFF).toByte()

                        val p = max(abs(outPair[0]), abs(outPair[1]))
                        if (p > chunkPeak) chunkPeak = p
                    }

                    _levelMeter.value = chunkPeak
                    track.write(outBytes, 0, actualFrames * 4)
                    currentFrame += actualFrames
                    _positionMs.value = (currentFrame * 1000L) / sampleRate
                }
            } catch (_: Exception) {
            } finally {
                try {
                    backingRaf?.close()
                    vocalRaf?.close()
                } catch (_: Exception) {
                }
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {
                }
                _isPlaying.value = false
                _levelMeter.value = 0f
                if (!shouldStop && currentFrame >= totalFrames) {
                    _positionMs.value = 0L
                }
            }
        }
    }

    fun playMp3File(scope: CoroutineScope, mp3File: File) {
        stop()
        if (!mp3File.exists()) return
        try {
            val mp = MediaPlayer().apply {
                setDataSource(mp3File.absolutePath)
                prepare()
                start()
            }
            mediaPlayer = mp
            _durationMs.value = mp.duration.toLong().coerceAtLeast(0L)
            _positionMs.value = 0L
            _isPlaying.value = true

            mp.setOnCompletionListener {
                _isPlaying.value = false
                _positionMs.value = 0L
            }

            mp3ProgressJob = scope.launch(Dispatchers.Main) {
                while (isActive && mediaPlayer != null) {
                    val activeMp = mediaPlayer ?: break
                    try {
                        if (activeMp.isPlaying) {
                            _positionMs.value = activeMp.currentPosition.toLong()
                        }
                    } catch (_: Exception) {
                        break
                    }
                    delay(100)
                }
            }
        } catch (_: Exception) {
            stop()
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

    fun stop() {
        shouldStop = true
        playbackJob?.cancel()
        playbackJob = null
        mp3ProgressJob?.cancel()
        mp3ProgressJob = null
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _levelMeter.value = 0f
    }

    fun release() {
        stop()
    }
}
