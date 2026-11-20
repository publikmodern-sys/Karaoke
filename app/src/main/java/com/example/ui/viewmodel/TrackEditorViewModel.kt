package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDecoder
import com.example.audio.LiveMixPlayer
import com.example.data.local.VocaStageDatabase
import com.example.data.model.LrcParser
import com.example.data.model.LyricLine
import com.example.data.repository.KaraokeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TrackEditorUiState(
    val editingTrackId: Long? = null,
    val selectedUri: Uri? = null,
    val title: String = "",
    val artist: String = "",
    val lyricsText: String = "",
    val backingPcmPath: String? = null,
    val durationMs: Long = 0L,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val isTapSyncActive: Boolean = false,
    val syncLines: List<LyricLine> = emptyList(),
    val currentSyncCursor: Int = 0,
    val errorMessage: String? = null,
    val savedTrackId: Long? = null
)

class TrackEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = VocaStageDatabase.getInstance(application)
    private val repository = KaraokeRepository(application, db.karaokeTrackDao(), db.vocalMixDao())
    private val syncPlayer = LiveMixPlayer()

    private val _uiState = MutableStateFlow(TrackEditorUiState())
    val uiState: StateFlow<TrackEditorUiState> = _uiState.asStateFlow()

    val isSyncPlaying: StateFlow<Boolean> = syncPlayer.isPlaying
    val syncPositionMs: StateFlow<Long> = syncPlayer.positionMs

    fun loadExistingTrack(trackId: Long) {
        if (trackId <= 0L) return
        viewModelScope.launch {
            val track = repository.getTrack(trackId) ?: return@launch
            _uiState.update {
                it.copy(
                    editingTrackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    lyricsText = track.lyricsRaw,
                    backingPcmPath = track.backingPcmPath,
                    durationMs = track.durationMs,
                    errorMessage = null
                )
            }
        }
    }

    fun onSongUriPicked(uri: Uri) {
        val context = getApplication<Application>()
        val meta = AudioDecoder.extractMetadata(context, uri)
        _uiState.update {
            it.copy(
                selectedUri = uri,
                title = meta.title,
                artist = meta.artist,
                durationMs = meta.durationMs,
                errorMessage = null
            )
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun updateArtist(value: String) {
        _uiState.update { it.copy(artist = value) }
    }

    fun updateLyricsText(value: String) {
        _uiState.update { it.copy(lyricsText = value) }
    }

    fun insertSampleLyricsTemplate() {
        val sample = """
            [00:05.00] Bait pertama mulai di sini
            [00:10.50] Ikuti irama lagu dengan santai
            [00:16.00] Ketuk sinkronisasi untuk mengatur waktu
            [00:22.00] Bernyanyi jernih bersama VocaStage
        """.trimIndent()
        _uiState.update { it.copy(lyricsText = sample) }
    }

    fun enterTapSyncMode() {
        val state = _uiState.value
        val pcmPath = state.backingPcmPath ?: return
        val pcmFile = File(pcmPath)
        if (!pcmFile.exists()) return
        val lines = LrcParser.parse(state.lyricsText)
        if (lines.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Masukkan teks lirik terlebih dahulu sebelum sinkronisasi.") }
            return
        }
        _uiState.update {
            it.copy(
                isTapSyncActive = true,
                syncLines = lines,
                currentSyncCursor = 0,
                errorMessage = null
            )
        }
        syncPlayer.startPcmMixPreview(
            scope = viewModelScope,
            backingPcmFile = pcmFile,
            vocalPcmFile = null,
            startPositionMs = 0L
        )
    }

    fun toggleSyncPlayback() {
        val pcmPath = _uiState.value.backingPcmPath ?: return
        val pcmFile = File(pcmPath)
        if (!pcmFile.exists()) return
        if (syncPlayer.isPlaying.value) {
            syncPlayer.stop()
        } else {
            syncPlayer.startPcmMixPreview(
                scope = viewModelScope,
                backingPcmFile = pcmFile,
                vocalPcmFile = null,
                startPositionMs = syncPlayer.positionMs.value
            )
        }
    }

    fun stampCurrentLine() {
        val state = _uiState.value
        val cursor = state.currentSyncCursor
        if (cursor !in state.syncLines.indices) return
        val nowMs = syncPlayer.positionMs.value
        val updated = state.syncLines.toMutableList()
        updated[cursor] = updated[cursor].copy(timeMs = nowMs)
        val nextCursor = (cursor + 1).coerceAtMost(updated.size)
        _uiState.update {
            it.copy(
                syncLines = updated,
                currentSyncCursor = nextCursor,
                lyricsText = LrcParser.serialize(updated)
            )
        }
    }

    fun resetTapSyncCursor() {
        syncPlayer.seekToMs(0L)
        _uiState.update { it.copy(currentSyncCursor = 0) }
    }

    fun exitTapSyncMode() {
        syncPlayer.stop()
        _uiState.update { it.copy(isTapSyncActive = false) }
    }

    fun saveTrack() {
        val state = _uiState.value
        if (state.isImporting) return

        viewModelScope.launch {
            if (state.editingTrackId != null) {
                repository.updateTrackDetails(
                    trackId = state.editingTrackId,
                    title = state.title,
                    artist = state.artist,
                    lyricsRaw = state.lyricsText
                )
                _uiState.update { it.copy(savedTrackId = state.editingTrackId) }
                return@launch
            }

            val uri = state.selectedUri
            if (uri == null) {
                _uiState.update { it.copy(errorMessage = "Silakan pilih file audio lagu terlebih dahulu.") }
                return@launch
            }

            _uiState.update { it.copy(isImporting = true, importProgress = 0f, errorMessage = null) }
            try {
                val trackId = repository.importTrack(
                    uri = uri,
                    title = state.title,
                    artist = state.artist,
                    lyricsRaw = state.lyricsText,
                    onProgress = { p ->
                        _uiState.update { it.copy(importProgress = p) }
                    }
                )
                _uiState.update { it.copy(isImporting = false, savedTrackId = trackId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Gagal memproses audio: ${e.message ?: "Format tidak didukung"}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        syncPlayer.release()
    }
}
