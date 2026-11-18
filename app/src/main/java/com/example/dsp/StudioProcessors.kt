package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time 48 kHz YIN + TD-PSOLA Auto-Tune / Pitch Correction processor.
 * Supports Chromatic, Major, Minor scales, 12 keys, Automatic Key Detection,
 * Correction Strength, Retune Speed, Humanize (vibrato preservation), Note Transition,
 * and Reference Tuning (A4 = 415..465 Hz).
 */
class PitchCorrectionProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    companion object {
        private const val RING_SIZE = 4096
        private const val RING_MASK = RING_SIZE - 1
        private const val LOOKBACK_SAMPLES = 720
        private const val OUTPUT_DELAY_SAMPLES = 1480
        private const val YIN_HOP_SAMPLES = 256
        private const val YIN_WINDOW_DEC = 240
        private const val YIN_TAU_MIN = 44 // ~545 Hz
        private const val YIN_TAU_MAX = 320 // 75 Hz
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }

    @Volatile
    private var config = PitchCorrectionConfig()

    @Volatile
    var lastDetectedNoteLabel: String = "--"
        private set

    @Volatile
    var lastAutoDetectedKey: MusicalKey = MusicalKey.C
        private set

    private val inRing = FloatArray(RING_SIZE)
    private val outOlaRing = FloatArray(RING_SIZE)
    private val outWeightRing = FloatArray(RING_SIZE)
    private val yinDiff = FloatArray(YIN_TAU_MAX + 2)
    private val yinCmndf = FloatArray(YIN_TAU_MAX + 2)
    private val pitchClassHistogram = FloatArray(12)

    private var writeCounter = 0L
    private var yinHopCounter = 0
    private var nextSynthCenter = 0.0
    private var lastInputEpoch = -10000.0
    private var isInitializedStream = false

    private var trackedPeriodSamples = 320f
    private var voicingConfidence = 0f
    private var smoothedMidiFloat = 60f
    private var slowCenterMidi = 60f
    private var targetSnappedMidi = 60
    private var smoothCorrectionRatio = 1.0f
    private var smoothActiveMix = 0.0f

    init {
        reset()
    }

    fun updateConfig(newConfig: PitchCorrectionConfig) {
        config = newConfig.copy(
            correctionStrengthPercent = newConfig.correctionStrengthPercent.coerceIn(0, 100),
            retuneSpeedMs = newConfig.retuneSpeedMs.coerceIn(0, 200),
            humanizePercent = newConfig.humanizePercent.coerceIn(0, 100),
            noteTransitionMs = newConfig.noteTransitionMs.coerceIn(0, 150),
            referenceTuningHz = newConfig.referenceTuningHz.coerceIn(415f, 465f)
        )
    }

    fun getConfig(): PitchCorrectionConfig = config

    fun reset() {
        inRing.fill(0f)
        outOlaRing.fill(0f)
        outWeightRing.fill(0f)
        yinDiff.fill(0f)
        yinCmndf.fill(0f)
        pitchClassHistogram.fill(0f)
        writeCounter = 0L
        yinHopCounter = 0
        nextSynthCenter = 0.0
        lastInputEpoch = -10000.0
        isInitializedStream = false
        trackedPeriodSamples = sampleRateHz / 150f
        voicingConfidence = 0f
        smoothedMidiFloat = 60f
        slowCenterMidi = 60f
        targetSnappedMidi = 60
        smoothCorrectionRatio = 1.0f
        smoothActiveMix = if (config.enabled) 1f else 0f
        lastDetectedNoteLabel = "--"
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config
        if (!cfg.enabled && smoothActiveMix < 0.0005f) {
            smoothActiveMix = 0f
            return
        }

        val targetActiveMix = if (cfg.enabled) 1f else 0f
        val fadeAlpha = 0.006f
        val retuneTauSec = (cfg.retuneSpeedMs.coerceAtLeast(2) / 1000.0)
        val transitionTauSec = (cfg.noteTransitionMs.coerceAtLeast(2) / 1000.0)
        val combinedTauSec = (retuneTauSec * 0.7 + transitionTauSec * 0.3).coerceAtLeast(0.003)
        val ratioAlpha = (1.0 - exp(-1.0 / (combinedTauSec * sampleRateHz))).toFloat()

        for (i in 0 until count) {
            smoothActiveMix += fadeAlpha * (targetActiveMix - smoothActiveMix)
            val raw = buffer[i]
            val n = writeCounter
            inRing[(n.toInt()) and RING_MASK] = raw

            if (!isInitializedStream) {
                nextSynthCenter = (n - LOOKBACK_SAMPLES).toDouble()
                isInitializedStream = true
            }

            yinHopCounter++
            if (yinHopCounter >= YIN_HOP_SAMPLES) {
                yinHopCounter = 0
                if (cfg.pitchDetectionEnabled && n >= (YIN_WINDOW_DEC * 2 + YIN_TAU_MAX * 2 + 16)) {
                    detectAndComputeTargetRatio(n, cfg)
                } else if (!cfg.pitchDetectionEnabled) {
                    voicingConfidence *= 0.85f
                }
            }

            val detectedHz = sampleRateHz / trackedPeriodSamples.coerceAtLeast(48f)
            val targetRatio = computeTargetRatioFromDetected(detectedHz, cfg)
            smoothCorrectionRatio += ratioAlpha * (targetRatio - smoothCorrectionRatio)

            val curCenter = (n - LOOKBACK_SAMPLES).toDouble()
            while (curCenter >= nextSynthCenter && n >= OUTPUT_DELAY_SAMPLES) {
                val stride = synthesizeGrain(curCenter, smoothCorrectionRatio)
                nextSynthCenter += stride
                if (curCenter - nextSynthCenter > stride * 3.0) {
                    nextSynthCenter = curCenter
                }
            }

            val outPos = n - OUTPUT_DELAY_SAMPLES
            val outIdx = (outPos.toInt()) and RING_MASK
            val w = outWeightRing[outIdx]
            val synth = if (w > 1e-4f) outOlaRing[outIdx] / w else 0f
            outOlaRing[outIdx] = 0f
            outWeightRing[outIdx] = 0f

            val dryDelayed = if (outPos >= 0L) inRing[outIdx] else 0f
            val processed = if (abs(smoothCorrectionRatio - 1.0f) < 0.003f || !cfg.pitchDetectionEnabled) {
                dryDelayed
            } else {
                synth
            }

            val active = smoothActiveMix.coerceIn(0f, 1f)
            buffer[i] = if (active >= 0.999f) processed else raw * (1f - active) + processed * active
            writeCounter = n + 1L
        }
    }

    private fun detectAndComputeTargetRatio(writeHead: Long, cfg: PitchCorrectionConfig) {
        val span = (YIN_WINDOW_DEC + YIN_TAU_MAX) * 2
        val basePos = writeHead - span

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

        yinCmndf[0] = 1f
        var runningSum = 0f
        for (tau in 1..YIN_TAU_MAX) {
            runningSum += yinDiff[tau]
            yinCmndf[tau] = if (runningSum > 1e-9f) yinDiff[tau] * tau / runningSum else 1f
        }

        var bestTau = -1
        var bestVal = 10f
        for (tau in YIN_TAU_MIN until YIN_TAU_MAX) {
            val v = yinCmndf[tau]
            if (v < 0.22f && v <= yinCmndf[tau - 1] && v <= yinCmndf[tau + 1]) {
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
            voicingConfidence = (voicingConfidence * 0.72f).coerceAtLeast(0f)
            return
        }

        val s0 = yinCmndf[(bestTau - 1).coerceAtLeast(1)]
        val s1 = yinCmndf[bestTau]
        val s2 = yinCmndf[(bestTau + 1).coerceAtMost(YIN_TAU_MAX)]
        val denom = 2f * (2f * s1 - s2 - s0)
        val delta = if (abs(denom) > 1e-6f) ((s0 - s2) / denom).coerceIn(-0.5f, 0.5f) else 0f
        val period48k = ((bestTau + delta) * 2f).coerceIn(88f, 640f)

        val conf = ((0.44f - bestVal) / 0.34f).coerceIn(0f, 1f)
        voicingConfidence += 0.45f * (conf - voicingConfidence)
        trackedPeriodSamples += 0.45f * (period48k - trackedPeriodSamples)

        val freqHz = sampleRateHz / trackedPeriodSamples
        val midi = (69.0 + 12.0 * (ln(freqHz.toDouble() / cfg.referenceTuningHz.toDouble()) / ln(2.0))).toFloat()
        smoothedMidiFloat += 0.5f * (midi - smoothedMidiFloat)
        slowCenterMidi += 0.08f * (midi - slowCenterMidi)

        val pc = ((midi.roundToInt() % 12) + 12) % 12
        for (k in 0..11) pitchClassHistogram[k] *= 0.985f
        pitchClassHistogram[pc] += conf

        if (cfg.automaticKeyDetection) {
            lastAutoDetectedKey = estimateBestKey(cfg.scale)
        }
        val nearestMidi = midi.roundToInt()
        val noteIdx = ((nearestMidi % 12) + 12) % 12
        val octave = (nearestMidi / 12) - 1
        lastDetectedNoteLabel = "${NOTE_NAMES[noteIdx]}$octave"
    }

    private fun estimateBestKey(scale: MusicalScale): MusicalKey {
        var bestKey = MusicalKey.C
        var bestScore = -1f
        for (candidate in MusicalKey.entries) {
            var score = 0f
            for (interval in scale.intervals) {
                score += pitchClassHistogram[(candidate.semitoneOffset + interval) % 12]
            }
            if (score > bestScore) {
                bestScore = score
                bestKey = candidate
            }
        }
        return bestKey
    }

    private fun computeTargetRatioFromDetected(detectedHz: Float, cfg: PitchCorrectionConfig): Float {
        if (!cfg.enabled || !cfg.pitchDetectionEnabled || voicingConfidence < 0.28f) {
            return 1.0f
        }
        val activeKey = if (cfg.automaticKeyDetection) lastAutoDetectedKey else cfg.key
        val midi = (69.0 + 12.0 * (ln(detectedHz.toDouble() / cfg.referenceTuningHz.toDouble()) / ln(2.0))).toFloat()
        val snapped = snapMidiToScale(midi, activeKey, cfg.scale)
        targetSnappedMidi = snapped

        val strength = cfg.correctionStrengthPercent / 100f
        val humanize = cfg.humanizePercent / 100f
        // Vibrato deviation around slow note center; humanize preserves natural expressiveness
        val vibratoComponent = (midi - slowCenterMidi).coerceIn(-0.65f, 0.65f) * humanize * 0.65f
        val correctedMidi = midi + (snapped.toFloat() + vibratoComponent - midi) * strength
        val shiftSemitones = (correctedMidi - midi).coerceIn(-6f, 6f)
        return 2f.pow(shiftSemitones / 12f)
    }

    private fun snapMidiToScale(midi: Float, key: MusicalKey, scale: MusicalScale): Int {
        val baseNote = floor(midi.toDouble()).toInt()
        var bestNote = baseNote
        var bestDist = Float.MAX_VALUE
        for (candidate in (baseNote - 2)..(baseNote + 2)) {
            val relPc = (((candidate - key.semitoneOffset) % 12) + 12) % 12
            if (scale.intervals.contains(relPc)) {
                val dist = abs(midi - candidate)
                if (dist < bestDist) {
                    bestDist = dist
                    bestNote = candidate
                }
            }
        }
        return bestNote
    }

    private fun synthesizeGrain(curCenter: Double, pitchRatio: Float): Double {
        val voiced = voicingConfidence > 0.28f
        val period = trackedPeriodSamples.toDouble()
        val grainCenter: Double
        val synthStride: Double
        val halfWin: Float

        if (voiced) {
            if (curCenter - lastInputEpoch > period * 1.6 || lastInputEpoch > curCenter + period) {
                lastInputEpoch = findPeakEpoch(curCenter - period * 0.45, curCenter + period * 0.45)
            } else {
                while (lastInputEpoch + period * 0.52 < curCenter) {
                    val expected = lastInputEpoch + period
                    lastInputEpoch = findPeakEpoch(expected - period * 0.18, expected + period * 0.18)
                }
            }
            grainCenter = lastInputEpoch
            synthStride = (period / pitchRatio.coerceIn(0.6f, 1.8f)).coerceIn(72.0, 680.0)
            val minWin = (synthStride * 1.18).toFloat().coerceAtLeast(110f)
            halfWin = minWin.coerceIn(96f, 480f)
        } else {
            grainCenter = curCenter
            synthStride = 160.0
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
            val inPos = grainCenter + (m - fracOut)
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
            val v = 0.25f * inRing[((p - 1).toInt()) and RING_MASK] +
                0.50f * inRing[(p.toInt()) and RING_MASK] +
                0.25f * inRing[((p + 1).toInt()) and RING_MASK]
            if (v > bestScore) {
                bestScore = v
                bestPos = p.toDouble()
            }
            p++
        }
        return bestPos
    }

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
}

