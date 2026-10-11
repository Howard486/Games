package com.howardaistudio.subscriptionhub.domain.reminder

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.subscription
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

class ReminderPlannerTest {
    private val prefs = ReminderPreferences.DEFAULT // D-7 / D-3 / D-1 at 09:00
    private val renewal = "2026-10-20"

    private fun at(day: String, time: String = "10:00") = LocalDateTime.of(date(day), LocalTime.parse(time))

    private fun plan(
        now: LocalDateTime,
        sent: Set<ReminderKey> = emptySet(),
        subs: List<com.howardaistudio.subscriptionhub.domain.model.Subscription> = listOf(subscription(anchor = renewal)),
        preferences: ReminderPreferences = prefs,
    ) = ReminderPlanner.dueReminders(subs, now, preferences, sent)

    // AT-16
    @Test
    fun `each selected offset produces its own reminder`() {
        val d7 = plan(at("2026-10-13")).single()
        assertEquals(ReminderKey("sub-1", date(renewal), 7), d7.key)
        assertEquals(7, d7.daysUntil)

        val d3 = plan(at("2026-10-17"), sent = setOf(d7.key)).single()
        assertEquals(3, d3.key.daysBefore)

        val d1 = plan(at("2026-10-19"), sent = setOf(d7.key, d3.key)).single()
        assertEquals(1, d1.key.daysBefore)
    }

    // AT-19: re-running the scan does not repeat a SENT reminder.
    @Test
    fun `already sent key is not repeated`() {
        val first = plan(at("2026-10-13")).single()
        assertEquals(emptyList(), plan(at("2026-10-13", "15:00"), sent = setOf(first.key)))
        // Between offsets nothing new is due.
        assertEquals(emptyList(), plan(at("2026-10-15"), sent = setOf(first.key)))
    }

    // Dev plan G4: a late scan sends one reminder, not every missed offset.
    @Test
    fun `missed offsets collapse into a single reminder`() {
        val late = plan(at("2026-10-18"))
        assertEquals(1, late.size)
        assertEquals(3, late.single().key.daysBefore)
        assertEquals(2, late.single().daysUntil)
    }

    // Dev plan G3: SKIPPED_PERMISSION is not passed as sent, so the reminder is still due later.
    @Test
    fun `reminder skipped for permission is still due once permission is granted`() {
        assertEquals(7, plan(at("2026-10-13"), sent = emptySet()).single().key.daysBefore)
    }

    @Test
    fun `nothing before preferred time of day`() {
        assertEquals(emptyList(), plan(at("2026-10-13", "08:59")))
        assertEquals(1, plan(at("2026-10-13", "09:00")).size)
    }

    @Test
    fun `day zero only when selected`() {
        val withoutZero = plan(at(renewal), sent = setOf(ReminderKey("sub-1", date(renewal), 1)))
        assertEquals(emptyList(), withoutZero)

        val withZero = prefs.copy(daysBefore = setOf(0, 1))
        val due = plan(at(renewal), sent = setOf(ReminderKey("sub-1", date(renewal), 1)), preferences = withZero)
        assertEquals(0, due.single().key.daysBefore)
    }

    @Test
    fun `next cycle gets fresh keys`() {
        val key = plan(at("2026-11-13")).single().key
        assertEquals(ReminderKey("sub-1", date("2026-11-20"), 7), key)
    }

    // AT-22 / FR-022
    @Test
    fun `no reminders for inactive subscriptions, disabled reminders, or global switch off`() {
        val subs = listOf(
            subscription(id = "paused", status = SubscriptionStatus.PAUSED, anchor = renewal),
            subscription(id = "cancelled", status = SubscriptionStatus.CANCELLED, anchor = renewal),
            subscription(id = "archived", status = SubscriptionStatus.ARCHIVED, anchor = renewal),
            subscription(id = "muted", reminderEnabled = false, anchor = renewal),
        )
        assertEquals(emptyList(), plan(at("2026-10-17"), subs = subs))
        assertEquals(emptyList(), plan(at("2026-10-17"), preferences = prefs.copy(enabled = false)))
        assertEquals(emptyList(), plan(at("2026-10-17"), preferences = prefs.copy(daysBefore = emptySet())))
    }

    @Test
    fun `trial reminds before the first paid date`() {
        val trial = subscription(status = SubscriptionStatus.TRIAL, anchor = "2026-11-01")
        val due = plan(at("2026-10-29"), subs = listOf(trial)).single()
        assertEquals(ReminderKey("sub-1", date("2026-11-01"), 3), due.key)
    }
}
