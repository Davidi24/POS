package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// The look of Restaurant info and a branch's page (redone 2026-11-22, user: "make them look much better"): white
// sections with one of the app's 3D pictures, a title and one short line, then their content.

/**
 * One part of the page: a white card with a 3D [picture] (or an [icon]) in a soft tile, a title and a short [note], an
 * optional control on the right ([trailing]; under the note when [compact]), then the content.
 */
@Composable
internal fun InfoSection(
    title: String,
    note: String?,
    modifier: Modifier = Modifier,
    picture: DrawableResource? = null,
    icon: ImageVector? = null,
    compact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    // The card being changed: a green frame.
    marked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White,
        border = if (marked) BorderStroke(1.5.dp, Kit.Green.copy(alpha = 0.7f)) else BorderStroke(1.dp, Kit.Border),
        shadowElevation = if (marked) 4.dp else 1.dp) {
        Column(Modifier.padding(if (compact) 14.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionPicture(picture, icon, if (compact) 46.dp else 54.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink)
                    note?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted) }
                    if (compact) trailing?.let { Box(Modifier.padding(top = 6.dp)) { it() } }
                }
                if (!compact) trailing?.invoke()
            }
            content()
        }
    }
}

/** A 3D picture (or an icon) on a soft green tile. */
@Composable
internal fun SectionPicture(picture: DrawableResource?, icon: ImageVector?, size: Dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(Kit.GreenSoft, Color(0xFFF8FBF7))))
            .border(1.dp, Color(0xFFE2EBDE), shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            picture != null -> Image(painterResource(picture), null, Modifier.size(size * 0.84f))
            icon != null -> Icon(icon, null, Modifier.size(size * 0.44f), tint = Kit.Green)
        }
    }
}

/** An amber note at the top of a part that's dangerous to change (Legal & tax, Currency & time zone). */
@Composable
internal fun CarefulBanner(title: String, text: String, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF8EC))
            .border(1.dp, Kit.Amber.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(if (compact) 14.dp else 16.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), Alignment.Center) {
            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(19.dp), tint = Kit.Amber)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF8A5206))
            Text(text, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF6B4A1A))
        }
    }
}

/** A small icon and one line of text (an address, a phone); [missing] in grey. */
@Composable
internal fun IconLine(icon: ImageVector, text: String, missing: Boolean = false, maxLines: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = if (missing) Kit.Faint else Kit.Muted)
        Text(text, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = if (missing) Kit.Faint else Kit.Ink, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis)
    }
}

/** A round face with someone's initials on a soft colour of their own. */
@Composable
internal fun InitialsFace(name: String, size: Dp) {
    val accent = accentOf(name)
    Box(Modifier.size(size).clip(CircleShape).background(accent.soft), Alignment.Center) {
        Text(initialsOf(name), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, color = accent.strong,
            translate = false)
    }
}

/** A small pill with a dot: whether something is open, its kind ("Main"). */
@Composable
internal fun DotPill(text: String, color: Color, modifier: Modifier = Modifier, onWhite: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (onWhite) Color.White.copy(alpha = 0.95f) else color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}

/** A word that opens another part ("Go to the branches ›"). */
@Composable
internal fun LinkText(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Kit.Green)
}
