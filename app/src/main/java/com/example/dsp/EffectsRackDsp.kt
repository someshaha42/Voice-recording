package com.example.dsp

import com.example.audio.AudioConfig
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
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
    val lowQ: Float = 0.71f,
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
    val highGainDb: Float = 0f,
    val highQ: Float = 0.71f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mixPercent", mixPercent)
        put("lowFreqHz", lowFreqHz.toDouble())
        put("lowGainDb", lowGainDb.toDouble())
        put("lowQ", lowQ.toDouble())
        put("mid1FreqHz", mid1FreqHz.toDouble())
        put("mid1GainDb", mid1GainDb.toDouble())
        put("mid1Q", mid1Q.toDouble())
        put("mid2FreqHz", mid2FreqHz.toDouble())
        put("mid2GainDb", mid2GainDb.toDouble())
        put("mid2Q", mid2Q.toDouble())
        put("mid3FreqHz", mid3FreqHz.toDouble())
        put("mid3GainDb", mid3GainDb.toDouble())
        put("mid3Q", mid3Q.toDouble())
        put("highFreqHz", highFreqHz.toDouble())
        put("highGainDb", highGainDb.toDouble())
        put("highQ", highQ.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): EqualizerConfig {
            val d = EqualizerConfig(enabled = enabled)
            return d.copy(
                mixPercent = obj.optInt("mixPercent", d.mixPercent).coerceIn(0, 100),
                lowFreqHz = obj.optDouble("lowFreqHz", d.lowFreqHz.toDouble()).toFloat().coerceIn(20f, 500f),
                lowGainDb = obj.optDouble("lowGainDb", d.lowGainDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                lowQ = obj.optDouble("lowQ", d.lowQ.toDouble()).toFloat().coerceIn(0.2f, 4f),
                mid1FreqHz = obj.optDouble("mid1FreqHz", d.mid1FreqHz.toDouble()).toFloat().coerceIn(100f, 1500f),
                mid1GainDb = obj.optDouble("mid1GainDb", d.mid1GainDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                mid1Q = obj.optDouble("mid1Q", d.mid1Q.toDouble()).toFloat().coerceIn(0.1f, 10f),
                mid2FreqHz = obj.optDouble("mid2FreqHz", d.mid2FreqHz.toDouble()).toFloat().coerceIn(300f, 5000f),
                mid2GainDb = obj.optDouble("mid2GainDb", d.mid2GainDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                mid2Q = obj.optDouble("mid2Q", d.mid2Q.toDouble()).toFloat().coerceIn(0.1f, 10f),
                mid3FreqHz = obj.optDouble("mid3FreqHz", d.mid3FreqHz.toDouble()).toFloat().coerceIn(1000f, 12000f),
                mid3GainDb = obj.optDouble("mid3GainDb", d.mid3GainDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                mid3Q = obj.optDouble("mid3Q", d.mid3Q.toDouble()).toFloat().coerceIn(0.1f, 10f),
                highFreqHz = obj.optDouble("highFreqHz", d.highFreqHz.toDouble()).toFloat().coerceIn(2000f, 16000f),
                highGainDb = obj.optDouble("highGainDb", d.highGainDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                highQ = obj.optDouble("highQ", d.highQ.toDouble()).toFloat().coerceIn(0.2f, 4f)
            )
        }
    }
}

data class ReverbConfig(
    val enabled: Boolean = false,
    val preset: ReverbPreset = ReverbPreset.STUDIO,
    val mixPercent: Int = 81,
    val roomSizePercent: Int = 50,
    val decayTimeSec: Float = 1.6f,
    val dampingPercent: Int = 50,
    val wetPercent: Int = 30,
    val dryPercent: Int = 100,
    val widthPercent: Int = 100,
    val preDelayMs: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("preset", preset.name)
        put("mixPercent", mixPercent)
        put("roomSizePercent", roomSizePercent)
        put("decayTimeSec", decayTimeSec.toDouble())
        put("dampingPercent", dampingPercent)
        put("wetPercent", wetPercent)
        put("dryPercent", dryPercent)
        put("widthPercent", widthPercent)
        put("preDelayMs", preDelayMs.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): ReverbConfig {
            val d = ReverbConfig(enabled = enabled)
            return d.copy(
                preset = ReverbPreset.entries.firstOrNull { it.name == obj.optString("preset") } ?: d.preset,
                mixPercent = obj.optInt("mixPercent", d.mixPercent).coerceIn(0, 100),
                roomSizePercent = obj.optInt("roomSizePercent", d.roomSizePercent).coerceIn(0, 100),
                decayTimeSec = obj.optDouble("decayTimeSec", d.decayTimeSec.toDouble()).toFloat().coerceIn(0.2f, 8.0f),
                dampingPercent = obj.optInt("dampingPercent", d.dampingPercent).coerceIn(0, 100),
                wetPercent = obj.optInt("wetPercent", d.wetPercent).coerceIn(0, 100),
                dryPercent = obj.optInt("dryPercent", d.dryPercent).coerceIn(0, 100),
                widthPercent = obj.optInt("widthPercent", d.widthPercent).coerceIn(0, 100),
                preDelayMs = obj.optDouble("preDelayMs", d.preDelayMs.toDouble()).toFloat().coerceIn(0f, 100f)
            )
        }
    }
}

/**
 * Shared Delay / Echo configuration synchronized between Home Studio Controls ("Delay")
 * and the Effects tab ("Echo").
 */
data class EchoConfig(
    val enabled: Boolean = false,
    val mixPercent: Int = 60,
    val timeMs: Int = 250,
    val feedbackPercent: Int = 35,
    val echoLevelPercent: Int = 30,
    val wetPercent: Int = 35,
    val dryPercent: Int = 100,
    val pingPong: Boolean = false,
    val widthPercent: Int = 60,
    val tempoSync: Boolean = false,
    val bpm: Int = 120,
    val subdivision: DelaySubdivision = DelaySubdivision.EIGHTH,
    val lowCutHz: Float = 80f,
    val highCutHz: Float = 8000f
) {
    val effectiveDelayMs: Int
        get() = if (tempoSync) {
            val beatMs = 60_000f / bpm.coerceIn(60, 200)
            (beatMs * subdivision.beatFactor).roundToInt().coerceIn(20, 1000)
        } else {
            timeMs.coerceIn(20, 1000)
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("mixPercent", mixPercent)
        put("timeMs", timeMs)
        put("feedbackPercent", feedbackPercent)
        put("echoLevelPercent", echoLevelPercent)
        put("wetPercent", wetPercent)
        put("dryPercent", dryPercent)
        put("pingPong", pingPong)
        put("widthPercent", widthPercent)
        put("tempoSync", tempoSync)
        put("bpm", bpm)
        put("subdivision", subdivision.name)
        put("lowCutHz", lowCutHz.toDouble())
        put("highCutHz", highCutHz.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): EchoConfig {
            val d = EchoConfig(enabled = enabled)
            return d.copy(
                mixPercent = obj.optInt("mixPercent", d.mixPercent).coerceIn(0, 100),
                timeMs = obj.optInt("timeMs", d.timeMs).coerceIn(20, 1000),
                feedbackPercent = obj.optInt("feedbackPercent", d.feedbackPercent).coerceIn(0, 90),
                echoLevelPercent = obj.optInt("echoLevelPercent", d.echoLevelPercent).coerceIn(0, 100),
                wetPercent = obj.optInt("wetPercent", d.wetPercent).coerceIn(0, 100),
                dryPercent = obj.optInt("dryPercent", d.dryPercent).coerceIn(0, 100),
                pingPong = obj.optBoolean("pingPong", d.pingPong),
                widthPercent = obj.optInt("widthPercent", d.widthPercent).coerceIn(0, 100),
                tempoSync = obj.optBoolean("tempoSync", d.tempoSync),
                bpm = obj.optInt("bpm", d.bpm).coerceIn(60, 200),
                subdivision = DelaySubdivision.entries.firstOrNull { it.name == obj.optString("subdivision") } ?: d.subdivision,
                lowCutHz = obj.optDouble("lowCutHz", d.lowCutHz.toDouble()).toFloat().coerceIn(20f, 1000f),
                highCutHz = obj.optDouble("highCutHz", d.highCutHz.toDouble()).toFloat().coerceIn(1000f, 18000f)
            )
        }
    }
}

data class HallConfig(
    val enabled: Boolean = false,
    val echoAmount: Int = 55,
    val reverberationAmount: Int = 71
)

fun NoiseGateConfig.toJson(): JSONObject = JSONObject().apply {
    put("mixPercent", mixPercent)
    put("thresholdDb", thresholdDb.toDouble())
    put("attackMs", attackMs.toDouble())
    put("holdMs", holdMs.toDouble())
    put("releaseMs", releaseMs.toDouble())
    put("rangeDb", rangeDb.toDouble())
}

fun noiseGateFromJson(obj: JSONObject, enabled: Boolean): NoiseGateConfig {
    val d = NoiseGateConfig(enabled = enabled)
    return d.copy(
        mixPercent = obj.optInt("mixPercent", d.mixPercent).coerceIn(0, 100),
        thresholdDb = obj.optDouble("thresholdDb", d.thresholdDb.toDouble()).toFloat().coerceIn(-70f, -10f),
        attackMs = obj.optDouble("attackMs", d.attackMs.toDouble()).toFloat().coerceIn(0.5f, 50f),
        holdMs = obj.optDouble("holdMs", d.holdMs.toDouble()).toFloat().coerceIn(0f, 500f),
        releaseMs = obj.optDouble("releaseMs", d.releaseMs.toDouble()).toFloat().coerceIn(20f, 600f),
        rangeDb = obj.optDouble("rangeDb", d.rangeDb.toDouble()).toFloat().coerceIn(0f, 80f)
    )
}

/**
 * Single unified Effects Rack & 10-Effect Studio Controls state shared across:
 *  - Live Microphone Recording
 *  - Live Voice Preview
 *  - Imported Audio Real-Time Playback
 *  - Processed Audio WAV Export
 *  - Home Screen "AUDIO EFFECTS & STUDIO CONTROLS" & Effects Tab
 */
data class EffectsRackConfig(
    val pitchCorrection: PitchCorrectionConfig = PitchCorrectionConfig(),
    val pitchFormant: PitchFormantConfig = PitchFormantConfig(),
    val equalizer: EqualizerConfig = EqualizerConfig(),
    val compression: CompressionConfig = CompressionConfig(),
    val saturation: SaturationConfig = SaturationConfig(),
    val reverb: ReverbConfig = ReverbConfig(),
    val echo: EchoConfig = EchoConfig(),
    val noiseGate: NoiseGateConfig = NoiseGateConfig(),
    val deEsser: DeEsserConfig = DeEsserConfig(),
    val limiter: LimiterConfig = LimiterConfig(),
    val lowPass: LowPassConfig = LowPassConfig(),
    val highPass: HighPassConfig = HighPassConfig(),
    val hall: HallConfig = HallConfig()
) {
    val activeStudioEffectCount: Int
        get() = listOf(
            pitchCorrection.enabled,
            pitchFormant.enabled,
            equalizer.enabled,
            compression.enabled,
            saturation.enabled,
            reverb.enabled,
            echo.enabled,
            noiseGate.enabled,
            deEsser.enabled,
            limiter.enabled
        ).count { it }

    val activeEffectCount: Int
        get() = activeStudioEffectCount + listOf(
            lowPass.enabled,
            highPass.enabled,
            hall.enabled
        ).count { it }
}

/**
 * Complete unified Studio DSP Processing Chain operating at 48,000 Hz mono.
 * Instantiate independently per audio stream (Live Engine, Imported Player, Offline Exporter)
 * while feeding identical [EffectsRackConfig] and [VoiceChangerConfig] values.
 */
class StudioDspPipeline(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    val noiseGate = NoiseGateProcessor(sampleRateHz)
    val pitchCorrection = PitchCorrectionProcessor(sampleRateHz)
    val voiceChanger = VoiceChangerDsp(sampleRateHz)
    val pitchFormant = PitchFormantProcessor(sampleRateHz)
    private val highPass = HighPassEffect(sampleRateHz)
    private val lowPass = LowPassEffect(sampleRateHz)
    private val equalizer = EqualizerEffect(sampleRateHz)
    private val deEsser = DeEsserProcessor(sampleRateHz)
    val compression = CompressionProcessor(sampleRateHz)
    private val saturation = SaturationProcessor(sampleRateHz)
    private val echo = EchoEffect(sampleRateHz)
    private val reverb = ReverbEffect(sampleRateHz)
    private val hall = HallEffect(sampleRateHz)
    private val limiter = LimiterProcessor(sampleRateHz)

    @Volatile
    private var effectsConfig = EffectsRackConfig()

    @Volatile
    private var voiceChangerConfig = VoiceChangerConfig()

    fun updateEffectsConfig(newConfig: EffectsRackConfig) {
        effectsConfig = newConfig
        noiseGate.updateConfig(newConfig.noiseGate)
        pitchCorrection.updateConfig(newConfig.pitchCorrection)
        pitchFormant.updateConfig(newConfig.pitchFormant)
        highPass.updateConfig(newConfig.highPass)
        lowPass.updateConfig(newConfig.lowPass)
        equalizer.updateConfig(newConfig.equalizer)
        deEsser.updateConfig(newConfig.deEsser)
        compression.updateConfig(newConfig.compression)
        saturation.updateConfig(newConfig.saturation)
        echo.updateConfig(newConfig.echo)
        reverb.updateConfig(newConfig.reverb)
        hall.updateConfig(newConfig.hall)
        limiter.updateConfig(newConfig.limiter)
    }

    fun updateVoiceChangerConfig(newConfig: VoiceChangerConfig) {
        voiceChangerConfig = newConfig
        voiceChanger.updateConfig(newConfig)
    }

    fun getEffectsConfig(): EffectsRackConfig = effectsConfig

    fun getVoiceChangerConfig(): VoiceChangerConfig = voiceChangerConfig

    fun reset() {
        noiseGate.reset()
        pitchCorrection.reset()
        voiceChanger.reset()
        pitchFormant.reset()
        highPass.reset()
        lowPass.reset()
        equalizer.reset()
        deEsser.reset()
        compression.reset()
        saturation.reset()
        echo.reset()
        reverb.reset()
        hall.reset()
        limiter.reset()
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        noiseGate.processInPlace(buffer, count)
        pitchCorrection.processInPlace(buffer, count)
        voiceChanger.processInPlace(buffer, count)
        pitchFormant.processInPlace(buffer, count)
        highPass.processInPlace(buffer, count)
        lowPass.processInPlace(buffer, count)
        equalizer.processInPlace(buffer, count)
        deEsser.processInPlace(buffer, count)
        compression.processInPlace(buffer, count)
        saturation.processInPlace(buffer, count)
        echo.processInPlace(buffer, count)
        reverb.processInPlace(buffer, count)
        hall.processInPlace(buffer, count)
        limiter.processInPlace(buffer, count)
    }
}

/**
 * Legacy-compatible facade delegating into the modular processors.
 */
class EffectsRackProcessor(
    sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    val noiseGate = NoiseGateProcessor(sampleRateHz)
    private val pitchCorrection = PitchCorrectionProcessor(sampleRateHz)
    private val pitchFormant = PitchFormantProcessor(sampleRateHz)
    private val lowPass = LowPassEffect(sampleRateHz)
    private val highPass = HighPassEffect(sampleRateHz)
    private val equalizer = EqualizerEffect(sampleRateHz)
    private val deEsser = DeEsserProcessor(sampleRateHz)
    private val compression = CompressionProcessor(sampleRateHz)
    private val saturation = SaturationProcessor(sampleRateHz)
    private val echo = EchoEffect(sampleRateHz)
    private val reverb = ReverbEffect(sampleRateHz)
    private val hall = HallEffect(sampleRateHz)
    private val limiter = LimiterProcessor(sampleRateHz)

    @Volatile
    private var config = EffectsRackConfig()

    fun updateConfig(newConfig: EffectsRackConfig) {
        config = newConfig
        noiseGate.updateConfig(newConfig.noiseGate)
        pitchCorrection.updateConfig(newConfig.pitchCorrection)
        pitchFormant.updateConfig(newConfig.pitchFormant)
        lowPass.updateConfig(newConfig.lowPass)
        highPass.updateConfig(newConfig.highPass)
        equalizer.updateConfig(newConfig.equalizer)
        deEsser.updateConfig(newConfig.deEsser)
        compression.updateConfig(newConfig.compression)
        saturation.updateConfig(newConfig.saturation)
        echo.updateConfig(newConfig.echo)
        reverb.updateConfig(newConfig.reverb)
        hall.updateConfig(newConfig.hall)
        limiter.updateConfig(newConfig.limiter)
    }

    fun getConfig(): EffectsRackConfig = config

    fun reset() {
        noiseGate.reset()
        pitchCorrection.reset()
        pitchFormant.reset()
        lowPass.reset()
        highPass.reset()
        equalizer.reset()
        deEsser.reset()
        compression.reset()
        saturation.reset()
        echo.reset()
        reverb.reset()
        hall.reset()
        limiter.reset()
    }

    fun processPreVoiceChanger(buffer: FloatArray, count: Int) {
        noiseGate.processInPlace(buffer, count)
        pitchCorrection.processInPlace(buffer, count)
    }

    fun processPostVoiceChanger(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        pitchFormant.processInPlace(buffer, count)
        highPass.processInPlace(buffer, count)
        lowPass.processInPlace(buffer, count)
        equalizer.processInPlace(buffer, count)
        deEsser.processInPlace(buffer, count)
        compression.processInPlace(buffer, count)
        saturation.processInPlace(buffer, count)
        echo.processInPlace(buffer, count)
        reverb.processInPlace(buffer, count)
        hall.processInPlace(buffer, count)
        limiter.processInPlace(buffer, count)
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
            lowQ = cfg.lowQ.coerceIn(0.2f, 4f),
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
            highGainDb = cfg.highGainDb.coerceIn(-12f, 12f),
            highQ = cfg.highQ.coerceIn(0.2f, 4f)
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
            lowBand.setLowShelf(sampleRateHz, cfg.lowFreqHz.toDouble(), cfg.lowGainDb.toDouble(), cfg.lowQ.toDouble())
            mid1Band.setPeaking(sampleRateHz, cfg.mid1FreqHz.toDouble(), cfg.mid1Q.toDouble(), cfg.mid1GainDb.toDouble())
            mid2Band.setPeaking(sampleRateHz, cfg.mid2FreqHz.toDouble(), cfg.mid2Q.toDouble(), cfg.mid2GainDb.toDouble())
            mid3Band.setPeaking(sampleRateHz, cfg.mid3FreqHz.toDouble(), cfg.mid3Q.toDouble(), cfg.mid3GainDb.toDouble())
            highBand.setHighShelf(sampleRateHz, cfg.highFreqHz.toDouble(), cfg.highGainDb.toDouble(), cfg.highQ.toDouble())
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

    companion object {
        fun computeMagnitudeCurveDb(
            cfg: EqualizerConfig,
            frequenciesHz: FloatArray,
            sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
        ): FloatArray {
            val f1 = BiquadFilter().apply { setLowShelf(sampleRateHz, cfg.lowFreqHz.toDouble(), cfg.lowGainDb.toDouble(), cfg.lowQ.toDouble()) }
            val f2 = BiquadFilter().apply { setPeaking(sampleRateHz, cfg.mid1FreqHz.toDouble(), cfg.mid1Q.toDouble(), cfg.mid1GainDb.toDouble()) }
            val f3 = BiquadFilter().apply { setPeaking(sampleRateHz, cfg.mid2FreqHz.toDouble(), cfg.mid2Q.toDouble(), cfg.mid2GainDb.toDouble()) }
            val f4 = BiquadFilter().apply { setPeaking(sampleRateHz, cfg.mid3FreqHz.toDouble(), cfg.mid3Q.toDouble(), cfg.mid3GainDb.toDouble()) }
            val f5 = BiquadFilter().apply { setHighShelf(sampleRateHz, cfg.highFreqHz.toDouble(), cfg.highGainDb.toDouble(), cfg.highQ.toDouble()) }
            val mixScale = cfg.mixPercent / 100f
            return FloatArray(frequenciesHz.size) { idx ->
                val f = frequenciesHz[idx].toDouble()
                val sumDb = f1.magnitudeDbAt(sampleRateHz, f) +
                    f2.magnitudeDbAt(sampleRateHz, f) +
                    f3.magnitudeDbAt(sampleRateHz, f) +
                    f4.magnitudeDbAt(sampleRateHz, f) +
                    f5.magnitudeDbAt(sampleRateHz, f)
                (sumDb.toFloat() * mixScale).coerceIn(-15f, 15f)
            }
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
    private val highCutFilter = BiquadFilter()
    private val pingPongAltFilter = BiquadFilter()
    private var lastLowCut = -1f
    private var lastHighCut = -1f

    init {
        pingPongAltFilter.setLowPass(sampleRateHz, 3400.0, 0.707)
    }

    fun updateConfig(cfg: EchoConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            timeMs = cfg.timeMs.coerceIn(20, 1000),
            feedbackPercent = cfg.feedbackPercent.coerceIn(0, 90),
            echoLevelPercent = cfg.echoLevelPercent.coerceIn(0, 100),
            wetPercent = cfg.wetPercent.coerceIn(0, 100),
            dryPercent = cfg.dryPercent.coerceIn(0, 100),
            widthPercent = cfg.widthPercent.coerceIn(0, 100),
            bpm = cfg.bpm.coerceIn(60, 200),
            lowCutHz = cfg.lowCutHz.coerceIn(20f, 1000f),
            highCutHz = cfg.highCutHz.coerceIn(1000f, 18000f)
        )
    }

    fun reset() {
        delayRing.fill(0f)
        writePos = 0
        currentDelaySamples = (config.effectiveDelayMs * sampleRateHz) / 1000f
        lowCutFilter.reset()
        highCutFilter.reset()
        pingPongAltFilter.reset()
        lastLowCut = -1f
        lastHighCut = -1f
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
        if (abs(cfg.highCutHz - lastHighCut) > 25f) {
            lastHighCut = cfg.highCutHz
            highCutFilter.setLowPass(sampleRateHz, cfg.highCutHz.toDouble(), 0.707)
        }

        val targetActive = if (cfg.enabled) 1f else 0f
        val targetDelaySamples = ((cfg.effectiveDelayMs * sampleRateHz) / 1000f)
            .coerceIn(24f, (maxSamples - 4).toFloat())
        val feedback = (cfg.feedbackPercent / 100f).coerceIn(0f, 0.90f)
        val wetGain = ((cfg.wetPercent / 100f) * 0.7f + (cfg.echoLevelPercent / 100f) * 0.3f)
        val dryGain = cfg.dryPercent / 100f
        val widthNorm = cfg.widthPercent / 100f
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

            val tapProcessed = if (cfg.pingPong || widthNorm > 0.1f) {
                val secondaryPos = (writePos - (currentDelaySamples * 0.5f).toInt() + maxSamples) % maxSamples
                val softBounce = pingPongAltFilter.process(delayRing[secondaryPos]) * (0.45f * widthNorm)
                val primaryWeight = if (cfg.pingPong) 0.75f else 1.0f - 0.15f * widthNorm
                delayed * primaryWeight + softBounce
            } else {
                delayed
            }

            val fbSignal = highCutFilter.process(lowCutFilter.process(delayed * feedback))
            delayRing[writePos] = if (cfg.enabled) {
                softClip(dry + fbSignal)
            } else {
                fbSignal * 0.95f
            }

            writePos++
            if (writePos >= maxSamples) writePos = 0

            val wet = dry * dryGain + tapProcessed * wetGain
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

    private val combTunings = intArrayOf(1214, 1293, 1390, 1478, 1546, 1622, 1694, 1765)
    private val allpassTunings = intArrayOf(605, 480, 371, 245)

    private val combs = Array(combTunings.size) { CombFilter(combTunings[it]) }
    private val combRight = Array(4) { CombFilter(combTunings[it] + 25) }
    private val allpasses = Array(allpassTunings.size) { AllpassFilter(allpassTunings[it]) }

    private val maxPreDelaySamples = (sampleRateHz * 110) / 1000
    private val preDelayRing = FloatArray(maxPreDelaySamples)
    private var preDelayWrite = 0
    private var smoothActive = 0f

    fun updateConfig(cfg: ReverbConfig) {
        config = cfg.copy(
            mixPercent = cfg.mixPercent.coerceIn(0, 100),
            roomSizePercent = cfg.roomSizePercent.coerceIn(0, 100),
            decayTimeSec = cfg.decayTimeSec.coerceIn(0.2f, 8.0f),
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
        val sizeNorm = cfg.roomSizePercent / 100f
        val decayNorm = ((cfg.decayTimeSec - 0.2f) / 7.8f).coerceIn(0f, 1f)
        val feedback = (0.62f + sizeNorm * 0.16f + decayNorm * 0.19f).coerceIn(0.60f, 0.965f)
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

internal class HallEffect(private val sampleRateHz: Int) {
    @Volatile
    private var config = HallConfig()

    private val hallCombTunings = intArrayOf(1557, 1689, 1843, 2011, 2179, 2347)
    private val hallAllpassTunings = intArrayOf(719, 541, 389)
    private val combs = Array(hallCombTunings.size) { CombFilter(hallCombTunings[it]) }
    private val allpasses = Array(hallAllpassTunings.size) { AllpassFilter(hallAllpassTunings[it]) }
    private val warmthLpf = BiquadFilter()

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
