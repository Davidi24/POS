package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockLevelDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.quantityText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import kotlin.time.Clock

internal enum class StockHealth(val label: String, val color: Color) {
    EMPTY("Out of stock", Kit.Danger), LOW("Running low", Kit.Amber), OK("Fine", Kit.Green), NO_LEVEL("No levels set", Kit.Grey)
}

internal fun StockLevelDto.health(): StockHealth {
    val onHand = onHandQuantity.asDouble()
    return when {
        onHand <= 0.0 -> StockHealth.EMPTY
        lowStock -> StockHealth.LOW
        reorderQuantity == null && parQuantity == null -> StockHealth.NO_LEVEL
        else -> StockHealth.OK
    }
}

@Composable
internal fun StockTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    if (size.isDesktop) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LevelsPanel(state, model, size, Modifier.weight(1f).fillMaxHeight())
            SideColumn(Modifier.width(330.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                LowStockPanel(state, model)
                QuickChangesPanel(state, model)
            }
        }
    } else {
        LevelsPanel(state, model, size, modifier)
    }
}

@Composable
private fun LevelsPanel(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    val units = state.items.associate { it.id to it.baseUnit }
    val types = state.items.associate { it.id to it.itemType }
    val levels = state.levels.sortedWith(compareBy<StockLevelDto> { it.health().ordinal }.thenBy { it.inventoryItemName?.lowercase() })
    OverviewPanel(Icons.Outlined.Inventory2, "Stock on hand", modifier, count = levels.size) {
        FilterPills(listOf<Pair<String?, String>>(null to "All places") + state.activeLocations.map { it.id to it.name }, state.levelLocationId, model::levelsAt)
        if (levels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("No stock here yet", "Receive a delivery to start counting what you have. Items appear here once they have stock.",
                    Icons.Outlined.Inventory2, image = Res.drawable.settings_orders_kitchen,
                    action = if (state.canEdit) ({ KitButton("Receive stock", { model.startMove(StockMoveKind.RECEIVE, locationId = state.levelLocationId) },
                        icon = Icons.Outlined.LocalShipping) }) else null)
            }
            return@OverviewPanel
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            items(levels, key = { "${it.locationId}-${it.inventoryItemId}" }) { level ->
                LevelCard(level, units[level.inventoryItemId], types[level.inventoryItemId], showPlace = state.levelLocationId == null,
                    compact = size.isPhone, canEdit = state.canEdit, model = model)
            }
        }
    }
}

@Composable
private fun LevelCard(level: StockLevelDto, unit: String?, type: String?, showPlace: Boolean, compact: Boolean, canEdit: Boolean, model: InventoryScreenModel) {
    val health = level.health()
    val actions = if (!canEdit) emptyList() else StockMoveKind.entries.map { kind ->
        val look = moveKindLook(kind)
        RowAction(if (kind == StockMoveKind.TRANSFER) "Move to another place" else look.label, look.icon, danger = kind == StockMoveKind.WASTE) {
            model.startMove(kind, level.inventoryItemId, level.locationId)
        }
    }
    StripCard(health.color, minHeight = 70.dp) {
        if (!compact) IconTile(itemTypeIcon(type), itemTypeColor(type), 38.dp)
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(level.inventoryItemName ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(level.locationName.takeIf { showPlace }, itemTypeLabel(type).takeUnless { compact },
                level.lastMovementAt?.let { "changed ${agoText(it, Clock.System.now(), RestaurantTime.zone)}" }).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (compact) StockGauge(level.onHandQuantity.asDouble(), level.reorderQuantity?.asDouble(), level.parQuantity?.asDouble(), health.color)
        }
        if (compact) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(plainNumber(level.onHandQuantity), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = if (health == StockHealth.EMPTY) Kit.Danger else Kit.Ink)
                    Text(" ${unitShort(unit)}", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                }
                Text(health.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = health.color, maxLines = 1)
            }
        } else {
            CardDivider()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(plainNumber(level.onHandQuantity), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        color = if (health == StockHealth.EMPTY) Kit.Danger else Kit.Ink)
                    Text(" ${unitShort(unit)}", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    level.parQuantity?.let { Text("full ${plainNumber(it)}", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Faint) }
                }
                StockGauge(level.onHandQuantity.asDouble(), level.reorderQuantity?.asDouble(), level.parQuantity?.asDouble(), health.color)
            }
            StatusPill(health.label, health.color)
        }
        RowActionsMenu(actions)
    }
}

