import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { DeviceHardwareManager } from '../audio/DeviceHardwareManager';
import { ImportedAudioEngine } from '../audio/ImportedAudioEngine';
import { RealTimeAudioEngine } from '../audio/RealTimeAudioEngine';
import { RecordingPlaybackController } from '../audio/RecordingPlaybackController';
import {
  decodeMediaFileTo48kPcm,
  probeAudioFileMetadata,
  renderAndExportProcessedWav,
  triggerBlobDownload,
} from '../data/MediaImportRepository';
import {
  deleteRecordingFromDb,
  getRecordingBlob,
  loadAllRecordings,
  renameRecordingInDb,
  saveRecordingBlob,
} from '../data/RecordingsStorage';
import { loadSettings, saveSettings } from '../data/SettingsRepository';
import {
  AudioHardwareState,
  CompressionConfig,
  createDefaultHardwareState,
  createDefaultLiveMetrics,
  createDefaultMonitoringConfig,
  DeEsserConfig,
  defaultCompression,
  defaultDeEsser,
  defaultEcho,
  defaultEqualizer,
  defaultLimiter,
  defaultNoiseGate,
  defaultPitchCorrection,
  defaultPitchFormant,
  defaultReverb,
  defaultSaturation,
  EchoConfig,
  EffectsRackConfig,
  EqualizerConfig,
  getVoicePresetDefaultConfig,
  HallConfig,
  HighPassConfig,
  ImportedPlaybackState,
  LimiterConfig,
  LiveAudioMetrics,
  LowPassConfig,
  MonitoringConfig,
  MonitoringConstants,
  NoiseGateConfig,
  PitchCorrectionConfig,
  PitchFormantConfig,
  PlaybackUiState,
  RecordingItem,
  RecordingStatus,
  ReverbConfig,
  SaturationConfig,
  StagedAudioItem,
  StudioEffectPreset,
  StudioEffectType,
  StudioTab,
  VoiceChangerConfig,
  VoicePreset,
} from '../types/audio';

export type MetricsSubscribeFn = (listener: () => void) => () => void;
export type MetricsSnapshotFn = () => LiveAudioMetrics;

/**
 * Isolated 20 Hz telemetry hook so only level meters, timers, and gate indicators
 * re-render during live audio capture rather than the entire screen or effects list.
 */
export function useLiveAudioMetrics(
  subscribeMetrics: MetricsSubscribeFn,
  getMetricsSnapshot: MetricsSnapshotFn
): LiveAudioMetrics {
  return useSyncExternalStore(subscribeMetrics, getMetricsSnapshot, getMetricsSnapshot);
}

