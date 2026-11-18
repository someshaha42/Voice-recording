package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import com.example.data.ImportedAudioSource
import com.example.dsp.EffectsRackConfig
import com.example.dsp.StudioDspPipeline
import com.example.dsp.VoiceChangerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class ImportedPlaybackState(
    val source: ImportedAudioSource? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val detectedNote: String = "--"
)

/**
 * Real-time 48 kHz PCM streaming player for imported audio / extracted video audio.
 * Routes every playback frame through its dedicated [StudioDspPipeline] instance
 * configured identically to [RealTimeAudioEngine].
 *
 * CRITICAL: Updating effects, Voice Changer, or any DSP parameter updates volatile configs
 * atomically in place WITHOUT restarting playback from 0, rebuilding AudioTrack, or resetting timeline!
 */
class ImportedAudioEngine {

    private val dspPipeline = StudioDspPipeline(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)

    private val _state = MutableStateFlow(ImportedPlaybackState())
    val state: StateFlow<ImportedPlaybackState> = _state.asStateFlow()

    private val playheadFrame = AtomicLong(0L)
    private val pendingSeekFrame = AtomicLong(-1L)

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var shouldPlay = false

    fun updateEffectsConfig(config: EffectsRackConfig) {
        dspPipeline.updateEffectsConfig(config)
    }

    fun updateVoiceChangerConfig(config: VoiceChangerConfig) {
        dspPipeline.updateVoiceChangerConfig(config)
    }

    fun setImporting(importing: Boolean) {
        _state.value = _state.value.copy(isImporting = importing)
    }

    fun setExporting(exporting: Boolean, progress: Float = 0f) {
        _state.value = _state.value.copy(isExporting = exporting, exportProgress = progress)
    }

    fun loadSource(source: ImportedAudioSource) {
        pause()
        playheadFrame.set(0L)
        pendingSeekFrame.set(-1L)
        dspPipeline.reset()
        _state.value = ImportedPlaybackState(
            source = source,
            isPlaying = false,
            positionMs = 0L,
            durationMs = source.durationMs
        )
    }

    fun clearSource() {
        pause()
        playheadFrame.set(0L)
        pendingSeekFrame.set(-1L)
        dspPipeline.reset()
        _state.value = ImportedPlaybackState()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val src = _state.value.source ?: return
        if (shouldPlay && workerThread?.isAlive == true) return
        if (playheadFrame.get() >= src.totalFrames48k) {
            playheadFrame.set(0L)
        }
        shouldPlay = true
        _state.value = _state.value.copy(isPlaying = true)
        val thread = Thread(::runPlaybackLoop, "VoxForgeImportedPlayer")
        workerThread = thread
        thread.start()
    }

    fun pause() {
        shouldPlay = false
        val t = workerThread
        workerThread = null
        if (t != null && t !== Thread.currentThread()) {
            runCatching { t.join(400) }
        }
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun seekTo(positionMs: Long) {
        val src = _state.value.source ?: return
        val clampedMs = positionMs.coerceIn(0L, src.durationMs.coerceAtLeast(0L))
        val targetFrame = ((clampedMs * AudioConfig.OUTPUT_SAMPLE_RATE_HZ) / 1000L)
            .coerceIn(0L, src.totalFrames48k)
        pendingSeekFrame.set(targetFrame)
        playheadFrame.set(targetFrame)
        _state.value = _state.value.copy(positionMs = clampedMs)
    }

    fun currentPlayheadFrame(): Long = playheadFrame.get()

    private fun runPlaybackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val src = _state.value.source ?: run {
            shouldPlay = false
            _state.value = _state.value.copy(isPlaying = false)
            return
        }

        val blockFrames = 512
        val byteBuf = ByteArray(blockFrames * 2)
        val floatBuf = FloatArray(blockFrames)
        val outShortBuf = ShortArray(blockFrames)

        var track: AudioTrack? = null
        var raf: RandomAccessFile? = null

        try {
            val minBytes = AudioTrack.getMinBufferSize(
                AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBytes.coerceAtLeast(blockFrames * 2 * 4))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            track = trackBuilder.build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                shouldPlay = false
                _state.value = _state.value.copy(isPlaying = false)
                return
            }

            raf = RandomAccessFile(src.pcmFile, "r")
            raf.seek(playheadFrame.get() * 2L)
            track.play()

            var lastUiMsUpdate = 0L

            while (shouldPlay) {
                val seekReq = pendingSeekFrame.getAndSet(-1L)
                if (seekReq >= 0L) {
                    raf.seek((seekReq * 2L).coerceAtMost(src.pcmFile.length()))
                    playheadFrame.set(seekReq)
                    runCatching { track.flush() }
                }

                val bytesRead = readChunk(raf, byteBuf, byteBuf.size)
                if (bytesRead <= 1) {
                    // End of imported audio reached cleanly
                    playheadFrame.set(0L)
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
                    shouldPlay = false
                    break
                }

                val framesRead = bytesRead / 2
                for (i in 0 until framesRead) {
                    val lo = byteBuf[i * 2].toInt() and 0xFF
                    val hi = byteBuf[i * 2 + 1].toInt()
                    val sample16 = (hi shl 8) or lo
                    floatBuf[i] = sample16 / 32768.0f
                }

                // Process smoothly through unified Studio DSP Pipeline using latest @Volatile parameters
                dspPipeline.processInPlace(floatBuf, framesRead)

                for (i in 0 until framesRead) {
                    val clamped = floatBuf[i].coerceIn(-1f, 1f)
                    outShortBuf[i] = (clamped * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
                }

                val written = track.write(outShortBuf, 0, framesRead, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break

                val curFrame = playheadFrame.addAndGet(framesRead.toLong())
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastUiMsUpdate >= 45L) {
                    lastUiMsUpdate = nowMs
                    val posMs = ((curFrame * 1000L) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                        .coerceIn(0L, src.durationMs)
                    _state.value = _state.value.copy(
                        positionMs = posMs,
                        detectedNote = dspPipeline.pitchCorrection.lastDetectedNoteLabel
                    )
                }
            }
        } catch (_: Throwable) {
            shouldPlay = false
            _state.value = _state.value.copy(isPlaying = false)
        } finally {
            runCatching { raf?.close() }
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
        }
    }

    private fun readChunk(raf: RandomAccessFile, dest: ByteArray, size: Int): Int {
        var total = 0
        while (total < size) {
            val n = raf.read(dest, total, size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
