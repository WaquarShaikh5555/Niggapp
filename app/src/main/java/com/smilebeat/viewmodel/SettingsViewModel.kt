package com.smilebeat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smilebeat.data.AppSettings
import com.smilebeat.data.DetectionMode
import com.smilebeat.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun updateVolume(volume: Float) {
        viewModelScope.launch { repository.updateVolume(volume) }
    }

    fun updateThreshold(threshold: Float) {
        viewModelScope.launch { repository.updateThreshold(threshold) }
    }

    fun updateCooldown(seconds: Int) {
        viewModelScope.launch { repository.updateCooldown(seconds) }
    }

    fun updateTrack(trackName: String) {
        viewModelScope.launch { repository.updateTrack(trackName) }
    }

    fun updateDetectionMode(mode: DetectionMode) {
        viewModelScope.launch { repository.updateDetectionMode(mode) }
    }

    fun updatePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updatePreviewEnabled(enabled) }
    }

    fun updateMuted(muted: Boolean) {
        viewModelScope.launch { repository.updateMuted(muted) }
    }

    fun getAvailableTracks(): List<String> {
        return try {
            val rawClass = com.smilebeat.R.raw::class.java
            rawClass.fields.map { it.name }
                .filter { !it.lowercase().contains("readme") }
                .sorted()
        } catch (e: Exception) {
            listOf("phonk_vibe_01", "midnight_phonk", "miguel_nights")
        }
    }
}
