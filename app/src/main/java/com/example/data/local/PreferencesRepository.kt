package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "vocastage_prefs")

data class StudioPreferences(
    val defaultSyncOffsetMs: Int = -60,
    val useHeadphoneMode: Boolean = true,
    val defaultBitrateKbps: Int = 192,
    val enableCountdown: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val DEFAULT_SYNC_OFFSET_MS = intPreferencesKey("default_sync_offset_ms")
        val USE_HEADPHONE_MODE = booleanPreferencesKey("use_headphone_mode")
        val DEFAULT_BITRATE_KBPS = intPreferencesKey("default_bitrate_kbps")
        val ENABLE_COUNTDOWN = booleanPreferencesKey("enable_countdown")
    }

    val preferences: Flow<StudioPreferences> = context.studioDataStore.data.map { prefs ->
        StudioPreferences(
            defaultSyncOffsetMs = prefs[Keys.DEFAULT_SYNC_OFFSET_MS] ?: -60,
            useHeadphoneMode = prefs[Keys.USE_HEADPHONE_MODE] ?: true,
            defaultBitrateKbps = prefs[Keys.DEFAULT_BITRATE_KBPS] ?: 192,
            enableCountdown = prefs[Keys.ENABLE_COUNTDOWN] ?: true
        )
    }

    suspend fun setDefaultSyncOffsetMs(offsetMs: Int) {
        context.studioDataStore.edit { it[Keys.DEFAULT_SYNC_OFFSET_MS] = offsetMs }
    }

    suspend fun setUseHeadphoneMode(headphone: Boolean) {
        context.studioDataStore.edit { it[Keys.USE_HEADPHONE_MODE] = headphone }
    }

    suspend fun setDefaultBitrateKbps(bitrate: Int) {
        context.studioDataStore.edit { it[Keys.DEFAULT_BITRATE_KBPS] = bitrate }
    }

    suspend fun setEnableCountdown(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.ENABLE_COUNTDOWN] = enabled }
    }
}
