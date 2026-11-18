import { StudioDspPipeline } from '../dsp/EffectsRackDsp';
import { HighPassFilter } from '../dsp/HighPassFilter';
import { LinearResampler } from '../dsp/LinearResampler';
import { MonitoringDelayLine } from '../dsp/MonitoringDelayLine';
import { NoiseFloorEstimator } from '../dsp/NoiseFloorEstimator';
import { NoiseGateProcessor } from '../dsp/NoiseGateProcessor';
import { PacketLossConcealment } from '../dsp/PacketLossConcealment';
import {
  AudioConfig,
  createDefaultLiveMetrics,
  createDefaultMonitoringConfig,
  EffectsRackConfig,
  getAddedOutputDelayMs,
  getMonitoringZone,
  LiveAudioMetrics,
  MonitoringConfig,
  MonitoringConstants,
  MonitoringLatencyZone,
  RecordingStatus,
  VoiceChangerConfig,
} from '../types/audio';
import { DeviceHardwareManager } from './DeviceHardwareManager';
import { StreamingWavAccumulator } from './WavPcmWriter';

export interface RecordedWavResult {
  blob: Blob;
  durationMs: number;
  sizeBytes: number;
  clipCount: number;
}

type AudioContextWithSink = AudioContext & {
  setSinkId?: (sinkId: string) => Promise<void>;
};

export class RealTimeAudioEngine {
  private readonly hardwareManager: DeviceHardwareManager;
  private readonly onRecordingFinalized: (result: RecordedWavResult) => void;
  private readonly onEngineError: (message: string) => void;

  // Stage 1: Acoustic Echo Cancellation DSP companion (120 Hz HPF + PLC + WebRTC track AEC)
  private readonly aecHighPassFilter = new HighPassFilter(120.0, AudioConfig.OUTPUT_SAMPLE_RATE_HZ);
  private readonly aecPlc = new PacketLossConcealment(AudioConfig.OUTPUT_SAMPLE_RATE_HZ);

  // Stage 2: Dedicated Adjustable dBFS Noise Gate (-60 to -20 dBFS, technically separate from AEC)
  private readonly aecNoiseGate = NoiseGateProcessor.createAecNoiseGate(
    AudioConfig.OUTPUT_SAMPLE_RATE_HZ
  );

  // Stage 3: Shared Voice Changer + Effects Rack
  private readonly dspPipeline = new StudioDspPipeline(AudioConfig.OUTPUT_SAMPLE_RATE_HZ);

  // Branch A output delay line & Noise floor estimator
  private readonly monitoringDelayLine = new MonitoringDelayLine(AudioConfig.OUTPUT_SAMPLE_RATE_HZ);
  private readonly noiseFloorEstimator = new NoiseFloorEstimator();
  private readonly wavAccumulator = new StreamingWavAccumulator();

  private recordingStatus: RecordingStatus = RecordingStatus.IDLE;
  private monitoringConfig: MonitoringConfig = createDefaultMonitoringConfig();
  private metrics: LiveAudioMetrics = createDefaultLiveMetrics();

  private statusListeners = new Set<(s: RecordingStatus) => void>();
  private monitoringListeners = new Set<(c: MonitoringConfig) => void>();
  private metricsListeners = new Set<(m: LiveAudioMetrics) => void>();

  private audioCtx: AudioContextWithSink | null = null;
  private mediaStream: MediaStream | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private processorNode: ScriptProcessorNode | null = null;
  private inputResampler: LinearResampler | null = null;
  private outputResampler: LinearResampler | null = null;

  private isRecordingPaused = false;
  private accumulatedClipCount = 0;
  private clipHoldFrames = 0;
  private smoothedMonitorGain = 1.0;

  private hasPermission = false;
  private isForeground = true;
  private manualInputMonitorEnabled = false;
  private isStarting = false;
  private restartNeededAfterStart = false;

