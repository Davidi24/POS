package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.data.dto.PasswordLinkView
import com.saporini.mobile_desktop.core.theme.Belleza
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.getPlatform
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Choosing a password from an emailed link: the owner's invitation, or "Forgot password?". The link usually opens the
 * page in the browser; here they can paste it instead. [link] fills the field when the app is opened with one.
 */
data class ResetPasswordScreen(val link: String = "") : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<com.saporini.mobile_desktop.auth.data.repository.AuthRepository>()
        val model = rememberScreenModel { SetPasswordScreenModel(repository) }
        val state by model.state.collectAsState()
        LaunchedEffect(Unit) { model.start(link) }
        val goBack: () -> Unit = {
            if (navigator.canPop) navigator.pop() else navigator.replaceAll(LoginScreen)
        }
        val toLogin: () -> Unit = { navigator.replaceAll(LoginScreen) }

        AuthImageScaffold {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                val isCompact = maxWidth < 380.dp || maxHeight < 700.dp
                val isDesktop = remember { !getPlatform().name.startsWith("Android") }
                val shouldCenterVertically = (isDesktop || !isCompact) && maxHeight >= 760.dp

                val primaryText = Color(0xFF111111)
                val mutedText = Color(0xFF666666)
                val fieldBorder = Color(0xFFE1E1E1)
                val successGreen = Color(0xFF2F8F4E)
                val dangerRed = Color(0xFFB24443)
                val horizontalPadding = if (isCompact) 16.dp else 20.dp
                val logoHeight = if (isCompact) 64.dp else 88.dp
                val titleFontSize = if (isCompact) 24.sp else 30.sp
                val sectionSpacing = if (isCompact) 12.dp else 18.dp
                val smallSpacing = if (isCompact) 6.dp else 8.dp
                val buttonHeight = if (isCompact) 48.dp else 56.dp
                val fieldShape = RoundedCornerShape(10.dp)

                var visible by remember { mutableStateOf(false) }

                if (!state.done) {
                    IconButton(
                        onClick = goBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Back",
                            tint = primaryText
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .align(if (shouldCenterVertically) Alignment.Center else Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = if (shouldCenterVertically) 0.dp else 56.dp,
                            bottom = 24.dp
                        ),
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

                    if (state.done) {
                        DoneContent(state.view, primaryText, mutedText, successGreen, buttonHeight, toLogin)
                        return@Column
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

                    val view = state.view?.takeIf { it.valid }
                    Text(
                        text = if (view != null && !view.invite) "Create new password" else "Set your password",
                        style = MaterialTheme.typography.headlineLarge.copy(lineHeight = (titleFontSize.value + 2).sp),
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CormorantGaramond(),
                        fontSize = titleFontSize
                    )

                    Spacer(modifier = Modifier.height(sectionSpacing / 3))

                    Text(
                        text = welcome(view),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                        color = mutedText,
                        fontFamily = Belleza()
                    )

                    Spacer(modifier = Modifier.height(sectionSpacing * 1.2f))

                    FieldLabel("Link from your email", primaryText)
                    Spacer(modifier = Modifier.height(smallSpacing))
                    AuthField(
                        value = state.link,
                        onValueChange = model::changeLink,
                        placeholder = "Paste the link here",
                        icon = Icons.Outlined.Link,
                        fieldShape = fieldShape,
                        fieldBorder = fieldBorder,
                        primaryText = primaryText,
                        mutedText = mutedText
                    )
                    Spacer(modifier = Modifier.height(smallSpacing))
                    LinkStatus(state, primaryText, mutedText, successGreen, dangerRed, model::retryCheck)

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    FieldLabel("New password", primaryText)
                    Spacer(modifier = Modifier.height(smallSpacing))
                    AuthField(
                        value = state.password,
                        onValueChange = model::changePassword,
                        placeholder = "Enter new password",
                        icon = Icons.Outlined.Lock,
                        password = true,
                        visible = visible,
                        onToggleVisible = { visible = !visible },
                        fieldShape = fieldShape,
                        fieldBorder = fieldBorder,
                        primaryText = primaryText,
                        mutedText = mutedText
                    )

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    FieldLabel("Confirm password", primaryText)
                    Spacer(modifier = Modifier.height(smallSpacing))
                    AuthField(
                        value = state.confirm,
                        onValueChange = model::changeConfirm,
                        placeholder = "Confirm new password",
                        icon = Icons.Outlined.Lock,
                        password = true,
                        visible = visible,
                        onToggleVisible = { visible = !visible },
                        onDone = model::save,
                        fieldShape = fieldShape,
                        fieldBorder = fieldBorder,
                        primaryText = primaryText,
                        mutedText = mutedText
                    )

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    (passwordChecks(state.password) + ("Both passwords are the same" to state.same)).forEach { (label, satisfied) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (satisfied) successGreen else Color(0xFFB9B9B9),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (satisfied) primaryText else mutedText,
                                fontFamily = Belleza()
                            )
                        }
                    }

                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(smallSpacing * 1.5f))
                        Text(text = error, color = dangerRed, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing * 1.4f))

                    Button(
                        onClick = model::save,
                        enabled = state.canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(buttonHeight),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF8A8A8A),
                            disabledContentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (view != null && !view.invite) "Update password" else "Set password",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(smallSpacing))

                    TextButton(
                        onClick = toLogin,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Back to login",
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryText,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }

                PreviewHomeButton()
            }
        }
    }
}

