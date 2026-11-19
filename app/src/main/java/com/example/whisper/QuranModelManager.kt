package com.example.whisper

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.SecureApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    READY,
    FAILED
}

data class WhisperModelDownloadState(
    val status: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
)

object QuranModelManager {
    const val MODEL_FILE_NAME = "ggml-base-quran-q5_0.bin"
    const val EXPECTED_MODEL_BYTES = 55_295_433L
    const val MIN_VALID_MODEL_BYTES = 45_000_000L
    const val WORK_NAME = "download_quran_whisper_model"

    const val PRIMARY_HUGGINGFACE_URL =
        "https://huggingface.co/androidapps11/whisper-base-ar-quran-ggml/resolve/main/ggml-base-quran-q5_0.bin?download=true"
    const val MIRROR_HUGGINGFACE_URL =
        "https://huggingface.co/herrero1234/ayahsync-model/resolve/main/ggml-base-ar-quran-q5_0.bin?download=true"

    internal const val KEY_PROGRESS = "progress_percent"
    internal const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
    internal const val KEY_TOTAL_BYTES = "total_bytes"
    internal const val KEY_ERROR_MESSAGE = "error_message"

    private const val TAG = "QuranModelManager"

    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    private val _downloadState = MutableStateFlow(WhisperModelDownloadState())
    val downloadState: StateFlow<WhisperModelDownloadState> = _downloadState.asStateFlow()

    fun getModelFile(context: Context): File = File(context.filesDir, MODEL_FILE_NAME)

    fun getPartialFile(context: Context): File = File(context.filesDir, "$MODEL_FILE_NAME.part")

    /**
     * Returns true iff [MODEL_FILE_NAME] exists inside [Context.getFilesDir] and meets the minimum
     * expected binary size. If an incomplete/corrupt `.bin` file is found, it is deleted cleanly.
     */
    fun isModelReady(context: Context): Boolean {
        val file = getModelFile(context)
        if (!file.exists()) return false
        if (!file.isFile || file.length() < MIN_VALID_MODEL_BYTES) {
            runCatching { file.delete() }
            return false
        }
        return true
    }

    internal fun updateProgressFromWorker(percent: Int, downloaded: Long, total: Long) {
        _downloadState.value = WhisperModelDownloadState(
            status = ModelDownloadStatus.DOWNLOADING,
            progressPercent = percent.coerceIn(0, 100),
            downloadedBytes = downloaded.coerceAtLeast(0L),
            totalBytes = total.coerceAtLeast(0L),
            errorMessage = null
        )
    }

    internal fun markReady(fileSize: Long) {
        _downloadState.value = WhisperModelDownloadState(
            status = ModelDownloadStatus.READY,
            progressPercent = 100,
            downloadedBytes = fileSize,
            totalBytes = fileSize,
            errorMessage = null
        )
    }

    internal fun markFailed(errorMessage: String) {
        _downloadState.value = WhisperModelDownloadState(
            status = ModelDownloadStatus.FAILED,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = 0L,
            errorMessage = errorMessage
        )
    }

