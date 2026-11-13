package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.MOVEMENT_KIND_SAMPLE
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockMovementDto
import com.saporini.mobile_desktop.admin.inventory.StockCountDto
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_changes
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.unit.DpOffset
import com.saporini.mobile_desktop.admin.inventory.ITEM_CHANGES
import com.saporini.mobile_desktop.admin.inventory.COUNT_HISTORY
import com.saporini.mobile_desktop.admin.inventory.StockItemEventDto
import com.saporini.mobile_desktop.admin.inventory.StockPersonDto
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.pos.reservations.CompactDatePicker

// Inventory → History: every stock change, newest first, a day at a time: when, what, where, who, why and how much
// (in the item's unit and at cost). Filter by kind of change and by item; the side shows what the loaded list adds up to.

private val HistoryTypes = listOf(null, "RECEIPT", "WASTE", "TRANSFER_OUT", "TRANSFER_IN", "SALE_CONSUMPTION", "COUNT_ADJUSTMENT", "MANUAL_ADJUSTMENT", "RETURN")

// History → Items (user, 2026-10-30: "a two-way toggle, Stock or Items, so I see them apart"): what happened to items themselves.
private val ItemEventKinds = listOf(null, "ITEM_CREATED", "ITEM_UPDATED", "ITEM_DELETED")

private fun historyLook(type: String): MoveLook = movementLook(type)

/** Added / Changed / Deleted: how an item's own change looks. */
private fun eventLook(action: String?): MoveLook = when (action) {
    "ITEM_CREATED" -> MoveLook("Added", Icons.Outlined.AddCircleOutline, Kit.Green)
    "ITEM_DELETED" -> MoveLook("Deleted", Icons.Outlined.Delete, Kit.Danger)
    else -> MoveLook("Changed", Icons.Outlined.EditNote, Kit.Purple)
}

/** One line of History: a stock change or something that happened to an item itself. */
private sealed interface HistoryEntry {
    val key: String
    val at: String?
    data class Move(val move: StockMovementDto) : HistoryEntry {
        override val key get() = "m-${move.id}"
        override val at get() = move.occurredAt
    }
    data class Event(val event: StockItemEventDto) : HistoryEntry {
        override val key get() = "e-${event.id}"
        override val at get() = event.occurredAt
    }
    data class Count(val count: StockCountDto) : HistoryEntry {
        override val key get() = "c-${count.id}"
        override val at get() = count.approvedAt
    }
}

/** Whether a change of [movementType] belongs under the kind filter [type] (deliveries count purchases too). */
private fun matchesType(movementType: String, type: String?) =
    type == null || movementType == type || (type == "RECEIPT" && movementType == "PURCHASE")