  private uiRmsAccumSq = 0;
  private uiSampleCount = 0;
  private uiPeakAbs = 0;
  private lastUiEmitMs = 0;

  private scratch48k = new Float32Array(2048);
  private scratchDelay = new Float32Array(2048);
  private scratchOut = new Float32Array(2048);

  constructor(
    hardwareManager: DeviceHardwareManager,
    onRecordingFinalized: (result: RecordedWavResult) => void,
    onEngineError: (message: string) => void
  ) {
    this.hardwareManager = hardwareManager;
    this.onRecordingFinalized = onRecordingFinalized;
    this.onEngineError = onEngineError;
    this.aecNoiseGate.updateAecThreshold(
      this.monitoringConfig.echoCancellationEnabled,
      this.monitoringConfig.noiseGateThresholdDb
    );
  }

  subscribeStatus(fn: (s: RecordingStatus) => void): () => void {
    this.statusListeners.add(fn);
    fn(this.recordingStatus);
    return () => this.statusListeners.delete(fn);
  }

  subscribeMonitoring(fn: (c: MonitoringConfig) => void): () => void {
    this.monitoringListeners.add(fn);
    fn(this.monitoringConfig);
    return () => this.monitoringListeners.delete(fn);
  }

  subscribeMetrics(fn: (m: LiveAudioMetrics) => void): () => void {
    this.metricsListeners.add(fn);
    fn(this.metrics);
    return () => this.metricsListeners.delete(fn);
  }

  getRecordingStatus(): RecordingStatus {
    return this.recordingStatus;
  }

  getMonitoringConfig(): MonitoringConfig {
    return this.monitoringConfig;
  }

  getMetrics(): LiveAudioMetrics {
    return this.metrics;
  }

  private emitStatus(): void {
    for (const fn of this.statusListeners) fn(this.recordingStatus);
  }

  private emitMonitoring(): void {
    for (const fn of this.monitoringListeners) fn(this.monitoringConfig);
  }

  private emitMetrics(): void {
    for (const fn of this.metricsListeners) fn(this.metrics);
  }

  updateEffectsRack(config: EffectsRackConfig): void {
    this.dspPipeline.updateEffectsConfig(config);
    this.updateEstimatedLatencyOnly();
  }

  updateVoiceChanger(config: VoiceChangerConfig): void {
    this.dspPipeline.updateVoiceChangerConfig(config);
    this.updateEstimatedLatencyOnly();
  }

  setAppForeground(foreground: boolean): void {
    if (this.isForeground === foreground) return;
    this.isForeground = foreground;
    // Never stop active recording or live preview when switching visibility;
    // only release idle input monitoring when app goes background.
    if (!foreground && this.manualInputMonitorEnabled) {
      this.manualInputMonitorEnabled = false;
    }
    void this.syncEngineLifecycle();
  }

  async setManualInputMonitorEnabled(enabled: boolean): Promise<void> {
    if (enabled && !this.hasPermission) {
      const ok = await this.requestMicrophonePermission();
      if (!ok) return;
    }
    this.manualInputMonitorEnabled = enabled;
    await this.syncEngineLifecycle();
  }

  async startNoiseFloorCalibration(): Promise<void> {
    if (!this.hasPermission) {
      const ok = await this.requestMicrophonePermission();
      if (!ok) return;
    }
    this.noiseFloorEstimator.startCalibration();
    this.metrics = {
      ...this.metrics,
      isCalibratingNoise: true,
      calibrationProgress: 0,
    };
    this.emitMetrics();
    await this.syncEngineLifecycle();
  }

