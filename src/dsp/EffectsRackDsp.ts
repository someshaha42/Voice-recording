import {
  AudioConfig,
  createDefaultEffectsRack,
  defaultEcho,
  defaultEqualizer,
  defaultHall,
  defaultHighPass,
  defaultLowPass,
  defaultReverb,
  EchoConfig,
  EffectsRackConfig,
  EqualizerConfig,
  FilterSlope,
  getEffectiveDelayMs,
  getVoicePresetDefaultConfig,
  HallConfig,
  HighPassConfig,
  LowPassConfig,
  ReverbConfig,
  VoiceChangerConfig,
  VoicePreset,
} from '../types/audio';
import { BiquadFilter } from './BiquadFilter';
import { NoiseGateProcessor } from './NoiseGateProcessor';
import {
  CompressionProcessor,
  DeEsserProcessor,
  LimiterProcessor,
  PitchCorrectionProcessor,
  PitchFormantProcessor,
  SaturationProcessor,
} from './StudioProcessors';
import { VoiceChangerDsp } from './VoiceChangerDsp';

function softClip(x: number): number {
  const ax = Math.abs(x);
  if (ax <= 0.88) return x;
  const s = 0.88 + 0.1 * Math.tanh((ax - 0.88) / 0.1);
  return x >= 0 ? s : -s;
}

class CombFilter {
  private readonly buffer: Float32Array;
  private idx = 0;
  private filterStore = 0;

  constructor(size: number) {
    this.buffer = new Float32Array(size);
  }

  reset(): void {
    this.buffer.fill(0);
    this.idx = 0;
    this.filterStore = 0;
  }

  process(input: number, feedback: number, damp: number): number {
    const output = this.buffer[this.idx];
    this.filterStore = output * (1 - damp) + this.filterStore * damp;
    this.buffer[this.idx] = input + this.filterStore * feedback;
    this.idx++;
    if (this.idx >= this.buffer.length) this.idx = 0;
    return output;
  }
}

class AllpassFilter {
  private readonly buffer: Float32Array;
  private idx = 0;

  constructor(size: number) {
    this.buffer = new Float32Array(size);
  }

  reset(): void {
    this.buffer.fill(0);
    this.idx = 0;
  }

  process(input: number): number {
    const bufOut = this.buffer[this.idx];
    const output = -input + bufOut;
    this.buffer[this.idx] = input + bufOut * 0.5;
    this.idx++;
    if (this.idx >= this.buffer.length) this.idx = 0;
    return output;
  }
}

class LowPassEffect {
  private readonly sampleRateHz: number;
  private config: LowPassConfig = defaultLowPass(false);
  private readonly stage1 = new BiquadFilter();
  private readonly stage2 = new BiquadFilter();
  private lastCutoff = -1;
  private lastRes = -1;
  private smoothActive = 0;

  constructor(sampleRateHz: number) {
    this.sampleRateHz = sampleRateHz;
  }

  updateConfig(cfg: LowPassConfig): void {
    this.config = {
      ...cfg,
      mixPercent: Math.max(0, Math.min(100, cfg.mixPercent)),
      cutoffHz: Math.max(200, Math.min(20000, cfg.cutoffHz)),
      resonance: Math.max(0.2, Math.min(4.0, cfg.resonance)),
    };
  }

  reset(): void {
    this.stage1.reset();
    this.stage2.reset();
    this.lastCutoff = -1;
    this.lastRes = -1;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }
    if (Math.abs(cfg.cutoffHz - this.lastCutoff) > 1 || Math.abs(cfg.resonance - this.lastRes) > 0.01) {
      this.lastCutoff = cfg.cutoffHz;
      this.lastRes = cfg.resonance;
      this.stage1.setLowPass(this.sampleRateHz, cfg.cutoffHz, cfg.resonance);
      this.stage2.setLowPass(this.sampleRateHz, cfg.cutoffHz, cfg.resonance);
    }
    const targetActive = cfg.enabled ? 1 : 0;
    const mix = cfg.mixPercent / 100;
    const is24Db = cfg.slope === FilterSlope.SLOPE_24;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.008 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      let wet = this.stage1.process(dry);
      if (is24Db) {
        wet = this.stage2.process(wet);
      }
      const effMix = mix * this.smoothActive;
      buffer[i] = dry * (1 - effMix) + wet * effMix;
    }
  }
}