/**
 * Independent Pitch & Formant Shifter using Formant-Warped TD-PSOLA.
 */
class PitchFormantProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    companion object {
        private const val RING_SIZE = 4096
        private const val RING_MASK = RING_SIZE - 1
        private const val LOOKBACK_SAMPLES = 720
        private const val OUTPUT_DELAY_SAMPLES = 1480
        private const val YIN_HOP_SAMPLES = 256
        private const val YIN_WINDOW_DEC = 240
        private const val YIN_TAU_MIN = 48
        private const val YIN_TAU_MAX = 320
    }

    @Volatile
    private var config = PitchFormantConfig()

    private val inRing = FloatArray(RING_SIZE)
    private val outOlaRing = FloatArray(RING_SIZE)
    private val outWeightRing = FloatArray(RING_SIZE)
    private val yinDiff = FloatArray(YIN_TAU_MAX + 2)
    private val yinCmndf = FloatArray(YIN_TAU_MAX + 2)

    private var writeCounter = 0L
    private var yinHopCounter = 0
    private var nextSynthCenter = 0.0
    private var lastInputEpoch = -10000.0
    private var isInitializedStream = false
    private var trackedPeriodSamples = 320f
    private var voicingConfidence = 0f

    private var smoothPitchRatio = 1f
    private var smoothFormantRatio = 1f
    private var smoothActiveMix = 0f

    init {
        reset()
    }

    fun updateConfig(newConfig: PitchFormantConfig) {
        config = newConfig.copy(
            pitchShiftSt = newConfig.pitchShiftSt.coerceIn(-12f, 12f),
            formantShiftSt = newConfig.formantShiftSt.coerceIn(-12f, 12f),
            fineTuneCents = newConfig.fineTuneCents.coerceIn(-100, 100),
            dryWetPercent = newConfig.dryWetPercent.coerceIn(0, 100),
            outputGainDb = newConfig.outputGainDb.coerceIn(-12f, 12f)
        )
    }

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
        trackedPeriodSamples = sampleRateHz / 150f
        voicingConfidence = 0f
        smoothPitchRatio = 1f
        smoothFormantRatio = 1f
        smoothActiveMix = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config
        if (!cfg.enabled && smoothActiveMix < 0.0005f) {
            smoothActiveMix = 0f
            return
        }

        val totalPitchSt = cfg.pitchShiftSt + (cfg.fineTuneCents / 100f)
        val effectiveFormantSt = if (cfg.formantPreservation) {
            cfg.formantShiftSt
        } else {
            (cfg.formantShiftSt + totalPitchSt * 0.65f).coerceIn(-12f, 12f)
        }
        val targetPitchRatio = 2f.pow(totalPitchSt / 12f)
        val targetFormantRatio = 2f.pow(effectiveFormantSt / 12f)
        val targetActive = if (cfg.enabled) 1f else 0f
        val mix = cfg.dryWetPercent / 100f
        val outGain = 10f.pow(cfg.outputGainDb / 20f)

        for (i in 0 until count) {
            smoothPitchRatio += 0.004f * (targetPitchRatio - smoothPitchRatio)
            smoothFormantRatio += 0.004f * (targetFormantRatio - smoothFormantRatio)
            smoothActiveMix += 0.006f * (targetActive - smoothActiveMix)

            val raw = buffer[i]
            val n = writeCounter
            inRing[(n.toInt()) and RING_MASK] = raw

            if (!isInitializedStream) {
                nextSynthCenter = (n - LOOKBACK_SAMPLES).toDouble()
                isInitializedStream = true
            }

            yinHopCounter++
            if (yinHopCounter >= YIN_HOP_SAMPLES) {
                yinHopCounter = 0
                if (n >= (YIN_WINDOW_DEC * 2 + YIN_TAU_MAX * 2 + 16)) {
                    detectPitchYin(n)
                }
            }

            val curCenter = (n - LOOKBACK_SAMPLES).toDouble()
            while (curCenter >= nextSynthCenter && n >= OUTPUT_DELAY_SAMPLES) {
                val stride = synthesizeGrain(curCenter, smoothPitchRatio, smoothFormantRatio)
                nextSynthCenter += stride
                if (curCenter - nextSynthCenter > stride * 3.0) {
                    nextSynthCenter = curCenter
                }
            }

            val outPos = n - OUTPUT_DELAY_SAMPLES
            val outIdx = (outPos.toInt()) and RING_MASK
            val w = outWeightRing[outIdx]
            val synth = if (w > 1e-4f) outOlaRing[outIdx] / w else 0f
            outOlaRing[outIdx] = 0f
            outWeightRing[outIdx] = 0f

            val dryDelayed = if (outPos >= 0L) inRing[outIdx] else 0f
            val neutral = abs(smoothPitchRatio - 1f) < 0.004f && abs(smoothFormantRatio - 1f) < 0.004f
            val wet = if (neutral) dryDelayed else synth
            val mixed = (raw * (1f - mix) + wet * mix) * outGain
            val active = smoothActiveMix.coerceIn(0f, 1f)
            buffer[i] = if (active >= 0.999f) mixed else raw * (1f - active) + mixed * active
            writeCounter = n + 1L
        }
    }

    private fun detectPitchYin(writeHead: Long) {
        val span = (YIN_WINDOW_DEC + YIN_TAU_MAX) * 2
        val basePos = writeHead - span
        var energySum = 0f
        for (j in 0 until YIN_WINDOW_DEC) {
            val s = inRing[((basePos + j * 2).toInt()) and RING_MASK]
            energySum += s * s
        }
        if (sqrt(energySum / YIN_WINDOW_DEC) < 0.002f) {
            voicingConfidence *= 0.8f
            return
        }
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
        yinCmndf[0] = 1f
        var runningSum = 0f
        for (tau in 1..YIN_TAU_MAX) {
            runningSum += yinDiff[tau]
            yinCmndf[tau] = if (runningSum > 1e-9f) yinDiff[tau] * tau / runningSum else 1f
        }
        var bestTau = -1
        var bestVal = 10f
        for (tau in YIN_TAU_MIN until YIN_TAU_MAX) {
            val v = yinCmndf[tau]
            if (v < 0.22f && v <= yinCmndf[tau - 1] && v <= yinCmndf[tau + 1]) {
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
        val period48k = (bestTau * 2f).coerceIn(96f, 640f)
        val conf = ((0.44f - bestVal) / 0.34f).coerceIn(0f, 1f)
        voicingConfidence += 0.45f * (conf - voicingConfidence)
        trackedPeriodSamples += 0.4f * (period48k - trackedPeriodSamples)
    }

    private fun synthesizeGrain(curCenter: Double, pitchRatio: Float, formantRatio: Float): Double {
        val voiced = voicingConfidence > 0.28f
        val period = trackedPeriodSamples.toDouble()
        val grainCenter: Double
        val synthStride: Double
        val effFormant: Float
        val halfWin: Float

        if (voiced) {
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
            effFormant = formantRatio
            val minOverlapWin = (synthStride * 1.18).toFloat().coerceAtLeast(110f)
            val maxSinglePulseWin = ((period * 1.05) / effFormant.coerceAtLeast(0.5f)).toFloat()
            halfWin = max(minOverlapWin, min(minOverlapWin * 1.1f, maxSinglePulseWin)).coerceIn(96f, 500f)
        } else {
            grainCenter = curCenter
            synthStride = 160.0
            effFormant = 1.0f + (formantRatio - 1.0f) * 0.42f
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
            val inPos = grainCenter + (m - fracOut) * effFormant
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
            val v = 0.25f * inRing[((p - 1).toInt()) and RING_MASK] +
                0.50f * inRing[(p.toInt()) and RING_MASK] +
                0.25f * inRing[((p + 1).toInt()) and RING_MASK]
            if (v > bestScore) {
                bestScore = v
                bestPos = p.toDouble()
            }
            p++
        }
        return bestPos
    }

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
}

/**
 * Soft-knee feed-forward dynamic Compressor at 48,000 Hz.
 */
class CompressionProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    @Volatile
    private var config = CompressionConfig()

    @Volatile
    var lastGainReductionDb: Float = 0f
        private set

    private var envDb = -60f
    private var smoothActive = 0f

    fun updateConfig(newConfig: CompressionConfig) {
        config = newConfig.copy(
            thresholdDb = newConfig.thresholdDb.coerceIn(-60f, 0f),
            ratio = newConfig.ratio.coerceIn(1f, 20f),
            attackMs = newConfig.attackMs.coerceIn(0.5f, 100f),
            releaseMs = newConfig.releaseMs.coerceIn(10f, 1000f),
            kneeDb = newConfig.kneeDb.coerceIn(0f, 24f),
            makeupGainDb = newConfig.makeupGainDb.coerceIn(0f, 24f),
            outputGainDb = newConfig.outputGainDb.coerceIn(-12f, 12f)
        )
    }

    fun reset() {
        envDb = -60f
        lastGainReductionDb = 0f
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            lastGainReductionDb = 0f
            return
        }

        val attackCoeff = exp(-1.0 / ((cfg.attackMs * 0.001) * sampleRateHz)).toFloat()
        val releaseCoeff = exp(-1.0 / ((cfg.releaseMs * 0.001) * sampleRateHz)).toFloat()
        val targetActive = if (cfg.enabled) 1f else 0f
        val makeupPlusOut = 10f.pow((cfg.makeupGainDb + cfg.outputGainDb) / 20f)
        val slope = 1f - (1f / cfg.ratio)
        val halfKnee = cfg.kneeDb * 0.5f
        var maxGrDb = 0f

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            val dry = buffer[i]
            val levelDb = 20f * log10(abs(dry).coerceAtLeast(1e-5f))

            envDb = if (levelDb > envDb) {
                attackCoeff * envDb + (1f - attackCoeff) * levelDb
            } else {
                releaseCoeff * envDb + (1f - releaseCoeff) * levelDb
            }

            val overshoot = envDb - cfg.thresholdDb
            val reductionDb = when {
                cfg.kneeDb > 0.1f && overshoot > -halfKnee && overshoot < halfKnee -> {
                    val x = overshoot + halfKnee
                    slope * (x * x) / (2f * cfg.kneeDb)
                }
                overshoot > 0f -> slope * overshoot
                else -> 0f
            }
            if (reductionDb > maxGrDb) maxGrDb = reductionDb

            val compGain = 10f.pow(-reductionDb / 20f) * makeupPlusOut
            val wet = dry * compGain
            buffer[i] = dry * (1f - smoothActive) + wet * smoothActive
        }
        lastGainReductionDb = if (cfg.enabled) -maxGrDb else 0f
    }
}

