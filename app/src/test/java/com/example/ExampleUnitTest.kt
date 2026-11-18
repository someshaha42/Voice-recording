package com.example

import com.example.audio.AudioConfig
import com.example.audio.LinearResampler
import com.example.audio.MonitoringConfig
import com.example.audio.MonitoringDelayLine
import com.example.audio.MonitoringLatencyZone
import com.example.audio.WavPcmWriter
import com.example.dsp.EchoConfig
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EffectsRackProcessor
import com.example.dsp.EqualizerConfig
import com.example.dsp.FilterSlope
import com.example.dsp.HallConfig
import com.example.dsp.HighPassConfig
import com.example.dsp.HighPassFilter
import com.example.dsp.LowPassConfig
import com.example.dsp.NoiseGateConfig
import com.example.dsp.NoiseGateProcessor
import com.example.dsp.PacketLossConcealment
import com.example.dsp.ReverbConfig
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
