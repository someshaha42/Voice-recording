package com.example

import com.example.audio.AudioConfig
import com.example.audio.LinearResampler
import com.example.audio.MonitoringConfig
import com.example.audio.MonitoringDelayLine
import com.example.audio.MonitoringLatencyZone
import com.example.audio.WavPcmWriter
import com.example.dsp.CompressionConfig
import com.example.dsp.CompressionProcessor
import com.example.dsp.DeEsserConfig
import com.example.dsp.DeEsserProcessor
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EffectsRackProcessor
import com.example.dsp.EqualizerConfig
import com.example.dsp.EqualizerEffect
import com.example.dsp.FilterSlope
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.HighPassFilter
import com.example.dsp.LimiterConfig
import com.example.dsp.LimiterProcessor
import com.example.dsp.LowPassConfig
import com.example.dsp.MusicalKey
import com.example.dsp.MusicalScale
import com.example.dsp.NoiseGateConfig
import com.example.dsp.NoiseGateProcessor
import com.example.dsp.PacketLossConcealment
import com.example.dsp.PitchCorrectionConfig
import com.example.dsp.PitchCorrectionProcessor
import com.example.dsp.PitchFormantConfig
import com.example.dsp.PitchFormantProcessor
import com.example.dsp.ReverbConfig
import com.example.dsp.ReverbPreset
import com.example.dsp.SaturationConfig
import com.example.dsp.SaturationProcessor
import com.example.dsp.StudioDspPipeline
import com.example.dsp.VoiceChangerConfig
import com.example.dsp.VoiceChangerDsp
import com.example.dsp.VoicePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun fixed_recording_spec_is_768kbps_48khz_16bit_mono() {
        assertEquals(48_000, AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
        assertEquals(16, AudioConfig.OUTPUT_BITS_PER_SAMPLE)
        assertEquals(1, AudioConfig.OUTPUT_CHANNELS)
        assertEquals(768_000, AudioConfig.OUTPUT_BITRATE_BPS)
        assertEquals(96_000, AudioConfig.BYTES_PER_SECOND)
    }

    @Test
    fun wav_pcm_writer_writes_valid_header_and_repairs_interrupted_file() {
        val tempFile = File.createTempFile("voxforge_test_", ".wav")
        try {
            val writer = WavPcmWriter(tempFile)
            writer.open()
            val block = FloatArray(4800) { i ->
                (0.25f * sin(2.0 * PI * 440.0 * i / 48000.0)).toFloat()
            }
            val clips = writer.writeSamples(block, block.size)
            assertEquals(0, clips)
            val bytesWritten = writer.close()
            assertEquals(9600L, bytesWritten)
            assertEquals(9600L + 44L, tempFile.length())

            val header = ByteArray(44)
            RandomAccessFile(tempFile, "r").use { it.readFully(header) }
            assertEquals('R'.code.toByte(), header[0])
            assertEquals('I'.code.toByte(), header[1])
            assertEquals('F'.code.toByte(), header[2])
            assertEquals('F'.code.toByte(), header[3])
            assertEquals(48_000, WavPcmWriter.readIntLE(header, 24))
            assertEquals(96_000, WavPcmWriter.readIntLE(header, 28))
            assertEquals(9600, WavPcmWriter.readIntLE(header, 40))

            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.seek(40L)
                raf.write(byteArrayOf(0, 0, 0, 0))
            }
            assertTrue(WavPcmWriter.repairHeaderIfNeeded(tempFile))
            RandomAccessFile(tempFile, "r").use { it.readFully(header) }
            assertEquals(9600, WavPcmWriter.readIntLE(header, 40))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun noise_gate_attenuates_quiet_noise_and_opens_on_voice_or_bypass() {
        val gate = NoiseGateProcessor(48_000)
        gate.updateConfig(
            NoiseGateConfig(
                enabled = true,
                mixPercent = 100,
                thresholdDb = -30f,
                attackMs = 2f,
                holdMs = 20f,
                releaseMs = 40f,
                rangeDb = 80f
            )
        )
        gate.reset()

        val quiet = FloatArray(4096) { 0.0018f }
        gate.processInPlace(quiet, quiet.size)
        assertFalse(gate.lastIsGateOpen)
        assertTrue(abs(quiet.last()) < 0.0005f)

        val loud = FloatArray(4096) { 0.5f }
        gate.processInPlace(loud, loud.size)
        assertTrue(gate.lastIsGateOpen)
        assertTrue(abs(loud.last()) > 0.45f)

        gate.updateConfig(NoiseGateConfig(enabled = false, thresholdDb = -10f))
        val bypassTest = FloatArray(2048) { 0.002f }
        gate.processInPlace(bypassTest, bypassTest.size)
        assertTrue(gate.lastIsGateOpen)
        assertTrue(abs(bypassTest.last() - 0.002f) < 0.0002f)
    }

    @Test
    fun voice_changer_defaults_off_bypasses_cleanly_and_processes_five_presets() {
        assertEquals(5, VoicePreset.entries.size)
        val defaultCfg = VoiceChangerConfig()
        assertFalse(defaultCfg.enabled)

        val dsp = VoiceChangerDsp(48_000)
        dsp.updateConfig(defaultCfg)
        dsp.reset()

        // When OFF, output must remain bit-identical to original input
        val original = FloatArray(2048) { i ->
            (0.4f * sin(2.0 * PI * 140.0 * i / 48000.0)).toFloat()
        }
        val offBuffer = original.copyOf()
        dsp.processInPlace(offBuffer, offBuffer.size)
        for (i in original.indices) {
            assertEquals(original[i], offBuffer[i], 0.0f)
        }

        // Verify all 5 presets have distinct defaults and run without NaN/Inf/clipping
        val configs = VoicePreset.entries.map { it.defaultConfig(enabled = true) }
        assertTrue(configs.map { it.preset }.toSet().size == 5)

        val buffer = FloatArray(2048)
        for (cfg in configs) {
            dsp.updateConfig(cfg)
            dsp.reset()
            for (block in 0 until 6) {
                for (i in buffer.indices) {
                    val sampleIdx = block * buffer.size + i
                    buffer[i] = (0.35f * sin(2.0 * PI * 135.0 * sampleIdx / 48000.0) +
                        0.12f * sin(2.0 * PI * 270.0 * sampleIdx / 48000.0)).toFloat()
                }
                dsp.processInPlace(buffer, buffer.size)

                for (s in buffer) {
                    assertFalse("NaN detected for preset ${cfg.preset}", s.isNaN())
                    assertFalse("Inf detected for preset ${cfg.preset}", s.isInfinite())
                    assertTrue("Clipping > 1.0 detected for preset ${cfg.preset}: $s", abs(s) <= 1.0f)
                }
            }
        }
    }

    @Test
    fun effects_rack_processes_all_seven_effects_cleanly() {
        val rack = EffectsRackProcessor(48_000)
        rack.updateConfig(
            EffectsRackConfig(
                noiseGate = NoiseGateConfig(enabled = true),
                lowPass = LowPassConfig(enabled = true, slope = FilterSlope.SLOPE_24),
                highPass = HighPassConfig(enabled = true, slope = FilterSlope.SLOPE_12),
                equalizer = EqualizerConfig(enabled = true, lowGainDb = 2f, mid2GainDb = -1.5f, highGainDb = 2.5f),
                reverb = ReverbConfig(enabled = true),
                echo = EchoConfig(enabled = true, pingPong = true),
                hall = HallConfig(enabled = true)
            )
        )
        rack.reset()

        val buf = FloatArray(2048) { i ->
            (0.35f * sin(2.0 * PI * 320.0 * i / 48000.0)).toFloat()
        }
        rack.processPreVoiceChanger(buf, buf.size)
        rack.processPostVoiceChanger(buf, buf.size)

        for (s in buf) {
            assertFalse(s.isNaN())
            assertFalse(s.isInfinite())
            assertTrue(abs(s) <= 1.0f)
        }
    }

    @Test
    fun studio_dsp_pipeline_processes_all_ten_effects_plus_voice_changer_cleanly() {
        val pipeline = StudioDspPipeline(48_000)
        pipeline.updateEffectsConfig(
            EffectsRackConfig(
                pitchCorrection = PitchCorrectionConfig(enabled = true, key = MusicalKey.C, scale = MusicalScale.MAJOR),
                pitchFormant = PitchFormantConfig(enabled = true, pitchShiftSt = 2.0f, formantShiftSt = 1.0f),
                equalizer = EqualizerConfig(enabled = true, lowGainDb = 3f, mid2GainDb = 2f, highGainDb = 2.5f),
                compression = CompressionConfig(enabled = true, thresholdDb = -20f, ratio = 4f),
                saturation = SaturationConfig(enabled = true, drivePercent = 40, warmthPercent = 50),
                reverb = ReverbPreset.HALL.applyTo(enabled = true),
                echo = EchoConfig(enabled = true, tempoSync = true, bpm = 124, highCutHz = 5500f),
                noiseGate = NoiseGateConfig(enabled = true, thresholdDb = -55f),
                deEsser = DeEsserConfig(enabled = true, frequencyHz = 6200f, reductionPercent = 60),
                limiter = LimiterConfig(enabled = true, ceilingDb = -1.0f, inputGainDb = 3.0f)
            )
        )
        pipeline.updateVoiceChangerConfig(VoicePreset.FEMALE.defaultConfig(enabled = true))
        pipeline.reset()

        val block = FloatArray(1024)
        for (b in 0 until 8) {
            for (i in block.indices) {
                val n = b * block.size + i
                block[i] = (0.35f * sin(2.0 * PI * 220.0 * n / 48000.0) +
                    0.10f * sin(2.0 * PI * 6200.0 * n / 48000.0)).toFloat()
            }
            pipeline.processInPlace(block, block.size)
            for (s in block) {
                assertFalse(s.isNaN())
                assertFalse(s.isInfinite())
                assertTrue("Output exceeds ceiling: $s", abs(s) <= 1.0f)
            }
        }
    }

    @Test
    fun studio_processors_behave_as_designed_individually() {
        // 1. Pitch Correction detects vocal pitch cleanly
        val pitchCorr = PitchCorrectionProcessor(48_000)
        pitchCorr.updateConfig(
            PitchCorrectionConfig(
                enabled = true,
                key = MusicalKey.A,
                scale = MusicalScale.MAJOR,
                correctionStrengthPercent = 90,
                retuneSpeedMs = 10
            )
        )
        pitchCorr.reset()
        val a4Buf = FloatArray(1024)
        for (b in 0 until 6) {
            for (i in a4Buf.indices) {
                val n = b * a4Buf.size + i
                a4Buf[i] = (0.4f * sin(2.0 * PI * 220.0 * n / 48000.0)).toFloat()
            }
            pitchCorr.processInPlace(a4Buf, a4Buf.size)
        }
        assertTrue(pitchCorr.lastDetectedNoteLabel.startsWith("A"))

        // 2. Pitch & Formant Shifter runs stably
        val pf = PitchFormantProcessor(48_000)
        pf.updateConfig(PitchFormantConfig(enabled = true, pitchShiftSt = -3f, formantShiftSt = -2f))
        pf.reset()
        val pfBuf = FloatArray(2048) { i -> (0.4f * sin(2.0 * PI * 260.0 * i / 48000.0)).toFloat() }
        pf.processInPlace(pfBuf, pfBuf.size)
        assertTrue(pfBuf.all { !it.isNaN() && abs(it) <= 1.0f })

        // 3. Compressor reduces loud peaks above threshold and reports negative GR
        val comp = CompressionProcessor(48_000)
        comp.updateConfig(
            CompressionConfig(
                enabled = true,
                thresholdDb = -24f,
                ratio = 8f,
                attackMs = 1f,
                releaseMs = 100f,
                makeupGainDb = 0f,
                outputGainDb = 0f
            )
        )
        comp.reset()
        val loud = FloatArray(4096) { i -> (0.85f * sin(2.0 * PI * 300.0 * i / 48000.0)).toFloat() }
        comp.processInPlace(loud, loud.size)
        assertTrue("Expected negative gain reduction, got ${comp.lastGainReductionDb}", comp.lastGainReductionDb < -5f)
        assertTrue("Expected attenuated tail amplitude", loud.takeLast(512).maxOf { abs(it) } < 0.6f)

        // 4. Saturation adds harmonics safely without runaway
        val sat = SaturationProcessor(48_000)
        sat.updateConfig(SaturationConfig(enabled = true, drivePercent = 70, warmthPercent = 60, mixPercent = 100))
        sat.reset()
        val satBuf = FloatArray(2048) { i -> (0.5f * sin(2.0 * PI * 200.0 * i / 48000.0)).toFloat() }
        sat.processInPlace(satBuf, satBuf.size)
        assertTrue(satBuf.all { !it.isNaN() && abs(it) <= 1.2f })

        // 5. De-Esser attenuates intense 6.5 kHz sibilance
        val deEsser = DeEsserProcessor(48_000)
        deEsser.updateConfig(
            DeEsserConfig(
                enabled = true,
                frequencyHz = 6500f,
                thresholdDb = -30f,
                reductionPercent = 90,
                bandwidthQ = 2.0f,
                mixPercent = 100
            )
        )
        deEsser.reset()
        val sib = FloatArray(8192) { i -> (0.6f * sin(2.0 * PI * 6500.0 * i / 48000.0)).toFloat() }
        deEsser.processInPlace(sib, sib.size)
        val tailSibPeak = sib.takeLast(1024).maxOf { abs(it) }
        assertTrue("Expected De-Esser to attenuate 6.5 kHz sibilance below 0.35, got $tailSibPeak", tailSibPeak < 0.35f)

        // 6. Brickwall Limiter strictly enforces ceiling dB under hot drive
        val limiter = LimiterProcessor(48_000)
        val ceilingDb = -1.5f
        val ceilingLin = 10f.pow(ceilingDb / 20f)
        limiter.updateConfig(
            LimiterConfig(
                enabled = true,
                thresholdDb = -6f,
                ceilingDb = ceilingDb,
                inputGainDb = 12f,
                outputGainDb = 0f
            )
        )
        limiter.reset()
        val hot = FloatArray(2048) { i -> (0.8f * sin(2.0 * PI * 400.0 * i / 48000.0)).toFloat() }
        limiter.processInPlace(hot, hot.size)
        val maxAbs = hot.maxOf { abs(it) }
        assertTrue("Expected limited peak <= $ceilingLin, got $maxAbs", maxAbs <= ceilingLin + 0.001f)
    }

    @Test
    fun equalizer_magnitude_curve_reflects_boost_and_cut() {
        val eq = EqualizerConfig(
            enabled = true,
            mixPercent = 100,
            lowFreqHz = 100f,
            lowGainDb = 6.0f,
            highFreqHz = 10000f,
            highGainDb = -6.0f
        )
        val freqs = floatArrayOf(50f, 1000f, 16000f)
        val curve = EqualizerEffect.computeMagnitudeCurveDb(eq, freqs)
        assertTrue("Expected bass boost > 3 dB at 50 Hz, got ${curve[0]}", curve[0] > 3.0f)
        assertTrue("Expected near-0 dB around 1 kHz, got ${curve[1]}", abs(curve[1]) < 1.5f)
        assertTrue("Expected treble cut < -3 dB at 16 kHz, got ${curve[2]}", curve[2] < -3.0f)
    }

    @Test
    fun monitoring_delay_config_and_delay_line_behave_accurately() {
        val defCfg = MonitoringConfig(livePreviewEnabled = true, delaySliderPosition = 0.0f)
        assertEquals(MonitoringLatencyZone.DEFAULT, defCfg.zone)
        assertEquals(0, defCfg.addedOutputDelayMs)
        assertEquals(1.25f, defCfg.bufferSizeMultiplier, 0.001f)
        assertEquals(100, defCfg.previewVolumePercent)
        assertEquals(500, MonitoringConfig.MAX_VOLUME_PERCENT)
        assertTrue(defCfg.echoCancellationEnabled)

        val fastCfg = MonitoringConfig(livePreviewEnabled = true, delaySliderPosition = -1.0f)
        assertEquals(MonitoringLatencyZone.FASTER, fastCfg.zone)
        assertEquals(0, fastCfg.addedOutputDelayMs)
        assertEquals(1.0f, fastCfg.bufferSizeMultiplier, 0.001f)

        val delayCfg = MonitoringConfig(livePreviewEnabled = true, delaySliderPosition = 0.5f)
        assertEquals(MonitoringLatencyZone.MORE_DELAY, delayCfg.zone)
        assertEquals(100, delayCfg.addedOutputDelayMs)

        val delayLine = MonitoringDelayLine(48_000)
        delayLine.setDelayMs(10)
        delayLine.reset()

        val input = FloatArray(1024)
        input[0] = 1.0f
        val output = FloatArray(1024)
        delayLine.process(input, input.size, output)

        assertEquals(0f, output[0], 0.001f)
        assertEquals(1.0f, output[480], 0.01f)
    }

    @Test
    fun resampler_produces_48khz_ratio_output() {
        val resampler = LinearResampler(inputSampleRateHz = 44_100, outputSampleRateHz = 48_000)
        val input = FloatArray(4410) { i -> sin(2.0 * PI * 440.0 * i / 44100.0).toFloat() }
        val output = FloatArray(6000)
        val outCount = resampler.process(input, input.size, output)
        assertTrue("Expected ~4800 resampled frames, got $outCount", outCount in 4780..4810)
    }

    @Test
    fun high_pass_filter_attenuates_boomy_50hz_and_preserves_440hz_voice() {
        val hpf = HighPassFilter(cutoffHz = 120.0, sampleRateHz = 48_000)
        val lowBoom = FloatArray(4800) { i -> (0.5f * sin(2.0 * PI * 50.0 * i / 48000.0)).toFloat() }
        hpf.processInPlace(lowBoom, lowBoom.size)
        val tailLowPeak = lowBoom.takeLast(1200).maxOf { abs(it) }
        assertTrue("Expected 50 Hz boom attenuated below 0.05, got $tailLowPeak", tailLowPeak < 0.05f)

        hpf.reset()
        val voiceTone = FloatArray(4800) { i -> (0.5f * sin(2.0 * PI * 440.0 * i / 48000.0)).toFloat() }
        hpf.processInPlace(voiceTone, voiceTone.size)
        val tailVoicePeak = voiceTone.takeLast(1200).maxOf { abs(it) }
        assertTrue("Expected 440 Hz voice preserved above 0.45, got $tailVoicePeak", tailVoicePeak > 0.45f)
    }

    @Test
    fun packet_loss_concealment_synthesizes_smooth_waveform_on_dropped_packet() {
        val plc = PacketLossConcealment(48_000)
        val valid = FloatArray(960) { i -> (0.4f * sin(2.0 * PI * 200.0 * i / 48000.0)).toFloat() }
        plc.recordValidFrame(valid, valid.size)

        val concealed = FloatArray(128)
        plc.concealPacket(concealed, concealed.size)
        assertEquals(1, plc.concealmentEventsCount)
        val concealedPeak = concealed.maxOf { abs(it) }
        assertTrue("Expected non-zero pitch-concealed waveform, got $concealedPeak", concealedPeak > 0.1f)
    }
}