  async requestMicrophonePermission(): Promise<boolean> {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      this.onEngineError('Browser microphone capture is not supported in this context.');
      return false;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: false,
          noiseSuppression: false,
          autoGainControl: false,
        },
      });
      stream.getTracks().forEach((t) => t.stop());
      this.setPermissionGranted(true);
      await this.hardwareManager.refreshDevices();
      return true;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Microphone permission denied.';
      this.onEngineError(`Microphone access required: ${msg}`);
      this.setPermissionGranted(false);
      return false;
    }
  }

  setPermissionGranted(granted: boolean): void {
    this.hasPermission = granted;
    void this.syncEngineLifecycle();
  }

  async updateMonitoringConfig(newConfig: MonitoringConfig): Promise<void> {
    const prev = this.monitoringConfig;
    const clampedThreshold = Math.max(
      MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB,
      Math.min(MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB, Math.round(newConfig.noiseGateThresholdDb))
    );
    const clamped: MonitoringConfig = {
      livePreviewEnabled: newConfig.livePreviewEnabled,
      delaySliderPosition: Math.max(
        MonitoringConstants.MIN_SLIDER,
        Math.min(MonitoringConstants.MAX_SLIDER, newConfig.delaySliderPosition)
      ),
      previewVolumePercent: Math.max(
        MonitoringConstants.MIN_VOLUME_PERCENT,
        Math.min(MonitoringConstants.MAX_VOLUME_PERCENT, newConfig.previewVolumePercent)
      ),
      echoCancellationEnabled: newConfig.echoCancellationEnabled,
      noiseGateThresholdDb: clampedThreshold,
    };
    this.monitoringConfig = clamped;
    this.monitoringDelayLine.setDelayMs(getAddedOutputDelayMs(clamped.delaySliderPosition));
    this.aecNoiseGate.updateAecThreshold(
      clamped.echoCancellationEnabled,
      clamped.noiseGateThresholdDb
    );
    this.emitMonitoring();

    if (
      clamped.echoCancellationEnabled !== prev.echoCancellationEnabled ||
      clamped.livePreviewEnabled !== prev.livePreviewEnabled
    ) {
      await this.applyMicConstraintsAndInspect(clamped);
    }

    if (clamped.livePreviewEnabled !== prev.livePreviewEnabled) {
      await this.syncEngineLifecycle();
      if (clamped.livePreviewEnabled && this.audioCtx?.state === 'suspended') {
        await this.audioCtx.resume().catch(() => undefined);
      }
    } else {
      this.updateEstimatedLatencyOnly();
    }
  }

  private async applyMicConstraintsAndInspect(mon: MonitoringConfig): Promise<void> {
    const track = this.mediaStream?.getAudioTracks()[0];
    if (!track) {
      this.hardwareManager.updateAecDiagnostics(mon.echoCancellationEnabled, false, false, false);
      return;
    }

    const hw = this.hardwareManager.getState();
    // Enable browser/HW AEC whenever AEC is ON and either live preview is running,
    // a Bluetooth route is active, or speakers may feed back to the mic.
    const shouldRequestHwAec =
      mon.echoCancellationEnabled &&
      (mon.livePreviewEnabled || !hw.isWiredHeadphoneConnected || hw.isBluetoothOutputConnected);

    try {
      await track.applyConstraints({
        echoCancellation: shouldRequestHwAec,
        noiseSuppression: false,
        autoGainControl: false,
      });
    } catch {
      // Some browsers require track reopening for echoCancellation changes; handled cleanly below
    }

    this.reportActualTrackAecStatus(track, mon.echoCancellationEnabled);
  }

  private reportActualTrackAecStatus(track: MediaStreamTrack | undefined, aecRequested: boolean): void {
    if (!track) {
      this.hardwareManager.updateAecDiagnostics(aecRequested, false, false, false);
      return;
    }
    const caps = (track.getCapabilities?.() ?? {}) as MediaTrackCapabilities & {
      echoCancellation?: boolean[];
    };
    const settings = track.getSettings?.() ?? {};
    const hwSupported = Array.isArray(caps.echoCancellation)
      ? caps.echoCancellation.includes(true)
      : Boolean(settings.echoCancellation);
    const hwActive = Boolean(aecRequested && settings.echoCancellation === true);

    this.hardwareManager.updateAecDiagnostics(aecRequested, true, hwSupported, hwActive);
  }

  async applyPreferredOutputRoute(): Promise<void> {
    const ctx = this.audioCtx;
    if (!ctx?.setSinkId) return;
    const hw = this.hardwareManager.getState();
    if (!hw.outputDeviceId) return;
    try {
      await ctx.setSinkId(hw.outputDeviceId);
    } catch {
      // Browser denied sink switch or device no longer available
    }
  }

  async onHardwareTopologyChanged(): Promise<void> {
    if (this.isStarting) {
      this.restartNeededAfterStart = true;
      return;
    }
    if (this.audioCtx) {
      await this.stopAudioPipeline();
      await this.syncEngineLifecycle();
    } else {
      const hw = this.hardwareManager.getState();
      this.hardwareManager.updateAecDiagnostics(
        this.monitoringConfig.echoCancellationEnabled,
        false,
        hw.aecHardwareSupported,
        false
      );
    }
  }

  async startRecording(): Promise<boolean> {
    if (!this.hasPermission) {
      const granted = await this.requestMicrophonePermission();
      if (!granted) return false;
    }

    if (
      this.recordingStatus === RecordingStatus.RECORDING ||
      this.recordingStatus === RecordingStatus.PAUSED ||
      this.recordingStatus === RecordingStatus.INTERRUPTED_PAUSED
    ) {
      return true;
    }

    this.wavAccumulator.reset();
    this.isRecordingPaused = false;
    this.accumulatedClipCount = 0;
    this.clipHoldFrames = 0;
    this.recordingStatus = RecordingStatus.RECORDING;
    this.emitStatus();

    await this.syncEngineLifecycle();
    if (this.audioCtx?.state === 'suspended') {
      await this.audioCtx.resume().catch(() => undefined);
    }
    return true;
  }

  pauseRecording(): void {
    if (this.recordingStatus === RecordingStatus.RECORDING) {
      this.isRecordingPaused = true;
      this.recordingStatus = RecordingStatus.PAUSED;
      this.emitStatus();
      void this.syncEngineLifecycle();
    }
  }

  async resumeRecording(): Promise<void> {
    if (
      this.recordingStatus === RecordingStatus.PAUSED ||
      this.recordingStatus === RecordingStatus.INTERRUPTED_PAUSED
    ) {
      this.isRecordingPaused = false;
      this.recordingStatus = RecordingStatus.RECORDING;
      this.emitStatus();
      await this.syncEngineLifecycle();
      if (this.audioCtx?.state === 'suspended') {
        await this.audioCtx.resume().catch(() => undefined);
      }
    }
  }

  stopRecording(): RecordedWavResult | null {
    this.isRecordingPaused = false;
    this.recordingStatus = RecordingStatus.IDLE;
    this.emitStatus();

    const finalized = this.wavAccumulator.finalizeToBlob();
    const totalClips = this.wavAccumulator.totalClips;
    this.wavAccumulator.reset();

    this.metrics = {
      ...this.metrics,
      recordingDurationMs: 0,
    };
    this.emitMetrics();
    void this.syncEngineLifecycle();

    if (finalized) {
      const res: RecordedWavResult = {
        ...finalized,
        clipCount: totalClips,
      };
      this.onRecordingFinalized(res);
      return res;
    }
    return null;
  }

  async stopAll(): Promise<void> {
    this.manualInputMonitorEnabled = false;
    this.noiseFloorEstimator.cancelCalibration();
    if (this.monitoringConfig.livePreviewEnabled) {
      this.monitoringConfig = { ...this.monitoringConfig, livePreviewEnabled: false };
      this.emitMonitoring();
    }
    if (this.recordingStatus !== RecordingStatus.IDLE) {
      this.stopRecording();
    }
    await this.stopAudioPipeline();
  }

  resetClipCounter(): void {
    this.accumulatedClipCount = 0;
    this.clipHoldFrames = 0;
    this.metrics = { ...this.metrics, isClipping: false, clipCount: 0 };
    this.emitMetrics();
  }

  private handleTrackInterruption(): void {
    if (this.recordingStatus === RecordingStatus.RECORDING) {
      this.isRecordingPaused = true;
      this.recordingStatus = RecordingStatus.INTERRUPTED_PAUSED;
      this.emitStatus();
      this.onEngineError(
        'Audio route changed or microphone interrupted. Tap Resume to continue recording.'
      );
    }
    void this.stopAudioPipeline().then(() => this.hardwareManager.refreshDevices());
  }

  /**
   * Strict Only-Active Processing Policy:
   * Activate microphone capture and DSP ONLY while actively recording (not paused),
   * Live Voice Preview is ON, calibrating ambient noise, or user enabled Input Level Monitor.
   */
  private async syncEngineLifecycle(): Promise<void> {
    const isActivelyRecording =
      this.recordingStatus === RecordingStatus.RECORDING && !this.isRecordingPaused;
    const isCalibrating = this.noiseFloorEstimator.getIsCalibrating();
    const needsEngine =
      isActivelyRecording ||
      this.monitoringConfig.livePreviewEnabled ||
      isCalibrating ||
      this.manualInputMonitorEnabled;

    if (needsEngine && !this.hasPermission) {
      if (this.monitoringConfig.livePreviewEnabled) {
        this.monitoringConfig = { ...this.monitoringConfig, livePreviewEnabled: false };
        this.emitMonitoring();
      }
      this.manualInputMonitorEnabled = false;
      this.noiseFloorEstimator.cancelCalibration();
      this.onEngineError('Microphone permission is required.');
      return;
    }

    if (needsEngine) {
      if (!this.audioCtx && !this.isStarting) {
        await this.startAudioPipeline();
      }
    } else {
      await this.stopAudioPipeline();
      this.metrics = {
        ...this.metrics,
        rmsDb: -60,
        peakDb: -60,
        isClipping: false,
        isInputMonitoringActive: false,
        isCalibratingNoise: false,
        calibrationProgress: 0,
      };
      this.updateEstimatedLatencyOnly();
    }
  }

  private async startAudioPipeline(): Promise<void> {
    if (this.isStarting || typeof window === 'undefined') return;
    this.isStarting = true;

    try {
      const hw = this.hardwareManager.getState();
      const monCfg = this.monitoringConfig;
      const shouldRequestHwAec =
        monCfg.echoCancellationEnabled &&
        (monCfg.livePreviewEnabled || !hw.isWiredHeadphoneConnected || hw.isBluetoothOutputConnected);

      const audioConstraints: MediaTrackConstraints = {
        echoCancellation: shouldRequestHwAec,
        noiseSuppression: false,
        autoGainControl: false,
        channelCount: 1,
      };
      if (hw.inputDeviceId && hw.inputDeviceId !== 'default') {
        audioConstraints.deviceId = { ideal: hw.inputDeviceId };
      }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: audioConstraints });
      const AudioCtxCtor =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AudioCtxCtor({
        sampleRate: AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
        latencyHint: 'interactive',
      }) as AudioContextWithSink;

      if (ctx.state === 'suspended') {
        await ctx.resume().catch(() => undefined);
      }

      if (ctx.setSinkId && hw.outputDeviceId && hw.outputDeviceId !== 'default') {
        await ctx.setSinkId(hw.outputDeviceId).catch(() => undefined);
      }

      const track = stream.getAudioTracks()[0];
      const settings = track?.getSettings?.() ?? {};
      const actualSampleRate = ctx.sampleRate || AudioConfig.OUTPUT_SAMPLE_RATE_HZ;
      const actualChannels = settings.channelCount || 1;

      track?.addEventListener('ended', () => {
        this.handleTrackInterruption();
      });

      this.hardwareManager.updateActiveCaptureStats(
        actualSampleRate,
        actualChannels,
        settings.deviceId ?? hw.inputDeviceId,
        track?.label ?? hw.inputDeviceName
      );
      this.reportActualTrackAecStatus(track, monCfg.echoCancellationEnabled);

      this.aecHighPassFilter.reset();
      this.aecPlc.reset();
      this.aecNoiseGate.reset();
      this.aecNoiseGate.updateAecThreshold(
        monCfg.echoCancellationEnabled,
        monCfg.noiseGateThresholdDb
      );
      this.dspPipeline.reset();
      this.monitoringDelayLine.reset();
      this.smoothedMonitorGain = monCfg.previewVolumePercent / 100;

      this.inputResampler = new LinearResampler(
        actualSampleRate,
        AudioConfig.OUTPUT_SAMPLE_RATE_HZ
      );
      this.outputResampler = new LinearResampler(
        AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
        actualSampleRate
      );

      // Smaller buffer when Faster zone is selected
      const bufferSize =
        getMonitoringZone(monCfg.delaySliderPosition) === MonitoringLatencyZone.FASTER ? 256 : 512;
      const source = ctx.createMediaStreamSource(stream);
      const processor = ctx.createScriptProcessor(bufferSize, 1, 1);

      processor.onaudioprocess = (event: AudioProcessingEvent) => {
        const inputData = event.inputBuffer.getChannelData(0);
        const outputData = event.outputBuffer.getChannelData(0);
        this.processAudioBlock(inputData, outputData, actualSampleRate);
      };

      source.connect(processor);
      processor.connect(ctx.destination);

      this.audioCtx = ctx;
      this.mediaStream = stream;
      this.sourceNode = source;
      this.processorNode = processor;
      this.metrics = { ...this.metrics, isInputMonitoringActive: true };
      this.updateEstimatedLatencyOnly();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unable to start audio capture.';
      this.onEngineError(msg);
      await this.stopAudioPipeline();
    } finally {
      this.isStarting = false;
      if (this.restartNeededAfterStart) {
        this.restartNeededAfterStart = false;
        void this.onHardwareTopologyChanged();
      }
    }
  }

  private async stopAudioPipeline(): Promise<void> {
    if (this.processorNode) {
      this.processorNode.onaudioprocess = null;
      try {
        this.processorNode.disconnect();
      } catch {
        // ignore
      }
      this.processorNode = null;
    }
    if (this.sourceNode) {
      try {
        this.sourceNode.disconnect();
      } catch {
        // ignore
      }
      this.sourceNode = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach((t) => t.stop());
      this.mediaStream = null;
    }
    if (this.audioCtx) {
      try {
        await this.audioCtx.close();
      } catch {
        // ignore
      }
      this.audioCtx = null;
    }
    this.hardwareManager.updateAecDiagnostics(
      this.monitoringConfig.echoCancellationEnabled,
      false,
      false,
      false
    );
  }

  private processAudioBlock(
    input: Float32Array,
    output: Float32Array,
    ctxSampleRateHz: number
  ): void {
    const inCount = input.length;
    if (inCount <= 0) {
      output.fill(0);
      return;
    }

    const needed48k = Math.ceil((inCount * AudioConfig.OUTPUT_SAMPLE_RATE_HZ) / ctxSampleRateHz) + 64;
    if (this.scratch48k.length < needed48k) {
      this.scratch48k = new Float32Array(needed48k * 2);
      this.scratchDelay = new Float32Array(needed48k * 2);
      this.scratchOut = new Float32Array(needed48k * 2);
    }

    const count48k = this.inputResampler
      ? this.inputResampler.process(input, inCount, this.scratch48k)
      : inCount;

    if (count48k <= 0) {
      output.fill(0);
      return;
    }

    // Measure raw microphone input level BEFORE AEC, Noise Gate, or Voice Changer DSP
    let blockClip = false;
    let blockSqSum = 0;
    for (let i = 0; i < count48k; i++) {
      const s = this.scratch48k[i];
      const a = Math.abs(s);
      if (a > this.uiPeakAbs) this.uiPeakAbs = a;
      if (a >= 0.99) blockClip = true;
      blockSqSum += s * s;
    }
    this.uiRmsAccumSq += blockSqSum;
    this.uiSampleCount += count48k;

    if (blockClip) {
      this.clipHoldFrames = 24;
    } else if (this.clipHoldFrames > 0) {
      this.clipHoldFrames--;
    }

    // Feed ambient Noise Floor Estimator / Calibration
    const blockRmsDb = 20 * Math.log10(Math.max(1e-4, Math.sqrt(blockSqSum / count48k)));
    const blockMs = (count48k * 1000) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ;
    const calibrationResult = this.noiseFloorEstimator.observeBlock(blockRmsDb, blockMs);
    if (calibrationResult !== null) {
      queueMicrotask(() => {
        this.metrics = {
          ...this.metrics,
          isCalibratingNoise: false,
          calibrationProgress: 0,
          calibrationRecommendationDb: calibrationResult,
          estimatedNoiseFloorDb: this.noiseFloorEstimator.getEstimatedNoiseFloorDb(),
          noiseFloorDetected: true,
        };
        this.emitMetrics();
        void this.syncEngineLifecycle();
      });
    }

    const previewOn = this.monitoringConfig.livePreviewEnabled;
    const isRecordingActive =
      this.recordingStatus === RecordingStatus.RECORDING && !this.isRecordingPaused;
    const aecOn = this.monitoringConfig.echoCancellationEnabled;

    // Run audio DSP stages when actively previewing, recording, or calibrating/monitoring gate state
    if (previewOn || isRecordingActive || this.manualInputMonitorEnabled) {
      // Stage 1: AEC DSP Stage (120 Hz HPF + PLC; avoids double processing)
      if (aecOn) {
        this.aecHighPassFilter.processInPlace(this.scratch48k, count48k);
        this.aecPlc.recordValidFrame(this.scratch48k, count48k);
      }

      // Stage 2: Dedicated Adjustable dBFS Noise Gate (-60 to -20 dBFS, technically separate from AEC)
      this.aecNoiseGate.processInPlace(this.scratch48k, count48k);

      // Stage 3: Studio DSP Pipeline (Effects Rack + Voice Changer) when recording or previewing
      if (previewOn || isRecordingActive) {
        this.dspPipeline.processInPlace(this.scratch48k, count48k);
      }

      // Branch B: Zero-delay 48 kHz 16-bit Mono PCM WAV recording
      if (isRecordingActive) {
        const clips = this.wavAccumulator.writeSamples(this.scratch48k, count48k);
        if (clips > 0) {
          this.accumulatedClipCount += clips;
        }
      }
    }

    // Branch A: Live Voice Preview (Delay + 0-500% Volume + soft limiter)
    if (previewOn) {
      this.monitoringDelayLine.process(this.scratch48k, count48k, this.scratchDelay);
      const targetGain = this.monitoringConfig.previewVolumePercent / 100;
      const alpha = 0.02;
      for (let i = 0; i < count48k; i++) {
        this.smoothedMonitorGain += alpha * (targetGain - this.smoothedMonitorGain);
        const amplified = this.scratchDelay[i] * this.smoothedMonitorGain;
        this.scratchDelay[i] = this.softProtectMonitor(amplified);
      }

      if (ctxSampleRateHz === AudioConfig.OUTPUT_SAMPLE_RATE_HZ || !this.outputResampler) {
        const copyLen = Math.min(output.length, count48k);
        output.set(this.scratchDelay.subarray(0, copyLen), 0);
        if (copyLen < output.length) output.fill(0, copyLen);
      } else {
        const written = this.outputResampler.process(this.scratchDelay, count48k, this.scratchOut);
        const copyLen = Math.min(output.length, written);
        output.set(this.scratchOut.subarray(0, copyLen), 0);
        if (copyLen < output.length) output.fill(0, copyLen);
      }
    } else {
      output.fill(0);
    }

    // Throttled UI telemetry (~20 Hz)
    const now = performance.now();
    if (now - this.lastUiEmitMs >= 50 && this.uiSampleCount > 0) {
      this.lastUiEmitMs = now;
      const rms = Math.sqrt(this.uiRmsAccumSq / this.uiSampleCount);
      const rmsDb = Math.max(-60, Math.min(0, 20 * Math.log10(Math.max(1e-4, rms))));
      const peakDb = Math.max(-60, Math.min(0, 20 * Math.log10(Math.max(1e-4, this.uiPeakAbs))));

      this.metrics = {
        ...this.metrics,
        rmsDb,
        peakDb,
        isClipping: this.clipHoldFrames > 0,
        clipCount: this.accumulatedClipCount,
        gateOpen: this.dspPipeline.noiseGate.lastIsGateOpen,
        gateGainReductionDb: this.dspPipeline.noiseGate.lastGainReductionDb,
        aecGateOpen: this.aecNoiseGate.lastIsGateOpen,
        aecGateGainReductionDb: this.aecNoiseGate.lastGainReductionDb,
        estimatedNoiseFloorDb: this.noiseFloorEstimator.getEstimatedNoiseFloorDb(),
        noiseFloorDetected: this.noiseFloorEstimator.getNoiseFloorDetected(),
        isCalibratingNoise: this.noiseFloorEstimator.getIsCalibrating(),
        calibrationProgress: this.noiseFloorEstimator.getCalibrationProgress(),
        isInputMonitoringActive: true,
        recordingDurationMs:
          this.recordingStatus !== RecordingStatus.IDLE
            ? this.wavAccumulator.getRecordedDurationMs()
            : 0,
        estimatedMonitoringLatencyMs: this.computeEstimatedLatencyMs(ctxSampleRateHz),
      };
      this.emitMetrics();

      this.uiRmsAccumSq = 0;
      this.uiSampleCount = 0;
      this.uiPeakAbs = 0;
    }
  }

  private softProtectMonitor(x: number): number {
    const absX = Math.abs(x);
    if (absX <= 0.8) return x;
    const excess = (absX - 0.8) / 0.16;
    const shaped = 0.8 + 0.16 * Math.tanh(excess);
    return x >= 0 ? shaped : -shaped;
  }

  private updateEstimatedLatencyOnly(): void {
    const ctxRate =
      this.audioCtx?.sampleRate ?? this.hardwareManager.getState().activeInputSampleRateHz;
    this.metrics = {
      ...this.metrics,
      estimatedMonitoringLatencyMs: this.computeEstimatedLatencyMs(ctxRate),
    };
    this.emitMetrics();
  }

  private computeEstimatedLatencyMs(sampleRateHz: number): number {
    const cfg = this.monitoringConfig;
    const hw = this.hardwareManager.getState();
    const bufferFrames =
      getMonitoringZone(cfg.delaySliderPosition) === MonitoringLatencyZone.FASTER ? 256 : 512;
    const ioMs = (bufferFrames * 1000) / Math.max(8000, sampleRateHz);
    const vc = this.dspPipeline.getVoiceChangerConfig();
    const fx = this.dspPipeline.getEffectsConfig();
    const pitchActive =
      (vc.enabled && vc.pitchLevelPercent > 0) ||
      fx.pitchCorrection.enabled ||
      fx.pitchFormant.enabled;
    const dspMs = pitchActive ? 30.8 : 0;
    const btPenaltyMs = hw.isBluetoothOutputConnected ? 45 : 0;
    const addedMs = getAddedOutputDelayMs(cfg.delaySliderPosition);
    return Math.max(5, Math.round(ioMs * 1.5 + dspMs + btPenaltyMs + addedMs));
  }
}
