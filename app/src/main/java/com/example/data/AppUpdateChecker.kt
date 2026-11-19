package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val forceUpdate: Boolean,
    val title: String,
    val message: String,
    val downloadUrl: String,
    val minSupportedVersion: Int,
    val releaseNotes: String
) {
    fun isForcedFor(currentVersionCode: Int): Boolean {
        return forceUpdate || currentVersionCode < minSupportedVersion
    }
}

sealed class AppUpdateCheckResult {
    data class UpdateAvailable(
        val manifest: AppUpdateManifest,
        val isForced: Boolean,
        val currentVersionCode: Int
    ) : AppUpdateCheckResult()

    object UpToDate : AppUpdateCheckResult()
    object UnavailableOrSkipped : AppUpdateCheckResult()
}

/**
 * Pure Kotlin Coroutines (Dispatchers.IO) update checker compatible with Android 11+ (API 30+)
 * without requiring Google Play Services.
 *
 * Reads external version.json over HTTPS, prevents redundant network requests via in-memory
 * session caching and cooldown locking, supports optional and forced updates, and safely
 * directs the user to the official APKPure app page.
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    const val APKPURE_APP_URL = "https://apkpure.com/p/com.immanyat.com"
    const val DEFAULT_VERSION_JSON_URL =
        "https://raw.githubusercontent.com/efootballpes2025ff/configs/main/version.json"

    private const val COOLDOWN_MS = 30L * 60L * 1000L // 30 minutes between automatic launch checks

    private val checkMutex = Mutex()
    @Volatile
    private var hasCheckedThisSession = false
    @Volatile
    private var optionalDismissedThisSession = false
    @Volatile
    private var lastCheckedAtMs = 0L
    @Volatile
    private var cachedManifest: AppUpdateManifest? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (_: Throwable) {
            1
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.5"
        } catch (_: Throwable) {
            "1.5"
        }
    }

    fun resolveVersionJsonUrl(context: Context, customUrl: String? = null): String? {
        if (!customUrl.isNullOrBlank()) {
            val trimmed = customUrl.trim()
            return if (trimmed.startsWith("https://", ignoreCase = true)) trimmed else null
        }

        val prefs = context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)
        val savedOverride = prefs.getString("update_config_url", null)?.trim()
        if (!savedOverride.isNullOrBlank() && savedOverride.startsWith("https://", ignoreCase = true)) {
            return savedOverride
        }

        val workerBase = SecureApiConfig.getHttpsBackendBaseUrl()
        if (!workerBase.isNullOrBlank()) {
            return "$workerBase/version.json"
        }

        return DEFAULT_VERSION_JSON_URL
    }

    fun markOptionalUpdateDismissedForSession() {
        optionalDismissedThisSession = true
    }

    fun resetSessionCacheForTesting() {
        hasCheckedThisSession = false
        optionalDismissedThisSession = false
        lastCheckedAtMs = 0L
        cachedManifest = null
    }

    suspend fun checkForUpdateOnLaunch(context: Context): AppUpdateCheckResult {
        return checkForUpdate(context = context, forceRefresh = false)
    }

    suspend fun checkForUpdate(
        context: Context,
        forceRefresh: Boolean = false,
        customUrl: String? = null
    ): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            val currentCode = getCurrentVersionCode(context)
            val now = System.currentTimeMillis()

            if (!forceRefresh) {
                if (hasCheckedThisSession && (now - lastCheckedAtMs) < COOLDOWN_MS) {
                    val cached = cachedManifest ?: return@withLock AppUpdateCheckResult.UnavailableOrSkipped
                    if (cached.versionCode > currentCode) {
                        val forced = cached.isForcedFor(currentCode)
                        if (!forced && optionalDismissedThisSession) {
                            return@withLock AppUpdateCheckResult.UnavailableOrSkipped
                        }
                        return@withLock AppUpdateCheckResult.UpdateAvailable(cached, forced, currentCode)
                    }
                    return@withLock AppUpdateCheckResult.UpToDate
                }
            }

            if (!NetworkUtils.isNetworkAvailable(context)) {
                return@withLock AppUpdateCheckResult.UnavailableOrSkipped
            }

            val manifest = fetchManifestBlocking(context, customUrl)
            lastCheckedAtMs = System.currentTimeMillis()
            hasCheckedThisSession = true

            if (manifest == null) {
                return@withLock AppUpdateCheckResult.UnavailableOrSkipped
            }

            cachedManifest = manifest
            if (manifest.versionCode > currentCode) {
                val forced = manifest.isForcedFor(currentCode)
                if (!forceRefresh && !forced && optionalDismissedThisSession) {
                    return@withLock AppUpdateCheckResult.UnavailableOrSkipped
                }
                AppUpdateCheckResult.UpdateAvailable(
                    manifest = manifest,
                    isForced = forced,
                    currentVersionCode = currentCode
                )
            } else {
                AppUpdateCheckResult.UpToDate
            }
        }
    }

    internal fun fetchManifestBlocking(context: Context, customUrl: String? = null): AppUpdateManifest? {
        val url = resolveVersionJsonUrl(context, customUrl) ?: run {
            Log.w(TAG, "Rejected non-HTTPS version manifest URL")
            return null
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.i(TAG, "Remote version.json unavailable (HTTP ${response.code})")
                    return null
                }
                val body = response.body?.string() ?: return null
                parseVersionJson(body)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed fetching version.json safely: ${t.message}")
            null
        }
    }

    fun parseVersionJson(rawJson: String): AppUpdateManifest? {
        return try {
            val json = JSONObject(rawJson)
            val versionCode = json.optInt("versionCode", json.optInt("latestVersionCode", 0))
            if (versionCode <= 0) return null

            val versionName = json.optString(
                "versionName",
                json.optString("latestVersionName", "")
            ).trim().ifEmpty { versionCode.toString() }

            val forceUpdate = json.optBoolean("forceUpdate", json.optBoolean("isForceUpdate", false))
            val minSupportedVersion = json.optInt(
                "minSupportedVersion",
                json.optInt("minRequiredVersionCode", 1)
            ).coerceAtLeast(1)

            val title = json.optString("title", "").trim().ifEmpty {
                if (forceUpdate) "تحديث إجباري لتطبيق إيمانيات ⚠️"
                else "يتوفر تحديث جديد لتطبيق إيمانيات ✨"
            }

            val releaseNotes = json.optString(
                "releaseNotes",
                json.optString("changelogAr", "")
            ).trim()

            val message = json.optString("message", "").trim().ifEmpty {
                releaseNotes.ifEmpty { "يتوفر إصدار أحدث من تطبيق إيمانيات بمزايا وتحسينات مهمة." }
            }

            val rawUrl = json.optString(
                "downloadUrl",
                json.optString("updateUrl", APKPURE_APP_URL)
            ).trim()

            val safeDownloadUrl = if (rawUrl.startsWith("https://", ignoreCase = true) && rawUrl.length > "https://".length) {
                rawUrl
            } else {
                APKPURE_APP_URL
            }

            AppUpdateManifest(
                versionCode = versionCode,
                versionName = versionName,
                forceUpdate = forceUpdate,
                title = title,
                message = message,
                downloadUrl = safeDownloadUrl,
                minSupportedVersion = minSupportedVersion,
                releaseNotes = releaseNotes
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Invalid version.json format ignored safely: ${t.message}")
            null
        }
    }

    fun openApkPurePage(context: Context, downloadUrl: String = APKPURE_APP_URL): Boolean {
        val targetUrl = if (downloadUrl.startsWith("https://", ignoreCase = true)) {
            downloadUrl
        } else {
            APKPURE_APP_URL
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(APKPURE_APP_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            }.getOrDefault(false)
        }
    }
}
