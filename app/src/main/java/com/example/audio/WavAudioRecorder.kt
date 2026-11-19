package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class WavRecordingResult(
    val file: File,
    val pcmBytesWritten: Long,
    val durationMillis: Long,
    val peakRmsNormalized: Float
)

/**
 * Records microphone audio via [AudioRecord] directly into a standard 16kHz, Mono, 16-bit PCM WAV file
 * as required by whisper.cpp.
 */
class WavAudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        private const val WAV_HEADER_SIZE = 44
        private const val TAG = "WavAudioRecorder"

        /**
         * Builds a standard 44-byte little-endian RIFF/WAVE header for uncompressed PCM audio.
         */
        fun buildWavHeader(
            pcmDataSize: Long,
            sampleRate: Int = SAMPLE_RATE_HZ,
            channels: Int = CHANNELS,
            bitsPerSample: Int = BITS_PER_SAMPLE
        ): ByteArray {
            val safeDataSize = pcmDataSize.coerceAtLeast(0L)
            val byteRate = sampleRate.toLong() * channels * (bitsPerSample / 8)
            val blockAlign = (channels * (bitsPerSample / 8)).toShort()
            val riffChunkSize = 36L + safeDataSize

            return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('R'.code.toByte())
                put('I'.code.toByte())
                put('F'.code.toByte())
                put('F'.code.toByte())
                putInt(riffChunkSize.toInt())
                put('W'.code.toByte())
                put('A'.code.toByte())
                put('V'.code.toByte())
                put('E'.code.toByte())
                put('f'.code.toByte())
                put('m'.code.toByte())
                put('t'.code.toByte())
                put(' '.code.toByte())
                putInt(16) // PCM fmt subchunk size
                putShort(1.toShort()) // AudioFormat = 1 (PCM)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate.toInt())
                putShort(blockAlign)
                putShort(bitsPerSample.toShort())
                put('d'.code.toByte())
                put('a'.code.toByte())
                put('t'.code.toByte())
                put('a'.code.toByte())
                putInt(safeDataSize.toInt())
            }.array()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null

    @Volatile
    private var keepRecording = false

    @Volatile
    private var pcmBytesWritten = 0L

    @Volatile
    private var peakRms = 0f

    @Volatile
    private var startTimeMs = 0L

    private val _amplitudeLevel = MutableStateFlow(0f)
    val amplitudeLevel: StateFlow<Float> = _amplitudeLevel.asStateFlow()

    val isRecording: Boolean
        get() = keepRecording

    fun currentOutputFile(): File? = outputFile

    fun start(targetFile: File): Result<File> {
        if (keepRecording) {
            return Result.failure(IllegalStateException("Recording already in progress"))
        }
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            return Result.failure(SecurityException("RECORD_AUDIO permission not granted"))
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            return Result.failure(IllegalStateException("AudioRecord unsupported configuration: $minBuffer"))
        }
        val bufferSizeBytes = (minBuffer * 2).coerceAtLeast(4096)

        return try {
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) targetFile.delete()

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return Result.failure(IllegalStateException("Failed to initialize AudioRecord"))
            }

            audioRecord = record
            outputFile = targetFile
            pcmBytesWritten = 0L
            peakRms = 0f
            startTimeMs = System.currentTimeMillis()
            keepRecording = true

            record.startRecording()

            recordingJob = scope.launch {
                val readBuffer = ShortArray(bufferSizeBytes / 2)
                val byteBuffer = ByteArray(readBuffer.size * 2)
                var stream: BufferedOutputStream? = null
                try {
                    stream = BufferedOutputStream(FileOutputStream(targetFile))
                    // Placeholder header; sizes are patched cleanly once recording stops
                    stream.write(buildWavHeader(0L))

                    while (isActive && keepRecording) {
                        val shortsRead = record.read(readBuffer, 0, readBuffer.size)
                        if (shortsRead > 0) {
                            var sumSquares = 0.0
                            for (i in 0 until shortsRead) {
                                val sample = readBuffer[i]
                                sumSquares += sample.toDouble() * sample.toDouble()
                                byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
                            }
                            val bytesToWrite = shortsRead * 2
                            stream.write(byteBuffer, 0, bytesToWrite)
                            pcmBytesWritten += bytesToWrite.toLong()

                            val rms = sqrt(sumSquares / shortsRead).toFloat() / Short.MAX_VALUE.toFloat()
                            if (rms > peakRms) peakRms = rms
                            _amplitudeLevel.value = (rms * 4.5f).coerceIn(0f, 1f)
                        } else if (shortsRead < 0) {
                            Log.w(TAG, "AudioRecord.read returned error code: $shortsRead")
                            break
                        }
                    }
                    stream.flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error while writing WAV stream", e)
                } finally {
                    try {
                        stream?.close()
                    } catch (_: Exception) {}
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start AudioRecord", e)
            cleanupRecorder()
            runCatching { targetFile.delete() }
            Result.failure(e)
        }
    }

    suspend fun stop(): WavRecordingResult? = withContext(Dispatchers.IO) {
        val file = outputFile
        if (!keepRecording && file == null) return@withContext null

        keepRecording = false
        _amplitudeLevel.value = 0f

        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord.stop failed", e)
        }

        try {
            recordingJob?.join()
        } catch (_: Exception) {}
        recordingJob = null

        cleanupRecorder()

        if (file == null || !file.exists()) {
            return@withContext null
        }

        val actualPcmBytes = (file.length() - WAV_HEADER_SIZE).coerceAtLeast(0L)
        if (actualPcmBytes <= 0L) {
            runCatching { file.delete() }
            outputFile = null
            return@withContext null
        }

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0L)
                raf.write(buildWavHeader(actualPcmBytes))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed patching WAV header", e)
            runCatching { file.delete() }
            outputFile = null
            return@withContext null
        }

        val elapsed = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
        WavRecordingResult(
            file = file,
            pcmBytesWritten = actualPcmBytes,
            durationMillis = elapsed,
            peakRmsNormalized = peakRms
        )
    }

    fun cancelAndDelete() {
        keepRecording = false
        _amplitudeLevel.value = 0f
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            }
        } catch (_: Exception) {}
        recordingJob?.cancel()
        recordingJob = null
        cleanupRecorder()
        outputFile?.let { f ->
            if (f.exists()) runCatching { f.delete() }
        }
        outputFile = null
    }

    private fun cleanupRecorder() {
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}
