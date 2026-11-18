import React, { useCallback } from 'react';
import { Mic, Sliders, Waves, X } from 'lucide-react';
import { ImportAudioOverlay } from './components/ImportAudioModal';
import { useVoxForge } from './hooks/useVoxForge';
import { EffectsScreen } from './screens/EffectsScreen';
import { RecorderScreen } from './screens/RecorderScreen';
import { VoiceChangerScreen } from './screens/VoiceChangerScreen';
import { RecordingStatus, StudioTab } from './types/audio';

const TABS: { id: StudioTab; label: string }[] = [
  { id: StudioTab.RECORDER, label: 'Recorder' },
  { id: StudioTab.VOICE_CHANGER, label: 'Voice Changer' },
  { id: StudioTab.EFFECTS, label: 'Effects' },
];

const TabIcon: React.FC<{ tab: StudioTab }> = ({ tab }) => {
  if (tab === StudioTab.RECORDER) return <Mic className="w-5 h-5" />;
  if (tab === StudioTab.VOICE_CHANGER) return <Sliders className="w-5 h-5" />;
  return <Waves className="w-5 h-5" />;
};

export function App() {
  const vf = useVoxForge();

  const handleNavKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLElement>, index: number, isVertical: boolean) => {
      const prevKey = isVertical ? 'ArrowUp' : 'ArrowLeft';
      const nextKey = isVertical ? 'ArrowDown' : 'ArrowRight';
      if (e.key === prevKey) {
        e.preventDefault();
        const prevIdx = (index - 1 + TABS.length) % TABS.length;
        vf.setSelectedTab(TABS[prevIdx].id);
      } else if (e.key === nextKey) {
        e.preventDefault();
        const nextIdx = (index + 1) % TABS.length;
        vf.setSelectedTab(TABS[nextIdx].id);
      }
    },
    [vf]
  );

  return (
    <div className="h-screen w-screen overflow-hidden bg-[#0B0E14] text-[#F1F5F9] flex flex-col md:flex-row">
      {/* Desktop / Tablet / Large-Screen Navigation Rail (>= 768px) */}
      <nav
        aria-label="Primary navigation"
        className="hidden md:flex md:flex-col md:w-20 lg:w-24 md:shrink-0 bg-[#131822] border-r border-[#2C374D] py-4 items-center gap-3"
      >
        {TABS.map((tab, idx) => {
          const selected = vf.selectedTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              aria-current={selected ? 'page' : undefined}
              onClick={() => vf.setSelectedTab(tab.id)}
              onKeyDown={(e) => handleNavKeyDown(e, idx, true)}
              className="flex flex-col items-center gap-1 px-1.5 py-1.5 w-full cursor-pointer group rounded-xl"
            >
              <div
                className={`w-14 h-8 rounded-full flex items-center justify-center transition-colors ${
                  selected
                    ? 'bg-[#0E303D] text-[#00D4FF]'
                    : 'text-[#94A3B8] group-hover:bg-[#1A2130] group-hover:text-[#F1F5F9]'
                }`}
              >
                <TabIcon tab={tab.id} />
              </div>
              <span
                className={`text-[11px] font-medium text-center leading-tight ${
                  selected ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                }`}
              >
                {tab.label}
              </span>
            </button>
          );
        })}
      </nav>

      {/* Main Column */}
      <div className="flex-1 min-w-0 min-h-0 flex flex-col">
        {/* Top App Bar */}
        <header className="h-14 shrink-0 bg-[#131822] border-b border-[#2C374D] px-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <h1 className="font-display text-xl font-bold tracking-tight text-[#F1F5F9]">
              VoxForge
            </h1>

            {vf.recordingStatus === RecordingStatus.RECORDING ? (
              <span className="px-2 py-0.5 rounded-md bg-[#3E151B] border border-[#FF4D4D] text-[11px] font-bold tracking-wider text-[#FF4D4D]">
                REC
              </span>
            ) : vf.monitoringConfig.livePreviewEnabled ? (
              <span className="px-2 py-0.5 rounded-md bg-[#0E303D] border border-[#00D4FF] text-[11px] font-bold tracking-wider text-[#00D4FF]">
                PREVIEW
              </span>
            ) : null}
          </div>
        </header>

        {/* Dismissible Status Banner */}
        {vf.statusBanner && (
          <div className="px-4 pt-2 flex justify-center shrink-0">
            <div className="w-full max-w-[640px] lg:max-w-[1120px] xl:max-w-[1360px] rounded-lg bg-[#1A2130] border border-[#2C374D] px-3 py-2 flex items-center justify-between gap-2">
              <span className="text-sm text-[#F1F5F9]">{vf.statusBanner}</span>
              <button
                type="button"
                onClick={vf.dismissBanner}
                aria-label="Dismiss notification"
                className="w-8 h-8 rounded-md flex items-center justify-center text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer shrink-0"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}

        {/* Active Screen */}
        <main className="flex-1 min-h-0 overflow-hidden">
          {vf.selectedTab === StudioTab.RECORDER && (
            <RecorderScreen
              hasMicPermission={vf.hasMicPermission}
              hardwareState={vf.hardwareState}
              recordingStatus={vf.recordingStatus}
              subscribeMetrics={vf.subscribeMetrics}
              getMetricsSnapshot={vf.getMetricsSnapshot}
              voiceChangerConfig={vf.voiceChangerConfig}
              effectsConfig={vf.effectsConfig}
              userEffectPresets={vf.userEffectPresets}
              monitoringConfig={vf.monitoringConfig}
              recordings={vf.recordings}
              playbackState={vf.playbackState}
              importedPlaybackState={vf.importedPlaybackState}
              onRequestMicPermission={() => void vf.requestMicPermission()}
              onRefreshDevices={vf.refreshDevices}
              onSelectInputDevice={vf.selectInputDevice}
              onSelectOutputDevice={vf.selectOutputDevice}
              onStartRecording={() => void vf.startRecording()}
              onPauseRecording={vf.pauseRecording}
              onResumeRecording={vf.resumeRecording}
              onStopRecording={vf.stopRecording}
              onResetClips={vf.resetClipCounter}
              onToggleMonitorInput={() => void vf.toggleManualInputMonitor()}
              onOpenImportAudio={vf.openImportSheet}
              onToggleImportedPlayPause={vf.toggleImportedPlayback}
              onSeekImportedPlayback={vf.seekImportedPlayback}
              onClearImportedAudio={vf.clearImportedAudio}
              onSaveAndShareImportedAudio={() => void vf.saveAndExportImportedAudio()}
              onPitchCorrectionChanged={vf.updatePitchCorrectionConfig}
              onPitchFormantChanged={vf.updatePitchFormantConfig}
              onEqualizerChanged={vf.updateEqualizerConfig}
              onCompressionChanged={vf.updateCompressionConfig}
              onSaturationChanged={vf.updateSaturationConfig}
              onReverbChanged={vf.updateReverbConfig}
              onDelayChanged={vf.updateEchoConfig}
              onNoiseGateChanged={vf.updateNoiseGateConfig}
              onDeEsserChanged={vf.updateDeEsserConfig}
              onLimiterChanged={vf.updateLimiterConfig}
              onResetStudioEffect={vf.resetStudioEffect}
              onSaveStudioPreset={vf.saveUserEffectPreset}
              onLoadStudioPreset={vf.loadEffectPreset}
              onDeleteUserPreset={vf.deleteUserEffectPreset}
              onTogglePlayback={(item) => void vf.togglePlayback(item)}
              onSeekPlayback={vf.seekPlayback}
              onRenameRecording={(item, name) => void vf.renameRecording(item, name)}
              onDeleteRecording={(item) => void vf.deleteRecording(item)}
              onShareRecording={(item) => void vf.shareOrDownloadRecording(item)}
            />
          )}

          {vf.selectedTab === StudioTab.VOICE_CHANGER && (
            <VoiceChangerScreen
              config={vf.voiceChangerConfig}
              monitoringConfig={vf.monitoringConfig}
              hardwareState={vf.hardwareState}
              subscribeMetrics={vf.subscribeMetrics}
              getMetricsSnapshot={vf.getMetricsSnapshot}
              onEnabledChanged={vf.setVoiceChangerEnabled}
              onSelectPreset={vf.selectVoicePreset}
              onResetPreset={vf.resetVoicePresetToDefault}
              onPitchLevelChanged={vf.updateVoicePitchLevel}
              onToneLevelChanged={vf.updateVoiceToneLevel}
              onClarityChanged={vf.updateVoiceClarity}
              onLivePreviewToggle={(enabled) => void vf.setLiveVoicePreviewEnabled(enabled)}
              onMonitoringDelayChanged={vf.updateMonitoringDelaySlider}
              onResetMonitoringDelay={vf.resetMonitoringDelayToDefault}
              onMonitoringVolumeChanged={vf.updateMonitoringVolume}
              onEchoCancellationToggle={vf.setEchoCancellationEnabled}
              onAecNoiseGateThresholdChanged={vf.updateAecNoiseGateThreshold}
              onCalibrateBackgroundNoise={() => void vf.calibrateBackgroundNoise()}
            />
          )}

          {vf.selectedTab === StudioTab.EFFECTS && (
            <EffectsScreen
              config={vf.effectsConfig}
              onNoiseGateChanged={vf.updateNoiseGateConfig}
              onLowPassChanged={vf.updateLowPassConfig}
              onHighPassChanged={vf.updateHighPassConfig}
              onEqualizerChanged={vf.updateEqualizerConfig}
              onReverbChanged={vf.updateReverbConfig}
              onEchoChanged={vf.updateEchoConfig}
              onHallChanged={vf.updateHallConfig}
            />
          )}
        </main>

        {/* Mobile Bottom Navigation Bar (< 768px) */}
        <nav
          aria-label="Bottom navigation"
          className="md:hidden shrink-0 h-16 bg-[#131822] border-t border-[#2C374D] grid grid-cols-3"
        >
          {TABS.map((tab, idx) => {
            const selected = vf.selectedTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                aria-current={selected ? 'page' : undefined}
                onClick={() => vf.setSelectedTab(tab.id)}
                onKeyDown={(e) => handleNavKeyDown(e, idx, false)}
                className="flex flex-col items-center justify-center gap-1 cursor-pointer"
              >
                <div
                  className={`px-4 py-1 rounded-full flex items-center justify-center transition-colors ${
                    selected ? 'bg-[#0E303D] text-[#00D4FF]' : 'text-[#94A3B8]'
                  }`}
                >
                  <TabIcon tab={tab.id} />
                </div>
                <span
                  className={`text-[11px] font-medium ${
                    selected ? 'text-[#00D4FF]' : 'text-[#94A3B8]'
                  }`}
                >
                  {tab.label}
                </span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Full-Screen Import Audio Overlay */}
      {vf.showImportSheet && (
        <ImportAudioOverlay
          stagedAudioFiles={vf.stagedAudioFiles}
          isLoadingAudio={vf.isLoadingStagedAudio}
          onAddStagedAudioFiles={(files) => void vf.addFilesToStagedAudio(files)}
          onImportMediaFile={(file, fromVideo) => void vf.importMediaFile(file, fromVideo)}
          onSelectStagedItem={(item) => void vf.importStagedItem(item)}
          onDismiss={vf.closeImportSheet}
        />
      )}
    </div>
  );
}

export default App;
