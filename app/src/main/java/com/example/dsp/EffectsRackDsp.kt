package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.tanh

enum class FilterSlope(val dbPerOct: Int, val label: String) {
    SLOPE_12(12, "12 dB/oct"),
    SLOPE_24(24, "24 dB/oct")
}

data class LowPassConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = 100,
    val cutoffHz: Float = 5000f,
    val resonance: Float = 0.71f,
    val slope: FilterSlope = FilterSlope.SLOPE_12
)

data class HighPassConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = 100,
    val cutoffHz: Float = 200f,
    val resonance: Float = 0.71f,
    val slope: FilterSlope = FilterSlope.SLOPE_12
)

data class EqualizerConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = 100,
    val lowFreqHz: Float = 120f,
    val lowGainDb: Float = 0f,
    val mid1FreqHz: Float = 400f,
    val mid1GainDb: Float = 0f,
    val mid1Q: Float = 0.71f,
    val mid2FreqHz: Float = 1500f,
    val mid2GainDb: Float = 0f,
    val mid2Q: Float = 1.00f,
    val mid3FreqHz: Float = 5000f,
    val mid3GainDb: Float = 0f,
    val mid3Q: Float = 1.00f,
    val highFreqHz: Float = 10000f,
    val highGainDb: Float = 0f
)

data class ReverbConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = 81,
    val roomSizePercent: Int = 50,
    val dampingPercent: Int = 50,
    val wetPercent: Int = 30,
    val dryPercent: Int = 100,
    val widthPercent: Int = 100,
    val preDelayMs: Float = 0f
)

data class EchoConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = 40,
    val timeMs: Int = 250,
    val feedbackPercent: Int = 35,
    val echoLevelPercent: Int = 30,
    val pingPong: Boolean = false,
    val lowCutHz: Float = 80f
)

data class HallConfig(
    val enabled: Boolean = false,
    val echoAmount: Int = 55,
    val reverberationAmount: Int = 71
)

data class EffectsRackConfig(
    val noiseGate: NoiseGateConfig = NoiseGateConfig(),
    val lowPass: LowPassConfig = LowPassConfig(),
    val highPass: HighPassConfig = HighPassConfig(),
    val equalizer: EqualizerConfig = EqualizerConfig(),
    val reverb: ReverbConfig = ReverbConfig(),
    val echo: EchoConfig = EchoConfig(),
    val hall: HallConfig = HallConfig()
) {
    val activeEffectCount: Int
        get() = listOf(
            noiseGate.enabled,
            lowPass.enabled,
            highPass.enabled,
            equalizer.enabled,
            reverb.enabled,
            echo.enabled,
            hall.enabled
        ).count { it }
}

/**
 * Zero-allocation real-time Effects Rack operating at 48,000 Hz.
 * Runs Noise Gate, Low-pass, High-pass, 5-Band Equalizer, Echo, Reverb, and Hall.
 * Note: Noise Gate runs before Voice Changer so noise is gated prior to pitch tracking;
 * post-effects (Low-pass, High-pass, Equalizer, Echo, Reverb, Hall) run after Voice Changer.
 */
class EffectsRackProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    val noiseGate = NoiseGateProcessor(sampleRateHz)
    private val lowPass = LowPassEffect(sampleRateHz)
    private val highPass = HighPassEffect(sampleRateHz)
    private val equalizer = EqualizerEffect(sampleRateHz)
    private val echo = EchoEffect(sampleRateHz)
    private val reverb = ReverbEffect(sampleRateHz)
    private val hall = HallEffect(sampleRateHz)

    @Volatile
    private var config = EffectsRackConfig()

    fun updateConfig(newConfig: EffectsRackConfig) {
        config = newConfig
        noiseGate.updateConfig(newConfig.noiseGate)
        lowPass.updateConfig(newConfig.lowPass)
        highPass.updateConfig(newConfig.highPass)
        equalizer.updateConfig(newConfig.equalizer)
        echo.updateConfig(newConfig.echo)
        reverb.updateConfig(newConfig.reverb)
        hall.updateConfig(newConfig.hall)
    }

    fun getConfig(): EffectsRackConfig = config

    fun reset() {
        noiseGate.reset()
        lowPass.reset()
        highPass.reset()
        equalizer.reset()
        echo.reset()
        reverb.reset()
        hall.reset()
    }

    fun processPreVoiceChanger(buffer: FloatArray, count: Int) {
        noiseGate.processInPlace(buffer, count)
    }

    fun processPostVoiceChanger(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        highPass.processInPlace(buffer, count)
        lowPass.processInPlace(buffer, count)
        equalizer.processInPlace(buffer, count)
        echo.processInPlace(buffer, count)
        reverb.processInPlace(buffer, count)
        hall.processInPlace(buffer, count)
    }
}

