package com.localbudget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {
    @Test fun parsesInput() {
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(1200L, Money.parse("12"))
        assertEquals(5L, Money.parse("0.05"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-3"))
    }

    @Test fun convertsToInput() {
        assertEquals("12.5", Money.toInput(1250))
        assertEquals("12", Money.toInput(1200))
        assertEquals("12.05", Money.toInput(1205))
        assertEquals("0", Money.toInput(0))
    }

    @Test fun formats() {
        assertEquals("$12.50", Money.format(1250, "USD", locale = Locale.US))
        assertEquals("−$12.50", Money.format(-1250, "USD", locale = Locale.US))
        assertEquals("+$1,000.00", Money.format(100000, "USD", showPlus = true, locale = Locale.US))
        assertEquals("$1,250", Money.formatShort(125000, "USD", locale = Locale.US))
    }
}

class KeypadTest {
    private fun type(keys: String) = keys.fold("") { acc, c -> Keypad.press(acc, c) }

    @Test fun typesNumbers() {
        assertEquals("123", type("123"))
        assertEquals("12.34", type("12.345"))
        assertEquals("0.5", type(".5"))
        assertEquals("1.5", type("1..5"))
        assertEquals("5", type("05"))
        assertEquals("1", type("12<"))
        assertEquals("", type("<"))
        assertEquals("123456789", type("1234567890"))
    }

    @Test fun convertsToCents() {
        assertEquals(1234L, Keypad.toCents("12.34"))
        assertEquals(1200L, Keypad.toCents("12."))
        assertEquals(0L, Keypad.toCents(""))
    }
}
