package com.howardaistudio.subscriptionhub.domain.forecast

import com.howardaistudio.subscriptionhub.domain.date
import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import com.howardaistudio.subscriptionhub.domain.subscription
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForecastCalculatorTest {
    private val october = YearMonth.of(2026, 10)

    // AT-09 / AT-10: yearly TWD 1200 renewing in November.
    @Test
    fun `yearly subscription counts only in its renewal month but has a monthly equivalent`() {
        val yearly = subscription(amount = "1200", currency = "TWD", cycle = BillingCycle.YEARLY, anchor = "2026-11-05")

        assertEquals(emptyList(), ForecastCalculator.forMonth(listOf(yearly), october).events)
        assertEquals(emptyList(), ForecastCalculator.forMonth(listOf(yearly), october).totalsByCurrency)
        assertEquals(listOf(money("1200", "TWD")), ForecastCalculator.forMonth(listOf(yearly), october.plusMonths(1)).totalsByCurrency)
        assertEquals(
            listOf(EstimatedAmount("TWD", BigDecimal("100.00"))),
            ForecastCalculator.monthlyEquivalents(listOf(yearly)),
        )
    }

    @Test
    fun `weekly monthly equivalent uses 52 over 12`() {
        val weekly = subscription(amount = "10.00", currency = "USD", cycle = BillingCycle.WEEKLY, anchor = "2026-10-01")
        assertEquals(listOf(EstimatedAmount("USD", BigDecimal("43.33"))), ForecastCalculator.monthlyEquivalents(listOf(weekly)))
        assertEquals(listOf(EstimatedAmount("USD", BigDecimal("520.00"))), ForecastCalculator.yearlyEquivalents(listOf(weekly)))
        // Actual events in a five-Thursday month: 5, not "4 weeks".
        assertEquals(5, ForecastCalculator.forMonth(listOf(weekly), october).events.size)
    }

    @Test
    fun `equivalents are summed before rounding`() {
        val subs = (1..3).map { subscription(id = "w$it", amount = "10.00", cycle = BillingCycle.WEEKLY, anchor = "2026-10-01") }
        // 3 x 43.333.. = 130.00, not 3 x 43.33 = 129.99
        assertEquals(listOf(EstimatedAmount("USD", BigDecimal("130.00"))), ForecastCalculator.monthlyEquivalents(subs))
    }

    // AT-14 / BR-08
    @Test
    fun `month totals are grouped per currency`() {
        val subs = listOf(
            subscription(id = "a", name = "Netflix", amount = "299", currency = "TWD", anchor = "2026-10-20", category = Category.VIDEO),
            subscription(id = "b", name = "ChatGPT Plus", amount = "20", currency = "USD", anchor = "2026-10-20"),
        )
        val forecast = ForecastCalculator.forMonth(subs, october)
        assertEquals(listOf(money("299", "TWD"), money("20", "USD")), forecast.totalsByCurrency)
        assertEquals(listOf("ChatGPT Plus", "Netflix"), forecast.events.map { it.name })
    }

    // AT-06 / BR-07
    @Test
    fun `paused cancelled and archived subscriptions project nothing`() {
        val subs = listOf(
            SubscriptionStatus.PAUSED,
            SubscriptionStatus.CANCELLED,
            SubscriptionStatus.ARCHIVED,
        ).mapIndexed { i, status -> subscription(id = "s$i", status = status, anchor = "2026-10-15") }

        assertEquals(emptyList(), ForecastCalculator.forMonth(subs, october).events)
        assertEquals(emptyList(), ForecastCalculator.monthlyEquivalents(subs))
        assertEquals(emptyList(), ForecastCalculator.categoryBreakdown(subs))
    }

    // BR-10: a trial counts from its first paid date.
    @Test
    fun `trial is projected from its first paid date`() {
        val trial = subscription(status = SubscriptionStatus.TRIAL, anchor = "2026-11-01")
        assertEquals(emptyList(), ForecastCalculator.forMonth(listOf(trial), october).events)
        assertEquals(listOf(date("2026-11-01")), ForecastCalculator.forMonth(listOf(trial), october.plusMonths(1)).events.map { it.date })
    }

    // AT-23: next 30 days, sorted by date.
    @Test
    fun `upcoming window is inclusive of day thirty`() {
        val subs = listOf(
            subscription(id = "late", name = "B", anchor = "2026-11-09"),
            subscription(id = "soon", name = "A", anchor = "2026-10-12"),
            subscription(id = "out", name = "C", anchor = "2026-11-10"),
        )
        val upcoming = ForecastCalculator.upcoming(subs, today = date("2026-10-10"), days = 30)
        assertEquals(listOf("soon", "late"), upcoming.events.map { it.subscriptionId })
    }

    // AT-24: several charges on one day are all listed.
    @Test
    fun `same day events are all listed`() {
        val subs = (1..3).map { subscription(id = "s$it", name = "Service $it", anchor = "2026-10-15") }
        val events = ForecastCalculator.forMonth(subs, october).events
        assertEquals(3, events.size)
        assertTrue(events.all { it.date == date("2026-10-15") })
    }

    // AT-25: category shares are computed per currency.
    @Test
    fun `category breakdown never spans currencies`() {
        val subs = listOf(
            subscription(id = "a", amount = "300", currency = "TWD", category = Category.VIDEO),
            subscription(id = "b", amount = "100", currency = "TWD", category = Category.AI),
            subscription(id = "c", amount = "20", currency = "USD", category = Category.AI),
        )
        val breakdown = ForecastCalculator.categoryBreakdown(subs)
        assertEquals(listOf("TWD", "USD"), breakdown.map { it.currencyCode })

        val twd = breakdown[0]
        assertEquals(BigDecimal("400.00"), twd.total)
        assertEquals(listOf(Category.VIDEO, Category.AI), twd.categories.map { it.category })
        assertEquals(listOf(BigDecimal("0.7500"), BigDecimal("0.2500")), twd.categories.map { it.share })

        assertEquals(BigDecimal("1.0000"), breakdown[1].categories.single().share)
    }
}
