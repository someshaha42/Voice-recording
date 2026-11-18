package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val activeFilePath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Plays back recorded WAV files with AudioFocus management and smooth position updates.
 */
class AudioPlaybackController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
        }
    }

    fun togglePlayPause(filePath: String, knownDurationMs: Long) {
        val current = _playbackState.value
        if (current.activeFilePath == filePath && mediaPlayer != null) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }
        playFile(filePath, knownDurationMs)
    }

    fun playFile(filePath: String, knownDurationMs: Long) {
        stop()
        if (!requestAudioFocus()) {
            _playbackState.value = PlaybackUiState(
                activeFilePath = filePath,
                errorMessage = "Audio focus unavailable for playback."
            )
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    stopProgressPolling()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        positionMs = 0L
                    )
                    runCatching { it.seekTo(0) }
                }
                start()
            }
            mediaPlayer = player
            _playbackState.value = PlaybackUiState(
                activeFilePath = filePath,
                isPlaying = true,
                positionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(knownDurationMs)
            )
            startProgressPolling()
        } catch (t: Throwable) {
            stop()
            _playbackState.value = PlaybackUiState(
                activeFilePath = filePath,
                errorMessage = t.message ?: "Unable to play WAV file."
            )
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        }
        stopProgressPolling()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun resume() {
        val player = mediaPlayer ?: return
        if (!requestAudioFocus()) return
        player.start()
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        startProgressPolling()
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.toInt().coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(clamped)
        _playbackState.value = _playbackState.value.copy(positionMs = clamped.toLong())
    }

    fun stop() {
        stopProgressPolling()
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        abandonAudioFocus()
        _playbackState.value = PlaybackUiState()
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        positionMs = player.currentPosition.toLong(),
                        durationMs = player.duration.toLong().coerceAtLeast(_playbackState.value.durationMs)
                    )
                }
                delay(50L)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }
}
