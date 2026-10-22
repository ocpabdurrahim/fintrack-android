package io.github.ocpabdurrahim.fin

import io.github.ocpabdurrahim.fin.domain.Grouping
import io.github.ocpabdurrahim.fin.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesWholeAndDecimalAmounts() {
        assertEquals(125000L, Money.parse("1250"))
        assertEquals(125050L, Money.parse("1,250.5"))
        assertEquals(125005L, Money.parse("1250.05"))
        assertEquals(50L, Money.parse(".5"))
        assertEquals(100L, Money.parse(" 1. "))
        assertEquals(-2500L, Money.parse("-25", allowNegative = true))
    }

    @Test
    fun rejectsInvalidAmounts() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("."))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun sanitizeKeepsOnlyValidPartialInput() {
        assertEquals("12.3", Money.sanitize("12.3"))
        assertEquals("12", Money.sanitize("1,2"))
        assertEquals("", Money.sanitize(""))
        assertNull(Money.sanitize("12.345"))
        assertNull(Money.sanitize("12a"))
        assertEquals("-4", Money.sanitize("-4", allowNegative = true))
    }

    @Test
    fun toInputRoundTrips() {
        assertEquals("1250.50", Money.toInput(125050))
        assertEquals("1250", Money.toInput(125000))
        assertEquals("-3.05", Money.toInput(-305))
        assertEquals(125050L, Money.parse(Money.toInput(125050)))
    }

    @Test
    fun formatsSouthAsianGrouping() {
        assertEquals("৳1,250", Money.format(125000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳12,34,567.50", Money.format(123456750, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳1,00,00,000", Money.format(1_000_000_000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳999", Money.format(99900, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳0", Money.format(0, "৳", Grouping.SOUTH_ASIAN))
    }

    @Test
    fun formatsInternationalGrouping() {
        assertEquals("$1,234,567.50", Money.format(123456750, "$", Grouping.INTERNATIONAL))
        assertEquals("$10,000", Money.format(1_000_000, "$", Grouping.INTERNATIONAL))
    }

    @Test
    fun signsAndWordSymbols() {
        assertEquals("-৳250", Money.format(-25000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("+৳250", Money.format(25000, "৳", Grouping.SOUTH_ASIAN, showPlus = true))
        assertEquals("৳0", Money.format(0, "৳", Grouping.SOUTH_ASIAN, showPlus = true))
        assertEquals("Rs 500", Money.format(50000, "Rs", Grouping.SOUTH_ASIAN))
        assertEquals("-SAR 1,500", Money.format(-150000, "SAR", Grouping.INTERNATIONAL))
    }

    @Test
    fun compactLabels() {
        assertEquals("৳950", Money.compact(95000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳12.5K", Money.compact(1_250_000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳1.5L", Money.compact(15_000_000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("৳2Cr", Money.compact(2_000_000_000, "৳", Grouping.SOUTH_ASIAN))
        assertEquals("$2M", Money.compact(200_000_000, "$", Grouping.INTERNATIONAL))
        assertEquals("$150K", Money.compact(15_000_000, "$", Grouping.INTERNATIONAL))
    }
}
