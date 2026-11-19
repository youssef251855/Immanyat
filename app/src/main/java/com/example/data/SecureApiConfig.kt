package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.BuildConfig

/**
 * Centralized guard around backend endpoints and client tokens bundled via BuildConfig.
 *
 * Defense-in-depth notes:
 * - True AI provider keys (such as GEMINI_API_KEY) live strictly on the Cloudflare Worker
 *   and are never compiled into the APK.
 * - Release builds enable R8 code shrinking/obfuscation and strip verbose/debug logs.
 * - Accessors enforce HTTPS-only endpoints and verify expected package identity before
 *   exposing optional app-client header tokens.
 */
object SecureApiConfig {
    private const val EXPECTED_PACKAGE_ID = "com.immanyat.com"

    fun getHttpsBackendBaseUrl(): String? {
        val raw = BuildConfig.BACKEND_BASE_URL.trim().removeSuffix("/")
        if (!raw.startsWith("https://", ignoreCase = true)) return null
        if (raw.length <= "https://".length) return null
        if (raw.contains("UNCONFIGURED_", ignoreCase = true)) return null
        return raw
    }

    fun isBackendConfigured(): Boolean = getHttpsBackendBaseUrl() != null

    fun getClientTokenIfAuthorized(context: Context? = null): String? {
        if (context != null && !verifyAppIntegrity(context)) {
            return null
        }
        val token = BuildConfig.BACKEND_CLIENT_TOKEN.trim()
        if (token.isEmpty() || token.startsWith("UNCONFIGURED_", ignoreCase = true)) {
            return null
        }
        return token
    }

    fun verifyAppIntegrity(context: Context): Boolean {
        val pkgName = context.packageName
        if (pkgName != EXPECTED_PACKAGE_ID && pkgName != "com.example") {
            return false
        }
        if (BuildConfig.DEBUG) {
            return true
        }
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signers = info.signingInfo?.apkContentsSigners
                !signers.isNullOrEmpty()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                !info.signatures.isNullOrEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }
}
