package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.workspace.ui.BottomLeftDecoration
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

/**
 * The sign-in screens' frame: the screen's own [content] on the left (over a small home-screen corner at its foot) and, on a wide window, the kitchen photo on the right
 * (user, 2026-11-16: "the right part looks great"; form 45%, photo 55%): rounded, with a dark fade at its foot and a line about the app on it.
 */
@Composable
internal fun AuthImageScaffold(
    content: @Composable BoxScope.() -> Unit
) {
    AuthFonts { AuthFrame(content) }
}

/**
 * The sign-in screen's font for every text on the sign-in screens (user, 2026-11-17: "the fonts are different from login,
 * make all the other pages the same as login"): Inter for the theme's styles and plain texts, so a text that names no
 * font still matches the sign-in form.
 */
@Composable
internal fun AuthFonts(content: @Composable () -> Unit) {
    val inter = Inter()
    val t = MaterialTheme.typography
    val typography = remember(t, inter) {
        t.copy(
            displayLarge = t.displayLarge.copy(fontFamily = inter), displayMedium = t.displayMedium.copy(fontFamily = inter),
            displaySmall = t.displaySmall.copy(fontFamily = inter), headlineLarge = t.headlineLarge.copy(fontFamily = inter),
            headlineMedium = t.headlineMedium.copy(fontFamily = inter), headlineSmall = t.headlineSmall.copy(fontFamily = inter),
            titleLarge = t.titleLarge.copy(fontFamily = inter), titleMedium = t.titleMedium.copy(fontFamily = inter),
            titleSmall = t.titleSmall.copy(fontFamily = inter), bodyLarge = t.bodyLarge.copy(fontFamily = inter),
            bodyMedium = t.bodyMedium.copy(fontFamily = inter), bodySmall = t.bodySmall.copy(fontFamily = inter),
            labelLarge = t.labelLarge.copy(fontFamily = inter), labelMedium = t.labelMedium.copy(fontFamily = inter),
            labelSmall = t.labelSmall.copy(fontFamily = inter)
        )
    }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = MaterialTheme.shapes, typography = typography) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = inter), content = content)
    }
}

@Composable
private fun AuthFrame(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val isWide = maxWidth >= 960.dp
        // The small home-screen corner sits bottom left on every sign-in screen (user, 2026-11-17: "in all pages"),
        // behind the screen's own content; smaller where the form side is small.
        val formWidth = if (isWide) maxWidth * 0.45f else maxWidth
        val cornerScale = if (formWidth < 380.dp || maxHeight < 700.dp) 0.45f else 0.6f

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .background(Color.White)
                ) {
                    BottomLeftDecoration(Modifier.matchParentSize(), cornerScale)
                    content()
                }
                AuthPhotoPanel(Modifier.weight(0.55f).fillMaxHeight().padding(14.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                BottomLeftDecoration(Modifier.matchParentSize(), cornerScale)
                content()
            }
        }
    }
}

/** The photo with a dark fade at its foot and a short line about the app on it. */
@Composable
private fun AuthPhotoPanel(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp))) {
        Image(painterResource(Res.drawable.auth_login_img), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.82f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, end = 40.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text("Saporini POS", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Color.White)
            }
            Text("Every table, order and booking in one place.", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp,
                color = Color.White)
            Text("Tables, kitchen, payments, stock and your team, on the till, the tablet and the phone.", fontFamily = Inter(), fontSize = 14.sp,
                lineHeight = 21.sp, color = Color.White.copy(alpha = 0.82f))
        }
    }
}
