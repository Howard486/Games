package com.howardaistudio.subscriptionhub.domain.reminder

import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.money.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** De-duplication key for a reminder (SA FR-019). Only SENT reminders count as delivered. */
data class ReminderKey(
    val subscriptionId: String,
    val renewalDate: LocalDate,
    val daysBefore: Int,
)

data class ReminderPreferences(
    val enabled: Boolean,
    val daysBefore: Set<Int>,
    /** Preferred local time. A preference, not a delivery guarantee (SA FR-017). */
    val preferredTime: LocalTime,
) {
    init {
        require(daysBefore.all { it in 0..MAX_DAYS_BEFORE }) { "daysBefore out of range: $daysBefore" }
    }

    companion object {
        const val MAX_DAYS_BEFORE = 30
        val SELECTABLE_DAYS: List<Int> = listOf(7, 3, 1, 0)
        val DEFAULT = ReminderPreferences(enabled = true, daysBefore = setOf(7, 3, 1), preferredTime = LocalTime.of(9, 0))
    }
}

data class DueReminder(
    val key: ReminderKey,
    val subscriptionName: String,
    val amount: Money,
    /** Actual days left; may be smaller than [ReminderKey.daysBefore] when a scan ran late. */
    val daysUntil: Int,
)

/**
 * Decides which reminders a background scan should post. Pure function of its inputs, so it is
 * safe to run any number of times (SA §9.2).
 *
 * Rules:
 * - Only subscriptions that project charges and have reminders enabled (SA FR-022, BR-07).
 * - Nothing before the preferred local time of day.
 * - At most one reminder per renewal per scan: the smallest selected offset that is still
 *   `>= daysUntil`. A scan that missed D-3 and runs on D-2 posts one reminder keyed D-3 instead of
 *   replaying every missed offset (dev plan G4).
 * - A key already in [alreadySent] is skipped. Callers must pass only SENT keys, so a reminder
 *   skipped for lack of permission can still be delivered once permission is granted (dev plan G3).
 */
object ReminderPlanner {

    fun dueReminders(
        subscriptions: List<Subscription>,
        now: LocalDateTime,
        preferences: ReminderPreferences,
        alreadySent: Set<ReminderKey>,
    ): List<DueReminder> {
        if (!preferences.enabled || preferences.daysBefore.isEmpty()) return emptyList()
        if (now.toLocalTime().isBefore(preferences.preferredTime)) return emptyList()
        val today = now.toLocalDate()
        return subscriptions
            .filter { it.status.projectsCharges && it.reminderEnabled }
            .mapNotNull { sub ->
                val renewal = sub.schedule.firstOnOrAfter(today)
                val daysUntil = ChronoUnit.DAYS.between(today, renewal)
                val offset = preferences.daysBefore.filter { it >= daysUntil }.minOrNull() ?: return@mapNotNull null
                val key = ReminderKey(sub.id, renewal, offset)
                if (key in alreadySent) null else DueReminder(key, sub.name, sub.price, daysUntil.toInt())
            }
            .sortedWith(compareBy<DueReminder>({ it.key.renewalDate }, { it.subscriptionName }))
    }
}
