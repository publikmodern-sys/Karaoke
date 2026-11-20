package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDecoder
import com.example.audio.LiveMixPlayer
import com.example.audio.MixSettings
import com.example.audio.Mp3MixExporter
import com.example.audio.ReverbPreset
import com.example.audio.StudioMixPreset
import com.example.data.local.KaraokeTrackEntity
import com.example.data.local.VocaStageDatabase
import com.example.data.local.VocalMixEntity
import com.example.data.repository.KaraokeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MixStudioUiState(
    val mix: VocalMixEntity? = null,
    val track: KaraokeTrackEntity? = null,
    val mixName: String = "",
    val settings: MixSettings = MixSettings(),
    val bitrateKbps: Int = 192,
    val waveformPeaks: List<Float> = emptyList(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportedMp3Path: String? = null,
    val exportedFileSizeBytes: Long = 0L,
    val savedToMusicFolder: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MixStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val db = VocaStageDatabase.getInstance(application)
    private val repository = KaraokeRepository(application, db.karaokeTrackDao(), db.vocalMixDao())
    private val livePlayer = LiveMixPlayer()

    private val _uiState = MutableStateFlow(MixStudioUiState())
    val uiState: StateFlow<MixStudioUiState> = _uiState.asStateFlow()

    val isPlaying: StateFlow<Boolean> = livePlayer.isPlaying
    val positionMs: StateFlow<Long> = livePlayer.positionMs
    val durationMs: StateFlow<Long> = livePlayer.durationMs
    val levelMeter: StateFlow<Float> = livePlayer.levelMeter

    fun loadMix(mixId: Long) {
        viewModelScope.launch {
            val mix = repository.getMix(mixId)
            if (mix == null) {
                _uiState.update { it.copy(errorMessage = "Data rekaman tidak ditemukan.") }
                return@launch
            }
            val track = repository.getTrack(mix.trackId)
            val peaks = if (track != null && track.waveformCsv.isNotBlank()) {
                track.waveformCsv.split(",").mapNotNull { it.toFloatOrNull() }
            } else if (track != null) {
                withContext(Dispatchers.IO) {
                    AudioDecoder.computeWaveformFromPcm(File(track.backingPcmPath))
                }
            } else emptyList()

            val loadedSettings = MixSettings(
                vocalGainDb = mix.vocalGainDb,
                musicGainDb = mix.musicGainDb,
                reverbWet = mix.reverbWet,
                reverbPreset = ReverbPreset.fromId(mix.reverbPreset),
                clarityBoost = mix.clarityBoost,
                warmthGain = mix.warmthGain,
                noiseGateThresholdDb = mix.noiseGateThresholdDb,
                compressorAmount = mix.compressorAmount,
                syncOffsetMs = mix.syncOffsetMs,
                soloVocal = false
            )
            livePlayer.updateSettings(loadedSettings)
            val existingMp3 = mix.exportedMp3Path?.let { File(it) }?.takeIf { it.exists() }

            _uiState.update {
                it.copy(
                    mix = mix,
                    track = track,
                    mixName = mix.mixName,
                    settings = loadedSettings,
                    bitrateKbps = mix.mp3BitrateKbps,
                    waveformPeaks = peaks,
                    exportedMp3Path = existingMp3?.absolutePath,
                    exportedFileSizeBytes = existingMp3?.length() ?: 0L,
                    errorMessage = null
                )
            }
        }
    }

    fun togglePreviewPlayback() {
        val state = _uiState.value
        val track = state.track ?: return
        val mix = state.mix ?: return
        if (livePlayer.isPlaying.value) {
            livePlayer.stop()
        } else {
            livePlayer.startPcmMixPreview(
                scope = viewModelScope,
                backingPcmFile = File(track.backingPcmPath),
                vocalPcmFile = File(mix.vocalPcmPath),
                initialSettings = state.settings,
                startPositionMs = livePlayer.positionMs.value
            )
        }
    }

    fun seekToFraction(fraction: Float) {
        val dur = (_uiState.value.track?.durationMs ?: livePlayer.durationMs.value).coerceAtLeast(1L)
        val targetMs = (fraction.coerceIn(0f, 1f) * dur).toLong()
        livePlayer.seekToMs(targetMs)
    }

    fun updateMixName(name: String) {
        _uiState.update { it.copy(mixName = name) }
    }

    fun updateSettings(transform: (MixSettings) -> MixSettings) {
        _uiState.update { current ->
            val updated = transform(current.settings)
            livePlayer.updateSettings(updated)
            current.copy(settings = updated)
        }
    }

    fun applyPreset(preset: StudioMixPreset) {
        updateSettings { preset.apply(it) }
    }

    fun updateBitrate(kbps: Int) {
        _uiState.update { it.copy(bitrateKbps = kbps) }
    }

    fun saveSettingsOnly() {
        val state = _uiState.value
        val mix = state.mix ?: return
        viewModelScope.launch {
            repository.updateMixSettings(
                mixId = mix.id,
                mixName = state.mixName,
                settings = state.settings,
                bitrateKbps = state.bitrateKbps
            )
            _uiState.update { it.copy(statusMessage = "Pengaturan mixing tersimpan.") }
        }
    }

    fun exportToMp3() {
        val state = _uiState.value
        val mix = state.mix ?: return
        val track = state.track ?: return
        if (state.isExporting) return

        livePlayer.stop()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    errorMessage = null,
                    statusMessage = null
                )
            }
            try {
                repository.updateMixSettings(
                    mixId = mix.id,
                    mixName = state.mixName,
                    settings = state.settings,
                    bitrateKbps = state.bitrateKbps
                )
                val outMp3File = repository.createExportMp3File(state.mixName.ifBlank { track.title })
                val result = Mp3MixExporter.renderAndExportMp3(
                    context = getApplication(),
                    backingPcmFile = File(track.backingPcmPath),
                    vocalPcmFile = File(mix.vocalPcmPath),
                    outputMp3File = outMp3File,
                    exportDisplayTitle = state.mixName.ifBlank { track.title },
                    settings = state.settings,
                    bitrateKbps = state.bitrateKbps,
                    onProgress = { p ->
                        _uiState.update { it.copy(exportProgress = p) }
                    }
                )
                repository.markMixExported(
                    mixId = mix.id,
                    mp3Path = result.internalMp3File.absolutePath,
                    displayName = result.displayName
                )
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        exportedMp3Path = result.internalMp3File.absolutePath,
                        exportedFileSizeBytes = result.fileSizeBytes,
                        savedToMusicFolder = result.mediaStoreUri != null,
                        statusMessage = if (result.mediaStoreUri != null) {
                            "MP3 tersimpan di folder Musik/VocaStage (${result.displayName})"
                        } else {
                            "MP3 berhasil dirender! Gunakan 'Simpan ke Perangkat' untuk memilih folder."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Gagal mengekspor MP3: ${e.message}"
                    )
                }
            }
        }
    }

    fun playExportedMp3() {
        val path = _uiState.value.exportedMp3Path ?: return
        val file = File(path)
        if (!file.exists()) return
        livePlayer.playMp3File(viewModelScope, file)
    }

    fun saveMp3CopyToUri(destUri: Uri) {
        val path = _uiState.value.exportedMp3Path ?: return
        val file = File(path)
        if (!file.exists()) return
        viewModelScope.launch {
            val ok = Mp3MixExporter.copyMp3ToUri(getApplication(), file, destUri)
            _uiState.update {
                if (ok) it.copy(statusMessage = "File MP3 berhasil diunduh ke lokasi pilihanmu.")
                else it.copy(errorMessage = "Gagal menulis file MP3 ke lokasi tujuan.")
            }
        }
    }

    fun buildShareMp3Intent(): Intent? {
        val path = _uiState.value.exportedMp3Path ?: return null
        val file = File(path)
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
            putExtra(Intent.EXTRA_SUBJECT, "${_uiState.value.mixName} - VocaStage")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        livePlayer.release()
    }
}
