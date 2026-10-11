package com.howardaistudio.subscriptionhub.domain.schedule

import com.howardaistudio.subscriptionhub.domain.date
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingScheduleTest {

    private fun BillingSchedule.first(n: Int) = (0 until n).map { occurrence(it.toLong()).toString() }

    // AT-11 / BR-02: month-end anchor is re-applied every month, not carried from the clamped date.
    @Test
    fun `monthly anchor on 31st clamps per month without drifting`() {
        val schedule = BillingSchedule(BillingCycle.MONTHLY, date("2027-01-31"))
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31"),
            schedule.first(5),
        )
    }

    @Test
    fun `monthly anchor on 31st lands on 29 Feb in leap years`() {
        val schedule = BillingSchedule(BillingCycle.MONTHLY, date("2028-01-31"))
        assertEquals(listOf("2028-01-31", "2028-02-29", "2028-03-31"), schedule.first(3))
    }

    @Test
    fun `monthly anchors on 29th and 30th`() {
        assertEquals(
            listOf("2027-01-30", "2027-02-28", "2027-03-30"),
            BillingSchedule(BillingCycle.MONTHLY, date("2027-01-30")).first(3),
        )
        assertEquals(
            listOf("2027-01-29", "2027-02-28", "2027-03-29"),
            BillingSchedule(BillingCycle.MONTHLY, date("2027-01-29")).first(3),
        )
    }

    // AT-12 / BR-03
    @Test
    fun `yearly 29 Feb anchor`() {
        val schedule = BillingSchedule(BillingCycle.YEARLY, date("2024-02-29"))
        assertEquals(listOf("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"), schedule.first(5))
    }

    // BR-04
    @Test
    fun `weekly adds seven days across month and year boundaries`() {
        val schedule = BillingSchedule(BillingCycle.WEEKLY, date("2026-12-24"))
        assertEquals(listOf("2026-12-24", "2026-12-31", "2027-01-07"), schedule.first(3))
    }

    // Dev plan G2: user enters 28 Feb meaning "end of month".
    @Test
    fun `month-end intent from a short month keeps landing on the last day`() {
        val schedule = BillingSchedule.fromUserInput(BillingCycle.MONTHLY, date("2027-02-28"), monthEnd = true)
        assertEquals(31, schedule.billingDay)
        assertEquals(listOf("2027-02-28", "2027-03-31", "2027-04-30"), schedule.first(3))
    }

    @Test
    fun `without month-end intent the entered day is kept`() {
        val schedule = BillingSchedule.fromUserInput(BillingCycle.MONTHLY, date("2027-02-28"), monthEnd = false)
        assertEquals(listOf("2027-02-28", "2027-03-28"), schedule.first(2))
    }

    @Test
    fun `month-end flag is ignored for non candidates and weekly`() {
        assertEquals(15, BillingSchedule.fromUserInput(BillingCycle.MONTHLY, date("2027-02-15"), monthEnd = true).billingDay)
        assertEquals(28, BillingSchedule.fromUserInput(BillingCycle.WEEKLY, date("2027-02-28"), monthEnd = true).billingDay)
        assertTrue(BillingSchedule.isMonthEndCandidate(date("2027-04-30")))
        assertFalse(BillingSchedule.isMonthEndCandidate(date("2027-03-31")))
    }

    @Test
    fun `rejects inconsistent billing day`() {
        assertFailsWith<IllegalArgumentException> { BillingSchedule(BillingCycle.MONTHLY, date("2027-03-15"), 31) }
        assertFailsWith<IllegalArgumentException> { BillingSchedule(BillingCycle.WEEKLY, date("2027-02-28"), 31) }
        assertFailsWith<IllegalArgumentException> { BillingSchedule(BillingCycle.MONTHLY, date("2027-02-28"), 0) }
    }

    @Test
    fun `first on or after`() {
        val monthly = BillingSchedule(BillingCycle.MONTHLY, date("2027-01-31"))
        assertEquals(date("2027-01-31"), monthly.firstOnOrAfter(date("2026-06-01")))
        assertEquals(date("2027-01-31"), monthly.firstOnOrAfter(date("2027-01-31")))
        assertEquals(date("2027-02-28"), monthly.firstOnOrAfter(date("2027-02-01")))
        assertEquals(date("2027-03-31"), monthly.firstOnOrAfter(date("2027-03-01")))
        assertEquals(date("2029-02-28"), monthly.firstOnOrAfter(date("2029-02-28")))

        val weekly = BillingSchedule(BillingCycle.WEEKLY, date("2026-10-10"))
        assertEquals(date("2026-10-17"), weekly.firstOnOrAfter(date("2026-10-11")))
        assertEquals(date("2026-10-17"), weekly.firstOnOrAfter(date("2026-10-17")))

        val yearly = BillingSchedule(BillingCycle.YEARLY, date("2024-02-29"))
        assertEquals(date("2028-02-29"), yearly.firstOnOrAfter(date("2027-03-01")))
    }

    @Test
    fun `first on or after agrees with brute force over ten years`() {
        val schedules = listOf(
            BillingSchedule(BillingCycle.MONTHLY, date("2025-01-31")),
            BillingSchedule(BillingCycle.MONTHLY, date("2025-02-28"), 31),
            BillingSchedule(BillingCycle.YEARLY, date("2024-02-29")),
            BillingSchedule(BillingCycle.WEEKLY, date("2025-01-01")),
        )
        for (schedule in schedules) {
            val all = (0L until 600L).map(schedule::occurrence)
            var day = date("2024-12-01")
            while (day.isBefore(date("2034-12-31"))) {
                val expected = all.first { !it.isBefore(day) }
                assertEquals(expected, schedule.firstOnOrAfter(day), "$schedule at $day")
                day = day.plusDays(1)
            }
        }
    }

    // BR-06: a month report covers [1st, 1st of next month).
    @Test
    fun `occurrences between is half open`() {
        val weekly = BillingSchedule(BillingCycle.WEEKLY, date("2026-10-01"))
        assertEquals(
            listOf("2026-10-01", "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-29"),
            weekly.occurrencesBetween(date("2026-10-01"), date("2026-11-01")).map { it.toString() },
        )
        val monthly = BillingSchedule(BillingCycle.MONTHLY, date("2026-11-01"))
        assertEquals(emptyList(), monthly.occurrencesBetween(date("2026-10-01"), date("2026-11-01")))
    }

    @Test
    fun `is billing date`() {
        val schedule = BillingSchedule(BillingCycle.MONTHLY, date("2027-01-31"))
        assertTrue(schedule.isBillingDate(date("2027-02-28")))
        assertFalse(schedule.isBillingDate(date("2027-03-28")))
        assertFalse(schedule.isBillingDate(date("2026-12-31")))
    }
}
