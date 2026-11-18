import { PlaybackUiState } from '../types/audio';

export class RecordingPlaybackController {
  private audioEl: HTMLAudioElement | null = null;
  private activeObjectUrl: string | null = null;
  private rafId: number | null = null;

  private state: PlaybackUiState = {
    activeId: null,
    isPlaying: false,
    positionMs: 0,
    durationMs: 0,
  };

  private listeners = new Set<(s: PlaybackUiState) => void>();

  subscribe(fn: (s: PlaybackUiState) => void): () => void {
    this.listeners.add(fn);
    fn(this.state);
    return () => this.listeners.delete(fn);
  }

  getState(): PlaybackUiState {
    return this.state;
  }

  private emit(): void {
    for (const fn of this.listeners) fn(this.state);
  }

  async togglePlayPause(id: string, blob: Blob, fallbackDurationMs: number): Promise<void> {
    if (this.state.activeId === id && this.audioEl) {
      if (this.state.isPlaying) {
        this.audioEl.pause();
        this.stopProgressLoop();
        this.state = { ...this.state, isPlaying: false };
        this.emit();
      } else {
        try {
          await this.audioEl.play();
          this.state = { ...this.state, isPlaying: true };
          this.emit();
          this.startProgressLoop();
        } catch {
          this.stop();
        }
      }
      return;
    }

    this.stop();

    const url = URL.createObjectURL(blob);
    const audio = new Audio(url);
    audio.preload = 'auto';

    this.activeObjectUrl = url;
    this.audioEl = audio;
    this.state = {
      activeId: id,
      isPlaying: true,
      positionMs: 0,
      durationMs: fallbackDurationMs,
    };
    this.emit();

    audio.addEventListener('loadedmetadata', () => {
      const dur = Number.isFinite(audio.duration) && audio.duration > 0
        ? Math.round(audio.duration * 1000)
        : fallbackDurationMs;
      this.state = { ...this.state, durationMs: dur };
      this.emit();
    });

    audio.addEventListener('ended', () => {
      this.stopProgressLoop();
      this.state = {
        ...this.state,
        isPlaying: false,
        positionMs: 0,
      };
      this.emit();
    });

    try {
      await audio.play();
      this.startProgressLoop();
    } catch {
      this.stop();
    }
  }

  seekTo(positionMs: number): void {
    if (!this.audioEl) return;
    const clampedMs = Math.max(0, Math.min(this.state.durationMs, positionMs));
    this.audioEl.currentTime = clampedMs / 1000;
    this.state = { ...this.state, positionMs: clampedMs };
    this.emit();
  }

  stop(): void {
    this.stopProgressLoop();
    if (this.audioEl) {
      this.audioEl.pause();
      this.audioEl.src = '';
      this.audioEl = null;
    }
    if (this.activeObjectUrl) {
      URL.revokeObjectURL(this.activeObjectUrl);
      this.activeObjectUrl = null;
    }
    this.state = {
      activeId: null,
      isPlaying: false,
      positionMs: 0,
      durationMs: 0,
    };
    this.emit();
  }

  private startProgressLoop(): void {
    this.stopProgressLoop();
    const tick = () => {
      if (!this.audioEl || !this.state.isPlaying) return;
      const posMs = Math.round(this.audioEl.currentTime * 1000);
      if (posMs !== this.state.positionMs) {
        this.state = {
          ...this.state,
          positionMs: Math.min(this.state.durationMs, posMs),
        };
        this.emit();
      }
      this.rafId = requestAnimationFrame(tick);
    };
    this.rafId = requestAnimationFrame(tick);
  }

  private stopProgressLoop(): void {
    if (this.rafId != null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }
}
