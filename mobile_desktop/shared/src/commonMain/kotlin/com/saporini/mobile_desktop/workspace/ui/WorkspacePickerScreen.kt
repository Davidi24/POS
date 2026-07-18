package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import org.koin.compose.koinInject

object WorkspacePickerScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sessionManager = koinInject<SessionManager>()
        val user by sessionManager.currentUser.collectAsState()

        val workspaces = user?.let { accessibleWorkspaces(it) }.orEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose workspace",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                workspaces.forEach { workspace ->
                    Button(
                        onClick = { navigator.push(screenFor(workspace)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        )
                    ) {
                        Text(workspaceLabel(workspace))
                    }
                }
            }
        }
    }
}

private fun workspaceLabel(workspace: Workspace): String = when (workspace) {
    Workspace.POS -> "POS"
    Workspace.KDS -> "Kitchen Display"
    Workspace.ADMIN -> "Administration"
}