internal class LowPassEffect(private val sampleRateHz: Int) {
    @Volatile
    private var config = LowPassConfig()
    private val stage1 = BiquadFilter()
    private val stage2 = BiquadFilter()
    private var lastCutoff = -1f
    private var lastRes = -1f
    private var smoothActive = 0f

    fun updateConfig(cfg: LowPassConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            cutoffHz = cfg.cutoffHz.coerceIn(200f, 20000f),
            resonance = cfg.resonance.coerceIn(0.2f, 4.0f)
        )
    }

    fun reset() {
        stage1.reset()
        stage2.reset()
        lastCutoff = -1f
        lastRes = -1f
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }
        if (abs(cfg.cutoffHz - lastCutoff) > 1f || abs(cfg.resonance - lastRes) > 0.01f) {
            lastCutoff = cfg.cutoffHz
            lastRes = cfg.resonance
            stage1.setLowPass(sampleRateHz, cfg.cutoffHz.toDouble(), cfg.resonance.toDouble())
            stage2.setLowPass(sampleRateHz, cfg.cutoffHz.toDouble(), cfg.resonance.toDouble())
        }
        val targetActive = if (cfg.enabled) 1f else 0f
        val mix = cfg.mixPercent / 100f
        val is24Db = cfg.slope == FilterSlope.SLOPE_24

        for (i in 0 until count) {
            smoothActive += 0.008f * (targetActive - smoothActive)
            val dry = buffer[i]
            var wet = stage1.process(dry)
            if (is24Db) {
                wet = stage2.process(wet)
            }
            val effMix = mix * smoothActive
            buffer[i] = dry * (1f - effMix) + wet * effMix
        }
    }
}

internal class HighPassEffect(private val sampleRateHz: Int) {
    @Volatile
    private var config = HighPassConfig()
    private val stage1 = BiquadFilter()
    private val stage2 = BiquadFilter()
    private var lastCutoff = -1f
    private var lastRes = -1f
    private var smoothActive = 0f

    fun updateConfig(cfg: HighPassConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            cutoffHz = cfg.cutoffHz.coerceIn(20f, 2000f),
            resonance = cfg.resonance.coerceIn(0.2f, 4.0f)
        )
    }

    fun reset() {
        stage1.reset()
        stage2.reset()
        lastCutoff = -1f
        lastRes = -1f
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }
        if (abs(cfg.cutoffHz - lastCutoff) > 0.5f || abs(cfg.resonance - lastRes) > 0.01f) {
            lastCutoff = cfg.cutoffHz
            lastRes = cfg.resonance
            stage1.setHighPass(sampleRateHz, cfg.cutoffHz.toDouble(), cfg.resonance.toDouble())
            stage2.setHighPass(sampleRateHz, cfg.cutoffHz.toDouble(), cfg.resonance.toDouble())
        }
        val targetActive = if (cfg.enabled) 1f else 0f
        val mix = cfg.mixPercent / 100f
        val is24Db = cfg.slope == FilterSlope.SLOPE_24

        for (i in 0 until count) {
            smoothActive += 0.008f * (targetActive - smoothActive)
            val dry = buffer[i]
            var wet = stage1.process(dry)
            if (is24Db) {
                wet = stage2.process(wet)
            }
            val effMix = mix * smoothActive
            buffer[i] = dry * (1f - effMix) + wet * effMix
        }
    }
}

internal class EqualizerEffect(private val sampleRateHz: Int) {
    @Volatile
    private var config = EqualizerConfig()
    private val lowBand = BiquadFilter()
    private val mid1Band = BiquadFilter()
    private val mid2Band = BiquadFilter()
    private val mid3Band = BiquadFilter()
    private val highBand = BiquadFilter()
    private var lastApplied: EqualizerConfig? = null
    private var smoothActive = 0f

