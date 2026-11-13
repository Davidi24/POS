package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import kotlin.math.roundToInt
import com.saporini.mobile_desktop.core.components.RightEdgeActionButtonHeight
import com.saporini.mobile_desktop.core.components.edgeActionButtonHeight
import com.saporini.mobile_desktop.core.components.RightEdgeActionButton
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.InventoryTab
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.RecipesState
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.CenterChosen
import com.saporini.mobile_desktop.core.components.fadingEdges
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.ui.PlatformBackHandler
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.inventory_stock_value
import mobile_desktop.shared.generated.resources.inventory_places
import mobile_desktop.shared.generated.resources.inventory_stock_changes
import mobile_desktop.shared.generated.resources.inventory_stock_items
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import com.saporini.mobile_desktop.core.components.DoneBurst

/** What the Inventory page shows. Items also holds sub-recipes (things made here), which have their own model. */
internal enum class InventoryView(val label: String, val tab: InventoryTab?) {
    OVERVIEW("Overview", InventoryTab.OVERVIEW),
    STOCK("Stock", InventoryTab.STOCK),
    ITEMS("Items", InventoryTab.ITEMS),
    COUNTS("Counts", InventoryTab.COUNTS),
    HISTORY("History", InventoryTab.HISTORY)
}

/**
 * Admin Hub → Inventory: the overview, stock (what is where, the places, recording changes), the items (what's bought and
 * what's made here, with its sub-recipe), the history and counts.
 */
@Composable
internal fun InventoryScreen(modifier: Modifier = Modifier) {
    val model = koinInject<InventoryScreenModel>()
    val recipes = koinInject<RecipesScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    var view by rememberSaveable { mutableStateOf(InventoryView.OVERVIEW) }
    // Sub-recipes only load while Items is open.
    DisposableEffect(recipes) { onDispose { recipes.onDispose() } }
    LaunchedEffect(view) { recipes.setActive(view == InventoryView.ITEMS) }
    val state by model.state.collectAsState()
    val recipeState by recipes.state.collectAsState()
    InventoryContent(state, model, recipeState, recipes, view, { view = it }, modifier)
}