export function useVoxForge() {
  const initialSettingsRef = useRef(loadSettings());

  const [selectedTab, setSelectedTab] = useState<StudioTab>(StudioTab.RECORDER);
  const [voiceChangerConfig, setVoiceChangerConfig] = useState<VoiceChangerConfig>(
    initialSettingsRef.current.voiceChanger
  );
  const [effectsConfig, setEffectsConfig] = useState<EffectsRackConfig>(
    initialSettingsRef.current.effectsRack
  );
  const [userEffectPresets, setUserEffectPresets] = useState<StudioEffectPreset[]>(
    initialSettingsRef.current.userEffectPresets
  );

  const [recordings, setRecordings] = useState<RecordingItem[]>([]);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);
  const [hasMicPermission, setHasMicPermission] = useState(false);

  const [showImportSheet, setShowImportSheet] = useState(false);
  const [stagedAudioFiles, setStagedAudioFiles] = useState<StagedAudioItem[]>([]);
  const [isLoadingStagedAudio, setIsLoadingStagedAudio] = useState(false);

  const [hardwareState, setHardwareState] = useState<AudioHardwareState>(
    createDefaultHardwareState
  );
  const [recordingStatus, setRecordingStatus] = useState<RecordingStatus>(RecordingStatus.IDLE);
  const [monitoringConfig, setMonitoringConfig] = useState<MonitoringConfig>(() => ({
    ...createDefaultMonitoringConfig(),
    delaySliderPosition: initialSettingsRef.current.monitoringDelaySlider,
    previewVolumePercent: initialSettingsRef.current.monitoringVolumePercent,
    echoCancellationEnabled: initialSettingsRef.current.echoCancellationEnabled,
    noiseGateThresholdDb: initialSettingsRef.current.noiseGateThresholdDb,
  }));

  const [playbackState, setPlaybackState] = useState<PlaybackUiState>({
    activeId: null,
    isPlaying: false,
    positionMs: 0,
    durationMs: 0,
  });
  const [importedPlaybackState, setImportedPlaybackState] = useState<ImportedPlaybackState>({
    source: null,
    isPlaying: false,
    positionMs: 0,
    durationMs: 0,
    isImporting: false,
    isExporting: false,
    exportProgress: 0,
    detectedNote: '--',
  });

  // High-frequency telemetry store kept outside top-level React state
  const latestMetricsRef = useRef<LiveAudioMetrics>(createDefaultLiveMetrics());
  const metricsSubscribersRef = useRef<Set<() => void>>(new Set());

  const subscribeMetrics = useCallback((listener: () => void) => {
    metricsSubscribersRef.current.add(listener);
    return () => {
      metricsSubscribersRef.current.delete(listener);
    };
  }, []);

  const getMetricsSnapshot = useCallback(() => latestMetricsRef.current, []);

  const hardwareManagerRef = useRef<DeviceHardwareManager | null>(null);
  const audioEngineRef = useRef<RealTimeAudioEngine | null>(null);
  const importedEngineRef = useRef<ImportedAudioEngine | null>(null);
  const playbackControllerRef = useRef<RecordingPlaybackController | null>(null);

  const persistSnapshotRef = useRef(initialSettingsRef.current);
  const persistCurrentSettings = useCallback(() => {
    saveSettings(persistSnapshotRef.current);
  }, []);

  const refreshRecordings = useCallback(async () => {
    const items = await loadAllRecordings();
    setRecordings(items);
  }, []);

  useEffect(() => {
    const hwMgr = new DeviceHardwareManager();
    const importedEng = new ImportedAudioEngine();
    const playCtrl = new RecordingPlaybackController();
    const engine = new RealTimeAudioEngine(
      hwMgr,
      (result) => {
        void (async () => {
          try {
            const savedItem = await saveRecordingBlob(
              result.blob,
              result.durationMs,
              result.sizeBytes
            );
            await refreshRecordings();
            setStatusBanner(
              `Saved ${savedItem.fileName} (${Math.max(1, Math.round(savedItem.sizeBytes / 1024))} KB)`
            );
          } catch (err) {
            const msg = err instanceof Error ? err.message : 'Failed to store WAV recording.';
            setStatusBanner(msg);
          }
        })();
      },
      (msg) => {
        setStatusBanner(msg);
      }
    );

    hardwareManagerRef.current = hwMgr;
    importedEngineRef.current = importedEng;
    playbackControllerRef.current = playCtrl;
    audioEngineRef.current = engine;

    const saved = initialSettingsRef.current;
    engine.updateVoiceChanger(saved.voiceChanger);
    engine.updateEffectsRack(saved.effectsRack);
    importedEng.updateVoiceChangerConfig(saved.voiceChanger);
    importedEng.updateEffectsConfig(saved.effectsRack);
    importedEng.updateAecNoiseGate(saved.echoCancellationEnabled, saved.noiseGateThresholdDb);
    void engine.updateMonitoringConfig({
      livePreviewEnabled: false,
      delaySliderPosition: saved.monitoringDelaySlider,
      previewVolumePercent: saved.monitoringVolumePercent,
      echoCancellationEnabled: saved.echoCancellationEnabled,
      noiseGateThresholdDb: saved.noiseGateThresholdDb,
    });

    const unsubHw = hwMgr.subscribe(setHardwareState);
    const unsubStatus = engine.subscribeStatus(setRecordingStatus);
    const unsubMon = engine.subscribeMonitoring(setMonitoringConfig);
    const unsubMetrics = engine.subscribeMetrics((nextMetrics) => {
      latestMetricsRef.current = nextMetrics;
      for (const listener of metricsSubscribersRef.current) {
        listener();
      }
    });
    const unsubPlay = playCtrl.subscribe(setPlaybackState);
    const unsubImported = importedEng.subscribe(setImportedPlaybackState);

    hwMgr.startMonitoring();
    void refreshRecordings();

    // Check existing browser microphone permission silently (without opening AudioContext until needed)
    if (typeof navigator !== 'undefined' && navigator.permissions?.query) {
      navigator.permissions
        .query({ name: 'microphone' as PermissionName })
        .then((permStatus) => {
          const isGranted = permStatus.state === 'granted';
          setHasMicPermission(isGranted);
          engine.setPermissionGranted(isGranted);
          if (isGranted) {
            void hwMgr.refreshDevices();
          }
          permStatus.onchange = () => {
            const nowGranted = permStatus.state === 'granted';
            setHasMicPermission(nowGranted);
            engine.setPermissionGranted(nowGranted);
            if (nowGranted) void hwMgr.refreshDevices();
          };
        })
        .catch(() => {
          // Permissions API unavailable on some browsers
        });
    }

    const onVisChange = () => {
      engine.setAppForeground(!document.hidden);
    };
    document.addEventListener('visibilitychange', onVisChange);

    return () => {
      document.removeEventListener('visibilitychange', onVisChange);
      unsubHw();
      unsubStatus();
      unsubMon();
      unsubMetrics();
      unsubPlay();
      unsubImported();
      hwMgr.stopMonitoring();
      playCtrl.stop();
      importedEng.clearSource();
      void engine.stopAll();
    };
  }, [refreshRecordings]);

  const requestMicPermission = useCallback(async () => {
    const engine = audioEngineRef.current;
    if (!engine) return false;
    const ok = await engine.requestMicrophonePermission();
    setHasMicPermission(ok);
    if (ok) {
      setStatusBanner(null);
    }
    return ok;
  }, []);

  const refreshDevices = useCallback(() => {
    void hardwareManagerRef.current?.refreshDevices();
    void audioEngineRef.current?.onHardwareTopologyChanged();
  }, []);

  const selectInputDevice = useCallback((deviceId: string | null) => {
    hardwareManagerRef.current?.selectPreferredInput(deviceId);
    void audioEngineRef.current?.onHardwareTopologyChanged();
  }, []);

  const selectOutputDevice = useCallback((deviceId: string | null) => {
    hardwareManagerRef.current?.selectPreferredOutput(deviceId);
    void audioEngineRef.current?.applyPreferredOutputRoute();
  }, []);

  // --- Transport Controls ---
  const startRecording = useCallback(async () => {
    playbackControllerRef.current?.stop();
    importedEngineRef.current?.pause();
    const ok = await audioEngineRef.current?.startRecording();
    if (ok) {
      setHasMicPermission(true);
      setStatusBanner(null);
    }
  }, []);

  const pauseRecording = useCallback(() => {
    audioEngineRef.current?.pauseRecording();
  }, []);

  const resumeRecording = useCallback(() => {
    playbackControllerRef.current?.stop();
    importedEngineRef.current?.pause();
    void audioEngineRef.current?.resumeRecording();
  }, []);

  const stopRecording = useCallback(() => {
    audioEngineRef.current?.stopRecording();
  }, []);

  const resetClipCounter = useCallback(() => {
    audioEngineRef.current?.resetClipCounter();
  }, []);

  const toggleManualInputMonitor = useCallback(async () => {
    const currentlyActive = latestMetricsRef.current.isInputMonitoringActive;
    await audioEngineRef.current?.setManualInputMonitorEnabled(!currentlyActive);
  }, []);

  const calibrateBackgroundNoise = useCallback(async () => {
    const ok = await audioEngineRef.current?.startNoiseFloorCalibration();
    void ok;
  }, []);

  // --- Recorded Files Playback & Management ---
  const togglePlayback = useCallback(
    async (item: RecordingItem) => {
      if (recordingStatus === RecordingStatus.RECORDING) {
        setStatusBanner('Stop or pause recording before playing back files.');
        return;
      }
      importedEngineRef.current?.pause();
      const blob = await getRecordingBlob(item.id);
      if (!blob) {
        setStatusBanner('Recording audio data could not be loaded.');
        return;
      }
      await playbackControllerRef.current?.togglePlayPause(item.id, blob, item.durationMs);
    },
    [recordingStatus]
  );

  const seekPlayback = useCallback((positionMs: number) => {
    playbackControllerRef.current?.seekTo(positionMs);
  }, []);

  const renameRecording = useCallback(
    async (item: RecordingItem, newName: string) => {
      try {
        if (playbackState.activeId === item.id) {
          playbackControllerRef.current?.stop();
        }
        await renameRecordingInDb(item.id, newName);
        await refreshRecordings();
      } catch (err) {
        setStatusBanner(err instanceof Error ? err.message : 'Rename failed.');
      }
    },
    [playbackState.activeId, refreshRecordings]
  );

  const deleteRecording = useCallback(
    async (item: RecordingItem) => {
      try {
        if (playbackState.activeId === item.id) {
          playbackControllerRef.current?.stop();
        }
        await deleteRecordingFromDb(item.id);
        await refreshRecordings();
      } catch {
        setStatusBanner('Unable to delete recording.');
      }
    },
    [playbackState.activeId, refreshRecordings]
  );

  const shareOrDownloadRecording = useCallback(async (item: RecordingItem) => {
    const blob = await getRecordingBlob(item.id);
    if (!blob) {
      setStatusBanner('Recording audio data is unavailable.');
      return;
    }
    const file = new File([blob], item.fileName, { type: 'audio/wav' });
    if (
      typeof navigator !== 'undefined' &&
      navigator.canShare &&
      navigator.share &&
      navigator.canShare({ files: [file] })
    ) {
      try {
        await navigator.share({
          files: [file],
          title: item.fileName,
        });
        return;
      } catch (err) {
        if ((err as Error)?.name === 'AbortError') return;
      }
    }
    triggerBlobDownload(blob, item.fileName);
    setStatusBanner(`Downloaded ${item.fileName}`);
  }, []);

  // --- Import Audio & Export Flow ---
  const openImportSheet = useCallback(() => {
    setShowImportSheet(true);
  }, []);

  const closeImportSheet = useCallback(() => {
    setShowImportSheet(false);
  }, []);

  const addFilesToStagedAudio = useCallback(async (files: FileList | File[]) => {
    const list = Array.from(files);
    if (list.length === 0) return;
    setIsLoadingStagedAudio(true);
    try {
      const probed = await Promise.all(list.map((f) => probeAudioFileMetadata(f)));
      setStagedAudioFiles((prev) => {
        const map = new Map<string, StagedAudioItem>();
        for (const item of [...probed, ...prev]) {
          map.set(item.id, item);
        }
        return Array.from(map.values()).sort((a, b) => b.dateModifiedSec - a.dateModifiedSec);
      });
    } finally {
      setIsLoadingStagedAudio(false);
    }
  }, []);

  const importMediaFile = useCallback(
    async (file: File, fromVideo: boolean) => {
      playbackControllerRef.current?.stop();
      importedEngineRef.current?.pause();
      importedEngineRef.current?.setImporting(true);
      setShowImportSheet(false);

      try {
        const source = await decodeMediaFileTo48kPcm(file, fromVideo);
        const importedEng = importedEngineRef.current;
        if (importedEng) {
          importedEng.updateAecNoiseGate(
            monitoringConfig.echoCancellationEnabled,
            monitoringConfig.noiseGateThresholdDb
          );
          importedEng.updateEffectsConfig(effectsConfig);
          importedEng.updateVoiceChangerConfig(voiceChangerConfig);
          importedEng.loadSource(source);
          importedEng.setImporting(false);
        }
        if (!fromVideo) {
          const item = await probeAudioFileMetadata(file);
          setStagedAudioFiles((prev) => {
            const exists = prev.some((p) => p.id === item.id);
            if (exists) return prev;
            return [item, ...prev].sort((a, b) => b.dateModifiedSec - a.dateModifiedSec);
          });
        }
      } catch (err) {
        importedEngineRef.current?.setImporting(false);
        setStatusBanner(
          err instanceof Error ? err.message : 'Failed to decode audio from selected file.'
        );
      }
    },
    [
      effectsConfig,
      monitoringConfig.echoCancellationEnabled,
      monitoringConfig.noiseGateThresholdDb,
      voiceChangerConfig,
    ]
  );

  const importStagedItem = useCallback(
    async (item: StagedAudioItem) => {
      if (!item.file) {
        setStatusBanner('Select the audio file from your device to load it into the DSP engine.');
        return;
      }
      await importMediaFile(item.file, false);
    },
    [importMediaFile]
  );

  const toggleImportedPlayback = useCallback(() => {
    if (recordingStatus === RecordingStatus.RECORDING) {
      setStatusBanner('Stop or pause recording before playing imported audio.');
      return;
    }
    playbackControllerRef.current?.stop();
    importedEngineRef.current?.togglePlayPause();
  }, [recordingStatus]);

  const seekImportedPlayback = useCallback((positionMs: number) => {
    importedEngineRef.current?.seekTo(positionMs);
  }, []);

  const clearImportedAudio = useCallback(() => {
    importedEngineRef.current?.clearSource();
  }, []);

  const saveAndExportImportedAudio = useCallback(async () => {
    const src = importedPlaybackState.source;
    if (!src || importedPlaybackState.isExporting) return;

    importedEngineRef.current?.setExporting(true, 0);
    try {
      const result = await renderAndExportProcessedWav(
        src,
        effectsConfig,
        voiceChangerConfig,
        {
          enabled: monitoringConfig.echoCancellationEnabled,
          thresholdDb: monitoringConfig.noiseGateThresholdDb,
        },
        (pct) => importedEngineRef.current?.setExporting(true, pct)
      );
      importedEngineRef.current?.setExporting(false, 0);
      setStatusBanner(
        `Saved ${result.fileName} (${Math.max(1, Math.round(result.sizeBytes / 1024))} KB)`
      );
      const outFile = new File([result.blob], result.fileName, { type: 'audio/wav' });
      if (
        typeof navigator !== 'undefined' &&
        navigator.canShare &&
        navigator.share &&
        navigator.canShare({ files: [outFile] })
      ) {
        try {
          await navigator.share({ files: [outFile], title: result.fileName });
          return;
        } catch (err) {
          if ((err as Error)?.name === 'AbortError') return;
        }
      }
      triggerBlobDownload(result.blob, result.fileName);
    } catch (err) {
      importedEngineRef.current?.setExporting(false, 0);
      setStatusBanner(
        `Export failed: ${err instanceof Error ? err.message : 'Unknown error'}`
      );
    }
  }, [
    effectsConfig,
    importedPlaybackState.isExporting,
    importedPlaybackState.source,
    monitoringConfig.echoCancellationEnabled,
    monitoringConfig.noiseGateThresholdDb,
    voiceChangerConfig,
  ]);

  // --- Voice Changer Controls ---
  const applyVoiceChangerConfig = useCallback(
    (next: VoiceChangerConfig) => {
      setVoiceChangerConfig(next);
      audioEngineRef.current?.updateVoiceChanger(next);
      importedEngineRef.current?.updateVoiceChangerConfig(next);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        voiceChanger: next,
      };
      persistCurrentSettings();
    },
    [persistCurrentSettings]
  );

  const setVoiceChangerEnabled = useCallback(
    (enabled: boolean) => {
      applyVoiceChangerConfig({ ...voiceChangerConfig, enabled });
    },
    [applyVoiceChangerConfig, voiceChangerConfig]
  );

  const selectVoicePreset = useCallback(
    (preset: VoicePreset) => {
      applyVoiceChangerConfig(getVoicePresetDefaultConfig(preset, voiceChangerConfig.enabled));
    },
    [applyVoiceChangerConfig, voiceChangerConfig.enabled]
  );

  const resetVoicePresetToDefault = useCallback(() => {
    applyVoiceChangerConfig(
      getVoicePresetDefaultConfig(voiceChangerConfig.preset, voiceChangerConfig.enabled)
    );
  }, [applyVoiceChangerConfig, voiceChangerConfig.enabled, voiceChangerConfig.preset]);

  const updateVoicePitchLevel = useCallback(
    (percent: number) => {
      applyVoiceChangerConfig({ ...voiceChangerConfig, pitchLevelPercent: percent });
    },
    [applyVoiceChangerConfig, voiceChangerConfig]
  );

  const updateVoiceToneLevel = useCallback(
    (percent: number) => {
      applyVoiceChangerConfig({ ...voiceChangerConfig, toneLevelPercent: percent });
    },
    [applyVoiceChangerConfig, voiceChangerConfig]
  );

  const updateVoiceClarity = useCallback(
    (percent: number) => {
      applyVoiceChangerConfig({ ...voiceChangerConfig, clarityPercent: percent });
    },
    [applyVoiceChangerConfig, voiceChangerConfig]
  );

  // --- Live Voice Preview, Monitoring & AEC Noise Gate Controls ---
  const setLiveVoicePreviewEnabled = useCallback(
    async (enabled: boolean) => {
      if (enabled && !hasMicPermission) {
        const granted = await requestMicPermission();
        if (!granted) {
          setStatusBanner('Grant microphone permission to enable Live Voice Preview.');
          return;
        }
      }
      const next = { ...monitoringConfig, livePreviewEnabled: enabled };
      await audioEngineRef.current?.updateMonitoringConfig(next);
    },
    [hasMicPermission, monitoringConfig, requestMicPermission]
  );

  const updateMonitoringDelaySlider = useCallback(
    (pos: number) => {
      const snapped =
        Math.round(pos / MonitoringConstants.STEP_SLIDER) * MonitoringConstants.STEP_SLIDER;
      const clamped = Math.max(
        MonitoringConstants.MIN_SLIDER,
        Math.min(MonitoringConstants.MAX_SLIDER, Number(snapped.toFixed(2)))
      );
      const next = { ...monitoringConfig, delaySliderPosition: clamped };
      void audioEngineRef.current?.updateMonitoringConfig(next);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        monitoringDelaySlider: clamped,
      };
      persistCurrentSettings();
    },
    [monitoringConfig, persistCurrentSettings]
  );

  const resetMonitoringDelayToDefault = useCallback(() => {
    updateMonitoringDelaySlider(MonitoringConstants.DEFAULT_SLIDER);
  }, [updateMonitoringDelaySlider]);

  const updateMonitoringVolume = useCallback(
    (volumePercent: number) => {
      const snapped =
        Math.round(volumePercent / MonitoringConstants.STEP_VOLUME_PERCENT) *
        MonitoringConstants.STEP_VOLUME_PERCENT;
      const clamped = Math.max(
        MonitoringConstants.MIN_VOLUME_PERCENT,
        Math.min(MonitoringConstants.MAX_VOLUME_PERCENT, snapped)
      );
      const next = { ...monitoringConfig, previewVolumePercent: clamped };
      void audioEngineRef.current?.updateMonitoringConfig(next);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        monitoringVolumePercent: clamped,
      };
      persistCurrentSettings();
    },
    [monitoringConfig, persistCurrentSettings]
  );

  const setEchoCancellationEnabled = useCallback(
    (enabled: boolean) => {
      const next = { ...monitoringConfig, echoCancellationEnabled: enabled };
      void audioEngineRef.current?.updateMonitoringConfig(next);
      importedEngineRef.current?.updateAecNoiseGate(enabled, next.noiseGateThresholdDb);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        echoCancellationEnabled: enabled,
      };
      persistCurrentSettings();
    },
    [monitoringConfig, persistCurrentSettings]
  );

  const updateAecNoiseGateThreshold = useCallback(
    (thresholdDb: number) => {
      const clamped = Math.max(
        MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB,
        Math.min(MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB, Math.round(thresholdDb))
      );
      const next = { ...monitoringConfig, noiseGateThresholdDb: clamped };
      void audioEngineRef.current?.updateMonitoringConfig(next);
      importedEngineRef.current?.updateAecNoiseGate(next.echoCancellationEnabled, clamped);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        noiseGateThresholdDb: clamped,
      };
      persistCurrentSettings();
    },
    [monitoringConfig, persistCurrentSettings]
  );

  // --- Unified Effects Rack & Studio Effects Controls ---
  const applyEffectsRack = useCallback(
    (next: EffectsRackConfig) => {
      setEffectsConfig(next);
      audioEngineRef.current?.updateEffectsRack(next);
      importedEngineRef.current?.updateEffectsConfig(next);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        effectsRack: next,
      };
      persistCurrentSettings();
    },
    [persistCurrentSettings]
  );

  const updatePitchCorrectionConfig = useCallback(
    (c: PitchCorrectionConfig) => applyEffectsRack({ ...effectsConfig, pitchCorrection: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updatePitchFormantConfig = useCallback(
    (c: PitchFormantConfig) => applyEffectsRack({ ...effectsConfig, pitchFormant: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateEqualizerConfig = useCallback(
    (c: EqualizerConfig) => applyEffectsRack({ ...effectsConfig, equalizer: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateCompressionConfig = useCallback(
    (c: CompressionConfig) => applyEffectsRack({ ...effectsConfig, compression: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateSaturationConfig = useCallback(
    (c: SaturationConfig) => applyEffectsRack({ ...effectsConfig, saturation: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateReverbConfig = useCallback(
    (c: ReverbConfig) => applyEffectsRack({ ...effectsConfig, reverb: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateEchoConfig = useCallback(
    (c: EchoConfig) => applyEffectsRack({ ...effectsConfig, echo: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateNoiseGateConfig = useCallback(
    (c: NoiseGateConfig) => applyEffectsRack({ ...effectsConfig, noiseGate: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateDeEsserConfig = useCallback(
    (c: DeEsserConfig) => applyEffectsRack({ ...effectsConfig, deEsser: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateLimiterConfig = useCallback(
    (c: LimiterConfig) => applyEffectsRack({ ...effectsConfig, limiter: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateLowPassConfig = useCallback(
    (c: LowPassConfig) => applyEffectsRack({ ...effectsConfig, lowPass: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateHighPassConfig = useCallback(
    (c: HighPassConfig) => applyEffectsRack({ ...effectsConfig, highPass: c }),
    [applyEffectsRack, effectsConfig]
  );
  const updateHallConfig = useCallback(
    (c: HallConfig) => applyEffectsRack({ ...effectsConfig, hall: c }),
    [applyEffectsRack, effectsConfig]
  );

  const resetStudioEffect = useCallback(
    (type: StudioEffectType) => {
      switch (type) {
        case StudioEffectType.PITCH_CORRECTION:
          updatePitchCorrectionConfig(defaultPitchCorrection(effectsConfig.pitchCorrection.enabled));
          break;
        case StudioEffectType.PITCH_FORMANT:
          updatePitchFormantConfig(defaultPitchFormant(effectsConfig.pitchFormant.enabled));
          break;
        case StudioEffectType.EQUALIZER:
          updateEqualizerConfig(defaultEqualizer(effectsConfig.equalizer.enabled));
          break;
        case StudioEffectType.COMPRESSION:
          updateCompressionConfig(defaultCompression(effectsConfig.compression.enabled));
          break;
        case StudioEffectType.SATURATION:
          updateSaturationConfig(defaultSaturation(effectsConfig.saturation.enabled));
          break;
        case StudioEffectType.REVERB:
          updateReverbConfig(defaultReverb(effectsConfig.reverb.enabled));
          break;
        case StudioEffectType.DELAY:
          updateEchoConfig(defaultEcho(effectsConfig.echo.enabled));
          break;
        case StudioEffectType.NOISE_GATE:
          updateNoiseGateConfig(defaultNoiseGate(effectsConfig.noiseGate.enabled));
          break;
        case StudioEffectType.DE_ESSER:
          updateDeEsserConfig(defaultDeEsser(effectsConfig.deEsser.enabled));
          break;
        case StudioEffectType.LIMITER:
          updateLimiterConfig(defaultLimiter(effectsConfig.limiter.enabled));
          break;
      }
    },
    [
      effectsConfig,
      updateCompressionConfig,
      updateDeEsserConfig,
      updateEchoConfig,
      updateEqualizerConfig,
      updateLimiterConfig,
      updateNoiseGateConfig,
      updatePitchCorrectionConfig,
      updatePitchFormantConfig,
      updateReverbConfig,
      updateSaturationConfig,
    ]
  );

  const saveUserEffectPreset = useCallback(
    (type: StudioEffectType, name: string) => {
      const cleanName = name.trim();
      if (!cleanName) return;

      let payload: Record<string, unknown>;
      switch (type) {
        case StudioEffectType.PITCH_CORRECTION:
          payload = { ...effectsConfig.pitchCorrection };
          break;
        case StudioEffectType.PITCH_FORMANT:
          payload = { ...effectsConfig.pitchFormant };
          break;
        case StudioEffectType.EQUALIZER:
          payload = { ...effectsConfig.equalizer };
          break;
        case StudioEffectType.COMPRESSION:
          payload = { ...effectsConfig.compression };
          break;
        case StudioEffectType.SATURATION:
          payload = { ...effectsConfig.saturation };
          break;
        case StudioEffectType.REVERB:
          payload = { ...effectsConfig.reverb };
          break;
        case StudioEffectType.DELAY:
          payload = { ...effectsConfig.echo };
          break;
        case StudioEffectType.NOISE_GATE:
          payload = { ...effectsConfig.noiseGate };
          break;
        case StudioEffectType.DE_ESSER:
          payload = { ...effectsConfig.deEsser };
          break;
        case StudioEffectType.LIMITER:
          payload = { ...effectsConfig.limiter };
          break;
      }

      const newPreset: StudioEffectPreset = {
        id: `usr_${type.toLowerCase()}_${Date.now()}`,
        name: cleanName,
        effectType: type,
        isBuiltIn: false,
        payload,
      };
      const updated = [...userEffectPresets, newPreset];
      setUserEffectPresets(updated);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        userEffectPresets: updated,
      };
      persistCurrentSettings();
    },
    [effectsConfig, persistCurrentSettings, userEffectPresets]
  );

  const loadEffectPreset = useCallback(
    (preset: StudioEffectPreset) => {
      const p = preset.payload;
      switch (preset.effectType) {
        case StudioEffectType.PITCH_CORRECTION:
          updatePitchCorrectionConfig({
            ...defaultPitchCorrection(effectsConfig.pitchCorrection.enabled),
            ...(p as Partial<PitchCorrectionConfig>),
            enabled: effectsConfig.pitchCorrection.enabled,
          });
          break;
        case StudioEffectType.PITCH_FORMANT:
          updatePitchFormantConfig({
            ...defaultPitchFormant(effectsConfig.pitchFormant.enabled),
            ...(p as Partial<PitchFormantConfig>),
            enabled: effectsConfig.pitchFormant.enabled,
          });
          break;
        case StudioEffectType.EQUALIZER:
          updateEqualizerConfig({
            ...defaultEqualizer(effectsConfig.equalizer.enabled),
            ...(p as Partial<EqualizerConfig>),
            enabled: effectsConfig.equalizer.enabled,
          });
          break;
        case StudioEffectType.COMPRESSION:
          updateCompressionConfig({
            ...defaultCompression(effectsConfig.compression.enabled),
            ...(p as Partial<CompressionConfig>),
            enabled: effectsConfig.compression.enabled,
          });
          break;
        case StudioEffectType.SATURATION:
          updateSaturationConfig({
            ...defaultSaturation(effectsConfig.saturation.enabled),
            ...(p as Partial<SaturationConfig>),
            enabled: effectsConfig.saturation.enabled,
          });
          break;
        case StudioEffectType.REVERB:
          updateReverbConfig({
            ...defaultReverb(effectsConfig.reverb.enabled),
            ...(p as Partial<ReverbConfig>),
            enabled: effectsConfig.reverb.enabled,
          });
          break;
        case StudioEffectType.DELAY:
          updateEchoConfig({
            ...defaultEcho(effectsConfig.echo.enabled),
            ...(p as Partial<EchoConfig>),
            enabled: effectsConfig.echo.enabled,
          });
          break;
        case StudioEffectType.NOISE_GATE:
          updateNoiseGateConfig({
            ...defaultNoiseGate(effectsConfig.noiseGate.enabled),
            ...(p as Partial<NoiseGateConfig>),
            enabled: effectsConfig.noiseGate.enabled,
          });
          break;
        case StudioEffectType.DE_ESSER:
          updateDeEsserConfig({
            ...defaultDeEsser(effectsConfig.deEsser.enabled),
            ...(p as Partial<DeEsserConfig>),
            enabled: effectsConfig.deEsser.enabled,
          });
          break;
        case StudioEffectType.LIMITER:
          updateLimiterConfig({
            ...defaultLimiter(effectsConfig.limiter.enabled),
            ...(p as Partial<LimiterConfig>),
            enabled: effectsConfig.limiter.enabled,
          });
          break;
      }
    },
    [
      effectsConfig,
      updateCompressionConfig,
      updateDeEsserConfig,
      updateEchoConfig,
      updateEqualizerConfig,
      updateLimiterConfig,
      updateNoiseGateConfig,
      updatePitchCorrectionConfig,
      updatePitchFormantConfig,
      updateReverbConfig,
      updateSaturationConfig,
    ]
  );

  const deleteUserEffectPreset = useCallback(
    (presetId: string) => {
      const updated = userEffectPresets.filter((item) => item.id !== presetId);
      setUserEffectPresets(updated);
      persistSnapshotRef.current = {
        ...persistSnapshotRef.current,
        userEffectPresets: updated,
      };
      persistCurrentSettings();
    },
    [persistCurrentSettings, userEffectPresets]
  );

  return {
    selectedTab,
    setSelectedTab,
    hasMicPermission,
    requestMicPermission,
    hardwareState,
    refreshDevices,
    selectInputDevice,
    selectOutputDevice,
    recordingStatus,
    subscribeMetrics,
    getMetricsSnapshot,
    monitoringConfig,
    voiceChangerConfig,
    effectsConfig,
    userEffectPresets,
    recordings,
    playbackState,
    importedPlaybackState,
    statusBanner,
    dismissBanner: () => setStatusBanner(null),
    // Recording transport
    startRecording,
    pauseRecording,
    resumeRecording,
    stopRecording,
    resetClipCounter,
    toggleManualInputMonitor,
    calibrateBackgroundNoise,
    // Recorded playback & management
    togglePlayback,
    seekPlayback,
    renameRecording,
    deleteRecording,
    shareOrDownloadRecording,
    // Import & offline WAV export
    showImportSheet,
    openImportSheet,
    closeImportSheet,
    stagedAudioFiles,
    isLoadingStagedAudio,
    addFilesToStagedAudio,
    importMediaFile,
    importStagedItem,
    toggleImportedPlayback,
    seekImportedPlayback,
    clearImportedAudio,
    saveAndExportImportedAudio,
    // Voice changer, monitoring & AEC Noise Gate
    setVoiceChangerEnabled,
    selectVoicePreset,
    resetVoicePresetToDefault,
    updateVoicePitchLevel,
    updateVoiceToneLevel,
    updateVoiceClarity,
    setLiveVoicePreviewEnabled,
    updateMonitoringDelaySlider,
    resetMonitoringDelayToDefault,
    updateMonitoringVolume,
    setEchoCancellationEnabled,
    updateAecNoiseGateThreshold,
    // Effects
    updatePitchCorrectionConfig,
    updatePitchFormantConfig,
    updateEqualizerConfig,
    updateCompressionConfig,
    updateSaturationConfig,
    updateReverbConfig,
    updateEchoConfig,
    updateNoiseGateConfig,
    updateDeEsserConfig,
    updateLimiterConfig,
    updateLowPassConfig,
    updateHighPassConfig,
    updateHallConfig,
    resetStudioEffect,
    saveUserEffectPreset,
    loadEffectPreset,
    deleteUserEffectPreset,
  };
}