class HighPassEffect {
  private readonly sampleRateHz: number;
  private config: HighPassConfig = defaultHighPass(false);
  private readonly stage1 = new BiquadFilter();
  private readonly stage2 = new BiquadFilter();
  private lastCutoff = -1;
  private lastRes = -1;
  private smoothActive = 0;

  constructor(sampleRateHz: number) {
    this.sampleRateHz = sampleRateHz;
  }

  updateConfig(cfg: HighPassConfig): void {
    this.config = {
      ...cfg,
      mixPercent: Math.max(0, Math.min(100, cfg.mixPercent)),
      cutoffHz: Math.max(20, Math.min(2000, cfg.cutoffHz)),
      resonance: Math.max(0.2, Math.min(4.0, cfg.resonance)),
    };
  }

  reset(): void {
    this.stage1.reset();
    this.stage2.reset();
    this.lastCutoff = -1;
    this.lastRes = -1;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }
    if (Math.abs(cfg.cutoffHz - this.lastCutoff) > 0.5 || Math.abs(cfg.resonance - this.lastRes) > 0.01) {
      this.lastCutoff = cfg.cutoffHz;
      this.lastRes = cfg.resonance;
      this.stage1.setHighPass(this.sampleRateHz, cfg.cutoffHz, cfg.resonance);
      this.stage2.setHighPass(this.sampleRateHz, cfg.cutoffHz, cfg.resonance);
    }
    const targetActive = cfg.enabled ? 1 : 0;
    const mix = cfg.mixPercent / 100;
    const is24Db = cfg.slope === FilterSlope.SLOPE_24;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.008 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      let wet = this.stage1.process(dry);
      if (is24Db) {
        wet = this.stage2.process(wet);
      }
      const effMix = mix * this.smoothActive;
      buffer[i] = dry * (1 - effMix) + wet * effMix;
    }
  }
}

export class EqualizerEffect {
  private readonly sampleRateHz: number;
  private config: EqualizerConfig = defaultEqualizer(false);
  private readonly lowBand = new BiquadFilter();
  private readonly mid1Band = new BiquadFilter();
  private readonly mid2Band = new BiquadFilter();
  private readonly mid3Band = new BiquadFilter();
  private readonly highBand = new BiquadFilter();
  private lastApplied: EqualizerConfig | null = null;
  private smoothActive = 0;

  constructor(sampleRateHz: number) {
    this.sampleRateHz = sampleRateHz;
  }

  updateConfig(cfg: EqualizerConfig): void {
    this.config = {
      ...cfg,
      mixPercent: Math.max(0, Math.min(100, cfg.mixPercent)),
      lowFreqHz: Math.max(20, Math.min(500, cfg.lowFreqHz)),
      lowGainDb: Math.max(-12, Math.min(12, cfg.lowGainDb)),
      lowQ: Math.max(0.2, Math.min(4, cfg.lowQ)),
      mid1FreqHz: Math.max(100, Math.min(1500, cfg.mid1FreqHz)),
      mid1GainDb: Math.max(-12, Math.min(12, cfg.mid1GainDb)),
      mid1Q: Math.max(0.1, Math.min(10, cfg.mid1Q)),
      mid2FreqHz: Math.max(300, Math.min(5000, cfg.mid2FreqHz)),
      mid2GainDb: Math.max(-12, Math.min(12, cfg.mid2GainDb)),
      mid2Q: Math.max(0.1, Math.min(10, cfg.mid2Q)),
      mid3FreqHz: Math.max(1000, Math.min(12000, cfg.mid3FreqHz)),
      mid3GainDb: Math.max(-12, Math.min(12, cfg.mid3GainDb)),
      mid3Q: Math.max(0.1, Math.min(10, cfg.mid3Q)),
      highFreqHz: Math.max(2000, Math.min(16000, cfg.highFreqHz)),
      highGainDb: Math.max(-12, Math.min(12, cfg.highGainDb)),
      highQ: Math.max(0.2, Math.min(4, cfg.highQ)),
    };
  }

