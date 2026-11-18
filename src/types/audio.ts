export const AudioConfig = {
  OUTPUT_FORMAT_LABEL: 'WAV (PCM)',
  OUTPUT_SAMPLE_RATE_HZ: 48_000,
  OUTPUT_BITS_PER_SAMPLE: 16,
  OUTPUT_CHANNELS: 1,
  OUTPUT_BITRATE_BPS: 48_000 * 16 * 1,
  OUTPUT_BITRATE_KBPS: 768,
  BYTES_PER_SAMPLE: 2,
  BYTES_PER_SECOND: 48_000 * 1 * 2,
  DSP_HOP_SIZE: 128,
  DSP_FFT_SIZE: 512,
} as const;

export enum StudioTab {
  RECORDER = 'RECORDER',
  VOICE_CHANGER = 'VOICE_CHANGER',
  EFFECTS = 'EFFECTS',
}

export enum RecordingStatus {
  IDLE = 'IDLE',
  RECORDING = 'RECORDING',
  PAUSED = 'PAUSED',
  INTERRUPTED_PAUSED = 'INTERRUPTED_PAUSED',
}

export enum MonitoringLatencyZone {
  FASTER = 'FASTER',
  DEFAULT = 'DEFAULT',
  MORE_DELAY = 'MORE_DELAY',
}

export interface MonitoringConfig {
  livePreviewEnabled: boolean;
  delaySliderPosition: number;
  previewVolumePercent: number;
  echoCancellationEnabled: boolean;
  noiseGateThresholdDb: number;
}

export const MonitoringConstants = {
  MIN_SLIDER: -1.0,
  MAX_SLIDER: 1.0,
  STEP_SLIDER: 0.05,
  DEFAULT_SLIDER: 0.0,
  MAX_ADDED_DELAY_MS: 200,
  MIN_VOLUME_PERCENT: 0,
  MAX_VOLUME_PERCENT: 500,
  STEP_VOLUME_PERCENT: 10,
  DEFAULT_VOLUME_PERCENT: 100,
  MIN_AEC_GATE_THRESHOLD_DB: -60,
  MAX_AEC_GATE_THRESHOLD_DB: -20,
  DEFAULT_AEC_GATE_THRESHOLD_DB: -45,
  STEP_AEC_GATE_THRESHOLD_DB: 1,
} as const;

export function getMonitoringZone(sliderPos: number): MonitoringLatencyZone {
  if (sliderPos < -0.025) return MonitoringLatencyZone.FASTER;
  if (sliderPos > 0.025) return MonitoringLatencyZone.MORE_DELAY;
  return MonitoringLatencyZone.DEFAULT;
}

export function getAddedOutputDelayMs(sliderPos: number): number {
  if (sliderPos > 0.025) {
    const clamped = Math.max(0, Math.min(1, sliderPos));
    return Math.round(clamped * MonitoringConstants.MAX_ADDED_DELAY_MS);
  }
  return 0;
}

export function createDefaultMonitoringConfig(): MonitoringConfig {
  return {
    livePreviewEnabled: false,
    delaySliderPosition: MonitoringConstants.DEFAULT_SLIDER,
    previewVolumePercent: MonitoringConstants.DEFAULT_VOLUME_PERCENT,
    echoCancellationEnabled: true,
    noiseGateThresholdDb: MonitoringConstants.DEFAULT_AEC_GATE_THRESHOLD_DB,
  };
}

export type BluetoothRouteType = 'NONE' | 'HFP_HSP' | 'A2DP';
export type AecRouteStatus = 'OFF' | 'ACTIVE_HW_AND_DSP' | 'ACTIVE_DSP_ONLY' | 'STANDBY';

export interface AudioDeviceOption {
  deviceId: string;
  label: string;
  isUsb: boolean;
  isBluetooth?: boolean;
}

export interface AudioHardwareState {
  inputDeviceId: string | null;
  inputDeviceName: string;
  isUsbMicConnected: boolean;
  isBluetoothInputConnected: boolean;
  supportedSampleRates: number[];
  activeInputSampleRateHz: number;
  activeInputChannelCount: number;
  isResamplingActive: boolean;
  outputDeviceId: string | null;
  outputDeviceName: string;
  isWiredHeadphoneConnected: boolean;
  isBluetoothOutputConnected: boolean;
  bluetoothRouteType: BluetoothRouteType;
  aecHardwareSupported: boolean;
  aecHardwareActive: boolean;
  aecRouteStatus: AecRouteStatus;
  aecStatusDetail: string;
  supportsOutputSinkRouting: boolean;
  hardwareValidated: boolean;
  availableInputs: AudioDeviceOption[];
  availableOutputs: AudioDeviceOption[];
}

