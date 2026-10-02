package com.smilebeat.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayerManager(private val context: Context) {

    enum class PlaybackState {
        IDLE,
        READY,
        PLAYING,
        FADING_OUT,
        COOLDOWN,
        ERROR
    }

    data class AudioState(
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val currentTrack: String = "miguel_nights",
        val volume: Float = 0.7f,
        val isMuted: Boolean = false,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state

    private var exoPlayer: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fadeJob: Job? = null

    private var targetVolume: Float = 0.7f

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initPlayer()
    }

    private fun initPlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("AudioPlayerManager", "Playback error: ${error.message}", error)
                        _state.value = _state.value.copy(
                            playbackState = PlaybackState.ERROR,
                            errorMessage = error.message
                        )
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _state.value = _state.value.copy(playbackState = PlaybackState.READY)
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to init player", e)
            _state.value = _state.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = e.message
            )
        }
    }

    fun loadTrack(trackName: String) {
        val resId = getRawResId(trackName)
        if (resId == 0) {
            Log.e("AudioPlayerManager", "Track not found: $trackName")
            _state.value = _state.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = "Track not found: $trackName"
            )
            return
        }

        try {
            exoPlayer?.let { player ->
                val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
                player.setMediaItem(mediaItem)
                player.prepare()
            }
            _state.value = _state.value.copy(
                currentTrack = trackName,
                playbackState = PlaybackState.READY,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to load track", e)
            _state.value = _state.value.copy(
                playbackState = PlaybackState.ERROR,
                errorMessage = e.message
            )
        }
    }

    fun updateVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = targetVolume)
        if (_state.value.playbackState == PlaybackState.PLAYING && !_state.value.isMuted) {
            exoPlayer?.volume = targetVolume
        }
    }

    fun updateMuted(isMuted: Boolean) {
        _state.value = _state.value.copy(isMuted = isMuted)
        exoPlayer?.volume = if (isMuted) 0f else targetVolume
    }

    fun playWithFadeIn() {
        if (_state.value.isMuted) return
        if (!requestAudioFocus()) {
            Log.w("AudioPlayerManager", "Audio focus not granted")
        }

        fadeJob?.cancel()
        scope.launch {
            try {
                exoPlayer?.let { player ->
                    if (player.playbackState == Player.STATE_IDLE) {
                        loadTrack(_state.value.currentTrack)
                    }
                    player.volume = 0f
                    player.playWhenReady = true
                    player.play()
                    _state.value = _state.value.copy(playbackState = PlaybackState.PLAYING)

                    // Fade in over 800ms
                    val steps = 20
                    val delayMs = 40L
                    for (i in 1..steps) {
                        if (!isActive) break
                        val vol = (targetVolume * i / steps).coerceIn(0f, 1f)
                        player.volume = if (_state.value.isMuted) 0f else vol
                        delay(delayMs)
                    }
                    player.volume = if (_state.value.isMuted) 0f else targetVolume
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Fade in failed", e)
            }
        }
    }

    fun stopWithFadeOut(onFinished: (() -> Unit)? = null) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            try {
                exoPlayer?.let { player ->
                    if (!player.isPlaying) {
                        onFinished?.invoke()
                        return@let
                    }
                    _state.value = _state.value.copy(playbackState = PlaybackState.FADING_OUT)
                    val startVolume = player.volume
                    val steps = 15
                    val delayMs = 40L
                    for (i in steps downTo 1) {
                        if (!isActive) break
                        val vol = (startVolume * i / steps).coerceIn(0f, 1f)
                        player.volume = vol
                        delay(delayMs)
                    }
                    player.volume = 0f
                    player.pause()
                    _state.value = _state.value.copy(playbackState = PlaybackState.READY)
                    abandonAudioFocus()
                    onFinished?.invoke()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Fade out failed", e)
                exoPlayer?.pause()
                onFinished?.invoke()
            }
        }
    }

    fun stopImmediately() {
        fadeJob?.cancel()
        exoPlayer?.pause()
        exoPlayer?.volume = 0f
        _state.value = _state.value.copy(playbackState = PlaybackState.READY)
        abandonAudioFocus()
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        return try {
            val am = audioManager ?: return false
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> stopWithFadeOut()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> exoPlayer?.pause()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                exoPlayer?.volume = targetVolume * 0.3f
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                exoPlayer?.volume = if (_state.value.isMuted) 0f else targetVolume
                                if (_state.value.playbackState == PlaybackState.PLAYING) exoPlayer?.play()
                            }
                        }
                    }
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> stopWithFadeOut()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> exoPlayer?.pause()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Audio focus request failed", e)
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
            hasAudioFocus = false
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Abandon focus failed", e)
        }
    }

    private fun getRawResId(trackName: String): Int {
        return context.resources.getIdentifier(trackName, "raw", context.packageName)
    }

    fun getAvailableTracks(): List<String> {
        // Use reflection over R.raw to list bundled tracks
        return try {
            val rawClass = com.smilebeat.R.raw::class.java
            rawClass.fields.map { it.name }
                .filter { !it.lowercase().contains("readme") }
                .filter { it.endsWith(".wav").not() } // R fields don't have extension, but keep filter
                .sorted()
        } catch (e: Exception) {
            listOf("phonk_vibe_01", "midnight_phonk", "miguel_nights")
        }
    }

    fun release() {
        fadeJob?.cancel()
        scope.cancel()
        try {
            exoPlayer?.release()
        } catch (_: Exception) {}
        exoPlayer = null
        abandonAudioFocus()
    }
}
