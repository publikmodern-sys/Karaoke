package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class VocalMixWithTrack(
    val id: Long,
    val trackId: Long,
    val trackTitle: String,
    val trackArtist: String,
    val backingPcmPath: String,
    val mixName: String,
    val vocalPcmPath: String,
    val exportedMp3Path: String?,
    val exportedDisplayName: String?,
    val durationMs: Long,
    val vocalGainDb: Float,
    val musicGainDb: Float,
    val reverbWet: Float,
    val reverbPreset: String,
    val clarityBoost: Float,
    val warmthGain: Float,
    val noiseGateThresholdDb: Float,
    val compressorAmount: Float,
    val syncOffsetMs: Int,
    val mp3BitrateKbps: Int,
    val createdAt: Long
)

@Dao
interface KaraokeTrackDao {
    @Query("SELECT * FROM karaoke_tracks ORDER BY createdAt DESC")
    fun observeAllTracks(): Flow<List<KaraokeTrackEntity>>

    @Query("SELECT * FROM karaoke_tracks WHERE id = :trackId")
    fun observeTrackById(trackId: Long): Flow<KaraokeTrackEntity?>

    @Query("SELECT * FROM karaoke_tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): KaraokeTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: KaraokeTrackEntity): Long

    @Update
    suspend fun updateTrack(track: KaraokeTrackEntity)

    @Query("DELETE FROM karaoke_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)
}

@Dao
interface VocalMixDao {
    @Query(
        """
        SELECT vm.id, vm.trackId, kt.title AS trackTitle, kt.artist AS trackArtist,
               kt.backingPcmPath, vm.mixName, vm.vocalPcmPath, vm.exportedMp3Path,
               vm.exportedDisplayName, vm.durationMs, vm.vocalGainDb, vm.musicGainDb,
               vm.reverbWet, vm.reverbPreset, vm.clarityBoost, vm.warmthGain,
               vm.noiseGateThresholdDb, vm.compressorAmount, vm.syncOffsetMs,
               vm.mp3BitrateKbps, vm.createdAt
        FROM vocal_mixes vm
        INNER JOIN karaoke_tracks kt ON kt.id = vm.trackId
        ORDER BY vm.createdAt DESC
        """
    )
    fun observeAllMixesWithTrack(): Flow<List<VocalMixWithTrack>>

    @Query("SELECT * FROM vocal_mixes WHERE id = :mixId")
    fun observeMixById(mixId: Long): Flow<VocalMixEntity?>

    @Query("SELECT * FROM vocal_mixes WHERE id = :mixId")
    suspend fun getMixById(mixId: Long): VocalMixEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMix(mix: VocalMixEntity): Long

    @Update
    suspend fun updateMix(mix: VocalMixEntity)

    @Query("DELETE FROM vocal_mixes WHERE id = :mixId")
    suspend fun deleteMixById(mixId: Long)
}
