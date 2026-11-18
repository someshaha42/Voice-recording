import {
  AudioConfig,
  CompressionConfig,
  DeEsserConfig,
  defaultCompression,
  defaultDeEsser,
  defaultLimiter,
  defaultPitchCorrection,
  defaultPitchFormant,
  defaultSaturation,
  LimiterConfig,
  MUSICAL_KEY_META,
  MUSICAL_SCALE_META,
  MusicalKey,
  MusicalScale,
  PitchCorrectionConfig,
  PitchFormantConfig,
  SaturationConfig,
} from '../types/audio';
import { BiquadFilter } from './BiquadFilter';

const RING_SIZE = 4096;
const RING_MASK = RING_SIZE - 1;
const LOOKBACK_SAMPLES = 720;
const OUTPUT_DELAY_SAMPLES = 1480;
const YIN_HOP_SAMPLES = 256;
const YIN_WINDOW_DEC = 240;
const YIN_TAU_MIN = 44;
const YIN_TAU_MAX = 320;
const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
const ALL_KEYS = Object.values(MusicalKey) as MusicalKey[];

export class PitchCorrectionProcessor {
  private readonly sampleRateHz: number;
  private config: PitchCorrectionConfig = defaultPitchCorrection(false);

  public lastDetectedNoteLabel = '--';
  public lastAutoDetectedKey: MusicalKey = MusicalKey.C;

  private readonly inRing = new Float32Array(RING_SIZE);
  private readonly outOlaRing = new Float32Array(RING_SIZE);
  private readonly outWeightRing = new Float32Array(RING_SIZE);
  private readonly yinDiff = new Float32Array(YIN_TAU_MAX + 2);
  private readonly yinCmndf = new Float32Array(YIN_TAU_MAX + 2);
  private readonly pitchClassHistogram = new Float32Array(12);

  private writeCounter = 0;
  private yinHopCounter = 0;
  private nextSynthCenter = 0;
  private lastInputEpoch = -10000;
  private isInitializedStream = false;

