package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.MonitoringConfig
import com.example.dsp.BuiltInEffectPresets
import com.example.dsp.CompressionConfig
import com.example.dsp.DeEsserConfig
import com.example.dsp.DelaySubdivision
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.FilterSlope
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.LimiterConfig
import com.example.dsp.LowPassConfig
import com.example.dsp.MusicalKey
import com.example.dsp.MusicalScale
import com.example.dsp.NoiseGateConfig
import com.example.dsp.PitchCorrectionConfig
import com.example.dsp.PitchFormantConfig
import com.example.dsp.ReverbConfig
import com.example.dsp.ReverbPreset
import com.example.dsp.SaturationConfig
import com.example.dsp.StudioEffectPreset
import com.example.dsp.VoiceChangerConfig
import com.example.dsp.VoicePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voxForgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxforge_settings"
)

data class PersistedAudioSettings(
    val voiceChanger: VoiceChangerConfig = VoiceChangerConfig(),
    val monitoringDelaySlider: Float = MonitoringConfig.DEFAULT_SLIDER,
    val monitoringVolumePercent: Int = MonitoringConfig.DEFAULT_VOLUME_PERCENT,
    val echoCancellationEnabled: Boolean = true,
    val effectsRack: EffectsRackConfig = EffectsRackConfig(),
    val userEffectPresets: List<StudioEffectPreset> = emptyList()
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        // Voice Changer v2 (default OFF)
        val VC_ENABLED = booleanPreferencesKey("vc_enabled_v2")
        val VC_PRESET = stringPreferencesKey("vc_preset_v2")
        val VC_PITCH_LEVEL = intPreferencesKey("vc_pitch_level")
        val VC_TONE_LEVEL = intPreferencesKey("vc_tone_level")
        val VC_CLARITY = intPreferencesKey("vc_clarity")

        // Monitoring
        val MONITORING_DELAY_SLIDER = floatPreferencesKey("monitoring_delay_slider")
        val MONITORING_VOLUME_PCT = intPreferencesKey("monitoring_volume_pct")
        val MONITORING_AEC_ENABLED = booleanPreferencesKey("monitoring_aec_enabled")

        // 1. Pitch Correction
        val PC_ENABLED = booleanPreferencesKey("fx_pc_enabled")
        val PC_DETECTION = booleanPreferencesKey("fx_pc_detection")
        val PC_KEY = stringPreferencesKey("fx_pc_key")
        val PC_SCALE = stringPreferencesKey("fx_pc_scale")
        val PC_AUTO_KEY = booleanPreferencesKey("fx_pc_auto_key")
        val PC_STRENGTH = intPreferencesKey("fx_pc_strength")
        val PC_RETUNE_MS = intPreferencesKey("fx_pc_retune_ms")
        val PC_HUMANIZE = intPreferencesKey("fx_pc_humanize")
        val PC_TRANSITION_MS = intPreferencesKey("fx_pc_transition_ms")
        val PC_REF_TUNING = floatPreferencesKey("fx_pc_ref_tuning")

        // 2. Pitch & Formant
        val PF_ENABLED = booleanPreferencesKey("fx_pf_enabled")
        val PF_PITCH_ST = floatPreferencesKey("fx_pf_pitch_st")
        val PF_FORMANT_ST = floatPreferencesKey("fx_pf_formant_st")
        val PF_FINE_CENTS = intPreferencesKey("fx_pf_fine_cents")
        val PF_FORMANT_PRESERVE = booleanPreferencesKey("fx_pf_formant_preserve")
        val PF_MIX = intPreferencesKey("fx_pf_mix")
        val PF_OUT_GAIN = floatPreferencesKey("fx_pf_out_gain")

        // 3. Equalizer
        val EQ_ENABLED = booleanPreferencesKey("fx_eq_enabled")
        val EQ_MIX = intPreferencesKey("fx_eq_mix")
        val EQ_LOW_FREQ = floatPreferencesKey("fx_eq_low_freq")
        val EQ_LOW_GAIN = floatPreferencesKey("fx_eq_low_gain")
        val EQ_LOW_Q = floatPreferencesKey("fx_eq_low_q")
        val EQ_MID1_FREQ = floatPreferencesKey("fx_eq_mid1_freq")
        val EQ_MID1_GAIN = floatPreferencesKey("fx_eq_mid1_gain")
        val EQ_MID1_Q = floatPreferencesKey("fx_eq_mid1_q")
        val EQ_MID2_FREQ = floatPreferencesKey("fx_eq_mid2_freq")
        val EQ_MID2_GAIN = floatPreferencesKey("fx_eq_mid2_gain")
        val EQ_MID2_Q = floatPreferencesKey("fx_eq_mid2_q")
        val EQ_MID3_FREQ = floatPreferencesKey("fx_eq_mid3_freq")
        val EQ_MID3_GAIN = floatPreferencesKey("fx_eq_mid3_gain")
        val EQ_MID3_Q = floatPreferencesKey("fx_eq_mid3_q")
        val EQ_HIGH_FREQ = floatPreferencesKey("fx_eq_high_freq")
        val EQ_HIGH_GAIN = floatPreferencesKey("fx_eq_high_gain")
        val EQ_HIGH_Q = floatPreferencesKey("fx_eq_high_q")

        // 4. Compression
        val COMP_ENABLED = booleanPreferencesKey("fx_comp_enabled")
        val COMP_THRESHOLD = floatPreferencesKey("fx_comp_threshold")
        val COMP_RATIO = floatPreferencesKey("fx_comp_ratio")
        val COMP_ATTACK = floatPreferencesKey("fx_comp_attack")
        val COMP_RELEASE = floatPreferencesKey("fx_comp_release")
        val COMP_KNEE = floatPreferencesKey("fx_comp_knee")
        val COMP_MAKEUP = floatPreferencesKey("fx_comp_makeup")
        val COMP_OUT_GAIN = floatPreferencesKey("fx_comp_out_gain")

        // 5. Saturation
        val SAT_ENABLED = booleanPreferencesKey("fx_sat_enabled")
        val SAT_DRIVE = intPreferencesKey("fx_sat_drive")
        val SAT_WARMTH = intPreferencesKey("fx_sat_warmth")
        val SAT_TONE = intPreferencesKey("fx_sat_tone")
        val SAT_HARMONICS = intPreferencesKey("fx_sat_harmonics")
        val SAT_MIX = intPreferencesKey("fx_sat_mix")
        val SAT_OUT_LEVEL = floatPreferencesKey("fx_sat_out_level")

        // 6. Reverb
        val REV_ENABLED = booleanPreferencesKey("fx_rev_enabled")
        val REV_PRESET = stringPreferencesKey("fx_rev_preset")
        val REV_MIX = intPreferencesKey("fx_rev_mix")
        val REV_ROOM_SIZE = intPreferencesKey("fx_rev_room_size")
        val REV_DECAY_SEC = floatPreferencesKey("fx_rev_decay_sec")
        val REV_DAMPING = intPreferencesKey("fx_rev_damping")
        val REV_WET = intPreferencesKey("fx_rev_wet")
        val REV_DRY = intPreferencesKey("fx_rev_dry")
        val REV_WIDTH = intPreferencesKey("fx_rev_width")
        val REV_PREDELAY = floatPreferencesKey("fx_rev_predelay")

        // 7. Delay / Echo
        val ECHO_ENABLED = booleanPreferencesKey("fx_echo_enabled")
        val ECHO_MIX = intPreferencesKey("fx_echo_mix")
        val ECHO_TIME = intPreferencesKey("fx_echo_time")
        val ECHO_FEEDBACK = intPreferencesKey("fx_echo_feedback")
        val ECHO_LEVEL = intPreferencesKey("fx_echo_level")
        val ECHO_WET = intPreferencesKey("fx_echo_wet")
        val ECHO_DRY = intPreferencesKey("fx_echo_dry")
        val ECHO_PINGPONG = booleanPreferencesKey("fx_echo_pingpong")
        val ECHO_WIDTH = intPreferencesKey("fx_echo_width")
        val ECHO_TEMPO_SYNC = booleanPreferencesKey("fx_echo_tempo_sync")
        val ECHO_BPM = intPreferencesKey("fx_echo_bpm")
        val ECHO_SUBDIV = stringPreferencesKey("fx_echo_subdiv")
        val ECHO_LOWCUT = floatPreferencesKey("fx_echo_lowcut")
        val ECHO_HIGHCUT = floatPreferencesKey("fx_echo_highcut")

        // 8. Noise Gate
        val GATE_ENABLED = booleanPreferencesKey("fx_gate_enabled")
        val GATE_MIX = intPreferencesKey("fx_gate_mix")
        val GATE_THRESHOLD_DB = floatPreferencesKey("fx_gate_threshold_db")
        val GATE_ATTACK_MS = floatPreferencesKey("fx_gate_attack_ms")
        val GATE_HOLD_MS = floatPreferencesKey("fx_gate_hold_ms")
        val GATE_RELEASE_MS = floatPreferencesKey("fx_gate_release_ms")
        val GATE_RANGE_DB = floatPreferencesKey("fx_gate_range_db")

        // 9. De-Esser
        val DEESS_ENABLED = booleanPreferencesKey("fx_deess_enabled")
        val DEESS_FREQ = floatPreferencesKey("fx_deess_freq")
        val DEESS_THRESHOLD = floatPreferencesKey("fx_deess_threshold")
        val DEESS_REDUCTION = intPreferencesKey("fx_deess_reduction")
        val DEESS_BANDWIDTH = floatPreferencesKey("fx_deess_bandwidth")
        val DEESS_MIX = intPreferencesKey("fx_deess_mix")

        // 10. Limiter
        val LIM_ENABLED = booleanPreferencesKey("fx_lim_enabled")
        val LIM_THRESHOLD = floatPreferencesKey("fx_lim_threshold")
        val LIM_CEILING = floatPreferencesKey("fx_lim_ceiling")
        val LIM_RELEASE = floatPreferencesKey("fx_lim_release")
        val LIM_IN_GAIN = floatPreferencesKey("fx_lim_in_gain")
        val LIM_OUT_GAIN = floatPreferencesKey("fx_lim_out_gain")

        // Additional rack effects (Low-pass, High-pass, Hall)
        val LPF_ENABLED = booleanPreferencesKey("fx_lpf_enabled")
        val LPF_MIX = intPreferencesKey("fx_lpf_mix")
        val LPF_CUTOFF = floatPreferencesKey("fx_lpf_cutoff")
        val LPF_RESONANCE = floatPreferencesKey("fx_lpf_resonance")
        val LPF_SLOPE = intPreferencesKey("fx_lpf_slope")

        val HPF_ENABLED = booleanPreferencesKey("fx_hpf_enabled")
        val HPF_MIX = intPreferencesKey("fx_hpf_mix")
        val HPF_CUTOFF = floatPreferencesKey("fx_hpf_cutoff")
        val HPF_RESONANCE = floatPreferencesKey("fx_hpf_resonance")
        val HPF_SLOPE = intPreferencesKey("fx_hpf_slope")

        val HALL_ENABLED = booleanPreferencesKey("fx_hall_enabled")
        val HALL_ECHO = intPreferencesKey("fx_hall_echo")
        val HALL_REVERB = intPreferencesKey("fx_hall_reverb")

        // User-saved custom presets
        val USER_EFFECT_PRESETS = stringPreferencesKey("user_effect_presets_json")
    }

    val settingsFlow: Flow<PersistedAudioSettings> = context.voxForgeDataStore.data.map { prefs ->
        val presetName = prefs[Keys.VC_PRESET]
        val preset = VoicePreset.entries.firstOrNull { it.name == presetName } ?: VoicePreset.NORMAL
        val presetDefaults = preset.defaultConfig()

        val lpfSlope = if (prefs[Keys.LPF_SLOPE] == 24) FilterSlope.SLOPE_24 else FilterSlope.SLOPE_12
        val hpfSlope = if (prefs[Keys.HPF_SLOPE] == 24) FilterSlope.SLOPE_24 else FilterSlope.SLOPE_12

        val revPreset = ReverbPreset.entries.firstOrNull { it.name == prefs[Keys.REV_PRESET] } ?: ReverbPreset.STUDIO
        val echoSubdiv = DelaySubdivision.entries.firstOrNull { it.name == prefs[Keys.ECHO_SUBDIV] } ?: DelaySubdivision.EIGHTH
        val pcKey = MusicalKey.entries.firstOrNull { it.name == prefs[Keys.PC_KEY] } ?: MusicalKey.C
        val pcScale = MusicalScale.entries.firstOrNull { it.name == prefs[Keys.PC_SCALE] } ?: MusicalScale.MAJOR

        PersistedAudioSettings(
            voiceChanger = VoiceChangerConfig(
                enabled = prefs[Keys.VC_ENABLED] ?: false,
                preset = preset,
                pitchLevelPercent = prefs[Keys.VC_PITCH_LEVEL] ?: presetDefaults.pitchLevelPercent,
                toneLevelPercent = prefs[Keys.VC_TONE_LEVEL] ?: presetDefaults.toneLevelPercent,
                clarityPercent = prefs[Keys.VC_CLARITY] ?: presetDefaults.clarityPercent
            ),
            monitoringDelaySlider = (prefs[Keys.MONITORING_DELAY_SLIDER] ?: MonitoringConfig.DEFAULT_SLIDER)
                .coerceIn(MonitoringConfig.MIN_SLIDER, MonitoringConfig.MAX_SLIDER),
            monitoringVolumePercent = (prefs[Keys.MONITORING_VOLUME_PCT] ?: MonitoringConfig.DEFAULT_VOLUME_PERCENT)
                .coerceIn(MonitoringConfig.MIN_VOLUME_PERCENT, MonitoringConfig.MAX_VOLUME_PERCENT),
            echoCancellationEnabled = prefs[Keys.MONITORING_AEC_ENABLED] ?: true,
            userEffectPresets = BuiltInEffectPresets.decodeUserPresets(prefs[Keys.USER_EFFECT_PRESETS].orEmpty()),
            effectsRack = EffectsRackConfig(
                pitchCorrection = PitchCorrectionConfig(
                    enabled = prefs[Keys.PC_ENABLED] ?: false,
                    pitchDetectionEnabled = prefs[Keys.PC_DETECTION] ?: true,
                    key = pcKey,
                    scale = pcScale,
                    automaticKeyDetection = prefs[Keys.PC_AUTO_KEY] ?: false,
                    correctionStrengthPercent = prefs[Keys.PC_STRENGTH] ?: 65,
                    retuneSpeedMs = prefs[Keys.PC_RETUNE_MS] ?: 35,
                    humanizePercent = prefs[Keys.PC_HUMANIZE] ?: 35,
                    noteTransitionMs = prefs[Keys.PC_TRANSITION_MS] ?: 25,
                    referenceTuningHz = prefs[Keys.PC_REF_TUNING] ?: 440f
                ),
                pitchFormant = PitchFormantConfig(
                    enabled = prefs[Keys.PF_ENABLED] ?: false,
                    pitchShiftSt = prefs[Keys.PF_PITCH_ST] ?: 0f,
                    formantShiftSt = prefs[Keys.PF_FORMANT_ST] ?: 0f,
                    fineTuneCents = prefs[Keys.PF_FINE_CENTS] ?: 0,
                    formantPreservation = prefs[Keys.PF_FORMANT_PRESERVE] ?: true,
                    dryWetPercent = prefs[Keys.PF_MIX] ?: 100,
                    outputGainDb = prefs[Keys.PF_OUT_GAIN] ?: 0f
                ),
                equalizer = EqualizerConfig(
                    enabled = prefs[Keys.EQ_ENABLED] ?: false,
                    mixPercent = prefs[Keys.EQ_MIX] ?: 100,
                    lowFreqHz = prefs[Keys.EQ_LOW_FREQ] ?: 120f,
                    lowGainDb = prefs[Keys.EQ_LOW_GAIN] ?: 0f,
                    lowQ = prefs[Keys.EQ_LOW_Q] ?: 0.71f,
                    mid1FreqHz = prefs[Keys.EQ_MID1_FREQ] ?: 400f,
                    mid1GainDb = prefs[Keys.EQ_MID1_GAIN] ?: 0f,
                    mid1Q = prefs[Keys.EQ_MID1_Q] ?: 0.71f,
                    mid2FreqHz = prefs[Keys.EQ_MID2_FREQ] ?: 1500f,
                    mid2GainDb = prefs[Keys.EQ_MID2_GAIN] ?: 0f,
                    mid2Q = prefs[Keys.EQ_MID2_Q] ?: 1.00f,
                    mid3FreqHz = prefs[Keys.EQ_MID3_FREQ] ?: 5000f,
                    mid3GainDb = prefs[Keys.EQ_MID3_GAIN] ?: 0f,
                    mid3Q = prefs[Keys.EQ_MID3_Q] ?: 1.00f,
                    highFreqHz = prefs[Keys.EQ_HIGH_FREQ] ?: 10000f,
                    highGainDb = prefs[Keys.EQ_HIGH_GAIN] ?: 0f,
                    highQ = prefs[Keys.EQ_HIGH_Q] ?: 0.71f
                ),
                compression = CompressionConfig(
                    enabled = prefs[Keys.COMP_ENABLED] ?: false,
                    thresholdDb = prefs[Keys.COMP_THRESHOLD] ?: -18f,
                    ratio = prefs[Keys.COMP_RATIO] ?: 3.5f,
                    attackMs = prefs[Keys.COMP_ATTACK] ?: 10f,
                    releaseMs = prefs[Keys.COMP_RELEASE] ?: 120f,
                    kneeDb = prefs[Keys.COMP_KNEE] ?: 6f,
                    makeupGainDb = prefs[Keys.COMP_MAKEUP] ?: 3f,
                    outputGainDb = prefs[Keys.COMP_OUT_GAIN] ?: 0f
                ),
                saturation = SaturationConfig(
                    enabled = prefs[Keys.SAT_ENABLED] ?: false,
                    drivePercent = prefs[Keys.SAT_DRIVE] ?: 25,
                    warmthPercent = prefs[Keys.SAT_WARMTH] ?: 40,
                    tonePercent = prefs[Keys.SAT_TONE] ?: 0,
                    harmonicAmountPercent = prefs[Keys.SAT_HARMONICS] ?: 35,
                    mixPercent = prefs[Keys.SAT_MIX] ?: 70,
                    outputLevelDb = prefs[Keys.SAT_OUT_LEVEL] ?: 0f
                ),
                reverb = ReverbConfig(
                    enabled = prefs[Keys.REV_ENABLED] ?: false,
                    preset = revPreset,
                    mixPercent = prefs[Keys.REV_MIX] ?: 81,
                    roomSizePercent = prefs[Keys.REV_ROOM_SIZE] ?: 50,
                    decayTimeSec = prefs[Keys.REV_DECAY_SEC] ?: 1.6f,
                    dampingPercent = prefs[Keys.REV_DAMPING] ?: 50,
                    wetPercent = prefs[Keys.REV_WET] ?: 30,
                    dryPercent = prefs[Keys.REV_DRY] ?: 100,
                    widthPercent = prefs[Keys.REV_WIDTH] ?: 100,
                    preDelayMs = prefs[Keys.REV_PREDELAY] ?: 0f
                ),
                echo = EchoConfig(
                    enabled = prefs[Keys.ECHO_ENABLED] ?: false,
                    mixPercent = prefs[Keys.ECHO_MIX] ?: 60,
                    timeMs = prefs[Keys.ECHO_TIME] ?: 250,
                    feedbackPercent = prefs[Keys.ECHO_FEEDBACK] ?: 35,
                    echoLevelPercent = prefs[Keys.ECHO_LEVEL] ?: 30,
                    wetPercent = prefs[Keys.ECHO_WET] ?: 35,
                    dryPercent = prefs[Keys.ECHO_DRY] ?: 100,
                    pingPong = prefs[Keys.ECHO_PINGPONG] ?: false,
                    widthPercent = prefs[Keys.ECHO_WIDTH] ?: 60,
                    tempoSync = prefs[Keys.ECHO_TEMPO_SYNC] ?: false,
                    bpm = prefs[Keys.ECHO_BPM] ?: 120,
                    subdivision = echoSubdiv,
                    lowCutHz = prefs[Keys.ECHO_LOWCUT] ?: 80f,
                    highCutHz = prefs[Keys.ECHO_HIGHCUT] ?: 8000f
                ),
                noiseGate = NoiseGateConfig(
                    enabled = prefs[Keys.GATE_ENABLED] ?: false,
                    mixPercent = prefs[Keys.GATE_MIX] ?: NoiseGateConfig.DEFAULT_MIX_PCT,
                    thresholdDb = prefs[Keys.GATE_THRESHOLD_DB] ?: NoiseGateConfig.DEFAULT_THRESHOLD_DB,
                    attackMs = prefs[Keys.GATE_ATTACK_MS] ?: NoiseGateConfig.DEFAULT_ATTACK_MS,
                    holdMs = prefs[Keys.GATE_HOLD_MS] ?: NoiseGateConfig.DEFAULT_HOLD_MS,
                    releaseMs = prefs[Keys.GATE_RELEASE_MS] ?: NoiseGateConfig.DEFAULT_RELEASE_MS,
                    rangeDb = prefs[Keys.GATE_RANGE_DB] ?: NoiseGateConfig.DEFAULT_RANGE_DB
                ),
                deEsser = DeEsserConfig(
                    enabled = prefs[Keys.DEESS_ENABLED] ?: false,
                    frequencyHz = prefs[Keys.DEESS_FREQ] ?: 6200f,
                    thresholdDb = prefs[Keys.DEESS_THRESHOLD] ?: -24f,
                    reductionPercent = prefs[Keys.DEESS_REDUCTION] ?: 55,
                    bandwidthQ = prefs[Keys.DEESS_BANDWIDTH] ?: 2.0f,
                    mixPercent = prefs[Keys.DEESS_MIX] ?: 100
                ),
                limiter = LimiterConfig(
                    enabled = prefs[Keys.LIM_ENABLED] ?: false,
                    thresholdDb = prefs[Keys.LIM_THRESHOLD] ?: -3.0f,
                    ceilingDb = prefs[Keys.LIM_CEILING] ?: -0.3f,
                    releaseMs = prefs[Keys.LIM_RELEASE] ?: 80f,
                    inputGainDb = prefs[Keys.LIM_IN_GAIN] ?: 0f,
                    outputGainDb = prefs[Keys.LIM_OUT_GAIN] ?: 0f
                ),
                lowPass = LowPassConfig(
                    enabled = prefs[Keys.LPF_ENABLED] ?: false,
                    mixPercent = prefs[Keys.LPF_MIX] ?: 100,
                    cutoffHz = prefs[Keys.LPF_CUTOFF] ?: 5000f,
                    resonance = prefs[Keys.LPF_RESONANCE] ?: 0.71f,
                    slope = lpfSlope
                ),
                highPass = HighPassConfig(
                    enabled = prefs[Keys.HPF_ENABLED] ?: false,
                    mixPercent = prefs[Keys.HPF_MIX] ?: 100,
                    cutoffHz = prefs[Keys.HPF_CUTOFF] ?: 200f,
                    resonance = prefs[Keys.HPF_RESONANCE] ?: 0.71f,
                    slope = hpfSlope
                ),
                hall = HallConfig(
                    enabled = prefs[Keys.HALL_ENABLED] ?: false,
                    echoAmount = prefs[Keys.HALL_ECHO] ?: 55,
                    reverberationAmount = prefs[Keys.HALL_REVERB] ?: 71
                )
            )
        )
    }

    suspend fun saveVoiceChanger(config: VoiceChangerConfig) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.VC_ENABLED] = config.enabled
            prefs[Keys.VC_PRESET] = config.preset.name
            prefs[Keys.VC_PITCH_LEVEL] = config.pitchLevelPercent
            prefs[Keys.VC_TONE_LEVEL] = config.toneLevelPercent
            prefs[Keys.VC_CLARITY] = config.clarityPercent
        }
    }

    suspend fun saveMonitoringDelaySlider(sliderValue: Float) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.MONITORING_DELAY_SLIDER] = sliderValue.coerceIn(
                MonitoringConfig.MIN_SLIDER,
                MonitoringConfig.MAX_SLIDER
            )
        }
    }

    suspend fun saveMonitoringVolumePercent(volumePercent: Int) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.MONITORING_VOLUME_PCT] = volumePercent.coerceIn(
                MonitoringConfig.MIN_VOLUME_PERCENT,
                MonitoringConfig.MAX_VOLUME_PERCENT
            )
        }
    }

    suspend fun saveMonitoringAecEnabled(enabled: Boolean) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.MONITORING_AEC_ENABLED] = enabled
        }
    }

    suspend fun saveUserEffectPresets(presets: List<StudioEffectPreset>) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.USER_EFFECT_PRESETS] = BuiltInEffectPresets.encodeUserPresets(presets)
        }
    }

    suspend fun saveEffectsRack(config: EffectsRackConfig) {
        context.voxForgeDataStore.edit { prefs ->
            val pc = config.pitchCorrection
            prefs[Keys.PC_ENABLED] = pc.enabled
            prefs[Keys.PC_DETECTION] = pc.pitchDetectionEnabled
            prefs[Keys.PC_KEY] = pc.key.name
            prefs[Keys.PC_SCALE] = pc.scale.name
            prefs[Keys.PC_AUTO_KEY] = pc.automaticKeyDetection
            prefs[Keys.PC_STRENGTH] = pc.correctionStrengthPercent
            prefs[Keys.PC_RETUNE_MS] = pc.retuneSpeedMs
            prefs[Keys.PC_HUMANIZE] = pc.humanizePercent
            prefs[Keys.PC_TRANSITION_MS] = pc.noteTransitionMs
            prefs[Keys.PC_REF_TUNING] = pc.referenceTuningHz

            val pf = config.pitchFormant
            prefs[Keys.PF_ENABLED] = pf.enabled
            prefs[Keys.PF_PITCH_ST] = pf.pitchShiftSt
            prefs[Keys.PF_FORMANT_ST] = pf.formantShiftSt
            prefs[Keys.PF_FINE_CENTS] = pf.fineTuneCents
            prefs[Keys.PF_FORMANT_PRESERVE] = pf.formantPreservation
            prefs[Keys.PF_MIX] = pf.dryWetPercent
            prefs[Keys.PF_OUT_GAIN] = pf.outputGainDb

            val eq = config.equalizer
            prefs[Keys.EQ_ENABLED] = eq.enabled
            prefs[Keys.EQ_MIX] = eq.mixPercent
            prefs[Keys.EQ_LOW_FREQ] = eq.lowFreqHz
            prefs[Keys.EQ_LOW_GAIN] = eq.lowGainDb
            prefs[Keys.EQ_LOW_Q] = eq.lowQ
            prefs[Keys.EQ_MID1_FREQ] = eq.mid1FreqHz
            prefs[Keys.EQ_MID1_GAIN] = eq.mid1GainDb
            prefs[Keys.EQ_MID1_Q] = eq.mid1Q
            prefs[Keys.EQ_MID2_FREQ] = eq.mid2FreqHz
            prefs[Keys.EQ_MID2_GAIN] = eq.mid2GainDb
            prefs[Keys.EQ_MID2_Q] = eq.mid2Q
            prefs[Keys.EQ_MID3_FREQ] = eq.mid3FreqHz
            prefs[Keys.EQ_MID3_GAIN] = eq.mid3GainDb
            prefs[Keys.EQ_MID3_Q] = eq.mid3Q
            prefs[Keys.EQ_HIGH_FREQ] = eq.highFreqHz
            prefs[Keys.EQ_HIGH_GAIN] = eq.highGainDb
            prefs[Keys.EQ_HIGH_Q] = eq.highQ

            val comp = config.compression
            prefs[Keys.COMP_ENABLED] = comp.enabled
            prefs[Keys.COMP_THRESHOLD] = comp.thresholdDb
            prefs[Keys.COMP_RATIO] = comp.ratio
            prefs[Keys.COMP_ATTACK] = comp.attackMs
            prefs[Keys.COMP_RELEASE] = comp.releaseMs
            prefs[Keys.COMP_KNEE] = comp.kneeDb
            prefs[Keys.COMP_MAKEUP] = comp.makeupGainDb
            prefs[Keys.COMP_OUT_GAIN] = comp.outputGainDb

            val sat = config.saturation
            prefs[Keys.SAT_ENABLED] = sat.enabled
            prefs[Keys.SAT_DRIVE] = sat.drivePercent
            prefs[Keys.SAT_WARMTH] = sat.warmthPercent
            prefs[Keys.SAT_TONE] = sat.tonePercent
            prefs[Keys.SAT_HARMONICS] = sat.harmonicAmountPercent
            prefs[Keys.SAT_MIX] = sat.mixPercent
            prefs[Keys.SAT_OUT_LEVEL] = sat.outputLevelDb

            val rev = config.reverb
            prefs[Keys.REV_ENABLED] = rev.enabled
            prefs[Keys.REV_PRESET] = rev.preset.name
            prefs[Keys.REV_MIX] = rev.mixPercent
            prefs[Keys.REV_ROOM_SIZE] = rev.roomSizePercent
            prefs[Keys.REV_DECAY_SEC] = rev.decayTimeSec
            prefs[Keys.REV_DAMPING] = rev.dampingPercent
            prefs[Keys.REV_WET] = rev.wetPercent
            prefs[Keys.REV_DRY] = rev.dryPercent
            prefs[Keys.REV_WIDTH] = rev.widthPercent
            prefs[Keys.REV_PREDELAY] = rev.preDelayMs

            val echo = config.echo
            prefs[Keys.ECHO_ENABLED] = echo.enabled
            prefs[Keys.ECHO_MIX] = echo.mixPercent
            prefs[Keys.ECHO_TIME] = echo.timeMs
            prefs[Keys.ECHO_FEEDBACK] = echo.feedbackPercent
            prefs[Keys.ECHO_LEVEL] = echo.echoLevelPercent
            prefs[Keys.ECHO_WET] = echo.wetPercent
            prefs[Keys.ECHO_DRY] = echo.dryPercent
            prefs[Keys.ECHO_PINGPONG] = echo.pingPong
            prefs[Keys.ECHO_WIDTH] = echo.widthPercent
            prefs[Keys.ECHO_TEMPO_SYNC] = echo.tempoSync
            prefs[Keys.ECHO_BPM] = echo.bpm
            prefs[Keys.ECHO_SUBDIV] = echo.subdivision.name
            prefs[Keys.ECHO_LOWCUT] = echo.lowCutHz
            prefs[Keys.ECHO_HIGHCUT] = echo.highCutHz

            val g = config.noiseGate
            prefs[Keys.GATE_ENABLED] = g.enabled
            prefs[Keys.GATE_MIX] = g.mixPercent
            prefs[Keys.GATE_THRESHOLD_DB] = g.thresholdDb
            prefs[Keys.GATE_ATTACK_MS] = g.attackMs
            prefs[Keys.GATE_HOLD_MS] = g.holdMs
            prefs[Keys.GATE_RELEASE_MS] = g.releaseMs
            prefs[Keys.GATE_RANGE_DB] = g.rangeDb

            val deess = config.deEsser
            prefs[Keys.DEESS_ENABLED] = deess.enabled
            prefs[Keys.DEESS_FREQ] = deess.frequencyHz
            prefs[Keys.DEESS_THRESHOLD] = deess.thresholdDb
            prefs[Keys.DEESS_REDUCTION] = deess.reductionPercent
            prefs[Keys.DEESS_BANDWIDTH] = deess.bandwidthQ
            prefs[Keys.DEESS_MIX] = deess.mixPercent

            val lim = config.limiter
            prefs[Keys.LIM_ENABLED] = lim.enabled
            prefs[Keys.LIM_THRESHOLD] = lim.thresholdDb
            prefs[Keys.LIM_CEILING] = lim.ceilingDb
            prefs[Keys.LIM_RELEASE] = lim.releaseMs
            prefs[Keys.LIM_IN_GAIN] = lim.inputGainDb
            prefs[Keys.LIM_OUT_GAIN] = lim.outputGainDb

            val lpf = config.lowPass
            prefs[Keys.LPF_ENABLED] = lpf.enabled
            prefs[Keys.LPF_MIX] = lpf.mixPercent
            prefs[Keys.LPF_CUTOFF] = lpf.cutoffHz
            prefs[Keys.LPF_RESONANCE] = lpf.resonance
            prefs[Keys.LPF_SLOPE] = lpf.slope.dbPerOct

            val hpf = config.highPass
            prefs[Keys.HPF_ENABLED] = hpf.enabled
            prefs[Keys.HPF_MIX] = hpf.mixPercent
            prefs[Keys.HPF_CUTOFF] = hpf.cutoffHz
            prefs[Keys.HPF_RESONANCE] = hpf.resonance
            prefs[Keys.HPF_SLOPE] = hpf.slope.dbPerOct

            val hall = config.hall
            prefs[Keys.HALL_ENABLED] = hall.enabled
            prefs[Keys.HALL_ECHO] = hall.echoAmount
            prefs[Keys.HALL_REVERB] = hall.reverberationAmount
        }
    }
}
