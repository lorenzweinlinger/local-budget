package com.localbudget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun monthlyKeepsDayOfMonth() {
        val start = d("2026-01-31")
        assertEquals(d("2026-02-28"), Recurrence.occurrence(start, Frequency.MONTHLY, 1, 1))
        assertEquals(d("2026-03-31"), Recurrence.occurrence(start, Frequency.MONTHLY, 1, 2))
        assertEquals(d("2026-04-30"), Recurrence.occurrence(start, Frequency.MONTHLY, 3, 1))
    }

    @Test fun weeklyAndYearly() {
        assertEquals(d("2026-01-15"), Recurrence.occurrence(d("2026-01-01"), Frequency.WEEKLY, 2, 1))
        assertEquals(d("2028-02-29"), Recurrence.occurrence(d("2024-02-29"), Frequency.YEARLY, 4, 1))
    }

    @Test fun dueOccurrencesCatchUp() {
        val due = Recurrence.dueOccurrences(
            start = d("2026-08-01"), frequency = Frequency.MONTHLY, every = 1,
            postedCount = 1, endDate = null, today = d("2026-11-24"),
        )
        assertEquals(listOf(d("2026-09-01"), d("2026-10-01"), d("2026-11-01")), due)
    }

    @Test fun dueOccurrencesRespectEndDate() {
        val due = Recurrence.dueOccurrences(
            start = d("2026-08-01"), frequency = Frequency.MONTHLY, every = 1,
            postedCount = 0, endDate = d("2026-09-15"), today = d("2026-11-24"),
        )
        assertEquals(listOf(d("2026-08-01"), d("2026-09-01")), due)
        assertNull(Recurrence.nextDate(d("2026-08-01"), Frequency.MONTHLY, 1, 2, d("2026-09-15")))
    }

    @Test fun nothingDueInFuture() {
        assertTrue(
            Recurrence.dueOccurrences(d("2026-12-01"), Frequency.MONTHLY, 1, 0, null, d("2026-11-24")).isEmpty(),
        )
    }

    @Test fun monthlyEquivalent() {
        assertEquals(1000L, Recurrence.monthlyEquivalent(1000, Frequency.MONTHLY, 1))
        assertEquals(1000L, Recurrence.monthlyEquivalent(12000, Frequency.YEARLY, 1))
        assertEquals(4333L, Recurrence.monthlyEquivalent(1000, Frequency.WEEKLY, 1))
        assertEquals(1000L, Recurrence.monthlyEquivalent(3000, Frequency.MONTHLY, 3))
        assertEquals("Quarterly", Recurrence.describe(Frequency.MONTHLY, 3))
        assertEquals("Every 2 weeks", Recurrence.describe(Frequency.WEEKLY, 2))
    }
}
