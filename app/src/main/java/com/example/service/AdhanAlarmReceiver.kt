package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AdhanManager
import java.io.File
import java.util.Calendar

class AdhanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("AdhanAlarmReceiver", "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" || 
            action == Intent.ACTION_TIME_CHANGED || 
            action == Intent.ACTION_TIMEZONE_CHANGED) {
            
            // Reschedule upcoming alarm cleanly
            AdhanManager.scheduleNextAlarm(context)
            return
        }

        if (action == "com.example.ACTION_TRIGGER_ATHAN") {
            val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Fajr"
            val prayerArabic = intent.getStringExtra("PRAYER_ARABIC") ?: "الفجر"
            val isPreAlert = intent.getBooleanExtra("IS_PRE_ALERT", false)

            if (isPreAlert) {
                // Raise Pre-Adhan early notification (e.g. 10 mins before)
                val preMins = intent.getIntExtra("PRE_ALERT_MINUTES", 10)
                showPreAdhanNotification(context, prayerArabic, preMins)
            } else {
                // Check if silent during sleep is set
                if (AdhanManager.isSilentDuringSleep(context)) {
                    val cal = Calendar.getInstance()
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour in 23..4 || hour in 0..4) { // Quiet hours between 11 PM and 5 AM
                        Log.d("AdhanAlarmReceiver", "Skipping Adhan sound during sleep hours (11PM-5AM)")
                        showSilentNotification(context, prayerArabic)
                        AdhanManager.scheduleNextAlarm(context)
                        return
                    }
                }

                // Launch Foreground Service to play full sound and build notification actions
                startPlayerService(context, prayerName, prayerArabic)
            }

            // Immediately schedule the NEXT prayer alarm to chain executions permanently
            AdhanManager.scheduleNextAlarm(context)
        }
    }

    private fun startPlayerService(context: Context, name: String, arabic: String) {
        val serviceIntent = Intent(context, AdhanPlayerService::class.java).apply {
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
            Log.e("AdhanAlarmReceiver", "Failed starting player service directly. Attempting custom action", e)
        }
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
