package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AdhanManager
import com.example.data.KhatmaPlanManager
import java.util.concurrent.TimeUnit

/**
 * Periodic daily safety-net worker that verifies the next Adhan, Iqamah, Evening Azkar,
 * and Khatma alarms remain scheduled in AlarmManager even if an alarm was missed while
 * the device was powered off or restricted in the background.
 */
class AdhanAlarmWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            AdhanManager.scheduleNextAlarm(applicationContext)
            KhatmaPlanManager.scheduleDailyReminder(applicationContext)
            Log.d(TAG, "Daily Adhan & Khatma alarm watchdog verified and rescheduled alarms.")
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Watchdog rescheduling encountered an error", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AdhanWatchdogWorker"
        const val UNIQUE_WORK_NAME = "emaniat_adhan_daily_watchdog"

        fun ensureScheduled(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<AdhanAlarmWatchdogWorker>(
                    24L, TimeUnit.HOURS,
                    3L, TimeUnit.HOURS
                )
                    .setConstraints(Constraints.NONE)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to enqueue daily alarm watchdog worker", t)
            }
        }
    }
}
