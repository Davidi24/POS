package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ScreenMessages
import cafe.adriel.voyager.navigator.LocalNavigator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen

object NoAccessScreen : Screen {
    @Composable
    override fun Content() {
        val session = koinInject<SessionManager>()
        val user by session.currentUser.collectAsState()
        val model = koinInject<WorkspaceScreenModel>()
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val noBranch = user?.restaurantId != null && user?.defaultBranchId == null &&
            user?.permissions.orEmpty().any { it == "POS_ACCESS" || it == "KDS_ACCESS" }
        ScreenMessages(error, null, { error = null })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (noBranch) "No open branch assigned" else "No access assigned",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (noBranch) "Your branch is unavailable. Ask your administrator to assign an open branch, then try again."
                    else "Your account has no workspace access yet.\nPlease contact your administrator.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            KitButton("Try again", {
                if (!busy) scope.launch {
                    busy = true
                    try {
                        session.refreshPermissions()
                        session.currentUser.value?.takeIf { accessibleWorkspaces(it).isNotEmpty() }?.let {
                            navigator?.replaceAll(resolveStartScreen(it))
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { error = com.saporini.mobile_desktop.platform.platformMessage(e, write = false) }
                    finally { busy = false }
                }
            }, loading = busy)
            TextButton(onClick = model::logout, enabled = !busy) { Text("Sign out") }
        }
    }
}