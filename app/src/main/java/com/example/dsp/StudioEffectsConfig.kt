package com.example.dsp

import org.json.JSONArray
import org.json.JSONObject

enum class StudioEffectType(val displayName: String) {
    PITCH_CORRECTION("Pitch Correction"),
    PITCH_FORMANT("Pitch & Formant"),
    EQUALIZER("Equalizer (EQ)"),
    COMPRESSION("Compression"),
    SATURATION("Saturation"),
    REVERB("Reverb"),
    DELAY("Delay"),
    NOISE_GATE("Noise Gate"),
    DE_ESSER("De-Esser"),
    LIMITER("Limiter")
}

enum class MusicalKey(val label: String, val semitoneOffset: Int) {
    C("C", 0),
    C_SHARP("C#", 1),
    D("D", 2),
    D_SHARP("D#", 3),
    E("E", 4),
    F("F", 5),
    F_SHARP("F#", 6),
    G("G", 7),
    G_SHARP("G#", 8),
    A("A", 9),
    A_SHARP("A#", 10),
    B("B", 11)
}

enum class MusicalScale(val label: String, val intervals: IntArray) {
    CHROMATIC("Chromatic", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    MAJOR("Major", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MINOR("Minor", intArrayOf(0, 2, 3, 5, 7, 8, 10))
}

data class PitchCorrectionConfig(
    val enabled: Boolean = false,
    val pitchDetectionEnabled: Boolean = true,
    val key: MusicalKey = MusicalKey.C,
    val scale: MusicalScale = MusicalScale.MAJOR,
    val automaticKeyDetection: Boolean = false,
    val correctionStrengthPercent: Int = 65,
    val retuneSpeedMs: Int = 35,
    val humanizePercent: Int = 35,
    val noteTransitionMs: Int = 25,
    val referenceTuningHz: Float = 440f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pitchDetectionEnabled", pitchDetectionEnabled)
        put("key", key.name)
        put("scale", scale.name)
        put("automaticKeyDetection", automaticKeyDetection)
        put("correctionStrengthPercent", correctionStrengthPercent)
        put("retuneSpeedMs", retuneSpeedMs)
        put("humanizePercent", humanizePercent)
        put("noteTransitionMs", noteTransitionMs)
        put("referenceTuningHz", referenceTuningHz.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): PitchCorrectionConfig {
            val d = PitchCorrectionConfig(enabled = enabled)
            return d.copy(
                pitchDetectionEnabled = obj.optBoolean("pitchDetectionEnabled", d.pitchDetectionEnabled),
                key = MusicalKey.entries.firstOrNull { it.name == obj.optString("key") } ?: d.key,
                scale = MusicalScale.entries.firstOrNull { it.name == obj.optString("scale") } ?: d.scale,
                automaticKeyDetection = obj.optBoolean("automaticKeyDetection", d.automaticKeyDetection),
                correctionStrengthPercent = obj.optInt("correctionStrengthPercent", d.correctionStrengthPercent).coerceIn(0, 100),
                retuneSpeedMs = obj.optInt("retuneSpeedMs", d.retuneSpeedMs).coerceIn(0, 200),
                humanizePercent = obj.optInt("humanizePercent", d.humanizePercent).coerceIn(0, 100),
                noteTransitionMs = obj.optInt("noteTransitionMs", d.noteTransitionMs).coerceIn(0, 150),
                referenceTuningHz = obj.optDouble("referenceTuningHz", d.referenceTuningHz.toDouble()).toFloat().coerceIn(415f, 465f)
            )
        }
    }
}

data class PitchFormantConfig(
    val enabled: Boolean = false,
    val pitchShiftSt: Float = 0f,
    val formantShiftSt: Float = 0f,
    val fineTuneCents: Int = 0,
    val formantPreservation: Boolean = true,
    val dryWetPercent: Int = 100,
    val outputGainDb: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pitchShiftSt", pitchShiftSt.toDouble())
        put("formantShiftSt", formantShiftSt.toDouble())
        put("fineTuneCents", fineTuneCents)
        put("formantPreservation", formantPreservation)
        put("dryWetPercent", dryWetPercent)
        put("outputGainDb", outputGainDb.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): PitchFormantConfig {
            val d = PitchFormantConfig(enabled = enabled)
            return d.copy(
                pitchShiftSt = obj.optDouble("pitchShiftSt", d.pitchShiftSt.toDouble()).toFloat().coerceIn(-12f, 12f),
                formantShiftSt = obj.optDouble("formantShiftSt", d.formantShiftSt.toDouble()).toFloat().coerceIn(-12f, 12f),
                fineTuneCents = obj.optInt("fineTuneCents", d.fineTuneCents).coerceIn(-100, 100),
                formantPreservation = obj.optBoolean("formantPreservation", d.formantPreservation),
                dryWetPercent = obj.optInt("dryWetPercent", d.dryWetPercent).coerceIn(0, 100),
                outputGainDb = obj.optDouble("outputGainDb", d.outputGainDb.toDouble()).toFloat().coerceIn(-12f, 12f)
            )
        }
    }
}

