package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects USB-C microphones (such as FIFINE AM8) and wired/USB headphones in real time,
 * probes supported sample rates honestly via AudioRecord validation, and exposes preferred
 * AudioDeviceInfo objects for routing.
 */
class UsbMicrophoneManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _hardwareState = MutableStateFlow(AudioHardwareState())
    val hardwareState: StateFlow<AudioHardwareState> = _hardwareState.asStateFlow()

    @Volatile
    var preferredInputDeviceInfo: AudioDeviceInfo? = null
        private set

    @Volatile
    var preferredOutputDeviceInfo: AudioDeviceInfo? = null
        private set

    private var onDeviceTopologyChanged: ((AudioHardwareState) -> Unit)? = null
    private var isRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }
    }

    fun startMonitoring(onChanged: ((AudioHardwareState) -> Unit)? = null) {
        onDeviceTopologyChanged = onChanged
        if (!isRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
            isRegistered = true
        }
        refreshDevices()
    }

    fun stopMonitoring() {
        if (isRegistered) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            isRegistered = false
        }
        onDeviceTopologyChanged = null
    }

    fun refreshDevices() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val usbMic = inputs.firstOrNull { isUsbDeviceType(it.type) }
        val builtInMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val selectedInput = usbMic ?: builtInMic ?: inputs.firstOrNull()

        val wiredHeadphone = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                isUsbDeviceType(it.type)
        }
        val bluetoothOut = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val speakerOut = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val selectedOutput = wiredHeadphone ?: bluetoothOut ?: speakerOut ?: outputs.firstOrNull()

        preferredInputDeviceInfo = selectedInput
        preferredOutputDeviceInfo = selectedOutput

        val (supportedRates, validatedRate) = validateCaptureRates(selectedInput)

        val rawInputName = selectedInput?.productName?.toString()?.trim().orEmpty()
        val inputDisplayName = when {
            usbMic != null -> {
                val base = rawInputName.ifBlank { "USB-C Microphone" }
                if (base.contains("USB", ignoreCase = true)) base else "$base (USB-C)"
            }
            selectedInput != null -> {
                if (rawInputName.isBlank() || rawInputName.equals(Build.MODEL, ignoreCase = true)) {
                    "Internal Microphone"
                } else {
                    rawInputName
                }
            }
            else -> "No Microphone Detected"
        }

        val outName = when {
            wiredHeadphone != null -> {
                val prod = wiredHeadphone.productName?.toString()?.trim().orEmpty()
                if (isUsbDeviceType(wiredHeadphone.type)) {
                    if (prod.isNotBlank()) "$prod (USB Audio)" else "USB-C Headphones"
                } else {
                    if (prod.isNotBlank() && !prod.equals(Build.MODEL, ignoreCase = true)) {
                        "$prod (Wired)"
                    } else {
                        "Wired Headphones"
                    }
                }
            }
            bluetoothOut != null -> {
                val prod = bluetoothOut.productName?.toString()?.trim().orEmpty()
                if (prod.isNotBlank()) "$prod (Bluetooth)" else "Bluetooth Audio"
            }
            else -> "Phone Speaker (Use headphones for preview)"
        }

        val inputChannels = selectedInput?.channelCounts?.firstOrNull { it > 0 } ?: 1

        val newState = AudioHardwareState(
            inputDeviceId = selectedInput?.id,
            inputDeviceName = inputDisplayName,
            isUsbMicConnected = usbMic != null,
            supportedSampleRates = supportedRates,
            activeInputSampleRateHz = validatedRate,
            activeInputChannelCount = inputChannels.coerceIn(1, 2),
            isResamplingActive = validatedRate != AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
            outputDeviceId = selectedOutput?.id,
            outputDeviceName = outName,
            isWiredHeadphoneConnected = wiredHeadphone != null,
            isBluetoothOutputConnected = wiredHeadphone == null && bluetoothOut != null,
            hardwareValidated = selectedInput != null && supportedRates.isNotEmpty()
        )

        val previous = _hardwareState.value
        _hardwareState.value = newState
        if (previous.inputDeviceId != newState.inputDeviceId ||
            previous.isUsbMicConnected != newState.isUsbMicConnected ||
            previous.activeInputSampleRateHz != newState.activeInputSampleRateHz
        ) {
            onDeviceTopologyChanged?.invoke(newState)
        }
    }

    private fun isUsbDeviceType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return true
        }
        return false
    }

    /**
     * Validates actual capture capabilities at runtime without falsely reporting
     * unsupported sample rates. Prefers 48,000 Hz when supported by the device.
     */
    @SuppressLint("MissingPermission")
    private fun validateCaptureRates(device: AudioDeviceInfo?): Pair<List<Int>, Int> {
        val candidateRates = buildList {
            val deviceReported = device?.sampleRates?.filter { it > 0 }?.sortedDescending().orEmpty()
            if (deviceReported.isNotEmpty()) {
                if (AudioConfig.OUTPUT_SAMPLE_RATE_HZ in deviceReported) {
                    add(AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                }
                deviceReported.forEach { if (it !in this) add(it) }
            }
            listOf(48_000, 44_100, 96_000, 32_000, 16_000).forEach {
                if (it !in this) add(it)
            }
        }

        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val validRates = mutableListOf<Int>()
        for (rate in candidateRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue

            if (!hasMicPerm) {
                // Without RECORD_AUDIO permission yet, report AudioDeviceInfo / AudioRecord.getMinBufferSize results
                if (device == null || device.sampleRates.isEmpty() || rate in device.sampleRates) {
                    validRates.add(rate)
                }
                continue
            }

            // Probe actual AudioRecord initialization for this rate
            var probe: AudioRecord? = null
            try {
                probe = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .build()
                if (probe.state == AudioRecord.STATE_INITIALIZED) {
                    validRates.add(rate)
                }
            } catch (_: Throwable) {
                // Unsupported rate or device busy
            } finally {
                try {
                    probe?.release()
                } catch (_: Throwable) {
                }
            }
        }

        val chosen = when {
            AudioConfig.OUTPUT_SAMPLE_RATE_HZ in validRates -> AudioConfig.OUTPUT_SAMPLE_RATE_HZ
            validRates.isNotEmpty() -> validRates.first()
            else -> AudioConfig.OUTPUT_SAMPLE_RATE_HZ
        }
        return validRates.distinct() to chosen
    }
}
