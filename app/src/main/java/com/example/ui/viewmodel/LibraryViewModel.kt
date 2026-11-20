package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LiveMixPlayer
import com.example.data.local.KaraokeTrackEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.StudioPreferences
import com.example.data.local.VocaStageDatabase
import com.example.data.local.VocalMixWithTrack
import com.example.data.repository.KaraokeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = VocaStageDatabase.getInstance(application)
    private val repository = KaraokeRepository(application, db.karaokeTrackDao(), db.vocalMixDao())
    private val prefsRepo = PreferencesRepository(application)
    private val previewPlayer = LiveMixPlayer()

    val tracks: StateFlow<List<KaraokeTrackEntity>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mixes: StateFlow<List<VocalMixWithTrack>> = repository.allMixes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studioPrefs: StateFlow<StudioPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioPreferences())

    private val _playingMixId = MutableStateFlow<Long?>(null)
    val playingMixId: StateFlow<Long?> = _playingMixId.asStateFlow()

    val playerPositionMs: StateFlow<Long> = previewPlayer.positionMs
    val playerDurationMs: StateFlow<Long> = previewPlayer.durationMs
    val isPlayerPlaying: StateFlow<Boolean> = previewPlayer.isPlaying

    fun togglePlayExportedMix(mix: VocalMixWithTrack) {
        val path = mix.exportedMp3Path ?: return
        val file = File(path)
        if (!file.exists()) return
        if (_playingMixId.value == mix.id && isPlayerPlaying.value) {
            previewPlayer.stop()
            _playingMixId.value = null
        } else {
            _playingMixId.value = mix.id
            previewPlayer.playMp3File(viewModelScope, file)
        }
    }

    fun stopPlayback() {
        previewPlayer.stop()
        _playingMixId.value = null
    }

    fun deleteTrack(track: KaraokeTrackEntity) {
        viewModelScope.launch {
            repository.deleteTrack(track)
        }
    }

    fun deleteMix(mixId: Long) {
        if (_playingMixId.value == mixId) {
            stopPlayback()
        }
        viewModelScope.launch {
            repository.deleteMix(mixId)
        }
    }

    fun buildShareMp3Intent(mp3Path: String, title: String): Intent? {
        val file = File(mp3Path)
        if (!file.exists()) return null
        val context = getApplication<Application>()
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$title - VocaStage Mix")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun updateDefaultSyncOffset(offsetMs: Int) {
        viewModelScope.launch { prefsRepo.setDefaultSyncOffsetMs(offsetMs) }
    }

    fun updateHeadphoneMode(headphone: Boolean) {
        viewModelScope.launch { prefsRepo.setUseHeadphoneMode(headphone) }
    }

    fun updateDefaultBitrate(kbps: Int) {
        viewModelScope.launch { prefsRepo.setDefaultBitrateKbps(kbps) }
    }

    fun updateCountdownEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setEnableCountdown(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer.release()
    }
}