    /**
     * Checks `context.filesDir/ggml-base-quran-q5_0.bin` at startup. If missing or incomplete,
     * enqueues a WorkManager background download with automatic network retry and progress updates.
     */
    fun ensureModelAvailable(context: Context, forceRetry: Boolean = false) {
        val appCtx = context.applicationContext
        if (isModelReady(appCtx)) {
            val part = getPartialFile(appCtx)
            if (part.exists()) runCatching { part.delete() }
            val readySize = getModelFile(appCtx).length()
            markReady(readySize)
            return
        }

        // Remove any leftover corrupt .part before starting fresh if retrying
        if (forceRetry) {
            runCatching { getPartialFile(appCtx).delete() }
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<QuranModelDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        val wm = WorkManager.getInstance(appCtx)
        val policy = if (forceRetry) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

        if (_downloadState.value.status == ModelDownloadStatus.NOT_DOWNLOADED || forceRetry) {
            _downloadState.value = WhisperModelDownloadState(
                status = ModelDownloadStatus.QUEUED,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = EXPECTED_MODEL_BYTES
            )
        }

        wm.enqueueUniqueWork(WORK_NAME, policy, request)
        observeWorkState(appCtx, wm)
    }

    fun cancelDownload(context: Context) {
        val appCtx = context.applicationContext
        WorkManager.getInstance(appCtx).cancelUniqueWork(WORK_NAME)
        runCatching { getPartialFile(appCtx).delete() }
        if (!isModelReady(appCtx)) {
            _downloadState.value = WhisperModelDownloadState(status = ModelDownloadStatus.NOT_DOWNLOADED)
        }
    }

    private fun observeWorkState(appCtx: Context, wm: WorkManager) {
        if (observerJob?.isActive == true) return
        observerJob = observerScope.launch {
            wm.getWorkInfosForUniqueWorkFlow(WORK_NAME).collect { infos ->
                if (isModelReady(appCtx)) {
                    markReady(getModelFile(appCtx).length())
                    return@collect
                }
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        val current = _downloadState.value
                        _downloadState.value = current.copy(
                            status = ModelDownloadStatus.QUEUED,
                            totalBytes = current.totalBytes.takeIf { it > 0L } ?: EXPECTED_MODEL_BYTES,
                            errorMessage = info.progress.getString(KEY_ERROR_MESSAGE)
                        )
                    }
                    WorkInfo.State.RUNNING -> {
                        val pct = info.progress.getInt(KEY_PROGRESS, _downloadState.value.progressPercent)
                        val downloaded = info.progress.getLong(KEY_DOWNLOADED_BYTES, _downloadState.value.downloadedBytes)
                        val total = info.progress.getLong(KEY_TOTAL_BYTES, EXPECTED_MODEL_BYTES)
                        _downloadState.value = WhisperModelDownloadState(
                            status = ModelDownloadStatus.DOWNLOADING,
                            progressPercent = pct.coerceIn(0, 100),
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            errorMessage = null
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        if (isModelReady(appCtx)) {
                            markReady(getModelFile(appCtx).length())
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        runCatching { getPartialFile(appCtx).delete() }
                        val err = info.outputData.getString(KEY_ERROR_MESSAGE)
                            ?: "تعذر تحميل نموذج التسميع بدون إنترنت. اضغط إعادة المحاولة."
                        markFailed(err)
                    }
                    WorkInfo.State.CANCELLED -> {
                        runCatching { getPartialFile(appCtx).delete() }
                        if (!isModelReady(appCtx)) {
                            _downloadState.value = WhisperModelDownloadState(
                                status = ModelDownloadStatus.NOT_DOWNLOADED
                            )
                        }
                    }
                }
            }
        }
    }
}

class QuranModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "QuranModelDownloadWork"
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val finalFile = QuranModelManager.getModelFile(applicationContext)
        val partFile = QuranModelManager.getPartialFile(applicationContext)

        if (QuranModelManager.isModelReady(applicationContext)) {
            if (partFile.exists()) runCatching { partFile.delete() }
            QuranModelManager.markReady(finalFile.length())
            return Result.success()
        }

        // Remove any prior broken partial file before attempting a clean download
        if (partFile.exists()) runCatching { partFile.delete() }

        val candidateUrls = resolveDownloadUrls()
        var lastError: Throwable? = null

        for (url in candidateUrls) {
            if (isStopped) {
                deleteIncompleteFiles(finalFile, partFile)
                return Result.failure()
            }
            try {
                downloadToFile(url, partFile)
                if (isStopped || partFile.length() < QuranModelManager.MIN_VALID_MODEL_BYTES) {
                    throw IOException("Downloaded model file incomplete (${partFile.length()} bytes)")
                }

                finalFile.parentFile?.mkdirs()
                if (finalFile.exists()) finalFile.delete()
                val moved = partFile.renameTo(finalFile)
                if (!moved) {
                    partFile.inputStream().use { input ->
                        FileOutputStream(finalFile).use { output ->
                            input.copyTo(output, bufferSize = 32 * 1024)
                        }
                    }
                    partFile.delete()
                }

                if (!QuranModelManager.isModelReady(applicationContext)) {
                    throw IOException("Final model verification failed after write")
                }

                val finalLength = finalFile.length()
                setProgress(
                    workDataOf(
                        QuranModelManager.KEY_PROGRESS to 100,
                        QuranModelManager.KEY_DOWNLOADED_BYTES to finalLength,
                        QuranModelManager.KEY_TOTAL_BYTES to finalLength
                    )
                )
                QuranModelManager.markReady(finalLength)
                return Result.success()
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt failed from $url: ${e.message}")
                lastError = e
                deleteIncompleteFiles(finalFile, partFile)
            }
        }

