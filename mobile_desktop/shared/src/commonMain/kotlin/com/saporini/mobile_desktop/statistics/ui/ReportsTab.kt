package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.InfoBox
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.dayCountText
import com.saporini.mobile_desktop.core.components.periodLabel
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.statistics.StatisticsScreenModel
import com.saporini.mobile_desktop.statistics.StatisticsState
import com.saporini.mobile_desktop.statistics.data.ReportInfoDto

private fun reportLook(code: String): Pair<ImageVector, Color> = when (code) {
    "daily-sales" -> Icons.Outlined.CalendarMonth to Kit.Green
    "items" -> Icons.Outlined.RestaurantMenu to Kit.Amber
    "staff" -> Icons.Outlined.Badge to Kit.Purple
    "payments" -> Icons.Outlined.Payments to Kit.Blue
    else -> Icons.Outlined.Description to Kit.Grey
}

@Composable
internal fun ReportsTab(state: StatisticsState, model: StatisticsScreenModel, size: ScreenSize) {
    val from = state.from
    val to = state.to
    if (state.reports.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
            OverviewEmpty("No reports", "Reports you can download show here.", Icons.AutoMirrored.Outlined.ReceiptLong)
        }
        return
    }
    if (from != null && to != null) {
        InfoBox(Icons.Outlined.TableChart, "Each report covers ${periodLabel(from, to)} (${dayCountText(from, to)}). Change the days at the top. " +
            "Files open in Excel, Numbers or Google Sheets.", Modifier.fillMaxWidth(), tone = Kit.Green, highlighted = true)
    }
    val columns = when (size) { ScreenSize.PHONE -> 1; ScreenSize.TABLET -> 2; ScreenSize.DESKTOP -> 2 }
    state.reports.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { report -> ReportCard(report, state, model, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ReportCard(report: ReportInfoDto, state: StatisticsState, model: StatisticsScreenModel, modifier: Modifier) {
    val (icon, tone) = reportLook(report.code)
    val busy = state.downloading == report.code
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border), shadowElevation = 1.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(6.dp).background(tone))
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.12f)), Alignment.Center) {
                    Icon(icon, null, Modifier.size(26.dp), tint = tone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                    Text(report.description, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Muted)
                    Text("CSV spreadsheet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Kit.Faint)
                }
                KitButton(if (busy) "Preparing…" else "Download", { model.download(report.code) }, icon = Icons.Outlined.Download, loading = busy,
                    enabled = state.downloading == null)
            }
        }
    }
}
