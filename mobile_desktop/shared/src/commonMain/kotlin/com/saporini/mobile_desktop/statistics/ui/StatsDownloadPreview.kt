package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.files.canPrintTables
import com.saporini.mobile_desktop.core.files.printTable
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.statistics.StatsDownload
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.launch

// What a Download shows first (user, 2026-11-15: "when I click download it gives a preview and the option download or print;
// download opens the folder so I save it wherever I want"): the spreadsheet as a table, then Print or Download, where
// Download asks where to save it.

/** The lines of a CSV made here (cells may be quoted, with "" inside). */
internal fun parseCsv(text: String): List<List<String>> = text.lines().filter { it.isNotEmpty() }.map { line ->
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            quoted && ch == '"' && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
            else -> cell.append(ch)
        }
        i++
    }
    cells += cell.toString()
    cells.map { it.removePrefix("'") }
}

/** "sales-by-floor-2026-11-01-to-2026-11-14.csv" → "Sales by floor"; the dates part → "1 Nov 2026 – 14 Nov 2026". */
private fun titleOf(fileName: String): Pair<String, String?> {
    val base = fileName.removeSuffix(".csv")
    val dates = Regex("-(\\d{4}-\\d{2}-\\d{2})(-to-(\\d{4}-\\d{2}-\\d{2}))?$").find(base)
    val name = (dates?.let { base.substring(0, it.range.first) } ?: base).replace('-', ' ').replaceFirstChar { it.uppercase() }
    val period = dates?.let { m ->
        val from = runCatching { kotlinx.datetime.LocalDate.parse(m.groupValues[1]) }.getOrNull()
        val to = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
        when {
            from == null -> null
            to == null || to == from -> com.saporini.mobile_desktop.core.format.weekdayDate(from)
            else -> com.saporini.mobile_desktop.core.components.periodLabel(from, to)
        }
    }
    return name to period
}

/**
 * The preview of a server report (the cards have their own export window, StatsReport.kt): its title and dates, the table,
 * then Close, Print (on the computer) and Download, which opens the system's save window.
 */
@Composable
internal fun DownloadPreviewDialog(download: StatsDownload, @Suppress("UNUSED_PARAMETER") chart: androidx.compose.ui.graphics.ImageBitmap?,
    onClose: () -> Unit, onResult: (String) -> Unit) {
    val rows = remember(download) { parseCsv(download.csv) }
    val header = rows.firstOrNull().orEmpty()
    val body = rows.drop(1)
    val (title, period) = remember(download) { titleOf(download.fileName) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val saver = rememberFileSaverLauncher(io.github.vinceglb.filekit.dialogs.FileKitDialogSettings.createDefault()) { file ->
        if (file == null) return@rememberFileSaverLauncher
        scope.launch {
            busy = true
            val result = runCatching { file.writeString(download.csv); "Saved to ${file.absolutePath()}" }
                .getOrElse { "Couldn't save the report: ${it.message ?: "unknown problem"}" }
            busy = false
            onResult(result)
            onClose()
        }
    }
    AppDialog(
        title = title,
        onDismiss = onClose,
        subtitle = listOfNotNull(period, "${body.size} ${if (body.size == 1) "line" else "lines"}").joinToString(" · "),
        busy = busy,
        maxWidth = 820.dp,
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            if (canPrintTables) KitButton("Print", {
                scope.launch {
                    busy = true
                    val printed = printTable(listOfNotNull(title, period).joinToString(" · "), header, body)
                    busy = false
                    if (!printed) onResult("Couldn't print: no printer, or printing was cancelled")
                }
            }, icon = Icons.Outlined.Print, style = ButtonStyle.PRIMARY_OUTLINE, enabled = !busy)
            KitButton("Download CSV", { saver.launch(download.fileName.removeSuffix(".csv"), "csv") }, icon = Icons.Outlined.Download, enabled = !busy)
        }
    ) {
        PreviewTable(header, body)
    }
}

/** The table: a tinted heading line, then the lines in soft stripes; numbers sit at the right. */
@Composable
internal fun PreviewTable(header: List<String>, body: List<List<String>>) {
    fun numeric(text: String) = text.isNotEmpty() && (text.toDoubleOrNull() != null || text.endsWith("%"))
    val weights = header.indices.map { if (it == 0) 1.4f else 1f }
    // A column of numbers sits at the right, a column of words at the left (its heading too).
    val numberColumn = header.indices.map { i -> body.any { numeric(it.getOrElse(i) { "" }) } && body.all { it.getOrElse(i) { "" }.let { c -> c.isEmpty() || numeric(c) } } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))) {
        Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 9.dp)) {
            header.forEachIndexed { i, h ->
                Text(h, Modifier.weight(weights[i]).padding(end = 8.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.sp,
                    color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (numberColumn[i]) TextAlign.End else TextAlign.Start)
            }
        }
        body.forEachIndexed { r, line ->
            Row(Modifier.fillMaxWidth().background(if (r % 2 == 1) Kit.Canvas.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.White)
                .padding(horizontal = 12.dp, vertical = 7.dp)) {
                header.indices.forEach { i ->
                    val cell = line.getOrElse(i) { "" }
                    Text(cell, Modifier.weight(weights[i]).padding(end = 8.dp), fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp,
                        color = if (cell.isEmpty()) Kit.Faint else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = if (numberColumn[i]) TextAlign.End else TextAlign.Start)
                }
            }
        }
        if (body.isEmpty()) Text("Nothing in these days.", Modifier.padding(16.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
    }
}