data class CompressionConfig(
    val enabled: Boolean = false,
    val thresholdDb: Float = -18f,
    val ratio: Float = 3.5f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 120f,
    val kneeDb: Float = 6f,
    val makeupGainDb: Float = 3f,
    val outputGainDb: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("thresholdDb", thresholdDb.toDouble())
        put("ratio", ratio.toDouble())
        put("attackMs", attackMs.toDouble())
        put("releaseMs", releaseMs.toDouble())
        put("kneeDb", kneeDb.toDouble())
        put("makeupGainDb", makeupGainDb.toDouble())
        put("outputGainDb", outputGainDb.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): CompressionConfig {
            val d = CompressionConfig(enabled = enabled)
            return d.copy(
                thresholdDb = obj.optDouble("thresholdDb", d.thresholdDb.toDouble()).toFloat().coerceIn(-60f, 0f),
                ratio = obj.optDouble("ratio", d.ratio.toDouble()).toFloat().coerceIn(1f, 20f),
                attackMs = obj.optDouble("attackMs", d.attackMs.toDouble()).toFloat().coerceIn(0.5f, 100f),
                releaseMs = obj.optDouble("releaseMs", d.releaseMs.toDouble()).toFloat().coerceIn(10f, 1000f),
                kneeDb = obj.optDouble("kneeDb", d.kneeDb.toDouble()).toFloat().coerceIn(0f, 24f),
                makeupGainDb = obj.optDouble("makeupGainDb", d.makeupGainDb.toDouble()).toFloat().coerceIn(0f, 24f),
                outputGainDb = obj.optDouble("outputGainDb", d.outputGainDb.toDouble()).toFloat().coerceIn(-12f, 12f)
            )
        }
    }
}

data class SaturationConfig(
    val enabled: Boolean = false,
    val drivePercent: Int = 25,
    val warmthPercent: Int = 40,
    val tonePercent: Int = 0,
    val harmonicAmountPercent: Int = 35,
    val mixPercent: Int = 70,
    val outputLevelDb: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("drivePercent", drivePercent)
        put("warmthPercent", warmthPercent)
        put("tonePercent", tonePercent)
        put("harmonicAmountPercent", harmonicAmountPercent)
        put("mixPercent", mixPercent)
        put("outputLevelDb", outputLevelDb.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): SaturationConfig {
            val d = SaturationConfig(enabled = enabled)
            return d.copy(
                drivePercent = obj.optInt("drivePercent", d.drivePercent).coerceIn(0, 100),
                warmthPercent = obj.optInt("warmthPercent", d.warmthPercent).coerceIn(0, 100),
                tonePercent = obj.optInt("tonePercent", d.tonePercent).coerceIn(-100, 100),
                harmonicAmountPercent = obj.optInt("harmonicAmountPercent", d.harmonicAmountPercent).coerceIn(0, 100),
                mixPercent = obj.optInt("mixPercent", d.mixPercent).coerceIn(0, 100),
                outputLevelDb = obj.optDouble("outputLevelDb", d.outputLevelDb.toDouble()).toFloat().coerceIn(-12f, 12f)
            )
        }
    }
}

