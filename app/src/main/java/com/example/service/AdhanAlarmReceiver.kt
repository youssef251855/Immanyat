package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.AdhanAlarmActivity
import com.example.MainActivity
import com.example.R
import com.example.data.AdhanManager
import java.util.Calendar

object AdhanWakeLockHolder {
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        try {
            if (wakeLock == null) {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Emaniat:AdhanWakeLockHolder")
                wakeLock?.setReferenceCounted(false)
            }
            wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes timeout to keep CPU alive during handoff
        } catch (e: Exception) {
            Log.e("AdhanWakeLockHolder", "Failed acquiring wakeLock", e)
        }
    }

    @Synchronized
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("AdhanWakeLockHolder", "Failed releasing wakeLock", e)
        }
    }
}

class AdhanAlarmReceiver : BroadcastReceiver() {

    companion object {
        @Volatile
        private var fallbackPlayer: MediaPlayer? = null

        @Synchronized
        fun stopFallbackAudio() {
            try {
                fallbackPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Throwable) {
            }
            fallbackPlayer = null
            AdhanPlayerService.activeMode.value = AdhanPlayerService.AlarmAudioMode.IDLE
            AdhanPlayerService.isAthanPlaying.value = false
            AdhanPlayerService.isDuaPlaying.value = false
            AdhanPlayerService.isIqamahPlaying.value = false
            AdhanPlayerService.isEveningAzkarPlaying.value = false
            AdhanWakeLockHolder.release()
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("AdhanAlarmReceiver", "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            AdhanManager.scheduleNextAlarm(context)
            AdhanAlarmWatchdogWorker.ensureScheduled(context)
            return
        }

        if (action == "com.example.ACTION_TRIGGER_ATHAN") {
            AdhanWakeLockHolder.acquire(context)

            val triggerMode = intent.getStringExtra("TRIGGER_MODE") ?: "ADHAN"
            val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Fajr"
            val prayerArabic = intent.getStringExtra("PRAYER_ARABIC")
                ?: if (triggerMode == "EVENING_AZKAR") "أذكار المساء" else "الفجر"
            val isPreAlert = intent.getBooleanExtra("IS_PRE_ALERT", false)
            val isTest = intent.getBooleanExtra("IS_TEST", false)

            if (isPreAlert) {
                val preMins = intent.getIntExtra("PRE_ALERT_MINUTES", 10)
                showPreAdhanNotification(context, prayerArabic, preMins)
                AdhanWakeLockHolder.release()
            } else {
                val isQuietSleep = !isTest &&
                    AdhanManager.isQuietSleepActive(context, triggerMode, prayerName)

                if (isQuietSleep) {
                    Log.d("AdhanAlarmReceiver", "Skipping $triggerMode sound during sleep hours (11PM-5AM, Fajr exempted)")
                    showSilentNotification(context, prayerArabic)
                    AdhanManager.scheduleNextAlarm(context)
                    AdhanWakeLockHolder.release()
                    return
                }

                if (!isTest) {
                    when (triggerMode) {
                        "IQAMAH" -> AdhanManager.markIqamahTriggeredToday(context, prayerName)
                        "EVENING_AZKAR" -> AdhanManager.markEveningAzkarTriggeredToday(context)
                        else -> AdhanManager.markPrayerTriggeredToday(context, prayerName)
                    }
                }

                startPlayerService(context, triggerMode, prayerName, prayerArabic)
            }

            if (!isTest) {
                AdhanManager.scheduleNextAlarm(context)
            }
        }
    }

    private fun startPlayerService(context: Context, triggerMode: String, name: String, arabic: String) {
        val serviceIntent = Intent(context, AdhanPlayerService::class.java).apply {
            putExtra("TRIGGER_MODE", triggerMode)
            putExtra("PRAYER_NAME", name)
            putExtra("PRAYER_ARABIC", arabic)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("AdhanAlarmReceiver", "Failed starting player service directly. Attempting direct fallback", e)
            playDirectFallbackAudio(context, triggerMode, name, arabic)
        }
    }

    private fun playDirectFallbackAudio(context: Context, triggerMode: String, name: String, arabic: String) {
        val mode = when (triggerMode) {
            "IQAMAH" -> AdhanPlayerService.AlarmAudioMode.IQAMAH
            "EVENING_AZKAR" -> AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR
            else -> AdhanPlayerService.AlarmAudioMode.ADHAN
        }
        AdhanPlayerService.currentAthanPrayer.value = name
        AdhanPlayerService.currentAthanArabic.value = arabic

        try {
            stopFallbackAudio()
            AdhanPlayerService.activeMode.value = mode
            AdhanPlayerService.isAthanPlaying.value = mode == AdhanPlayerService.AlarmAudioMode.ADHAN
            AdhanPlayerService.isIqamahPlaying.value = mode == AdhanPlayerService.AlarmAudioMode.IQAMAH
            AdhanPlayerService.isEveningAzkarPlaying.value = mode == AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val minAudible = (maxVol * 0.7).toInt().coerceAtLeast(1)
            if (currentVol < minAudible && maxVol > 0) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minAudible, 0)
                } catch (_: Throwable) {}
            }

