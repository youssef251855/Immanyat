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
    fun adhanManager_bundles_4_offline_muadhin_assets_and_applies_to_all_prayers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(4, AdhanManager.MUADHIN_VOICES.size)

        AdhanManager.MUADHIN_VOICES.forEach { voice ->
            assertFalse("Voice path must be local asset, not URL: ${voice.assetPath}", voice.assetPath.startsWith("http"))
            context.assets.openFd(voice.assetPath).use { afd ->
                assertTrue("Bundled Adhan MP3 ${voice.assetPath} must be > 100KB", afd.length > 100 * 1024L)
            }
            assertTrue("isMuadhinAssetAvailable should return true for ${voice.name}", AdhanManager.isMuadhinAssetAvailable(context, voice.name))
        }

        AdhanManager.setMuadhinForAllPrayers(context, "مؤذن مكة")
        assertEquals(5, AdhanManager.getPrayersUsingMuadhinCount(context, "مؤذن مكة"))
        assertEquals("مؤذن مكة", AdhanManager.getMuadhinForPrayer(context, "Fajr"))
        assertEquals("مؤذن مكة", AdhanManager.getMuadhinForPrayer(context, "Isha"))

        // Legacy names normalize cleanly onto bundled voices
        assertEquals("مؤذن مكة", AdhanManager.normalizeMuadhinName("أذان المسجد الأقصى"))
    }

    @Test
    fun networkUtils_detects_unknown_host_exception_chain() {
        val root = java.net.UnknownHostException("Unable to resolve host: no network")
        val wrapped = RuntimeException("ExoPlayerImplInternal: Playback error", root)
        assertTrue(com.example.data.NetworkUtils.isNetworkOrHostError(wrapped))
    }

    @Test
    fun iqamah_postAdhanDua_and_eveningAzkar_assets_and_schedules_are_accurate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AdhanManager.saveCoordinates(context, 30.0444, 31.2357, "القاهرة")

        // Verify all 3 additional bundled audio files are packaged & non-empty
        listOf(
            AdhanManager.ASSET_IQAMAH_AUDIO,
            AdhanManager.ASSET_POST_ADHAN_DUA_AUDIO,
            AdhanManager.ASSET_EVENING_AZKAR_AUDIO
        ).forEach { assetPath ->
            context.assets.openFd(assetPath).use { afd ->
                assertTrue("Bundled audio $assetPath must be > 100KB", afd.length > 100 * 1024L)
            }
            assertTrue("isAssetAvailable should be true for $assetPath", AdhanManager.isAssetAvailable(context, assetPath))
        }

        // Verify exact requested Iqamah delays
        assertEquals(15, AdhanManager.getIqamahDelayMinutes(context, "Fajr"))
        assertEquals(15, AdhanManager.getIqamahDelayMinutes(context, "Dhuhr"))
        assertEquals(15, AdhanManager.getIqamahDelayMinutes(context, "Asr"))
        assertEquals(12, AdhanManager.getIqamahDelayMinutes(context, "Maghrib"))
        assertEquals(15, AdhanManager.getIqamahDelayMinutes(context, "Isha"))

        // Verify next Evening Azkar timestamp is strictly 30 minutes before Maghrib
        val nextAzkar = AdhanManager.getNextEveningAzkarTime(context, bufferSeconds = 0L)
        assertTrue("Next Evening Azkar time must be scheduled", nextAzkar != null)

        val todayMaghrib = AdhanManager.calculatePrayerTimesForDate(context, Date()).first { it.name == "Maghrib" }
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrowMaghrib = AdhanManager.calculatePrayerTimesForDate(context, cal.time).first { it.name == "Maghrib" }
        val expectedToday = todayMaghrib.timeMillis - 30 * 60_000L
        val expectedTomorrow = tomorrowMaghrib.timeMillis - 30 * 60_000L
        val actual = nextAzkar!!.first
        assertTrue(
            "Evening Azkar ($actual) must be 30 mins before today's ($expectedToday) or tomorrow's ($expectedTomorrow) Maghrib",
            actual == expectedToday || actual == expectedTomorrow
        )
    }

    @Test
    fun loadQuranFromAssets_loads_all_114_surahs_cleanly_without_bom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quranMap = com.example.ui.screens.loadQuranFromAssets(context)
        assertEquals(114, quranMap.size)

        val fatiha = com.example.ui.screens.getSurahFullText(1, quranMap, emptyMap())
        assertFalse("Fatiha reference text must not contain BOM", fatiha.contains("\uFEFF"))
        assertTrue(fatiha.contains("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"))

        val ikhlas = com.example.ui.screens.getSurahFullText(112, quranMap, emptyMap())
        assertTrue(ikhlas.contains("قُلْ هُوَ اللَّهُ أَحَدٌ"))
    }

    @Test
    fun wavAudioRecorder_builds_exact_16khz_mono_16bit_pcm_header() {
        val pcmBytes = 32000L // 1 second at 16kHz * 1ch * 2 bytes
        val header = com.example.audio.WavAudioRecorder.buildWavHeader(pcmBytes)
        assertEquals(44, header.size)

        val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + pcmBytes.toInt(), buf.getInt(4))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals(16, buf.getInt(16)) // PCM chunk size
        assertEquals(1.toShort(), buf.getShort(20)) // PCM format = 1
        assertEquals(1.toShort(), buf.getShort(22)) // Mono = 1
        assertEquals(16000, buf.getInt(24)) // 16kHz
        assertEquals(32000, buf.getInt(28)) // byteRate = 16000 * 1 * 2
        assertEquals(2.toShort(), buf.getShort(32)) // blockAlign = 2
        assertEquals(16.toShort(), buf.getShort(34)) // 16-bit
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))
        assertEquals(pcmBytes.toInt(), buf.getInt(40))
    }

    @Test
    fun quranRecitationEvaluator_normalizes_arabic_scores_one_decimal_and_flags_missed_words() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quranMap = com.example.ui.screens.loadQuranFromAssets(context)
        val fatiha = com.example.ui.screens.getSurahFullText(1, quranMap, emptyMap())

        // 1. Normalization unifies Alefs and strips diacritics, dagger alef, & tatweel
        val normalized = com.example.data.QuranRecitationEvaluator.normalizeArabic("بِسْمِ اللَّهِ الرَّحْمَـٰنِ أَحَدٌ إِيَّاكَ آيَاتٍ")
        assertEquals("بسم الله الرحمن احد اياك ايات", normalized)

        // 2. Clean recitation without diacritics achieves 10.0/10
        val perfectTranscript =
            "بسم الله الرحمن الرحيم الحمد لله رب العالمين الرحمن الرحيم مالك يوم الدين " +
                "اياك نعبد واياك نستعين اهدنا الصراط المستقيم صراط الذين انعمت عليهم غير المغضوب عليهم ولا الضالين"
        val perfectEval = com.example.data.QuranRecitationEvaluator.evaluate("الفاتحة", fatiha, perfectTranscript)
        assertFalse(perfectEval.isMismatch)
        assertEquals(10.0, perfectEval.score, 0.001)
        assertTrue("Perfect recitation should have no mistakes", perfectEval.mistakeWords.isEmpty())

        // 3. Omitting "العالمين" & "المستقيم" deducts score (rounded to 1 decimal) and highlights MISSED words
        val missingWordsTranscript =
            "بسم الله الرحمن الرحيم الحمد لله رب الرحمن الرحيم مالك يوم الدين " +
                "اياك نعبد واياك نستعين اهدنا الصراط صراط الذين انعمت عليهم غير المغضوب عليهم ولا الضالين"
        val partialEval = com.example.data.QuranRecitationEvaluator.evaluate("الفاتحة", fatiha, missingWordsTranscript)
        assertFalse(partialEval.isMismatch)
        assertTrue("Partial score (${partialEval.score}) must be < 10.0", partialEval.score < 10.0)
        // Verify rounded to 1 decimal place
        assertEquals(partialEval.score, kotlin.math.round(partialEval.score * 10.0) / 10.0, 0.0001)
        val missedDiffs = partialEval.wordDiffs
            .filter { it.status == com.example.data.WordDiffStatus.MISSED }
            .map { com.example.data.QuranRecitationEvaluator.normalizeArabic(it.word) }
        assertTrue("Missed words must include العالمين", missedDiffs.contains("العالمين"))
        assertTrue("Missed words must include المستقيم", missedDiffs.contains("المستقيم"))

        // 4. Non-Quranic speech is rejected with 0.0
        val chatterEval = com.example.data.QuranRecitationEvaluator.evaluate(
            "الفاتحة",
            fatiha,
            "السلام عليكم اهلا وسهلا تجربة صوتية"
        )
        assertTrue(chatterEval.isMismatch)
        assertEquals(0.0, chatterEval.score, 0.001)
    }

    @Test
    fun quranModelManager_rejects_and_deletes_incomplete_model_file() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelFile = com.example.whisper.QuranModelManager.getModelFile(context)
        try {
            // Write an incomplete 1KB corrupt file
            modelFile.writeBytes(ByteArray(1024))
            assertTrue(modelFile.exists())

            // isModelReady must return false and clean up the incomplete file
            assertFalse(com.example.whisper.QuranModelManager.isModelReady(context))
            assertFalse("Incomplete .bin file must be deleted", modelFile.exists())
        } finally {
            if (modelFile.exists()) modelFile.delete()
        }
    }

    @Test
    fun libraryData_all_categories_have_50_unique_valid_articles_and_search_works() {
        val categories = com.example.data.LibraryData.categories
        assertEquals(19, categories.size)

        val categoryIds = categories.map { it.id }.toSet()
        assertEquals(19, categoryIds.size)

        val allArticleIds = mutableSetOf<String>()
        categories.forEach { category ->
            assertEquals("Category ${category.id} count must equal 50", 50, category.count)
            assertEquals("Category ${category.id} articles size must equal 50", 50, category.articles.size)

            val categoryTitles = mutableSetOf<String>()
            category.articles.forEach { article ->
                assertTrue("Article ${article.id} id is blank", article.id.isNotBlank())
                assertTrue("Duplicate article id ${article.id}", allArticleIds.add(article.id))
                assertTrue("Article ${article.id} title is blank", article.title.isNotBlank())
                assertTrue(
                    "Duplicate title '${article.title}' in category ${category.id}",
                    categoryTitles.add(article.title.trim())
                )
                assertTrue("Article ${article.id} content is too short", article.content.trim().length >= 60)
                assertTrue("Article ${article.id} source is blank", article.source.isNotBlank())
            }
        }
        assertEquals(19 * 50, allArticleIds.size)

        // Blank query returns all 19 categories with 50 each
        assertEquals(19, com.example.data.LibraryData.searchLibrary("   ").size)

        // Specific search narrows results accurately and matches count to returned articles
        val badrResults = com.example.data.LibraryData.searchLibrary("غزوة بدر")
        assertTrue(badrResults.isNotEmpty())
        badrResults.forEach { result ->
            assertEquals(result.articles.size, result.count)
        }
    }

    @Test
    fun videosAsset_uses_valid_https_urls_and_networkUtils_detects_ssl_chain_failure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videos = com.example.ui.screens.loadVideosFromAssets(context)
        assertTrue("videos.json must contain entries", videos.isNotEmpty())
        videos.forEach { video ->
            assertTrue("Video URL must use https: ${video.videoUrl}", video.videoUrl.startsWith("https://"))
            assertFalse("Video URL must not use expired i.top4top.io node", video.videoUrl.contains("://i.top4top.io"))
        }

        val sslErr = javax.net.ssl.SSLHandshakeException("Chain validation failed")
        val wrapped = RuntimeException("HttpDataSourceException", sslErr)
        assertTrue(com.example.data.NetworkUtils.isSslChainError(wrapped))
        assertTrue(com.example.data.NetworkUtils.isNetworkOrHostError(wrapped))
    }

    @Test
    fun summerFajr_isExempted_from_quietSleep_while_lateNightNonFajr_isMuted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AdhanManager.setSilentDuringSleep(context, true)

        // 03:45 AM (typical Summer Fajr before 5 AM) -> MUST NOT be silenced
        val calFajr = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 3)
            set(java.util.Calendar.MINUTE, 45)
        }
        assertFalse(
            "Summer Fajr Adhan before 5 AM must never be silenced",
            AdhanManager.isQuietSleepActive(context, "ADHAN", "Fajr", calFajr.timeInMillis)
        )
        assertFalse(
            "Summer Fajr Iqamah before 5 AM must never be silenced",
            AdhanManager.isQuietSleepActive(context, "IQAMAH", "Fajr", calFajr.timeInMillis)
        )

        // 11:30 PM non-Fajr (e.g., Isha) -> SHOULD be silenced when sleep mode is enabled
        val calLate = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 30)
        }
        assertTrue(
            "Late-night non-Fajr alert at 11:30 PM should be silenced when sleep mode is on",
            AdhanManager.isQuietSleepActive(context, "ADHAN", "Isha", calLate.timeInMillis)
        )

        AdhanManager.setSilentDuringSleep(context, false)
    }

    @Test
    fun manifest_declares_batteryExemptionPermission_and_packageReplacedReceivers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission must be declared",
            permissions.contains(android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        )

        val pkgReplacedIntent = android.content.Intent(android.content.Intent.ACTION_MY_PACKAGE_REPLACED).apply {
            setPackage(context.packageName)
        }
        val receivers = pm.queryBroadcastReceivers(pkgReplacedIntent, 0)
            .mapNotNull { it.activityInfo?.name }
            .toSet()

        assertTrue(
            "AdhanAlarmReceiver must listen for MY_PACKAGE_REPLACED",
            receivers.contains("com.example.service.AdhanAlarmReceiver")
        )
        assertTrue(
            "KhatmaAlarmReceiver must listen for MY_PACKAGE_REPLACED",
            receivers.contains("com.example.service.KhatmaAlarmReceiver")
        )
        assertEquals(
            "emaniat_adhan_daily_watchdog",
            com.example.service.AdhanAlarmWatchdogWorker.UNIQUE_WORK_NAME
        )
    }

    @Test
    fun secureApiConfig_verifiesPackageIntegrity_and_blocksUnconfiguredBackend() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(com.example.data.SecureApiConfig.verifyAppIntegrity(context))
        if (!com.example.data.SecureApiConfig.isBackendConfigured()) {
            assertNull(com.example.data.SecureApiConfig.getHttpsBackendBaseUrl())
        }
    }

    @Test
    fun buildConfig_preserves_packageName_and_bumps_version_1_5() {
        assertEquals("com.immanyat.com", BuildConfig.APPLICATION_ID)
        assertEquals("1.5", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetFiles = context.assets.list("")?.toSet().orEmpty()
        assertFalse(
            "version.json must NOT be bundled inside APK assets",
            assetFiles.contains("version.json")
        )
    }

    @Test
    fun appUpdateChecker_parses_external_versionJson_handles_forced_optional_and_malformed_safely() {
        val sampleJson = """
            {
              "versionCode": 3,
              "versionName": "1.6",
              "forceUpdate": false,
              "title": "تحديث جديد لتطبيق إيمانيات",
              "message": "يتوفر إصدار جديد بمزايا محسنة",
              "downloadUrl": "https://apkpure.com/p/com.immanyat.com",
              "minSupportedVersion": 2,
              "releaseNotes": "• تحسين دقة المواقيت\n• تسريع التلاوة"
            }
        """.trimIndent()

        val manifest = com.example.data.AppUpdateChecker.parseVersionJson(sampleJson)
        assertTrue(manifest != null)
        manifest!!
        assertEquals(3, manifest.versionCode)
        assertEquals("1.6", manifest.versionName)
        assertFalse(manifest.forceUpdate)
        assertEquals("تحديث جديد لتطبيق إيمانيات", manifest.title)
        assertEquals("يتوفر إصدار جديد بمزايا محسنة", manifest.message)
        assertEquals(com.example.data.AppUpdateChecker.APKPURE_APP_URL, manifest.downloadUrl)
        assertEquals(2, manifest.minSupportedVersion)
        assertTrue(manifest.releaseNotes.contains("تحسين دقة المواقيت"))

        // Current v1.5 (versionCode = 2) meets minSupportedVersion = 2 -> optional update
        assertFalse(manifest.isForcedFor(currentVersionCode = 2))
        // Older versionCode = 1 < minSupportedVersion = 2 -> forced update
        assertTrue(manifest.isForcedFor(currentVersionCode = 1))

        // Non-HTTPS downloadUrl falls back to official APKPure page
        val insecureUrlJson = """
            {
              "versionCode": 4,
              "versionName": "1.7",
              "forceUpdate": true,
              "title": "تحديث",
              "message": "رسالة",
              "downloadUrl": "http://untrusted.example.com/app.apk",
              "minSupportedVersion": 3,
              "releaseNotes": "ملاحظات"
            }
        """.trimIndent()
        val safeManifest = com.example.data.AppUpdateChecker.parseVersionJson(insecureUrlJson)!!
        assertEquals(com.example.data.AppUpdateChecker.APKPURE_APP_URL, safeManifest.downloadUrl)
        assertTrue(safeManifest.isForcedFor(currentVersionCode = 3))

        // Malformed or invalid JSON returns null safely without crashing
        assertNull(com.example.data.AppUpdateChecker.parseVersionJson("{broken-json"))
        assertNull(com.example.data.AppUpdateChecker.parseVersionJson("""{"versionCode": 0}"""))
    }
}
