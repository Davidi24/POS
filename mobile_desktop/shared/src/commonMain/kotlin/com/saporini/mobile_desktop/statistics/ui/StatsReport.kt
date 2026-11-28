package com.saporini.mobile_desktop.statistics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.files.A4_HEIGHT
import com.saporini.mobile_desktop.core.files.A4_WIDTH
import com.saporini.mobile_desktop.core.files.DocAlign
import com.saporini.mobile_desktop.core.files.DocInk
import com.saporini.mobile_desktop.core.files.DocItem
import com.saporini.mobile_desktop.core.files.DocLine
import com.saporini.mobile_desktop.core.files.DocMuted
import com.saporini.mobile_desktop.core.files.DocPage
import com.saporini.mobile_desktop.core.files.canPrintTables
import com.saporini.mobile_desktop.core.files.pdfDocument
import com.saporini.mobile_desktop.core.files.printDocument
import com.saporini.mobile_desktop.core.files.rememberFolderPicker
import com.saporini.mobile_desktop.core.files.textWidth
import com.saporini.mobile_desktop.core.files.wrapText
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.statistics.StatsView
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.launch

// A card's report (user, 2026-11-15: "look at the PDF: the graph, and the data as a table with totals; make it look like an
// invoice; the person picks both or one; the page's Download saves everything into a folder"). Each card says what its
// table holds (columns, lines, totals); this file turns that into a spreadsheet and into A4 pages that look like an invoice,
// shows them before saving, and saves every card of a page at once.

enum class ColumnKind { TEXT, MONEY, COUNT, SHARE }

class ReportColumn(val name: String, val kind: ColumnKind = ColumnKind.TEXT)

/** A figure under the table: money in cents, or a count. */
class ReportTotal(val label: String, val value: Long, val money: Boolean = true)

/**
 * What a card's download holds. [rows] hold text (String) in TEXT and SHARE columns and numbers (Long: cents for MONEY) in the
 * others. [dates] and [fileDates] replace the top's dates when the card shows its own day or week.
 */
class CardReport(
    val stem: String,
    val title: String,
    val columns: List<ReportColumn>,
    val rows: List<List<Any>>,
    val totals: List<ReportTotal> = emptyList(),
    val dates: String? = null,
    val fileDates: String? = null,
    val note: String? = null
)

/** Who and what the report is for, said at the top of every page. */
class ReportInfo(val restaurant: String, val branch: String?, val page: String, val period: String, val compare: String?,
    val madeOn: String, val currency: String, val fileDates: String)

/** The report as a spreadsheet (money as plain numbers, "Sales (EUR)"). */
fun CardReport.csv(currency: String): String = csvTable(
    columns.map { if (it.kind == ColumnKind.MONEY) "${it.name} ($currency)" else it.name },
    rows.map { row -> row.mapIndexed { i, cell -> if (columns.getOrNull(i)?.kind == ColumnKind.MONEY) csvMoney(cell as Long) else cell.toString() } }
)

private fun CardReport.cellText(i: Int, cell: Any, currency: String): String = when (columns.getOrNull(i)?.kind) {
    ColumnKind.MONEY -> moneyText(cell as Long, currency)
    ColumnKind.COUNT -> countText(cell as Long)
    else -> cell.toString()
}

// ---- The pages ----

internal const val Left = 48f
internal const val Right = A4_WIDTH - 48f
internal const val FooterTop = A4_HEIGHT - 64f
internal val PageGreen = Color(0xFF4F7942)
internal val PageCanvas = Color(0xFFF6F8F5)

/**
 * The report's A4 pages, like an invoice: on top the restaurant and the report's name and dates, then who it's for and the
 * details; a page with the card as it looks ([chart], when [withChart]); the table with its totals ([withTable], more pages
 * when it's long); a note and "Page 1 of 2" at the foot.
 */
fun reportPages(report: CardReport, info: ReportInfo, chart: ImageBitmap?, withChart: Boolean, withTable: Boolean): List<DocPage> {
    val bodies = mutableListOf<Pair<String, MutableList<DocItem>>>()
    if (withChart && chart != null) bodies += "CHART" to chartBody(report, info, chart).toMutableList()
    if (withTable || bodies.isEmpty()) tableBodies(report, info).forEach { bodies += "DETAILS" to it.toMutableList() }
    val note = report.note ?: "Sales are the bills after discounts, VAT included, for the days shown. Made by Saporini POS."
    return bodies.mapIndexed { index, (kind, body) ->
        DocPage(header(report.title, report.dates, info.compare?.takeIf { report.dates == null }, info, kind) + body + footer(note, index + 1, bodies.size))
    }
}

