package com.howardaistudio.subscriptionhub.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.howardaistudio.subscriptionhub.MainActivity
import com.howardaistudio.subscriptionhub.R
import com.howardaistudio.subscriptionhub.core.ui.UiFormat
import com.howardaistudio.subscriptionhub.domain.reminder.DueReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")

    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    override fun canNotify(): Boolean = notificationsAllowed(context)

    override fun show(reminder: DueReminder, today: LocalDate): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val key = reminder.key
        val notificationId = key.hashCode()
        val date = shortDate.format(key.renewalDate)
        val amount = UiFormat.money(reminder.amount)
        val text = if (reminder.daysUntil == 0) {
            context.getString(R.string.notification_text_today, date, amount)
        } else {
            context.getString(R.string.notification_text_days, reminder.daysUntil, date, amount)
        }

        val openDetail = PendingIntent.getActivity(
            context,
            notificationId,
            MainActivity.openSubscriptionIntent(context, key.subscriptionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Lock screen shows only a generic line (SA §9.3).
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.notification_public_title))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.notification_title, reminder.subscriptionName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openDetail)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(TAG, notificationId, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "renewal_reminders"
        private const val TAG = "renewal"

        fun notificationsAllowed(context: Context): Boolean {
            val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
