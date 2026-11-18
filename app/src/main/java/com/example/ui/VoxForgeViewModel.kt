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
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.LowPassConfig
import com.example.dsp.NoiseGateConfig
import com.example.dsp.ReverbConfig
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
    EFFECTS
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

    private val _effectsConfig = MutableStateFlow(EffectsRackConfig())
    val effectsConfig: StateFlow<EffectsRackConfig> = _effectsConfig.asStateFlow()

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
            _effectsConfig.value = saved.effectsRack
            audioEngine.updateVoiceChanger(saved.voiceChanger)
            audioEngine.updateEffectsRack(saved.effectsRack)
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
        if (changed) {
            audioEngine.onPermissionChanged()
        }
    }

    fun setAppForeground(foreground: Boolean) {
        audioEngine.setAppForeground(foreground)
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

    // --- Voice Changer Controls (5 presets, ON/OFF default OFF, 3 controls) ---

    fun setVoiceChangerEnabled(enabled: Boolean) {
        applyVoiceChangerConfig(_voiceChangerConfig.value.copy(enabled = enabled))
    }

    fun selectVoicePreset(preset: VoicePreset) {
        val currentlyEnabled = _voiceChangerConfig.value.enabled
        val updated = preset.defaultConfig(enabled = currentlyEnabled)
        applyVoiceChangerConfig(updated)
    }

    fun resetVoicePresetToDefault() {
        val current = _voiceChangerConfig.value
        val updated = current.preset.defaultConfig(enabled = current.enabled)
        applyVoiceChangerConfig(updated)
    }

    fun updateVoicePitchLevel(percent: Int) {
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                pitchLevelPercent = percent.coerceIn(
                    VoiceChangerConfig.MIN_LEVEL_PCT,
                    VoiceChangerConfig.MAX_LEVEL_PCT
                )
            )
        )
    }

    fun updateVoiceToneLevel(percent: Int) {
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                toneLevelPercent = percent.coerceIn(
                    VoiceChangerConfig.MIN_LEVEL_PCT,
                    VoiceChangerConfig.MAX_LEVEL_PCT
                )
            )
        )
    }

    fun updateVoiceClarity(percent: Int) {
        applyVoiceChangerConfig(
            _voiceChangerConfig.value.copy(
                clarityPercent = percent.coerceIn(
                    VoiceChangerConfig.MIN_LEVEL_PCT,
                    VoiceChangerConfig.MAX_LEVEL_PCT
                )
            )
        )
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

    // --- Effects Rack Controls ---

    fun updateNoiseGateConfig(config: NoiseGateConfig) {
        applyEffectsRack(_effectsConfig.value.copy(noiseGate = config))
    }

    fun updateLowPassConfig(config: LowPassConfig) {
        applyEffectsRack(_effectsConfig.value.copy(lowPass = config))
    }

    fun updateHighPassConfig(config: HighPassConfig) {
        applyEffectsRack(_effectsConfig.value.copy(highPass = config))
    }

    fun updateEqualizerConfig(config: EqualizerConfig) {
        applyEffectsRack(_effectsConfig.value.copy(equalizer = config))
    }

    fun updateReverbConfig(config: ReverbConfig) {
        applyEffectsRack(_effectsConfig.value.copy(reverb = config))
    }

    fun updateEchoConfig(config: EchoConfig) {
        applyEffectsRack(_effectsConfig.value.copy(echo = config))
    }

    fun updateHallConfig(config: HallConfig) {
        applyEffectsRack(_effectsConfig.value.copy(hall = config))
    }

    private fun applyEffectsRack(config: EffectsRackConfig) {
        _effectsConfig.value = config
        audioEngine.updateEffectsRack(config)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveEffectsRack(config)
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