/** The top of a page: the restaurant, the report's [title] and [dates] (null = the top's), what the page holds ([kind]), who it's for. */
internal fun header(reportTitle: String, dates: String?, compare: String?, info: ReportInfo, kind: String): List<DocItem> = buildList {
    // The restaurant: its first letter on a green square, its name, and where the report comes from.
    add(DocItem.Box(Left, 44f, 36f, 36f, fill = PageGreen, radius = 8f))
    add(DocItem.Text(Left + 18f, 68f, info.restaurant.trim().firstOrNull()?.uppercase() ?: "S", 17f, bold = true, color = Color.White, align = DocAlign.CENTER))
    add(DocItem.Text(Left + 48f, 58f, info.restaurant, 13.5f, bold = true))
    add(DocItem.Text(Left + 48f, 74f, "Statistics · ${info.page}", 9.5f, color = DocMuted))
    // The report: its name spaced out, its dates, and what this page holds in a framed tag (like an invoice's stamp).
    // A long name gets smaller (down to 11 pt) so it never runs into the restaurant's name on the left.
    val room = Right - (Left + 48f + textWidth(info.restaurant, 13.5f, bold = true) + 20f)
    val title = reportTitle.uppercase()
    var titleSize = 18f
    while (titleSize > 11f && textWidth(title, titleSize, bold = true, spacing = titleSize / 9f) > room) titleSize -= 0.5f
    add(DocItem.Text(Right, 64f, fit(title, titleSize, true, room), titleSize, bold = true, align = DocAlign.RIGHT, spacing = titleSize / 9f))
    add(DocItem.Text(Right, 82f, dates ?: info.period, 10f, color = DocMuted, align = DocAlign.RIGHT))
    val tagWidth = textWidth(kind, 8.5f, bold = true, spacing = 1.5f) + 18f
    add(DocItem.Box(Right - tagWidth, 92f, tagWidth, 18f, stroke = PageGreen, strokeWidth = 1.3f, radius = 5f))
    add(DocItem.Text(Right - tagWidth / 2, 104.5f, kind, 8.5f, bold = true, color = PageGreen, align = DocAlign.CENTER, spacing = 1.5f))
    add(DocItem.Line(Left, 126f, Right, 126f))
    // Who it's for, and the details on the right.
    add(DocItem.Text(Left, 152f, "PREPARED FOR", 8f, bold = true, color = DocMuted, spacing = 1.2f))
    add(DocItem.Text(Left, 171f, info.restaurant, 12.5f, bold = true))
    add(DocItem.Text(Left, 187f, info.branch ?: "The whole restaurant", 10f, color = DocMuted))
    val facts = listOfNotNull(
        "Period" to (dates ?: info.period),
        compare?.let { "Compared with" to it },
        "Made on" to info.madeOn,
        "Amounts in" to info.currency
    )
    facts.forEachIndexed { i, (label, value) ->
        add(DocItem.Text(330f, 152f + i * 18f, label, 9.5f, color = DocMuted))
        add(DocItem.Text(Right, 152f + i * 18f, value, 9.5f, align = DocAlign.RIGHT))
    }
}

internal const val BodyTop = 236f

private fun sectionLabel(text: String, y: Float = BodyTop + 10f) = DocItem.Text(Left, y, text, 8f, bold = true, color = DocMuted, spacing = 1.2f)

private fun chartBody(report: CardReport, info: ReportInfo, chart: ImageBitmap): List<DocItem> = buildList {
    add(sectionLabel(report.title.uppercase()))
    // The card as it looks, framed, as wide as the page allows; the totals in boxes under it.
    val totalsRoom = if (report.totals.isNotEmpty()) 84f else 0f
    val maxW = Right - Left - 24f
    val maxH = FooterTop - 24f - totalsRoom - (BodyTop + 24f) - 24f
    val scale = minOf(maxW / chart.width, maxH / chart.height)
    val w = chart.width * scale
    val h = chart.height * scale
    val frameTop = BodyTop + 22f
    add(DocItem.Box(Left, frameTop, Right - Left, h + 24f, stroke = DocLine, radius = 10f))
    add(DocItem.Picture(Left + (Right - Left - w) / 2, frameTop + 12f, w, h, chart))
    if (report.totals.isNotEmpty()) {
        val shown = report.totals.take(3)
        val gap = 10f
        val boxW = (Right - Left - gap * (shown.size - 1)) / shown.size
        val top = frameTop + h + 24f + 18f
        shown.forEachIndexed { i, t ->
            val x = Left + i * (boxW + gap)
            add(DocItem.Box(x, top, boxW, 54f, fill = PageCanvas, stroke = DocLine, radius = 8f))
            add(DocItem.Text(x + 14f, top + 20f, t.label, 9f, color = DocMuted))
            add(DocItem.Text(x + 14f, top + 41f, if (t.money) moneyText(t.value, info.currency) else countText(t.value), 15f, bold = true))
        }
    }
}

