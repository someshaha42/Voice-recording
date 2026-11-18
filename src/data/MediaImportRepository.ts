import { encodeFloat32ToWavBlob } from '../audio/WavPcmWriter';
import { StudioDspPipeline } from '../dsp/EffectsRackDsp';
import { resampleMonoArray } from '../dsp/LinearResampler';
import { NoiseGateProcessor } from '../dsp/NoiseGateProcessor';
import {
  AudioConfig,
  EffectsRackConfig,
  ImportedAudioSource,
  StagedAudioItem,
  VoiceChangerConfig,
} from '../types/audio';

export function inferAudioFormatLabel(file: File): string {
  const ext = file.name.split('.').pop()?.toUpperCase() ?? '';
  if (ext) return ext;
  if (file.type) {
    const part = file.type.split('/').pop()?.toUpperCase();
    if (part) return part;
  }
  return 'AUDIO';
}

export async function decodeMediaFileTo48kPcm(
  file: File,
  fromVideo: boolean
): Promise<ImportedAudioSource> {
  const AudioCtxCtor =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  const ctx = new AudioCtxCtor();
  try {
    const arrayBuf = await file.arrayBuffer();
    const audioBuffer = await ctx.decodeAudioData(arrayBuf.slice(0));
    const channels = audioBuffer.numberOfChannels;
    const len = audioBuffer.length;

    const mono = new Float32Array(len);
    if (channels === 1) {
      mono.set(audioBuffer.getChannelData(0));
    } else {
      const inv = 1 / channels;
      for (let c = 0; c < channels; c++) {
        const ch = audioBuffer.getChannelData(c);
        for (let i = 0; i < len; i++) {
          mono[i] += ch[i] * inv;
        }
      }
    }

    const pcm48k = resampleMonoArray(
      mono,
      audioBuffer.sampleRate,
      AudioConfig.OUTPUT_SAMPLE_RATE_HZ
    );
    const totalFrames48k = pcm48k.length;
    const durationMs = Math.round((totalFrames48k * 1000) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ);

    return {
      id: `imp_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      displayName: file.name,
      formatLabel: fromVideo ? 'Video Audio' : inferAudioFormatLabel(file),
      fromVideo,
      pcm48k,
      totalFrames48k,
      durationMs,
    };
  } catch (err) {
    const msg = err instanceof Error ? err.message : '';
    throw new Error(
      fromVideo
        ? `Unable to extract an audio track from "${file.name}". ${msg}`.trim()
        : `Unsupported or corrupted audio file "${file.name}". ${msg}`.trim()
    );
  } finally {
    await ctx.close().catch(() => undefined);
  }
}

export async function probeAudioFileMetadata(file: File): Promise<StagedAudioItem> {
  const id = `aud_${file.lastModified}_${file.size}_${file.name}`;
  let durationMs = 0;
  try {
    const url = URL.createObjectURL(file);
    try {
      durationMs = await new Promise<number>((resolve) => {
        const el = document.createElement('audio');
        el.preload = 'metadata';
        el.onloadedmetadata = () => {
          resolve(Number.isFinite(el.duration) ? Math.round(el.duration * 1000) : 0);
        };
        el.onerror = () => resolve(0);
        el.src = url;
      });
    } finally {
      URL.revokeObjectURL(url);
    }
  } catch {
    durationMs = 0;
  }

  return {
    id,
    displayName: file.name,
    durationMs,
    formatLabel: inferAudioFormatLabel(file),
    dateModifiedSec: Math.floor(file.lastModified / 1000),
    sizeBytes: file.size,
    file,
  };
}

export async function renderAndExportProcessedWav(
  source: ImportedAudioSource,
  effectsConfig: EffectsRackConfig,
  voiceChangerConfig: VoiceChangerConfig,
  aecNoiseGate: { enabled: boolean; thresholdDb: number },
  onProgress: (progress: number) => void
): Promise<{ blob: Blob; fileName: string; sizeBytes: number; durationMs: number }> {
  const aecGate = NoiseGateProcessor.createAecNoiseGate(AudioConfig.OUTPUT_SAMPLE_RATE_HZ);
  aecGate.updateAecThreshold(aecNoiseGate.enabled, aecNoiseGate.thresholdDb);
  aecGate.reset();

  const pipeline = new StudioDspPipeline(AudioConfig.OUTPUT_SAMPLE_RATE_HZ);
  pipeline.updateEffectsConfig(effectsConfig);
  pipeline.updateVoiceChangerConfig(voiceChangerConfig);
  pipeline.reset();

  const total = source.totalFrames48k;
  const output = new Float32Array(total);
  const blockSize = 4096;
  const scratch = new Float32Array(blockSize);

  let offset = 0;
  let blockCounter = 0;
  while (offset < total) {
    const count = Math.min(blockSize, total - offset);
    scratch.set(source.pcm48k.subarray(offset, offset + count), 0);
    aecGate.processInPlace(scratch, count);
    pipeline.processInPlace(scratch, count);
    output.set(scratch.subarray(0, count), offset);
    offset += count;
    blockCounter++;
    onProgress(offset / Math.max(1, total));
    if (blockCounter % 16 === 0) {
      await new Promise((r) => setTimeout(r, 0));
    }
  }

  const encoded = encodeFloat32ToWavBlob(output, AudioConfig.OUTPUT_SAMPLE_RATE_HZ);
  const baseName = source.displayName.replace(/\.[^.]+$/, '') || 'Imported_Audio';
  const cleanBase = baseName.replace(/[^a-zA-Z0-9._ -]/g, '_');
  const fileName = `VoxForge_${cleanBase}_processed.wav`;

  return {
    blob: encoded.blob,
    fileName,
    sizeBytes: encoded.sizeBytes,
    durationMs: encoded.durationMs,
  };
}

export function triggerBlobDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
