package com.saporini.mobile_desktop.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen
import org.koin.compose.koinInject

object AdminScreen : Screen {
    @Composable
    override fun Content() {
        var selected by remember { mutableStateOf(AdminSection.SHIFTS) }
        val sessionManager = koinInject<SessionManager>()
        val currentUser by sessionManager.currentUser.collectAsState()
        val navigator = LocalNavigator.current
        val backToWorkspaces: (() -> Unit)? =
            if ((currentUser?.let { accessibleWorkspaces(it) }?.size ?: 0) > 1) {
                { if (navigator?.pop() != true) navigator?.replaceAll(WorkspacePickerScreen) }
            } else null
        val isPhoneLayout = isPhoneWindow()

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
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (selected) {
                    AdminSection.SHIFTS -> com.saporini.mobile_desktop.pos.shifts.ShiftScreen(Modifier.fillMaxSize(), management = true)
                    AdminSection.SETTINGS -> com.saporini.mobile_desktop.admin.settings.AdminSettings(Modifier.fillMaxSize())
                    else -> Text(selected.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold)
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
