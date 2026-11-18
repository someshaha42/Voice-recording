package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.dsp.HighPassFilter
import com.example.dsp.NoiseGateConfig
import com.example.dsp.NoiseGateProcessor
import com.example.dsp.PacketLossConcealment
import com.example.dsp.VoiceChangerConfig
import com.example.dsp.VoiceChangerDsp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Single shared real-time audio engine serving BOTH:
 *  - Live Voice Preview / Direct Monitoring (Branch A, with adjustable MonitoringDelayLine & 0-500% Volume)
 *  - 48 kHz / 16-bit / Mono WAV Recording (Branch B, zero monitoring delay)
 *
 * Pipeline:
 *   Microphone Capture (USB-C FIFINE AM8 or Built-in) + Hardware AEC (when enabled)
 *   -> Resample to 48 kHz (pass-through when capture is already 48 kHz)
 *   -> 120 Hz 4th-order Butterworth High-Pass Filter (removes proximity boom & rumble)
 *   -> Packet Loss Concealment (PLC for wireless / jitter drops)
 *   -> Noise Gate Processor (if enabled)
 *   -> Voice Changer DSP (if not bypassed)
 *   -> Processed Audio Signal
 *       ├── Branch A (if Live Voice Preview ON): MonitoringDelayLine -> Volume Gain (0-500%) & Limiter -> Low-Latency AudioTrack
 *       └── Branch B (if Recording ACTIVE): WavPcmWriter (48 kHz, 16-bit, Mono)
 */
