package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMissedOutgoing
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Blender
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveDown
import androidx.compose.material.icons.outlined.MoveUp
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal

// Names, icons and colours for stock things: item types, places, stock changes and counts.

internal fun itemTypeLabel(type: String?): String = when (type) {
    "INGREDIENT" -> "Ingredient"
    "PREPARED_COMPONENT" -> "Prepared (sauce, dough…)"
    "FINISHED_GOOD" -> "Ready to sell"
    "BEVERAGE" -> "Drink"
    "ALCOHOL" -> "Alcohol"
    "PACKAGING" -> "Packaging"
    "SUPPLY" -> "Supplies"
    else -> humanize(type)
}

internal fun itemTypeIcon(type: String?): ImageVector = when (type) {
    "INGREDIENT" -> Icons.Outlined.Eco
    "PREPARED_COMPONENT" -> Icons.Outlined.Blender
    "FINISHED_GOOD" -> Icons.Outlined.Restaurant
    "BEVERAGE" -> Icons.Outlined.LocalDrink
    "ALCOHOL" -> Icons.Outlined.WineBar
    "PACKAGING" -> Icons.Outlined.Outbox
    "SUPPLY" -> Icons.Outlined.CleaningServices
    else -> Icons.Outlined.Category
}

internal fun itemTypeColor(type: String?): Color = when (type) {
    "INGREDIENT" -> Kit.Green
    "PREPARED_COMPONENT" -> Kit.Amber
    "FINISHED_GOOD" -> Kit.Purple
    "BEVERAGE" -> Kit.Blue
    "ALCOHOL" -> Color(0xFF8E3B5B)
    else -> Kit.Grey
}

internal fun placeTypeLabel(type: String?): String = when (type) {
    "BRANCH_STORAGE" -> "Storeroom"
    "KITCHEN" -> "Kitchen"
    "BAR" -> "Bar"
    "WALK_IN" -> "Walk-in fridge"
    "FREEZER" -> "Freezer"
    "DRY_STORAGE" -> "Dry store"
    "CENTRAL_WAREHOUSE" -> "Central warehouse"
    "TRANSIT" -> "On the way"
    else -> humanize(type)
}

internal fun placeTypeIcon(type: String?): ImageVector = when (type) {
    "KITCHEN" -> Icons.Outlined.Kitchen
    "BAR" -> Icons.Outlined.LocalBar
    "WALK_IN", "FREEZER" -> Icons.Outlined.AcUnit
    "DRY_STORAGE", "BRANCH_STORAGE" -> Icons.Outlined.Inventory2
    "CENTRAL_WAREHOUSE" -> Icons.Outlined.Warehouse
    "TRANSIT" -> Icons.Outlined.LocalShipping
    else -> Icons.Outlined.Storefront
}

internal fun placeTypeColor(type: String?): Color = when (type) {
    "KITCHEN" -> Kit.Amber
    "BAR" -> Kit.Purple
    "WALK_IN", "FREEZER" -> Kit.Blue
    "CENTRAL_WAREHOUSE", "TRANSIT" -> Kit.Grey
    else -> Kit.Green
}

internal data class MoveLook(val label: String, val icon: ImageVector, val color: Color)

internal fun moveKindLook(kind: StockMoveKind): MoveLook = when (kind) {
    StockMoveKind.RECEIVE -> MoveLook("Receive", Icons.Outlined.LocalShipping, Kit.Green)
    StockMoveKind.WASTE -> MoveLook("Waste", Icons.Outlined.DeleteSweep, Kit.Danger)
    StockMoveKind.TRANSFER -> MoveLook("Move", Icons.Outlined.SwapHoriz, Kit.Blue)
    StockMoveKind.RETURN -> MoveLook("Return", Icons.AutoMirrored.Outlined.Undo, Kit.Purple)
    StockMoveKind.ADJUST -> MoveLook("Correct", Icons.Outlined.Tune, Kit.Amber)
}

internal fun movementLook(type: String): MoveLook = when (type) {
    "PURCHASE", "RECEIPT" -> MoveLook("Received", Icons.Outlined.LocalShipping, Kit.Green)
    "WASTE" -> MoveLook("Wasted", Icons.Outlined.DeleteSweep, Kit.Danger)
    "TRANSFER_IN" -> MoveLook("Moved in", Icons.Outlined.MoveDown, Kit.Blue)
    "TRANSFER_OUT" -> MoveLook("Moved out", Icons.Outlined.MoveUp, Kit.Blue)
    "SALE_CONSUMPTION" -> MoveLook("Sold", Icons.Outlined.PointOfSale, Kit.Purple)
    "RETURN" -> MoveLook("Returned", Icons.AutoMirrored.Outlined.CallMissedOutgoing, Kit.Purple)
    "COUNT_ADJUSTMENT" -> MoveLook("Count", Icons.AutoMirrored.Outlined.FactCheck, Kit.Amber)
    "MANUAL_ADJUSTMENT" -> MoveLook("Corrected", Icons.Outlined.Tune, Kit.Amber)
    "PREP_PRODUCTION" -> MoveLook("Prepared", Icons.Outlined.Blender, Kit.Green)
    "VOID" -> MoveLook("Cancelled", Icons.Outlined.Inventory, Kit.Grey)
    else -> MoveLook(humanize(type), Icons.Outlined.Inventory, Kit.Grey)
}

internal fun countStatusLabel(status: String?): String = when (status) {
    "DRAFT" -> "Not started"
    "IN_PROGRESS" -> "Counting"
    "COMPLETED" -> "Waiting for approval"
    "APPROVED" -> "Approved"
    "CANCELLED" -> "Cancelled"
    else -> humanize(status)
}

internal fun countStatusColor(status: String?): Color = when (status) {
    "DRAFT" -> Kit.Grey
    "IN_PROGRESS" -> Kit.Blue
    "COMPLETED" -> Kit.Amber
    "APPROVED" -> Kit.Green
    else -> Kit.Grey
}

internal fun countStepLabel(step: String): String = when (step) {
    "start" -> "Start counting"
    "complete" -> "Finish count"
    "approve" -> "Approve and update stock"
    "cancel" -> "Cancel count"
    else -> humanize(step)
}

/** For bars and sums on screen only (never sent back): decimal text as a number. */
internal fun OrderDecimal?.asDouble(): Double = this?.value?.toDoubleOrNull() ?: 0.0
