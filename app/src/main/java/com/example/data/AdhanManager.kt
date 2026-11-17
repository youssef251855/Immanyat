package com.example.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.service.AdhanAlarmReceiver
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.*

data class PrayerTimeInfo(
    val name: String,
    val arabicName: String,
    val timeMillis: Long,
    val formattedTime: String,
    val isEnabled: Boolean = true
)

object AdhanManager {
    private const val PREFS_NAME = "emaniat_adhan_prefs"
    private const val DEFAULT_LATITUDE = 21.4225
    private const val DEFAULT_LONGITUDE = 39.8262

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Location settings ---
    private fun getDefaultCityByTimeZone(): Triple<Double, Double, String> {
        return when (TimeZone.getDefault().id) {
            "Africa/Cairo" -> Triple(30.0444, 31.2357, "القاهرة")
            "Asia/Riyadh" -> Triple(24.7136, 46.6753, "الرياض")
            "Asia/Dubai" -> Triple(25.2048, 55.2708, "دبي")
            "Asia/Amman" -> Triple(31.9539, 35.9106, "عمّان")
            "Asia/Jerusalem", "Asia/Gaza", "Asia/Hebron" -> Triple(31.7683, 35.2137, "القدس الشريف")
            "Asia/Beirut" -> Triple(33.8938, 35.5018, "بيروت")
            "Asia/Baghdad" -> Triple(33.3128, 44.3615, "بغداد")
            "Asia/Damascus" -> Triple(33.5138, 36.2765, "دمشق")
            "Asia/Kuwait" -> Triple(29.3759, 47.9774, "الكويت")
            "Asia/Bahrain" -> Triple(26.2285, 50.5860, "المنامة")
            "Asia/Qatar" -> Triple(25.2854, 51.5310, "الدوحة")
            "Asia/Muscat" -> Triple(23.5859, 58.4059, "مسقط")
            "Asia/Aden" -> Triple(15.3694, 44.1910, "صنعاء")
            "Africa/Tripoli" -> Triple(32.8872, 13.1913, "طرابلس")
            "Africa/Tunis" -> Triple(36.8065, 10.1815, "تونس")
            "Africa/Algiers" -> Triple(36.7525, 3.0360, "الجزائر")
            "Africa/Casablanca" -> Triple(34.0209, -6.8416, "الرباط")
            "Africa/Khartoum" -> Triple(15.5007, 32.5599, "الخرطوم")
            "Europe/Istanbul", "Asia/Istanbul" -> Triple(41.0082, 28.9784, "إسطنبول")
            else -> Triple(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, "مكة المكرمة")
        }
    }

    fun getSavedCoordinates(context: Context): Coordinates {
        val prefs = getPrefs(context)
        if (!prefs.contains("latitude") || !prefs.contains("longitude")) {
            val (lat, lon, _) = getDefaultCityByTimeZone()
            return Coordinates(lat, lon)
        }
        val lat = prefs.getFloat("latitude", DEFAULT_LATITUDE.toFloat()).toDouble()
        val lon = prefs.getFloat("longitude", DEFAULT_LONGITUDE.toFloat()).toDouble()
        return Coordinates(lat, lon)
    }

    fun saveCoordinates(context: Context, latitude: Double, longitude: Double, locationName: String) {
        getPrefs(context).edit()
            .putFloat("latitude", latitude.toFloat())
            .putFloat("longitude", longitude.toFloat())
            .putString("location_name", locationName)
            .apply()
        try {
            com.example.widget.WidgetUpdateHelper.updateAllWidgets(context)
        } catch (_: Throwable) {}
    }

    fun getSavedLocationName(context: Context): String {
        val prefs = getPrefs(context)
        if (!prefs.contains("location_name")) {
            return getDefaultCityByTimeZone().third
        }
        return prefs.getString("location_name", "مكة المكرمة") ?: "مكة المكرمة"
    }

