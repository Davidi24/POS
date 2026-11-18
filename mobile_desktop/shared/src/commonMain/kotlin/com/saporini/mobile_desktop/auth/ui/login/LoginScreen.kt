package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import mobile_desktop.shared.generated.resources.auth_account_off
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
import com.saporini.mobile_desktop.core.theme.Inter
import androidx.compose.ui.text.TextStyle
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
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        AuthImageScaffold {
            LoginForm(
                centerContent = true,
                // Back to the PIN pad it came from, or to it (only shown on a connected till).
                onPinClick = {
                    val previous = navigator.items.getOrNull(navigator.items.size - 2)
                    if (previous == PinLoginScreen) navigator.pop() else navigator.push(PinLoginScreen)
                },
                onForgetPassClick = { navigator.push(ForgotPasswordScreen) },
                // A new restaurant fills in its application on the web, not in the app (user, 2026-11-17).
                onNoAccountClick = { runCatching { uriHandler.openUri(SignUpPage.url()) } },
                onLoginSuccess = { user -> navigator.replaceAll(resolveStartScreen(user)) },
                onLocked = { until -> navigator.push(AccountLockedScreen(until)) }
            )
        }
    }
}

@Composable
private fun LoginForm(
    modifier: Modifier = Modifier,
    centerContent: Boolean = false,
    onPinClick: () -> Unit = {},
    onForgetPassClick: () -> Unit = {},
    onNoAccountClick: () -> Unit = {},
    onLoginSuccess: (CurrentUserResponse) -> Unit = {},
    onLocked: (String?) -> Unit = {},
) {
    val screenModel = koinInject<LoginScreenModel>()
    val state by screenModel.state.collectAsState()
    LaunchedEffect(state.user) {
        state.user?.let { onLoginSuccess(it) }
    }
    // A locked account opens Account locked with until when (user, 2026-11-16).
    LaunchedEffect(state.problem) {
        state.problem?.takeIf { it.kind == LoginProblemKind.LOCKED }?.let { onLocked(it.lockedUntil); screenModel.clearProblem() }
    }
    // A wrong username or password shakes the two fields once.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.tries) {
        if (state.problem?.kind == LoginProblemKind.WRONG) {
            for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
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
        val titleFontSize = if (isCompact) 23.sp else 27.sp
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
                .widthIn(max = 420.dp)
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
            // No logo for now (user, 2026-11-17: "remove the logo for the moment, leave only the login in the centre").

            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineLarge.copy(
                    lineHeight = (titleFontSize.value + 2).sp
                ),
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontFamily = Inter(),
                fontSize = titleFontSize,
                letterSpacing = (-0.3).sp
            )

            Text(
                text = "Sign in to continue to your account.",
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 16.sp
                ),
                color = mutedText,
                fontFamily = Inter(),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            Text(
                text = "Identifier",
                style = MaterialTheme.typography.bodyLarge,
                color = primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 3.dp),
                fontFamily = Inter()
            )

            Spacer(modifier = Modifier.height(smallSpacing))

            var email by remember { mutableStateOf("") }
            // Tab goes from the username to the password, Shift+Tab back; Enter signs in once both are filled, from
            // either field (user, 2026-11-17).
            val emailFocus = remember { FocusRequester() }
            val passwordFocus = remember { FocusRequester() }
            var password by remember { mutableStateOf("") }
            val signIn = {
                if (email.isNotBlank() && password.isNotEmpty() && !state.isLoading) screenModel.login(email.trim(), password)
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; screenModel.clearProblem() },
                placeholder = {
                    Text(
                        "Enter your username or email",
                        color = mutedText,
                        fontFamily = Inter(),
                        fontSize = 14.5.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = iconGray
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Tab -> if (event.isShiftPressed) false else { passwordFocus.requestFocus(); true }
                            Key.Enter, Key.NumPadEnter -> {
                                if (password.isEmpty()) passwordFocus.requestFocus() else signIn()
                                true
                            }
                            else -> false
                        }
                    }
                    .graphicsLayer { translationX = shake.value * density }
                    .shadow(
                        elevation = 10.dp,
                        shape = fieldShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    ),
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter(), fontSize = 14.5.sp, color = primaryText),
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                fontFamily = Inter()
            )

            Spacer(modifier = Modifier.height(smallSpacing))

            var passwordVisible by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; screenModel.clearProblem() },
                placeholder = {
                    Text(
                        "Enter your password",
                        color = mutedText,
                        fontFamily = Inter(),
                        fontSize = 14.5.sp
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { signIn() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.Tab && event.isShiftPressed -> { emailFocus.requestFocus(); true }
                            event.key == Key.Enter || event.key == Key.NumPadEnter -> { signIn(); true }
                            else -> false
                        }
                    }
                    .graphicsLayer { translationX = shake.value * density }
                    .shadow(
                        elevation = 10.dp,
                        shape = fieldShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    ),
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter(), fontSize = 14.5.sp, color = primaryText),
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
                // No "Got an invitation?" here (user, 2026-11-17): the link in the email works in the browser, and
                // Forgot password → "I have the link" still opens Set your password for pasting it.
                TextButton(
                    onClick = onForgetPassClick ,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.bodyMedium,
                        // Darker and bolder after a few wrong passwords, so it's the way out.
                        color = if (state.warnAboutPause) primaryText else mutedText,
                        fontWeight = if (state.warnAboutPause) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = Inter(),
                        textDecoration = TextDecoration.Underline
                    )
                }
            }

            // What went wrong, sliding in above the button (user, 2026-11-16: "add animation to look good").
            LoginProblemCard(state.problem, warn = state.warnAboutPause, onRetry = { screenModel.login(email.trim(), password) })

            // At the device limit: pick a device to sign out, then this one signs in (user, 2026-11-18).
            if (state.devices.isNotEmpty()) {
                DeviceLimitDialog(state.devices, state.devicesMessage, busy = state.isLoading,
                    onPick = { id -> screenModel.login(email.trim(), password, id) }, onDismiss = screenModel::dismissDevices)
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            val filled = email.isNotBlank() && password.isNotEmpty()
            Button(
                onClick = { screenModel.login(email.trim(), password) },
                enabled = !state.isLoading && filled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    // Grey while a field is empty; black with the spinner while signing in.
                    disabledContainerColor = if (state.isLoading) Color.Black else Color(0xFFC4C4C4),
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
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter(),
                        letterSpacing = 0.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            // Only a device connected to its restaurant as a till offers the PIN (user, 2026-11-18).
            val till by com.saporini.mobile_desktop.core.session.TokenStore.till.collectAsState()
            if (till != null) {
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
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Inter()
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontFamily = Inter()
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

            Spacer(modifier = Modifier.height(sectionSpacing))
            }

            // No account: the web page where a new restaurant applies (user, 2026-11-17: "the form is filled in on the web, not here").
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Don't have an account?", color = mutedText, style = MaterialTheme.typography.bodyMedium, fontFamily = Inter())
                TextButton(onClick = onNoAccountClick, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(
                        text = "Click here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Inter(),
                        textDecoration = TextDecoration.Underline
                    )
                }
            }

            if (isCompact) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * What went wrong with the sign-in, in a small tinted card that slides in (and out when typing again): a bold title and one
 * short line (user, 2026-11-17: "not long messages, make it small, make it look good"); a paused device or network counts down
 * to when it can try again. Red for a wrong password, a switched-off account (with its picture) or a closed restaurant; amber
 * for pauses and an email not confirmed; grey for no connection, with Try again.
 */
@Composable
private fun LoginProblemCard(problem: LoginProblem?, warn: Boolean, onRetry: () -> Unit) {
    // Keeps the last problem while the card slides out.
    var shown by remember { mutableStateOf(problem) }
    if (problem != null && problem.kind != LoginProblemKind.LOCKED) shown = problem
    AnimatedVisibility(
        visible = problem != null && problem.kind != LoginProblemKind.LOCKED,
        enter = expandVertically(tween(260)) + fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 3 },
        exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
    ) {
        val p = shown ?: return@AnimatedVisibility
        val amber = Color(0xFFC8790B)
        val red = Color(0xFFB24443)
        val grey = Color(0xFF5F6B73)
        val tint = when (p.kind) {
            LoginProblemKind.DEVICE_PAUSED, LoginProblemKind.NETWORK_PAUSED, LoginProblemKind.TOO_MANY_TRIES, LoginProblemKind.EMAIL_NOT_CONFIRMED -> amber
            LoginProblemKind.NO_CONNECTION, LoginProblemKind.OTHER -> grey
            else -> red
        }
        val icon = when (p.kind) {
            LoginProblemKind.DEVICE_PAUSED -> Icons.Outlined.PhonelinkLock
            LoginProblemKind.NETWORK_PAUSED -> Icons.Outlined.WifiOff
            LoginProblemKind.TOO_MANY_TRIES -> Icons.Outlined.HourglassTop
            LoginProblemKind.EMAIL_NOT_CONFIRMED -> Icons.Outlined.MarkEmailUnread
            LoginProblemKind.RESTAURANT_CLOSED -> Icons.Outlined.Storefront
            LoginProblemKind.NO_CONNECTION -> Icons.Outlined.CloudOff
            else -> Icons.Outlined.ErrorOutline
        }
        val left = rememberSecondsLeft(p.lockedUntil.takeIf { p.kind == LoginProblemKind.DEVICE_PAUSED || p.kind == LoginProblemKind.NETWORK_PAUSED })
        val wait = left?.let { if (it > 0) "Try again in ${it / 60}:${(it % 60).toString().padStart(2, '0')}." else "You can try again now." }
        val (title, line) = when (p.kind) {
            // After the 3rd wrong password in a row, the same card warns (no number, so it tells nothing about the account).
            LoginProblemKind.WRONG -> "Wrong username or password" to (if (warn) "Careful: more wrong tries pause sign-in." else null)
            LoginProblemKind.ACCOUNT_OFF -> "Account switched off" to "Ask your manager."
            LoginProblemKind.EMAIL_NOT_CONFIRMED -> "Email not confirmed" to "Use the link we emailed you."
            LoginProblemKind.RESTAURANT_CLOSED -> "Restaurant closed" to "Ask the owner."
            LoginProblemKind.DEVICE_PAUSED -> "Paused on this device" to "Too many wrong tries. ${wait.orEmpty()}"
            LoginProblemKind.NETWORK_PAUSED -> "Paused on this network" to "Many wrong tries on this Wi-Fi. ${wait.orEmpty()}"
            LoginProblemKind.TOO_MANY_TRIES -> "Too many tries" to "Wait a moment and try again."
            LoginProblemKind.NO_CONNECTION -> "Can't reach Saporini" to "Check the internet connection."
            else -> "Something went wrong" to "Try again."
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.07f))
                .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            if (p.kind == LoginProblemKind.ACCOUNT_OFF) {
                Image(painterResource(Res.drawable.auth_account_off), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = tint)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Color(0xFF222426))
                line?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Color(0xFF5E605D)) }
                if (p.kind == LoginProblemKind.NETWORK_PAUSED) {
                    Text("Signed in? Keep working. Or use mobile data.", fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 16.sp, color = Color(0xFF8A8C88))
                }
            }
            if (p.kind == LoginProblemKind.NO_CONNECTION) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Try again", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF111111))
                }
            }
        }
    }
}