export function createDefaultHardwareState(): AudioHardwareState {
  return {
    inputDeviceId: null,
    inputDeviceName: 'Internal Microphone',
    isUsbMicConnected: false,
    isBluetoothInputConnected: false,
    supportedSampleRates: [48_000],
    activeInputSampleRateHz: AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
    activeInputChannelCount: 1,
    isResamplingActive: false,
    outputDeviceId: null,
    outputDeviceName: 'System Output (Use headphones for preview)',
    isWiredHeadphoneConnected: false,
    isBluetoothOutputConnected: false,
    bluetoothRouteType: 'NONE',
    aecHardwareSupported: false,
    aecHardwareActive: false,
    aecRouteStatus: 'STANDBY',
    aecStatusDetail: 'AEC & Noise Gate ready',
    supportsOutputSinkRouting: false,
    hardwareValidated: false,
    availableInputs: [],
    availableOutputs: [],
  };
}

export interface LiveAudioMetrics {
  rmsDb: number;
  peakDb: number;
  isClipping: boolean;
  clipCount: number;
  gateOpen: boolean;
  gateGainReductionDb: number;
  aecGateOpen: boolean;
  aecGateGainReductionDb: number;
  estimatedNoiseFloorDb: number;
  noiseFloorDetected: boolean;
  isCalibratingNoise: boolean;
  calibrationProgress: number;
  calibrationRecommendationDb: number | null;
  isInputMonitoringActive: boolean;
  recordingDurationMs: number;
  estimatedMonitoringLatencyMs: number;
}

export function createDefaultLiveMetrics(): LiveAudioMetrics {
  return {
    rmsDb: -60,
    peakDb: -60,
    isClipping: false,
    clipCount: 0,
    gateOpen: true,
    gateGainReductionDb: 0,
    aecGateOpen: true,
    aecGateGainReductionDb: 0,
    estimatedNoiseFloorDb: -60,
    noiseFloorDetected: false,
    isCalibratingNoise: false,
    calibrationProgress: 0,
    calibrationRecommendationDb: null,
    isInputMonitoringActive: false,
    recordingDurationMs: 0,
    estimatedMonitoringLatencyMs: 28,
  };
}

export enum VoicePreset {
  NORMAL = 'NORMAL',
  FEMALE = 'FEMALE',
  CHILD = 'CHILD',
  GIRL = 'GIRL',
  WOMAN = 'WOMAN',
}

export const VOICE_PRESET_LABELS: Record<VoicePreset, string> = {
  [VoicePreset.NORMAL]: 'Normal',
  [VoicePreset.FEMALE]: 'Female',
  [VoicePreset.CHILD]: 'Child',
  [VoicePreset.GIRL]: 'Girl',
  [VoicePreset.WOMAN]: 'Woman',
};

export interface PresetVoiceProfile {
  basePitchSemitones: number;
  baseFormantSemitones: number;
  chestGainDb: number;
  chestFreqHz: number;
  warmthGainDb: number;
  warmthFreqHz: number;
  presenceGainDb: number;
  presenceFreqHz: number;
  airGainDb: number;
  airFreqHz: number;
  deEssAmount: number;
  defaultPitchLevel: number;
  defaultToneLevel: number;
  defaultClarity: number;
}

