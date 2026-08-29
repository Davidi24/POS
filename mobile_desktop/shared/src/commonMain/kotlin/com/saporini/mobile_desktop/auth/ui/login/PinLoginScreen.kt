package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.core.theme.Belleza
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource

object PinLoginScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        AuthImageScaffold {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
            val isCompact = maxWidth < 380.dp || maxHeight < 700.dp

            val primaryText = Color(0xFF111111)
            val mutedText = Color(0xFF666666)
            val logoHeight = if (isCompact) 72.dp else 100.dp
            val titleFontSize = if (isCompact) 24.sp else 30.sp
            val sectionSpacing = if (isCompact) 12.dp else 20.dp
            val keySize = if (isCompact) 56.dp else 64.dp
            val keySpacing = if (isCompact) 12.dp else 16.dp

            var pin by remember { mutableStateOf("") }
            val maxPinLength = 6

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(Res.drawable.brand_logo),
                    contentDescription = "Saporini Italiano",
                    modifier = Modifier.height(logoHeight),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(sectionSpacing * 2))

                Text(
                    text = "Enter your PIN",
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CormorantGaramond(),
                    fontSize = titleFontSize
                )

                Text(
                    text = "Enter your 4\u20136 digit PIN to sign in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText,
                    fontFamily = Belleza()
                )

                Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

                PinDots(
                    filledCount = pin.length,
                    total = maxPinLength
                )

                Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                )

                keys.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(keySpacing)
                    ) {
                        rowKeys.forEach { key ->
                            when (key) {
                                "" -> Spacer(modifier = Modifier.size(keySize))
                                "⌫" -> PinKey(
                                    size = keySize,
                                    showBorder = false,
                                    onClick = { pin = pin.dropLast(1) }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                        contentDescription = "Delete",
                                        tint = primaryText
                                    )
                                }
                                else -> PinKey(
                                    size = keySize,
                                    onClick = {
                                        if (pin.length < maxPinLength) pin += key
                                    }
                                ) {
                                    Text(
                                        text = key,
                                        color = primaryText,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(keySpacing))
                }

                Spacer(modifier = Modifier.height(sectionSpacing))

                TextButton(onClick = { navigator.pop() }) {
                    Text(
                        text = "Back to login",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText,
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

@Composable
private fun PinDots(
    filledCount: Int,
    total: Int
) {
    val filledColor = Color(0xFF2E7D32)
    val emptyColor = Color(0xFFD0D0D0)

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (index < filledCount) filledColor else Color.Transparent,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (index < filledCount) filledColor else emptyColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinKey(
    size: androidx.compose.ui.unit.Dp,
    showBorder: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (showBorder) Modifier.border(
                    width = 1.dp,
                    color = Color(0xFFD0D0D0),
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
