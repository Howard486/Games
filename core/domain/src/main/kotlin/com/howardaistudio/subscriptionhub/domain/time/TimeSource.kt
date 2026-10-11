package com.howardaistudio.subscriptionhub.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Current time for business rules. The zone is resolved on every call, so a device time-zone
 * change (SA AT-15) takes effect immediately instead of being frozen at injection time, as a
 * `Clock.systemDefaultZone()` singleton would be.
 */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId

    /** The user's current calendar date; billing dates are compared against this (SA FR-015). */
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()

    fun localNow(): LocalDateTime = now().atZone(zone()).toLocalDateTime()
}

object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