export const VOICE_PRESET_PROFILES: Record<VoicePreset, PresetVoiceProfile> = {
  [VoicePreset.NORMAL]: {
    basePitchSemitones: 0.0,
    baseFormantSemitones: 0.0,
    chestGainDb: 0.0,
    chestFreqHz: 165.0,
    warmthGainDb: 0.0,
    warmthFreqHz: 280.0,
    presenceGainDb: 0.0,
    presenceFreqHz: 2800.0,
    airGainDb: 0.0,
    airFreqHz: 7200.0,
    deEssAmount: 0.0,
    defaultPitchLevel: 0,
    defaultToneLevel: 50,
    defaultClarity: 50,
  },
  [VoicePreset.FEMALE]: {
    basePitchSemitones: 5.4,
    baseFormantSemitones: 3.5,
    chestGainDb: -3.4,
    chestFreqHz: 170.0,
    warmthGainDb: 1.4,
    warmthFreqHz: 310.0,
    presenceGainDb: 2.1,
    presenceFreqHz: 3000.0,
    airGainDb: 1.6,
    airFreqHz: 7500.0,
    deEssAmount: 0.25,
    defaultPitchLevel: 85,
    defaultToneLevel: 80,
    defaultClarity: 65,
  },
  [VoicePreset.CHILD]: {
    basePitchSemitones: 9.2,
    baseFormantSemitones: 5.6,
    chestGainDb: -5.5,
    chestFreqHz: 185.0,
    warmthGainDb: 0.6,
    warmthFreqHz: 360.0,
    presenceGainDb: 2.6,
    presenceFreqHz: 2600.0,
    airGainDb: 2.0,
    airFreqHz: 6800.0,
    deEssAmount: 0.32,
    defaultPitchLevel: 90,
    defaultToneLevel: 85,
    defaultClarity: 70,
  },
  [VoicePreset.GIRL]: {
    basePitchSemitones: 7.1,
    baseFormantSemitones: 4.4,
    chestGainDb: -4.4,
    chestFreqHz: 175.0,
    warmthGainDb: 1.0,
    warmthFreqHz: 330.0,
    presenceGainDb: 2.7,
    presenceFreqHz: 3400.0,
    airGainDb: 2.2,
    airFreqHz: 8000.0,
    deEssAmount: 0.28,
    defaultPitchLevel: 88,
    defaultToneLevel: 82,
    defaultClarity: 68,
  },
  [VoicePreset.WOMAN]: {
    basePitchSemitones: 4.3,
    baseFormantSemitones: 2.7,
    chestGainDb: -1.8,
    chestFreqHz: 155.0,
    warmthGainDb: 1.9,
    warmthFreqHz: 320.0,
    presenceGainDb: 1.7,
    presenceFreqHz: 2650.0,
    airGainDb: 0.9,
    airFreqHz: 6500.0,
    deEssAmount: 0.20,
    defaultPitchLevel: 85,
    defaultToneLevel: 78,
    defaultClarity: 60,
  },
};

export interface VoiceChangerConfig {
  enabled: boolean;
  preset: VoicePreset;
  pitchLevelPercent: number;
  toneLevelPercent: number;
  clarityPercent: number;
}

export const VoiceChangerConstants = {
  MIN_LEVEL_PCT: 0,
  MAX_LEVEL_PCT: 100,
  STEP_LEVEL_PCT: 5,
} as const;

export function getVoicePresetDefaultConfig(
  preset: VoicePreset,
  enabled = false
): VoiceChangerConfig {
  const p = VOICE_PRESET_PROFILES[preset];
  return {
    enabled,
    preset,
    pitchLevelPercent: p.defaultPitchLevel,
    toneLevelPercent: p.defaultToneLevel,
    clarityPercent: p.defaultClarity,
  };
}

export enum StudioEffectType {
  PITCH_CORRECTION = 'PITCH_CORRECTION',
  PITCH_FORMANT = 'PITCH_FORMANT',
  EQUALIZER = 'EQUALIZER',
  COMPRESSION = 'COMPRESSION',
  SATURATION = 'SATURATION',
  REVERB = 'REVERB',
  DELAY = 'DELAY',
  NOISE_GATE = 'NOISE_GATE',
  DE_ESSER = 'DE_ESSER',
  LIMITER = 'LIMITER',
}

export const STUDIO_EFFECT_LABELS: Record<StudioEffectType, string> = {
  [StudioEffectType.PITCH_CORRECTION]: 'Pitch Correction',
  [StudioEffectType.PITCH_FORMANT]: 'Pitch & Formant',
  [StudioEffectType.EQUALIZER]: 'Equalizer (EQ)',
  [StudioEffectType.COMPRESSION]: 'Compression',
  [StudioEffectType.SATURATION]: 'Saturation',
  [StudioEffectType.REVERB]: 'Reverb',
  [StudioEffectType.DELAY]: 'Delay',
  [StudioEffectType.NOISE_GATE]: 'Noise Gate',
  [StudioEffectType.DE_ESSER]: 'De-Esser',
  [StudioEffectType.LIMITER]: 'Limiter',
};

export enum MusicalKey {
  C = 'C',
  C_SHARP = 'C_SHARP',
  D = 'D',
  D_SHARP = 'D_SHARP',
  E = 'E',
  F = 'F',
  F_SHARP = 'F_SHARP',
  G = 'G',
  G_SHARP = 'G_SHARP',
  A = 'A',
  A_SHARP = 'A_SHARP',
  B = 'B',
}

