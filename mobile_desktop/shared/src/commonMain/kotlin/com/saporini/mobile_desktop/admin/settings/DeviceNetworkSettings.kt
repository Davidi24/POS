package com.saporini.mobile_desktop.admin.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DeviceNetworkApi
import com.saporini.mobile_desktop.admin.devices.DeviceNetworkView
import com.saporini.mobile_desktop.admin.devices.RefusedNetwork
import com.saporini.mobile_desktop.admin.devices.SavedNetwork
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BottomToast
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.SettingsCard
import com.saporini.mobile_desktop.core.components.SettingsInfoBanner
import com.saporini.mobile_desktop.core.components.SettingsSwitch
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.instantOrNull
import com.saporini.mobile_desktop.core.format.shortDate
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.i18n.tr
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.HoverTooltip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.koinInject

private val RowLine = Color(0xFFF0F1EE)

/**
 * Settings → Devices & printing → the restaurant's network (user, 2026-11-24: "tills, tablets and screens can be set up
 * only in the restaurant's network"). For the branch worked in: the switch, the saved networks (the first till's is saved
 * by itself), "Use this network" from a device in the restaurant, and the networks devices were turned away from, which
 * can be allowed from anywhere (the restaurant's internet address can change). Each change saves at once.
 */
@Composable
internal fun DeviceNetworkSettings(restaurantId: String, branchId: String?, phone: Boolean) {
    if (branchId == null) {
        SettingsInfoBanner("Choose the branch you work in to set its network.")
        return
    }
    val api = koinInject<DeviceNetworkApi>()
    val coroutines = rememberCoroutineScope()
    var view by remember(restaurantId, branchId) { mutableStateOf<DeviceNetworkView?>(null) }
    var loadError by remember(restaurantId, branchId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // What is being saved now: "rule", "this", a network's id or a turned-away address.
    var working by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<SavedNetwork?>(null) }

    LaunchedEffect(restaurantId, branchId, reload) {
        loadError = null
        try {
            view = api.get(restaurantId, branchId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: "Couldn't load the network."
        }
    }

    fun act(key: String, done: String, call: suspend () -> DeviceNetworkView) {
        if (working != null) return
        working = key
        error = null
        coroutines.launch {
            try {
                view = call()
                notice = done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't save. Please try again."
            } finally {
                working = null
            }
        }
    }

    val current = view
    when {
        current == null && loadError != null -> SettingsCard("The restaurant's network", icon = Icons.Outlined.Wifi) {
            Spacer(Modifier.height(10.dp))
            Text(loadError.orEmpty(), fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
            Spacer(Modifier.height(8.dp))
            KitButton("Try again", { reload++ }, style = ButtonStyle.SECONDARY)
        }
        current == null -> NetworkSkeleton()
        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NetworkCard(current, phone, working,
                onSwitch = { on ->
                    act("rule", if (on) "Tills, tablets and screens work only on the restaurant's network now." else "Tills, tablets and screens work on any network now.") {
                        api.setRule(restaurantId, branchId, on)
                    }
                },
                onUseThis = { act("this", "This network is saved.") { api.add(restaurantId, branchId) } },
                onRemove = { removing = it })
            if (current.refused.isNotEmpty()) {
                RefusedCard(current.refused, working, full = current.networks.size >= current.maxNetworks) { refused ->
                    act(refused.address, "${refused.address} is allowed now.") { api.add(restaurantId, branchId, refused.address) }
                }
            }
            ComingNext()
        }
    }

    removing?.let { network ->
        val last = current != null && current.networks.size == 1 && current.onlyOnNetwork
        AppDialog(
            title = "Remove ${network.address}?",
            onDismiss = { removing = null },
            maxWidth = 440.dp,
            buttons = {
                KitButton("Cancel", { removing = null }, style = ButtonStyle.SECONDARY)
                KitButton("Remove", {
                    removing = null
                    act(network.id, "${network.address} is removed.") { api.remove(restaurantId, branchId, network.id) }
                }, style = ButtonStyle.DANGER)
            }
        ) {
            Text(
                if (last) "It's the only one, so tills, tablets and screens will work on any network until you save one again."
                else "Devices on it won't connect or sign in with a PIN any more.",
                fontFamily = Inter(), fontSize = 14.sp, color = Kit.Ink
            )
        }
    }

    BottomToast(error ?: notice, isError = error != null) { error = null; notice = null }
}

/** The switch, the saved networks and "Use this network". */
@Composable
private fun NetworkCard(
    view: DeviceNetworkView,
    phone: Boolean,
    working: String?,
    onSwitch: (Boolean) -> Unit,
    onUseThis: () -> Unit,
    onRemove: (SavedNetwork) -> Unit
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = Clock.System.now()
    SettingsCard(
        "Tills only on the restaurant's network",
        note = "Tills, tablets and screens are connected, and staff sign in with their PIN, only on the restaurant's Wi-Fi. Owners can connect a device from anywhere.",
        icon = Icons.Outlined.Wifi,
        trailing = {
            SettingsSwitch(view.onlyOnNetwork, onSwitch, enabled = working == null && (view.onlyOnNetwork || view.networks.isNotEmpty()))
        }
    ) {
        Spacer(Modifier.height(12.dp))
        if (view.networks.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("No network saved yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink)
                Text("It's saved by itself when the first till is connected here. Or save it now, from a device on the restaurant's Wi-Fi.",
                    fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
            }
        } else {
            view.networks.forEachIndexed { index, network ->
                if (index > 0) HorizontalDivider(color = RowLine)
                NetworkLine(network, zone, now, working, onRemove)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = RowLine)
        Spacer(Modifier.height(12.dp))
        YourNetwork(view, phone, working, onUseThis)
    }
}

@Composable
private fun NetworkLine(network: SavedNetwork, zone: TimeZone, now: kotlin.time.Instant, working: String?, onRemove: (SavedNetwork) -> Unit) {
    val added = instantOrNull(network.addedAt)?.toLocalDateTime(zone)?.date?.let { shortDate(it) }
    val from = when {
        network.learnedFrom != null -> "Saved when ${network.learnedFrom} was connected"
        network.addedBy != null -> "Added by ${network.addedBy}"
        else -> "Added"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Blue.copy(alpha = 0.12f)), Alignment.Center) {
            Icon(Icons.Outlined.Wifi, null, Modifier.size(18.dp), tint = Kit.Blue)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(network.address, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (network.yours) Pill("You're on it", Kit.Green, Kit.GreenSoft)
            }
            Text(listOfNotNull(from, added).joinToString(" · "), fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            network.lastSeenAt?.let {
                Text("Last used ${tr(agoText(it, now, zone))}", fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Faint)
            }
        }
        HoverTooltip("Remove") {
            Box(
                Modifier.size(34.dp).clip(CircleShape).clickable(enabled = working == null) { onRemove(network) }
                    .alpha(if (working == network.id) 0.4f else 1f),
                Alignment.Center
            ) {
                Icon(Icons.Outlined.DeleteOutline, "Remove", Modifier.size(19.dp), tint = Kit.Danger)
            }
        }
    }
}

/** The network this device is on now, and "Use this network" while it isn't saved. */
@Composable
private fun YourNetwork(view: DeviceNetworkView, phone: Boolean, working: String?, onUseThis: () -> Unit) {
    val yours = view.yourNetwork
    val words: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view.yourNetworkSaved) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Kit.Green)
            Text(
                when {
                    yours == null -> "Can't tell which network this device is on."
                    view.yourNetworkSaved -> "This device is on the restaurant's network."
                    else -> "This device is on $yours."
                },
                fontFamily = Inter(), fontSize = 12.5.sp, color = if (view.yourNetworkSaved) Kit.Ink else Kit.Muted
            )
        }
    }
    val full = view.networks.size >= view.maxNetworks
    val button: (@Composable () -> Unit)? = if (yours != null && !view.yourNetworkSaved) {
        {
            if (full) Text("${view.maxNetworks} networks are saved. Remove one first.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Amber)
            else KitButton("Use this network", onUseThis, icon = Icons.Outlined.Add, style = ButtonStyle.PRIMARY_OUTLINE,
                enabled = working == null, loading = working == "this")
        }
    } else null
    if (phone) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            words(Modifier)
            button?.invoke()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            words(Modifier.weight(1f))
            button?.invoke()
        }
    }
}

