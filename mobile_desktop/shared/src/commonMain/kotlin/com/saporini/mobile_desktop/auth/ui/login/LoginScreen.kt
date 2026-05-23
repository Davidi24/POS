package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.SaporiniColors
import com.saporini.mobile_desktop.core.theme.SaporiniWordmark
import com.saporini.mobile_desktop.home.ui.HomeScreen

object LoginScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LoginScreenModel() }
        val state by screenModel.state.collectAsState()

        var identifier by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        LaunchedEffect(state.user) {
            state.user?.let {
                SessionManager.signIn(it)
                navigator.replaceAll(HomeScreen)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaporiniColors.Cream),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = SaporiniColors.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SaporiniWordmark()

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SaporiniColors.Charcoal
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Sign in to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SaporiniColors.Muted
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text("Email or username") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = SaporiniColors.Gold
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = saporiniTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = SaporiniColors.Gold
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = SaporiniColors.Muted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        colors = saporiniTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(
                            onClick = { /* TODO password reset */ },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Forgot password?",
                                color = SaporiniColors.Gold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { screenModel.login(identifier, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !state.isLoading && identifier.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SaporiniColors.Gold,
                            contentColor = SaporiniColors.White,
                            disabledContainerColor = SaporiniColors.GoldLight,
                            disabledContentColor = SaporiniColors.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = SaporiniColors.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "SIGN IN",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = SaporiniColors.Terracotta.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(14.dp),
                                color = SaporiniColors.Terracotta,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun saporiniTextFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SaporiniColors.Gold,
        unfocusedBorderColor = SaporiniColors.Cream,
        focusedLabelColor = SaporiniColors.Gold,
        unfocusedLabelColor = SaporiniColors.Muted,
        cursorColor = SaporiniColors.Gold,
        focusedTextColor = SaporiniColors.Charcoal,
        unfocusedTextColor = SaporiniColors.Charcoal
    )
}