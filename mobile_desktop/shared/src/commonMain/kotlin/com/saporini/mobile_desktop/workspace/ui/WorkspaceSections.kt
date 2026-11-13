package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.pos.ui.shell.NavEntry
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavTopBar
import org.koin.compose.koinInject
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll

internal data class WorkspaceTab(val label: String, val icon: ImageVector, val filledIcon: ImageVector)
internal enum class WorkspacePage(val title: String, val workspace: Workspace) {
    INVENTORY("Inventory", Workspace.INVENTORY),
    STATISTICS("Statistics", Workspace.STATISTICS),
    FRAUD_DETECTION("Fraud Detection", Workspace.FRAUD_DETECTION),
    RESTAURANT("Restaurant", Workspace.RESTAURANT);

    val tabs: List<WorkspaceTab> get() = when (this) {
        INVENTORY -> listOf(
            WorkspaceTab("Inventory", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
            WorkspaceTab("Suppliers", Icons.Outlined.LocalShipping, Icons.Filled.LocalShipping)
        )
        // In StatsTab order (the user's: Overview, Reports, then the rest); the place's index is the tab's.
        STATISTICS -> listOf(
            WorkspaceTab("Overview", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
            WorkspaceTab("Reports", Icons.Outlined.Assessment, Icons.Filled.Assessment),
            WorkspaceTab("Sales & orders", Icons.Outlined.BarChart, Icons.Filled.BarChart),
            WorkspaceTab("Dishes & stock", Icons.Outlined.RestaurantMenu, Icons.Filled.RestaurantMenu),
            WorkspaceTab("Payments & finance", Icons.Outlined.Payments, Icons.Filled.Payments),
            WorkspaceTab("Team & bookings", Icons.Outlined.Groups, Icons.Filled.Groups)
        )
        FRAUD_DETECTION -> listOf(
            WorkspaceTab("Overview", Icons.Outlined.Security, Icons.Filled.Security),
            WorkspaceTab("Alerts", Icons.Outlined.WarningAmber, Icons.Filled.Warning),
            WorkspaceTab("Activity", Icons.Outlined.History, Icons.Filled.History),
            WorkspaceTab("Rules", Icons.Outlined.Tune, Icons.Filled.Tune)
        )
        RESTAURANT -> com.saporini.mobile_desktop.platform.RestaurantTab.entries.map { tab ->
            when (tab) {
                com.saporini.mobile_desktop.platform.RestaurantTab.DETAILS -> WorkspaceTab(tab.label, Icons.Outlined.Storefront, Icons.Filled.Storefront)
                com.saporini.mobile_desktop.platform.RestaurantTab.BRANCHES -> WorkspaceTab(tab.label, Icons.Outlined.Business, Icons.Filled.Business)
                com.saporini.mobile_desktop.platform.RestaurantTab.PLACES -> WorkspaceTab(tab.label, Icons.Outlined.Place, Icons.Filled.Place)
                com.saporini.mobile_desktop.platform.RestaurantTab.BRANDING -> WorkspaceTab(tab.label, Icons.Outlined.Palette, Icons.Filled.Palette)
                com.saporini.mobile_desktop.platform.RestaurantTab.LEGAL -> WorkspaceTab(tab.label, Icons.Outlined.Gavel, Icons.Filled.Gavel)
                com.saporini.mobile_desktop.platform.RestaurantTab.MONEY -> WorkspaceTab(tab.label, Icons.Outlined.CurrencyExchange, Icons.Filled.CurrencyExchange)
            }
        }
    }
}

/** The workspace navigation and, under it, the content of the chosen tab. */
@Composable
internal fun WorkspaceSections(page: WorkspacePage) {
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val navigator = LocalNavigator.current
    val allowed = user?.let { accessibleWorkspaces(it) }.orEmpty()
    if (page.workspace !in allowed) {
        LaunchedEffect(user) { user?.let { navigator?.replaceAll(resolveStartScreen(it)) } }
        return
    }
    var selectedIndex by rememberSaveable(page) { mutableStateOf(0) }
    // Stock's "Go to <supplier>" switches the Inventory workspace to Suppliers.
    if (page == WorkspacePage.INVENTORY) {
        val wanted = com.saporini.mobile_desktop.admin.inventory.SupplierBook.openRequest
        LaunchedEffect(wanted) { if (wanted != null) selectedIndex = 1 }
    }
    // The POS bar: three places always show, then the one picked last from More (Restaurant has six).
    val entries = remember(page) { page.tabs.mapIndexed { index, tab -> NavEntry(index, tab.label, tab.icon, tab.filledIcon) } }
    var unsaved by remember { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val leave: (() -> Unit) -> Unit = { action -> if (unsaved) pendingLeave = action else action() }
    val logout = { leave { session.signOut() } }
    // Back to where it was opened from; the Restaurant page (opened from the restaurant list, never a picker card) falls back
    // to that list.
    val home: () -> cafe.adriel.voyager.core.screen.Screen = {
        if (page == WorkspacePage.RESTAURANT) com.saporini.mobile_desktop.platform.ui.RestaurantListScreen else WorkspacePickerScreen
    }
    val back: (() -> Unit)? = if (allowed.size > 1 || com.saporini.mobile_desktop.core.session.isPlatformAdmin(user)) {
        { leave { if (navigator?.pop() != true) navigator?.replaceAll(home()) } }
    } else null
    if (pendingLeave != null) com.saporini.mobile_desktop.core.components.ConfirmDialog(
        "Discard unsaved changes?", "Your restaurant appearance and receipt changes have not been saved.", "Discard and leave",
        onConfirm = { val action = pendingLeave; pendingLeave = null; action?.invoke() }, onDismiss = { pendingLeave = null })
    val phone = isPhoneWindow()
    // Inventory on a phone (user, 2026-10-30): a top bar with back, the logo, notifications and the profile that slides away
    // while the page scrolls down and comes back as it scrolls up; the bottom bar then holds only Inventory and Suppliers.
    val phoneTopBar = phone && page == WorkspacePage.INVENTORY
    val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(phoneTopBar)
    LaunchedEffect(selectedIndex) { hideBar.show() }
    // The phone's back button in Inventory: Suppliers goes back to Inventory before leaving the workspace (user, 2026-10-30).
    com.saporini.mobile_desktop.core.ui.PlatformBackHandler(enabled = phoneTopBar && selectedIndex != 0) { selectedIndex = 0 }
    // Each place keeps its own state (Inventory stays on the tab it was on while Suppliers is open).
    val placeStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().background(Color.White).then(if (phone) Modifier.safeDrawingPadding() else Modifier)) {
        if (!phone) {
            WorkspaceNavTopBar(entries, fixed = 3, selected = selectedIndex, onSelect = { selectedIndex = it }, onLogout = logout,
                onBackToWorkspaces = back)
        } else if (phoneTopBar) {
            com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                PickerTopBar(user, loggingOut = false, onLogout = logout, compact = true, onBack = back)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection), contentAlignment = Alignment.Center) {
            when (page) {
                WorkspacePage.INVENTORY -> placeStates.SaveableStateProvider(selectedIndex) {
                    if (selectedIndex == 0) com.saporini.mobile_desktop.admin.inventory.ui.InventoryScreen(Modifier.fillMaxSize())
                    else com.saporini.mobile_desktop.admin.inventory.ui.SuppliersScreen(Modifier.fillMaxSize(), onBackToInventory = { selectedIndex = 0 })
                }
                WorkspacePage.STATISTICS -> com.saporini.mobile_desktop.statistics.ui.StatisticsWorkspace(
                    com.saporini.mobile_desktop.statistics.StatsTab.entries[selectedIndex], { selectedIndex = it.ordinal }, Modifier.fillMaxSize())
                WorkspacePage.FRAUD_DETECTION -> com.saporini.mobile_desktop.fraud.ui.FraudWorkspace(
                    com.saporini.mobile_desktop.fraud.FraudTab.entries[selectedIndex], { selectedIndex = it.ordinal }, Modifier.fillMaxSize())
                WorkspacePage.RESTAURANT -> com.saporini.mobile_desktop.platform.ui.RestaurantDetailsScreen(
                    com.saporini.mobile_desktop.platform.RestaurantTab.entries[selectedIndex], { selectedIndex = it.ordinal }, Modifier.fillMaxSize(), onUnsavedChange = { unsaved = it })
            }
        }
        if (phone) WorkspaceNavBottomBar(entries, selectedIndex, { selectedIndex = it }, logout, back, withMore = !phoneTopBar, compact = phoneTopBar)
    }
}