/** The table over as many pages as it needs; the totals box on the last. */
private fun tableBodies(report: CardReport, info: ReportInfo): List<List<DocItem>> {
    val rowHeight = 22f
    val headerY = BodyTop + 34f
    val firstRowTop = headerY + 8f
    val totalsHeight = if (report.totals.isEmpty()) 0f else 40f + report.totals.size * 18f
    val rows = report.rows
    val perPage = ((FooterTop - 16f - firstRowTop) / rowHeight).toInt().coerceAtLeast(1)
    val chunks: List<List<List<Any>>> = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(perPage)
    // The totals need room under the last lines; when there isn't, they go on a page of their own after them.
    val lastFits = firstRowTop + chunks.last().size * rowHeight + 18f + totalsHeight <= FooterTop - 16f
    val pages: MutableList<List<List<Any>>> = chunks.toMutableList()
    if (!lastFits && report.totals.isNotEmpty()) pages.add(emptyList())
    // Columns: words get more room than numbers.
    val weights = report.columns.mapIndexed { i, c -> if (c.kind == ColumnKind.TEXT) (if (i == 0) 2f else 1.5f) else 1f }
    val totalWeight = weights.sum()
    val widths = weights.map { (Right - Left) * it / totalWeight }
    val starts = widths.runningFold(Left) { acc, w -> acc + w }
    return pages.mapIndexed { pageIndex, lines ->
        buildList {
            add(sectionLabel(if (pageIndex == 0) "DETAILS" else "DETAILS · CONTINUED"))
            report.columns.forEachIndexed { i, c ->
                val numeric = c.kind != ColumnKind.TEXT
                val label = (if (c.kind == ColumnKind.MONEY) "${c.name} (${info.currency})" else c.name).uppercase()
                add(DocItem.Text(if (numeric) starts[i] + widths[i] else starts[i], headerY, fit(label, 8f, true, widths[i] - 8f), 8f, bold = true,
                    color = DocMuted, align = if (numeric) DocAlign.RIGHT else DocAlign.LEFT, spacing = 0.6f))
            }
            add(DocItem.Line(Left, headerY + 8f, Right, headerY + 8f, color = DocInk, width = 1.3f))
            lines.forEachIndexed { r, row ->
                val top = firstRowTop + r * rowHeight
                row.forEachIndexed { i, cell ->
                    val c = report.columns.getOrNull(i) ?: return@forEachIndexed
                    val numeric = c.kind != ColumnKind.TEXT
                    val text = fit(report.cellText(i, cell, info.currency), 10f, false, widths[i] - 8f)
                    add(DocItem.Text(if (numeric) starts[i] + widths[i] else starts[i], top + 15f, text, 10f,
                        color = if (text.isEmpty()) DocMuted else DocInk, align = if (numeric) DocAlign.RIGHT else DocAlign.LEFT))
                }
                add(DocItem.Line(Left, top + rowHeight, Right, top + rowHeight, width = 0.6f))
            }
            if (rows.isEmpty() && pageIndex == 0) add(DocItem.Text(Left, firstRowTop + 18f, "Nothing in these days.", 10f, color = DocMuted))
            if (pageIndex == pages.lastIndex && report.totals.isNotEmpty()) {
                val top = if (lines.isEmpty()) firstRowTop else firstRowTop + lines.size * rowHeight + 18f
                val boxW = 250f
                add(DocItem.Box(Right - boxW, top, boxW, totalsHeight, fill = PageCanvas, stroke = DocLine, radius = 8f))
                report.totals.forEachIndexed { i, t ->
                    val y = top + 24f + i * 18f
                    add(DocItem.Text(Right - boxW + 16f, y, t.label, 9.5f, color = DocMuted))
                    add(DocItem.Text(Right - 16f, y, if (t.money) moneyText(t.value, info.currency) else countText(t.value), if (i == 0) 13f else 10.5f,
                        bold = true, align = DocAlign.RIGHT))
                }
                add(DocItem.Text(Right - 16f, top + totalsHeight - 10f, "Amounts in ${info.currency}", 8f, color = DocMuted, align = DocAlign.RIGHT))
            }
        }
    }
}

/** [text] cut with "…" so it fits [width]. */
internal fun fit(text: String, size: Float, bold: Boolean, width: Float): String {
    if (textWidth(text, size, bold) <= width) return text
    var cut = text
    while (cut.isNotEmpty() && textWidth("$cut…", size, bold) > width) cut = cut.dropLast(1)
    return "$cut…"
}

internal fun footer(note: String, page: Int, pages: Int): List<DocItem> = buildList {
    add(DocItem.Line(Left, FooterTop, Right, FooterTop))
    wrapText(note, 8.5f, 380f).take(3).forEachIndexed { i, line -> add(DocItem.Text(Left, FooterTop + 18f + i * 12f, line, 8.5f, color = DocMuted)) }
    add(DocItem.Text(Right, FooterTop + 18f, "Page $page of $pages", 8.5f, color = DocMuted, align = DocAlign.RIGHT))
}

// ---- A longer report, over as many pages as it needs ----

/** The top of a page after the first in a longer report: the restaurant and the report's name in one slim band. */
internal fun shortHeader(reportTitle: String, dates: String?, info: ReportInfo): List<DocItem> = buildList {
    add(DocItem.Box(Left, 40f, 26f, 26f, fill = PageGreen, radius = 6f))
    add(DocItem.Text(Left + 13f, 58f, info.restaurant.trim().firstOrNull()?.uppercase() ?: "S", 12f, bold = true, color = Color.White, align = DocAlign.CENTER))
    add(DocItem.Text(Left + 36f, 52f, info.restaurant, 10.5f, bold = true))
    add(DocItem.Text(Left + 36f, 64f, "Statistics · ${info.page}", 8f, color = DocMuted))
    val room = Right - (Left + 36f + textWidth(info.restaurant, 10.5f, bold = true) + 24f)
    add(DocItem.Text(Right, 52f, fit(reportTitle.uppercase(), 10.5f, true, room), 10.5f, bold = true, align = DocAlign.RIGHT, spacing = 1f))
    add(DocItem.Text(Right, 64f, dates ?: info.period, 8f, color = DocMuted, align = DocAlign.RIGHT))
    add(DocItem.Line(Left, 80f, Right, 80f))
}

/** Where the body starts on a page with the slim band. */
private const val ShortBodyTop = 100f

/** A figure in a box: its name, its value and a line under it ([tone] > 0 green, < 0 red, 0 grey). */
internal class DocFigure(val label: String, val value: String, val note: String? = null, val tone: Int = 0)

internal val DocUp = Color(0xFF15803D)
internal val DocDown = Color(0xFFB91C1C)

/**
 * A report written top to bottom (Sales details' Download all, user 2026-11-15: "even the content inside; make the pages look
 * good, more like reports"): sections, figure boxes, drawings and tables go one under another, and a new page starts
 * whenever the next piece doesn't fit. The first page has the full header (who it's for, period, compared with), the next
 * ones a slim band; a table goes on over pages with its column names again.
 */
