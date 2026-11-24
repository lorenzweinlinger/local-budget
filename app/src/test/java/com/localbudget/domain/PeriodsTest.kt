package com.localbudget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PeriodsTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun calendarMonth() {
        val p = Periods.containing(d("2026-02-14"), 1)
        assertEquals(d("2026-02-01"), p.start)
        assertEquals(d("2026-02-28"), p.endInclusive)
        assertEquals(28, p.days)
        assertEquals("February 2026", Periods.label(p, Locale.US))
    }

    @Test fun paydayPeriod() {
        val before = Periods.containing(d("2026-11-10"), 25)
        assertEquals(d("2026-10-25"), before.start)
        assertEquals(d("2026-11-24"), before.endInclusive)
        val on = Periods.containing(d("2026-11-25"), 25)
        assertEquals(d("2026-11-25"), on.start)
        assertEquals(d("2026-12-24"), on.endInclusive)
        assertEquals("25 Oct – 24 Nov 2026", Periods.label(before, Locale.UK))
    }

    @Test fun relativePeriods() {
        val p = Periods.relative(d("2026-01-15"), 1, -1)
        assertEquals(d("2025-12-01"), p.start)
        assertEquals(d("2025-12-31"), p.endInclusive)
        val q = Periods.relative(d("2026-01-15"), 1, 2)
        assertEquals(d("2026-03-01"), q.start)
    }

    @Test fun daysLeftAndElapsed() {
        val p = Periods.containing(d("2026-11-24"), 1)
        assertEquals(7, p.daysLeft(d("2026-11-24")))
        assertEquals(24, p.daysElapsed(d("2026-11-24")))
        assertEquals(30, p.daysElapsed(d("2026-12-05")))
        assertEquals(0, p.daysLeft(d("2026-12-05")))
        assertEquals(0, p.daysElapsed(d("2026-10-05")))
        assertTrue(d("2026-11-30") in p)
        assertFalse(d("2026-12-01") in p)
    }
}
