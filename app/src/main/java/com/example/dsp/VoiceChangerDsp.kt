package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Exactly five fixed voice presets as specified.
 * Each preset defines internal developer-calibrated DSP characteristics
 * (pitch, formant, chest trim, vocal presence, air, clarity) and optimized
 * default values for the three user controls (Pitch Level, Tone Level, Clarity).
 */
enum class VoicePreset(val displayName: String) {
    NORMAL("Normal"),
    FEMALE("Female"),
    CHILD("Child"),
    GIRL("Girl"),
    WOMAN("Woman");

    internal fun profile(): PresetVoiceProfile = when (this) {
        NORMAL -> PresetVoiceProfile(
            basePitchSemitones = 0.0f,
            baseFormantSemitones = 0.0f,
            chestGainDb = 0.0f,
            chestFreqHz = 165.0,
            warmthGainDb = 0.0f,
            warmthFreqHz = 280.0,
            presenceGainDb = 0.0f,
            presenceFreqHz = 2800.0,
            airGainDb = 0.0f,
            airFreqHz = 7200.0,
            deEssAmount = 0.0f,
            defaultPitchLevel = 0,
            defaultToneLevel = 50,
            defaultClarity = 50
        )
        // Balanced, natural adult female speech
        FEMALE -> PresetVoiceProfile(
            basePitchSemitones = 5.4f,
            baseFormantSemitones = 3.5f,
            chestGainDb = -3.4f,
            chestFreqHz = 170.0,
            warmthGainDb = 1.4f,
            warmthFreqHz = 310.0,
            presenceGainDb = 2.1f,
            presenceFreqHz = 3000.0,
            airGainDb = 1.6f,
            airFreqHz = 7500.0,
            deEssAmount = 0.25f,
            defaultPitchLevel = 85,
            defaultToneLevel = 80,
            defaultClarity = 65
        )
        // Bright, playful, youthful child vocal tract
        CHILD -> PresetVoiceProfile(
            basePitchSemitones = 9.2f,
            baseFormantSemitones = 5.6f,
            chestGainDb = -5.5f,
            chestFreqHz = 185.0,
            warmthGainDb = 0.6f,
            warmthFreqHz = 360.0,
            presenceGainDb = 2.6f,
            presenceFreqHz = 2600.0,
            airGainDb = 2.0f,
            airFreqHz = 6800.0,
            deEssAmount = 0.32f,
            defaultPitchLevel = 90,
            defaultToneLevel = 85,
            defaultClarity = 70
        )
        // Lively, light, sweet young girl character
        GIRL -> PresetVoiceProfile(
            basePitchSemitones = 7.1f,
            baseFormantSemitones = 4.4f,
            chestGainDb = -4.4f,
            chestFreqHz = 175.0,
            warmthGainDb = 1.0f,
            warmthFreqHz = 330.0,
            presenceGainDb = 2.7f,
            presenceFreqHz = 3400.0,
            airGainDb = 2.2f,
            airFreqHz = 8000.0,
            deEssAmount = 0.28f,
            defaultPitchLevel = 88,
            defaultToneLevel = 82,
            defaultClarity = 68
        )
        // Rich, warm, mature adult woman voice
        WOMAN -> PresetVoiceProfile(
            basePitchSemitones = 4.3f,
            baseFormantSemitones = 2.7f,
            chestGainDb = -1.8f,
            chestFreqHz = 155.0,
            warmthGainDb = 1.9f,
            warmthFreqHz = 320.0,
            presenceGainDb = 1.7f,
            presenceFreqHz = 2650.0,
            airGainDb = 0.9f,
            airFreqHz = 6500.0,
            deEssAmount = 0.20f,
            defaultPitchLevel = 85,
            defaultToneLevel = 78,
            defaultClarity = 60
        )
    }

    fun defaultConfig(enabled: Boolean = false): VoiceChangerConfig {
        val p = profile()
        return VoiceChangerConfig(
            enabled = enabled,
            preset = this,
            pitchLevelPercent = p.defaultPitchLevel,
            toneLevelPercent = p.defaultToneLevel,
            clarityPercent = p.defaultClarity
        )
    }
}

