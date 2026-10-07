package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// The Reservations overview look, shared by the POS screens built after it (Kitchen Status, My shift, My Sales) and
// Admin Hub → Hours & pay: white cards with a thin green-grey border, a coloured strip on summary cards, bold titles.
internal object Kit {
    val Border = Color(0xFFE3E8E1)
    val RowBorder = Color(0xFFE8ECE6)
    val Ink = Color(0xFF222426)
    val Muted = Color(0xFF747572)
    val Faint = Color(0xFF9A9C97)
    val Green = Color(0xFF4F7942)
    val GreenSoft = Color(0xFFEEF3EB)
    val Tint = Color(0xFFF2F6F2)
    val Canvas = Color(0xFFF7F9F7)
    val Blue = Color(0xFF24748A)
    val Amber = Color(0xFFC8790B)
    val Danger = Color(0xFFB13A2F)
    val Grey = Color(0xFF8A8D88)
    val Purple = Color(0xFF6E5A9E)
}

// Summary card of an overview: coloured strip, picture (or icon), title, big value, one detail line, optional bar.
@Composable
internal fun OverviewStatCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier,
    image: DrawableResource? = null,
    icon: ImageVector? = null,
    suffix: String? = null,
    progress: Float? = null,
    imageScale: Float = 1f,
    valueColor: Color = Kit.Ink,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp
    ) {
        BoxWithConstraints {
            // Narrow cards (two per row on a phone) skip the picture so the numbers keep their room.
            val showPicture = maxWidth >= 200.dp
            Row(Modifier.height(82.dp)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showPicture && image != null) {
                        Image(painterResource(image), null, Modifier.size(52.dp).scale(imageScale), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(11.dp))
                    } else if (showPicture && icon != null) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(24.dp), tint = accent)
                        }
                        Spacer(Modifier.width(11.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = valueColor, maxLines = 1)
                            suffix?.let {
                                Text(" $it", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        progress?.let { fraction ->
                            Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f))) {
                                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(50)).background(accent))
                            }
                        }
                    }
                }
            }
        }
    }
}

// A white box with a title row (icon, title, extra text, action, count) and its content below.
@Composable
internal fun OverviewPanel(
    icon: ImageVector,
    title: String,
    modifier: Modifier,
    count: Int? = null,
    titleExtra: String? = null,
    titleColor: Color = Kit.Ink,
    iconTint: Color = titleColor,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
                Spacer(Modifier.width(8.dp))
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                titleExtra?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                action?.invoke()
                count?.let {
                    Spacer(Modifier.width(6.dp))
                    CountPill(it)
                }
            }
            content()
        }
    }
}

@Composable
internal fun CountPill(count: Int, color: Color = Kit.Ink, background: Color = Kit.Tint) {
    Text(
        "$count", Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 9.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color
    )
}

// Small coloured label: "Ready 4 min", "On break", "No wage".
@Composable
internal fun StatusChip(text: String, color: Color, icon: ImageVector? = null, strong: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (strong) color else color.copy(alpha = 0.12f))
            .padding(start = if (icon != null) 7.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = if (strong) Color.White else color) }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (strong) Color.White else color, maxLines = 1)
    }
}

// Segmented switch in the overview's tab style (green pill on the chosen one).
@Composable
internal fun OverviewTabs(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 36.dp) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { option ->
            val chosen = option == selected
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(if (chosen) Kit.Green else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (chosen) Color.White else Kit.Ink, maxLines = 1)
            }
        }
    }
}

// Empty or problem state in the middle of a box: picture (or icon in a soft circle), title, hint.
@Composable
internal fun OverviewEmpty(title: String, hint: String, icon: ImageVector, image: DrawableResource? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.widthIn(max = 420.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (image != null) {
            Image(painterResource(image), null, Modifier.size(76.dp), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Kit.GreenSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp), tint = Kit.Green)
            }
        }
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, textAlign = TextAlign.Center)
        Text(hint, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 19.sp, color = Kit.Muted, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

// One-line empty note inside a list.
@Composable
internal fun OverviewCompactEmpty(title: String, hint: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Canvas)
            .border(1.dp, Kit.Border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Kit.GreenSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = Kit.Green)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            Text(hint, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
    }
}

// A label and value on one line, e.g. "Hours worked ........ 7h 30m".
@Composable
internal fun ValueLine(label: String, value: String, strong: Boolean = false, valueColor: Color = Kit.Ink, labelColor: Color = Kit.Muted) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (strong) 14.sp else 13.sp, color = if (strong) Kit.Ink else labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (strong) 16.sp else 13.sp, color = valueColor, maxLines = 1)
    }
}

// Round initials, as on the shift calendar.
@Composable
internal fun InitialsAvatar(name: String, modifier: Modifier = Modifier, color: Color = Kit.Green, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(initials, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, color = color)
    }
}

// A thin line with a small label on it, used between groups in a list.
@Composable
internal fun GroupLabel(text: String, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        detail?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Kit.Muted) }
        Box(Modifier.weight(1f).height(1.dp).background(Kit.Border))
    }
}

// A small summary tile for busy screens (the shift calendar): coloured strip, optional icon, label, value.
@Composable
internal fun CompactStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    valueColor: Color = Kit.Ink,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)
    ) {
        Row(Modifier.height(50.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(it, null, Modifier.size(16.dp), tint = accent)
                    }
                    Spacer(Modifier.width(9.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = valueColor, maxLines = 1)
                        detail?.let {
                            Text("  $it", Modifier.padding(bottom = 1.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
