package com.saporini.mobile_desktop.fraud.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.humanize

internal fun severityLabel(severity: String?): String = when (severity) {
    "HIGH" -> "High"
    "MEDIUM" -> "Medium"
    "LOW" -> "Low"
    else -> humanize(severity)
}

internal fun severityColor(severity: String?): Color = when (severity) {
    "HIGH" -> Kit.Danger
    "MEDIUM" -> Kit.Amber
    else -> Kit.Blue
}

internal fun reviewLabel(status: String?): String = when (status) {
    "OPEN" -> "To check"
    "REVIEWED" -> "Checked"
    "DISMISSED" -> "Not a problem"
    "CONFIRMED" -> "Confirmed"
    else -> humanize(status)
}

internal fun reviewColor(status: String?): Color = when (status) {
    "OPEN" -> Kit.Amber
    "REVIEWED" -> Kit.Blue
    "DISMISSED" -> Kit.Grey
    "CONFIRMED" -> Kit.Danger
    else -> Kit.Grey
}

internal fun reviewIcon(status: String?): ImageVector = when (status) {
    "OPEN" -> Icons.Outlined.NewReleases
    "REVIEWED" -> Icons.Outlined.DoneAll
    "DISMISSED" -> Icons.Outlined.ThumbDown
    "CONFIRMED" -> Icons.Outlined.Gavel
    else -> Icons.Outlined.Shield
}

internal fun ruleIcon(rule: String?): ImageVector = when (rule) {
    "LARGE_DISCOUNT" -> Icons.Outlined.LocalOffer
    "ITEM_VOID_AFTER_KITCHEN", "MANY_VOIDS" -> Icons.Outlined.RemoveShoppingCart
    "ORDER_VOIDED_AFTER_KITCHEN" -> Icons.Outlined.Block
    "CANCELLED_AFTER_KITCHEN" -> Icons.Outlined.Cancel
    "LARGE_REFUND", "CASH_REFUNDS" -> Icons.AutoMirrored.Outlined.Undo
    "PAYMENT_VOIDED" -> Icons.Outlined.CreditCardOff
    "REOPENED_PAID_ORDER" -> Icons.Outlined.LockOpen
    "CLOSED_WITHOUT_PAYMENT" -> Icons.Outlined.MoneyOff
    "MARKED_PAID_MANUALLY" -> Icons.Outlined.Payments
    "HIGH_TIP" -> Icons.Outlined.VolunteerActivism
    "OFF_SHIFT_ACTION" -> Icons.Outlined.AccessTime
    else -> Icons.Outlined.Shield
}

internal fun activityLabel(type: String?): String = when (type) {
    "DISCOUNT" -> "Discount"
    "ITEM_REMOVED" -> "Item removed"
    "ORDER_VOIDED" -> "Order voided"
    "ORDER_CANCELLED" -> "Order cancelled"
    "REFUND" -> "Refund"
    "PAYMENT_CANCELLED" -> "Payment cancelled"
    "ORDER_REOPENED" -> "Order reopened"
    else -> humanize(type)
}

internal fun activityIcon(type: String?): ImageVector = when (type) {
    "DISCOUNT" -> Icons.Outlined.LocalOffer
    "ITEM_REMOVED" -> Icons.Outlined.RemoveCircleOutline
    "ORDER_VOIDED" -> Icons.Outlined.Block
    "ORDER_CANCELLED" -> Icons.Outlined.Cancel
    "REFUND" -> Icons.AutoMirrored.Outlined.Undo
    "PAYMENT_CANCELLED" -> Icons.Outlined.CreditCardOff
    "ORDER_REOPENED" -> Icons.Outlined.LockOpen
    else -> Icons.Outlined.Shield
}

internal fun activityColor(type: String?): Color = when (type) {
    "DISCOUNT" -> Kit.Amber
    "ITEM_REMOVED" -> Kit.Purple
    "ORDER_VOIDED", "REFUND" -> Kit.Danger
    "ORDER_CANCELLED" -> Kit.Grey
    "PAYMENT_CANCELLED" -> Kit.Danger
    "ORDER_REOPENED" -> Kit.Blue
    else -> Kit.Grey
}
