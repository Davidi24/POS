package com.saporini.mobile_desktop.admin.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SettingsCard
import com.saporini.mobile_desktop.core.components.SettingsRow
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.theme.Inter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
private data class TillLock(
    val tillLockSeconds: Int = 30,
    val tillLockImportantSeconds: Int = 180,
    val pinPauseAfter: Int = 10,
    val pinPauseMinutes: Int = 15
)

/**
 * The till lock and the PIN pause (decided 2026-11-28): how long a till waits untouched before it locks, how long during
 * important work (an order, a payment, seating), and how many wrong PINs pause it for how long. Saves on its own a moment
 * after the last change; tills pick it up within a minute.
 */
@Composable
internal fun TillLockSettings(restaurantId: String, canEdit: Boolean) {
    val client = koinInject<HttpClient>()
    val base = "${ApiConfig.BASE_URL.trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/settings"
    var values by remember(restaurantId) { mutableStateOf<TillLock?>(null) }
    var saved by remember(restaurantId) { mutableStateOf<TillLock?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(restaurantId) {
        val loaded = runCatching { client.get(base).body<TillLock>() }.getOrNull() ?: TillLock()
        values = loaded
        saved = loaded
    }
    // A moment after the last + or −, the four are saved together.
    LaunchedEffect(round) {
        val next = values ?: return@LaunchedEffect
        if (round == 0 || next == saved) return@LaunchedEffect
        delay(700)
        runCatching { client.patch("$base/till-lock") { contentType(ContentType.Application.Json); setBody(next) }.body<TillLock>() }
            .onSuccess { saved = it; notice = "Saved." }
            .onFailure { values = saved; error = "Couldn't save. Try again." }
    }
    fun change(next: TillLock) { values = next; round++ }
    val current = values
    SettingsCard("Till lock", note = "A till goes back to the PIN screen when nobody touches it.", icon = Icons.Outlined.Lock) {
        if (current == null) {
            Text("Loading…", Modifier.padding(vertical = 10.dp), fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            return@SettingsCard
        }
        SettingsRow("Locks after", "When nobody touches it.", divider = false) {
            Stepper(timeText(current.tillLockSeconds), canEdit,
                onLess = { change(current.copy(tillLockSeconds = (current.tillLockSeconds - step(current.tillLockSeconds)).coerceAtLeast(10))) },
                onMore = { change(current.copy(tillLockSeconds = (current.tillLockSeconds + step(current.tillLockSeconds)).coerceAtMost(minOf(1800, current.tillLockImportantSeconds)))) })
        }
        SettingsRow("During important work", "An order, a payment, seating guests, a booking.") {
            Stepper(timeText(current.tillLockImportantSeconds), canEdit,
                onLess = { change(current.copy(tillLockImportantSeconds = (current.tillLockImportantSeconds - 30).coerceAtLeast(maxOf(30, current.tillLockSeconds)))) },
                onMore = { change(current.copy(tillLockImportantSeconds = (current.tillLockImportantSeconds + 30).coerceAtMost(3600))) })
        }
        SettingsRow("Wrong PINs before a pause", "On one till, within the pause time.") {
            Stepper("${current.pinPauseAfter}", canEdit,
                onLess = { change(current.copy(pinPauseAfter = (current.pinPauseAfter - 1).coerceAtLeast(3))) },
                onMore = { change(current.copy(pinPauseAfter = (current.pinPauseAfter + 1).coerceAtMost(50))) })
        }
        SettingsRow("Pause", "PIN sign-in stops on that till; passwords still work.") {
            Stepper("${current.pinPauseMinutes} min", canEdit,
                onLess = { change(current.copy(pinPauseMinutes = (current.pinPauseMinutes - if (current.pinPauseMinutes > 10) 5 else 1).coerceAtLeast(1))) },
                onMore = { change(current.copy(pinPauseMinutes = (current.pinPauseMinutes + if (current.pinPauseMinutes >= 10) 5 else 1).coerceAtMost(120))) })
        }
    }
    ScreenMessages(error, notice, { error = null; notice = null })
}

/** Ten seconds at a time under two minutes, thirty after. */
private fun step(seconds: Int): Int = if (seconds < 120) 10 else 30

/** "30 s", "1 min 30 s", "3 min". */
private fun timeText(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

/** − value + */
@Composable
private fun Stepper(value: String, enabled: Boolean, onLess: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Outlined.Remove, enabled, onLess)
        Text(value, Modifier.widthIn(min = 84.dp).padding(horizontal = 6.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
            letterSpacing = 0.sp, color = Kit.Ink, textAlign = TextAlign.Center)
        StepButton(Icons.Outlined.Add, enabled, onMore)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).background(Kit.Tint).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}