internal class DocFlow(
    private val info: ReportInfo,
    private val title: String,
    private val dates: String?,
    private val compare: String?,
    private val kind: String,
    private val note: String
) {
    private val bodies = mutableListOf(mutableListOf<DocItem>())
    private var y = BodyTop
    private val bottom = FooterTop - 12f
    private val page get() = bodies.last()
    private val top get() = if (bodies.size == 1) BodyTop else ShortBodyTop

    val width get() = Right - Left

    fun newPage() {
        bodies += mutableListOf<DocItem>()
        y = ShortBodyTop
    }

    /** A new page unless [h] points still fit (never on a page with nothing on it yet). */
    private fun room(h: Float) {
        if (y + h > bottom && y > top) newPage()
    }

    fun gap(h: Float) {
        y += h
    }

    /** A section's name (small, spaced), with [extra] on the right, kept on one page with the first [keep] points under it. */
    fun section(label: String, extra: String? = null, keep: Float = 60f) {
        room(22f + keep)
        page += DocItem.Text(Left, y + 10f, label.uppercase(), 8f, bold = true, color = DocMuted, spacing = 1.2f)
        extra?.let { page += DocItem.Text(Right, y + 10f, it, 8.5f, color = DocMuted, align = DocAlign.RIGHT) }
        page += DocItem.Line(Left, y + 16f, Right, y + 16f, width = 0.6f)
        y += 26f
    }

    /** Figures in boxes side by side. */
    fun figures(list: List<DocFigure>) {
        if (list.isEmpty()) return
        val h = 62f
        room(h)
        val gapX = 10f
        val w = (width - gapX * (list.size - 1)) / list.size
        list.forEachIndexed { i, f ->
            val x = Left + i * (w + gapX)
            page += DocItem.Box(x, y, w, h, fill = PageCanvas, stroke = DocLine, radius = 8f)
            page += DocItem.Text(x + 12f, y + 18f, fit(f.label, 8.5f, false, w - 24f), 8.5f, color = DocMuted)
            page += DocItem.Text(x + 12f, y + 38f, fit(f.value, 14f, true, w - 24f), 14f, bold = true)
            f.note?.let {
                page += DocItem.Text(x + 12f, y + 53f, fit(it, 7.5f, f.tone != 0, w - 24f), 7.5f, bold = f.tone != 0,
                    color = when { f.tone > 0 -> DocUp; f.tone < 0 -> DocDown; else -> DocMuted })
            }
        }
        y += h
    }

    /** A drawing [h] points high across the page; [draw] gets its top. */
    fun drawing(h: Float, draw: MutableList<DocItem>.(top: Float) -> Unit) {
        room(h)
        page.draw(y)
        y += h
    }

    /** Words, wrapped to the page. */
    fun text(value: String, size: Float = 9.5f, color: Color = DocInk, bold: Boolean = false) {
        wrapText(value, size, width).forEach { line ->
            room(size + 6f)
            page += DocItem.Text(Left, y + size, line, size, bold = bold, color = color)
            y += size + 6f
        }
    }

    /**
     * A table: its column names over a dark line, its lines (going on over pages, with "… · continued" and the names again),
     * then its totals in a box on the right. [weights] = each column's share of the width (words get more by default).
     */
    fun table(name: String, columns: List<ReportColumn>, rows: List<List<Any>>, totals: List<ReportTotal> = emptyList(),
              weights: List<Float>? = null, extra: String? = null, empty: String = "Nothing here.") {
        val rowHeight = 21f
        val shares = weights ?: columns.mapIndexed { i, c -> if (c.kind == ColumnKind.TEXT) (if (i == 0) 1.6f else 1.4f) else 1f }
        val sum = shares.sum()
        val widths = shares.map { width * it / sum }
        val starts = widths.runningFold(Left) { acc, w -> acc + w }
        // Figures sit at their column's right edge, a little in from the next column's words (the last one at the page's edge).
        fun rightOf(i: Int) = starts[i] + widths[i] - if (i == columns.lastIndex) 0f else 10f
        val totalsHeight = 16f + totals.size * 16f
        fun heads() {
            columns.forEachIndexed { i, c ->
                val numeric = c.kind != ColumnKind.TEXT
                val label = (if (c.kind == ColumnKind.MONEY) "${c.name} (${info.currency})" else c.name).uppercase()
                // Column names get smaller (down to 6 pt) before they're cut.
                val room = widths[i] - if (i == columns.lastIndex) 0f else 8f
                var size = 7.5f
                while (size > 6f && textWidth(label, size, bold = true, spacing = 0.5f) > room) size -= 0.25f
                page += DocItem.Text(if (numeric) rightOf(i) else starts[i], y + 9f, fit(label, size, true, room), size,
                    bold = true, color = DocMuted, align = if (numeric) DocAlign.RIGHT else DocAlign.LEFT, spacing = 0.5f)
            }
            page += DocItem.Line(Left, y + 15f, Right, y + 15f, color = DocInk, width = 1.2f)
            y += 15f
        }
        section(name, extra, keep = 15f + rowHeight * rows.size.coerceIn(1, 3))
        heads()
        if (rows.isEmpty()) {
            page += DocItem.Text(Left, y + 15f, empty, 9.5f, color = DocMuted)
            y += rowHeight
        }
        rows.forEachIndexed { index, row ->
            // The totals never sit alone on a page: the last two lines go over with them when they don't fit.
            val left = rows.size - index
            val totalsAlone = totals.isNotEmpty() && left <= 2 && y + left * rowHeight + 10f + totalsHeight > bottom && y > top + 60f
            if (y + rowHeight > bottom || totalsAlone) {
                newPage()
                section("$name · continued", keep = 15f + rowHeight)
                heads()
            }
            row.forEachIndexed { i, cell ->
                val c = columns.getOrNull(i) ?: return@forEachIndexed
                val numeric = c.kind != ColumnKind.TEXT
                // Words get smaller (down to 7 pt) before they're cut, so an order number always shows whole (user, 2026-11-15).
                val full = cellText(c, cell)
                val room = widths[i] - 12f
                var size = 9.5f
                while (size > 7f && textWidth(full, size) > room) size -= 0.25f
                page += DocItem.Text(if (numeric) rightOf(i) else starts[i], y + 14f, fit(full, size, false, room), size,
                    align = if (numeric) DocAlign.RIGHT else DocAlign.LEFT)
            }
            page += DocItem.Line(Left, y + rowHeight, Right, y + rowHeight, width = 0.6f)
            y += rowHeight
        }
        if (totals.isNotEmpty()) {
            val h = totalsHeight
            y += 10f
            room(h)
            val boxW = 240f
            page += DocItem.Box(Right - boxW, y, boxW, h, fill = PageCanvas, stroke = DocLine, radius = 8f)
            totals.forEachIndexed { i, t ->
                val ty = y + 19f + i * 16f
                page += DocItem.Text(Right - boxW + 14f, ty, t.label, 9f, color = DocMuted)
                page += DocItem.Text(Right - 14f, ty, if (t.money) moneyText(t.value, info.currency) else countText(t.value), if (i == 0) 12f else 10f,
                    bold = true, align = DocAlign.RIGHT)
            }
            y += h
        }
    }

    /**
     * What something adds up to, like an invoice's foot: a box on the right with a small [title], its [lines] (name and
     * value) and, under a line, the [total] in bold green.
     */
    fun totalsBox(title: String, lines: List<Pair<String, String>>, total: Pair<String, String>) {
        val h = 34f + lines.size * 16f + 30f
        y += 12f
        room(h)
        val boxW = 260f
        val x = Right - boxW
        page += DocItem.Box(x, y, boxW, h, fill = PageCanvas, stroke = DocLine, radius = 8f)
        page += DocItem.Text(x + 14f, y + 18f, title.uppercase(), 7.5f, bold = true, color = DocMuted, spacing = 1f)
        lines.forEachIndexed { i, (label, value) ->
            val ty = y + 36f + i * 16f
            page += DocItem.Text(x + 14f, ty, label, 9f, color = DocMuted)
            page += DocItem.Text(Right - 14f, ty, value, 9.5f, bold = true, align = DocAlign.RIGHT)
        }
        val lineY = y + 28f + lines.size * 16f
        page += DocItem.Line(x + 14f, lineY, Right - 14f, lineY, width = 0.8f)
        page += DocItem.Text(x + 14f, lineY + 19f, total.first, 10f, bold = true, color = PageGreen)
        page += DocItem.Text(Right - 14f, lineY + 20f, total.second, 13.5f, bold = true, color = PageGreen, align = DocAlign.RIGHT)
        y += h
    }

    private fun cellText(c: ReportColumn, cell: Any): String = when (c.kind) {
        ColumnKind.MONEY -> moneyText(cell as Long, info.currency)
        ColumnKind.COUNT -> countText(cell as Long)
        else -> cell.toString()
    }

    /** The pages, each with its header (full on the first, the slim band after) and "Page 2 of 4" at the foot. */
    fun pages(): List<DocPage> = bodies.mapIndexed { i, body ->
        DocPage((if (i == 0) header(title, dates, compare, info, kind) else shortHeader(title, dates, info)) + body + footer(note, i + 1, bodies.size))
    }
}