    fun updateConfig(cfg: EqualizerConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            lowFreqHz = cfg.lowFreqHz.coerceIn(20f, 500f),
            lowGainDb = cfg.lowGainDb.coerceIn(-12f, 12f),
            mid1FreqHz = cfg.mid1FreqHz.coerceIn(100f, 1500f),
            mid1GainDb = cfg.mid1GainDb.coerceIn(-12f, 12f),
            mid1Q = cfg.mid1Q.coerceIn(0.1f, 10f),
            mid2FreqHz = cfg.mid2FreqHz.coerceIn(300f, 5000f),
            mid2GainDb = cfg.mid2GainDb.coerceIn(-12f, 12f),
            mid2Q = cfg.mid2Q.coerceIn(0.1f, 10f),
            mid3FreqHz = cfg.mid3FreqHz.coerceIn(1000f, 12000f),
            mid3GainDb = cfg.mid3GainDb.coerceIn(-12f, 12f),
            mid3Q = cfg.mid3Q.coerceIn(0.1f, 10f),
            highFreqHz = cfg.highFreqHz.coerceIn(2000f, 16000f),
            highGainDb = cfg.highGainDb.coerceIn(-12f, 12f)
        )
    }

    fun reset() {
        lowBand.reset()
        mid1Band.reset()
        mid2Band.reset()
        mid3Band.reset()
        highBand.reset()
        lastApplied = null
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }
        if (cfg != lastApplied) {
            lastApplied = cfg
            lowBand.setLowShelf(sampleRateHz, cfg.lowFreqHz.toDouble(), cfg.lowGainDb.toDouble())
            mid1Band.setPeaking(sampleRateHz, cfg.mid1FreqHz.toDouble(), cfg.mid1Q.toDouble(), cfg.mid1GainDb.toDouble())
            mid2Band.setPeaking(sampleRateHz, cfg.mid2FreqHz.toDouble(), cfg.mid2Q.toDouble(), cfg.mid2GainDb.toDouble())
            mid3Band.setPeaking(sampleRateHz, cfg.mid3FreqHz.toDouble(), cfg.mid3Q.toDouble(), cfg.mid3GainDb.toDouble())
            highBand.setHighShelf(sampleRateHz, cfg.highFreqHz.toDouble(), cfg.highGainDb.toDouble())
        }
        val targetActive = if (cfg.enabled) 1f else 0f
        val mix = cfg.mixPercent / 100f

        for (i in 0 until count) {
            smoothActive += 0.008f * (targetActive - smoothActive)
            val dry = buffer[i]
            var wet = lowBand.process(dry)
            wet = mid1Band.process(wet)
            wet = mid2Band.process(wet)
            wet = mid3Band.process(wet)
            wet = highBand.process(wet)
            val effMix = mix * smoothActive
            val out = dry * (1f - effMix) + wet * effMix
            buffer[i] = softClip(out)
        }
    }
}

internal class EchoEffect(private val sampleRateHz: Int) {
    companion object {
        private const val MAX_DELAY_MS = 1050
    }

    @Volatile
    private var config = EchoConfig()
    private val maxSamples = (sampleRateHz * MAX_DELAY_MS) / 1000
    private val delayRing = FloatArray(maxSamples)
    private var writePos = 0
    private var currentDelaySamples = (sampleRateHz * 0.25f)
    private var smoothActive = 0f

    private val lowCutFilter = BiquadFilter()
    private val pingPongAltFilter = BiquadFilter()
    private var lastLowCut = -1f
    private var tapCounter = 0

    init {
        pingPongAltFilter.setLowPass(sampleRateHz, 3400.0, 0.707)
    }

    fun updateConfig(cfg: EchoConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            timeMs = cfg.timeMs.coerceIn(20, 1000),
            feedbackPercent = cfg.feedbackPercent.coerceIn(0, 90),
            echoLevelPercent = cfg.echoLevelPercent.coerceIn(0, 100),
            lowCutHz = cfg.lowCutHz.coerceIn(20f, 1000f)
        )
    }

    fun reset() {
        delayRing.fill(0f)
        writePos = 0
        currentDelaySamples = (config.timeMs * sampleRateHz) / 1000f
        lowCutFilter.reset()
        pingPongAltFilter.reset()
        lastLowCut = -1f
        tapCounter = 0
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }
        if (abs(cfg.lowCutHz - lastLowCut) > 1f) {
            lastLowCut = cfg.lowCutHz
            lowCutFilter.setHighPass(sampleRateHz, cfg.lowCutHz.toDouble(), 0.707)
        }

        val targetActive = if (cfg.enabled) 1f else 0f
        val targetDelaySamples = ((cfg.timeMs * sampleRateHz) / 1000f)
            .coerceIn(24f, (maxSamples - 4).toFloat())
        val feedback = (cfg.feedbackPercent / 100f).coerceIn(0f, 0.90f)
        val echoGain = cfg.echoLevelPercent / 100f
        val mix = cfg.mixPercent / 100f

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            currentDelaySamples += 0.002f * (targetDelaySamples - currentDelaySamples)

            val dry = buffer[i]
            var readPos = writePos - currentDelaySamples
            if (readPos < 0f) readPos += maxSamples
            val idx0 = readPos.toInt() % maxSamples
            val idx1 = (idx0 + 1) % maxSamples
            val frac = readPos - floor(readPos)
            val delayed = delayRing[idx0] * (1f - frac) + delayRing[idx1] * frac

            // Optional Ping-Pong stereo-feel alternating tap modulation in mono
            val tapProcessed = if (cfg.pingPong) {
                val secondaryPos = (writePos - (currentDelaySamples * 0.5f).toInt() + maxSamples) % maxSamples
                val softBounce = pingPongAltFilter.process(delayRing[secondaryPos]) * 0.45f
                delayed * 0.75f + softBounce
            } else {
                delayed
            }

            val fbSignal = lowCutFilter.process(delayed * feedback)
            delayRing[writePos] = if (cfg.enabled) {
                softClip(dry + fbSignal)
            } else {
                fbSignal * 0.95f // smoothly decay tail on disable
            }

            writePos++
            if (writePos >= maxSamples) writePos = 0

            val wet = dry + tapProcessed * echoGain
            val effMix = mix * smoothActive
            buffer[i] = softClip(dry * (1f - effMix) + wet * effMix)
        }
    }
}

