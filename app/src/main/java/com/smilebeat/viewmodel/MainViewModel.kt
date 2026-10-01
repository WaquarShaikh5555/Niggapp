package com.smilebeat.viewmodel

import android.app.Application
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smilebeat.audio.AudioPlayerManager
import com.smilebeat.data.AppSettings
import com.smilebeat.data.SettingsRepository
import com.smilebeat.detection.FaceDetectorWrapper
import com.smilebeat.detection.SkinToneAnalyzer
import com.smilebeat.detection.ToneTriggerController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val faceDetector = FaceDetectorWrapper()
    private val toneAnalyzer = SkinToneAnalyzer()
    private val triggerController = ToneTriggerController()
    private val audioManager = AudioPlayerManager(application)

    // Exposed for CameraController creation in UI
    fun getFaceDetector() = faceDetector
    fun getToneAnalyzer() = toneAnalyzer

    data class UiState(
        val isCameraActive: Boolean = false,
        val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
        val hasFace: Boolean = false,
        val faceCount: Int = 0,
        val toneScore: Float? = null,
        val toneConfidence: Float = 0f,
        val isPoorLighting: Boolean = false,
        val triggerState: ToneTriggerController.TriggerState = ToneTriggerController.TriggerState.IDLE,
        val cooldownRemaining: Long = 0L,
        val isMuted: Boolean = false,
        val settings: AppSettings = AppSettings(),
        val errorMessage: String? = null,
        val isAnalyzingLargestFace: Boolean = false,
        val audioState: AudioPlayerManager.AudioState = AudioPlayerManager.AudioState(),
        val musicStatusText: String = "VIBE READY"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    init {
        // Observe settings
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                triggerController.configure(settings.toneThreshold, settings.cooldownSeconds)
                audioManager.updateVolume(settings.volume)
                audioManager.updateMuted(settings.isMuted)
                // Load track if changed
                if (_uiState.value.audioState.currentTrack != settings.selectedTrack) {
                    audioManager.loadTrack(settings.selectedTrack)
                }

                _uiState.update {
                    it.copy(
                        settings = settings,
                        isMuted = settings.isMuted,
                        lensFacing = it.lensFacing // keep
                    )
                }
            }
        }

        // Observe trigger state
        viewModelScope.launch {
            triggerController.state.collect { triggerData ->
                val musicStatus = when (triggerData.state) {
                    ToneTriggerController.TriggerState.TRIGGERED -> "VIBING"
                    ToneTriggerController.TriggerState.COOLDOWN -> "COOLDOWN"
                    else -> "VIBE READY"
                }

                _uiState.update {
                    it.copy(
                        triggerState = triggerData.state,
                        cooldownRemaining = triggerData.cooldownRemainingMs,
                        toneScore = triggerData.currentToneScore ?: it.toneScore,
                        musicStatusText = musicStatus
                    )
                }
            }
        }

        // Observe audio state
        viewModelScope.launch {
            audioManager.state.collect { audioState ->
                _uiState.update { it.copy(audioState = audioState) }
            }
        }

        // Load default track
        viewModelScope.launch {
            val defaultTrack = _uiState.value.settings.selectedTrack
            audioManager.loadTrack(defaultTrack)
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
    }

    fun setCameraActive(active: Boolean) {
        _uiState.update { it.copy(isCameraActive = active) }
    }

    fun flipCamera() {
        val newFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        _uiState.update { it.copy(lensFacing = newFacing) }
    }

    fun getCurrentLensFacing(): Int = _uiState.value.lensFacing

    fun onFrameAnalyzed(
        hasFace: Boolean,
        faceCount: Int,
        toneScore: Float?,
        confidence: Float,
        isPoorLighting: Boolean
    ) {
        val decision = triggerController.onNewToneScore(
            toneScore = toneScore,
            hasFace = hasFace,
            isPoorLighting = isPoorLighting
        )

        when (decision) {
            is ToneTriggerController.TriggerDecision.ShouldTrigger -> {
                if (!_uiState.value.isMuted) {
                    audioManager.playWithFadeIn()
                }
            }
            is ToneTriggerController.TriggerDecision.ShouldStop -> {
                audioManager.stopWithFadeOut()
            }
            else -> {}
        }

        _uiState.update {
            it.copy(
                hasFace = hasFace,
                faceCount = faceCount,
                toneScore = toneScore ?: it.toneScore,
                toneConfidence = confidence,
                isPoorLighting = isPoorLighting,
                isAnalyzingLargestFace = faceCount > 1
            )
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val newMuted = !_uiState.value.isMuted
            settingsRepo.updateMuted(newMuted)
            if (newMuted) {
                audioManager.stopWithFadeOut()
            }
            _uiState.update { it.copy(isMuted = newMuted) }
        }
    }

    fun updateVolume(volume: Float) {
        viewModelScope.launch {
            settingsRepo.updateVolume(volume)
        }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onAppBackgrounded() {
        // Stop camera processing handled in UI, but stop audio fading
        audioManager.stopWithFadeOut()
        triggerController.reset()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            faceDetector.close()
            audioManager.release()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Clear failed", e)
        }
    }

    fun getAudioManager(): AudioPlayerManager = audioManager
    fun getSettingsRepository(): SettingsRepository = settingsRepo
}