@Composable
internal fun InventoryContent(
    state: InventoryState,
    model: InventoryScreenModel,
    recipeState: RecipesState,
    recipes: RecipesScreenModel,
    view: InventoryView,
    onView: (InventoryView) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(view) { view.tab?.let(model::tab) }
    var startCount by remember { mutableStateOf(false) }
    // After a press on refresh the tab shows its skeleton until everything is back (and at least a moment), so it's
    // clear that it's reloading.
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing, state.loading, recipeState.loading) {
        if (refreshing && !state.loading && !recipeState.loading) {
            delay(350)
            refreshing = false
        }
    }
    // Where the right-edge add button sits (0 top … 1 bottom); it can be dragged out of the way, like in POS.
    var edgeButtonPosition by rememberSaveable { mutableStateOf(.84f) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        // The phone's back button: from any other view back to the Overview first, then out (user, 2026-10-30).
        PlatformBackHandler(enabled = size.isPhone && view != InventoryView.OVERVIEW) { onView(InventoryView.OVERVIEW) }
        Column(
            // Hardly any room under the content: lists and the overview run almost to the window's edge. Phones keep 16 dp
            // on both sides (user, 2026-10-30: "not enough padding").
            Modifier.fillMaxSize().padding(start = if (size.isPhone) 16.dp else 22.dp, end = if (size.isPhone) 16.dp else 22.dp,
                top = if (size.isPhone) 16.dp else 18.dp, bottom = if (size.isPhone) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 12.dp else 14.dp)
        ) {
            val refresh: @Composable () -> Unit = {
                RefreshButton(state.loading || recipeState.loading, {
                    refreshing = true
                    model.refresh()
                    if (view == InventoryView.ITEMS) recipes.refresh()
                }, size = if (size.isPhone) 38.dp else PageControlHeight)
            }
            if (size.isPhone) {
                // A phone: the title with refresh next to it, then the views as one row of tabs.
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Inventory", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
                        Text("What you have, where it is, and every change", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    refresh()
                }
                PhoneViewTabs(view, onView)
            } else {
                PageHeader("Inventory", "What you have, where it is, and every change") {
                    if (size.isDesktop) OverviewTabs(InventoryView.entries.map { it.label }, view.label, { label -> onView(InventoryView.entries.first { it.label == label }) },
                        Modifier.width(660.dp), height = 44.dp)
                    // The header always holds the same controls, so the tabs never move; adding is the edge button below.
                    refresh()
                }
                if (!size.isDesktop) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        OverviewTabs(InventoryView.entries.map { it.label }, view.label, { label -> onView(InventoryView.entries.first { it.label == label }) },
                            Modifier.width(660.dp))
                    }
                }
            }
            ScreenMessages(state.error.takeIf { state.itemDraft == null && state.locationDraft == null && state.moveDraft == null && state.bulkMove == null },
                state.notice, model::clearMessages)
            // Sub-recipes say "saved" (and errors outside their editor) the same way, from their own model.
            if (view == InventoryView.ITEMS) ScreenMessages(recipeState.error.takeIf { recipeState.subRecipe == null }, recipeState.notice, recipes::clearMessages)
            if (state.moveOutcomeUnknown && state.moveDraft == null) {
                MessageBar("A stock change may not have been saved. Open it again and retry the same change.", MessageKind.WARNING)
            }
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to inventory", "Your role needs “View stock” to see it.")
                state.items.isEmpty() && state.locations.isEmpty() && state.loading -> InventoryTabSkeleton(view, size, Modifier.fillMaxWidth().weight(1f))
                state.items.isEmpty() && state.locations.isEmpty() && state.stale && state.error != null ->
                    PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                else -> {
                    // While a tab's own data is still on its way it shows its skeleton, then fades into the content.
                    val waiting = refreshing || when (view) {
                        InventoryView.OVERVIEW, InventoryView.STOCK -> state.loading && state.levels.isEmpty()
                        InventoryView.ITEMS -> state.loading && state.items.isEmpty()
                        // These lists show their own loading lines, so their filters stay put while a filter reloads them.
                        InventoryView.HISTORY, InventoryView.COUNTS -> false
                    }
                    Crossfade(waiting, Modifier.fillMaxWidth().weight(1f), animationSpec = tween(220), label = "inventory-loading") { skeleton ->
                        val body = Modifier.fillMaxSize()
                        if (skeleton) InventoryTabSkeleton(view, size, body) else when (view) {
                            // The key numbers only show on the overview, so the other tabs keep their room for lists.
                            InventoryView.OVERVIEW -> OverviewTab(state, model, size, inventoryCards(state, model, onView), onView, modifier = body)
                            InventoryView.ITEMS -> ItemsTab(state, model, recipeState, onAddSubRecipe = recipes::newSubRecipe,
                                onOpenSubRecipe = recipes::openSubRecipe, size = size, modifier = body)
                            InventoryView.STOCK -> StockTab(state, model, size, body)
                            InventoryView.HISTORY -> HistoryTab(state, model, size, body)
                            InventoryView.COUNTS -> CountsTab(state, model, size, body)
                        }
                    }
                }
            }
        }

        // Adding something new, on the tabs where that's the main job (Stock has its change buttons).
        val edge: Triple<String, Dp, () -> Unit>? = when (view) {
            InventoryView.ITEMS -> if (state.canEdit) Triple("New item", if (size.isPhone) 82.dp else 100.dp, model::newItem) else null
            InventoryView.COUNTS -> if (state.canEdit) Triple("New count", if (size.isPhone) 86.dp else 104.dp, { startCount = true }) else null
            else -> null
        }
        if (edge != null && state.canRead) {
            // Phones get the smaller tab (user, 2026-10-30).
            val maxOffset = with(LocalDensity.current) { (maxHeight - edgeActionButtonHeight(size.isPhone)).coerceAtLeast(0.dp).toPx() }
            RightEdgeActionButton(
                text = edge.first,
                onClick = edge.third,
                enabled = !state.saving && !recipeState.saving,
                width = edge.second,
                compact = size.isPhone,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, (edgeButtonPosition * maxOffset).roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (maxOffset > 0f) edgeButtonPosition = (edgeButtonPosition + delta / maxOffset).coerceIn(0f, 1f)
                        }
                    )
            )
        }
    }

    InventoryDialogs(state, model)
    DoneBurst(state.done)
    if (startCount) StartCountDialog(state, model) { startCount = false }
    SubRecipeDialog(recipeState, recipes)
    // A saved sub-recipe changes its item (name, kind, cost), so Items reloads.
    LaunchedEffect(recipeState.savedTick) { if (recipeState.savedTick > 0) model.refresh() }
}

