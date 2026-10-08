package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.delay

// Page furniture shared by the Admin Hub, Statistics, Fraud Detection and KDS screens: the page header with its
// controls, toolbar buttons, message bars, whole-page states and the "load more" footer of paged lists. Everything
// uses the Reservations overview look (OverviewKit's colours).

/** Toolbar control height, the same as the Reservations header controls. */
internal val PageControlHeight = 44.dp

/** Keeps a screen's model loading only while the screen is shown, and releases it when the screen leaves. */
@Composable
internal fun BindToLifecycle(setActive: (Boolean) -> Unit, dispose: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) setActive(true)
            if (event == Lifecycle.Event.ON_STOP) setActive(false)
        }
        owner.lifecycle.addObserver(observer)
        setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            owner.lifecycle.removeObserver(observer)
            setActive(false)
            dispose()
        }
    }
}

/**
 * Title and subtitle on the left, controls on the right. On narrow screens the controls go under the title and
 * scroll sideways, so nothing is squeezed.
 */
@Composable
internal fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    controls: @Composable RowScope.() -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 900.dp
        val titleBlock: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                leading?.invoke()
                Column {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { titleBlock() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = controls)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                titleBlock()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = controls
                )
            }
        }
    }
}

/** A square white toolbar button with an icon (previous, next, today, settings...). */
@Composable
internal fun ToolbarIconButton(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color = Kit.Ink, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.size(PageControlHeight), shape = RoundedCornerShape(8.dp),
        color = Color.White, border = BorderStroke(1.dp, Kit.Border)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(19.dp), tint = if (enabled) tint else Kit.Faint) }
    }
}

/** Refresh button that turns into a spinner while loading. */
@Composable
internal fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = !loading, modifier = Modifier.size(PageControlHeight), shape = RoundedCornerShape(8.dp),
        color = Color.White, border = BorderStroke(1.dp, Kit.Border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, "Refresh", Modifier.size(18.dp), tint = Kit.Ink)
        }
    }
}

/** The page's main action in the toolbar ("Add person", "New role"): solid green. */
@Composable
internal fun ToolbarPrimaryButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.height(PageControlHeight), shape = RoundedCornerShape(8.dp),
        color = if (enabled) Kit.Green else Kit.Green.copy(alpha = 0.45f), contentColor = Color.White
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, maxLines = 1)
        }
    }
}

/** A secondary toolbar action: white with a border. */
@Composable
internal fun ToolbarButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.height(PageControlHeight), shape = RoundedCornerShape(8.dp),
        color = Color.White, border = BorderStroke(1.dp, Kit.Border)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
                color = if (enabled) Kit.Ink else Kit.Faint, maxLines = 1)
        }
    }
}

enum class MessageKind { ERROR, WARNING, SUCCESS, INFO }

/**
 * A one-line message under the header. Success messages close themselves after a few seconds; errors stay until
 * dismissed.
 */
@Composable
internal fun MessageBar(text: String, kind: MessageKind, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    if (kind == MessageKind.SUCCESS && onDismiss != null) {
        LaunchedEffect(text) { delay(3_500); onDismiss() }
    }
    val color = when (kind) {
        MessageKind.ERROR -> Kit.Danger
        MessageKind.WARNING -> Kit.Amber
        MessageKind.SUCCESS -> Kit.Green
        MessageKind.INFO -> Kit.Blue
    }
    val icon = when (kind) {
        MessageKind.ERROR -> Icons.Outlined.ErrorOutline
        MessageKind.WARNING -> Icons.Outlined.WarningAmber
        MessageKind.SUCCESS -> Icons.Outlined.CheckCircle
        MessageKind.INFO -> Icons.Outlined.Info
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(8.dp)).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(text, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
            color = if (kind == MessageKind.WARNING) Color(0xFF8A5A0B) else color)
        onDismiss?.let {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = it), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(16.dp), tint = Kit.Muted)
            }
        }
    }
}

/** Shows an error and a notice of a screen model, one under the other. */
@Composable
internal fun ScreenMessages(error: String?, notice: String?, onClear: () -> Unit, warning: String? = null) {
    warning?.let { MessageBar(it, MessageKind.WARNING) }
    error?.let { MessageBar(it, MessageKind.ERROR, onDismiss = onClear) }
    notice?.let { MessageBar(it, MessageKind.SUCCESS, onDismiss = onClear) }
}

/** A full-size placeholder in the middle of the page: no access, loading, could not load, or nothing yet. */
@Composable
internal fun PageState(
    kind: PageStateKind,
    title: String = kind.defaultTitle,
    hint: String = kind.defaultHint,
    modifier: Modifier = Modifier,
    minHeight: Dp = 320.dp,
    icon: ImageVector = kind.icon,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight), contentAlignment = Alignment.Center) {
        if (kind == PageStateKind.LOADING) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Kit.Green, strokeWidth = 3.dp)
        } else {
            OverviewEmpty(title, hint, icon, action = action)
        }
    }
}

enum class PageStateKind(val defaultTitle: String, val defaultHint: String, val icon: ImageVector) {
    NO_ACCESS("No access", "Your role doesn't include this page. Ask an owner or manager.", Icons.Outlined.Lock),
    LOADING("", "", Icons.Outlined.Refresh),
    FAILED("Couldn't load this", "Check the connection, then try again.", Icons.Outlined.CloudOff),
    EMPTY("Nothing here yet", "", Icons.Outlined.Info)
}

/** "Try again" as a small green text button, for page states and list footers. */
@Composable
internal fun RetryText(text: String = "Try again", onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green)
    }
}

/**
 * The bottom of a paged list: a spinner while the next page loads, "Try again" after a failure, or how much is
 * shown when everything is loaded.
 */
@Composable
internal fun PagedFooter(shown: Int, total: Long?, hasNext: Boolean, loadingMore: Boolean, failed: Boolean, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        when {
            loadingMore -> CircularProgressIndicator(Modifier.size(20.dp), color = Kit.Green, strokeWidth = 2.dp)
            hasNext && failed -> RetryText("Load more", onLoadMore)
            hasNext -> RetryText("Show more", onLoadMore)
            shown > 0 -> Text(
                if (total != null && total > shown) "Showing $shown of $total" else "All $shown shown",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint
            )
        }
    }
}

/** Green "Live" or amber "Reconnecting" dot with a label. */
@Composable
internal fun LiveBadge(live: Boolean, liveText: String = "Live", offlineText: String = "Reconnecting") {
    val color = if (live) Kit.Green else Kit.Amber
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(if (live) liveText else offlineText, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color)
    }
}

/** A row of pill filters ("All", "Active", "Off"); the chosen one is green. */
@Composable
internal fun <T> FilterPills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val chosen = value == selected
            Text(
                label,
                Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.Green else Color.White)
                    .border(1.dp, if (chosen) Kit.Green else Kit.Border, RoundedCornerShape(50))
                    .clickable { onSelect(value) }.padding(horizontal = 14.dp, vertical = 7.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (chosen) Color.White else Kit.Ink, maxLines = 1
            )
        }
    }
}

/** A label above a value, for detail panels ("Email" / "anna@…"). */
@Composable
internal fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Kit.Ink) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Thin divider in the kit's border colour. */
@Composable
internal fun KitDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Kit.Border))
}

/** Horizontal space that grows; handy inside toolbars. */
@Composable
internal fun RowScope.Grow() = Spacer(Modifier.weight(1f))

/** A small grey uppercase caption above a group of controls. */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Faint)
}
