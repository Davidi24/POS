package com.saporini.mobile_desktop.core.format

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// How the newer screens write money, quantities, times and server codes for people. Money stays in whole cents so
// nothing goes through floating point.

/** "12.505" → 1251 cents (half up); null when it isn't a number. */
internal fun OrderDecimal?.centsOrNull(): Long? = this?.value?.let(::decimalTextToCents)

internal fun decimalTextToCents(raw: String): Long? {
    val text = raw.trim()
    if (!Regex("-?\\d+(\\.\\d+)?").matches(text)) return null
    val negative = text.startsWith("-")
    val digits = text.removePrefix("-")
    val whole = digits.substringBefore('.').toLongOrNull() ?: return null
    val fraction = (digits.substringAfter('.', "") + "000").take(3).toInt()
    val rounded = whole * 100 + fraction / 10 + if (fraction % 10 >= 5) 1 else 0
    return if (negative) -rounded else rounded
}

internal fun currencySign(currency: String?): String = when (currency) {
    null, "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; "CHF" -> "CHF "; else -> "$currency "
}

/** 123450 → "€1,234.50". */
internal fun moneyText(cents: Long, currency: String?): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return "$sign${currencySign(currency)}$whole.${(abs % 100).toString().padStart(2, '0')}"
}

/** Decimal text from the server as money; "–" when missing. */
internal fun money(value: OrderDecimal?, currency: String?): String = value.centsOrNull()?.let { moneyText(it, currency) } ?: "–"

/** Short money for chart labels: "€950", "€1.2k". */
internal fun shortMoney(cents: Long, currency: String?): String {
    val whole = kotlin.math.abs(cents) / 100
    val sign = if (cents < 0) "-" else ""
    return when {
        whole >= 1_000_000 -> "$sign${currencySign(currency).trim()}${whole / 1_000_000}.${(whole % 1_000_000) / 100_000}M"
        whole >= 1_000 -> "$sign${currencySign(currency).trim()}${whole / 1000}.${(whole % 1000) / 100}k"
        else -> "$sign${currencySign(currency).trim()}$whole"
    }
}

/** Decimal text with trailing zeros removed: "2.500" → "2.5", "3.000" → "3". */
internal fun plainNumber(value: OrderDecimal?): String {
    val text = value?.value ?: return "–"
    if (!text.contains('.')) return text
    return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

/** A stock quantity with its unit: "2.5 kg", "12 each". */
internal fun quantityText(value: OrderDecimal?, unit: String?): String = "${plainNumber(value)} ${unitShort(unit)}".trim()

internal fun unitShort(unit: String?): String = when (unit) {
    "EACH" -> "pcs"; "GRAM" -> "g"; "KILOGRAM" -> "kg"; "MILLILITER" -> "ml"; "LITER" -> "l"; "OUNCE" -> "oz"; "POUND" -> "lb"
    "CUP" -> "cup"; "TABLESPOON" -> "tbsp"; "TEASPOON" -> "tsp"; "PORTION" -> "portion"; "CASE" -> "case"; "BOTTLE" -> "bottle"
    "PACK" -> "pack"; "TRAY" -> "tray"; null -> ""; else -> humanize(unit).lowercase()
}

/** "PREP_BATCH" → "Prep batch", "KDS" stays "KDS". */
internal fun humanize(code: String?): String {
    if (code.isNullOrBlank()) return ""
    if (code.length <= 3 && code.all { it.isUpperCase() }) return code
    return code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun instantOrNull(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun localOf(text: String?, zone: TimeZone): LocalDateTime? = instantOrNull(text)?.toLocalDateTime(zone)

/** "14:05". */
internal fun timeText(text: String?, zone: TimeZone): String = localOf(text, zone)?.let { "${two(it.hour)}:${two(it.minute)}" } ?: "–"

/** "6 Oct, 14:05" (or "Today, 14:05"). */
internal fun dateTimeText(text: String?, zone: TimeZone, today: LocalDate? = null): String {
    val local = localOf(text, zone) ?: return "–"
    val day = if (today != null && local.date == today) "Today" else shortDate(local.date)
    return "$day, ${two(local.hour)}:${two(local.minute)}"
}

/** "6 Oct 2026" without the year when [withYear] is false. */
internal fun shortDate(date: LocalDate, withYear: Boolean = false): String {
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return if (withYear) "${date.day} $month ${date.year}" else "${date.day} $month"
}

/** "Mon 6 Oct". */
internal fun weekdayDate(date: LocalDate): String =
    "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${shortDate(date)}"

/** Minutes between [from] and [now], never negative. */
internal fun minutesSince(from: Instant?, now: Instant): Long = from?.let { ((now - it).inWholeMinutes).coerceAtLeast(0) } ?: 0

/** "now", "4 min", "1 h 05", for how long something has been waiting. */
internal fun ageText(minutes: Long): String = when {
    minutes < 1 -> "now"
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h ${two((minutes % 60).toInt())}"
}

/** "3 min ago", "2 h ago", "yesterday", or the date. */
internal fun agoText(text: String?, now: Instant, zone: TimeZone): String {
    val at = instantOrNull(text) ?: return "–"
    val minutes = (now - at).inWholeMinutes
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "yesterday"
        else -> shortDate(at.toLocalDateTime(zone).date)
    }
}

private fun two(value: Int): String = value.toString().padStart(2, '0')

/** "1 dish" / "3 dishes". */
internal fun plural(count: Number, one: String, many: String = one + "s"): String = "$count ${if (count.toLong() == 1L) one else many}"
