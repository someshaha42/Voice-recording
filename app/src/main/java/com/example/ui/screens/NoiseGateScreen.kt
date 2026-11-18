package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.LiveAudioMetrics
import com.example.dsp.NoiseGateConfig
import com.example.ui.components.DspParameterSlider
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun NoiseGateScreen(
    config: NoiseGateConfig,
    metrics: LiveAudioMetrics,
    onEnabledChanged: (Boolean) -> Unit,
    onThresholdChanged: (Float) -> Unit,
    onAttackChanged: (Float) -> Unit,
    onReleaseChanged: (Float) -> Unit,
    onResetDefaults: () -> Unit,
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
            NoiseGateStatusCard(
                config = config,
                metrics = metrics,
                onEnabledChanged = onEnabledChanged,
                onResetDefaults = onResetDefaults
            )

            DspParameterSlider(
                label = "Threshold",
                valueText = String.format(Locale.US, "%.0f dBFS", config.thresholdDb),
                value = config.thresholdDb,
                valueRange = NoiseGateConfig.MIN_THRESHOLD_DB..NoiseGateConfig.MAX_THRESHOLD_DB,
                step = NoiseGateConfig.STEP_THRESHOLD_DB,
                minLabel = "-70 dBFS",
                maxLabel = "-10 dBFS",
                enabled = config.enabled,
                accentColor = EmeraldSafe,
                testTagPrefix = "gate_threshold",
                onValueChange = onThresholdChanged
            )

            DspParameterSlider(
                label = "Attack",
                valueText = String.format(Locale.US, "%.1f ms", config.attackMs),
                value = config.attackMs,
                valueRange = NoiseGateConfig.MIN_ATTACK_MS..NoiseGateConfig.MAX_ATTACK_MS,
                step = NoiseGateConfig.STEP_ATTACK_MS,
                minLabel = "0.5 ms (Fast)",
                maxLabel = "50.0 ms (Soft)",
                enabled = config.enabled,
                accentColor = EmeraldSafe,
                testTagPrefix = "gate_attack",
                onValueChange = onAttackChanged
            )

            DspParameterSlider(
                label = "Release",
                valueText = String.format(Locale.US, "%.0f ms", config.releaseMs),
                value = config.releaseMs,
                valueRange = NoiseGateConfig.MIN_RELEASE_MS..NoiseGateConfig.MAX_RELEASE_MS,
                step = NoiseGateConfig.STEP_RELEASE_MS,
                minLabel = "20 ms (Tight)",
                maxLabel = "600 ms (Smooth)",
                enabled = config.enabled,
                accentColor = EmeraldSafe,
                testTagPrefix = "gate_release",
                onValueChange = onReleaseChanged
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = StudioPanel,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Text(
                    text = "Tip: Noise Gate attenuates ambient room noise during silent pauses between spoken phrases. Set Threshold just above your room noise floor; it does not strip noise mixed inside active speech.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun NoiseGateStatusCard(
    config: NoiseGateConfig,
    metrics: LiveAudioMetrics,
    onEnabledChanged: (Boolean) -> Unit,
    onResetDefaults: () -> Unit
) {
    val gateBadgeText = when {
        !config.enabled -> "BYPASSED"
        metrics.gateOpen -> "GATE OPEN"
        else -> "GATE CLOSED"
    }
    val gateBadgeColor = when {
        !config.enabled -> TextMuted
        metrics.gateOpen -> EmeraldSafe
        else -> AmberCaution
    }
    val gateBadgeBg = when {
        !config.enabled -> StudioRack
        metrics.gateOpen -> EmeraldContainer
        else -> AmberContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = StudioPanel,
        border = BorderStroke(1.dp, if (config.enabled) EmeraldSafe.copy(alpha = 0.6f) else StudioBorder)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Noise Gate",
                        tint = if (config.enabled) EmeraldSafe else TextMuted
                    )
                    Column {
                        Text(
                            text = "Real-Time Noise Gate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.enabled) {
                                "Attenuates quiet pauses below threshold"
                            } else {
                                "Noise Gate bypassed (Voice Changer unaffected)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = onEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioRack,
                        checkedTrackColor = EmeraldSafe
                    ),
                    modifier = Modifier.testTag("noise_gate_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = gateBadgeBg,
                    border = BorderStroke(1.dp, gateBadgeColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("noise_gate_state_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(gateBadgeColor)
                        )
                        Text(
                            text = gateBadgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = gateBadgeColor
                        )
                    }
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("noise_gate_reset")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Defaults", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Gain Reduction Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gain Reduction",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f dB", metrics.gateGainReductionDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (config.enabled && !metrics.gateOpen) AmberCaution else TextSecondary
                    )
                }
                val reductionNorm = ((-metrics.gateGainReductionDb) / 60f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { reductionNorm },
                    color = AmberCaution,
                    trackColor = StudioRack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
