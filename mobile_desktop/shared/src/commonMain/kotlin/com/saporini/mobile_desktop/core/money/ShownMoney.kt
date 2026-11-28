package com.saporini.mobile_desktop.core.money

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saporini.mobile_desktop.core.session.DeviceCurrency
import com.saporini.mobile_desktop.platform.CURRENCIES
import com.saporini.mobile_desktop.platform.ZERO_DECIMAL_CURRENCIES
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Amounts also seen in a currency the person knows (user, 2026-11-19: "just to see around how much that is in dollars;
 * all the payments stay in the restaurant's currency"). Chosen on each device beside the language. Every amount the app
 * writes ("€1,234.50", "DOP 82,600.00", "€1.2k") then shows as "≈ $1,357.12" ([show], used by the app's Text and the
 * charts). Screens where money is typed or taken keep the real amounts ([RealMoney]), and downloads never change.
 *
 * Values are snapshot state, so a screen that reads them redraws when the choice or the rates change.
 */
object ShownMoney {
    /** The currency chosen on this device, or null to see amounts only as the restaurant charges them. */
    var chosen: String? by mutableStateOf(DeviceCurrency.load())
        private set

    /** The restaurant's own currency (from the server), for the menu's prices and the payment notes. */
    var restaurantCurrency: String? by mutableStateOf(null)
        private set

    /** Today's rates into [chosen]; null while there are none (nothing is converted then). */
    var rates: MoneyRates? by mutableStateOf(null)
        private set

    /** True when a currency is chosen but no rate could be had. */
    var unavailable: Boolean by mutableStateOf(false)
        private set

    /** True for the moment a card's picture is taken for a download, so the picture has the real amounts. */
    var capturing: Boolean by mutableStateOf(false)

    /** The conversion in use now, or null to show amounts as they are. */
    val view: MoneyRates? get() = if (capturing) null else rates?.takeIf { it.to == chosen }

    fun choose(code: String?) {
        if (code == chosen) return
        chosen = code
        DeviceCurrency.save(code)
        rates = null
        unavailable = false
    }

    internal suspend fun refresh(api: ExchangeRateApi) {
        val target = chosen
        val answer = try {
            api.rates(target)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (target != chosen) return
        // A failed refresh keeps the rates already there.
        if (answer == null) {
            unavailable = target != null && rates == null
            return
        }
        restaurantCurrency = answer.currency?.trim()?.uppercase()?.takeIf { it.length == 3 }
        rates = answer.to?.takeIf { answer.rates.isNotEmpty() }?.let { MoneyRates(it, answer.date, answer.rates, restaurantCurrency) }
        unavailable = target != null && target != restaurantCurrency && rates == null
    }

    /** Signed out: nothing is known about a restaurant any more. */
    fun forget() {
        restaurantCurrency = null
        rates = null
        unavailable = false
    }

    fun show(text: String): String = view?.show(text) ?: text
}

/** What one unit of each currency is in [to], with the day the rates are from ("2026-11-19"). */
@Immutable
class MoneyRates internal constructor(
    val to: String,
    val date: String?,
    private val oneIs: Map<String, Double>,
    private val home: String?
) {
    /** [cents] of [from] (the restaurant's currency when null) in [to]; null when it's the same currency or has no rate. */
    fun convert(cents: Long, from: String?): Long? {
        val code = from?.trim()?.uppercase() ?: home ?: return null
        if (code == to) return null
        val rate = oneIs[code] ?: return null
        return (cents * rate).roundToLong()
    }

    /** 1 unit of [from] in [to] ("1 DOP = $0.0164"), or null. */
    fun oneOf(from: String): Double? = oneIs[from]

    /** Every amount in [text] written in [to]: "Bill €12.50" → "Bill ≈ $13.74". Amounts already in [to] stay. */
    fun show(text: String): String {
        if (text.none { it.isDigit() }) return text
        return Amount.replace(text) { match -> inTarget(match) ?: match.value }
    }

    private fun inTarget(match: MatchResult): String? {
        val (minus, mark, whole, fraction, short) = match.destructured
        val code = when (mark) { "€" -> "EUR"; "$" -> "USD"; "£" -> "GBP"; else -> mark.trim() }
        if (code == to) return null
        val rate = oneIs[code] ?: return null
        if (mark.length >= 3) {
            // A currency written as its code ("DOP 1,250.00") only when it's one restaurants use, and never a plain
            // number after a word ("ALL 3"); the short "DOP950" of charts only for the restaurant's own.
            if (code != home && CURRENCIES.none { it.code == code }) return null
            val plain = fraction.isEmpty() && short.isEmpty() && ',' !in whole
            if (plain && (code != home || mark.endsWith(" "))) return null
        }
        val number = (whole.replace(",", "") + fraction).toDoubleOrNull() ?: return null
        val amount = number * when (short) { "k" -> 1_000.0; "M" -> 1_000_000.0; else -> 1.0 } * rate
        // Nothing is nothing in any currency: "$0.00", not "≈ $0.00".
        if (number == 0.0) return withSymbol(to, if (short.isNotEmpty() || fraction.isEmpty()) "0" else "0.00")
        val shown = when {
            short.isNotEmpty() -> shortNumber(amount)
            fraction.isNotEmpty() && to !in ZERO_DECIMAL_CURRENCIES -> {
                val cents = (amount * 100).roundToLong()
                "${grouped(cents / 100)}.${(cents % 100).toString().padStart(2, '0')}"
            }
            else -> grouped(amount.roundToLong())
        }
        return "≈ " + withSymbol(to, shown, minus)
    }

    private companion object {
        // "€1,234.50", "-€35.20", "$12", "£3.5k", "CHF 12.00", "DOP 82,600.00", "DOP950": the shapes the app writes money in.
        val Amount = Regex("""(?<![\p{L}\d])([+\-−]?)(€|\$|£|[A-Z]{3} ?)(\d{1,3}(?:,\d{3})+|\d+)(\.\d+)?([kM])?(?![\p{L}\d])""")

        fun grouped(value: Long): String = abs(value).toString().reversed().chunked(3).joinToString(",").reversed()

        fun shortNumber(amount: Double): String {
            val whole = abs(amount).roundToLong()
            return when {
                whole >= 1_000_000 -> "${whole / 1_000_000}.${(whole % 1_000_000) / 100_000}M"
                whole >= 1_000 -> "${whole / 1_000}.${(whole % 1_000) / 100}k"
                else -> whole.toString()
            }
        }
    }
}
