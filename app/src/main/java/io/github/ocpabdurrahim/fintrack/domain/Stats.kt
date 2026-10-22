package io.github.ocpabdurrahim.fintrack.domain

import io.github.ocpabdurrahim.fintrack.data.Frequency
import io.github.ocpabdurrahim.fintrack.data.TransactionEntity
import io.github.ocpabdurrahim.fintrack.data.TxnType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Income and expense over a set of transactions. Transfers move money but are neither. */
data class Totals(val income: Long = 0, val expense: Long = 0, val count: Int = 0) {
    val net: Long get() = income - expense

    companion object {
        fun of(list: List<TransactionEntity>): Totals {
            var income = 0L
            var expense = 0L
            for (t in list) {
                when (t.type) {
                    TxnType.INCOME -> income += t.amount
                    TxnType.EXPENSE -> expense += t.amount
                    TxnType.TRANSFER -> Unit
                }
            }
            return Totals(income, expense, list.size)
        }
    }
}

data class CategoryShare(val categoryId: String?, val amount: Long, val fraction: Float)

object Stats {
    /** Per-category totals for one type, largest first, with each one's share of the whole. */
    fun byCategory(list: List<TransactionEntity>, type: TxnType): List<CategoryShare> {
        val matching = list.filter { it.type == type }
        val total = matching.sumOf { it.amount }
        if (total <= 0L) return emptyList()
        return matching.groupBy { it.categoryId }
            .map { (id, txns) ->
                val sum = txns.sumOf { it.amount }
                CategoryShare(id, sum, sum.toFloat() / total)
            }
            .sortedByDescending { it.amount }
    }

    /** One total per day of the month (index 0 is the 1st). */
    fun daily(list: List<TransactionEntity>, month: YearMonth, type: TxnType, zone: ZoneId): List<Long> {
        val days = LongArray(month.lengthOfMonth())
        for (t in list) {
            if (t.type != type) continue
            val date = Dates.toLocalDate(t.date, zone)
            if (YearMonth.from(date) == month) days[date.dayOfMonth - 1] += t.amount
        }
        return days.toList()
    }

    fun monthly(list: List<TransactionEntity>, months: List<YearMonth>, zone: ZoneId): List<Totals> {
        val grouped = list.groupBy { YearMonth.from(Dates.toLocalDate(it.date, zone)) }
        return months.map { Totals.of(grouped[it].orEmpty()) }
    }

    /** Days that count for an average: elapsed days this month, all days for a past month. */
    fun daysCounted(month: YearMonth, today: LocalDate): Int {
        val current = YearMonth.from(today)
        return when {
            month == current -> today.dayOfMonth
            month.isBefore(current) -> month.lengthOfMonth()
            else -> 0
        }
    }

    /** Rounded to whole units (taka, dollars): an average does not need paisa. */
    fun dailyAverage(total: Long, month: YearMonth, today: LocalDate): Long {
        val days = daysCounted(month, today)
        return if (days == 0) 0 else (total / days + 50) / 100 * 100
    }
}

object Recurrence {
    /** Epoch millis of occurrence [n] (0 is the start itself). */
    fun occurrence(start: Long, frequency: Frequency, n: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val base = Instant.ofEpochMilli(start).atZone(zone)
        val step = n.toLong()
        val next = when (frequency) {
            Frequency.DAILY -> base.plusDays(step)
            Frequency.WEEKLY -> base.plusWeeks(step)
            Frequency.MONTHLY -> base.plusMonths(step)
            Frequency.YEARLY -> base.plusYears(step)
        }
        return next.toInstant().toEpochMilli()
    }
}
