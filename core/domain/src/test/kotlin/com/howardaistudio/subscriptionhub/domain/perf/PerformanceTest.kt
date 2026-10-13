package com.howardaistudio.subscriptionhub.domain.perf

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.forecast.Summaries
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.query.SortOrder
import com.howardaistudio.subscriptionhub.domain.query.SubscriptionFilter
import com.howardaistudio.subscriptionhub.domain.query.SubscriptionQuery
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPlanner
import com.howardaistudio.subscriptionhub.domain.reminder.ReminderPreferences
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.subscription
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proxy for SA NFR-03 / AT-30 (1,000 subscriptions). Device timing still needs a fixed reference
 * phone; this guards against accidental O(n^2) work in the shared calculations.
 */
class PerformanceTest {
    private val currencies = listOf("TWD", "USD", "JPY", "EUR")
    private val cycles = BillingCycle.entries
    private val statuses = listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL, SubscriptionStatus.PAUSED)

    private val subscriptions = (0 until 1_000).map { i ->
        val currency = currencies[i % currencies.size]
        subscription(
            id = "s$i",
            name = "Service ${i % 300}",
            amount = if (currency == "JPY") "${100 + i}" else "${1 + i % 50}.99",
            currency = currency,
            cycle = cycles[i % cycles.size],
            anchor = date("2025-01-01").plusDays((i * 7L) % 700).toString(),
            status = statuses[i % statuses.size],
            category = Category.entries[i % Category.entries.size],
        )
    }

    @Test
    fun `all screen calculations for 1000 subscriptions stay well under budget`() {
        val today = date("2026-10-10")
        fun allScreens() {
            Summaries.dashboard(subscriptions, today)
            Summaries.calendar(subscriptions, YearMonth.from(today))
            Summaries.report(subscriptions, YearMonth.from(today))
            SortOrder.entries.forEach { sort ->
                SubscriptionQuery.apply(subscriptions, SubscriptionFilter(sort = sort), today) { a, b -> a.compareTo(b) }
            }
            ReminderPlanner.dueReminders(subscriptions, LocalDateTime.of(2026, 10, 10, 10, 0), ReminderPreferences.DEFAULT, emptySet())
        }
        allScreens() // warm-up (class loading / JIT)

        val runs = 5
        val start = System.nanoTime()
        repeat(runs) { allScreens() }
        val averageMs = (System.nanoTime() - start) / 1_000_000.0 / runs

        assertTrue(averageMs < BUDGET_MS, "average ${"%.1f".format(averageMs)} ms exceeds $BUDGET_MS ms")
        assertEquals(1_000, Summaries.dashboard(subscriptions, today).totalCount)
    }

    private companion object {
        /** Generous JVM budget; the SA device target for the first dashboard draw is < 2 s. */
        const val BUDGET_MS = 500.0
    }
}
