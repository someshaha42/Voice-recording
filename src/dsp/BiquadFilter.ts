/**
 * Reusable zero-allocation 2nd-order RBJ biquad filter.
 */
export class BiquadFilter {
  private b0 = 1;
  private b1 = 0;
  private b2 = 0;
  private a1 = 0;
  private a2 = 0;
  private z1 = 0;
  private z2 = 0;

  reset(): void {
    this.z1 = 0;
    this.z2 = 0;
  }

  process(x: number): number {
    const y = this.b0 * x + this.z1;
    this.z1 = this.b1 * x - this.a1 * y + this.z2;
    this.z2 = this.b2 * x - this.a2 * y;
    return y;
  }

  setBypass(): void {
    this.b0 = 1;
    this.b1 = 0;
    this.b2 = 0;
    this.a1 = 0;
    this.a2 = 0;
  }

  setLowPass(sampleRateHz: number, freqHz: number, q: number): void {
    const clampedFreq = Math.max(20, Math.min(sampleRateHz * 0.45, freqHz));
    const clampedQ = Math.max(0.1, Math.min(10.0, q));
    const w0 = (2.0 * Math.PI * clampedFreq) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const alpha = Math.sin(w0) / (2.0 * clampedQ);
    const a0 = 1.0 + alpha;
    this.b0 = (1.0 - cosW0) / 2.0 / a0;
    this.b1 = (1.0 - cosW0) / a0;
    this.b2 = (1.0 - cosW0) / 2.0 / a0;
    this.a1 = (-2.0 * cosW0) / a0;
    this.a2 = (1.0 - alpha) / a0;
  }

  setHighPass(sampleRateHz: number, freqHz: number, q: number): void {
    const clampedFreq = Math.max(20, Math.min(sampleRateHz * 0.45, freqHz));
    const clampedQ = Math.max(0.1, Math.min(10.0, q));
    const w0 = (2.0 * Math.PI * clampedFreq) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const alpha = Math.sin(w0) / (2.0 * clampedQ);
    const a0 = 1.0 + alpha;
    this.b0 = (1.0 + cosW0) / 2.0 / a0;
    this.b1 = -(1.0 + cosW0) / a0;
    this.b2 = (1.0 + cosW0) / 2.0 / a0;
    this.a1 = (-2.0 * cosW0) / a0;
    this.a2 = (1.0 - alpha) / a0;
  }

  setBandPass(sampleRateHz: number, freqHz: number, q: number): void {
    const clampedFreq = Math.max(20, Math.min(sampleRateHz * 0.45, freqHz));
    const clampedQ = Math.max(0.1, Math.min(10.0, q));
    const w0 = (2.0 * Math.PI * clampedFreq) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const alpha = Math.sin(w0) / (2.0 * clampedQ);
    const a0 = 1.0 + alpha;
    this.b0 = alpha / a0;
    this.b1 = 0;
    this.b2 = -alpha / a0;
    this.a1 = (-2.0 * cosW0) / a0;
    this.a2 = (1.0 - alpha) / a0;
  }

  setPeaking(sampleRateHz: number, freqHz: number, q: number, gainDb: number): void {
    if (Math.abs(gainDb) < 0.02) {
      this.setBypass();
      return;
    }
    const clampedFreq = Math.max(20, Math.min(sampleRateHz * 0.45, freqHz));
    const clampedQ = Math.max(0.1, Math.min(10.0, q));
    const a = Math.pow(10.0, gainDb / 40.0);
    const w0 = (2.0 * Math.PI * clampedFreq) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const alpha = Math.sin(w0) / (2.0 * clampedQ);
    const a0 = 1.0 + alpha / a;
    this.b0 = (1.0 + alpha * a) / a0;
    this.b1 = (-2.0 * cosW0) / a0;
    this.b2 = (1.0 - alpha * a) / a0;
    this.a1 = (-2.0 * cosW0) / a0;
    this.a2 = (1.0 - alpha / a) / a0;
  }

  setLowShelf(sampleRateHz: number, freqHz: number, gainDb: number, q = 0.70710678): void {
    if (Math.abs(gainDb) < 0.02) {
      this.setBypass();
      return;
    }
    const clampedFreq = Math.max(20, Math.min(sampleRateHz * 0.45, freqHz));
    const clampedQ = Math.max(0.2, Math.min(4.0, q));
    const a = Math.pow(10.0, gainDb / 40.0);
    const w0 = (2.0 * Math.PI * clampedFreq) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const sinW0 = Math.sin(w0);
    const alpha = sinW0 / (2.0 * clampedQ);
    const twoSqrtAAlpha = 2.0 * Math.sqrt(a) * alpha;
    const a0 = a + 1.0 + (a - 1.0) * cosW0 + twoSqrtAAlpha;
    this.b0 = (a * (a + 1.0 - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0;
    this.b1 = (2.0 * a * (a - 1.0 - (a + 1.0) * cosW0)) / a0;
    this.b2 = (a * (a + 1.0 - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0;
    this.a1 = (-2.0 * (a - 1.0 + (a + 1.0) * cosW0)) / a0;
    this.a2 = (a + 1.0 + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0;
  }

  setHighShelf(sampleRateHz: number, freqHz: number, gainDb: number, q = 0.70710678): void {
    if (Math.abs(gainDb) < 0.02) {
      this.setBypass();
      return;
    }
    const clampedFreq = Math.max(20, Math.min(sampleRateHz * 0.45, freqHz));
    const clampedQ = Math.max(0.2, Math.min(4.0, q));
    const a = Math.pow(10.0, gainDb / 40.0);
    const w0 = (2.0 * Math.PI * clampedFreq) / sampleRateHz;
    const cosW0 = Math.cos(w0);
    const sinW0 = Math.sin(w0);
    const alpha = sinW0 / (2.0 * clampedQ);
    const twoSqrtAAlpha = 2.0 * Math.sqrt(a) * alpha;
    const a0 = a + 1.0 - (a - 1.0) * cosW0 + twoSqrtAAlpha;
    this.b0 = (a * (a + 1.0 + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0;
    this.b1 = (-2.0 * a * (a - 1.0 + (a + 1.0) * cosW0)) / a0;
    this.b2 = (a * (a + 1.0 + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0;
    this.a1 = (2.0 * (a - 1.0 - (a + 1.0) * cosW0)) / a0;
    this.a2 = (a + 1.0 - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0;
  }

  magnitudeDbAt(sampleRateHz: number, freqHz: number): number {
    const w = (2.0 * Math.PI * freqHz) / sampleRateHz;
    const cosW = Math.cos(w);
    const cos2W = Math.cos(2.0 * w);
    const sinW = Math.sin(w);
    const sin2W = Math.sin(2.0 * w);
    const numRe = this.b0 + this.b1 * cosW + this.b2 * cos2W;
    const numIm = -(this.b1 * sinW + this.b2 * sin2W);
    const denRe = 1.0 + this.a1 * cosW + this.a2 * cos2W;
    const denIm = -(this.a1 * sinW + this.a2 * sin2W);
    const numMagSq = numRe * numRe + numIm * numIm;
    const denMagSq = Math.max(1e-12, denRe * denRe + denIm * denIm);
    const mag = Math.max(1e-6, Math.sqrt(numMagSq / denMagSq));
    return 20.0 * Math.log10(mag);
  }
}
