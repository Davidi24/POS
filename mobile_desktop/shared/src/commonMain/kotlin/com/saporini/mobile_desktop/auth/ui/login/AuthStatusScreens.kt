package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.core.theme.Belleza
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.getPlatform
import kotlinx.coroutines.delay
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource

object PasswordUpdatedScreen : Screen {
    @Composable
    override fun Content() {
        AuthScreenFrame { isCompact ->
            val successGreen = Color(0xFF2F8F4E)

            AuthLogo(isCompact)
            Spacer(modifier = Modifier.height(64.dp))
            AnimatedSuccessCheck(successGreen = successGreen)
            Spacer(modifier = Modifier.height(24.dp))
            AuthTitle("Password updated!")
            Spacer(modifier = Modifier.height(10.dp))
            AuthBody("Your password has been changed\nsuccessfully.")
            Spacer(modifier = Modifier.height(56.dp))
            BackToLoginButton()
        }
    }
}

object CreatePinScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        CompactAuthFrame { isCompact ->
            var pin by remember { mutableStateOf("") }
            var confirmPin by remember { mutableStateOf("") }
            var editingConfirm by remember { mutableStateOf(false) }
            var validationState by remember { mutableStateOf(PinValidationState.Neutral) }
            var shakeTick by remember { mutableStateOf(0) }
            var shakeTarget by remember { mutableStateOf(0f) }
            val activePin = if (editingConfirm) confirmPin else pin
            val maxPinLength = 6
            val keySize = if (isCompact) 56.dp else 64.dp
            val keySpacing = if (isCompact) 12.dp else 16.dp
            val shakeOffset by animateFloatAsState(
                targetValue = shakeTarget,
                animationSpec = tween(durationMillis = 55),
                label = "create-pin-shake-offset"
            )
            fun shake() {
                shakeTick += 1
            }
            fun appendDigit(digit: String) {
                if (activePin.length >= maxPinLength) return
                validationState = PinValidationState.Neutral
                if (editingConfirm) {
                    confirmPin += digit
                } else {
                    pin += digit
                    if (pin.length >= maxPinLength) {
                        editingConfirm = true
                    }
                }
            }
            fun deleteDigit() {
                validationState = PinValidationState.Neutral
                if (editingConfirm) {
                    if (confirmPin.isNotEmpty()) {
                        confirmPin = confirmPin.dropLast(1)
                    } else {
                        editingConfirm = false
                    }
                } else if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                }
            }
            fun validatePins() {
                if (pin.length < 4 || confirmPin.length < 4) {
                    validationState = PinValidationState.Error
                    shake()
                    return
                }
                validationState = if (pin == confirmPin) {
                    PinValidationState.Success
                } else {
                    shake()
                    PinValidationState.Error
                }
            }
            LaunchedEffect(pin, confirmPin) {
                if (pin.length == maxPinLength && confirmPin.length == maxPinLength) {
                    validationState = if (pin == confirmPin) {
                        PinValidationState.Success
                    } else {
                        shake()
                        PinValidationState.Error
                    }
                }
            }
            LaunchedEffect(shakeTick) {
                if (shakeTick > 0) {
                    shakeTarget = 12f
                    delay(55)
                    shakeTarget = -12f
                    delay(55)
                    shakeTarget = 8f
                    delay(55)
                    shakeTarget = 0f
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { navigator.popOrPreview() },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black
                    )
                }
                AuthTitle(
                    text = "Create your PIN",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            AuthBody("Set a 4-6 digit PIN to sign in quickly\nand securely.")
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier.offset(x = shakeOffset.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PinLabel("Enter PIN")
                Spacer(modifier = Modifier.height(10.dp))
                PinDots(
                    filledCount = pin.length,
                    state = validationState
                )
                Spacer(modifier = Modifier.height(18.dp))
                PinLabel("Confirm PIN")
                Spacer(modifier = Modifier.height(10.dp))
                PinDots(
                    filledCount = confirmPin.length,
                    state = validationState
                )
            }
            Spacer(modifier = Modifier.height(44.dp))
            PinNumberPad(
                keySize = keySize,
                keySpacing = keySpacing,
                onDigit = ::appendDigit,
                onDelete = ::deleteDigit
            )
            Spacer(modifier = Modifier.height(8.dp))
            BlackButton("Create PIN", onClick = ::validatePins)
        }
    }
}

