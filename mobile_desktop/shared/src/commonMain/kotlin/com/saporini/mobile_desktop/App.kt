package com.saporini.mobile_desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    var isCheckingSession by remember { mutableStateOf(true) }
    val sessionManager = koinInject<SessionManager>()
    val authRepository = koinInject<AuthRepository>()

    val isAuthenticated by sessionManager.isAuthenticated.collectAsState()
    val user by sessionManager.currentUser.collectAsState()
    val contextError by sessionManager.contextError.collectAsState()
    var startupError by remember { mutableStateOf(false) }
    var startupAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(user?.id, user?.restaurantId, user?.defaultBranchId) {
        if (user?.restaurantId == null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(15_000)
            try { sessionManager.refreshPermissions() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Requests retain their retryable failure state during outages. */ }
        }
    }

    LaunchedEffect(startupAttempt) {
        startupError = false
        isCheckingSession = true
        if (TokenStore.isLoggedIn) {
            try {
                val me = authRepository.me()
                sessionManager.signIn(me)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is com.saporini.mobile_desktop.core.network.ApiException && e.status in listOf(400, 401, 403)) sessionManager.signOut()
                else startupError = true
            }
        }
        isCheckingSession = false
    }

    SaporiniTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (isCheckingSession) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (startupError) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.saporini.mobile_desktop.core.components.PageState(
                        com.saporini.mobile_desktop.core.components.PageStateKind.FAILED,
                        "Session couldn't be restored", "Check the connection and try again.",
                        action = { com.saporini.mobile_desktop.core.components.RetryText(onClick = { startupAttempt++ }) })
                }
            } else {
                val startScreen = user?.let { resolveStartScreen(it) } ?: LoginScreen
                Navigator(startScreen) { navigator ->
                    LaunchedEffect(isAuthenticated, user?.id) {
                        if (!isAuthenticated) {
                            navigator.replaceAll(LoginScreen)
                        } else if (navigator.lastItem == LoginScreen) {
                            user?.let { navigator.replaceAll(resolveStartScreen(it)) }
                        }
                    }
                    LaunchedEffect(user, navigator.lastItem) {
                        val current = user ?: return@LaunchedEffect
                        val allowed = com.saporini.mobile_desktop.core.session.accessibleWorkspaces(current)
                        val required = com.saporini.mobile_desktop.core.session.Workspace.entries.firstOrNull {
                            com.saporini.mobile_desktop.workspace.ui.screenFor(it) == navigator.lastItem
                        }
                        if ((required != null && required !in allowed) ||
                            (navigator.lastItem == com.saporini.mobile_desktop.workspace.ui.NoAccessScreen && allowed.isNotEmpty())) {
                            navigator.replaceAll(resolveStartScreen(current))
                        }
                    }
                    LaunchedEffect(contextError) {
                        if (contextError != null) navigator.replaceAll(com.saporini.mobile_desktop.platform.ui.RestaurantListScreen)
                    }
                    key(user?.id, user?.restaurantId, user?.defaultBranchId) { CurrentScreen() }
                    com.saporini.mobile_desktop.core.components.ScreenMessages(contextError, null, sessionManager::clearContextError)
                }
            }
        }
    }
}