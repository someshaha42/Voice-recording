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
import com.example.dsp.NoiseGateConfig
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
    val noiseGate: NoiseGateConfig = NoiseGateConfig()
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VOICE_PRESET = stringPreferencesKey("voice_preset")
        val PITCH_ST = floatPreferencesKey("pitch_st")
        val FORMANT_ST = floatPreferencesKey("formant_st")
        val FINE_TUNE_CENTS = intPreferencesKey("fine_tune_cents")
        val TIMBRE_DB = floatPreferencesKey("timbre_db")
        val DRY_WET_PCT = intPreferencesKey("dry_wet_pct")
        val VOICE_BYPASS = booleanPreferencesKey("voice_bypass")

        val MONITORING_DELAY_SLIDER = floatPreferencesKey("monitoring_delay_slider")

        val GATE_ENABLED = booleanPreferencesKey("gate_enabled")
        val GATE_THRESHOLD_DB = floatPreferencesKey("gate_threshold_db")
        val GATE_ATTACK_MS = floatPreferencesKey("gate_attack_ms")
        val GATE_RELEASE_MS = floatPreferencesKey("gate_release_ms")
    }

    val settingsFlow: Flow<PersistedAudioSettings> = context.voxForgeDataStore.data.map { prefs ->
        val presetName = prefs[Keys.VOICE_PRESET]
        val preset = VoicePreset.entries.firstOrNull { it.name == presetName } ?: VoicePreset.NORMAL
        val presetDefaults = preset.defaultConfig()

        PersistedAudioSettings(
            voiceChanger = VoiceChangerConfig(
                preset = preset,
                pitchSemitones = prefs[Keys.PITCH_ST] ?: presetDefaults.pitchSemitones,
                formantSemitones = prefs[Keys.FORMANT_ST] ?: presetDefaults.formantSemitones,
                fineTuneCents = prefs[Keys.FINE_TUNE_CENTS] ?: presetDefaults.fineTuneCents,
                timbreDb = prefs[Keys.TIMBRE_DB] ?: presetDefaults.timbreDb,
                dryWetPercent = prefs[Keys.DRY_WET_PCT] ?: presetDefaults.dryWetPercent,
                bypass = prefs[Keys.VOICE_BYPASS] ?: false
            ),
            monitoringDelaySlider = (prefs[Keys.MONITORING_DELAY_SLIDER] ?: MonitoringConfig.DEFAULT_SLIDER)
                .coerceIn(MonitoringConfig.MIN_SLIDER, MonitoringConfig.MAX_SLIDER),
            noiseGate = NoiseGateConfig(
                enabled = prefs[Keys.GATE_ENABLED] ?: true,
                thresholdDb = prefs[Keys.GATE_THRESHOLD_DB] ?: NoiseGateConfig.DEFAULT_THRESHOLD_DB,
                attackMs = prefs[Keys.GATE_ATTACK_MS] ?: NoiseGateConfig.DEFAULT_ATTACK_MS,
                releaseMs = prefs[Keys.GATE_RELEASE_MS] ?: NoiseGateConfig.DEFAULT_RELEASE_MS
            )
        )
    }

    suspend fun saveVoiceChanger(config: VoiceChangerConfig) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.VOICE_PRESET] = config.preset.name
            prefs[Keys.PITCH_ST] = config.pitchSemitones
            prefs[Keys.FORMANT_ST] = config.formantSemitones
            prefs[Keys.FINE_TUNE_CENTS] = config.fineTuneCents
            prefs[Keys.TIMBRE_DB] = config.timbreDb
            prefs[Keys.DRY_WET_PCT] = config.dryWetPercent
            prefs[Keys.VOICE_BYPASS] = config.bypass
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

    suspend fun saveNoiseGate(config: NoiseGateConfig) {
        context.voxForgeDataStore.edit { prefs ->
            prefs[Keys.GATE_ENABLED] = config.enabled
            prefs[Keys.GATE_THRESHOLD_DB] = config.thresholdDb
            prefs[Keys.GATE_ATTACK_MS] = config.attackMs
            prefs[Keys.GATE_RELEASE_MS] = config.releaseMs
        }
    }
}
