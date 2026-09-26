package com.saporini.mobile_desktop.kds.ui

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

object KdsScreen : Screen {

    @Composable
    override fun Content() {
        var selected by remember { mutableStateOf(KdsSection.TICKETS) }
        val sessionManager = koinInject<SessionManager>()
        val currentUser by sessionManager.currentUser.collectAsState()
        val navigator = LocalNavigator.current
        // Users who can open more than one workspace get a way back to the picker.
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
                KdsTopBar(
                    selected = selected,
                    onSelect = { selected = it },
                    onLogout = { sessionManager.signOut() },
                    onBackToWorkspaces = backToWorkspaces
                )
            }

            // Each tab shows its name until its screen is built.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(selected.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold)
            }

            if (isPhoneLayout) {
                KdsBottomBar(
                    selected = selected,
                    onSelect = { selected = it },
                    onLogout = { sessionManager.signOut() },
                    onBackToWorkspaces = backToWorkspaces
                )
            }
        }
    }
}
