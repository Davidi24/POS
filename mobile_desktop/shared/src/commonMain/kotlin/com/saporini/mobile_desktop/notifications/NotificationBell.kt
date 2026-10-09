package com.saporini.mobile_desktop.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.delay
import org.koin.compose.getKoin
import kotlin.time.Clock
import kotlin.time.Instant

private val Ink = Color(0xFF1F2322)
private val Muted = Color(0xFF6F736E)
private val Green = Color(0xFF4F7942)
private val Divider = Color(0xFFEDEEEB)

// The bell in the top bars: shows how many notifications are unread and opens the list.
@Composable
fun NotificationBell(
    onOpen: ((StaffNotification) -> Unit)? = null,
    buttonSize: Dp = 42.dp,
    iconSize: Dp = 30.dp,
    tint: Color = Color(0xFF3D4342)
) {
    val center = getKoin().getOrNull<NotificationCenter>()
    val state by (center?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(NotificationsState()) }).collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = { open = true }, modifier = Modifier.size(buttonSize)) {
                Icon(Icons.Outlined.NotificationsNone, "Notifications", Modifier.size(iconSize), tint = tint)
            }
            if (state.unreadCount > 0) {
                Badge(modifier = Modifier.offset(x = (-1).dp, y = 2.dp), containerColor = Color(0xFFFF1414), contentColor = Color.White) {
                    Text(
                        if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                    )
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White,
            shadowElevation = 10.dp
        ) {
            NotificationPanel(
                state = state,
                onMarkAllRead = { center?.markAllRead() },
                onClick = { notification ->
                    center?.markRead(notification)
                    open = false
                    if (onOpen != null) onOpen(notification) else center?.requestOpen(notification)
                }
            )
        }
    }
}

@Composable
private fun NotificationPanel(state: NotificationsState, onMarkAllRead: () -> Unit, onClick: (StaffNotification) -> Unit) {
    Column(Modifier.width(380.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
            if (state.unreadCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.unreadCount} new",
                    Modifier.clip(RoundedCornerShape(50)).background(Green.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Green
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.unreadCount > 0) {
                Text(
                    "Mark all as read",
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onMarkAllRead).padding(horizontal = 8.dp, vertical = 6.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Green
                )
            }
        }
        HorizontalDivider(color = Divider)
        if (state.items.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Green.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(26.dp), tint = Green)
                }
                Text(if (state.available) "You're all caught up" else "No notifications", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                Text("New reservations will show up here.", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
            }
        } else {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                state.items.forEachIndexed { index, notification ->
                    NotificationRow(notification) { onClick(notification) }
                    if (index < state.items.lastIndex) HorizontalDivider(color = Divider)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: StaffNotification, onClick: () -> Unit) {
    val kind = notification.kind
    Row(
        Modifier.fillMaxWidth().background(Color.White)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NotificationKindIcon(kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title, Modifier.weight(1f),
                    fontFamily = Inter(), fontWeight = if (notification.read) FontWeight.SemiBold else FontWeight.Bold,
                    fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(timeAgo(notification.createdAt), fontFamily = Inter(), fontSize = 11.sp, color = Muted)
            }
            Text(notification.message, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!notification.read) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(kind.accent))
        }
    }
}

// Pop-ups for notifications that arrive while the app is open; each hides itself after a few seconds.
@Composable
internal fun NotificationArrivalQueue(
    notifications: List<StaffNotification>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        notifications.forEach { notification ->
            key(notification.id) { NotificationArrivalCard(notification, onDismiss) }
        }
    }
}

@Composable
private fun NotificationArrivalCard(notification: StaffNotification, onDismiss: (String) -> Unit) {
    val kind = notification.kind
    LaunchedEffect(notification.id) {
        delay(5000)
        onDismiss(notification.id)
    }
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, kind.accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NotificationKindIcon(kind, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(notification.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (notification.message.isNotBlank()) {
                Text(notification.message, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = { onDismiss(notification.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Close, "Dismiss notification", Modifier.size(18.dp), tint = Muted)
        }
    }
}

// "Just now", "5 min ago", "2 h ago", "Yesterday", then the date.
internal fun timeAgo(iso: String?): String {
    val at = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val minutes = (Clock.System.now() - at).inWholeMinutes
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "Yesterday"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
