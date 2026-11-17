package com.saporini.mobile_desktop.core.files

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

// Documents made on the device (user, 2026-11-15: "make it look like an invoice, A4"): pages described once as text, lines,
// boxes and pictures in points from the top left, then written as a PDF here (no PDF library needed), drawn by the
// printer (Java's printing on the computer) and drawn on screen for the preview, so all three look the same.

/** The picture as JPEG bytes (white where it was see-through). */
expect fun encodeJpeg(image: ImageBitmap, quality: Int = 92): ByteArray

/** What became of a print. */
enum class PrintOutcome { PRINTED, OPENED_IN_VIEWER, NOT_PRINTED }

/**
 * Prints the pages (A4): with the system's print window when the computer has a printer; with none set up, the pages open as
 * a PDF in the computer's own viewer, whose Print can still print or save them (OPENED_IN_VIEWER). NOT_PRINTED when cancelled
 * or on the phone.
 */
expect suspend fun printDocument(title: String, pages: List<DocPage>): PrintOutcome

/** A4 in points. */
const val A4_WIDTH = 595f
const val A4_HEIGHT = 842f

enum class DocAlign { LEFT, RIGHT, CENTER }

/** One thing on a page; every position is in points from the page's top left. */
sealed interface DocItem {
    /** [y] is the text's baseline; [spacing] adds room between letters (for spaced-out titles). */
    data class Text(val x: Float, val y: Float, val text: String, val size: Float, val bold: Boolean = false, val color: Color = DocInk,
        val align: DocAlign = DocAlign.LEFT, val spacing: Float = 0f) : DocItem
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float, val fill: Color? = null, val stroke: Color? = null,
        val strokeWidth: Float = 1f, val radius: Float = 0f) : DocItem
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Color = DocLine, val width: Float = 1f) : DocItem
    data class Picture(val x: Float, val y: Float, val w: Float, val h: Float, val image: ImageBitmap) : DocItem
}

class DocPage(val items: List<DocItem>, val width: Float = A4_WIDTH, val height: Float = A4_HEIGHT)

val DocInk = Color(0xFF1F2421)
val DocMuted = Color(0xFF6B726E)
val DocLine = Color(0xFFE3E6E1)

/** The picture laid on white, so rounded corners don't come out black in a JPEG. */
fun onWhite(image: ImageBitmap): ImageBitmap {
    val out = ImageBitmap(image.width, image.height)
    Canvas(out).apply {
        drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), Paint().apply { color = Color.White })
        drawImage(image, Offset.Zero, Paint())
    }
    return out
}

// ---- Measuring text (Helvetica, the PDF's standard font) ----

private const val PrintableAscii = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
private val HelveticaWidths = intArrayOf(
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556,
    278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667,
    611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833,
    556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
)
private val HelveticaBoldWidths = intArrayOf(
    278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556,
    333, 333, 584, 584, 584, 611, 975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667, 778, 722, 667,
    611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556, 333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889,
    611, 611, 611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584
)

/** How wide [text] is in points at [size] (Helvetica, or Helvetica-Bold), with [spacing] between letters. */
fun textWidth(text: String, size: Float, bold: Boolean = false, spacing: Float = 0f): Float {
    val table = if (bold) HelveticaBoldWidths else HelveticaWidths
    val units = text.sumOf { c ->
        val i = PrintableAscii.indexOf(c)
        when {
            i >= 0 -> table[i]
            c == '·' -> 278
            else -> 556
        }
    }
    return units * size / 1000f + spacing * (text.length - 1).coerceAtLeast(0)
}

