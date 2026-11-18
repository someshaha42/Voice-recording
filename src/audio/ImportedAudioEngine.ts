import { StudioDspPipeline } from '../dsp/EffectsRackDsp';
import { LinearResampler } from '../dsp/LinearResampler';
import { NoiseGateProcessor } from '../dsp/NoiseGateProcessor';
import {
  AudioConfig,
  EffectsRackConfig,
  ImportedAudioSource,
  ImportedPlaybackState,
  MonitoringConstants,
  VoiceChangerConfig,
} from '../types/audio';

export class ImportedAudioEngine {
  private readonly aecNoiseGate = NoiseGateProcessor.createAecNoiseGate(
    AudioConfig.OUTPUT_SAMPLE_RATE_HZ
  );
  private readonly dspPipeline = new StudioDspPipeline(AudioConfig.OUTPUT_SAMPLE_RATE_HZ);

  private state: ImportedPlaybackState = {
    source: null,
    isPlaying: false,
    positionMs: 0,
    durationMs: 0,
    isImporting: false,
    isExporting: false,
    exportProgress: 0,
    detectedNote: '--',
  };

  private listeners = new Set<(s: ImportedPlaybackState) => void>();

  private audioCtx: AudioContext | null = null;
  private processorNode: ScriptProcessorNode | null = null;
  private outputResampler: LinearResampler | null = null;

  private readFramePos = 0;
  private scratchChunk = new Float32Array(2048);
  private scratchOut = new Float32Array(2048);
  private lastUiEmitMs = 0;

  constructor() {
    this.aecNoiseGate.updateAecThreshold(true, MonitoringConstants.DEFAULT_AEC_GATE_THRESHOLD_DB);
  }

  subscribe(fn: (s: ImportedPlaybackState) => void): () => void {
    this.listeners.add(fn);
    fn(this.state);
    return () => this.listeners.delete(fn);
  }

  getState(): ImportedPlaybackState {
    return this.state;
  }

  private emit(): void {
    for (const fn of this.listeners) fn(this.state);
  }

  updateAecNoiseGate(enabled: boolean, thresholdDb: number): void {
    this.aecNoiseGate.updateAecThreshold(enabled, thresholdDb);
  }

  updateEffectsConfig(config: EffectsRackConfig): void {
    this.dspPipeline.updateEffectsConfig(config);
  }

  updateVoiceChangerConfig(config: VoiceChangerConfig): void {
    this.dspPipeline.updateVoiceChangerConfig(config);
  }

  setImporting(importing: boolean): void {
    this.state = { ...this.state, isImporting: importing };
    this.emit();
  }

  setExporting(exporting: boolean, progress: number): void {
    this.state = {
      ...this.state,
      isExporting: exporting,
      exportProgress: Math.max(0, Math.min(1, progress)),
    };
    this.emit();
  }

  loadSource(source: ImportedAudioSource): void {
    this.stopAudioLoop();
    this.readFramePos = 0;
    this.aecNoiseGate.reset();
    this.dspPipeline.reset();
    this.state = {
      ...this.state,
      source,
      isPlaying: false,
      positionMs: 0,
      durationMs: source.durationMs,
      isImporting: false,
      detectedNote: '--',
    };
    this.emit();
  }

  clearSource(): void {
    this.stopAudioLoop();
    this.readFramePos = 0;
    this.state = {
      ...this.state,
      source: null,
      isPlaying: false,
      positionMs: 0,
      durationMs: 0,
      detectedNote: '--',
    };
    this.emit();
  }

  togglePlayPause(): void {
    if (!this.state.source) return;
    if (this.state.isPlaying) {
      this.pause();
    } else {
      void this.play();
    }
  }

  async play(): Promise<void> {
    const src = this.state.source;
    if (!src) return;
    if (this.readFramePos >= src.totalFrames48k) {
      this.readFramePos = 0;
      this.aecNoiseGate.reset();
      this.dspPipeline.reset();
    }
    this.state = { ...this.state, isPlaying: true };
    this.emit();
    await this.ensureAudioLoopRunning();
  }

  pause(): void {
    if (!this.state.isPlaying) return;
    this.state = { ...this.state, isPlaying: false };
    this.emit();
    this.stopAudioLoop();
  }

