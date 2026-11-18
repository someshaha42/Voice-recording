package com.example.audio

import kotlin.math.roundToInt

enum class MonitoringLatencyZone(val label: String) {
    FASTER("Faster"),
    DEFAULT("Default"),
    MORE_DELAY("More Delay")
}

data class MonitoringConfig(
    val livePreviewEnabled: Boolean = false,
    /**
     * Normalized slider position in [-1.0f, +1.0f]:
     * - [-1.0f .. <0.0f]: Faster (minimizes AudioTrack/AudioRecord buffer overhead toward lowest stable burst)
     * - [0.0f]: Default (balanced stable buffer, 0 ms added output delay)
     * - [>0.0f .. +1.0f]: More Delay (adds 0 .. +200 ms calibrated delay exclusively to monitoring output)
     */
    val delaySliderPosition: Float = 0.0f,
    /**
     * Live Voice Preview headphone output volume boost (0% to 500%, default 100%).
     * Applied exclusively to the monitoring output with soft-knee protection limiting.
     */
    val previewVolumePercent: Int = DEFAULT_VOLUME_PERCENT,
    /**
     * Enables Android hardware AcousticEchoCanceler (when available) + feedback protection
     * during Live Voice Preview to prevent speaker crackle and howling feedback.
     */
    val echoCancellationEnabled: Boolean = true
) {
    val zone: MonitoringLatencyZone
        get() = when {
            delaySliderPosition < -0.025f -> MonitoringLatencyZone.FASTER
            delaySliderPosition > 0.025f -> MonitoringLatencyZone.MORE_DELAY
            else -> MonitoringLatencyZone.DEFAULT
        }

    val addedOutputDelayMs: Int
        get() = if (delaySliderPosition > 0.025f) {
            (delaySliderPosition.coerceIn(0f, 1f) * MAX_ADDED_DELAY_MS).roundToInt()
        } else {
            0
        }

    /**
     * Buffer multiplier for AudioTrack/AudioRecord sizing:
     * - Default (0.0f): 1.25x hardware burst buffer for low-latency live preview
     * - Faster (-1.0f): 1.0x minimum stable hardware burst buffer
     */
    val bufferSizeMultiplier: Float
        get() = if (delaySliderPosition < 0f) {
            (1.25f + delaySliderPosition.coerceIn(-1f, 0f) * 0.25f).coerceIn(1.0f, 1.25f)
        } else {
            1.25f
        }

    companion object {
        const val MIN_SLIDER = -1.0f
        const val MAX_SLIDER = 1.0f
        const val STEP_SLIDER = 0.05f
        const val DEFAULT_SLIDER = 0.0f
        const val MAX_ADDED_DELAY_MS = 200

        const val MIN_VOLUME_PERCENT = 0
        const val MAX_VOLUME_PERCENT = 500
        const val STEP_VOLUME_PERCENT = 10
        const val DEFAULT_VOLUME_PERCENT = 100
    }
}

/**
 * Circular ring-buffer delay line applied ONLY to the Live Monitoring Output path.
 * Never touches the WAV recording branch, preserving exact recording timing.
 * Uses smooth crossfaded delay pointer slewing to avoid clicks when dragging the slider.
 */
class MonitoringDelayLine(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    private val maxDelaySamples = (sampleRateHz * (MonitoringConfig.MAX_ADDED_DELAY_MS + 20)) / 1000
    private val ringBuffer = FloatArray(maxDelaySamples)
    private var writeIndex = 0
    private var currentDelaySamples = 0f

    @Volatile
    private var targetDelaySamples = 0f

    fun setDelayMs(delayMs: Int) {
        val clampedMs = delayMs.coerceIn(0, MonitoringConfig.MAX_ADDED_DELAY_MS)
        targetDelaySamples = (clampedMs * sampleRateHz) / 1000f
    }

    fun reset() {
        ringBuffer.fill(0f)
        writeIndex = 0
        currentDelaySamples = targetDelaySamples
    }

    fun process(input: FloatArray, count: Int, output: FloatArray) {
        if (count <= 0) return
        val target = targetDelaySamples
        val slewAlpha = 0.004f

        for (i in 0 until count) {
            ringBuffer[writeIndex] = input[i]

            currentDelaySamples += slewAlpha * (target - currentDelaySamples)
            val delay = currentDelaySamples.coerceIn(0f, (maxDelaySamples - 2).toFloat())

            if (delay < 0.5f) {
                output[i] = input[i]
            } else {
                var readPos = writeIndex - delay
                if (readPos < 0f) readPos += maxDelaySamples
                val idx0 = readPos.toInt() % maxDelaySamples
                val idx1 = (idx0 + 1) % maxDelaySamples
                val frac = readPos - readPos.toInt()
                output[i] = ringBuffer[idx0] * (1f - frac) + ringBuffer[idx1] * frac
            }

            writeIndex++
            if (writeIndex >= maxDelaySamples) writeIndex = 0
        }
    }
}