enum class ReverbPreset(val label: String) {
    SMALL_ROOM("Small Room"),
    STUDIO("Studio"),
    HALL("Hall"),
    PLATE("Plate");

    fun applyTo(enabled: Boolean): ReverbConfig = when (this) {
        SMALL_ROOM -> ReverbConfig(
            enabled = enabled,
            preset = this,
            roomSizePercent = 28,
            decayTimeSec = 0.7f,
            preDelayMs = 6f,
            dampingPercent = 62,
            wetPercent = 24,
            dryPercent = 100,
            widthPercent = 65,
            mixPercent = 75
        )
        STUDIO -> ReverbConfig(
            enabled = enabled,
            preset = this,
            roomSizePercent = 48,
            decayTimeSec = 1.4f,
            preDelayMs = 14f,
            dampingPercent = 48,
            wetPercent = 30,
            dryPercent = 100,
            widthPercent = 90,
            mixPercent = 82
        )
        HALL -> ReverbConfig(
            enabled = enabled,
            preset = this,
            roomSizePercent = 82,
            decayTimeSec = 3.8f,
            preDelayMs = 28f,
            dampingPercent = 36,
            wetPercent = 46,
            dryPercent = 92,
            widthPercent = 100,
            mixPercent = 88
        )
        PLATE -> ReverbConfig(
            enabled = enabled,
            preset = this,
            roomSizePercent = 64,
            decayTimeSec = 2.4f,
            preDelayMs = 4f,
            dampingPercent = 24,
            wetPercent = 38,
            dryPercent = 100,
            widthPercent = 100,
            mixPercent = 85
        )
    }
}

enum class DelaySubdivision(val label: String, val beatFactor: Float) {
    QUARTER("1/4", 1.0f),
    EIGHTH("1/8", 0.5f),
    DOTTED_EIGHTH("1/8D", 0.75f),
    SIXTEENTH("1/16", 0.25f)
}

data class DeEsserConfig(
    val enabled: Boolean = false,
    val frequencyHz: Float = 6200f,
    val thresholdDb: Float = -24f,
    val reductionPercent: Int = 55,
    val bandwidthQ: Float = 2.0f,
    val mixPercent: Int = 100
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("frequencyHz", frequencyHz.toDouble())
        put("thresholdDb", thresholdDb.toDouble())
        put("reductionPercent", reductionPercent)
        put("bandwidthQ", bandwidthQ.toDouble())
        put("mixPercent", mixPercent)
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): DeEsserConfig {
            val d = DeEsserConfig(enabled = enabled)
            return d.copy(
                frequencyHz = obj.optDouble("frequencyHz", d.frequencyHz.toDouble()).toFloat().coerceIn(3000f, 10000f),
                thresholdDb = obj.optDouble("thresholdDb", d.thresholdDb.toDouble()).toFloat().coerceIn(-50f, 0f),
                reductionPercent = obj.optInt("reductionPercent", d.reductionPercent).coerceIn(0, 100),
                bandwidthQ = obj.optDouble("bandwidthQ", d.bandwidthQ.toDouble()).toFloat().coerceIn(0.5f, 6.0f),
                mixPercent = obj.optInt("mixPercent", d.mixPercent).coerceIn(0, 100)
            )
        }
    }
}

data class LimiterConfig(
    val enabled: Boolean = false,
    val thresholdDb: Float = -3.0f,
    val ceilingDb: Float = -0.3f,
    val releaseMs: Float = 80f,
    val inputGainDb: Float = 0f,
    val outputGainDb: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("thresholdDb", thresholdDb.toDouble())
        put("ceilingDb", ceilingDb.toDouble())
        put("releaseMs", releaseMs.toDouble())
        put("inputGainDb", inputGainDb.toDouble())
        put("outputGainDb", outputGainDb.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject, enabled: Boolean): LimiterConfig {
            val d = LimiterConfig(enabled = enabled)
            return d.copy(
                thresholdDb = obj.optDouble("thresholdDb", d.thresholdDb.toDouble()).toFloat().coerceIn(-30f, 0f),
                ceilingDb = obj.optDouble("ceilingDb", d.ceilingDb.toDouble()).toFloat().coerceIn(-12f, -0.1f),
                releaseMs = obj.optDouble("releaseMs", d.releaseMs.toDouble()).toFloat().coerceIn(10f, 500f),
                inputGainDb = obj.optDouble("inputGainDb", d.inputGainDb.toDouble()).toFloat().coerceIn(-12f, 18f),
                outputGainDb = obj.optDouble("outputGainDb", d.outputGainDb.toDouble()).toFloat().coerceIn(-12f, 6f)
            )
        }
    }
}

