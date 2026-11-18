package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioHardwareState
import com.example.audio.LiveAudioMetrics
import com.example.audio.MonitoringConfig
import com.example.audio.MonitoringLatencyZone
import com.example.dsp.VoiceChangerConfig
import com.example.dsp.VoicePreset
import com.example.ui.components.DspParameterSlider
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPanelElevated
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VoiceChangerScreen(
    config: VoiceChangerConfig,
    monitoringConfig: MonitoringConfig,
    hardwareState: AudioHardwareState,
    metrics: LiveAudioMetrics,
    onSelectPreset: (VoicePreset) -> Unit,
    onResetPreset: () -> Unit,
    onBypassChanged: (Boolean) -> Unit,
    onPitchChanged: (Float) -> Unit,
    onFormantChanged: (Float) -> Unit,
    onFineTuneChanged: (Int) -> Unit,
    onTimbreChanged: (Float) -> Unit,
    onDryWetChanged: (Int) -> Unit,
    onLivePreviewToggle: (Boolean) -> Unit,
    onMonitoringDelayChanged: (Float) -> Unit,
    onResetMonitoringDelay: () -> Unit,
    onMonitoringVolumeChanged: (Int) -> Unit,
    onEchoCancellationToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live Voice Preview, Monitoring Delay & Volume (0-500%)
            LiveVoicePreviewCard(
                monitoringConfig = monitoringConfig,
                hardwareState = hardwareState,
                estimatedLatencyMs = metrics.estimatedMonitoringLatencyMs,
                onLivePreviewToggle = onLivePreviewToggle,
                onDelayChanged = onMonitoringDelayChanged,
                onResetDelay = onResetMonitoringDelay,
                onVolumeChanged = onMonitoringVolumeChanged,
                onEchoCancellationToggle = onEchoCancellationToggle
            )

            // 2. Voice Presets & Bypass Bar
            VoicePresetAndBypassCard(
                config = config,
                onSelectPreset = onSelectPreset,
                onResetPreset = onResetPreset,
                onBypassChanged = onBypassChanged
            )

            // 3. Five DSP Parameter Sliders
            val controlsActive = !config.bypass

            DspParameterSlider(
                label = "Pitch",
                valueText = String.format(Locale.US, "%+.1f st", config.pitchSemitones),
                value = config.pitchSemitones,
                valueRange = VoiceChangerConfig.MIN_PITCH_ST..VoiceChangerConfig.MAX_PITCH_ST,
                step = VoiceChangerConfig.STEP_PITCH_ST,
                minLabel = "-12.0 st",
                maxLabel = "+12.0 st",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_pitch",
                onValueChange = onPitchChanged
            )

            DspParameterSlider(
                label = "Formant",
                valueText = String.format(Locale.US, "%+.1f st", config.formantSemitones),
                value = config.formantSemitones,
                valueRange = VoiceChangerConfig.MIN_FORMANT_ST..VoiceChangerConfig.MAX_FORMANT_ST,
                step = VoiceChangerConfig.STEP_FORMANT_ST,
                minLabel = "-12.0 st (Deep)",
                maxLabel = "+12.0 st (Light)",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_formant",
                onValueChange = onFormantChanged
            )

            DspParameterSlider(
                label = "Fine Tune",
                valueText = String.format(Locale.US, "%+d cents", config.fineTuneCents),
                value = config.fineTuneCents.toFloat(),
                valueRange = VoiceChangerConfig.MIN_FINE_TUNE_CENTS.toFloat()..VoiceChangerConfig.MAX_FINE_TUNE_CENTS.toFloat(),
                step = VoiceChangerConfig.STEP_FINE_TUNE_CENTS.toFloat(),
                minLabel = "-100 cents",
                maxLabel = "+100 cents",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_finetune",
                onValueChange = { onFineTuneChanged(it.roundToInt()) }
            )

            DspParameterSlider(
                label = "Timbre / Tone",
                valueText = String.format(Locale.US, "%+.1f dB", config.timbreDb),
                value = config.timbreDb,
                valueRange = VoiceChangerConfig.MIN_TIMBRE_DB..VoiceChangerConfig.MAX_TIMBRE_DB,
                step = VoiceChangerConfig.STEP_TIMBRE_DB,
                minLabel = "-12.0 dB (Warm)",
                maxLabel = "+12.0 dB (Crisp)",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_timbre",
                onValueChange = onTimbreChanged
            )

            DspParameterSlider(
                label = "Dry / Wet Mix",
                valueText = "${config.dryWetPercent}%",
                value = config.dryWetPercent.toFloat(),
                valueRange = VoiceChangerConfig.MIN_DRY_WET_PCT.toFloat()..VoiceChangerConfig.MAX_DRY_WET_PCT.toFloat(),
                step = VoiceChangerConfig.STEP_DRY_WET_PCT.toFloat(),
                minLabel = "0% (Dry)",
                maxLabel = "100% (Wet)",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_drywet",
                onValueChange = { onDryWetChanged(it.roundToInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LiveVoicePreviewCard(
    monitoringConfig: MonitoringConfig,
    hardwareState: AudioHardwareState,
    estimatedLatencyMs: Int,
    onLivePreviewToggle: (Boolean) -> Unit,
    onDelayChanged: (Float) -> Unit,
    onResetDelay: () -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onEchoCancellationToggle: (Boolean) -> Unit
) {
    val zone = monitoringConfig.zone
    val addedMs = monitoringConfig.addedOutputDelayMs

    val statusReadout = when (zone) {
        MonitoringLatencyZone.FASTER ->
            "Faster • Min Buffer (~$estimatedLatencyMs ms est. latency)"
        MonitoringLatencyZone.DEFAULT ->
            "Default • 0 ms added (~$estimatedLatencyMs ms est. latency)"
        MonitoringLatencyZone.MORE_DELAY ->
            "More Delay • +$addedMs ms added (~$estimatedLatencyMs ms est. latency)"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = StudioPanel,
        border = BorderStroke(
            1.dp,
            if (monitoringConfig.livePreviewEnabled) CyanSignal else StudioBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Live Voice Preview ON/OFF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = "Live Voice Preview",
                        tint = if (monitoringConfig.livePreviewEnabled) CyanSignal else TextSecondary
                    )
                    Column {
                        Text(
                            text = "Live Voice Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (monitoringConfig.livePreviewEnabled) {
                                "Monitoring processed voice in real time"
                            } else {
                                "Real-time headphone monitoring is OFF"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (monitoringConfig.livePreviewEnabled) CyanSignal else TextSecondary
                        )
                    }
                }

                Switch(
                    checked = monitoringConfig.livePreviewEnabled,
                    onCheckedChange = onLivePreviewToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioRack,
                        checkedTrackColor = CyanSignal
                    ),
                    modifier = Modifier.testTag("live_preview_switch")
                )
            }

            if (monitoringConfig.livePreviewEnabled && !hardwareState.isWiredHeadphoneConnected) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberContainer,
                    border = BorderStroke(1.dp, AmberCaution.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = AmberCaution,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Connect wired headphones to prevent acoustic speaker feedback.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberCaution
                        )
                    }
                }
            }

            // 2. Monitoring Delay Adjustment Slider directly underneath ON/OFF
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioRack,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monitoring Delay Adjustment",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onResetDelay,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("monitoring_delay_reset")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset to Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = statusReadout,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (zone) {
                            MonitoringLatencyZone.FASTER -> EmeraldSafe
                            MonitoringLatencyZone.DEFAULT -> CyanSignal
                            MonitoringLatencyZone.MORE_DELAY -> AmberCaution
                        },
                        modifier = Modifier.testTag("monitoring_delay_status")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                onDelayChanged(
                                    (monitoringConfig.delaySliderPosition - MonitoringConfig.STEP_SLIDER)
                                        .coerceIn(MonitoringConfig.MIN_SLIDER, MonitoringConfig.MAX_SLIDER)
                                )
                            },
                            enabled = monitoringConfig.delaySliderPosition > MonitoringConfig.MIN_SLIDER + 0.001f,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = StudioPanelElevated,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("monitoring_delay_minus")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Decrease monitoring delay"
                            )
                        }

                        Slider(
                            value = monitoringConfig.delaySliderPosition,
                            onValueChange = onDelayChanged,
                            valueRange = MonitoringConfig.MIN_SLIDER..MonitoringConfig.MAX_SLIDER,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanSignal,
                                activeTrackColor = CyanSignal,
                                inactiveTrackColor = StudioPanelElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("monitoring_delay_slider")
                        )

                        FilledTonalIconButton(
                            onClick = {
                                onDelayChanged(
                                    (monitoringConfig.delaySliderPosition + MonitoringConfig.STEP_SLIDER)
                                        .coerceIn(MonitoringConfig.MIN_SLIDER, MonitoringConfig.MAX_SLIDER)
                                )
                            },
                            enabled = monitoringConfig.delaySliderPosition < MonitoringConfig.MAX_SLIDER - 0.001f,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = StudioPanelElevated,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("monitoring_delay_plus")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Increase monitoring delay"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Faster",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (zone == MonitoringLatencyZone.FASTER) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (zone == MonitoringLatencyZone.FASTER) EmeraldSafe else TextMuted
                        )
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (zone == MonitoringLatencyZone.DEFAULT) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (zone == MonitoringLatencyZone.DEFAULT) CyanSignal else TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "More Delay",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (zone == MonitoringLatencyZone.MORE_DELAY) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (zone == MonitoringLatencyZone.MORE_DELAY) AmberCaution else TextMuted
                        )
                    }

                    Text(
                        text = "Applies to Live Voice Preview headphones only. WAV recording path stays zero-delay.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // 3. Preview Volume Boost Slider (100% starting, max 500%)
            DspParameterSlider(
                label = "Preview Volume",
                valueText = "${monitoringConfig.previewVolumePercent}%",
                value = monitoringConfig.previewVolumePercent.toFloat(),
                valueRange = MonitoringConfig.MIN_VOLUME_PERCENT.toFloat()..MonitoringConfig.MAX_VOLUME_PERCENT.toFloat(),
                step = MonitoringConfig.STEP_VOLUME_PERCENT.toFloat(),
                minLabel = "0% (Mute)",
                maxLabel = "500% (Max Boost)",
                enabled = true,
                accentColor = CyanSignal,
                testTagPrefix = "monitoring_volume",
                onValueChange = { onVolumeChanged(it.roundToInt()) }
            )

            // 4. Echo Cancellation (AEC) + Audio Pipeline Protections
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioRack,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acoustic Echo Cancellation (AEC)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Prevents speaker feedback crackle • Active: Low-Latency Burst, PLC & 120 Hz HPF",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = monitoringConfig.echoCancellationEnabled,
                            onCheckedChange = onEchoCancellationToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StudioRack,
                                checkedTrackColor = EmeraldSafe
                            ),
                            modifier = Modifier.testTag("monitoring_aec_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePresetAndBypassCard(
    config: VoiceChangerConfig,
    onSelectPreset: (VoicePreset) -> Unit,
    onResetPreset: () -> Unit,
    onBypassChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = CyanSignal,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Voice Presets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (config.bypass) "BYPASSED" else "DSP ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (config.bypass) AmberCaution else EmeraldSafe
                    )
                    Switch(
                        checked = config.bypass,
                        onCheckedChange = onBypassChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioRack,
                            checkedTrackColor = AmberCaution
                        ),
                        modifier = Modifier.testTag("voice_bypass_switch")
                    )
                }
            }

            // Exactly four presets: Normal, Girl, Boy, Child
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoicePreset.entries.forEach { preset ->
                    val selected = config.preset == preset
                    Surface(
                        onClick = { onSelectPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) CyanContainer else StudioRack,
                        border = BorderStroke(
                            1.dp,
                            if (selected) CyanSignal else StudioBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("preset_${preset.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) CyanSignal else TextSecondary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (config.bypass) {
                        "Voice Changer bypassed (Noise Gate unaffected)"
                    } else {
                        "Preset: ${config.preset.displayName} • Pitch & Formant DSP"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                OutlinedButton(
                    onClick = onResetPreset,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("voice_reset_preset_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Preset", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
