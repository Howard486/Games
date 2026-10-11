package com.howardaistudio.subscriptionhub.domain.schedule

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class BillingCycle { WEEKLY, MONTHLY, YEARLY }

/**
 * Recurring billing dates computed from a fixed anchor (SA FR-011, FR-012, BR-02..BR-04).
 *
 * Occurrence `n` is always derived from [anchorDate] plus `n` periods, never from the previous
 * (possibly month-end clamped) occurrence, so 1/31 yields 2/28 then 3/31 rather than 3/28.
 *
 * [billingDay] is the intended day of month for MONTHLY / YEARLY. It equals the anchor's day,
 * except when the anchor is the last day of a short month and the user confirmed a month-end
 * intent: anchor 2027-02-28 with billingDay 31 continues 3/31, 4/30 (dev plan G2). WEEKLY ignores
 * the day of month and must keep `billingDay == anchorDate.dayOfMonth`.
 *
 * All dates are user-perceived [LocalDate]s, never derived from UTC timestamps (SA FR-015).
 */
data class BillingSchedule(
    val cycle: BillingCycle,
    val anchorDate: LocalDate,
    val billingDay: Int = anchorDate.dayOfMonth,
) {
    init {
        require(billingDay in 1..31) { "billingDay out of range: $billingDay" }
        val anchorDay = anchorDate.dayOfMonth
        if (cycle == BillingCycle.WEEKLY) {
            require(billingDay == anchorDay) { "WEEKLY schedules cannot override the billing day" }
        } else {
            require(billingDay == anchorDay || (anchorDay == anchorDate.lengthOfMonth() && billingDay > anchorDay)) {
                "billingDay $billingDay is inconsistent with anchor $anchorDate"
            }
        }
    }

    /** The [index]-th billing date (0 = [anchorDate]). */
    fun occurrence(index: Long): LocalDate {
        require(index >= 0) { "index must be >= 0" }
        return when (cycle) {
            BillingCycle.WEEKLY -> anchorDate.plusWeeks(index)
            BillingCycle.MONTHLY -> dayIn(YearMonth.from(anchorDate).plusMonths(index))
            BillingCycle.YEARLY -> dayIn(YearMonth.from(anchorDate).plusYears(index))
        }
    }

    /** Index of the first occurrence on or after [date]. */
    fun firstIndexOnOrAfter(date: LocalDate): Long {
        if (!date.isAfter(anchorDate)) return 0
        // Start from an index whose occurrence is known to be before [date], then step forward.
        // Occurrences are strictly increasing, so the loop runs at most a couple of times.
        var index = when (cycle) {
            BillingCycle.WEEKLY -> ChronoUnit.DAYS.between(anchorDate, date) / 7
            BillingCycle.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(anchorDate), YearMonth.from(date)) - 1
            BillingCycle.YEARLY -> ChronoUnit.YEARS.between(YearMonth.from(anchorDate), YearMonth.from(date)) - 1
        }.coerceAtLeast(0)
        while (occurrence(index).isBefore(date)) index++
        return index
    }

    /** The next billing date on or after [date]; equals [anchorDate] while [date] is not after it. */
    fun firstOnOrAfter(date: LocalDate): LocalDate = occurrence(firstIndexOnOrAfter(date))

    /** Billing dates in `[startInclusive, endExclusive)`, ascending. */
    fun occurrencesBetween(startInclusive: LocalDate, endExclusive: LocalDate): List<LocalDate> {
        if (!startInclusive.isBefore(endExclusive)) return emptyList()
        val result = ArrayList<LocalDate>()
        var index = firstIndexOnOrAfter(startInclusive)
        while (true) {
            val date = occurrence(index)
            if (!date.isBefore(endExclusive)) break
            result += date
            index++
        }
        return result
    }

    /** Whether [date] is one of this schedule's billing dates. */
    fun isBillingDate(date: LocalDate): Boolean = !date.isBefore(anchorDate) && firstOnOrAfter(date) == date

    private fun dayIn(month: YearMonth): LocalDate = month.atDay(minOf(billingDay, month.lengthOfMonth()))

    companion object {
        /**
         * Builds a schedule from the "next estimated billing date" entered in the form (SA §6.1:
         * the MVP form stores it as the anchor). [monthEnd] applies only to month-end candidates.
         */
        fun fromUserInput(cycle: BillingCycle, nextBillingDate: LocalDate, monthEnd: Boolean): BillingSchedule {
            val day = if (monthEnd && cycle != BillingCycle.WEEKLY && isMonthEndCandidate(nextBillingDate)) {
                31
            } else {
                nextBillingDate.dayOfMonth
            }
            return BillingSchedule(cycle, nextBillingDate, day)
        }

        /**
         * True when [date] is the last day of a month shorter than 31 days, i.e. the form should ask
         * "bill on the last day of every month?" before saving.
         */
        fun isMonthEndCandidate(date: LocalDate): Boolean =
            date.dayOfMonth == date.lengthOfMonth() && date.dayOfMonth < 31
    }
}
