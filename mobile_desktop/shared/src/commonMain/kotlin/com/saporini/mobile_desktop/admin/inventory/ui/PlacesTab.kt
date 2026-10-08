package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.INVENTORY_LOCATION_TYPES
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.plan

@Composable
internal fun PlacesTab(state: InventoryState, model: InventoryScreenModel, size: ScreenSize, modifier: Modifier) {
    val places = state.locations.filter { state.showInactive || it.active }.sortedWith(compareBy<StockLocationDto> { !it.active }.thenBy { it.name.lowercase() })
    // Item counts need every place's levels, which are listed when no single place is chosen on the Stock tab.
    val levelsComplete = state.levelLocationId == null
    val grid: @Composable (Modifier) -> Unit = { m ->
        OverviewPanel(Icons.Outlined.Storefront, "Places", m, count = places.size) {
            if (places.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    OverviewEmpty("No places yet", "Add where you keep stock: the kitchen, the bar, the walk-in fridge, the dry store.",
                        Icons.Outlined.Storefront, image = Res.drawable.plan)
                }
                return@OverviewPanel
            }
            LazyVerticalGrid(GridCells.Fixed(when (size) { ScreenSize.PHONE -> 1; ScreenSize.TABLET -> 2; ScreenSize.DESKTOP -> 3 }),
                Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 6.dp)) {
                items(places, key = { it.id }) { place ->
                    val levels = state.levels.filter { it.locationId == place.id }
                    PlaceTile(place, if (levelsComplete) levels.size else null, if (levelsComplete) levels.count { it.lowStock } else null, state, model)
                }
            }
        }
    }
    if (size.isDesktop) {
        androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            grid(Modifier.weight(1f).fillMaxHeight())
            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                OverviewPanel(Icons.Outlined.Inventory2, "By kind", Modifier.fillMaxWidth()) {
                    INVENTORY_LOCATION_TYPES.forEach { type ->
                        FilterRow(placeTypeIcon(type), placeTypeLabel(type), state.activeLocations.count { it.locationType == type }, placeTypeColor(type))
                    }
                }
            }
        }
    } else grid(modifier)
}

@Composable
private fun PlaceTile(place: StockLocationDto, items: Int?, low: Int?, state: InventoryState, model: InventoryScreenModel) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tone = placeTypeColor(place.locationType)
    val actions = if (!state.canEdit) emptyList() else buildList {
        add(RowAction("Edit", Icons.Outlined.Edit) { model.editLocation(place.id) })
        if (place.active) {
            add(RowAction("Receive stock here", Icons.Outlined.LocalShipping) { model.startMove(StockMoveKind.RECEIVE, locationId = place.id) })
            add(RowAction("Count this place", Icons.AutoMirrored.Outlined.FactCheck) { model.newCount(place.id) })
            add(RowAction("Switch off", Icons.Outlined.ToggleOff, danger = true) { model.askDeactivateLocation(place.id) })
        }
    }
    Surface(
        Modifier.fillMaxWidth().alpha(if (place.active) 1f else 0.6f), shape = RoundedCornerShape(12.dp), color = Color.White,
        border = BorderStroke(1.dp, if (hovered) Kit.Green.copy(alpha = 0.45f) else Kit.RowBorder), shadowElevation = if (hovered) 3.dp else 1.dp
    ) {
        Column(
            Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = state.canEdit) { model.editLocation(place.id) }
        ) {
            Box(Modifier.fillMaxWidth().background(tone.copy(alpha = 0.08f))) {
                Row(Modifier.fillMaxWidth().padding14(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(Color.White), Alignment.Center) {
                        Icon(placeTypeIcon(place.locationType), null, Modifier.size(22.dp), tint = tone)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(place.name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(placeTypeLabel(place.locationType), place.code?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                    }
                    RowActionsMenu(actions)
                }
            }
            Row(Modifier.fillMaxWidth().padding14(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("Items", items?.toString() ?: "–")
                Metric("Running low", low?.toString() ?: "–", if ((low ?: 0) > 0) Kit.Danger else Kit.Ink)
                Spacer(Modifier.weight(1f))
                StatusPill(if (place.active) "In use" else "Switched off", if (place.active) Kit.Green else Kit.Grey)
            }
            place.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.fillMaxWidth().padding14(top = 0), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color = Kit.Ink) {
    Column {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
        Text(label, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
    }
}

private fun Modifier.padding14(top: Int = 12): Modifier = this.padding(start = 14.dp, end = 8.dp, top = top.dp, bottom = 12.dp)
