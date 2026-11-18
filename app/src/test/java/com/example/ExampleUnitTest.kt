package com.example

import com.example.audio.AudioConfig
import com.example.audio.LinearResampler
import com.example.audio.MonitoringConfig
import com.example.audio.MonitoringDelayLine
import com.example.audio.MonitoringLatencyZone
import com.example.audio.WavPcmWriter
import com.example.dsp.NoiseGateConfig
import com.example.dsp.NoiseGateProcessor
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
            assertEquals(9600L, bytesWritten) // 4800 samples * 2 bytes
            assertEquals(9600L + 44L, tempFile.length())

            // Verify WAV header fields
            val header = ByteArray(44)
            RandomAccessFile(tempFile, "r").use { it.readFully(header) }
            assertEquals('R'.code.toByte(), header[0])
            assertEquals('I'.code.toByte(), header[1])
            assertEquals('F'.code.toByte(), header[2])
            assertEquals('F'.code.toByte(), header[3])
            assertEquals(48_000, WavPcmWriter.readIntLE(header, 24))
            assertEquals(96_000, WavPcmWriter.readIntLE(header, 28))
            assertEquals(9600, WavPcmWriter.readIntLE(header, 40))

            // Simulate crash leaving data chunk size = 0 and verify repair
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
                thresholdDb = -30f,
                attackMs = 2f,
                releaseMs = 40f
            )
        )
        gate.reset()

        // Quiet signal (-55 dBFS) should stay closed and attenuated
        val quiet = FloatArray(4096) { 0.0018f }
        gate.processInPlace(quiet, quiet.size)
        assertFalse(gate.lastIsGateOpen)
        assertTrue(abs(quiet.last()) < 0.0005f)

        // Loud voice signal (-6 dBFS) should open the gate
        val loud = FloatArray(4096) { 0.5f }
        gate.processInPlace(loud, loud.size)
        assertTrue(gate.lastIsGateOpen)
        assertTrue(abs(loud.last()) > 0.45f)

        // When disabled, bypasses cleanly even on quiet signals
        gate.updateConfig(NoiseGateConfig(enabled = false, thresholdDb = -10f))
        val bypassTest = FloatArray(2048) { 0.002f }
        gate.processInPlace(bypassTest, bypassTest.size)
        assertTrue(gate.lastIsGateOpen)
        assertTrue(abs(bypassTest.last() - 0.002f) < 0.0002f)
    }

    @Test
    fun voice_changer_dsp_processes_all_presets_without_nan_or_clipping() {
        val dsp = VoiceChangerDsp(48_000)
        val buffer = FloatArray(2048)

        for (preset in VoicePreset.entries) {
            dsp.updateConfig(preset.defaultConfig())
            dsp.reset()

            for (block in 0 until 6) {
                for (i in buffer.indices) {
                    val sampleIdx = block * buffer.size + i
                    buffer[i] = (0.45f * sin(2.0 * PI * 180.0 * sampleIdx / 48000.0)).toFloat()
                }
                dsp.processInPlace(buffer, buffer.size)

                for (s in buffer) {
                    assertFalse("NaN detected for preset $preset", s.isNaN())
                    assertFalse("Inf detected for preset $preset", s.isInfinite())
                    assertTrue("Clipping > 1.0 detected for preset $preset: $s", abs(s) <= 1.0f)
                }
            }
        }
    }

    @Test
    fun monitoring_delay_config_and_delay_line_behave_accurately() {
        val defCfg = MonitoringConfig(livePreviewEnabled = true, delaySliderPosition = 0.0f)
        assertEquals(MonitoringLatencyZone.DEFAULT, defCfg.zone)
        assertEquals(0, defCfg.addedOutputDelayMs)
        assertEquals(2.0f, defCfg.bufferSizeMultiplier, 0.001f)

        val fastCfg = MonitoringConfig(livePreviewEnabled = true, delaySliderPosition = -1.0f)
        assertEquals(MonitoringLatencyZone.FASTER, fastCfg.zone)
        assertEquals(0, fastCfg.addedOutputDelayMs)
        assertEquals(1.0f, fastCfg.bufferSizeMultiplier, 0.001f)

        val delayCfg = MonitoringConfig(livePreviewEnabled = true, delaySliderPosition = 0.5f)
        assertEquals(MonitoringLatencyZone.MORE_DELAY, delayCfg.zone)
        assertEquals(100, delayCfg.addedOutputDelayMs)

        // Verify circular delay line delays impulse by ~480 samples (10 ms @ 48 kHz)
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
}
