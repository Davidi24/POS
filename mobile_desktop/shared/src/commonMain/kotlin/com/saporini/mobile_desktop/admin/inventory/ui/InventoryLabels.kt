package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.outlined.CallMissedOutgoing
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Blender
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalOffer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import com.saporini.mobile_desktop.admin.inventory.CUSTOM_KIND
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockKindDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.place_bakery
import mobile_desktop.shared.generated.resources.place_bar
import mobile_desktop.shared.generated.resources.place_beverage_fridge
import mobile_desktop.shared.generated.resources.place_cleaning
import mobile_desktop.shared.generated.resources.place_dry_store
import mobile_desktop.shared.generated.resources.place_freezer
import mobile_desktop.shared.generated.resources.place_kitchen
import mobile_desktop.shared.generated.resources.place_packaging
import mobile_desktop.shared.generated.resources.place_prep_station
import mobile_desktop.shared.generated.resources.place_reach_in_fridge
import mobile_desktop.shared.generated.resources.place_transit
import mobile_desktop.shared.generated.resources.place_warehouse
import mobile_desktop.shared.generated.resources.place_walk_in_fridge
import mobile_desktop.shared.generated.resources.place_wine
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.painterResource

// Names, icons and colours for stock things: item types, places, stock changes and counts.

internal fun itemTypeLabel(type: String?): String = when (type) {
    "INGREDIENT" -> "Ingredient"
    "PREPARED_COMPONENT" -> "Prepared (sauce, dough…)"
    "FINISHED_GOOD" -> "Ready to sell"
    "BEVERAGE" -> "Drink"
    "ALCOHOL" -> "Alcohol"
    "PACKAGING" -> "Packaging"
    "SUPPLY" -> "Supplies"
    "UNCATEGORIZED" -> "Uncategorized"
    "CUSTOM" -> "Own kind"
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
    "UNCATEGORIZED" -> Icons.Outlined.Inbox
    "CUSTOM" -> Icons.Outlined.LocalOffer
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
    "UNCATEGORIZED" -> "Uncategorized"
    "CUSTOM" -> "Own kind"
    else -> humanize(type)
}

internal fun placeTypeIcon(type: String?): ImageVector = when (type) {
    "KITCHEN" -> Icons.Outlined.Kitchen
    "BAR" -> Icons.Outlined.LocalBar
    "WALK_IN", "FREEZER" -> Icons.Outlined.AcUnit
    "DRY_STORAGE", "BRANCH_STORAGE" -> Icons.Outlined.Inventory2
    "CENTRAL_WAREHOUSE" -> Icons.Outlined.Warehouse
    "TRANSIT" -> Icons.Outlined.LocalShipping
    "UNCATEGORIZED" -> Icons.Outlined.Inbox
    "CUSTOM" -> Icons.Outlined.LocalOffer
    else -> Icons.Outlined.Storefront
}

internal data class PlaceIconChoice(val code: String, val label: String, val image: DrawableResource? = null, val vector: ImageVector? = null)

/** Previously available Material icons remain selectable; the supplied illustrated icons follow them. */
internal val placeIconChoices = listOf(
    PlaceIconChoice("legacy_store", "Store", vector = Icons.Outlined.Storefront),
    PlaceIconChoice("legacy_kitchen", "Kitchen", vector = Icons.Outlined.Kitchen),
    PlaceIconChoice("legacy_bar", "Bar", vector = Icons.Outlined.LocalBar),
    PlaceIconChoice("legacy_cold", "Cold store", vector = Icons.Outlined.AcUnit),
    PlaceIconChoice("legacy_storage", "Storage", vector = Icons.Outlined.Inventory2),
    PlaceIconChoice("legacy_warehouse", "Warehouse", vector = Icons.Outlined.Warehouse),
    PlaceIconChoice("legacy_transit", "Transit", vector = Icons.Outlined.LocalShipping),
    PlaceIconChoice("legacy_category", "Other", vector = Icons.Outlined.LocalOffer),
    PlaceIconChoice("bakery", "Bakery", Res.drawable.place_bakery),
    PlaceIconChoice("bar", "Bar", Res.drawable.place_bar),
    PlaceIconChoice("transit", "In transit", Res.drawable.place_transit),
    PlaceIconChoice("cleaning", "Cleaning", Res.drawable.place_cleaning),
    PlaceIconChoice("dry_store", "Dry store", Res.drawable.place_dry_store),
    PlaceIconChoice("freezer", "Freezer", Res.drawable.place_freezer),
    PlaceIconChoice("kitchen", "Kitchen", Res.drawable.place_kitchen),
    PlaceIconChoice("packaging", "Packaging", Res.drawable.place_packaging),
    PlaceIconChoice("prep_station", "Prep station", Res.drawable.place_prep_station),
    PlaceIconChoice("reach_in_fridge", "Reach-in fridge", Res.drawable.place_reach_in_fridge),
    PlaceIconChoice("beverage_fridge", "Drinks fridge", Res.drawable.place_beverage_fridge),
    PlaceIconChoice("walk_in_fridge", "Walk-in fridge", Res.drawable.place_walk_in_fridge),
    PlaceIconChoice("warehouse", "Warehouse", Res.drawable.place_warehouse),
    PlaceIconChoice("wine", "Wine cellar", Res.drawable.place_wine)
)

