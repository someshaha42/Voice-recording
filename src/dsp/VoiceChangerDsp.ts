import {
  AudioConfig,
  getVoicePresetDefaultConfig,
  VOICE_PRESET_PROFILES,
  VoiceChangerConfig,
  VoiceChangerConstants,
  VoicePreset,
} from '../types/audio';
import { BiquadFilter } from './BiquadFilter';

const RING_SIZE = 4096;
const RING_MASK = RING_SIZE - 1;
const LOOKBACK_SAMPLES = 720;
const OUTPUT_DELAY_SAMPLES = 1480;
const MAX_HALF_WIN = 500;
const YIN_HOP_SAMPLES = 256;
const YIN_WINDOW_DEC = 240;
const YIN_TAU_MIN = 48;
const YIN_TAU_MAX = 320;

export class VoiceChangerDsp {
  private readonly sampleRateHz: number;
  private targetConfig: VoiceChangerConfig = getVoicePresetDefaultConfig(VoicePreset.NORMAL, false);

  private readonly inRing = new Float32Array(RING_SIZE);
  private readonly outOlaRing = new Float32Array(RING_SIZE);
  private readonly outWeightRing = new Float32Array(RING_SIZE);
  private readonly yinDiff = new Float32Array(YIN_TAU_MAX + 2);
  private readonly yinCmndf = new Float32Array(YIN_TAU_MAX + 2);

  private writeCounter = 0;
  private yinHopCounter = 0;
  private nextSynthCenter = 0;
  private lastInputEpoch = -10000;
  private isInitializedStream = false;

  private trackedPeriodSamples = 320;
  private lastCandidatePeriod = 320;
  private octaveConfirmCount = 0;
  private voicingConfidence = 0;

  private smoothPitchRatio = 1.0;
  private smoothFormantRatio = 1.0;
  private smoothToneStrength = 0.5;
  private smoothClarityStrength = 0.5;
  private smoothActiveMix = 0.0;

  private readonly eqChest = new BiquadFilter();
  private readonly eqWarmth = new BiquadFilter();
  private readonly eqPresence = new BiquadFilter();
  private readonly eqAir = new BiquadFilter();

  private readonly eqClaritySub = new BiquadFilter();
  private readonly eqClarityPresence = new BiquadFilter();
  private readonly eqDeEssShelf = new BiquadFilter();

