import { AudioConfig } from '../types/audio';

/**
 * Real-time Packet Loss Concealment (PLC) processor at 48,000 Hz.
 */
export class PacketLossConcealment {
  private readonly historySize: number;
  private readonly historyRing: Float32Array;
  private historyCursor = 0;
  private historyFilled = 0;

  private readonly minPeriodSamples: number;
  private readonly maxPeriodSamples: number;

  private consecutiveLostFrames = 0;
  private recoveryFadeSamplesRemaining = 0;
  private lastConcealedSample = 0;

  public concealmentEventsCount = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.historySize = Math.floor((sampleRateHz * 25) / 1000);
    this.historyRing = new Float32Array(this.historySize);
    this.minPeriodSamples = Math.max(32, Math.floor(sampleRateHz / 400));
    this.maxPeriodSamples = Math.min(Math.floor(this.historySize / 2), Math.floor(sampleRateHz / 75));
  }

  reset(): void {
    this.historyRing.fill(0);
    this.historyCursor = 0;
    this.historyFilled = 0;
    this.consecutiveLostFrames = 0;
    this.recoveryFadeSamplesRemaining = 0;
    this.lastConcealedSample = 0;
  }

  recordValidFrame(buffer: Float32Array, count: number): void {
    if (count <= 0) return;

    if (this.recoveryFadeSamplesRemaining > 0) {
      const fadeLen = Math.min(count, this.recoveryFadeSamplesRemaining);
      const totalFade = 64;
      for (let i = 0; i < fadeLen; i++) {
        const alpha = 1 - Math.max(0, Math.min(1, this.recoveryFadeSamplesRemaining / totalFade));
        buffer[i] = this.lastConcealedSample * (1 - alpha) + buffer[i] * alpha;
        this.lastConcealedSample *= 0.96;
        this.recoveryFadeSamplesRemaining--;
      }
    }

    this.consecutiveLostFrames = 0;
    for (let i = 0; i < count; i++) {
      this.historyRing[this.historyCursor] = buffer[i];
      this.historyCursor = (this.historyCursor + 1) % this.historySize;
      if (this.historyFilled < this.historySize) this.historyFilled++;
    }
  }

  concealPacket(output: Float32Array, count: number): void {
    if (count <= 0) return;
    if (this.consecutiveLostFrames === 0) {
      this.concealmentEventsCount++;
    }
    this.consecutiveLostFrames++;
    this.recoveryFadeSamplesRemaining = 64;

    if (this.historyFilled < this.minPeriodSamples * 2) {
      output.fill(0, 0, count);
      this.lastConcealedSample = 0;
      return;
    }

    const period = this.estimatePitchPeriod();
    let baseAtten = 0.25;
    if (this.consecutiveLostFrames <= 1) baseAtten = 0.92;
    else if (this.consecutiveLostFrames <= 4) baseAtten = 0.78;
    else if (this.consecutiveLostFrames <= 8) baseAtten = 0.55;

    let gain = baseAtten;
    const decayPerSample = this.consecutiveLostFrames > 2 ? 0.997 : 0.9992;

    for (let i = 0; i < count; i++) {
      const offsetFromHead = period - (i % period);
      let idx = this.historyCursor - offsetFromHead;
      while (idx < 0) idx += this.historySize;
      const sample = this.historyRing[idx % this.historySize] * gain;
      output[i] = sample;
      this.historyRing[this.historyCursor] = sample;
      this.historyCursor = (this.historyCursor + 1) % this.historySize;
      this.lastConcealedSample = sample;
      gain *= decayPerSample;
    }
  }

  private estimatePitchPeriod(): number {
    const window = Math.min(this.minPeriodSamples, Math.floor(this.historyFilled / 3));
    if (window < 16) return this.minPeriodSamples;

    let bestLag = this.minPeriodSamples;
    let bestScore = Number.NEGATIVE_INFINITY;
    const maxLag = Math.min(this.maxPeriodSamples, this.historyFilled - window - 1);

    for (let lag = this.minPeriodSamples; lag <= maxLag; lag += 2) {
      let corr = 0;
      let energy = 1e-6;
      for (let k = 0; k < window; k += 2) {
        const i1 = (this.historyCursor - 1 - k + this.historySize) % this.historySize;
        const i2 = (this.historyCursor - 1 - k - lag + this.historySize) % this.historySize;
        const s1 = this.historyRing[i1];
        const s2 = this.historyRing[i2];
        corr += s1 * s2;
        energy += s2 * s2;
      }
      const normalized = corr / Math.sqrt(energy);
      if (normalized > bestScore) {
        bestScore = normalized;
        bestLag = lag;
      }
    }
    return bestLag;
  }
}