  reset(): void {
    this.lowBand.reset();
    this.mid1Band.reset();
    this.mid2Band.reset();
    this.mid3Band.reset();
    this.highBand.reset();
    this.lastApplied = null;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }
    if (cfg !== this.lastApplied) {
      this.lastApplied = cfg;
      this.lowBand.setLowShelf(this.sampleRateHz, cfg.lowFreqHz, cfg.lowGainDb, cfg.lowQ);
      this.mid1Band.setPeaking(this.sampleRateHz, cfg.mid1FreqHz, cfg.mid1Q, cfg.mid1GainDb);
      this.mid2Band.setPeaking(this.sampleRateHz, cfg.mid2FreqHz, cfg.mid2Q, cfg.mid2GainDb);
      this.mid3Band.setPeaking(this.sampleRateHz, cfg.mid3FreqHz, cfg.mid3Q, cfg.mid3GainDb);
      this.highBand.setHighShelf(this.sampleRateHz, cfg.highFreqHz, cfg.highGainDb, cfg.highQ);
    }
    const targetActive = cfg.enabled ? 1 : 0;
    const mix = cfg.mixPercent / 100;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.008 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      let wet = this.lowBand.process(dry);
      wet = this.mid1Band.process(wet);
      wet = this.mid2Band.process(wet);
      wet = this.mid3Band.process(wet);
      wet = this.highBand.process(wet);
      const effMix = mix * this.smoothActive;
      const out = dry * (1 - effMix) + wet * effMix;
      buffer[i] = softClip(out);
    }
  }

  static computeMagnitudeCurveDb(
    cfg: EqualizerConfig,
    frequenciesHz: Float32Array,
    sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
  ): Float32Array {
    const f1 = new BiquadFilter();
    f1.setLowShelf(sampleRateHz, cfg.lowFreqHz, cfg.lowGainDb, cfg.lowQ);
    const f2 = new BiquadFilter();
    f2.setPeaking(sampleRateHz, cfg.mid1FreqHz, cfg.mid1Q, cfg.mid1GainDb);
    const f3 = new BiquadFilter();
    f3.setPeaking(sampleRateHz, cfg.mid2FreqHz, cfg.mid2Q, cfg.mid2GainDb);
    const f4 = new BiquadFilter();
    f4.setPeaking(sampleRateHz, cfg.mid3FreqHz, cfg.mid3Q, cfg.mid3GainDb);
    const f5 = new BiquadFilter();
    f5.setHighShelf(sampleRateHz, cfg.highFreqHz, cfg.highGainDb, cfg.highQ);

    const mixScale = cfg.mixPercent / 100;
    const out = new Float32Array(frequenciesHz.length);
    for (let idx = 0; idx < frequenciesHz.length; idx++) {
      const f = frequenciesHz[idx];
      const sumDb =
        f1.magnitudeDbAt(sampleRateHz, f) +
        f2.magnitudeDbAt(sampleRateHz, f) +
        f3.magnitudeDbAt(sampleRateHz, f) +
        f4.magnitudeDbAt(sampleRateHz, f) +
        f5.magnitudeDbAt(sampleRateHz, f);
      out[idx] = Math.max(-15, Math.min(15, sumDb * mixScale));
    }
    return out;
  }
}

class EchoEffect {
  private readonly sampleRateHz: number;
  private config: EchoConfig = defaultEcho(false);
  private readonly maxSamples: number;
  private readonly delayRing: Float32Array;
  private writePos = 0;
  private currentDelaySamples: number;
  private smoothActive = 0;

  private readonly lowCutFilter = new BiquadFilter();
  private readonly highCutFilter = new BiquadFilter();
  private readonly pingPongAltFilter = new BiquadFilter();
  private lastLowCut = -1;
  private lastHighCut = -1;

