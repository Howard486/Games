package com.howardaistudio.subscriptionhub.domain.forecast

import com.howardaistudio.subscriptionhub.domain.model.Category
import com.howardaistudio.subscriptionhub.domain.model.Subscription
import com.howardaistudio.subscriptionhub.domain.money.Money
import com.howardaistudio.subscriptionhub.domain.money.totalsByCurrency
import com.howardaistudio.subscriptionhub.domain.schedule.BillingCycle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** One projected (estimated, not actual) charge. */
data class BillingEvent(
    val subscriptionId: String,
    val name: String,
    val category: Category,
    val date: LocalDate,
    val amount: Money,
)

/** Projected charges in `[startInclusive, endExclusive)` with per-currency totals (never mixed). */
data class PeriodForecast(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
    val events: List<BillingEvent>,
    val totalsByCurrency: List<Money>,
)

/** A comparison estimate such as a monthly equivalent; not a payment forecast for a given month. */
data class EstimatedAmount(val currencyCode: String, val value: BigDecimal)

data class CategoryShare(val category: Category, val monthlyEquivalent: BigDecimal, val share: BigDecimal)

/** Category split of monthly equivalents, computed separately for each currency (SA FR-025). */
data class CurrencyCategoryBreakdown(
    val currencyCode: String,
    val total: BigDecimal,
    val categories: List<CategoryShare>,
)

object ForecastCalculator {
    private const val WEEKS_PER_YEAR = 52
    private const val MONTHS_PER_YEAR = 12
    private const val SHARE_SCALE = 4

    /** Projected charges for subscriptions whose status projects charges (SA BR-07). */
    fun events(subscriptions: List<Subscription>, startInclusive: LocalDate, endExclusive: LocalDate): List<BillingEvent> =
        subscriptions
            .filter { it.status.projectsCharges }
            .flatMap { sub ->
                sub.schedule.occurrencesBetween(startInclusive, endExclusive).map { date ->
                    BillingEvent(sub.id, sub.name, sub.category, date, sub.price)
                }
            }
            .sortedWith(compareBy<BillingEvent>({ it.date }, { it.name }, { it.subscriptionId }))

    fun period(subscriptions: List<Subscription>, startInclusive: LocalDate, endExclusive: LocalDate): PeriodForecast {
        val events = events(subscriptions, startInclusive, endExclusive)
        return PeriodForecast(startInclusive, endExclusive, events, events.map { it.amount }.totalsByCurrency())
    }

    /** "Estimated payments this month": `[1st, 1st of next month)` (SA FR-009, BR-06). */
    fun forMonth(subscriptions: List<Subscription>, month: YearMonth): PeriodForecast =
        period(subscriptions, month.atDay(1), month.plusMonths(1).atDay(1))

    /** Upcoming charges from [today] through `today + days` inclusive (SA FR-023). */
    fun upcoming(subscriptions: List<Subscription>, today: LocalDate, days: Long = 30): PeriodForecast =
        period(subscriptions, today, today.plusDays(days + 1))

    /**
     * Average monthly burden per currency (SA FR-010): MONTHLY = amount, YEARLY = amount / 12,
     * WEEKLY = amount × 52 / 12. Rounded half-even to the currency's storage scale.
     */
    fun monthlyEquivalents(subscriptions: List<Subscription>): List<EstimatedAmount> =
        equivalentsByCurrency(subscriptions) { monthlyEquivalentUnrounded(it) }

    /** Annualised estimate per currency (SA §6.2): MONTHLY × 12, YEARLY × 1, WEEKLY × 52. */
    fun yearlyEquivalents(subscriptions: List<Subscription>): List<EstimatedAmount> =
        equivalentsByCurrency(subscriptions) { sub ->
            val amount = sub.price.toBigDecimal()
            when (sub.schedule.cycle) {
                BillingCycle.WEEKLY -> amount.multiply(BigDecimal(WEEKS_PER_YEAR))
                BillingCycle.MONTHLY -> amount.multiply(BigDecimal(MONTHS_PER_YEAR))
                BillingCycle.YEARLY -> amount
            }
        }

    /** Category shares of the monthly equivalent, per currency; shares never span currencies. */
    fun categoryBreakdown(subscriptions: List<Subscription>): List<CurrencyCategoryBreakdown> =
        subscriptions
            .filter { it.status.projectsCharges }
            .groupBy { it.price.currencyCode }
            .toSortedMap()
            .map { (currency, subs) ->
                val scale = subs.maxOf { it.price.scale }
                val byCategory = subs.groupBy { it.category }
                    .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { acc, s -> acc + monthlyEquivalentUnrounded(s) } }
                val total = byCategory.values.fold(BigDecimal.ZERO, BigDecimal::add)
                val shares = byCategory
                    .map { (category, value) ->
                        val share = if (total.signum() == 0) BigDecimal.ZERO else value.divide(total, SHARE_SCALE, RoundingMode.HALF_EVEN)
                        CategoryShare(category, value.setScale(scale, RoundingMode.HALF_EVEN), share)
                    }
                    .sortedWith(compareByDescending<CategoryShare> { it.monthlyEquivalent }.thenBy { it.category.ordinal })
                CurrencyCategoryBreakdown(currency, total.setScale(scale, RoundingMode.HALF_EVEN), shares)
            }

    private fun monthlyEquivalentUnrounded(sub: Subscription): BigDecimal {
        val amount = sub.price.toBigDecimal()
        // Keep generous precision until the final rounding so per-item rounding does not accumulate.
        val workingScale = sub.price.scale + 6
        return when (sub.schedule.cycle) {
            BillingCycle.MONTHLY -> amount
            BillingCycle.YEARLY -> amount.divide(BigDecimal(MONTHS_PER_YEAR), workingScale, RoundingMode.HALF_EVEN)
            BillingCycle.WEEKLY -> amount.multiply(BigDecimal(WEEKS_PER_YEAR))
                .divide(BigDecimal(MONTHS_PER_YEAR), workingScale, RoundingMode.HALF_EVEN)
        }
    }

    private inline fun equivalentsByCurrency(
        subscriptions: List<Subscription>,
        perItem: (Subscription) -> BigDecimal,
    ): List<EstimatedAmount> =
        subscriptions
            .filter { it.status.projectsCharges }
            .groupBy { it.price.currencyCode }
            .toSortedMap()
            .map { (currency, subs) ->
                val scale = subs.maxOf { it.price.scale }
                val sum = subs.fold(BigDecimal.ZERO) { acc, s -> acc + perItem(s) }
                EstimatedAmount(currency, sum.setScale(scale, RoundingMode.HALF_EVEN))
            }
}
