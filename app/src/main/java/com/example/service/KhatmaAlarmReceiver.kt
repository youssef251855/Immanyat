package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class KhatmaAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("KhatmaAlarmReceiver", "Received Khatma action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {
            // Reschedule Khatma reminder if enabled
            rescheduleReminder(context)
            return
        }

        if (action == "com.example.ACTION_QURAN_KHATMA_REMINDER") {
            val dailyPages = intent.getIntExtra("DAILY_PAGES", 20)
            val planDays = intent.getIntExtra("PLAN_DAYS", 30)
            val currentPage = intent.getIntExtra("CURRENT_PAGE", 1)

            showKhatmaNotification(context, dailyPages, planDays, currentPage)
            rescheduleReminder(context)
        }
    }

    private fun showKhatmaNotification(context: Context, dailyPages: Int, planDays: Int, currentPage: Int) {
        val channelId = "emaniat_khatma_chan"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "تنبيهات الورد القرآني والختمة",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(chan)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "QURAN")
        }
        val pIntent = PendingIntent.getActivity(
            context,
            301,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📖 حان موعد وردك القرآني اليومي")
            .setContentText("هدفك اليوم قراءة $dailyPages صفحة لتكمل ختمتك ($planDays يوماً) - صفحتك الحالية: $currentPage.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pIntent)
            .addAction(android.R.drawable.ic_menu_view, "ابدأ التلاوة الآن 📖", pIntent)

        notificationManager.notify(777, builder.build())
    }

    private fun rescheduleReminder(context: Context) {
        try {
            com.example.data.KhatmaPlanManager.scheduleDailyReminder(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