/**
 * Analog-modeled Tube/Tape Saturation with DC-blocker, Tone tilt, Harmonic blend, and Output Level.
 */
class SaturationProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    @Volatile
    private var config = SaturationConfig()

    private val toneShelf = BiquadFilter()
    private val dcBlocker = BiquadFilter()
    private var lastTone = Int.MIN_VALUE
    private var smoothActive = 0f

    init {
        dcBlocker.setHighPass(sampleRateHz, 22.0, 0.707)
    }

    fun updateConfig(newConfig: SaturationConfig) {
        config = newConfig.copy(
            drivePercent = newConfig.drivePercent.coerceIn(0, 100),
            warmthPercent = newConfig.warmthPercent.coerceIn(0, 100),
            tonePercent = newConfig.tonePercent.coerceIn(-100, 100),
            harmonicAmountPercent = newConfig.harmonicAmountPercent.coerceIn(0, 100),
            mixPercent = newConfig.mixPercent.coerceIn(0, 100),
            outputLevelDb = newConfig.outputLevelDb.coerceIn(-12f, 12f)
        )
    }

    fun reset() {
        toneShelf.reset()
        dcBlocker.reset()
        lastTone = Int.MIN_VALUE
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }

        if (cfg.tonePercent != lastTone) {
            lastTone = cfg.tonePercent
            val gainDb = (cfg.tonePercent / 100.0) * 6.0
            toneShelf.setHighShelf(sampleRateHz, 2600.0, gainDb)
        }

        val driveGain = 1.0f + (cfg.drivePercent / 100f) * 6.5f
        val warmth = (cfg.warmthPercent / 100f) * 0.28f
        val harmonics = cfg.harmonicAmountPercent / 100f
        val mix = cfg.mixPercent / 100f
        val outGain = 10f.pow(cfg.outputLevelDb / 20f)
        val norm = 1.0f / tanh(driveGain).coerceAtLeast(1e-3f)
        val targetActive = if (cfg.enabled) 1f else 0f

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            val dry = buffer[i]
            val driven = dry * driveGain + warmth * (dry * dry)
            val shaped = tanh(driven) * norm
            val harmonicBlended = dry + (shaped - dry) * (0.35f + 0.65f * harmonics)
            val cleaned = dcBlocker.process(harmonicBlended)
            val toned = toneShelf.process(cleaned)
            val wet = toned * outGain
            val effMix = mix * smoothActive
            buffer[i] = dry * (1f - effMix) + wet * effMix
        }
    }
}

