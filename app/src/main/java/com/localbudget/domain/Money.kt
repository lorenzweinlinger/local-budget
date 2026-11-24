package com.localbudget.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * All amounts are stored as [Long] in hundredths of the currency unit ("cents"),
 * which avoids floating point rounding problems.
 */
object Money {

    fun format(
        cents: Long,
        currencyCode: String,
        showPlus: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        runCatching { formatter.currency = Currency.getInstance(currencyCode) }
        val text = formatter.format(BigDecimal.valueOf(abs(cents)).movePointLeft(2))
        return when {
            cents < 0 -> "−$text"
            showPlus && cents > 0 -> "+$text"
            else -> text
        }
    }

    /** Compact version without decimals for chart labels, e.g. "€1,250". */
    fun formatShort(cents: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        runCatching { formatter.currency = Currency.getInstance(currencyCode) }
        formatter.maximumFractionDigits = 0
        formatter.minimumFractionDigits = 0
        val text = formatter.format(BigDecimal.valueOf(abs(cents)).movePointLeft(2))
        return if (cents < 0) "−$text" else text
    }

    fun symbol(currencyCode: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(currencyCode).getSymbol(locale) }.getOrDefault(currencyCode)

    /** Parses user input like "12", "12.5", "12,50" into cents. Returns null for invalid input. */
    fun parse(text: String): Long? {
        val normalized = text.trim().replace(',', '.').replace(" ", "")
        if (normalized.isEmpty()) return null
        return runCatching {
            BigDecimal(normalized).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()?.takeIf { it >= 0 }
    }

    /** Converts cents to a plain editable string: 1250 -> "12.5", 1200 -> "12", 1205 -> "12.05". */
    fun toInput(cents: Long): String {
        val value = BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros()
        return value.toPlainString()
    }
}

/** Pure state machine for the on-screen number pad. */
object Keypad {
    const val BACKSPACE = '<'
    const val DECIMAL = '.'
    private const val MAX_INTEGER_DIGITS = 9

    fun press(current: String, key: Char): String {
        return when (key) {
            BACKSPACE -> current.dropLast(1)
            DECIMAL -> when {
                current.contains(DECIMAL) -> current
                current.isEmpty() -> "0."
                else -> current + DECIMAL
            }
            in '0'..'9' -> {
                val dot = current.indexOf(DECIMAL)
                when {
                    dot >= 0 && current.length - dot > 2 -> current // max 2 decimals
                    dot < 0 && current == "0" -> key.toString() // no leading zeros
                    dot < 0 && current.length >= MAX_INTEGER_DIGITS -> current
                    else -> current + key
                }
            }
            else -> current
        }
    }

    fun toCents(input: String): Long = Money.parse(input.trimEnd(DECIMAL)) ?: 0L
}