object AccountLockedScreen : Screen {
    @Composable
    override fun Content() {
        AuthScreenFrame { isCompact ->
            val dangerRed = Color(0xFFB24443)

            AuthLogo(isCompact)
            Spacer(modifier = Modifier.height(34.dp))
            AnimatedLockIcon(dangerRed = dangerRed)
            Spacer(modifier = Modifier.height(24.dp))
            AuthTitle("Account locked")
            Spacer(modifier = Modifier.height(10.dp))
            AuthBody("Too many failed login attempts.")
            Spacer(modifier = Modifier.height(18.dp))
            AuthBody("For your security, your account has\nbeen locked.")
            Spacer(modifier = Modifier.height(18.dp))
            AuthBody("Please try again later or contact your\nadministrator.")
            Spacer(modifier = Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFF5F5F5F),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Try again in 14:59",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF222222),
                    fontWeight = FontWeight.Bold,
                    fontFamily = Belleza()
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            BackToLoginText()
        }
    }
}

object SessionExpiredScreen : Screen {
    @Composable
    override fun Content() {
        AuthScreenFrame { isCompact ->
            val mutedGray = Color(0xFF666666)

            AuthLogo(isCompact)
            Spacer(modifier = Modifier.height(38.dp))
            CircleIcon(
                tint = mutedGray,
                borderColor = Color(0xFFD5D5D5)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HourglassEmpty,
                    contentDescription = null,
                    tint = mutedGray,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(34.dp))
            AuthTitle("Session expired")
            Spacer(modifier = Modifier.height(18.dp))
            AuthBody("For your security, your session has\nexpired due to inactivity.")
            Spacer(modifier = Modifier.height(18.dp))
            AuthBody("Please sign in again to continue.")
            Spacer(modifier = Modifier.height(82.dp))
            BackToLoginButton()
        }
    }
}

@Composable
private fun AuthScreenFrame(content: @Composable ColumnScope.(Boolean) -> Unit) {
    AuthImageScaffold {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            val isCompact = maxWidth < 380.dp || maxHeight < 700.dp
            val isDesktop = remember { !getPlatform().name.startsWith("Android") }
            val shouldCenterVertically = isDesktop || !isCompact
            val topPadding = if (shouldCenterVertically) 0.dp else 34.dp

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .align(if (shouldCenterVertically) Alignment.Center else Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isCompact) 16.dp else 20.dp, vertical = topPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { content(isCompact) }
            )

            PreviewHomeButton()
        }
    }
}

@Composable
private fun CompactAuthFrame(content: @Composable ColumnScope.(Boolean) -> Unit) {
    AuthImageScaffold {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            val isCompact = maxWidth < 380.dp || maxHeight < 700.dp
            val isDesktop = remember { !getPlatform().name.startsWith("Android") }
            val shouldCenterVertically = isDesktop || !isCompact

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .align(if (shouldCenterVertically) Alignment.Center else Alignment.TopCenter)
                    .padding(
                        start = if (isCompact) 16.dp else 20.dp,
                        end = if (isCompact) 16.dp else 20.dp,
                        top = if (shouldCenterVertically) 0.dp else 10.dp,
                        bottom = 12.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { content(isCompact) }
            )

            PreviewHomeButton()
        }
    }
}