  private trackedPeriodSamples = 320;
  private voicingConfidence = 0;
  private slowCenterMidi = 60;
  private smoothCorrectionRatio = 1.0;
  private smoothActiveMix = 0.0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
    this.reset();
  }

  updateConfig(newConfig: PitchCorrectionConfig): void {
    this.config = {
      ...newConfig,
      correctionStrengthPercent: Math.max(0, Math.min(100, newConfig.correctionStrengthPercent)),
      retuneSpeedMs: Math.max(0, Math.min(200, newConfig.retuneSpeedMs)),
      humanizePercent: Math.max(0, Math.min(100, newConfig.humanizePercent)),
      noteTransitionMs: Math.max(0, Math.min(150, newConfig.noteTransitionMs)),
      referenceTuningHz: Math.max(415, Math.min(465, newConfig.referenceTuningHz)),
    };
  }

  getConfig(): PitchCorrectionConfig {
    return this.config;
  }

  reset(): void {
    this.inRing.fill(0);
    this.outOlaRing.fill(0);
    this.outWeightRing.fill(0);
    this.yinDiff.fill(0);
    this.yinCmndf.fill(0);
    this.pitchClassHistogram.fill(0);
    this.writeCounter = 0;
    this.yinHopCounter = 0;
    this.nextSynthCenter = 0;
    this.lastInputEpoch = -10000;
    this.isInitializedStream = false;
    this.trackedPeriodSamples = this.sampleRateHz / 150;
    this.voicingConfidence = 0;
    this.slowCenterMidi = 60;
    this.smoothCorrectionRatio = 1.0;
    this.smoothActiveMix = this.config.enabled ? 1 : 0;
    this.lastDetectedNoteLabel = '--';
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActiveMix < 0.0005) {
      this.smoothActiveMix = 0;
      return;
    }

    const targetActiveMix = cfg.enabled ? 1 : 0;
    const fadeAlpha = 0.006;
    const retuneTauSec = Math.max(2, cfg.retuneSpeedMs) / 1000.0;
    const transitionTauSec = Math.max(2, cfg.noteTransitionMs) / 1000.0;
    const combinedTauSec = Math.max(0.003, retuneTauSec * 0.7 + transitionTauSec * 0.3);
    const ratioAlpha = 1.0 - Math.exp(-1.0 / (combinedTauSec * this.sampleRateHz));

    for (let i = 0; i < count; i++) {
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
        if (cfg.pitchDetectionEnabled && n >= YIN_WINDOW_DEC * 2 + YIN_TAU_MAX * 2 + 16) {
          this.detectAndComputeTargetRatio(n, cfg);
        } else if (!cfg.pitchDetectionEnabled) {
          this.voicingConfidence *= 0.85;
        }
      }

      const detectedHz = this.sampleRateHz / Math.max(48, this.trackedPeriodSamples);
      const targetRatio = this.computeTargetRatioFromDetected(detectedHz, cfg);
      this.smoothCorrectionRatio += ratioAlpha * (targetRatio - this.smoothCorrectionRatio);

      const curCenter = n - LOOKBACK_SAMPLES;
      while (curCenter >= this.nextSynthCenter && n >= OUTPUT_DELAY_SAMPLES) {
        const stride = this.synthesizeGrain(curCenter, this.smoothCorrectionRatio);
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
      const processed =
        Math.abs(this.smoothCorrectionRatio - 1.0) < 0.003 || !cfg.pitchDetectionEnabled
          ? dryDelayed
          : synth;

      const active = Math.max(0, Math.min(1, this.smoothActiveMix));
      buffer[i] = active >= 0.999 ? processed : raw * (1 - active) + processed * active;
      this.writeCounter = n + 1;
    }
  }

  private detectAndComputeTargetRatio(writeHead: number, cfg: PitchCorrectionConfig): void {
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
      if (v < 0.22 && v <= this.yinCmndf[tau - 1] && v <= this.yinCmndf[tau + 1]) {
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
      this.voicingConfidence = Math.max(0, this.voicingConfidence * 0.72);
      return;
    }

    const s0 = this.yinCmndf[Math.max(1, bestTau - 1)];
    const s1 = this.yinCmndf[bestTau];
    const s2 = this.yinCmndf[Math.min(YIN_TAU_MAX, bestTau + 1)];
    const denom = 2 * (2 * s1 - s2 - s0);
    const delta = Math.abs(denom) > 1e-6 ? Math.max(-0.5, Math.min(0.5, (s0 - s2) / denom)) : 0;
    const period48k = Math.max(88, Math.min(640, (bestTau + delta) * 2));

    const conf = Math.max(0, Math.min(1, (0.44 - bestVal) / 0.34));
    this.voicingConfidence += 0.45 * (conf - this.voicingConfidence);
    this.trackedPeriodSamples += 0.45 * (period48k - this.trackedPeriodSamples);

    const freqHz = this.sampleRateHz / this.trackedPeriodSamples;
    const midi = 69.0 + 12.0 * (Math.log(freqHz / cfg.referenceTuningHz) / Math.LN2);
    this.slowCenterMidi += 0.08 * (midi - this.slowCenterMidi);

    const nearestMidi = Math.round(midi);
    const pc = ((nearestMidi % 12) + 12) % 12;
    for (let k = 0; k < 12; k++) this.pitchClassHistogram[k] *= 0.985;
    this.pitchClassHistogram[pc] += conf;

    if (cfg.automaticKeyDetection) {
      this.lastAutoDetectedKey = this.estimateBestKey(cfg.scale);
    }
    const octave = Math.floor(nearestMidi / 12) - 1;
    this.lastDetectedNoteLabel = `${NOTE_NAMES[pc]}${octave}`;
  }

  private estimateBestKey(scale: MusicalScale): MusicalKey {
    let bestKey = MusicalKey.C;
    let bestScore = -1;
    const intervals = MUSICAL_SCALE_META[scale].intervals;
    for (const candidate of ALL_KEYS) {
      const offset = MUSICAL_KEY_META[candidate].semitoneOffset;
      let score = 0;
      for (const interval of intervals) {
        score += this.pitchClassHistogram[(offset + interval) % 12];
      }
      if (score > bestScore) {
        bestScore = score;
        bestKey = candidate;
      }
    }
    return bestKey;
  }

  private computeTargetRatioFromDetected(detectedHz: number, cfg: PitchCorrectionConfig): number {
    if (!cfg.enabled || !cfg.pitchDetectionEnabled || this.voicingConfidence < 0.28) {
      return 1.0;
    }
    const activeKey = cfg.automaticKeyDetection ? this.lastAutoDetectedKey : cfg.key;
    const midi = 69.0 + 12.0 * (Math.log(detectedHz / cfg.referenceTuningHz) / Math.LN2);
    const snapped = this.snapMidiToScale(midi, activeKey, cfg.scale);

    const strength = cfg.correctionStrengthPercent / 100;
    const humanize = cfg.humanizePercent / 100;
    const vibratoComponent = Math.max(-0.65, Math.min(0.65, midi - this.slowCenterMidi)) * humanize * 0.65;
    const correctedMidi = midi + (snapped + vibratoComponent - midi) * strength;
    const shiftSemitones = Math.max(-6, Math.min(6, correctedMidi - midi));
    return Math.pow(2, shiftSemitones / 12);
  }

  private snapMidiToScale(midi: number, key: MusicalKey, scale: MusicalScale): number {
    const baseNote = Math.floor(midi);
    let bestNote = baseNote;
    let bestDist = Number.MAX_VALUE;
    const keyOffset = MUSICAL_KEY_META[key].semitoneOffset;
    const intervals = MUSICAL_SCALE_META[scale].intervals;

    for (let candidate = baseNote - 2; candidate <= baseNote + 2; candidate++) {
      const relPc = (((candidate - keyOffset) % 12) + 12) % 12;
      if (intervals.includes(relPc)) {
        const dist = Math.abs(midi - candidate);
        if (dist < bestDist) {
          bestDist = dist;
          bestNote = candidate;
        }
      }
    }
    return bestNote;
  }

  private synthesizeGrain(curCenter: number, pitchRatio: number): number {
    const voiced = this.voicingConfidence > 0.28;
    const period = this.trackedPeriodSamples;
    let grainCenter: number;
    let synthStride: number;
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
      const clampedPitch = Math.max(0.6, Math.min(1.8, pitchRatio));
      synthStride = Math.max(72.0, Math.min(680.0, period / clampedPitch));
      const minWin = Math.max(110, synthStride * 1.18);
      halfWin = Math.max(96, Math.min(480, minWin));
    } else {
      grainCenter = curCenter;
      synthStride = 160.0;
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
      const inPos = grainCenter + (m - fracOut);
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
      const v =
        0.25 * this.inRing[(p - 1) & RING_MASK] +
        0.5 * this.inRing[p & RING_MASK] +
        0.25 * this.inRing[(p + 1) & RING_MASK];
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
}

