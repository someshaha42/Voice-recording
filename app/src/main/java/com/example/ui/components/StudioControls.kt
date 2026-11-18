package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioHardwareState
import com.example.audio.LiveAudioMetrics
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RecordCoral
import com.example.ui.theme.RecordCoralContainer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPanelElevated
import com.example.ui.theme.StudioRack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DspParameterSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    minLabel: String,
    maxLabel: String,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    testTagPrefix: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else TextMuted
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StudioRack,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) accentColor else TextMuted,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("${testTagPrefix}_value")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val next = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(next)
                    },
                    enabled = enabled && value > valueRange.start + 0.0001f,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = StudioPanelElevated,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_minus")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.cd_decrease_param)
                    )
                }

                Slider(
                    value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = StudioRack
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_slider")
                )

                FilledTonalIconButton(
                    onClick = {
                        val next = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(next)
                    },
                    enabled = enabled && value < valueRange.endInclusive - 0.0001f,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = StudioPanelElevated,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_plus")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_increase_param)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = minLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = maxLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun StudioLevelMeterCard(
    metrics: LiveAudioMetrics,
    onResetClips: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE INPUT LEVEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "RMS %.1f dB  •  PK %.1f dB", metrics.rmsDb, metrics.peakDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (metrics.isClipping || metrics.clipCount > 0) {
                            RecordCoralContainer
                        } else {
                            StudioRack
                        },
                        border = BorderStroke(
                            1.dp,
                            if (metrics.isClipping || metrics.clipCount > 0) RecordCoral else StudioBorder
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onResetClips)
                            .testTag("clip_indicator")
                    ) {
                        Text(
                            text = if (metrics.clipCount > 0) "CLIP ${metrics.clipCount}" else "CLIP",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (metrics.isClipping || metrics.clipCount > 0) RecordCoral else TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Segmented precision dBFS bar (-60 dB to 0 dB)
            val normRms = ((metrics.rmsDb + 60f) / 60f).coerceIn(0f, 1f)
            val normPeak = ((metrics.peakDb + 60f) / 60f).coerceIn(0f, 1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                val segments = 30
                val gap = 3.dp.toPx()
                val segWidth = (size.width - gap * (segments - 1)) / segments
                val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                val peakSeg = (normPeak * segments).toInt().coerceIn(0, segments - 1)

                for (i in 0 until segments) {
                    val threshold = (i + 1) / segments.toFloat()
                    val lit = normRms >= threshold - (1f / segments * 0.5f)
                    val isPeakHold = normPeak > 0.04f && i == peakSeg

                    val activeColor = when {
                        i >= 26 -> RecordCoral // -8 dB to 0 dB
                        i >= 20 -> AmberCaution // -20 dB to -8 dB
                        else -> EmeraldSafe
                    }
                    val color = when {
                        lit || isPeakHold -> activeColor
                        else -> Color(0xFF1F2737)
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * (segWidth + gap), 0f),
                        size = Size(segWidth, size.height),
                        cornerRadius = radius
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("-60", "-42", "-24", "-12", "-6", "0 dB").forEach { mark ->
                    Text(
                        text = mark,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun HardwareStatusCard(
    hardwareState: AudioHardwareState,
    onRefreshDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(
            1.dp,
            if (hardwareState.isUsbMicConnected) CyanSignal.copy(alpha = 0.65f) else StudioBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (hardwareState.isUsbMicConnected) CyanContainer else StudioRack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hardwareState.isUsbMicConnected) Icons.Filled.Usb else Icons.Filled.Mic,
                            contentDescription = "Microphone input source",
                            tint = if (hardwareState.isUsbMicConnected) CyanSignal else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = hardwareState.inputDeviceName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val rateText = if (hardwareState.isResamplingActive) {
                            "Capture ${hardwareState.activeInputSampleRateHz} Hz → Resampled 48,000 Hz"
                        } else {
                            "Capture ${hardwareState.activeInputSampleRateHz} Hz • Native 48 kHz"
                        }
                        Text(
                            text = rateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hardwareState.isUsbMicConnected) CyanSignal else TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hardwareState.isUsbMicConnected) EmeraldContainer else StudioRack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRefreshDevices)
                        .testTag("usb_status_badge")
                ) {
                    Text(
                        text = if (hardwareState.isUsbMicConnected) "USB-C READY" else "BUILT-IN",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (hardwareState.isUsbMicConnected) EmeraldSafe else TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (hardwareState.isWiredHeadphoneConnected) {
                        Icons.Filled.Headphones
                    } else {
                        Icons.Filled.Warning
                    },
                    contentDescription = "Monitoring output status",
                    tint = if (hardwareState.isWiredHeadphoneConnected) EmeraldSafe else AmberCaution,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Monitor Out: ${hardwareState.outputDeviceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hardwareState.isWiredHeadphoneConnected) EmeraldSafe else AmberCaution,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
