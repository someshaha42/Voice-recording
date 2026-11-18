package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Real-time sample rate converter targeting 48,000 Hz mono output.
 * Zero-overhead pass-through when inputSampleRateHz == 48,000.
 * Uses a 2nd-order anti-aliasing Butterworth low-pass filter when downsampling
 * and Catmull-Rom cubic Hermite interpolation for crisp voice fidelity.
 */
class LinearResampler(
    private val inputSampleRateHz: Int,
    private val outputSampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    private val isPassThrough = inputSampleRateHz == outputSampleRateHz
    private val ratio: Double = inputSampleRateHz.toDouble() / outputSampleRateHz.toDouble()

    // Ring history for cubic interpolation (last 4 input samples)
    private var s0 = 0f
    private var s1 = 0f
    private var s2 = 0f
    private var s3 = 0f
    private var phase = 0.0

    // Optional anti-aliasing lowpass biquad state when downsampling (inputRate > outputRate)
    private val needsLowPass = inputSampleRateHz > outputSampleRateHz
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    init {
        if (needsLowPass) {
            val cutoff = (outputSampleRateHz * 0.43).coerceAtMost(20_500.0)
            val w0 = 2.0 * PI * cutoff / inputSampleRateHz.toDouble()
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * 0.70710678)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
            b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }
    }

    fun reset() {
        s0 = 0f
        s1 = 0f
        s2 = 0f
        s3 = 0f
        phase = 0.0
        z1 = 0f
        z2 = 0f
    }

    /**
     * Resamples [inputCount] samples from [input] into [output].
     * Returns the exact number of 48 kHz samples written to [output].
     */
    fun process(input: FloatArray, inputCount: Int, output: FloatArray): Int {
        if (inputCount <= 0) return 0
        if (isPassThrough) {
            val count = inputCount.coerceAtMost(output.size)
            System.arraycopy(input, 0, output, 0, count)
            return count
        }

        var outIndex = 0
        var inIndex = 0

        while (inIndex < inputCount && outIndex < output.size) {
            // Advance input samples until phase < 1.0
            while (phase >= 1.0 && inIndex < inputCount) {
                val raw = input[inIndex++]
                val filtered = if (needsLowPass) {
                    val y = b0 * raw + z1
                    z1 = b1 * raw - a1 * y + z2
                    z2 = b2 * raw - a2 * y
                    y
                } else {
                    raw
                }
                s0 = s1
                s1 = s2
                s2 = s3
                s3 = filtered
                phase -= 1.0
            }

            if (phase < 1.0 && outIndex < output.size) {
                val t = phase.toFloat()
                // Catmull-Rom cubic interpolation between s1 and s2
                val c0 = s1
                val c1 = 0.5f * (s2 - s0)
                val c2 = s0 - 2.5f * s1 + 2.0f * s2 - 0.5f * s3
                val c3 = 0.5f * (s3 - s0) + 1.5f * (s1 - s2)
                output[outIndex++] = ((c3 * t + c2) * t + c1) * t + c0
                phase += ratio
            }
        }

        // Consume any trailing input samples if output buffer filled cleanly
        while (inIndex < inputCount) {
            val raw = input[inIndex++]
            val filtered = if (needsLowPass) {
                val y = b0 * raw + z1
                z1 = b1 * raw - a1 * y + z2
                z2 = b2 * raw - a2 * y
                y
            } else {
                raw
            }
            s0 = s1
            s1 = s2
            s2 = s3
            s3 = filtered
            phase -= 1.0
            while (phase < 1.0 && outIndex < output.size) {
                val t = phase.toFloat().coerceIn(0f, 1f)
                val c0 = s1
                val c1 = 0.5f * (s2 - s0)
                val c2 = s0 - 2.5f * s1 + 2.0f * s2 - 0.5f * s3
                val c3 = 0.5f * (s3 - s0) + 1.5f * (s1 - s2)
                output[outIndex++] = ((c3 * t + c2) * t + c1) * t + c0
                phase += ratio
            }
        }

        // Keep phase bounded if slightly negative
        val floorPhase = floor(phase)
        if (floorPhase < 0.0) {
            phase = 0.0
        }
        return outIndex
    }
}