export const MUSICAL_KEY_META: Record<MusicalKey, { label: string; semitoneOffset: number }> = {
  [MusicalKey.C]: { label: 'C', semitoneOffset: 0 },
  [MusicalKey.C_SHARP]: { label: 'C#', semitoneOffset: 1 },
  [MusicalKey.D]: { label: 'D', semitoneOffset: 2 },
  [MusicalKey.D_SHARP]: { label: 'D#', semitoneOffset: 3 },
  [MusicalKey.E]: { label: 'E', semitoneOffset: 4 },
  [MusicalKey.F]: { label: 'F', semitoneOffset: 5 },
  [MusicalKey.F_SHARP]: { label: 'F#', semitoneOffset: 6 },
  [MusicalKey.G]: { label: 'G', semitoneOffset: 7 },
  [MusicalKey.G_SHARP]: { label: 'G#', semitoneOffset: 8 },
  [MusicalKey.A]: { label: 'A', semitoneOffset: 9 },
  [MusicalKey.A_SHARP]: { label: 'A#', semitoneOffset: 10 },
  [MusicalKey.B]: { label: 'B', semitoneOffset: 11 },
};

export enum MusicalScale {
  CHROMATIC = 'CHROMATIC',
  MAJOR = 'MAJOR',
  MINOR = 'MINOR',
}

export const MUSICAL_SCALE_META: Record<MusicalScale, { label: string; intervals: number[] }> = {
  [MusicalScale.CHROMATIC]: { label: 'Chromatic', intervals: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11] },
  [MusicalScale.MAJOR]: { label: 'Major', intervals: [0, 2, 4, 5, 7, 9, 11] },
  [MusicalScale.MINOR]: { label: 'Minor', intervals: [0, 2, 3, 5, 7, 8, 10] },
};

export enum FilterSlope {
  SLOPE_12 = 12,
  SLOPE_24 = 24,
}

export enum ReverbPreset {
  SMALL_ROOM = 'SMALL_ROOM',
  STUDIO = 'STUDIO',
  HALL = 'HALL',
  PLATE = 'PLATE',
}

export const REVERB_PRESET_LABELS: Record<ReverbPreset, string> = {
  [ReverbPreset.SMALL_ROOM]: 'Small Room',
  [ReverbPreset.STUDIO]: 'Studio',
  [ReverbPreset.HALL]: 'Hall',
  [ReverbPreset.PLATE]: 'Plate',
};

export enum DelaySubdivision {
  QUARTER = 'QUARTER',
  EIGHTH = 'EIGHTH',
  DOTTED_EIGHTH = 'DOTTED_EIGHTH',
  SIXTEENTH = 'SIXTEENTH',
}

export const DELAY_SUBDIVISION_META: Record<DelaySubdivision, { label: string; beatFactor: number }> = {
  [DelaySubdivision.QUARTER]: { label: '1/4', beatFactor: 1.0 },
  [DelaySubdivision.EIGHTH]: { label: '1/8', beatFactor: 0.5 },
  [DelaySubdivision.DOTTED_EIGHTH]: { label: '1/8D', beatFactor: 0.75 },
  [DelaySubdivision.SIXTEENTH]: { label: '1/16', beatFactor: 0.25 },
};

export interface PitchCorrectionConfig {
  enabled: boolean;
  pitchDetectionEnabled: boolean;
  key: MusicalKey;
  scale: MusicalScale;
  automaticKeyDetection: boolean;
  correctionStrengthPercent: number;
  retuneSpeedMs: number;
  humanizePercent: number;
  noteTransitionMs: number;
  referenceTuningHz: number;
}

export interface PitchFormantConfig {
  enabled: boolean;
  pitchShiftSt: number;
  formantShiftSt: number;
  fineTuneCents: number;
  formantPreservation: boolean;
  dryWetPercent: number;
  outputGainDb: number;
}

export interface EqualizerConfig {
  enabled: boolean;
  mixPercent: number;
  lowFreqHz: number;
  lowGainDb: number;
  lowQ: number;
  mid1FreqHz: number;
  mid1GainDb: number;
  mid1Q: number;
  mid2FreqHz: number;
  mid2GainDb: number;
  mid2Q: number;
  mid3FreqHz: number;
  mid3GainDb: number;
  mid3Q: number;
  highFreqHz: number;
  highGainDb: number;
  highQ: number;
}

export interface CompressionConfig {
  enabled: boolean;
  thresholdDb: number;
  ratio: number;
  attackMs: number;
  releaseMs: number;
  kneeDb: number;
  makeupGainDb: number;
  outputGainDb: number;
}

