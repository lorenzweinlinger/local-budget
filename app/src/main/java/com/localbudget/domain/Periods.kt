package com.localbudget.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A budget period, usually a calendar month (or e.g. 25th to 24th when paid on the 25th). */
data class Period(val start: LocalDate, val endInclusive: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Days remaining including [today], or 0 if the period is over. */
    fun daysLeft(today: LocalDate): Int = when {
        today.isAfter(endInclusive) -> 0
        today.isBefore(start) -> days
        else -> (ChronoUnit.DAYS.between(today, endInclusive) + 1).toInt()
    }

    /** Number of days elapsed including [today], clamped to the period. */
    fun daysElapsed(today: LocalDate): Int = when {
        today.isBefore(start) -> 0
        today.isAfter(endInclusive) -> days
        else -> (ChronoUnit.DAYS.between(start, today) + 1).toInt()
    }
}

object Periods {
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28

    fun containing(date: LocalDate, startDay: Int): Period {
        val day = startDay.coerceIn(MIN_START_DAY, MAX_START_DAY)
        val start = if (date.dayOfMonth >= day) date.withDayOfMonth(day) else date.minusMonths(1).withDayOfMonth(day)
        return Period(start, start.plusMonths(1).minusDays(1))
    }

    /** The period [offset] periods away from the one containing [date] (negative = past). */
    fun relative(date: LocalDate, startDay: Int, offset: Int): Period {
        val current = containing(date, startDay)
        return containing(current.start.plusMonths(offset.toLong()), startDay)
    }

    fun label(period: Period, locale: Locale = Locale.getDefault()): String {
        return if (period.start.dayOfMonth == 1) {
            period.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        } else {
            val startFmt = DateTimeFormatter.ofPattern("d MMM", locale)
            val endFmt = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
            "${period.start.format(startFmt)} – ${period.endInclusive.format(endFmt)}"
        }
    }

    fun shortLabel(period: Period, locale: Locale = Locale.getDefault()): String {
        // Name the period after the month containing most of its days.
        val mid = period.start.plusDays(period.days / 2L)
        return mid.format(DateTimeFormatter.ofPattern("MMM", locale))
    }
}