@Composable
internal fun HistoryTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    val zone = RestaurantTime.zone
    val itemsById = state.items.associateBy { it.id }
    // Days folded shut (like the Reservations sections): click a day to fold or open it.
    var folded by remember { mutableStateOf(setOf<String>()) }
    // Three separate histories: stock movements, item changes, and approved physical counts.
    val itemsMode = state.movementType == ITEM_CHANGES
    val countsMode = state.movementType == COUNT_HISTORY
    val mode = when { itemsMode -> "Items"; countsMode -> "Counts"; else -> "Stock" }
    var eventKind by remember { mutableStateOf<String?>(null) }
    val switchMode = { selected: String ->
        eventKind = null
        model.historyFilter(when (selected) { "Items" -> ITEM_CHANGES; "Counts" -> COUNT_HISTORY; else -> null }, null)
    }
    val entries: List<HistoryEntry> = when {
        countsMode -> state.historyCounts.map { HistoryEntry.Count(it) }
        itemsMode -> state.itemEvents.filter { eventKind == null || it.action == eventKind }.map { HistoryEntry.Event(it) }
        else -> state.movements.map { HistoryEntry.Move(it) }
    }
    val days = entries.map { localOf(it.at, zone)?.date.toString() }.distinct()
    val list: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(when { countsMode -> Icons.AutoMirrored.Outlined.FactCheck; itemsMode -> Icons.Outlined.EditNote; else -> Icons.Outlined.History },
            when { countsMode -> "Count history"; itemsMode -> "Item history"; else -> "Stock history" }, m,
            count = entries.size, bottomPadding = 0.dp, flat = size.isPhone) {
            // Off a computer there's no side column, so the Stock | Items switch leads the panel.
            if (!size.isDesktop) HistoryModeSwitch(mode, switchMode, Modifier.fillMaxWidth())
            if (countsMode) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchableSelect<StockItemDto?>(
                        state.items.firstOrNull { it.id == state.movementItemId }, listOf<StockItemDto?>(null) + state.items,
                        { it?.name ?: "Every item" }, { model.historyFilter(COUNT_HISTORY, it?.id) },
                        Modifier.weight(1f), detailOf = { it?.let { item -> item.kindLabel() } }, icon = Icons.Outlined.Inventory2,
                        placeholder = "Every item", compact = size.isPhone,
                        leadingOf = { item, side -> if (item != null) StockItemThumb(item, side) else IconTile(Icons.Outlined.Inventory2, Kit.Green, side) }
                    )
                    if (!size.isPhone) Text("Approved counts", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
            }
            if (!countsMode) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (size.isPhone) 8.dp else 10.dp)) {
              Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (size.isPhone) 8.dp else 10.dp)) {
                SearchableSelect<StockItemDto?>(
                    state.items.firstOrNull { it.id == state.movementItemId }, listOf<StockItemDto?>(null) + state.items,
                    { it?.name ?: "Every item" }, { model.historyFilter(state.movementType, it?.id) },
                    if (size.isPhone) Modifier.weight(1f) else Modifier.weight(1.6f).widthIn(max = 320.dp),
                    detailOf = { it?.let { item -> item.kindLabel() } }, icon = Icons.Outlined.Inventory2, placeholder = "Every item",
                    compact = size.isPhone,
                    leadingOf = { item, side -> if (item != null) StockItemThumb(item, side) else IconTile(Icons.Outlined.Inventory2, Kit.Green, side) }
                )
                // A phone: the kind of change, the day and the person sit together in one Filters button (user, 2026-10-30).
                if (size.isPhone) HistoryPhoneFilters(state, model, itemsMode, eventKind) { eventKind = it }
                else if (!size.isDesktop) {
                    if (itemsMode) SelectInput(eventKind, ItemEventKinds, { it?.let { a -> eventLook(a).label } ?: "Every item change" }, { eventKind = it },
                        if (size.isPhone) Modifier.weight(1f) else Modifier.weight(1f).widthIn(max = 190.dp),
                        icon = eventKind?.let { eventLook(it).icon } ?: Icons.Outlined.EditNote, placeholder = "Every item change", compact = size.isPhone)
                    else SelectInput(state.movementType, HistoryTypes, { it?.let { t -> historyLook(t).label } ?: "Every change" },
                        { model.historyFilter(it, state.movementItemId) }, if (size.isPhone) Modifier.weight(1f) else Modifier.weight(1f).widthIn(max = 190.dp),
                        icon = state.movementType?.let { historyLook(it).icon } ?: Icons.Outlined.Timeline, placeholder = "Every change", compact = size.isPhone)
                }
                // Day and person (user, 2026-10-30); on a phone they get a row of their own below.
                if (!size.isPhone) {
                    DayFilter(state.movementDay, model::historyDay, Modifier.weight(1f).widthIn(max = 170.dp))
                    PersonFilter(state, model, Modifier.weight(1.2f).widthIn(max = 200.dp))
                }
              }
                if (!size.isPhone) Box(Modifier.width(118.dp), Alignment.CenterEnd) {
                    if (days.size > 1) {
                        val allFolded = days.all { it in folded }
                        SmallLink(if (allFolded) "Open all" else "Fold all days", if (allFolded) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess) {
                            folded = if (allFolded) emptySet() else days.toSet()
                        }
                    }
                }
            }
            if (entries.isEmpty() && state.loading) {
                ListLinesSkeleton(8, 60.dp, Modifier.fillMaxWidth().weight(1f))
                return@OverviewPanel
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    val filtered = if (countsMode) state.movementItemId != null else
                        (!itemsMode && state.movementType != null) || eventKind != null || state.movementItemId != null ||
                            state.movementDay != null || state.movementPersonId != null
                    OverviewEmpty(if (filtered) "Nothing like that yet" else when { countsMode -> "No approved counts yet"; itemsMode -> "No item changes yet"; else -> "No stock changes yet" },
                        when { countsMode -> "Approved counts appear here. Open one to see each item's before quantity, counted quantity and change.";
                            itemsMode -> "Items added, changed or deleted show here."; else -> "Deliveries, waste, moves, counts and sales show here, newest first." },
                        when { countsMode -> Icons.AutoMirrored.Outlined.FactCheck; itemsMode -> Icons.Outlined.EditNote; else -> Icons.Outlined.History },
                        image = Res.drawable.inventory_stock_changes)
                }
                return@OverviewPanel
            }
            val listState = rememberLazyListState()
            val canLoadMore = when { countsMode -> state.historyCountsHasNext; itemsMode -> false; else -> state.movementsHasNext }
            LoadMoreWhenNearEnd(listState, canLoadMore, state.loading || state.loadingMore, onLoadMore = model::loadMore)
            val today = Clock.System.now().toLocalDateTime(zone).date
            val byDay = entries.groupBy { localOf(it.at, zone)?.date }
            ScrollList(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                byDay.forEach { (day, dayEntries) ->
                    val key = day.toString()
                    item(key = "day-$day") {
                        DayHeader(day, today, dayEntries.size, folded = key in folded) { folded = if (key in folded) folded - key else folded + key }
                    }
                    if (key !in folded) items(dayEntries, key = { it.key }) { entry ->
                        when (entry) {
                            is HistoryEntry.Move -> MovementLine(entry.move, itemsById[entry.move.inventoryItemId], zone, compact = size.isPhone)
                            is HistoryEntry.Event -> ItemEventLine(entry.event, entry.event.itemId?.let { itemsById[it] }, zone, compact = size.isPhone)
                            is HistoryEntry.Count -> CountHistoryLine(entry.count, zone, compact = size.isPhone) { model.openCount(entry.count.id) }
                        }
                    }
                }
                item(key = "footer") {
                    if (itemsMode) PagedFooter(entries.size, null, false, false, false) {}
                    else if (countsMode) PagedFooter(state.historyCounts.size, null, state.historyCountsHasNext, state.loadingMore, state.error != null, model::loadMore)
                    else PagedFooter(state.movements.size, null, state.movementsHasNext, state.loadingMore, state.error != null, model::loadMore)
                }
            }
        }
    }
    if (size.isDesktop) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              // The Stock | Items switch stays put; only the panels under it scroll (user, 2026-10-30).
              HistoryModeSwitch(mode, switchMode, Modifier.fillMaxWidth())
              ScrollColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (countsMode) OverviewPanel(Icons.AutoMirrored.Outlined.FactCheck, "Approved counts", Modifier.fillMaxWidth()) {
                    Text("Each approved count shows the stock snapshot and the correction made to each item.", fontFamily = Inter(), fontSize = 12.sp,
                        lineHeight = 17.sp, color = Kit.Muted)
                } else if (itemsMode) OverviewPanel(Icons.Outlined.EditNote, "Kind of change", Modifier.fillMaxWidth()) {
                    ItemEventKinds.forEach { kind ->
                        val look = kind?.let(::eventLook)
                        FilterRow(look?.icon ?: Icons.Outlined.EditNote, look?.label ?: "Every item change",
                            state.itemEvents.count { kind == null || it.action == kind }, look?.color ?: Kit.Green, eventKind == kind) {
                            eventKind = if (eventKind == kind) null else kind
                        }
                    }
                } else OverviewPanel(Icons.Outlined.Timeline, "Kind of change", Modifier.fillMaxWidth()) {
                    // Counted once per load, not from the filtered list, so picking a kind leaves every number as it is.
                    val counts = state.movementTypeCounts
                    val kindRow: @Composable (String?) -> Unit = { type ->
                        val look = type?.let(::historyLook)
                        val count = counts?.let { byType -> if (type == null) byType.values.sum() else byType.filterKeys { matchesType(it, type) }.values.sum() }
                        FilterRow(look?.icon ?: Icons.Outlined.History, look?.label ?: "Every change", count,
                            look?.color ?: Kit.Green, state.movementType == type) { model.historyFilter(if (state.movementType == type) null else type, state.movementItemId) }
                    }
                    // "Every change" stays on top; the kinds scroll after about five and a half (user, 2026-10-30: "not so tall,
                    // scrollable"), like Items → Kinds.
                    kindRow(null)
                    ScrollColumn(Modifier.fillMaxWidth().heightIn(max = 272.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryTypes.filterNotNull().forEach { kindRow(it) }
                    }
                    if (state.movementTypeCountsCapped) {
                        Text("Counted over the newest $MOVEMENT_KIND_SAMPLE changes.", Modifier.padding(start = 4.dp), fontFamily = Inter(), fontSize = 11.sp,
                            color = Kit.Faint)
                    }
                }
                if (!itemsMode && !countsMode) ListTotals(state)
              }
            }
            list(Modifier.weight(1f).fillMaxHeight())
        }
    } else list(modifier)
}

