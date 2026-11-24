package com.localbudget.domain

import java.time.LocalDate

enum class Frequency(val code: Int) {
    WEEKLY(0), MONTHLY(1), YEARLY(2);

    companion object {
        fun of(code: Int): Frequency = entries.firstOrNull { it.code == code } ?: MONTHLY
    }
}

object Recurrence {

    /**
     * Date of the occurrence with the given [index] (0 = first).
     * Always computed from [start] so a bill on the 31st stays on the 31st
     * (or the last day of shorter months) instead of drifting.
     */
    fun occurrence(start: LocalDate, frequency: Frequency, every: Int, index: Int): LocalDate {
        val steps = every.coerceAtLeast(1).toLong() * index
        return when (frequency) {
            Frequency.WEEKLY -> start.plusWeeks(steps)
            Frequency.MONTHLY -> start.plusMonths(steps)
            Frequency.YEARLY -> start.plusYears(steps)
        }
    }

    /** Next date that has not been booked yet, or null if the series has ended. */
    fun nextDate(start: LocalDate, frequency: Frequency, every: Int, postedCount: Int, endDate: LocalDate?): LocalDate? {
        val next = occurrence(start, frequency, every, postedCount)
        return if (endDate != null && next.isAfter(endDate)) null else next
    }

    /** All not-yet-booked occurrences on or before [today]. */
    fun dueOccurrences(
        start: LocalDate,
        frequency: Frequency,
        every: Int,
        postedCount: Int,
        endDate: LocalDate?,
        today: LocalDate,
        max: Int = 500,
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var index = postedCount
        while (result.size < max) {
            val date = occurrence(start, frequency, every, index)
            if (date.isAfter(today) || (endDate != null && date.isAfter(endDate))) break
            result += date
            index++
        }
        return result
    }

    /** Approximate monthly cost of a recurring amount, used for the "fixed costs per month" summary. */
    fun monthlyEquivalent(amount: Long, frequency: Frequency, every: Int): Long {
        val n = every.coerceAtLeast(1)
        return when (frequency) {
            Frequency.WEEKLY -> Math.round(amount * 52.0 / 12.0 / n)
            Frequency.MONTHLY -> Math.round(amount.toDouble() / n)
            Frequency.YEARLY -> Math.round(amount.toDouble() / (12.0 * n))
        }
    }

    fun describe(frequency: Frequency, every: Int): String {
        val n = every.coerceAtLeast(1)
        return when (frequency) {
            Frequency.WEEKLY -> if (n == 1) "Weekly" else "Every $n weeks"
            Frequency.MONTHLY -> when (n) {
                1 -> "Monthly"
                3 -> "Quarterly"
                6 -> "Every 6 months"
                else -> "Every $n months"
            }
            Frequency.YEARLY -> if (n == 1) "Yearly" else "Every $n years"
        }
    }
}
