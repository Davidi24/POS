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
    val Cream = Color(0xFFF5EFE3)
    val CreamSoft = Color(0xFFFAF6EE)
    val Gold = Color(0xFFB8860B)
    val GoldDark = Color(0xFF8B6508)
    val GoldLight = Color(0xFFD4A93C)
    val ForestGreen = Color(0xFF1F3326)
    val Charcoal = Color(0xFF2A2A2A)
    val Muted = Color(0xFF6B6B6B)
    val Terracotta = Color(0xFFB85C3C)
    val White = Color(0xFFFFFFFF)
}

private val SaporiniColorScheme = lightColorScheme(
    primary = SaporiniColors.Gold,
    onPrimary = SaporiniColors.White,
    primaryContainer = SaporiniColors.GoldLight,
    onPrimaryContainer = SaporiniColors.ForestGreen,
    secondary = SaporiniColors.ForestGreen,
    onSecondary = SaporiniColors.Cream,
    background = SaporiniColors.Cream,
    onBackground = SaporiniColors.Charcoal,
    surface = SaporiniColors.White,
    onSurface = SaporiniColors.Charcoal,
    surfaceVariant = SaporiniColors.CreamSoft,
    onSurfaceVariant = SaporiniColors.Muted,
    error = SaporiniColors.Terracotta,
    onError = SaporiniColors.White,
    outline = SaporiniColors.GoldLight,
    outlineVariant = SaporiniColors.Cream
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