class RealTimeAudioEngine(
    private val context: Context,
    private val usbMicManager: UsbMicrophoneManager,
    private val onRecordingFinalized: (File) -> Unit,
    private val onEngineError: (String) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val highPassFilter = HighPassFilter(120.0, AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
    private val packetLossConcealment = PacketLossConcealment(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
    private val noiseGate = NoiseGateProcessor(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
    private val voiceChanger = VoiceChangerDsp(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
    private val monitoringDelayLine = MonitoringDelayLine(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)

    private val _recordingStatus = MutableStateFlow(RecordingStatus.IDLE)
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    private val _monitoringConfig = MutableStateFlow(MonitoringConfig())
    val monitoringConfig: StateFlow<MonitoringConfig> = _monitoringConfig.asStateFlow()

    private val _metrics = MutableStateFlow(LiveAudioMetrics())
    val metrics: StateFlow<LiveAudioMetrics> = _metrics.asStateFlow()

    private val _activeRecordingFile = MutableStateFlow<File?>(null)
    val activeRecordingFile: StateFlow<File?> = _activeRecordingFile.asStateFlow()

    private val engineLock = Any()

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var shouldRunEngine = false

    @Volatile
    private var restartCaptureRequested = false

    @Volatile
    private var rebuildTrackRequested = false

    @Volatile
    private var wavWriter: WavPcmWriter? = null

    @Volatile
    private var isRecordingPaused = false

    @Volatile
    private var accumulatedClipCount = 0

    @Volatile
    private var clipHoldFrames = 0

    @Volatile
    private var lastAppliedBufferMultiplier = 1.25f

    @Volatile
    private var smoothedMonitorGain = 1.0f

    private var focusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS -> {
                handleAudioInterruption()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Keep INTERRUPTED_PAUSED so user consciously resumes
            }
        }
    }

    fun updateNoiseGate(config: NoiseGateConfig) {
        noiseGate.updateConfig(config)
    }

    fun updateVoiceChanger(config: VoiceChangerConfig) {
        voiceChanger.updateConfig(config)
    }

    fun updateMonitoringConfig(newConfig: MonitoringConfig) {
        val prev = _monitoringConfig.value
        val clamped = newConfig.copy(
            delaySliderPosition = newConfig.delaySliderPosition.coerceIn(
                MonitoringConfig.MIN_SLIDER,
                MonitoringConfig.MAX_SLIDER
            ),
            previewVolumePercent = newConfig.previewVolumePercent.coerceIn(
                MonitoringConfig.MIN_VOLUME_PERCENT,
                MonitoringConfig.MAX_VOLUME_PERCENT
            )
        )
        _monitoringConfig.value = clamped
        monitoringDelayLine.setDelayMs(clamped.addedOutputDelayMs)

        if (abs(clamped.bufferSizeMultiplier - lastAppliedBufferMultiplier) >= 0.08f) {
            lastAppliedBufferMultiplier = clamped.bufferSizeMultiplier
            rebuildTrackRequested = true
        }

        if (clamped.echoCancellationEnabled != prev.echoCancellationEnabled && clamped.livePreviewEnabled) {
            restartCaptureRequested = true
        }

        if (clamped.livePreviewEnabled != prev.livePreviewEnabled) {
            // If Live Voice Preview switched ON/OFF while speaker is active, re-align AEC capture mode if needed
            if (shouldRunEngine && clamped.echoCancellationEnabled && !usbMicManager.hardwareState.value.isWiredHeadphoneConnected) {
                restartCaptureRequested = true
            }
            syncEngineLifecycle()
        } else {
            updateEstimatedLatencyOnly()
        }
    }

    fun onHardwareTopologyChanged(hardwareState: AudioHardwareState) {
        if (!shouldRunEngine) return
        restartCaptureRequested = true
        rebuildTrackRequested = true
    }

    fun startRecording(targetFile: File): Boolean {
        if (!hasRecordPermission()) {
            onEngineError("Microphone permission is required to start recording.")
            return false
        }

        synchronized(engineLock) {
            val status = _recordingStatus.value
            if (status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED ||
                status == RecordingStatus.INTERRUPTED_PAUSED
            ) {
                return true
            }

            if (!requestAudioFocus()) {
                onEngineError("Could not obtain audio focus for recording.")
                return false
            }

            return try {
                val writer = WavPcmWriter(targetFile)
                writer.open()
                wavWriter = writer
                _activeRecordingFile.value = targetFile
                isRecordingPaused = false
                accumulatedClipCount = 0
                clipHoldFrames = 0
                _recordingStatus.value = RecordingStatus.RECORDING
                syncEngineLifecycle()
                true
            } catch (t: Throwable) {
                wavWriter = null
                _activeRecordingFile.value = null
                _recordingStatus.value = RecordingStatus.IDLE
                onEngineError("Failed to create WAV file: ${t.message}")
                false
            }
        }
    }

    fun pauseRecording() {
        synchronized(engineLock) {
            if (_recordingStatus.value == RecordingStatus.RECORDING) {
                isRecordingPaused = true
                wavWriter?.flush()
                _recordingStatus.value = RecordingStatus.PAUSED
            }
        }
    }

    fun resumeRecording() {
        synchronized(engineLock) {
            val status = _recordingStatus.value
            if (status == RecordingStatus.PAUSED || status == RecordingStatus.INTERRUPTED_PAUSED) {
                if (!requestAudioFocus()) {
                    onEngineError("Audio focus unavailable to resume recording.")
                    return
                }
                isRecordingPaused = false
                _recordingStatus.value = RecordingStatus.RECORDING
                syncEngineLifecycle()
            }
        }
    }

    fun stopRecording(): File? {
        val finishedFile: File?
        synchronized(engineLock) {
            val writer = wavWriter
            finishedFile = _activeRecordingFile.value
            wavWriter = null
            isRecordingPaused = false
            _recordingStatus.value = RecordingStatus.IDLE
            _activeRecordingFile.value = null

            if (writer != null) {
                runCatching { writer.close() }
            }
            abandonAudioFocus()
            syncEngineLifecycle()
        }

        if (finishedFile != null && finishedFile.exists() && finishedFile.length() > WavPcmWriter.WAV_HEADER_SIZE) {
            onRecordingFinalized(finishedFile)
            return finishedFile
        }
        return null
    }

    fun stopAll() {
        synchronized(engineLock) {
            _monitoringConfig.value = _monitoringConfig.value.copy(livePreviewEnabled = false)
            if (_recordingStatus.value != RecordingStatus.IDLE) {
                stopRecording()
            } else {
                syncEngineLifecycle()
            }
        }
    }

    fun resetClipCounter() {
        accumulatedClipCount = 0
        clipHoldFrames = 0
        _metrics.value = _metrics.value.copy(isClipping = false, clipCount = 0)
    }

    fun isEngineActive(): Boolean {
        return _monitoringConfig.value.livePreviewEnabled || _recordingStatus.value != RecordingStatus.IDLE
    }

    private fun handleAudioInterruption() {
        synchronized(engineLock) {
            if (_recordingStatus.value == RecordingStatus.RECORDING) {
                isRecordingPaused = true
                runCatching { wavWriter?.flush() }
                _recordingStatus.value = RecordingStatus.INTERRUPTED_PAUSED
            }
        }
    }

    private fun syncEngineLifecycle() {
        val needsEngine = _monitoringConfig.value.livePreviewEnabled ||
            _recordingStatus.value != RecordingStatus.IDLE

        if (needsEngine && !hasRecordPermission()) {
            _monitoringConfig.value = _monitoringConfig.value.copy(livePreviewEnabled = false)
            onEngineError("Microphone permission is required.")
            return
        }

        if (needsEngine) {
            if (workerThread == null || !shouldRunEngine) {
                shouldRunEngine = true
                val thread = Thread(::runAudioLoop, "VoxForgeAudioEngine").apply {
                    priority = Thread.MAX_PRIORITY
                }
                workerThread = thread
                thread.start()
            }
        } else {
            shouldRunEngine = false
            val t = workerThread
            workerThread = null
            if (t != null && t !== Thread.currentThread()) {
                runCatching { t.join(500) }
            }
            val burst = getHardwareBurstFrames()
            _metrics.value = LiveAudioMetrics(
                estimatedMonitoringLatencyMs = estimateMonitoringLatencyMs(
                    AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
                    AudioConfig.DSP_HOP_SIZE,
                    burst * 2
                )
            )
        }
    }

    private fun updateEstimatedLatencyOnly() {
        val cur = _metrics.value
        val cfg = _monitoringConfig.value
        val burst = getHardwareBurstFrames()
        val trackFrames = if (cfg.zone == MonitoringLatencyZone.FASTER) burst else burst * 2
        val est = estimateMonitoringLatencyMs(
            usbMicManager.hardwareState.value.activeInputSampleRateHz,
            AudioConfig.DSP_HOP_SIZE,
            trackFrames
        )
        _metrics.value = cur.copy(estimatedMonitoringLatencyMs = est)
    }

    private fun getHardwareBurstFrames(): Int {
        val prop = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        val parsed = prop?.toIntOrNull()
        return (parsed ?: 192).coerceIn(64, 512)
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        var audioRecord: AudioRecord? = null
        var echoCanceler: AcousticEchoCanceler? = null
        var audioTrack: AudioTrack? = null
        var resampler: LinearResampler? = null
        var inputRateHz = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
        var captureFrames = AudioConfig.DSP_HOP_SIZE
        var trackBufferFrames = getHardwareBurstFrames() * 2
        var lastUnderrunCount = 0

        var readShortBuffer = ShortArray(1024)
        var rawFloatBuffer = FloatArray(1024)
        var resampled48kBuffer = FloatArray(2048)
        var monitorOutBuffer = FloatArray(2048)
        var trackShortBuffer = ShortArray(2048)

        highPassFilter.reset()
        packetLossConcealment.reset()
        noiseGate.reset()
        voiceChanger.reset()
        monitoringDelayLine.reset()
        smoothedMonitorGain = _monitoringConfig.value.previewVolumePercent / 100f

        var lastUiUpdateNs = 0L
        var uiRmsAccumSq = 0.0
        var uiSampleCount = 0
        var uiPeakAbs = 0f

        try {
            while (shouldRunEngine) {
                val hw = usbMicManager.hardwareState.value
                val monCfg = _monitoringConfig.value

                // 1. Initialize or rebuild AudioRecord when needed
                if (audioRecord == null || restartCaptureRequested) {
                    restartCaptureRequested = false
                    runCatching { echoCanceler?.release() }
                    echoCanceler = null
                    runCatching { audioRecord?.stop() }
                    runCatching { audioRecord?.release() }
                    audioRecord = null

                    inputRateHz = hw.activeInputSampleRateHz
                    val mult = monCfg.bufferSizeMultiplier
                    val minRecBytes = AudioRecord.getMinBufferSize(
                        inputRateHz,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(1024)

                    // Optimize capture buffer size cleanly for low-latency live preview
                    val burstCaptureBytes = ((inputRateHz * AudioConfig.DSP_HOP_SIZE) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ) * 2 * 4
                    val targetRecBytes = max(minRecBytes, (burstCaptureBytes * mult).roundToInt())
                    captureFrames = ((inputRateHz * AudioConfig.DSP_HOP_SIZE) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                        .coerceIn(64, 512)

                    if (readShortBuffer.size < captureFrames) {
                        readShortBuffer = ShortArray(captureFrames * 2)
                        rawFloatBuffer = FloatArray(captureFrames * 2)
                    }

                    resampler = LinearResampler(inputRateHz, AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                    val preferAecMode = monCfg.livePreviewEnabled &&
                        monCfg.echoCancellationEnabled &&
                        !hw.isWiredHeadphoneConnected

                    audioRecord = createBestAudioRecord(
                        sampleRateHz = inputRateHz,
                        bufferBytes = targetRecBytes,
                        preferredDevice = usbMicManager.preferredInputDeviceInfo,
                        preferAecSource = preferAecMode
                    )

                    if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        runCatching { audioRecord?.release() }
                        audioRecord = null
                        onEngineError("Unable to initialize microphone capture at $inputRateHz Hz.")
                        break
                    }

                    // Attach hardware Acoustic Echo Cancellation (AEC) when enabled
                    if (monCfg.echoCancellationEnabled && monCfg.livePreviewEnabled && AcousticEchoCanceler.isAvailable()) {
                        runCatching {
                            val aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)
                            if (aec != null) {
                                aec.enabled = true
                                echoCanceler = aec
                            }
                        }
                    }

                    audioRecord.startRecording()
                }

                // 2. Manage AudioTrack strictly according to Live Voice Preview ON/OFF
                val previewOn = monCfg.livePreviewEnabled
                if (previewOn && (audioTrack == null || rebuildTrackRequested)) {
                    rebuildTrackRequested = false
                    runCatching { audioTrack?.pause() }
                    runCatching { audioTrack?.flush() }
                    runCatching { audioTrack?.release() }
                    audioTrack = null

                    val burstFrames = getHardwareBurstFrames()
                    val desiredActiveFrames = when {
                        hw.isBluetoothOutputConnected -> burstFrames * 4 // wireless PLC jitter cushion
                        monCfg.zone == MonitoringLatencyZone.FASTER -> burstFrames
                        else -> burstFrames * 2
                    }

                    val minTrackBytes = AudioTrack.getMinBufferSize(
                        AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(1024)

                    val allocBytes = max(minTrackBytes, desiredActiveFrames * 2 * 2)
                    audioTrack = createLowLatencyAudioTrack(
                        bufferBytes = allocBytes,
                        desiredBufferFrames = desiredActiveFrames,
                        preferredOutput = usbMicManager.preferredOutputDeviceInfo
                    )
                    trackBufferFrames = audioTrack?.bufferSizeInFrames ?: desiredActiveFrames
                    lastUnderrunCount = audioTrack?.underrunCount ?: 0
                    monitoringDelayLine.reset()
                    audioTrack?.play()
                } else if (!previewOn && audioTrack != null) {
                    runCatching { audioTrack?.pause() }
                    runCatching { audioTrack?.flush() }
                    runCatching { audioTrack?.release() }
                    audioTrack = null
                    rebuildTrackRequested = false
                }

                // 3. Read live microphone PCM with Packet Loss Concealment fallback on dropouts
                val rec = audioRecord ?: break
                val framesRead = rec.read(readShortBuffer, 0, captureFrames, AudioRecord.READ_BLOCKING)
                var count48k: Int

                if (framesRead <= 0) {
                    if (framesRead == AudioRecord.ERROR_INVALID_OPERATION ||
                        framesRead == AudioRecord.ERROR_DEAD_OBJECT
                    ) {
                        usbMicManager.refreshDevices()
                        restartCaptureRequested = true
                    }
                    // Packet Loss Concealment on transient read drop so audio doesn't pop or mute abruptly
                    count48k = AudioConfig.DSP_HOP_SIZE
                    if (resampled48kBuffer.size < count48k) {
                        resampled48kBuffer = FloatArray(count48k * 2)
                    }
                    packetLossConcealment.concealPacket(resampled48kBuffer, count48k)
                } else {
                    for (i in 0 until framesRead) {
                        rawFloatBuffer[i] = readShortBuffer[i] / 32768.0f
                    }
                    count48k = resampler?.process(rawFloatBuffer, framesRead, resampled48kBuffer)
                        ?: framesRead
                    if (count48k <= 0) continue

                    // 4. Proximity & Plosive 120 Hz 4th-order Butterworth High-Pass Filter
                    highPassFilter.processInPlace(resampled48kBuffer, count48k)

                    // Record clean valid frame into PLC history (and smooth recovery crossfade if recovering)
                    packetLossConcealment.recordValidFrame(resampled48kBuffer, count48k)
                }

                if (monitorOutBuffer.size < count48k) {
                    monitorOutBuffer = FloatArray(count48k * 2)
                    trackShortBuffer = ShortArray(count48k * 2)
                }

                // 5. Noise Gate (shared pipeline)
                noiseGate.processInPlace(resampled48kBuffer, count48k)

                // 6. Voice Changer DSP (shared pipeline)
                voiceChanger.processInPlace(resampled48kBuffer, count48k)

                // 7. Compute metering on processed signal
                var blockClip = false
                for (i in 0 until count48k) {
                    val s = resampled48kBuffer[i]
                    val a = abs(s)
                    if (a > uiPeakAbs) uiPeakAbs = a
                    if (a >= 0.99f) blockClip = true
                    uiRmsAccumSq += (s * s).toDouble()
                }
                uiSampleCount += count48k
                if (blockClip) {
                    clipHoldFrames = 24
                } else if (clipHoldFrames > 0) {
                    clipHoldFrames--
                }

                // 8. Branch B: Recording Output (ZERO monitoring delay, 100% unity recording gain!)
                val writer = wavWriter
                if (writer != null && writer.isOpen && !isRecordingPaused &&
                    _recordingStatus.value == RecordingStatus.RECORDING
                ) {
                    val clips = writer.writeSamples(resampled48kBuffer, count48k)
                    if (clips > 0) {
                        accumulatedClipCount += clips
                    }
                }

                // 9. Branch A: Live Monitoring Output (Delay + 0-500% Preview Volume + PLC Underrun Guard)
                val track = audioTrack
                if (previewOn && track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // Check wireless / output buffer underruns and cushion with PLC if needed
                    val curUnderruns = track.underrunCount
                    if (curUnderruns > lastUnderrunCount) {
                        lastUnderrunCount = curUnderruns
                        // Conceal brief extra cushion packet to bridge wireless Bluetooth jitter
                        packetLossConcealment.concealPacket(monitorOutBuffer, count48k)
                        writeMonitorSamples(
                            track = track,
                            floatSamples = monitorOutBuffer,
                            shortBuffer = trackShortBuffer,
                            count = count48k,
                            targetGain = monCfg.previewVolumePercent / 100f
                        )
                    }

                    monitoringDelayLine.process(resampled48kBuffer, count48k, monitorOutBuffer)
                    val written = writeMonitorSamples(
                        track = track,
                        floatSamples = monitorOutBuffer,
                        shortBuffer = trackShortBuffer,
                        count = count48k,
                        targetGain = monCfg.previewVolumePercent / 100f
                    )
                    if (written == AudioTrack.ERROR_DEAD_OBJECT) {
                        rebuildTrackRequested = true
                    }
                }

                // 10. Throttled UI telemetry (~25 Hz)
                val nowNs = System.nanoTime()
                if (nowNs - lastUiUpdateNs >= 40_000_000L && uiSampleCount > 0) {
                    lastUiUpdateNs = nowNs
                    val rms = sqrt(uiRmsAccumSq / uiSampleCount).toFloat()
                    val rmsDb = (20f * log10(rms.coerceAtLeast(1e-4f))).coerceIn(-60f, 0f)
                    val peakDb = (20f * log10(uiPeakAbs.coerceAtLeast(1e-4f))).coerceIn(-60f, 0f)

                    val estLatency = estimateMonitoringLatencyMs(
                        inputRateHz = inputRateHz,
                        captureFrames = captureFrames,
                        trackFrames = trackBufferFrames
                    )

                    _metrics.value = LiveAudioMetrics(
                        rmsDb = rmsDb,
                        peakDb = peakDb,
                        isClipping = clipHoldFrames > 0,
                        clipCount = accumulatedClipCount,
                        gateOpen = noiseGate.lastIsGateOpen,
                        gateGainReductionDb = noiseGate.lastGainReductionDb,
                        recordingDurationMs = wavWriter?.getRecordedDurationMs() ?: _metrics.value.recordingDurationMs,
                        estimatedMonitoringLatencyMs = estLatency
                    )

                    uiRmsAccumSq = 0.0
                    uiSampleCount = 0
                    uiPeakAbs = 0f
                }
            }
        } catch (t: Throwable) {
            onEngineError("Audio engine interrupted: ${t.message ?: "Unknown error"}")
        } finally {
            runCatching { echoCanceler?.release() }
            runCatching { audioRecord?.stop() }
            runCatching { audioRecord?.release() }
            runCatching { audioTrack?.pause() }
            runCatching { audioTrack?.flush() }
            runCatching { audioTrack?.release() }
        }
    }

    private fun writeMonitorSamples(
        track: AudioTrack,
        floatSamples: FloatArray,
        shortBuffer: ShortArray,
        count: Int,
        targetGain: Float
    ): Int {
        val alpha = 0.02f
        for (i in 0 until count) {
            smoothedMonitorGain += alpha * (targetGain - smoothedMonitorGain)
            val amplified = floatSamples[i] * smoothedMonitorGain
            // Soft-knee protection limiter keeps 500% monitoring boost smooth without harsh square-wave crackle
            val limited = softProtectMonitor(amplified)
            shortBuffer[i] = (limited * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return track.write(shortBuffer, 0, count, AudioTrack.WRITE_NON_BLOCKING)
    }

    private fun softProtectMonitor(x: Float): Float {
        val absX = abs(x)
        return if (absX <= 0.80f) {
            x
        } else {
            val excess = (absX - 0.80f) / 0.16f
            val shaped = 0.80f + 0.16f * tanh(excess)
            if (x >= 0f) shaped else -shaped
        }
    }

    private fun estimateMonitoringLatencyMs(
        inputRateHz: Int,
        captureFrames: Int,
        trackFrames: Int
    ): Int {
        val captureMs = (captureFrames * 1000f) / inputRateHz.coerceAtLeast(8000)
        val vc = voiceChanger.getConfig()
        val dspActive = !vc.bypass && (
            abs(vc.pitchSemitones) > 0.01f ||
                vc.fineTuneCents != 0 ||
                abs(vc.formantSemitones) > 0.01f
            )
        val dspMs = if (dspActive) 10.7f else 0f // 512-sample STFT @ 48 kHz
        val trackMs = (trackFrames * 1000f) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ
        val addedMs = _monitoringConfig.value.addedOutputDelayMs.toFloat()
        return (captureMs + dspMs + trackMs * 0.5f + addedMs).roundToInt().coerceAtLeast(5)
    }

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(
        sampleRateHz: Int,
        bufferBytes: Int,
        preferredDevice: AudioDeviceInfo?,
        preferAecSource: Boolean
    ): AudioRecord? {
        val supportsUnprocessed =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        val sourceOrder = buildList {
            if (preferAecSource) {
                add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            }
            if (supportsUnprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }

        for (source in sourceOrder) {
            try {
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRateHz)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .build()

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    if (preferredDevice != null) {
                        record.setPreferredDevice(preferredDevice)
                    }
                    return record
                }
                record.release()
            } catch (_: Throwable) {
                // Try next audio source
            }
        }
        return null
    }

    private fun createLowLatencyAudioTrack(
        bufferBytes: Int,
        desiredBufferFrames: Int,
        preferredOutput: AudioDeviceInfo?
    ): AudioTrack? {
        return try {
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
                .setBufferSizeInBytes(bufferBytes)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val track = builder.build()
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                // Tighten active buffer to hardware burst multiple for low-latency Live Voice Preview
                runCatching {
                    track.setBufferSizeInFrames(desiredBufferFrames.coerceIn(64, track.bufferCapacityInFrames))
                }
                if (preferredOutput != null) {
                    track.setPreferredDevice(preferredOutput)
                }
            }
            track
        } catch (_: Throwable) {
            null
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
