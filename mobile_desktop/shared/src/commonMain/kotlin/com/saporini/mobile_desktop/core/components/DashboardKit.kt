package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize

// Layout pieces shared by the Statistics and Fraud Detection dashboards.

/**
 * Panels side by side with the given weights and the same height on wide screens; one under the other on phones
 * (and on tablets when there are more than two). Panel content must not use lazy lists or BoxWithConstraints.
 */
@Composable
internal fun PanelRow(size: ScreenSize, vararg panels: Pair<Float, @Composable (Modifier) -> Unit>) {
    val side = size.isDesktop || (size == ScreenSize.TABLET && panels.size <= 2)
    if (side) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            panels.forEach { (weight, panel) -> panel(Modifier.weight(weight).fillMaxHeight()) }
        }
    } else {
        panels.forEach { (_, panel) -> panel(Modifier.fillMaxWidth()) }
    }
}

/** A big figure in a panel's head: value, change badge and a caption ("€12,480  +8%  sales in 7 days"). */
@Composable
internal fun HeroFigure(value: String, caption: String, change: String? = null, inverted: Boolean = false) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Kit.Ink, maxLines = 1)
        TrendBadge(change, inverted)
        Text(caption, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row in a "things to watch" list: icon square, label, the figure and its change. */
@Composable
internal fun WatchRow(icon: ImageVector, label: String, value: String, color: Color, change: String? = null, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F9F7)).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)), Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = color)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        if (change != null) {
            Spacer(Modifier.width(8.dp))
            TrendBadge(change, inverted = true)
        }
    }
}

/** A labelled small figure for a strip of facts under a chart ("Best day  Sat 4 Oct  €2,140"). */
@Composable
internal fun FactChip(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null, tone: Color = Kit.Green) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
