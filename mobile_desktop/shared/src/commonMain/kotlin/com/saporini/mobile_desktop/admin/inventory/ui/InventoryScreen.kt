package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddHomeWork
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.InventoryTab
import com.saporini.mobile_desktop.admin.inventory.RecipesScreenModel
import com.saporini.mobile_desktop.admin.inventory.RecipesState
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.OverviewTabs
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.plan
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.settings_payments
import org.koin.compose.koinInject

/** What the Inventory page shows; Recipes has its own model, the rest share one. */
internal enum class InventoryView(val label: String, val tab: InventoryTab?) {
    STOCK("Stock", InventoryTab.STOCK),
    ITEMS("Items", InventoryTab.ITEMS),
    PLACES("Places", InventoryTab.PLACES),
    HISTORY("History", InventoryTab.HISTORY),
    COUNTS("Counts", InventoryTab.COUNTS),
    RECIPES("Recipes", null)
}

/** Admin Hub → Inventory: stock on hand, items, places, history of changes, stock counts and recipes. */
@Composable
internal fun InventoryScreen(modifier: Modifier = Modifier) {
    val model = koinInject<InventoryScreenModel>()
    val recipes = koinInject<RecipesScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    var view by rememberSaveable { mutableStateOf(InventoryView.STOCK) }
    // Recipes only load while their tab is open.
    DisposableEffect(recipes) { onDispose { recipes.onDispose() } }
    LaunchedEffect(view) { recipes.setActive(view == InventoryView.RECIPES) }
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
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Inventory", "Stock on hand, items, places, counts and recipes") {
                if (size.isDesktop) OverviewTabs(InventoryView.entries.map { it.label }, view.label, { label -> onView(InventoryView.entries.first { it.label == label }) },
                    Modifier.width(560.dp), height = 44.dp)
                RefreshButton(state.loading || recipeState.loading, { model.refresh(); if (view == InventoryView.RECIPES) recipes.refresh() })
                if (state.canEdit) when (view) {
                    InventoryView.STOCK, InventoryView.HISTORY ->
                        ToolbarPrimaryButton("Receive stock", Icons.Outlined.LocalShipping, !state.saving) { model.startMove(StockMoveKind.RECEIVE) }
                    InventoryView.ITEMS -> ToolbarPrimaryButton("New item", Icons.Outlined.AddBox, !state.saving, model::newItem)
                    InventoryView.PLACES -> ToolbarPrimaryButton("New place", Icons.Outlined.AddHomeWork, !state.saving, model::newLocation)
                    InventoryView.COUNTS -> ToolbarPrimaryButton("Start a count", Icons.AutoMirrored.Outlined.FactCheck, !state.saving) { startCount = true }
                    InventoryView.RECIPES -> if (recipeState.canEdit) ToolbarPrimaryButton("New recipe", Icons.AutoMirrored.Outlined.MenuBook, !recipeState.saving) { recipes.newRecipe() }
                }
            }
            if (!size.isDesktop) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    OverviewTabs(InventoryView.entries.map { it.label }, view.label, { label -> onView(InventoryView.entries.first { it.label == label }) },
                        Modifier.width(560.dp))
                }
            }
            if (view == InventoryView.RECIPES) {
                ScreenMessages(recipeState.error.takeIf { recipeState.draft == null && recipeState.component == null }, recipeState.notice, recipes::clearMessages)
            } else {
                ScreenMessages(state.error.takeIf { state.itemDraft == null && state.locationDraft == null && state.moveDraft == null }, state.notice, model::clearMessages)
            }
            if (state.moveOutcomeUnknown && state.moveDraft == null) {
                MessageBar("A stock change may not have been saved. Open it again and retry the same change.", MessageKind.WARNING)
            }
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to inventory", "Your role needs “View settings” to see stock.")
                state.items.isEmpty() && state.locations.isEmpty() && state.loading -> OverviewPageSkeleton(size)
                state.items.isEmpty() && state.locations.isEmpty() && state.stale && state.error != null ->
                    PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                else -> {
                    SummaryCardRow(size, inventoryCards(state, model, onView))
                    val body = Modifier.fillMaxWidth().weight(1f)
                    when (view) {
                        InventoryView.STOCK -> StockTab(state, model, size, body)
                        InventoryView.ITEMS -> ItemsTab(state, model, size, body)
                        InventoryView.PLACES -> PlacesTab(state, model, size, body)
                        InventoryView.HISTORY -> HistoryTab(state, model, size, body)
                        InventoryView.COUNTS -> CountsTab(state, model, size, body)
                        InventoryView.RECIPES -> RecipesTab(recipeState, recipes, state, size, body)
                    }
                }
            }
        }
    }

    InventoryDialogs(state, model)
    if (startCount) StartCountDialog(state, model) { startCount = false }
    RecipeDialogs(recipeState, recipes, state)
}

private fun inventoryCards(state: InventoryState, model: InventoryScreenModel, onView: (InventoryView) -> Unit): List<@Composable (Modifier) -> Unit> {
    val tracked = state.items.count { it.active && it.trackInventory }
    val untracked = state.items.count { it.active && !it.trackInventory }
    val off = state.items.count { !it.active }
    val places = state.activeLocations.size
    // Stock value at cost over every place listed (the levels are the whole list, not a page).
    val costs = state.items.associate { it.id to it.costPerUnit.asDouble() }
    val valueCents = if (state.levelLocationId == null) state.levels.sumOf { it.onHandQuantity.asDouble() * (costs[it.inventoryItemId] ?: 0.0) * 100 }.toLong() else null
    return listOf(
        { m -> OverviewStatCard("Stock items", "$tracked", "$untracked not counted · $off switched off", Kit.Green, m,
            image = Res.drawable.settings_orders_kitchen, suffix = "tracked", onClick = { onView(InventoryView.ITEMS) }) },
        { m -> OverviewStatCard("Running low", "${state.lowStock.size}", if (state.lowStock.isEmpty()) "Everything is above its reorder point" else "below their reorder point",
            if (state.lowStock.isEmpty()) Kit.Grey else Kit.Danger, m, image = Res.drawable.settings_notifications,
            valueColor = if (state.lowStock.isEmpty()) Kit.Ink else Kit.Danger, onClick = { onView(InventoryView.STOCK); model.levelsAt(null) }) },
        { m -> OverviewStatCard("Places", "$places", "${state.saleSources.size} items have a place for sales", Kit.Blue, m, image = Res.drawable.plan,
            onClick = { onView(InventoryView.PLACES) }) },
        { m -> OverviewStatCard("Stock value", valueCents?.let { moneyText(it, "EUR") } ?: "–",
            if (valueCents == null) "Shown when all places are listed" else "at cost, in every place", Kit.Amber, m, image = Res.drawable.settings_payments) }
    )
}