// ---- On screen ----

/** One page as it will be printed: drawn from the same items as the PDF, on a white sheet with a soft shadow. */
@Composable
internal fun DocPageView(page: DocPage, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(page.width / page.height).shadow(3.dp, RoundedCornerShape(2.dp)).background(Color.White)) {
        val s = size.width / page.width
        page.items.forEach { item ->
            when (item) {
                is DocItem.Box -> {
                    val topLeft = Offset(item.x * s, item.y * s)
                    val boxSize = Size(item.w * s, item.h * s)
                    val corner = CornerRadius(item.radius * s)
                    item.fill?.let { drawRoundRect(it, topLeft, boxSize, corner) }
                    item.stroke?.let { drawRoundRect(it, topLeft, boxSize, corner, style = Stroke(item.strokeWidth * s)) }
                }
                is DocItem.Line -> drawLine(item.color, Offset(item.x1 * s, item.y1 * s), Offset(item.x2 * s, item.y2 * s), item.width * s)
                is DocItem.Text -> {
                    val style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal,
                        fontSize = (item.size * s / density).sp, letterSpacing = (item.spacing * s / density).sp, color = item.color)
                    val layout = measurer.measure(item.text, style, softWrap = false, maxLines = 1)
                    val x = when (item.align) {
                        DocAlign.LEFT -> item.x * s
                        DocAlign.RIGHT -> item.x * s - layout.size.width
                        DocAlign.CENTER -> item.x * s - layout.size.width / 2f
                    }
                    drawText(layout, topLeft = Offset(x, item.y * s - layout.firstBaseline))
                }
                is DocItem.Picture -> drawImage(item.image, dstOffset = IntOffset((item.x * s).toInt(), (item.y * s).toInt()),
                    dstSize = IntSize((item.w * s).toInt(), (item.h * s).toInt()))
            }
        }
    }
}

// ---- Saving a card ----

