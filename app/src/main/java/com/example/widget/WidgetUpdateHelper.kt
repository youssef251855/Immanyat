package com.example.widget

import android.content.Context

object WidgetUpdateHelper {
    fun updateAllWidgets(context: Context) {
        try {
            PrayerTimesWidgetProvider.updateAll(context)
            DailyZikrWidgetProvider.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
