package com.example.dsp

import com.example.audio.AudioConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Exactly four voice presets as specified.
 */
enum class VoicePreset(val displayName: String) {
    NORMAL("Normal"),
    GIRL("Girl"),
    BOY("Boy"),
    CHILD("Child");

    fun defaultConfig(): VoiceChangerConfig = when (this) {
        NORMAL -> VoiceChangerConfig(
            preset = NORMAL,
            pitchSemitones = 0.0f,
            formantSemitones = 0.0f,
            fineTuneCents = 0,
            timbreDb = 0.0f,
            dryWetPercent = 100,
            bypass = false
        )
        GIRL -> VoiceChangerConfig(
            preset = GIRL,
            pitchSemitones = 4.5f,
            formantSemitones = 3.5f,
            fineTuneCents = 0,
            timbreDb = 2.5f,
            dryWetPercent = 100,
            bypass = false
        )
        BOY -> VoiceChangerConfig(
            preset = BOY,
            pitchSemitones = -4.0f,
            formantSemitones = -3.0f,
            fineTuneCents = 0,
            timbreDb = -2.0f,
            dryWetPercent = 100,
            bypass = false
        )
        CHILD -> VoiceChangerConfig(
            preset = CHILD,
            pitchSemitones = 7.5f,
            formantSemitones = 6.0f,
            fineTuneCents = 0,
            timbreDb = 3.5f,
            dryWetPercent = 100,
            bypass = false
        )
    }
}

data class VoiceChangerConfig(
    val preset: VoicePreset = VoicePreset.NORMAL,
    val pitchSemitones: Float = 0.0f,
    val formantSemitones: Float = 0.0f,
    val fineTuneCents: Int = 0,
    val timbreDb: Float = 0.0f,
    val dryWetPercent: Int = 100,
    val bypass: Boolean = false
) {
    companion object {
        const val MIN_PITCH_ST = -12.0f
        const val MAX_PITCH_ST = 12.0f
        const val STEP_PITCH_ST = 0.5f

        const val MIN_FORMANT_ST = -12.0f
        const val MAX_FORMANT_ST = 12.0f
        const val STEP_FORMANT_ST = 0.5f

        const val MIN_FINE_TUNE_CENTS = -100
        const val MAX_FINE_TUNE_CENTS = 100
        const val STEP_FINE_TUNE_CENTS = 5

        const val MIN_TIMBRE_DB = -12.0f
        const val MAX_TIMBRE_DB = 12.0f
        const val STEP_TIMBRE_DB = 0.5f

        const val MIN_DRY_WET_PCT = 0
        const val MAX_DRY_WET_PCT = 100
        const val STEP_DRY_WET_PCT = 5
    }
}

/**
 * Real-time zero-allocation Voice Changer DSP Engine operating at 48,000 Hz.
 *
 * Architecture:
 * 1. 80 Hz 2nd-order Butterworth high-pass filter (removes desk/handling rumble).
 * 2. Phase-locked STFT Phase Vocoder (N = 1024, Hop = 256, 4x Hann OLA) with
 *    cepstral-style smoothed log-spectral envelope separation:
 *      - Pitch shifting scales excitation harmonics independently without changing speech duration/speed.
 *      - Formant shifting warps the spectral vocal-tract envelope independently.
 *      - Identity phase-locking around spectral peaks preserves natural vocal coherence.
 * 3. Latency-matched Dry delay line so Dry/Wet crossfading has zero comb-filtering.
 * 4. Dual-shelf Timbre/Tone tilt EQ (warmth <-> brilliance) in dB.
 * 5. Smooth soft-knee brickwall limiter to eliminate output clipping.
 */