/** A card's report and its picture, waiting in the export window. */
class CardExport(val report: CardReport, val chart: ImageBitmap?)

/**
 * A card's export window (user, 2026-11-15): PDF or CSV; for a PDF the person picks the chart page, the table page or both,
 * and sees the pages as they'll be; Print sends those pages to the printer (the computer), Download asks where to save.
 */
@Composable
internal fun ReportExportDialog(export: CardExport, info: ReportInfo, onClose: () -> Unit, onResult: (String) -> Unit) =
    com.saporini.mobile_desktop.core.money.RealMoney { ReportExportDialogRealMoney(export = export, info = info, onClose = onClose, onResult = onResult) }

@Composable
private fun ReportExportDialogRealMoney(export: CardExport, info: ReportInfo, onClose: () -> Unit, onResult: (String) -> Unit) {
    val report = export.report
    val scope = rememberCoroutineScope()
    var pdf by remember(export) { mutableStateOf(true) }
    var withChart by remember(export) { mutableStateOf(export.chart != null) }
    var withTable by remember(export) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    val documentPrinter = com.saporini.mobile_desktop.core.printing.rememberDocumentPrinterPicker()
    val pages = remember(export, withChart, withTable) { reportPages(report, info, export.chart, withChart, withTable) }
    val fileName = "${report.stem}-${report.fileDates ?: info.fileDates}"
    val saver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file ->
        if (file == null) return@rememberFileSaverLauncher
        scope.launch {
            busy = true
            val result = runCatching {
                if (pdf) file.write(pdfDocument(pages)) else file.writeString(report.csv(info.currency))
                // A saved PDF opens in the computer's viewer at once (user, 2026-11-15: "when I download it opens the PDFs").
                if (pdf) com.saporini.mobile_desktop.core.files.openInSystem(file.absolutePath())
                "Saved to ${file.absolutePath()}"
            }.getOrElse { "Couldn't save the report: ${it.message ?: "unknown problem"}" }
            busy = false
            onResult(result)
            onClose()
        }
    }
    AppDialog(
        title = report.title,
        onDismiss = onClose,
        subtitle = listOfNotNull(report.dates ?: info.period, if (pdf) "${pages.size} ${if (pages.size == 1) "page" else "pages"}, A4" else "spreadsheet")
            .joinToString(" · "),
        busy = busy || printing,
        maxWidth = 860.dp,
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            // Printing has its own flag: only Print shows it's working (user, 2026-11-15: Download looked busy while printing).
            if (canPrintTables) KitButton("Print", {
                scope.launch {
                    // A document: one of the computer's printers, chosen now (user, 2026-11-27).
                    val target = documentPrinter.choose() ?: return@launch
                    printing = true
                    val outcome = com.saporini.mobile_desktop.core.printing.printDocumentOn(target, report.title,
                        if (pdf) pages else reportPages(report, info, null, withChart = false, withTable = true))
                    printing = false
                    printMessage(outcome)?.let(onResult)
                }
            }, icon = Icons.Outlined.Print, style = ButtonStyle.PRIMARY_OUTLINE, enabled = !busy && !printing, loading = printing)
            KitButton(if (pdf) "Download PDF" else "Download CSV", { saver.launch(fileName, if (pdf) "pdf" else "csv") },
                icon = Icons.Outlined.Download, enabled = !busy && !printing, loading = busy)
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.saporini.mobile_desktop.core.components.OverviewTabs(listOf("PDF", "CSV"), if (pdf) "PDF" else "CSV", { pdf = it == "PDF" },
                Modifier.width(170.dp))
            if (pdf) {
                // What goes in the PDF: the chart, the table, or both (one always stays).
                if (export.chart != null) PageChoice("Chart", withChart) { withChart = !withChart || !withTable; if (!withChart) withTable = true }
                PageChoice("Table with totals", withTable) { withTable = !withTable || !withChart; if (!withTable) withChart = export.chart != null }
            } else Text("The table as a spreadsheet, for Excel or Google Sheets", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
        if (pdf) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pages.forEach { DocPageView(it, Modifier.fillMaxWidth(0.82f)) }
            }
        } else {
            val header = report.columns.map { if (it.kind == ColumnKind.MONEY) "${it.name} (${info.currency})" else it.name }
            PreviewTable(header, report.rows.map { row -> row.mapIndexed { i, cell -> if (report.columns.getOrNull(i)?.kind == ColumnKind.MONEY) csvMoney(cell as Long) else cell.toString() } })
        }
    }
}

/** A tick chip choosing a page of the PDF. */
@Composable
private fun PageChoice(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.height(32.dp).clip(shape).background(if (on) Kit.GreenSoft else Color.White).border(1.dp, if (on) Kit.Green else Kit.Border, shape)
        .clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Kit.Green else Color.White).border(1.dp, if (on) Kit.Green else Kit.Border, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = Color.White)
        }
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = if (on) Kit.Green else Kit.Ink)
    }
}

// ---- Every card of a page at once ----

/** A card's way to make its report and take its picture, for the page's Download. */
class CardEntry(val report: () -> CardReport, val picture: (suspend () -> ImageBitmap?)?)

/** The cards on the page now, by file name start (they come and go with the page). */
class CardRegistry { val cards = mutableStateMapOf<String, CardEntry>() }

internal val LocalCardRegistry = staticCompositionLocalOf { CardRegistry() }