            val assetPath = when (mode) {
                AdhanPlayerService.AlarmAudioMode.IQAMAH -> AdhanManager.ASSET_IQAMAH_AUDIO
                AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> AdhanManager.ASSET_EVENING_AZKAR_AUDIO
                else -> AdhanManager.getMuadhinAssetPath(AdhanManager.getMuadhinForPrayer(context, name))
            }
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                try {
                    context.assets.openFd(assetPath).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } catch (_: Throwable) {
                    val rawFallback = if (mode == AdhanPlayerService.AlarmAudioMode.IQAMAH) R.raw.iqamah else R.raw.adhan
                    context.resources.openRawResourceFd(rawFallback).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                }
                prepare()
                val vol = AdhanManager.getAthanVolume(context).coerceIn(0.4f, 1.0f)
                setVolume(vol, vol)
                start()
                setOnCompletionListener {
                    stopFallbackAudio()
                }
            }
            fallbackPlayer = player
        } catch (t: Throwable) {
            Log.e("AdhanAlarmReceiver", "Direct fallback audio failed", t)
            stopFallbackAudio()
        }

        val channelId = "emaniat_athan_alarm_chan_v2"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "الأذان ومواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(chan)
        }

        val launchIntent = Intent(context, AdhanAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_ATHAN_SCREEN", true)
            putExtra("PRAYER_ARABIC", arabic)
        }
        try {
            context.startActivity(launchIntent)
        } catch (_: Throwable) {}

        val pIntent = PendingIntent.getActivity(
            context,
            501,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = when (mode) {
            AdhanPlayerService.AlarmAudioMode.IQAMAH -> "حان الآن موعد إقامة صلاة $arabic 📣"
            AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> "أذكار المساء المباركة 🌅"
            else -> "حان الآن موعد صلاة $arabic 🕌"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notifTitle)
            .setContentText("اضغط لفتح الشاشة والتحكم بالصوت")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pIntent)

        notificationManager.notify(12345, builder.build())
    }

    private fun showPreAdhanNotification(context: Context, prayerArabic: String, minutes: Int) {
        val channelId = "emaniat_alerts_chan"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "تنبيهات الإستعداد للصلاة",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(chan)
        }

        // Active Audio Reminder for Alert Before Adhan
        try {
            val alertUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            val ringtone = android.media.RingtoneManager.getRingtone(context, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                ringtone?.audioAttributes = attrs
            }
            ringtone?.play()
            
            // Limit playing time to 4 seconds to serve as a gentle beep/alarm
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (ringtone?.isPlaying == true) {
                        ringtone.stop()
                    }
                } catch (t: Throwable) {}
            }, 4000L)
        } catch (e: Exception) {
            Log.e("AdhanAlarmReceiver", "Error playing pre-adhan audio alert", e)
        }

        // Trigger safe vibration for early alarm
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pIntent = PendingIntent.getActivity(context, 201, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان موعد الاستعداد للصلاة 🕌")
            .setContentText("سترفع شعائر أذان صلاة $prayerArabic بعد $minutes دقائق إن شاء الله.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pIntent)

        notificationManager.notify(789, builder.build())
    }

    private fun showSilentNotification(context: Context, prayerArabic: String) {
        val channelId = "emaniat_athan_chan"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "الأذان ومواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(chan)
        }

        val launchIntent = Intent(context, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(context, 202, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("حان الآن وقت صلاة $prayerArabic 🕌")
            .setContentText("تم كتم صوت الأذان لوجود الهاتف في وضع النوم الهادئ.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pIntent)

        notificationManager.notify(456, builder.build())
    }
}