  private lastEqPreset: VoicePreset = VoicePreset.NORMAL;
  private lastEqTone = Number.NaN;
  private lastEqClarity = Number.NaN;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
    this.reset();
  }

  updateConfig(config: VoiceChangerConfig): void {
    this.targetConfig = {
      enabled: config.enabled,
      preset: config.preset,
      pitchLevelPercent: Math.max(
        VoiceChangerConstants.MIN_LEVEL_PCT,
        Math.min(VoiceChangerConstants.MAX_LEVEL_PCT, config.pitchLevelPercent)
      ),
      toneLevelPercent: Math.max(
        VoiceChangerConstants.MIN_LEVEL_PCT,
        Math.min(VoiceChangerConstants.MAX_LEVEL_PCT, config.toneLevelPercent)
      ),
      clarityPercent: Math.max(
        VoiceChangerConstants.MIN_LEVEL_PCT,
        Math.min(VoiceChangerConstants.MAX_LEVEL_PCT, config.clarityPercent)
      ),
    };
  }

  getConfig(): VoiceChangerConfig {
    return this.targetConfig;
  }

  reset(): void {
    this.inRing.fill(0);
    this.outOlaRing.fill(0);
    this.outWeightRing.fill(0);
    this.yinDiff.fill(0);
    this.yinCmndf.fill(0);
    this.writeCounter = 0;
    this.yinHopCounter = 0;
    this.nextSynthCenter = 0;
    this.lastInputEpoch = -10000;
    this.isInitializedStream = false;
    this.trackedPeriodSamples = this.sampleRateHz / 150;
    this.lastCandidatePeriod = this.trackedPeriodSamples;
    this.octaveConfirmCount = 0;
    this.voicingConfidence = 0;

    this.eqChest.reset();
    this.eqWarmth.reset();
    this.eqPresence.reset();
    this.eqAir.reset();
    this.eqClaritySub.reset();
    this.eqClarityPresence.reset();
    this.eqDeEssShelf.reset();

    const cfg = this.targetConfig;
    const profile = VOICE_PRESET_PROFILES[cfg.preset];
    const pFactor = cfg.pitchLevelPercent / 100;
    this.smoothPitchRatio = Math.pow(2, (profile.basePitchSemitones * pFactor) / 12);
    this.smoothFormantRatio = Math.pow(2, (profile.baseFormantSemitones * pFactor) / 12);
    this.smoothToneStrength = cfg.toneLevelPercent / 100;
    this.smoothClarityStrength = cfg.clarityPercent / 100;
    this.smoothActiveMix = cfg.enabled ? 1 : 0;

    this.updatePostFilters(cfg.preset, this.smoothToneStrength, this.smoothClarityStrength, true);
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;

    const cfg = this.targetConfig;
    if (!cfg.enabled && this.smoothActiveMix < 0.0005) {
      this.smoothActiveMix = 0;
      return;
    }

    const profile = VOICE_PRESET_PROFILES[cfg.preset];
    const pitchScale = cfg.pitchLevelPercent / 100;
    const targetPitchRatio = Math.pow(2, (profile.basePitchSemitones * pitchScale) / 12);
    const targetFormantRatio = Math.pow(2, (profile.baseFormantSemitones * pitchScale) / 12);
    const targetToneStrength = cfg.toneLevelPercent / 100;
    const targetClarityStrength = cfg.clarityPercent / 100;
    const targetActiveMix = cfg.enabled ? 1 : 0;

    const paramAlpha = 0.003;
    const fadeAlpha = 0.006;

    for (let i = 0; i < count; i++) {
      this.smoothPitchRatio += paramAlpha * (targetPitchRatio - this.smoothPitchRatio);
      this.smoothFormantRatio += paramAlpha * (targetFormantRatio - this.smoothFormantRatio);
      this.smoothToneStrength += paramAlpha * (targetToneStrength - this.smoothToneStrength);
      this.smoothClarityStrength += paramAlpha * (targetClarityStrength - this.smoothClarityStrength);
      this.smoothActiveMix += fadeAlpha * (targetActiveMix - this.smoothActiveMix);

      const raw = buffer[i];
      const n = this.writeCounter;
      this.inRing[n & RING_MASK] = raw;

      if (!this.isInitializedStream) {
        this.nextSynthCenter = n - LOOKBACK_SAMPLES;
        this.isInitializedStream = true;
      }

      this.yinHopCounter++;
      if (this.yinHopCounter >= YIN_HOP_SAMPLES) {
        this.yinHopCounter = 0;
        if (n >= YIN_WINDOW_DEC * 2 + YIN_TAU_MAX * 2 + 16) {
          this.detectPitchYin(n);
        }
      }

      const curCenter = n - LOOKBACK_SAMPLES;
      while (curCenter >= this.nextSynthCenter && n >= OUTPUT_DELAY_SAMPLES) {
        const stride = this.synthesizeGrain(
          curCenter,
          this.smoothPitchRatio,
          this.smoothFormantRatio
        );
        this.nextSynthCenter += stride;
        if (curCenter - this.nextSynthCenter > stride * 3.0) {
          this.nextSynthCenter = curCenter;
        }
      }

      const outPos = n - OUTPUT_DELAY_SAMPLES;
      const outIdx = outPos & RING_MASK;
      const w = this.outWeightRing[outIdx];
      const synth = w > 1e-4 ? this.outOlaRing[outIdx] / w : 0;
      this.outOlaRing[outIdx] = 0;
      this.outWeightRing[outIdx] = 0;

      const dryDelayed = outPos >= 0 ? this.inRing[outIdx] : 0;
      const isNeutralPitchFormant =
        Math.abs(this.smoothPitchRatio - 1.0) < 0.004 &&
        Math.abs(this.smoothFormantRatio - 1.0) < 0.004;
      const pitchFormantOut = isNeutralPitchFormant ? dryDelayed : synth;

      if (
        cfg.preset !== this.lastEqPreset ||
        Math.abs(this.smoothToneStrength - this.lastEqTone) > 0.015 ||
        Math.abs(this.smoothClarityStrength - this.lastEqClarity) > 0.015
      ) {
        this.updatePostFilters(
          cfg.preset,
          this.smoothToneStrength,
          this.smoothClarityStrength,
          false
        );
      }

      let processed = pitchFormantOut;
      if (this.smoothToneStrength > 0.005) {
        processed = this.eqChest.process(processed);
        processed = this.eqWarmth.process(processed);
        processed = this.eqPresence.process(processed);
        processed = this.eqAir.process(processed);
      }
      if (this.smoothClarityStrength > 0.005) {
        processed = this.eqClaritySub.process(processed);
        processed = this.eqClarityPresence.process(processed);
      }
      if (profile.deEssAmount > 0.01 && this.smoothToneStrength > 0.1) {
        processed = this.eqDeEssShelf.process(processed);
      }

      const active = Math.max(0, Math.min(1, this.smoothActiveMix));
      const blended = active >= 0.999 ? processed : raw * (1 - active) + processed * active;

      buffer[i] = active > 0.001 ? this.softLimit(blended) : raw;
      this.writeCounter = n + 1;
    }
  }

  private detectPitchYin(writeHead: number): void {
    const span = (YIN_WINDOW_DEC + YIN_TAU_MAX) * 2;
    const basePos = writeHead - span;

    let energySum = 0;
    for (let j = 0; j < YIN_WINDOW_DEC; j++) {
      const s = this.inRing[(basePos + j * 2) & RING_MASK];
      energySum += s * s;
    }
    const rms = Math.sqrt(energySum / YIN_WINDOW_DEC);
    if (rms < 0.002) {
      this.voicingConfidence *= 0.8;
      return;
    }

    this.yinDiff[0] = 0;
    for (let tau = 1; tau <= YIN_TAU_MAX; tau++) {
      let acc = 0;
      const lag2 = tau * 2;
      for (let j = 0; j < YIN_WINDOW_DEC; j++) {
        const p1 = (basePos + j * 2) & RING_MASK;
        const p2 = (basePos + j * 2 + lag2) & RING_MASK;
        const diff = this.inRing[p1] - this.inRing[p2];
        acc += diff * diff;
      }
      this.yinDiff[tau] = acc;
    }

    this.yinCmndf[0] = 1;
    let runningSum = 0;
    for (let tau = 1; tau <= YIN_TAU_MAX; tau++) {
      runningSum += this.yinDiff[tau];
      this.yinCmndf[tau] = runningSum > 1e-9 ? (this.yinDiff[tau] * tau) / runningSum : 1;
    }

    let bestTau = -1;
    let bestVal = 10;
    for (let tau = YIN_TAU_MIN; tau < YIN_TAU_MAX; tau++) {
      const v = this.yinCmndf[tau];
      if (v < 0.20 && v <= this.yinCmndf[tau - 1] && v <= this.yinCmndf[tau + 1]) {
        bestTau = tau;
        bestVal = v;
        break;
      }
      if (v < bestVal) {
        bestVal = v;
        bestTau = tau;
      }
    }

    if (bestTau < YIN_TAU_MIN || bestVal > 0.44) {
      this.voicingConfidence = Math.max(0, this.voicingConfidence * 0.7);
      return;
    }

    const s0 = this.yinCmndf[Math.max(1, bestTau - 1)];
    const s1 = this.yinCmndf[bestTau];
    const s2 = this.yinCmndf[Math.min(YIN_TAU_MAX, bestTau + 1)];
    const denom = 2 * (2 * s1 - s2 - s0);
    const delta = Math.abs(denom) > 1e-6 ? Math.max(-0.5, Math.min(0.5, (s0 - s2) / denom)) : 0;
    const detectedSamples48k = Math.max(96, Math.min(640, (bestTau + delta) * 2));

    const conf = Math.max(0, Math.min(1, (0.44 - bestVal) / 0.34));
    this.voicingConfidence += 0.45 * (conf - this.voicingConfidence);

    const ratio = detectedSamples48k / this.trackedPeriodSamples;
    if (ratio >= 0.60 && ratio <= 1.65) {
      this.octaveConfirmCount = 0;
      this.trackedPeriodSamples += 0.40 * (detectedSamples48k - this.trackedPeriodSamples);
    } else {
      if (Math.abs(detectedSamples48k - this.lastCandidatePeriod) / this.lastCandidatePeriod < 0.15) {
        this.octaveConfirmCount++;
        if (this.octaveConfirmCount >= 2) {
          this.trackedPeriodSamples = detectedSamples48k;
          this.octaveConfirmCount = 0;
        }
      } else {
        this.octaveConfirmCount = 1;
      }
      this.lastCandidatePeriod = detectedSamples48k;
    }
  }

  private synthesizeGrain(curCenter: number, pitchRatio: number, formantRatio: number): number {
    const voiced = this.voicingConfidence > 0.28;
    const period = this.trackedPeriodSamples;

    let grainCenter: number;
    let synthStride: number;
    let effectiveFormant: number;
    let halfWin: number;

    if (voiced) {
      if (curCenter - this.lastInputEpoch > period * 1.6 || this.lastInputEpoch > curCenter + period) {
        this.lastInputEpoch = this.findPeakEpoch(curCenter - period * 0.45, curCenter + period * 0.45);
      } else {
        while (this.lastInputEpoch + period * 0.52 < curCenter) {
          const expected = this.lastInputEpoch + period;
          this.lastInputEpoch = this.findPeakEpoch(expected - period * 0.18, expected + period * 0.18);
        }
      }
      grainCenter = this.lastInputEpoch;
      const clampedPitch = Math.max(0.45, Math.min(2.5, pitchRatio));
      synthStride = Math.max(72.0, Math.min(680.0, period / clampedPitch));
      effectiveFormant = formantRatio;
      const minOverlapWin = Math.max(110, synthStride * 1.18);
      const maxSinglePulseWin = (period * 1.05) / Math.max(0.5, effectiveFormant);
      halfWin = Math.max(
        96,
        Math.min(MAX_HALF_WIN, Math.max(minOverlapWin, Math.min(minOverlapWin * 1.1, maxSinglePulseWin)))
      );
    } else {
      grainCenter = curCenter;
      synthStride = 160.0;
      effectiveFormant = 1.0 + (formantRatio - 1.0) * 0.42;
      halfWin = 200;
    }

    const centerOut = Math.round(curCenter);
    const fracOut = curCenter - centerOut;
    const intHalf = Math.floor(halfWin);
    const invHalf = 1.0 / halfWin;

    for (let m = -intHalf; m <= intHalf; m++) {
      const dist = Math.abs(m - fracOut);
      if (dist >= halfWin) continue;

      const w = 0.5 * (1.0 + Math.cos(Math.PI * dist * invHalf));
      const inPos = grainCenter + (m - fracOut) * effectiveFormant;
      const sample = this.sampleCubic(inPos);

      const outIdx = (centerOut + m) & RING_MASK;
      this.outOlaRing[outIdx] += sample * w;
      this.outWeightRing[outIdx] += w;
    }

    return synthStride;
  }

  private findPeakEpoch(startPos: number, endPos: number): number {
    const lo = Math.floor(startPos);
    const hi = Math.floor(endPos);
    let bestPos = (lo + hi) * 0.5;
    let bestScore = -Infinity;

    for (let p = lo; p <= hi; p++) {
      const idx0 = (p - 1) & RING_MASK;
      const idx1 = p & RING_MASK;
      const idx2 = (p + 1) & RING_MASK;
      const v = 0.25 * this.inRing[idx0] + 0.5 * this.inRing[idx1] + 0.25 * this.inRing[idx2];
      if (v > bestScore) {
        bestScore = v;
        bestPos = p;
      }
    }
    return bestPos;
  }

  private sampleCubic(pos: number): number {
    const i1 = Math.floor(pos);
    const t = pos - i1;

    const y0 = this.inRing[(i1 - 1) & RING_MASK];
    const y1 = this.inRing[i1 & RING_MASK];
    const y2 = this.inRing[(i1 + 1) & RING_MASK];
    const y3 = this.inRing[(i1 + 2) & RING_MASK];

    const c0 = y1;
    const c1 = 0.5 * (y2 - y0);
    const c2 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3;
    const c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2);

    return ((c3 * t + c2) * t + c1) * t + c0;
  }

  private updatePostFilters(
    preset: VoicePreset,
    toneStrength: number,
    clarityStrength: number,
    force: boolean
  ): void {
    if (
      !force &&
      preset === this.lastEqPreset &&
      Math.abs(toneStrength - this.lastEqTone) <= 0.015 &&
      Math.abs(clarityStrength - this.lastEqClarity) <= 0.015
    ) {
      return;
    }
    this.lastEqPreset = preset;
    this.lastEqTone = toneStrength;
    this.lastEqClarity = clarityStrength;

    const p = VOICE_PRESET_PROFILES[preset];
    this.eqChest.setPeaking(this.sampleRateHz, p.chestFreqHz, 0.9, p.chestGainDb * toneStrength);
    this.eqWarmth.setPeaking(this.sampleRateHz, p.warmthFreqHz, 1.0, p.warmthGainDb * toneStrength);
    this.eqPresence.setPeaking(this.sampleRateHz, p.presenceFreqHz, 1.1, p.presenceGainDb * toneStrength);
    this.eqAir.setHighShelf(this.sampleRateHz, p.airFreqHz, p.airGainDb * toneStrength);

    const deEssDb = -2.8 * p.deEssAmount * toneStrength;
    this.eqDeEssShelf.setPeaking(this.sampleRateHz, 6400.0, 1.8, deEssDb);

    this.eqClaritySub.setLowShelf(this.sampleRateHz, 130.0, -1.8 * clarityStrength);
    this.eqClarityPresence.setPeaking(this.sampleRateHz, 2850.0, 0.85, 3.2 * clarityStrength);
  }

  private softLimit(x: number): number {
    const absX = Math.abs(x);
    if (absX <= 0.85) return x;
    const excess = (absX - 0.85) / 0.10;
    const limited = 0.85 + 0.10 * Math.tanh(excess);
    return x >= 0 ? limited : -limited;
  }
}
