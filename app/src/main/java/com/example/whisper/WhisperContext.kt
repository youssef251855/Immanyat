package com.example.whisper

import android.content.Context
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperException
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class WhisperAbiUnsupportedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Thread-safe wrapper around the native whisper.cpp Android binding ([dev.ffmpegkit.whisper.Whisper]).
 * Loads `ggml-base-quran-q5_0.bin` from [Context.getFilesDir] and performs offline Arabic transcription
 * on [Dispatchers.IO].
 */
class WhisperContext private constructor(
    private val model: WhisperModel,
    val modelFile: File
) {
    private val mutex = Mutex()

    @Volatile
    private var released = false

    suspend fun transcribeWav(audioPath: String, language: String = "ar"): String =
        withContext(Dispatchers.IO) {
            check(!released) { "WhisperContext has already been released" }
            val wavFile = File(audioPath)
            require(wavFile.exists() && wavFile.length() > 44L) {
                "Invalid or empty WAV audio file: $audioPath"
            }

            mutex.withLock {
                check(!released) { "WhisperContext was released while waiting for transcription" }
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                val config = WhisperConfig(
                    language = language,
                    translate = false,
                    threads = threads,
                    maxSegmentLength = 0,
                    printTimestamps = false
                )
                try {
                    val result = Whisper.transcribe(model, wavFile.absolutePath, config)
                    val raw = result.text.trim()
                    if (raw.isNotEmpty()) {
                        raw
                    } else {
                        result.segments.joinToString(separator = " ") { it.text.trim() }.trim()
                    }
                } catch (e: UnsatisfiedLinkError) {
                    throw WhisperAbiUnsupportedException(
                        "معالج هذا الجهاز لا يدعم مكتبة whisper.cpp المحلية (arm64-v8a فقط).",
                        e
                    )
                }
            }
        }

    fun release() {
        if (!released) {
            released = true
            runCatching { Whisper.releaseModel(model) }
                .onFailure { Log.w(TAG, "Error releasing WhisperModel", it) }
        }
    }

    companion object {
        private const val TAG = "WhisperContext"

        suspend fun createFromFile(context: Context, modelFile: File): WhisperContext =
            withContext(Dispatchers.IO) {
                require(modelFile.exists() && modelFile.length() >= QuranModelManager.MIN_VALID_MODEL_BYTES) {
                    "ملف نموذج التسميع غير موجود أو غير مكتمل: ${modelFile.absolutePath}"
                }
                try {
                    val loaded = Whisper.loadModel(context.applicationContext, modelFile.absolutePath)
                    WhisperContext(loaded, modelFile)
                } catch (e: UnsatisfiedLinkError) {
                    throw WhisperAbiUnsupportedException(
                        "معمارية هذا الجهاز لا تدعم تشغيل نموذج Whisper المحلي (يتطلب arm64-v8a).",
                        e
                    )
                } catch (e: WhisperException) {
                    throw IllegalStateException("تعذر تحميل نموذج التلاوة المحلي: ${e.message}", e)
                }
            }
    }
}

/**
 * Caches the active [WhisperContext] in memory so consecutive Tasmee' sessions run immediately
 * without reloading the 55 MB GGML model each time.
 */
object OfflineWhisperEngine {
    private val cacheMutex = Mutex()
    private var cachedContext: WhisperContext? = null
    private var cachedLastModified: Long = -1L

    suspend fun getOrCreateContext(context: Context): WhisperContext = cacheMutex.withLock {
        val modelFile = QuranModelManager.getModelFile(context)
        if (!QuranModelManager.isModelReady(context)) {
            cachedContext?.release()
            cachedContext = null
            throw IllegalStateException("نموذج التسميع بدون إنترنت غير مكتمل التحميل بعد.")
        }

        val currentModified = modelFile.lastModified()
        val existing = cachedContext
        if (existing != null && cachedLastModified == currentModified) {
            return@withLock existing
        }

        existing?.release()
        val created = WhisperContext.createFromFile(context.applicationContext, modelFile)
        cachedContext = created
        cachedLastModified = currentModified
        created
    }

    suspend fun transcribeWav(context: Context, wavPath: String): String {
        val ctx = getOrCreateContext(context)
        return ctx.transcribeWav(audioPath = wavPath, language = "ar")
    }

    suspend fun release() = cacheMutex.withLock {
        cachedContext?.release()
        cachedContext = null
        cachedLastModified = -1L
    }
}
