import React, { memo } from 'react';
import {
  Activity,
  AlertTriangle,
  Headphones,
  Minus,
  Plus,
  RotateCcw,
  Sliders,
  Sparkles,
} from 'lucide-react';
import { DspParameterSlider } from '../components/StudioControls';
import {
  MetricsSnapshotFn,
  MetricsSubscribeFn,
  useLiveAudioMetrics,
} from '../hooks/useVoxForge';
import {
  AudioHardwareState,
  getAddedOutputDelayMs,
  getMonitoringZone,
  MonitoringConfig,
  MonitoringConstants,
  MonitoringLatencyZone,
  VOICE_PRESET_LABELS,
  VoiceChangerConfig,
  VoiceChangerConstants,
  VoicePreset,
} from '../types/audio';

interface VoiceChangerScreenProps {
  config: VoiceChangerConfig;
  monitoringConfig: MonitoringConfig;
  hardwareState: AudioHardwareState;
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
  onEnabledChanged: (enabled: boolean) => void;
  onSelectPreset: (preset: VoicePreset) => void;
  onResetPreset: () => void;
  onPitchLevelChanged: (pct: number) => void;
  onToneLevelChanged: (pct: number) => void;
  onClarityChanged: (pct: number) => void;
  onLivePreviewToggle: (enabled: boolean) => void;
  onMonitoringDelayChanged: (sliderPos: number) => void;
  onResetMonitoringDelay: () => void;
  onMonitoringVolumeChanged: (pct: number) => void;
  onEchoCancellationToggle: (enabled: boolean) => void;
  onAecNoiseGateThresholdChanged: (db: number) => void;
  onCalibrateBackgroundNoise: () => void;
}

const MonitoringLatencyReadout: React.FC<{
  sliderPosition: number;
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
}> = memo(({ sliderPosition, subscribeMetrics, getMetricsSnapshot }) => {
  const metrics = useLiveAudioMetrics(subscribeMetrics, getMetricsSnapshot);
  const zone = getMonitoringZone(sliderPosition);
  const addedMs = getAddedOutputDelayMs(sliderPosition);

  const statusReadout =
    zone === MonitoringLatencyZone.FASTER
      ? `Faster • Min Buffer (~${metrics.estimatedMonitoringLatencyMs} ms est. latency)`
      : zone === MonitoringLatencyZone.DEFAULT
        ? `Default • 0 ms added (~${metrics.estimatedMonitoringLatencyMs} ms est. latency)`
        : `More Delay • +${addedMs} ms added (~${metrics.estimatedMonitoringLatencyMs} ms est. latency)`;

  const statusToneClass =
    zone === MonitoringLatencyZone.FASTER
      ? 'text-[#22C55E]'
      : zone === MonitoringLatencyZone.DEFAULT
        ? 'text-[#00D4FF]'
        : 'text-[#F59E0B]';

  return (
    <span className={`text-xs font-medium font-mono-tabular ${statusToneClass}`}>
      {statusReadout}
    </span>
  );
});

MonitoringLatencyReadout.displayName = 'MonitoringLatencyReadout';

