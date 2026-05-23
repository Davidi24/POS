package com.saporini.mobile_desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.home.ui.HomeScreen

@Composable
@Preview
fun App() {
    var isCheckingSession by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (TokenStore.isLoggedIn) {
            try {
                val me = AuthRepository().me()
                SessionManager.signIn(me)
            } catch (_: Exception) {
                TokenStore.clear()
            }
        }
        isCheckingSession = false
    }

    SaporiniTheme  {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (isCheckingSession) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val isAuthenticated by SessionManager.isAuthenticated.collectAsState()
                val startScreen = if (isAuthenticated) HomeScreen else LoginScreen
                Navigator(startScreen) { navigator -> SlideTransition(navigator) }
            }
        }
    }
}