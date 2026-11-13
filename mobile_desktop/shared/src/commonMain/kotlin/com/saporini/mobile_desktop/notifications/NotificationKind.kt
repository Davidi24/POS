package com.saporini.mobile_desktop.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.notification_order
import mobile_desktop.shared.generated.resources.notification_reservation
import mobile_desktop.shared.generated.resources.notification_table
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// What a notification is about. Each kind has one color, shared with the table map (reserved purple,
// occupied orange, free green, bill pending blue), so staff can tell who it's from at a glance.
internal enum class NotificationKind(
    val accent: Color,
    val image: DrawableResource?,
    val fallbackIcon: ImageVector
) {
    RESERVATION(Color(0xFF8B5CF6), Res.drawable.notification_reservation, Icons.Outlined.NotificationsNone),
    ORDER(Color(0xFFD15F00), Res.drawable.notification_order, Icons.Outlined.NotificationsNone),
    TABLE(Color(0xFF147A25), Res.drawable.notification_table, Icons.Outlined.NotificationsNone),
    PAYMENT(Color(0xFF3B82F6), null, Icons.Outlined.Payments),
    OTHER(Color(0xFF6F736E), null, Icons.Outlined.NotificationsNone)
}

internal val StaffNotification.kind: NotificationKind
    get() = when ((referenceType ?: eventCode.substringBefore('_')).uppercase()) {
        "RESERVATION" -> NotificationKind.RESERVATION
        "ORDER", "KDS" -> NotificationKind.ORDER
        "TABLE" -> NotificationKind.TABLE
        "PAYMENT" -> NotificationKind.PAYMENT
        else -> NotificationKind.OTHER
    }

// The kind's picture, or a tinted icon in a soft circle for kinds that don't have one yet.
@Composable
internal fun NotificationKindIcon(kind: NotificationKind, size: Dp = 40.dp) {
    val image = kind.image
    if (image != null) {
        Image(painterResource(image), contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(kind.accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(kind.fallbackIcon, null, Modifier.size(size * 0.52f), tint = kind.accent)
        }
    }
}
