package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.audio.AudioConfig
import com.example.audio.WavPcmWriter
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val createdAtMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val channels: Int,
    val wasRecovered: Boolean = false
)

/**
 * Manages recorded 48 kHz / 16-bit / Mono WAV files on app-scoped external storage,
 * including startup recovery of incomplete WAV headers after interruptions.
 */
class RecordingFileManager(private val context: Context) {

    fun getRecordingsDirectory(): File {
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        val dir = extDir?.let { File(it, "VoxForge") } ?: File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createNewRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var candidate = File(getRecordingsDirectory(), "Voiceover_$timestamp.wav")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(getRecordingsDirectory(), "Voiceover_${timestamp}_$counter.wav")
            counter++
        }
        return candidate
    }

    /**
     * Scans the recordings directory, repairs any WAV files whose RIFF/data headers were
     * left incomplete due to unexpected interruption, and returns sorted recordings newest-first.
     */
    fun loadRecordingsAndRepair(): List<RecordingItem> {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { file ->
            file.isFile && file.extension.equals("wav", ignoreCase = true)
        } ?: return emptyList()

        return files
            .filter { it.length() > WavPcmWriter.WAV_HEADER_SIZE }
            .map { file ->
                val repaired = runCatching { WavPcmWriter.repairHeaderIfNeeded(file) }.getOrDefault(false)
                parseWavMetadata(file, repaired)
            }
            .sortedByDescending { it.createdAtMs }
    }

    fun renameRecording(item: RecordingItem, newBaseName: String): Result<RecordingItem> = runCatching {
        val sanitized = newBaseName
            .trim()
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .removeSuffix(".wav")
            .removeSuffix(".WAV")
            .trim()
        require(sanitized.isNotBlank()) { "Recording name cannot be empty." }

        val src = File(item.filePath)
        require(src.exists()) { "Source recording no longer exists." }

        val dst = File(src.parentFile, "$sanitized.wav")
        require(!dst.exists() || dst.absolutePath == src.absolutePath) {
            "A recording with that name already exists."
        }
        if (dst.absolutePath != src.absolutePath) {
            check(src.renameTo(dst)) { "Failed to rename recording file." }
        }
        parseWavMetadata(dst, item.wasRecovered)
    }

    fun deleteRecording(item: RecordingItem): Boolean {
        val file = File(item.filePath)
        return !file.exists() || file.delete()
    }

    fun getShareUri(item: RecordingItem): Uri {
        val file = File(item.filePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun parseWavMetadata(file: File, wasRecovered: Boolean): RecordingItem {
        var sampleRate = AudioConfig.OUTPUT_SAMPLE_RATE_HZ
        var channels = AudioConfig.OUTPUT_CHANNELS
        var bitsPerSample = AudioConfig.OUTPUT_BITS_PER_SAMPLE
        var dataBytes = (file.length() - WavPcmWriter.WAV_HEADER_SIZE).coerceAtLeast(0L)

        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() >= WavPcmWriter.WAV_HEADER_SIZE) {
                    val header = ByteArray(WavPcmWriter.WAV_HEADER_SIZE)
                    raf.readFully(header)
                    channels = ((header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8))
                        .coerceAtLeast(1)
                    sampleRate = WavPcmWriter.readIntLE(header, 24).coerceAtLeast(8000)
                    bitsPerSample = ((header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8))
                        .coerceAtLeast(8)
                    val headerDataLen = WavPcmWriter.readIntLE(header, 40).toLong() and 0xFFFFFFFFL
                    if (headerDataLen > 0L && headerDataLen <= raf.length() - WavPcmWriter.WAV_HEADER_SIZE) {
                        dataBytes = headerDataLen
                    }
                }
            }
        }

        val byteRate = (sampleRate * channels * (bitsPerSample / 8)).coerceAtLeast(1)
        val durationMs = (dataBytes * 1000L) / byteRate

        return RecordingItem(
            id = file.absolutePath,
            fileName = file.name,
            filePath = file.absolutePath,
            createdAtMs = file.lastModified(),
            durationMs = durationMs,
            sizeBytes = file.length(),
            sampleRateHz = sampleRate,
            bitsPerSample = bitsPerSample,
            channels = channels,
            wasRecovered = wasRecovered
        )
    }
}