/** A card adds itself to the page's list while it's on screen. */
@Composable
internal fun RegisterCard(stem: String, report: () -> CardReport) {
    val registry = LocalCardRegistry.current
    val picture = LocalPanelPicture.current
    val latest = rememberUpdatedState(report)
    val latestPicture = rememberUpdatedState(picture)
    DisposableEffect(stem, registry) {
        val takePicture: suspend () -> ImageBitmap? = { latestPicture.value?.invoke() }
        registry.cards[stem] = CardEntry({ latest.value() }, takePicture)
        onDispose { registry.cards.remove(stem) }
    }
}

/** One card's report ready for the page's Download: its table, its picture and its A4 pages. */
class PageExportItem(val stem: String, val report: CardReport, val chart: ImageBitmap?, val pages: List<DocPage>)

/**
 * The page's Download (a square in the header): takes every card's picture, then shows them all first (user, 2026-11-15:
 * "give the preview when downloading all") in [PageExportDialog], which saves them into a folder.
 */
@Composable
internal fun PageExportButton(info: ReportInfo, size: Dp, onResult: (String) -> Unit) {
    if (!LocalStatsDownloads.current) return
    val registry = LocalCardRegistry.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<PageExportItem>?>(null) }
    items?.let { PageExportDialog(it, info, onClose = { items = null }, onResult = onResult) }
    Surface(onClick = {
        if (busy) return@Surface
        scope.launch {
            busy = true
            items = registry.cards.entries.sortedBy { it.key }.map { (stem, entry) ->
                val report = entry.report()
                val chart = entry.picture?.invoke()
                PageExportItem(stem, report, chart, reportPages(report, info, chart, withChart = chart != null, withTable = true))
            }
            busy = false
        }
    }, modifier = Modifier.size(size), shape = RoundedCornerShape(8.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Download, "Download every card of this page", Modifier.size(19.dp), tint = Kit.Ink)
        }
    }
}

/**
 * Every card of the page before saving (user, 2026-11-15). Like a card's own window, a PDF | CSV switch (user: "PDF vs CSV
 * also here, as in the card's"): PDF shows every report with all its A4 pages (400 dp, two a row), every page ticked, a click
 * takes a page out (faded) or back, and a report's PDF holds its ticked pages; CSV shows every report's table with a tick to
 * leave the report out. Reports with nothing ticked are left out. Print all prints what's ticked (the computer); Download
 * all asks for a folder and saves into a folder of its own ("Sales 2026-11-01 to 2026-11-14").
 */
