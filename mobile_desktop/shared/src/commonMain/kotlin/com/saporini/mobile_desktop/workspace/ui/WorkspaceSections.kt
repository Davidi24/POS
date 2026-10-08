package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.pos.ui.shell.PhoneNavItem
import com.saporini.mobile_desktop.pos.ui.shell.TopBarNavItem
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceBottomBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceTopBar
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

internal data class WorkspaceTab(val label: String, val icon: ImageVector, val filledIcon: ImageVector)
internal enum class WorkspacePage(val title: String, val workspace: Workspace) {
    STATISTICS("Statistics", Workspace.STATISTICS),
    FRAUD_DETECTION("Fraud Detection", Workspace.FRAUD_DETECTION);

    val tabs: List<WorkspaceTab> get() = when (this) {
        STATISTICS -> listOf(
            WorkspaceTab("Overview", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
            WorkspaceTab("Sales", Icons.Outlined.BarChart, Icons.Filled.BarChart),
            WorkspaceTab("Staff", Icons.Outlined.People, Icons.Filled.People),
            WorkspaceTab("Reports", Icons.Outlined.Assessment, Icons.Filled.Assessment)
        )
        FRAUD_DETECTION -> listOf(
            WorkspaceTab("Overview", Icons.Outlined.Security, Icons.Filled.Security),
            WorkspaceTab("Alerts", Icons.Outlined.WarningAmber, Icons.Filled.Warning),
            WorkspaceTab("Activity", Icons.Outlined.History, Icons.Filled.History),
            WorkspaceTab("Rules", Icons.Outlined.Tune, Icons.Filled.Tune)
        )
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
    val tabs = remember(page) { page.tabs }
    val back: (() -> Unit)? = if (allowed.size > 1) {
        { if (navigator?.pop() != true) navigator?.replaceAll(WorkspacePickerScreen) }
    } else null
    val phone = isPhoneWindow()
    Column(Modifier.fillMaxSize().background(Color.White).then(if (phone) Modifier.safeDrawingPadding() else Modifier)) {
        if (!phone) {
            WorkspaceTopBar(onLogout = session::signOut, onBackToWorkspaces = back, logo = {
                Image(painterResource(Res.drawable.pos_simple_logo), "Saporini", Modifier.size(82.dp), contentScale = ContentScale.Fit)
            }) {
                tabs.forEachIndexed { index, tab ->
                    TopBarNavItem(tab.label, tab.icon, selectedIndex == index, { selectedIndex = index })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (page) {
                WorkspacePage.STATISTICS -> com.saporini.mobile_desktop.statistics.ui.StatisticsWorkspace(
                    com.saporini.mobile_desktop.statistics.StatsTab.entries[selectedIndex], { selectedIndex = it.ordinal }, Modifier.fillMaxSize())
                WorkspacePage.FRAUD_DETECTION -> com.saporini.mobile_desktop.fraud.ui.FraudWorkspace(
                    com.saporini.mobile_desktop.fraud.FraudTab.entries[selectedIndex], { selectedIndex = it.ordinal }, Modifier.fillMaxSize())
            }
        }
        if (phone) {
            WorkspaceBottomBar(moreSelected = false, onLogout = session::signOut, onBackToWorkspaces = back) {
                tabs.forEachIndexed { index, tab ->
                    PhoneNavItem(
                        modifier = Modifier.weight(1f), label = tab.label,
                        icon = if (selectedIndex == index) tab.filledIcon else tab.icon,
                        selected = selectedIndex == index, onClick = { selectedIndex = index }
                    )
                }
            }
        }
    }
}
