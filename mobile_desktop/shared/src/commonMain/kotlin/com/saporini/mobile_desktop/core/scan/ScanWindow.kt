package com.saporini.mobile_desktop.core.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import com.saporini.mobile_desktop.admin.inventory.InventoryJump
import com.saporini.mobile_desktop.admin.inventory.InventoryRepository
import com.saporini.mobile_desktop.admin.inventory.InventoryRights
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import com.saporini.mobile_desktop.admin.inventory.StockLevelDto
import com.saporini.mobile_desktop.admin.inventory.ui.ItemPictureLarge
import com.saporini.mobile_desktop.admin.inventory.ui.asDouble
import com.saporini.mobile_desktop.admin.inventory.ui.kindLabel
import com.saporini.mobile_desktop.admin.inventory.ui.trimmed
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.workspace.ui.InventoryWorkspaceScreen
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

/** What a scanned barcode is, as looked up for the person signed in. */
internal sealed interface ScanLookup {
    data object Loading : ScanLookup
    data object NotAllowed : ScanLookup
    data object Failed : ScanLookup
    data object Missing : ScanLookup
    data class Found(val item: StockItemDto, val levels: List<StockLevelDto>?) : ScanLookup
}

/** Looks [code] up among the restaurant's stock items (barcodes are kept trimmed and in capitals). */
@Composable
internal fun rememberScanLookup(code: String): ScanLookup {
    val repository = koinInject<InventoryRepository>()
    val session = koinInject<SessionManager>()
    return produceState<ScanLookup>(ScanLookup.Loading, code) {
        val user = session.currentUser.value
        val restaurantId = user?.restaurantId?.takeIf { it.isNotBlank() }
        val rights = InventoryRights.of(user?.permissions.orEmpty())
        if (restaurantId == null || !(rights.items || rights.stock || rights.overview)) {
            value = ScanLookup.NotAllowed
            return@produceState
        }
        value = try {
            val wanted = code.trim().replace(" ", "").uppercase()
            val item = repository.items(restaurantId, includeInactive = true)
                .firstOrNull { it.barcode?.trim()?.replace(" ", "")?.uppercase() == wanted }
            if (item == null) ScanLookup.Missing
            else ScanLookup.Found(item, if (rights.stock || rights.overview) repository.levels(restaurantId, null, item.id) else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ScanLookup.Failed
        }
    }.value
}

/**
 * A barcode scanned anywhere in the app while someone is signed in (user, 2026-11-27: "show what it is"): a small window with
 * the stock item that has it (picture, how much and where, Open in Inventory), or "No item has this barcode" with New item.
 */
@Composable
fun ScanWindow(navigator: Navigator) {
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val signedIn by rememberUpdatedState(user?.restaurantId?.isNotBlank() == true)
    var code by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        ScanCatcher.scans.collect { scanned -> if (signedIn) code = scanned }
    }
    val shown = code ?: return
    val permissions = user?.permissions.orEmpty()
    val rights = InventoryRights.of(permissions)
    val canOpen = "INVENTORY_ACCESS" in permissions && rights.stock
    val canCreate = "INVENTORY_ACCESS" in permissions && rights.createItems
    val lookup = rememberScanLookup(shown)
    fun goToInventory() {
        if (navigator.lastItem != InventoryWorkspaceScreen) navigator.push(InventoryWorkspaceScreen)
    }
    AppDialog("Scanned $shown", { code = null }, maxWidth = 500.dp, buttons = {
        KitButton("Close", { code = null }, style = ButtonStyle.SECONDARY)
        when {
            lookup is ScanLookup.Found && canOpen -> KitButton("Open in Inventory", {
                InventoryJump.scan = shown
                code = null
                goToInventory()
            }, icon = Icons.Outlined.Inventory2)
            lookup is ScanLookup.Missing && canCreate -> KitButton("New item with this barcode", {
                InventoryJump.newItem = shown
                code = null
                goToInventory()
            }, icon = Icons.Outlined.Add)
        }
    }) {
        ScanLookupContent(lookup)
    }
}

/** What the lookup found, for the scan window and the scanner's "Try it" box in Devices. */
@Composable
internal fun ScanLookupContent(lookup: ScanLookup) {
    when (lookup) {
        ScanLookup.Loading -> Note("Looking it up…")
        ScanLookup.NotAllowed -> Note("You can't see stock items. Ask someone who handles stock.")
        ScanLookup.Failed -> Note("Couldn't look it up. Scan it again.")
        ScanLookup.Missing -> Note("No item has this barcode.")
        is ScanLookup.Found -> FoundItem(lookup.item, lookup.levels)
    }
}

@Composable
private fun Note(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Tint).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(20.dp), tint = Kit.Muted)
        Text(text, fontFamily = Inter(), fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Kit.Ink)
    }
}

@Composable
private fun FoundItem(item: StockItemDto, levels: List<StockLevelDto>?) {
    val unit = unitShort(item.baseUnit)
    val places = levels.orEmpty().filter { it.onHandQuantity.asDouble() != 0.0 }.sortedByDescending { it.onHandQuantity.asDouble() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ItemPictureLarge(item, Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.kindLabel(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
                Text(item.name, translate = false, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp, color = Kit.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    !item.active -> Text("This item is switched off.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Amber)
                    !item.trackInventory -> Text("Not counted, so it has no stock.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
                    levels != null -> Text("${trimmed(places.sumOf { it.onHandQuantity.asDouble() })} $unit in stock", fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
                }
            }
        }
        if (item.active && item.trackInventory && levels != null) {
            if (places.isEmpty()) Note("None in stock.")
            else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Tint).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                places.forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(level.locationName ?: "–", Modifier.weight(1f), translate = false, fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp,
                            color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${trimmed(level.onHandQuantity.asDouble())} $unit", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            letterSpacing = 0.sp, color = if (level.lowStock) Kit.Amber else Kit.Ink)
                    }
                }
            }
        }
    }
}
