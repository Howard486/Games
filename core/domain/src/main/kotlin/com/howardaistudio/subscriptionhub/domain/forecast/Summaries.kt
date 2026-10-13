package com.howardaistudio.subscriptionhub.domain.forecast

import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.model.SubscriptionStatus
import com.howardaistudio.subscriptionhub.domain.money.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** SA SCR-02 / FR-023: this month's estimate per currency plus the next 30 days of renewals. */
data class DashboardSummary(
    val today: LocalDate,
    val month: YearMonth,
    val monthTotals: List<Money>,
    val monthEventCount: Int,
    val upcoming: List<BillingEvent>,
    val upcomingDays: Long,
    val monthlyEquivalents: List<EstimatedAmount>,
    val activeCount: Int,
    val totalCount: Int,
)

/** SA SCR-06 / FR-024: projected charges of one month, grouped by day. */
data class CalendarMonth(
    val month: YearMonth,
    val eventsByDate: Map<LocalDate, List<BillingEvent>>,
    val totals: List<Money>,
)

/** SA SCR-07 / FR-025: month estimate, comparison estimates and category split, all per currency. */
data class MonthReport(
    val month: YearMonth,
    val monthTotals: List<Money>,
    val monthlyEquivalents: List<EstimatedAmount>,
    val yearlyEquivalents: List<EstimatedAmount>,
    val categoryBreakdown: List<CurrencyCategoryBreakdown>,
    val statusCounts: Map<SubscriptionStatus, Int>,
)

object Summaries {
    const val UPCOMING_DAYS = 30L

    fun dashboard(subscriptions: List<Subscription>, today: LocalDate, upcomingDays: Long = UPCOMING_DAYS): DashboardSummary {
        val month = YearMonth.from(today)
        val monthForecast = ForecastCalculator.forMonth(subscriptions, month)
        return DashboardSummary(
            today = today,
            month = month,
            monthTotals = monthForecast.totalsByCurrency,
            monthEventCount = monthForecast.events.size,
            upcoming = ForecastCalculator.upcoming(subscriptions, today, upcomingDays).events,
            upcomingDays = upcomingDays,
            monthlyEquivalents = ForecastCalculator.monthlyEquivalents(subscriptions),
            activeCount = subscriptions.count { it.status.projectsCharges },
            totalCount = subscriptions.size,
        )
    }

    fun calendar(subscriptions: List<Subscription>, month: YearMonth): CalendarMonth {
        val forecast = ForecastCalculator.forMonth(subscriptions, month)
        return CalendarMonth(month, forecast.events.groupBy { it.date }, forecast.totalsByCurrency)
    }

    fun report(subscriptions: List<Subscription>, month: YearMonth): MonthReport = MonthReport(
        month = month,
        monthTotals = ForecastCalculator.forMonth(subscriptions, month).totalsByCurrency,
        monthlyEquivalents = ForecastCalculator.monthlyEquivalents(subscriptions),
        yearlyEquivalents = ForecastCalculator.yearlyEquivalents(subscriptions),
        categoryBreakdown = ForecastCalculator.categoryBreakdown(subscriptions),
        statusCounts = SubscriptionStatus.entries.associateWith { status -> subscriptions.count { it.status == status } },
    )
}

/** Month grid for the calendar screen: whole weeks, with `null` for days outside [month]. */
object CalendarGrid {
    fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val leading = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
        val start = first.minusDays(leading.toLong())
        val cells = leading + month.lengthOfMonth()
        val weekCount = (cells + 6) / 7
        return (0 until weekCount).map { week ->
            (0 until 7).map { day ->
                val date = start.plusDays((week * 7 + day).toLong())
                date.takeIf { YearMonth.from(it) == month }
            }
        }
    }

    /** Days of week in display order starting at [firstDayOfWeek]. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }

    fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}
