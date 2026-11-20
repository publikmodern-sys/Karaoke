package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.KaraokeRecorderEngine
import com.example.data.local.KaraokeTrackEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.VocaStageDatabase
import com.example.data.model.LrcParser
import com.example.data.model.LyricLine
import com.example.data.repository.KaraokeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KaraokeRecordUiState(
    val track: KaraokeTrackEntity? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val hasSyncedLyrics: Boolean = false,
    val useHeadphoneMode: Boolean = true,
    val monitoringVolume: Float = 0.85f,
    val enableCountdown: Boolean = true,
    val countdownRemaining: Int = 0,
    val isSavingTake: Boolean = false,
    val createdMixId: Long? = null,
    val errorMessage: String? = null
)

class KaraokeRecordViewModel(application: Application) : AndroidViewModel(application) {
    private val db = VocaStageDatabase.getInstance(application)
    private val repository = KaraokeRepository(application, db.karaokeTrackDao(), db.vocalMixDao())
    private val prefsRepo = PreferencesRepository(application)
    private val recorder = KaraokeRecorderEngine()

    private val _uiState = MutableStateFlow(KaraokeRecordUiState())
    val uiState: StateFlow<KaraokeRecordUiState> = _uiState.asStateFlow()

    val isRecording: StateFlow<Boolean> = recorder.isRecording
    val isPaused: StateFlow<Boolean> = recorder.isPaused
    val positionMs: StateFlow<Long> = recorder.positionMs
    val vocalMeter: StateFlow<Float> = recorder.vocalMeter
    val musicMeter: StateFlow<Float> = recorder.musicMeter

    private var currentVocalFile: File? = null

    fun loadTrack(trackId: Long) {
        viewModelScope.launch {
            val prefs = prefsRepo.preferences.first()
            val track = repository.getTrack(trackId)
            if (track == null) {
                _uiState.update { it.copy(errorMessage = "Lagu tidak ditemukan.") }
                return@launch
            }
            val parsedLyrics = LrcParser.parse(track.lyricsRaw)
            _uiState.update {
                it.copy(
                    track = track,
                    lyrics = parsedLyrics,
                    hasSyncedLyrics = LrcParser.hasTimestamps(parsedLyrics),
                    useHeadphoneMode = prefs.useHeadphoneMode,
                    enableCountdown = prefs.enableCountdown
                )
            }
        }
    }

    fun setHeadphoneMode(headphone: Boolean) {
        if (recorder.isRecording.value) return
        _uiState.update { it.copy(useHeadphoneMode = headphone) }
        viewModelScope.launch { prefsRepo.setUseHeadphoneMode(headphone) }
    }

    fun setMonitoringVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1.1f)
        _uiState.update { it.copy(monitoringVolume = clamped) }
        recorder.setMonitoringMusicGain(clamped)
    }

    fun toggleCountdownSetting() {
        val next = !_uiState.value.enableCountdown
        _uiState.update { it.copy(enableCountdown = next) }
        viewModelScope.launch { prefsRepo.setEnableCountdown(next) }
    }

    fun startRecordingWithOptionalCountdown() {
        val state = _uiState.value
        val track = state.track ?: return
        if (recorder.isRecording.value || state.countdownRemaining > 0) return

        viewModelScope.launch {
            if (state.enableCountdown) {
                for (c in 3 downTo 1) {
                    _uiState.update { it.copy(countdownRemaining = c) }
                    delay(850L)
                }
                _uiState.update { it.copy(countdownRemaining = 0) }
            }
            beginCapture(track)
        }
    }

    private fun beginCapture(track: KaraokeTrackEntity) {
        val backingFile = File(track.backingPcmPath)
        if (!backingFile.exists()) {
            _uiState.update { it.copy(errorMessage = "File audio pengiring tidak ditemukan.") }
            return
        }
        val vocalFile = repository.createNewVocalPcmFile(track.id)
        currentVocalFile = vocalFile
        recorder.setMonitoringMusicGain(_uiState.value.monitoringVolume)

        recorder.startSession(
            scope = viewModelScope,
            backingPcmFile = backingFile,
            outputVocalPcmFile = vocalFile,
            useHeadphoneMode = _uiState.value.useHeadphoneMode,
            onFinished = { recordedMs ->
                saveTakeToDatabase(track, vocalFile, recordedMs)
            },
            onError = { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
        )
    }

    fun togglePauseRecording() {
        recorder.togglePause()
    }

    fun stopAndFinishRecording() {
        recorder.stopRecording()
    }

    private fun saveTakeToDatabase(track: KaraokeTrackEntity, vocalFile: File, recordedMs: Long) {
        if (!vocalFile.exists() || vocalFile.length() < 4096L) {
            _uiState.update { it.copy(errorMessage = "Rekaman terlalu pendek untuk disimpan.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTake = true) }
            val prefs = prefsRepo.preferences.first()
            val timeStamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
            val mixName = "${track.title} ($timeStamp)"
            val mixId = repository.saveRecordedMix(
                trackId = track.id,
                mixName = mixName,
                vocalPcmFile = vocalFile,
                durationMs = recordedMs.coerceAtLeast(1000L),
                defaultSyncOffsetMs = prefs.defaultSyncOffsetMs,
                defaultBitrateKbps = prefs.defaultBitrateKbps
            )
            _uiState.update { it.copy(isSavingTake = false, createdMixId = mixId) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.release()
    }
}
