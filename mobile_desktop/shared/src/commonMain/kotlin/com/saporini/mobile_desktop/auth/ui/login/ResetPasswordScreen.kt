package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.core.theme.Belleza
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.getPlatform
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource

object ResetPasswordScreen : Screen {

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
            val successGreen = Color(0xFF2F8F4E)
            val horizontalPadding = if (isCompact) 16.dp else 20.dp
            val topPadding = if (shouldCenterVertically) 0.dp else 48.dp
            val logoHeight = if (isCompact) 72.dp else 100.dp
            val titleFontSize = if (isCompact) 24.sp else 30.sp
            val sectionSpacing = if (isCompact) 12.dp else 20.dp
            val smallSpacing = if (isCompact) 6.dp else 8.dp
            val buttonHeight = if (isCompact) 48.dp else 56.dp
            val fieldShape = RoundedCornerShape(10.dp)

            var newPassword by remember { mutableStateOf("") }
            var confirmPassword by remember { mutableStateOf("") }
            var newVisible by remember { mutableStateOf(false) }
            var confirmVisible by remember { mutableStateOf(false) }

            val rules = listOf(
                "At least 8 characters" to (newPassword.length >= 8),
                "One uppercase letter" to newPassword.any { it.isUpperCase() },
                "One number" to newPassword.any { it.isDigit() },
                "One special character" to newPassword.any { !it.isLetterOrDigit() }
            )

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
                    text = "Create new password",
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
                    text = "Your new password must be different from previous ones.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    color = mutedText,
                    fontFamily = Belleza()
                )

                Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

                Text(
                    text = "New password",
                    style = MaterialTheme.typography.bodyLarge,
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp),
                    fontFamily = Belleza()
                )

                Spacer(modifier = Modifier.height(smallSpacing))

                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = "Enter new password",
                    visible = newVisible,
                    onToggleVisible = { newVisible = !newVisible },
                    fieldShape = fieldShape,
                    fieldBorder = fieldBorder,
                    primaryText = primaryText,
                    mutedText = mutedText
                )

                Spacer(modifier = Modifier.height(sectionSpacing))

                Text(
                    text = "Confirm password",
                    style = MaterialTheme.typography.bodyLarge,
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp),
                    fontFamily = Belleza()
                )

                Spacer(modifier = Modifier.height(smallSpacing))

                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "Confirm new password",
                    visible = confirmVisible,
                    onToggleVisible = { confirmVisible = !confirmVisible },
                    fieldShape = fieldShape,
                    fieldBorder = fieldBorder,
                    primaryText = primaryText,
                    mutedText = mutedText
                )

                Spacer(modifier = Modifier.height(sectionSpacing))

                rules.forEach { (label, satisfied) ->
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

                Spacer(modifier = Modifier.height(sectionSpacing * 1.5f))

                Button(
                    onClick = { },
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
                        text = "Update password",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }

                Spacer(modifier = Modifier.height(sectionSpacing))
            }

                PreviewHomeButton()
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    fieldShape: RoundedCornerShape,
    fieldBorder: Color,
    primaryText: Color,
    mutedText: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = mutedText) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = mutedText
            )
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = mutedText
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
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
