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
import com.example.audio.ImportedAudioEngine
import com.example.audio.ImportedPlaybackState
import com.example.audio.LiveAudioMetrics
import com.example.audio.MonitoringConfig
import com.example.audio.PlaybackUiState
import com.example.audio.RealTimeAudioEngine
import com.example.audio.RecordingStatus
import com.example.audio.UsbMicrophoneManager
import com.example.data.DeviceAudioItem
import com.example.data.MediaImportRepository
import com.example.data.RecordingFileManager
import com.example.data.RecordingItem
import com.example.data.SettingsRepository
import com.example.dsp.CompressionConfig
import com.example.dsp.DeEsserConfig
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.LimiterConfig
import com.example.dsp.LowPassConfig
import com.example.dsp.NoiseGateConfig
import com.example.dsp.PitchCorrectionConfig
import com.example.dsp.PitchFormantConfig
import com.example.dsp.ReverbConfig
import com.example.dsp.SaturationConfig
import com.example.dsp.StudioEffectPreset
import com.example.dsp.StudioEffectType
import com.example.dsp.VoiceChangerConfig
import com.example.dsp.VoicePreset
import com.example.dsp.noiseGateFromJson
import com.example.dsp.toJson
import com.example.service.RecordingForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private val mediaImportRepo = MediaImportRepository(appContext)
    private val usbMicManager = UsbMicrophoneManager(appContext)
    private val playbackController = AudioPlaybackController(appContext)
    private val importedAudioEngine = ImportedAudioEngine()

    private val _selectedTab = MutableStateFlow(StudioTab.RECORDER)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _voiceChangerConfig = MutableStateFlow(VoiceChangerConfig())
    val voiceChangerConfig: StateFlow<VoiceChangerConfig> = _voiceChangerConfig.asStateFlow()

    private val _effectsConfig = MutableStateFlow(EffectsRackConfig())
    val effectsConfig: StateFlow<EffectsRackConfig> = _effectsConfig.asStateFlow()

    private val _userEffectPresets = MutableStateFlow<List<StudioEffectPreset>>(emptyList())
    val userEffectPresets: StateFlow<List<StudioEffectPreset>> = _userEffectPresets.asStateFlow()

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    // Import Audio & Media Discovery State
    private val _showImportSheet = MutableStateFlow(false)
    val showImportSheet: StateFlow<Boolean> = _showImportSheet.asStateFlow()

    private val _hasAudioLibraryPermission = MutableStateFlow(false)
    val hasAudioLibraryPermission: StateFlow<Boolean> = _hasAudioLibraryPermission.asStateFlow()

    private val _deviceAudioFiles = MutableStateFlow<List<DeviceAudioItem>>(emptyList())
    val deviceAudioFiles: StateFlow<List<DeviceAudioItem>> = _deviceAudioFiles.asStateFlow()

    private val _isLoadingDeviceAudio = MutableStateFlow(false)
    val isLoadingDeviceAudio: StateFlow<Boolean> = _isLoadingDeviceAudio.asStateFlow()

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
    val importedPlaybackState: StateFlow<ImportedPlaybackState> = importedAudioEngine.state

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
            _userEffectPresets.value = saved.userEffectPresets

            audioEngine.updateVoiceChanger(saved.voiceChanger)
            audioEngine.updateEffectsRack(saved.effectsRack)
            importedAudioEngine.updateVoiceChangerConfig(saved.voiceChanger)
            importedAudioEngine.updateEffectsConfig(saved.effectsRack)

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

    fun setAudioLibraryPermissionGranted(granted: Boolean) {
        _hasAudioLibraryPermission.value = granted
        if (granted) {
            loadDeviceAudioFiles()
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

    // --- Import Audio Flow & Playback ---

    fun openImportSheet() {
        _showImportSheet.value = true
        if (_hasAudioLibraryPermission.value) {
            loadDeviceAudioFiles()
        }
    }

    fun closeImportSheet() {
        _showImportSheet.value = false
    }

    fun loadDeviceAudioFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingDeviceAudio.value = true
            runCatching {
                mediaImportRepo.queryAllDeviceAudioNewestFirst()
            }.onSuccess { list ->
                _deviceAudioFiles.value = list
            }.onFailure { err ->
                _statusBanner.value = "Unable to scan device audio: ${err.message}"
            }
            _isLoadingDeviceAudio.value = false
        }
    }

    fun importVideoUri(uri: Uri) {
        importUriInternal(uri = uri, fromVideo = true, knownName = null, knownFormat = "Video Audio")
    }

    fun importAudioItem(item: DeviceAudioItem) {
        importUriInternal(
            uri = item.uri,
            fromVideo = false,
            knownName = item.displayName,
            knownFormat = item.formatLabel
        )
    }

    private fun importUriInternal(
        uri: Uri,
        fromVideo: Boolean,
        knownName: String?,
        knownFormat: String?
    ) {
        playbackController.stop()
        importedAudioEngine.pause()
        importedAudioEngine.setImporting(true)
        _showImportSheet.value = false

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                mediaImportRepo.importAndDecodeTo48kPcm(
                    uri = uri,
                    fromVideo = fromVideo,
                    knownDisplayName = knownName,
                    knownFormatLabel = knownFormat
                )
            }.onSuccess { source ->
                importedAudioEngine.updateEffectsConfig(_effectsConfig.value)
                importedAudioEngine.updateVoiceChangerConfig(_voiceChangerConfig.value)
                importedAudioEngine.loadSource(source)
                importedAudioEngine.setImporting(false)
            }.onFailure { err ->
                importedAudioEngine.setImporting(false)
                _statusBanner.value = err.message ?: "Failed to decode audio from selected file."
            }
        }
    }

    fun toggleImportedPlayback() {
        if (recordingStatus.value == RecordingStatus.RECORDING) {
            _statusBanner.value = "Stop or pause recording before playing imported audio."
            return
        }
        playbackController.stop()
        importedAudioEngine.togglePlayPause()
    }

    fun seekImportedPlayback(positionMs: Long) {
        importedAudioEngine.seekTo(positionMs)
    }

    fun clearImportedAudio() {
        importedAudioEngine.clearSource()
        viewModelScope.launch(Dispatchers.IO) {
            mediaImportRepo.clearCachedPcmFiles()
        }
    }

    fun saveAndShareImportedAudio(onShareReady: (Uri, String) -> Unit) {
        val source = importedPlaybackState.value.source ?: return
        if (importedPlaybackState.value.isExporting) return

        importedAudioEngine.setExporting(true, 0f)
        val fxSnapshot = _effectsConfig.value
        val vcSnapshot = _voiceChangerConfig.value

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                mediaImportRepo.renderAndExportWav(
                    source = source,
                    effectsConfig = fxSnapshot,
                    voiceChangerConfig = vcSnapshot,
                    onProgress = { pct ->
                        importedAudioEngine.setExporting(true, pct)
                    }
                )
            }
            withContext(Dispatchers.Main) {
                importedAudioEngine.setExporting(false, 0f)
                result.onSuccess { (file, uri) ->
                    _statusBanner.value = "Saved ${file.name} (${file.length() / 1024} KB)"
                    onShareReady(uri, file.name)
                }.onFailure { err ->
                    _statusBanner.value = "Export failed: ${err.message ?: "Unknown error"}"
                }
            }
        }
    }

    // --- Recorder Controls ---

    fun startRecording() {
        playbackController.stop()
        importedAudioEngine.pause()
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
        importedAudioEngine.pause()
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

    // --- Recorded Voiceovers Playback & File Management ---

    fun togglePlayback(item: RecordingItem) {
        if (recordingStatus.value == RecordingStatus.RECORDING) {
            _statusBanner.value = "Stop or pause recording before playing back files."
            return
        }
        importedAudioEngine.pause()
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
        // Apply immediately to both engines without resetting playback position
        audioEngine.updateVoiceChanger(config)
        importedAudioEngine.updateVoiceChangerConfig(config)
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

    // --- Unified 10 Studio Effects & Effects Rack Controls ---

    fun updatePitchCorrectionConfig(config: PitchCorrectionConfig) {
        applyEffectsRack(_effectsConfig.value.copy(pitchCorrection = config))
    }

    fun updatePitchFormantConfig(config: PitchFormantConfig) {
        applyEffectsRack(_effectsConfig.value.copy(pitchFormant = config))
    }

    fun updateEqualizerConfig(config: EqualizerConfig) {
        applyEffectsRack(_effectsConfig.value.copy(equalizer = config))
    }

    fun updateCompressionConfig(config: CompressionConfig) {
        applyEffectsRack(_effectsConfig.value.copy(compression = config))
    }

    fun updateSaturationConfig(config: SaturationConfig) {
        applyEffectsRack(_effectsConfig.value.copy(saturation = config))
    }

    fun updateReverbConfig(config: ReverbConfig) {
        applyEffectsRack(_effectsConfig.value.copy(reverb = config))
    }

    fun updateEchoConfig(config: EchoConfig) {
        applyEffectsRack(_effectsConfig.value.copy(echo = config))
    }

    fun updateNoiseGateConfig(config: NoiseGateConfig) {
        applyEffectsRack(_effectsConfig.value.copy(noiseGate = config))
    }

    fun updateDeEsserConfig(config: DeEsserConfig) {
        applyEffectsRack(_effectsConfig.value.copy(deEsser = config))
    }

    fun updateLimiterConfig(config: LimiterConfig) {
        applyEffectsRack(_effectsConfig.value.copy(limiter = config))
    }

    fun updateLowPassConfig(config: LowPassConfig) {
        applyEffectsRack(_effectsConfig.value.copy(lowPass = config))
    }

    fun updateHighPassConfig(config: HighPassConfig) {
        applyEffectsRack(_effectsConfig.value.copy(highPass = config))
    }

    fun updateHallConfig(config: HallConfig) {
        applyEffectsRack(_effectsConfig.value.copy(hall = config))
    }

    fun resetStudioEffect(type: StudioEffectType) {
        val cur = _effectsConfig.value
        val updated = when (type) {
            StudioEffectType.PITCH_CORRECTION -> cur.copy(pitchCorrection = PitchCorrectionConfig(enabled = cur.pitchCorrection.enabled))
            StudioEffectType.PITCH_FORMANT -> cur.copy(pitchFormant = PitchFormantConfig(enabled = cur.pitchFormant.enabled))
            StudioEffectType.EQUALIZER -> cur.copy(equalizer = EqualizerConfig(enabled = cur.equalizer.enabled))
            StudioEffectType.COMPRESSION -> cur.copy(compression = CompressionConfig(enabled = cur.compression.enabled))
            StudioEffectType.SATURATION -> cur.copy(saturation = SaturationConfig(enabled = cur.saturation.enabled))
            StudioEffectType.REVERB -> cur.copy(reverb = ReverbConfig(enabled = cur.reverb.enabled))
            StudioEffectType.DELAY -> cur.copy(echo = EchoConfig(enabled = cur.echo.enabled))
            StudioEffectType.NOISE_GATE -> cur.copy(noiseGate = NoiseGateConfig(enabled = cur.noiseGate.enabled))
            StudioEffectType.DE_ESSER -> cur.copy(deEsser = DeEsserConfig(enabled = cur.deEsser.enabled))
            StudioEffectType.LIMITER -> cur.copy(limiter = LimiterConfig(enabled = cur.limiter.enabled))
        }
        applyEffectsRack(updated)
    }

    fun saveUserEffectPreset(type: StudioEffectType, name: String) {
        val cleanName = name.trim().ifBlank { return }
        val cur = _effectsConfig.value
        val payload = when (type) {
            StudioEffectType.PITCH_CORRECTION -> cur.pitchCorrection.toJson().toString()
            StudioEffectType.PITCH_FORMANT -> cur.pitchFormant.toJson().toString()
            StudioEffectType.EQUALIZER -> cur.equalizer.toJson().toString()
            StudioEffectType.COMPRESSION -> cur.compression.toJson().toString()
            StudioEffectType.SATURATION -> cur.saturation.toJson().toString()
            StudioEffectType.REVERB -> cur.reverb.toJson().toString()
            StudioEffectType.DELAY -> cur.echo.toJson().toString()
            StudioEffectType.NOISE_GATE -> cur.noiseGate.toJson().toString()
            StudioEffectType.DE_ESSER -> cur.deEsser.toJson().toString()
            StudioEffectType.LIMITER -> cur.limiter.toJson().toString()
        }
        val newPreset = StudioEffectPreset(
            id = "usr_${type.name.lowercase()}_${System.currentTimeMillis()}",
            name = cleanName,
            effectType = type,
            isBuiltIn = false,
            payloadJson = payload
        )
        val updatedList = (_userEffectPresets.value + newPreset)
        _userEffectPresets.value = updatedList
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveUserEffectPresets(updatedList)
        }
    }

    fun loadEffectPreset(preset: StudioEffectPreset) {
        val obj = runCatching { JSONObject(preset.payloadJson) }.getOrNull() ?: return
        val cur = _effectsConfig.value
        val updated = when (preset.effectType) {
            StudioEffectType.PITCH_CORRECTION -> cur.copy(
                pitchCorrection = PitchCorrectionConfig.fromJson(obj, enabled = cur.pitchCorrection.enabled)
            )
            StudioEffectType.PITCH_FORMANT -> cur.copy(
                pitchFormant = PitchFormantConfig.fromJson(obj, enabled = cur.pitchFormant.enabled)
            )
            StudioEffectType.EQUALIZER -> cur.copy(
                equalizer = EqualizerConfig.fromJson(obj, enabled = cur.equalizer.enabled)
            )
            StudioEffectType.COMPRESSION -> cur.copy(
                compression = CompressionConfig.fromJson(obj, enabled = cur.compression.enabled)
            )
            StudioEffectType.SATURATION -> cur.copy(
                saturation = SaturationConfig.fromJson(obj, enabled = cur.saturation.enabled)
            )
            StudioEffectType.REVERB -> cur.copy(
                reverb = ReverbConfig.fromJson(obj, enabled = cur.reverb.enabled)
            )
            StudioEffectType.DELAY -> cur.copy(
                echo = EchoConfig.fromJson(obj, enabled = cur.echo.enabled)
            )
            StudioEffectType.NOISE_GATE -> cur.copy(
                noiseGate = noiseGateFromJson(obj, enabled = cur.noiseGate.enabled)
            )
            StudioEffectType.DE_ESSER -> cur.copy(
                deEsser = DeEsserConfig.fromJson(obj, enabled = cur.deEsser.enabled)
            )
            StudioEffectType.LIMITER -> cur.copy(
                limiter = LimiterConfig.fromJson(obj, enabled = cur.limiter.enabled)
            )
        }
        applyEffectsRack(updated)
    }

    fun deleteUserEffectPreset(presetId: String) {
        val updated = _userEffectPresets.value.filterNot { it.id == presetId }
        _userEffectPresets.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveUserEffectPresets(updated)
        }
    }

    private fun applyEffectsRack(config: EffectsRackConfig) {
        _effectsConfig.value = config
        // Sync immediately across live mic engine AND imported audio player WITHOUT resetting position!
        audioEngine.updateEffectsRack(config)
        importedAudioEngine.updateEffectsConfig(config)
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
        importedAudioEngine.clearSource()
        audioEngine.stopAll()
        syncForegroundService()
        super.onCleared()
    }
}