/**
 * How full a place is: the bar fills towards the full (par) level, a small mark shows the reorder point. Without a
 * full level the reorder point counts as half way.
 */
@Composable
internal fun StockGauge(onHand: Double, reorder: Double?, par: Double?, color: Color) {
    val top = par?.takeIf { it > 0 } ?: reorder?.takeIf { it > 0 }?.let { it * 2 } ?: maxOf(onHand, 1.0)
    val fill = (onHand / top).coerceIn(0.0, 1.0).toFloat()
    val mark = reorder?.let { (it / top).coerceIn(0.0, 1.0).toFloat() }
    val markColor = Kit.Ink.copy(alpha = 0.55f)
    // Drawn on a canvas: it sits in cards measured by intrinsic height, where BoxWithConstraints can't be used.
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val bar = 6.dp.toPx()
        val top = (size.height - bar) / 2
        val radius = androidx.compose.ui.geometry.CornerRadius(bar / 2)
        drawRoundRect(color.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, bar), radius)
        if (fill > 0f) drawRoundRect(color, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(size.width * fill, bar), radius)
        mark?.let { drawRect(markColor, androidx.compose.ui.geometry.Offset(size.width * it - 1.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
    }
}

@Composable
private fun LowStockPanel(state: InventoryState, model: InventoryScreenModel) {
    val units = state.items.associate { it.id to it.baseUnit }
    OverviewPanel(Icons.Outlined.NotificationImportant, "Running low", Modifier.fillMaxWidth(), count = state.lowStock.size,
        titleColor = if (state.lowStock.isEmpty()) Kit.Ink else Kit.Danger) {
        if (state.lowStock.isEmpty()) {
            OverviewCompactEmpty("Nothing is low", "Items show here when they drop under their reorder point.", Icons.Outlined.NotificationImportant)
        }
        state.lowStock.take(8).forEach { level ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Danger.copy(alpha = 0.05f)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(level.inventoryItemName ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${quantityText(level.onHandQuantity, units[level.inventoryItemId])} left · ${level.locationName ?: ""}",
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.canEdit) KitButton("Receive", { model.startMove(StockMoveKind.RECEIVE, level.inventoryItemId, level.locationId) },
                    icon = Icons.Outlined.LocalShipping, style = ButtonStyle.SECONDARY)
            }
        }
        if (state.lowStock.size > 8) Text("and ${state.lowStock.size - 8} more", Modifier.padding(horizontal = 6.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
}

@Composable
private fun QuickChangesPanel(state: InventoryState, model: InventoryScreenModel) {
    OverviewPanel(Icons.Outlined.Bolt, "Record a stock change", Modifier.fillMaxWidth()) {
        StockMoveKind.entries.forEach { kind ->
            val look = moveKindLook(kind)
            FilterRow(look.icon, when (kind) {
                StockMoveKind.RECEIVE -> "Receive a delivery"
                StockMoveKind.WASTE -> "Record waste"
                StockMoveKind.TRANSFER -> "Move between places"
                StockMoveKind.RETURN -> "Return to a supplier"
                StockMoveKind.ADJUST -> "Correct a number"
            }, null, look.color, detail = when (kind) {
                StockMoveKind.RECEIVE -> "Adds stock, optionally at a new cost"
                StockMoveKind.WASTE -> "Takes stock off with a reason"
                StockMoveKind.TRANSFER -> "From one place to another"
                StockMoveKind.RETURN -> "Takes stock off, sent back"
                StockMoveKind.ADJUST -> "Plus or minus, with a reason"
            }, onClick = if (state.canEdit) ({ model.startMove(kind, locationId = state.levelLocationId) }) else null)
        }
        if (!state.canEdit) OverviewCompactEmpty("Read only", "Your role can look at stock but not change it.", Icons.Outlined.Storefront)
    }
}
