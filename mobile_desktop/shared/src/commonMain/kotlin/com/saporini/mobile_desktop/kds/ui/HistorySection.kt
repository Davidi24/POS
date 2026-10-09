package com.saporini.mobile_desktop.kds.ui

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

/** Finished and cancelled tickets: what went out, when, and how long it took. */
@Composable
internal fun HistorySection(state: KdsState, model: KdsScreenModel, size: ScreenSize) {
    val history = state.history
    val filter = history.filter
    val range = rangeOf(filter)
    val served = history.items.filter { it.status == "COMPLETED" }
    val cook = served.mapNotNull { it.cookMinutes() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("History", "Served and cancelled tickets, newest first") {
            RefreshButton(history.loading, { model.refresh() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterPills(HistoryRange.entries.map { it to it.label }, range, { model.setHistoryFilter(rangeFilter(it ?: HistoryRange.ALL, filter.status)) })
            if (!size.isPhone) FilterPills(listOf<Pair<String?, String>>(null to "All", "COMPLETED" to "Served", "CANCELLED" to "Cancelled"), filter.status,
                { status -> model.setHistoryFilter(filter.copy(status = status)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactStat("Tickets", "${history.totalElements}", Kit.Green, Modifier.weight(1f), icon = Icons.Outlined.History,
                detail = if (history.hasNext) "${history.items.size} loaded" else null)
            CompactStat("Served", "${served.size}", Kit.Blue, Modifier.weight(1f), icon = Icons.Outlined.DoneAll, detail = "in the list")
            CompactStat("Average time", cook.takeIf { it.isNotEmpty() }?.let { "${it.average().toLong()} min" } ?: "–", Kit.Amber, Modifier.weight(1f),
                icon = Icons.Outlined.Timer, detail = "sent to ready")
            if (!size.isPhone) CompactStat("Slowest", cook.maxOrNull()?.let { "$it min" } ?: "–", Kit.Purple, Modifier.weight(1f), icon = Icons.Outlined.Speed)
        }
        OverviewPanel(Icons.Outlined.History, "Tickets", Modifier.fillMaxWidth().weight(1f), count = history.items.size) {
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
                    val listState = rememberLazyListState()
                    LoadMoreWhenNearEnd(listState, history.hasNext, history.loading, onLoadMore = { model.loadMoreHistory() })
                    val byDay = history.items.groupBy { localOf(it.completedAt ?: it.updatedAt, zone)?.date }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)) {
                        byDay.forEach { (day, tickets) ->
                            item(key = "d-$day") { GroupLabel(day?.let(::weekdayDate) ?: "Unknown day", "${tickets.size} tickets") }
                            items(tickets, key = { it.id }) { ticket -> HistoryCard(ticket, size.isPhone, state.posTiming.slowAfterMinutes) { model.selectTicket(ticket.id) } }
                        }
                        item(key = "footer") {
                            PagedFooter(history.items.size, history.totalElements, history.hasNext, history.loading && history.items.isNotEmpty(), history.error != null,
                                { model.loadMoreHistory() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(ticket: KdsTicket, compact: Boolean, slowAfter: Int, onOpen: () -> Unit) {
    val zone = RestaurantTime.zone
    val cancelled = ticket.status == "CANCELLED"
    val minutes = ticket.cookMinutes()
    StripCard(if (cancelled) Kit.Grey else Kit.Green, minHeight = 64.dp, chevron = true, faded = cancelled, onClick = onOpen) {
        if (!compact) {
            TimeColumn(timeText(ticket.completedAt ?: ticket.updatedAt, zone), if (cancelled) "Cancelled" else "Served", if (cancelled) Kit.Grey else Kit.Green,
                Modifier.width(84.dp), icon = if (cancelled) Icons.Outlined.Cancel else Icons.Outlined.DoneAll)
            CardDivider()
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(listOfNotNull(ticket.title(), ticket.stationName).joinToString(" · "), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ticket.items.joinToString(", ") { "${it.quantity}× ${it.itemNameSnapshot}" }, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            ticket.voidReason?.takeIf { cancelled && it.isNotBlank() }?.let { Text("“$it”", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
        }
        if (minutes != null && !cancelled) StatusPill("$minutes min", if (minutes >= slowAfter) Kit.Amber else Kit.Green)
    }
}
