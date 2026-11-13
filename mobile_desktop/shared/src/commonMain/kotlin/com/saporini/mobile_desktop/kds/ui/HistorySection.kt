package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.saporini.mobile_desktop.core.components.ChevronCircle
import com.saporini.mobile_desktop.core.ui.PlatformVerticalScrollbar
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.workspace_kds
import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.shifts_worked
import mobile_desktop.shared.generated.resources.overview_next_hours
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.CompactStat
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SkeletonListPanel
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.TimeColumn
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.model.KdsHistoryFilter
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.asInstant
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The day ranges the history offers, in restaurant days. */
private enum class HistoryRange(val label: String, val days: Int, val endsDaysAgo: Int) {
    TODAY("Today", 1, 0), YESTERDAY("Yesterday", 1, 1), WEEK("Last 7 days", 7, 0), ALL("Everything", 0, 0)
}

private fun rangeFilter(range: HistoryRange, status: String?): KdsHistoryFilter {
    if (range == HistoryRange.ALL) return KdsHistoryFilter(status = status)
    val zone = RestaurantTime.zone
    val today = Clock.System.todayIn(zone)
    val lastDay: LocalDate = today.minus(DatePeriod(days = range.endsDaysAgo))
    val firstDay = lastDay.minus(DatePeriod(days = range.days - 1))
    return KdsHistoryFilter(firstDay.atStartOfDayIn(zone), lastDay.plus(DatePeriod(days = 1)).atStartOfDayIn(zone), status)
}

private fun rangeOf(filter: KdsHistoryFilter): HistoryRange? =
    HistoryRange.entries.firstOrNull { rangeFilter(it, filter.status).let { f -> f.from == filter.from && f.to == filter.to } }

/** Minutes from sent to ready: how long the kitchen took. */
private fun KdsTicket.cookMinutes(): Long? {
    val start = since() ?: return null
    val end = readyAt.asInstant() ?: completedAt.asInstant() ?: return null
    return (end - start).inWholeMinutes.coerceAtLeast(0)
}

/**
 * Finished and cancelled tickets, laid out like Inventory → Stock (user, 2026-10-29): one white panel with the period and
 * status on top, column heads, and a line per ticket with a strip in its status colour, the dishes' photos, how long the
 * kitchen took, when it went out and its status.
 */
@Composable
internal fun HistorySection(state: KdsState, model: KdsScreenModel, size: ScreenSize) {
    val history = state.history
    val filter = history.filter
    val range = rangeOf(filter)
    val served = history.items.filter { it.status == "COMPLETED" }
    val cook = served.mapNotNull { it.cookMinutes() }
    val photos = remember(state.menu.items) { state.menu.items.associate { it.item.id to it.item.imageUrl } }
    val phone = size.isPhone
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("History", listOfNotNull("${history.totalElements} tickets", cook.takeIf { it.isNotEmpty() }?.let { "on average ${it.average().toLong()} min to cook" },
            cook.maxOrNull()?.let { "slowest $it min" }).joinToString(" · ")) {
            RefreshButton(history.loading, { model.refresh() })
        }
        OverviewPanel(Icons.Outlined.History, "Finished tickets", Modifier.fillMaxWidth().weight(1f), count = history.items.size, flat = phone) {
            // The period and the status, like Stock's search and sort row.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterPills(HistoryRange.entries.map { it to it.label }, range, { model.setHistoryFilter(rangeFilter(it ?: HistoryRange.ALL, filter.status)) }, Modifier.weight(1f))
                if (!phone) FilterPills(listOf<Pair<String?, String>>(null to "All", "COMPLETED" to "Served", "CANCELLED" to "Cancelled"), filter.status,
                    { status -> model.setHistoryFilter(filter.copy(status = status)) })
            }
            val zone = RestaurantTime.zone
            when {
                history.items.isEmpty() && history.loading -> SkeletonListPanel(Modifier.fillMaxWidth().weight(1f), 6, 64.dp)
                history.items.isEmpty() && history.error != null -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("Couldn't load the history", history.error.message, Icons.Outlined.WarningAmber, action = { RetryText(onClick = { model.refresh() }) })
                }
                history.items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("Nothing here yet", "Tickets show here once they're served or cancelled.", Icons.Outlined.History)
                }
                else -> {
                    if (!phone) HistoryHead()
                    val listState = rememberLazyListState()
                    LoadMoreWhenNearEnd(listState, history.hasNext, history.loading, onLoadMore = { model.loadMoreHistory() })
                    val byDay = history.items.groupBy { localOf(it.completedAt ?: it.updatedAt, zone)?.date }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 8.dp, bottom = 4.dp)) {
                            byDay.forEach { (day, tickets) ->
                                item(key = "d-$day") { GroupLabel(day?.let(::weekdayDate) ?: "Unknown day", "${tickets.size} tickets") }
                                items(tickets, key = { it.id }) { ticket ->
                                    HistoryLine(ticket, phone, state.posTiming.slowAfterMinutes, photos) { model.selectTicket(ticket.id) }
                                }
                            }
                            item(key = "footer") {
                                PagedFooter(history.items.size, history.totalElements, history.hasNext, history.loading && history.items.isNotEmpty(), history.error != null,
                                    { model.loadMoreHistory() })
                            }
                        }
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) { PlatformVerticalScrollbar(listState, Modifier.fillMaxHeight().width(3.dp)) }
                    }
                }
            }
        }
    }
}