  constructor(sampleRateHz: number) {
    this.sampleRateHz = sampleRateHz;
    this.maxSamples = Math.floor((sampleRateHz * 1050) / 1000);
    this.delayRing = new Float32Array(this.maxSamples);
    this.currentDelaySamples = sampleRateHz * 0.25;
    this.pingPongAltFilter.setLowPass(sampleRateHz, 3400.0, 0.707);
  }

  updateConfig(cfg: EchoConfig): void {
    this.config = {
      ...cfg,
      mixPercent: Math.max(0, Math.min(100, cfg.mixPercent)),
      timeMs: Math.max(20, Math.min(1000, cfg.timeMs)),
      feedbackPercent: Math.max(0, Math.min(90, cfg.feedbackPercent)),
      echoLevelPercent: Math.max(0, Math.min(100, cfg.echoLevelPercent)),
      wetPercent: Math.max(0, Math.min(100, cfg.wetPercent)),
      dryPercent: Math.max(0, Math.min(100, cfg.dryPercent)),
      widthPercent: Math.max(0, Math.min(100, cfg.widthPercent)),
      bpm: Math.max(60, Math.min(200, cfg.bpm)),
      lowCutHz: Math.max(20, Math.min(1000, cfg.lowCutHz)),
      highCutHz: Math.max(1000, Math.min(18000, cfg.highCutHz)),
    };
  }

  reset(): void {
    this.delayRing.fill(0);
    this.writePos = 0;
    this.currentDelaySamples = (getEffectiveDelayMs(this.config) * this.sampleRateHz) / 1000;
    this.lowCutFilter.reset();
    this.highCutFilter.reset();
    this.pingPongAltFilter.reset();
    this.lastLowCut = -1;
    this.lastHighCut = -1;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }
    if (Math.abs(cfg.lowCutHz - this.lastLowCut) > 1) {
      this.lastLowCut = cfg.lowCutHz;
      this.lowCutFilter.setHighPass(this.sampleRateHz, cfg.lowCutHz, 0.707);
    }
    if (Math.abs(cfg.highCutHz - this.lastHighCut) > 25) {
      this.lastHighCut = cfg.highCutHz;
      this.highCutFilter.setLowPass(this.sampleRateHz, cfg.highCutHz, 0.707);
    }

    const targetActive = cfg.enabled ? 1 : 0;
    const targetDelaySamples = Math.max(
      24,
      Math.min(this.maxSamples - 4, (getEffectiveDelayMs(cfg) * this.sampleRateHz) / 1000)
    );
    const feedback = Math.max(0, Math.min(0.9, cfg.feedbackPercent / 100));
    const wetGain = (cfg.wetPercent / 100) * 0.7 + (cfg.echoLevelPercent / 100) * 0.3;
    const dryGain = cfg.dryPercent / 100;
    const widthNorm = cfg.widthPercent / 100;
    const mix = cfg.mixPercent / 100;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      this.currentDelaySamples += 0.002 * (targetDelaySamples - this.currentDelaySamples);

      const dry = buffer[i];
      let readPos = this.writePos - this.currentDelaySamples;
      if (readPos < 0) readPos += this.maxSamples;
      const idx0 = Math.floor(readPos) % this.maxSamples;
      const idx1 = (idx0 + 1) % this.maxSamples;
      const frac = readPos - Math.floor(readPos);
      const delayed = this.delayRing[idx0] * (1 - frac) + this.delayRing[idx1] * frac;

      let tapProcessed = delayed;
      if (cfg.pingPong || widthNorm > 0.1) {
        const secondaryPos =
          (this.writePos - Math.floor(this.currentDelaySamples * 0.5) + this.maxSamples) %
          this.maxSamples;
        const softBounce =
          this.pingPongAltFilter.process(this.delayRing[secondaryPos]) * (0.45 * widthNorm);
        const primaryWeight = cfg.pingPong ? 0.75 : 1.0 - 0.15 * widthNorm;
        tapProcessed = delayed * primaryWeight + softBounce;
      }

      const fbSignal = this.highCutFilter.process(this.lowCutFilter.process(delayed * feedback));
      this.delayRing[this.writePos] = cfg.enabled ? softClip(dry + fbSignal) : fbSignal * 0.95;