@Composable
private fun AuthLogo(isCompact: Boolean) {
    Image(
        painter = painterResource(Res.drawable.brand_logo),
        contentDescription = "Saporini Italiano",
        modifier = Modifier.height(if (isCompact) 72.dp else 100.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun CircleIcon(
    tint: Color,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun AnimatedSuccessCheck(successGreen: Color) {
    var startDrawing by remember { mutableStateOf(false) }
    var checkVisible by remember { mutableStateOf(false) }

    val ringProgress by animateFloatAsState(
        targetValue = if (startDrawing) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "password-updated-ring-progress"
    )
    val successColor by animateColorAsState(
        targetValue = if (checkVisible) successGreen else Color(0xFF111111),
        animationSpec = tween(durationMillis = 400),
        label = "password-updated-success-color"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checkVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EaseOutBack),
        label = "password-updated-check-scale"
    )

    LaunchedEffect(Unit) {
        startDrawing = true
        delay(950)
        checkVisible = true
    }

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            drawArc(
                color = successColor,
                startAngle = -90f,
                sweepAngle = 360f * ringProgress,
                useCenter = false,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = successColor,
            modifier = Modifier
                .size(58.dp)
                .scale(checkScale)
        )
    }
}

@Composable
private fun AnimatedLockIcon(dangerRed: Color) {
    var startDrawing by remember { mutableStateOf(false) }
    var lockVisible by remember { mutableStateOf(false) }

    val ringProgress by animateFloatAsState(
        targetValue = if (startDrawing) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "account-locked-ring-progress"
    )
    val lockColor by animateColorAsState(
        targetValue = if (lockVisible) dangerRed else Color(0xFF111111),
        animationSpec = tween(durationMillis = 400),
        label = "account-locked-color"
    )
    val lockScale by animateFloatAsState(
        targetValue = if (lockVisible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 350, easing = EaseOutBack),
        label = "account-locked-lock-scale"
    )

    LaunchedEffect(Unit) {
        startDrawing = true
        delay(950)
        lockVisible = true
    }

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            drawArc(
                color = lockColor,
                startAngle = -90f,
                sweepAngle = 360f * ringProgress,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = lockColor,
            modifier = Modifier
                .size(50.dp)
                .scale(lockScale)
        )
    }
}

@Composable
private fun AuthTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 32.sp),
        color = Color(0xFF111111),
        fontWeight = FontWeight.Bold,
        fontFamily = CormorantGaramond(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AuthBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
        color = Color(0xFF666666),
        fontFamily = Belleza(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PinLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFF111111),
        fontWeight = FontWeight.Bold,
        fontFamily = Belleza()
    )
}

private enum class PinValidationState {
    Neutral,
    Error,
    Success
}

@Composable
private fun PinDots(
    filledCount: Int,
    state: PinValidationState
) {
    val activeColor = when (state) {
        PinValidationState.Neutral -> Color(0xFF111111)
        PinValidationState.Error -> Color(0xFFB24443)
        PinValidationState.Success -> Color(0xFF2F8F4E)
    }
    val emptyColor = Color(0xFFD5D5D5)
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        repeat(6) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (index < filledCount) activeColor else Color.Transparent,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (index < filledCount) activeColor else emptyColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinNumberPad(
    keySize: androidx.compose.ui.unit.Dp,
    keySpacing: androidx.compose.ui.unit.Dp,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "delete")
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        keys.forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                rowKeys.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(keySize))
                        "delete" -> PinNumberKey(
                            size = keySize,
                            showBorder = false,
                            onClick = onDelete
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = "Delete",
                                tint = Color(0xFF111111)
                            )
                        }
                        else -> PinNumberKey(
                            size = keySize,
                            onClick = { onDigit(key) }
                        ) {
                            Text(
                                text = key,
                                color = Color(0xFF111111),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(keySpacing))
        }
    }
}

@Composable
private fun PinNumberKey(
    size: androidx.compose.ui.unit.Dp,
    showBorder: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 1.dp,
                        color = Color(0xFFD0D0D0),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun BlackButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )
    }
}

@Composable
private fun BackToLoginButton() {
    val navigator = LocalNavigator.currentOrThrow
    BlackButton("Back to login") {
        navigator.push(LoginScreen)
    }
}

@Composable
private fun BackToLoginText() {
    val navigator = LocalNavigator.currentOrThrow
    TextButton(onClick = { navigator.push(LoginScreen) }) {
        Text(
            text = "Back to login",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF333333),
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline
        )
    }
}

private fun cafe.adriel.voyager.navigator.Navigator.popOrPreview() {
    replaceAll(UiPreviewScreen)
}
