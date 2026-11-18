package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioHardwareState
import com.example.audio.AudioPlaybackController
import com.example.audio.LiveAudioMetrics
import com.example.audio.MonitoringConfig
import com.example.audio.PlaybackUiState
import com.example.audio.RealTimeAudioEngine
import com.example.audio.RecordingStatus
import com.example.audio.UsbMicrophoneManager
import com.example.data.RecordingFileManager
import com.example.data.RecordingItem
import com.example.data.SettingsRepository
import com.example.dsp.NoiseGateConfig
import com.example.dsp.VoiceChangerConfig
import com.example.dsp.VoicePreset
import com.example.service.RecordingForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class StudioTab {
    RECORDER,
    VOICE_CHANGER,
    NOISE_GATE
}

class VoxForgeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val settingsRepo = SettingsRepository(appContext)
    private val fileManager = RecordingFileManager(appContext)
    private val usbMicManager = UsbMicrophoneManager(appContext)
    private val playbackController = AudioPlaybackController(appContext)

    private val _selectedTab = MutableStateFlow(StudioTab.RECORDER)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _voiceChangerConfig = MutableStateFlow(VoiceChangerConfig())
    val voiceChangerConfig: StateFlow<VoiceChangerConfig> = _voiceChangerConfig.asStateFlow()

    private val _noiseGateConfig = MutableStateFlow(NoiseGateConfig())
    val noiseGateConfig: StateFlow<NoiseGateConfig> = _noiseGateConfig.asStateFlow()

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val audioEngine = RealTimeAudioEngine(
        context = appContext,
        usbMicManager = usbMicManager,
        onRecordingFinalized = {
            refreshRecordings()
            syncForegroundService()
        },
        onEngineError = { message ->
            _statusBanner.value = message
            syncForegroundService()
        }
    )

    val hardwareState: StateFlow<AudioHardwareState> = usbMicManager.hardwareState
    val recordingStatus: StateFlow<RecordingStatus> = audioEngine.recordingStatus
    val monitoringConfig: StateFlow<MonitoringConfig> = audioEngine.monitoringConfig
    val liveMetrics: StateFlow<LiveAudioMetrics> = audioEngine.metrics
    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    private val serviceCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(RecordingForegroundService.EXTRA_COMMAND)) {
                RecordingForegroundService.CMD_PAUSE -> pauseRecording()
                RecordingForegroundService.CMD_RESUME -> resumeRecording()
                RecordingForegroundService.CMD_STOP -> stopRecording()
            }
        }
    }

    init {
        usbMicManager.startMonitoring { hw ->
            audioEngine.onHardwareTopologyChanged(hw)
        }
        registerServiceReceiver()
        loadInitialData()
    }

    private fun registerServiceReceiver() {
        val filter = IntentFilter(RecordingForegroundService.BROADCAST_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(serviceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(serviceCommandReceiver, filter)
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = settingsRepo.settingsFlow.first()
            _voiceChangerConfig.value = saved.voiceChanger
            _noiseGateConfig.value = saved.noiseGate
            audioEngine.updateVoiceChanger(saved.voiceChanger)
            audioEngine.updateNoiseGate(saved.noiseGate)
            audioEngine.updateMonitoringConfig(
                MonitoringConfig(
                    livePreviewEnabled = false,
                    delaySliderPosition = saved.monitoringDelaySlider,
                    previewVolumePercent = saved.monitoringVolumePercent,
                    echoCancellationEnabled = saved.echoCancellationEnabled
                )
            )
            refreshRecordingsSync()
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        val changed = _hasMicPermission.value != granted
        _hasMicPermission.value = granted
        if (granted && changed) {
            usbMicManager.refreshDevices()
        }
    }

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    // --- Recorder Controls ---

    fun startRecording() {
        playbackController.stop()
        val file = fileManager.createNewRecordingFile()
        if (audioEngine.startRecording(file)) {
            _statusBanner.value = null
            syncForegroundService()
        }
    }

    fun pauseRecording() {
        audioEngine.pauseRecording()
        syncForegroundService()
    }

    fun resumeRecording() {
        playbackController.stop()
        audioEngine.resumeRecording()
        syncForegroundService()
    }

    fun stopRecording() {
        audioEngine.stopRecording()
        syncForegroundService()
        refreshRecordings()
    }

    fun resetClipCounter() {
        audioEngine.resetClipCounter()
    }

    fun refreshDevices() {
        usbMicManager.refreshDevices()
    }

    // --- Playback & File Management ---

    fun togglePlayback(item: RecordingItem) {
        if (recordingStatus.value == RecordingStatus.RECORDING) {
            _statusBanner.value = "Stop or pause recording before playing back files."
            return
        }
        playbackController.togglePlayPause(item.filePath, item.durationMs)
    }

    fun seekPlayback(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun renameRecording(item: RecordingItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playbackState.value.activeFilePath == item.filePath) {
                playbackController.stop()
            }
            fileManager.renameRecording(item, newName)
                .onSuccess { refreshRecordingsSync() }
                .onFailure { _statusBanner.value = it.message ?: "Rename failed." }
        }
    }

    fun deleteRecording(item: RecordingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playbackState.value.activeFilePath == item.filePath) {
                playbackController.stop()
            }
            if (fileManager.deleteRecording(item)) {
                refreshRecordingsSync()
            } else {
                _statusBanner.value = "Unable to delete file."
            }
        }
    }

    fun getShareUri(item: RecordingItem): Uri = fileManager.getShareUri(item)

    private fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshRecordingsSync()
        }
    }

    private fun refreshRecordingsSync() {
        val loaded = fileManager.loadRecordingsAndRepair()
        _recordings.value = loaded
        val recoveredCount = loaded.count { it.wasRecovered }
        if (recoveredCount > 0 && _statusBanner.value == null) {
            _statusBanner.value = "Recovered $recoveredCount interrupted WAV recording(s) cleanly."
        }
    }

    // --- Voice Changer Controls ---

    fun selectVoicePreset(preset: VoicePreset) {
        val currentBypass = _voiceChangerConfig.value.bypass
        val updated = preset.defaultConfig().copy(bypass = currentBypass)
        applyVoiceChangerConfig(updated)
    }

    fun resetVoicePresetToDefault() {
        val current = _voiceChangerConfig.value
        val updated = current.preset.defaultConfig().copy(bypass = current.bypass)
        applyVoiceChangerConfig(updated)
    }

    fun updatePitch(pitchSemitones: Float) {
        val snapped = (pitchSemitones / VoiceChangerConfig.STEP_PITCH_ST).roundToInt() *
            VoiceChangerConfig.STEP_PITCH_ST
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                pitchSemitones = snapped.coerceIn(
                    VoiceChangerConfig.MIN_PITCH_ST,
                    VoiceChangerConfig.MAX_PITCH_ST
                )
            )
        )
    }

    fun updateFormant(formantSemitones: Float) {
        val snapped = (formantSemitones / VoiceChangerConfig.STEP_FORMANT_ST).roundToInt() *
            VoiceChangerConfig.STEP_FORMANT_ST
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                formantSemitones = snapped.coerceIn(
                    VoiceChangerConfig.MIN_FORMANT_ST,
                    VoiceChangerConfig.MAX_FORMANT_ST
                )
            )
        )
    }

    fun updateFineTune(fineTuneCents: Int) {
        val snapped = (fineTuneCents.toFloat() / VoiceChangerConfig.STEP_FINE_TUNE_CENTS).roundToInt() *
            VoiceChangerConfig.STEP_FINE_TUNE_CENTS
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                fineTuneCents = snapped.coerceIn(
                    VoiceChangerConfig.MIN_FINE_TUNE_CENTS,
                    VoiceChangerConfig.MAX_FINE_TUNE_CENTS
                )
            )
        )
    }

    fun updateTimbre(timbreDb: Float) {
        val snapped = (timbreDb / VoiceChangerConfig.STEP_TIMBRE_DB).roundToInt() *
            VoiceChangerConfig.STEP_TIMBRE_DB
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                timbreDb = snapped.coerceIn(
                    VoiceChangerConfig.MIN_TIMBRE_DB,
                    VoiceChangerConfig.MAX_TIMBRE_DB
                )
            )
        )
    }

    fun updateDryWet(dryWetPercent: Int) {
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                dryWetPercent = dryWetPercent.coerceIn(
                    VoiceChangerConfig.MIN_DRY_WET_PCT,
                    VoiceChangerConfig.MAX_DRY_WET_PCT
                )
            )
        )
    }

    fun setVoiceChangerBypass(bypass: Boolean) {
        applyVoiceChangerConfig(_voiceChangerConfig.value.copy(bypass = bypass))
    }

    private fun applyVoiceChangerConfig(config: VoiceChangerConfig) {
        _voiceChangerConfig.value = config
        audioEngine.updateVoiceChanger(config)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveVoiceChanger(config)
        }
    }

    // --- Live Voice Preview & Monitoring Delay Controls ---

    fun setLiveVoicePreviewEnabled(enabled: Boolean) {
        if (enabled && !_hasMicPermission.value) {
            _statusBanner.value = "Grant microphone permission to enable Live Voice Preview."
            return
        }
        val updated = audioEngine.monitoringConfig.value.copy(livePreviewEnabled = enabled)
        audioEngine.updateMonitoringConfig(updated)
        syncForegroundService()
    }

    fun updateMonitoringDelaySlider(position: Float) {
        val snapped = (position / MonitoringConfig.STEP_SLIDER).roundToInt() * MonitoringConfig.STEP_SLIDER
        val clamped = snapped.coerceIn(MonitoringConfig.MIN_SLIDER, MonitoringConfig.MAX_SLIDER)
        val updated = audioEngine.monitoringConfig.value.copy(delaySliderPosition = clamped)
        audioEngine.updateMonitoringConfig(updated)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveMonitoringDelaySlider(clamped)
        }
    }

    fun resetMonitoringDelayToDefault() {
        updateMonitoringDelaySlider(MonitoringConfig.DEFAULT_SLIDER)
    }

    fun updateMonitoringVolume(volumePercent: Int) {
        val snapped = (volumePercent.toFloat() / MonitoringConfig.STEP_VOLUME_PERCENT).roundToInt() *
            MonitoringConfig.STEP_VOLUME_PERCENT
        val clamped = snapped.coerceIn(
            MonitoringConfig.MIN_VOLUME_PERCENT,
            MonitoringConfig.MAX_VOLUME_PERCENT
        )
        val updated = audioEngine.monitoringConfig.value.copy(previewVolumePercent = clamped)
        audioEngine.updateMonitoringConfig(updated)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveMonitoringVolumePercent(clamped)
        }
    }

    fun setEchoCancellationEnabled(enabled: Boolean) {
        val updated = audioEngine.monitoringConfig.value.copy(echoCancellationEnabled = enabled)
        audioEngine.updateMonitoringConfig(updated)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveMonitoringAecEnabled(enabled)
        }
    }

    // --- Noise Gate Controls ---

    fun setNoiseGateEnabled(enabled: Boolean) {
        applyNoiseGateConfig(_noiseGateConfig.value.copy(enabled = enabled))
    }

    fun updateGateThreshold(thresholdDb: Float) {
        val snapped = (thresholdDb / NoiseGateConfig.STEP_THRESHOLD_DB).roundToInt() *
            NoiseGateConfig.STEP_THRESHOLD_DB
        applyNoiseGateConfig(
            _noiseGateConfig.value.copy(
                thresholdDb = snapped.coerceIn(
                    NoiseGateConfig.MIN_THRESHOLD_DB,
                    NoiseGateConfig.MAX_THRESHOLD_DB
                )
            )
        )
    }

    fun updateGateAttack(attackMs: Float) {
        val snapped = (attackMs / NoiseGateConfig.STEP_ATTACK_MS).roundToInt() *
            NoiseGateConfig.STEP_ATTACK_MS
        applyNoiseGateConfig(
            _noiseGateConfig.value.copy(
                attackMs = snapped.coerceIn(
                    NoiseGateConfig.MIN_ATTACK_MS,
                    NoiseGateConfig.MAX_ATTACK_MS
                )
            )
        )
    }

    fun updateGateRelease(releaseMs: Float) {
        val snapped = (releaseMs / NoiseGateConfig.STEP_RELEASE_MS).roundToInt() *
            NoiseGateConfig.STEP_RELEASE_MS
        applyNoiseGateConfig(
            _noiseGateConfig.value.copy(
                releaseMs = snapped.coerceIn(
                    NoiseGateConfig.MIN_RELEASE_MS,
                    NoiseGateConfig.MAX_RELEASE_MS
                )
            )
        )
    }

    fun resetNoiseGateToDefault() {
        applyNoiseGateConfig(NoiseGateConfig(enabled = _noiseGateConfig.value.enabled))
    }

    private fun applyNoiseGateConfig(config: NoiseGateConfig) {
        _noiseGateConfig.value = config
        audioEngine.updateNoiseGate(config)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveNoiseGate(config)
        }
    }

    private fun syncForegroundService() {
        val status = recordingStatus.value
        val isRec = status != RecordingStatus.IDLE
        val isPaused = status == RecordingStatus.PAUSED || status == RecordingStatus.INTERRUPTED_PAUSED
        val isMon = monitoringConfig.value.livePreviewEnabled
        RecordingForegroundService.syncServiceState(
            context = appContext,
            isRecording = isRec,
            isPaused = isPaused,
            isMonitoring = isMon
        )
    }

    override fun onCleared() {
        runCatching { appContext.unregisterReceiver(serviceCommandReceiver) }
        usbMicManager.stopMonitoring()
        playbackController.stop()
        audioEngine.stopAll()
        syncForegroundService()
        super.onCleared()
    }
}