export class PitchFormantProcessor {
  private readonly sampleRateHz: number;
  private config: PitchFormantConfig = defaultPitchFormant(false);

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
  private voicingConfidence = 0;

  private smoothPitchRatio = 1;
  private smoothFormantRatio = 1;
  private smoothActiveMix = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
    this.reset();
  }

  updateConfig(newConfig: PitchFormantConfig): void {
    this.config = {
      ...newConfig,
      pitchShiftSt: Math.max(-12, Math.min(12, newConfig.pitchShiftSt)),
      formantShiftSt: Math.max(-12, Math.min(12, newConfig.formantShiftSt)),
      fineTuneCents: Math.max(-100, Math.min(100, newConfig.fineTuneCents)),
      dryWetPercent: Math.max(0, Math.min(100, newConfig.dryWetPercent)),
      outputGainDb: Math.max(-12, Math.min(12, newConfig.outputGainDb)),
    };
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
    this.voicingConfidence = 0;
    this.smoothPitchRatio = 1;
    this.smoothFormantRatio = 1;
    this.smoothActiveMix = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActiveMix < 0.0005) {
      this.smoothActiveMix = 0;
      return;
    }

    const totalPitchSt = cfg.pitchShiftSt + cfg.fineTuneCents / 100;
    const effectiveFormantSt = cfg.formantPreservation
      ? cfg.formantShiftSt
      : Math.max(-12, Math.min(12, cfg.formantShiftSt + totalPitchSt * 0.65));
    const targetPitchRatio = Math.pow(2, totalPitchSt / 12);
    const targetFormantRatio = Math.pow(2, effectiveFormantSt / 12);
    const targetActive = cfg.enabled ? 1 : 0;
    const mix = cfg.dryWetPercent / 100;
    const outGain = Math.pow(10, cfg.outputGainDb / 20);

    for (let i = 0; i < count; i++) {
      this.smoothPitchRatio += 0.004 * (targetPitchRatio - this.smoothPitchRatio);
      this.smoothFormantRatio += 0.004 * (targetFormantRatio - this.smoothFormantRatio);
      this.smoothActiveMix += 0.006 * (targetActive - this.smoothActiveMix);

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
      const neutral =
        Math.abs(this.smoothPitchRatio - 1) < 0.004 &&
        Math.abs(this.smoothFormantRatio - 1) < 0.004;
      const wet = neutral ? dryDelayed : synth;
      const mixed = (raw * (1 - mix) + wet * mix) * outGain;
      const active = Math.max(0, Math.min(1, this.smoothActiveMix));
      buffer[i] = active >= 0.999 ? mixed : raw * (1 - active) + mixed * active;
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
    if (Math.sqrt(energySum / YIN_WINDOW_DEC) < 0.002) {
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
    for (let tau = 48; tau < YIN_TAU_MAX; tau++) {
      const v = this.yinCmndf[tau];
      if (v < 0.22 && v <= this.yinCmndf[tau - 1] && v <= this.yinCmndf[tau + 1]) {
        bestTau = tau;
        bestVal = v;
        break;
      }
      if (v < bestVal) {
        bestVal = v;
        bestTau = tau;
      }
    }
    if (bestTau < 48 || bestVal > 0.44) {
      this.voicingConfidence = Math.max(0, this.voicingConfidence * 0.7);
      return;
    }
    const period48k = Math.max(96, Math.min(640, bestTau * 2));
    const conf = Math.max(0, Math.min(1, (0.44 - bestVal) / 0.34));
    this.voicingConfidence += 0.45 * (conf - this.voicingConfidence);
    this.trackedPeriodSamples += 0.4 * (period48k - this.trackedPeriodSamples);
  }

  private synthesizeGrain(curCenter: number, pitchRatio: number, formantRatio: number): number {
    const voiced = this.voicingConfidence > 0.28;
    const period = this.trackedPeriodSamples;
    let grainCenter: number;
    let synthStride: number;
    let effFormant: number;
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
      effFormant = formantRatio;
      const minOverlapWin = Math.max(110, synthStride * 1.18);
      const maxSinglePulseWin = (period * 1.05) / Math.max(0.5, effFormant);
      halfWin = Math.max(
        96,
        Math.min(500, Math.max(minOverlapWin, Math.min(minOverlapWin * 1.1, maxSinglePulseWin)))
      );
    } else {
      grainCenter = curCenter;
      synthStride = 160.0;
      effFormant = 1.0 + (formantRatio - 1.0) * 0.42;
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
      const inPos = grainCenter + (m - fracOut) * effFormant;
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
      const v =
        0.25 * this.inRing[(p - 1) & RING_MASK] +
        0.5 * this.inRing[p & RING_MASK] +
        0.25 * this.inRing[(p + 1) & RING_MASK];
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
}

export class CompressionProcessor {
  private readonly sampleRateHz: number;
  private config: CompressionConfig = defaultCompression(false);

  public lastGainReductionDb = 0;
  private envDb = -60;
  private smoothActive = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
  }

  updateConfig(newConfig: CompressionConfig): void {
    this.config = {
      ...newConfig,
      thresholdDb: Math.max(-60, Math.min(0, newConfig.thresholdDb)),
      ratio: Math.max(1, Math.min(20, newConfig.ratio)),
      attackMs: Math.max(0.5, Math.min(100, newConfig.attackMs)),
      releaseMs: Math.max(10, Math.min(1000, newConfig.releaseMs)),
      kneeDb: Math.max(0, Math.min(24, newConfig.kneeDb)),
      makeupGainDb: Math.max(0, Math.min(24, newConfig.makeupGainDb)),
      outputGainDb: Math.max(-12, Math.min(12, newConfig.outputGainDb)),
    };
  }

  reset(): void {
    this.envDb = -60;
    this.lastGainReductionDb = 0;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      this.lastGainReductionDb = 0;
      return;
    }

    const attackCoeff = Math.exp(-1.0 / (cfg.attackMs * 0.001 * this.sampleRateHz));
    const releaseCoeff = Math.exp(-1.0 / (cfg.releaseMs * 0.001 * this.sampleRateHz));
    const targetActive = cfg.enabled ? 1 : 0;
    const makeupPlusOut = Math.pow(10, (cfg.makeupGainDb + cfg.outputGainDb) / 20);
    const slope = 1 - 1 / cfg.ratio;
    const halfKnee = cfg.kneeDb * 0.5;
    let maxGrDb = 0;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      const levelDb = 20 * Math.log10(Math.max(1e-5, Math.abs(dry)));

      this.envDb =
        levelDb > this.envDb
          ? attackCoeff * this.envDb + (1 - attackCoeff) * levelDb
          : releaseCoeff * this.envDb + (1 - releaseCoeff) * levelDb;

      const overshoot = this.envDb - cfg.thresholdDb;
      let reductionDb = 0;
      if (cfg.kneeDb > 0.1 && overshoot > -halfKnee && overshoot < halfKnee) {
        const x = overshoot + halfKnee;
        reductionDb = (slope * (x * x)) / (2 * cfg.kneeDb);
      } else if (overshoot > 0) {
        reductionDb = slope * overshoot;
      }
      if (reductionDb > maxGrDb) maxGrDb = reductionDb;

      const compGain = Math.pow(10, -reductionDb / 20) * makeupPlusOut;
      const wet = dry * compGain;
      buffer[i] = dry * (1 - this.smoothActive) + wet * this.smoothActive;
    }
    this.lastGainReductionDb = cfg.enabled ? -maxGrDb : 0;
  }
}

