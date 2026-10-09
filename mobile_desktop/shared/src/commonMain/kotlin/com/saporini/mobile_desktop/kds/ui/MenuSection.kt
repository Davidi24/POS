package com.saporini.mobile_desktop.kds.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.saporini.mobile_desktop.core.components.CompactStat
import com.saporini.mobile_desktop.core.components.FilterPills
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SkeletonListPanel
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.kds.KdsScreenModel
import com.saporini.mobile_desktop.kds.data.KdsMenuEntry
import com.saporini.mobile_desktop.kds.model.KdsState
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.settings_orders_kitchen

private const val ALL_MENUS = "All menus"

/** The kitchen's dishes: switch one off when it runs out, and the waiters can't order it any more. */
@Composable
internal fun MenuSection(state: KdsState, model: KdsScreenModel, size: ScreenSize) {
    val menu = state.menu
    val menus = menu.items.associate { it.menuId to it.menuName }
    val shown = menu.visibleItems
    val soldOut = menu.items.count { !it.item.available }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader("Menu", "Switch a dish off when it runs out; the waiters see it straight away") {
            SearchField(menu.query, { model.setMenuFilter(query = it) }, Modifier.width(if (size.isPhone) 180.dp else 240.dp), placeholder = "Find a dish",
                height = PageControlHeight)
            if (menus.size > 1) HeaderDropdown(menu.menuId?.let { menus[it] } ?: ALL_MENUS, Icons.AutoMirrored.Outlined.MenuBook, Modifier.width(170.dp),
                listOf(ALL_MENUS) + menus.values.sorted(), { name -> model.setMenuFilter(menuId = menus.entries.firstOrNull { it.value == name }?.key) })
            RefreshButton(menu.loading, { model.refresh() })
        }
        if (!state.canUpdateMenu && state.canReadMenu) {
            MessageBar("You can see the dishes but not switch them. Ask a manager for “Edit menus”.", MessageKind.INFO)
        }
        when {
            !state.canReadMenu -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("No access to the menu", "Your role needs “View menus” to see the kitchen's dishes.", Icons.Outlined.RestaurantMenu)
            }
            !menu.loaded && menu.loading -> SkeletonListPanel(Modifier.fillMaxWidth().weight(1f), 6, 64.dp)
            !menu.loaded && menu.error != null -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("Couldn't load the menu", menu.error.message, Icons.Outlined.WarningAmber, action = { RetryText(onClick = { model.refresh() }) })
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactStat("Kitchen dishes", "${menu.items.size}", Kit.Green, Modifier.weight(1f), icon = Icons.Outlined.RestaurantMenu)
                    CompactStat("Available", "${menu.items.size - soldOut}", Kit.Blue, Modifier.weight(1f), icon = Icons.Outlined.CheckCircle)
                    CompactStat("Sold out", "$soldOut", if (soldOut > 0) Kit.Danger else Kit.Grey, Modifier.weight(1f), icon = Icons.Outlined.Block,
                        valueColor = if (soldOut > 0) Kit.Danger else Kit.Ink, onClick = { model.setMenuFilter(available = false) })
                }
                FilterPills(listOf<Pair<Boolean?, String>>(null to "Every dish", true to "Available", false to "Sold out"), menu.available,
                    { model.setMenuFilter(available = it) })
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                        OverviewEmpty(if (menu.items.isEmpty()) "No kitchen dishes" else "Nothing matches",
                            if (menu.items.isEmpty()) "Dishes marked “send to kitchen” on an active menu show here." else "Try another word or filter.",
                            Icons.Outlined.RestaurantMenu, image = Res.drawable.settings_orders_kitchen)
                    }
                } else {
                    val bySection = shown.groupBy { it.sectionName }
                    LazyVerticalGrid(GridCells.Adaptive(250.dp), Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        bySection.forEach { (section, entries) ->
                            item(key = "s-$section", span = { GridItemSpan(maxLineSpan) }) {
                                GroupLabel(section, "${entries.size} · ${entries.count { !it.item.available }} sold out")
                            }
                            items(entries, key = { it.item.id }) { entry -> DishTile(entry, state, menus.size > 1) { model.setAvailability(entry.item.id, it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishTile(entry: KdsMenuEntry, state: KdsState, showMenu: Boolean, onAvailable: (Boolean) -> Unit) {
    val item = entry.item
    val busy = "menu:${item.id}" in state.busyKeys
    val tone = if (item.available) Kit.Green else Kit.Danger
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = if (item.available) Color.White else Kit.Danger.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, if (item.available) Kit.RowBorder else Kit.Danger.copy(alpha = 0.3f)), shadowElevation = if (item.available) 1.dp else 0.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.12f)), Alignment.Center) {
                Icon(if (item.available) Icons.Outlined.RestaurantMenu else Icons.Outlined.Block, null, Modifier.size(20.dp), tint = tone)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(moneyText(kotlin.math.round(item.basePrice * 100).toLong(), null), entry.menuName.takeIf { showMenu }).joinToString(" · "),
                    fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                Text(if (item.available) "Available" else "Sold out", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = tone)
            }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Kit.Green, strokeWidth = 2.dp)
            else KitSwitch(item.available, onAvailable, enabled = state.canUpdateMenu)
        }
    }
}
