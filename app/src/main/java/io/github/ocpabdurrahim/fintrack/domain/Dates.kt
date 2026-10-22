package io.github.ocpabdurrahim.fintrack.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date maths in the phone's time zone, and the date labels the UI shows. */
object Dates {
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val dayYearFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val fullFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val fileFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun monthStart(month: YearMonth, zone: ZoneId = zone()): Long =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end: the first moment of the next month. */
    fun monthEnd(month: YearMonth, zone: ZoneId = zone()): Long = monthStart(month.plusMonths(1), zone)

    fun dayStart(date: LocalDate, zone: ZoneId = zone()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun toLocalDate(millis: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toLocalTime(millis: Long, zone: ZoneId = zone()): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    fun toMillis(date: LocalDate, time: LocalTime, zone: ZoneId = zone()): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    fun month(month: YearMonth): String = month.format(monthFormat)
    fun shortMonth(month: YearMonth): String = month.format(shortMonthFormat)
    fun full(date: LocalDate): String = date.format(fullFormat)
    fun short(date: LocalDate): String = date.format(shortFormat)
    fun time(millis: Long): String = toLocalTime(millis).format(timeFormat)
    fun forFileName(date: LocalDate): String = date.format(fileFormat)

    /** "Today", "Yesterday", "Mon, 20 Oct", or with the year once it is not this year. */
    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayFormat) else date.format(dayYearFormat)
    }

    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> full(date)
    }
}
