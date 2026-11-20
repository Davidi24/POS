package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.files.csvRow
import com.saporini.mobile_desktop.statistics.StatsView
import kotlinx.coroutines.launch

// Every Statistics card has its own Download at its top right (user, 2026-11-15: "every card has a download button on the top
// right"): it saves what the card shows as a spreadsheet, for the days it shows.

/** Opens a card's export window with its report and picture. The screen provides it. */
internal val LocalStatsSave = staticCompositionLocalOf<(CardReport, androidx.compose.ui.graphics.ImageBitmap?) -> Unit> { { _, _ -> } }

/** A card's save: takes the card's picture as it is now, then opens the export window with the report and the picture. */
@Composable
internal fun rememberCardSave(): (CardReport) -> Unit {
    val save = LocalStatsSave.current
    val picture = LocalPanelPicture.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return { report -> scope.launch { save(report, picture?.invoke()) } }
}

/** Cents as a plain number for a spreadsheet: 1234567 → "12345.67". */
internal fun csvMoney(cents: Long): String =
    (if (cents < 0) "-" else "") + "${kotlin.math.abs(cents) / 100}.${(kotlin.math.abs(cents) % 100).toString().padStart(2, '0')}"

/** A heading line and the rows, as CSV. */
internal fun csvTable(header: List<String>, rows: List<List<String>>): String = (listOf(header) + rows).joinToString("\n") { csvRow(it) }

/** "34%" of [total], for a spreadsheet's share column. */
internal fun csvShare(part: Long, total: Long): String = if (total <= 0) "0%" else "${(part * 100 + total / 2) / total}%"

/**
 * A card's Download at its top right: opens the card's export window (chart and table as a PDF, or the table as a CSV), and
 * puts the card on the page's list for the page's own Download.
 */
@Composable
internal fun CardDownload(v: StatsView, stem: String, size: Dp = 32.dp, report: () -> CardReport) {
    val save = rememberCardSave()
    RegisterCard(stem, report)
    PanelDownloadButton(size) { save(report()) }
}
