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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlin.math.roundToInt

@Composable
fun VoiceChangerScreen(
    config: VoiceChangerConfig,
    monitoringConfig: MonitoringConfig,
    hardwareState: AudioHardwareState,
    metrics: LiveAudioMetrics,
    onEnabledChanged: (Boolean) -> Unit,
    onSelectPreset: (VoicePreset) -> Unit,
    onResetPreset: () -> Unit,
    onPitchLevelChanged: (Int) -> Unit,
    onToneLevelChanged: (Int) -> Unit,
    onClarityChanged: (Int) -> Unit,
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

            // 2. Voice Changer Module Card (ON/OFF toggle, default OFF + 5 fixed presets)
            VoiceChangerPresetCard(
                config = config,
                onEnabledChanged = onEnabledChanged,
                onSelectPreset = onSelectPreset,
                onResetPreset = onResetPreset
            )

            // 3. ONLY Three User-Adjustable Controls: Pitch Level, Tone Level, Clarity
            val controlsActive = config.enabled

            DspParameterSlider(
                label = "Pitch Level",
                valueText = "${config.pitchLevelPercent}%",
                value = config.pitchLevelPercent.toFloat(),
                valueRange = VoiceChangerConfig.MIN_LEVEL_PCT.toFloat()..VoiceChangerConfig.MAX_LEVEL_PCT.toFloat(),
                step = VoiceChangerConfig.STEP_LEVEL_PCT.toFloat(),
                minLabel = "0% (No Pitch Shift)",
                maxLabel = "100% (Full Voice)",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_pitch_level",
                onValueChange = { onPitchLevelChanged(it.roundToInt()) }
            )

            DspParameterSlider(
                label = "Tone Level",
                valueText = "${config.toneLevelPercent}%",
                value = config.toneLevelPercent.toFloat(),
                valueRange = VoiceChangerConfig.MIN_LEVEL_PCT.toFloat()..VoiceChangerConfig.MAX_LEVEL_PCT.toFloat(),
                step = VoiceChangerConfig.STEP_LEVEL_PCT.toFloat(),
                minLabel = "0% (Original Tone)",
                maxLabel = "100% (Full Tone)",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_tone_level",
                onValueChange = { onToneLevelChanged(it.roundToInt()) }
            )

            DspParameterSlider(
                label = "Clarity",
                valueText = "${config.clarityPercent}%",
                value = config.clarityPercent.toFloat(),
                valueRange = VoiceChangerConfig.MIN_LEVEL_PCT.toFloat()..VoiceChangerConfig.MAX_LEVEL_PCT.toFloat(),
                step = VoiceChangerConfig.STEP_LEVEL_PCT.toFloat(),
                minLabel = "0% (Natural)",
                maxLabel = "100% (High Clarity)",
                enabled = controlsActive,
                accentColor = CyanSignal,
                testTagPrefix = "vc_clarity",
                onValueChange = { onClarityChanged(it.roundToInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VoiceChangerPresetCard(
    config: VoiceChangerConfig,
    onEnabledChanged: (Boolean) -> Unit,
    onSelectPreset: (VoicePreset) -> Unit,
    onResetPreset: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = StudioPanel,
        border = BorderStroke(
            1.dp,
            if (config.enabled) CyanSignal.copy(alpha = 0.7f) else StudioBorder
        )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Voice Changer",
                        tint = if (config.enabled) CyanSignal else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Voice Changer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.enabled) {
                                "Active: ${config.preset.displayName} profile"
                            } else {
                                "OFF • Original microphone audio unchanged"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (config.enabled) CyanSignal else TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (config.enabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (config.enabled) EmeraldSafe else TextMuted
                    )
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = onEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioRack,
                            checkedTrackColor = CyanSignal
                        ),
                        modifier = Modifier.testTag("voice_changer_switch")
                    )
                }
            }

            // Five Fixed Voice Presets: Normal, Female, Child, Girl, Woman
            val presets = VoicePreset.entries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.take(3).forEach { preset ->
                        PresetPillButton(
                            preset = preset,
                            selected = config.preset == preset,
                            active = config.enabled,
                            onClick = { onSelectPreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.drop(3).forEach { preset ->
                        PresetPillButton(
                            preset = preset,
                            selected = config.preset == preset,
                            active = config.enabled,
                            onClick = { onSelectPreset(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (config.enabled) {
                        "Natural PSOLA + Formant vocal profile"
                    } else {
                        "Turn ON to apply selected voice profile"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
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

@Composable
private fun PresetPillButton(
    preset: VoicePreset,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightColor = if (active) CyanSignal else TextPrimaryColorFallback(selected)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            selected && active -> CyanContainer
            selected -> StudioPanelElevated
            else -> StudioRack
        },
        border = BorderStroke(
            1.dp,
            when {
                selected && active -> CyanSignal
                selected -> TextSecondary
                else -> StudioBorder
            }
        ),
        modifier = modifier
            .height(48.dp)
            .testTag("preset_${preset.name.lowercase()}")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) highlightColor else TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TextPrimaryColorFallback(selected: Boolean) =
    if (selected) MaterialTheme.colorScheme.onSurface else TextSecondary

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

            // 2. Monitoring Delay Adjustment Slider
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

            // 3. Preview Volume Boost Slider
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

            // 4. Echo Cancellation (AEC)
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