export class SaturationProcessor {
  private readonly sampleRateHz: number;
  private config: SaturationConfig = defaultSaturation(false);
  private readonly toneShelf = new BiquadFilter();
  private readonly dcBlocker = new BiquadFilter();
  private lastTone = Number.MIN_SAFE_INTEGER;
  private smoothActive = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
    this.dcBlocker.setHighPass(sampleRateHz, 22.0, 0.707);
  }

  updateConfig(newConfig: SaturationConfig): void {
    this.config = {
      ...newConfig,
      drivePercent: Math.max(0, Math.min(100, newConfig.drivePercent)),
      warmthPercent: Math.max(0, Math.min(100, newConfig.warmthPercent)),
      tonePercent: Math.max(-100, Math.min(100, newConfig.tonePercent)),
      harmonicAmountPercent: Math.max(0, Math.min(100, newConfig.harmonicAmountPercent)),
      mixPercent: Math.max(0, Math.min(100, newConfig.mixPercent)),
      outputLevelDb: Math.max(-12, Math.min(12, newConfig.outputLevelDb)),
    };
  }

  reset(): void {
    this.toneShelf.reset();
    this.dcBlocker.reset();
    this.lastTone = Number.MIN_SAFE_INTEGER;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }

    if (cfg.tonePercent !== this.lastTone) {
      this.lastTone = cfg.tonePercent;
      const gainDb = (cfg.tonePercent / 100.0) * 6.0;
      this.toneShelf.setHighShelf(this.sampleRateHz, 2600.0, gainDb);
    }

    const driveGain = 1.0 + (cfg.drivePercent / 100) * 6.5;
    const warmth = (cfg.warmthPercent / 100) * 0.28;
    const harmonics = cfg.harmonicAmountPercent / 100;
    const mix = cfg.mixPercent / 100;
    const outGain = Math.pow(10, cfg.outputLevelDb / 20);
    const norm = 1.0 / Math.max(1e-3, Math.tanh(driveGain));
    const targetActive = cfg.enabled ? 1 : 0;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      const driven = dry * driveGain + warmth * (dry * dry);
      const shaped = Math.tanh(driven) * norm;
      const harmonicBlended = dry + (shaped - dry) * (0.35 + 0.65 * harmonics);
      const cleaned = this.dcBlocker.process(harmonicBlended);
      const toned = this.toneShelf.process(cleaned);
      const wet = toned * outGain;
      const effMix = mix * this.smoothActive;
      buffer[i] = dry * (1 - effMix) + wet * effMix;
    }
  }
}

