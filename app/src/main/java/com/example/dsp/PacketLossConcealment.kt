package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.min

/**
 * Real-time Packet Loss Concealment (PLC) processor at 48,000 Hz.
 *
 * Maintains a rolling pitch-history buffer of recent valid voice frames.
 * When a wireless (Bluetooth) or USB audio packet drops / underruns, synthesizes
 * pitch-period-aligned concealed samples with smooth exponential decay and
 * crossfades seamlessly back into live audio on recovery to eliminate clicks,
 * pops, and dropout gaps.
 */
class PacketLossConcealment(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    // Store ~25 ms of recent history (1200 samples @ 48 kHz)
    private val historySize = (sampleRateHz * 25) / 1000
    private val historyRing = FloatArray(historySize)
    private var historyCursor = 0
    private var historyFilled = 0

    // Pitch period search bounds for speech (75 Hz .. 400 Hz)
    private val minPeriodSamples = (sampleRateHz / 400).coerceAtLeast(32)
    private val maxPeriodSamples = (sampleRateHz / 75).coerceAtMost(historySize / 2)

    private var consecutiveLostFrames = 0
    private var recoveryFadeSamplesRemaining = 0
    private var lastConcealedSample = 0f

    @Volatile
    var concealmentEventsCount: Int = 0
        private set

    fun reset() {
        historyRing.fill(0f)
        historyCursor = 0
        historyFilled = 0
        consecutiveLostFrames = 0
        recoveryFadeSamplesRemaining = 0
        lastConcealedSample = 0f
    }

    /**
     * Records a valid incoming audio block and smoothly crossfades if recovering from loss.
     */
    fun recordValidFrame(buffer: FloatArray, count: Int) {
        if (count <= 0) return

        // Smoothly crossfade back from concealed tail to live signal to avoid click
        if (recoveryFadeSamplesRemaining > 0) {
            val fadeLen = min(count, recoveryFadeSamplesRemaining)
            val totalFade = 64f
            for (i in 0 until fadeLen) {
                val alpha = 1f - (recoveryFadeSamplesRemaining / totalFade).coerceIn(0f, 1f)
                buffer[i] = lastConcealedSample * (1f - alpha) + buffer[i] * alpha
                lastConcealedSample *= 0.96f
                recoveryFadeSamplesRemaining--
            }
        }

        consecutiveLostFrames = 0
        for (i in 0 until count) {
            historyRing[historyCursor] = buffer[i]
            historyCursor = (historyCursor + 1) % historySize
            if (historyFilled < historySize) historyFilled++
        }
    }

    /**
     * Generates [count] concealed samples into [output] when a packet drop / underrun occurs.
     */
    fun concealPacket(output: FloatArray, count: Int) {
        if (count <= 0) return
        if (consecutiveLostFrames == 0) {
            concealmentEventsCount++
        }
        consecutiveLostFrames++
        recoveryFadeSamplesRemaining = 64

        if (historyFilled < minPeriodSamples * 2) {
            output.fill(0f, 0, count)
            lastConcealedSample = 0f
            return
        }

        val period = estimatePitchPeriod()
        // Progressive attenuation during prolonged dropouts (~0.82 per block)
        val baseAtten = when {
            consecutiveLostFrames <= 1 -> 0.92f
            consecutiveLostFrames <= 4 -> 0.78f
            consecutiveLostFrames <= 8 -> 0.55f
            else -> 0.25f
        }

        var gain = baseAtten
        val decayPerSample = if (consecutiveLostFrames > 2) 0.997f else 0.9992f

        for (i in 0 until count) {
            val offsetFromHead = period - (i % period)
            var idx = historyCursor - offsetFromHead
            while (idx < 0) idx += historySize
            val sample = historyRing[idx % historySize] * gain
            output[i] = sample
            historyRing[historyCursor] = sample
            historyCursor = (historyCursor + 1) % historySize
            lastConcealedSample = sample
            gain *= decayPerSample
        }
    }

    private fun estimatePitchPeriod(): Int {
        val window = min(minPeriodSamples, historyFilled / 3)
        if (window < 16) return minPeriodSamples

        var bestLag = minPeriodSamples
        var bestScore = Float.NEGATIVE_INFINITY
        val maxLag = min(maxPeriodSamples, historyFilled - window - 1)

        var lag = minPeriodSamples
        while (lag <= maxLag) {
            var corr = 0f
            var energy = 1e-6f
            for (k in 0 until window step 2) {
                val i1 = (historyCursor - 1 - k + historySize) % historySize
                val i2 = (historyCursor - 1 - k - lag + historySize) % historySize
                val s1 = historyRing[i1]
                val s2 = historyRing[i2]
                corr += s1 * s2
                energy += s2 * s2
            }
            val normalized = corr / kotlin.math.sqrt(energy)
            if (normalized > bestScore) {
                bestScore = normalized
                bestLag = lag
            }
            lag += 2
        }
        return bestLag
    }
}
