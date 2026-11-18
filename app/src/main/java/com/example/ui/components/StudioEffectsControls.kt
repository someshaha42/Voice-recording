package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dsp.BuiltInEffectPresets
import com.example.dsp.CompressionConfig
import com.example.dsp.DeEsserConfig
import com.example.dsp.DelaySubdivision
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.EqualizerEffect
import com.example.dsp.LimiterConfig
import com.example.dsp.MusicalKey
import com.example.dsp.MusicalScale
import com.example.dsp.NoiseGateConfig
import com.example.dsp.PitchCorrectionConfig
import com.example.dsp.PitchFormantConfig
import com.example.dsp.ReverbConfig
import com.example.dsp.ReverbPreset
import com.example.dsp.SaturationConfig
import com.example.dsp.StudioEffectPreset
import com.example.dsp.StudioEffectType
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPanelElevated
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Adds the "AUDIO EFFECTS & STUDIO CONTROLS" vertical section with 10 independent
 * collapsible effect cards directly inside RecorderScreen's LazyColumn.
 */
fun LazyListScope.studioEffectsSection(
    effectsConfig: EffectsRackConfig,
    userPresets: List<StudioEffectPreset>,
    onPitchCorrectionChanged: (PitchCorrectionConfig) -> Unit,
    onPitchFormantChanged: (PitchFormantConfig) -> Unit,
    onEqualizerChanged: (EqualizerConfig) -> Unit,
    onCompressionChanged: (CompressionConfig) -> Unit,
    onSaturationChanged: (SaturationConfig) -> Unit,
    onReverbChanged: (ReverbConfig) -> Unit,
    onDelayChanged: (EchoConfig) -> Unit,
    onNoiseGateChanged: (NoiseGateConfig) -> Unit,
    onDeEsserChanged: (DeEsserConfig) -> Unit,
    onLimiterChanged: (LimiterConfig) -> Unit,
    onResetEffect: (StudioEffectType) -> Unit,
    onSavePreset: (StudioEffectType, String) -> Unit,
    onLoadPreset: (StudioEffectPreset) -> Unit,
    onDeleteUserPreset: (String) -> Unit
) {
    item(key = "studio_effects_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO EFFECTS & STUDIO CONTROLS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CyanSignal,
                modifier = Modifier.testTag("studio_effects_header")
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (effectsConfig.activeStudioEffectCount > 0) EmeraldContainer else StudioRack,
                border = BorderStroke(
                    1.dp,
                    if (effectsConfig.activeStudioEffectCount > 0) EmeraldSafe.copy(alpha = 0.6f) else StudioBorder
                )
            ) {
                Text(
                    text = "${effectsConfig.activeStudioEffectCount}/10 ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (effectsConfig.activeStudioEffectCount > 0) EmeraldSafe else TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }

    // 1. Pitch Correction
    item(key = "studio_fx_pitch_correction") {
        val pc = effectsConfig.pitchCorrection
        StudioEffectCard(
            type = StudioEffectType.PITCH_CORRECTION,
            icon = Icons.Filled.MusicNote,
            enabled = pc.enabled,
            subtitle = if (pc.enabled) "${pc.key.label} ${pc.scale.label} • ${pc.correctionStrengthPercent}%" else null,
            userPresets = userPresets,
            onEnabledChanged = { onPitchCorrectionChanged(pc.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.PITCH_CORRECTION) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.PITCH_CORRECTION, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioToggleRow(
                label = "Automatic Key Detection",
                checked = pc.automaticKeyDetection,
                onCheckedChange = { onPitchCorrectionChanged(pc.copy(automaticKeyDetection = it)) }
            )
            if (!pc.automaticKeyDetection) {
                Text("Key Selection", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MusicalKey.entries.forEach { k ->
                        val selected = pc.key == k
                        FilterChip(
                            selected = selected,
                            onClick = { onPitchCorrectionChanged(pc.copy(key = k)) },
                            label = { Text(k.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanContainer,
                                selectedLabelColor = CyanSignal
                            )
                        )
                    }
                }
            }
            Text("Scale Selection", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MusicalScale.entries.forEach { sc ->
                    val selected = pc.scale == sc
                    FilterChip(
                        selected = selected,
                        onClick = { onPitchCorrectionChanged(pc.copy(scale = sc)) },
                        label = { Text(sc.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanContainer,
                            selectedLabelColor = CyanSignal
                        )
                    )
                }
            }
            StudioEffectSlider(
                label = "Correction Strength",
                valueText = "${pc.correctionStrengthPercent}%",
                value = pc.correctionStrengthPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onPitchCorrectionChanged(pc.copy(correctionStrengthPercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Retune Speed",
                valueText = "${pc.retuneSpeedMs} ms",
                value = pc.retuneSpeedMs.toFloat(),
                valueRange = 0f..200f,
                minLabel = "0 ms (Fast)",
                maxLabel = "200 ms",
                onValueChange = { onPitchCorrectionChanged(pc.copy(retuneSpeedMs = it.roundToInt())) }
            )
            AdvancedSettingsSection {
                StudioToggleRow(
                    label = "Pitch Detection",
                    checked = pc.pitchDetectionEnabled,
                    onCheckedChange = { onPitchCorrectionChanged(pc.copy(pitchDetectionEnabled = it)) }
                )
                StudioEffectSlider(
                    label = "Humanize",
                    valueText = "${pc.humanizePercent}%",
                    value = pc.humanizePercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onPitchCorrectionChanged(pc.copy(humanizePercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Note Transition",
                    valueText = "${pc.noteTransitionMs} ms",
                    value = pc.noteTransitionMs.toFloat(),
                    valueRange = 0f..150f,
                    minLabel = "0 ms",
                    maxLabel = "150 ms",
                    onValueChange = { onPitchCorrectionChanged(pc.copy(noteTransitionMs = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Reference Tuning (A4)",
                    valueText = "${pc.referenceTuningHz.roundToInt()} Hz",
                    value = pc.referenceTuningHz,
                    valueRange = 415f..465f,
                    minLabel = "415 Hz",
                    maxLabel = "465 Hz",
                    onValueChange = { onPitchCorrectionChanged(pc.copy(referenceTuningHz = it.roundToInt().toFloat())) }
                )
            }
        }
    }

    // 2. Pitch & Formant
    item(key = "studio_fx_pitch_formant") {
        val pf = effectsConfig.pitchFormant
        StudioEffectCard(
            type = StudioEffectType.PITCH_FORMANT,
            icon = Icons.Filled.GraphicEq,
            enabled = pf.enabled,
            subtitle = if (pf.enabled) String.format(Locale.US, "%+.1f st • Formant %+.1f st", pf.pitchShiftSt, pf.formantShiftSt) else null,
            userPresets = userPresets,
            onEnabledChanged = { onPitchFormantChanged(pf.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.PITCH_FORMANT) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.PITCH_FORMANT, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Pitch Shift",
                valueText = String.format(Locale.US, "%+.1f st", pf.pitchShiftSt),
                value = pf.pitchShiftSt,
                valueRange = -12f..12f,
                minLabel = "-12 st",
                maxLabel = "+12 st",
                onValueChange = { onPitchFormantChanged(pf.copy(pitchShiftSt = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Formant Shift",
                valueText = String.format(Locale.US, "%+.1f st", pf.formantShiftSt),
                value = pf.formantShiftSt,
                valueRange = -12f..12f,
                minLabel = "-12 st",
                maxLabel = "+12 st",
                onValueChange = { onPitchFormantChanged(pf.copy(formantShiftSt = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Dry / Wet Mix",
                valueText = "${pf.dryWetPercent}%",
                value = pf.dryWetPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onPitchFormantChanged(pf.copy(dryWetPercent = it.roundToInt())) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Fine Tune",
                    valueText = "${pf.fineTuneCents} cents",
                    value = pf.fineTuneCents.toFloat(),
                    valueRange = -100f..100f,
                    minLabel = "-100 ct",
                    maxLabel = "+100 ct",
                    onValueChange = { onPitchFormantChanged(pf.copy(fineTuneCents = it.roundToInt())) }
                )
                StudioToggleRow(
                    label = "Formant Preservation",
                    checked = pf.formantPreservation,
                    onCheckedChange = { onPitchFormantChanged(pf.copy(formantPreservation = it)) }
                )
                StudioEffectSlider(
                    label = "Output Gain",
                    valueText = formatDb(pf.outputGainDb),
                    value = pf.outputGainDb,
                    valueRange = -12f..12f,
                    minLabel = "-12 dB",
                    maxLabel = "+12 dB",
                    onValueChange = { onPitchFormantChanged(pf.copy(outputGainDb = (it * 2f).roundToInt() / 2f)) }
                )
            }
        }
    }

    // 3. Equalizer (EQ) + Frequency Graph
    item(key = "studio_fx_equalizer") {
        val eq = effectsConfig.equalizer
        StudioEffectCard(
            type = StudioEffectType.EQUALIZER,
            icon = Icons.Filled.Equalizer,
            enabled = eq.enabled,
            subtitle = if (eq.enabled) "5-Band Parametric • Mix ${eq.mixPercent}%" else null,
            userPresets = userPresets,
            onEnabledChanged = { onEqualizerChanged(eq.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.EQUALIZER) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.EQUALIZER, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            EqualizerResponseGraph(config = eq)
            StudioEffectSlider(
                label = "Low / Bass Gain",
                valueText = formatDb(eq.lowGainDb),
                value = eq.lowGainDb,
                valueRange = -12f..12f,
                minLabel = "-12 dB",
                maxLabel = "+12 dB",
                onValueChange = { onEqualizerChanged(eq.copy(lowGainDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Low-Mid Gain",
                valueText = formatDb(eq.mid1GainDb),
                value = eq.mid1GainDb,
                valueRange = -12f..12f,
                minLabel = "-12 dB",
                maxLabel = "+12 dB",
                onValueChange = { onEqualizerChanged(eq.copy(mid1GainDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Mid Gain",
                valueText = formatDb(eq.mid2GainDb),
                value = eq.mid2GainDb,
                valueRange = -12f..12f,
                minLabel = "-12 dB",
                maxLabel = "+12 dB",
                onValueChange = { onEqualizerChanged(eq.copy(mid2GainDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "High-Mid Gain",
                valueText = formatDb(eq.mid3GainDb),
                value = eq.mid3GainDb,
                valueRange = -12f..12f,
                minLabel = "-12 dB",
                maxLabel = "+12 dB",
                onValueChange = { onEqualizerChanged(eq.copy(mid3GainDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "High / Treble Gain",
                valueText = formatDb(eq.highGainDb),
                value = eq.highGainDb,
                valueRange = -12f..12f,
                minLabel = "-12 dB",
                maxLabel = "+12 dB",
                onValueChange = { onEqualizerChanged(eq.copy(highGainDb = (it * 2f).roundToInt() / 2f)) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Mix",
                    valueText = "${eq.mixPercent}%",
                    value = eq.mixPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onEqualizerChanged(eq.copy(mixPercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Low Freq",
                    valueText = formatHz(eq.lowFreqHz),
                    value = eq.lowFreqHz,
                    valueRange = 20f..500f,
                    minLabel = "20 Hz",
                    maxLabel = "500 Hz",
                    onValueChange = { onEqualizerChanged(eq.copy(lowFreqHz = (it / 5f).roundToInt() * 5f)) }
                )
                StudioEffectSlider(
                    label = "Low Q Factor",
                    valueText = String.format(Locale.US, "%.2f", eq.lowQ),
                    value = eq.lowQ,
                    valueRange = 0.30f..4.00f,
                    minLabel = "0.30",
                    maxLabel = "4.00",
                    onValueChange = { onEqualizerChanged(eq.copy(lowQ = (it * 20f).roundToInt() / 20f)) }
                )
                StudioEffectSlider(
                    label = "Low-Mid Freq",
                    valueText = formatHz(eq.mid1FreqHz),
                    value = eq.mid1FreqHz,
                    valueRange = 100f..1500f,
                    minLabel = "100 Hz",
                    maxLabel = "1.5 kHz",
                    onValueChange = { onEqualizerChanged(eq.copy(mid1FreqHz = (it / 10f).roundToInt() * 10f)) }
                )
                StudioEffectSlider(
                    label = "Low-Mid Q",
                    valueText = String.format(Locale.US, "%.2f", eq.mid1Q),
                    value = eq.mid1Q,
                    valueRange = 0.10f..10.0f,
                    minLabel = "0.10",
                    maxLabel = "10.0",
                    onValueChange = { onEqualizerChanged(eq.copy(mid1Q = (it * 20f).roundToInt() / 20f)) }
                )
                StudioEffectSlider(
                    label = "Mid Freq",
                    valueText = formatHz(eq.mid2FreqHz),
                    value = eq.mid2FreqHz,
                    valueRange = 300f..5000f,
                    minLabel = "300 Hz",
                    maxLabel = "5.0 kHz",
                    onValueChange = { onEqualizerChanged(eq.copy(mid2FreqHz = (it / 25f).roundToInt() * 25f)) }
                )
                StudioEffectSlider(
                    label = "Mid Q",
                    valueText = String.format(Locale.US, "%.2f", eq.mid2Q),
                    value = eq.mid2Q,
                    valueRange = 0.10f..10.0f,
                    minLabel = "0.10",
                    maxLabel = "10.0",
                    onValueChange = { onEqualizerChanged(eq.copy(mid2Q = (it * 20f).roundToInt() / 20f)) }
                )
                StudioEffectSlider(
                    label = "High-Mid Freq",
                    valueText = formatHz(eq.mid3FreqHz),
                    value = eq.mid3FreqHz,
                    valueRange = 1000f..12000f,
                    minLabel = "1.0 kHz",
                    maxLabel = "12 kHz",
                    onValueChange = { onEqualizerChanged(eq.copy(mid3FreqHz = (it / 50f).roundToInt() * 50f)) }
                )
                StudioEffectSlider(
                    label = "High-Mid Q",
                    valueText = String.format(Locale.US, "%.2f", eq.mid3Q),
                    value = eq.mid3Q,
                    valueRange = 0.10f..10.0f,
                    minLabel = "0.10",
                    maxLabel = "10.0",
                    onValueChange = { onEqualizerChanged(eq.copy(mid3Q = (it * 20f).roundToInt() / 20f)) }
                )
                StudioEffectSlider(
                    label = "High Freq",
                    valueText = formatHz(eq.highFreqHz),
                    value = eq.highFreqHz,
                    valueRange = 2000f..16000f,
                    minLabel = "2.0 kHz",
                    maxLabel = "16 kHz",
                    onValueChange = { onEqualizerChanged(eq.copy(highFreqHz = (it / 100f).roundToInt() * 100f)) }
                )
                StudioEffectSlider(
                    label = "High Q Factor",
                    valueText = String.format(Locale.US, "%.2f", eq.highQ),
                    value = eq.highQ,
                    valueRange = 0.30f..4.00f,
                    minLabel = "0.30",
                    maxLabel = "4.00",
                    onValueChange = { onEqualizerChanged(eq.copy(highQ = (it * 20f).roundToInt() / 20f)) }
                )
            }
        }
    }

    // 4. Compression
    item(key = "studio_fx_compression") {
        val c = effectsConfig.compression
        StudioEffectCard(
            type = StudioEffectType.COMPRESSION,
            icon = Icons.Filled.Compress,
            enabled = c.enabled,
            subtitle = if (c.enabled) String.format(Locale.US, "%.0f dB • %.1f:1", c.thresholdDb, c.ratio) else null,
            userPresets = userPresets,
            onEnabledChanged = { onCompressionChanged(c.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.COMPRESSION) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.COMPRESSION, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Threshold",
                valueText = formatDb(c.thresholdDb),
                value = c.thresholdDb,
                valueRange = -60f..0f,
                minLabel = "-60 dB",
                maxLabel = "0 dB",
                onValueChange = { onCompressionChanged(c.copy(thresholdDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Ratio",
                valueText = String.format(Locale.US, "%.1f:1", c.ratio),
                value = c.ratio,
                valueRange = 1.0f..20.0f,
                minLabel = "1.0:1",
                maxLabel = "20.0:1",
                onValueChange = { onCompressionChanged(c.copy(ratio = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Makeup Gain",
                valueText = formatDb(c.makeupGainDb),
                value = c.makeupGainDb,
                valueRange = 0f..24f,
                minLabel = "0 dB",
                maxLabel = "+24 dB",
                onValueChange = { onCompressionChanged(c.copy(makeupGainDb = (it * 2f).roundToInt() / 2f)) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Attack",
                    valueText = String.format(Locale.US, "%.1f ms", c.attackMs),
                    value = c.attackMs,
                    valueRange = 0.5f..100f,
                    minLabel = "0.5 ms",
                    maxLabel = "100 ms",
                    onValueChange = { onCompressionChanged(c.copy(attackMs = (it * 2f).roundToInt() / 2f)) }
                )
                StudioEffectSlider(
                    label = "Release",
                    valueText = "${c.releaseMs.roundToInt()} ms",
                    value = c.releaseMs,
                    valueRange = 10f..1000f,
                    minLabel = "10 ms",
                    maxLabel = "1000 ms",
                    onValueChange = { onCompressionChanged(c.copy(releaseMs = (it / 5f).roundToInt() * 5f)) }
                )
                StudioEffectSlider(
                    label = "Knee",
                    valueText = String.format(Locale.US, "%.1f dB", c.kneeDb),
                    value = c.kneeDb,
                    valueRange = 0f..24f,
                    minLabel = "0 dB",
                    maxLabel = "24 dB",
                    onValueChange = { onCompressionChanged(c.copy(kneeDb = (it * 2f).roundToInt() / 2f)) }
                )
                StudioEffectSlider(
                    label = "Output Gain",
                    valueText = formatDb(c.outputGainDb),
                    value = c.outputGainDb,
                    valueRange = -12f..12f,
                    minLabel = "-12 dB",
                    maxLabel = "+12 dB",
                    onValueChange = { onCompressionChanged(c.copy(outputGainDb = (it * 2f).roundToInt() / 2f)) }
                )
            }
        }
    }

    // 5. Saturation
    item(key = "studio_fx_saturation") {
        val sat = effectsConfig.saturation
        StudioEffectCard(
            type = StudioEffectType.SATURATION,
            icon = Icons.Filled.Whatshot,
            enabled = sat.enabled,
            subtitle = if (sat.enabled) "Drive ${sat.drivePercent}% • Warmth ${sat.warmthPercent}%" else null,
            userPresets = userPresets,
            onEnabledChanged = { onSaturationChanged(sat.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.SATURATION) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.SATURATION, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Drive",
                valueText = "${sat.drivePercent}%",
                value = sat.drivePercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onSaturationChanged(sat.copy(drivePercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Warmth",
                valueText = "${sat.warmthPercent}%",
                value = sat.warmthPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onSaturationChanged(sat.copy(warmthPercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Mix",
                valueText = "${sat.mixPercent}%",
                value = sat.mixPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onSaturationChanged(sat.copy(mixPercent = it.roundToInt())) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Tone",
                    valueText = "${if (sat.tonePercent > 0) "+" else ""}${sat.tonePercent}%",
                    value = sat.tonePercent.toFloat(),
                    valueRange = -100f..100f,
                    minLabel = "Warm",
                    maxLabel = "Bright",
                    onValueChange = { onSaturationChanged(sat.copy(tonePercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Harmonic Amount",
                    valueText = "${sat.harmonicAmountPercent}%",
                    value = sat.harmonicAmountPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onSaturationChanged(sat.copy(harmonicAmountPercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Output Level",
                    valueText = formatDb(sat.outputLevelDb),
                    value = sat.outputLevelDb,
                    valueRange = -12f..12f,
                    minLabel = "-12 dB",
                    maxLabel = "+12 dB",
                    onValueChange = { onSaturationChanged(sat.copy(outputLevelDb = (it * 2f).roundToInt() / 2f)) }
                )
            }
        }
    }

    // 6. Reverb
    item(key = "studio_fx_reverb") {
        val rev = effectsConfig.reverb
        StudioEffectCard(
            type = StudioEffectType.REVERB,
            icon = Icons.Filled.SurroundSound,
            enabled = rev.enabled,
            subtitle = if (rev.enabled) "${rev.preset.label} • ${String.format(Locale.US, "%.1fs", rev.decayTimeSec)}" else null,
            userPresets = userPresets,
            onEnabledChanged = { onReverbChanged(rev.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.REVERB) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.REVERB, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReverbPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = rev.preset == preset,
                        onClick = { onReverbChanged(preset.applyTo(enabled = rev.enabled)) },
                        label = { Text(preset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanContainer,
                            selectedLabelColor = CyanSignal
                        )
                    )
                }
            }
            StudioEffectSlider(
                label = "Room Size",
                valueText = "${rev.roomSizePercent}%",
                value = rev.roomSizePercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onReverbChanged(rev.copy(roomSizePercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Decay Time",
                valueText = String.format(Locale.US, "%.1f s", rev.decayTimeSec),
                value = rev.decayTimeSec,
                valueRange = 0.2f..8.0f,
                minLabel = "0.2 s",
                maxLabel = "8.0 s",
                onValueChange = { onReverbChanged(rev.copy(decayTimeSec = (it * 10f).roundToInt() / 10f)) }
            )
            StudioEffectSlider(
                label = "Wet Level",
                valueText = "${rev.wetPercent}%",
                value = rev.wetPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onReverbChanged(rev.copy(wetPercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Dry Level",
                valueText = "${rev.dryPercent}%",
                value = rev.dryPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onReverbChanged(rev.copy(dryPercent = it.roundToInt())) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Pre-Delay",
                    valueText = String.format(Locale.US, "%.1f ms", rev.preDelayMs),
                    value = rev.preDelayMs,
                    valueRange = 0f..100f,
                    minLabel = "0 ms",
                    maxLabel = "100 ms",
                    onValueChange = { onReverbChanged(rev.copy(preDelayMs = (it * 2f).roundToInt() / 2f)) }
                )
                StudioEffectSlider(
                    label = "Damping",
                    valueText = "${rev.dampingPercent}%",
                    value = rev.dampingPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onReverbChanged(rev.copy(dampingPercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Stereo Width",
                    valueText = "${rev.widthPercent}%",
                    value = rev.widthPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onReverbChanged(rev.copy(widthPercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Mix",
                    valueText = "${rev.mixPercent}%",
                    value = rev.mixPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onReverbChanged(rev.copy(mixPercent = it.roundToInt())) }
                )
            }
        }
    }

    // 7. Delay (synchronized with Echo in Effects tab)
    item(key = "studio_fx_delay") {
        val del = effectsConfig.echo
        StudioEffectCard(
            type = StudioEffectType.DELAY,
            icon = Icons.Filled.Schedule,
            enabled = del.enabled,
            subtitle = if (del.enabled) "${del.effectiveDelayMs} ms • FB ${del.feedbackPercent}%" else null,
            userPresets = userPresets,
            onEnabledChanged = { onDelayChanged(del.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.DELAY) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.DELAY, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Delay Time",
                valueText = "${del.effectiveDelayMs} ms",
                value = del.effectiveDelayMs.toFloat(),
                valueRange = 20f..1000f,
                minLabel = "20 ms",
                maxLabel = "1000 ms",
                enabled = !del.tempoSync,
                onValueChange = { onDelayChanged(del.copy(timeMs = (it / 5f).roundToInt() * 5)) }
            )
            StudioEffectSlider(
                label = "Feedback",
                valueText = "${del.feedbackPercent}%",
                value = del.feedbackPercent.toFloat(),
                valueRange = 0f..90f,
                minLabel = "0%",
                maxLabel = "90%",
                onValueChange = { onDelayChanged(del.copy(feedbackPercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Wet Level",
                valueText = "${del.wetPercent}%",
                value = del.wetPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onDelayChanged(del.copy(wetPercent = it.roundToInt())) }
            )
            StudioEffectSlider(
                label = "Dry Level",
                valueText = "${del.dryPercent}%",
                value = del.dryPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onDelayChanged(del.copy(dryPercent = it.roundToInt())) }
            )
            AdvancedSettingsSection {
                StudioToggleRow(
                    label = "Tempo Sync",
                    checked = del.tempoSync,
                    onCheckedChange = { onDelayChanged(del.copy(tempoSync = it)) }
                )
                if (del.tempoSync) {
                    StudioEffectSlider(
                        label = "Tempo (BPM)",
                        valueText = "${del.bpm} BPM",
                        value = del.bpm.toFloat(),
                        valueRange = 60f..200f,
                        minLabel = "60 BPM",
                        maxLabel = "200 BPM",
                        onValueChange = { onDelayChanged(del.copy(bpm = it.roundToInt())) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DelaySubdivision.entries.forEach { div ->
                            FilterChip(
                                selected = del.subdivision == div,
                                onClick = { onDelayChanged(del.copy(subdivision = div)) },
                                label = { Text(div.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanContainer,
                                    selectedLabelColor = CyanSignal
                                )
                            )
                        }
                    }
                }
                StudioEffectSlider(
                    label = "Stereo Width",
                    valueText = "${del.widthPercent}%",
                    value = del.widthPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onDelayChanged(del.copy(widthPercent = it.roundToInt())) }
                )
                StudioEffectSlider(
                    label = "Low Cut",
                    valueText = formatHz(del.lowCutHz),
                    value = del.lowCutHz,
                    valueRange = 20f..1000f,
                    minLabel = "20 Hz",
                    maxLabel = "1.0 kHz",
                    onValueChange = { onDelayChanged(del.copy(lowCutHz = (it / 10f).roundToInt() * 10f)) }
                )
                StudioEffectSlider(
                    label = "High Cut",
                    valueText = formatHz(del.highCutHz),
                    value = del.highCutHz,
                    valueRange = 1000f..18000f,
                    minLabel = "1.0 kHz",
                    maxLabel = "18 kHz",
                    onValueChange = { onDelayChanged(del.copy(highCutHz = (it / 100f).roundToInt() * 100f)) }
                )
            }
        }
    }

    // 8. Noise Gate (synchronized with Effects tab)
    item(key = "studio_fx_noise_gate") {
        val g = effectsConfig.noiseGate
        StudioEffectCard(
            type = StudioEffectType.NOISE_GATE,
            icon = Icons.Filled.VolumeOff,
            enabled = g.enabled,
            subtitle = if (g.enabled) String.format(Locale.US, "%.0f dB • Range %.0f dB", g.thresholdDb, g.rangeDb) else null,
            userPresets = userPresets,
            onEnabledChanged = { onNoiseGateChanged(g.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.NOISE_GATE) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.NOISE_GATE, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Threshold",
                valueText = formatDb(g.thresholdDb),
                value = g.thresholdDb,
                valueRange = NoiseGateConfig.MIN_THRESHOLD_DB..NoiseGateConfig.MAX_THRESHOLD_DB,
                minLabel = "-70 dB",
                maxLabel = "-10 dB",
                onValueChange = { onNoiseGateChanged(g.copy(thresholdDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Range",
                valueText = formatDb(g.rangeDb),
                value = g.rangeDb,
                valueRange = NoiseGateConfig.MIN_RANGE_DB..NoiseGateConfig.MAX_RANGE_DB,
                minLabel = "0 dB",
                maxLabel = "80 dB",
                onValueChange = { onNoiseGateChanged(g.copy(rangeDb = it.roundToInt().toFloat())) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Attack",
                    valueText = String.format(Locale.US, "%.1f ms", g.attackMs),
                    value = g.attackMs,
                    valueRange = NoiseGateConfig.MIN_ATTACK_MS..NoiseGateConfig.MAX_ATTACK_MS,
                    minLabel = "0.5 ms",
                    maxLabel = "50 ms",
                    onValueChange = { onNoiseGateChanged(g.copy(attackMs = (it * 2f).roundToInt() / 2f)) }
                )
                StudioEffectSlider(
                    label = "Hold",
                    valueText = "${g.holdMs.roundToInt()} ms",
                    value = g.holdMs,
                    valueRange = NoiseGateConfig.MIN_HOLD_MS..NoiseGateConfig.MAX_HOLD_MS,
                    minLabel = "0 ms",
                    maxLabel = "500 ms",
                    onValueChange = { onNoiseGateChanged(g.copy(holdMs = (it / 5f).roundToInt() * 5f)) }
                )
                StudioEffectSlider(
                    label = "Release",
                    valueText = "${g.releaseMs.roundToInt()} ms",
                    value = g.releaseMs,
                    valueRange = NoiseGateConfig.MIN_RELEASE_MS..NoiseGateConfig.MAX_RELEASE_MS,
                    minLabel = "20 ms",
                    maxLabel = "600 ms",
                    onValueChange = { onNoiseGateChanged(g.copy(releaseMs = (it / 10f).roundToInt() * 10f)) }
                )
                StudioEffectSlider(
                    label = "Mix",
                    valueText = "${g.mixPercent}%",
                    value = g.mixPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onNoiseGateChanged(g.copy(mixPercent = it.roundToInt())) }
                )
            }
        }
    }

    // 9. De-Esser
    item(key = "studio_fx_de_esser") {
        val de = effectsConfig.deEsser
        StudioEffectCard(
            type = StudioEffectType.DE_ESSER,
            icon = Icons.Filled.Hearing,
            enabled = de.enabled,
            subtitle = if (de.enabled) "${formatHz(de.frequencyHz)} • ${de.reductionPercent}%" else null,
            userPresets = userPresets,
            onEnabledChanged = { onDeEsserChanged(de.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.DE_ESSER) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.DE_ESSER, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Frequency",
                valueText = formatHz(de.frequencyHz),
                value = de.frequencyHz,
                valueRange = 3000f..10000f,
                minLabel = "3.0 kHz",
                maxLabel = "10.0 kHz",
                onValueChange = { onDeEsserChanged(de.copy(frequencyHz = (it / 50f).roundToInt() * 50f)) }
            )
            StudioEffectSlider(
                label = "Threshold",
                valueText = formatDb(de.thresholdDb),
                value = de.thresholdDb,
                valueRange = -50f..0f,
                minLabel = "-50 dB",
                maxLabel = "0 dB",
                onValueChange = { onDeEsserChanged(de.copy(thresholdDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Reduction",
                valueText = "${de.reductionPercent}%",
                value = de.reductionPercent.toFloat(),
                valueRange = 0f..100f,
                minLabel = "0%",
                maxLabel = "100%",
                onValueChange = { onDeEsserChanged(de.copy(reductionPercent = it.roundToInt())) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Bandwidth (Q)",
                    valueText = String.format(Locale.US, "%.2f", de.bandwidthQ),
                    value = de.bandwidthQ,
                    valueRange = 0.5f..6.0f,
                    minLabel = "0.50",
                    maxLabel = "6.00",
                    onValueChange = { onDeEsserChanged(de.copy(bandwidthQ = (it * 10f).roundToInt() / 10f)) }
                )
                StudioEffectSlider(
                    label = "Mix",
                    valueText = "${de.mixPercent}%",
                    value = de.mixPercent.toFloat(),
                    valueRange = 0f..100f,
                    minLabel = "0%",
                    maxLabel = "100%",
                    onValueChange = { onDeEsserChanged(de.copy(mixPercent = it.roundToInt())) }
                )
            }
        }
    }

    // 10. Limiter
    item(key = "studio_fx_limiter") {
        val lim = effectsConfig.limiter
        StudioEffectCard(
            type = StudioEffectType.LIMITER,
            icon = Icons.Filled.Shield,
            enabled = lim.enabled,
            subtitle = if (lim.enabled) String.format(Locale.US, "Ceil %.1f dB • Thresh %.1f dB", lim.ceilingDb, lim.thresholdDb) else null,
            userPresets = userPresets,
            onEnabledChanged = { onLimiterChanged(lim.copy(enabled = it)) },
            onReset = { onResetEffect(StudioEffectType.LIMITER) },
            onSavePreset = { name -> onSavePreset(StudioEffectType.LIMITER, name) },
            onLoadPreset = onLoadPreset,
            onDeleteUserPreset = onDeleteUserPreset
        ) {
            StudioEffectSlider(
                label = "Threshold",
                valueText = formatDb(lim.thresholdDb),
                value = lim.thresholdDb,
                valueRange = -30f..0f,
                minLabel = "-30 dB",
                maxLabel = "0 dB",
                onValueChange = { onLimiterChanged(lim.copy(thresholdDb = (it * 2f).roundToInt() / 2f)) }
            )
            StudioEffectSlider(
                label = "Ceiling",
                valueText = formatDb(lim.ceilingDb),
                value = lim.ceilingDb,
                valueRange = -12f..-0.1f,
                minLabel = "-12 dB",
                maxLabel = "-0.1 dB",
                onValueChange = { onLimiterChanged(lim.copy(ceilingDb = (it * 10f).roundToInt() / 10f)) }
            )
            StudioEffectSlider(
                label = "Input Gain",
                valueText = formatDb(lim.inputGainDb),
                value = lim.inputGainDb,
                valueRange = -12f..18f,
                minLabel = "-12 dB",
                maxLabel = "+18 dB",
                onValueChange = { onLimiterChanged(lim.copy(inputGainDb = (it * 2f).roundToInt() / 2f)) }
            )
            AdvancedSettingsSection {
                StudioEffectSlider(
                    label = "Release",
                    valueText = "${lim.releaseMs.roundToInt()} ms",
                    value = lim.releaseMs,
                    valueRange = 10f..500f,
                    minLabel = "10 ms",
                    maxLabel = "500 ms",
                    onValueChange = { onLimiterChanged(lim.copy(releaseMs = (it / 5f).roundToInt() * 5f)) }
                )
                StudioEffectSlider(
                    label = "Output Gain",
                    valueText = formatDb(lim.outputGainDb),
                    value = lim.outputGainDb,
                    valueRange = -12f..6f,
                    minLabel = "-12 dB",
                    maxLabel = "+6 dB",
                    onValueChange = { onLimiterChanged(lim.copy(outputGainDb = (it * 2f).roundToInt() / 2f)) }
                )
            }
        }
    }
}

@Composable
private fun StudioEffectCard(
    type: StudioEffectType,
    icon: ImageVector,
    enabled: Boolean,
    subtitle: String?,
    userPresets: List<StudioEffectPreset>,
    onEnabledChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (StudioEffectPreset) -> Unit,
    onDeleteUserPreset: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val tagKey = type.name.lowercase()
    var showSaveDialog by remember { mutableStateOf(false) }

    val availablePresets = remember(type, userPresets) {
        BuiltInEffectPresets.forEffect(type) + userPresets.filter { it.effectType == type }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_fx_${tagKey}_card"),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(
            1.dp,
            if (enabled) CyanSignal.copy(alpha = 0.65f) else StudioBorder
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (enabled) CyanContainer else StudioRack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) CyanSignal else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSignal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioRack,
                        checkedTrackColor = EmeraldSafe
                    ),
                    modifier = Modifier.testTag("studio_fx_${tagKey}_switch")
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = StudioBorder)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Independent Reset / Save Preset / Load Preset Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onReset,
                                border = BorderStroke(1.dp, StudioBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("studio_fx_${tagKey}_reset")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset ${type.displayName}",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }

                            OutlinedButton(
                                onClick = { showSaveDialog = true },
                                border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("studio_fx_${tagKey}_save_preset")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd,
                                    contentDescription = "Save preset for ${type.displayName}",
                                    tint = CyanSignal,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Preset", style = MaterialTheme.typography.labelSmall, color = CyanSignal)
                            }
                        }

                        if (availablePresets.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Presets:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                availablePresets.forEach { preset ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = StudioRack,
                                        border = BorderStroke(1.dp, StudioBorder),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onLoadPreset(preset) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = preset.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                            if (!preset.isBuiltIn) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Delete preset ${preset.name}",
                                                    tint = TextMuted,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { onDeleteUserPreset(preset.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        content()
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = StudioPanel,
            title = { Text("Save ${type.displayName} Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    singleLine = true,
                    label = { Text("Preset name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePreset(presetName)
                        showSaveDialog = false
                    },
                    enabled = presetName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSignal, contentColor = StudioRack)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdvancedSettingsSection(
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = StudioRack,
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse advanced settings" else "Expand advanced settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun StudioEffectSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    minLabel: String,
    maxLabel: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) TextSecondary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(106.dp)
            )
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = CyanSignal,
                    activeTrackColor = CyanSignal,
                    inactiveTrackColor = StudioRack
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = StudioRack,
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.width(76.dp)
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 114.dp, end = 84.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = minLabel, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = maxLabel, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun StudioToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioRack,
                checkedTrackColor = CyanSignal
            )
        )
    }
}

/**
 * Compact Equalizer Frequency Response Graph (20 Hz - 20 kHz log scale, -15 dB to +15 dB).
 */
@Composable
fun EqualizerResponseGraph(
    config: EqualizerConfig,
    modifier: Modifier = Modifier
) {
    val freqPoints = remember {
        FloatArray(64) { i ->
            val t = i / 63f
            (20.0 * 1000.0.pow(t.toDouble())).toFloat()
        }
    }
    val responseDb = remember(config) {
        EqualizerEffect.computeMagnitudeCurveDb(config, freqPoints)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("eq_frequency_graph"),
        shape = RoundedCornerShape(10.dp),
        color = StudioRack,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EQ FREQUENCY RESPONSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = "±15 dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanSignal
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                val w = size.width
                val h = size.height
                val midY = h * 0.5f

                // Grid lines: +6 dB, 0 dB, -6 dB and vertical log decades (100Hz, 1kHz, 10kHz)
                val dbLines = listOf(-6f, 0f, 6f)
                dbLines.forEach { db ->
                    val y = midY - (db / 15f) * (h * 0.45f)
                    drawLine(
                        color = if (db == 0f) Color(0xFF3A4A66) else Color(0xFF1F2838),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = if (db == 0f) 1.5f else 1f
                    )
                }

                listOf(100f, 1000f, 10000f).forEach { fMark ->
                    val xNorm = (ln(fMark / 20f) / ln(1000f)).coerceIn(0f, 1f)
                    drawLine(
                        color = Color(0xFF1F2838),
                        start = Offset(xNorm * w, 0f),
                        end = Offset(xNorm * w, h),
                        strokeWidth = 1f
                    )
                }

                val linePath = Path()
                val fillPath = Path()
                responseDb.forEachIndexed { idx, db ->
                    val x = (idx / (responseDb.size - 1f)) * w
                    val y = (midY - (db / 15f) * (h * 0.45f)).coerceIn(2f, h - 2f)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, midY)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, midY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(CyanSignal.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                drawPath(
                    path = linePath,
                    color = CyanSignal,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Plot 5 band control nodes
                val bands = listOf(
                    config.lowFreqHz to config.lowGainDb,
                    config.mid1FreqHz to config.mid1GainDb,
                    config.mid2FreqHz to config.mid2GainDb,
                    config.mid3FreqHz to config.mid3GainDb,
                    config.highFreqHz to config.highGainDb
                )
                bands.forEach { (freq, gain) ->
                    val xNorm = (ln(freq.coerceIn(20f, 20000f) / 20f) / ln(1000f)).coerceIn(0f, 1f)
                    val x = xNorm * w
                    val y = (midY - (gain / 15f) * (h * 0.45f)).coerceIn(4f, h - 4f)
                    drawCircle(color = StudioRack, radius = 6f, center = Offset(x, y))
                    drawCircle(color = EmeraldSafe, radius = 4f, center = Offset(x, y))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("20 Hz", "100 Hz", "1 kHz", "10 kHz", "20 kHz").forEach { lbl ->
                    Text(text = lbl, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

private fun formatHz(hz: Float): String {
    return if (hz >= 1000f) {
        String.format(Locale.US, "%.2f kHz", hz / 1000f)
    } else {
        String.format(Locale.US, "%d Hz", hz.roundToInt())
    }
}

private fun formatDb(db: Float): String {
    return String.format(Locale.US, "%+.1f dB", db)
}