internal fun defaultPlaceIconCode(type: String?): String = when (type) {
    "KITCHEN" -> "kitchen"
    "BAR" -> "bar"
    "WALK_IN" -> "walk_in_fridge"
    "FREEZER" -> "freezer"
    "DRY_STORAGE" -> "dry_store"
    "CENTRAL_WAREHOUSE", "BRANCH_STORAGE" -> "warehouse"
    "TRANSIT" -> "transit"
    else -> "warehouse"
}

@Composable
internal fun PlaceIconImage(type: String?, iconCode: String? = null, modifier: Modifier = Modifier, tint: Color = Kit.Grey, contentDescription: String? = null) {
    val code = iconCode ?: defaultPlaceIconCode(type)
    val choice = placeIconChoices.firstOrNull { it.code == code } ?: placeIconChoices.first { it.code == defaultPlaceIconCode(type) }
    if (choice.image != null) Image(painterResource(choice.image), contentDescription, modifier, contentScale = ContentScale.Fit)
    else Icon(choice.vector ?: placeTypeIcon(type), contentDescription, modifier, tint = tint)
}

/** A place's picture: its own uploaded one when it has one (cropped, rounded), else its chosen or default icon. */
@Composable
internal fun PlaceIconImage(place: StockLocationDto, modifier: Modifier = Modifier, tint: Color = placeTypeColor(place.locationType)) {
    val url = place.imageUrl?.takeIf { it.isNotBlank() }
    if (url == null) PlaceIconImage(place.locationType, place.iconCode, modifier, tint)
    else com.saporini.mobile_desktop.core.ui.RemoteImage(url, place.name, modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 22))) {
        PlaceIconImage(place.locationType, place.iconCode, Modifier.matchParentSize(), tint)
    }
}

internal fun placeTypeColor(type: String?): Color = when (type) {
    "KITCHEN" -> Kit.Amber
    "BAR" -> Kit.Purple
    "WALK_IN", "FREEZER" -> Kit.Blue
    "CENTRAL_WAREHOUSE", "TRANSIT", "UNCATEGORIZED" -> Kit.Grey
    "CUSTOM" -> OwnKindColors.first()
    else -> Kit.Green
}

// ---- Kinds the restaurant manages (built-in ones, its own, and Uncategorized) ----

// The colours the restaurant's own kinds take, one per kind by its id, so a kind keeps its colour everywhere.
private val OwnKindColors = listOf(Color(0xFF2E7D6B), Color(0xFF5C6BC0), Color(0xFFB0662E), Color(0xFFC2185B), Color(0xFF6D8B2F), Color(0xFF00838F))

/** The colour of a kind key ("INGREDIENT", or "CUSTOM:<id>" for an own kind). */
internal fun kindKeyColor(key: String?): Color =
    if (key != null && key.startsWith("$CUSTOM_KIND:")) OwnKindColors[(key.hashCode() and Int.MAX_VALUE) % OwnKindColors.size]
    else itemTypeColor(key)

internal fun kindKeyIcon(key: String?): ImageVector =
    if (key != null && key.startsWith("$CUSTOM_KIND:")) Icons.Outlined.LocalOffer else itemTypeIcon(key)

/** What items, places and filters use to point at an item's kind: its code, or "CUSTOM:<id>" for an own kind. */
internal fun StockItemDto.kindKey(): String = if (itemType == CUSTOM_KIND) "$CUSTOM_KIND:$kindId" else itemType

/** "Spices" for an own kind; "Prepared" (or "Prepared (sauce, dough…)" when [long]) and the like for built-in ones. */
internal fun StockItemDto.kindLabel(long: Boolean = false): String =
    if (itemType == CUSTOM_KIND) kindName ?: "Own kind" else if (long) itemTypeLabel(itemType) else kindShort(itemType)

internal fun StockItemDto.kindColor(): Color = kindKeyColor(kindKey())
internal fun StockItemDto.kindIcon(): ImageVector = kindKeyIcon(kindKey())

internal fun StockKindDto.itemLabel(): String = if (code == CUSTOM_KIND) name ?: "Own kind" else kindShort(code)
internal fun StockKindDto.placeLabel(): String = if (code == CUSTOM_KIND) name ?: "Own kind" else placeTypeLabel(code)

internal fun StockLocationDto.kindLabel(): String = if (locationType == CUSTOM_KIND) kindName ?: "Own kind" else placeTypeLabel(locationType)

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

/** 125.0 → "125", 12.3456 → "12.35". */
internal fun trimmed(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
