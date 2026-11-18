package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class NoiseGateConfig(
    val enabled: Boolean = true,
    val thresholdDb: Float = DEFAULT_THRESHOLD_DB,
    val attackMs: Float = DEFAULT_ATTACK_MS,
    val releaseMs: Float = DEFAULT_RELEASE_MS
) {
    companion object {
        const val MIN_THRESHOLD_DB = -70f
        const val MAX_THRESHOLD_DB = -10f
        const val STEP_THRESHOLD_DB = 1f
        const val DEFAULT_THRESHOLD_DB = -42f

        const val MIN_ATTACK_MS = 0.5f
        const val MAX_ATTACK_MS = 50f
        const val STEP_ATTACK_MS = 0.5f
        const val DEFAULT_ATTACK_MS = 5f

        const val MIN_RELEASE_MS = 20f
        const val MAX_RELEASE_MS = 600f
        const val STEP_RELEASE_MS = 10f
        const val DEFAULT_RELEASE_MS = 140f
    }
}

/**
 * Zero-allocation real-time Noise Gate DSP processor at 48,000 Hz.
 * Features hysteresis (-3 dB close margin) to prevent speech-tail chattering,
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

    @Volatile
    var lastIsGateOpen: Boolean = true
        private set

    @Volatile
    var lastGainReductionDb: Float = 0f
        private set

    // Fast envelope follower constants (~2 ms rise, ~25 ms fall for detector)
    private val detectorAttackCoeff = exp(-1.0 / (0.002 * sampleRateHz)).toFloat()
    private val detectorReleaseCoeff = exp(-1.0 / (0.025 * sampleRateHz)).toFloat()

    // Soft closed floor (-60 dB attenuation instead of hard digital mute for natural room feel)
    private val closedFloorGain = 0.001f

    fun updateConfig(newConfig: NoiseGateConfig) {
        config = newConfig.copy(
            thresholdDb = newConfig.thresholdDb.coerceIn(
                NoiseGateConfig.MIN_THRESHOLD_DB,
                NoiseGateConfig.MAX_THRESHOLD_DB
            ),
            attackMs = newConfig.attackMs.coerceIn(
                NoiseGateConfig.MIN_ATTACK_MS,
                NoiseGateConfig.MAX_ATTACK_MS
            ),
            releaseMs = newConfig.releaseMs.coerceIn(
                NoiseGateConfig.MIN_RELEASE_MS,
                NoiseGateConfig.MAX_RELEASE_MS
            )
        )
    }

    fun getConfig(): NoiseGateConfig = config

    fun reset() {
        envelope = 0f
        gateGain = if (config.enabled) closedFloorGain else 1f
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

        val attackSamples = max(1f, (cfg.attackMs * 0.001f) * sampleRateHz)
        val releaseSamples = max(1f, (cfg.releaseMs * 0.001f) * sampleRateHz)
        val gainAttackCoeff = exp(-1.0 / attackSamples).toFloat()
        val gainReleaseCoeff = exp(-1.0 / releaseSamples).toFloat()

        // Smooth bypass fade (~4 ms) so toggling OFF never causes clicks
        val bypassFadeCoeff = exp(-1.0 / (0.004 * sampleRateHz)).toFloat()

        var env = envelope
        var gain = gateGain
        var open = isOpenState

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
                gain = bypassFadeCoeff * gain + (1f - bypassFadeCoeff) * 1f
            } else {
                open = if (open) {
                    env >= closeThresholdLin
                } else {
                    env >= openThresholdLin
                }

                val targetGain = if (open) 1f else closedFloorGain
                gain = if (targetGain > gain) {
                    gainAttackCoeff * gain + (1f - gainAttackCoeff) * targetGain
                } else {
                    gainReleaseCoeff * gain + (1f - gainReleaseCoeff) * targetGain
                }
            }

            buffer[i] = sample * gain
        }

        envelope = env
        gateGain = gain
        isOpenState = open
        lastIsGateOpen = open
        lastGainReductionDb = if (cfg.enabled) {
            (20f * log10(gain.coerceAtLeast(1e-5f))).coerceIn(-60f, 0f)
        } else {
            0f
        }
    }
}
