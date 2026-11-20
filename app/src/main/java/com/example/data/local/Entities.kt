package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "karaoke_tracks")
data class KaraokeTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val artist: String,
    val backingSourcePath: String,
    val backingPcmPath: String,
    val durationMs: Long,
    val waveformCsv: String,
    val lyricsRaw: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vocal_mixes",
    foreignKeys = [
        ForeignKey(
            entity = KaraokeTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId")]
)
data class VocalMixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val mixName: String,
    val vocalPcmPath: String,
    val exportedMp3Path: String? = null,
    val exportedDisplayName: String? = null,
    val durationMs: Long,
    val vocalGainDb: Float = 2.0f,
    val musicGainDb: Float = -2.5f,
    val reverbWet: Float = 0.22f,
    val reverbPreset: String = "STUDIO",
    val clarityBoost: Float = 0.60f,
    val warmthGain: Float = 1.5f,
    val noiseGateThresholdDb: Float = -46f,
    val compressorAmount: Float = 0.55f,
    val syncOffsetMs: Int = -60,
    val mp3BitrateKbps: Int = 192,
    val createdAt: Long = System.currentTimeMillis()
)