/** What the changes shown add up to, at cost, and who made the most of them. */
@Composable
private fun ListTotals(state: InventoryState) {
    val moves = state.movements
    val cents = { move: StockMovementDto -> ((move.totalCostDelta?.asDouble() ?: 0.0) * 100).roundToLong() }
    val cameIn = moves.sumOf { cents(it).coerceAtLeast(0) }
    val wentOut = moves.sumOf { (-cents(it)).coerceAtLeast(0) }
    val people = moves.mapNotNull { it.createdByUserName }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
    OverviewPanel(Icons.Outlined.People, "In this list", Modifier.fillMaxWidth()) {
        TotalLine(Icons.AutoMirrored.Outlined.TrendingUp, "Came in", "+${moneyText(cameIn, "EUR")}", Kit.Green)
        TotalLine(Icons.AutoMirrored.Outlined.TrendingDown, "Went out", "−${moneyText(wentOut, "EUR")}", Kit.Danger)
        if (people.isNotEmpty()) {
            Text("MOST CHANGES BY", Modifier.padding(top = 6.dp, start = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 0.6.sp, color = Kit.Muted)
            people.forEach { (name, count) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Kit.GreenSoft), Alignment.Center) {
                        Text(name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""), fontFamily = Inter(),
                            fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Kit.Green)
                    }
                    Text(name, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
                }
            }
        }
        Text("Counted over the changes loaded below.", Modifier.padding(start = 4.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
    }
}

