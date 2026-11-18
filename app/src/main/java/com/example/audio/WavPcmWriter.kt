package com.example.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * Writes standard 44-byte RIFF/WAVE PCM files using the fixed specification:
 * 48,000 Hz, 16-bit signed little-endian, Mono (768 kbps).
 *
 * Periodically flushes chunk sizes to disk and supports startup WAV header repair
 * for deterministic recording recovery after unexpected interruptions.
 */
class WavPcmWriter(private val file: File) {
    private var outputStream: BufferedOutputStream? = null
    private var pcmBytesWritten: Long = 0L
    private var bytesSinceLastHeaderSync: Long = 0L
    private var pcmByteBuffer = ByteArray(4096)

    @Volatile
    var isOpen: Boolean = false
        private set

    fun open() {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file, false)
        val bos = BufferedOutputStream(fos, 16_384)
        bos.write(createHeaderBytes(0L))
        bos.flush()
        outputStream = bos
        pcmBytesWritten = 0L
        bytesSinceLastHeaderSync = 0L
        isOpen = true
    }

    /**
     * Writes [count] normalized float samples [-1.0f, 1.0f] as 16-bit PCM mono.
     * Returns the number of clipped samples detected in this block.
     */
    fun writeSamples(samples: FloatArray, count: Int): Int {
        val stream = outputStream ?: return 0
        if (!isOpen || count <= 0) return 0

        val neededBytes = count * 2
        if (pcmByteBuffer.size < neededBytes) {
            pcmByteBuffer = ByteArray(neededBytes)
        }

        var clipCount = 0
        var byteIdx = 0
        for (i in 0 until count) {
            val s = samples[i]
            if (s >= 0.99f || s <= -0.99f) {
                clipCount++
            }
            val clamped = s.coerceIn(-1.0f, 1.0f)
            val pcm16 = (clamped * 32767.0f).roundToInt().coerceIn(-32768, 32767)
            pcmByteBuffer[byteIdx++] = (pcm16 and 0xFF).toByte()
            pcmByteBuffer[byteIdx++] = ((pcm16 ushr 8) and 0xFF).toByte()
        }

        stream.write(pcmByteBuffer, 0, neededBytes)
        pcmBytesWritten += neededBytes
        bytesSinceLastHeaderSync += neededBytes

        // Sync WAV header sizes every ~2 seconds (192,000 bytes) for crash/interruption recovery
        if (bytesSinceLastHeaderSync >= AudioConfig.BYTES_PER_SECOND * 2L) {
            stream.flush()
            updateHeaderSizes(file, pcmBytesWritten)
            bytesSinceLastHeaderSync = 0L
        }

        return clipCount
    }

    fun flush() {
        if (!isOpen) return
        outputStream?.flush()
        updateHeaderSizes(file, pcmBytesWritten)
    }

    fun close(): Long {
        if (!isOpen) return pcmBytesWritten
        isOpen = false
        try {
            outputStream?.flush()
            outputStream?.close()
        } finally {
            outputStream = null
        }
        updateHeaderSizes(file, pcmBytesWritten)
        return pcmBytesWritten
    }

    fun getRecordedDurationMs(): Long {
        return (pcmBytesWritten * 1000L) / AudioConfig.BYTES_PER_SECOND
    }

    companion object {
        const val WAV_HEADER_SIZE = 44

        fun createHeaderBytes(pcmDataBytes: Long): ByteArray {
            val header = ByteArray(WAV_HEADER_SIZE)
            val totalDataLen = pcmDataBytes + 36L
            val sampleRate = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
            val channels = AudioConfig.OUTPUT_CHANNELS
            val bitsPerSample = AudioConfig.OUTPUT_BITS_PER_SAMPLE
            val byteRate = AudioConfig.BYTES_PER_SECOND
            val blockAlign = channels * (bitsPerSample / 8)

            // "RIFF"
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            writeIntLE(header, 4, totalDataLen.toInt())
            // "WAVE"
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            // "fmt "
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            writeIntLE(header, 16, 16) // PCM fmt chunk size
            writeShortLE(header, 20, 1) // AudioFormat = 1 (PCM)
            writeShortLE(header, 22, channels.toShort())
            writeIntLE(header, 24, sampleRate)
            writeIntLE(header, 28, byteRate)
            writeShortLE(header, 32, blockAlign.toShort())
            writeShortLE(header, 34, bitsPerSample.toShort())
            // "data"
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            writeIntLE(header, 40, pcmDataBytes.toInt())

            return header
        }

        fun updateHeaderSizes(file: File, pcmDataBytes: Long) {
            if (!file.exists() || file.length() < WAV_HEADER_SIZE) return
            RandomAccessFile(file, "rw").use { raf ->
                val totalDataLen = (pcmDataBytes + 36L).toInt()
                val dataLen = pcmDataBytes.toInt()
                raf.seek(4L)
                raf.write(intToLE(totalDataLen))
                raf.seek(40L)
                raf.write(intToLE(dataLen))
            }
        }

        /**
         * Repairs any WAV file whose RIFF/data chunk header is missing or mismatched
         * due to an unexpected interruption or process termination.
         * Returns true if the file was repaired.
         */
        fun repairHeaderIfNeeded(file: File): Boolean {
            if (!file.exists() || file.length() <= WAV_HEADER_SIZE) return false
            val fileLen = file.length()
            val expectedPcmBytes = ((fileLen - WAV_HEADER_SIZE) / 2L) * 2L // align to 16-bit frame
            RandomAccessFile(file, "rw").use { raf ->
                val header = ByteArray(WAV_HEADER_SIZE)
                raf.seek(0L)
                raf.readFully(header)
                val isRiff = header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
                val isWave = header[8] == 'W'.code.toByte() && header[9] == 'A'.code.toByte() &&
                    header[10] == 'V'.code.toByte() && header[11] == 'E'.code.toByte()
                if (!isRiff || !isWave) return false

                val currentDataSize = readIntLE(header, 40).toLong() and 0xFFFFFFFFL
                if (currentDataSize != expectedPcmBytes) {
                    raf.seek(4L)
                    raf.write(intToLE((expectedPcmBytes + 36L).toInt()))
                    raf.seek(40L)
                    raf.write(intToLE(expectedPcmBytes.toInt()))
                    return true
                }
            }
            return false
        }

        private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        private fun writeShortLE(buf: ByteArray, offset: Int, value: Short) {
            val v = value.toInt()
            buf[offset] = (v and 0xFF).toByte()
            buf[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        }

        private fun intToLE(value: Int): ByteArray = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte()
        )

        fun readIntLE(buf: ByteArray, offset: Int): Int {
            return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
        }
    }
}