internal data class PresetVoiceProfile(
    val basePitchSemitones: Float,
    val baseFormantSemitones: Float,
    val chestGainDb: Float,
    val chestFreqHz: Double,
    val warmthGainDb: Float,
    val warmthFreqHz: Double,
    val presenceGainDb: Float,
    val presenceFreqHz: Double,
    val airGainDb: Float,
    val airFreqHz: Double,
    val deEssAmount: Float,
    val defaultPitchLevel: Int,
    val defaultToneLevel: Int,
    val defaultClarity: Int
)

/**
 * Voice Changer configuration with ONLY the 3 user-adjustable controls
 * plus the independent ON/OFF toggle (default OFF) and 5 fixed presets.
 */
data class VoiceChangerConfig(
    val enabled: Boolean = false,
    val preset: VoicePreset = VoicePreset.NORMAL,
    val pitchLevelPercent: Int = 0,
    val toneLevelPercent: Int = 50,
    val clarityPercent: Int = 50
) {
    companion object {
        const val MIN_LEVEL_PCT = 0
        const val MAX_LEVEL_PCT = 100
        const val STEP_LEVEL_PCT = 5
    }
}

/**
 * Real-time, zero-allocation natural Voice Changer DSP operating at 48,000 Hz.
 *
 * Architecture:
 *  - Independent module: when [VoiceChangerConfig.enabled] is false, bypasses completely
 *    with bit-clean original microphone audio output (zero delay, zero EQ, zero artifacts).
 *  - When enabled:
 *    1. YIN (CMNDF) real-time fundamental pitch & voicing confidence detector (75 Hz - 500 Hz).
 *       Separates voiced periodic speech from unvoiced consonants (/s/, /sh/, /f/, breaths)
 *       so fricatives never receive metallic buzz or grain repetition.
 *    2. Formant-Warped Time-Domain Pitch-Synchronous Overlap-Add (TD-PSOLA):
 *       - Locks synthesis grains to real glottal pulse epochs for natural vocal coherence.
 *       - Scales vocal tract formants cleanly in the time domain via 4-point Catmull-Rom
 *         cubic resampling inside each pitch-synchronous grain (no STFT bin holes or phasiness).
 *       - Normalized Overlap-Add eliminates amplitude tremolo/flutter.
 *    3. Preset-calibrated 4-band Vocal Tract Tone EQ (Chest trim, Warmth, Presence, Air) +
 *       gentle dynamic sibilance control, scaled by [VoiceChangerConfig.toneLevelPercent].
 *    4. Natural Speech Clarity Optimizer scaled by [VoiceChangerConfig.clarityPercent].
 *    5. Smooth enable/disable crossfade & soft-knee brickwall limiter.
 */
