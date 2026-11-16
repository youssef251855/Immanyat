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

    @Test
    fun compassMath_handles_wraparound_and_circular_smoothing_cleanly() {
        // Crossing North from 358 deg to 2 deg should move +4 deg (to 362), NOT -356 deg
        val unwrappedUp = com.example.data.CompassMath.unwrapAngle(358f, 2f)
        assertEquals(362f, unwrappedUp, 0.001f)

        // Crossing North backwards from 362 deg (equiv to 2 deg) to 357 deg should move -5 deg (to 357)
        val unwrappedDown = com.example.data.CompassMath.unwrapAngle(362f, 357f)
        assertEquals(357f, unwrappedDown, 0.001f)

        // Circular low-pass filter across 350 -> 10 should move through 0 deg (not backwards through 180)
        val smoothed = com.example.data.CompassMath.circularLowPassFilter(350f, 10f, alpha = 0.5f)
        assertEquals(0f, smoothed, 0.001f)
    }

    @Test
    fun hadithCollection_has_50_authentic_hadiths_with_narrators_and_sources() {
        val all = com.example.data.HadithCollection.ALL_50_HADITHS
        assertEquals(50, all.size)

        val ids = all.map { it.id }.toSet()
        assertEquals(50, ids.size)

        all.forEachIndexed { idx, hadith ->
            assertEquals(idx + 1, hadith.number)
            assertTrue("Hadith ${hadith.number} text is blank", hadith.text.isNotBlank())
            assertTrue("Hadith ${hadith.number} narrator is blank", hadith.narrator.isNotBlank())
            assertTrue("Hadith ${hadith.number} source is blank", hadith.source.isNotBlank())
            assertTrue("Hadith ${hadith.number} explanation is blank", hadith.explanation.isNotBlank())
        }

        val libraryCategory = com.example.data.LibraryData.categories.first { it.id == "prophetic_hadiths" }
        assertEquals(50, libraryCategory.count)
        assertEquals(50, libraryCategory.articles.size)
    }

    @Test
    fun adhanManager_provides_muezzin_urls_and_applies_to_all_prayers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(AdhanManager.MUADHIN_VOICES.size >= 5)
        AdhanManager.MUADHIN_URLS.values.forEach { url ->
            assertTrue("Adhan stream URL must use HTTPS: $url", url.startsWith("https://"))
        }

        AdhanManager.setMuadhinForAllPrayers(context, "مؤذن مكة")
        assertEquals(5, AdhanManager.getPrayersUsingMuadhinCount(context, "مؤذن مكة"))
        assertEquals("مؤذن مكة", AdhanManager.getMuadhinForPrayer(context, "Fajr"))
        assertEquals("مؤذن مكة", AdhanManager.getMuadhinForPrayer(context, "Isha"))
    }

    @Test
    fun networkUtils_detects_unknown_host_exception_chain() {
        val root = java.net.UnknownHostException("Unable to resolve host: no network")
        val wrapped = RuntimeException("ExoPlayerImplInternal: Playback error", root)
        assertTrue(com.example.data.NetworkUtils.isNetworkOrHostError(wrapped))
    }
}
