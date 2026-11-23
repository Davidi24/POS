package com.saporini.mobile_desktop.core.files

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.RoundRectangle2D
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob

actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray =
    Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes ?: error("Couldn't make the picture")

actual fun encodePng(image: ImageBitmap): ByteArray =
    Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("Couldn't make the picture")

/** The computer prints the pages with Java's printing (the system print window, A4), drawing them as the PDF does. */
actual suspend fun printDocument(title: String, pages: List<DocPage>): PrintOutcome = withContext(Dispatchers.IO) {
    // No printer set up (common on a fresh computer): open the PDF in the system's viewer, which has its own Print.
    if (javax.print.PrintServiceLookup.lookupPrintServices(null, null).isEmpty()) {
        return@withContext runCatching {
            val file = java.io.File.createTempFile(title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "report" } + "-", ".pdf")
            file.deleteOnExit()
            file.writeBytes(pdfDocument(pages))
            java.awt.Desktop.getDesktop().open(file)
            PrintOutcome.OPENED_IN_VIEWER
        }.getOrDefault(PrintOutcome.NOT_PRINTED)
    }
    val printed = runCatching {
        val pictures = pages.flatMap { it.items.filterIsInstance<DocItem.Picture>() }.associateWith { onWhite(it.image).toAwtImage() }
        val job = PrinterJob.getPrinterJob()
        job.jobName = title
        val paper = Paper().apply {
            setSize(A4_WIDTH.toDouble(), A4_HEIGHT.toDouble())
            setImageableArea(0.0, 0.0, A4_WIDTH.toDouble(), A4_HEIGHT.toDouble())
        }
        val format = job.defaultPage().apply { this.paper = paper; orientation = PageFormat.PORTRAIT }
        job.setPrintable({ graphics, pf, index ->
            val page = pages.getOrNull(index) ?: return@setPrintable Printable.NO_SUCH_PAGE
            val g = graphics as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            // The printer's own margins: the page is scaled into what it can reach.
            g.translate(pf.imageableX, pf.imageableY)
            val fit = minOf(pf.imageableWidth / page.width, pf.imageableHeight / page.height, 1.0)
            g.scale(fit, fit)
            page.items.forEach { item ->
                when (item) {
                    is DocItem.Box -> {
                        val shape = RoundRectangle2D.Float(item.x, item.y, item.w, item.h, item.radius * 2, item.radius * 2)
                        item.fill?.let { g.color = java.awt.Color(it.toArgb(), true); g.fill(shape) }
                        item.stroke?.let { g.color = java.awt.Color(it.toArgb(), true); g.stroke = BasicStroke(item.strokeWidth); g.draw(shape) }
                    }
                    is DocItem.Line -> {
                        g.color = java.awt.Color(item.color.toArgb(), true)
                        g.stroke = BasicStroke(item.width)
                        g.draw(java.awt.geom.Line2D.Float(item.x1, item.y1, item.x2, item.y2))
                    }
                    is DocItem.Text -> {
                        val base = Font("SansSerif", if (item.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(item.size)
                        g.font = if (item.spacing != 0f) base.deriveFont(mapOf(TextAttribute.TRACKING to item.spacing / item.size)) else base
                        g.color = java.awt.Color(item.color.toArgb(), true)
                        val width = g.fontMetrics.stringWidth(item.text).toFloat()
                        val x = when (item.align) {
                            DocAlign.LEFT -> item.x
                            DocAlign.RIGHT -> item.x - width
                            DocAlign.CENTER -> item.x - width / 2
                        }
                        g.drawString(item.text, x, item.y)
                    }
                    is DocItem.Picture -> g.drawImage(pictures[item], item.x.toInt(), item.y.toInt(), item.w.toInt(), item.h.toInt(), null)
                }
            }
            Printable.PAGE_EXISTS
        }, format)
        if (!job.printDialog()) return@runCatching false
        job.print()
        true
    }.getOrDefault(false)
    if (printed) PrintOutcome.PRINTED else PrintOutcome.NOT_PRINTED
}