/**
 * Schroeder-Moorer Freeverb-style zero-allocation real-time Reverb at 48,000 Hz.
 */
internal class ReverbEffect(private val sampleRateHz: Int) {
    @Volatile
    private var config = ReverbConfig()

    // Scale classic 44.1 kHz Freeverb comb/allpass lengths to 48 kHz
    private val combTunings = intArrayOf(1214, 1293, 1390, 1478, 1546, 1622, 1694, 1765)
    private val allpassTunings = intArrayOf(605, 480, 371, 245)

    private val combs = Array(combTunings.size) { CombFilter(combTunings[it]) }
    private val combRight = Array(4) { CombFilter(combTunings[it] + 25) } // width Haas taps
    private val allpasses = Array(allpassTunings.size) { AllpassFilter(allpassTunings[it]) }

    private val maxPreDelaySamples = (sampleRateHz * 110) / 1000
    private val preDelayRing = FloatArray(maxPreDelaySamples)
    private var preDelayWrite = 0
    private var smoothActive = 0f

    fun updateConfig(cfg: ReverbConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            roomSizePercent = cfg.roomSizePercent.coerceIn(0, 100),
            dampingPercent = cfg.dampingPercent.coerceIn(0, 100),
            wetPercent = cfg.wetPercent.coerceIn(0, 100),
            dryPercent = cfg.dryPercent.coerceIn(0, 100),
            widthPercent = cfg.widthPercent.coerceIn(0, 100),
            preDelayMs = cfg.preDelayMs.coerceIn(0f, 100f)
        )
    }

    fun reset() {
        combs.forEach { it.reset() }
        combRight.forEach { it.reset() }
        allpasses.forEach { it.reset() }
        preDelayRing.fill(0f)
        preDelayWrite = 0
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }

        val targetActive = if (cfg.enabled) 1f else 0f
        val feedback = 0.68f + (cfg.roomSizePercent / 100f) * 0.28f
        val damp = (cfg.dampingPercent / 100f) * 0.45f
        val wetGain = (cfg.wetPercent / 100f) * 0.35f
        val dryGain = cfg.dryPercent / 100f
        val width = (cfg.widthPercent / 100f) * 0.35f
        val mix = cfg.mixPercent / 100f
        val preDelaySamples = ((cfg.preDelayMs * sampleRateHz) / 1000f)
            .roundToInt().coerceIn(0, maxPreDelaySamples - 2)

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            val dry = buffer[i]

            preDelayRing[preDelayWrite] = if (cfg.enabled) dry else 0f
            val preRead = (preDelayWrite - preDelaySamples + maxPreDelaySamples) % maxPreDelaySamples
            val delayedIn = preDelayRing[preRead]
            preDelayWrite++
            if (preDelayWrite >= maxPreDelaySamples) preDelayWrite = 0

            val input = delayedIn * 0.18f
            var out = 0f
            for (c in combs.indices) {
                out += combs[c].process(input, feedback, damp)
            }
            if (width > 0.01f) {
                var wTap = 0f
                for (c in combRight.indices) {
                    wTap += combRight[c].process(input, feedback, damp)
                }
                out = out * (1f - width * 0.4f) + wTap * width
            }
            for (a in allpasses.indices) {
                out = allpasses[a].process(out)
            }

            val wetMixSignal = dry * dryGain + out * wetGain
            val effMix = mix * smoothActive
            buffer[i] = softClip(dry * (1f - effMix) + wetMixSignal * effMix)
        }
    }
}

