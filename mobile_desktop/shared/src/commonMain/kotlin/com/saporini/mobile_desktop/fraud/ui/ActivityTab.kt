package com.saporini.mobile_desktop.fraud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.SkeletonListPanel
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.TimeColumn
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.core.format.money
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.format.weekdayDate
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.fraud.ACTIVITY_TYPES
import com.saporini.mobile_desktop.fraud.FraudScreenModel
import com.saporini.mobile_desktop.fraud.FraudState
import com.saporini.mobile_desktop.fraud.data.FraudActivityDto
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime

/** Every discount, removal, void, cancel, refund and reopen in the period, newest first: the raw trail behind the alerts. */
@Composable
internal fun ActivityTab(state: FraudState, model: FraudScreenModel, size: ScreenSize, modifier: Modifier) {
    val list: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(Icons.Outlined.History, "Sensitive actions", m, count = state.activityTotal.toInt()) {
            if (!size.isDesktop) {
                FilterPills(listOf<Pair<String?, String>>(null to "Everything") + ACTIVITY_TYPES.map { it to activityLabel(it) }, state.activityType,
                    { model.activityFilter(it, state.activityStaffId) })
            }
            state.activityStaffId?.let { id ->
                val name = state.activity.firstOrNull { it.staffId == id }?.staffName ?: "One person"
                FilterPills(listOf<Pair<String?, String>>(id to "Only $name", null to "Everyone"), id, { model.activityFilter(state.activityType, it) })
            }
            when {
                state.activity.isEmpty() && state.loading -> SkeletonListPanel(Modifier.fillMaxWidth().weight(1f).alpha(rememberSkeletonAlpha("activity")), 6, 66.dp)
                state.activity.isEmpty() && state.error != null -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("Couldn't load the actions", state.error, Icons.Outlined.WarningAmber, action = { RetryText(onClick = model::refresh) })
                }
                state.activity.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("No sensitive actions", "Discounts, removals, voids, refunds and reopened orders show here.", Icons.Outlined.VerifiedUser)
                }
                else -> {
                    val listState = rememberLazyListState()
                    LoadMoreWhenNearEnd(listState, state.activityHasNext, state.loading || state.loadingMore, onLoadMore = model::loadMore)
                    val zone = RestaurantTime.zone
                    val byDay = state.activity.withIndex().groupBy { localOf(it.value.at, zone)?.date }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)) {
                        byDay.forEach { (day, rows) ->
                            item(key = "day-$day") { GroupLabel(day?.let(::weekdayDate) ?: "Unknown day", "${rows.size} ${if (rows.size == 1) "action" else "actions"}") }
                            // Actions have no id of their own; their place in the list is stable while pages are added at the end.
                            itemsIndexed(rows, key = { _, row -> "a-${row.index}" }) { _, row ->
                                ActivityCard(row.value, compact = size.isPhone) { staffId -> model.activityFilter(state.activityType, staffId) }
                            }
                        }
                        item(key = "footer") {
                            PagedFooter(state.activity.size, state.activityTotal, state.activityHasNext, state.loadingMore, state.error != null, model::loadMore)
                        }
                    }
                }
            }
        }
    }
    if (size.isDesktop) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            list(Modifier.weight(1f).fillMaxHeight())
            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                OverviewPanel(Icons.Outlined.Category, "Kind of action", Modifier.fillMaxWidth()) {
                    FilterRow(Icons.Outlined.History, "Everything", null, Kit.Green, state.activityType == null) { model.activityFilter(null, state.activityStaffId) }
                    ACTIVITY_TYPES.forEach { type ->
                        FilterRow(activityIcon(type), activityLabel(type), null, activityColor(type), state.activityType == type) {
                            model.activityFilter(if (state.activityType == type) null else type, state.activityStaffId)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.1f)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AccessTime, null, Modifier.size(16.dp), tint = Kit.Amber)
                    Text("An amber strip means the person wasn't clocked in when they did it.", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Ink)
                }
            }
        }
    } else list(modifier)
}

@Composable
private fun ActivityCard(row: FraudActivityDto, compact: Boolean, onPerson: (String) -> Unit) {
    val zone = RestaurantTime.zone
    val color = activityColor(row.type)
    StripCard(if (!row.onShift) Kit.Amber else color, minHeight = 64.dp, onClick = row.staffId?.let { id -> { onPerson(id) } }) {
        if (!compact) {
            TimeColumn(timeText(row.at, zone), activityLabel(row.type), color, Modifier.width(110.dp))
            CardDivider()
        }
        IconTile(activityIcon(row.type), color, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (compact) "${activityLabel(row.type)} · ${timeText(row.at, zone)}" else row.detail?.takeIf { it.isNotBlank() } ?: activityLabel(row.type),
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(row.orderNumber?.let { "order #$it" }, if (compact) row.detail?.takeIf { it.isNotBlank() } else null,
                if (!row.onShift) "not clocked in" else null).joinToString(" · ").ifEmpty { " " },
                fontFamily = Inter(), fontSize = 11.sp, color = if (!row.onShift) Kit.Amber else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) Row(Modifier.width(170.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InitialsAvatar(row.staffName ?: "?", color = if (!row.onShift) Kit.Amber else Kit.Green, size = 28.dp)
            Text(row.staffName ?: "Someone", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        row.amount?.let { Text(money(it, row.currency), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink) }
    }
}
