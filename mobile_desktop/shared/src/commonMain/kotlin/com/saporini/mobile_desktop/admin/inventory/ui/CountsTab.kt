package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import mobile_desktop.shared.generated.resources.inventory_stock_items
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.components.CountPill
import androidx.compose.runtime.saveable.rememberSaveable
import com.saporini.mobile_desktop.core.components.FilterRow
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_TEXT
import com.saporini.mobile_desktop.admin.inventory.StockCountDto
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.countSteps
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_reservations
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlin.time.Clock

private val CountStatuses = listOf<Pair<String?, String>>(null to "All", "DRAFT" to "Not started", "IN_PROGRESS" to "Counting",
    "COMPLETED" to "Waiting for approval", "APPROVED" to "Approved", "CANCELLED" to "Cancelled")

/** Counts not finished yet: not started, counting, or waiting for approval. A place should have at most one. */
private val OpenCountStatuses = setOf("DRAFT", "IN_PROGRESS", "COMPLETED")

/**
 * Inventory → Counts: checking what's really on the shelves, one place at a time. The list puts what still needs doing
 * first ("To do"), each count with what it's at and the button for its next step.
 * The side starts a count for a place (or continues the one already open) and filters by step.
 */
@Composable
internal fun CountsTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    val list: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(Icons.AutoMirrored.Outlined.FactCheck, "Stock counts", m, count = state.counts.size, bottomPadding = 0.dp, flat = size.isPhone) {
            if (!size.isDesktop) CountScheduleContent(state, model, compact = true)
            if (!size.isDesktop) CountSection("Count sessions", state.counts.size, "latest first")
            if (!size.isDesktop) FilterPills(CountStatuses, state.countStatus, model::countFilter)
            if (state.counts.isEmpty() && state.loading) {
                ListLinesSkeleton(6, 64.dp, Modifier.fillMaxWidth().weight(1f))
                return@OverviewPanel
            }
            if (state.counts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty(if (state.countStatus == null) "No counts yet" else "No counts like that",
                        "Count a place to check what's really on the shelves. Nothing changes in stock until you approve the count.",
                        Icons.AutoMirrored.Outlined.FactCheck, image = Res.drawable.inventory_stock_items)
                }
                return@OverviewPanel
            }
            val listState = rememberLazyListState()
            LoadMoreWhenNearEnd(listState, state.countsHasNext, state.loading || state.countsLoadingMore, onLoadMore = model::loadMoreCounts)
            // Without a step filter: what still needs doing first (waiting for approval, then counting, then not started), the rest under "Done".
            val urgency = listOf("COMPLETED", "IN_PROGRESS", "DRAFT")
            val toDo = state.counts.filter { it.status in OpenCountStatuses }
                .sortedWith(compareBy<StockCountDto> { urgency.indexOf(it.status) }.thenByDescending { it.updatedAt.orEmpty() })
            val done = state.counts.filter { it.status !in OpenCountStatuses }.sortedByDescending { it.updatedAt.orEmpty() }
            // Each count is a card (user, 2026-11-11: "make this with cards, a great card"): three a row when they fit, two on a
            // small window, one on a phone; the cards of a row keep one height.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val columns = when {
                    maxWidth >= 840.dp -> 3
                    maxWidth >= 520.dp -> 2
                    else -> 1
                }
                ScrollList(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fun cards(key: String, counts: List<StockCountDto>) {
                        items(counts.chunked(columns), key = { row -> "$key-${row.first().id}" }) { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { count ->
                                    CountCard(count, state, Modifier.weight(1f).fillMaxHeight()) { model.openCount(count.id) }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    if (state.countStatus != null) {
                        cards("all", state.counts)
                    } else {
                        if (toDo.isNotEmpty()) {
                            item(key = "todo") { CountSection("To do", toDo.size, "not finished yet") }
                            cards("todo", toDo)
                        }
                        if (done.isNotEmpty()) {
                            item(key = "done") { CountSection("Done", done.size, "approved or cancelled") }
                            cards("done", done)
                        }
                    }
                    item(key = "footer") { PagedFooter(state.counts.size, null, state.countsHasNext, state.countsLoadingMore, state.error != null, model::loadMoreCounts) }
                }
            }
        }
    }
    if (!size.isDesktop) {
        list(modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ScrollColumn(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CountSchedulePanel(state, model)
            OverviewPanel(Icons.Outlined.Timeline, "Show", Modifier.fillMaxWidth()) {
                CountStatuses.forEach { (status, label) ->
                    // The server's totals per step, loaded with the tab: picking a step leaves every number as it is.
                    val count = state.countStatusTotals?.let { totals -> if (status == null) totals.values.sum() else totals[status] ?: 0 }?.toInt()
                    FilterRow(if (status == null) Icons.AutoMirrored.Outlined.FactCheck else countStepIcon(status), if (status == null) "Every count" else label, count,
                        if (status == null) Kit.Green else countStatusColor(status), state.countStatus == status) {
                        model.countFilter(if (state.countStatus == status) null else status)
                    }
                }
            }
        }
        list(Modifier.weight(1f).fillMaxHeight())
    }
}

