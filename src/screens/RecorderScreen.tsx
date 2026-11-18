import React, { memo, useState } from 'react';
import {
  Circle,
  FileAudio,
  Mic,
  Pause,
  Pencil,
  Play,
  Share2,
  Square,
  Trash2,
  Waves,
} from 'lucide-react';
import { ImportedAudioStudioCard } from '../components/ImportAudioModal';
import { HardwareStatusCard, StudioLevelMeterCard } from '../components/StudioControls';
import { StudioEffectsControls } from '../components/StudioEffectsControls';
import {
  MetricsSnapshotFn,
  MetricsSubscribeFn,
  useLiveAudioMetrics,
} from '../hooks/useVoxForge';
import {
  AudioConfig,
  AudioHardwareState,
  CompressionConfig,
  DeEsserConfig,
  EchoConfig,
  EffectsRackConfig,
  EqualizerConfig,
  getActiveEffectCount,
  ImportedPlaybackState,
  LimiterConfig,
  MonitoringConfig,
  NoiseGateConfig,
  PitchCorrectionConfig,
  PitchFormantConfig,
  PlaybackUiState,
  RecordingItem,
  RecordingStatus,
  ReverbConfig,
  SaturationConfig,
  StudioEffectPreset,
  StudioEffectType,
  VOICE_PRESET_LABELS,
  VoiceChangerConfig,
} from '../types/audio';
import { formatShortDuration, formatTimerMs } from '../utils/format';

interface RecorderScreenProps {
  hasMicPermission: boolean;
  hardwareState: AudioHardwareState;
  recordingStatus: RecordingStatus;
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
  voiceChangerConfig: VoiceChangerConfig;
  effectsConfig: EffectsRackConfig;
  userEffectPresets: StudioEffectPreset[];
  monitoringConfig: MonitoringConfig;
  recordings: RecordingItem[];
  playbackState: PlaybackUiState;
  importedPlaybackState: ImportedPlaybackState;
  onRequestMicPermission: () => void;
  onRefreshDevices: () => void;
  onSelectInputDevice: (deviceId: string | null) => void;
  onSelectOutputDevice: (deviceId: string | null) => void;
  onStartRecording: () => void;
  onPauseRecording: () => void;
  onResumeRecording: () => void;
  onStopRecording: () => void;
  onResetClips: () => void;
  onToggleMonitorInput: () => void;
  onOpenImportAudio: () => void;
  onToggleImportedPlayPause: () => void;
  onSeekImportedPlayback: (ms: number) => void;
  onClearImportedAudio: () => void;
  onSaveAndShareImportedAudio: () => void;
  onPitchCorrectionChanged: (c: PitchCorrectionConfig) => void;
  onPitchFormantChanged: (c: PitchFormantConfig) => void;
  onEqualizerChanged: (c: EqualizerConfig) => void;
  onCompressionChanged: (c: CompressionConfig) => void;
  onSaturationChanged: (c: SaturationConfig) => void;
  onReverbChanged: (c: ReverbConfig) => void;
  onDelayChanged: (c: EchoConfig) => void;
  onNoiseGateChanged: (c: NoiseGateConfig) => void;
  onDeEsserChanged: (c: DeEsserConfig) => void;
  onLimiterChanged: (c: LimiterConfig) => void;
  onResetStudioEffect: (type: StudioEffectType) => void;
  onSaveStudioPreset: (type: StudioEffectType, name: string) => void;
  onLoadStudioPreset: (preset: StudioEffectPreset) => void;
  onDeleteUserPreset: (id: string) => void;
  onTogglePlayback: (item: RecordingItem) => void;
  onSeekPlayback: (ms: number) => void;
  onRenameRecording: (item: RecordingItem, newName: string) => void;
  onDeleteRecording: (item: RecordingItem) => void;
  onShareRecording: (item: RecordingItem) => void;
}

const TransportTimer: React.FC<{
  recordingStatus: RecordingStatus;
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
}> = memo(({ recordingStatus, subscribeMetrics, getMetricsSnapshot }) => {
  const metrics = useLiveAudioMetrics(subscribeMetrics, getMetricsSnapshot);
  return (
    <div
      className={`font-mono-tabular text-4xl font-semibold tracking-tight ${
        recordingStatus === RecordingStatus.RECORDING ? 'text-[#F1F5F9]' : 'text-[#94A3B8]'
      }`}
    >
      {formatTimerMs(metrics.recordingDurationMs)}
    </div>
  );
});

TransportTimer.displayName = 'TransportTimer';

