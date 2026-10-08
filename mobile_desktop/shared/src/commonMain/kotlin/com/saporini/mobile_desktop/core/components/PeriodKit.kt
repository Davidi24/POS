package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Picking a period of days (Statistics, Fraud Detection) and showing a figure against the period before.

/** "8 Oct", "1 – 7 Oct", "28 Sep – 4 Oct", "20 Dec 2025 – 3 Jan 2026". */
internal fun periodLabel(from: LocalDate, to: LocalDate): String = when {
    from == to -> shortDate(from)
    from.year != to.year -> "${shortDate(from, withYear = true)} – ${shortDate(to, withYear = true)}"
    from.month == to.month -> "${from.day} – ${shortDate(to)}"
    else -> "${shortDate(from)} – ${shortDate(to)}"
}

/** "7 days", "1 day". */
internal fun dayCountText(from: LocalDate, to: LocalDate): String = (from.daysUntil(to) + 1).let { "$it ${if (it == 1) "day" else "days"}" }

/**
 * The header's date-range button: shows the range, and opens a calendar where the first tap picks the first day and
 * the second tap the last one. Days after [maxDate] can't be picked; at most [maxDays] days.
 */
@Composable
internal fun DateRangeButton(
    from: LocalDate,
    to: LocalDate,
    maxDate: LocalDate,
    onChange: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxDays: Int = 366
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { open = true }, modifier = Modifier.height(PageControlHeight), shape = RoundedCornerShape(8.dp), color = Color.White,
            border = BorderStroke(1.dp, Kit.Border)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = Kit.Ink)
                Text(periodLabel(from, to), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, 6.dp), shape = RoundedCornerShape(12.dp), containerColor = Color.White,
            shadowElevation = 8.dp) {
            RangeCalendar(from, to, maxDate, maxDays) { a, b -> onChange(a, b); open = false }
        }
    }
}

/** A month calendar for choosing a range: tap the first day, then the last; the days between are tinted. */
@Composable
internal fun RangeCalendar(from: LocalDate, to: LocalDate, maxDate: LocalDate, maxDays: Int, onPick: (LocalDate, LocalDate) -> Unit) {
    var month by remember { mutableStateOf(LocalDate(to.year, to.month, 1)) }
    // Null until the first day is tapped; then the second tap finishes the range.
    var start by remember { mutableStateOf<LocalDate?>(null) }
    val shownFrom = start ?: from
    val shownTo = start ?: to
    val lastDay = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val blanks = month.dayOfWeek.isoDayNumber - 1
    Column(Modifier.width(292.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarArrow(Icons.Outlined.ChevronLeft, "Previous month") { month = month.minus(DatePeriod(months = 1)) }
            Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", Modifier.weight(1f), fontFamily = Inter(),
                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, textAlign = TextAlign.Center)
            CalendarArrow(Icons.Outlined.ChevronRight, "Next month", enabled = month.plus(DatePeriod(months = 1)) <= maxDate) {
                month = month.plus(DatePeriod(months = 1))
            }
        }
        Row {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                Text(it, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Faint, textAlign = TextAlign.Center)
            }
        }
        (List(blanks) { null } + (1..lastDay).map { LocalDate(month.year, month.month, it) }).chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).height(34.dp)) else {
                        val first = start
                        val allowed = day <= maxDate && (first == null || day < first || first.daysUntil(day) + 1 <= maxDays)
                        val inRange = day in shownFrom..shownTo
                        val edge = day == shownFrom || day == shownTo
                        Box(
                            Modifier.weight(1f).height(34.dp).background(if (inRange && !edge) Kit.GreenSoft else Color.Transparent)
                                .clickable(enabled = allowed) {
                                    if (first == null || day < first) start = day
                                    else { start = null; onPick(first, day) }
                                },
                            Alignment.Center
                        ) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(if (edge) Kit.Green else Color.Transparent), Alignment.Center) {
                                Text("${day.day}", fontFamily = Inter(), fontWeight = if (edge) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp,
                                    color = when { edge -> Color.White; !allowed -> Kit.Faint.copy(alpha = 0.5f); else -> Kit.Ink })
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(if (start == null) "Tap the first day" else "Now tap the last day (or the same day again)", fontFamily = Inter(), fontSize = 11.sp,
            color = Kit.Muted)
    }
}

@Composable
private fun CalendarArrow(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(icon, description, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}

/** Back and forward arrows that move the whole period, as one control. */
@Composable
internal fun PeriodStepper(onBack: () -> Unit, onForward: () -> Unit, forwardEnabled: Boolean) {
    Row(Modifier.height(PageControlHeight), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onBack, modifier = Modifier.size(PageControlHeight), shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ChevronLeft, "Earlier", Modifier.size(20.dp), tint = Kit.Ink) }
        }
        Surface(onClick = onForward, enabled = forwardEnabled, modifier = Modifier.size(PageControlHeight),
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronRight, "Later", Modifier.size(20.dp), tint = if (forwardEnabled) Kit.Ink else Kit.Faint)
            }
        }
    }
}

/**
 * A summary card with a figure and how it moved against the period before: the overview card look (strip, 3D
 * picture, title, big value) plus a trend badge. [inverted] when lower is better (refunds, removals).
 */
@Composable
internal fun KpiCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier,
    change: String? = null,
    inverted: Boolean = false,
    image: DrawableResource? = null,
    icon: ImageVector? = null,
    valueColor: Color = Kit.Ink,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp
    ) {
        BoxWithConstraints {
            val showPicture = maxWidth >= 220.dp
            Row(Modifier.height(86.dp)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showPicture && image != null) {
                        Image(painterResource(image), null, Modifier.size(52.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(11.dp))
                    } else if (showPicture && icon != null) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)), Alignment.Center) {
                            Icon(icon, null, Modifier.size(24.dp), tint = accent)
                        }
                        Spacer(Modifier.width(11.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(value, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = valueColor,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // On narrow cards the badge moves under the figure, so the figure keeps its room.
                            if (showPicture) TrendBadge(change, inverted)
                        }
                        if (showPicture || change == null) {
                            Text(detail, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else TrendBadge(change, inverted)
                    }
                }
            }
        }
    }
}
