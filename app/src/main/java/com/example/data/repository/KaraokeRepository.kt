package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.audio.AudioDecoder
import com.example.audio.MixSettings
import com.example.audio.ReverbPreset
import com.example.data.local.KaraokeTrackDao
import com.example.data.local.KaraokeTrackEntity
import com.example.data.local.VocalMixDao
import com.example.data.local.VocalMixEntity
import com.example.data.local.VocalMixWithTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class KaraokeRepository(
    private val context: Context,
    private val trackDao: KaraokeTrackDao,
    private val mixDao: VocalMixDao
) {
    private val tracksDir = File(context.filesDir, "tracks").apply { mkdirs() }
    private val vocalsDir = File(context.filesDir, "vocals").apply { mkdirs() }
    private val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }

    val allTracks: Flow<List<KaraokeTrackEntity>> = trackDao.observeAllTracks()
    val allMixes: Flow<List<VocalMixWithTrack>> = mixDao.observeAllMixesWithTrack()

    fun observeTrack(trackId: Long): Flow<KaraokeTrackEntity?> = trackDao.observeTrackById(trackId)
    fun observeMix(mixId: Long): Flow<VocalMixEntity?> = mixDao.observeMixById(mixId)

    suspend fun getTrack(trackId: Long): KaraokeTrackEntity? = trackDao.getTrackById(trackId)
    suspend fun getMix(mixId: Long): VocalMixEntity? = mixDao.getMixById(mixId)

    suspend fun importTrack(
        uri: Uri,
        title: String,
        artist: String,
        lyricsRaw: String,
        onProgress: (Float) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val idTag = UUID.randomUUID().toString().take(8)
        val pcmFile = File(tracksDir, "backing_${idTag}.pcm")
        val decoded = AudioDecoder.decodeUriToStereoPcm44100(
            context = context,
            uri = uri,
            destPcmFile = pcmFile,
            onProgress = onProgress
        )
        val waveformCsv = decoded.waveformPeaks.joinToString(",") { "%.3f".format(java.util.Locale.US, it) }
        val entity = KaraokeTrackEntity(
            title = title.trim().ifBlank { "Lagu Tanpa Judul" },
            artist = artist.trim().ifBlank { "Artis Tidak Diketahui" },
            backingSourcePath = uri.toString(),
            backingPcmPath = pcmFile.absolutePath,
            durationMs = decoded.durationMs,
            waveformCsv = waveformCsv,
            lyricsRaw = lyricsRaw.trim()
        )
        trackDao.insertTrack(entity)
    }

    suspend fun updateTrackDetails(
        trackId: Long,
        title: String,
        artist: String,
        lyricsRaw: String
    ) = withContext(Dispatchers.IO) {
        val existing = trackDao.getTrackById(trackId) ?: return@withContext
        trackDao.updateTrack(
            existing.copy(
                title = title.trim().ifBlank { existing.title },
                artist = artist.trim().ifBlank { existing.artist },
                lyricsRaw = lyricsRaw.trim()
            )
        )
    }

    suspend fun deleteTrack(track: KaraokeTrackEntity) = withContext(Dispatchers.IO) {
        trackDao.deleteTrackById(track.id)
        File(track.backingPcmPath).delete()
    }

    fun createNewVocalPcmFile(trackId: Long): File {
        val idTag = UUID.randomUUID().toString().take(8)
        return File(vocalsDir, "vocal_${trackId}_${idTag}.pcm")
    }

    fun createExportMp3File(fileNameBase: String): File {
        val safeName = fileNameBase.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(40)
            .ifBlank { "VocaStage_Mix" }
        val suffix = System.currentTimeMillis().toString().takeLast(5)
        return File(exportsDir, "${safeName}_${suffix}.mp3")
    }

    suspend fun saveRecordedMix(
        trackId: Long,
        mixName: String,
        vocalPcmFile: File,
        durationMs: Long,
        defaultSyncOffsetMs: Int,
        defaultBitrateKbps: Int
    ): Long = withContext(Dispatchers.IO) {
        val entity = VocalMixEntity(
            trackId = trackId,
            mixName = mixName,
            vocalPcmPath = vocalPcmFile.absolutePath,
            durationMs = durationMs,
            syncOffsetMs = defaultSyncOffsetMs,
            mp3BitrateKbps = defaultBitrateKbps
        )
        mixDao.insertMix(entity)
    }

    suspend fun updateMixSettings(
        mixId: Long,
        mixName: String,
        settings: MixSettings,
        bitrateKbps: Int
    ) = withContext(Dispatchers.IO) {
        val existing = mixDao.getMixById(mixId) ?: return@withContext
        mixDao.updateMix(
            existing.copy(
                mixName = mixName.trim().ifBlank { existing.mixName },
                vocalGainDb = settings.vocalGainDb,
                musicGainDb = settings.musicGainDb,
                reverbWet = settings.reverbWet,
                reverbPreset = settings.reverbPreset.id,
                clarityBoost = settings.clarityBoost,
                warmthGain = settings.warmthGain,
                noiseGateThresholdDb = settings.noiseGateThresholdDb,
                compressorAmount = settings.compressorAmount,
                syncOffsetMs = settings.syncOffsetMs,
                mp3BitrateKbps = bitrateKbps
            )
        )
    }

    suspend fun markMixExported(
        mixId: Long,
        mp3Path: String,
        displayName: String
    ) = withContext(Dispatchers.IO) {
        val existing = mixDao.getMixById(mixId) ?: return@withContext
        mixDao.updateMix(
            existing.copy(
                exportedMp3Path = mp3Path,
                exportedDisplayName = displayName
            )
        )
    }

    suspend fun deleteMix(mixId: Long) = withContext(Dispatchers.IO) {
        val existing = mixDao.getMixById(mixId)
        if (existing != null) {
            File(existing.vocalPcmPath).delete()
            existing.exportedMp3Path?.let { File(it).delete() }
            mixDao.deleteMixById(mixId)
        }
    }

    fun VocalMixEntity.toMixSettings(): MixSettings = MixSettings(
        vocalGainDb = vocalGainDb,
        musicGainDb = musicGainDb,
        reverbWet = reverbWet,
        reverbPreset = ReverbPreset.fromId(reverbPreset),
        clarityBoost = clarityBoost,
        warmthGain = warmthGain,
        noiseGateThresholdDb = noiseGateThresholdDb,
        compressorAmount = compressorAmount,
        syncOffsetMs = syncOffsetMs,
        soloVocal = false
    )
}
