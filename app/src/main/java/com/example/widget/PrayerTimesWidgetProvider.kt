package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AdhanManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrayerTimesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)

            try {
                val prayers = AdhanManager.calculatePrayerTimesForDate(context, Date())
                val location = AdhanManager.getSavedLocationName(context)
                val nextPair = AdhanManager.getNextPrayer(context)

                val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale("ar")).format(Date())
                views.setTextViewText(R.id.tv_widget_location_and_date, "$location 📍 • $dateStr")

                if (nextPair != null) {
                    val nextPrayer = nextPair.first
                    val remainingMillis = nextPair.second
                    val hours = remainingMillis / (1000 * 60 * 60)
                    val minutes = (remainingMillis % (1000 * 60 * 60)) / (1000 * 60)

                    views.setTextViewText(R.id.tv_widget_next_prayer_title, "الصلاة القادمة: ${nextPrayer.arabicName} 🕌")
                    views.setTextViewText(R.id.tv_widget_time_remaining, String.format(Locale.getDefault(), "%02d:%02d", hours, minutes))
                } else {
                    views.setTextViewText(R.id.tv_widget_next_prayer_title, "مواقيت الصلاة 🕌")
                    views.setTextViewText(R.id.tv_widget_time_remaining, "--:--")
                }

                // Fill each prayer time
                prayers.find { it.name == "Fajr" }?.let {
                    views.setTextViewText(R.id.tv_fajr_time, formatShortTime(it.timeMillis))
                }
                prayers.find { it.name == "Dhuhr" }?.let {
                    views.setTextViewText(R.id.tv_dhuhr_time, formatShortTime(it.timeMillis))
                }
                prayers.find { it.name == "Asr" }?.let {
                    views.setTextViewText(R.id.tv_asr_time, formatShortTime(it.timeMillis))
                }
                prayers.find { it.name == "Maghrib" }?.let {
                    views.setTextViewText(R.id.tv_maghrib_time, formatShortTime(it.timeMillis))
                }
                prayers.find { it.name == "Isha" }?.let {
                    views.setTextViewText(R.id.tv_isha_time, formatShortTime(it.timeMillis))
                }

                // Set Click Intent to open MainActivity
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    1101,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            } catch (e: Exception) {
                e.printStackTrace()
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun formatShortTime(millis: Long): String {
            val sdf = SimpleDateFormat("hh:mm", Locale.US)
            return sdf.format(Date(millis))
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PrayerTimesWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
            }
        }
    }
}
