package com.smilebeat.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks consecutive darker-tone frames and handles cooldown logic.
 * Strictly visual tone analysis, no race/ethnicity inference.
 */
class ToneTriggerController {

    enum class TriggerState {
        IDLE, // no face or searching
        NORMAL, // tone above threshold
        ANALYZING, // face detected but not enough consecutive frames
        TRIGGERED, // dark tone confirmed, music should play
        COOLDOWN
    }

    data class TriggerStateData(
        val state: TriggerState = TriggerState.IDLE,
        val consecutiveDarkFrames: Int = 0,
        val consecutiveNormalFrames: Int = 0,
        val currentToneScore: Float? = null,
        val threshold: Float = 0.45f,
        val cooldownRemainingMs: Long = 0L,
        val isInCooldown: Boolean = false
    )

    private val _state = MutableStateFlow(TriggerStateData())
    val state: StateFlow<TriggerStateData> = _state

    private var darkFrameCount = 0
    private var normalFrameCount = 0
    private var lastTriggerTimeMs = 0L
    private var cooldownUntilMs = 0L

    private var threshold: Float = 0.45f
    private var cooldownMs: Long = 3000L
    private var requiredConsecutiveFrames: Int = 5
    private var normalFramesToReset: Int = 3 // need 3 normal frames to exit triggered state

    fun configure(threshold: Float, cooldownSeconds: Int) {
        this.threshold = threshold.coerceIn(0.05f, 0.95f)
        this.cooldownMs = (cooldownSeconds.coerceIn(1, 15) * 1000L)
        _state.value = _state.value.copy(threshold = this.threshold)
    }

    /**
     * Call for each analyzed frame.
     * Returns true if should trigger music, false if should stop, null if no change.
     */
    fun onNewToneScore(toneScore: Float?, hasFace: Boolean, isPoorLighting: Boolean = false): TriggerDecision {
        val now = System.currentTimeMillis()

        // Handle cooldown
        if (now < cooldownUntilMs) {
            val remaining = cooldownUntilMs - now
            _state.value = _state.value.copy(
                state = TriggerState.COOLDOWN,
                cooldownRemainingMs = remaining,
                isInCooldown = true,
                currentToneScore = toneScore
            )
            // During cooldown, still track but don't trigger
            return TriggerDecision.NoChange
        } else if (_state.value.isInCooldown) {
            // Cooldown ended
            _state.value = _state.value.copy(isInCooldown = false, cooldownRemainingMs = 0L)
        }

        if (!hasFace || toneScore == null) {
            darkFrameCount = 0
            normalFrameCount = 0
            _state.value = _state.value.copy(
                state = TriggerState.IDLE,
                consecutiveDarkFrames = 0,
                consecutiveNormalFrames = 0,
                currentToneScore = toneScore
            )
            return TriggerDecision.ShouldStop
        }

        val isDark = toneScore <= threshold

        if (isDark) {
            darkFrameCount++
            normalFrameCount = 0
        } else {
            normalFrameCount++
            // Only reset dark count after enough normal frames to avoid flicker
            if (normalFrameCount >= 2) {
                darkFrameCount = 0
            }
        }

        val currentState = _state.value.state

        return if (isDark) {
            if (darkFrameCount >= requiredConsecutiveFrames) {
                if (currentState != TriggerState.TRIGGERED) {
                    lastTriggerTimeMs = now
                    _state.value = _state.value.copy(
                        state = TriggerState.TRIGGERED,
                        consecutiveDarkFrames = darkFrameCount,
                        consecutiveNormalFrames = normalFrameCount,
                        currentToneScore = toneScore
                    )
                    TriggerDecision.ShouldTrigger
                } else {
                    _state.value = _state.value.copy(
                        state = TriggerState.TRIGGERED,
                        consecutiveDarkFrames = darkFrameCount,
                        consecutiveNormalFrames = 0,
                        currentToneScore = toneScore
                    )
                    TriggerDecision.NoChange
                }
            } else {
                _state.value = _state.value.copy(
                    state = TriggerState.ANALYZING,
                    consecutiveDarkFrames = darkFrameCount,
                    consecutiveNormalFrames = normalFrameCount,
                    currentToneScore = toneScore
                )
                TriggerDecision.NoChange
            }
        } else {
            // Normal tone
            if (currentState == TriggerState.TRIGGERED) {
                if (normalFrameCount >= normalFramesToReset) {
                    // Exit triggered, enter cooldown
                    cooldownUntilMs = now + cooldownMs
                    darkFrameCount = 0
                    _state.value = _state.value.copy(
                        state = TriggerState.COOLDOWN,
                        consecutiveDarkFrames = 0,
                        consecutiveNormalFrames = normalFrameCount,
                        currentToneScore = toneScore,
                        cooldownRemainingMs = cooldownMs,
                        isInCooldown = true
                    )
                    TriggerDecision.ShouldStop
                } else {
                    // Still in triggered but counting normal frames
                    _state.value = _state.value.copy(
                        state = TriggerState.TRIGGERED,
                        consecutiveDarkFrames = darkFrameCount,
                        consecutiveNormalFrames = normalFrameCount,
                        currentToneScore = toneScore
                    )
                    TriggerDecision.NoChange
                }
            } else {
                _state.value = _state.value.copy(
                    state = TriggerState.NORMAL,
                    consecutiveDarkFrames = darkFrameCount,
                    consecutiveNormalFrames = normalFrameCount,
                    currentToneScore = toneScore
                )
                TriggerDecision.NoChange
            }
        }
    }

    fun reset() {
        darkFrameCount = 0
        normalFrameCount = 0
        cooldownUntilMs = 0L
        _state.value = TriggerStateData(threshold = threshold)
    }

    fun startCooldown() {
        cooldownUntilMs = System.currentTimeMillis() + cooldownMs
    }

    sealed class TriggerDecision {
        object ShouldTrigger : TriggerDecision()
        object ShouldStop : TriggerDecision()
        object NoChange : TriggerDecision()
    }
}
