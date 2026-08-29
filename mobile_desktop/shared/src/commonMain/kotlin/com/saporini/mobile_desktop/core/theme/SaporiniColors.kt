package com.saporini.mobile_desktop.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Saporini brand palette — inspired by the moodboard
object SaporiniColors {
    val CreamBg = Color(0xFFFAF1E8)
    val WarmCream = Color(0xFFF1E7DD)
    val SoftBeige = Color(0xFFE8DDD2)
    val BorderBeige = Color(0xFFD8CFC3)
    val DeepGreen = Color(0xFF3B422E)
    val OliveGreen = Color(0xFF585C46)
    val MutedOlive = Color(0xFF7A7A63)
    val Gold = Color(0xFFC8A577)
    val DarkGold = Color(0xFF8B6A38)
    val TextDark = Color(0xFF2F382A)
    val TextMuted = Color(0xFF6F6E67)
    val LineSoft = Color(0xFFD6CCC0)
    val White = Color(0xFFFFFFFF)

    // Backwards-compatible aliases for existing UI code.
    val Cream = WarmCream
    val CreamSoft = SoftBeige
    val GoldDark = DarkGold
    val GoldLight = Gold
    val ForestGreen = DeepGreen
    val Charcoal = TextDark
    val Muted = TextMuted
    val Terracotta = DarkGold
    val Background = CreamBg
}

private val SaporiniColorScheme = lightColorScheme(
    primary = SaporiniColors.Gold,
    onPrimary = SaporiniColors.White,
    primaryContainer = SaporiniColors.WarmCream,
    onPrimaryContainer = SaporiniColors.TextDark,
    secondary = SaporiniColors.DeepGreen,
    onSecondary = SaporiniColors.CreamBg,
    background = SaporiniColors.Background,
    onBackground = SaporiniColors.TextDark,
    surface = SaporiniColors.WarmCream,
    onSurface = SaporiniColors.TextDark,
    surfaceVariant = SaporiniColors.SoftBeige,
    onSurfaceVariant = SaporiniColors.TextMuted,
    error = SaporiniColors.DarkGold,
    onError = SaporiniColors.White,
    outline = SaporiniColors.BorderBeige,
    outlineVariant = SaporiniColors.LineSoft,
)

private val SaporiniTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 56.sp,
        letterSpacing = 1.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 4.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 1.5.sp
    )
)

@Composable
fun SaporiniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaporiniColorScheme,
        typography = SaporiniTypography,
        content = content
    )
}