export const RecorderScreen: React.FC<RecorderScreenProps> = memo(
  ({
    hasMicPermission,
    hardwareState,
    recordingStatus,
    subscribeMetrics,
    getMetricsSnapshot,
    voiceChangerConfig,
    effectsConfig,
    userEffectPresets,
    monitoringConfig,
    recordings,
    playbackState,
    importedPlaybackState,
    onRequestMicPermission,
    onRefreshDevices,
    onSelectInputDevice,
    onSelectOutputDevice,
    onStartRecording,
    onPauseRecording,
    onResumeRecording,
    onStopRecording,
    onResetClips,
    onToggleMonitorInput,
    onOpenImportAudio,
    onToggleImportedPlayPause,
    onSeekImportedPlayback,
    onClearImportedAudio,
    onSaveAndShareImportedAudio,
    onPitchCorrectionChanged,
    onPitchFormantChanged,
    onEqualizerChanged,
    onCompressionChanged,
    onSaturationChanged,
    onReverbChanged,
    onDelayChanged,
    onNoiseGateChanged,
    onDeEsserChanged,
    onLimiterChanged,
    onResetStudioEffect,
    onSaveStudioPreset,
    onLoadStudioPreset,
    onDeleteUserPreset,
    onTogglePlayback,
    onSeekPlayback,
    onRenameRecording,
    onDeleteRecording,
    onShareRecording,
  }) => {
    const [renameTarget, setRenameTarget] = useState<RecordingItem | null>(null);
    const [renameInput, setRenameInput] = useState('');
    const [deleteTarget, setDeleteTarget] = useState<RecordingItem | null>(null);

    return (
      <div className="w-full h-full overflow-y-auto flex justify-center">
        <div className="w-full max-w-[640px] lg:max-w-[1120px] xl:max-w-[1360px] px-4 py-3.5 flex flex-col gap-3">
          {!hasMicPermission && (
            <div className="w-full rounded-xl bg-[#3E151B] border border-[#FF4D4D] p-3.5 flex flex-col gap-2.5">
              <h3 className="font-display text-base font-semibold text-[#F1F5F9]">
                Microphone Access Required
              </h3>
              <p className="text-sm text-[#94A3B8]">
                VoxForge needs microphone permission to capture 48 kHz mono studio recordings and
                power real-time voice monitoring.
              </p>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={onRequestMicPermission}
                  className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-[#FF4D4D] text-[#F1F5F9] text-sm font-semibold hover:brightness-110 cursor-pointer transition"
                >
                  <Mic className="w-[18px] h-[18px]" />
                  <span>Grant Microphone Permission</span>
                </button>
              </div>
            </div>
          )}

          {/* Adaptive Workbench: single column on mobile, balanced 2-col on Tablet/TV */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 items-start">
            <div className="lg:col-span-7 flex flex-col gap-3">
              <TransportConsoleCard
                hasMicPermission={hasMicPermission}
                recordingStatus={recordingStatus}
                subscribeMetrics={subscribeMetrics}
                getMetricsSnapshot={getMetricsSnapshot}
                voiceChangerConfig={voiceChangerConfig}
                effectsConfig={effectsConfig}
                monitoringConfig={monitoringConfig}
                onRequestMicPermission={onRequestMicPermission}
                onStartRecording={onStartRecording}
                onOpenImportAudio={onOpenImportAudio}
                onPauseRecording={onPauseRecording}
                onResumeRecording={onResumeRecording}
                onStopRecording={onStopRecording}
              />

              {(importedPlaybackState.source !== null || importedPlaybackState.isImporting) && (
                <ImportedAudioStudioCard
                  playbackState={importedPlaybackState}
                  onTogglePlayPause={onToggleImportedPlayPause}
                  onSeek={onSeekImportedPlayback}
                  onChangeSource={onOpenImportAudio}
                  onClearSource={onClearImportedAudio}
                  onSaveAndExport={onSaveAndShareImportedAudio}
                />
              )}
            </div>

            <div className="lg:col-span-5 flex flex-col gap-3">
              <HardwareStatusCard
                hardwareState={hardwareState}
                onRefreshDevices={onRefreshDevices}
                onSelectInputDevice={onSelectInputDevice}
                onSelectOutputDevice={onSelectOutputDevice}
              />

              <StudioLevelMeterCard
                subscribeMetrics={subscribeMetrics}
                getMetricsSnapshot={getMetricsSnapshot}
                echoCancellationEnabled={monitoringConfig.echoCancellationEnabled}
                noiseGateThresholdDb={monitoringConfig.noiseGateThresholdDb}
                onResetClips={onResetClips}
                onToggleMonitorInput={onToggleMonitorInput}
              />
            </div>
          </div>

          <StudioEffectsControls
            effectsConfig={effectsConfig}
            userPresets={userEffectPresets}
            onPitchCorrectionChanged={onPitchCorrectionChanged}
            onPitchFormantChanged={onPitchFormantChanged}
            onEqualizerChanged={onEqualizerChanged}
            onCompressionChanged={onCompressionChanged}
            onSaturationChanged={onSaturationChanged}
            onReverbChanged={onReverbChanged}
            onDelayChanged={onDelayChanged}
            onNoiseGateChanged={onNoiseGateChanged}
            onDeEsserChanged={onDeEsserChanged}
            onLimiterChanged={onLimiterChanged}
            onResetEffect={onResetStudioEffect}
            onSavePreset={onSaveStudioPreset}
            onLoadPreset={onLoadStudioPreset}
            onDeleteUserPreset={onDeleteUserPreset}
          />

          {/* Recorded Voiceovers Header */}
          <div className="flex items-center justify-between pt-2">
            <span className="text-[11px] font-semibold tracking-wider text-[#94A3B8]">
              RECORDED VOICEOVERS ({recordings.length})
            </span>
            <span className="text-[11px] font-mono-tabular text-[#64748B]">48 kHz WAV PCM</span>
          </div>

          {recordings.length === 0 ? (
            <div className="w-full rounded-xl bg-[#1A2130] border border-[#2C374D] p-6 flex flex-col items-center text-center gap-2">
              <Waves className="w-8 h-8 text-[#00D4FF]" />
              <h3 className="font-display text-base font-semibold text-[#F1F5F9]">
                No Voiceovers Recorded Yet
              </h3>
              <p className="text-sm text-[#94A3B8]">
                Configure your Effects and Voice Changer tabs, connect headphones for Live Voice
                Preview, and press Start Recording.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-2.5 pb-4">
              {recordings.map((item) => {
                const isCurrent = playbackState.activeId === item.id;
                return (
                  <RecordingRowCard
                    key={item.id}
                    item={item}
                    isCurrent={isCurrent}
                    isPlaying={isCurrent && playbackState.isPlaying}
                    posMs={isCurrent ? playbackState.positionMs : 0}
                    durMs={
                      isCurrent && playbackState.durationMs > 0
                        ? playbackState.durationMs
                        : item.durationMs
                    }
                    onTogglePlayback={onTogglePlayback}
                    onSeekPlayback={onSeekPlayback}
                    onShareRecording={onShareRecording}
                    onStartRename={(target) => {
                      setRenameTarget(target);
                      setRenameInput(target.fileName.replace(/\.wav$/i, ''));
                    }}
                    onStartDelete={setDeleteTarget}
                  />
                );
              })}
            </div>
          )}
        </div>

        {/* Rename Modal */}
        {renameTarget && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
            role="dialog"
            aria-modal="true"
            aria-label="Rename Recording"
          >
            <div className="w-full max-w-sm rounded-2xl bg-[#1A2130] border border-[#2C374D] p-5 flex flex-col gap-4 shadow-2xl">
              <h3 className="font-display text-lg font-semibold text-[#F1F5F9]">Rename Recording</h3>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs text-[#94A3B8]" htmlFor="rename-wav-input">
                  File name (.wav)
                </label>
                <input
                  id="rename-wav-input"
                  type="text"
                  autoFocus
                  value={renameInput}
                  onChange={(e) => setRenameInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && renameInput.trim()) {
                      onRenameRecording(renameTarget, renameInput);
                      setRenameTarget(null);
                    }
                  }}
                  className="w-full rounded-lg bg-[#131822] border border-[#2C374D] px-3 py-2 text-sm text-[#F1F5F9] focus:outline-none focus:border-[#00D4FF]"
                />
              </div>
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setRenameTarget(null)}
                  className="px-3.5 py-2 rounded-lg text-sm text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={!renameInput.trim()}
                  onClick={() => {
                    onRenameRecording(renameTarget, renameInput);
                    setRenameTarget(null);
                  }}
                  className="px-4 py-2 rounded-lg bg-[#00D4FF] text-[#131822] text-sm font-semibold disabled:opacity-40 cursor-pointer"
                >
                  Save
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Delete Confirmation Modal */}
        {deleteTarget && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
            role="dialog"
            aria-modal="true"
            aria-label="Delete Recording"
          >
            <div className="w-full max-w-sm rounded-2xl bg-[#1A2130] border border-[#2C374D] p-5 flex flex-col gap-4 shadow-2xl">
              <h3 className="font-display text-lg font-semibold text-[#F1F5F9]">
                Delete Recording?
              </h3>
              <p className="text-sm text-[#94A3B8]">
                Remove &ldquo;{deleteTarget.fileName}&rdquo; permanently from device storage?
              </p>
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setDeleteTarget(null)}
                  className="px-3.5 py-2 rounded-lg text-sm text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => {
                    onDeleteRecording(deleteTarget);
                    setDeleteTarget(null);
                  }}
                  className="px-4 py-2 rounded-lg bg-[#FF4D4D] text-[#F1F5F9] text-sm font-semibold cursor-pointer"
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
);

