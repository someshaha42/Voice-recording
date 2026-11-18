package com.example.audio

/**
 * Fixed, non-configurable output recording specification required for
 * broadcast YouTube voiceover WAV output.
 */
object AudioConfig {
    const val OUTPUT_FORMAT_LABEL = "WAV (PCM)"
    const val OUTPUT_SAMPLE_RATE_HZ = 48_000
    const val OUTPUT_BITS_PER_SAMPLE = 16
    const val OUTPUT_CHANNELS = 1
    const val OUTPUT_BITRATE_BPS =
        OUTPUT_SAMPLE_RATE_HZ * OUTPUT_BITS_PER_SAMPLE * OUTPUT_CHANNELS // 768,000 bps (768 kbps)
    const val OUTPUT_BITRATE_KBPS = OUTPUT_BITRATE_BPS / 1000 // 768 kbps
    const val BYTES_PER_SAMPLE = OUTPUT_BITS_PER_SAMPLE / 8
    const val BYTES_PER_SECOND = OUTPUT_SAMPLE_RATE_HZ * OUTPUT_CHANNELS * BYTES_PER_SAMPLE // 96,000 B/s

    const val DSP_HOP_SIZE = 128 // 2.67 ms @ 48 kHz
    const val DSP_FFT_SIZE = 512 // 10.67 ms @ 48 kHz
}

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    INTERRUPTED_PAUSED
}

data class AudioHardwareState(
    val inputDeviceId: Int? = null,
    val inputDeviceName: String = "Internal Microphone",
    val isUsbMicConnected: Boolean = false,
    val supportedSampleRates: List<Int> = listOf(48_000),
    val activeInputSampleRateHz: Int = AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
    val activeInputChannelCount: Int = 1,
    val isResamplingActive: Boolean = false,
    val outputDeviceId: Int? = null,
    val outputDeviceName: String = "Phone Speaker",
    val isWiredHeadphoneConnected: Boolean = false,
    val isBluetoothOutputConnected: Boolean = false,
    val hardwareValidated: Boolean = false
)

data class LiveAudioMetrics(
    val rmsDb: Float = -60f,
    val peakDb: Float = -60f,
    val isClipping: Boolean = false,
    val clipCount: Int = 0,
    val gateOpen: Boolean = false,
    val gateGainReductionDb: Float = 0f,
    val recordingDurationMs: Long = 0L,
    val estimatedMonitoringLatencyMs: Int = 28
)