private data class PlaceCountSchedule(
    val place: StockLocationDto,
    val state: String,
    val detail: String,
    val approvedThisWeek: Int,
    val target: Int,
    val openCount: StockCountDto?,
    val dueToday: Boolean,
    val missed: Boolean,
    val doneThisWeek: Boolean
)

@Composable
private fun CountSchedulePanel(state: InventoryState, model: InventoryScreenModel) {
    OverviewPanel(Icons.Outlined.CalendarToday, "Counting schedule", Modifier.fillMaxWidth()) {
        CountScheduleContent(state, model, compact = false)
    }
}

@Composable
private fun CountScheduleContent(state: InventoryState, model: InventoryScreenModel, compact: Boolean) {
    var selected by rememberSaveable { mutableStateOf("DUE_TODAY") }
    val now = Clock.System.now().toLocalDateTime(RestaurantTime.zone)
    val weekStart = now.date.minus(DatePeriod(days = now.date.dayOfWeek.isoDayNumber - 1))
    val scheduleStart = state.countSchedule.startedAt?.let { localOf(it, RestaurantTime.zone)?.date } ?: weekStart
    val startDate = if (scheduleStart < weekStart) weekStart else scheduleStart
    val daily = state.countSchedule.period == "DAILY"
    val frequency = state.countSchedule.frequency.coerceIn(1, if (daily) 24 else 7)
    val approvedByPlace = state.scheduleCounts.filter { it.status == "APPROVED" }
        .groupBy { it.locationId }
    val openByPlace = state.counts.filter { it.status in OpenCountStatuses }
        .groupBy { it.locationId }
    val places = state.activeLocations.sortedBy { it.name.lowercase() }.map { place ->
        val approved = approvedByPlace[place.id].orEmpty().mapNotNull { count ->
            count.approvedAt?.let { localOf(it, RestaurantTime.zone)?.date }
        }.filter { it >= startDate && it <= now.date }
        val byDay = approved.groupingBy { it }.eachCount()
        val dueDays = if (daily) emptyList() else (0 until frequency).map { slot ->
            weekStart.plus(DatePeriod(days = (((slot + 1) * 7) / frequency) - 1))
        }.filter { it >= startDate }
        val pastDays = if (daily) {
            (0 until (now.date.toEpochDays() - startDate.toEpochDays()).toInt().coerceIn(0, 6)).map { startDate.plus(DatePeriod(days = it)) }
        } else dueDays.filter { it < now.date }
        val missed = if (daily) pastDays.any { (byDay[it] ?: 0) < frequency }
            else pastDays.any { (byDay[it] ?: 0) < 1 }
        val todayCount = byDay[now.date] ?: 0
        val dueToday = if (daily) todayCount < frequency else now.date in dueDays && todayCount < 1
        val completed = approved.size
        val expectedThroughToday = if (daily) {
            ((now.date.toEpochDays() - startDate.toEpochDays()).toInt().coerceAtLeast(0) + 1) * frequency
        } else dueDays.count { it <= now.date }
        val caughtUp = expectedThroughToday > 0 && completed >= expectedThroughToday && !missed && !dueToday
        val status = when {
            missed -> "MISSED"
            dueToday -> "DUE_TODAY"
            caughtUp -> "DONE_THIS_WEEK"
            else -> "UPCOMING"
        }
        val target = if (daily) frequency * 7 else frequency
        val detail = when (status) {
            "MISSED" -> "Missed scheduled count · $completed of $target this week"
            "DUE_TODAY" -> "Due today · $completed of $target this week"
            "DONE_THIS_WEEK" -> "On schedule · $completed of $target this week"
            else -> {
                val next = if (daily) "today" else dueDays.firstOrNull { it > now.date }?.let(::weekdayDate) ?: "next week"
                "Next count $next · $completed of $target this week"
            }
        }
        PlaceCountSchedule(place, status, detail, completed, target, openByPlace[place.id].orEmpty().maxByOrNull { it.updatedAt.orEmpty() },
            dueToday, missed, caughtUp)
    }
    val due = places.count { it.dueToday }
    val missed = places.count { it.missed }
    val done = places.count { it.doneThisWeek }
    val upcoming = places.count { it.state == "UPCOMING" }
    val filtered = places.filter { when (selected) {
        "DUE_TODAY" -> it.dueToday
        "MISSED" -> it.missed
        "DONE_THIS_WEEK" -> it.doneThisWeek
        else -> it.state == "UPCOMING"
    } }
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScheduleMetric("Due today", due, Kit.Amber, selected == "DUE_TODAY", Modifier.weight(1f)) { selected = "DUE_TODAY" }
            ScheduleMetric("Missed", missed, Kit.Danger, selected == "MISSED", Modifier.weight(1f)) { selected = "MISSED" }
            ScheduleMetric("Done this week", done, Kit.Green, selected == "DONE_THIS_WEEK", Modifier.weight(1f)) { selected = "DONE_THIS_WEEK" }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Places", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
            Spacer(Modifier.weight(1f))
            SmallLink("Upcoming · $upcoming", Icons.Outlined.ChevronRight) { selected = "UPCOMING" }
        }
        if (filtered.isEmpty()) {
            Text(if (places.isEmpty()) "Add a place on the Stock tab first." else "Nothing in this group right now.",
                Modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 2.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
        } else {
            ScrollColumn(Modifier.fillMaxWidth().heightIn(max = if (compact) 190.dp else 258.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filtered.forEach { entry ->
                    val tone = when (entry.state) {
                        "MISSED" -> Kit.Danger
                        "DUE_TODAY" -> Kit.Amber
                        "DONE_THIS_WEEK" -> Kit.Green
                        else -> Kit.Blue
                    }
                    PlaceToCount(entry.place, entry.detail, tone,
                        if (entry.openCount != null) "Continue" else "Start",
                        onAction = { if (entry.openCount != null) model.openCount(entry.openCount.id) else model.newCount(entry.place.id) },
                        enabled = state.canEdit)
                }
            }
        }
        if (state.countSchedule.period == "DAILY") {
            Text("${frequency} ${if (frequency == 1) "count" else "counts"} per place, every day", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Faint)
        } else {
            Text("${frequency} ${if (frequency == 1) "count" else "counts"} per place, spread across this week", fontFamily = Inter(), fontSize = 10.sp, color = Kit.Faint)
        }
    }
    if (compact) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun ScheduleMetric(label: String, count: Int, tone: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = if (selected) tone.copy(alpha = 0.1f) else Kit.Canvas,
        border = BorderStroke(1.dp, if (selected) tone.copy(alpha = 0.45f) else Kit.RowBorder)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("$count", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = tone)
            Text(label, fontFamily = Inter(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 10.sp,
                color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun countStepIcon(status: String) = when (status) {
    "DRAFT" -> Icons.Outlined.HourglassEmpty
    "IN_PROGRESS" -> Icons.Outlined.Edit
    "COMPLETED" -> Icons.Outlined.Pending
    "APPROVED" -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.Block
}

/** "To do · 3 — not finished yet" above a part of the list. */
@Composable
private fun CountSection(title: String, count: Int, detail: String) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        CountPill(count)
        Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(Kit.Border))
    }
}

/**
 * Every active place with where its counting stands: a count that's still open (Continue), when it was last counted, or
 * never; Start only when no count is open there, so a place never has two.
 */
@Composable
private fun CountAPlacePanel(state: InventoryState, model: InventoryScreenModel) {
    OverviewPanel(Icons.Outlined.Storefront, "Count a place", Modifier.fillMaxWidth()) {
        val zone = RestaurantTime.zone
        val byPlace = state.counts.groupBy { it.locationId }
        val places = state.activeLocations.sortedBy { it.name.lowercase() }
        if (places.isEmpty()) {
            Text("Add a place on the Stock tab first.", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            return@OverviewPanel
        }
        ScrollColumn(Modifier.fillMaxWidth().heightIn(max = 46.dp * 6 + 4.dp * 5), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            places.forEach { place ->
                val counts = byPlace[place.id].orEmpty()
                val open = counts.filter { it.status in OpenCountStatuses }.maxByOrNull { it.updatedAt.orEmpty() }
                val lastApproved = counts.filter { it.status == "APPROVED" }.maxByOrNull { (it.approvedAt ?: it.updatedAt).orEmpty() }
                PlaceToCount(place,
                    status = when {
                        open != null -> if (open.status == "DRAFT") "Open count, not started" else countStatusLabel(open.status)
                        lastApproved != null -> "Last counted ${localOf(lastApproved.approvedAt ?: lastApproved.updatedAt, zone)?.date?.let(::weekdayDate) ?: ""}"
                        else -> "Never counted"
                    },
                    statusColor = open?.let { countStatusColor(it.status) } ?: Kit.Muted,
                    action = if (open != null) "Continue" else "Start",
                    onAction = { if (open != null) model.openCount(open.id) else model.newCount(place.id) })
            }
        }
    }
}

/** A place with where its counting stands and a Start or Continue button. */
@Composable
private fun PlaceToCount(place: StockLocationDto, status: String, statusColor: Color, action: String, onAction: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(placeTypeColor(place.locationType).copy(alpha = 0.12f)), Alignment.Center) {
            PlaceIconImage(place, Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(place.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, fontFamily = Inter(), fontWeight = if (statusColor == Kit.Muted) FontWeight.Normal else FontWeight.SemiBold, fontSize = 11.sp,
                color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) SmallLink(action, if (action == "Start") Icons.Outlined.PlayArrow else Icons.Outlined.Edit, onAction)
    }
}

/** What a count is at, in words, and the button for its next step. */
private class CountStage(val detail: String, val action: String, val strongAction: Boolean)

private fun countStage(count: StockCountDto, zone: kotlinx.datetime.TimeZone): CountStage {
    val counted = count.lines.count { it.countedQuantity != null }
    val total = count.lines.size
    return when (count.status) {
        "DRAFT" -> CountStage("Nothing counted yet", "Start counting", strongAction = true)
        "IN_PROGRESS" -> CountStage(if (total == 0) "No items counted yet" else "$counted of $total items counted", "Continue", strongAction = true)
        "COMPLETED" -> CountStage("All counted · check it and approve", "Review", strongAction = true)
        "APPROVED" -> CountStage("Stock updated${count.approvedAt?.let { " ${dateTimeText(it, zone)}" } ?: ""}${count.approvedByUserName?.let { " by $it" } ?: ""}",
            "Open", strongAction = false)
        else -> CountStage("Cancelled · stock didn't change", "Open", strongAction = false)
    }
}

/**
 * One count as a card (user, 2026-11-11: "make the image bigger, make it look better"): the place's picture across the
 * top with the step and the place's name on it; how far the counting got; what it changes in stock value; then when,
 * and the button for the next step. The picture zooms a little on hover; a cancelled count is faded and grey.
 */
@Composable
private fun CountCard(count: StockCountDto, state: InventoryState, modifier: Modifier, onClick: () -> Unit) {
    val tone = countStatusColor(count.status)
    val zone = RestaurantTime.zone
    val place = state.location(count.locationId)
    val stage = countStage(count, zone)
    val counted = count.lines.count { it.countedQuantity != null }
    val total = count.lines.size
    val cancelled = count.status == "CANCELLED"
    val finished = count.status == "APPROVED" || count.status == "COMPLETED"
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val zoom by animateFloatAsState(if (hovered) 1.06f else 1f, tween(320))
    val name = count.locationName ?: place?.name ?: "Place"
    val kind = place?.kindLabel()?.takeUnless { it.equals(name, ignoreCase = true) }
    Surface(onClick = onClick, modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand).alpha(if (cancelled) 0.6f else 1f),
        shape = RoundedCornerShape(18.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) tone.copy(alpha = 0.5f) else Kit.RowBorder),
        shadowElevation = if (hovered) 8.dp else 2.dp, interactionSource = interaction) {
        Column {
            // The picture, big, with the step on top and the place's name over its foot.
            PlaceBanner(place, name, listOfNotNull(kind, count.createdByUserName?.let { "by $it" }).joinToString(" · ").ifEmpty { "A stock count" },
                zoom, grey = cancelled, fallback = tone) {
                BannerPill(countStepIcon(count.status), countStatusLabel(count.status), tone, Modifier.align(Alignment.TopStart))
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                // How far it got.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stage.detail, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp,
                            color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!cancelled && (finished || total > 0)) {
                            Text("${if (finished) 100 else counted * 100 / total}%", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                                letterSpacing = 0.sp, color = tone, maxLines = 1)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.14f))) {
                        val share = when { finished -> 1f; total > 0 -> counted.toFloat() / total; else -> 0f }
                        if (share > 0f && !cancelled) Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(RoundedCornerShape(50)).background(tone))
                    }
                }
                // What it changes in stock value.
                val variance = count.varianceValue?.asDouble()?.takeIf { counted > 0 }
                val varianceColor = when { variance == null || variance == 0.0 -> Kit.Ink; variance < 0 -> Kit.Danger; else -> Kit.Green }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (variance == null || variance == 0.0) Kit.Canvas else varianceColor.copy(alpha = 0.07f))
                    .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("Difference", fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1)
                        Text(when {
                            variance == null -> "Nothing counted yet"
                            variance > 0 -> "More than expected"
                            variance < 0 -> "Less than expected"
                            else -> "As expected"
                        }, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
                    }
                    Text(variance?.let { (if (it > 0) "+" else "") + money(count.varianceValue, null) } ?: "–", fontFamily = Inter(), fontWeight = FontWeight.Bold,
                        fontSize = 17.sp, letterSpacing = 0.sp, maxLines = 1, color = varianceColor)
                }
                count.notes?.takeIf { it.isNotBlank() }?.let {
                    Text("“$it”", fontFamily = Inter(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 12.sp, letterSpacing = 0.sp,
                        color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                // When it last changed, and its next step.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = Kit.Faint)
                        Text(dateTimeText(count.updatedAt, zone), fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    // A count not started yet is grey, but its Start counting button stays the app's green.
                    CountActionButton(stage, if (count.status == "DRAFT") Kit.Green else tone, onClick)
                }
            }
        }
    }
}

