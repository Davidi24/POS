package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.core.theme.Belleza
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.workspace.ui.WorkspacePickerScreen

object UiPreviewScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val primaryText = Color(0xFF111111)
        val mutedText = Color(0xFF666666)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "UI screens",
                    style = MaterialTheme.typography.headlineLarge,
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CormorantGaramond()
                )
                Text(
                    text = "Open any screen directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText,
                    fontFamily = Belleza()
                )

                Spacer(modifier = Modifier.height(28.dp))

                PreviewButton("Login") { navigator.push(LoginScreen) }
                PreviewButton("PIN login") { navigator.push(PinLoginScreen) }
                PreviewButton("Forgot password") { navigator.push(ForgotPasswordScreen) }
                PreviewButton("Reset password") { navigator.push(ResetPasswordScreen) }
                PreviewButton("Password updated") { navigator.push(PasswordUpdatedScreen) }
                PreviewButton("Create PIN") { navigator.push(CreatePinScreen) }
                PreviewButton("Account locked") { navigator.push(AccountLockedScreen) }
                PreviewButton("Session expired") { navigator.push(SessionExpiredScreen) }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { navigator.push(WorkspacePickerScreen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Workspaces",
                        color = primaryText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(bottom = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold
        )
    }
}
