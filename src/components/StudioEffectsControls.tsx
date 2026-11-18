import React, { useMemo, useState } from 'react';
import {
  AudioWaveform,
  BookmarkPlus,
  ChevronDown,
  ChevronUp,
  Clock,
  Ear,
  Flame,
  Gauge,
  Music,
  RotateCcw,
  Shield,
  Sliders,
  VolumeX,
  Waves,
  X,
} from 'lucide-react';
import {
  applyReverbPreset,
  CompressionConfig,
  DeEsserConfig,
  DELAY_SUBDIVISION_META,
  DelaySubdivision,
  EchoConfig,
  EffectsRackConfig,
  EqualizerConfig,
  getActiveStudioEffectCount,
  getBuiltInPresetsForEffect,
  getEffectiveDelayMs,
  LimiterConfig,
  MUSICAL_KEY_META,
  MUSICAL_SCALE_META,
  MusicalKey,
  MusicalScale,
  NoiseGateConfig,
  NoiseGateConstants,
  PitchCorrectionConfig,
  PitchFormantConfig,
  REVERB_PRESET_LABELS,
  ReverbConfig,
  ReverbPreset,
  SaturationConfig,
  STUDIO_EFFECT_LABELS,
  StudioEffectPreset,
  StudioEffectType,
} from '../types/audio';
import { formatDb, formatFreqHz } from '../utils/format';
import { EqualizerResponseGraph } from './EqualizerResponseGraph';

interface StudioEffectsControlsProps {
  effectsConfig: EffectsRackConfig;
  userPresets: StudioEffectPreset[];
  onPitchCorrectionChanged: (c: PitchCorrectionConfig) => void;
  onPitchFormantChanged: (c: PitchFormantConfig) => void;
  onEqualizerChanged: (c: EqualizerConfig) => void;
  onCompressionChanged: (c: CompressionConfig) => void;
  onSaturationChanged: (c: SaturationConfig) => void;
  onReverbChanged: (c: ReverbConfig) => void;
  onDelayChanged: (c: EchoConfig) => void;
  onNoiseGateChanged: (c: NoiseGateConfig) => void;
  onDeEsserChanged: (c: DeEsserConfig) => void;
  onLimiterChanged: (c: LimiterConfig) => void;
  onResetEffect: (type: StudioEffectType) => void;
  onSavePreset: (type: StudioEffectType, name: string) => void;
  onLoadPreset: (preset: StudioEffectPreset) => void;
  onDeleteUserPreset: (presetId: string) => void;
}

