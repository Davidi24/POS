package com.saporini.mobile_desktop.admin.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SettingsCard
import com.saporini.mobile_desktop.core.components.SettingsRow
import com.saporini.mobile_desktop.core.components.SettingsSwitch
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
private data class PrintingValues(
    val printKitchenOnSend: Boolean = true,
    val printBillOnRequest: Boolean = true,
    val printReceiptAfterPayment: String = "ASK"
)

/**
 * When things print by themselves (user, 2026-11-27): kitchen tickets when an order is sent, the bill when it's asked for, the
 * receipt after the last payment (always, ask, never). Saves on its own, like the network card above it.
 */
@Composable
internal fun PrintingSettings(restaurantId: String, canEdit: Boolean) {
    val client = koinInject<HttpClient>()
    val scope = rememberCoroutineScope()
    val base = "${ApiConfig.BASE_URL.trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/settings"
    var values by remember(restaurantId) { mutableStateOf<PrintingValues?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(restaurantId) {
        values = runCatching { client.get(base).body<PrintingValues>() }.getOrNull() ?: PrintingValues()
    }
    fun save(next: PrintingValues) {
        val before = values
        values = next
        scope.launch {
            runCatching { client.patch("$base/printing") { contentType(ContentType.Application.Json); setBody(next) }.body<PrintingValues>() }
                .onSuccess { values = it; notice = "Saved." }
                .onFailure { values = before; error = "Couldn't save. Try again." }
        }
    }
    val current = values
    SettingsCard("When things print", note = "What prints by itself. Each printer's page in Devices says what it prints.", icon = Icons.Outlined.Print) {
        if (current == null) {
            Text("Loading…", Modifier.padding(vertical = 10.dp), fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            return@SettingsCard
        }
        SettingsRow("Print kitchen tickets when an order is sent", "On each kitchen station's printer.", divider = false) {
            SettingsSwitch(current.printKitchenOnSend, { save(current.copy(printKitchenOnSend = it)) }, enabled = canEdit)
        }
        SettingsRow("Print the bill when it's asked for", "On the till's receipt printer.") {
            SettingsSwitch(current.printBillOnRequest, { save(current.copy(printBillOnRequest = it)) }, enabled = canEdit)
        }
        SettingsRow("Receipt after the last payment", "Always prints it, asks first, or never prints it.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALWAYS" to "Always", "ASK" to "Ask", "NEVER" to "Never").forEach { (code, label) ->
                    val chosen = current.printReceiptAfterPayment == code
                    Text(label, Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.GreenSoft else androidx.compose.ui.graphics.Color.White)
                        .border(1.dp, if (chosen) Kit.Green else Kit.Border, RoundedCornerShape(50))
                        .clickable(enabled = canEdit && !chosen) { save(current.copy(printReceiptAfterPayment = code)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp,
                        color = if (chosen) Kit.Green else Kit.Ink)
                }
            }
        }
    }
    ScreenMessages(error, notice, { error = null; notice = null })
}
