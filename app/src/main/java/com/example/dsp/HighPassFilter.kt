package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4th-order (24 dB/octave) cascaded Butterworth High-Pass Filter at 120 Hz (48 kHz).
 * Eliminates close-mic proximity boominess, breath plosives, and desk handling rumble
 * before Noise Gate, Voice Changer DSP, Live Preview, and WAV Recording.
 */
class HighPassFilter(
    cutoffHz: Double = 120.0,
    sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    // Cascaded 2nd-order Butterworth stages (Q1 = 0.54119610, Q2 = 1.30656296)
    private var b0_1 = 1f; private var b1_1 = 0f; private var b2_1 = 0f
    private var a1_1 = 0f; private var a2_1 = 0f
    private var z1_1 = 0f; private var z2_1 = 0f

    private var b0_2 = 1f; private var b1_2 = 0f; private var b2_2 = 0f
    private var a1_2 = 0f; private var a2_2 = 0f
    private var z1_2 = 0f; private var z2_2 = 0f

    init {
        val w0 = 2.0 * PI * cutoffHz / sampleRateHz.toDouble()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)

        // Stage 1
        val alpha1 = sinW0 / (2.0 * 0.54119610)
        val a0_1 = 1.0 + alpha1
        b0_1 = ((1.0 + cosW0) / 2.0 / a0_1).toFloat()
        b1_1 = (-(1.0 + cosW0) / a0_1).toFloat()
        b2_1 = ((1.0 + cosW0) / 2.0 / a0_1).toFloat()
        a1_1 = (-2.0 * cosW0 / a0_1).toFloat()
        a2_1 = ((1.0 - alpha1) / a0_1).toFloat()

        // Stage 2
        val alpha2 = sinW0 / (2.0 * 1.30656296)
        val a0_2 = 1.0 + alpha2
        b0_2 = ((1.0 + cosW0) / 2.0 / a0_2).toFloat()
        b1_2 = (-(1.0 + cosW0) / a0_2).toFloat()
        b2_2 = ((1.0 + cosW0) / 2.0 / a0_2).toFloat()
        a1_2 = (-2.0 * cosW0 / a0_2).toFloat()
        a2_2 = ((1.0 - alpha2) / a0_2).toFloat()
    }

    fun reset() {
        z1_1 = 0f; z2_1 = 0f
        z1_2 = 0f; z2_2 = 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        for (i in 0 until count) {
            val x = buffer[i]
            val y1 = b0_1 * x + z1_1
            z1_1 = b1_1 * x - a1_1 * y1 + z2_1
            z2_1 = b2_1 * x - a2_1 * y1

            val y2 = b0_2 * y1 + z1_2
            z1_2 = b1_2 * y1 - a1_2 * y2 + z2_2
            z2_2 = b2_2 * y1 - a2_2 * y2

            buffer[i] = y2
        }
    }
}
