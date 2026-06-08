package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minRequiredVersionCode: Int,
    val isForceUpdate: Boolean, // Backup flag for explicit forced updates
    val changelogAr: String,
    val changelogEn: String,
    val updateUrl: String
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    
    // Default raw URL hosted on GitHub for testing, but fully customisable
    private const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/efootballpes2025ff/configs/main/emaniat-update.json"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Gets the current app's versionCode.
     */
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Gets the current app's versionName.
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * Fetches update details from the remote JSON endpoint.
     * Catches and logs all errors, returning null if the fetch fails (supports offline mode gracefully).
     */
    fun fetchUpdateInfo(context: Context, customUrl: String? = null): UpdateInfo? {
        val urlToUse = customUrl ?: getUpdateUrl(context)
        Log.d(TAG, "Fetching update info from: $urlToUse")
        
        val request = Request.Builder()
            .url(urlToUse)
            .header("Cache-Control", "no-cache") // Ensure fresh update check
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed response code: ${response.code}")
                    return null
                }
                
                val bodyString = response.body?.string() ?: return null
                Log.d(TAG, "Update response details: $bodyString")
                
                val json = JSONObject(bodyString)
                val latestVersionCode = json.optInt("latestVersionCode", 1)
                val latestVersionName = json.optString("latestVersionName", "1.0")
                val minRequiredVersionCode = json.optInt("minRequiredVersionCode", 1)
                val isForceUpdate = json.optBoolean("isForceUpdate", false)
                val changelogAr = json.optString("changelogAr", "")
                val changelogEn = json.optString("changelogEn", "")
                val updateUrl = json.optString("updateUrl", "https://play.google.com/store/apps/details?id=com.immanyat.com")
                
                return UpdateInfo(
                    latestVersionCode = latestVersionCode,
                    latestVersionName = latestVersionName,
                    minRequiredVersionCode = minRequiredVersionCode,
                    isForceUpdate = isForceUpdate,
                    changelogAr = changelogAr,
                    changelogEn = changelogEn,
                    updateUrl = updateUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while fetching update file: ${e.message}", e)
            return null
        }
    }

    /**
     * Gets the configured update URL from persistent preferences.
     */
    fun getUpdateUrl(context: Context): String {
        val prefs = context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)
        return prefs.getString("update_config_url", DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
    }

    /**
     * Sets or overrides the update check URL (useful for customization or debugging).
     */
    fun setUpdateUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("update_config_url", url).apply()
    }
}
