package com.saporini.mobile_desktop.hours.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.hours.OpeningHoursApi
import com.saporini.mobile_desktop.hours.OpeningHoursDto
import com.saporini.mobile_desktop.hours.clockAt
import com.saporini.mobile_desktop.hours.hm
import com.saporini.mobile_desktop.hours.zoneOf
import com.saporini.mobile_desktop.platform.platformMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The POS while the branch is closed (user, 2026-11-20): a strip under the bar says so ("Closed now · Opens at 19:00");
 * nobody seats guests or starts orders (the server refuses), open orders can still be paid. Someone with "Open outside the
 * hours" opens it for now, and the strip then says until when, with Close again. Nothing shows while it's open or its hours
 * were never set. Checked every minute.
 */
@Composable
internal fun BranchClosedStrip(restaurantId: String?, branchId: String?, permissions: Collection<String>) {
    val api = remember { org.koin.mp.KoinPlatform.getKoinOrNull()?.getOrNull<OpeningHoursApi>() } ?: return
    if (restaurantId == null || branchId == null) return
    var hours by remember(restaurantId, branchId) { mutableStateOf<OpeningHoursDto?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var opening by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(restaurantId, branchId, refresh) {
        while (true) {
            hours = try { api.hours(restaurantId, branchId) } catch (e: CancellationException) { throw e } catch (_: Exception) { hours }
            delay(60_000)
        }
    }
    val shown = hours ?: return
    val now = shown.now
    val zone = zoneOf(shown.timezone)
    val canOpen = "OPEN_OUTSIDE_HOURS" in permissions
    val opened = now.why == "OPENED"
    if (!shown.set || (now.open && !opened)) {
        return
    }
    val act: (suspend () -> Unit) -> Unit = { call ->
        scope.launch {
            busy = true
            error = null
            try {
                call()
                opening = false
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = platformMessage(e, write = true)
            } finally {
                busy = false
            }
        }
    }
    if (opened) {
        Strip(
            color = Kit.Amber, tint = Color(0xFFFFF7E8), icon = Icons.Outlined.LockOpen,
            title = "Open outside the hours until ${clockAt(now.openedUntil ?: now.until, zone)?.hm().orEmpty()}",
            line = "Opened for now. Seating and new orders work until then.",
            action = if (canOpen) "Close again" else null, busy = busy, onAction = { act { api.closeNow(restaurantId, branchId) } }
        )
    } else {
        Strip(
            color = Color(0xFFDC2626), tint = Color(0xFFFEF2F2), icon = Icons.Outlined.EventBusy,
            title = listOfNotNull("Closed now", nextOpenText(now.nextOpen, zone) ?: "No opening in the next 30 days", now.note?.takeIf { it.isNotBlank() })
                .joinToString(" · "),
            line = "Seating guests and new orders wait until it opens. Open orders can still be paid.",
            action = if (canOpen) "Open now" else null, busy = busy, onAction = { opening = true; error = null }
        )
    }
    if (opening) OpenNowDialog(zone, busy, error, onDismiss = { opening = false }) { until -> act { api.openNow(restaurantId, branchId, until.toString()) } }
}

@Composable
private fun Strip(color: Color, tint: Color, icon: ImageVector, title: String, line: String, action: String?, busy: Boolean, onAction: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(tint)) {
        val narrow = maxWidth < 600.dp
        Row(Modifier.fillMaxWidth().padding(horizontal = if (narrow) 12.dp else 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), Alignment.Center) {
                Icon(icon, null, Modifier.size(16.dp), tint = color)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink, maxLines = 2)
                if (!narrow) Text(line, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1)
            }
            if (action != null) {
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy, onClick = onAction).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = color, strokeWidth = 2.dp)
                    Text(action, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = color, maxLines = 1)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.25f)))
    }
}