      this.writePos++;
      if (this.writePos >= this.maxSamples) this.writePos = 0;

      const wet = dry * dryGain + tapProcessed * wetGain;
      const effMix = mix * this.smoothActive;
      buffer[i] = softClip(dry * (1 - effMix) + wet * effMix);
    }
  }
}

class ReverbEffect {
  private readonly sampleRateHz: number;
  private config: ReverbConfig = defaultReverb(false);
  private readonly combs: CombFilter[];
  private readonly combRight: CombFilter[];
  private readonly allpasses: AllpassFilter[];
  private readonly maxPreDelaySamples: number;
  private readonly preDelayRing: Float32Array;
  private preDelayWrite = 0;
  private smoothActive = 0;

  constructor(sampleRateHz: number) {
    this.sampleRateHz = sampleRateHz;
    const combTunings = [1214, 1293, 1390, 1478, 1546, 1622, 1694, 1765];
    const allpassTunings = [605, 480, 371, 245];
    this.combs = combTunings.map((t) => new CombFilter(t));
    this.combRight = combTunings.slice(0, 4).map((t) => new CombFilter(t + 25));
    this.allpasses = allpassTunings.map((t) => new AllpassFilter(t));
    this.maxPreDelaySamples = Math.floor((sampleRateHz * 110) / 1000);
    this.preDelayRing = new Float32Array(this.maxPreDelaySamples);
  }

  updateConfig(cfg: ReverbConfig): void {
    this.config = {
      ...cfg,
      mixPercent: Math.max(0, Math.min(100, cfg.mixPercent)),
      roomSizePercent: Math.max(0, Math.min(100, cfg.roomSizePercent)),
      decayTimeSec: Math.max(0.2, Math.min(8.0, cfg.decayTimeSec)),
      dampingPercent: Math.max(0, Math.min(100, cfg.dampingPercent)),
      wetPercent: Math.max(0, Math.min(100, cfg.wetPercent)),
      dryPercent: Math.max(0, Math.min(100, cfg.dryPercent)),
      widthPercent: Math.max(0, Math.min(100, cfg.widthPercent)),
      preDelayMs: Math.max(0, Math.min(100, cfg.preDelayMs)),
    };
  }

  reset(): void {
    this.combs.forEach((c) => c.reset());
    this.combRight.forEach((c) => c.reset());
    this.allpasses.forEach((a) => a.reset());
    this.preDelayRing.fill(0);
    this.preDelayWrite = 0;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }

    const targetActive = cfg.enabled ? 1 : 0;
    const sizeNorm = cfg.roomSizePercent / 100;
    const decayNorm = Math.max(0, Math.min(1, (cfg.decayTimeSec - 0.2) / 7.8));
    const feedback = Math.max(0.6, Math.min(0.965, 0.62 + sizeNorm * 0.16 + decayNorm * 0.19));
    const damp = (cfg.dampingPercent / 100) * 0.45;
    const wetGain = (cfg.wetPercent / 100) * 0.35;
    const dryGain = cfg.dryPercent / 100;
    const width = (cfg.widthPercent / 100) * 0.35;
    const mix = cfg.mixPercent / 100;
    const preDelaySamples = Math.max(
      0,
      Math.min(this.maxPreDelaySamples - 2, Math.round((cfg.preDelayMs * this.sampleRateHz) / 1000))
    );

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      const dry = buffer[i];

      this.preDelayRing[this.preDelayWrite] = cfg.enabled ? dry : 0;
      const preRead =
        (this.preDelayWrite - preDelaySamples + this.maxPreDelaySamples) %
        this.maxPreDelaySamples;
      const delayedIn = this.preDelayRing[preRead];
      this.preDelayWrite++;
      if (this.preDelayWrite >= this.maxPreDelaySamples) this.preDelayWrite = 0;

