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
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.FilterSlope
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.LowPassConfig
import com.example.dsp.NoiseGateConfig
import com.example.dsp.ReverbConfig
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
    val effectsRack: EffectsRackConfig = EffectsRackConfig()
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

        // Effects: Noise Gate
        val GATE_ENABLED = booleanPreferencesKey("fx_gate_enabled")
        val GATE_MIX = intPreferencesKey("fx_gate_mix")
        val GATE_THRESHOLD_DB = floatPreferencesKey("fx_gate_threshold_db")
        val GATE_ATTACK_MS = floatPreferencesKey("fx_gate_attack_ms")
        val GATE_HOLD_MS = floatPreferencesKey("fx_gate_hold_ms")
        val GATE_RELEASE_MS = floatPreferencesKey("fx_gate_release_ms")
        val GATE_RANGE_DB = floatPreferencesKey("fx_gate_range_db")

        // Effects: Low-pass
        val LPF_ENABLED = booleanPreferencesKey("fx_lpf_enabled")
        val LPF_MIX = intPreferencesKey("fx_lpf_mix")
        val LPF_CUTOFF = floatPreferencesKey("fx_lpf_cutoff")
        val LPF_RESONANCE = floatPreferencesKey("fx_lpf_resonance")
        val LPF_SLOPE = intPreferencesKey("fx_lpf_slope")

        // Effects: High-pass
        val HPF_ENABLED = booleanPreferencesKey("fx_hpf_enabled")
        val HPF_MIX = intPreferencesKey("fx_hpf_mix")
        val HPF_CUTOFF = floatPreferencesKey("fx_hpf_cutoff")
        val HPF_RESONANCE = floatPreferencesKey("fx_hpf_resonance")
        val HPF_SLOPE = intPreferencesKey("fx_hpf_slope")

        // Effects: Equalizer
        val EQ_ENABLED = booleanPreferencesKey("fx_eq_enabled")
        val EQ_MIX = intPreferencesKey("fx_eq_mix")
        val EQ_LOW_FREQ = floatPreferencesKey("fx_eq_low_freq")
        val EQ_LOW_GAIN = floatPreferencesKey("fx_eq_low_gain")
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

        // Effects: Reverb
        val REV_ENABLED = booleanPreferencesKey("fx_rev_enabled")
        val REV_MIX = intPreferencesKey("fx_rev_mix")
        val REV_ROOM_SIZE = intPreferencesKey("fx_rev_room_size")
        val REV_DAMPING = intPreferencesKey("fx_rev_damping")
        val REV_WET = intPreferencesKey("fx_rev_wet")
        val REV_DRY = intPreferencesKey("fx_rev_dry")
        val REV_WIDTH = intPreferencesKey("fx_rev_width")
        val REV_PREDELAY = floatPreferencesKey("fx_rev_predelay")

        // Effects: Echo
        val ECHO_ENABLED = booleanPreferencesKey("fx_echo_enabled")
        val ECHO_MIX = intPreferencesKey("fx_echo_mix")
        val ECHO_TIME = intPreferencesKey("fx_echo_time")
        val ECHO_FEEDBACK = intPreferencesKey("fx_echo_feedback")
        val ECHO_LEVEL = intPreferencesKey("fx_echo_level")
        val ECHO_PINGPONG = booleanPreferencesKey("fx_echo_pingpong")
        val ECHO_LOWCUT = floatPreferencesKey("fx_echo_lowcut")

        // Effects: Hall
        val HALL_ENABLED = booleanPreferencesKey("fx_hall_enabled")
        val HALL_ECHO = intPreferencesKey("fx_hall_echo")
        val HALL_REVERB = intPreferencesKey("fx_hall_reverb")
    }

    val settingsFlow: Flow<PersistedAudioSettings> = context.voxForgeDataStore.data.map { prefs ->
        val presetName = prefs[Keys.VC_PRESET]
        val preset = VoicePreset.entries.firstOrNull { it.name == presetName } ?: VoicePreset.NORMAL
        val presetDefaults = preset.defaultConfig()

        val lpfSlope = if (prefs[Keys.LPF_SLOPE] == 24) FilterSlope.SLOPE_24 else FilterSlope.SLOPE_12
        val hpfSlope = if (prefs[Keys.HPF_SLOPE] == 24) FilterSlope.SLOPE_24 else FilterSlope.SLOPE_12

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
            effectsRack = EffectsRackConfig(
                noiseGate = NoiseGateConfig(
                    enabled = prefs[Keys.GATE_ENABLED] ?: false,
                    mixPercent = prefs[Keys.GATE_MIX] ?: NoiseGateConfig.DEFAULT_MIX_PCT,
                    thresholdDb = prefs[Keys.GATE_THRESHOLD_DB] ?: NoiseGateConfig.DEFAULT_THRESHOLD_DB,
                    attackMs = prefs[Keys.GATE_ATTACK_MS] ?: NoiseGateConfig.DEFAULT_ATTACK_MS,
                    holdMs = prefs[Keys.GATE_HOLD_MS] ?: NoiseGateConfig.DEFAULT_HOLD_MS,
                    releaseMs = prefs[Keys.GATE_RELEASE_MS] ?: NoiseGateConfig.DEFAULT_RELEASE_MS,
                    rangeDb = prefs[Keys.GATE_RANGE_DB] ?: NoiseGateConfig.DEFAULT_RANGE_DB
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
                equalizer = EqualizerConfig(
                    enabled = prefs[Keys.EQ_ENABLED] ?: false,
                    mixPercent = prefs[Keys.EQ_MIX] ?: 100,
                    lowFreqHz = prefs[Keys.EQ_LOW_FREQ] ?: 120f,
                    lowGainDb = prefs[Keys.EQ_LOW_GAIN] ?: 0f,
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
                    highGainDb = prefs[Keys.EQ_HIGH_GAIN] ?: 0f
                ),
                reverb = ReverbConfig(
                    enabled = prefs[Keys.REV_ENABLED] ?: false,
                    mixPercent = prefs[Keys.REV_MIX] ?: 81,
                    roomSizePercent = prefs[Keys.REV_ROOM_SIZE] ?: 50,
                    dampingPercent = prefs[Keys.REV_DAMPING] ?: 50,
                    wetPercent = prefs[Keys.REV_WET] ?: 30,
                    dryPercent = prefs[Keys.REV_DRY] ?: 100,
                    widthPercent = prefs[Keys.REV_WIDTH] ?: 100,
                    preDelayMs = prefs[Keys.REV_PREDELAY] ?: 0f
                ),
                echo = EchoConfig(
                    enabled = prefs[Keys.ECHO_ENABLED] ?: false,
                    mixPercent = prefs[Keys.ECHO_MIX] ?: 40,
                    timeMs = prefs[Keys.ECHO_TIME] ?: 250,
                    feedbackPercent = prefs[Keys.ECHO_FEEDBACK] ?: 35,
                    echoLevelPercent = prefs[Keys.ECHO_LEVEL] ?: 30,
                    pingPong = prefs[Keys.ECHO_PINGPONG] ?: false,
                    lowCutHz = prefs[Keys.ECHO_LOWCUT] ?: 80f
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

    suspend fun saveEffectsRack(config: EffectsRackConfig) {
        context.voxForgeDataStore.edit { prefs ->
            val g = config.noiseGate
            prefs[Keys.GATE_ENABLED] = g.enabled
            prefs[Keys.GATE_MIX] = g.mixPercent
            prefs[Keys.GATE_THRESHOLD_DB] = g.thresholdDb
            prefs[Keys.GATE_ATTACK_MS] = g.attackMs
            prefs[Keys.GATE_HOLD_MS] = g.holdMs
            prefs[Keys.GATE_RELEASE_MS] = g.releaseMs
            prefs[Keys.GATE_RANGE_DB] = g.rangeDb

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

            val eq = config.equalizer
            prefs[Keys.EQ_ENABLED] = eq.enabled
            prefs[Keys.EQ_MIX] = eq.mixPercent
            prefs[Keys.EQ_LOW_FREQ] = eq.lowFreqHz
            prefs[Keys.EQ_LOW_GAIN] = eq.lowGainDb
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

            val rev = config.reverb
            prefs[Keys.REV_ENABLED] = rev.enabled
            prefs[Keys.REV_MIX] = rev.mixPercent
            prefs[Keys.REV_ROOM_SIZE] = rev.roomSizePercent
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
            prefs[Keys.ECHO_PINGPONG] = echo.pingPong
            prefs[Keys.ECHO_LOWCUT] = echo.lowCutHz

            val hall = config.hall
            prefs[Keys.HALL_ENABLED] = hall.enabled
            prefs[Keys.HALL_ECHO] = hall.echoAmount
            prefs[Keys.HALL_REVERB] = hall.reverberationAmount
        }
    }
}
