package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.shortMoney
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.datetime.LocalDate

internal fun methodLabel(method: String): String = when (method) {
    "CASH" -> "Cash"
    "CARD" -> "Card"
    "CONTACTLESS" -> "Contactless"
    "DIGITAL_WALLET" -> "Phone wallet"
    "GIFT_CARD" -> "Gift card"
    "HOUSE_ACCOUNT" -> "House account"
    "LOYALTY" -> "Loyalty points"
    "BANK_TRANSFER" -> "Bank transfer"
    "ONLINE" -> "Online"
    else -> humanize(method)
}

internal fun methodIcon(method: String): ImageVector = when (method) {
    "CASH" -> Icons.Outlined.Payments
    "CARD" -> Icons.Outlined.CreditCard
    "CONTACTLESS" -> Icons.Outlined.Contactless
    "DIGITAL_WALLET" -> Icons.Outlined.Wallet
    "GIFT_CARD" -> Icons.Outlined.CardGiftcard
    "LOYALTY" -> Icons.Outlined.Loyalty
    "BANK_TRANSFER", "HOUSE_ACCOUNT" -> Icons.Outlined.AccountBalance
    else -> Icons.AutoMirrored.Outlined.ReceiptLong
}

internal fun orderTypeLabel(type: String): String = when (type) {
    "DINE_IN" -> "Eat in"
    "TAKEAWAY" -> "Takeaway"
    "DELIVERY" -> "Delivery"
    else -> humanize(type)
}

internal fun sourceLabel(source: String): String = when (source) {
    "POS" -> "Till"
    "WEB" -> "Website"
    "MOBILE" -> "App"
    "QR_TABLE" -> "QR at the table"
    "KIOSK" -> "Kiosk"
    "PHONE" -> "Phone"
    "THIRD_PARTY" -> "Delivery apps"
    else -> humanize(source)
}

/** Money of a figure in cents (0 when missing), for charts and sums. */
internal fun OrderDecimal?.cents(): Long = this.centsOrNull() ?: 0L

internal fun OrderDecimal?.money(currency: String?): String = moneyText(cents(), currency)

internal fun OrderDecimal?.shortMoney(currency: String?): String = shortMoney(cents(), currency)

/** "Mon", "Tue"… for a chart label. */
internal fun weekdayShort(date: LocalDate): String = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

/** "14", "9" → "14:00" style hour label; the short form for charts. */
internal fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0')

/** Text of a decimal number of hours: "38.5 h". */
internal fun hoursText(value: OrderDecimal?): String = value?.value?.toDoubleOrNull()?.let(::hoursText) ?: "–"

internal fun hoursText(hours: Double): String {
    val tenths = kotlin.math.round(hours * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10} h" else "${tenths / 10}.${kotlin.math.abs(tenths % 10)} h"
}

internal fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()