/**
 * Hall spatial processor with independent Echo and Reverberation parameters (0 - 100).
 */
internal class HallEffect(private val sampleRateHz: Int) {
    @Volatile
    private var config = HallConfig()

    private val hallCombTunings = intArrayOf(1557, 1689, 1843, 2011, 2179, 2347)
    private val hallAllpassTunings = intArrayOf(719, 541, 389)
    private val combs = Array(hallCombTunings.size) { CombFilter(hallCombTunings[it]) }
    private val allpasses = Array(hallAllpassTunings.size) { AllpassFilter(hallAllpassTunings[it]) }
    private val warmthLpf = BiquadFilter()

    // Dedicated early hall echo tap (~145 ms & ~240 ms)
    private val echoRingSize = (sampleRateHz * 320) / 1000
    private val echoRing = FloatArray(echoRingSize)
    private var echoWrite = 0
    private var smoothActive = 0f

    init {
        warmthLpf.setLowPass(sampleRateHz, 4200.0, 0.707)
    }

    fun updateConfig(cfg: HallConfig) {
        config = cfg.copy(
            echoAmount = cfg.echoAmount.coerceIn(0, 100),
            reverberationAmount = cfg.reverberationAmount.coerceIn(0, 100)
        )
    }

    fun reset() {
        combs.forEach { it.reset() }
        allpasses.forEach { it.reset() }
        warmthLpf.reset()
        echoRing.fill(0f)
        echoWrite = 0
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }

        val targetActive = if (cfg.enabled) 1f else 0f
        val echoNorm = cfg.echoAmount / 100f
        val reverbNorm = cfg.reverberationAmount / 100f
        val feedback = 0.70f + reverbNorm * 0.25f
        val damp = 0.22f + (1f - reverbNorm) * 0.25f
        val tap1 = (sampleRateHz * 145) / 1000
        val tap2 = (sampleRateHz * 245) / 1000

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            val dry = buffer[i]

            val r1 = echoRing[(echoWrite - tap1 + echoRingSize) % echoRingSize]
            val r2 = echoRing[(echoWrite - tap2 + echoRingSize) % echoRingSize]
            val hallEcho = r1 * 0.6f + r2 * 0.4f

            echoRing[echoWrite] = if (cfg.enabled) {
                softClip(dry + hallEcho * (0.20f + echoNorm * 0.30f))
            } else {
                hallEcho * 0.5f
            }
            echoWrite++
            if (echoWrite >= echoRingSize) echoWrite = 0

            val revIn = (if (cfg.enabled) dry else 0f) * 0.16f + hallEcho * 0.10f
            var revOut = 0f
            for (c in combs.indices) {
                revOut += combs[c].process(revIn, feedback, damp)
            }
            for (a in allpasses.indices) {
                revOut = allpasses[a].process(revOut)
            }
            revOut = warmthLpf.process(revOut)

            val wet = dry + hallEcho * (echoNorm * 0.45f) + revOut * (reverbNorm * 0.45f)
            buffer[i] = softClip(dry * (1f - smoothActive) + wet * smoothActive)
        }
    }
}

internal class CombFilter(size: Int) {
    private val buffer = FloatArray(size)
    private var idx = 0
    private var filterStore = 0f

    fun reset() {
        buffer.fill(0f)
        idx = 0
        filterStore = 0f
    }

    fun process(input: Float, feedback: Float, damp: Float): Float {
        val output = buffer[idx]
        filterStore = output * (1f - damp) + filterStore * damp
        buffer[idx] = input + filterStore * feedback
        idx++
        if (idx >= buffer.size) idx = 0
        return output
    }
}

internal class AllpassFilter(size: Int) {
    private val buffer = FloatArray(size)
    private var idx = 0

    fun reset() {
        buffer.fill(0f)
        idx = 0
    }

    fun process(input: Float): Float {
        val bufOut = buffer[idx]
        val output = -input + bufOut
        buffer[idx] = input + bufOut * 0.5f
        idx++
        if (idx >= buffer.size) idx = 0
        return output
    }
}

private fun softClip(x: Float): Float {
    val ax = abs(x)
    return if (ax <= 0.88f) x else {
        val s = 0.88f + 0.10f * tanh((ax - 0.88f) / 0.10f)
        if (x >= 0f) s else -s
    }
}