export interface SaturationConfig {
  enabled: boolean;
  drivePercent: number;
  warmthPercent: number;
  tonePercent: number;
  harmonicAmountPercent: number;
  mixPercent: number;
  outputLevelDb: number;
}

export interface ReverbConfig {
  enabled: boolean;
  preset: ReverbPreset;
  mixPercent: number;
  roomSizePercent: number;
  decayTimeSec: number;
  dampingPercent: number;
  wetPercent: number;
  dryPercent: number;
  widthPercent: number;
  preDelayMs: number;
}

export interface EchoConfig {
  enabled: boolean;
  mixPercent: number;
  timeMs: number;
  feedbackPercent: number;
  echoLevelPercent: number;
  wetPercent: number;
  dryPercent: number;
  pingPong: boolean;
  widthPercent: number;
  tempoSync: boolean;
  bpm: number;
  subdivision: DelaySubdivision;
  lowCutHz: number;
  highCutHz: number;
}

export interface NoiseGateConfig {
  enabled: boolean;
  mixPercent: number;
  thresholdDb: number;
  attackMs: number;
  holdMs: number;
  releaseMs: number;
  rangeDb: number;
}

export const NoiseGateConstants = {
  MIN_MIX_PCT: 0,
  MAX_MIX_PCT: 100,
  DEFAULT_MIX_PCT: 100,
  MIN_THRESHOLD_DB: -70,
  MAX_THRESHOLD_DB: -10,
  DEFAULT_THRESHOLD_DB: -50,
  MIN_ATTACK_MS: 0.5,
  MAX_ATTACK_MS: 50,
  DEFAULT_ATTACK_MS: 5,
  MIN_HOLD_MS: 0,
  MAX_HOLD_MS: 500,
  DEFAULT_HOLD_MS: 50,
  MIN_RELEASE_MS: 20,
  MAX_RELEASE_MS: 600,
  DEFAULT_RELEASE_MS: 100,
  MIN_RANGE_DB: 0,
  MAX_RANGE_DB: 80,
  DEFAULT_RANGE_DB: 80,
} as const;

export interface DeEsserConfig {
  enabled: boolean;
  frequencyHz: number;
  thresholdDb: number;
  reductionPercent: number;
  bandwidthQ: number;
  mixPercent: number;
}

export interface LimiterConfig {
  enabled: boolean;
  thresholdDb: number;
  ceilingDb: number;
  releaseMs: number;
  inputGainDb: number;
  outputGainDb: number;
}

export interface LowPassConfig {
  enabled: boolean;
  mixPercent: number;
  cutoffHz: number;
  resonance: number;
  slope: FilterSlope;
}

export interface HighPassConfig {
  enabled: boolean;
  mixPercent: number;
  cutoffHz: number;
  resonance: number;
  slope: FilterSlope;
}

export interface HallConfig {
  enabled: boolean;
  echoAmount: number;
  reverberationAmount: number;
}

export interface EffectsRackConfig {
  pitchCorrection: PitchCorrectionConfig;
  pitchFormant: PitchFormantConfig;
  equalizer: EqualizerConfig;
  compression: CompressionConfig;
  saturation: SaturationConfig;
  reverb: ReverbConfig;
  echo: EchoConfig;
  noiseGate: NoiseGateConfig;
  deEsser: DeEsserConfig;
  limiter: LimiterConfig;
  lowPass: LowPassConfig;
  highPass: HighPassConfig;
  hall: HallConfig;
}

export function getEffectiveDelayMs(echo: EchoConfig): number {
  if (echo.tempoSync) {
    const clampedBpm = Math.max(60, Math.min(200, echo.bpm));
    const beatMs = 60_000 / clampedBpm;
    const raw = Math.round(beatMs * DELAY_SUBDIVISION_META[echo.subdivision].beatFactor);
    return Math.max(20, Math.min(1000, raw));
  }
  return Math.max(20, Math.min(1000, echo.timeMs));
}

export function getActiveStudioEffectCount(fx: EffectsRackConfig): number {
  return [
    fx.pitchCorrection.enabled,
    fx.pitchFormant.enabled,
    fx.equalizer.enabled,
    fx.compression.enabled,
    fx.saturation.enabled,
    fx.reverb.enabled,
    fx.echo.enabled,
    fx.noiseGate.enabled,
    fx.deEsser.enabled,
    fx.limiter.enabled,
  ].filter(Boolean).length;
}

export function getActiveEffectCount(fx: EffectsRackConfig): number {
  return (
    getActiveStudioEffectCount(fx) +
    [fx.lowPass.enabled, fx.highPass.enabled, fx.hall.enabled].filter(Boolean).length
  );
}