/** Seconds until [until] (the server's ISO time), ticking every second; null without one. */
@Composable
private fun rememberSecondsLeft(until: String?): Long? {
    if (until == null) return null
    val end = remember(until) { com.saporini.mobile_desktop.core.format.instantOrNull(until) } ?: return null
    fun left() = (end - kotlin.time.Clock.System.now()).inWholeSeconds.coerceAtLeast(0)
    var seconds by remember(until) { mutableStateOf(left()) }
    LaunchedEffect(until) {
        while (seconds > 0) {
            kotlinx.coroutines.delay(1000)
            seconds = left()
        }
    }
    return seconds
}

/**
 * "You're signed in on 5 devices. Sign out one to continue." (user, 2026-11-18): every device with its name and when it was
 * last used; the one unused longest comes first and is already picked; one button signs it out and signs in here.
 */
@Composable
private fun DeviceLimitDialog(
    devices: List<com.saporini.mobile_desktop.core.network.SignedInDevice>,
    message: String?,
    busy: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember(devices) { mutableStateOf(devices.first().id) }
    val now = remember { kotlin.time.Clock.System.now() }
    val zone = remember { kotlinx.datetime.TimeZone.currentSystemDefault() }
    val ink = Color(0xFF111111)
    val muted = Color(0xFF666666)
    com.saporini.mobile_desktop.core.components.AppDialog(
        title = "Sign out one device",
        onDismiss = onDismiss,
        busy = busy,
        maxWidth = 480.dp,
        buttons = {
            com.saporini.mobile_desktop.core.components.KitButton("Cancel", onDismiss,
                style = com.saporini.mobile_desktop.core.components.ButtonStyle.SECONDARY, enabled = !busy)
            com.saporini.mobile_desktop.core.components.KitButton("Sign out and continue", { onPick(picked) }, loading = busy)
        }
    ) {
        Text(message ?: "You're signed in on too many devices. Sign out one to continue.", fontFamily = Inter(), fontSize = 14.sp,
            lineHeight = 20.sp, color = muted)
        Spacer(Modifier.height(4.dp))
        devices.forEach { device ->
            val chosen = device.id == picked
            val name = device.name?.takeIf { it.isNotBlank() && it != "UNKNOWN" } ?: "Unknown device"
            val computer = listOf("computer", "windows", "mac", "linux").any { name.contains(it, ignoreCase = true) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(if (chosen) 1.5.dp else 1.dp, if (chosen) ink else Color(0xFFE2E2E2), RoundedCornerShape(12.dp))
                    .background(if (chosen) Color(0xFFF6F6F6) else Color.White)
                    .clickable(enabled = !busy) { picked = device.id }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(if (computer) Icons.Outlined.Computer else Icons.Outlined.Smartphone, null, Modifier.size(22.dp), tint = ink)
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Last used " + com.saporini.mobile_desktop.core.format.agoText(device.lastUsedAt ?: device.signedInAt, now, zone),
                        fontFamily = Inter(), fontSize = 12.5.sp, color = muted)
                }
                RadioButton(selected = chosen, onClick = if (busy) null else ({ picked = device.id }),
                    colors = RadioButtonDefaults.colors(selectedColor = ink))
            }
        }
    }
}