/** [text] cut to lines that fit [width] at [size]. */
fun wrapText(text: String, size: Float, width: Float, bold: Boolean = false): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    text.split(' ').forEach { word ->
        val tryLine = if (line.isEmpty()) word else "$line $word"
        if (textWidth(tryLine, size, bold) <= width || line.isEmpty()) line = tryLine
        else { lines += line; line = word }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

// ---- Writing the PDF ----

/** The pages as a PDF: Helvetica and Helvetica-Bold (WinAnsi letters, so €, – and · are kept), pictures as JPEG. */
fun pdfDocument(pages: List<DocPage>): ByteArray {
    val out = PdfBytes()
    out.text("%PDF-1.4\n")
    val offsets = mutableMapOf<Int, Int>()
    fun obj(number: Int, body: () -> Unit) { offsets[number] = out.size; out.text("$number 0 obj\n"); body(); out.text("\nendobj\n") }
    // Object numbers: 1 catalog, 2 pages, 3 and 4 the fonts, then per page its page and contents, then the pictures.
    val pictures = pages.flatMap { page -> page.items.filterIsInstance<DocItem.Picture>() }
    val pictureNumbers = pictures.mapIndexed { i, p -> p to 5 + pages.size * 2 + i }.toMap()
    obj(1) { out.text("<< /Type /Catalog /Pages 2 0 R >>") }
    obj(2) { out.text("<< /Type /Pages /Kids [${pages.indices.joinToString(" ") { "${5 + it * 2} 0 R" }}] /Count ${pages.size} >>") }
    obj(3) { out.text("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>") }
    obj(4) { out.text("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>") }
    pages.forEachIndexed { index, page ->
        val pageNumber = 5 + index * 2
        val onPage = page.items.filterIsInstance<DocItem.Picture>()
        val xObjects = onPage.joinToString(" ") { "/Im${pictureNumbers.getValue(it)} ${pictureNumbers.getValue(it)} 0 R" }
        obj(pageNumber) {
            out.text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(page.width)} ${n(page.height)}] " +
                "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> /XObject << $xObjects >> >> /Contents ${pageNumber + 1} 0 R >>")
        }
        val content = winAnsi(pageContent(page, pictureNumbers))
        obj(pageNumber + 1) {
            out.text("<< /Length ${content.size} >>\nstream\n")
            out.bytes(content)
            out.text("endstream")
        }
    }
    pictures.forEach { picture ->
        val jpeg = encodeJpeg(onWhite(picture.image))
        obj(pictureNumbers.getValue(picture)) {
            out.text("<< /Type /XObject /Subtype /Image /Width ${picture.image.width} /Height ${picture.image.height} /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
            out.bytes(jpeg)
            out.text("\nendstream")
        }
    }
    val count = offsets.keys.max()
    val xref = out.size
    out.text("xref\n0 ${count + 1}\n0000000000 65535 f \n")
    (1..count).forEach { out.text("${(offsets[it] ?: 0).toString().padStart(10, '0')} 00000 n \n") }
    out.text("trailer\n<< /Size ${count + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
}

private fun n(v: Float): String {
    val r = kotlin.math.round(v * 100) / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

private fun rgb(c: Color) = "${n(c.red)} ${n(c.green)} ${n(c.blue)}"

/** The drawing commands of one page (PDF's y goes up from the bottom). */
private fun pageContent(page: DocPage, pictures: Map<DocItem.Picture, Int>): String = buildString {
    val h = page.height
    page.items.forEach { item ->
        when (item) {
            is DocItem.Box -> {
                val y = h - item.y - item.h
                append("q ")
                item.fill?.let { append("${rgb(it)} rg ") }
                item.stroke?.let { append("${rgb(it)} RG ${n(item.strokeWidth)} w ") }
                append(roundedRect(item.x, y, item.w, item.h, item.radius))
                append(when {
                    item.fill != null && item.stroke != null -> " B"
                    item.fill != null -> " f"
                    else -> " S"
                })
                append(" Q\n")
            }
            is DocItem.Line -> append("q ${rgb(item.color)} RG ${n(item.width)} w ${n(item.x1)} ${n(h - item.y1)} m ${n(item.x2)} ${n(h - item.y2)} l S Q\n")
            is DocItem.Text -> {
                val width = textWidth(item.text, item.size, item.bold, item.spacing)
                val x = when (item.align) {
                    DocAlign.LEFT -> item.x
                    DocAlign.RIGHT -> item.x - width
                    DocAlign.CENTER -> item.x - width / 2
                }
                append("BT ${rgb(item.color)} rg /${if (item.bold) "F2" else "F1"} ${n(item.size)} Tf ${n(item.spacing)} Tc ${n(x)} ${n(h - item.y)} Td (${pdfText(item.text)}) Tj ET\n")
            }
            is DocItem.Picture -> append("q ${n(item.w)} 0 0 ${n(item.h)} ${n(item.x)} ${n(h - item.y - item.h)} cm /Im${pictures.getValue(item)} Do Q\n")
        }
    }
}

/** A rectangle with round corners (Bézier curves), as a path. */
private fun roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    if (r <= 0f) return "${n(x)} ${n(y)} ${n(w)} ${n(h)} re"
    val k = 0.5523f * r
    return buildString {
        append("${n(x + r)} ${n(y)} m ")
        append("${n(x + w - r)} ${n(y)} l ")
        append("${n(x + w - r + k)} ${n(y)} ${n(x + w)} ${n(y + r - k)} ${n(x + w)} ${n(y + r)} c ")
        append("${n(x + w)} ${n(y + h - r)} l ")
        append("${n(x + w)} ${n(y + h - r + k)} ${n(x + w - r + k)} ${n(y + h)} ${n(x + w - r)} ${n(y + h)} c ")
        append("${n(x + r)} ${n(y + h)} l ")
        append("${n(x + r - k)} ${n(y + h)} ${n(x)} ${n(y + h - r + k)} ${n(x)} ${n(y + h - r)} c ")
        append("${n(x)} ${n(y + r)} l ")
        append("${n(x)} ${n(y + r - k)} ${n(x + r - k)} ${n(y)} ${n(x + r)} ${n(y)} c h")
    }
}

private class PdfBytes {
    private val parts = mutableListOf<ByteArray>()
    var size = 0
        private set
    fun text(s: String) = bytes(winAnsi(s))
    fun bytes(b: ByteArray) { parts += b; size += b.size }
    fun toByteArray(): ByteArray {
        val all = ByteArray(size)
        var at = 0
        parts.forEach { it.copyInto(all, at); at += it.size }
        return all
    }
}

/** Text for a PDF string: the brackets and backslash escaped. */
private fun pdfText(s: String) = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

/** Bytes in the PDF's WinAnsi letters (€ – · and the Latin-1 ones kept; anything else becomes ?). */
private fun winAnsi(s: String): ByteArray = ByteArray(s.length) { i ->
    when (val c = s[i]) {
        '€' -> 0x80.toByte()
        '–' -> 0x96.toByte()
        '—' -> 0x97.toByte()
        '’' -> 0x92.toByte()
        '“' -> 0x93.toByte()
        '”' -> 0x94.toByte()
        '•' -> 0x95.toByte()
        '…' -> 0x85.toByte()
        else -> if (c.code < 256) c.code.toByte() else '?'.code.toByte()
    }
}