export const defaultPitchCorrection = (enabled = false): PitchCorrectionConfig => ({
  enabled,
  pitchDetectionEnabled: true,
  key: MusicalKey.C,
  scale: MusicalScale.MAJOR,
  automaticKeyDetection: false,
  correctionStrengthPercent: 65,
  retuneSpeedMs: 35,
  humanizePercent: 35,
  noteTransitionMs: 25,
  referenceTuningHz: 440,
});

export const defaultPitchFormant = (enabled = false): PitchFormantConfig => ({
  enabled,
  pitchShiftSt: 0,
  formantShiftSt: 0,
  fineTuneCents: 0,
  formantPreservation: true,
  dryWetPercent: 100,
  outputGainDb: 0,
});

export const defaultEqualizer = (enabled = false): EqualizerConfig => ({
  enabled,
  mixPercent: 100,
  lowFreqHz: 120,
  lowGainDb: 0,
  lowQ: 0.71,
  mid1FreqHz: 400,
  mid1GainDb: 0,
  mid1Q: 0.71,
  mid2FreqHz: 1500,
  mid2GainDb: 0,
  mid2Q: 1.0,
  mid3FreqHz: 5000,
  mid3GainDb: 0,
  mid3Q: 1.0,
  highFreqHz: 10000,
  highGainDb: 0,
  highQ: 0.71,
});

export const defaultCompression = (enabled = false): CompressionConfig => ({
  enabled,
  thresholdDb: -18,
  ratio: 3.5,
  attackMs: 10,
  releaseMs: 120,
  kneeDb: 6,
  makeupGainDb: 3,
  outputGainDb: 0,
});

export const defaultSaturation = (enabled = false): SaturationConfig => ({
  enabled,
  drivePercent: 25,
  warmthPercent: 40,
  tonePercent: 0,
  harmonicAmountPercent: 35,
  mixPercent: 70,
  outputLevelDb: 0,
});

export const defaultReverb = (enabled = false): ReverbConfig => ({
  enabled,
  preset: ReverbPreset.STUDIO,
  mixPercent: 81,
  roomSizePercent: 50,
  decayTimeSec: 1.6,
  dampingPercent: 50,
  wetPercent: 30,
  dryPercent: 100,
  widthPercent: 100,
  preDelayMs: 0,
});

export function applyReverbPreset(preset: ReverbPreset, enabled: boolean): ReverbConfig {
  switch (preset) {
    case ReverbPreset.SMALL_ROOM:
      return {
        enabled,
        preset,
        roomSizePercent: 28,
        decayTimeSec: 0.7,
        preDelayMs: 6,
        dampingPercent: 62,
        wetPercent: 24,
        dryPercent: 100,
        widthPercent: 65,
        mixPercent: 75,
      };
    case ReverbPreset.STUDIO:
      return {
        enabled,
        preset,
        roomSizePercent: 48,
        decayTimeSec: 1.4,
        preDelayMs: 14,
        dampingPercent: 48,
        wetPercent: 30,
        dryPercent: 100,
        widthPercent: 90,
        mixPercent: 82,
      };
    case ReverbPreset.HALL:
      return {
        enabled,
        preset,
        roomSizePercent: 82,
        decayTimeSec: 3.8,
        preDelayMs: 28,
        dampingPercent: 36,
        wetPercent: 46,
        dryPercent: 92,
        widthPercent: 100,
        mixPercent: 88,
      };
    case ReverbPreset.PLATE:
      return {
        enabled,
        preset,
        roomSizePercent: 64,
        decayTimeSec: 2.4,
        preDelayMs: 4,
        dampingPercent: 24,
        wetPercent: 38,
        dryPercent: 100,
        widthPercent: 100,
        mixPercent: 85,
      };
  }
}

export const defaultEcho = (enabled = false): EchoConfig => ({
  enabled,
  mixPercent: 60,
  timeMs: 250,
  feedbackPercent: 35,
  echoLevelPercent: 30,
  wetPercent: 35,
  dryPercent: 100,
  pingPong: false,
  widthPercent: 60,
  tempoSync: false,
  bpm: 120,
  subdivision: DelaySubdivision.EIGHTH,
  lowCutHz: 80,
  highCutHz: 8000,
});