// The columns, as wide as in each line (like Stock's ITEM / PLACE / IN STOCK / … heads).
private const val ColTicket = 1.5f
private const val ColDishes = 1.7f
private const val ColTook = 0.8f
private const val ColOut = 0.8f
private const val ColStatus = 0.9f

@Composable
private fun HistoryHead() {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 50.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Ticket" to ColTicket, "Dishes" to ColDishes, "Took" to ColTook, "Went out" to ColOut, "Status" to ColStatus).forEach { (label, weight) ->
            Text(label.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.6.sp,
                color = Kit.Muted, maxLines = 1)
        }
    }
}

/** One finished ticket as a Stock line: strip, who it was for, the dishes with photos, the time taken, when, the status. */
@Composable
private fun HistoryLine(ticket: KdsTicket, phone: Boolean, slowAfter: Int, photos: Map<String, String?>, onOpen: () -> Unit) {
    val zone = RestaurantTime.zone
    val cancelled = ticket.status == "CANCELLED"
    val minutes = ticket.cookMinutes()
    val slow = minutes != null && minutes >= slowAfter
    val tone = when { cancelled -> Kit.Grey; slow -> Kit.Amber; else -> Kit.Green }
    val orderKind = ticket.kind()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().hoverable(interaction), shape = RoundedCornerShape(12.dp),
        color = if (hovered) Kit.Canvas else Color.White, border = BorderStroke(1.dp, Kit.RowBorder), interactionSource = interaction) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(if (cancelled) 0.7f else 1f)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(tone))
            Row(Modifier.weight(1f).heightIn(min = 62.dp).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Ticket: the first dish's photo, the number and where it went.
                Row(Modifier.weight(ColTicket), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DishPhoto(ticket.items.firstOrNull()?.menuItemId?.let { photos[it] }, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("#${ticket.ticketNumber.ifBlank { ticket.orderNumber }.takeLast(6)} · ${if (orderKind == OrderKind.DINE_IN) ticket.title() else ticket.customerName ?: orderKind.label}",
                            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(orderKind.label, ticket.stationName, ticket.guestCount?.let { if (it == 1) "1 guest" else "$it guests" },
                            if (phone) "${timeText(ticket.completedAt ?: ticket.updatedAt, zone)}${minutes?.let { " · $it min" } ?: ""}" else null,
                            ticket.voidReason?.takeIf { phone && cancelled && it.isNotBlank() }?.let { "“$it”" }).joinToString(" · "),
                            fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!phone) {
                    Column(Modifier.weight(ColDishes), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ticket.items.joinToString(", ") { "${it.quantity}× ${it.itemNameSnapshot}" }, fontFamily = Inter(),
                            fontSize = 12.5.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ticket.voidReason?.takeIf { cancelled && it.isNotBlank() }?.let {
                            Text("“$it”", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.weight(ColTook), verticalAlignment = Alignment.Bottom) {
                        Text(minutes?.toString() ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (slow) Kit.Amber else Kit.Ink)
                        if (minutes != null) Text(" min", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                    }
                    Column(Modifier.weight(ColOut), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(timeText(ticket.completedAt ?: ticket.updatedAt, zone), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                        Text("sent ${timeText(ticket.firedAt ?: ticket.createdAt, zone)}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                    Box(Modifier.weight(ColStatus)) {
                        StatusPill(when { cancelled -> "Cancelled"; slow -> "Served late"; else -> "Served" }, tone)
                    }
                } else StatusPill(if (cancelled) "Cancelled" else minutes?.let { "$it min" } ?: "Served", tone)
                ChevronCircle(hovered)
            }
        }
    }
}