/** The next step of a count as a button: filled in the step's colour while there's something to do, light once it's done. */
@Composable
private fun CountActionButton(stage: CountStage, tone: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.width(136.dp).height(34.dp), shape = RoundedCornerShape(10.dp),
        color = if (stage.strongAction) tone else Color.White, border = if (stage.strongAction) null else BorderStroke(1.dp, Kit.Border)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text(stage.action, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (stage.strongAction) Color.White else Kit.Ink, maxLines = 1)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = if (stage.strongAction) Color.White else Kit.Muted)
        }
    }
}

/** Choosing the place to count, with an optional note. A place that already has an open count offers to open that one instead. */
@Composable
internal fun StartCountDialog(state: InventoryState, model: InventoryScreenModel, onClose: () -> Unit) {
    var place by remember { mutableStateOf<StockLocationDto?>(state.activeLocations.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    val open = place?.let { chosen -> state.counts.filter { it.locationId == chosen.id && it.status in OpenCountStatuses }.maxByOrNull { it.updatedAt.orEmpty() } }
    AppDialog("Start a stock count", onClose, subtitle = "Count one place at a time. Nothing changes in stock until you approve the count.", maxWidth = 520.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY)
            if (open != null) KitButton("Open that count", { model.openCount(open.id); onClose() }, icon = Icons.AutoMirrored.Outlined.FactCheck)
            else KitButton("Start count", { place?.let { model.newCount(it.id, notes); onClose() } }, icon = Icons.AutoMirrored.Outlined.FactCheck, enabled = place != null)
        }) {
        SelectInput(place, state.activeLocations, { it.name }, { place = it }, label = "Place", icon = Icons.Outlined.Storefront, required = true,
            placeholder = "Choose a place")
        if (open != null) {
            MessageBar("${place?.name ?: "This place"} already has a count that isn't finished (${countStatusLabel(open.status).lowercase()}, " +
                "${dateTimeText(open.updatedAt, RestaurantTime.zone)}). Carry on with that one, or cancel it first.", MessageKind.INFO)
        } else {
            TextInput(notes, { notes = it }, label = "Notes", optional = true, singleLine = false, minLines = 2, icon = Icons.AutoMirrored.Outlined.Notes,
                placeholder = "Monthly count, after the delivery…", maxLength = MAX_STOCK_TEXT)
        }
    }
}