export const defaultNoiseGate = (enabled = false): NoiseGateConfig => ({
  enabled,
  mixPercent: NoiseGateConstants.DEFAULT_MIX_PCT,
  thresholdDb: NoiseGateConstants.DEFAULT_THRESHOLD_DB,
  attackMs: NoiseGateConstants.DEFAULT_ATTACK_MS,
  holdMs: NoiseGateConstants.DEFAULT_HOLD_MS,
  releaseMs: NoiseGateConstants.DEFAULT_RELEASE_MS,
  rangeDb: NoiseGateConstants.DEFAULT_RANGE_DB,
});

export const defaultDeEsser = (enabled = false): DeEsserConfig => ({
  enabled,
  frequencyHz: 6200,
  thresholdDb: -24,
  reductionPercent: 55,
  bandwidthQ: 2.0,
  mixPercent: 100,
});

export const defaultLimiter = (enabled = false): LimiterConfig => ({
  enabled,
  thresholdDb: -3.0,
  ceilingDb: -0.3,
  releaseMs: 80,
  inputGainDb: 0,
  outputGainDb: 0,
});

export const defaultLowPass = (enabled = false): LowPassConfig => ({
  enabled,
  mixPercent: 100,
  cutoffHz: 5000,
  resonance: 0.71,
  slope: FilterSlope.SLOPE_12,
});

export const defaultHighPass = (enabled = false): HighPassConfig => ({
  enabled,
  mixPercent: 100,
  cutoffHz: 200,
  resonance: 0.71,
  slope: FilterSlope.SLOPE_12,
});

export const defaultHall = (enabled = false): HallConfig => ({
  enabled,
  echoAmount: 55,
  reverberationAmount: 71,
});

export const createDefaultEffectsRack = (): EffectsRackConfig => ({
  pitchCorrection: defaultPitchCorrection(false),
  pitchFormant: defaultPitchFormant(false),
  equalizer: defaultEqualizer(false),
  compression: defaultCompression(false),
  saturation: defaultSaturation(false),
  reverb: defaultReverb(false),
  echo: defaultEcho(false),
  noiseGate: defaultNoiseGate(false),
  deEsser: defaultDeEsser(false),
  limiter: defaultLimiter(false),
  lowPass: defaultLowPass(false),
  highPass: defaultHighPass(false),
  hall: defaultHall(false),
});

export interface StudioEffectPreset {
  id: string;
  name: string;
  effectType: StudioEffectType;
  isBuiltIn: boolean;
  payload: Record<string, unknown>;
}