private fun welcome(view: PasswordLinkView?): String = when {
    view == null -> "Paste the link from your invitation or reset email. You can also just open the link: it works in any browser."
    view.invite -> buildString {
        append("Hi ${view.firstName ?: "there"}")
        append(if (view.restaurantName != null) ", you're the owner of ${view.restaurantName}. " else ", your account is ready. ")
        append("Choose the password you'll sign in with.")
    }
    else -> "Hi ${view.firstName ?: "there"}, choose a new password for your account."
}

@Composable
private fun LinkStatus(
    state: SetPasswordState,
    primaryText: Color,
    mutedText: Color,
    successGreen: Color,
    dangerRed: Color,
    onRetry: () -> Unit
) {
    val view = state.view
    when {
        state.link.isBlank() -> Text(
            "Copy the link from the email and paste it here.",
            style = MaterialTheme.typography.bodySmall, color = mutedText, fontFamily = Belleza()
        )
        state.checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = mutedText, strokeWidth = 1.5.dp)
            Text("Checking the link…", style = MaterialTheme.typography.bodySmall, color = mutedText)
        }
        state.linkError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(Icons.Outlined.ErrorOutline, dangerRed)
            Text(state.linkError, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = dangerRed)
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Try again", style = MaterialTheme.typography.bodySmall, color = primaryText, textDecoration = TextDecoration.Underline)
            }
        }
        view == null -> Unit
        view.valid -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(Icons.Outlined.CheckCircle, successGreen)
            Text(
                buildAnnotatedString {
                    append("The link works. You'll sign in as ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryText)) { append(view.username ?: view.email.orEmpty()) }
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall, color = successGreen
            )
        }
        else -> Row(verticalAlignment = Alignment.Top) {
            StatusIcon(Icons.Outlined.ErrorOutline, dangerRed)
            Text(view.message ?: "This link doesn't work.", style = MaterialTheme.typography.bodySmall, color = dangerRed)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: Color) {
    Icon(icon, null, Modifier.padding(end = 6.dp).size(16.dp), tint = tint)
}

@Composable
private fun DoneContent(
    view: PasswordLinkView?,
    primaryText: Color,
    mutedText: Color,
    successGreen: Color,
    buttonHeight: androidx.compose.ui.unit.Dp,
    onLogin: () -> Unit
) {
    val firstPassword = view?.invite == true
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(40.dp))
        AnimatedSuccessCheck(successGreen = successGreen)
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = if (firstPassword) "Your password is set" else "Password updated!",
            style = MaterialTheme.typography.headlineLarge,
            color = primaryText,
            fontWeight = FontWeight.Bold,
            fontFamily = CormorantGaramond(),
            fontSize = 30.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildAnnotatedString {
                append("Sign in with ")
                val name = view?.username ?: view?.email
                if (name != null) {
                    append("your username ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryText)) { append(name) }
                } else {
                    append("your username or email")
                }
                append(" and your new password.")
                if (!firstPassword) append("\nYou were signed out on your other devices.")
            },
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = mutedText,
            fontFamily = Belleza(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(buttonHeight),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Go to sign in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        }
    }
}

@Composable
private fun FieldLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 3.dp),
        fontFamily = Belleza()
    )
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    fieldShape: RoundedCornerShape,
    fieldBorder: Color,
    primaryText: Color,
    mutedText: Color,
    password: Boolean = false,
    visible: Boolean = false,
    onToggleVisible: () -> Unit = {},
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = mutedText) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = mutedText) },
        trailingIcon = if (!password) null else {
            {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        tint = mutedText
                    )
                }
            }
        },
        visualTransformation = if (!password || visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
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
}
