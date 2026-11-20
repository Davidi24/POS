package com.saporini.mobile_desktop.core.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter

/** True inside screens where money is typed, taken or paid out: amounts show only as the restaurant charges them. */
val LocalRealMoney = compositionLocalOf { false }

/** Keeps every amount inside as the restaurant charges it (payments, price and wage fields, currency change, downloads). */
@Composable
fun RealMoney(content: @Composable () -> Unit) = CompositionLocalProvider(LocalRealMoney provides true, content = content)

/** The conversion the app's Text uses here: null inside [RealMoney] or with no other currency chosen. */
@Composable
fun shownMoney(): MoneyRates? = if (LocalRealMoney.current) null else ShownMoney.view

/**
 * Under a total where money is taken or paid out (user, 2026-11-19): "≈ $1,357.12, just to give you an idea. The payment
 * is in DOP." Shows nothing when no other currency is chosen on this device.
 */
@Composable
fun ApproxMoneyNote(cents: Long, currency: String?, modifier: Modifier = Modifier) {
    val rates = ShownMoney.view ?: return
    val from = currency?.trim()?.uppercase()?.takeIf { it.length == 3 } ?: ShownMoney.restaurantCurrency ?: return
    val approx = rates.convert(cents, from) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.CurrencyExchange, null, Modifier.size(14.dp), tint = Kit.Muted)
        Text("≈ ${shownMoneyText(approx, rates.to)}, just to give you an idea. The payment is in $from.",
            fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
    }
}

/** The currencies offered beside the language (user, 2026-11-19: dollars and euros first), without the restaurant's own. */
private val QuickCurrencies = listOf("USD", "EUR", "GBP", "CHF", "ALL", "DOP")

/** What can be picked to also see amounts in: the quick ones, and the one chosen on this device if it isn't among them. */
fun shownCurrencyChoices(): List<String> {
    val home = ShownMoney.restaurantCurrency
    val chosen = ShownMoney.chosen
    return (QuickCurrencies + listOfNotNull(chosen)).distinct().filter { it != home }
}

/** The rate in use, for the menus: "1 DOP = 0.0164 USD · 19 Nov"; null with nothing converted. */
fun shownRateLine(): String? {
    val rates = ShownMoney.view ?: return null
    val home = ShownMoney.restaurantCurrency ?: return null
    val one = rates.oneOf(home) ?: return null
    val number = rateText(one)
    val day = rates.date?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
        ?.let { com.saporini.mobile_desktop.core.format.shortDate(it) }
    return listOfNotNull("1 $home = $number ${rates.to}", day).joinToString(" · ")
}

/** A rate with four figures that matter, never in E-notation: 90.29, 1.099, 0.01642, 0.000061. */
private fun rateText(value: Double): String {
    if (value <= 0) return "0"
    val decimals = (4 - kotlin.math.floor(kotlin.math.log10(value)).toInt() - 1).coerceIn(2, 10)
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(value * factor).toLong()
    val fraction = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${scaled / factor}" else "${scaled / factor}.$fraction"
}

/**
 * How a currency is known to people (user, 2026-11-19: "show the special char when it has one"): "$", "€", "RD$", "zł";
 * the code when it has none ("CHF", "AED").
 */
fun shownSymbol(code: String): String = Symbols[code] ?: code

/** An amount with its currency's own sign, before or after as people write it: "RD$1,250.00", "1,250.00 L", "CHF 12.00". */
fun withSymbol(code: String, number: String, minus: String = ""): String {
    val symbol = shownSymbol(code)
    return when {
        code in SymbolAfter -> "$minus$number $symbol"
        symbol.any { !it.isLetter() } -> "$minus$symbol$number"
        else -> "$minus$symbol $number"
    }
}

/** [cents] of [code] with its own sign: "RD$82,600.00". */
fun shownMoneyText(cents: Long, code: String): String {
    val abs = kotlin.math.abs(cents)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val number = if (code in com.saporini.mobile_desktop.platform.ZERO_DECIMAL_CURRENCIES) whole else "$whole.${(abs % 100).toString().padStart(2, '0')}"
    return withSymbol(code, number, if (cents < 0) "-" else "")
}

private val Symbols = mapOf(
    "USD" to "$", "EUR" to "€", "GBP" to "£", "ALL" to "L", "DOP" to "RD$", "MKD" to "ден", "RSD" to "дин", "BAM" to "KM",
    "HUF" to "Ft", "CZK" to "Kč", "PLN" to "zł", "RON" to "lei", "BGN" to "лв", "TRY" to "₺", "SEK" to "kr", "NOK" to "kr",
    "DKK" to "kr", "ISK" to "kr", "UAH" to "₴", "MDL" to "L", "GEL" to "₾", "ILS" to "₪", "EGP" to "E£", "ZAR" to "R",
    "CAD" to "CA$", "MXN" to "MX$", "BRL" to "R$", "ARS" to "AR$", "AUD" to "A$", "NZD" to "NZ$", "JPY" to "¥", "CNY" to "¥",
    "HKD" to "HK$", "SGD" to "S$", "INR" to "₹", "THB" to "฿", "KRW" to "₩", "IDR" to "Rp"
)

/** Currencies whose sign goes after the amount: "1,250 L", "300 zł", "90 kr". */
private val SymbolAfter = setOf("ALL", "MDL", "MKD", "RSD", "BAM", "HUF", "CZK", "PLN", "RON", "BGN", "SEK", "NOK", "DKK", "ISK")
