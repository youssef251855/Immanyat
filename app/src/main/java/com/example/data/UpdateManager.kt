package com.example.data

import android.content.Context

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minRequiredVersionCode: Int,
    val isForceUpdate: Boolean,
    val title: String,
    val message: String,
    val changelogAr: String,
    val changelogEn: String,
    val updateUrl: String
)

/**
 * Compatibility facade delegating to [AppUpdateChecker].
 */
object UpdateManager {
    fun getCurrentVersionCode(context: Context): Int = AppUpdateChecker.getCurrentVersionCode(context)

    fun getCurrentVersionName(context: Context): String = AppUpdateChecker.getCurrentVersionName(context)

    fun fetchUpdateInfo(context: Context, customUrl: String? = null): UpdateInfo? {
        val manifest = AppUpdateChecker.fetchManifestBlocking(context, customUrl) ?: return null
        return UpdateInfo(
            latestVersionCode = manifest.versionCode,
            latestVersionName = manifest.versionName,
            minRequiredVersionCode = manifest.minSupportedVersion,
            isForceUpdate = manifest.forceUpdate,
            title = manifest.title,
            message = manifest.message,
            changelogAr = manifest.releaseNotes.ifBlank { manifest.message },
            changelogEn = "",
            updateUrl = manifest.downloadUrl
        )
    }

    fun getUpdateUrl(context: Context): String {
        return AppUpdateChecker.resolveVersionJsonUrl(context)
            ?: AppUpdateChecker.DEFAULT_VERSION_JSON_URL
    }

    fun setUpdateUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return
        val prefs = context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("update_config_url", trimmed).apply()
    }
}
