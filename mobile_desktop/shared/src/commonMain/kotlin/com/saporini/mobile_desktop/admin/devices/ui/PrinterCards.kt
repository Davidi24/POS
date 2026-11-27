@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.saporini.mobile_desktop.admin.devices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.DevicesState
import com.saporini.mobile_desktop.admin.devices.PrintTargetDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter

/**
 * A printer's "What it prints" (user, 2026-11-27: "you configure a printer and you say what it should print, that's all"): the
 * tills whose receipts and bills it prints and the kitchen stations whose tickets it prints. A click takes one or lets it go,
 * saved at once; each prints on one printer, so one shown "on Back printer" moves here. A document printer prints what people
 * choose it for when they print a report. Test page prints from this device.
 */
@Composable
internal fun PrinterJobsCard(device: DeviceDto, state: DevicesState, model: DevicesScreenModel) {
    Panel("What it prints", null, Icons.Outlined.Print, Kit.Ink,
        trailing = { KitButton("Test page", { model.testPrinter(device) }, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Print) }) {
        PrinterJobs(device, state, model)
    }
}

/** Right after a printer is added and confirmed: the same choice, in a window. */
@Composable
internal fun PrinterJobsDialog(device: DeviceDto, state: DevicesState, model: DevicesScreenModel, onDone: () -> Unit) {
    AppDialog("What does ${device.name} print?", onDone, subtitle = "Tick the tills and kitchen stations it prints for.", maxWidth = 600.dp,
        buttons = { KitButton("Done", onDone) }) {
        PrinterJobs(device, state, model)
    }
}

@Composable
private fun PrinterJobs(device: DeviceDto, state: DevicesState, model: DevicesScreenModel) {
    if (device.printerRole == "DOCUMENT") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Tint), Alignment.Center) {
                Icon(Icons.Outlined.Description, null, Modifier.size(17.dp), tint = Kit.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Documents and reports", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
                Text("Chosen each time someone prints, like a report in Statistics.", fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp,
                    letterSpacing = 0.sp, color = Kit.Muted)
            }
        }
        return
    }
    val jobs = state.printerJobs
    if (jobs == null) {
        Text("Loading…", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        JobGroup("RECEIPTS AND BILLS FOR", Icons.Outlined.PointOfSale, jobs.tills, jobs.receiptTillIds, device.id, state.canEdit,
            "No tills or tablets yet.") { ids -> model.savePrinterJobs(device.id, ids, jobs.kitchenStationIds) }
        JobGroup("KITCHEN TICKETS FOR", Icons.Outlined.Restaurant, jobs.stations, jobs.kitchenStationIds, device.id, state.canEdit,
            "No kitchen stations yet.") { ids -> model.savePrinterJobs(device.id, jobs.receiptTillIds, ids) }
        if (state.canEdit) Text("Each one prints on one printer. Picking one from another printer moves it here.", fontFamily = Inter(),
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp, color = Kit.Faint)
    }
}

@Composable
private fun JobGroup(title: String, icon: ImageVector, targets: List<PrintTargetDto>, chosen: List<String>, printerId: String, canEdit: Boolean,
                     empty: String, onChange: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(15.dp), tint = Kit.Faint)
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
        }
        if (targets.isEmpty()) {
            Text(empty, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            return
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { target ->
                val on = target.id in chosen
                val elsewhere = target.printerName?.takeIf { target.printerId != null && target.printerId != printerId }
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) Kit.GreenSoft else Color.White)
                    .border(1.dp, if (on) Kit.Green else Kit.Border, RoundedCornerShape(50))
                    .clickable(enabled = canEdit) { onChange(if (on) chosen - target.id else chosen + target.id) }
                    .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (on) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(16.dp),
                        tint = if (on) Kit.Green else Kit.Faint)
                    Text(target.name, translate = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp,
                        color = if (on) Kit.Green else Kit.Ink)
                    target.branchName?.let { Text(it, translate = false, fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Faint) }
                    elsewhere?.let { Text("on $it", fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Amber) }
                }
            }
        }
    }
}
