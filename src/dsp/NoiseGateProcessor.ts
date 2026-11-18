import {
  AudioConfig,
  defaultNoiseGate,
  MonitoringConstants,
  NoiseGateConfig,
  NoiseGateConstants,
} from '../types/audio';

/**
 * Smooth-knee dBFS Noise Gate with RMS/peak detector blend, hysteresis, attack, hold, and release.
 * Used both as an independent DSP stage in StudioDspPipeline and as the dedicated AEC Noise Gate stage.
 */
export class NoiseGateProcessor {
  private readonly sampleRateHz: number;
  private config: NoiseGateConfig = defaultNoiseGate(false);
  private hysteresisDb = 4.0;
  private softKneeDb = 4.0;

  private peakEnvelope = 0;
  private rmsSqEnvelope = 0;
  private gateGain = 1;
  private isOpenState = true;
  private holdSamplesRemaining = 0;

  public lastIsGateOpen = true;
  public lastGainReductionDb = 0;

  private readonly detectorAttackCoeff: number;
  private readonly detectorReleaseCoeff: number;
  private readonly rmsCoeff: number;

  constructor(sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ) {
    this.sampleRateHz = sampleRateHz;
    this.detectorAttackCoeff = Math.exp(-1.0 / (0.002 * sampleRateHz));
    this.detectorReleaseCoeff = Math.exp(-1.0 / (0.03 * sampleRateHz));
    this.rmsCoeff = Math.exp(-1.0 / (0.012 * sampleRateHz));
  }

  static createAecNoiseGate(
    sampleRateHz: number = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
  ): NoiseGateProcessor {
    const gate = new NoiseGateProcessor(sampleRateHz);
    gate.hysteresisDb = 4.5;
    gate.softKneeDb = 5.0;
    gate.updateConfig({
      enabled: true,
      mixPercent: 100,
      thresholdDb: MonitoringConstants.DEFAULT_AEC_GATE_THRESHOLD_DB,
      attackMs: 3.0,
      holdMs: 110.0,
      releaseMs: 170.0,
      rangeDb: 54.0,
    });
    return gate;
  }

  updateAecThreshold(enabled: boolean, thresholdDb: number): void {
    const clampedThresh = Math.max(
      MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB,
      Math.min(MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB, thresholdDb)
    );
    this.config = {
      enabled,
      mixPercent: 100,
      thresholdDb: clampedThresh,
      attackMs: 3.0,
      holdMs: 110.0,
      releaseMs: 170.0,
      rangeDb: 54.0,
    };
  }

  updateConfig(newConfig: NoiseGateConfig): void {
    this.config = {
      enabled: newConfig.enabled,
      mixPercent: Math.max(
        NoiseGateConstants.MIN_MIX_PCT,
        Math.min(NoiseGateConstants.MAX_MIX_PCT, newConfig.mixPercent)
      ),
      thresholdDb: Math.max(
        NoiseGateConstants.MIN_THRESHOLD_DB,
        Math.min(NoiseGateConstants.MAX_THRESHOLD_DB, newConfig.thresholdDb)
      ),
      attackMs: Math.max(
        NoiseGateConstants.MIN_ATTACK_MS,
        Math.min(NoiseGateConstants.MAX_ATTACK_MS, newConfig.attackMs)
      ),
      holdMs: Math.max(
        NoiseGateConstants.MIN_HOLD_MS,
        Math.min(NoiseGateConstants.MAX_HOLD_MS, newConfig.holdMs)
      ),
      releaseMs: Math.max(
        NoiseGateConstants.MIN_RELEASE_MS,
        Math.min(NoiseGateConstants.MAX_RELEASE_MS, newConfig.releaseMs)
      ),
      rangeDb: Math.max(
        NoiseGateConstants.MIN_RANGE_DB,
        Math.min(NoiseGateConstants.MAX_RANGE_DB, newConfig.rangeDb)
      ),
    };
  }

  getConfig(): NoiseGateConfig {
    return this.config;
  }

  reset(): void {
    this.peakEnvelope = 0;
    this.rmsSqEnvelope = 0;
    this.holdSamplesRemaining = 0;
    const floorGain = Math.max(0.0001, Math.min(1, Math.pow(10, -this.config.rangeDb / 20)));
    this.gateGain = this.config.enabled ? floorGain : 1;
    this.isOpenState = !this.config.enabled;
    this.lastIsGateOpen = this.isOpenState;
    this.lastGainReductionDb = 0;
  }

