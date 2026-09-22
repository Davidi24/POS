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

    LaunchedEffect(Unit) {
        if (TokenStore.isLoggedIn) {
            try {
                val me = authRepository.me()
                sessionManager.signIn(me)
            } catch (_: Exception) {
                TokenStore.clear()
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
            } else {
                val startScreen = user?.let { resolveStartScreen(it) } ?: LoginScreen
                Navigator(startScreen) { navigator ->
                    LaunchedEffect(isAuthenticated) {
                        if (!isAuthenticated) {
                            navigator.replaceAll(LoginScreen)
                        }
                    }
                    CurrentScreen()
                }
            }
        }
    }
}