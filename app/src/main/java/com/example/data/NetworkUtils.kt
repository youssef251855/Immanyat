package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Shared network availability and playback error classification utilities.
 * Prevents unguarded ExoPlayer/MediaPlayer network attempts when offline
 * and translates UnknownHostException / network failures into clear Arabic messages.
 */
object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                hasInternet && hasTransport
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected
            }
        } catch (_: SecurityException) {
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isNetworkOrHostError(error: Throwable?): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 12) {
            if (current is UnknownHostException ||
                current is ConnectException ||
                current is SocketTimeoutException ||
                current is NoRouteToHostException ||
                current is SocketException ||
                current is SSLException
            ) {
                return true
            }
            val className = current.javaClass.name
            val msg = current.message.orEmpty()
            if (className.contains("HttpDataSource", ignoreCase = true) ||
                msg.contains("UnknownHostException", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("no network", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("Chain validation failed", ignoreCase = true) ||
                msg.contains("SSLHandshakeException", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    fun isSslChainError(error: Throwable?): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 12) {
            val msg = current.message.orEmpty()
            if (current is SSLException ||
                msg.contains("Chain validation failed", ignoreCase = true) ||
                msg.contains("CertPathValidatorException", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    fun formatPlaybackErrorMessage(error: Throwable?, context: Context): String {
        if (!isNetworkAvailable(context)) {
            return "لا يوجد اتصال بالإنترنت حالياً. يرجى تفعيل الشبكة أو تحميل الملف للاستماع بدون نت."
        }
        if (isSslChainError(error)) {
            return "تعذر التحقق من شهادة الأمان للخادم (تأكد من ضبط تاريخ ووقت الجهاز وحاول مجدداً)."
        }
        if (isNetworkOrHostError(error)) {
            return "تعذر الوصول إلى خادم البث الصوتي (تحقق من استقرار الشبكة أو حاول مجدداً)."
        }
        return "تعذر تشغيل المقطع حالياً. يرجى المحاولة مرة أخرى."
    }
}
