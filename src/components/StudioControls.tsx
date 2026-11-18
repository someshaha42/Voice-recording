import React, { memo } from 'react';
import {
  Activity,
  AlertTriangle,
  Bluetooth,
  Headphones,
  Mic,
  Minus,
  Plus,
  ShieldCheck,
  Usb,
} from 'lucide-react';
import {
  MetricsSnapshotFn,
  MetricsSubscribeFn,
  useLiveAudioMetrics,
} from '../hooks/useVoxForge';
import { AudioHardwareState } from '../types/audio';

interface DspParameterSliderProps {
  label: string;
  valueText: string;
  value: number;
  min: number;
  max: number;
  step: number;
  minLabel: string;
  maxLabel: string;
  enabled?: boolean;
  accentColor?: 'cyan' | 'emerald' | 'amber' | 'coral';
  onChange: (val: number) => void;
}

export const DspParameterSlider: React.FC<DspParameterSliderProps> = memo(
  ({
    label,
    valueText,
    value,
    min,
    max,
    step,
    minLabel,
    maxLabel,
    enabled = true,
    accentColor = 'cyan',
    onChange,
  }) => {
    const accentTextClass =
      accentColor === 'emerald'
        ? 'text-[#22C55E]'
        : accentColor === 'amber'
          ? 'text-[#F59E0B]'
          : accentColor === 'coral'
            ? 'text-[#FF4D4D]'
            : 'text-[#00D4FF]';

    const clamped = Math.max(min, Math.min(max, value));

    return (
      <div className="w-full rounded-xl bg-[#1A2130] border border-[#2C374D] px-3.5 py-3">
        <div className="flex items-center justify-between gap-2">
          <span className={`text-sm font-semibold ${enabled ? 'text-[#F1F5F9]' : 'text-[#64748B]'}`}>
            {label}
          </span>
          <span
            className={`px-2 py-1 rounded-md bg-[#131822] border border-[#2C374D] font-mono-tabular text-xs font-medium ${
              enabled ? accentTextClass : 'text-[#64748B]'
            }`}
          >
            {valueText}
          </span>
        </div>

        <div className="mt-2 flex items-center gap-2">
          <button
            type="button"
            onClick={() => onChange(Math.max(min, Number((clamped - step).toFixed(4))))}
            disabled={!enabled || clamped <= min + 0.0001}
            aria-label={`Decrease ${label}`}
            className="w-11 h-11 shrink-0 rounded-xl bg-[#222B3D] text-[#F1F5F9] flex items-center justify-center hover:bg-[#2C374D] disabled:opacity-35 disabled:cursor-not-allowed transition-colors cursor-pointer"
          >
            <Minus className="w-4 h-4" />
          </button>

          <input
            type="range"
            min={min}
            max={max}
            step={step}
            value={clamped}
            disabled={!enabled}
            onChange={(e) => onChange(Number(e.target.value))}
            className="studio-slider flex-1 disabled:opacity-40"
            aria-label={label}
          />

          <button
            type="button"
            onClick={() => onChange(Math.min(max, Number((clamped + step).toFixed(4))))}
            disabled={!enabled || clamped >= max - 0.0001}
            aria-label={`Increase ${label}`}
            className="w-11 h-11 shrink-0 rounded-xl bg-[#222B3D] text-[#F1F5F9] flex items-center justify-center hover:bg-[#2C374D] disabled:opacity-35 disabled:cursor-not-allowed transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4" />
          </button>
        </div>

        <div className="mt-1 flex items-center justify-between text-[11px] text-[#64748B]">
          <span>{minLabel}</span>
          <span>{maxLabel}</span>
        </div>
      </div>
    );
  }
);

DspParameterSlider.displayName = 'DspParameterSlider';

interface StudioLevelMeterCardProps {
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
  echoCancellationEnabled: boolean;
  noiseGateThresholdDb: number;
  onResetClips: () => void;
  onToggleMonitorInput: () => void;
}