@Composable
private fun TotalLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tone: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = tone)
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
    }
}

/** "Today · Sun 25 Oct", "Yesterday · …" or the date, with how many changes that day; click to fold or open the day. */
@Composable
private fun DayHeader(day: LocalDate?, today: LocalDate, changes: Int, folded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (folded) -90f else 0f, tween(220), label = "day-fold")
    val name = when (day) {
        null -> "Unknown date"
        today -> "Today · ${weekdayDate(day)}"
        today.minus(DatePeriod(days = 1)) -> "Yesterday · ${weekdayDate(day)}"
        else -> weekdayDate(day)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle)
        .background(if (folded) Kit.Canvas else Color.Transparent).padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.ExpandMore, if (folded) "Open $name" else "Fold $name", Modifier.size(18.dp).graphicsLayer { rotationZ = turn }, tint = Kit.Green)
        Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(Kit.Border))
        Text("$changes ${if (changes == 1) "change" else "changes"}${if (folded) " · folded" else ""}", fontFamily = Inter(), fontSize = 11.sp,
            color = Kit.Muted, maxLines = 1)
    }
}

/** One change: time, the item's picture and name, where / who / why, the kind as a chip, and the amount (and its value). */
@Composable
private fun MovementLine(move: StockMovementDto, item: StockItemDto?, zone: TimeZone, compact: Boolean) {
    val look = movementLook(move.movementType)
    val delta = move.quantityDelta.asDouble()
    val unit = unitShort(move.unit ?: item?.baseUnit)
    // A phone: tap a line to read all of it (place, who and the note), tap again to fold it (user, 2026-10-30).
    var open by remember(move.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().then(if (compact) Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = !open } else Modifier),
        shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!compact) Text(timeText(move.occurredAt, zone), Modifier.width(42.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = Kit.Muted, maxLines = 1)
            if (item != null) StockItemThumb(item, 38.dp) else ItemThumb(null, null, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(move.inventoryItemName ?: item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink,
                    maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                val where = listOfNotNull(move.locationName, move.createdByUserName?.let { "by $it" }, move.reason?.takeIf { it.isNotBlank() }?.let { "“$it”" })
                if (compact) {
                    // The kind leads in its own colour (no room for the chip); the time sits under the amount.
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = look.color, fontWeight = FontWeight.SemiBold)) { append(look.label) }
                        where.forEach { append(" · "); append(it) }
                    }, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = if (open) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis)
                } else {
                    Text(where.joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!compact) KindChip(look)
            Column(Modifier.widthIn(min = if (compact) 56.dp else 92.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${if (delta > 0) "+" else ""}${plainNumber(move.quantityDelta)} $unit", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = if (delta >= 0) Kit.Green else Kit.Danger, textAlign = TextAlign.End, maxLines = 1)
                if (compact) Text(timeText(move.occurredAt, zone), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                else move.totalCostDelta?.asDouble()?.takeIf { abs(it) >= 0.005 }?.let { value ->
                    Text("${if (value > 0) "+" else "−"}${moneyText((abs(value) * 100).roundToLong(), "EUR")}", fontFamily = Inter(), fontSize = 11.sp,
                        color = Kit.Muted, maxLines = 1)
                }
            }
        }
    }
}

