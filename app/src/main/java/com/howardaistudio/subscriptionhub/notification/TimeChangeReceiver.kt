package com.howardaistudio.subscriptionhub.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-checks reminders when the device date, time or time zone changes (SA §9.2 item 6, dev plan
 * G8). These broadcasts are exempt from the implicit-broadcast limits for manifest receivers.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED ->
                ReminderScheduler.runNow(context)
        }
    }
}
