import { AudioConfig } from '../types/audio';

export const WAV_HEADER_SIZE = 44;

export function writeWavHeader(
  view: DataView,
  dataByteLength: number,
  sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
  channels: number = AudioConfig.OUTPUT_CHANNELS,
  bitsPerSample: number = AudioConfig.OUTPUT_BITS_PER_SAMPLE
): void {
  const byteRate = sampleRateHz * channels * (bitsPerSample / 8);
  const blockAlign = channels * (bitsPerSample / 8);

  // "RIFF"
  view.setUint8(0, 0x52);
  view.setUint8(1, 0x49);
  view.setUint8(2, 0x46);
  view.setUint8(3, 0x46);
  view.setUint32(4, 36 + dataByteLength, true);
  // "WAVE"
  view.setUint8(8, 0x57);
  view.setUint8(9, 0x41);
  view.setUint8(10, 0x56);
  view.setUint8(11, 0x45);
  // "fmt "
  view.setUint8(12, 0x66);
  view.setUint8(13, 0x6d);
  view.setUint8(14, 0x74);
  view.setUint8(15, 0x20);
  view.setUint32(16, 16, true); // PCM subchunk size
  view.setUint16(20, 1, true); // AudioFormat = 1 (PCM)
  view.setUint16(22, channels, true);
  view.setUint32(24, sampleRateHz, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, bitsPerSample, true);
  // "data"
  view.setUint8(36, 0x64);
  view.setUint8(37, 0x61);
  view.setUint8(38, 0x74);
  view.setUint8(39, 0x61);
  view.setUint32(40, dataByteLength, true);
}

export function encodeFloat32ToWavBlob(
  samples: Float32Array,
  sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
): { blob: Blob; clipCount: number; durationMs: number; sizeBytes: number } {
  const numSamples = samples.length;
  const dataByteLength = numSamples * AudioConfig.BYTES_PER_SAMPLE;
  const buffer = new ArrayBuffer(WAV_HEADER_SIZE + dataByteLength);
  const view = new DataView(buffer);

  writeWavHeader(view, dataByteLength, sampleRateHz, AudioConfig.OUTPUT_CHANNELS, AudioConfig.OUTPUT_BITS_PER_SAMPLE);

  let clipCount = 0;
  let offset = WAV_HEADER_SIZE;
  for (let i = 0; i < numSamples; i++) {
    let s = samples[i];
    if (s >= 1.0 || s <= -1.0) {
      clipCount++;
      s = Math.max(-1.0, Math.min(1.0, s));
    }
    const int16 = Math.round(s * 32767.0);
    view.setInt16(offset, Math.max(-32768, Math.min(32767, int16)), true);
    offset += 2;
  }

  const blob = new Blob([buffer], { type: 'audio/wav' });
  const durationMs = Math.round((numSamples * 1000) / sampleRateHz);
  return {
    blob,
    clipCount,
    durationMs,
    sizeBytes: buffer.byteLength,
  };
}

export class StreamingWavAccumulator {
  private chunks: Int16Array[] = [];
  private totalSamples = 0;
  public totalClips = 0;

  writeSamples(samples: Float32Array, count: number): number {
    if (count <= 0) return 0;
    const pcm16 = new Int16Array(count);
    let blockClips = 0;
    for (let i = 0; i < count; i++) {
      let s = samples[i];
      if (s >= 1.0 || s <= -1.0) {
        blockClips++;
        s = Math.max(-1.0, Math.min(1.0, s));
      }
      pcm16[i] = Math.max(-32768, Math.min(32767, Math.round(s * 32767.0)));
    }
    this.chunks.push(pcm16);
    this.totalSamples += count;
    this.totalClips += blockClips;
    return blockClips;
  }

  getRecordedDurationMs(): number {
    return Math.round((this.totalSamples * 1000) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ);
  }

  getTotalSamples(): number {
    return this.totalSamples;
  }

  finalizeToBlob(): { blob: Blob; durationMs: number; sizeBytes: number } | null {
    if (this.totalSamples <= 0) return null;
    const dataByteLength = this.totalSamples * AudioConfig.BYTES_PER_SAMPLE;
    const headerBuffer = new ArrayBuffer(WAV_HEADER_SIZE);
    const view = new DataView(headerBuffer);
    writeWavHeader(
      view,
      dataByteLength,
      AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
      AudioConfig.OUTPUT_CHANNELS,
      AudioConfig.OUTPUT_BITS_PER_SAMPLE
    );

    const blobParts: BlobPart[] = [headerBuffer];
    for (const chunk of this.chunks) {
      blobParts.push(chunk.buffer.slice(chunk.byteOffset, chunk.byteOffset + chunk.byteLength) as ArrayBuffer);
    }
    const blob = new Blob(blobParts, { type: 'audio/wav' });
    return {
      blob,
      durationMs: this.getRecordedDurationMs(),
      sizeBytes: WAV_HEADER_SIZE + dataByteLength,
    };
  }

  reset(): void {
    this.chunks = [];
    this.totalSamples = 0;
    this.totalClips = 0;
  }
}
