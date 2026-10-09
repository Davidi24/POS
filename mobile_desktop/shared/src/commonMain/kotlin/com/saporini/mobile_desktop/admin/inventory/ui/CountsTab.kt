package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
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

private val CountStatuses = listOf<Pair<String?, String>>(null to "All", "DRAFT" to "Not started", "IN_PROGRESS" to "Counting",
    "COMPLETED" to "Waiting for approval", "APPROVED" to "Approved", "CANCELLED" to "Cancelled")

@Composable
internal fun CountsTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    OverviewPanel(Icons.AutoMirrored.Outlined.FactCheck, "Stock counts", modifier, count = state.counts.size) {
        FilterPills(CountStatuses, state.countStatus, model::countFilter)
        if (state.counts.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("No counts here", "Count a place to check what's really on the shelves; approving it corrects the stock.",
                    Icons.AutoMirrored.Outlined.FactCheck, image = Res.drawable.overview_reservations)
            }
            return@OverviewPanel
        }
        val listState = rememberLazyListState()
        LoadMoreWhenNearEnd(listState, state.countsHasNext, state.loading || state.countsLoadingMore, onLoadMore = model::loadMoreCounts)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            items(state.counts, key = { it.id }) { count -> CountCard(count, compact = size.isPhone) { model.openCount(count.id) } }
            item(key = "footer") { PagedFooter(state.counts.size, null, state.countsHasNext, state.countsLoadingMore, state.error != null, model::loadMoreCounts) }
        }
    }
}

@Composable
private fun CountCard(count: StockCountDto, compact: Boolean, onClick: () -> Unit) {
    val tone = countStatusColor(count.status)
    val zone = RestaurantTime.zone
    StripCard(tone, minHeight = 66.dp, chevron = !compact, faded = count.status == "CANCELLED", onClick = onClick) {
        IconTile(Icons.AutoMirrored.Outlined.FactCheck, tone, 38.dp)
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(count.locationName ?: "Place", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
            Text(listOfNotNull(count.countNumber?.let { "#${it.takeLast(8)}" }, count.createdByUserName?.let { "by $it" }, dateTimeText(count.updatedAt, zone))
                .joinToString(" · "), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) {
            CardDivider()
            Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val counted = count.lines.count { it.countedQuantity != null }
                Text("$counted ${if (counted == 1) "item" else "items"} counted", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
                Text(count.varianceValue?.let { "Difference ${money(it, null)}" } ?: "No difference yet", fontFamily = Inter(), fontSize = 11.sp,
                    color = if ((count.varianceValue?.asDouble() ?: 0.0) < 0) Kit.Danger else Kit.Muted)
            }
        }
        StatusPill(countStatusLabel(count.status), tone)
    }
}

/** Choosing the place to count, with an optional note. */
@Composable
internal fun StartCountDialog(state: InventoryState, model: InventoryScreenModel, onClose: () -> Unit) {
    var place by remember { mutableStateOf<StockLocationDto?>(state.activeLocations.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    AppDialog("Start a stock count", onClose, subtitle = "Count one place at a time. Nothing changes until you approve the count.", maxWidth = 520.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY)
            KitButton("Start count", { place?.let { model.newCount(it.id, notes); onClose() } }, icon = Icons.AutoMirrored.Outlined.FactCheck, enabled = place != null)
        }) {
        SelectInput(place, state.activeLocations, { it.name }, { place = it }, label = "Place", icon = Icons.Outlined.Storefront, required = true,
            placeholder = "Choose a place")
        TextInput(notes, { notes = it }, label = "Notes", optional = true, singleLine = false, minLines = 2, icon = Icons.AutoMirrored.Outlined.Notes,
            placeholder = "Monthly count, after the delivery…", maxLength = MAX_STOCK_TEXT)
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
    AppDialog(
        "Count · ${count.locationName ?: "place"}",
        { model.openCount(null) },
        subtitle = listOfNotNull(count.countNumber?.let { "#${it.takeLast(8)}" }, count.notes?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null },
        busy = state.saving, maxWidth = 920.dp,
        buttons = {
            Text("${count.lines.count { it.countedQuantity != null }} of ${count.lines.size} counted" + (count.varianceValue?.let { " · difference ${money(it, null)}" } ?: ""), Modifier.weight(1f),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
            KitButton("Close", { model.openCount(null) }, style = ButtonStyle.SECONDARY)
            if (state.canEdit) countSteps(count.status).forEach { step ->
                KitButton(countStepLabel(step), { model.askCountStep(step) }, style = when (step) { "cancel" -> ButtonStyle.SECONDARY; else -> ButtonStyle.PRIMARY },
                    icon = if (step == "cancel") Icons.Outlined.Close else Icons.Outlined.Check, loading = state.saving && step != "cancel")
            }
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        CountProgress(count.status)
        when (count.status) {
            "DRAFT" -> MessageBar("Start the count, then type what you find on the shelves.", MessageKind.INFO)
            "COMPLETED" -> MessageBar("Check the differences. Approving sets the stock to what was counted.", MessageKind.WARNING)
            "APPROVED" -> MessageBar("Approved ${count.approvedAt?.let { dateTimeText(it, RestaurantTime.zone) } ?: ""}${count.approvedByUserName?.let { " by $it" } ?: ""}. The stock now matches this count.", MessageKind.SUCCESS)
            "CANCELLED" -> MessageBar("This count was cancelled; it changed nothing.", MessageKind.INFO)
        }
        SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Find an item", height = PageControlHeight)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Item" to 1.6f, "Expected" to 0.7f, "Counted" to 1f, "Difference" to 0.8f).forEach { (title, weight) ->
                    Text(title.uppercase(), Modifier.weight(weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.5.sp, color = Kit.Muted)
                }
            }
            Box(Modifier.heightIn(max = 420.dp)) {
                LazyColumn {
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
                                    Text(itemTypeLabel(item.itemType), fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted)
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
private fun CountProgress(status: String) {
    val reached = CountSteps.indexOfFirst { it.first == status }
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
