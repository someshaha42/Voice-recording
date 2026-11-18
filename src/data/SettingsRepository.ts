import {
  createDefaultEffectsRack,
  EffectsRackConfig,
  getVoicePresetDefaultConfig,
  MonitoringConstants,
  StudioEffectPreset,
  VoiceChangerConfig,
  VoicePreset,
} from '../types/audio';

const STORAGE_KEY = 'voxforge_studio_settings_v1';

export interface PersistedStudioSettings {
  voiceChanger: VoiceChangerConfig;
  effectsRack: EffectsRackConfig;
  monitoringDelaySlider: number;
  monitoringVolumePercent: number;
  echoCancellationEnabled: boolean;
  noiseGateThresholdDb: number;
  userEffectPresets: StudioEffectPreset[];
}

export function createDefaultPersistedSettings(): PersistedStudioSettings {
  return {
    voiceChanger: getVoicePresetDefaultConfig(VoicePreset.NORMAL, false),
    effectsRack: createDefaultEffectsRack(),
    monitoringDelaySlider: MonitoringConstants.DEFAULT_SLIDER,
    monitoringVolumePercent: MonitoringConstants.DEFAULT_VOLUME_PERCENT,
    echoCancellationEnabled: true,
    noiseGateThresholdDb: MonitoringConstants.DEFAULT_AEC_GATE_THRESHOLD_DB,
    userEffectPresets: [],
  };
}

export function loadSettings(): PersistedStudioSettings {
  const defaults = createDefaultPersistedSettings();
  if (typeof localStorage === 'undefined') return defaults;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw) as Partial<PersistedStudioSettings>;
    const fx = parsed.effectsRack;
    const defFx = defaults.effectsRack;
    return {
      voiceChanger: parsed.voiceChanger
        ? {
            ...defaults.voiceChanger,
            ...parsed.voiceChanger,
          }
        : defaults.voiceChanger,
      effectsRack: fx
        ? {
            pitchCorrection: { ...defFx.pitchCorrection, ...(fx.pitchCorrection ?? {}) },
            pitchFormant: { ...defFx.pitchFormant, ...(fx.pitchFormant ?? {}) },
            equalizer: { ...defFx.equalizer, ...(fx.equalizer ?? {}) },
            compression: { ...defFx.compression, ...(fx.compression ?? {}) },
            saturation: { ...defFx.saturation, ...(fx.saturation ?? {}) },
            reverb: { ...defFx.reverb, ...(fx.reverb ?? {}) },
            echo: { ...defFx.echo, ...(fx.echo ?? {}) },
            noiseGate: { ...defFx.noiseGate, ...(fx.noiseGate ?? {}) },
            deEsser: { ...defFx.deEsser, ...(fx.deEsser ?? {}) },
            limiter: { ...defFx.limiter, ...(fx.limiter ?? {}) },
            lowPass: { ...defFx.lowPass, ...(fx.lowPass ?? {}) },
            highPass: { ...defFx.highPass, ...(fx.highPass ?? {}) },
            hall: { ...defFx.hall, ...(fx.hall ?? {}) },
          }
        : defFx,
      monitoringDelaySlider:
        typeof parsed.monitoringDelaySlider === 'number'
          ? Math.max(
              MonitoringConstants.MIN_SLIDER,
              Math.min(MonitoringConstants.MAX_SLIDER, parsed.monitoringDelaySlider)
            )
          : defaults.monitoringDelaySlider,
      monitoringVolumePercent:
        typeof parsed.monitoringVolumePercent === 'number'
          ? Math.max(
              MonitoringConstants.MIN_VOLUME_PERCENT,
              Math.min(MonitoringConstants.MAX_VOLUME_PERCENT, parsed.monitoringVolumePercent)
            )
          : defaults.monitoringVolumePercent,
      echoCancellationEnabled:
        typeof parsed.echoCancellationEnabled === 'boolean'
          ? parsed.echoCancellationEnabled
          : defaults.echoCancellationEnabled,
      noiseGateThresholdDb:
        typeof parsed.noiseGateThresholdDb === 'number'
          ? Math.max(
              MonitoringConstants.MIN_AEC_GATE_THRESHOLD_DB,
              Math.min(
                MonitoringConstants.MAX_AEC_GATE_THRESHOLD_DB,
                Math.round(parsed.noiseGateThresholdDb)
              )
            )
          : defaults.noiseGateThresholdDb,
      userEffectPresets: Array.isArray(parsed.userEffectPresets)
        ? parsed.userEffectPresets
        : defaults.userEffectPresets,
    };
  } catch {
    return defaults;
  }
}

export function saveSettings(settings: PersistedStudioSettings): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Ignore quota errors
  }
}