class VoiceChangerDsp(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    companion object {
        private const val RING_SIZE = 4096
        private const val RING_MASK = RING_SIZE - 1

        // Analysis & latency geometry (48 kHz)
        // Center is 720 samples behind write head; max grain half-width is 500 samples;
        // output read head is 1480 samples behind write head (~30.8 ms total when active).
        private const val LOOKBACK_SAMPLES = 720
        private const val OUTPUT_DELAY_SAMPLES = 1480
        private const val MAX_HALF_WIN = 500f

        // YIN pitch detector params (2x decimated -> 24 kHz)
        private const val YIN_HOP_SAMPLES = 256 // ~5.33 ms
        private const val YIN_WINDOW_DEC = 240  // 10 ms at 24 kHz
        private const val YIN_TAU_MIN = 48      // 500 Hz
        private const val YIN_TAU_MAX = 320     // 75 Hz
    }

    @Volatile
    private var targetConfig = VoiceChangerConfig()

    // Circular streaming buffers
    private val inRing = FloatArray(RING_SIZE)
    private val outOlaRing = FloatArray(RING_SIZE)
    private val outWeightRing = FloatArray(RING_SIZE)
    private val yinDiff = FloatArray(YIN_TAU_MAX + 2)
    private val yinCmndf = FloatArray(YIN_TAU_MAX + 2)

    private var writeCounter: Long = 0L
    private var yinHopCounter: Int = 0
    private var nextSynthCenter: Double = 0.0
    private var lastInputEpoch: Double = -10000.0
    private var isInitializedStream: Boolean = false

    // YIN pitch tracking state
    private var trackedPeriodSamples: Float = 320f // ~150 Hz initial guess
    private var lastCandidatePeriod: Float = 320f
    private var octaveConfirmCount: Int = 0
    private var voicingConfidence: Float = 0f

    // Smoothed DSP parameters
    private var smoothPitchRatio = 1.0f
    private var smoothFormantRatio = 1.0f
    private var smoothToneStrength = 0.5f
    private var smoothClarityStrength = 0.5f
    private var smoothActiveMix = 0.0f // 0 = completely bypassed

    // Vocal-tract Tone EQ biquads (chest, warmth, presence, air)
    private val eqChest = BiquadFilter()
    private val eqWarmth = BiquadFilter()
    private val eqPresence = BiquadFilter()
    private val eqAir = BiquadFilter()

    // Clarity EQ & gentle de-esser biquad
    private val eqClaritySub = BiquadFilter()
    private val eqClarityPresence = BiquadFilter()
    private val eqDeEssShelf = BiquadFilter()
    private var sibilanceEnv = 0f

    private var lastEqPreset = VoicePreset.NORMAL
    private var lastEqTone = Float.NaN
    private var lastEqClarity = Float.NaN

    init {
        reset()
    }

    fun updateConfig(config: VoiceChangerConfig) {
        targetConfig = config.copy(
            pitchLevelPercent = config.pitchLevelPercent.coerceIn(
                VoiceChangerConfig.MIN_LEVEL_PCT,
                VoiceChangerConfig.MAX_LEVEL_PCT
            ),
            toneLevelPercent = config.toneLevelPercent.coerceIn(
                VoiceChangerConfig.MIN_LEVEL_PCT,
                VoiceChangerConfig.MAX_LEVEL_PCT
            ),
            clarityPercent = config.clarityPercent.coerceIn(
                VoiceChangerConfig.MIN_LEVEL_PCT,
                VoiceChangerConfig.MAX_LEVEL_PCT
            )
        )
    }

    fun getConfig(): VoiceChangerConfig = targetConfig

    fun reset() {
        inRing.fill(0f)
        outOlaRing.fill(0f)
        outWeightRing.fill(0f)
        yinDiff.fill(0f)
        yinCmndf.fill(0f)
        writeCounter = 0L
        yinHopCounter = 0
        nextSynthCenter = 0.0
        lastInputEpoch = -10000.0
        isInitializedStream = false
        trackedPeriodSamples = (sampleRateHz / 150f)
        lastCandidatePeriod = trackedPeriodSamples
        octaveConfirmCount = 0
        voicingConfidence = 0f
        sibilanceEnv = 0f

        eqChest.reset()
        eqWarmth.reset()
        eqPresence.reset()
        eqAir.reset()
        eqClaritySub.reset()
        eqClarityPresence.reset()
        eqDeEssShelf.reset()

        val cfg = targetConfig
        val profile = cfg.preset.profile()
        val pFactor = cfg.pitchLevelPercent / 100f
        smoothPitchRatio = 2f.pow((profile.basePitchSemitones * pFactor) / 12f)
        smoothFormantRatio = 2f.pow((profile.baseFormantSemitones * pFactor) / 12f)
        smoothToneStrength = cfg.toneLevelPercent / 100f
        smoothClarityStrength = cfg.clarityPercent / 100f
        smoothActiveMix = if (cfg.enabled) 1f else 0f

        updatePostFilters(cfg.preset, smoothToneStrength, smoothClarityStrength, force = true)
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return

        val cfg = targetConfig
        // Complete zero-overhead bypass when OFF and crossfade has settled
        if (!cfg.enabled && smoothActiveMix < 0.0005f) {
            smoothActiveMix = 0f
            return
        }

        val profile = cfg.preset.profile()
        val pitchScale = cfg.pitchLevelPercent / 100f
        val targetPitchRatio = 2f.pow((profile.basePitchSemitones * pitchScale) / 12f)
        val targetFormantRatio = 2f.pow((profile.baseFormantSemitones * pitchScale) / 12f)
        val targetToneStrength = cfg.toneLevelPercent / 100f
        val targetClarityStrength = cfg.clarityPercent / 100f
        val targetActiveMix = if (cfg.enabled) 1f else 0f

        val paramAlpha = 0.003f
        val fadeAlpha = 0.006f // ~3.5 ms smooth enable/disable crossfade

        for (i in 0 until count) {
            smoothPitchRatio += paramAlpha * (targetPitchRatio - smoothPitchRatio)
            smoothFormantRatio += paramAlpha * (targetFormantRatio - smoothFormantRatio)
            smoothToneStrength += paramAlpha * (targetToneStrength - smoothToneStrength)
            smoothClarityStrength += paramAlpha * (targetClarityStrength - smoothClarityStrength)
            smoothActiveMix += fadeAlpha * (targetActiveMix - smoothActiveMix)

            val raw = buffer[i]
            val n = writeCounter
            inRing[(n.toInt()) and RING_MASK] = raw

            if (!isInitializedStream) {
                nextSynthCenter = (n - LOOKBACK_SAMPLES).toDouble()
                isInitializedStream = true
            }

            // Update YIN pitch & voicing detector every YIN_HOP_SAMPLES
            yinHopCounter++
            if (yinHopCounter >= YIN_HOP_SAMPLES) {
                yinHopCounter = 0
                if (n >= (YIN_WINDOW_DEC * 2 + YIN_TAU_MAX * 2 + 16)) {
                    detectPitchYin(n)
                }
            }

            // Trigger pitch-synchronous synthesis grain(s) when center reaches nextSynthCenter
            val curCenter = (n - LOOKBACK_SAMPLES).toDouble()
            while (curCenter >= nextSynthCenter && n >= OUTPUT_DELAY_SAMPLES) {
                val stride = synthesizeGrain(
                    curCenter = curCenter,
                    pitchRatio = smoothPitchRatio,
                    formantRatio = smoothFormantRatio
                )
                nextSynthCenter += stride
                // Guard against runaway catch-up on large pauses
                if (curCenter - nextSynthCenter > stride * 3.0) {
                    nextSynthCenter = curCenter
                }
            }

            // Read latency-aligned processed sample and normalized OLA synthesis
            val outPos = n - OUTPUT_DELAY_SAMPLES
            val outIdx = (outPos.toInt()) and RING_MASK
            val w = outWeightRing[outIdx]
            val synth = if (w > 1e-4f) outOlaRing[outIdx] / w else 0f
            outOlaRing[outIdx] = 0f
            outWeightRing[outIdx] = 0f

            // If pitch & formant shift are virtually unity (e.g. Normal preset at 0% Pitch Level),
            // align wet input directly without grain resampling artifacts.
            val dryDelayed = if (outPos >= 0L) inRing[outIdx] else 0f
            val isNeutralPitchFormant =
                abs(smoothPitchRatio - 1.0f) < 0.004f && abs(smoothFormantRatio - 1.0f) < 0.004f
            val pitchFormantOut = if (isNeutralPitchFormant) dryDelayed else synth

            // Update post-filter coefficients only when values change meaningfully
            if (cfg.preset != lastEqPreset ||
                abs(smoothToneStrength - lastEqTone) > 0.015f ||
                abs(smoothClarityStrength - lastEqClarity) > 0.015f
            ) {
                updatePostFilters(cfg.preset, smoothToneStrength, smoothClarityStrength, force = false)
            }

            // Apply Preset Vocal-Tract Tone EQ & Clarity
            var processed = pitchFormantOut
            if (smoothToneStrength > 0.005f) {
                processed = eqChest.process(processed)
                processed = eqWarmth.process(processed)
                processed = eqPresence.process(processed)
                processed = eqAir.process(processed)
            }
            if (smoothClarityStrength > 0.005f) {
                processed = eqClaritySub.process(processed)
                processed = eqClarityPresence.process(processed)
            }
            if (profile.deEssAmount > 0.01f && smoothToneStrength > 0.1f) {
                // Gentle dynamic sibilance polish for upward-shifted voices
                processed = eqDeEssShelf.process(processed)
            }

            // Blend seamlessly between original and processed according to Voice Changer ON/OFF
            val active = smoothActiveMix.coerceIn(0f, 1f)
            val blended = if (active >= 0.999f) {
                processed
            } else {
                raw * (1f - active) + processed * active
            }

            buffer[i] = if (active > 0.001f) softLimit(blended) else raw
            writeCounter = n + 1L
        }
    }

    private fun detectPitchYin(writeHead: Long) {
        // Compute over [baseIdx .. baseIdx + 2*YIN_WINDOW_DEC] with 2x decimation
        val span = (YIN_WINDOW_DEC + YIN_TAU_MAX) * 2
        val basePos = writeHead - span

        // Quick RMS silence check
        var energySum = 0f
        for (j in 0 until YIN_WINDOW_DEC) {
            val s = inRing[((basePos + j * 2).toInt()) and RING_MASK]
            energySum += s * s
        }
        val rms = sqrt(energySum / YIN_WINDOW_DEC)
        if (rms < 0.002f) {
            voicingConfidence *= 0.8f
            return
        }

        // Difference function d(tau) at 24 kHz
        yinDiff[0] = 0f
        for (tau in 1..YIN_TAU_MAX) {
            var acc = 0f
            val lag2 = tau * 2
            for (j in 0 until YIN_WINDOW_DEC) {
                val p1 = ((basePos + j * 2).toInt()) and RING_MASK
                val p2 = ((basePos + j * 2 + lag2).toInt()) and RING_MASK
                val diff = inRing[p1] - inRing[p2]
                acc += diff * diff
            }
            yinDiff[tau] = acc
        }

        // Cumulative mean normalized difference function (CMNDF)
        yinCmndf[0] = 1f
        var runningSum = 0f
        for (tau in 1..YIN_TAU_MAX) {
            runningSum += yinDiff[tau]
            yinCmndf[tau] = if (runningSum > 1e-9f) {
                yinDiff[tau] * tau / runningSum
            } else {
                1f
            }
        }

        // Find first local minimum below 0.20, or global minimum if < 0.42
        var bestTau = -1
        var bestVal = 10f
        for (tau in YIN_TAU_MIN until YIN_TAU_MAX) {
            val v = yinCmndf[tau]
            if (v < 0.20f && v <= yinCmndf[tau - 1] && v <= yinCmndf[tau + 1]) {
                bestTau = tau
                bestVal = v
                break
            }
            if (v < bestVal) {
                bestVal = v
                bestTau = tau
            }
        }

        if (bestTau < YIN_TAU_MIN || bestVal > 0.44f) {
            voicingConfidence = (voicingConfidence * 0.7f).coerceAtLeast(0f)
            return
        }

        // Parabolic interpolation for sub-sample period accuracy
        val s0 = yinCmndf[(bestTau - 1).coerceAtLeast(1)]
        val s1 = yinCmndf[bestTau]
        val s2 = yinCmndf[(bestTau + 1).coerceAtMost(YIN_TAU_MAX)]
        val denom = 2f * (2f * s1 - s2 - s0)
        val delta = if (abs(denom) > 1e-6f) ((s0 - s2) / denom).coerceIn(-0.5f, 0.5f) else 0f
        val detectedSamples48k = ((bestTau + delta) * 2f).coerceIn(96f, 640f)

        val conf = ((0.44f - bestVal) / 0.34f).coerceIn(0f, 1f)
        voicingConfidence += 0.45f * (conf - voicingConfidence)

        // Median / octave-jump guard
        val ratio = detectedSamples48k / trackedPeriodSamples
        if (ratio in 0.60f..1.65f) {
            octaveConfirmCount = 0
            trackedPeriodSamples += 0.40f * (detectedSamples48k - trackedPeriodSamples)
        } else {
            if (abs(detectedSamples48k - lastCandidatePeriod) / lastCandidatePeriod < 0.15f) {
                octaveConfirmCount++
                if (octaveConfirmCount >= 2) {
                    trackedPeriodSamples = detectedSamples48k
                    octaveConfirmCount = 0
                }
            } else {
                octaveConfirmCount = 1
            }
            lastCandidatePeriod = detectedSamples48k
        }
    }

    private fun synthesizeGrain(
        curCenter: Double,
        pitchRatio: Float,
        formantRatio: Float
    ): Double {
        val voiced = voicingConfidence > 0.28f
        val period = trackedPeriodSamples.toDouble()

        val grainCenter: Double
        val synthStride: Double
        val effectiveFormant: Float
        val halfWin: Float

        if (voiced) {
            // Advance input glottal epoch lock cleanly
            if (curCenter - lastInputEpoch > period * 1.6 || lastInputEpoch > curCenter + period) {
                lastInputEpoch = findPeakEpoch(curCenter - period * 0.45, curCenter + period * 0.45)
            } else {
                while (lastInputEpoch + period * 0.52 < curCenter) {
                    val expected = lastInputEpoch + period
                    lastInputEpoch = findPeakEpoch(expected - period * 0.18, expected + period * 0.18)
                }
            }
            grainCenter = lastInputEpoch
            synthStride = (period / pitchRatio.coerceIn(0.45f, 2.5f)).coerceIn(72.0, 680.0)
            effectiveFormant = formantRatio
            // Keep grain inside ~1 pitch period when upshifting, while guaranteeing >= 1.15x overlap
            val minOverlapWin = (synthStride * 1.18).toFloat().coerceAtLeast(110f)
            val maxSinglePulseWin = ((period * 1.05) / effectiveFormant.coerceAtLeast(0.5f)).toFloat()
            halfWin = max(minOverlapWin, min(minOverlapWin * 1.1f, maxSinglePulseWin))
                .coerceIn(96f, MAX_HALF_WIN)
        } else {
            // Unvoiced speech (/s/, /sh/, /f/, breaths): preserve natural timing & consonant air!
            grainCenter = curCenter
            synthStride = 160.0 // 3.33 ms clean unvoiced stride
            effectiveFormant = 1.0f + (formantRatio - 1.0f) * 0.42f
            halfWin = 200f
        }

        val centerOut = curCenter.roundToInt().toLong()
        val fracOut = (curCenter - centerOut).toFloat()
        val intHalf = floor(halfWin).toInt()
        val invHalf = 1.0f / halfWin

        for (m in -intHalf..intHalf) {
            val dist = abs(m - fracOut)
            if (dist >= halfWin) continue

            val w = 0.5f * (1.0f + cos(PI.toFloat() * dist * invHalf))
            val inPos = grainCenter + (m - fracOut) * effectiveFormant
            val sample = sampleCubic(inPos)

            val outIdx = ((centerOut + m).toInt()) and RING_MASK
            outOlaRing[outIdx] += sample * w
            outWeightRing[outIdx] += w
        }

        return synthStride
    }

    private fun findPeakEpoch(startPos: Double, endPos: Double): Double {
        val lo = floor(startPos).toLong()
        val hi = floor(endPos).toLong()
        var bestPos = (lo + hi) * 0.5
        var bestScore = Float.NEGATIVE_INFINITY

        var p = lo
        while (p <= hi) {
            val idx0 = ((p - 1).toInt()) and RING_MASK
            val idx1 = (p.toInt()) and RING_MASK
            val idx2 = ((p + 1).toInt()) and RING_MASK
            // Smoothed instantaneous glottal peak preference
            val v = 0.25f * inRing[idx0] + 0.50f * inRing[idx1] + 0.25f * inRing[idx2]
            if (v > bestScore) {
                bestScore = v
                bestPos = p.toDouble()
            }
            p++
        }
        return bestPos
    }

    /**
     * 4-point Catmull-Rom cubic interpolation for artifact-free formant scaling.
     */
    private fun sampleCubic(pos: Double): Float {
        val i1 = floor(pos).toLong()
        val t = (pos - i1).toFloat()

        val y0 = inRing[((i1 - 1L).toInt()) and RING_MASK]
        val y1 = inRing[(i1.toInt()) and RING_MASK]
        val y2 = inRing[((i1 + 1L).toInt()) and RING_MASK]
        val y3 = inRing[((i1 + 2L).toInt()) and RING_MASK]

        val c0 = y1
        val c1 = 0.5f * (y2 - y0)
        val c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3
        val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)

        return ((c3 * t + c2) * t + c1) * t + c0
    }

    private fun updatePostFilters(
        preset: VoicePreset,
        toneStrength: Float,
        clarityStrength: Float,
        force: Boolean
    ) {
        if (!force &&
            preset == lastEqPreset &&
            abs(toneStrength - lastEqTone) <= 0.015f &&
            abs(clarityStrength - lastEqClarity) <= 0.015f
        ) {
            return
        }
        lastEqPreset = preset
        lastEqTone = toneStrength
        lastEqClarity = clarityStrength

        val p = preset.profile()

        eqChest.setPeaking(sampleRateHz, p.chestFreqHz, 0.9, (p.chestGainDb * toneStrength).toDouble())
        eqWarmth.setPeaking(sampleRateHz, p.warmthFreqHz, 1.0, (p.warmthGainDb * toneStrength).toDouble())
        eqPresence.setPeaking(sampleRateHz, p.presenceFreqHz, 1.1, (p.presenceGainDb * toneStrength).toDouble())
        eqAir.setHighShelf(sampleRateHz, p.airFreqHz, (p.airGainDb * toneStrength).toDouble())

        // Gentle sibilance smoothing around 6.4 kHz for upward-shifted voices
        val deEssDb = -2.8 * p.deEssAmount * toneStrength
        eqDeEssShelf.setPeaking(sampleRateHz, 6400.0, 1.8, deEssDb)

        // Clarity optimizer: clean sub-mud (-1.8 dB @ 130 Hz max) + natural speech articulation (+3.2 dB @ 2.8 kHz max)
        eqClaritySub.setLowShelf(sampleRateHz, 130.0, (-1.8 * clarityStrength).toDouble())
        eqClarityPresence.setPeaking(sampleRateHz, 2850.0, 0.85, (3.2 * clarityStrength).toDouble())
    }

    private fun softLimit(x: Float): Float {
        val absX = abs(x)
        return if (absX <= 0.85f) {
            x
        } else {
            val excess = (absX - 0.85f) / 0.10f
            val limited = 0.85f + 0.10f * tanh(excess)
            if (x >= 0f) limited else -limited
        }
    }
}

