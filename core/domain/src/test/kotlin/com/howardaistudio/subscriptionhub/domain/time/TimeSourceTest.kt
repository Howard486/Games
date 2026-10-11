package com.howardaistudio.subscriptionhub.domain.time

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

class TimeSourceTest {
    private class FakeTimeSource(var instant: Instant, var zoneId: ZoneId) : TimeSource {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = zoneId
    }

    // AT-15: switching Taipei -> London changes "today" only through the clock, never stored dates.
    @Test
    fun `today follows the current zone on every call`() {
        val time = FakeTimeSource(Instant.parse("2026-10-14T20:00:00Z"), ZoneId.of("Asia/Taipei"))
        assertEquals("2026-10-15", time.today().toString())
        time.zoneId = ZoneId.of("Europe/London")
        assertEquals("2026-10-14", time.today().toString())
        assertEquals("2026-10-14T21:00", time.localNow().toString())
    }
}
