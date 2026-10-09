package com.saporini.mobile_desktop.pos.payment.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.payment.PayMethod

internal fun PayMethod.icon(): ImageVector = when (this) {
    PayMethod.CASH -> Icons.Outlined.Payments
    PayMethod.CARD -> Icons.Outlined.CreditCard
    PayMethod.CONTACTLESS -> Icons.Outlined.Contactless
    PayMethod.DIGITAL_WALLET -> Icons.Outlined.Smartphone
    PayMethod.GIFT_CARD -> Icons.Outlined.CardGiftcard
    PayMethod.BANK_TRANSFER -> Icons.Outlined.AccountBalance
    PayMethod.OTHER -> Icons.Outlined.MoreHoriz
}

internal fun PayMethod.hint(): String = when (this) {
    PayMethod.CASH -> "Notes and coins"
    PayMethod.CARD -> "Chip or swipe"
    PayMethod.CONTACTLESS -> "Tap a card"
    PayMethod.DIGITAL_WALLET -> "Apple Pay, Google Pay"
    PayMethod.GIFT_CARD -> "The restaurant's card"
    PayMethod.BANK_TRANSFER -> "Paid to the bank"
    PayMethod.OTHER -> "Anything else"
}

internal fun methodName(api: String): String = PayMethod.fromApi(api)?.label ?: humanize(api)

internal fun methodIcon(api: String): ImageVector = PayMethod.fromApi(api)?.icon() ?: Icons.Outlined.MoreHoriz

internal fun paymentStatusLabel(status: String): String = when (status) {
    "CAPTURED" -> "Paid"
    "PARTIALLY_REFUNDED" -> "Partly refunded"
    "REFUNDED" -> "Refunded"
    "VOIDED", "CANCELLED" -> "Cancelled"
    "PENDING", "AUTHORIZED" -> "Waiting"
    "FAILED" -> "Failed"
    else -> humanize(status)
}

internal fun paymentStatusColor(status: String): Color = when (status) {
    "CAPTURED" -> Kit.Green
    "PARTIALLY_REFUNDED" -> Kit.Amber
    "REFUNDED", "VOIDED", "CANCELLED", "FAILED" -> Kit.Danger
    else -> Kit.Grey
}

internal fun OrderDecimal?.cents(): Long = this.centsOrNull() ?: 0L

internal fun OrderDecimal?.money(currency: String?): String = moneyText(cents(), currency)
