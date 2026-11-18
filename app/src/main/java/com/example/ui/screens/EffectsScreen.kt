package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.FilterSlope
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.LowPassConfig
import com.example.dsp.NoiseGateConfig
import com.example.dsp.ReverbConfig
import com.example.ui.components.EqualizerResponseGraph
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
import kotlin.math.roundToInt

@Composable
fun EffectsScreen(
    config: EffectsRackConfig,
    onNoiseGateChanged: (NoiseGateConfig) -> Unit,
    onLowPassChanged: (LowPassConfig) -> Unit,
    onHighPassChanged: (HighPassConfig) -> Unit,
    onEqualizerChanged: (EqualizerConfig) -> Unit,
    onReverbChanged: (ReverbConfig) -> Unit,
    onEchoChanged: (EchoConfig) -> Unit,
    onHallChanged: (HallConfig) -> Unit,
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
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("effects_list"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Noise Gate
            run {
                val g = config.noiseGate
                CollapsibleEffectCard(
                    title = "Noise Gate",
                    enabled = g.enabled,
                    testTagPrefix = "fx_noise_gate",
                    onEnabledChanged = { onNoiseGateChanged(g.copy(enabled = it)) },
                    onReset = { onNoiseGateChanged(NoiseGateConfig(enabled = g.enabled)) }
                ) {
                    EffectSliderRow(
                        label = "Mix",
                        valueText = "${g.mixPercent}%",
                        value = g.mixPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onNoiseGateChanged(g.copy(mixPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Threshold",
                        valueText = String.format(Locale.US, "%.1f dB", g.thresholdDb),
                        value = g.thresholdDb,
                        valueRange = NoiseGateConfig.MIN_THRESHOLD_DB..NoiseGateConfig.MAX_THRESHOLD_DB,
                        onValueChange = {
                            val snapped = (it / 0.5f).roundToInt() * 0.5f
                            onNoiseGateChanged(g.copy(thresholdDb = snapped))
                        }
                    )
                    EffectSliderRow(
                        label = "Attack",
                        valueText = String.format(Locale.US, "%.1f ms", g.attackMs),
                        value = g.attackMs,
                        valueRange = NoiseGateConfig.MIN_ATTACK_MS..NoiseGateConfig.MAX_ATTACK_MS,
                        onValueChange = {
                            val snapped = (it / 0.5f).roundToInt() * 0.5f
                            onNoiseGateChanged(g.copy(attackMs = snapped))
                        }
                    )
                    EffectSliderRow(
                        label = "Hold",
                        valueText = "${g.holdMs.roundToInt()} ms",
                        value = g.holdMs,
                        valueRange = NoiseGateConfig.MIN_HOLD_MS..NoiseGateConfig.MAX_HOLD_MS,
                        onValueChange = {
                            val snapped = (it / 5f).roundToInt() * 5f
                            onNoiseGateChanged(g.copy(holdMs = snapped))
                        }
                    )
                    EffectSliderRow(
                        label = "Release",
                        valueText = "${g.releaseMs.roundToInt()} ms",
                        value = g.releaseMs,
                        valueRange = NoiseGateConfig.MIN_RELEASE_MS..NoiseGateConfig.MAX_RELEASE_MS,
                        onValueChange = {
                            val snapped = (it / 10f).roundToInt() * 10f
                            onNoiseGateChanged(g.copy(releaseMs = snapped))
                        }
                    )
                    EffectSliderRow(
                        label = "Range",
                        valueText = String.format(Locale.US, "%.1f dB", g.rangeDb),
                        value = g.rangeDb,
                        valueRange = NoiseGateConfig.MIN_RANGE_DB..NoiseGateConfig.MAX_RANGE_DB,
                        onValueChange = {
                            val snapped = (it / 1f).roundToInt() * 1f
                            onNoiseGateChanged(g.copy(rangeDb = snapped))
                        }
                    )
                }
            }

            // 2. Low-pass
            run {
                val lp = config.lowPass
                CollapsibleEffectCard(
                    title = "Low-pass",
                    enabled = lp.enabled,
                    testTagPrefix = "fx_low_pass",
                    onEnabledChanged = { onLowPassChanged(lp.copy(enabled = it)) },
                    onReset = { onLowPassChanged(LowPassConfig(enabled = lp.enabled)) }
                ) {
                    EffectSliderRow(
                        label = "Mix",
                        valueText = "${lp.mixPercent}%",
                        value = lp.mixPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onLowPassChanged(lp.copy(mixPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Cutoff",
                        valueText = formatFreqHz(lp.cutoffHz),
                        value = lp.cutoffHz,
                        valueRange = 200f..20000f,
                        onValueChange = {
                            val snapped = (it / 50f).roundToInt() * 50f
                            onLowPassChanged(lp.copy(cutoffHz = snapped))
                        }
                    )
                    EffectSliderRow(
                        label = "Resonance",
                        valueText = String.format(Locale.US, "%.2f", lp.resonance),
                        value = lp.resonance,
                        valueRange = 0.30f..4.00f,
                        onValueChange = {
                            val snapped = (it * 100f).roundToInt() / 100f
                            onLowPassChanged(lp.copy(resonance = snapped))
                        }
                    )
                    EffectSegmentRow(
                        label = "Slope",
                        optionA = FilterSlope.SLOPE_12.label,
                        optionB = FilterSlope.SLOPE_24.label,
                        isBSelected = lp.slope == FilterSlope.SLOPE_24,
                        onSelectB = { is24 ->
                            onLowPassChanged(
                                lp.copy(slope = if (is24) FilterSlope.SLOPE_24 else FilterSlope.SLOPE_12)
                            )
                        }
                    )
                }
            }

            // 3. High-pass
            run {
                val hp = config.highPass
                CollapsibleEffectCard(
                    title = "High-pass",
                    enabled = hp.enabled,
                    testTagPrefix = "fx_high_pass",
                    onEnabledChanged = { onHighPassChanged(hp.copy(enabled = it)) },
                    onReset = { onHighPassChanged(HighPassConfig(enabled = hp.enabled)) }
                ) {
                    EffectSliderRow(
                        label = "Mix",
                        valueText = "${hp.mixPercent}%",
                        value = hp.mixPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onHighPassChanged(hp.copy(mixPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Cutoff",
                        valueText = formatFreqHz(hp.cutoffHz),
                        value = hp.cutoffHz,
                        valueRange = 20f..2000f,
                        onValueChange = {
                            val snapped = (it / 10f).roundToInt() * 10f
                            onHighPassChanged(hp.copy(cutoffHz = snapped))
                        }
                    )
                    EffectSliderRow(
                        label = "Resonance",
                        valueText = String.format(Locale.US, "%.2f", hp.resonance),
                        value = hp.resonance,
                        valueRange = 0.30f..4.00f,
                        onValueChange = {
                            val snapped = (it * 100f).roundToInt() / 100f
                            onHighPassChanged(hp.copy(resonance = snapped))
                        }
                    )
                    EffectSegmentRow(
                        label = "Slope",
                        optionA = FilterSlope.SLOPE_12.label,
                        optionB = FilterSlope.SLOPE_24.label,
                        isBSelected = hp.slope == FilterSlope.SLOPE_24,
                        onSelectB = { is24 ->
                            onHighPassChanged(
                                hp.copy(slope = if (is24) FilterSlope.SLOPE_24 else FilterSlope.SLOPE_12)
                            )
                        }
                    )
                }
            }

            // 4. Equalizer
            run {
                val eq = config.equalizer
                CollapsibleEffectCard(
                    title = "Equalizer",
                    enabled = eq.enabled,
                    testTagPrefix = "fx_equalizer",
                    onEnabledChanged = { onEqualizerChanged(eq.copy(enabled = it)) },
                    onReset = { onEqualizerChanged(EqualizerConfig(enabled = eq.enabled)) }
                ) {
                    EqualizerResponseGraph(config = eq)
                    EffectSliderRow(
                        label = "Mix",
                        valueText = "${eq.mixPercent}%",
                        value = eq.mixPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onEqualizerChanged(eq.copy(mixPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Low Freq",
                        valueText = formatFreqHz(eq.lowFreqHz),
                        value = eq.lowFreqHz,
                        valueRange = 20f..500f,
                        onValueChange = { onEqualizerChanged(eq.copy(lowFreqHz = (it / 5f).roundToInt() * 5f)) }
                    )
                    EffectSliderRow(
                        label = "Low Gain",
                        valueText = formatGainDb(eq.lowGainDb),
                        value = eq.lowGainDb,
                        valueRange = -12f..12f,
                        onValueChange = { onEqualizerChanged(eq.copy(lowGainDb = (it * 2f).roundToInt() / 2f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 1 Freq",
                        valueText = formatFreqHz(eq.mid1FreqHz),
                        value = eq.mid1FreqHz,
                        valueRange = 100f..1500f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid1FreqHz = (it / 10f).roundToInt() * 10f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 1 Gain",
                        valueText = formatGainDb(eq.mid1GainDb),
                        value = eq.mid1GainDb,
                        valueRange = -12f..12f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid1GainDb = (it * 2f).roundToInt() / 2f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 1 Q",
                        valueText = String.format(Locale.US, "%.2f", eq.mid1Q),
                        value = eq.mid1Q,
                        valueRange = 0.10f..10.0f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid1Q = (it * 20f).roundToInt() / 20f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 2 Freq",
                        valueText = formatFreqHz(eq.mid2FreqHz),
                        value = eq.mid2FreqHz,
                        valueRange = 300f..5000f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid2FreqHz = (it / 25f).roundToInt() * 25f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 2 Gain",
                        valueText = formatGainDb(eq.mid2GainDb),
                        value = eq.mid2GainDb,
                        valueRange = -12f..12f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid2GainDb = (it * 2f).roundToInt() / 2f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 2 Q",
                        valueText = String.format(Locale.US, "%.2f", eq.mid2Q),
                        value = eq.mid2Q,
                        valueRange = 0.10f..10.0f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid2Q = (it * 20f).roundToInt() / 20f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 3 Freq",
                        valueText = formatFreqHz(eq.mid3FreqHz),
                        value = eq.mid3FreqHz,
                        valueRange = 1000f..12000f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid3FreqHz = (it / 50f).roundToInt() * 50f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 3 Gain",
                        valueText = formatGainDb(eq.mid3GainDb),
                        value = eq.mid3GainDb,
                        valueRange = -12f..12f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid3GainDb = (it * 2f).roundToInt() / 2f)) }
                    )
                    EffectSliderRow(
                        label = "Mid 3 Q",
                        valueText = String.format(Locale.US, "%.2f", eq.mid3Q),
                        value = eq.mid3Q,
                        valueRange = 0.10f..10.0f,
                        onValueChange = { onEqualizerChanged(eq.copy(mid3Q = (it * 20f).roundToInt() / 20f)) }
                    )
                    EffectSliderRow(
                        label = "High Freq",
                        valueText = formatFreqHz(eq.highFreqHz),
                        value = eq.highFreqHz,
                        valueRange = 2000f..16000f,
                        onValueChange = { onEqualizerChanged(eq.copy(highFreqHz = (it / 100f).roundToInt() * 100f)) }
                    )
                    EffectSliderRow(
                        label = "High Gain",
                        valueText = formatGainDb(eq.highGainDb),
                        value = eq.highGainDb,
                        valueRange = -12f..12f,
                        onValueChange = { onEqualizerChanged(eq.copy(highGainDb = (it * 2f).roundToInt() / 2f)) }
                    )
                }
            }

            // 5. Reverb
            run {
                val rev = config.reverb
                CollapsibleEffectCard(
                    title = "Reverb",
                    enabled = rev.enabled,
                    testTagPrefix = "fx_reverb",
                    onEnabledChanged = { onReverbChanged(rev.copy(enabled = it)) },
                    onReset = { onReverbChanged(ReverbConfig(enabled = rev.enabled)) }
                ) {
                    EffectSliderRow(
                        label = "Mix",
                        valueText = "${rev.mixPercent}%",
                        value = rev.mixPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(mixPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Room Size",
                        valueText = "${rev.roomSizePercent}%",
                        value = rev.roomSizePercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(roomSizePercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Decay Time",
                        valueText = String.format(Locale.US, "%.1f s", rev.decayTimeSec),
                        value = rev.decayTimeSec,
                        valueRange = 0.2f..8.0f,
                        onValueChange = { onReverbChanged(rev.copy(decayTimeSec = (it * 10f).roundToInt() / 10f)) }
                    )
                    EffectSliderRow(
                        label = "Damping",
                        valueText = "${rev.dampingPercent}%",
                        value = rev.dampingPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(dampingPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Wet",
                        valueText = "${rev.wetPercent}%",
                        value = rev.wetPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(wetPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Dry",
                        valueText = "${rev.dryPercent}%",
                        value = rev.dryPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(dryPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Width",
                        valueText = "${rev.widthPercent}%",
                        value = rev.widthPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(widthPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Pre-delay",
                        valueText = String.format(Locale.US, "%.1f ms", rev.preDelayMs),
                        value = rev.preDelayMs,
                        valueRange = 0f..100f,
                        onValueChange = { onReverbChanged(rev.copy(preDelayMs = (it * 2f).roundToInt() / 2f)) }
                    )
                }
            }

            // 6. Echo
            run {
                val ec = config.echo
                CollapsibleEffectCard(
                    title = "Echo",
                    enabled = ec.enabled,
                    testTagPrefix = "fx_echo",
                    onEnabledChanged = { onEchoChanged(ec.copy(enabled = it)) },
                    onReset = { onEchoChanged(EchoConfig(enabled = ec.enabled)) }
                ) {
                    EffectSliderRow(
                        label = "Mix",
                        valueText = "${ec.mixPercent}%",
                        value = ec.mixPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onEchoChanged(ec.copy(mixPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Time",
                        valueText = "${ec.timeMs} ms",
                        value = ec.timeMs.toFloat(),
                        valueRange = 20f..1000f,
                        onValueChange = { onEchoChanged(ec.copy(timeMs = ((it / 5f).roundToInt() * 5))) }
                    )
                    EffectSliderRow(
                        label = "Feedback",
                        valueText = "${ec.feedbackPercent}%",
                        value = ec.feedbackPercent.toFloat(),
                        valueRange = 0f..90f,
                        onValueChange = { onEchoChanged(ec.copy(feedbackPercent = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Echo Level",
                        valueText = "${ec.echoLevelPercent}%",
                        value = ec.echoLevelPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onEchoChanged(ec.copy(echoLevelPercent = it.roundToInt())) }
                    )
                    EffectSegmentRow(
                        label = "Ping-pong",
                        optionA = "Off",
                        optionB = "On",
                        isBSelected = ec.pingPong,
                        onSelectB = { onEchoChanged(ec.copy(pingPong = it)) }
                    )
                    EffectSliderRow(
                        label = "Low Cut",
                        valueText = formatFreqHz(ec.lowCutHz),
                        value = ec.lowCutHz,
                        valueRange = 20f..1000f,
                        onValueChange = { onEchoChanged(ec.copy(lowCutHz = (it / 10f).roundToInt() * 10f)) }
                    )
                }
            }

            // 7. Hall
            run {
                val hall = config.hall
                CollapsibleEffectCard(
                    title = "Hall",
                    enabled = hall.enabled,
                    testTagPrefix = "fx_hall",
                    onEnabledChanged = { onHallChanged(hall.copy(enabled = it)) },
                    onReset = { onHallChanged(HallConfig(enabled = hall.enabled)) }
                ) {
                    EffectSliderRow(
                        label = "Echo",
                        valueText = "${hall.echoAmount}",
                        value = hall.echoAmount.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onHallChanged(hall.copy(echoAmount = it.roundToInt())) }
                    )
                    EffectSliderRow(
                        label = "Reverberation",
                        valueText = "${hall.reverberationAmount}",
                        value = hall.reverberationAmount.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onHallChanged(hall.copy(reverberationAmount = it.roundToInt())) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CollapsibleEffectCard(
    title: String,
    enabled: Boolean,
    testTagPrefix: String,
    onEnabledChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StudioPanel,
        border = BorderStroke(
            1.dp,
            if (enabled) EmeraldSafe.copy(alpha = 0.65f) else StudioBorder
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact header row: Title + Enable toggle (plus Reset icon only when expanded)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (enabled) {
                        IconButton(
                            onClick = onReset,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("${testTagPrefix}_reset")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset $title",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StudioRack,
                            checkedTrackColor = EmeraldSafe
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_switch")
                    )
                }
            }

            if (enabled) {
                HorizontalDivider(color = StudioBorder)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun EffectSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(92.dp)
        )

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
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
            modifier = Modifier.width(74.dp)
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun EffectSegmentRow(
    label: String,
    optionA: String,
    optionB: String,
    isBSelected: Boolean,
    onSelectB: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(92.dp)
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = StudioRack,
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (!isBSelected) EmeraldContainer else StudioRack,
                    border = if (!isBSelected) BorderStroke(1.dp, EmeraldSafe) else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onSelectB(false) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = optionA,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (!isBSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (!isBSelected) EmeraldSafe else TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (isBSelected) EmeraldContainer else StudioRack,
                    border = if (isBSelected) BorderStroke(1.dp, EmeraldSafe) else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onSelectB(true) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = optionB,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isBSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isBSelected) EmeraldSafe else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun formatFreqHz(hz: Float): String {
    return if (hz >= 1000f) {
        String.format(Locale.US, "%.2f kHz", hz / 1000f)
    } else {
        String.format(Locale.US, "%d Hz", hz.roundToInt())
    }
}

private fun formatGainDb(db: Float): String {
    return String.format(Locale.US, "%+.1f dB", db)
}
