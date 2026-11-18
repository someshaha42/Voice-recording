package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class NoiseGateConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = DEFAULT_MIX_PCT,
    val thresholdDb: Float = DEFAULT_THRESHOLD_DB,
    val attackMs: Float = DEFAULT_ATTACK_MS,
    val holdMs: Float = DEFAULT_HOLD_MS,
    val releaseMs: Float = DEFAULT_RELEASE_MS,
    val rangeDb: Float = DEFAULT_RANGE_DB
) {
    companion object {
        const val MIN_MIX_PCT = 0
        const val MAX_MIX_PCT = 100
        const val STEP_MIX_PCT = 1
        const val DEFAULT_MIX_PCT = 100

        const val MIN_THRESHOLD_DB = -70f
        const val MAX_THRESHOLD_DB = -10f
        const val STEP_THRESHOLD_DB = 1f
        const val DEFAULT_THRESHOLD_DB = -50f

        const val MIN_ATTACK_MS = 0.5f
        const val MAX_ATTACK_MS = 50f
        const val STEP_ATTACK_MS = 0.5f
        const val DEFAULT_ATTACK_MS = 5f

        const val MIN_HOLD_MS = 0f
        const val MAX_HOLD_MS = 500f
        const val STEP_HOLD_MS = 5f
        const val DEFAULT_HOLD_MS = 50f

        const val MIN_RELEASE_MS = 20f
        const val MAX_RELEASE_MS = 600f
        const val STEP_RELEASE_MS = 10f
        const val DEFAULT_RELEASE_MS = 100f

        const val MIN_RANGE_DB = 0f
        const val MAX_RANGE_DB = 80f
        const val STEP_RANGE_DB = 1f
        const val DEFAULT_RANGE_DB = 80f
    }
}

/**
 * Zero-allocation real-time Noise Gate DSP processor at 48,000 Hz.
 * Features Mix, Threshold, Attack, Hold, Release, Range, -3 dB hysteresis,
 * smooth gain slewing to eliminate clicks/pops, and instant clean bypass when disabled.
 */
class NoiseGateProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    @Volatile
    private var config = NoiseGateConfig()

    private var envelope = 0f
    private var gateGain = 1f
    private var isOpenState = false
    private var holdSamplesRemaining = 0

    @Volatile
    var lastIsGateOpen: Boolean = true
        private set

    @Volatile
    var lastGainReductionDb: Float = 0f
        private set

    // Fast envelope follower constants (~2 ms rise, ~25 ms fall for detector)
    private val detectorAttackCoeff = exp(-1.0 / (0.002 * sampleRateHz)).toFloat()
    private val detectorReleaseCoeff = exp(-1.0 / (0.025 * sampleRateHz)).toFloat()

    fun updateConfig(newConfig: NoiseGateConfig) {
        config = newConfig.copy(
            mixPercent = newConfig.mixPercent.coerceIn(
                NoiseGateConfig.MIN_MIX_PCT,
                NoiseGateConfig.MAX_MIX_PCT
            ),
            thresholdDb = newConfig.thresholdDb.coerceIn(
                NoiseGateConfig.MIN_THRESHOLD_DB,
                NoiseGateConfig.MAX_THRESHOLD_DB
            ),
            attackMs = newConfig.attackMs.coerceIn(
                NoiseGateConfig.MIN_ATTACK_MS,
                NoiseGateConfig.MAX_ATTACK_MS
            ),
            holdMs = newConfig.holdMs.coerceIn(
                NoiseGateConfig.MIN_HOLD_MS,
                NoiseGateConfig.MAX_HOLD_MS
            ),
            releaseMs = newConfig.releaseMs.coerceIn(
                NoiseGateConfig.MIN_RELEASE_MS,
                NoiseGateConfig.MAX_RELEASE_MS
            ),
            rangeDb = newConfig.rangeDb.coerceIn(
                NoiseGateConfig.MIN_RANGE_DB,
                NoiseGateConfig.MAX_RANGE_DB
            )
        )
    }

    fun getConfig(): NoiseGateConfig = config

    fun reset() {
        envelope = 0f
        holdSamplesRemaining = 0
        val floorGain = 10f.pow(-config.rangeDb / 20f).coerceIn(0.0001f, 1f)
        gateGain = if (config.enabled) floorGain else 1f
        isOpenState = !config.enabled
        lastIsGateOpen = isOpenState
        lastGainReductionDb = 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config

        val openThresholdLin = 10f.pow(cfg.thresholdDb / 20f)
        // 3 dB hysteresis prevents rapid chattering around threshold
        val closeThresholdLin = 10f.pow((cfg.thresholdDb - 3f) / 20f)
        val floorGain = 10f.pow(-cfg.rangeDb / 20f).coerceIn(0.0001f, 1f)
        val mix = cfg.mixPercent / 100f

        val attackSamples = max(1f, (cfg.attackMs * 0.001f) * sampleRateHz)
        val holdTotalSamples = ((cfg.holdMs * 0.001f) * sampleRateHz).toInt().coerceAtLeast(0)
        val releaseSamples = max(1f, (cfg.releaseMs * 0.001f) * sampleRateHz)
        val gainAttackCoeff = exp(-1.0 / attackSamples).toFloat()
        val gainReleaseCoeff = exp(-1.0 / releaseSamples).toFloat()

        // Smooth bypass fade (~4 ms) so toggling OFF never causes clicks
        val bypassFadeCoeff = exp(-1.0 / (0.004 * sampleRateHz)).toFloat()

        var env = envelope
        var gain = gateGain
        var open = isOpenState
        var holdRem = holdSamplesRemaining

        for (i in 0 until count) {
            val sample = buffer[i]
            val absSample = abs(sample)

            env = if (absSample > env) {
                detectorAttackCoeff * env + (1f - detectorAttackCoeff) * absSample
            } else {
                detectorReleaseCoeff * env + (1f - detectorReleaseCoeff) * absSample
            }

            if (!cfg.enabled) {
                open = true
                holdRem = 0
                gain = bypassFadeCoeff * gain + (1f - bypassFadeCoeff) * 1f
                buffer[i] = sample * gain
            } else {
                if (open) {
                    if (env >= closeThresholdLin) {
                        holdRem = holdTotalSamples
                    } else if (holdRem > 0) {
                        holdRem--
                    } else {
                        open = false
                    }
                } else {
                    if (env >= openThresholdLin) {
                        open = true
                        holdRem = holdTotalSamples
                    }
                }

                val targetGain = if (open) 1f else floorGain
                gain = if (targetGain > gain) {
                    gainAttackCoeff * gain + (1f - gainAttackCoeff) * targetGain
                } else {
                    gainReleaseCoeff * gain + (1f - gainReleaseCoeff) * targetGain
                }

                val gated = sample * gain
                buffer[i] = sample * (1f - mix) + gated * mix
            }
        }

        envelope = env
        gateGain = gain
        isOpenState = open
        holdSamplesRemaining = holdRem
        lastIsGateOpen = open
        lastGainReductionDb = if (cfg.enabled) {
            val effectiveGain = (1f - mix) + gain * mix
            (20f * log10(effectiveGain.coerceAtLeast(1e-5f))).coerceIn(-80f, 0f)
        } else {
            0f
        }
    }
}
