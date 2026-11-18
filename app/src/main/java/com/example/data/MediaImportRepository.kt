package com.example.data

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.audio.AudioConfig
import com.example.audio.LinearResampler
import com.example.audio.WavPcmWriter
import com.example.dsp.EffectsRackConfig
import com.example.dsp.StudioDspPipeline
import com.example.dsp.VoiceChangerConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DeviceAudioItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val formatLabel: String,
    val dateModifiedSec: Long,
    val sizeBytes: Long
)

data class ImportedAudioSource(
    val uri: Uri,
    val displayName: String,
    val formatLabel: String,
    val fromVideo: Boolean,
    val pcmFile: File,
    val totalFrames48k: Long,
    val durationMs: Long
)

class MediaImportRepository(private val context: Context) {

    private val pcmCacheDir: File
        get() = File(context.cacheDir, "imported_pcm").apply {
            if (!exists()) mkdirs()
        }

    private val exportDir: File
        get() = File(context.filesDir, "exports").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Queries ALL accessible audio files across all device directories sorted newest-first.
     */
    fun queryAllDeviceAudioNewestFirst(): List<DeviceAudioItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC, ${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val items = mutableListOf<DeviceAudioItem>()

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val modCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val addedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                if (size <= 0L && duration <= 0L) continue

                val rawName = if (nameCol >= 0) cursor.getString(nameCol) else null
                val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                val displayName = (rawName?.takeIf { it.isNotBlank() } ?: title ?: "Audio_$id")
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                val modSec = if (modCol >= 0) cursor.getLong(modCol) else 0L
                val addSec = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                val bestDateSec = if (modSec > 0L) modSec else addSec
                val uri = ContentUris.withAppendedId(collection, id)

                items.add(
                    DeviceAudioItem(
                        id = id,
                        uri = uri,
                        displayName = displayName,
                        durationMs = duration.coerceAtLeast(0L),
                        formatLabel = deriveFormatLabel(displayName, mime),
                        dateModifiedSec = bestDateSec,
                        sizeBytes = size
                    )
                )
            }
        }

        return items.sortedByDescending { it.dateModifiedSec }
    }

    /**
     * Decodes any Android audio URI or extracts + decodes the audio track from any selected video URI
     * into a 48,000 Hz mono PCM16 scratch file while leaving the original file completely untouched.
     */
    fun importAndDecodeTo48kPcm(
        uri: Uri,
        fromVideo: Boolean,
        knownDisplayName: String? = null,
        knownFormatLabel: String? = null
    ): ImportedAudioSource {
        clearCachedPcmFiles()

        val resolvedName = knownDisplayName ?: resolveDisplayName(uri)
            ?: if (fromVideo) "Imported_Video_Audio" else "Imported_Audio"
        val resolvedFormat = knownFormatLabel ?: if (fromVideo) {
            "Video Audio"
        } else {
            deriveFormatLabel(resolvedName, context.contentResolver.getType(uri))
        }

        val outPcm = File(pcmCacheDir, "imported_${System.currentTimeMillis()}.pcm")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIdx = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    trackFormat = fmt
                    break
                }
            }

            if (audioTrackIdx < 0 || trackFormat == null) {
                throw IllegalStateException("Selected file does not contain an audio track.")
            }

            extractor.selectTrack(audioTrackIdx)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Unsupported audio stream.")

            var srcSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                AudioConfig.OUTPUT_SAMPLE_RATE_HZ
            }
            var srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }
            var srcPcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                trackFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
            ) {
                srcPcmEncoding = trackFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }

            var resampler = LinearResampler(srcSampleRate, AudioConfig.OUTPUT_SAMPLE_RATE_HZ)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            var monoWorkBuf = FloatArray(8192)
            var resampledBuf = FloatArray(16384)
            var outByteBuf = ByteArray(32768)

            BufferedOutputStream(FileOutputStream(outPcm), 65536).use { outStream ->
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            val newRate = if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            } else {
                                srcSampleRate
                            }
                            srcChannels = if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                            } else {
                                srcChannels
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ) {
                                srcPcmEncoding = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                            if (newRate != srcSampleRate) {
                                srcSampleRate = newRate
                                resampler = LinearResampler(srcSampleRate, AudioConfig.OUTPUT_SAMPLE_RATE_HZ)
                            }
                        }
                        outIdx >= 0 -> {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val slice = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN)

                                val bytesPerSample = if (srcPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                val totalSamples = bufferInfo.size / bytesPerSample
                                val frameCount = totalSamples / srcChannels

                                if (frameCount > 0) {
                                    if (monoWorkBuf.size < frameCount) {
                                        monoWorkBuf = FloatArray(frameCount * 2)
                                    }
                                    downmixToMonoFloat(
                                        buf = slice,
                                        frameCount = frameCount,
                                        channels = srcChannels,
                                        isFloatPcm = srcPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT,
                                        dest = monoWorkBuf
                                    )

                                    val maxOutNeeded = ((frameCount.toLong() * AudioConfig.OUTPUT_SAMPLE_RATE_HZ) /
                                        srcSampleRate.coerceAtLeast(8000)).toInt() + 64
                                    if (resampledBuf.size < maxOutNeeded) {
                                        resampledBuf = FloatArray(maxOutNeeded * 2)
                                    }
                                    val outSamples = resampler.process(monoWorkBuf, frameCount, resampledBuf)
                                    if (outSamples > 0) {
                                        val bytesNeeded = outSamples * 2
                                        if (outByteBuf.size < bytesNeeded) {
                                            outByteBuf = ByteArray(bytesNeeded * 2)
                                        }
                                        var bPos = 0
                                        for (sIdx in 0 until outSamples) {
                                            val clamped = resampledBuf[sIdx].coerceIn(-1f, 1f)
                                            val pcm16 = (clamped * 32767f).roundToInt().coerceIn(-32768, 32767)
                                            outByteBuf[bPos++] = (pcm16 and 0xFF).toByte()
                                            outByteBuf[bPos++] = ((pcm16 ushr 8) and 0xFF).toByte()
                                        }
                                        outStream.write(outByteBuf, 0, bytesNeeded)
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                        }
                    }
                }
                outStream.flush()
            }

            val totalFrames = outPcm.length() / 2L
            if (totalFrames <= 0L) {
                throw IllegalStateException("Decoded audio stream was empty.")
            }
            val durationMs = (totalFrames * 1000L) / AudioConfig.OUTPUT_SAMPLE_RATE_HZ

            return ImportedAudioSource(
                uri = uri,
                displayName = resolvedName,
                formatLabel = resolvedFormat,
                fromVideo = fromVideo,
                pcmFile = outPcm,
                totalFrames48k = totalFrames,
                durationMs = durationMs
            )
        } catch (t: Throwable) {
            outPcm.delete()
            throw t
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Renders the entire imported 48 kHz audio stream through the exact active Studio DSP pipeline
     * into a broadcast 48 kHz mono 16-bit WAV file and returns a FileProvider Uri ready to share.
     */
    fun renderAndExportWav(
        source: ImportedAudioSource,
        effectsConfig: EffectsRackConfig,
        voiceChangerConfig: VoiceChangerConfig,
        onProgress: (Float) -> Unit = {}
    ): Pair<File, Uri> {
        val baseName = source.displayName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(36)
            .ifBlank { "StudioAudio" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(exportDir, "VoxForge_${baseName}_$stamp.wav")

        val pipeline = StudioDspPipeline(AudioConfig.OUTPUT_SAMPLE_RATE_HZ).apply {
            updateEffectsConfig(effectsConfig)
            updateVoiceChangerConfig(voiceChangerConfig)
            reset()
        }

        val writer = WavPcmWriter(outFile)
        writer.open()

        val blockFrames = 1024
        val byteBuf = ByteArray(blockFrames * 2)
        val floatBuf = FloatArray(blockFrames)
        var framesDone = 0L
        val totalFrames = source.totalFrames48k.coerceAtLeast(1L)

        try {
            BufferedInputStream(FileInputStream(source.pcmFile), 65536).use { inStream ->
                while (true) {
                    val bytesRead = readFullyUpTo(inStream, byteBuf, byteBuf.size)
                    if (bytesRead <= 0) break
                    val framesRead = bytesRead / 2
                    for (i in 0 until framesRead) {
                        val lo = byteBuf[i * 2].toInt() and 0xFF
                        val hi = byteBuf[i * 2 + 1].toInt()
                        val sample16 = (hi shl 8) or lo
                        floatBuf[i] = sample16 / 32768.0f
                    }
                    pipeline.processInPlace(floatBuf, framesRead)
                    writer.writeSamples(floatBuf, framesRead)
                    framesDone += framesRead
                    onProgress((framesDone.toFloat() / totalFrames).coerceIn(0f, 1f))
                }
            }
            writer.close()
        } catch (t: Throwable) {
            runCatching { writer.close() }
            outFile.delete()
            throw t
        }

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        return outFile to shareUri
    }

    fun clearCachedPcmFiles() {
        pcmCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun readFullyUpTo(stream: BufferedInputStream, dest: ByteArray, targetBytes: Int): Int {
        var total = 0
        while (total < targetBytes) {
            val n = stream.read(dest, total, targetBytes - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun downmixToMonoFloat(
        buf: ByteBuffer,
        frameCount: Int,
        channels: Int,
        isFloatPcm: Boolean,
        dest: FloatArray
    ) {
        val invChannels = 1.0f / channels.coerceAtLeast(1)
        if (isFloatPcm) {
            for (f in 0 until frameCount) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += buf.float
                }
                dest[f] = sum * invChannels
            }
        } else {
            for (f in 0 until frameCount) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += buf.short / 32768.0f
                }
                dest[f] = sum * invChannels
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun deriveFormatLabel(fileName: String, mime: String?): String {
        val ext = fileName.substringAfterLast('.', "").uppercase(Locale.US)
        if (ext.isNotBlank() && ext.length <= 5) return ext
        return when {
            mime == null -> "AUDIO"
            mime.contains("wav") -> "WAV"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            mime.contains("mp4") || mime.contains("aac") -> "M4A"
            mime.contains("flac") -> "FLAC"
            mime.contains("ogg") -> "OGG"
            mime.contains("opus") -> "OPUS"
            else -> mime.substringAfter('/').uppercase(Locale.US)
        }
    }
}
