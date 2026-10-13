package com.howardaistudio.subscriptionhub.notification

import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.reminder.DueReminder
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderKey
import com.howardaistudio.subscriptionhub.domain.repository.ReminderDeliveryStatus
import com.howardaistudio.subscriptionhub.domain.repository.ReminderLogRepository
import com.howardaistudio.subscriptionhub.domain.settings.AppSettings
import com.howardaistudio.subscriptionhub.domain.time.TimeSource
import com.howardaistudio.subscriptionhub.feature.subscription.FakeSettingsRepository
import com.howardaistudio.subscriptionhub.feature.subscription.InMemorySubscriptionRepository
import com.howardaistudio.subscriptionhub.feature.subscription.testSubscription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Same rules as RoomReminderLogRepository: SENT is final, other statuses may be replaced. */
private class InMemoryReminderLogs : ReminderLogRepository {
    val rows = mutableMapOf<ReminderKey, ReminderDeliveryStatus>()

    override suspend fun sentKeys(fromDate: LocalDate): Set<ReminderKey> =
        rows.filter { (key, status) -> status == ReminderDeliveryStatus.SENT && !key.renewalDate.isBefore(fromDate) }.keys

    override suspend fun record(key: ReminderKey, status: ReminderDeliveryStatus, at: Instant) {
        if (rows[key] != ReminderDeliveryStatus.SENT) rows[key] = status
    }

    override suspend fun pruneBefore(date: LocalDate) {
        rows.keys.removeAll { it.renewalDate.isBefore(date) }
    }
}

private class FakeNotifier(var allowed: Boolean = true, var accept: Boolean = true) : ReminderNotifier {
    val shown = mutableListOf<DueReminder>()
    override fun canNotify() = allowed
    override fun show(reminder: DueReminder, today: LocalDate): Boolean {
        if (accept) shown += reminder
        return accept
    }
}

private class MutableTime(var instant: Instant) : TimeSource {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = ZoneId.of("Asia/Taipei")
}

class ReminderRunnerTest {
    // 2026-10-12 10:00 in Taipei: D-3 for a renewal on 10/15 (default offsets 7/3/1, 09:00).
    private val time = MutableTime(Instant.parse("2026-10-12T02:00:00Z"))
    private val subscriptions = InMemorySubscriptionRepository(listOf(testSubscription("s", anchor = "2026-10-15")))
    private val logs = InMemoryReminderLogs()
    private val notifier = FakeNotifier()
    private val runner = ReminderRunner(subscriptions, logs, FakeSettingsRepository(AppSettings()), time, notifier)

    // AT-16 / AT-19
    @Test
    fun `posts a due reminder once even when the scan repeats`() = runBlocking {
        assertEquals(ReminderRunner.Outcome(sent = 1, skipped = 0, failed = 0), runner.run())
        assertEquals(ReminderKey("s", LocalDate.of(2026, 10, 15), 3), notifier.shown.single().key)

        assertEquals(ReminderRunner.Outcome(0, 0, 0), runner.run())
        assertEquals(1, notifier.shown.size)
    }

    // AT-20 + dev plan G3
    @Test
    fun `without permission the reminder is skipped and delivered after permission is granted`() = runBlocking {
        notifier.allowed = false
        assertEquals(ReminderRunner.Outcome(sent = 0, skipped = 1, failed = 0), runner.run())
        assertTrue(notifier.shown.isEmpty())
        assertEquals(ReminderDeliveryStatus.SKIPPED_PERMISSION, logs.rows.values.single())

        notifier.allowed = true
        assertEquals(ReminderRunner.Outcome(sent = 1, skipped = 0, failed = 0), runner.run())
        assertEquals(ReminderDeliveryStatus.SENT, logs.rows.values.single())
    }

    @Test
    fun `a refused post is retried on the next scan`() = runBlocking {
        notifier.accept = false
        assertEquals(ReminderRunner.Outcome(sent = 0, skipped = 0, failed = 1), runner.run())
        notifier.accept = true
        assertEquals(ReminderRunner.Outcome(sent = 1, skipped = 0, failed = 0), runner.run())
    }

    // AT-22
    @Test
    fun `paused subscriptions and disabled reminders produce nothing`() = runBlocking {
        subscriptions.upsert(testSubscription("s", anchor = "2026-10-15", status = SubscriptionStatus.PAUSED))
        assertEquals(ReminderRunner.Outcome(0, 0, 0), runner.run())

        val off = ReminderRunner(subscriptions, logs, FakeSettingsRepository(AppSettings().let { it.copy(reminders = it.reminders.copy(enabled = false)) }), time, notifier)
        subscriptions.upsert(testSubscription("s", anchor = "2026-10-15"))
        assertEquals(ReminderRunner.Outcome(0, 0, 0), off.run())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `nothing is posted before the preferred time and old logs are pruned`() = runBlocking {
        logs.rows[ReminderKey("s", LocalDate.of(2026, 9, 15), 3)] = ReminderDeliveryStatus.SENT
        time.instant = Instant.parse("2026-10-12T00:30:00Z") // 08:30 in Taipei

        assertEquals(ReminderRunner.Outcome(0, 0, 0), runner.run())
        assertTrue(logs.rows.isEmpty())
    }
}