      const input = delayedIn * 0.18;
      let out = 0;
      for (let c = 0; c < this.combs.length; c++) {
        out += this.combs[c].process(input, feedback, damp);
      }
      if (width > 0.01) {
        let wTap = 0;
        for (let c = 0; c < this.combRight.length; c++) {
          wTap += this.combRight[c].process(input, feedback, damp);
        }
        out = out * (1 - width * 0.4) + wTap * width;
      }
      for (let a = 0; a < this.allpasses.length; a++) {
        out = this.allpasses[a].process(out);
      }

      const wetMixSignal = dry * dryGain + out * wetGain;
      const effMix = mix * this.smoothActive;
      buffer[i] = softClip(dry * (1 - effMix) + wetMixSignal * effMix);
    }
  }
}

class HallEffect {
  private readonly sampleRateHz: number;
  private config: HallConfig = defaultHall(false);
  private readonly combs: CombFilter[];
  private readonly allpasses: AllpassFilter[];
  private readonly warmthLpf = new BiquadFilter();
  private readonly echoRingSize: number;
  private readonly echoRing: Float32Array;
  private echoWrite = 0;
  private smoothActive = 0;

  constructor(sampleRateHz: number) {
    this.sampleRateHz = sampleRateHz;
    this.combs = [1557, 1689, 1843, 2011, 2179, 2347].map((t) => new CombFilter(t));
    this.allpasses = [719, 541, 389].map((t) => new AllpassFilter(t));
    this.warmthLpf.setLowPass(sampleRateHz, 4200.0, 0.707);
    this.echoRingSize = Math.floor((sampleRateHz * 320) / 1000);
    this.echoRing = new Float32Array(this.echoRingSize);
  }

  updateConfig(cfg: HallConfig): void {
    this.config = {
      ...cfg,
      echoAmount: Math.max(0, Math.min(100, cfg.echoAmount)),
      reverberationAmount: Math.max(0, Math.min(100, cfg.reverberationAmount)),
    };
  }

  reset(): void {
    this.combs.forEach((c) => c.reset());
    this.allpasses.forEach((a) => a.reset());
    this.warmthLpf.reset();
    this.echoRing.fill(0);
    this.echoWrite = 0;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }

    const targetActive = cfg.enabled ? 1 : 0;
    const echoNorm = cfg.echoAmount / 100;
    const reverbNorm = cfg.reverberationAmount / 100;
    const feedback = 0.7 + reverbNorm * 0.25;
    const damp = 0.22 + (1 - reverbNorm) * 0.25;
    const tap1 = Math.floor((this.sampleRateHz * 145) / 1000);
    const tap2 = Math.floor((this.sampleRateHz * 245) / 1000);

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      const dry = buffer[i];

      const r1 = this.echoRing[(this.echoWrite - tap1 + this.echoRingSize) % this.echoRingSize];
      const r2 = this.echoRing[(this.echoWrite - tap2 + this.echoRingSize) % this.echoRingSize];
      const hallEcho = r1 * 0.6 + r2 * 0.4;

      this.echoRing[this.echoWrite] = cfg.enabled
        ? softClip(dry + hallEcho * (0.2 + echoNorm * 0.3))
        : hallEcho * 0.5;
      this.echoWrite++;
      if (this.echoWrite >= this.echoRingSize) this.echoWrite = 0;

      const revIn = (cfg.enabled ? dry : 0) * 0.16 + hallEcho * 0.1;
      let revOut = 0;
      for (let c = 0; c < this.combs.length; c++) {
        revOut += this.combs[c].process(revIn, feedback, damp);
      }
      for (let a = 0; a < this.allpasses.length; a++) {
        revOut = this.allpasses[a].process(revOut);
      }
      revOut = this.warmthLpf.process(revOut);

      const wet = dry + hallEcho * (echoNorm * 0.45) + revOut * (reverbNorm * 0.45);
      buffer[i] = softClip(dry * (1 - this.smoothActive) + wet * this.smoothActive);
    }
  }
}