export class DeEsserProcessor {
  private readonly sampleRateHz: number;
  private config: DeEsserConfig = defaultDeEsser(false);

  private readonly detectorFilter = new BiquadFilter();
  private readonly cutFilter = new BiquadFilter();
  private lastFreq = -1;
  private lastQ = -1;
  private lastCutDb = 100;
  private sibilanceEnvDb = -60;
  private smoothReductionDb = 0;
  private smoothActive = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
  }

  updateConfig(newConfig: DeEsserConfig): void {
    this.config = {
      ...newConfig,
      frequencyHz: Math.max(3000, Math.min(10000, newConfig.frequencyHz)),
      thresholdDb: Math.max(-50, Math.min(0, newConfig.thresholdDb)),
      reductionPercent: Math.max(0, Math.min(100, newConfig.reductionPercent)),
      bandwidthQ: Math.max(0.5, Math.min(6.0, newConfig.bandwidthQ)),
      mixPercent: Math.max(0, Math.min(100, newConfig.mixPercent)),
    };
  }

  reset(): void {
    this.detectorFilter.reset();
    this.cutFilter.reset();
    this.lastFreq = -1;
    this.lastQ = -1;
    this.lastCutDb = 100;
    this.sibilanceEnvDb = -60;
    this.smoothReductionDb = 0;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }

    if (Math.abs(cfg.frequencyHz - this.lastFreq) > 5 || Math.abs(cfg.bandwidthQ - this.lastQ) > 0.05) {
      this.lastFreq = cfg.frequencyHz;
      this.lastQ = cfg.bandwidthQ;
      this.detectorFilter.setBandPass(this.sampleRateHz, cfg.frequencyHz, cfg.bandwidthQ);
    }

    const attackCoeff = Math.exp(-1.0 / (0.0015 * this.sampleRateHz));
    const releaseCoeff = Math.exp(-1.0 / (0.045 * this.sampleRateHz));
    const maxReductionDb = (cfg.reductionPercent / 100) * 16;
    const mix = cfg.mixPercent / 100;
    const targetActive = cfg.enabled ? 1 : 0;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      const side = Math.abs(this.detectorFilter.process(dry));
      const sideDb = 20 * Math.log10(Math.max(1e-5, side));
      this.sibilanceEnvDb =
        sideDb > this.sibilanceEnvDb
          ? attackCoeff * this.sibilanceEnvDb + (1 - attackCoeff) * sideDb
          : releaseCoeff * this.sibilanceEnvDb + (1 - releaseCoeff) * sideDb;

      const over = Math.max(0, this.sibilanceEnvDb - cfg.thresholdDb);
      const targetCutDb = -Math.min(maxReductionDb, over * 0.85);
      this.smoothReductionDb += 0.03 * (targetCutDb - this.smoothReductionDb);

      if (Math.abs(this.smoothReductionDb - this.lastCutDb) > 0.25) {
        this.lastCutDb = this.smoothReductionDb;
        this.cutFilter.setPeaking(this.sampleRateHz, cfg.frequencyHz, cfg.bandwidthQ, this.smoothReductionDb);
      }

      const wet = this.cutFilter.process(dry);
      const effMix = mix * this.smoothActive;
      buffer[i] = dry * (1 - effMix) + wet * effMix;
    }
  }
}