/** Something that happened to an item itself: time, picture and name, "by whom · what changed", and Added / Changed / Deleted. */
@Composable
private fun ItemEventLine(event: StockItemEventDto, item: StockItemDto?, zone: TimeZone, compact: Boolean) {
    val look = eventLook(event.action)
    val detail = listOfNotNull(event.createdByUserName?.let { "by $it" }, event.summary.takeIf { it.isNotBlank() && event.action == "ITEM_UPDATED" })
    var open by remember(event.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().then(if (compact) Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = !open } else Modifier),
        shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!compact) Text(timeText(event.occurredAt, zone), Modifier.width(42.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = Kit.Muted, maxLines = 1)
            if (item != null) StockItemThumb(item, 38.dp) else ItemThumb(null, null, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.itemName ?: item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink,
                    maxLines = if (compact && open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                Text(buildAnnotatedString {
                    if (compact) withStyle(SpanStyle(color = look.color, fontWeight = FontWeight.SemiBold)) { append(look.label); if (detail.isNotEmpty()) append(" · ") }
                    append(detail.joinToString(" · "))
                }, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = if (compact && open) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (!compact) KindChip(look)
            Box(Modifier.widthIn(min = if (compact) 56.dp else 92.dp), Alignment.CenterEnd) {
                if (compact) Text(timeText(event.occurredAt, zone), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
        }
    }
}

/** A phone's one Filters button: kind of change, day (any, today, yesterday or one from the calendar) and who. */
@Composable
private fun HistoryPhoneFilters(state: InventoryState, model: InventoryScreenModel, itemsMode: Boolean, eventKind: String?, onEventKind: (String?) -> Unit) {
    val today = Clock.System.now().toLocalDateTime(RestaurantTime.zone).date
    val yesterday = today.minus(DatePeriod(days = 1))
    val picked = state.movementDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val otherDay = picked?.takeIf { it != today && it != yesterday }
    val active = listOf(if (itemsMode) eventKind != null else state.movementType != null, picked != null, state.movementPersonId != null).count { it }
    var calendar by remember { mutableStateOf(false) }
    PhoneOptionsButton(Icons.Outlined.Tune, "Filters", active) { close ->
        OptionGroup("Kind of change")
        if (itemsMode) ItemEventKinds.forEach { kind ->
            val look = kind?.let(::eventLook)
            OptionLine(look?.label ?: "Every item change", eventKind == kind, { onEventKind(kind); close() }, icon = look?.icon ?: Icons.Outlined.EditNote,
                color = look?.color ?: Kit.Green)
        } else HistoryTypes.forEach { type ->
            val look = type?.let(::historyLook)
            OptionLine(look?.label ?: "Every change", state.movementType == type, { model.historyFilter(type, state.movementItemId); close() },
                icon = look?.icon ?: Icons.Outlined.History, color = look?.color ?: Kit.Green)
        }
        OptionGroup("Day")
        OptionLine("Any day", picked == null, { model.historyDay(null); close() }, icon = Icons.Outlined.CalendarToday)
        OptionLine("Today", picked == today, { model.historyDay(today.toString()); close() }, icon = Icons.Outlined.CalendarToday)
        OptionLine("Yesterday", picked == yesterday, { model.historyDay(yesterday.toString()); close() }, icon = Icons.Outlined.CalendarToday)
        OptionLine(otherDay?.let(::weekdayDate) ?: "Pick a day", otherDay != null, { calendar = !calendar }, icon = Icons.Outlined.DateRange)
        if (calendar) Box(Modifier.padding(horizontal = 6.dp)) {
            CompactDatePicker(picked ?: today) { model.historyDay(it.toString()); calendar = false; close() }
        }
        OptionGroup("Who")
        OptionLine("Everyone", state.movementPersonId == null, { model.historyPerson(null); close() }, icon = Icons.Outlined.Groups)
        state.movementPeople.forEach { person ->
            OptionLine(person.name, state.movementPersonId == person.id, { model.historyPerson(person.id); close() }, icon = Icons.Outlined.Person,
                color = Kit.Blue, count = person.count.toInt())
        }
    }
}

/** Stock | Items | Counts: which history shows. */
@Composable
private fun HistoryModeSwitch(selected: String, onSwitch: (String) -> Unit, modifier: Modifier) {
    OverviewTabs(listOf("Stock", "Items", "Counts"), selected, onSwitch, modifier, height = 40.dp)
}

@Composable
private fun CountHistoryLine(count: StockCountDto, zone: TimeZone, compact: Boolean, onClick: () -> Unit) {
    val date = count.approvedAt?.let { timeText(it, zone) }
    val variance = count.varianceValue?.asDouble()
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.AutoMirrored.Outlined.FactCheck, Kit.Amber, if (compact) 38.dp else 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(count.locationName ?: "Stock count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Kit.Amber, fontWeight = FontWeight.SemiBold)) { append("Approved count") }
                    append(" · "); append("${count.lines.size} ${if (count.lines.size == 1) "item" else "items"}")
                    count.approvedByUserName?.let { append(" · by $it") }
                }, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                count.countNumber?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted) }
                Text(date ?: "", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                if (variance != null && kotlin.math.abs(variance) >= 0.005) {
                    Text("${if (variance > 0) "+" else "−"}${moneyText((kotlin.math.abs(variance) * 100).roundToLong(), "EUR")}", fontFamily = Inter(),
                        fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (variance > 0) Kit.Green else Kit.Danger)
                }
            }
        }
    }
}