  seekTo(positionMs: number): void {
    const src = this.state.source;
    if (!src) return;
    const clampedMs = Math.max(0, Math.min(src.durationMs, positionMs));
    this.readFramePos = Math.min(
      src.totalFrames48k,
      Math.max(0, Math.floor((clampedMs * AudioConfig.OUTPUT_SAMPLE_RATE_HZ) / 1000))
    );
    this.aecNoiseGate.reset();
    this.dspPipeline.reset();
    this.state = { ...this.state, positionMs: clampedMs };
    this.emit();
  }

  private async ensureAudioLoopRunning(): Promise<void> {
    if (this.audioCtx) {
      if (this.audioCtx.state === 'suspended') {
        await this.audioCtx.resume().catch(() => undefined);
      }
      return;
    }

    const AudioCtxCtor =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const ctx = new AudioCtxCtor({
      sampleRate: AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
      latencyHint: 'interactive',
    });
    if (ctx.state === 'suspended') {
      await ctx.resume().catch(() => undefined);
    }

    const ctxRate = ctx.sampleRate || AudioConfig.OUTPUT_SAMPLE_RATE_HZ;
    this.outputResampler = new LinearResampler(AudioConfig.OUTPUT_SAMPLE_RATE_HZ, ctxRate);

    const processor = ctx.createScriptProcessor(1024, 0, 1);
    processor.onaudioprocess = (event: AudioProcessingEvent) => {
      const outChannel = event.outputBuffer.getChannelData(0);
      this.fillAudioBlock(outChannel, ctxRate);
    };
    processor.connect(ctx.destination);

    this.audioCtx = ctx;
    this.processorNode = processor;
  }

  private stopAudioLoop(): void {
    if (this.processorNode) {
      this.processorNode.onaudioprocess = null;
      try {
        this.processorNode.disconnect();
      } catch {
        // ignore
      }
      this.processorNode = null;
    }
    if (this.audioCtx) {
      void this.audioCtx.close().catch(() => undefined);
      this.audioCtx = null;
    }
  }

  private fillAudioBlock(output: Float32Array, ctxSampleRateHz: number): void {
    const src = this.state.source;
    if (!src || !this.state.isPlaying) {
      output.fill(0);
      return;
    }

    const outLen = output.length;
    const needed48k =
      ctxSampleRateHz === AudioConfig.OUTPUT_SAMPLE_RATE_HZ
        ? outLen
        : Math.max(1, Math.round((outLen * AudioConfig.OUTPUT_SAMPLE_RATE_HZ) / ctxSampleRateHz));

    if (this.scratchChunk.length < needed48k) {
      this.scratchChunk = new Float32Array(needed48k * 2);
      this.scratchOut = new Float32Array(outLen * 2);
    }

    const remaining = src.totalFrames48k - this.readFramePos;
    if (remaining <= 0) {
      output.fill(0);
      this.readFramePos = 0;
      this.aecNoiseGate.reset();
      this.dspPipeline.reset();
      queueMicrotask(() => {
        this.stopAudioLoop();
        this.state = {
          ...this.state,
          isPlaying: false,
          positionMs: 0,
        };
        this.emit();
      });
      return;
    }

    const copyCount = Math.min(needed48k, remaining);
    this.scratchChunk.set(
      src.pcm48k.subarray(this.readFramePos, this.readFramePos + copyCount),
      0
    );
    if (copyCount < needed48k) {
      this.scratchChunk.fill(0, copyCount, needed48k);
    }
    this.readFramePos += copyCount;

    // Non-destructive processing on working buffer copy:
    // Shared AEC Noise Gate stage (-60 to -20 dBFS) -> Studio Effects & Voice Changer DSP
    this.aecNoiseGate.processInPlace(this.scratchChunk, needed48k);
    this.dspPipeline.processInPlace(this.scratchChunk, needed48k);

    if (ctxSampleRateHz === AudioConfig.OUTPUT_SAMPLE_RATE_HZ || !this.outputResampler) {
      output.set(this.scratchChunk.subarray(0, outLen), 0);
    } else {
      const written = this.outputResampler.process(this.scratchChunk, needed48k, this.scratchOut);
      const safeLen = Math.min(outLen, written);
      output.set(this.scratchOut.subarray(0, safeLen), 0);
      if (safeLen < outLen) output.fill(0, safeLen);
    }

    const now = performance.now();
    if (now - this.lastUiEmitMs >= 50) {
      this.lastUiEmitMs = now;
      const posMs = Math.min(
        src.durationMs,
        Math.round((this.readFramePos * 1000) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
      );
      this.state = {
        ...this.state,
        positionMs: posMs,
        detectedNote: this.dspPipeline.pitchCorrection.lastDetectedNoteLabel,
      };
      this.emit();
    }
  }
}
