package com.howardaistudio.subscriptionhub.domain.forecast

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.subscription
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummariesTest {
    private val today = date("2026-10-10")
    private val subs = listOf(
        subscription(id = "a", name = "ChatGPT Plus", amount = "20", currency = "USD", anchor = "2026-10-15"),
        subscription(id = "b", name = "Netflix", amount = "390", currency = "TWD", anchor = "2026-10-15", category = Category.VIDEO),
        subscription(id = "c", name = "iCloud", amount = "1200", currency = "TWD", cycle = BillingCycle.YEARLY, anchor = "2026-11-05", category = Category.CLOUD_STORAGE),
        subscription(id = "d", name = "Paused", amount = "99", currency = "TWD", anchor = "2026-10-20", status = SubscriptionStatus.PAUSED),
        subscription(id = "e", name = "Past", amount = "50", currency = "TWD", anchor = "2026-10-03"),
    )

    // AT-23 / AT-14
    @Test
    fun `dashboard separates currencies and lists the next 30 days`() {
        val summary = Summaries.dashboard(subs, today)

        // October: a (USD 20), b (TWD 390), e (TWD 50 on 10/03). Paused d excluded; yearly c is in November.
        assertEquals(listOf(money("440", "TWD"), money("20", "USD")), summary.monthTotals)
        assertEquals(3, summary.monthEventCount)
        // Upcoming from 10/10: a and b on 10/15, c on 11/05, e on 11/03. Past 10/03 excluded.
        assertEquals(listOf("a", "b", "e", "c"), summary.upcoming.map { it.subscriptionId })
        assertEquals(4, summary.activeCount)
        assertEquals(5, summary.totalCount)
        assertEquals(
            listOf(EstimatedAmount("TWD", BigDecimal("540.00")), EstimatedAmount("USD", BigDecimal("20.00"))),
            summary.monthlyEquivalents,
        )
    }

    // AT-24
    @Test
    fun `calendar groups same-day events`() {
        val october = Summaries.calendar(subs, YearMonth.of(2026, 10))
        assertEquals(setOf(date("2026-10-03"), date("2026-10-15")), october.eventsByDate.keys)
        assertEquals(listOf("a", "b"), october.eventsByDate.getValue(date("2026-10-15")).map { it.subscriptionId })
    }

    // AT-25
    @Test
    fun `report has per currency totals and status counts`() {
        val report = Summaries.report(subs, YearMonth.of(2026, 11))
        // November: a, b, e monthly plus yearly c.
        assertEquals(listOf(money("1640", "TWD"), money("20", "USD")), report.monthTotals)
        assertEquals(4, report.statusCounts[SubscriptionStatus.ACTIVE])
        assertEquals(1, report.statusCounts[SubscriptionStatus.PAUSED])
        assertEquals(0, report.statusCounts[SubscriptionStatus.ARCHIVED])
        assertEquals(listOf("TWD", "USD"), report.categoryBreakdown.map { it.currencyCode })
    }

    @Test
    fun `calendar grid pads whole weeks`() {
        // October 2026 starts on a Thursday.
        val sundayFirst = CalendarGrid.weeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(5, sundayFirst.size)
        assertTrue(sundayFirst.all { it.size == 7 })
        assertNull(sundayFirst[0][3])
        assertEquals(date("2026-10-01"), sundayFirst[0][4])
        assertEquals(date("2026-10-31"), sundayFirst[4][6])

        val mondayFirst = CalendarGrid.weeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(date("2026-10-01"), mondayFirst[0][3])

        // February 2026 starts on Sunday and has exactly four weeks.
        assertEquals(4, CalendarGrid.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY).size)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            CalendarGrid.weekdays(DayOfWeek.MONDAY),
        )
    }
}
