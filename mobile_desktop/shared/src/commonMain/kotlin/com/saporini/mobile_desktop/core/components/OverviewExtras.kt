package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize

// More of the Reservations overview look for the newer screens: the summary-card row, the card with a coloured
// strip (like a booking card), clickable count rows (like "By status"), status pills, metric bars and page
// skeletons. Hover states make the desktop feel alive; on touch screens they simply don't show.

/** Four summary cards in a row on desktop, two by two on tablets and phones. */
@Composable
internal fun SummaryCardRow(size: ScreenSize, cards: List<@Composable (Modifier) -> Unit>) {
    val gap = if (size.isPhone) 10.dp else 12.dp
    if (size.isDesktop || cards.size <= 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { cards.forEach { it(Modifier.weight(1f)) } }
    } else {
        cards.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEach { it(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The booking-card look: white, thin border, a soft shadow and a coloured strip on the left. Hovering tints the
 * border; [selected] keeps it green. Content goes in a row; [chevron] adds the round arrow at the end.
 */
@Composable
internal fun StripCard(
    strip: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    minHeight: Dp = 64.dp,
    chevron: Boolean = false,
    faded: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        modifier.fillMaxWidth().alpha(if (faded) 0.62f else 1f),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Kit.GreenSoft else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, when {
            selected -> Kit.Green
            hovered && onClick != null -> Kit.Green.copy(alpha = 0.45f)
            else -> Kit.RowBorder
        }),
        shadowElevation = if (hovered && onClick != null) 3.dp else 1.dp
    ) {
        // Intrinsic height lets the strip follow the content's height. Keep lazy lists out of card content.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = minHeight)
                .then(
                    if (onClick != null) Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
                    else Modifier
                )
        ) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(strip))
            Row(
                Modifier.weight(1f).heightIn(min = minHeight).padding(start = 14.dp, end = if (chevron) 10.dp else 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
                // Without a click there is no arrow, but its room stays so the cards line up.
                if (chevron) { if (onClick != null) ChevronCircle(hovered || selected) else Spacer(Modifier.size(30.dp)) }
            }
        }
    }
}

/** The round arrow at the end of a clickable card. */
@Composable
internal fun ChevronCircle(active: Boolean = false) {
    Box(Modifier.size(30.dp).background(if (active) Kit.GreenSoft else Color(0xFFF0F4F0), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = if (active) Kit.Green else Kit.Ink)
    }
}

/** A thin vertical line between parts of a card. */
@Composable
internal fun CardDivider(height: Dp = 38.dp) {
    Box(Modifier.width(1.dp).height(height).background(Kit.Border))
}

/** Dot and text in a tinted pill, like a booking's status. */
@Composable
internal fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1)
    }
}

/** A grey label box with an icon, like the table box on a booking card ("T4", "Kitchen", "€12.50"). */
@Composable
internal fun InfoBox(icon: ImageVector, text: String, modifier: Modifier = Modifier, tone: Color = Kit.Ink, highlighted: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (highlighted) tone.copy(alpha = 0.10f) else Kit.Tint)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = tone)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A clickable count row for side panels ("Active 12", "Switched off 3"): icon square, label, count. The chosen one is
 * tinted with a check; rows with nothing in them turn grey. [count] null hides the number.
 */
@Composable
internal fun FilterRow(
    icon: ImageVector,
    label: String,
    count: Int?,
    color: Color,
    selected: Boolean = false,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    val lit = count == null || count > 0 || selected
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(8.dp))
            .background(when { selected -> color.copy(alpha = 0.1f); !lit -> Color(0xFFF3F4F2); hovered -> Color(0xFFF6F9F4); else -> Color.White })
            .border(if (selected) 1.5.dp else 1.dp, when {
                selected -> color
                !lit -> Color(0xFFD8DBD5)
                hovered -> color.copy(alpha = 0.45f)
                else -> Color(0xFFE8E8E4)
            }, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (lit) color.copy(alpha = if (selected) 0.2f else 0.14f) else Color(0xFFE1E4DE)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (lit) color else Kit.Faint)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
                color = if (lit) Kit.Ink else Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        count?.let { Text("$it", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (lit) Kit.Ink else Kit.Faint) }
        if (selected) {
            Spacer(Modifier.width(7.dp))
            Icon(Icons.Filled.CheckCircle, "Selected", Modifier.size(17.dp), tint = color)
        }
    }
}

/** Label, value and a thin bar of how much of the whole it is. */
@Composable
internal fun MetricBar(label: String, value: String, fraction: Float, color: Color, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** A small ranked number in a circle: green for the first. */
@Composable
internal fun RankBadge(rank: Int) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(if (rank == 1) Kit.Green else Kit.Tint), Alignment.Center) {
        Text("$rank", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (rank == 1) Color.White else Kit.Ink)
    }
}

/** Big time (or number) over a small caption, the left column of a list card. */
@Composable
internal fun TimeColumn(time: String, caption: String, captionColor: Color = Kit.Green, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, Modifier.size(15.dp), tint = Kit.Ink)
                Spacer(Modifier.width(6.dp))
            }
            Text(time, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1)
        }
        Text(caption, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Skeletons ----

/** The loading shape of an overview page: summary cards, a list and (on desktop) a side panel. */
@Composable
internal fun OverviewPageSkeleton(size: ScreenSize, modifier: Modifier = Modifier, cards: Int = 4, sidePanel: Boolean = true) {
    val alpha = rememberSkeletonAlpha("overview-page")
    Column(modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCardRow(size, List(cards) { { m: Modifier -> SkeletonSummaryCard(m) } })
        if (size.isDesktop && sidePanel) {
            Row(Modifier.fillMaxWidth().height(420.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonListPanel(Modifier.weight(1f).fillMaxHeight(), rows = 6, rowHeight = 66.dp)
                SkeletonListPanel(Modifier.width(320.dp).fillMaxHeight(), rows = 6, rowHeight = 42.dp)
            }
        } else {
            SkeletonListPanel(Modifier.fillMaxWidth().height(360.dp), rows = 5, rowHeight = if (size.isPhone) 86.dp else 66.dp)
        }
    }
}

@Composable
internal fun SkeletonSummaryCard(modifier: Modifier) {
    Row(modifier.height(82.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))) {
        SkeletonBox(Modifier.width(5.dp).fillMaxHeight(), shape = RoundedCornerShape(0.dp))
        Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(38.dp), SkeletonLight, RoundedCornerShape(10.dp))
            Spacer(Modifier.width(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.width(70.dp).height(10.dp))
                SkeletonBox(Modifier.width(96.dp).height(18.dp))
                SkeletonBox(Modifier.width(118.dp).height(9.dp), SkeletonLight)
            }
        }
    }
}

@Composable
internal fun SkeletonListPanel(modifier: Modifier, rows: Int, rowHeight: Dp) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(12.dp).clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(18.dp), shape = RoundedCornerShape(5.dp))
            Spacer(Modifier.width(8.dp))
            SkeletonBox(Modifier.width(96.dp).height(14.dp))
        }
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.width(5.dp).fillMaxHeight(), SkeletonLight, RoundedCornerShape(0.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.35f).height(12.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.55f).height(10.dp), SkeletonLight)
                }
                SkeletonBox(Modifier.padding(end = 14.dp).size(28.dp), SkeletonLight, RoundedCornerShape(14.dp))
            }
        }
    }
}

/** A side column of panels (filters, summaries) next to the main list on desktop. */
@Composable
internal fun SideColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
