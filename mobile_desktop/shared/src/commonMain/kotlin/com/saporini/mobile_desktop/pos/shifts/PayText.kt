package com.saporini.mobile_desktop.pos.shifts

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal

// Money in whole cents, so sums never go through floating point. The server sends decimal text like "12.50".
internal fun OrderDecimal?.toCents(): Long? = this?.value?.toCentsOrNull()

internal fun String.toCentsOrNull(): Long? {
    val text = trim()
    if (!Regex("-?\\d+(\\.\\d+)?").matches(text)) return null
    val negative = text.startsWith("-")
    val digits = text.removePrefix("-")
    val whole = digits.substringBefore('.').toLongOrNull() ?: return null
    val fraction = digits.substringAfter('.', "")
    val cents = (fraction + "000").take(3).toInt()
    // Half up on the third decimal.
    val rounded = whole * 100 + cents / 10 + if (cents % 10 >= 5) 1 else 0
    return if (negative) -rounded else rounded
}

internal fun currencySymbol(currency: String): String = when (currency) {
    "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; "CHF" -> "CHF "; else -> "$currency "
}

// "€1,234.50"
internal fun centsText(cents: Long, currency: String): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return "$sign${currencySymbol(currency)}$whole.${(abs % 100).toString().padStart(2, '0')}"
}

internal fun centsToDecimal(cents: Long): OrderDecimal {
    val abs = kotlin.math.abs(cents)
    return OrderDecimal("${if (cents < 0) "-" else ""}${abs / 100}.${(abs % 100).toString().padStart(2, '0')}")
}

// "7h 30m", "45m", "8h"
internal fun hoursText(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