        deleteIncompleteFiles(finalFile, partFile)

        if (runAttemptCount < MAX_RETRY_ATTEMPTS && !isStopped) {
            val retryMsg = "انقطع الاتصال أثناء التحميل، ستتم إعادة المحاولة تلقائياً عند توفر الإنترنت..."
            setProgress(workDataOf(QuranModelManager.KEY_ERROR_MESSAGE to retryMsg))
            return Result.retry()
        }

        val errMsg = "فشل تحميل نموذج التسميع بدون إنترنت: ${lastError?.message ?: "خطأ في الاتصال"}"
        QuranModelManager.markFailed(errMsg)
        return Result.failure(workDataOf(QuranModelManager.KEY_ERROR_MESSAGE to errMsg))
    }

    private fun resolveDownloadUrls(): List<String> {
        val urls = LinkedHashSet<String>()
        val backendBase = SecureApiConfig.getHttpsBackendBaseUrl()
        if (backendBase != null) {
            try {
                val reqBuilder = Request.Builder()
                    .url("$backendBase/api/models/quran-whisper")
                    .header("Accept", "application/json")
                SecureApiConfig.getClientTokenIfAuthorized(applicationContext)?.let { token ->
                    reqBuilder.header("X-App-Token", token)
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string()
                    if (resp.isSuccessful && !body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val primary = json.optString("primaryUrl", "").trim()
                        if (primary.startsWith("https://")) urls.add(primary)
                        val mirrors = json.optJSONArray("mirrorUrls")
                        if (mirrors != null) {
                            for (i in 0 until mirrors.length()) {
                                val m = mirrors.optString(i, "").trim()
                                if (m.startsWith("https://")) urls.add(m)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Manifest lookup skipped: ${e.message}")
            }
        }

        urls.add(QuranModelManager.PRIMARY_HUGGINGFACE_URL)
        urls.add(QuranModelManager.MIRROR_HUGGINGFACE_URL)
        return urls.toList()
    }

    private suspend fun downloadToFile(url: String, partFile: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Imaniyat-Android/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading model")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
            if (contentType.contains("text/html")) {
                throw IOException("Unexpected HTML response instead of GGML binary")
            }

            val contentLength = body.contentLength()
            val expectedTotal = if (contentLength > 0L) contentLength else QuranModelManager.EXPECTED_MODEL_BYTES

            partFile.parentFile?.mkdirs()
            var downloadedBytes = 0L
            var lastReportedPct = -1

            body.byteStream().use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (isStopped) {
                            throw IOException("Worker stopped before download completed")
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val pct = ((downloadedBytes * 100L) / expectedTotal)
                            .toInt()
                            .coerceIn(0, 99)
                        if (pct != lastReportedPct) {
                            lastReportedPct = pct
                            QuranModelManager.updateProgressFromWorker(pct, downloadedBytes, expectedTotal)
                            setProgress(
                                workDataOf(
                                    QuranModelManager.KEY_PROGRESS to pct,
                                    QuranModelManager.KEY_DOWNLOADED_BYTES to downloadedBytes,
                                    QuranModelManager.KEY_TOTAL_BYTES to expectedTotal
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun deleteIncompleteFiles(finalFile: File, partFile: File) {
        if (partFile.exists()) {
            runCatching { partFile.delete() }
        }
        if (finalFile.exists() && finalFile.length() < QuranModelManager.MIN_VALID_MODEL_BYTES) {
            runCatching { finalFile.delete() }
        }
    }
}
