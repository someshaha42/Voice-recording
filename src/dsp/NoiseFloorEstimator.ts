import { MonitoringConstants } from '../types/audio';

/**
 * Tracks ambient background noise floor in dBFS using slow-rise / faster-fall minimum tracking,
 * plus an explicit 1.5-second silent calibration pass that computes a conservative threshold
 * recommendation without cutting off quiet speech.
 */
export class NoiseFloorEstimator {
  private estimatedFloorDb = -60;
  private hasDetected = false;

  private isCalibrating = false;
  private calibrationElapsedMs = 0;
  private readonly calibrationDurationMs = 1500;
  private calAccumDb = 0;
  private calMaxRmsDb = -60;
  private calBlockCount = 0;
  private lastRecommendationDb: number | null = null;

  reset(): void {
    this.estimatedFloorDb = -60;
    this.hasDetected = false;
    this.isCalibrating = false;
    this.calibrationElapsedMs = 0;
    this.calAccumDb = 0;
    this.calMaxRmsDb = -60;
    this.calBlockCount = 0;
  }

  startCalibration(): void {
    this.isCalibrating = true;
    this.calibrationElapsedMs = 0;
    this.calAccumDb = 0;
    this.calMaxRmsDb = -60;
    this.calBlockCount = 0;
  }

  cancelCalibration(): void {
    this.isCalibrating = false;
    this.calibrationElapsedMs = 0;
  }

  getIsCalibrating(): boolean {
    return this.isCalibrating;
  }

  getCalibrationProgress(): number {
    if (!this.isCalibrating) return 0;
    return Math.max(0, Math.min(1, this.calibrationElapsedMs / this.calibrationDurationMs));
  }

  getEstimatedNoiseFloorDb(): number {
    return this.estimatedFloorDb;
  }

  getNoiseFloorDetected(): boolean {
    return this.hasDetected;
  }

  getRecommendationDb(): number | null {
    return this.lastRecommendationDb;
  }

  /**
   * Feed a raw RMS block measurement (before DSP/Gate) and elapsed block time in ms.
   * Returns a newly finalized recommended threshold in dBFS if calibration just completed, else null.
   */
  observeBlock(rmsDb: number, blockDurationMs: number): number | null {
    const clampedRms = Math.max(-75, Math.min(0, rmsDb));

    // Continuous asymmetric noise-floor tracking: drops quickly toward quiet room tone, rises slowly
    if (!this.hasDetected) {
      this.estimatedFloorDb = Math.max(-65, Math.min(-18, clampedRms));
      this.hasDetected = true;
    } else if (clampedRms < this.estimatedFloorDb) {
      this.estimatedFloorDb = this.estimatedFloorDb * 0.85 + clampedRms * 0.15;
    } else if (clampedRms < this.estimatedFloorDb + 8) {
      this.estimatedFloorDb = this.estimatedFloorDb * 0.96 + clampedRms * 0.04;
    } else {
      // Likely speech above background; creep very slowly so room baseline tracks drift without jumping to speech level
      this.estimatedFloorDb = this.estimatedFloorDb * 0.998 + clampedRms * 0.002;
    }

    if (!this.isCalibrating) {
      return null;
    }

    this.calibrationElapsedMs += Math.max(1, blockDurationMs);
    this.calAccumDb += clampedRms;
    if (clampedRms > this.calMaxRmsDb) {
      this.calMaxRmsDb = clampedRms;
    }
    this.calBlockCount++;

    if (this.calibrationElapsedMs >= this.calibrationDurationMs && this.calBlockCount > 0) {
      const avgAmbientDb = this.calAccumDb / this.calBlockCount;
      // Blend average ambient level and peak-weighted ambient room tone
      const ambientFloorDb = avgAmbientDb * 0.7 + this.calMaxRmsDb * 0.3;
      this.estimatedFloorDb = Math.max(-65, Math.min(-18, ambientFloorDb));
      this.hasDetected = true;
      this.isCalibrating = false;
      this.calibrationElapsedMs = 0;

      // Conservative +5 dB headroom, capped at -32 dBFS so quiet speech / word endings remain safe
      const safeThreshold = Math.round(ambientFloorDb + 5);
      const recommended = Math.max(
        MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB,
        Math.min(-32, safeThreshold)
      );
      this.lastRecommendationDb = recommended;
      return recommended;
    }

    return null;
  }
}