private val CountSteps = listOf("DRAFT" to "Not started", "IN_PROGRESS" to "Counting", "COMPLETED" to "Finished", "APPROVED" to "Approved")

/** One count: the progress, every item of the place with what's expected and what was counted, and its next step. */
@Composable
internal fun CountDialog(count: StockCountDto, state: InventoryState, model: InventoryScreenModel) {
    var query by remember(count.id) { mutableStateOf("") }
    val typed = remember(count.id) { mutableStateMapOf<String, String>() }
    val lines = count.lines.associateBy { it.inventoryItemId }
    val levels = state.levels.filter { it.locationId == count.locationId }.associateBy { it.inventoryItemId }
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    // Items counted already come first, then the ones stocked at this place, then the rest.
    val items = state.items.filter { (it.active && it.trackInventory) || it.id in lines }
        .filter { item -> words.all { it in item.name.lowercase() } }
        .sortedWith(compareBy({ it.id !in lines }, { it.id !in levels }, { it.name.lowercase() }))
    val editable = count.editable && state.canEdit
    val summary = "${count.lines.count { it.countedQuantity != null }} of ${count.lines.size} counted" + (count.varianceValue?.let { " · difference ${money(it, null)}" } ?: "")
    // A phone has no room for the table, the summary in the footer or a Close button (the cross closes): each item is a small card.
    // (Narrow is only known inside the dialog, so it's read there.)
    AppDialog(
        "Count · ${count.locationName ?: "place"}",
        { model.openCount(null) },
        subtitle = listOfNotNull(count.countNumber?.let(::countLabel), count.notes?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null },
        busy = state.saving, maxWidth = 920.dp,
        buttons = {
            val narrow = LocalNarrowDialog.current
            val steps = if (state.canEdit) countSteps(count.status) else emptyList()
            if (!narrow) {
                Text(summary, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            }
            if (!narrow || steps.isEmpty()) KitButton("Close", { model.openCount(null) }, style = ButtonStyle.SECONDARY)
            steps.forEach { step ->
                KitButton(countStepLabel(step), { model.askCountStep(step) }, if (narrow) Modifier.weight(1f) else Modifier,
                    style = when (step) { "cancel" -> ButtonStyle.SECONDARY; else -> ButtonStyle.PRIMARY },
                    icon = if (step == "cancel") Icons.Outlined.Close else Icons.Outlined.Check, loading = state.saving && step != "cancel")
            }
        }
    ) {
        val narrow = LocalNarrowDialog.current
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        CountProgress(count.status, narrow)
        if (narrow) Text(summary, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
        when (count.status) {
            "DRAFT" -> MessageBar("Press “Start counting”, then type what you find on the shelves for each item and press ✓. Stock doesn't change yet.", MessageKind.INFO)
            "IN_PROGRESS" -> MessageBar("Type what's on the shelf for each item and press ✓. When everything is counted, press “Finish count”.", MessageKind.INFO)
            "COMPLETED" -> MessageBar("Check the differences. “Approve and update stock” makes the stock what was counted; the differences are kept as count corrections.", MessageKind.WARNING)
            "APPROVED" -> MessageBar("Approved ${count.approvedAt?.let { dateTimeText(it, RestaurantTime.zone) } ?: ""}${count.approvedByUserName?.let { " by $it" } ?: ""}. The stock now matches this count.", MessageKind.SUCCESS)
            "CANCELLED" -> MessageBar("This count was cancelled; it changed nothing.", MessageKind.INFO)
        }
        SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find an item", height = PageControlHeight)
        if (count.status == "APPROVED") {
            val historyLines = count.lines.filter { line ->
                val name = line.itemNameSnapshot.orEmpty().lowercase()
                words.all { it in name }
            }
            if (historyLines.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 100.dp), Alignment.Center) {
                    Text("No counted items match that search.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                }
            } else if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    historyLines.forEach { CountHistoryDetailLine(it, compact = true) }
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
                    Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("Item" to 1.6f, "Before" to 0.8f, "Counted" to 0.8f, "Change" to 0.8f).forEach { (title, weight) ->
                            Text(title.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                letterSpacing = 0.5.sp, color = Kit.Muted)
                        }
                    }
                    Box(Modifier.heightIn(max = 420.dp)) {
                        ScrollList(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
                            items(historyLines, key = { it.id }) { line ->
                                KitDivider()
                                CountHistoryDetailLine(line, compact = false)
                            }
                        }
                    }
                }
            }
            return@AppDialog
        }
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item -> CountItemCard(item, lines[item.id], lines[item.id]?.expectedQuantity ?: levels[item.id]?.onHandQuantity, editable, state, model, typed) }
            }
            return@AppDialog
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Item" to 1.6f, "Expected" to 0.7f, "Counted" to 1f, "Difference" to 0.8f).forEach { (title, weight) ->
                    Text(title.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = Kit.Muted)
                }
            }
            Box(Modifier.heightIn(max = 420.dp)) {
                ScrollList(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
                    items(items, key = { it.id }) { item ->
                        val line = lines[item.id]
                        val expected = line?.expectedQuantity ?: levels[item.id]?.onHandQuantity
                        KitDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (line?.countedQuantity != null) Kit.Green else Color(0xFFD5D8D3)))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.kindLabel(long = true), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
                                }
                            }
                            Text(expected?.let { "${plainNumber(it)} ${unitShort(item.baseUnit)}" } ?: "–", Modifier.weight(0.7f),
                                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                if (editable) {
                                    val text = typed[item.id] ?: line?.countedQuantity?.let(::plainNumber).orEmpty()
                                    TextInput(text, { typed[item.id] = it }, Modifier.weight(1f), placeholder = "0", keyboardType = KeyboardType.Decimal,
                                        suffix = unitShort(item.baseUnit))
                                    val changed = typed[item.id] != null && typed[item.id] != line?.countedQuantity?.let(::plainNumber)
                                    IconButton({ model.countItem(item.id, text); typed.remove(item.id) }, enabled = changed && !state.saving && text.isNotBlank()) {
                                        Icon(Icons.Outlined.Check, "Save", Modifier.size(19.dp), tint = if (changed) Kit.Green else Kit.Faint)
                                    }
                                } else {
                                    Text(line?.countedQuantity?.let { "${plainNumber(it)} ${unitShort(item.baseUnit)}" } ?: "–", fontFamily = Inter(),
                                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                                }
                            }
                            val variance = line?.varianceQuantity?.asDouble()
                            Column(Modifier.weight(0.8f)) {
                                Text(line?.varianceQuantity?.let { "${if ((variance ?: 0.0) > 0) "+" else ""}${plainNumber(it)}" } ?: "–", fontFamily = Inter(),
                                    fontWeight = FontWeight.Bold, fontSize = 12.sp, color = when { variance == null || variance == 0.0 -> Kit.Muted; variance > 0 -> Kit.Green; else -> Kit.Danger })
                                line?.varianceValue?.let { Text(money(it, null), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountHistoryDetailLine(line: com.saporini.mobile_desktop.admin.inventory.StockCountLineDto, compact: Boolean) {
    val unit = unitShort(line.unit)
    val variance = line.varianceQuantity?.asDouble()
    val tone = when { variance == null || variance == 0.0 -> Kit.Muted; variance > 0 -> Kit.Green; else -> Kit.Danger }
    if (compact) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas).padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.itemNameSnapshot ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Before ${line.expectedQuantity?.let { "${plainNumber(it)} $unit" } ?: "–"}  →  Counted ${line.countedQuantity?.let { "${plainNumber(it)} $unit" } ?: "–"}",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Change ${line.varianceQuantity?.let { "${if ((variance ?: 0.0) > 0) "+" else ""}${plainNumber(it)} $unit" } ?: "–"}",
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tone)
                line.varianceValue?.let { Text("· ${money(it, null)}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(line.itemNameSnapshot ?: "Item", Modifier.weight(1.6f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line.expectedQuantity?.let { "${plainNumber(it)} $unit" } ?: "–", Modifier.weight(0.8f), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            Text(line.countedQuantity?.let { "${plainNumber(it)} $unit" } ?: "–", Modifier.weight(0.8f), fontFamily = Inter(), fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, color = Kit.Ink)
            Column(Modifier.weight(0.8f)) {
                Text(line.varianceQuantity?.let { "${if ((variance ?: 0.0) > 0) "+" else ""}${plainNumber(it)} $unit" } ?: "–",
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tone)
                line.varianceValue?.let { Text(money(it, null), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted) }
            }
        }
    }
}

/** One item of a count on a phone: name and what's expected, the difference, and the counted amount with its save tick. */
@Composable
private fun CountItemCard(
    item: com.saporini.mobile_desktop.admin.inventory.StockItemDto,
    line: com.saporini.mobile_desktop.admin.inventory.StockCountLineDto?,
    expected: com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal?,
    editable: Boolean,
    state: InventoryState,
    model: InventoryScreenModel,
    typed: MutableMap<String, String>
) {
    val unit = unitShort(item.baseUnit)
    val variance = line?.varianceQuantity?.asDouble()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (line?.countedQuantity != null) Kit.GreenSoft.copy(alpha = 0.5f) else Kit.Canvas)
        .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StockItemThumb(item, 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("expected ${expected?.let { "${plainNumber(it)} $unit" } ?: "–"}", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(line?.varianceQuantity?.let { "${if ((variance ?: 0.0) > 0) "+" else ""}${plainNumber(it)} $unit" } ?: "–", fontFamily = Inter(),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, color = when { variance == null || variance == 0.0 -> Kit.Muted; variance > 0 -> Kit.Green; else -> Kit.Danger })
                line?.varianceValue?.let { Text(money(it, null), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted) }
            }
        }
        if (editable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = typed[item.id] ?: line?.countedQuantity?.let(::plainNumber).orEmpty()
                TextInput(text, { typed[item.id] = it }, Modifier.weight(1f), placeholder = "Counted", keyboardType = KeyboardType.Decimal, suffix = unit)
                val changed = typed[item.id] != null && typed[item.id] != line?.countedQuantity?.let(::plainNumber)
                IconButton({ model.countItem(item.id, text); typed.remove(item.id) }, enabled = changed && !state.saving && text.isNotBlank()) {
                    Icon(Icons.Outlined.Check, "Save", Modifier.size(20.dp), tint = if (changed) Kit.Green else Kit.Faint)
                }
            }
        } else {
            Text("counted ${line?.countedQuantity?.let { "${plainNumber(it)} $unit" } ?: "–"}", Modifier.padding(start = 44.dp), fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        }
    }
}

