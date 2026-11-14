package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.api.GeminiApiClient
import com.example.data.AdhanManager
import com.example.data.UpdateManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreLaunchAuditTest {

    @Test
    fun manifest_removes_high_risk_permissions_and_cleartext_traffic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()

        assertFalse(
            "USE_EXACT_ALARM must not be declared",
            permissions.contains("android.permission.USE_EXACT_ALARM")
        )
        assertFalse(
            "USE_FULL_SCREEN_INTENT must not be declared",
            permissions.contains("android.permission.USE_FULL_SCREEN_INTENT")
        )

        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "Cleartext traffic flag must be disabled",
            0,
            appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC
        )
    }

    @Test
    fun adhanManager_computes_ordered_prayer_times_and_valid_qibla() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AdhanManager.saveCoordinates(context, 30.0444, 31.2357, "القاهرة")

        val times = AdhanManager.calculatePrayerTimesForDate(context, Date())
        assertEquals(6, times.size)
        for (i in 0 until times.size - 1) {
            assertTrue(
                "Prayer ${times[i].name} should precede ${times[i + 1].name}",
                times[i].timeMillis < times[i + 1].timeMillis
            )
        }

        val bearing = AdhanManager.calculateQiblaDirection(context)
        assertFalse(bearing.isNaN())
        assertTrue("Qibla bearing must be in [0, 360)", bearing >= 0.0 && bearing < 360.0)
    }

    @Test
    fun quranAsset_contains_114_surahs_and_6236_non_blank_ayahs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val jsonStr = context.assets.open("quran.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val surahs = root.getJSONArray("surahs")
        assertEquals(114, surahs.length())

        var totalAyahs = 0
        for (i in 0 until surahs.length()) {
            val surah = surahs.getJSONObject(i)
            val verses = surah.getJSONArray("verses")
            for (j in 0 until verses.length()) {
                val text = verses.getString(j)
                assertTrue("Ayah ${j + 1} in surah ${i + 1} is blank", text.isNotBlank())
                totalAyahs++
            }
        }
        assertEquals(6236, totalAyahs)
    }

    @Test
    fun updateManager_rejects_insecure_http_urls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = UpdateManager.fetchUpdateInfo(context, "http://insecure.example.com/update.json")
        assertNull(info)
    }

    @Test
    fun backendClients_fail_closed_without_fake_simulation_when_unconfigured() = runBlocking {
        if (!GeminiApiClient.isConfigured) {
            val result = GeminiApiClient.evaluateMemorization(
                surahName = "الفاتحة",
                correctText = "بسم الله الرحمن الرحيم",
                recitationText = "بسم الله الرحمن الرحيم"
            )
            val json = JSONObject(result)
            assertFalse("Evaluation must not contain simulated mock marker", result.contains("محاكاة"))
            assertEquals(0.0, json.getDouble("score"), 0.001)
            assertTrue(json.getString("status").contains("غير مهيأة"))
        }
    }
}
