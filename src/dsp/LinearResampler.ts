import { AudioConfig } from '../types/audio';

export class LinearResampler {
  private readonly isPassThrough: boolean;
  private readonly ratio: number;
  private s0 = 0;
  private s1 = 0;
  private s2 = 0;
  private s3 = 0;
  private phase = 0.0;

  private readonly needsLowPass: boolean;
  private b0 = 1;
  private b1 = 0;
  private b2 = 0;
  private a1 = 0;
  private a2 = 0;
  private z1 = 0;
  private z2 = 0;

  constructor(
    inputSampleRateHz: number,
    outputSampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
  ) {
    this.isPassThrough = inputSampleRateHz === outputSampleRateHz;
    this.ratio = inputSampleRateHz / outputSampleRateHz;
    this.needsLowPass = inputSampleRateHz > outputSampleRateHz;

    if (this.needsLowPass) {
      const cutoff = Math.min(20_500.0, outputSampleRateHz * 0.43);
      const w0 = (2.0 * Math.PI * cutoff) / inputSampleRateHz;
      const cosW0 = Math.cos(w0);
      const alpha = Math.sin(w0) / (2.0 * 0.70710678);
      const a0 = 1.0 + alpha;
      this.b0 = (1.0 - cosW0) / 2.0 / a0;
      this.b1 = (1.0 - cosW0) / a0;
      this.b2 = (1.0 - cosW0) / 2.0 / a0;
      this.a1 = (-2.0 * cosW0) / a0;
      this.a2 = (1.0 - alpha) / a0;
    }
  }

  reset(): void {
    this.s0 = 0;
    this.s1 = 0;
    this.s2 = 0;
    this.s3 = 0;
    this.phase = 0.0;
    this.z1 = 0;
    this.z2 = 0;
  }

  process(input: Float32Array, inputCount: number, output: Float32Array): number {
    if (inputCount <= 0) return 0;
    if (this.isPassThrough) {
      const count = Math.min(inputCount, output.length);
      output.set(input.subarray(0, count), 0);
      return count;
    }

    let outIndex = 0;
    let inIndex = 0;

    while (inIndex < inputCount && outIndex < output.length) {
      while (this.phase >= 1.0 && inIndex < inputCount) {
        const raw = input[inIndex++];
        let filtered = raw;
        if (this.needsLowPass) {
          const y = this.b0 * raw + this.z1;
          this.z1 = this.b1 * raw - this.a1 * y + this.z2;
          this.z2 = this.b2 * raw - this.a2 * y;
          filtered = y;
        }
        this.s0 = this.s1;
        this.s1 = this.s2;
        this.s2 = this.s3;
        this.s3 = filtered;
        this.phase -= 1.0;
      }

      if (this.phase < 1.0 && outIndex < output.length) {
        const t = this.phase;
        const c0 = this.s1;
        const c1 = 0.5 * (this.s2 - this.s0);
        const c2 = this.s0 - 2.5 * this.s1 + 2.0 * this.s2 - 0.5 * this.s3;
        const c3 = 0.5 * (this.s3 - this.s0) + 1.5 * (this.s1 - this.s2);
        output[outIndex++] = ((c3 * t + c2) * t + c1) * t + c0;
        this.phase += this.ratio;
      }
    }

    while (inIndex < inputCount) {
      const raw = input[inIndex++];
      let filtered = raw;
      if (this.needsLowPass) {
        const y = this.b0 * raw + this.z1;
        this.z1 = this.b1 * raw - this.a1 * y + this.z2;
        this.z2 = this.b2 * raw - this.a2 * y;
        filtered = y;
      }
      this.s0 = this.s1;
      this.s1 = this.s2;
      this.s2 = this.s3;
      this.s3 = filtered;
      this.phase -= 1.0;
      while (this.phase < 1.0 && outIndex < output.length) {
        const t = Math.max(0, Math.min(1, this.phase));
        const c0 = this.s1;
        const c1 = 0.5 * (this.s2 - this.s0);
        const c2 = this.s0 - 2.5 * this.s1 + 2.0 * this.s2 - 0.5 * this.s3;
        const c3 = 0.5 * (this.s3 - this.s0) + 1.5 * (this.s1 - this.s2);
        output[outIndex++] = ((c3 * t + c2) * t + c1) * t + c0;
        this.phase += this.ratio;
      }
    }

    if (Math.floor(this.phase) < 0) {
      this.phase = 0.0;
    }
    return outIndex;
  }
}

export function resampleMonoArray(
  input: Float32Array,
  inputSampleRateHz: number,
  outputSampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
): Float32Array {
  if (inputSampleRateHz === outputSampleRateHz) {
    return input;
  }
  const estimatedOut = Math.ceil((input.length * outputSampleRateHz) / inputSampleRateHz) + 64;
  const output = new Float32Array(estimatedOut);
  const resampler = new LinearResampler(inputSampleRateHz, outputSampleRateHz);
  const written = resampler.process(input, input.length, output);
  return output.subarray(0, written);
}