export function getBuiltInPresetsForEffect(type: StudioEffectType): StudioEffectPreset[] {
  switch (type) {
    case StudioEffectType.PITCH_CORRECTION:
      return [
        {
          id: 'bi_pc_natural',
          name: 'Natural Vocal',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultPitchCorrection(true),
            correctionStrengthPercent: 45,
            retuneSpeedMs: 65,
            humanizePercent: 60,
            noteTransitionMs: 35,
          },
        },
        {
          id: 'bi_pc_tight',
          name: 'Tight Pop Tune',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultPitchCorrection(true),
            correctionStrengthPercent: 92,
            retuneSpeedMs: 8,
            humanizePercent: 12,
            noteTransitionMs: 10,
          },
        },
      ];
    case StudioEffectType.PITCH_FORMANT:
      return [
        {
          id: 'bi_pf_deep',
          name: 'Deep Narrator',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultPitchFormant(true),
            pitchShiftSt: -2.5,
            formantShiftSt: -1.8,
            formantPreservation: false,
            dryWetPercent: 100,
          },
        },
        {
          id: 'bi_pf_bright',
          name: 'Bright Formant',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultPitchFormant(true),
            pitchShiftSt: 2.0,
            formantShiftSt: 2.2,
            formantPreservation: false,
            dryWetPercent: 100,
          },
        },
      ];
    case StudioEffectType.EQUALIZER:
      return [
        {
          id: 'bi_eq_podcast',
          name: 'Podcast Crisp',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultEqualizer(true),
            lowFreqHz: 100,
            lowGainDb: -3.0,
            mid1FreqHz: 320,
            mid1GainDb: -2.0,
            mid2FreqHz: 2400,
            mid2GainDb: 2.5,
            highFreqHz: 9000,
            highGainDb: 3.0,
          },
        },
        {
          id: 'bi_eq_warm',
          name: 'Warm Presence',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultEqualizer(true),
            lowFreqHz: 140,
            lowGainDb: 3.0,
            mid1FreqHz: 450,
            mid1GainDb: -1.5,
            mid2FreqHz: 3000,
            mid2GainDb: 2.0,
            highFreqHz: 10000,
            highGainDb: 1.5,
          },
        },
      ];
    case StudioEffectType.COMPRESSION:
      return [
        {
          id: 'bi_comp_vocal',
          name: 'Broadcast Leveler',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultCompression(true),
            thresholdDb: -20,
            ratio: 4.0,
            attackMs: 8,
            releaseMs: 110,
            kneeDb: 6,
            makeupGainDb: 4,
          },
        },
        {
          id: 'bi_comp_punch',
          name: 'Punchy Voice',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultCompression(true),
            thresholdDb: -26,
            ratio: 6.5,
            attackMs: 4,
            releaseMs: 85,
            kneeDb: 4,
            makeupGainDb: 6.5,
          },
        },
      ];
    case StudioEffectType.SATURATION:
      return [
        {
          id: 'bi_sat_tube',
          name: 'Analog Tube',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultSaturation(true),
            drivePercent: 30,
            warmthPercent: 65,
            tonePercent: -10,
            harmonicAmountPercent: 45,
            mixPercent: 60,
          },
        },
        {
          id: 'bi_sat_grit',
          name: 'Tape Grit',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultSaturation(true),
            drivePercent: 62,
            warmthPercent: 45,
            tonePercent: 18,
            harmonicAmountPercent: 70,
            mixPercent: 75,
          },
        },
      ];
    case StudioEffectType.REVERB:
      return (Object.values(ReverbPreset) as ReverbPreset[]).map((rp) => ({
        id: `bi_rev_${rp.toLowerCase()}`,
        name: REVERB_PRESET_LABELS[rp],
        effectType: type,
        isBuiltIn: true,
        payload: { ...applyReverbPreset(rp, true) },
      }));
    case StudioEffectType.DELAY:
      return [
        {
          id: 'bi_del_slap',
          name: 'Vocal Slapback',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultEcho(true),
            timeMs: 115,
            feedbackPercent: 18,
            wetPercent: 28,
            dryPercent: 100,
            mixPercent: 60,
            lowCutHz: 180,
            highCutHz: 4800,
          },
        },
        {
          id: 'bi_del_ambient',
          name: 'Stereo Space',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultEcho(true),
            timeMs: 340,
            feedbackPercent: 42,
            wetPercent: 36,
            dryPercent: 100,
            widthPercent: 85,
            pingPong: true,
            mixPercent: 75,
            lowCutHz: 140,
            highCutHz: 6000,
          },
        },
      ];
    case StudioEffectType.NOISE_GATE:
      return [
        {
          id: 'bi_gate_voice',
          name: 'Clean Dialogue',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultNoiseGate(true),
            thresholdDb: -44,
            attackMs: 4,
            holdMs: 70,
            releaseMs: 120,
            rangeDb: 70,
            mixPercent: 100,
          },
        },
      ];
    case StudioEffectType.DE_ESSER:
      return [
        {
          id: 'bi_deess_soft',
          name: 'Smooth Sibilance',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultDeEsser(true),
            frequencyHz: 6400,
            thresholdDb: -25,
            reductionPercent: 50,
            bandwidthQ: 2.2,
            mixPercent: 100,
          },
        },
      ];
    case StudioEffectType.LIMITER:
      return [
        {
          id: 'bi_lim_master',
          name: 'Broadcast -1 dBTP',
          effectType: type,
          isBuiltIn: true,
          payload: {
            ...defaultLimiter(true),
            thresholdDb: -4.5,
            ceilingDb: -1.0,
            releaseMs: 90,
            inputGainDb: 2.0,
            outputGainDb: 0,
          },
        },
      ];
  }
}

export interface RecordingItem {
  id: string;
  fileName: string;
  createdAtMs: number;
  durationMs: number;
  sizeBytes: number;
  sampleRateHz: number;
  bitsPerSample: number;
  channels: number;
  wasRecovered?: boolean;
}

export interface PlaybackUiState {
  activeId: string | null;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
}

export interface StagedAudioItem {
  id: string;
  displayName: string;
  durationMs: number;
  formatLabel: string;
  dateModifiedSec: number;
  sizeBytes: number;
  file?: File;
}

export interface ImportedAudioSource {
  id: string;
  displayName: string;
  formatLabel: string;
  fromVideo: boolean;
  pcm48k: Float32Array;
  totalFrames48k: number;
  durationMs: number;
}

export interface ImportedPlaybackState {
  source: ImportedAudioSource | null;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  isImporting: boolean;
  isExporting: boolean;
  exportProgress: number;
  detectedNote: string;
}