export const StudioLevelMeterCard: React.FC<StudioLevelMeterCardProps> = memo(
  ({
    subscribeMetrics,
    getMetricsSnapshot,
    echoCancellationEnabled,
    noiseGateThresholdDb,
    onResetClips,
    onToggleMonitorInput,
  }) => {
    const metrics = useLiveAudioMetrics(subscribeMetrics, getMetricsSnapshot);

    const normRms = Math.max(0, Math.min(1, (metrics.rmsDb + 60) / 60));
    const normPeak = Math.max(0, Math.min(1, (metrics.peakDb + 60) / 60));
    const thresholdNorm = Math.max(0, Math.min(1, (noiseGateThresholdDb + 60) / 60));
    const segments = 30;
    const peakSeg = Math.max(0, Math.min(segments - 1, Math.floor(normPeak * segments)));
    const hasClips = metrics.isClipping || metrics.clipCount > 0;

    return (
      <div className="w-full rounded-xl bg-[#1A2130] border border-[#2C374D] p-3.5 flex flex-col gap-2.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-semibold tracking-wider text-[#94A3B8]">
              LIVE INPUT LEVEL
            </span>
            <button
              type="button"
              onClick={onToggleMonitorInput}
              title="Toggle idle microphone level monitoring (saves CPU/RAM when OFF)"
              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md border text-[11px] font-semibold cursor-pointer transition-colors ${
                metrics.isInputMonitoringActive
                  ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
                  : 'bg-[#131822] border-[#2C374D] text-[#94A3B8] hover:text-[#F1F5F9]'
              }`}
            >
              <Activity className="w-3 h-3" />
              <span>{metrics.isInputMonitoringActive ? 'Monitor Input: ON' : 'Monitor Input'}</span>
            </button>
          </div>

          <div className="flex items-center gap-2">
            <span className="font-mono-tabular text-xs font-medium text-[#F1F5F9]">
              RMS {metrics.rmsDb.toFixed(1)} dB • PK {metrics.peakDb.toFixed(1)} dB
            </span>
            <button
              type="button"
              onClick={onResetClips}
              title="Click to reset clip counter"
              className={`px-2 py-1 rounded-md border text-[11px] font-bold tracking-wide cursor-pointer transition-colors ${
                hasClips
                  ? 'bg-[#3E151B] border-[#FF4D4D] text-[#FF4D4D]'
                  : 'bg-[#131822] border-[#2C374D] text-[#64748B] hover:text-[#94A3B8]'
              }`}
            >
              {metrics.clipCount > 0 ? `CLIP ${metrics.clipCount}` : 'CLIP'}
            </button>
          </div>
        </div>

        {/* Segmented 30-bar precision dBFS meter (-60 dB to 0 dB) with AEC Gate Threshold Marker */}
        <div className="relative w-full h-[22px]">
          <div
            className="w-full h-full grid grid-cols-[repeat(30,minmax(0,1fr))] gap-[3px]"
            aria-label="Live input dBFS meter"
          >
            {Array.from({ length: segments }).map((_, i) => {
              const threshold = (i + 1) / segments;
              const lit = normRms >= threshold - (1 / segments) * 0.5;
              const isPeakHold = normPeak > 0.04 && i === peakSeg;

              let bg = '#1F2737';
              if (lit || isPeakHold) {
                if (i >= 26) bg = '#FF4D4D';
                else if (i >= 20) bg = '#F59E0B';
                else bg = '#22C55E';
              }

              return <div key={i} className="h-full rounded-[2px]" style={{ backgroundColor: bg }} />;
            })}
          </div>
          {echoCancellationEnabled && (
            <div
              className="pointer-events-none absolute top-[-2px] bottom-[-2px] w-[2px] bg-[#00D4FF] shadow-[0_0_6px_rgba(0,212,255,0.85)]"
              style={{ left: `${(thresholdNorm * 100).toFixed(1)}%` }}
              title={`AEC Noise Gate Threshold: ${noiseGateThresholdDb} dBFS`}
            />
          )}
        </div>

        <div className="flex items-center justify-between text-[11px] font-mono-tabular text-[#64748B]">
          {['-60', '-42', '-24', '-12', '-6', '0 dB'].map((mark) => (
            <span key={mark}>{mark}</span>
          ))}
        </div>

        {/* Noise Floor & Gate Status Strip */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-[#2C374D]/80 text-[11px]">
          <div className="flex flex-wrap items-center gap-1.5">
            {echoCancellationEnabled ? (
              <span
                className={`px-2 py-0.5 rounded-md border font-semibold ${
                  metrics.aecGateOpen
                    ? 'bg-[#123222] border-[#22C55E]/50 text-[#22C55E]'
                    : 'bg-[#3A280C] border-[#F59E0B]/50 text-[#F59E0B]'
                }`}
              >
                {metrics.aecGateOpen ? 'Gate Open' : 'Gate Closed'}
              </span>
            ) : (
              <span className="px-2 py-0.5 rounded-md border bg-[#131822] border-[#2C374D] text-[#64748B] font-semibold">
                AEC Gate Bypassed
              </span>
            )}

            <span
              className={`px-2 py-0.5 rounded-md border font-mono-tabular ${
                metrics.noiseFloorDetected
                  ? 'bg-[#0E303D]/60 border-[#00D4FF]/40 text-[#00D4FF]'
                  : 'bg-[#131822] border-[#2C374D] text-[#64748B]'
              }`}
            >
              {metrics.noiseFloorDetected
                ? `Noise Floor Detected: ${metrics.estimatedNoiseFloorDb.toFixed(1)} dBFS`
                : 'Noise Floor: Standby'}
            </span>
          </div>

          <span className="font-mono-tabular text-[#94A3B8]">
            Gate Threshold: {noiseGateThresholdDb} dBFS
          </span>
        </div>
      </div>
    );
  }
);

StudioLevelMeterCard.displayName = 'StudioLevelMeterCard';

interface HardwareStatusCardProps {
  hardwareState: AudioHardwareState;
  onRefreshDevices: () => void;
  onSelectInputDevice?: (deviceId: string | null) => void;
  onSelectOutputDevice?: (deviceId: string | null) => void;
}

export const HardwareStatusCard: React.FC<HardwareStatusCardProps> = memo(
  ({ hardwareState, onRefreshDevices, onSelectInputDevice, onSelectOutputDevice }) => {
    const rateText = hardwareState.isResamplingActive
      ? `Capture ${hardwareState.activeInputSampleRateHz} Hz → Resampled 48,000 Hz`
      : `Capture ${hardwareState.activeInputSampleRateHz} Hz • Native 48 kHz`;

    const isBluetooth =
      hardwareState.isBluetoothInputConnected || hardwareState.isBluetoothOutputConnected;

    const aecBadgeTone =
      hardwareState.aecRouteStatus === 'ACTIVE_HW_AND_DSP'
        ? 'text-[#22C55E]'
        : hardwareState.aecRouteStatus === 'ACTIVE_DSP_ONLY'
          ? 'text-[#00D4FF]'
          : hardwareState.aecRouteStatus === 'STANDBY'
            ? 'text-[#94A3B8]'
            : 'text-[#64748B]';

    return (
      <div
        className={`w-full rounded-xl bg-[#1A2130] border p-3.5 flex flex-col gap-2 ${
          hardwareState.isUsbMicConnected
            ? 'border-[#00D4FF]/65'
            : isBluetooth
              ? 'border-[#00D4FF]/40'
              : 'border-[#2C374D]'
        }`}
      >
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0 flex-1">
            <div
              className={`w-9 h-9 rounded-full shrink-0 flex items-center justify-center ${
                hardwareState.isUsbMicConnected || isBluetooth
                  ? 'bg-[#0E303D] text-[#00D4FF]'
                  : 'bg-[#131822] text-[#94A3B8]'
              }`}
            >
              {hardwareState.isUsbMicConnected ? (
                <Usb className="w-[18px] h-[18px]" />
              ) : isBluetooth ? (
                <Bluetooth className="w-[18px] h-[18px]" />
              ) : (
                <Mic className="w-[18px] h-[18px]" />
              )}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-[#F1F5F9] truncate">
                {hardwareState.inputDeviceName}
              </p>
              <p
                className={`text-[11px] font-medium truncate ${
                  hardwareState.isUsbMicConnected ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                }`}
              >
                {rateText}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            {hardwareState.bluetoothRouteType !== 'NONE' && (
              <span className="px-2 py-1 rounded-lg bg-[#0E303D] border border-[#00D4FF]/40 text-[10px] font-bold tracking-wider text-[#00D4FF]">
                {hardwareState.bluetoothRouteType === 'HFP_HSP' ? 'BT HFP/HSP' : 'BT A2DP'}
              </span>
            )}
            <button
              type="button"
              onClick={onRefreshDevices}
              title="Click to refresh connected audio devices"
              className={`px-2.5 py-1.5 rounded-lg text-[11px] font-bold tracking-wider shrink-0 cursor-pointer transition-colors ${
                hardwareState.isUsbMicConnected
                  ? 'bg-[#123222] text-[#22C55E] border border-[#22C55E]/40'
                  : 'bg-[#131822] text-[#94A3B8] border border-[#2C374D] hover:text-[#F1F5F9]'
              }`}
            >
              {hardwareState.isUsbMicConnected ? 'USB-C READY' : 'BUILT-IN'}
            </button>
          </div>
        </div>

        {hardwareState.availableInputs.length > 1 && onSelectInputDevice && (
          <div className="flex items-center gap-2 pt-0.5">
            <span className="text-[11px] text-[#64748B] shrink-0">Input Source:</span>
            <select
              value={hardwareState.inputDeviceId ?? ''}
              onChange={(e) => onSelectInputDevice(e.target.value || null)}
              className="flex-1 min-w-0 rounded-md bg-[#131822] border border-[#2C374D] text-xs text-[#F1F5F9] px-2 py-1 focus:outline-none focus:border-[#00D4FF]"
            >
              {hardwareState.availableInputs.map((dev) => (
                <option key={dev.deviceId} value={dev.deviceId}>
                  {dev.label}
                </option>
              ))}
            </select>
          </div>
        )}

        {hardwareState.supportsOutputSinkRouting &&
          hardwareState.availableOutputs.length > 1 &&
          onSelectOutputDevice && (
            <div className="flex items-center gap-2">
              <span className="text-[11px] text-[#64748B] shrink-0">Output Route:</span>
              <select
                value={hardwareState.outputDeviceId ?? ''}
                onChange={(e) => onSelectOutputDevice(e.target.value || null)}
                className="flex-1 min-w-0 rounded-md bg-[#131822] border border-[#2C374D] text-xs text-[#F1F5F9] px-2 py-1 focus:outline-none focus:border-[#00D4FF]"
              >
                {hardwareState.availableOutputs.map((dev) => (
                  <option key={dev.deviceId} value={dev.deviceId}>
                    {dev.label}
                  </option>
                ))}
              </select>
            </div>
          )}

        <div className="flex items-center gap-1.5 min-w-0">
          {hardwareState.isWiredHeadphoneConnected ? (
            <Headphones className="w-3.5 h-3.5 shrink-0 text-[#22C55E]" />
          ) : hardwareState.isBluetoothOutputConnected ? (
            <Bluetooth className="w-3.5 h-3.5 shrink-0 text-[#00D4FF]" />
          ) : (
            <AlertTriangle className="w-3.5 h-3.5 shrink-0 text-[#F59E0B]" />
          )}
          <span
            className={`text-[11px] truncate ${
              hardwareState.isWiredHeadphoneConnected
                ? 'text-[#22C55E]'
                : hardwareState.isBluetoothOutputConnected
                  ? 'text-[#00D4FF]'
                  : 'text-[#F59E0B]'
            }`}
          >
            Monitor Out: {hardwareState.outputDeviceName}
          </span>
        </div>

        <div className="flex items-center gap-1.5 min-w-0">
          <ShieldCheck className={`w-3.5 h-3.5 shrink-0 ${aecBadgeTone}`} />
          <span className={`text-[11px] truncate ${aecBadgeTone}`}>
            {hardwareState.aecStatusDetail}
          </span>
        </div>
      </div>
    );
  }
);

HardwareStatusCard.displayName = 'HardwareStatusCard';