/**
 * The six views on a phone: one row of tabs (icon and name, the chosen one green and underlined) that slides sideways,
 * and keeps the chosen tab in sight.
 */
@Composable
private fun PhoneViewTabs(view: InventoryView, onView: (InventoryView) -> Unit) {
    val scroll = rememberScrollState()
    // Where each tab sits inside the row (start, width), to slide the chosen one to the middle.
    val tabs = remember { mutableStateMapOf<InventoryView, Pair<Int, Int>>() }
    CenterChosen(scroll, view, tabs)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Kit.Border))
        // The edges fade where more tabs wait off-screen.
        Row(Modifier.fillMaxWidth().fadingEdges(scroll).horizontalScroll(scroll)) {
            InventoryView.entries.forEach { option ->
                val chosen = option == view
                Column(Modifier.width(IntrinsicSize.Max).onGloballyPositioned { tabs[option] = it.positionInParent().x.roundToInt() to it.size.width }
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable { onView(option) }.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(viewIcon(option), null, Modifier.size(17.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                        Text(option.label, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.5.sp,
                            letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1, softWrap = false)
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (chosen) Kit.Green else Color.Transparent))
                }
            }
        }
    }
}

private fun viewIcon(view: InventoryView): ImageVector = when (view) {
    InventoryView.OVERVIEW -> Icons.Outlined.Insights
    InventoryView.STOCK -> Icons.Outlined.Inventory2
    InventoryView.ITEMS -> Icons.Outlined.Category
    InventoryView.HISTORY -> Icons.Outlined.History
    InventoryView.COUNTS -> Icons.AutoMirrored.Outlined.FactCheck
}

private fun inventoryCards(state: InventoryState, model: InventoryScreenModel, onView: (InventoryView) -> Unit): List<@Composable (Modifier) -> Unit> {
    val tracked = state.items.count { it.active && it.trackInventory }
    val untracked = state.items.count { it.active && !it.trackInventory }
    val off = state.items.count { !it.active }
    val places = state.activeLocations.size
    // Stock value at cost over every place (the levels are the whole list, not a page).
    val valueCents = stockValueCents(state, state.levels)
    return listOf(
        { m -> OverviewStatCard("Stock items", "$tracked", "$untracked not counted · $off switched off", Kit.Green, m,
            image = Res.drawable.inventory_stock_items, suffix = "tracked", onClick = { onView(InventoryView.ITEMS) }) },
        // Running low is already in the panels below, so the second card counts the stock changes behind the chart.
        { m -> OverviewStatCard("Stock changes", state.activity?.changes?.toString() ?: "–", "recorded in the last ${state.activityDays} days", Kit.Purple, m,
            image = Res.drawable.inventory_stock_changes, onClick = { onView(InventoryView.HISTORY) }) },
        { m -> OverviewStatCard("Places", "$places", "${state.saleSources.size} items have a place for sales", Kit.Blue, m,
            image = Res.drawable.inventory_places, onClick = { onView(InventoryView.ITEMS) }) },
        { m -> OverviewStatCard("Stock value", moneyText(valueCents, "EUR"), "at cost, in every place", Kit.Amber, m, image = Res.drawable.inventory_stock_value) }
    )
}

/** What [levels] are worth at each item's cost, in cents. */
internal fun stockValueCents(state: InventoryState, levels: List<com.saporini.mobile_desktop.admin.inventory.StockLevelDto>): Long {
    val costs = state.items.associate { it.id to it.costPerUnit.asDouble() }
    return levels.sumOf { it.onHandQuantity.asDouble().coerceAtLeast(0.0) * (costs[it.inventoryItemId] ?: 0.0) * 100 }.toLong()
}