/** Networks devices were turned away from in the last week, each with "Allow this network". */
@Composable
private fun RefusedCard(refused: List<RefusedNetwork>, working: String?, full: Boolean, onAllow: (RefusedNetwork) -> Unit) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = Clock.System.todayIn(zone)
    SettingsCard(
        "Tried from other networks",
        note = "Devices turned away in the last week. If the restaurant's internet changed, allow its new network here.",
        icon = Icons.Outlined.WifiOff,
        iconTint = Kit.Amber
    ) {
        Spacer(Modifier.height(8.dp))
        refused.forEachIndexed { index, line ->
            if (index > 0) HorizontalDivider(color = RowLine)
            val what = listOfNotNull("PIN sign-in".takeIf { line.pinSignIn }, "Connecting".takeIf { line.connecting }).joinToString(" · ")
            val times = if (line.times == 1) "Tried once" else "Tried ${line.times} times"
            val detail = listOf(line.devices.joinToString(", ").ifBlank { "A device" }, what, dateTimeText(line.lastAt, zone, today), times)
                .filter { it.isNotBlank() }.joinToString(" · ")
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(Icons.Outlined.WifiOff, null, Modifier.size(18.dp), tint = Kit.Amber)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.address, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (line.address != "unknown") {
                    KitButton("Allow this network", { onAllow(line) }, style = ButtonStyle.PRIMARY_OUTLINE,
                        enabled = working == null && !full, loading = working == line.address)
                }
            }
        }
    }
}

/** What else this page will hold. */
@Composable
private fun ComingNext() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Print, null, Modifier.size(18.dp), tint = Kit.Faint)
        Text("Printers and where each ticket prints come next.", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
    }
}

@Composable
private fun Pill(text: String, color: Color, background: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = color, maxLines = 1)
}

/** The card's shape while it loads. */
@Composable
private fun NetworkSkeleton() {
    val alpha = rememberSkeletonAlpha("network")
    Column(
        Modifier.fillMaxWidth().alpha(alpha).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, Kit.Border, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(32.dp), SkeletonLight, RoundedCornerShape(9.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.width(220.dp).height(13.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.8f).height(10.dp), SkeletonLight)
            }
            SkeletonBox(Modifier.width(48.dp).height(26.dp), SkeletonLight, RoundedCornerShape(50))
        }
        repeat(2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(36.dp), SkeletonLight, RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.width(140.dp).height(12.dp))
                    SkeletonBox(Modifier.width(200.dp).height(9.dp), SkeletonLight)
                }
            }
        }
    }
}