  processInPlace(buffer: Float32Array, count: number): void {
    if (count <= 0) return;
    const cfg = this.config;

    const openThresholdLin = Math.pow(10, cfg.thresholdDb / 20);
    const closeThresholdLin = Math.pow(10, (cfg.thresholdDb - this.hysteresisDb) / 20);
    const softKneeBottomLin = Math.pow(
      10,
      (cfg.thresholdDb - this.hysteresisDb - this.softKneeDb) / 20
    );
    const floorGain = Math.max(0.0001, Math.min(1, Math.pow(10, -cfg.rangeDb / 20)));
    const mix = cfg.mixPercent / 100;

    const attackSamples = Math.max(1, cfg.attackMs * 0.001 * this.sampleRateHz);
    const holdTotalSamples = Math.max(0, Math.floor(cfg.holdMs * 0.001 * this.sampleRateHz));
    const releaseSamples = Math.max(1, cfg.releaseMs * 0.001 * this.sampleRateHz);
    const gainAttackCoeff = Math.exp(-1.0 / attackSamples);
    const gainReleaseCoeff = Math.exp(-1.0 / releaseSamples);
    const bypassFadeCoeff = Math.exp(-1.0 / (0.004 * this.sampleRateHz));

    let peakEnv = this.peakEnvelope;
    let rmsSq = this.rmsSqEnvelope;
    let gain = this.gateGain;
    let open = this.isOpenState;
    let holdRem = this.holdSamplesRemaining;

    for (let i = 0; i < count; i++) {
      const sample = buffer[i];
      const absSample = Math.abs(sample);

      peakEnv =
        absSample > peakEnv
          ? this.detectorAttackCoeff * peakEnv + (1 - this.detectorAttackCoeff) * absSample
          : this.detectorReleaseCoeff * peakEnv + (1 - this.detectorReleaseCoeff) * absSample;
      rmsSq = this.rmsCoeff * rmsSq + (1 - this.rmsCoeff) * (sample * sample);

      // Blend RMS + Peak for reliable speech onset without clicking on sharp background spikes
      const rmsEnv = Math.sqrt(rmsSq);
      const level = rmsEnv * 0.65 + peakEnv * 0.35;

      if (!cfg.enabled) {
        open = true;
        holdRem = 0;
        gain = bypassFadeCoeff * gain + (1 - bypassFadeCoeff) * 1;
        buffer[i] = sample * gain;
      } else {
        if (open) {
          if (level >= closeThresholdLin) {
            holdRem = holdTotalSamples;
          } else if (holdRem > 0) {
            holdRem--;
          } else {
            open = false;
          }
        } else if (level >= openThresholdLin) {
          open = true;
          holdRem = holdTotalSamples;
        }

        // Smooth soft-knee expansion when transitioning below close threshold
        let targetGain: number;
        if (open) {
          targetGain = 1;
        } else if (level <= softKneeBottomLin || closeThresholdLin <= softKneeBottomLin) {
          targetGain = floorGain;
        } else {
          const t =
            (level - softKneeBottomLin) / Math.max(1e-7, closeThresholdLin - softKneeBottomLin);
          const smoothT = t * t * (3 - 2 * t);
          targetGain = floorGain + (1 - floorGain) * smoothT * 0.35;
        }

        gain =
          targetGain > gain
            ? gainAttackCoeff * gain + (1 - gainAttackCoeff) * targetGain
            : gainReleaseCoeff * gain + (1 - gainReleaseCoeff) * targetGain;

        const gated = sample * gain;
        buffer[i] = sample * (1 - mix) + gated * mix;
      }
    }

    this.peakEnvelope = peakEnv;
    this.rmsSqEnvelope = rmsSq;
    this.gateGain = gain;
    this.isOpenState = open;
    this.holdSamplesRemaining = holdRem;
    this.lastIsGateOpen = open;
    if (cfg.enabled) {
      const effectiveGain = Math.max(1e-5, 1 - mix + gain * mix);
      this.lastGainReductionDb = Math.max(-80, Math.min(0, 20 * Math.log10(effectiveGain)));
    } else {
      this.lastGainReductionDb = 0;
    }
  }
}
