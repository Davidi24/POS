package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MarkAsUnread
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
object ForgotPasswordScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val goBack: () -> Unit = {
            navigator.replaceAll(UiPreviewScreen)
        }

        AuthImageScaffold {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
            val isCompact = maxWidth < 380.dp || maxHeight < 700.dp
            val isDesktop = remember { !getPlatform().name.startsWith("Android") }
            val shouldCenterVertically = isDesktop || !isCompact

            val primaryText = Color(0xFF111111)
            val mutedText = Color(0xFF666666)
            val fieldBorder = Color(0xFFE1E1E1)
            val horizontalPadding = if (isCompact) 16.dp else 20.dp
            val topPadding = if (shouldCenterVertically) 0.dp else 48.dp
            val logoHeight = if (isCompact) 72.dp else 100.dp
            val titleFontSize = if (isCompact) 24.sp else 30.sp
            val sectionSpacing = if (isCompact) 12.dp else 20.dp
            val smallSpacing = if (isCompact) 6.dp else 8.dp
            val buttonHeight = if (isCompact) 48.dp else 56.dp
            val fieldShape = RoundedCornerShape(10.dp)
            var email by remember { mutableStateOf("") }
            var resetLinkSent by remember { mutableStateOf(false) }

            if (!resetLinkSent) {
                IconButton(
                    onClick = goBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                    .then(
                        if (isCompact) Modifier.verticalScroll(rememberScrollState())
                        else Modifier
                    )
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPadding
                    ),
                horizontalAlignment = Alignment.Start
            ) {
                Crossfade(
                    targetState = resetLinkSent,
                    label = "forgot-password-state"
                ) { isSent ->
                    if (isSent) {
                        ResetLinkSentContent(
                            isCompact = isCompact,
                            primaryText = primaryText,
                            mutedText = mutedText,
                            titleFontSize = titleFontSize,
                            sectionSpacing = sectionSpacing,
                            smallSpacing = smallSpacing,
                            logoHeight = logoHeight,
                            buttonHeight = buttonHeight,
                            onBackToLogin = { navigator.pop() }
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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

                            Spacer(modifier = Modifier.height(sectionSpacing * 2))

                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    lineHeight = (titleFontSize.value + 2).sp
                                ),
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CormorantGaramond(),
                                fontSize = titleFontSize
                            )
                            Spacer(modifier = Modifier.height(sectionSpacing / 3))
                            Text(
                                text = "No worries! Enter your email and we'll send you a link to reset your password.",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 16.sp),
                                color = mutedText,
                                fontFamily = Belleza()
                            )

                            Spacer(modifier = Modifier.height(sectionSpacing * 3))

                            Text(
                                text = "Email",
                                style = MaterialTheme.typography.bodyLarge,
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 3.dp),
                                fontFamily = Belleza()
                            )

                            Spacer(modifier = Modifier.height(smallSpacing))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = {
                                    Text("Enter your email", color = mutedText)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Email,
                                        contentDescription = null,
                                        tint = mutedText
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

                            Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

                            Button(
                                onClick = { resetLinkSent = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(buttonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Send reset link",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(sectionSpacing))

                            TextButton(
                                onClick = { navigator.pop() },
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
                    }
                }
            }

                PreviewHomeButton()
            }
        }
    }
}

@Composable
private fun ResetLinkSentContent(
    isCompact: Boolean,
    primaryText: Color,
    mutedText: Color,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    sectionSpacing: androidx.compose.ui.unit.Dp,
    smallSpacing: androidx.compose.ui.unit.Dp,
    logoHeight: androidx.compose.ui.unit.Dp,
    buttonHeight: androidx.compose.ui.unit.Dp,
    onBackToLogin: () -> Unit
) {
    val successGreen = Color(0xFF2F8F4E)

    val circleSize = if (isCompact) 120.dp else 140.dp
    val emailIconSize = if (isCompact) 56.dp else 66.dp
    val checkSize = if (isCompact) 28.dp else 32.dp

    var startDrawing by remember { mutableStateOf(false) }
    var checkVisible by remember { mutableStateOf(false) }

    // 0f..1f — how much of the ring is drawn
    val ringProgress by animateFloatAsState(
        targetValue = if (startDrawing) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "ring-progress"
    )

    // black while drawing, green when the ring is complete
    val successColor by animateColorAsState(
        targetValue = if (checkVisible) successGreen else Color(0xFF111111),
        animationSpec = tween(durationMillis = 400),
        label = "success-color"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (checkVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EaseOutBack),
        label = "check-scale"
    )

    LaunchedEffect(Unit) {
        startDrawing = true
        delay(950)
        checkVisible = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.brand_logo),
            contentDescription = "Saporini Italiano",
            modifier = Modifier.height(logoHeight),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(sectionSpacing * 2.5f))

        Box(
            modifier = Modifier.size(circleSize),
            contentAlignment = Alignment.Center
        ) {
            // ring that draws itself around
            Canvas(modifier = Modifier.size(circleSize)) {
                drawArc(
                    color = successColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress,
                    useCenter = false,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Icon(
                imageVector = Icons.Outlined.MarkAsUnread,
                contentDescription = null,
                tint = successColor,
                modifier = Modifier.size(emailIconSize)
            )

            // check badge on the envelope's top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = emailIconSize / 2 - checkSize / 4,
                        y = -emailIconSize / 2 + checkSize / 4
                    )
                    .scale(checkScale)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = successColor,
                    modifier = Modifier.size(checkSize)
                )
            }
        }

        Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

        Text(
            text = "Check your email",
            style = MaterialTheme.typography.headlineLarge.copy(
                lineHeight = (titleFontSize.value + 2).sp
            ),
            color = primaryText,
            fontWeight = FontWeight.Bold,
            fontFamily = CormorantGaramond(),
            fontSize = titleFontSize
        )

        Spacer(modifier = Modifier.height(smallSpacing * 2))

        Text(
            text = "We've sent a password reset link to\nyour email address.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            color = mutedText,
            fontFamily = Belleza(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(sectionSpacing))

        Text(
            text = "If you don't see it, check your spam\nor junk folder.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            color = mutedText,
            fontFamily = Belleza(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(sectionSpacing * 3))

        Button(
            onClick = onBackToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "Back to login",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }
    }
}