/**
 * Dynamic vocal De-Esser with tunable bandpass sibilance detector and dynamic attenuation.
 */
class DeEsserProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    @Volatile
    private var config = DeEsserConfig()

    private val detectorFilter = BiquadFilter()
    private val cutFilter = BiquadFilter()
    private var lastFreq = -1f
    private var lastQ = -1f
    private var lastCutDb = 100f
    private var sibilanceEnvDb = -60f
    private var smoothReductionDb = 0f
    private var smoothActive = 0f

    fun updateConfig(newConfig: DeEsserConfig) {
        config = newConfig.copy(
            frequencyHz = newConfig.frequencyHz.coerceIn(3000f, 10000f),
            thresholdDb = newConfig.thresholdDb.coerceIn(-50f, 0f),
            reductionPercent = newConfig.reductionPercent.coerceIn(0, 100),
            bandwidthQ = newConfig.bandwidthQ.coerceIn(0.5f, 6.0f),
            mixPercent = newConfig.mixPercent.coerceIn(0, 100)
        )
    }

    fun reset() {
        detectorFilter.reset()
        cutFilter.reset()
        lastFreq = -1f
        lastQ = -1f
        lastCutDb = 100f
        sibilanceEnvDb = -60f
        smoothReductionDb = 0f
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }

        if (abs(cfg.frequencyHz - lastFreq) > 5f || abs(cfg.bandwidthQ - lastQ) > 0.05f) {
            lastFreq = cfg.frequencyHz
            lastQ = cfg.bandwidthQ
            detectorFilter.setBandPass(sampleRateHz, cfg.frequencyHz.toDouble(), cfg.bandwidthQ.toDouble())
        }

        val attackCoeff = exp(-1.0 / (0.0015 * sampleRateHz)).toFloat()
        val releaseCoeff = exp(-1.0 / (0.045 * sampleRateHz)).toFloat()
        val maxReductionDb = (cfg.reductionPercent / 100f) * 16f
        val mix = cfg.mixPercent / 100f
        val targetActive = if (cfg.enabled) 1f else 0f

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            val dry = buffer[i]
            val side = abs(detectorFilter.process(dry))
            val sideDb = 20f * log10(side.coerceAtLeast(1e-5f))
            sibilanceEnvDb = if (sideDb > sibilanceEnvDb) {
                attackCoeff * sibilanceEnvDb + (1f - attackCoeff) * sideDb
            } else {
                releaseCoeff * sibilanceEnvDb + (1f - releaseCoeff) * sideDb
            }

            val over = (sibilanceEnvDb - cfg.thresholdDb).coerceAtLeast(0f)
            val targetCutDb = -min(maxReductionDb, over * 0.85f)
            smoothReductionDb += 0.03f * (targetCutDb - smoothReductionDb)

            if (abs(smoothReductionDb - lastCutDb) > 0.25f) {
                lastCutDb = smoothReductionDb
                cutFilter.setPeaking(sampleRateHz, cfg.frequencyHz.toDouble(), cfg.bandwidthQ.toDouble(), smoothReductionDb.toDouble())
            }

            val wet = cutFilter.process(dry)
            val effMix = mix * smoothActive
            buffer[i] = dry * (1f - effMix) + wet * effMix
        }
    }
}