const AecNoiseGateTelemetryStrip: React.FC<{
  echoCancellationEnabled: boolean;
  noiseGateThresholdDb: number;
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
  onApplyRecommendedThreshold: (db: number) => void;
  onCalibrateBackgroundNoise: () => void;
}> = memo(
  ({
    echoCancellationEnabled,
    noiseGateThresholdDb,
    subscribeMetrics,
    getMetricsSnapshot,
    onApplyRecommendedThreshold,
    onCalibrateBackgroundNoise,
  }) => {
    const metrics = useLiveAudioMetrics(subscribeMetrics, getMetricsSnapshot);
    const calPct = Math.round(metrics.calibrationProgress * 100);

    return (
      <div className="flex flex-col gap-2 pt-2 border-t border-[#2C374D]">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <button
            type="button"
            disabled={metrics.isCalibratingNoise}
            onClick={onCalibrateBackgroundNoise}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-semibold cursor-pointer transition-colors ${
              metrics.isCalibratingNoise
                ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
                : 'bg-[#1A2130] border-[#00D4FF]/60 text-[#00D4FF] hover:bg-[#0E303D]/60'
            }`}
          >
            <Activity className="w-3.5 h-3.5" />
            <span>
              {metrics.isCalibratingNoise
                ? `Sampling Room Noise... ${calPct}%`
                : 'Calibrate Background Noise'}
            </span>
          </button>

          {metrics.calibrationRecommendationDb !== null &&
            metrics.calibrationRecommendationDb !== noiseGateThresholdDb && (
              <button
                type="button"
                onClick={() =>
                  onApplyRecommendedThreshold(metrics.calibrationRecommendationDb!)
                }
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#123222] border border-[#22C55E]/60 text-[11px] font-semibold text-[#22C55E] hover:brightness-110 cursor-pointer"
              >
                <Sparkles className="w-3 h-3" />
                <span>Apply Suggested {metrics.calibrationRecommendationDb} dBFS</span>
              </button>
            )}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 text-[11px] font-mono-tabular">
          <div className="rounded-lg bg-[#1A2130] border border-[#2C374D] px-2 py-1.5">
            <span className="block text-[10px] text-[#64748B] font-sans">RMS / Peak</span>
            <span className="text-[#F1F5F9]">
              {metrics.rmsDb.toFixed(1)} / {metrics.peakDb.toFixed(1)} dB
            </span>
          </div>

          <div className="rounded-lg bg-[#1A2130] border border-[#2C374D] px-2 py-1.5">
            <span className="block text-[10px] text-[#64748B] font-sans">Noise Floor</span>
            <span
              className={
                metrics.noiseFloorDetected ? 'text-[#00D4FF]' : 'text-[#64748B]'
              }
            >
              {metrics.noiseFloorDetected
                ? `${metrics.estimatedNoiseFloorDb.toFixed(1)} dBFS`
                : 'Not Sampled'}
            </span>
          </div>

          <div className="rounded-lg bg-[#1A2130] border border-[#2C374D] px-2 py-1.5">
            <span className="block text-[10px] text-[#64748B] font-sans">Selected Threshold</span>
            <span className="text-[#00D4FF]">{noiseGateThresholdDb} dBFS</span>
          </div>

          <div className="rounded-lg bg-[#1A2130] border border-[#2C374D] px-2 py-1.5">
            <span className="block text-[10px] text-[#64748B] font-sans">Gate Status</span>
            <span
              className={
                !echoCancellationEnabled
                  ? 'text-[#64748B]'
                  : metrics.aecGateOpen
                    ? 'text-[#22C55E] font-semibold'
                    : 'text-[#F59E0B] font-semibold'
              }
            >
              {!echoCancellationEnabled
                ? 'Bypassed'
                : metrics.aecGateOpen
                  ? 'Gate Open'
                  : 'Gate Closed'}
            </span>
          </div>
        </div>
      </div>
    );
  }
);

AecNoiseGateTelemetryStrip.displayName = 'AecNoiseGateTelemetryStrip';

export const VoiceChangerScreen: React.FC<VoiceChangerScreenProps> = memo(
  ({
    config,
    monitoringConfig,
    hardwareState,
    subscribeMetrics,
    getMetricsSnapshot,
    onEnabledChanged,
    onSelectPreset,
    onResetPreset,
    onPitchLevelChanged,
    onToneLevelChanged,
    onClarityChanged,
    onLivePreviewToggle,
    onMonitoringDelayChanged,
    onResetMonitoringDelay,
    onMonitoringVolumeChanged,
    onEchoCancellationToggle,
    onAecNoiseGateThresholdChanged,
    onCalibrateBackgroundNoise,
  }) => {
    const zone = getMonitoringZone(monitoringConfig.delaySliderPosition);
    const presets = Object.values(VoicePreset) as VoicePreset[];
    const aecGateTh = monitoringConfig.noiseGateThresholdDb;
    const aecOn = monitoringConfig.echoCancellationEnabled;

    return (
      <div className="w-full h-full overflow-y-auto flex justify-center">
        <div className="w-full max-w-[640px] lg:max-w-[1120px] xl:max-w-[1360px] px-4 py-3.5 flex flex-col gap-3">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 items-start">
            {/* LEFT COLUMN: Live Voice Preview, Delay, Volume & AEC + Adjustable Noise Gate */}
            <div
              className={`w-full rounded-2xl bg-[#1A2130] border p-3.5 flex flex-col gap-2.5 ${
                monitoringConfig.livePreviewEnabled ? 'border-[#00D4FF]' : 'border-[#2C374D]'
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2.5 min-w-0 flex-1">
                  <Headphones
                    className={`w-5 h-5 shrink-0 ${
                      monitoringConfig.livePreviewEnabled ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                    }`}
                  />
                  <div className="min-w-0">
                    <h3 className="font-display text-base font-semibold text-[#F1F5F9]">
                      Live Voice Preview
                    </h3>
                    <p
                      className={`text-sm truncate ${
                        monitoringConfig.livePreviewEnabled ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                      }`}
                    >
                      {monitoringConfig.livePreviewEnabled
                        ? 'Monitoring processed voice in real time'
                        : 'Real-time headphone monitoring is OFF'}
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  role="switch"
                  aria-checked={monitoringConfig.livePreviewEnabled}
                  aria-label="Live Voice Preview"
                  onClick={() => onLivePreviewToggle(!monitoringConfig.livePreviewEnabled)}
                  className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors cursor-pointer ${
                    monitoringConfig.livePreviewEnabled
                      ? 'bg-[#00D4FF] border-[#00D4FF]'
                      : 'bg-[#131822] border-[#2C374D]'
                  }`}
                >
                  <span
                    className={`inline-block h-5 w-5 rounded-full transition-transform ${
                      monitoringConfig.livePreviewEnabled
                        ? 'translate-x-6 bg-[#131822]'
                        : 'translate-x-1 bg-[#94A3B8]'
                    }`}
                  />
                </button>
              </div>

              {monitoringConfig.livePreviewEnabled &&
                !hardwareState.isWiredHeadphoneConnected && (
                  <div className="rounded-lg bg-[#3A280C] border border-[#F59E0B]/60 px-2.5 py-2 flex items-center gap-2">
                    <AlertTriangle className="w-[18px] h-[18px] text-[#F59E0B] shrink-0" />
                    <span className="text-xs text-[#F59E0B]">
                      {hardwareState.isBluetoothOutputConnected
                        ? 'Bluetooth route active — AEC & Noise Gate guard against echo bleed.'
                        : 'Connect wired headphones to prevent acoustic speaker feedback.'}
                    </span>
                  </div>
                )}

              {/* Monitoring Delay Adjustment Card */}
              <div className="rounded-xl bg-[#131822] border border-[#2C374D] p-3 flex flex-col gap-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-sm font-semibold text-[#F1F5F9]">
                    Monitoring Delay Adjustment
                  </span>
                  <button
                    type="button"
                    onClick={onResetMonitoringDelay}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-[#2C374D] bg-[#1A2130] text-[11px] font-medium text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>Reset to Default</span>
                  </button>
                </div>

                <MonitoringLatencyReadout
                  sliderPosition={monitoringConfig.delaySliderPosition}
                  subscribeMetrics={subscribeMetrics}
                  getMetricsSnapshot={getMetricsSnapshot}
                />

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      onMonitoringDelayChanged(
                        Math.max(
                          MonitoringConstants.MIN_SLIDER,
                          monitoringConfig.delaySliderPosition - MonitoringConstants.STEP_SLIDER
                        )
                      )
                    }
                    disabled={
                      monitoringConfig.delaySliderPosition <= MonitoringConstants.MIN_SLIDER + 0.001
                    }
                    aria-label="Decrease monitoring delay"
                    className="w-11 h-11 rounded-xl bg-[#222B3D] text-[#F1F5F9] flex items-center justify-center hover:bg-[#2C374D] disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer"
                  >
                    <Minus className="w-4 h-4" />
                  </button>

                  <input
                    type="range"
                    min={MonitoringConstants.MIN_SLIDER}
                    max={MonitoringConstants.MAX_SLIDER}
                    step={MonitoringConstants.STEP_SLIDER}
                    value={monitoringConfig.delaySliderPosition}
                    onChange={(e) => onMonitoringDelayChanged(Number(e.target.value))}
                    aria-label="Monitoring delay adjustment"
                    className="studio-slider flex-1"
                  />

                  <button
                    type="button"
                    onClick={() =>
                      onMonitoringDelayChanged(
                        Math.min(
                          MonitoringConstants.MAX_SLIDER,
                          monitoringConfig.delaySliderPosition + MonitoringConstants.STEP_SLIDER
                        )
                      )
                    }
                    disabled={
                      monitoringConfig.delaySliderPosition >= MonitoringConstants.MAX_SLIDER - 0.001
                    }
                    aria-label="Increase monitoring delay"
                    className="w-11 h-11 rounded-xl bg-[#222B3D] text-[#F1F5F9] flex items-center justify-center hover:bg-[#2C374D] disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer"
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                </div>

                <div className="flex items-center justify-between text-[11px]">
                  <span
                    className={
                      zone === MonitoringLatencyZone.FASTER
                        ? 'font-bold text-[#22C55E]'
                        : 'text-[#64748B]'
                    }
                  >
                    Faster
                  </span>
                  <span
                    className={
                      zone === MonitoringLatencyZone.DEFAULT
                        ? 'font-bold text-[#00D4FF]'
                        : 'text-[#64748B]'
                    }
                  >
                    Default
                  </span>
                  <span
                    className={
                      zone === MonitoringLatencyZone.MORE_DELAY
                        ? 'font-bold text-[#F59E0B]'
                        : 'text-[#64748B]'
                    }
                  >
                    More Delay
                  </span>
                </div>

                <p className="text-[11px] text-[#64748B]">
                  Applies to Live Voice Preview headphones only. WAV recording path stays zero-delay.
                </p>
              </div>

              {/* Preview Volume Boost (0-500%) */}
              <DspParameterSlider
                label="Preview Volume"
                valueText={`${monitoringConfig.previewVolumePercent}%`}
                value={monitoringConfig.previewVolumePercent}
                min={MonitoringConstants.MIN_VOLUME_PERCENT}
                max={MonitoringConstants.MAX_VOLUME_PERCENT}
                step={MonitoringConstants.STEP_VOLUME_PERCENT}
                minLabel="0% (Mute)"
                maxLabel="500% (Max Boost)"
                enabled
                accentColor="cyan"
                onChange={(v) => onMonitoringVolumeChanged(Math.round(v))}
              />

              {/* Acoustic Echo Cancellation (AEC) + Adjustable Decibel Noise Gate (-60 to -20 dBFS) */}
              <div className="rounded-xl bg-[#131822] border border-[#2C374D] px-3 py-3 flex flex-col gap-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-[#F1F5F9]">
                      Acoustic Echo Cancellation (AEC)
                    </p>
                    <p className="text-[11px] text-[#94A3B8]">
                      {hardwareState.aecStatusDetail}
                    </p>
                  </div>

                  <button
                    type="button"
                    role="switch"
                    aria-checked={aecOn}
                    aria-label="Acoustic Echo Cancellation"
                    onClick={() => onEchoCancellationToggle(!aecOn)}
                    className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors cursor-pointer ${
                      aecOn
                        ? 'bg-[#22C55E] border-[#22C55E]'
                        : 'bg-[#1A2130] border-[#2C374D]'
                    }`}
                  >
                    <span
                      className={`inline-block h-5 w-5 rounded-full transition-transform ${
                        aecOn ? 'translate-x-6 bg-[#131822]' : 'translate-x-1 bg-[#94A3B8]'
                      }`}
                    />
                  </button>
                </div>

                {/* Adjustable Decibel Noise Gate Threshold Control directly below AEC toggle */}
                <div className="pt-2 border-t border-[#2C374D] flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className={`text-sm font-semibold ${
                        aecOn ? 'text-[#F1F5F9]' : 'text-[#64748B]'
                      }`}
                    >
                      Noise Gate Threshold: {aecGateTh} dBFS
                    </span>
                    <button
                      type="button"
                      disabled={!aecOn}
                      onClick={() =>
                        onAecNoiseGateThresholdChanged(
                          MonitoringConstants.DEFAULT_AEC_GATE_THRESHOLD_DB
                        )
                      }
                      className="px-2 py-0.5 rounded-md bg-[#1A2130] border border-[#2C374D] text-[11px] text-[#94A3B8] hover:text-[#F1F5F9] disabled:opacity-40 cursor-pointer"
                    >
                      Default (-45)
                    </button>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() =>
                        onAecNoiseGateThresholdChanged(
                          Math.max(
                            MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB,
                            aecGateTh - MonitoringConstants.STEP_AEC_GATE_THRESHOLD_DB
                          )
                        )
                      }
                      disabled={
                        !aecOn ||
                        aecGateTh <= MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB
                      }
                      aria-label="Decrease Noise Gate Threshold"
                      className="w-11 h-11 shrink-0 rounded-xl bg-[#222B3D] text-[#F1F5F9] flex items-center justify-center hover:bg-[#2C374D] disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer"
                    >
                      <Minus className="w-4 h-4" />
                    </button>

                    <input
                      type="range"
                      min={MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB}
                      max={MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB}
                      step={MonitoringConstants.STEP_AEC_GATE_THRESHOLD_DB}
                      value={aecGateTh}
                      disabled={!aecOn}
                      onChange={(e) => onAecNoiseGateThresholdChanged(Number(e.target.value))}
                      aria-label="Noise Gate Threshold in dBFS"
                      className="studio-slider flex-1"
                    />

                    <button
                      type="button"
                      onClick={() =>
                        onAecNoiseGateThresholdChanged(
                          Math.min(
                            MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB,
                            aecGateTh + MonitoringConstants.STEP_AEC_GATE_THRESHOLD_DB
                          )
                        )
                      }
                      disabled={
                        !aecOn ||
                        aecGateTh >= MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB
                      }
                      aria-label="Increase Noise Gate Threshold"
                      className="w-11 h-11 shrink-0 rounded-xl bg-[#222B3D] text-[#F1F5F9] flex items-center justify-center hover:bg-[#2C374D] disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>

                  <div className="flex items-center justify-between text-[11px] font-mono-tabular text-[#64748B]">
                    <span>-60 dBFS (Sensitive)</span>
                    <span>-20 dBFS (Aggressive)</span>
                  </div>
                </div>

                {/* Live Audio Level, Noise Floor Detection & Calibration */}
                <AecNoiseGateTelemetryStrip
                  echoCancellationEnabled={aecOn}
                  noiseGateThresholdDb={aecGateTh}
                  subscribeMetrics={subscribeMetrics}
                  getMetricsSnapshot={getMetricsSnapshot}
                  onApplyRecommendedThreshold={onAecNoiseGateThresholdChanged}
                  onCalibrateBackgroundNoise={onCalibrateBackgroundNoise}
                />
              </div>
            </div>

            {/* RIGHT COLUMN: Voice Changer Module + Pitch / Tone / Clarity Sliders */}
            <div className="flex flex-col gap-3">
              <div
                className={`w-full rounded-2xl bg-[#1A2130] border p-3.5 flex flex-col gap-3 ${
                  config.enabled ? 'border-[#00D4FF]/70' : 'border-[#2C374D]'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0 flex-1">
                    <Sliders
                      className={`w-5 h-5 shrink-0 ${
                        config.enabled ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                      }`}
                    />
                    <div className="min-w-0">
                      <h3 className="font-display text-base font-semibold text-[#F1F5F9]">
                        Voice Changer
                      </h3>
                      <p
                        className={`text-sm truncate ${
                          config.enabled ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                        }`}
                      >
                        {config.enabled
                          ? `Active: ${VOICE_PRESET_LABELS[config.preset]} profile`
                          : 'OFF • Original microphone audio unchanged'}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <span
                      className={`text-[11px] font-bold tracking-wider ${
                        config.enabled ? 'text-[#22C55E]' : 'text-[#64748B]'
                      }`}
                    >
                      {config.enabled ? 'ON' : 'OFF'}
                    </span>
                    <button
                      type="button"
                      role="switch"
                      aria-checked={config.enabled}
                      aria-label="Voice Changer"
                      onClick={() => onEnabledChanged(!config.enabled)}
                      className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors cursor-pointer ${
                        config.enabled
                          ? 'bg-[#00D4FF] border-[#00D4FF]'
                          : 'bg-[#131822] border-[#2C374D]'
                      }`}
                    >
                      <span
                        className={`inline-block h-5 w-5 rounded-full transition-transform ${
                          config.enabled
                            ? 'translate-x-6 bg-[#131822]'
                            : 'translate-x-1 bg-[#94A3B8]'
                        }`}
                      />
                    </button>
                  </div>
                </div>

                {/* 5 Fixed Voice Presets: 3 + 2 grid */}
                <div className="flex flex-col gap-2">
                  <div className="grid grid-cols-3 gap-2">
                    {presets.slice(0, 3).map((preset) => (
                      <PresetButton
                        key={preset}
                        label={VOICE_PRESET_LABELS[preset]}
                        selected={config.preset === preset}
                        active={config.enabled}
                        onClick={() => onSelectPreset(preset)}
                      />
                    ))}
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    {presets.slice(3).map((preset) => (
                      <PresetButton
                        key={preset}
                        label={VOICE_PRESET_LABELS[preset]}
                        selected={config.preset === preset}
                        active={config.enabled}
                        onClick={() => onSelectPreset(preset)}
                      />
                    ))}
                  </div>
                </div>

                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] text-[#64748B]">
                    {config.enabled
                      ? 'Natural PSOLA + Formant vocal profile'
                      : 'Turn ON to apply selected voice profile'}
                  </span>
                  <button
                    type="button"
                    onClick={onResetPreset}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-[#2C374D] bg-[#131822] text-[11px] font-medium text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>Reset Preset</span>
                  </button>
                </div>
              </div>

              <DspParameterSlider
                label="Pitch Level"
                valueText={`${config.pitchLevelPercent}%`}
                value={config.pitchLevelPercent}
                min={VoiceChangerConstants.MIN_LEVEL_PCT}
                max={VoiceChangerConstants.MAX_LEVEL_PCT}
                step={VoiceChangerConstants.STEP_LEVEL_PCT}
                minLabel="0% (No Pitch Shift)"
                maxLabel="100% (Full Voice)"
                enabled={config.enabled}
                accentColor="cyan"
                onChange={(v) => onPitchLevelChanged(Math.round(v))}
              />

              <DspParameterSlider
                label="Tone Level"
                valueText={`${config.toneLevelPercent}%`}
                value={config.toneLevelPercent}
                min={VoiceChangerConstants.MIN_LEVEL_PCT}
                max={VoiceChangerConstants.MAX_LEVEL_PCT}
                step={VoiceChangerConstants.STEP_LEVEL_PCT}
                minLabel="0% (Original Tone)"
                maxLabel="100% (Full Tone)"
                enabled={config.enabled}
                accentColor="cyan"
                onChange={(v) => onToneLevelChanged(Math.round(v))}
              />

              <DspParameterSlider
                label="Clarity"
                valueText={`${config.clarityPercent}%`}
                value={config.clarityPercent}
                min={VoiceChangerConstants.MIN_LEVEL_PCT}
                max={VoiceChangerConstants.MAX_LEVEL_PCT}
                step={VoiceChangerConstants.STEP_LEVEL_PCT}
                minLabel="0% (Natural)"
                maxLabel="100% (High Clarity)"
                enabled={config.enabled}
                accentColor="cyan"
                onChange={(v) => onClarityChanged(Math.round(v))}
              />
            </div>
          </div>

          <div className="h-2" />
        </div>
      </div>
    );
  }
);

VoiceChangerScreen.displayName = 'VoiceChangerScreen';

const PresetButton: React.FC<{
  label: string;
  selected: boolean;
  active: boolean;
  onClick: () => void;
}> = memo(({ label, selected, active, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    className={`h-12 rounded-[10px] border text-sm font-medium flex items-center justify-center cursor-pointer transition-colors ${
      selected && active
        ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
        : selected
          ? 'bg-[#222B3D] border-[#94A3B8] text-[#F1F5F9]'
          : 'bg-[#131822] border-[#2C374D] text-[#94A3B8] hover:text-[#F1F5F9]'
    }`}
  >
    {label}
  </button>
));

PresetButton.displayName = 'PresetButton';