RecorderScreen.displayName = 'RecorderScreen';

interface RecordingRowCardProps {
  item: RecordingItem;
  isCurrent: boolean;
  isPlaying: boolean;
  posMs: number;
  durMs: number;
  onTogglePlayback: (item: RecordingItem) => void;
  onSeekPlayback: (ms: number) => void;
  onShareRecording: (item: RecordingItem) => void;
  onStartRename: (item: RecordingItem) => void;
  onStartDelete: (item: RecordingItem) => void;
}

const RecordingRowCard: React.FC<RecordingRowCardProps> = memo(
  ({
    item,
    isCurrent,
    isPlaying,
    posMs,
    durMs,
    onTogglePlayback,
    onSeekPlayback,
    onShareRecording,
    onStartRename,
    onStartDelete,
  }) => {
    const sizeKb = Math.max(1, Math.round(item.sizeBytes / 1024));
    const dateStr = new Date(item.createdAtMs).toLocaleString([], {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });

    return (
      <div
        className={`perf-card w-full rounded-xl bg-[#1A2130] border p-3 flex flex-col gap-2 ${
          isCurrent ? 'border-[#00D4FF]' : 'border-[#2C374D]'
        }`}
      >
        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={() => onTogglePlayback(item)}
            aria-label={isPlaying ? `Pause ${item.fileName}` : `Play ${item.fileName}`}
            className={`w-12 h-12 rounded-full shrink-0 flex items-center justify-center cursor-pointer transition-colors ${
              isPlaying
                ? 'bg-[#0E303D] text-[#00D4FF]'
                : 'bg-[#131822] text-[#00D4FF] hover:bg-[#222B3D]'
            }`}
          >
            {isPlaying ? <Pause className="w-5 h-5" /> : <Play className="w-5 h-5 ml-0.5" />}
          </button>

          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-[#F1F5F9] truncate">{item.fileName}</p>
            <p className="text-[11px] font-mono-tabular text-[#94A3B8] truncate">
              {formatShortDuration(item.durationMs)} • {sizeKb} KB • {dateStr}
            </p>
            {item.wasRecovered && (
              <p className="text-[11px] text-[#22C55E]">Recovered from interruption</p>
            )}
          </div>

          <div className="flex items-center shrink-0">
            <button
              type="button"
              onClick={() => onStartRename(item)}
              aria-label={`Rename ${item.fileName}`}
              className="w-10 h-10 rounded-lg flex items-center justify-center text-[#94A3B8] hover:text-[#F1F5F9] hover:bg-[#222B3D] cursor-pointer"
            >
              <Pencil className="w-[18px] h-[18px]" />
            </button>
            <button
              type="button"
              onClick={() => onShareRecording(item)}
              aria-label={`Share or download ${item.fileName}`}
              className="w-10 h-10 rounded-lg flex items-center justify-center text-[#94A3B8] hover:text-[#00D4FF] hover:bg-[#222B3D] cursor-pointer"
            >
              <Share2 className="w-[18px] h-[18px]" />
            </button>
            <button
              type="button"
              onClick={() => onStartDelete(item)}
              aria-label={`Delete ${item.fileName}`}
              className="w-10 h-10 rounded-lg flex items-center justify-center text-[#FF4D4D] hover:bg-[#3E151B] cursor-pointer"
            >
              <Trash2 className="w-[18px] h-[18px]" />
            </button>
          </div>
        </div>

        {isCurrent && durMs > 0 && (
          <div className="flex items-center gap-2.5 pt-1">
            <span className="text-[11px] font-mono-tabular text-[#00D4FF]">
              {formatShortDuration(posMs)}
            </span>
            <input
              type="range"
              min={0}
              max={Math.max(1, durMs)}
              value={Math.max(0, Math.min(durMs, posMs))}
              onChange={(e) => onSeekPlayback(Number(e.target.value))}
              aria-label={`Seek ${item.fileName}`}
              className="studio-slider flex-1"
            />
            <span className="text-[11px] font-mono-tabular text-[#94A3B8]">
              {formatShortDuration(durMs)}
            </span>
          </div>
        )}
      </div>
    );
  }
);

RecordingRowCard.displayName = 'RecordingRowCard';

interface TransportConsoleCardProps {
  hasMicPermission: boolean;
  recordingStatus: RecordingStatus;
  subscribeMetrics: MetricsSubscribeFn;
  getMetricsSnapshot: MetricsSnapshotFn;
  voiceChangerConfig: VoiceChangerConfig;
  effectsConfig: EffectsRackConfig;
  monitoringConfig: MonitoringConfig;
  onRequestMicPermission: () => void;
  onStartRecording: () => void;
  onOpenImportAudio: () => void;
  onPauseRecording: () => void;
  onResumeRecording: () => void;
  onStopRecording: () => void;
}

const TransportConsoleCard: React.FC<TransportConsoleCardProps> = memo(
  ({
    hasMicPermission,
    recordingStatus,
    subscribeMetrics,
    getMetricsSnapshot,
    voiceChangerConfig,
    effectsConfig,
    monitoringConfig,
    onRequestMicPermission,
    onStartRecording,
    onOpenImportAudio,
    onPauseRecording,
    onResumeRecording,
    onStopRecording,
  }) => {
    const statusLabel =
      recordingStatus === RecordingStatus.RECORDING
        ? 'RECORDING'
        : recordingStatus === RecordingStatus.PAUSED
          ? 'PAUSED'
          : recordingStatus === RecordingStatus.INTERRUPTED_PAUSED
            ? 'INTERRUPTED — PAUSED'
            : 'READY';

    const statusTone =
      recordingStatus === RecordingStatus.RECORDING
        ? 'bg-[#3E151B] border-[#FF4D4D]/60 text-[#FF4D4D]'
        : recordingStatus === RecordingStatus.PAUSED ||
            recordingStatus === RecordingStatus.INTERRUPTED_PAUSED
          ? 'bg-[#3A280C] border-[#F59E0B]/60 text-[#F59E0B]'
          : 'bg-[#131822] border-[#2C374D] text-[#94A3B8]';

    const dotColor =
      recordingStatus === RecordingStatus.RECORDING
        ? 'bg-[#FF4D4D]'
        : recordingStatus === RecordingStatus.PAUSED ||
            recordingStatus === RecordingStatus.INTERRUPTED_PAUSED
          ? 'bg-[#F59E0B]'
          : 'bg-[#94A3B8]';

    const activeFxCount = getActiveEffectCount(effectsConfig);
    const voiceLabel = voiceChangerConfig.enabled
      ? `Voice: ${VOICE_PRESET_LABELS[voiceChangerConfig.preset]}`
      : 'Voice: OFF';

    const handleStartClick = () => {
      if (!hasMicPermission) {
        onRequestMicPermission();
      } else {
        onStartRecording();
      }
    };

    return (
      <div
        className={`w-full rounded-2xl bg-[#1A2130] border p-4 flex flex-col items-center gap-3 ${
          recordingStatus === RecordingStatus.RECORDING ? 'border-[#FF4D4D]' : 'border-[#2C374D]'
        }`}
      >
        <div className="w-full flex flex-wrap items-center justify-between gap-2">
          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-[11px] font-bold tracking-wider ${statusTone}`}
          >
            <span className={`w-2 h-2 rounded-full ${dotColor}`} />
            <span>{statusLabel}</span>
          </span>

          <span className="px-2 py-1 rounded-lg bg-[#131822] border border-[#2C374D] text-[11px] font-mono-tabular text-[#94A3B8]">
            {AudioConfig.OUTPUT_FORMAT_LABEL} • 48 kHz • 16-bit Mono •{' '}
            {AudioConfig.OUTPUT_BITRATE_KBPS} kbps
          </span>
        </div>

        {/* Isolated Tabular Recording Timer (only node updating at 20 Hz inside Transport card) */}
        <TransportTimer
          recordingStatus={recordingStatus}
          subscribeMetrics={subscribeMetrics}
          getMetricsSnapshot={getMetricsSnapshot}
        />

        {/* Active shared DSP summary badges */}
        <div className="flex flex-wrap items-center justify-center gap-2">
          <span
            className={`px-2 py-1 rounded-md border text-[11px] font-medium ${
              voiceChangerConfig.enabled
                ? 'bg-[#222B3D] border-[#00D4FF]/50 text-[#00D4FF]'
                : 'bg-[#131822] border-[#2C374D] text-[#64748B]'
            }`}
          >
            {voiceLabel}
          </span>

          <span
            className={`px-2 py-1 rounded-md border text-[11px] font-medium ${
              activeFxCount > 0
                ? 'bg-[#222B3D] border-[#22C55E]/50 text-[#22C55E]'
                : 'bg-[#131822] border-[#2C374D] text-[#64748B]'
            }`}
          >
            {activeFxCount > 0 ? `FX: ${activeFxCount} ON` : 'FX: OFF'}
          </span>

          <span
            className={`px-2 py-1 rounded-md border text-[11px] font-medium ${
              monitoringConfig.livePreviewEnabled
                ? 'bg-[#222B3D] border-[#00D4FF]/50 text-[#00D4FF]'
                : 'bg-[#131822] border-[#2C374D] text-[#64748B]'
            }`}
          >
            {monitoringConfig.livePreviewEnabled ? 'Preview: ON' : 'Preview: OFF'}
          </span>

          <span
            className={`px-2 py-1 rounded-md border text-[11px] font-medium ${
              monitoringConfig.echoCancellationEnabled
                ? 'bg-[#222B3D] border-[#22C55E]/50 text-[#22C55E]'
                : 'bg-[#131822] border-[#2C374D] text-[#64748B]'
            }`}
          >
            {monitoringConfig.echoCancellationEnabled
              ? `AEC Gate: ${monitoringConfig.noiseGateThresholdDb} dBFS`
              : 'AEC Gate: OFF'}
          </span>
        </div>

        {/* Transport Controls (Single Start Recording button in entire app) */}
        {recordingStatus === RecordingStatus.IDLE ? (
          <button
            type="button"
            onClick={handleStartClick}
            className="w-full h-14 rounded-[14px] bg-[#FF4D4D] text-[#F1F5F9] font-display text-base font-semibold flex items-center justify-center gap-2 hover:brightness-110 cursor-pointer transition"
          >
            <Circle className="w-5 h-5 fill-current" />
            <span>Start Recording</span>
          </button>
        ) : (
          <div className="w-full grid grid-cols-2 gap-2.5">
            {recordingStatus === RecordingStatus.RECORDING ? (
              <button
                type="button"
                onClick={onPauseRecording}
                className="h-[54px] rounded-[14px] bg-[#3A280C] border border-[#F59E0B]/40 text-[#F59E0B] font-semibold text-sm flex items-center justify-center gap-1.5 hover:brightness-110 cursor-pointer transition"
              >
                <Pause className="w-4 h-4" />
                <span>Pause</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={onResumeRecording}
                className="h-[54px] rounded-[14px] bg-[#22C55E] text-[#131822] font-semibold text-sm flex items-center justify-center gap-1.5 hover:brightness-110 cursor-pointer transition"
              >
                <Play className="w-4 h-4" />
                <span>Resume</span>
              </button>
            )}

            <button
              type="button"
              onClick={onStopRecording}
              className="h-[54px] rounded-[14px] bg-[#FF4D4D] text-[#F1F5F9] font-semibold text-sm flex items-center justify-center gap-1.5 hover:brightness-110 cursor-pointer transition"
            >
              <Square className="w-4 h-4 fill-current" />
              <span>Stop &amp; Save</span>
            </button>
          </div>
        )}

        {/* Import Audio secondary CTA */}
        <button
          type="button"
          disabled={recordingStatus !== RecordingStatus.IDLE}
          onClick={onOpenImportAudio}
          className={`w-full h-[50px] rounded-[14px] border font-semibold text-sm flex items-center justify-center gap-2 transition ${
            recordingStatus === RecordingStatus.IDLE
              ? 'bg-[#0E303D]/45 border-[#00D4FF]/75 text-[#00D4FF] hover:bg-[#0E303D]/70 cursor-pointer'
              : 'bg-[#131822] border-[#2C374D] text-[#64748B] cursor-not-allowed'
          }`}
        >
          <FileAudio className="w-[19px] h-[19px]" />
          <span>Import Audio</span>
        </button>
      </div>
    );
  }
);

TransportConsoleCard.displayName = 'TransportConsoleCard';