data class StudioEffectPreset(
    val id: String,
    val name: String,
    val effectType: StudioEffectType,
    val isBuiltIn: Boolean,
    val payloadJson: String
)

object BuiltInEffectPresets {
    fun forEffect(type: StudioEffectType): List<StudioEffectPreset> = when (type) {
        StudioEffectType.PITCH_CORRECTION -> listOf(
            StudioEffectPreset(
                "bi_pc_natural",
                "Natural Vocal",
                type,
                true,
                PitchCorrectionConfig(
                    correctionStrengthPercent = 45,
                    retuneSpeedMs = 65,
                    humanizePercent = 60,
                    noteTransitionMs = 35
                ).toJson().toString()
            ),
            StudioEffectPreset(
                "bi_pc_tight",
                "Tight Pop Tune",
                type,
                true,
                PitchCorrectionConfig(
                    correctionStrengthPercent = 92,
                    retuneSpeedMs = 8,
                    humanizePercent = 12,
                    noteTransitionMs = 10
                ).toJson().toString()
            )
        )
        StudioEffectType.PITCH_FORMANT -> listOf(
            StudioEffectPreset(
                "bi_pf_deep",
                "Deep Narrator",
                type,
                true,
                PitchFormantConfig(
                    pitchShiftSt = -2.5f,
                    formantShiftSt = -1.8f,
                    formantPreservation = false,
                    dryWetPercent = 100
                ).toJson().toString()
            ),
            StudioEffectPreset(
                "bi_pf_bright",
                "Bright Formant",
                type,
                true,
                PitchFormantConfig(
                    pitchShiftSt = 2.0f,
                    formantShiftSt = 2.2f,
                    formantPreservation = false,
                    dryWetPercent = 100
                ).toJson().toString()
            )
        )
        StudioEffectType.EQUALIZER -> listOf(
            StudioEffectPreset(
                "bi_eq_podcast",
                "Podcast Crisp",
                type,
                true,
                EqualizerConfig(
                    lowFreqHz = 100f,
                    lowGainDb = -3.0f,
                    mid1FreqHz = 320f,
                    mid1GainDb = -2.0f,
                    mid2FreqHz = 2400f,
                    mid2GainDb = 2.5f,
                    highFreqHz = 9000f,
                    highGainDb = 3.0f
                ).toJson().toString()
            ),
            StudioEffectPreset(
                "bi_eq_warm",
                "Warm Presence",
                type,
                true,
                EqualizerConfig(
                    lowFreqHz = 140f,
                    lowGainDb = 3.0f,
                    mid1FreqHz = 450f,
                    mid1GainDb = -1.5f,
                    mid2FreqHz = 3000f,
                    mid2GainDb = 2.0f,
                    highFreqHz = 10000f,
                    highGainDb = 1.5f
                ).toJson().toString()
            )
        )
        StudioEffectType.COMPRESSION -> listOf(
            StudioEffectPreset(
                "bi_comp_vocal",
                "Broadcast Leveler",
                type,
                true,
                CompressionConfig(
                    thresholdDb = -20f,
                    ratio = 4.0f,
                    attackMs = 8f,
                    releaseMs = 110f,
                    kneeDb = 6f,
                    makeupGainDb = 4f
                ).toJson().toString()
            ),
            StudioEffectPreset(
                "bi_comp_punch",
                "Punchy Voice",
                type,
                true,
                CompressionConfig(
                    thresholdDb = -26f,
                    ratio = 6.5f,
                    attackMs = 4f,
                    releaseMs = 85f,
                    kneeDb = 4f,
                    makeupGainDb = 6.5f
                ).toJson().toString()
            )
        )
        StudioEffectType.SATURATION -> listOf(
            StudioEffectPreset(
                "bi_sat_tube",
                "Analog Tube",
                type,
                true,
                SaturationConfig(
                    drivePercent = 30,
                    warmthPercent = 65,
                    tonePercent = -10,
                    harmonicAmountPercent = 45,
                    mixPercent = 60
                ).toJson().toString()
            ),
            StudioEffectPreset(
                "bi_sat_grit",
                "Tape Grit",
                type,
                true,
                SaturationConfig(
                    drivePercent = 62,
                    warmthPercent = 45,
                    tonePercent = 18,
                    harmonicAmountPercent = 70,
                    mixPercent = 75
                ).toJson().toString()
            )
        )
        StudioEffectType.REVERB -> ReverbPreset.entries.map { rp ->
            StudioEffectPreset(
                id = "bi_rev_${rp.name.lowercase()}",
                name = rp.label,
                effectType = type,
                isBuiltIn = true,
                payloadJson = rp.applyTo(enabled = true).toJson().toString()
            )
        }
        StudioEffectType.DELAY -> listOf(
            StudioEffectPreset(
                "bi_del_slap",
                "Vocal Slapback",
                type,
                true,
                EchoConfig(
                    timeMs = 115,
                    feedbackPercent = 18,
                    wetPercent = 28,
                    dryPercent = 100,
                    mixPercent = 60,
                    lowCutHz = 180f,
                    highCutHz = 4800f
                ).toJson().toString()
            ),
            StudioEffectPreset(
                "bi_del_ambient",
                "Stereo Space",
                type,
                true,
                EchoConfig(
                    timeMs = 340,
                    feedbackPercent = 42,
                    wetPercent = 36,
                    dryPercent = 100,
                    widthPercent = 85,
                    pingPong = true,
                    mixPercent = 75,
                    lowCutHz = 140f,
                    highCutHz = 6000f
                ).toJson().toString()
            )
        )
        StudioEffectType.NOISE_GATE -> listOf(
            StudioEffectPreset(
                "bi_gate_voice",
                "Clean Dialogue",
                type,
                true,
                NoiseGateConfig(
                    thresholdDb = -44f,
                    attackMs = 4f,
                    holdMs = 70f,
                    releaseMs = 120f,
                    rangeDb = 70f,
                    mixPercent = 100
                ).toJson().toString()
            )
        )
        StudioEffectType.DE_ESSER -> listOf(
            StudioEffectPreset(
                "bi_deess_soft",
                "Smooth Sibilance",
                type,
                true,
                DeEsserConfig(
                    frequencyHz = 6400f,
                    thresholdDb = -25f,
                    reductionPercent = 50,
                    bandwidthQ = 2.2f,
                    mixPercent = 100
                ).toJson().toString()
            )
        )
        StudioEffectType.LIMITER -> listOf(
            StudioEffectPreset(
                "bi_lim_master",
                "Broadcast -1 dBTP",
                type,
                true,
                LimiterConfig(
                    thresholdDb = -4.5f,
                    ceilingDb = -1.0f,
                    releaseMs = 90f,
                    inputGainDb = 2.0f,
                    outputGainDb = 0f
                ).toJson().toString()
            )
        )
    }

    fun encodeUserPresets(presets: List<StudioEffectPreset>): String {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("effectType", p.effectType.name)
                    put("payloadJson", p.payloadJson)
                }
            )
        }
        return arr.toString()
    }

    fun decodeUserPresets(raw: String): List<StudioEffectPreset> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val type = StudioEffectType.entries.firstOrNull {
                        it.name == obj.optString("effectType")
                    } ?: continue
                    add(
                        StudioEffectPreset(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            effectType = type,
                            isBuiltIn = false,
                            payloadJson = obj.optString("payloadJson")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
