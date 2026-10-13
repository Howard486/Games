package com.howardaistudio.subscriptionhub.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduling (SA FR-018, §9.2). An hourly unique periodic scan survives reboots;
 * data, settings and clock changes trigger an immediate one-off scan. Delivery time is best
 * effort: Doze and battery savers may delay it (SA §5.3), and the scan only posts after the
 * user's preferred time of day.
 */
object ReminderScheduler {
    private const val PERIODIC_WORK = "reminder-periodic"
    private const val IMMEDIATE_WORK = "reminder-now"
    private const val PERIOD_HOURS = 1L

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(PERIOD_HOURS, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