    fun wasPrayerTriggeredToday(context: Context, prayerKey: String): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return getPrefs(context).getString("last_triggered_$prayerKey", "") == today
    }

    fun markPrayerTriggeredToday(context: Context, prayerKey: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        getPrefs(context).edit().putString("last_triggered_$prayerKey", today).apply()
    }

    fun wasIqamahTriggeredToday(context: Context, prayerKey: String): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return getPrefs(context).getString("last_iqamah_triggered_$prayerKey", "") == today
    }

    fun markIqamahTriggeredToday(context: Context, prayerKey: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        getPrefs(context).edit().putString("last_iqamah_triggered_$prayerKey", today).apply()
    }

    fun wasEveningAzkarTriggeredToday(context: Context): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return getPrefs(context).getString("last_evening_azkar_triggered", "") == today
    }

    fun markEveningAzkarTriggeredToday(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        getPrefs(context).edit().putString("last_evening_azkar_triggered", today).apply()
    }

    // --- Calculation and Madhab configs ---
    fun getCalculationMethod(context: Context): String {
        return getPrefs(context).getString("calc_method", "UMM_AL_QURA") ?: "UMM_AL_QURA"
    }

    fun setCalculationMethod(context: Context, method: String) {
        getPrefs(context).edit().putString("calc_method", method).apply()
    }

    fun getMadhab(context: Context): String {
        return getPrefs(context).getString("madhab", "SHAFI") ?: "SHAFI"
    }

    fun setMadhab(context: Context, madhab: String) {
        getPrefs(context).edit().putString("madhab", madhab).apply()
    }

    // --- Prayer toggles ---
    fun isPrayerEnabled(context: Context, prayerKey: String): Boolean {
        // prayerKey: Fajr, Dhuhr, Asr, Maghrib, Isha
        return getPrefs(context).getBoolean("pray_enabled_$prayerKey", true)
    }

    fun setPrayerEnabled(context: Context, prayerKey: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("pray_enabled_$prayerKey", enabled).apply()
    }

    // --- Muadhin parameters (100% offline bundled MP3s in assets/Sounds/) ---
    fun normalizeMuadhinName(rawName: String?): String {
        return when (rawName?.trim()) {
            "مشاري العفاسي" -> "مشاري العفاسي"
            "عبد الباسط" -> "عبد الباسط"
            "مؤذن مكة" -> "مؤذن مكة"
            "مؤذن المدينة" -> "مؤذن المدينة"
            // Map legacy/previous names cleanly onto bundled offline voices
            "أذان المسجد الأقصى" -> "مؤذن مكة"
            "النقشبندي" -> "عبد الباسط"
            else -> "مشاري العفاسي"
        }
    }

    fun getMuadhinForPrayer(context: Context, prayerKey: String): String {
        val stored = getPrefs(context).getString("pray_muadhin_$prayerKey", "مشاري العفاسي")
        return normalizeMuadhinName(stored)
    }

    fun setMuadhinForPrayer(context: Context, prayerKey: String, muadhin: String) {
        getPrefs(context).edit().putString("pray_muadhin_$prayerKey", normalizeMuadhinName(muadhin)).apply()
    }

    fun setMuadhinForAllPrayers(context: Context, muadhin: String) {
        val normalized = normalizeMuadhinName(muadhin)
        val editor = getPrefs(context).edit()
        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach { key ->
            editor.putString("pray_muadhin_$key", normalized)
        }
        editor.apply()
    }

    fun getPrayersUsingMuadhinCount(context: Context, muadhin: String): Int {
        val normalized = normalizeMuadhinName(muadhin)
        return listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
            .count { getMuadhinForPrayer(context, it) == normalized }
    }

    data class MuadhinVoice(
        val name: String,
        val subtitle: String,
        val assetPath: String
    )

    val MUADHIN_VOICES = listOf(
        MuadhinVoice(
            name = "مشاري العفاسي",
            subtitle = "الكويت • نبرة خاشعة صافية • مدمج بالكامل",
            assetPath = "Sounds/adhan_mishary.mp3"
        ),
        MuadhinVoice(
            name = "عبد الباسط",
            subtitle = "مصر • أداء مجود عريق • مدمج بالكامل",
            assetPath = "Sounds/adhan_abdulbasit.mp3"
        ),
        MuadhinVoice(
            name = "مؤذن مكة",
            subtitle = "الحرم المكي الشريف • مدمج بالكامل",
            assetPath = "Sounds/adhan_makkah.mp3"
        ),
        MuadhinVoice(
            name = "مؤذن المدينة",
            subtitle = "المسجد النبوي المبارك • مدمج بالكامل",
            assetPath = "Sounds/adhan_madinah.mp3"
        )
    )

    val MUADHIN_ASSETS = MUADHIN_VOICES.associate { it.name to it.assetPath }

    fun getMuadhinSubtitle(muadhinName: String): String {
        val normalized = normalizeMuadhinName(muadhinName)
        return MUADHIN_VOICES.firstOrNull { it.name == normalized }?.subtitle ?: "أذان إسلامي مدمج"
    }

    fun getMuadhinAssetPath(muadhinName: String): String {
        val normalized = normalizeMuadhinName(muadhinName)
        return MUADHIN_ASSETS[normalized] ?: "Sounds/adhan_mishary.mp3"
    }

    const val ASSET_IQAMAH_AUDIO = "Sounds/iqama salat.mp3"
    const val ASSET_POST_ADHAN_DUA_AUDIO = "Sounds/post_adhan_dua.mp3"
    const val ASSET_EVENING_AZKAR_AUDIO = "Sounds/9.mp3"
    const val EVENING_AZKAR_BEFORE_MAGHRIB_MINUTES = 30

    val DEFAULT_IQAMAH_DELAY_MINUTES = mapOf(
        "Fajr" to 15,
        "Dhuhr" to 15,
        "Asr" to 15,
        "Maghrib" to 12,
        "Isha" to 15
    )

    fun getIqamahDelayMinutes(context: Context, prayerKey: String): Int {
        val defaultMins = DEFAULT_IQAMAH_DELAY_MINUTES[prayerKey] ?: 15
        return getPrefs(context).getInt("iqamah_delay_$prayerKey", defaultMins).coerceIn(1, 60)
    }

    fun setIqamahDelayMinutes(context: Context, prayerKey: String, minutes: Int) {
        getPrefs(context).edit().putInt("iqamah_delay_$prayerKey", minutes.coerceIn(1, 60)).apply()
    }

    fun isIqamahAlarmEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("iqamah_alarm_enabled", true)
    }

    fun setIqamahAlarmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("iqamah_alarm_enabled", enabled).apply()
    }

    fun isPostAdhanDuaEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("post_adhan_dua_enabled", true)
    }

    fun setPostAdhanDuaEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("post_adhan_dua_enabled", enabled).apply()
    }

    fun isEveningAzkarAlarmEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("evening_azkar_alarm_enabled", true)
    }

    fun setEveningAzkarAlarmEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("evening_azkar_alarm_enabled", enabled).apply()
    }

    fun isAssetAvailable(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.openFd(assetPath).use { it.length > 0L }
        } catch (_: Throwable) {
            false
        }
    }

    fun isMuadhinAssetAvailable(context: Context, muadhinName: String): Boolean {
        return isAssetAvailable(context, getMuadhinAssetPath(muadhinName))
    }

    // --- Audio volume & progressive volume & alerts configurations ---
    fun getAthanVolume(context: Context): Float {
        return getPrefs(context).getFloat("adhan_volume", 0.8f)
    }

    fun setAthanVolume(context: Context, volume: Float) {
        getPrefs(context).edit().putFloat("adhan_volume", volume).apply()
    }

    fun isGradualVolume(context: Context): Boolean {
        return getPrefs(context).getBoolean("adhan_gradual_volume", true)
    }

    fun setGradualVolume(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("adhan_gradual_volume", enabled).apply()
    }

    fun isVibrateOnAdhan(context: Context): Boolean {
        return getPrefs(context).getBoolean("adhan_vibrate", true)
    }

    fun setVibrateOnAdhan(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("adhan_vibrate", enabled).apply()
    }

    // Alerts Settings
    fun getPreAdhanMinutes(context: Context): Int {
        // Offset: 0 (No Alert), 5, 10, 15 minutes before
        return getPrefs(context).getInt("pre_adhan_minutes", 0)
    }

    fun setPreAdhanMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt("pre_adhan_minutes", minutes).apply()
    }

    fun isSilentDuringSleep(context: Context): Boolean {
        return getPrefs(context).getBoolean("silent_sleep", false)
    }

    fun setSilentDuringSleep(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("silent_sleep", enabled).apply()
    }

    fun isTravelMode(context: Context): Boolean {
        return getPrefs(context).getBoolean("travel_mode", false)
    }

    fun setTravelMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("travel_mode", enabled).apply()
    }

    fun isDaylightSaving(context: Context): Boolean {
        return getPrefs(context).getBoolean("daylight_saving", false)
    }

    fun setDaylightSaving(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("daylight_saving", enabled).apply()
    }

    // Reminders
    fun isPostPrayerAzkarReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("remind_post_azkar", true)
    }

    fun setPostPrayerAzkarReminder(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("remind_post_azkar", enabled).apply()
    }

    fun isQiyamReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("remind_qiyam", true)
    }

    fun setQiyamReminder(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("remind_qiyam", enabled).apply()
    }

    fun isFastingReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("remind_fasting", true)
    }

    fun setFastingReminder(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("remind_fasting", enabled).apply()
    }

    // --- Manual prayer offsets in minutes ---
    fun getManualOffset(context: Context, prayerKey: String): Int {
        return getPrefs(context).getInt("manual_offset_$prayerKey", 0)
    }

    fun setManualOffset(context: Context, prayerKey: String, offsetMinutes: Int) {
        getPrefs(context).edit().putInt("manual_offset_$prayerKey", offsetMinutes).apply()
    }

    // --- Calculate Prayer Times dynamically ---
    fun calculatePrayerTimesForDate(context: Context, date: Date): List<PrayerTimeInfo> {
        val coordinates = getSavedCoordinates(context)
        val calcMethodKey = getCalculationMethod(context)
        val madhabKey = getMadhab(context)
        val isDst = isDaylightSaving(context)

        val dateComponents = DateComponents.from(date)
        
        val defaultMethod = if (!getPrefs(context).contains("calc_method")) {
            when (TimeZone.getDefault().id) {
                "Africa/Cairo", "Africa/Khartoum", "Africa/Tripoli" -> "EGYPTIAN"
                "Asia/Riyadh", "Asia/Kuwait", "Asia/Bahrain", "Asia/Qatar", "Asia/Muscat", "Asia/Dubai", "Asia/Aden" -> "UMM_AL_QURA"
                else -> "MWL"
            }
        } else {
            calcMethodKey
        }

        val params = when (defaultMethod) {
            "EGYPTIAN" -> CalculationMethod.EGYPTIAN.parameters
            "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA.parameters
            "MWL" -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            "ISNA" -> CalculationMethod.NORTH_AMERICA.parameters
            "KARACHI" -> CalculationMethod.KARACHI.parameters
            else -> CalculationMethod.UMM_AL_QURA.parameters
        }
        
        params.madhab = if (madhabKey == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
        
        val pTimes = PrayerTimes(coordinates, dateComponents, params)
        
        val rawList = listOf(
            Triple("Fajr", "الفجر", pTimes.fajr),
            Triple("Sunrise", "الشروق", pTimes.sunrise),
            Triple("Dhuhr", "الظهر", pTimes.dhuhr),
            Triple("Asr", "العصر", pTimes.asr),
            Triple("Maghrib", "المغرب", pTimes.maghrib),
            Triple("Isha", "العشاء", pTimes.isha)
        )

        val output = mutableListOf<PrayerTimeInfo>()
        val formatter = SimpleDateFormat("hh:mm a", Locale.US)
        
        for (item in rawList) {
            val key = item.first
            val arName = item.second
            var rawTime = item.third ?: Date()
            val normCal = Calendar.getInstance().apply {
                time = rawTime
                set(Calendar.MILLISECOND, 0)
            }
            rawTime = normCal.time

            // Apply Manual Offsets
            val offsetMin = getManualOffset(context, key)
            if (offsetMin != 0) {
                val cal = Calendar.getInstance()
                cal.time = rawTime
                cal.add(Calendar.MINUTE, offsetMin)
                rawTime = cal.time
            }

            // Apply Daylight Saving adjustment if flagged manually (though system might already handle timezone offsets)
            if (isDst) {
                // Determine if we actually need to forcefully add an hour.
                // It's safer to just provide +1 hour if manually toggled, but a lot of systems 
                // apply DST automatically. If they check it, we just add 60 mins.
                val cal = Calendar.getInstance()
                cal.time = rawTime
                cal.add(Calendar.HOUR_OF_DAY, 1)
                rawTime = cal.time
            }

            output.add(
                PrayerTimeInfo(
                    name = key,
                    arabicName = arName,
                    timeMillis = rawTime.time,
                    formattedTime = formatter.format(rawTime),
                    isEnabled = isPrayerEnabled(context, key)
                )
            )
        }
        return output
    }

    // --- Find Next Prayer and Remaining Countdown ---
    fun getNextPrayer(context: Context, bufferSeconds: Long = 15L): Pair<PrayerTimeInfo, Long>? {
        val now = System.currentTimeMillis() + (bufferSeconds * 1000L)
        val todayPrayers = calculatePrayerTimesForDate(context, Date())
        
        // Find if we have any remaining enabled prayer today
        val nextToday = todayPrayers.find { it.timeMillis > now && it.name != "Sunrise" && isPrayerEnabled(context, it.name) }
        if (nextToday != null) {
            val diff = (nextToday.timeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            return Pair(nextToday, diff)
        }
        
        // If not, check tomorrow's prayers for first enabled prayer
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowPrayers = calculatePrayerTimesForDate(context, cal.time)
        val firstTomorrow = tomorrowPrayers.find { it.name != "Sunrise" && isPrayerEnabled(context, it.name) }
        if (firstTomorrow != null) {
            val diff = (firstTomorrow.timeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            return Pair(firstTomorrow, diff)
        }
        
        // Fallback: Tomorrow's Fajr even if toggled off so an alarm is always prepared
        val defaultFajr = tomorrowPrayers.find { it.name == "Fajr" }
        if (defaultFajr != null) {
            val diff = (defaultFajr.timeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            return Pair(defaultFajr, diff)
        }
        
        return null
    }

    fun getNextIqamah(context: Context, bufferSeconds: Long = 10L): Pair<PrayerTimeInfo, Long>? {
        val now = System.currentTimeMillis()
        val threshold = now + (bufferSeconds * 1000L)
        val formatter = SimpleDateFormat("hh:mm a", Locale.US)

        val todayPrayers = calculatePrayerTimesForDate(context, Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowPrayers = calculatePrayerTimesForDate(context, cal.time)

        val candidates = (todayPrayers + tomorrowPrayers)
            .filter { it.name != "Sunrise" && isPrayerEnabled(context, it.name) }
            .map { prayer ->
                val delayMins = getIqamahDelayMinutes(context, prayer.name)
                val iqamahMillis = prayer.timeMillis + (delayMins * 60_000L)
                PrayerTimeInfo(
                    name = prayer.name,
                    arabicName = prayer.arabicName,
                    timeMillis = iqamahMillis,
                    formattedTime = formatter.format(Date(iqamahMillis)),
                    isEnabled = prayer.isEnabled
                )
            }
            .filter { it.timeMillis > threshold }
            .sortedBy { it.timeMillis }

        val next = candidates.firstOrNull() ?: return null
        return Pair(next, (next.timeMillis - now).coerceAtLeast(0L))
    }

    fun getNextEveningAzkarTime(context: Context, bufferSeconds: Long = 10L): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        val threshold = now + (bufferSeconds * 1000L)

        val todayMaghrib = calculatePrayerTimesForDate(context, Date()).firstOrNull { it.name == "Maghrib" }
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowMaghrib = calculatePrayerTimesForDate(context, cal.time).firstOrNull { it.name == "Maghrib" }

        val nextTrigger = listOfNotNull(todayMaghrib, tomorrowMaghrib)
            .map { it.timeMillis - (EVENING_AZKAR_BEFORE_MAGHRIB_MINUTES * 60_000L) }
            .firstOrNull { it > threshold }
            ?: return null

        return Pair(nextTrigger, (nextTrigger - now).coerceAtLeast(0L))
    }

    // --- Qibla Direction in Degrees ---
    fun calculateQiblaDirection(context: Context): Double {
        val coords = getSavedCoordinates(context)
        val latRad = Math.toRadians(coords.latitude)
        val lonRad = Math.toRadians(coords.longitude)
        
        // Mecca coordinates
        val meccaLatRad = Math.toRadians(DEFAULT_LATITUDE)
        val meccaLonRad = Math.toRadians(DEFAULT_LONGITUDE)
        
        val dLon = meccaLonRad - lonRad
        
        val y = Math.sin(dLon) * Math.cos(meccaLatRad)
        val x = Math.cos(latRad) * Math.sin(meccaLatRad) - Math.sin(latRad) * Math.cos(meccaLatRad) * Math.cos(dLon)
        
        var qiblaRad = Math.atan2(y, x)
        var qiblaDeg = Math.toDegrees(qiblaRad)
        
        qiblaDeg = (qiblaDeg + 360.0) % 360.0
        return qiblaDeg
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarmClockExact(
        alarmManager: AlarmManager,
        triggerMillis: Long,
        pendingIntent: PendingIntent,
        showPendingIntent: PendingIntent
    ) {
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Throwable) {
            Log.w("AdhanManager", "setAlarmClock failed, falling back", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } catch (e2: Throwable) {
                Log.e("AdhanManager", "Failed entirely to schedule alarm", e2)
            }
        }
    }

    // --- Android Exact Alarm Rescheduler ---
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleNextAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val showIntent = Intent(context, com.example.MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("OPEN_SCREEN", "ADHAN_SETTINGS")
            }
            val showPendingIntent = PendingIntent.getActivity(context, 1003, showIntent, flags)

            val nextPair = getNextPrayer(context, 20L)
            if (nextPair != null) {
                val nextPrayer = nextPair.first
                val nextTimeMillis = nextPrayer.timeMillis

                val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                    action = "com.example.ACTION_TRIGGER_ATHAN"
                    putExtra("TRIGGER_MODE", "ADHAN")
                    putExtra("PRAYER_NAME", nextPrayer.name)
                    putExtra("PRAYER_ARABIC", nextPrayer.arabicName)
                    putExtra("PRAYER_TIME", nextTimeMillis)
                }
                val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)
                scheduleAlarmClockExact(alarmManager, nextTimeMillis, pendingIntent, showPendingIntent)
                Log.d("AdhanManager", "Scheduled next Adhan for ${nextPrayer.arabicName} at ${nextPrayer.formattedTime}")

                // Pre-Adhan alert notification if configured
                val preMinutes = getPreAdhanMinutes(context)
                val preIntent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                    action = "com.example.ACTION_TRIGGER_ATHAN"
                    putExtra("PRAYER_NAME", nextPrayer.name)
                    putExtra("PRAYER_ARABIC", nextPrayer.arabicName)
                    putExtra("PRAYER_TIME", nextTimeMillis)
                    putExtra("IS_PRE_ALERT", true)
                    putExtra("PRE_ALERT_MINUTES", preMinutes)
                }
                val prePendingIntent = PendingIntent.getBroadcast(context, 1002, preIntent, flags)
                if (preMinutes > 0) {
                    val preTimeMillis = nextTimeMillis - (preMinutes * 60 * 1000L)
                    if (preTimeMillis > System.currentTimeMillis() + 10000L) {
                        scheduleAlarmClockExact(alarmManager, preTimeMillis, prePendingIntent, showPendingIntent)
                    }
                } else {
                    alarmManager.cancel(prePendingIntent)
                }
            }

            // Schedule Next Iqamah Alarm (Fajr 15m, Dhuhr 15m, Asr 15m, Maghrib 12m, Isha 15m)
            val iqamahIntentBase = Intent(context, AdhanAlarmReceiver::class.java).apply {
                action = "com.example.ACTION_TRIGGER_ATHAN"
                putExtra("TRIGGER_MODE", "IQAMAH")
            }
            if (isIqamahAlarmEnabled(context)) {
                val nextIqamahPair = getNextIqamah(context, 10L)
                if (nextIqamahPair != null) {
                    val iqamahInfo = nextIqamahPair.first
                    iqamahIntentBase.putExtra("PRAYER_NAME", iqamahInfo.name)
                    iqamahIntentBase.putExtra("PRAYER_ARABIC", iqamahInfo.arabicName)
                    iqamahIntentBase.putExtra("PRAYER_TIME", iqamahInfo.timeMillis)
                    val iqamahPendingIntent = PendingIntent.getBroadcast(context, 1004, iqamahIntentBase, flags)
                    scheduleAlarmClockExact(alarmManager, iqamahInfo.timeMillis, iqamahPendingIntent, showPendingIntent)
                    Log.d("AdhanManager", "Scheduled next Iqamah for ${iqamahInfo.arabicName} at ${iqamahInfo.formattedTime}")
                }
            } else {
                val iqamahPendingIntent = PendingIntent.getBroadcast(context, 1004, iqamahIntentBase, flags)
                alarmManager.cancel(iqamahPendingIntent)
            }

            // Schedule Evening Azkar Audio Alarm (30 minutes before Maghrib)
            val eveningAzkarIntentBase = Intent(context, AdhanAlarmReceiver::class.java).apply {
                action = "com.example.ACTION_TRIGGER_ATHAN"
                putExtra("TRIGGER_MODE", "EVENING_AZKAR")
                putExtra("PRAYER_NAME", "Maghrib")
                putExtra("PRAYER_ARABIC", "أذكار المساء")
            }
            if (isEveningAzkarAlarmEnabled(context)) {
                val nextEveningAzkar = getNextEveningAzkarTime(context, 10L)
                if (nextEveningAzkar != null) {
                    eveningAzkarIntentBase.putExtra("PRAYER_TIME", nextEveningAzkar.first)
                    val eveningPendingIntent = PendingIntent.getBroadcast(context, 1005, eveningAzkarIntentBase, flags)
                    scheduleAlarmClockExact(alarmManager, nextEveningAzkar.first, eveningPendingIntent, showPendingIntent)
                    Log.d("AdhanManager", "Scheduled Evening Azkar (30m before Maghrib) at ${Date(nextEveningAzkar.first)}")
                }
            } else {
                val eveningPendingIntent = PendingIntent.getBroadcast(context, 1005, eveningAzkarIntentBase, flags)
                alarmManager.cancel(eveningPendingIntent)
            }
        } catch (t: Throwable) {
            Log.e("AdhanManager", "General error inside scheduleNextAlarm", t)
        }
    }

    // --- Schedule Immediate Test Alarm for Testing Locking & Alarm Trigger ---
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleTestAlarm(context: Context, delaySeconds: Int = 10) {
        try {
            val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                action = "com.example.ACTION_TRIGGER_ATHAN"
                putExtra("TRIGGER_MODE", "ADHAN")
                putExtra("PRAYER_NAME", "Asr")
                putExtra("PRAYER_ARABIC", "العصر (تجربة)")
                putExtra("PRAYER_TIME", triggerTime)
                putExtra("IS_TEST", true)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, 1099, intent, flags)

            val showIntent = Intent(context, com.example.MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val showPendingIntent = PendingIntent.getActivity(context, 1098, showIntent, flags)

            scheduleAlarmClockExact(alarmManager, triggerTime, pendingIntent, showPendingIntent)
            Log.d("AdhanManager", "Test alarmClock scheduled for $delaySeconds seconds from now")
        } catch (t: Throwable) {
            Log.e("AdhanManager", "Failed to schedule test alarm", t)
        }
    }
}