/**
 * Reusable zero-allocation 2nd-order RBJ biquad filter.
 */
internal class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun reset() {
        z1 = 0f
        z2 = 0f
    }

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun setBypass() {
        b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
    }

    fun setLowPass(sampleRateHz: Int, freqHz: Double, q: Double) {
        val clampedFreq = freqHz.coerceIn(20.0, sampleRateHz * 0.45)
        val clampedQ = q.coerceIn(0.1, 10.0)
        val w0 = 2.0 * PI * clampedFreq / sampleRateHz
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * clampedQ)
        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
        b1 = ((1.0 - cosW0) / a0).toFloat()
        b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun setHighPass(sampleRateHz: Int, freqHz: Double, q: Double) {
        val clampedFreq = freqHz.coerceIn(20.0, sampleRateHz * 0.45)
        val clampedQ = q.coerceIn(0.1, 10.0)
        val w0 = 2.0 * PI * clampedFreq / sampleRateHz
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * clampedQ)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        b1 = (-(1.0 + cosW0) / a0).toFloat()
        b2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun setBandPass(sampleRateHz: Int, freqHz: Double, q: Double) {
        val clampedFreq = freqHz.coerceIn(20.0, sampleRateHz * 0.45)
        val clampedQ = q.coerceIn(0.1, 10.0)
        val w0 = 2.0 * PI * clampedFreq / sampleRateHz
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * clampedQ)
        val a0 = 1.0 + alpha
        b0 = (alpha / a0).toFloat()
        b1 = 0f
        b2 = (-alpha / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun setPeaking(sampleRateHz: Int, freqHz: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.02) {
            setBypass()
            return
        }
        val clampedFreq = freqHz.coerceIn(20.0, sampleRateHz * 0.45)
        val clampedQ = q.coerceIn(0.1, 10.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * clampedFreq / sampleRateHz
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * clampedQ)
        val a0 = 1.0 + alpha / a
        b0 = ((1.0 + alpha * a) / a0).toFloat()
        b1 = (-2.0 * cosW0 / a0).toFloat()
        b2 = ((1.0 - alpha * a) / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha / a) / a0).toFloat()
    }

    fun setLowShelf(sampleRateHz: Int, freqHz: Double, gainDb: Double, q: Double = 0.70710678) {
        if (abs(gainDb) < 0.02) {
            setBypass()
            return
        }
        val clampedFreq = freqHz.coerceIn(20.0, sampleRateHz * 0.45)
        val clampedQ = q.coerceIn(0.2, 4.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * clampedFreq / sampleRateHz
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * clampedQ)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        a2 = (((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
    }

    fun setHighShelf(sampleRateHz: Int, freqHz: Double, gainDb: Double, q: Double = 0.70710678) {
        if (abs(gainDb) < 0.02) {
            setBypass()
            return
        }
        val clampedFreq = freqHz.coerceIn(20.0, sampleRateHz * 0.45)
        val clampedQ = q.coerceIn(0.2, 4.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * clampedFreq / sampleRateHz
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * clampedQ)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        a2 = (((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
    }

    fun magnitudeDbAt(sampleRateHz: Int, freqHz: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRateHz
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)
        val numRe = b0 + b1 * cosW + b2 * cos2W
        val numIm = -(b1 * sinW + b2 * sin2W)
        val denRe = 1.0 + a1 * cosW + a2 * cos2W
        val denIm = -(a1 * sinW + a2 * sin2W)
        val numMagSq = numRe * numRe + numIm * numIm
        val denMagSq = (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)
        val mag = sqrt(numMagSq / denMagSq).coerceAtLeast(1e-6)
        return 20.0 * kotlin.math.log10(mag)
    }
}