@Composable
internal fun PageExportDialog(items: List<PageExportItem>, info: ReportInfo, onClose: () -> Unit, onResult: (String) -> Unit,
                              onePdf: String? = null, pdfHint: String = "Each report's chart and table, as A4 pages") =
    com.saporini.mobile_desktop.core.money.RealMoney { PageExportDialogRealMoney(items = items, info = info, onClose = onClose, onResult = onResult, onePdf = onePdf, pdfHint = pdfHint) }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PageExportDialogRealMoney(items: List<PageExportItem>, info: ReportInfo, onClose: () -> Unit, onResult: (String) -> Unit,
                              onePdf: String? = null, pdfHint: String = "Each report's chart and table, as A4 pages") {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    val documentPrinter = com.saporini.mobile_desktop.core.printing.rememberDocumentPrinterPicker()
    var pdf by remember { mutableStateOf(true) }
    // The PDF pages taken out ("stem#index") and the reports left out of the CSV files.
    var leftPages by remember(items) { mutableStateOf(emptySet<String>()) }
    var leftTables by remember(items) { mutableStateOf(emptySet<String>()) }
    fun key(item: PageExportItem, index: Int) = "${item.stem}#$index"
    fun pagesOf(item: PageExportItem) = item.pages.filterIndexed { i, _ -> key(item, i) !in leftPages }
    fun kept(item: PageExportItem) = if (pdf) pagesOf(item).isNotEmpty() else item.stem !in leftTables
    val folderName = "${info.page} ${info.fileDates.replace("-to-", " to ")}"
    val pick = rememberFolderPicker { folder ->
        if (folder == null) return@rememberFolderPicker
        scope.launch {
            busy = true
            val result = runCatching {
                val target = folder / folderName
                target.createDirectories()
                val saving = items.filter { kept(it) }
                // One report made of many parts (Sales details): every ticked page in one PDF, in order (user, 2026-11-15).
                if (pdf && onePdf != null) {
                    val file = folder / "$onePdf.pdf"
                    file.write(pdfDocument(saving.flatMap { pagesOf(it) }))
                    com.saporini.mobile_desktop.core.files.openInSystem(file.absolutePath())
                    return@runCatching "Saved ${saving.sumOf { pagesOf(it).size }} pages to ${file.absolutePath()}"
                }
                saving.forEach { item ->
                    if (pdf) (target / "${item.stem}.pdf").write(pdfDocument(pagesOf(item)))
                    else (target / "${item.stem}.csv").writeString(item.report.csv(info.currency))
                }
                // The PDFs open at once (user, 2026-11-15): one report in the viewer, several as their folder, so the screen
                // isn't covered by a window per report.
                if (pdf) {
                    val only = saving.singleOrNull()
                    com.saporini.mobile_desktop.core.files.openInSystem(if (only != null) (target / "${only.stem}.pdf").absolutePath() else target.absolutePath())
                }
                "Saved ${saving.size} ${if (saving.size == 1) "report" else "reports"} to ${target.absolutePath()}"
            }.getOrElse { "Couldn't save the reports: ${it.message ?: "unknown problem"}" }
            busy = false
            onResult(result)
            onClose()
        }
    }
    val files = items.count { kept(it) }
    val ticked = items.sumOf { pagesOf(it).size }
    val allPages = items.sumOf { it.pages.size }
    AppDialog(
        title = "Download all · ${info.page}",
        onDismiss = onClose,
        subtitle = (if (pdf) "$ticked of $allPages pages" else "$files of ${items.size} tables") + " · ${info.period} · " +
            (if (pdf && onePdf != null) "one PDF, \"$onePdf.pdf\""
            else "$files ${if (pdf) "PDF" else "CSV"} ${if (files == 1) "file" else "files"} in a folder \"$folderName\""),
        busy = busy || printing,
        maxWidth = 960.dp,
        buttons = {
            KitButton("Close", onClose, style = ButtonStyle.SECONDARY)
            // Printing has its own flag: only Print all shows it's working (user, 2026-11-15: Download looked busy while printing).
            if (canPrintTables) KitButton("Print all", {
                scope.launch {
                    val target = documentPrinter.choose() ?: return@launch
                    printing = true
                    val pages = if (pdf) items.flatMap { pagesOf(it) }
                        else items.filter { kept(it) }.flatMap { reportPages(it.report, info, null, withChart = false, withTable = true) }
                    val outcome = com.saporini.mobile_desktop.core.printing.printDocumentOn(target, "${info.page} · ${info.period}", pages)
                    printing = false
                    printMessage(outcome)?.let(onResult)
                }
            }, icon = Icons.Outlined.Print, style = ButtonStyle.PRIMARY_OUTLINE, enabled = !busy && !printing && files > 0, loading = printing)
            KitButton(if (pdf) "Download all PDF" else "Download all CSV", pick, icon = Icons.Outlined.Download, enabled = !busy && !printing && files > 0,
                loading = busy)
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.saporini.mobile_desktop.core.components.OverviewTabs(listOf("PDF", "CSV"), if (pdf) "PDF" else "CSV", { pdf = it == "PDF" },
                Modifier.width(170.dp))
            Text(if (pdf) pdfHint else "Each report's table as a spreadsheet", fontFamily = Inter(),
                fontSize = 12.sp, color = Kit.Muted)
            Box(Modifier.weight(1f))
            val anyLeft = if (pdf) leftPages.isNotEmpty() else leftTables.isNotEmpty()
            Text(if (!anyLeft) (if (pdf) "Click a page to leave it out" else "Click a tick to leave a table out") else "Tick all",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = anyLeft) { if (pdf) leftPages = emptySet() else leftTables = emptySet() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = if (anyLeft) Kit.Green else Kit.Muted)
        }
        items.forEach { item ->
            val on = kept(item)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.report.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) Kit.Ink else Kit.Faint)
                        Text(listOfNotNull(item.report.dates ?: info.period,
                            item.report.totals.firstOrNull()?.let { t -> "${t.label} ${if (t.money) moneyText(t.value, info.currency) else countText(t.value)}" })
                            .joinToString(" · "), fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted)
                    }
                    val kept = pagesOf(item).size
                    Text(when {
                        !on -> "Left out"
                        pdf && onePdf != null -> "$kept ${if (kept == 1) "page" else "pages"}"
                        pdf -> "${item.stem}.pdf · $kept ${if (kept == 1) "page" else "pages"}"
                        else -> "${item.stem}.csv · ${item.report.rows.size} lines"
                    }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = if (on) Kit.Green else Kit.Faint)
                    if (!pdf) RoundTick(on) { leftTables = if (on) leftTables + item.stem else leftTables - item.stem }
                }
                if (pdf) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item.pages.forEachIndexed { i, page ->
                            val pageOn = key(item, i) !in leftPages
                            Box(Modifier.width(400.dp).clip(RoundedCornerShape(4.dp)).clickable {
                                leftPages = if (pageOn) leftPages + key(item, i) else leftPages - key(item, i)
                            }) {
                                DocPageView(page, Modifier.fillMaxWidth().alpha(if (pageOn) 1f else 0.35f))
                                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { RoundTick(pageOn, null) }
                            }
                        }
                    }
                } else {
                    // The table as the spreadsheet will hold it, faded when it's left out.
                    val header = item.report.columns.map { if (it.kind == ColumnKind.MONEY) "${it.name} (${info.currency})" else it.name }
                    Box(Modifier.fillMaxWidth().alpha(if (on) 1f else 0.4f)) {
                        PreviewTable(header, item.report.rows.map { row ->
                            row.mapIndexed { i, cell -> if (item.report.columns.getOrNull(i)?.kind == ColumnKind.MONEY) csvMoney(cell as Long) else cell.toString() }
                        })
                    }
                }
            }
        }
    }
}

/** A round tick: green with ✓ when on, white when off; clickable when [onClick] is given. */
@Composable
private fun RoundTick(on: Boolean, onClick: (() -> Unit)?) {
    Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) Kit.Green else Color.White)
        .border(1.5.dp, if (on) Kit.Green else Kit.Border, androidx.compose.foundation.shape.CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Outlined.Check, if (on) "Ticked" else "Not ticked", Modifier.size(15.dp), tint = Color.White)
    }
}

/** What to tell after Print: nothing when it printed. */
private fun printMessage(outcome: com.saporini.mobile_desktop.core.files.PrintOutcome): String? = when (outcome) {
    com.saporini.mobile_desktop.core.files.PrintOutcome.PRINTED -> null
    com.saporini.mobile_desktop.core.files.PrintOutcome.OPENED_IN_VIEWER -> "No printer is set up on this computer: the pages opened in your PDF viewer, print them from there"
    com.saporini.mobile_desktop.core.files.PrintOutcome.NOT_PRINTED -> "Couldn't print: printing was cancelled or isn't possible here"
}
