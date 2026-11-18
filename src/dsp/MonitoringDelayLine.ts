import { AudioConfig, MonitoringConstants } from '../types/audio';

export class MonitoringDelayLine {
  private readonly sampleRateHz: number;
  private readonly maxDelaySamples: number;
  private readonly ringBuffer: Float32Array;
  private writeIndex = 0;
  private currentDelaySamples = 0;
  private targetDelaySamples = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
    this.maxDelaySamples = Math.floor(
      (sampleRateHz * (MonitoringConstants.MAX_ADDED_DELAY_MS + 20)) / 1000
    );
    this.ringBuffer = new Float32Array(this.maxDelaySamples);
  }

  setDelayMs(delayMs: number): void {
    const clampedMs = Math.max(0, Math.min(MonitoringConstants.MAX_ADDED_DELAY_MS, delayMs));
    this.targetDelaySamples = (clampedMs * this.sampleRateHz) / 1000;
  }

  reset(): void {
    this.ringBuffer.fill(0);
    this.writeIndex = 0;
    this.currentDelaySamples = this.targetDelaySamples;
  }

  process(input: Float32Array, count: number, output: Float32Array): void {
    if (count <= 0) return;
    const target = this.targetDelaySamples;
    const slewAlpha = 0.004;

    for (let i = 0; i < count; i++) {
      this.ringBuffer[this.writeIndex] = input[i];
      this.currentDelaySamples += slewAlpha * (target - this.currentDelaySamples);
      const delay = Math.max(0, Math.min(this.maxDelaySamples - 2, this.currentDelaySamples));

      if (delay < 0.5) {
        output[i] = input[i];
      } else {
        let readPos = this.writeIndex - delay;
        if (readPos < 0) readPos += this.maxDelaySamples;
        const intPos = Math.floor(readPos);
        const idx0 = intPos % this.maxDelaySamples;
        const idx1 = (idx0 + 1) % this.maxDelaySamples;
        const frac = readPos - intPos;
        output[i] = this.ringBuffer[idx0] * (1 - frac) + this.ringBuffer[idx1] * frac;
      }

      this.writeIndex++;
      if (this.writeIndex >= this.maxDelaySamples) this.writeIndex = 0;
    }
  }
}
