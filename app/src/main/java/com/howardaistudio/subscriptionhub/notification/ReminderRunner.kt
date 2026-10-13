package com.howardaistudio.subscriptionhub.notification

import com.howardaistudio.subscriptionhub.domain.reminder.DueReminder
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPlanner
import com.howardaistudio.subscriptionhub.domain.repository.ReminderDeliveryStatus
import com.howardaistudio.subscriptionhub.domain.repository.ReminderLogRepository
import com.howardaistudio.subscriptionhub.domain.repository.SettingsRepository
import com.howardaistudio.subscriptionhub.domain.repository.SubscriptionRepository
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Posts reminder notifications; abstracted so the scan logic is testable without Android. */
interface ReminderNotifier {
    /** Runtime permission granted and notifications not blocked for the app (SA FR-020). */
    fun canNotify(): Boolean

    /** Returns false when the system refused to post. */
    fun show(reminder: DueReminder, today: LocalDate): Boolean

    /** Removes every posted reminder, e.g. after all data is cleared (dev plan G7). */
    fun cancelAll()
}

/**
 * One background reminder scan (SA §9.1–9.2). Safe to run any number of times: de-duplication
 * relies only on SENT log rows, so a repeat scan never re-posts (AT-19), and reminders skipped
 * for missing permission are delivered once permission is granted (dev plan G3).
 */
class ReminderRunner @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val logs: ReminderLogRepository,
    private val settings: SettingsRepository,
    private val timeSource: TimeSource,
    private val notifier: ReminderNotifier,
) {
    data class Outcome(val sent: Int, val skipped: Int, val failed: Int)

    suspend fun run(): Outcome {
        val localNow = timeSource.localNow()
        val today = localNow.toLocalDate()
        val now = timeSource.now()

        subscriptions.refreshDerivedState(today, now)
        logs.pruneBefore(today)

        val preferences = settings.settings.first().reminders
        val due = ReminderPlanner.dueReminders(subscriptions.getAll(), localNow, preferences, logs.sentKeys(today))
        if (due.isEmpty()) return Outcome(0, 0, 0)

        val canNotify = notifier.canNotify()
        var sent = 0
        var skipped = 0
        var failed = 0
        for (reminder in due) {
            val status = when {
                !canNotify -> ReminderDeliveryStatus.SKIPPED_PERMISSION.also { skipped++ }
                notifier.show(reminder, today) -> ReminderDeliveryStatus.SENT.also { sent++ }
                else -> ReminderDeliveryStatus.ERROR.also { failed++ }
            }
            // The subscription may have been deleted since the query; its log row is then moot.
            runCatching { logs.record(reminder.key, status, now) }
        }
        return Outcome(sent, skipped, failed)
    }
}
