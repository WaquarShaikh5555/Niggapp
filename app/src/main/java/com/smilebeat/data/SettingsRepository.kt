package com.smilebeat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smilebeat_settings")

enum class DetectionMode {
    DARKER_VISIBLE_TONE,
    EXPERIMENTAL_INVERTED
}

data class AppSettings(
    val volume: Float = 0.7f,
    val toneThreshold: Float = 0.45f, // 0=darkest, 1=brightest; trigger when <= threshold
    val cooldownSeconds: Int = 3,
    val selectedTrack: String = "miguel_nights", // Default Miguel-inspired dark R&B/phonk
    val detectionMode: DetectionMode = DetectionMode.DARKER_VISIBLE_TONE,
    val previewEnabled: Boolean = true,
    val isMuted: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VOLUME = floatPreferencesKey("volume")
        val TONE_THRESHOLD = floatPreferencesKey("tone_threshold")
        val COOLDOWN = intPreferencesKey("cooldown_seconds")
        val SELECTED_TRACK = stringPreferencesKey("selected_track")
        val DETECTION_MODE = stringPreferencesKey("detection_mode")
        val PREVIEW_ENABLED = booleanPreferencesKey("preview_enabled")
        val IS_MUTED = booleanPreferencesKey("is_muted")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            volume = prefs[Keys.VOLUME] ?: 0.7f,
            toneThreshold = prefs[Keys.TONE_THRESHOLD] ?: 0.45f,
            cooldownSeconds = prefs[Keys.COOLDOWN] ?: 3,
            selectedTrack = prefs[Keys.SELECTED_TRACK] ?: "miguel_nights",
            detectionMode = try {
                DetectionMode.valueOf(prefs[Keys.DETECTION_MODE] ?: DetectionMode.DARKER_VISIBLE_TONE.name)
            } catch (e: Exception) {
                DetectionMode.DARKER_VISIBLE_TONE
            },
            previewEnabled = prefs[Keys.PREVIEW_ENABLED] ?: true,
            isMuted = prefs[Keys.IS_MUTED] ?: false
        )
    }

    suspend fun updateVolume(volume: Float) {
        context.dataStore.edit { it[Keys.VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun updateThreshold(threshold: Float) {
        context.dataStore.edit { it[Keys.TONE_THRESHOLD] = threshold.coerceIn(0.05f, 0.95f) }
    }

    suspend fun updateCooldown(seconds: Int) {
        context.dataStore.edit { it[Keys.COOLDOWN] = seconds.coerceIn(1, 15) }
    }

    suspend fun updateTrack(trackName: String) {
        context.dataStore.edit { it[Keys.SELECTED_TRACK] = trackName }
    }

    suspend fun updateDetectionMode(mode: DetectionMode) {
        context.dataStore.edit { it[Keys.DETECTION_MODE] = mode.name }
    }

    suspend fun updatePreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PREVIEW_ENABLED] = enabled }
    }

    suspend fun updateMuted(muted: Boolean) {
        context.dataStore.edit { it[Keys.IS_MUTED] = muted }
    }
}
