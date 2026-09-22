package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.saporini.mobile_desktop.core.theme.Belleza
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.core.theme.SaporiniColors
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import org.koin.compose.koinInject

object LoginScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AuthImageScaffold {
            LoginForm(
                centerContent = true,
                onPinClick = { navigator.push(PinLoginScreen) },
                onForgetPassClick = { navigator.push(ForgotPasswordScreen) },
                onLoginSuccess = { user -> navigator.replaceAll(resolveStartScreen(user)) }
            )

            PreviewHomeButton()
        }
    }
}

@Composable
private fun LoginForm(
    modifier: Modifier = Modifier,
    centerContent: Boolean = false,
    onPinClick: () -> Unit = {},
    onForgetPassClick: () -> Unit = {},
    onLoginSuccess: (CurrentUserResponse) -> Unit = {},
) {
    val screenModel = koinInject<LoginScreenModel>()
    val state by screenModel.state.collectAsState()
    LaunchedEffect(state.user) {
        state.user?.let { onLoginSuccess(it) }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SaporiniColors.White)
    ) {
        val formWidth = maxWidth
        val formHeight = maxHeight

        val isCompact = formWidth < 380.dp || formHeight < 700.dp

        val primaryText = Color(0xFF111111)
        val mutedText = Color(0xFF666666)
        val fieldBorder = Color(0xFFE1E1E1)
        val iconGray = Color(0xFF666666)
        val horizontalPadding = if (isCompact) 16.dp else 20.dp
        val topPadding = if (isCompact) 18.dp else 35.dp
        val logoHeight = if (isCompact) 72.dp else 100.dp
        val titleFontSize = if (isCompact) 24.sp else 30.sp
        val sectionSpacing = if (isCompact) 12.dp else 20.dp
        val smallSpacing = if (isCompact) 6.dp else 8.dp
        val logoToTitleSpacing = sectionSpacing * 2
        val fieldShape = RoundedCornerShape(10.dp)
        val buttonHeight = if (isCompact) 48.dp else 56.dp
        val pinIconSize = if (isCompact) 44.dp else 52.dp
        val pinIconInner = if (isCompact) 22.dp else 26.dp
        val formAlignment = if (centerContent) {
            Alignment.Center
        } else {
            Alignment.TopCenter
        }
        val formTopPadding = if (centerContent) 0.dp else topPadding

        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .align(formAlignment)
                .then(
                    if (isCompact) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                )
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = formTopPadding
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Image(
                painter = painterResource(Res.drawable.brand_logo),
                contentDescription = "Saporini Italiano",
                modifier = Modifier
                    .height(logoHeight)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(logoToTitleSpacing))

            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineLarge.copy(
                    lineHeight = (titleFontSize.value + 2).sp
                ),
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontFamily = CormorantGaramond(),
                fontSize = titleFontSize
            )

            Text(
                text = "Sign in to continue to your account.",
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 16.sp
                ),
                color = mutedText,
                fontFamily = Belleza()
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            Text(
                text = "Identifier",
                style = MaterialTheme.typography.bodyLarge,
                color = primaryText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 3.dp),
                fontFamily = Belleza()
            )

            Spacer(modifier = Modifier.height(smallSpacing))

            var email by remember { mutableStateOf("") }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = {
                    Text(
                        "Enter your username or email",
                        color = mutedText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = iconGray
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 10.dp,
                        shape = fieldShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    ),
                singleLine = true,
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fieldBorder,
                    unfocusedBorderColor = fieldBorder,
                    cursorColor = primaryText,
                    focusedTextColor = primaryText,
                    unfocusedTextColor = primaryText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            Text(
                text = "Password",
                style = MaterialTheme.typography.bodyLarge,
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontFamily = Belleza()
            )

            Spacer(modifier = Modifier.height(smallSpacing))

            var password by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = {
                    Text(
                        "Enter your password",
                        color = mutedText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = iconGray
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = iconGray
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 10.dp,
                        shape = fieldShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    ),
                singleLine = true,
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fieldBorder,
                    unfocusedBorderColor = fieldBorder,
                    cursorColor = primaryText,
                    focusedTextColor = primaryText,
                    unfocusedTextColor = primaryText,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = onForgetPassClick ,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText,
                        fontWeight = FontWeight.Normal,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            Button(
                onClick = { screenModel.login(email, password) },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Black,
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ){
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Sign in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(smallSpacing))
                Text(
                    text = error,
                    color = Color(0xFFB24443),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFDADADA),
                    thickness = 1.dp
                )
                Text(
                    text = "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFDADADA),
                    thickness = 1.dp
                )
            }

            Spacer(modifier = Modifier.height(smallSpacing * 2))

            Text(
                text = "Sign in with PIN",
                style = MaterialTheme.typography.bodyLarge,
                color = primaryText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontFamily = Belleza()
            )

            Spacer(modifier = Modifier.height(smallSpacing))

            IconButton(
                onClick = onPinClick,
                modifier = Modifier
                    .size(pinIconSize)
                    .align(Alignment.CenterHorizontally)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .background(Color.White, CircleShape)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFD0D0D0),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Dialpad,
                    contentDescription = "Sign in with PIN",
                    tint = primaryText,
                    modifier = Modifier.size(pinIconInner)
                )
            }

            if (isCompact) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