/** History → day: "Any day" or the picked day, with a calendar and "Any day" to let go. */
@Composable
private fun DayFilter(day: String?, onPick: (String?) -> Unit, modifier: Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val picked = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Box(modifier) {
        Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(if (compact) PhoneControlHeight else PageControlHeight),
            shape = RoundedCornerShape(8.dp), color = if (picked != null) Kit.GreenSoft else Color.White,
            border = BorderStroke(1.dp, if (open || picked != null) Kit.Green else Kit.Border)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = Kit.Green)
                Text(picked?.let(::weekdayDate) ?: "Any day", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (picked != null) Icon(Icons.Outlined.Close, "Any day", Modifier.size(16.dp).clip(CircleShape).clickable { onPick(null) }, tint = Kit.Muted)
                else Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Muted)
            }
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White,
            shadowElevation = 8.dp) {
            CompactDatePicker(picked ?: Clock.System.now().toLocalDateTime(RestaurantTime.zone).date) { onPick(it.toString()); open = false }
            if (picked != null) Box(Modifier.padding(start = 8.dp, bottom = 6.dp)) { SmallLink("Any day", Icons.Outlined.Close) { onPick(null); open = false } }
        }
    }
}

/** History → person: everyone, or one person who changed stock or items (with how many changes). */
@Composable
private fun PersonFilter(state: InventoryState, model: InventoryScreenModel, modifier: Modifier, compact: Boolean = false) {
    val chosen = state.movementPeople.firstOrNull { it.id == state.movementPersonId }
    SelectInput<StockPersonDto?>(chosen, listOf<StockPersonDto?>(null) + state.movementPeople,
        { person -> person?.let { "${it.name} (${it.count})" } ?: "Everyone" }, { model.historyPerson(it?.id) }, modifier,
        icon = Icons.Outlined.Person, placeholder = "Everyone", compact = compact,
        // Each choice with an icon (user, 2026-10-30): a group for everyone, a person for each name.
        iconOf = { if (it == null) Icons.Outlined.Groups else Icons.Outlined.Person }, iconColorOf = { if (it == null) Kit.Green else Kit.Blue })
}

@Composable
private fun KindChip(look: MoveLook) {
    Row(Modifier.width(132.dp), horizontalArrangement = Arrangement.Start) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(look.color.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(look.icon, null, Modifier.size(14.dp), tint = look.color)
            Text(look.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Ink, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
    }
}
