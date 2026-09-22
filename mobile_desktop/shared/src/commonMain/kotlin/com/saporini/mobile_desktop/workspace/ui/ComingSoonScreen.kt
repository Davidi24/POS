package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import org.koin.compose.koinInject

@Composable
fun ComingSoonScreen(title: String) {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = koinInject<WorkspaceScreenModel>()
    val state by screenModel.state.collectAsState()
    val sessionManager = koinInject<SessionManager>()
    val user by sessionManager.currentUser.collectAsState()
    val hasMultipleWorkspaces = (user?.let { accessibleWorkspaces(it) }?.size ?: 0) > 1

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            navigator.replaceAll(LoginScreen)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$title - coming soon",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasMultipleWorkspaces) {
                Button(
                    onClick = {
                        if (!navigator.pop()) {
                            navigator.replaceAll(WorkspacePickerScreen)
                        }
                    }
                ) {
                    Text("Back")
                }
            }

            Button(
                onClick = { screenModel.logout() },
                enabled = !state.isLoggingOut
            ) {
                if (state.isLoggingOut) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Log out")
                }
            }
        }
    }
}