export class StudioDspPipeline {
  public readonly noiseGate: NoiseGateProcessor;
  public readonly pitchCorrection: PitchCorrectionProcessor;
  public readonly voiceChanger: VoiceChangerDsp;
  public readonly pitchFormant: PitchFormantProcessor;
  private readonly highPass: HighPassEffect;
  private readonly lowPass: LowPassEffect;
  private readonly equalizer: EqualizerEffect;
  private readonly deEsser: DeEsserProcessor;
  public readonly compression: CompressionProcessor;
  private readonly saturation: SaturationProcessor;
  private readonly echo: EchoEffect;
  private readonly reverb: ReverbEffect;
  private readonly hall: HallEffect;
  private readonly limiter: LimiterProcessor;

  private effectsConfig: EffectsRackConfig = createDefaultEffectsRack();
  private voiceChangerConfig: VoiceChangerConfig = getVoicePresetDefaultConfig(
    VoicePreset.NORMAL,
    false
  );

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.noiseGate = new NoiseGateProcessor(sampleRateHz);
    this.pitchCorrection = new PitchCorrectionProcessor(sampleRateHz);
    this.voiceChanger = new VoiceChangerDsp(sampleRateHz);
    this.pitchFormant = new PitchFormantProcessor(sampleRateHz);
    this.highPass = new HighPassEffect(sampleRateHz);
    this.lowPass = new LowPassEffect(sampleRateHz);
    this.equalizer = new EqualizerEffect(sampleRateHz);
    this.deEsser = new DeEsserProcessor(sampleRateHz);
    this.compression = new CompressionProcessor(sampleRateHz);
    this.saturation = new SaturationProcessor(sampleRateHz);
    this.echo = new EchoEffect(sampleRateHz);
    this.reverb = new ReverbEffect(sampleRateHz);
    this.hall = new HallEffect(sampleRateHz);
    this.limiter = new LimiterProcessor(sampleRateHz);
  }

  updateEffectsConfig(newConfig: EffectsRackConfig): void {
    this.effectsConfig = newConfig;
    this.noiseGate.updateConfig(newConfig.noiseGate);
    this.pitchCorrection.updateConfig(newConfig.pitchCorrection);
    this.pitchFormant.updateConfig(newConfig.pitchFormant);
    this.highPass.updateConfig(newConfig.highPass);
    this.lowPass.updateConfig(newConfig.lowPass);
    this.equalizer.updateConfig(newConfig.equalizer);
    this.deEsser.updateConfig(newConfig.deEsser);
    this.compression.updateConfig(newConfig.compression);
    this.saturation.updateConfig(newConfig.saturation);
    this.echo.updateConfig(newConfig.echo);
    this.reverb.updateConfig(newConfig.reverb);
    this.hall.updateConfig(newConfig.hall);
    this.limiter.updateConfig(newConfig.limiter);
  }

  updateVoiceChangerConfig(newConfig: VoiceChangerConfig): void {
    this.voiceChangerConfig = newConfig;
    this.voiceChanger.updateConfig(newConfig);
  }

  getEffectsConfig(): EffectsRackConfig {
    return this.effectsConfig;
  }

  getVoiceChangerConfig(): VoiceChangerConfig {
    return this.voiceChangerConfig;
  }

  reset(): void {
    this.noiseGate.reset();
    this.pitchCorrection.reset();
    this.voiceChanger.reset();
    this.pitchFormant.reset();
    this.highPass.reset();
    this.lowPass.reset();
    this.equalizer.reset();
    this.deEsser.reset();
    this.compression.reset();
    this.saturation.reset();
    this.echo.reset();
    this.reverb.reset();
    this.hall.reset();
    this.limiter.reset();
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    this.noiseGate.processInPlace(buffer, count);
    this.pitchCorrection.processInPlace(buffer, count);
    this.voiceChanger.processInPlace(buffer, count);
    this.pitchFormant.processInPlace(buffer, count);
    this.highPass.processInPlace(buffer, count);
    this.lowPass.processInPlace(buffer, count);
    this.equalizer.processInPlace(buffer, count);
    this.deEsser.processInPlace(buffer, count);
    this.compression.processInPlace(buffer, count);
    this.saturation.processInPlace(buffer, count);
    this.echo.processInPlace(buffer, count);
    this.reverb.processInPlace(buffer, count);
    this.hall.processInPlace(buffer, count);
    this.limiter.processInPlace(buffer, count);
  }
}
