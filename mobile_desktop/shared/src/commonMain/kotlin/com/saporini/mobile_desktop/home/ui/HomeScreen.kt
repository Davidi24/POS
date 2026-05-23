package com.saporini.mobile_desktop.home.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.session.SessionManager

object HomeScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { HomeScreenModel() }
        val state by screenModel.state.collectAsState()
        val user by SessionManager.currentUser.collectAsState()

        LaunchedEffect(state.loggedOut) {
            if (state.loggedOut) {
                navigator.replaceAll(LoginScreen)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Hello, ${user?.firstName ?: "Guest"}")

            Spacer(modifier = Modifier.height(8.dp))

            Text("Email: ${user?.email ?: "—"}")
            Text("Roles: ${user?.roles?.joinToString() ?: "—"}")

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { screenModel.logout() },
                enabled = !state.isLoggingOut
            ) {
                if (state.isLoggingOut) CircularProgressIndicator() else Text("Log out")
            }
        }
    }
}