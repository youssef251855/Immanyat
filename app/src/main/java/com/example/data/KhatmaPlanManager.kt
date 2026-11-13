package com.example.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.service.KhatmaAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

data class KhatmaPlan(
    val planDays: Int = 30,
    val startPage: Int = 1,
    val currentPage: Int = 1,
    val startDateMillis: Long = System.currentTimeMillis(),
    val targetDateMillis: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20, // 8:00 PM default
    val reminderMinute: Int = 0,
    val todayReadCount: Int = 0,
    val lastReadDayOfYear: Int = -1
) {
    val totalPages: Int = 604
    val remainingPages: Int = (totalPages - currentPage).coerceAtLeast(0)
    
    val dailyTargetPages: Int
        get() {
            val daysLeft = daysRemaining.coerceAtLeast(1)
            return ceil(remainingPages.toDouble() / daysLeft).toInt().coerceAtLeast(1)
        }

    val daysRemaining: Int
        get() {
            val now = System.currentTimeMillis()
            val diff = targetDateMillis - now
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            return days.coerceAtLeast(0)
        }

    val daysPassed: Int
        get() {
            val now = System.currentTimeMillis()
            val diff = now - startDateMillis
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            return days.coerceAtLeast(0)
        }

    val progressPercent: Float
        get() = (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

    val isCompleted: Boolean
        get() = currentPage >= totalPages

    val isTodayGoalCompleted: Boolean
        get() = todayReadCount >= dailyTargetPages
}

object KhatmaPlanManager {
    private const val PREFS_NAME = "emaniat_khatma_plan_prefs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPlan(context: Context): KhatmaPlan {
        val prefs = getPrefs(context)
        val planDays = prefs.getInt("plan_days", 30)
        val startPage = prefs.getInt("start_page", 1)
        val currentPage = prefs.getInt("current_page", 1)
        val startDateMillis = prefs.getLong("start_date", System.currentTimeMillis())
        val defaultTarget = startDateMillis + (planDays.toLong() * 24 * 60 * 60 * 1000)
        val targetDateMillis = prefs.getLong("target_date", defaultTarget)
        val reminderEnabled = prefs.getBoolean("reminder_enabled", true)
        val reminderHour = prefs.getInt("reminder_hour", 20)
        val reminderMinute = prefs.getInt("reminder_minute", 0)
        
        val lastDay = prefs.getInt("last_read_day", -1)
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val todayRead = if (lastDay == currentDay) prefs.getInt("today_read_count", 0) else 0

        return KhatmaPlan(
            planDays = planDays,
            startPage = startPage,
            currentPage = currentPage,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
            todayReadCount = todayRead,
            lastReadDayOfYear = lastDay
        )
    }

    fun createNewPlan(
        context: Context,
        planDays: Int,
        startPage: Int = 1,
        reminderHour: Int = 20,
        reminderMinute: Int = 0,
        reminderEnabled: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        val targetDate = now + (planDays.toLong() * 24 * 60 * 60 * 1000)
        val cal = Calendar.getInstance()

        getPrefs(context).edit()
            .putInt("plan_days", planDays)
            .putInt("start_page", startPage)
            .putInt("current_page", startPage)
            .putLong("start_date", now)
            .putLong("target_date", targetDate)
            .putBoolean("reminder_enabled", reminderEnabled)
            .putInt("reminder_hour", reminderHour)
            .putInt("reminder_minute", reminderMinute)
            .putInt("today_read_count", 0)
            .putInt("last_read_day", cal.get(Calendar.DAY_OF_YEAR))
            .apply()

        scheduleDailyReminder(context)
    }

    fun updateProgress(context: Context, newCurrentPage: Int) {
        val prefs = getPrefs(context)
        val oldPage = prefs.getInt("current_page", 1)
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("last_read_day", -1)
        
        var todayRead = if (lastDay == currentDay) prefs.getInt("today_read_count", 0) else 0
        val diff = (newCurrentPage - oldPage).coerceAtLeast(0)
        todayRead += diff

        prefs.edit()
            .putInt("current_page", newCurrentPage.coerceIn(1, 604))
            .putInt("today_read_count", todayRead)
            .putInt("last_read_day", currentDay)
            .apply()
    }

    fun setReminderSettings(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        getPrefs(context).edit()
            .putBoolean("reminder_enabled", enabled)
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()

        scheduleDailyReminder(context)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleDailyReminder(context: Context) {
        val plan = getPlan(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, KhatmaAlarmReceiver::class.java).apply {
            action = "com.example.ACTION_QURAN_KHATMA_REMINDER"
            putExtra("DAILY_PAGES", plan.dailyTargetPages)
            putExtra("PLAN_DAYS", plan.planDays)
            putExtra("CURRENT_PAGE", plan.currentPage)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 2001, intent, flags)

        if (!plan.reminderEnabled || plan.isCompleted) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, plan.reminderHour)
            set(Calendar.MINUTE, plan.reminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("KhatmaPlanManager", "Khatma daily reminder scheduled for ${cal.time}")
        } catch (e: Throwable) {
            Log.e("KhatmaPlanManager", "Failed to schedule exact khatma reminder", e)
        }
    }
}
