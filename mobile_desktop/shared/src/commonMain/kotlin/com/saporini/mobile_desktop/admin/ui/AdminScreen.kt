package com.saporini.mobile_desktop.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen
import org.koin.compose.koinInject

object AdminScreen : Screen {
    @Composable
    override fun Content() {
        val sessionManager = koinInject<SessionManager>()
        val currentUser by sessionManager.currentUser.collectAsState()
        // Inventory and Suppliers are their own workspace now; the Admin Hub opens on Shifts (Users for people who can't see shifts).
        var selected by remember {
            val canReadShifts = currentUser?.permissions.orEmpty().any { it == "SHIFT_READ" || it == "SHIFT_MANAGE" }
            mutableStateOf(if (canReadShifts) AdminSection.SHIFTS else AdminSection.USERS)
        }
        val navigator = LocalNavigator.current
        val backToWorkspaces: (() -> Unit)? =
            if ((currentUser?.let { accessibleWorkspaces(it) }?.size ?: 0) > 1) {
                { if (navigator?.pop() != true) navigator?.replaceAll(WorkspacePickerScreen) }
            } else null
        val isPhoneLayout = isPhoneWindow()
        // On a phone the bar with notifications and the profile sits on top, as in Inventory, and slides away while the page
        // scrolls down and comes back as it scrolls up (user, 2026-10-31).
        val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(isPhoneLayout)
        LaunchedEffect(selected) { hideBar.show() }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .then(if (isPhoneLayout) Modifier.safeDrawingPadding() else Modifier)
        ) {
            if (!isPhoneLayout) {
                AdminTopBar(
                    selected = selected,
                    onSelect = { selected = it },
                    onLogout = { sessionManager.signOut() },
                    onBackToWorkspaces = backToWorkspaces
                )
            } else com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                com.saporini.mobile_desktop.workspace.ui.PickerTopBar(currentUser, loggingOut = false, onLogout = { sessionManager.signOut() }, compact = true,
                    onBack = backToWorkspaces)
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection),
                contentAlignment = Alignment.Center
            ) {
                when (selected) {
                    AdminSection.SHIFTS -> com.saporini.mobile_desktop.pos.shifts.ShiftScreen(Modifier.fillMaxSize(), management = true)
                    AdminSection.PAY -> com.saporini.mobile_desktop.pos.shifts.HoursAndPayScreen(Modifier.fillMaxSize())
                    AdminSection.DEVICES -> com.saporini.mobile_desktop.admin.devices.ui.DevicesScreen(Modifier.fillMaxSize())
                    AdminSection.SETTINGS -> com.saporini.mobile_desktop.admin.settings.AdminSettings(Modifier.fillMaxSize())
                    AdminSection.USERS -> com.saporini.mobile_desktop.admin.people.ui.UsersScreen(Modifier.fillMaxSize())
                    AdminSection.AUDIT_LOGS -> com.saporini.mobile_desktop.admin.audit.ui.AuditLogScreen(Modifier.fillMaxSize())
                    AdminSection.RESTAURANT_INFO -> com.saporini.mobile_desktop.platform.ui.RestaurantInfoScreen(Modifier.fillMaxSize())
                }
            }

            if (isPhoneLayout) {
                AdminBottomBar(
                    selected = selected,
                    onSelect = { selected = it },
                    onLogout = { sessionManager.signOut() },
                    onBackToWorkspaces = backToWorkspaces
                )
            }
        }
    }
}
