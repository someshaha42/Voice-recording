import { AudioConfig } from '../types/audio';

/**
 * 4th-order (24 dB/octave) cascaded Butterworth High-Pass Filter at 120 Hz (48 kHz).
 * Eliminates close-mic proximity boominess, breath plosives, and desk handling rumble.
 */
export class HighPassFilter {
  private readonly b0_1: number;
  private readonly b1_1: number;
  private readonly b2_1: number;
  private readonly a1_1: number;
  private readonly a2_1: number;
  private z1_1 = 0;
  private z2_1 = 0;

  private readonly b0_2: number;
  private readonly b1_2: number;
  private readonly b2_2: number;
  private readonly a1_2: number;
  private readonly a2_2: number;
  private z1_2 = 0;
  private z2_2 = 0;

  constructor(cutoffHz = 120.0, sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    const w0 = (2.0 * Math.PI * cutoffHz) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const sinW0 = Math.sin(w0);

    const alpha1 = sinW0 / (2.0 * 0.5411961);
    const a0_1 = 1.0 + alpha1;
    this.b0_1 = (1.0 + cosW0) / 2.0 / a0_1;
    this.b1_1 = -(1.0 + cosW0) / a0_1;
    this.b2_1 = (1.0 + cosW0) / 2.0 / a0_1;
    this.a1_1 = (-2.0 * cosW0) / a0_1;
    this.a2_1 = (1.0 - alpha1) / a0_1;

    const alpha2 = sinW0 / (2.0 * 1.30656296);
    const a0_2 = 1.0 + alpha2;
    this.b0_2 = (1.0 + cosW0) / 2.0 / a0_2;
    this.b1_2 = -(1.0 + cosW0) / a0_2;
    this.b2_2 = (1.0 + cosW0) / 2.0 / a0_2;
    this.a1_2 = (-2.0 * cosW0) / a0_2;
    this.a2_2 = (1.0 - alpha2) / a0_2;
  }

  reset(): void {
    this.z1_1 = 0;
    this.z2_1 = 0;
    this.z1_2 = 0;
    this.z2_2 = 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    for (let i = 0; i < count; i++) {
      const x = buffer[i];
      const y1 = this.b0_1 * x + this.z1_1;
      this.z1_1 = this.b1_1 * x - this.a1_1 * y1 + this.z2_1;
      this.z2_1 = this.b2_1 * x - this.a2_1 * y1;

      const y2 = this.b0_2 * y1 + this.z1_2;
      this.z1_2 = this.b1_2 * y1 - this.a1_2 * y2 + this.z2_2;
      this.z2_2 = this.b2_2 * y1 - this.a2_2 * y2;

      buffer[i] = y2;
    }
  }
}
