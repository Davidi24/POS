package com.saporini.mobile_desktop.core.printing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.ReceiptKind
import org.koin.compose.getKoin

/**
 * Printing's messages and questions, wherever the app is (user, 2026-11-27): "Print the receipt?" after the last payment when
 * the Admin Hub says to ask, which printer when this device has none of its own, and a toast when a printer didn't print.
 */
@Composable
fun PrintingPanel() {
    val koin = getKoin()
    val printing = remember { koin.getOrNull<Printing>() } ?: return
    val notice by printing.notice.collectAsState()
    val question by printing.question.collectAsState()
    question?.let { q ->
        if (q.choices.isNotEmpty()) {
            AppDialog(title = "Print on which printer?", onDismiss = printing::dismissQuestion, maxWidth = 460.dp,
                buttons = { KitButton("Don't print", printing::dismissQuestion, style = ButtonStyle.SECONDARY) }) {
                Text(if (q.kind == ReceiptKind.FISCAL) "The payment is done. Choose where the receipt prints." else "Choose where the bill prints.",
                    fontFamily = Inter(), fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.choices.forEach { printer -> PrinterChoice(printer.name, printer.where) { printing.printAnswer(q, printer) } }
                }
            }
        } else {
            AppDialog(title = "Print the receipt?", onDismiss = printing::dismissQuestion, maxWidth = 420.dp,
                buttons = {
                    KitButton("No", printing::dismissQuestion, style = ButtonStyle.SECONDARY)
                    KitButton("Print", { printing.printAnswer(q) }, icon = Icons.Outlined.Print)
                }) {
                Text("The payment is done.", fontFamily = Inter(), fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
            }
        }
    }
    ScreenMessages(notice?.takeIf { it.problem }?.text, notice?.takeIf { !it.problem }?.text, printing::clearNotice)
}

/** A printer to pick: its name and where it is. */
@Composable
fun PrinterChoice(name: String, where: String?, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))
        .clickable(onClick = onPick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint), Alignment.Center) {
            Icon(Icons.Outlined.Print, null, Modifier.size(18.dp), tint = Kit.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(name, translate = false, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
            where?.takeIf { it.isNotBlank() }?.let {
                Text(it, translate = false, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            }
        }
    }
}
