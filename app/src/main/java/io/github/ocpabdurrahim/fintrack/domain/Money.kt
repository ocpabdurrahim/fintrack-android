package io.github.ocpabdurrahim.fintrack.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** How digits are grouped: 1,234,567 or the South Asian 12,34,567. */
enum class Grouping { INTERNATIONAL, SOUTH_ASIAN }

/** Parsing and formatting of amounts held as Long minor units (1 unit = 1/100). */
object Money {
    private val positiveInput = Regex("^\\d{0,12}(\\.\\d{0,2})?$")
    private val signedInput = Regex("^-?\\d{0,12}(\\.\\d{0,2})?$")

    /** "1,250.5" -> 125050. Null when the text is not a usable amount. */
    fun parse(input: String, allowNegative: Boolean = false): Long? {
        val s = input.trim().replace(",", "")
        if (!(if (allowNegative) signedInput else positiveInput).matches(s)) return null
        val negative = s.startsWith("-")
        val body = s.removePrefix("-")
        if (body.isEmpty() || body == ".") return null
        val dot = body.indexOf('.')
        val whole = (if (dot >= 0) body.substring(0, dot) else body).ifEmpty { "0" }.toLong()
        val fraction = (if (dot >= 0) body.substring(dot + 1) else "").padEnd(2, '0').toLong()
        val value = whole * 100 + fraction
        return if (negative) -value else value
    }

    /**
     * Keyboard filter for amount fields: returns the cleaned text, or null when
     * the keystroke would make the field invalid (so it is ignored).
     */
    fun sanitize(raw: String, allowNegative: Boolean = false): String? {
        val s = raw.replace(",", "").replace(" ", "")
        return if ((if (allowNegative) signedInput else positiveInput).matches(s)) s else null
    }

    /** 125050 -> "1250.50", 125000 -> "1250": what an edit field starts with. */
    fun toInput(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val a = abs(minor)
        val fraction = a % 100
        return if (fraction == 0L) "$sign${a / 100}" else "$sign${a / 100}.${fraction.toString().padStart(2, '0')}"
    }

    /** 125050 -> "৳1,250.50"; whole amounts drop the decimals: "৳1,250". */
    fun format(minor: Long, symbol: String, grouping: Grouping, showPlus: Boolean = false): String {
        val a = abs(minor)
        val fraction = a % 100
        val digits = group((a / 100).toString(), grouping)
        val number = if (fraction == 0L) digits else "$digits.${fraction.toString().padStart(2, '0')}"
        val sign = when {
            minor < 0 -> "-"
            showPlus && minor > 0 -> "+"
            else -> ""
        }
        return "$sign${prefix(symbol)}$number"
    }

    /** Short labels for chart axes: ৳950, ৳12.5K, ৳3.2L, ৳1.1Cr / $4.2M. */
    fun compact(minor: Long, symbol: String, grouping: Grouping): String {
        val value = abs(minor) / 100.0
        val sign = if (minor < 0) "-" else ""
        val (divisor, suffix) = when {
            grouping == Grouping.SOUTH_ASIAN && value >= 1e7 -> 1e7 to "Cr"
            grouping == Grouping.SOUTH_ASIAN && value >= 1e5 -> 1e5 to "L"
            grouping == Grouping.INTERNATIONAL && value >= 1e9 -> 1e9 to "B"
            grouping == Grouping.INTERNATIONAL && value >= 1e6 -> 1e6 to "M"
            value >= 1e3 -> 1e3 to "K"
            else -> 1.0 to ""
        }
        val number = if (suffix.isEmpty()) value.roundToLong().toString() else oneDecimal(value / divisor)
        return "$sign${prefix(symbol)}$number$suffix"
    }

    fun group(digits: String, grouping: Grouping): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val size = if (grouping == Grouping.SOUTH_ASIAN) 2 else 3
        val groups = head.reversed().chunked(size).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + digits.takeLast(3)
    }

    /** Word-like symbols get a space ("Rs 500"); sign-like ones do not ("৳500"). */
    private fun prefix(symbol: String): String =
        if (symbol.isNotEmpty() && symbol.last().isLetter()) "$symbol " else symbol

    private fun oneDecimal(v: Double): String {
        val tenths = (v * 10).roundToLong()
        return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }
}