@Composable
private fun CountProgress(status: String, narrow: Boolean) {
    val reached = CountSteps.indexOfFirst { it.first == status }
    if (narrow) {
        // Just the numbered dots on a phone, with the step reached written under them.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CountSteps.forEachIndexed { index, _ ->
                    val done = status != "CANCELLED" && index <= reached
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) Kit.Green else Kit.Tint), Alignment.Center) {
                        if (done && index < reached) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White)
                        else Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (done) Color.White else Kit.Muted)
                    }
                    if (index < CountSteps.lastIndex) {
                        Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(2.dp).background(if (status != "CANCELLED" && index < reached) Kit.Green else Kit.Border))
                    }
                }
            }
            Text(if (status == "CANCELLED") "Cancelled" else "Step ${reached + 1} of ${CountSteps.size} · ${CountSteps.getOrNull(reached)?.second ?: ""}",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Ink)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CountSteps.forEachIndexed { index, (_, label) ->
            val done = status != "CANCELLED" && index <= reached
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) Kit.Green else Kit.Tint), Alignment.Center) {
                    if (done && index < reached) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Color.White)
                    else Text("${index + 1}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (done) Color.White else Kit.Muted)
                }
                Text(label, fontFamily = Inter(), fontWeight = if (index == reached) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp,
                    color = if (done) Kit.Ink else Kit.Muted)
            }
            if (index < CountSteps.lastIndex) {
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(2.dp).background(if (status != "CANCELLED" && index < reached) Kit.Green else Kit.Border))
            }
        }
    }
}

/** The count's number as people read it: short numbers whole, long generated ones by their last characters. */
private fun countLabel(number: String): String = if (number.length <= 14) number else "#${number.takeLast(8)}"
