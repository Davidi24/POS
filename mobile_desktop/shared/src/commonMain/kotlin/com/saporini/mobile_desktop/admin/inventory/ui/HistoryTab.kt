package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockMovementDto
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.TimeColumn
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.TimeZone
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_reservations

private val HistoryTypes = listOf(null, "RECEIPT", "WASTE", "TRANSFER_OUT", "TRANSFER_IN", "SALE_CONSUMPTION", "COUNT_ADJUSTMENT", "MANUAL_ADJUSTMENT", "RETURN")

@Composable
internal fun HistoryTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    val zone = RestaurantTime.zone
    val itemsById = state.items.associateBy { it.id }
    val list: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(Icons.Outlined.History, "Stock history", m, count = state.movements.size) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchableSelect<StockItemDto?>(
                    state.items.firstOrNull { it.id == state.movementItemId }, listOf<StockItemDto?>(null) + state.items,
                    { it?.name ?: "Every item" }, { model.historyFilter(state.movementType, it?.id) }, Modifier.weight(1f),
                    detailOf = { it?.let { item -> itemTypeLabel(item.itemType) } }, icon = Icons.Outlined.Inventory2, placeholder = "Every item"
                )
                if (!size.isDesktop) {
                    com.saporini.mobile_desktop.core.components.SelectInput(state.movementType, HistoryTypes, { it?.let { t -> movementLook(t).label } ?: "Every change" },
                        { model.historyFilter(it, state.movementItemId) }, Modifier.width(180.dp))
                }
            }
            if (state.movements.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("No stock changes", "Deliveries, waste, moves, counts and sales show here, newest first.", Icons.Outlined.History,
                        image = Res.drawable.overview_reservations)
                }
                return@OverviewPanel
            }
            val listState = rememberLazyListState()
            LoadMoreWhenNearEnd(listState, state.movementsHasNext, state.loading || state.loadingMore, onLoadMore = model::loadMore)
            val byDay = state.movements.groupBy { localOf(it.occurredAt, zone)?.date }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                byDay.forEach { (day, moves) ->
                    item(key = "day-$day") { GroupLabel(day?.let(::weekdayDate) ?: "Unknown date", "${moves.size} ${if (moves.size == 1) "change" else "changes"}") }
                    items(moves, key = { it.id }) { MovementCard(it, itemsById[it.inventoryItemId], zone, compact = size.isPhone) }
                }
                item(key = "footer") { PagedFooter(state.movements.size, null, state.movementsHasNext, state.loadingMore, state.error != null, model::loadMore) }
            }
        }
    }
    if (size.isDesktop) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            list(Modifier.weight(1f).fillMaxHeight())
            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                OverviewPanel(Icons.Outlined.Timeline, "Kind of change", Modifier.fillMaxWidth(), titleExtra = "in the list") {
                    HistoryTypes.forEach { type ->
                        val look = type?.let(::movementLook)
                        FilterRow(look?.icon ?: Icons.Outlined.History, look?.label ?: "Every change",
                            if (type == null) state.movements.size else state.movements.count { it.movementType == type || (type == "RECEIPT" && it.movementType == "PURCHASE") },
                            look?.color ?: Kit.Green, state.movementType == type) { model.historyFilter(type, state.movementItemId) }
                    }
                }
            }
        }
    } else list(modifier)
}

@Composable
private fun MovementCard(move: StockMovementDto, item: StockItemDto?, zone: TimeZone, compact: Boolean) {
    val look = movementLook(move.movementType)
    val delta = move.quantityDelta.asDouble()
    val unit = unitShort(move.unit ?: item?.baseUnit)
    StripCard(look.color, minHeight = 64.dp) {
        if (!compact) {
            TimeColumn(timeText(move.occurredAt, zone), look.label, look.color, Modifier.width(80.dp))
            CardDivider()
        }
        Box(Modifier.size(34.dp).background(look.color.copy(alpha = 0.12f), CircleShape), Alignment.Center) {
            Icon(look.icon, null, Modifier.size(18.dp), tint = look.color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(move.inventoryItemName ?: item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(move.locationName, move.reason?.takeIf { it.isNotBlank() }?.let { "“$it”" }, move.createdByUserName?.let { "by $it" },
                if (compact) timeText(move.occurredAt, zone) else null).joinToString(" · "),
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${if (delta > 0) "+" else ""}${plainNumber(move.quantityDelta)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (delta >= 0) Kit.Green else Kit.Danger, textAlign = TextAlign.End)
            move.totalCostDelta?.let { Text(money(it, null), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted) }
        }
    }
}