export const StudioEffectsControls: React.FC<StudioEffectsControlsProps> = React.memo(({
  effectsConfig,
  userPresets,
  onPitchCorrectionChanged,
  onPitchFormantChanged,
  onEqualizerChanged,
  onCompressionChanged,
  onSaturationChanged,
  onReverbChanged,
  onDelayChanged,
  onNoiseGateChanged,
  onDeEsserChanged,
  onLimiterChanged,
  onResetEffect,
  onSavePreset,
  onLoadPreset,
  onDeleteUserPreset,
}) => {
  const activeCount = getActiveStudioEffectCount(effectsConfig);
  const pc = effectsConfig.pitchCorrection;
  const pf = effectsConfig.pitchFormant;
  const eq = effectsConfig.equalizer;
  const comp = effectsConfig.compression;
  const sat = effectsConfig.saturation;
  const rev = effectsConfig.reverb;
  const del = effectsConfig.echo;
  const gate = effectsConfig.noiseGate;
  const de = effectsConfig.deEsser;
  const lim = effectsConfig.limiter;

  return (
    <div className="flex flex-col gap-3">
      {/* Header Row */}
      <div className="flex items-center justify-between pt-2 pb-0.5">
        <span className="text-[11px] font-bold tracking-wider text-[#00D4FF]">
          AUDIO EFFECTS &amp; STUDIO CONTROLS
        </span>
        <span
          className={`px-2 py-0.5 rounded-md border text-[11px] font-semibold font-mono-tabular ${
            activeCount > 0
              ? 'bg-[#123222] border-[#22C55E]/60 text-[#22C55E]'
              : 'bg-[#131822] border-[#2C374D] text-[#64748B]'
          }`}
        >
          {activeCount}/10 ACTIVE
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 items-start">
      {/* 1. Pitch Correction */}
      <StudioEffectCard
        type={StudioEffectType.PITCH_CORRECTION}
        icon={<Music className="w-[18px] h-[18px]" />}
        enabled={pc.enabled}
        subtitle={
          pc.enabled
            ? `${MUSICAL_KEY_META[pc.key].label} ${MUSICAL_SCALE_META[pc.scale].label} • ${pc.correctionStrengthPercent}%`
            : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onPitchCorrectionChanged({ ...pc, enabled })}
        onReset={() => onResetEffect(StudioEffectType.PITCH_CORRECTION)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.PITCH_CORRECTION, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioToggleRow
          label="Automatic Key Detection"
          checked={pc.automaticKeyDetection}
          onChange={(automaticKeyDetection) =>
            onPitchCorrectionChanged({ ...pc, automaticKeyDetection })
          }
        />

        {!pc.automaticKeyDetection && (
          <div className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-[#94A3B8]">Key Selection</span>
            <div className="flex gap-1.5 overflow-x-auto pb-1">
              {(Object.values(MusicalKey) as MusicalKey[]).map((k) => {
                const selected = pc.key === k;
                return (
                  <button
                    key={k}
                    type="button"
                    onClick={() => onPitchCorrectionChanged({ ...pc, key: k })}
                    className={`px-2.5 py-1.5 rounded-lg border text-xs font-medium shrink-0 cursor-pointer transition-colors ${
                      selected
                        ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
                        : 'bg-[#131822] border-[#2C374D] text-[#94A3B8] hover:text-[#F1F5F9]'
                    }`}
                  >
                    {MUSICAL_KEY_META[k].label}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-[#94A3B8]">Scale Selection</span>
          <div className="flex flex-wrap gap-1.5">
            {(Object.values(MusicalScale) as MusicalScale[]).map((sc) => {
              const selected = pc.scale === sc;
              return (
                <button
                  key={sc}
                  type="button"
                  onClick={() => onPitchCorrectionChanged({ ...pc, scale: sc })}
                  className={`px-3 py-1.5 rounded-lg border text-xs font-medium cursor-pointer transition-colors ${
                    selected
                      ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
                      : 'bg-[#131822] border-[#2C374D] text-[#94A3B8] hover:text-[#F1F5F9]'
                  }`}
                >
                  {MUSICAL_SCALE_META[sc].label}
                </button>
              );
            })}
          </div>
        </div>

        <StudioEffectSlider
          label="Correction Strength"
          valueText={`${pc.correctionStrengthPercent}%`}
          value={pc.correctionStrengthPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) =>
            onPitchCorrectionChanged({ ...pc, correctionStrengthPercent: Math.round(v) })
          }
        />

        <StudioEffectSlider
          label="Retune Speed"
          valueText={`${pc.retuneSpeedMs} ms`}
          value={pc.retuneSpeedMs}
          min={0}
          max={200}
          step={1}
          minLabel="0 ms (Fast)"
          maxLabel="200 ms"
          onChange={(v) => onPitchCorrectionChanged({ ...pc, retuneSpeedMs: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioToggleRow
            label="Pitch Detection"
            checked={pc.pitchDetectionEnabled}
            onChange={(pitchDetectionEnabled) =>
              onPitchCorrectionChanged({ ...pc, pitchDetectionEnabled })
            }
          />
          <StudioEffectSlider
            label="Humanize"
            valueText={`${pc.humanizePercent}%`}
            value={pc.humanizePercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onPitchCorrectionChanged({ ...pc, humanizePercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Note Transition"
            valueText={`${pc.noteTransitionMs} ms`}
            value={pc.noteTransitionMs}
            min={0}
            max={150}
            step={1}
            minLabel="0 ms"
            maxLabel="150 ms"
            onChange={(v) => onPitchCorrectionChanged({ ...pc, noteTransitionMs: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Reference Tuning (A4)"
            valueText={`${Math.round(pc.referenceTuningHz)} Hz`}
            value={pc.referenceTuningHz}
            min={415}
            max={465}
            step={1}
            minLabel="415 Hz"
            maxLabel="465 Hz"
            onChange={(v) =>
              onPitchCorrectionChanged({ ...pc, referenceTuningHz: Math.round(v) })
            }
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 2. Pitch & Formant */}
      <StudioEffectCard
        type={StudioEffectType.PITCH_FORMANT}
        icon={<AudioWaveform className="w-[18px] h-[18px]" />}
        enabled={pf.enabled}
        subtitle={
          pf.enabled
            ? `${pf.pitchShiftSt >= 0 ? '+' : ''}${pf.pitchShiftSt.toFixed(1)} st • Formant ${
                pf.formantShiftSt >= 0 ? '+' : ''
              }${pf.formantShiftSt.toFixed(1)} st`
            : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onPitchFormantChanged({ ...pf, enabled })}
        onReset={() => onResetEffect(StudioEffectType.PITCH_FORMANT)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.PITCH_FORMANT, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Pitch Shift"
          valueText={`${pf.pitchShiftSt >= 0 ? '+' : ''}${pf.pitchShiftSt.toFixed(1)} st`}
          value={pf.pitchShiftSt}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 st"
          maxLabel="+12 st"
          onChange={(v) =>
            onPitchFormantChanged({ ...pf, pitchShiftSt: Math.round(v * 2) / 2 })
          }
        />
        <StudioEffectSlider
          label="Formant Shift"
          valueText={`${pf.formantShiftSt >= 0 ? '+' : ''}${pf.formantShiftSt.toFixed(1)} st`}
          value={pf.formantShiftSt}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 st"
          maxLabel="+12 st"
          onChange={(v) =>
            onPitchFormantChanged({ ...pf, formantShiftSt: Math.round(v * 2) / 2 })
          }
        />
        <StudioEffectSlider
          label="Dry / Wet Mix"
          valueText={`${pf.dryWetPercent}%`}
          value={pf.dryWetPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onPitchFormantChanged({ ...pf, dryWetPercent: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Fine Tune"
            valueText={`${pf.fineTuneCents} cents`}
            value={pf.fineTuneCents}
            min={-100}
            max={100}
            step={1}
            minLabel="-100 ct"
            maxLabel="+100 ct"
            onChange={(v) => onPitchFormantChanged({ ...pf, fineTuneCents: Math.round(v) })}
          />
          <StudioToggleRow
            label="Formant Preservation"
            checked={pf.formantPreservation}
            onChange={(formantPreservation) =>
              onPitchFormantChanged({ ...pf, formantPreservation })
            }
          />
          <StudioEffectSlider
            label="Output Gain"
            valueText={formatDb(pf.outputGainDb)}
            value={pf.outputGainDb}
            min={-12}
            max={12}
            step={0.5}
            minLabel="-12 dB"
            maxLabel="+12 dB"
            onChange={(v) =>
              onPitchFormantChanged({ ...pf, outputGainDb: Math.round(v * 2) / 2 })
            }
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 3. Equalizer (EQ) + Frequency Graph */}
      <StudioEffectCard
        type={StudioEffectType.EQUALIZER}
        icon={<Sliders className="w-[18px] h-[18px]" />}
        enabled={eq.enabled}
        subtitle={eq.enabled ? `5-Band Parametric • Mix ${eq.mixPercent}%` : undefined}
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onEqualizerChanged({ ...eq, enabled })}
        onReset={() => onResetEffect(StudioEffectType.EQUALIZER)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.EQUALIZER, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <EqualizerResponseGraph config={eq} />

        <StudioEffectSlider
          label="Low / Bass Gain"
          valueText={formatDb(eq.lowGainDb)}
          value={eq.lowGainDb}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 dB"
          maxLabel="+12 dB"
          onChange={(v) => onEqualizerChanged({ ...eq, lowGainDb: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="Low-Mid Gain"
          valueText={formatDb(eq.mid1GainDb)}
          value={eq.mid1GainDb}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 dB"
          maxLabel="+12 dB"
          onChange={(v) => onEqualizerChanged({ ...eq, mid1GainDb: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="Mid Gain"
          valueText={formatDb(eq.mid2GainDb)}
          value={eq.mid2GainDb}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 dB"
          maxLabel="+12 dB"
          onChange={(v) => onEqualizerChanged({ ...eq, mid2GainDb: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="High-Mid Gain"
          valueText={formatDb(eq.mid3GainDb)}
          value={eq.mid3GainDb}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 dB"
          maxLabel="+12 dB"
          onChange={(v) => onEqualizerChanged({ ...eq, mid3GainDb: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="High / Treble Gain"
          valueText={formatDb(eq.highGainDb)}
          value={eq.highGainDb}
          min={-12}
          max={12}
          step={0.5}
          minLabel="-12 dB"
          maxLabel="+12 dB"
          onChange={(v) => onEqualizerChanged({ ...eq, highGainDb: Math.round(v * 2) / 2 })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Mix"
            valueText={`${eq.mixPercent}%`}
            value={eq.mixPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onEqualizerChanged({ ...eq, mixPercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Low Freq"
            valueText={formatFreqHz(eq.lowFreqHz)}
            value={eq.lowFreqHz}
            min={20}
            max={500}
            step={5}
            minLabel="20 Hz"
            maxLabel="500 Hz"
            onChange={(v) => onEqualizerChanged({ ...eq, lowFreqHz: Math.round(v / 5) * 5 })}
          />
          <StudioEffectSlider
            label="Low Q Factor"
            valueText={eq.lowQ.toFixed(2)}
            value={eq.lowQ}
            min={0.3}
            max={4.0}
            step={0.05}
            minLabel="0.30"
            maxLabel="4.00"
            onChange={(v) => onEqualizerChanged({ ...eq, lowQ: Math.round(v * 20) / 20 })}
          />
          <StudioEffectSlider
            label="Low-Mid Freq"
            valueText={formatFreqHz(eq.mid1FreqHz)}
            value={eq.mid1FreqHz}
            min={100}
            max={1500}
            step={10}
            minLabel="100 Hz"
            maxLabel="1.5 kHz"
            onChange={(v) => onEqualizerChanged({ ...eq, mid1FreqHz: Math.round(v / 10) * 10 })}
          />
          <StudioEffectSlider
            label="Low-Mid Q"
            valueText={eq.mid1Q.toFixed(2)}
            value={eq.mid1Q}
            min={0.1}
            max={10.0}
            step={0.05}
            minLabel="0.10"
            maxLabel="10.0"
            onChange={(v) => onEqualizerChanged({ ...eq, mid1Q: Math.round(v * 20) / 20 })}
          />
          <StudioEffectSlider
            label="Mid Freq"
            valueText={formatFreqHz(eq.mid2FreqHz)}
            value={eq.mid2FreqHz}
            min={300}
            max={5000}
            step={25}
            minLabel="300 Hz"
            maxLabel="5.0 kHz"
            onChange={(v) => onEqualizerChanged({ ...eq, mid2FreqHz: Math.round(v / 25) * 25 })}
          />
          <StudioEffectSlider
            label="Mid Q"
            valueText={eq.mid2Q.toFixed(2)}
            value={eq.mid2Q}
            min={0.1}
            max={10.0}
            step={0.05}
            minLabel="0.10"
            maxLabel="10.0"
            onChange={(v) => onEqualizerChanged({ ...eq, mid2Q: Math.round(v * 20) / 20 })}
          />
          <StudioEffectSlider
            label="High-Mid Freq"
            valueText={formatFreqHz(eq.mid3FreqHz)}
            value={eq.mid3FreqHz}
            min={1000}
            max={12000}
            step={50}
            minLabel="1.0 kHz"
            maxLabel="12 kHz"
            onChange={(v) => onEqualizerChanged({ ...eq, mid3FreqHz: Math.round(v / 50) * 50 })}
          />
          <StudioEffectSlider
            label="High-Mid Q"
            valueText={eq.mid3Q.toFixed(2)}
            value={eq.mid3Q}
            min={0.1}
            max={10.0}
            step={0.05}
            minLabel="0.10"
            maxLabel="10.0"
            onChange={(v) => onEqualizerChanged({ ...eq, mid3Q: Math.round(v * 20) / 20 })}
          />
          <StudioEffectSlider
            label="High Freq"
            valueText={formatFreqHz(eq.highFreqHz)}
            value={eq.highFreqHz}
            min={2000}
            max={16000}
            step={100}
            minLabel="2.0 kHz"
            maxLabel="16 kHz"
            onChange={(v) => onEqualizerChanged({ ...eq, highFreqHz: Math.round(v / 100) * 100 })}
          />
          <StudioEffectSlider
            label="High Q Factor"
            valueText={eq.highQ.toFixed(2)}
            value={eq.highQ}
            min={0.3}
            max={4.0}
            step={0.05}
            minLabel="0.30"
            maxLabel="4.00"
            onChange={(v) => onEqualizerChanged({ ...eq, highQ: Math.round(v * 20) / 20 })}
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 4. Compression */}
      <StudioEffectCard
        type={StudioEffectType.COMPRESSION}
        icon={<Gauge className="w-[18px] h-[18px]" />}
        enabled={comp.enabled}
        subtitle={
          comp.enabled ? `${comp.thresholdDb.toFixed(0)} dB • ${comp.ratio.toFixed(1)}:1` : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onCompressionChanged({ ...comp, enabled })}
        onReset={() => onResetEffect(StudioEffectType.COMPRESSION)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.COMPRESSION, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Threshold"
          valueText={formatDb(comp.thresholdDb)}
          value={comp.thresholdDb}
          min={-60}
          max={0}
          step={0.5}
          minLabel="-60 dB"
          maxLabel="0 dB"
          onChange={(v) =>
            onCompressionChanged({ ...comp, thresholdDb: Math.round(v * 2) / 2 })
          }
        />
        <StudioEffectSlider
          label="Ratio"
          valueText={`${comp.ratio.toFixed(1)}:1`}
          value={comp.ratio}
          min={1.0}
          max={20.0}
          step={0.5}
          minLabel="1.0:1"
          maxLabel="20.0:1"
          onChange={(v) => onCompressionChanged({ ...comp, ratio: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="Makeup Gain"
          valueText={formatDb(comp.makeupGainDb)}
          value={comp.makeupGainDb}
          min={0}
          max={24}
          step={0.5}
          minLabel="0 dB"
          maxLabel="+24 dB"
          onChange={(v) =>
            onCompressionChanged({ ...comp, makeupGainDb: Math.round(v * 2) / 2 })
          }
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Attack"
            valueText={`${comp.attackMs.toFixed(1)} ms`}
            value={comp.attackMs}
            min={0.5}
            max={100}
            step={0.5}
            minLabel="0.5 ms"
            maxLabel="100 ms"
            onChange={(v) => onCompressionChanged({ ...comp, attackMs: Math.round(v * 2) / 2 })}
          />
          <StudioEffectSlider
            label="Release"
            valueText={`${Math.round(comp.releaseMs)} ms`}
            value={comp.releaseMs}
            min={10}
            max={1000}
            step={5}
            minLabel="10 ms"
            maxLabel="1000 ms"
            onChange={(v) =>
              onCompressionChanged({ ...comp, releaseMs: Math.round(v / 5) * 5 })
            }
          />
          <StudioEffectSlider
            label="Knee"
            valueText={`${comp.kneeDb.toFixed(1)} dB`}
            value={comp.kneeDb}
            min={0}
            max={24}
            step={0.5}
            minLabel="0 dB"
            maxLabel="24 dB"
            onChange={(v) => onCompressionChanged({ ...comp, kneeDb: Math.round(v * 2) / 2 })}
          />
          <StudioEffectSlider
            label="Output Gain"
            valueText={formatDb(comp.outputGainDb)}
            value={comp.outputGainDb}
            min={-12}
            max={12}
            step={0.5}
            minLabel="-12 dB"
            maxLabel="+12 dB"
            onChange={(v) =>
              onCompressionChanged({ ...comp, outputGainDb: Math.round(v * 2) / 2 })
            }
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 5. Saturation */}
      <StudioEffectCard
        type={StudioEffectType.SATURATION}
        icon={<Flame className="w-[18px] h-[18px]" />}
        enabled={sat.enabled}
        subtitle={sat.enabled ? `Drive ${sat.drivePercent}% • Warmth ${sat.warmthPercent}%` : undefined}
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onSaturationChanged({ ...sat, enabled })}
        onReset={() => onResetEffect(StudioEffectType.SATURATION)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.SATURATION, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Drive"
          valueText={`${sat.drivePercent}%`}
          value={sat.drivePercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onSaturationChanged({ ...sat, drivePercent: Math.round(v) })}
        />
        <StudioEffectSlider
          label="Warmth"
          valueText={`${sat.warmthPercent}%`}
          value={sat.warmthPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onSaturationChanged({ ...sat, warmthPercent: Math.round(v) })}
        />
        <StudioEffectSlider
          label="Mix"
          valueText={`${sat.mixPercent}%`}
          value={sat.mixPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onSaturationChanged({ ...sat, mixPercent: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Tone"
            valueText={`${sat.tonePercent > 0 ? '+' : ''}${sat.tonePercent}%`}
            value={sat.tonePercent}
            min={-100}
            max={100}
            step={1}
            minLabel="Warm"
            maxLabel="Bright"
            onChange={(v) => onSaturationChanged({ ...sat, tonePercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Harmonic Amount"
            valueText={`${sat.harmonicAmountPercent}%`}
            value={sat.harmonicAmountPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onSaturationChanged({ ...sat, harmonicAmountPercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Output Level"
            valueText={formatDb(sat.outputLevelDb)}
            value={sat.outputLevelDb}
            min={-12}
            max={12}
            step={0.5}
            minLabel="-12 dB"
            maxLabel="+12 dB"
            onChange={(v) =>
              onSaturationChanged({ ...sat, outputLevelDb: Math.round(v * 2) / 2 })
            }
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 6. Reverb */}
      <StudioEffectCard
        type={StudioEffectType.REVERB}
        icon={<Waves className="w-[18px] h-[18px]" />}
        enabled={rev.enabled}
        subtitle={
          rev.enabled ? `${REVERB_PRESET_LABELS[rev.preset]} • ${rev.decayTimeSec.toFixed(1)}s` : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onReverbChanged({ ...rev, enabled })}
        onReset={() => onResetEffect(StudioEffectType.REVERB)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.REVERB, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <div className="flex gap-1.5 overflow-x-auto pb-1">
          {(Object.values(ReverbPreset) as ReverbPreset[]).map((preset) => {
            const selected = rev.preset === preset;
            return (
              <button
                key={preset}
                type="button"
                onClick={() => onReverbChanged(applyReverbPreset(preset, rev.enabled))}
                className={`px-3 py-1.5 rounded-lg border text-xs font-medium shrink-0 cursor-pointer transition-colors ${
                  selected
                    ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
                    : 'bg-[#131822] border-[#2C374D] text-[#94A3B8] hover:text-[#F1F5F9]'
                }`}
              >
                {REVERB_PRESET_LABELS[preset]}
              </button>
            );
          })}
        </div>

        <StudioEffectSlider
          label="Room Size"
          valueText={`${rev.roomSizePercent}%`}
          value={rev.roomSizePercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onReverbChanged({ ...rev, roomSizePercent: Math.round(v) })}
        />
        <StudioEffectSlider
          label="Decay Time"
          valueText={`${rev.decayTimeSec.toFixed(1)} s`}
          value={rev.decayTimeSec}
          min={0.2}
          max={8.0}
          step={0.1}
          minLabel="0.2 s"
          maxLabel="8.0 s"
          onChange={(v) => onReverbChanged({ ...rev, decayTimeSec: Math.round(v * 10) / 10 })}
        />
        <StudioEffectSlider
          label="Wet Level"
          valueText={`${rev.wetPercent}%`}
          value={rev.wetPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onReverbChanged({ ...rev, wetPercent: Math.round(v) })}
        />
        <StudioEffectSlider
          label="Dry Level"
          valueText={`${rev.dryPercent}%`}
          value={rev.dryPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onReverbChanged({ ...rev, dryPercent: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Pre-Delay"
            valueText={`${rev.preDelayMs.toFixed(1)} ms`}
            value={rev.preDelayMs}
            min={0}
            max={100}
            step={0.5}
            minLabel="0 ms"
            maxLabel="100 ms"
            onChange={(v) => onReverbChanged({ ...rev, preDelayMs: Math.round(v * 2) / 2 })}
          />
          <StudioEffectSlider
            label="Damping"
            valueText={`${rev.dampingPercent}%`}
            value={rev.dampingPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onReverbChanged({ ...rev, dampingPercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Stereo Width"
            valueText={`${rev.widthPercent}%`}
            value={rev.widthPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onReverbChanged({ ...rev, widthPercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Mix"
            valueText={`${rev.mixPercent}%`}
            value={rev.mixPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onReverbChanged({ ...rev, mixPercent: Math.round(v) })}
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 7. Delay (synchronized with Echo in Effects tab) */}
      <StudioEffectCard
        type={StudioEffectType.DELAY}
        icon={<Clock className="w-[18px] h-[18px]" />}
        enabled={del.enabled}
        subtitle={
          del.enabled ? `${getEffectiveDelayMs(del)} ms • FB ${del.feedbackPercent}%` : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onDelayChanged({ ...del, enabled })}
        onReset={() => onResetEffect(StudioEffectType.DELAY)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.DELAY, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Delay Time"
          valueText={`${getEffectiveDelayMs(del)} ms`}
          value={getEffectiveDelayMs(del)}
          min={20}
          max={1000}
          step={5}
          minLabel="20 ms"
          maxLabel="1000 ms"
          enabled={!del.tempoSync}
          onChange={(v) => onDelayChanged({ ...del, timeMs: Math.round(v / 5) * 5 })}
        />
        <StudioEffectSlider
          label="Feedback"
          valueText={`${del.feedbackPercent}%`}
          value={del.feedbackPercent}
          min={0}
          max={90}
          step={1}
          minLabel="0%"
          maxLabel="90%"
          onChange={(v) => onDelayChanged({ ...del, feedbackPercent: Math.round(v) })}
        />
        <StudioEffectSlider
          label="Wet Level"
          valueText={`${del.wetPercent}%`}
          value={del.wetPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onDelayChanged({ ...del, wetPercent: Math.round(v) })}
        />
        <StudioEffectSlider
          label="Dry Level"
          valueText={`${del.dryPercent}%`}
          value={del.dryPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onDelayChanged({ ...del, dryPercent: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioToggleRow
            label="Tempo Sync"
            checked={del.tempoSync}
            onChange={(tempoSync) => onDelayChanged({ ...del, tempoSync })}
          />
          {del.tempoSync && (
            <>
              <StudioEffectSlider
                label="Tempo (BPM)"
                valueText={`${del.bpm} BPM`}
                value={del.bpm}
                min={60}
                max={200}
                step={1}
                minLabel="60 BPM"
                maxLabel="200 BPM"
                onChange={(v) => onDelayChanged({ ...del, bpm: Math.round(v) })}
              />
              <div className="flex flex-wrap gap-1.5">
                {(Object.values(DelaySubdivision) as DelaySubdivision[]).map((div) => {
                  const selected = del.subdivision === div;
                  return (
                    <button
                      key={div}
                      type="button"
                      onClick={() => onDelayChanged({ ...del, subdivision: div })}
                      className={`px-3 py-1.5 rounded-lg border text-xs font-medium cursor-pointer transition-colors ${
                        selected
                          ? 'bg-[#0E303D] border-[#00D4FF] text-[#00D4FF]'
                          : 'bg-[#131822] border-[#2C374D] text-[#94A3B8] hover:text-[#F1F5F9]'
                      }`}
                    >
                      {DELAY_SUBDIVISION_META[div].label}
                    </button>
                  );
                })}
              </div>
            </>
          )}
          <StudioEffectSlider
            label="Stereo Width"
            valueText={`${del.widthPercent}%`}
            value={del.widthPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onDelayChanged({ ...del, widthPercent: Math.round(v) })}
          />
          <StudioEffectSlider
            label="Low Cut"
            valueText={formatFreqHz(del.lowCutHz)}
            value={del.lowCutHz}
            min={20}
            max={1000}
            step={10}
            minLabel="20 Hz"
            maxLabel="1.0 kHz"
            onChange={(v) => onDelayChanged({ ...del, lowCutHz: Math.round(v / 10) * 10 })}
          />
          <StudioEffectSlider
            label="High Cut"
            valueText={formatFreqHz(del.highCutHz)}
            value={del.highCutHz}
            min={1000}
            max={18000}
            step={100}
            minLabel="1.0 kHz"
            maxLabel="18 kHz"
            onChange={(v) => onDelayChanged({ ...del, highCutHz: Math.round(v / 100) * 100 })}
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 8. Noise Gate (synchronized with Effects tab) */}
      <StudioEffectCard
        type={StudioEffectType.NOISE_GATE}
        icon={<VolumeX className="w-[18px] h-[18px]" />}
        enabled={gate.enabled}
        subtitle={
          gate.enabled
            ? `${gate.thresholdDb.toFixed(0)} dB • Range ${gate.rangeDb.toFixed(0)} dB`
            : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onNoiseGateChanged({ ...gate, enabled })}
        onReset={() => onResetEffect(StudioEffectType.NOISE_GATE)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.NOISE_GATE, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Threshold"
          valueText={formatDb(gate.thresholdDb)}
          value={gate.thresholdDb}
          min={NoiseGateConstants.MIN_THRESHOLD_DB}
          max={NoiseGateConstants.MAX_THRESHOLD_DB}
          step={0.5}
          minLabel="-70 dB"
          maxLabel="-10 dB"
          onChange={(v) =>
            onNoiseGateChanged({ ...gate, thresholdDb: Math.round(v * 2) / 2 })
          }
        />
        <StudioEffectSlider
          label="Range"
          valueText={formatDb(gate.rangeDb)}
          value={gate.rangeDb}
          min={NoiseGateConstants.MIN_RANGE_DB}
          max={NoiseGateConstants.MAX_RANGE_DB}
          step={1}
          minLabel="0 dB"
          maxLabel="80 dB"
          onChange={(v) => onNoiseGateChanged({ ...gate, rangeDb: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Attack"
            valueText={`${gate.attackMs.toFixed(1)} ms`}
            value={gate.attackMs}
            min={NoiseGateConstants.MIN_ATTACK_MS}
            max={NoiseGateConstants.MAX_ATTACK_MS}
            step={0.5}
            minLabel="0.5 ms"
            maxLabel="50 ms"
            onChange={(v) => onNoiseGateChanged({ ...gate, attackMs: Math.round(v * 2) / 2 })}
          />
          <StudioEffectSlider
            label="Hold"
            valueText={`${Math.round(gate.holdMs)} ms`}
            value={gate.holdMs}
            min={NoiseGateConstants.MIN_HOLD_MS}
            max={NoiseGateConstants.MAX_HOLD_MS}
            step={5}
            minLabel="0 ms"
            maxLabel="500 ms"
            onChange={(v) => onNoiseGateChanged({ ...gate, holdMs: Math.round(v / 5) * 5 })}
          />
          <StudioEffectSlider
            label="Release"
            valueText={`${Math.round(gate.releaseMs)} ms`}
            value={gate.releaseMs}
            min={NoiseGateConstants.MIN_RELEASE_MS}
            max={NoiseGateConstants.MAX_RELEASE_MS}
            step={10}
            minLabel="20 ms"
            maxLabel="600 ms"
            onChange={(v) => onNoiseGateChanged({ ...gate, releaseMs: Math.round(v / 10) * 10 })}
          />
          <StudioEffectSlider
            label="Mix"
            valueText={`${gate.mixPercent}%`}
            value={gate.mixPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onNoiseGateChanged({ ...gate, mixPercent: Math.round(v) })}
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 9. De-Esser */}
      <StudioEffectCard
        type={StudioEffectType.DE_ESSER}
        icon={<Ear className="w-[18px] h-[18px]" />}
        enabled={de.enabled}
        subtitle={
          de.enabled ? `${formatFreqHz(de.frequencyHz)} • ${de.reductionPercent}%` : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onDeEsserChanged({ ...de, enabled })}
        onReset={() => onResetEffect(StudioEffectType.DE_ESSER)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.DE_ESSER, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Frequency"
          valueText={formatFreqHz(de.frequencyHz)}
          value={de.frequencyHz}
          min={3000}
          max={10000}
          step={50}
          minLabel="3.0 kHz"
          maxLabel="10.0 kHz"
          onChange={(v) => onDeEsserChanged({ ...de, frequencyHz: Math.round(v / 50) * 50 })}
        />
        <StudioEffectSlider
          label="Threshold"
          valueText={formatDb(de.thresholdDb)}
          value={de.thresholdDb}
          min={-50}
          max={0}
          step={0.5}
          minLabel="-50 dB"
          maxLabel="0 dB"
          onChange={(v) => onDeEsserChanged({ ...de, thresholdDb: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="Reduction"
          valueText={`${de.reductionPercent}%`}
          value={de.reductionPercent}
          min={0}
          max={100}
          step={1}
          minLabel="0%"
          maxLabel="100%"
          onChange={(v) => onDeEsserChanged({ ...de, reductionPercent: Math.round(v) })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Bandwidth (Q)"
            valueText={de.bandwidthQ.toFixed(2)}
            value={de.bandwidthQ}
            min={0.5}
            max={6.0}
            step={0.1}
            minLabel="0.50"
            maxLabel="6.00"
            onChange={(v) => onDeEsserChanged({ ...de, bandwidthQ: Math.round(v * 10) / 10 })}
          />
          <StudioEffectSlider
            label="Mix"
            valueText={`${de.mixPercent}%`}
            value={de.mixPercent}
            min={0}
            max={100}
            step={1}
            minLabel="0%"
            maxLabel="100%"
            onChange={(v) => onDeEsserChanged({ ...de, mixPercent: Math.round(v) })}
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>

      {/* 10. Limiter */}
      <StudioEffectCard
        type={StudioEffectType.LIMITER}
        icon={<Shield className="w-[18px] h-[18px]" />}
        enabled={lim.enabled}
        subtitle={
          lim.enabled
            ? `Ceil ${lim.ceilingDb.toFixed(1)} dB • Thresh ${lim.thresholdDb.toFixed(1)} dB`
            : undefined
        }
        userPresets={userPresets}
        onEnabledChanged={(enabled) => onLimiterChanged({ ...lim, enabled })}
        onReset={() => onResetEffect(StudioEffectType.LIMITER)}
        onSavePreset={(name) => onSavePreset(StudioEffectType.LIMITER, name)}
        onLoadPreset={onLoadPreset}
        onDeleteUserPreset={onDeleteUserPreset}
      >
        <StudioEffectSlider
          label="Threshold"
          valueText={formatDb(lim.thresholdDb)}
          value={lim.thresholdDb}
          min={-30}
          max={0}
          step={0.5}
          minLabel="-30 dB"
          maxLabel="0 dB"
          onChange={(v) => onLimiterChanged({ ...lim, thresholdDb: Math.round(v * 2) / 2 })}
        />
        <StudioEffectSlider
          label="Ceiling"
          valueText={formatDb(lim.ceilingDb)}
          value={lim.ceilingDb}
          min={-12}
          max={-0.1}
          step={0.1}
          minLabel="-12 dB"
          maxLabel="-0.1 dB"
          onChange={(v) => onLimiterChanged({ ...lim, ceilingDb: Math.round(v * 10) / 10 })}
        />
        <StudioEffectSlider
          label="Input Gain"
          valueText={formatDb(lim.inputGainDb)}
          value={lim.inputGainDb}
          min={-12}
          max={18}
          step={0.5}
          minLabel="-12 dB"
          maxLabel="+18 dB"
          onChange={(v) => onLimiterChanged({ ...lim, inputGainDb: Math.round(v * 2) / 2 })}
        />

        <AdvancedSettingsSection>
          <StudioEffectSlider
            label="Release"
            valueText={`${Math.round(lim.releaseMs)} ms`}
            value={lim.releaseMs}
            min={10}
            max={500}
            step={5}
            minLabel="10 ms"
            maxLabel="500 ms"
            onChange={(v) => onLimiterChanged({ ...lim, releaseMs: Math.round(v / 5) * 5 })}
          />
          <StudioEffectSlider
            label="Output Gain"
            valueText={formatDb(lim.outputGainDb)}
            value={lim.outputGainDb}
            min={-12}
            max={6}
            step={0.5}
            minLabel="-12 dB"
            maxLabel="+6 dB"
            onChange={(v) => onLimiterChanged({ ...lim, outputGainDb: Math.round(v * 2) / 2 })}
          />
        </AdvancedSettingsSection>
      </StudioEffectCard>
      </div>
    </div>
  );
});

StudioEffectsControls.displayName = 'StudioEffectsControls';

interface StudioEffectCardProps {
  type: StudioEffectType;
  icon: React.ReactNode;
  enabled: boolean;
  subtitle?: string;
  userPresets: StudioEffectPreset[];
  onEnabledChanged: (enabled: boolean) => void;
  onReset: () => void;
  onSavePreset: (name: string) => void;
  onLoadPreset: (preset: StudioEffectPreset) => void;
  onDeleteUserPreset: (presetId: string) => void;
  children: React.ReactNode;
}

const StudioEffectCard: React.FC<StudioEffectCardProps> = React.memo(({
  type,
  icon,
  enabled,
  subtitle,
  userPresets,
  onEnabledChanged,
  onReset,
  onSavePreset,
  onLoadPreset,
  onDeleteUserPreset,
  children,
}) => {
  const [showSaveModal, setShowSaveModal] = useState(false);
  const [presetName, setPresetName] = useState('');

  const availablePresets = useMemo(
    () => [
      ...getBuiltInPresetsForEffect(type),
      ...userPresets.filter((p) => p.effectType === type),
    ],
    [type, userPresets]
  );

  const label = STUDIO_EFFECT_LABELS[type];

  return (
    <>
      <div
        className={`perf-card w-full rounded-xl bg-[#1A2130] border ${
          enabled ? 'border-[#00D4FF]/65' : 'border-[#2C374D]'
        }`}
      >
        <div className="flex items-center justify-between gap-2 px-3 py-2">
          <div className="flex items-center gap-2.5 min-w-0 flex-1">
            <div
              className={`w-[34px] h-[34px] rounded-lg shrink-0 flex items-center justify-center ${
                enabled ? 'bg-[#0E303D] text-[#00D4FF]' : 'bg-[#131822] text-[#94A3B8]'
              }`}
            >
              {icon}
            </div>
            <div className="min-w-0 flex-1">
              <p
                className={`text-sm font-semibold truncate ${
                  enabled ? 'text-[#F1F5F9]' : 'text-[#94A3B8]'
                }`}
              >
                {label}
              </p>
              {subtitle && (
                <p className="text-[11px] text-[#00D4FF] truncate font-mono-tabular">{subtitle}</p>
              )}
            </div>
          </div>

          <button
            type="button"
            role="switch"
            aria-checked={enabled}
            aria-label={`Toggle ${label}`}
            onClick={() => onEnabledChanged(!enabled)}
            className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors cursor-pointer ${
              enabled
                ? 'bg-[#22C55E] border-[#22C55E]'
                : 'bg-[#131822] border-[#2C374D]'
            }`}
          >
            <span
              className={`inline-block h-5 w-5 rounded-full transition-transform ${
                enabled ? 'translate-x-6 bg-[#131822]' : 'translate-x-1 bg-[#94A3B8]'
              }`}
            />
          </button>
        </div>

        {enabled && (
          <>
            <div className="border-t border-[#2C374D]" />
            <div className="px-3.5 py-2.5 flex flex-col gap-2.5">
              {/* Reset & Save Preset Toolbar */}
              <div className="flex items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={onReset}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#131822] border border-[#2C374D] text-xs text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer transition-colors"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Reset</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setPresetName('');
                    setShowSaveModal(true);
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#131822] border border-[#00D4FF]/50 text-xs text-[#00D4FF] hover:bg-[#0E303D]/60 cursor-pointer transition-colors"
                >
                  <BookmarkPlus className="w-3.5 h-3.5" />
                  <span>Save Preset</span>
                </button>
              </div>

              {/* Presets Strip */}
              {availablePresets.length > 0 && (
                <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
                  <span className="text-[11px] text-[#64748B] shrink-0">Presets:</span>
                  {availablePresets.map((preset) => (
                    <div
                      key={preset.id}
                      className="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-[#131822] border border-[#2C374D] shrink-0"
                    >
                      <button
                        type="button"
                        onClick={() => onLoadPreset(preset)}
                        className="text-[11px] text-[#94A3B8] hover:text-[#00D4FF] cursor-pointer"
                      >
                        {preset.name}
                      </button>
                      {!preset.isBuiltIn && (
                        <button
                          type="button"
                          onClick={() => onDeleteUserPreset(preset.id)}
                          aria-label={`Delete preset ${preset.name}`}
                          className="text-[#64748B] hover:text-[#FF4D4D] cursor-pointer"
                        >
                          <X className="w-3 h-3" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {children}
            </div>
          </>
        )}
      </div>

      {showSaveModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          role="dialog"
          aria-modal="true"
          aria-label={`Save ${label} Preset`}
        >
          <div className="w-full max-w-sm rounded-2xl bg-[#1A2130] border border-[#2C374D] p-5 flex flex-col gap-4 shadow-2xl">
            <h3 className="font-display text-lg font-semibold text-[#F1F5F9]">
              Save {label} Preset
            </h3>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs text-[#94A3B8]" htmlFor={`preset-name-${type}`}>
                Preset name
              </label>
              <input
                id={`preset-name-${type}`}
                type="text"
                autoFocus
                value={presetName}
                onChange={(e) => setPresetName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && presetName.trim()) {
                    onSavePreset(presetName.trim());
                    setShowSaveModal(false);
                  }
                }}
                placeholder="e.g. Warm Podcast Voice"
                className="w-full rounded-lg bg-[#131822] border border-[#2C374D] px-3 py-2 text-sm text-[#F1F5F9] focus:outline-none focus:border-[#00D4FF]"
              />
            </div>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowSaveModal(false)}
                className="px-3.5 py-2 rounded-lg text-sm text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!presetName.trim()}
                onClick={() => {
                  onSavePreset(presetName.trim());
                  setShowSaveModal(false);
                }}
                className="px-4 py-2 rounded-lg bg-[#00D4FF] text-[#131822] text-sm font-semibold disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
});

StudioEffectCard.displayName = 'StudioEffectCard';

const AdvancedSettingsSection: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="w-full">
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="w-full rounded-lg bg-[#131822] border border-[#2C374D] px-2.5 py-2 flex items-center justify-between text-xs font-medium text-[#94A3B8] hover:text-[#F1F5F9] cursor-pointer transition-colors"
      >
        <span>Advanced Settings</span>
        {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
      </button>

      {expanded && <div className="mt-2 flex flex-col gap-2">{children}</div>}
    </div>
  );
};

interface StudioEffectSliderProps {
  label: string;
  valueText: string;
  value: number;
  min: number;
  max: number;
  step: number;
  minLabel: string;
  maxLabel: string;
  enabled?: boolean;
  onChange: (value: number) => void;
}

const StudioEffectSlider: React.FC<StudioEffectSliderProps> = ({
  label,
  valueText,
  value,
  min,
  max,
  step,
  minLabel,
  maxLabel,
  enabled = true,
  onChange,
}) => {
  const clamped = Math.max(min, Math.min(max, value));
  return (
    <div className="w-full flex flex-col gap-0.5">
      <div className="flex items-center gap-2">
        <span
          className={`w-[106px] shrink-0 text-xs truncate ${
            enabled ? 'text-[#94A3B8]' : 'text-[#64748B]'
          }`}
        >
          {label}
        </span>
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={clamped}
          disabled={!enabled}
          onChange={(e) => onChange(Number(e.target.value))}
          className="studio-slider flex-1"
          aria-label={label}
        />
        <span
          className={`w-[78px] shrink-0 rounded-md bg-[#131822] border border-[#2C374D] px-1.5 py-1 text-center font-mono-tabular text-[11px] truncate ${
            enabled ? 'text-[#F1F5F9]' : 'text-[#64748B]'
          }`}
        >
          {valueText}
        </span>
      </div>
      <div className="pl-[114px] pr-[86px] flex items-center justify-between text-[10px] text-[#64748B]">
        <span>{minLabel}</span>
        <span>{maxLabel}</span>
      </div>
    </div>
  );
};

const StudioToggleRow: React.FC<{
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}> = ({ label, checked, onChange }) => (
  <div className="flex items-center justify-between py-0.5">
    <span className="text-xs text-[#94A3B8]">{label}</span>
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border transition-colors cursor-pointer ${
        checked ? 'bg-[#00D4FF] border-[#00D4FF]' : 'bg-[#131822] border-[#2C374D]'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 rounded-full transition-transform ${
          checked ? 'translate-x-6 bg-[#131822]' : 'translate-x-1 bg-[#94A3B8]'
        }`}
      />
    </button>
  </div>
);