/**
 * Fast brickwall peak Limiter protecting against digital overs while shaping loudness.
 */
class LimiterProcessor(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    @Volatile
    private var config = LimiterConfig()

    private var gainEnv = 1f
    private var smoothActive = 0f

    fun updateConfig(newConfig: LimiterConfig) {
        config = newConfig.copy(
            thresholdDb = newConfig.thresholdDb.coerceIn(-30f, 0f),
            ceilingDb = newConfig.ceilingDb.coerceIn(-12f, -0.1f),
            releaseMs = newConfig.releaseMs.coerceIn(10f, 500f),
            inputGainDb = newConfig.inputGainDb.coerceIn(-12f, 18f),
            outputGainDb = newConfig.outputGainDb.coerceIn(-12f, 6f)
        )
    }

    fun reset() {
        gainEnv = 1f
        smoothActive = if (config.enabled) 1f else 0f
    }

    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return
        val cfg = config
        if (!cfg.enabled && smoothActive < 0.0005f) {
            smoothActive = 0f
            return
        }

        val inGain = 10f.pow(cfg.inputGainDb / 20f)
        val thresholdLin = 10f.pow(cfg.thresholdDb / 20f)
        val ceilingLin = 10f.pow(cfg.ceilingDb / 20f)
        val outGain = 10f.pow(cfg.outputGainDb / 20f)
        val releaseCoeff = exp(-1.0 / ((cfg.releaseMs * 0.001) * sampleRateHz)).toFloat()
        val targetActive = if (cfg.enabled) 1f else 0f

        for (i in 0 until count) {
            smoothActive += 0.006f * (targetActive - smoothActive)
            val dry = buffer[i]
            val driven = dry * inGain
            val absDriven = abs(driven)
            val neededGain = if (absDriven > thresholdLin && absDriven > 1e-6f) {
                (thresholdLin / absDriven).coerceIn(0.01f, 1f)
            } else {
                1f
            }
            gainEnv = if (neededGain < gainEnv) {
                neededGain // instant brickwall attack
            } else {
                releaseCoeff * gainEnv + (1f - releaseCoeff) * neededGain
            }

            var limited = (driven * gainEnv) * outGain
            if (abs(limited) > ceilingLin) {
                limited = if (limited >= 0f) ceilingLin else -ceilingLin
            }
            buffer[i] = dry * (1f - smoothActive) + limited * smoothActive
        }
    }
}