class VoiceChangerDsp(
    private val sampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
) {
    private val fftSize = AudioConfig.DSP_FFT_SIZE // 1024
    private val halfBins = fftSize / 2 + 1 // 513
    private val hopSize = AudioConfig.DSP_HOP_SIZE // 256
    private val twoPi = (2.0 * PI).toFloat()

    // Window & normalization for 4x overlap-add periodic Hann
    private val window = FloatArray(fftSize)
    private val olaScale = 2.0f / 3.0f

    // Precomputed FFT tables
    private val bitRev = IntArray(fftSize)
    private val cosTable = FloatArray(fftSize / 2)
    private val sinTable = FloatArray(fftSize / 2)

    // Analysis & synthesis circular buffers
    private val inRing = FloatArray(fftSize)
    private val outOlaRing = FloatArray(fftSize)
    private val dryDelayRing = FloatArray(fftSize)
    private var ringCursor = 0
    private var hopCounter = 0

    // Pre-allocated FFT working buffers
    private val fftRe = FloatArray(fftSize)
    private val fftIm = FloatArray(fftSize)
    private val mag = FloatArray(halfBins)
    private val phase = FloatArray(halfBins)
    private val logMag = FloatArray(halfBins)
    private val spectralEnv = FloatArray(halfBins)
    private val warpedEnv = FloatArray(halfBins)
    private val excitation = FloatArray(halfBins)

    // Phase vocoder state
    private val prevInPhase = FloatArray(halfBins)
    private val sumOutPhase = FloatArray(halfBins)
    private val instFreq = FloatArray(halfBins)
    private val shiftedExc = FloatArray(halfBins)
    private val shiftedFreq = FloatArray(halfBins)
    private val peakBinMask = BooleanArray(halfBins)
    private val peakOwner = IntArray(halfBins)

    // Smoothed parameters to avoid clicks/zipper noise
    @Volatile
    private var targetConfig = VoiceChangerConfig()
    private var smoothPitchRatio = 1.0f
    private var smoothFormantRatio = 1.0f
    private var smoothTimbreDb = 0.0f
    private var smoothWetMix = 1.0f
    private var smoothActiveMix = 1.0f // 0 = bypassed, 1 = active

    // 80 Hz High-pass Butterworth state
    private var hpB0 = 1f
    private var hpB1 = 0f
    private var hpB2 = 0f
    private var hpA1 = 0f
    private var hpA2 = 0f
    private var hpZ1 = 0f
    private var hpZ2 = 0f

    // Low-shelf (240 Hz) and High-shelf (3200 Hz) Timbre tilt biquads
    private var lsB0 = 1f; private var lsB1 = 0f; private var lsB2 = 0f; private var lsA1 = 0f; private var lsA2 = 0f
    private var lsZ1 = 0f; private var lsZ2 = 0f
    private var hsB0 = 1f; private var hsB1 = 0f; private var hsB2 = 0f; private var hsA1 = 0f; private var hsA2 = 0f
    private var hsZ1 = 0f; private var hsZ2 = 0f
    private var lastAppliedTimbreDb = Float.NaN

    // Expected phase advance per hop for each bin
    private val omegaBin = FloatArray(halfBins)

    init {
        val log2N = 9 // 2^9 = 512 (low-latency 10.67 ms STFT frame)
        for (i in 0 until fftSize) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
            var rev = 0
            var x = i
            for (b in 0 until log2N) {
                rev = (rev shl 1) or (x and 1)
                x = x ushr 1
            }
            bitRev[i] = rev
        }
        for (i in 0 until fftSize / 2) {
            val angle = -2.0 * PI * i / fftSize
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        for (k in 0 until halfBins) {
            omegaBin[k] = twoPi * k * hopSize / fftSize.toFloat()
        }
        computeHighPassCoeffs(80.0)
        updateTimbreCoeffs(0f)
    }

    fun updateConfig(config: VoiceChangerConfig) {
        targetConfig = config.copy(
            pitchSemitones = config.pitchSemitones.coerceIn(
                VoiceChangerConfig.MIN_PITCH_ST,
                VoiceChangerConfig.MAX_PITCH_ST
            ),
            formantSemitones = config.formantSemitones.coerceIn(
                VoiceChangerConfig.MIN_FORMANT_ST,
                VoiceChangerConfig.MAX_FORMANT_ST
            ),
            fineTuneCents = config.fineTuneCents.coerceIn(
                VoiceChangerConfig.MIN_FINE_TUNE_CENTS,
                VoiceChangerConfig.MAX_FINE_TUNE_CENTS
            ),
            timbreDb = config.timbreDb.coerceIn(
                VoiceChangerConfig.MIN_TIMBRE_DB,
                VoiceChangerConfig.MAX_TIMBRE_DB
            ),
            dryWetPercent = config.dryWetPercent.coerceIn(
                VoiceChangerConfig.MIN_DRY_WET_PCT,
                VoiceChangerConfig.MAX_DRY_WET_PCT
            )
        )
    }

    fun getConfig(): VoiceChangerConfig = targetConfig

    fun reset() {
        inRing.fill(0f)
        outOlaRing.fill(0f)
        dryDelayRing.fill(0f)
        prevInPhase.fill(0f)
        sumOutPhase.fill(0f)
        ringCursor = 0
        hopCounter = 0
        hpZ1 = 0f; hpZ2 = 0f
        lsZ1 = 0f; lsZ2 = 0f
        hsZ1 = 0f; hsZ2 = 0f
        val cfg = targetConfig
        val totalSemitones = cfg.pitchSemitones + cfg.fineTuneCents / 100f
        smoothPitchRatio = 2f.pow(totalSemitones / 12f)
        smoothFormantRatio = 2f.pow(cfg.formantSemitones / 12f)
        smoothTimbreDb = cfg.timbreDb
        smoothWetMix = cfg.dryWetPercent / 100f
        smoothActiveMix = if (cfg.bypass) 0f else 1f
        updateTimbreCoeffs(smoothTimbreDb)
    }

    /**
     * Processes [count] mono samples at 48,000 Hz in place.
     */
    fun processInPlace(buffer: FloatArray, count: Int) {
        if (count <= 0) return

        val cfg = targetConfig
        val totalSemitones = cfg.pitchSemitones + cfg.fineTuneCents / 100f
        val targetPitchRatio = 2f.pow(totalSemitones / 12f)
        val targetFormantRatio = 2f.pow(cfg.formantSemitones / 12f)
        val targetWetMix = cfg.dryWetPercent / 100f
        val targetActiveMix = if (cfg.bypass) 0f else 1f

        val paramAlpha = 0.0025f // smooth per-sample parameter slew

        for (i in 0 until count) {
            smoothPitchRatio += paramAlpha * (targetPitchRatio - smoothPitchRatio)
            smoothFormantRatio += paramAlpha * (targetFormantRatio - smoothFormantRatio)
            smoothTimbreDb += paramAlpha * (cfg.timbreDb - smoothTimbreDb)
            smoothWetMix += paramAlpha * (targetWetMix - smoothWetMix)
            smoothActiveMix += paramAlpha * (targetActiveMix - smoothActiveMix)

            // 1. Subtle 80 Hz rumble high-pass
            val raw = buffer[i]
            val hpOut = hpB0 * raw + hpZ1
            hpZ1 = hpB1 * raw - hpA1 * hpOut + hpZ2
            hpZ2 = hpB2 * raw - hpA2 * hpOut

            // 2. Write to input & latency-aligned dry delay rings
            inRing[ringCursor] = hpOut
            val dryDelayed = dryDelayRing[ringCursor]
            dryDelayRing[ringCursor] = hpOut

            // Read synthesized OLA sample and clear slot for future accumulation
            val wetSynth = outOlaRing[ringCursor] * olaScale
            outOlaRing[ringCursor] = 0f

            // Advance circular cursor
            ringCursor = (ringCursor + 1) and (fftSize - 1)
            hopCounter++

            if (hopCounter >= hopSize) {
                hopCounter = 0
                processStftFrame(smoothPitchRatio, smoothFormantRatio)
            }

            // 3. Apply Timbre/Tone tilt EQ to wet branch
            if (abs(smoothTimbreDb - lastAppliedTimbreDb) > 0.08f) {
                updateTimbreCoeffs(smoothTimbreDb)
            }
            val wetToned = if (abs(smoothTimbreDb) > 0.05f) {
                val lsOut = lsB0 * wetSynth + lsZ1
                lsZ1 = lsB1 * wetSynth - lsA1 * lsOut + lsZ2
                lsZ2 = lsB2 * wetSynth - lsA2 * lsOut

                val hsOut = hsB0 * lsOut + hsZ1
                hsZ1 = hsB1 * lsOut - hsA1 * hsOut + hsZ2
                hsZ2 = hsB2 * lsOut - hsA2 * hsOut
                hsOut
            } else {
                wetSynth
            }

            // 4. Dry/Wet & Bypass smooth blending
            val wetEffective = smoothWetMix * smoothActiveMix
            val mixed = dryDelayed * (1f - wetEffective) + wetToned * wetEffective

            // 5. Soft-knee brickwall voiceover limiter (-0.45 dBFS ceiling)
            buffer[i] = softLimit(mixed)
        }
    }

    private fun processStftFrame(pitchRatio: Float, formantRatio: Float) {
        val isNeutralShift =
            abs(pitchRatio - 1.0f) < 0.002f && abs(formantRatio - 1.0f) < 0.002f

        if (isNeutralShift) {
            // Bit-clean unity pass-through through window + OLA so phase/neutral Normal has zero artifacts
            var idx = ringCursor
            for (n in 0 until fftSize) {
                val s = inRing[idx] * window[n]
                outOlaRing[idx] += s
                idx = (idx + 1) and (fftSize - 1)
            }
            return
        }

        // Load windowed input frame into FFT arrays
        var idx = ringCursor
        for (n in 0 until fftSize) {
            fftRe[n] = inRing[idx] * window[n]
            fftIm[n] = 0f
            idx = (idx + 1) and (fftSize - 1)
        }

        fft(fftRe, fftIm, inverse = false)

        // 1. Magnitude, Phase, Instantaneous Frequency & Log Magnitude
        for (k in 0 until halfBins) {
            val re = fftRe[k]
            val im = fftIm[k]
            val m = sqrt(re * re + im * im)
            val ph = atan2(im, re)
            mag[k] = m
            phase[k] = ph
            logMag[k] = ln(m + 1e-6f)

            val delta = wrapPhase(ph - prevInPhase[k] - omegaBin[k])
            prevInPhase[k] = ph
            instFreq[k] = omegaBin[k] + delta
        }

        // 2. Smooth spectral envelope (moving window liftering in log-magnitude domain)
        val envHalfWidth = 6
        for (k in 0 until halfBins) {
            val lo = max(0, k - envHalfWidth)
            val hi = min(halfBins - 1, k + envHalfWidth)
            var sum = 0f
            var weightSum = 0f
            for (j in lo..hi) {
                val dist = abs(j - k).toFloat() / (envHalfWidth + 1)
                val w = 1f - dist
                sum += logMag[j] * w
                weightSum += w
            }
            val env = exp(sum / max(weightSum, 1e-6f))
            spectralEnv[k] = env
            excitation[k] = mag[k] / max(env, 1e-6f)
        }

        // 3. Independent Formant Envelope Warping
        for (k in 0 until halfBins) {
            val srcPos = (k / formantRatio).coerceIn(0f, (halfBins - 1).toFloat())
            val i0 = floor(srcPos).toInt()
            val i1 = min(halfBins - 1, i0 + 1)
            val frac = srcPos - i0
            warpedEnv[k] = spectralEnv[i0] * (1f - frac) + spectralEnv[i1] * frac
        }

        // 4. Identify spectral peaks for Laroche-Dolson identity phase-locking
        peakBinMask.fill(false)
        for (k in 2 until halfBins - 2) {
            val m = mag[k]
            if (m > mag[k - 1] && m > mag[k + 1] && m > mag[k - 2] && m > mag[k + 2] && m > 1e-4f) {
                peakBinMask[k] = true
            }
        }
        // Assign every bin to its nearest spectral peak
        var currentPeak = 0
        for (k in 0 until halfBins) {
            if (peakBinMask[k]) {
                currentPeak = k
            } else {
                // Check if next peak is closer
                var nextPeak = currentPeak
                for (j in k + 1 until min(halfBins, k + 16)) {
                    if (peakBinMask[j]) {
                        nextPeak = j
                        break
                    }
                }
                if (nextPeak != currentPeak && abs(nextPeak - k) < abs(k - currentPeak)) {
                    currentPeak = nextPeak
                }
            }
            peakOwner[k] = currentPeak
        }

        // 5. Pitch-shift excitation spectrum
        shiftedExc.fill(0f)
        shiftedFreq.fill(0f)

        // Keep DC bin clean
        shiftedExc[0] = excitation[0]
        for (k in 1 until halfBins - 1) {
            val dst = (k * pitchRatio).roundToInt()
            if (dst in 1 until halfBins - 1) {
                // Energy accumulation while tracking strongest contributor's frequency
                val exc = excitation[k]
                if (exc > shiftedExc[dst]) {
                    shiftedFreq[dst] = instFreq[k] * pitchRatio
                }
                shiftedExc[dst] += exc
            }
        }

        // 6. Two-pass phase propagation: advance peak bins first, then lock region bins
        fftRe[0] = shiftedExc[0] * warpedEnv[0]
        fftIm[0] = 0f

        for (k in 1 until halfBins - 1) {
            val ownerDst = (peakOwner[k] * pitchRatio).roundToInt().coerceIn(1, halfBins - 2)
            if (k == ownerDst || shiftedExc[ownerDst] < 1e-6f) {
                sumOutPhase[k] = wrapPhase(sumOutPhase[k] + shiftedFreq[k])
            }
        }

        for (k in 1 until halfBins - 1) {
            val targetOutMag = shiftedExc[k] * warpedEnv[k]
            val ownerSrc = peakOwner[k]
            val ownerDst = (ownerSrc * pitchRatio).roundToInt().coerceIn(1, halfBins - 2)

            val outPh = if (k == ownerDst || shiftedExc[ownerDst] < 1e-6f) {
                sumOutPhase[k]
            } else {
                val peakPhase = sumOutPhase[ownerDst]
                val relPhase = phase[k] - phase[ownerSrc]
                val locked = wrapPhase(peakPhase + relPhase * pitchRatio)
                sumOutPhase[k] = locked
                locked
            }

            val re = targetOutMag * cos(outPh)
            val im = targetOutMag * sin(outPh)
            fftRe[k] = re
            fftIm[k] = im
            // Conjugate symmetry for real IFFT
            fftRe[fftSize - k] = re
            fftIm[fftSize - k] = -im
        }

        val nyq = halfBins - 1
        fftRe[nyq] = shiftedExc[nyq] * warpedEnv[nyq]
        fftIm[nyq] = 0f

        fft(fftRe, fftIm, inverse = true)

        // 7. Windowed Overlap-Add into output ring
        idx = ringCursor
        for (n in 0 until fftSize) {
            outOlaRing[idx] += fftRe[n] * window[n]
            idx = (idx + 1) and (fftSize - 1)
        }
    }

    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = fftSize
        for (i in 0 until n) {
            val j = bitRev[i]
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len ushr 1
            val step = n / len
            for (i in 0 until n step len) {
                var tableIdx = 0
                for (j in 0 until halfLen) {
                    val c = cosTable[tableIdx]
                    val s = if (inverse) -sinTable[tableIdx] else sinTable[tableIdx]
                    tableIdx += step

                    val idx1 = i + j
                    val idx2 = idx1 + halfLen
                    val tre = re[idx2] * c - im[idx2] * s
                    val tim = re[idx2] * s + im[idx2] * c

                    re[idx2] = re[idx1] - tre
                    im[idx2] = im[idx1] - tim
                    re[idx1] += tre
                    im[idx1] += tim
                }
            }
            len = len shl 1
        }

        if (inverse) {
            val invN = 1.0f / n
            for (i in 0 until n) {
                re[i] *= invN
                im[i] *= invN
            }
        }
    }

    private fun wrapPhase(angle: Float): Float {
        var a = angle
        while (a > PI) a -= twoPi
        while (a < -PI) a += twoPi
        return a
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

    private fun computeHighPassCoeffs(cutoffHz: Double) {
        val w0 = 2.0 * PI * cutoffHz / sampleRateHz.toDouble()
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * 0.70710678)
        val a0 = 1.0 + alpha
        hpB0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        hpB1 = (-(1.0 + cosW0) / a0).toFloat()
        hpB2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        hpA1 = (-2.0 * cosW0 / a0).toFloat()
        hpA2 = ((1.0 - alpha) / a0).toFloat()
    }

    private fun updateTimbreCoeffs(timbreDb: Float) {
        lastAppliedTimbreDb = timbreDb
        // Tilt EQ: positive timbre boosts treble (+0.75 * timbreDb) & trims low warmth (-0.55 * timbreDb)
        val lowGainDb = (-0.55 * timbreDb).toDouble()
        val highGainDb = (0.75 * timbreDb).toDouble()

        val ls = calcLowShelf(240.0, lowGainDb)
        lsB0 = ls[0]; lsB1 = ls[1]; lsB2 = ls[2]; lsA1 = ls[3]; lsA2 = ls[4]

        val hs = calcHighShelf(3200.0, highGainDb)
        hsB0 = hs[0]; hsB1 = hs[1]; hsB2 = hs[2]; hsA1 = hs[3]; hsA2 = hs[4]
    }

    private fun calcLowShelf(freqHz: Double, gainDb: Double): FloatArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRateHz.toDouble()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        val b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        val b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        val a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        val a2 = (((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        return floatArrayOf(b0, b1, b2, a1, a2)
    }

    private fun calcHighShelf(freqHz: Double, gainDb: Double): FloatArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRateHz.toDouble()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        val b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        val b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        val a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        val a2 = (((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        return floatArrayOf(b0, b1, b2, a1, a2)
    }
}
