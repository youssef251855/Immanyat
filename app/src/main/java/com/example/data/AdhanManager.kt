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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PrayerTimeInfo(
    val name: String,
    val arabicName: String,
    val timeMillis: Long,
    val formattedTime: String,
    val isEnabled: Boolean = true
)

data class MosqueModel(
    val name: String,
    val distanceMeters: Int,
    val directionDegrees: Double,
    val address: String
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

    // --- Muadhin parameters ---
    fun getMuadhinForPrayer(context: Context, prayerKey: String): String {
        // Defaults: Mishary Al-Afasy, Abdul Basit, Al-Naqshabandi, Mecca, Medina
        return getPrefs(context).getString("pray_muadhin_$prayerKey", "مشاري العفاسي") ?: "مشاري العفاسي"
    }

    fun setMuadhinForPrayer(context: Context, prayerKey: String, muadhin: String) {
        getPrefs(context).edit().putString("pray_muadhin_$prayerKey", muadhin).apply()
    }

    // Muadhin play list and audio streaming URLs
    val MUADHIN_URLS = mapOf(
        "مشاري العفاسي" to "https://www.islamcan.com/audio/adhan/azan2.mp3",
        "عبد الباسط" to "https://www.islamcan.com/audio/adhan/azan3.mp3",
        "النقشبندي" to "https://www.islamcan.com/audio/adhan/azan15.mp3",
        "مؤذن مكة" to "https://www.islamcan.com/audio/adhan/azan1.mp3",
        "مؤذن المدينة" to "https://www.islamcan.com/audio/adhan/azan13.mp3"
    )

    // --- Offline audio downloading system ---
    fun getLocalAthanFile(context: Context, muadhinName: String): File {
        val sanitized = muadhinName.replace(" ", "_").lowercase()
        return File(context.filesDir, "adhan_$sanitized.mp3")
    }

    fun isAthanDownloaded(context: Context, muadhinName: String): Boolean {
        val file = getLocalAthanFile(context, muadhinName)
        return file.exists() && file.length() > 50 * 1024 // At least 50KB to make sure it's valid MP3
    }

    suspend fun downloadAthan(context: Context, muadhinName: String, onProgress: (Float) -> Unit): Boolean {
        val urlString = MUADHIN_URLS[muadhinName] ?: return false
        val file = getLocalAthanFile(context, muadhinName)
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()
                
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }
                
                val fileLength = connection.contentLength
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(file)
                
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (inputStream.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgress(total.toFloat() / fileLength)
                    }
                    outputStream.write(data, 0, count)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                true
            } catch (e: Exception) {
                Log.e("AdhanManager", "Download failed for $muadhinName", e)
                if (file.exists()) {
                    file.delete()
                }
                false
            }
        }
    }

    fun deleteAthan(context: Context, muadhinName: String) {
        val file = getLocalAthanFile(context, muadhinName)
        if (file.exists()) {
            file.delete()
        }
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

    // --- Qibla Direction in Degrees ---
    fun calculateQiblaDirection(context: Context): Double {
        val coords = getSavedCoordinates(context)
        val latRad = Math.toRadians(coords.latitude)
        val lonRad = Math.toRadians(coords.longitude)
        
        // Mecca coordinates
        val meccaLatRad = Math.toRadians(DEFAULT_LATITUDE)
        val meccaLonRad = Math.toRadians(DEFAULT_LONGITUDE)
        
        val dLon = meccaLonRad - lonRad
        
        val y = Math.sin(dLon)
        val x = Math.cos(latRad) * Math.sin(meccaLatRad) - Math.sin(latRad) * Math.cos(meccaLatRad) * Math.cos(dLon)
        
        var qiblaRad = Math.atan2(y, x)
        var qiblaDeg = Math.toDegrees(qiblaRad)
        
        qiblaDeg = (qiblaDeg + 360.0) % 360.0
        return qiblaDeg
    }

    // --- Simulated Nearest Mosques ---
    fun getNearestMosques(context: Context): List<MosqueModel> {
        val coords = getSavedCoordinates(context)
        val qibla = calculateQiblaDirection(context)
        
        // Generate coordinates and parameters relative to the user position as deterministic mocks
        val baseSeed = (coords.latitude + coords.longitude).toInt()
        val rand = Random(baseSeed.toLong())
        
        return listOf(
            MosqueModel("مسجد التقوى", 240 + rand.nextInt(400), (qibla - 15 + rand.nextInt(30) + 360) % 360, "على بعد دقيقتين سيرًا"),
            MosqueModel("مسجد الرحمن", 650 + rand.nextInt(300), (qibla - 8 + rand.nextInt(25) + 360) % 360, "على بعد 7 دقائق سيرًا"),
            MosqueModel("مسجد النور الكبير", 1200 + rand.nextInt(600), (qibla + 20 + rand.nextInt(30) + 360) % 360, "على بعد 3 دقائق بالسيارة")
        )
    }

    // --- Android Exact Alarm Rescheduler ---
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleNextAlarm(context: Context) {
        try {
            val nextPair = getNextPrayer(context, 20L) ?: return
            val nextPrayer = nextPair.first
            val nextTimeMillis = nextPrayer.timeMillis

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            // Alarm intent to trigger BroadcastReceiver custom action
            val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                action = "com.example.ACTION_TRIGGER_ATHAN"
                putExtra("PRAYER_NAME", nextPrayer.name)
                putExtra("PRAYER_ARABIC", nextPrayer.arabicName)
                putExtra("PRAYER_TIME", nextTimeMillis)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)

            // Intent to open app when clicking alarm icon in lockscreen or notification
            val showIntent = Intent(context, com.example.MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("OPEN_SCREEN", "ADHAN_SETTINGS")
            }
            val showPendingIntent = PendingIntent.getActivity(context, 1003, showIntent, flags)

            // Set via AlarmClockInfo:
            // 1) Exempts app from Background Foreground Service Start Restrictions (Android 12/13/14)
            // 2) Bypasses Doze Mode and deep sleep restrictions reliably
            // 3) Exactly triggers at the millisecond
            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTimeMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d("AdhanManager", "setAlarmClock succeeded for ${nextPrayer.arabicName} at ${nextPrayer.formattedTime} ($nextTimeMillis)")
            } catch (e: Throwable) {
                Log.w("AdhanManager", "setAlarmClock failed, falling back to setExactAndAllowWhileIdle", e)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                nextTimeMillis,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                nextTimeMillis,
                                pendingIntent
                            )
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextTimeMillis,
                            pendingIntent
                        )
                    }
                } catch (e2: Throwable) {
                    Log.e("AdhanManager", "Failed entirely to schedule primary alarm", e2)
                }
            }

            // Also schedule pre-adhan alerts if configured (e.g. 10 mins before)
            val preMinutes = getPreAdhanMinutes(context)
            if (preMinutes > 0) {
                val preTimeMillis = nextTimeMillis - (preMinutes * 60 * 1000)
                if (preTimeMillis > System.currentTimeMillis() + 10000L) {
                    val preIntent = Intent(context, AdhanAlarmReceiver::class.java).apply {
                        action = "com.example.ACTION_TRIGGER_ATHAN"
                        putExtra("PRAYER_NAME", nextPrayer.name)
                        putExtra("PRAYER_ARABIC", nextPrayer.arabicName)
                        putExtra("PRAYER_TIME", nextTimeMillis)
                        putExtra("IS_PRE_ALERT", true)
                        putExtra("PRE_ALERT_MINUTES", preMinutes)
                    }
                    val prePendingIntent = PendingIntent.getBroadcast(context, 1002, preIntent, flags)
                    try {
                        val preClockInfo = AlarmManager.AlarmClockInfo(preTimeMillis, showPendingIntent)
                        alarmManager.setAlarmClock(preClockInfo, prePendingIntent)
                        Log.d("AdhanManager", "Pre-Adhan setAlarmClock $preMinutes mins before ${nextPrayer.arabicName}")
                    } catch (e3: Throwable) {
                        try {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                preTimeMillis,
                                prePendingIntent
                            )
                        } catch (t: Throwable) {}
                    }
                }
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

            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d("AdhanManager", "Test alarmClock scheduled for $delaySeconds seconds from now")
            } catch (e: Throwable) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (t: Throwable) {
            Log.e("AdhanManager", "Failed to schedule test alarm", t)
        }
    }
}
