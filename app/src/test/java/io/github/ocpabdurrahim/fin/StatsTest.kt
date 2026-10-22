package io.github.ocpabdurrahim.fin

import io.github.ocpabdurrahim.fin.data.Frequency
import io.github.ocpabdurrahim.fin.data.TransactionEntity
import io.github.ocpabdurrahim.fin.data.TxnType
import io.github.ocpabdurrahim.fin.domain.Pin
import io.github.ocpabdurrahim.fin.domain.Recurrence
import io.github.ocpabdurrahim.fin.domain.Stats
import io.github.ocpabdurrahim.fin.domain.Totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class StatsTest {
    private val utc = ZoneOffset.UTC

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun txn(type: TxnType, amount: Long, category: Long?, date: Long, id: Long = 0) =
        TransactionEntity(id = id, type = type, amount = amount, categoryId = category, accountId = 1, toAccountId = if (type == TxnType.TRANSFER) 2 else null, date = date, createdAt = 0)

    @Test
    fun totalsIgnoreTransfers() {
        val list = listOf(
            txn(TxnType.INCOME, 1000, 10, at(2026, 10, 1)),
            txn(TxnType.EXPENSE, 300, 20, at(2026, 10, 2)),
            txn(TxnType.EXPENSE, 200, 21, at(2026, 10, 2)),
            txn(TxnType.TRANSFER, 5000, null, at(2026, 10, 3)),
        )
        val t = Totals.of(list)
        assertEquals(1000L, t.income)
        assertEquals(500L, t.expense)
        assertEquals(500L, t.net)
        assertEquals(4, t.count)
    }

    @Test
    fun byCategoryIsSortedWithShares() {
        val list = listOf(
            txn(TxnType.EXPENSE, 100, 1, at(2026, 10, 1)),
            txn(TxnType.EXPENSE, 300, 2, at(2026, 10, 1)),
            txn(TxnType.EXPENSE, 100, 1, at(2026, 10, 2)),
            txn(TxnType.INCOME, 9999, 3, at(2026, 10, 2)),
        )
        val shares = Stats.byCategory(list, TxnType.EXPENSE)
        assertEquals(listOf(2L, 1L), shares.map { it.categoryId })
        assertEquals(300L, shares[0].amount)
        assertEquals(0.6f, shares[0].fraction, 0.0001f)
        assertEquals(0.4f, shares[1].fraction, 0.0001f)
        assertTrue(Stats.byCategory(emptyList(), TxnType.EXPENSE).isEmpty())
    }

    @Test
    fun dailyTotalsCoverTheWholeMonth() {
        val list = listOf(
            txn(TxnType.EXPENSE, 100, 1, at(2026, 2, 1)),
            txn(TxnType.EXPENSE, 50, 1, at(2026, 2, 1, 20)),
            txn(TxnType.EXPENSE, 70, 1, at(2026, 2, 28)),
            txn(TxnType.EXPENSE, 999, 1, at(2026, 3, 1)),
            txn(TxnType.INCOME, 999, 1, at(2026, 2, 10)),
        )
        val days = Stats.daily(list, YearMonth.of(2026, 2), TxnType.EXPENSE, utc)
        assertEquals(28, days.size)
        assertEquals(150L, days[0])
        assertEquals(70L, days[27])
        assertEquals(220L, days.sum())
    }

    @Test
    fun monthlyGroupsByCalendarMonth() {
        val list = listOf(
            txn(TxnType.INCOME, 100, 1, at(2026, 9, 30, 23)),
            txn(TxnType.EXPENSE, 40, 1, at(2026, 10, 1, 0)),
        )
        val months = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        val totals = Stats.monthly(list, months, utc)
        assertEquals(100L, totals[0].income)
        assertEquals(0L, totals[0].expense)
        assertEquals(40L, totals[1].expense)
    }

    @Test
    fun dailyAverageUsesElapsedDays() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(100L, Stats.dailyAverage(1000, YearMonth.of(2026, 10), today))
        assertEquals(100L, Stats.dailyAverage(3000, YearMonth.of(2026, 9), today))
        assertEquals(0L, Stats.dailyAverage(3000, YearMonth.of(2026, 11), today))
    }

    @Test
    fun monthlyRecurrenceDoesNotDrift() {
        val start = at(2026, 1, 31)
        fun nth(n: Int) = LocalDateTime.ofEpochSecond(Recurrence.occurrence(start, Frequency.MONTHLY, n, utc) / 1000, 0, utc).toLocalDate()
        assertEquals(LocalDate.of(2026, 1, 31), nth(0))
        assertEquals(LocalDate.of(2026, 2, 28), nth(1))
        assertEquals(LocalDate.of(2026, 3, 31), nth(2))
        assertEquals(LocalDate.of(2026, 4, 30), nth(3))
    }

    @Test
    fun otherFrequencies() {
        val start = at(2024, 2, 29)
        fun date(f: Frequency, n: Int) = LocalDateTime.ofEpochSecond(Recurrence.occurrence(start, f, n, utc) / 1000, 0, utc).toLocalDate()
        assertEquals(LocalDate.of(2024, 3, 1), date(Frequency.DAILY, 1))
        assertEquals(LocalDate.of(2024, 3, 14), date(Frequency.WEEKLY, 2))
        assertEquals(LocalDate.of(2025, 2, 28), date(Frequency.YEARLY, 1))
        assertEquals(LocalDate.of(2028, 2, 29), date(Frequency.YEARLY, 4))
    }

    @Test
    fun pinHashing() {
        val stored = Pin.hash("1234")
        assertTrue(Pin.verify("1234", stored))
        assertFalse(Pin.verify("1235", stored))
        assertFalse(Pin.verify("1234", "garbage"))
        assertNotEquals(stored, Pin.hash("1234"))
    }
}
