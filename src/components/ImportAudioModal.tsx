import React, { useMemo, useRef, useState } from 'react';
import {
  FileAudio,
  FileVideo,
  Film,
  FolderOpen,
  Pause,
  Play,
  Plus,
  Search,
  Share2,
  X,
} from 'lucide-react';
import { ImportedPlaybackState, StagedAudioItem } from '../types/audio';
import { formatShortDuration } from '../utils/format';

interface ImportAudioModalProps {
  stagedAudioFiles: StagedAudioItem[];
  isLoadingAudio: boolean;
  onAddStagedAudioFiles: (files: FileList | File[]) => void;
  onImportMediaFile: (file: File, fromVideo: boolean) => void;
  onSelectStagedItem: (item: StagedAudioItem) => void;
  onDismiss: () => void;
}

type ImportTab = 'MEDIA' | 'AUDIO';

export const ImportAudioOverlay: React.FC<ImportAudioModalProps> = ({
  stagedAudioFiles,
  isLoadingAudio,
  onAddStagedAudioFiles,
  onImportMediaFile,
  onSelectStagedItem,
  onDismiss,
}) => {
  const [selectedTab, setSelectedTab] = useState<ImportTab>('MEDIA');
  const [searchQuery, setSearchQuery] = useState('');
  const videoInputRef = useRef<HTMLInputElement | null>(null);
  const audioInputRef = useRef<HTMLInputElement | null>(null);

  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return stagedAudioFiles;
    return stagedAudioFiles.filter((item) => item.displayName.toLowerCase().includes(q));
  }, [searchQuery, stagedAudioFiles]);

  return (
    <div
      className="fixed inset-0 z-40 bg-[#0B0E14] flex justify-center overflow-hidden"
      role="dialog"
      aria-modal="true"
      aria-label="Import Audio"
    >
      <input
        ref={videoInputRef}
        type="file"
        accept="video/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) {
            onImportMediaFile(file, true);
          }
          e.target.value = '';
        }}
      />

      <input
        ref={audioInputRef}
        type="file"
        accept="audio/*"
        multiple
        className="hidden"
        onChange={(e) => {
          const files = e.target.files;
          if (files && files.length > 0) {
            if (files.length === 1) {
              onImportMediaFile(files[0], false);
            } else {
              onAddStagedAudioFiles(files);
            }
          }
          e.target.value = '';
        }}
      />

      <div className="w-full max-w-[640px] h-full flex flex-col">
        {/* Header */}
        <div className="bg-[#131822] px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FolderOpen className="w-5 h-5 text-[#00D4FF]" />
            <h2 className="font-display text-lg font-semibold text-[#F1F5F9]">Import Audio</h2>
          </div>
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Close import modal"
            className="w-10 h-10 rounded-lg flex items-center justify-center text-[#94A3B8] hover:text-[#F1F5F9] hover:bg-[#1A2130] cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 2-Tab Selector: Media | Audio */}
        <div className="bg-[#131822] px-4 pb-3 pt-1 grid grid-cols-2 gap-2.5 border-b border-[#2C374D]">
          <button
            type="button"
            onClick={() => setSelectedTab('MEDIA')}
            className={`h-12 rounded-[10px] border flex items-center justify-center gap-2 text-sm cursor-pointer transition-colors ${
              selectedTab === 'MEDIA'
                ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF] font-bold'
                : 'bg-[#1A2130] border-[#2C374D] text-[#94A3B8] font-medium hover:text-[#F1F5F9]'
            }`}
          >
            <FileVideo className="w-[18px] h-[18px]" />
            <span>Media</span>
          </button>

          <button
            type="button"
            onClick={() => setSelectedTab('AUDIO')}
            className={`h-12 rounded-[10px] border flex items-center justify-center gap-2 text-sm cursor-pointer transition-colors ${
              selectedTab === 'AUDIO'
                ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF] font-bold'
                : 'bg-[#1A2130] border-[#2C374D] text-[#94A3B8] font-medium hover:text-[#F1F5F9]'
            }`}
          >
            <FileAudio className="w-[18px] h-[18px]" />
            <span>Audio</span>
          </button>
        </div>

        {/* Body */}
        {selectedTab === 'MEDIA' ? (
          <div className="flex-1 p-5 overflow-y-auto">
            <div className="rounded-2xl bg-[#1A2130] border border-[#00D4FF]/45 p-5 flex flex-col items-center text-center gap-3">
              <div className="w-14 h-14 rounded-full bg-[#0E303D] flex items-center justify-center">
                <Film className="w-7 h-7 text-[#00D4FF]" />
              </div>
              <h3 className="font-display text-base font-semibold text-[#F1F5F9]">
                Extract Audio from Video
              </h3>
              <p className="text-sm text-[#94A3B8] leading-relaxed">
                Select any video file from your device. Your original video remains untouched while
                VoxForge extracts its audio track into the 48 kHz DSP engine.
              </p>
              <button
                type="button"
                onClick={() => videoInputRef.current?.click()}
                className="mt-1 w-full h-[52px] rounded-xl bg-[#00D4FF] text-[#131822] font-semibold text-sm flex items-center justify-center gap-2 hover:brightness-110 cursor-pointer transition"
              >
                <FileVideo className="w-5 h-5" />
                <span>Choose Video File</span>
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex flex-col overflow-hidden">
            <div className="px-4 py-3 flex items-center gap-2">
              <div className="relative flex-1">
                <Search className="w-4 h-4 text-[#94A3B8] absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Filter audio files..."
                  className="w-full rounded-xl bg-[#1A2130] border border-[#2C374D] pl-9 pr-3 py-2.5 text-sm text-[#F1F5F9] placeholder-[#64748B] focus:outline-none focus:border-[#00D4FF]"
                />
              </div>

              <button
                type="button"
                onClick={() => audioInputRef.current?.click()}
                className="h-[42px] px-3.5 rounded-xl bg-[#00D4FF] text-[#131822] text-xs font-bold flex items-center gap-1.5 shrink-0 hover:brightness-110 cursor-pointer transition"
              >
                <Plus className="w-4 h-4" />
                <span>Browse Audio</span>
              </button>
            </div>

            <div className="px-4 pb-1 flex items-center justify-between">
              <span className="text-[11px] font-semibold tracking-wider text-[#94A3B8]">
                ALL DEVICE AUDIO ({filtered.length})
              </span>
              <span className="text-[11px] font-medium text-[#00D4FF]">Newest first</span>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-2">
              {isLoadingAudio ? (
                <div className="h-40 flex items-center justify-center">
                  <div className="w-7 h-7 rounded-full border-2 border-[#00D4FF] border-t-transparent animate-spin" />
                </div>
              ) : filtered.length === 0 ? (
                <div className="h-48 flex flex-col items-center justify-center text-center gap-3 px-4">
                  <p className="text-sm text-[#94A3B8]">
                    Select an audio file (WAV, MP3, M4A, OGG, FLAC, AAC) from your device to import
                    into the 48 kHz DSP engine.
                  </p>
                  <button
                    type="button"
                    onClick={() => audioInputRef.current?.click()}
                    className="px-4 py-2.5 rounded-xl bg-[#0E303D] border border-[#00D4FF] text-[#00D4FF] text-sm font-medium cursor-pointer"
                  >
                    Select Audio File
                  </button>
                </div>
              ) : (
                <div className="flex flex-col gap-2 pb-4">
                  {filtered.map((audio) => (
                    <button
                      key={audio.id}
                      type="button"
                      onClick={() => onSelectStagedItem(audio)}
                      className="w-full rounded-xl bg-[#1A2130] border border-[#2C374D] hover:border-[#00D4FF]/60 px-3.5 py-3 flex items-center gap-3 text-left cursor-pointer transition-colors"
                    >
                      <div className="w-10 h-10 rounded-full bg-[#131822] flex items-center justify-center shrink-0">
                        <FileAudio className="w-5 h-5 text-[#00D4FF]" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-[#F1F5F9] truncate">
                          {audio.displayName}
                        </p>
                        <div className="mt-1 flex items-center gap-2">
                          <span className="px-1.5 py-0.5 rounded bg-[#0E303D] text-[10px] font-semibold text-[#00D4FF]">
                            {audio.formatLabel}
                          </span>
                          <span className="text-[11px] font-mono-tabular text-[#94A3B8]">
                            {formatShortDuration(audio.durationMs)}
                          </span>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

interface ImportedAudioStudioCardProps {
  playbackState: ImportedPlaybackState;
  onTogglePlayPause: () => void;
  onSeek: (positionMs: number) => void;
  onChangeSource: () => void;
  onClearSource: () => void;
  onSaveAndExport: () => void;
}

export const ImportedAudioStudioCard: React.FC<ImportedAudioStudioCardProps> = ({
  playbackState,
  onTogglePlayPause,
  onSeek,
  onChangeSource,
  onClearSource,
  onSaveAndExport,
}) => {
  const source = playbackState.source;

  return (
    <div className="w-full rounded-2xl bg-[#1A2130] border border-[#00D4FF]/70 p-4 flex flex-col gap-2.5">
      {playbackState.isImporting ? (
        <div className="flex items-center gap-3 py-1">
          <div className="w-6 h-6 rounded-full border-2 border-[#00D4FF] border-t-transparent animate-spin shrink-0" />
          <span className="text-sm font-medium text-[#F1F5F9]">
            Extracting &amp; resampling audio to 48 kHz...
          </span>
        </div>
      ) : (
        source && (
          <>
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2.5 min-w-0 flex-1">
                <div className="w-[38px] h-[38px] rounded-[10px] bg-[#0E303D] flex items-center justify-center shrink-0">
                  {source.fromVideo ? (
                    <Film className="w-5 h-5 text-[#00D4FF]" />
                  ) : (
                    <FileAudio className="w-5 h-5 text-[#00D4FF]" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-[#F1F5F9] truncate">
                    {source.displayName}
                  </p>
                  <p className="text-[11px] font-medium text-[#00D4FF] truncate">
                    {source.formatLabel} • Real-Time 48 kHz DSP
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  onClick={onChangeSource}
                  className="px-2.5 py-1.5 rounded-lg text-xs font-medium text-[#00D4FF] hover:bg-[#0E303D]/60 cursor-pointer"
                >
                  Replace
                </button>
                <button
                  type="button"
                  onClick={onClearSource}
                  aria-label="Clear imported audio"
                  className="w-9 h-9 rounded-lg flex items-center justify-center text-[#94A3B8] hover:text-[#F1F5F9] hover:bg-[#222B3D] cursor-pointer"
                >
                  <X className="w-[18px] h-[18px]" />
                </button>
              </div>
            </div>

            {/* Transport Row */}
            <div className="flex items-center gap-2.5">
              <button
                type="button"
                onClick={onTogglePlayPause}
                aria-label={playbackState.isPlaying ? 'Pause imported audio' : 'Play imported audio'}
                className={`w-12 h-12 rounded-full shrink-0 flex items-center justify-center cursor-pointer transition-colors ${
                  playbackState.isPlaying
                    ? 'bg-[#00D4FF] text-[#131822]'
                    : 'bg-[#222B3D] text-[#00D4FF] hover:bg-[#2C374D]'
                }`}
              >
                {playbackState.isPlaying ? (
                  <Pause className="w-5 h-5" />
                ) : (
                  <Play className="w-5 h-5 ml-0.5" />
                )}
              </button>

              <div className="flex-1 flex flex-col gap-1">
                <input
                  type="range"
                  min={0}
                  max={Math.max(1, playbackState.durationMs)}
                  value={Math.max(0, Math.min(playbackState.durationMs, playbackState.positionMs))}
                  onChange={(e) => onSeek(Number(e.target.value))}
                  aria-label="Seek imported audio"
                  className="studio-slider"
                />
                <div className="flex items-center justify-between text-[11px] font-mono-tabular">
                  <span className="text-[#00D4FF]">
                    {formatShortDuration(playbackState.positionMs)}
                  </span>
                  <span className="text-[#94A3B8]">
                    {formatShortDuration(playbackState.durationMs)}
                  </span>
                </div>
              </div>
            </div>

            <p className="text-[11px] text-[#64748B]">
              Effects and Voice Changer update live without resetting playback position.
            </p>

            {playbackState.isExporting && (
              <div className="flex flex-col gap-1">
                <div className="w-full h-1.5 rounded-full bg-[#131822] overflow-hidden">
                  <div
                    className="h-full bg-[#22C55E] transition-all"
                    style={{ width: `${Math.round(playbackState.exportProgress * 100)}%` }}
                  />
                </div>
                <span className="text-[11px] font-mono-tabular text-[#22C55E]">
                  Rendering full processed WAV ({Math.round(playbackState.exportProgress * 100)}%)...
                </span>
              </div>
            )}

            <button
              type="button"
              disabled={playbackState.isExporting}
              onClick={onSaveAndExport}
              className="w-full h-[50px] rounded-xl bg-[#123222] border border-[#22C55E] text-[#22C55E] text-sm font-semibold flex items-center justify-center gap-2 hover:brightness-110 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer transition"
            >
              <Share2 className="w-[18px] h-[18px]" />
              <span>
                {playbackState.isExporting ? 'Rendering Processed Audio...' : 'Save Audio'}
              </span>
            </button>
          </>
        )
      )}
    </div>
  );
};