export class LimiterProcessor {
  private readonly sampleRateHz: number;
  private config: LimiterConfig = defaultLimiter(false);
  private gainEnv = 1;
  private smoothActive = 0;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
  }

  updateConfig(newConfig: LimiterConfig): void {
    this.config = {
      ...newConfig,
      thresholdDb: Math.max(-30, Math.min(0, newConfig.thresholdDb)),
      ceilingDb: Math.max(-12, Math.min(-0.1, newConfig.ceilingDb)),
      releaseMs: Math.max(10, Math.min(500, newConfig.releaseMs)),
      inputGainDb: Math.max(-12, Math.min(18, newConfig.inputGainDb)),
      outputGainDb: Math.max(-12, Math.min(6, newConfig.outputGainDb)),
    };
  }

  reset(): void {
    this.gainEnv = 1;
    this.smoothActive = this.config.enabled ? 1 : 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;
    if (!cfg.enabled && this.smoothActive < 0.0005) {
      this.smoothActive = 0;
      return;
    }

    const inGain = Math.pow(10, cfg.inputGainDb / 20);
    const thresholdLin = Math.pow(10, cfg.thresholdDb / 20);
    const ceilingLin = Math.pow(10, cfg.ceilingDb / 20);
    const outGain = Math.pow(10, cfg.outputGainDb / 20);
    const releaseCoeff = Math.exp(-1.0 / (cfg.releaseMs * 0.001 * this.sampleRateHz));
    const targetActive = cfg.enabled ? 1 : 0;

    for (let i = 0; i < count; i++) {
      this.smoothActive += 0.006 * (targetActive - this.smoothActive);
      const dry = buffer[i];
      const driven = dry * inGain;
      const absDriven = Math.abs(driven);
      const neededGain =
        absDriven > thresholdLin && absDriven > 1e-6
          ? Math.max(0.01, Math.min(1, thresholdLin / absDriven))
          : 1;
      this.gainEnv =
        neededGain < this.gainEnv
          ? neededGain
          : releaseCoeff * this.gainEnv + (1 - releaseCoeff) * neededGain;

      let limited = driven * this.gainEnv * outGain;
      if (Math.abs(limited) > ceilingLin) {
        limited = limited >= 0 ? ceilingLin : -ceilingLin;
      }
      buffer[i] = dry * (1 - this.smoothActive) + limited * this.smoothActive;
    }
  }
}
