package com.localbudget.data

import com.localbudget.domain.Frequency
import com.localbudget.domain.Recurrence
import java.time.LocalDate

enum class EntryType(val code: Int) {
    EXPENSE(0), INCOME(1);

    companion object {
        fun of(code: Int): EntryType = if (code == INCOME.code) INCOME else EXPENSE
    }
}

data class Category(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Int,
    val type: EntryType,
    /** Optional monthly limit in cents. */
    val budget: Long? = null,
    val sortOrder: Int = 0,
)

/** A single income or expense booking. */
data class Entry(
    val id: Long = 0,
    val type: EntryType,
    val amount: Long,
    val categoryId: Long?,
    val note: String,
    val date: LocalDate,
    val recurringId: Long? = null,
    val category: Category? = null,
)

/** A repeating income or expense (rent, subscriptions, salary, …). */
data class Recurring(
    val id: Long = 0,
    val name: String,
    val type: EntryType,
    val amount: Long,
    val categoryId: Long?,
    val frequency: Frequency,
    val every: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    /** How many occurrences have already been booked as entries. */
    val postedCount: Int,
    val active: Boolean,
    val category: Category? = null,
) {
    val nextDate: LocalDate? get() = Recurrence.nextDate(startDate, frequency, every, postedCount, endDate)
    val monthlyEquivalent: Long get() = Recurrence.monthlyEquivalent(amount, frequency, every)
}

data class Totals(val income: Long, val expense: Long) {
    val balance: Long get() = income - expense
}

data class CategoryAmount(val category: Category?, val amount: Long)
