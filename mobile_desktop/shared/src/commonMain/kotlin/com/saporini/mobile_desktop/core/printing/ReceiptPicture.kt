package com.saporini.mobile_desktop.core.printing

import com.saporini.mobile_desktop.core.ui.barcodeBars
import com.saporini.mobile_desktop.core.ui.qrModules
import com.saporini.mobile_desktop.platform.Backdrop

/** What a receipt is written on: the printer's own commands ([EscPos]) or one picture ([ReceiptPicture]). */
interface ReceiptSink {
    val columnsNow: Int
    val dots: Int
    fun align(align: EscPos.Align): ReceiptSink
    fun bold(on: Boolean): ReceiptSink
    fun size(width: Int = 1, height: Int = 1): ReceiptSink
    fun line(text: String = ""): ReceiptSink
    fun rule(char: Char = '-'): ReceiptSink
    fun columns(left: String, right: String): ReceiptSink
    fun feed(lines: Int): ReceiptSink
    fun image(image: MonoImage): ReceiptSink
    fun barcode(data: String): ReceiptSink
    fun qr(data: String, module: Int = 6): ReceiptSink
}

/**
 * A receipt drawn as one picture (decided 2026-11-28: "print it as one picture"), for designs with pictures behind the words:
 * the same lines, sizes and alignment as the printer's own letters ([textDots] draws them), then the pictures behind under
 * everything, sent as one image. Slower to print and a little less sharp, but it looks like the designer.
 */
class ReceiptPicture(paperWidthMm: Int) : ReceiptSink {
    private val columns: Int = if (paperWidthMm in 1..60) 32 else 48
    override val dots: Int = if (paperWidthMm in 1..60) 384 else 576
    private val cellWidth = dots / columns
    private val cellHeight = 24
    private var widthScale = 1
    private var heightScale = 1
    private var bold = false
    private var align = EscPos.Align.LEFT
    private var y = 0
    private class Placed(val x: Int, val y: Int, val image: MonoImage)
    private val placed = mutableListOf<Placed>()

    override val columnsNow: Int get() = columns / widthScale

    override fun align(align: EscPos.Align) = apply { this.align = align }
    override fun bold(on: Boolean) = apply { bold = on }
    override fun size(width: Int, height: Int) = apply { widthScale = width.coerceIn(1, 4); heightScale = height.coerceIn(1, 4) }

    private fun place(image: MonoImage) {
        val x = when (align) {
            EscPos.Align.LEFT -> 0
            EscPos.Align.CENTER -> (dots - image.width) / 2
            EscPos.Align.RIGHT -> dots - image.width
        }.coerceAtLeast(0)
        placed += Placed(x, y, image)
        y += image.height
    }

    override fun line(text: String) = apply {
        if (text.isEmpty()) { y += cellHeight * heightScale; return@apply }
        EscPos.wrap(text, columnsNow).forEach { part ->
            if (part.isBlank()) y += cellHeight * heightScale
            else place(textDots(part, cellWidth * widthScale, cellHeight * heightScale, bold))
        }
    }

    override fun rule(char: Char) = apply { line(char.toString().repeat(columnsNow)) }

    override fun columns(left: String, right: String) = apply {
        val room = columnsNow - right.length - 1
        if (room < 4) { line(left); val was = align; align(EscPos.Align.RIGHT).line(right).align(was); return@apply }
        val lines = EscPos.wrap(left, room)
        lines.forEachIndexed { index, part -> if (index == lines.lastIndex) line(part.padEnd(room) + " " + right) else line(part) }
    }

    override fun feed(lines: Int) = apply { y += cellHeight * lines.coerceIn(0, 50) }

    override fun image(image: MonoImage) = apply { place(image) }

    override fun barcode(data: String) = apply {
        val clean = data.filter { it.code in 32..126 }.take(40)
        val bars = barcodeBars(clean) ?: return@apply
        val scale = (dots * 0.8 / bars.size).toInt().coerceIn(1, 3)
        val width = bars.size * scale
        val height = 80
        val bits = BooleanArray(width * height) { i -> bars[(i % width) / scale] }
        val was = align
        align(EscPos.Align.CENTER)
        place(MonoImage(width, height, bits))
        line(clean)
        align(was)
    }

    override fun qr(data: String, module: Int) = apply {
        val modules = qrModules(data) ?: return@apply
        val scale = module.coerceIn(1, 16)
        val side = modules.size * scale
        val bits = BooleanArray(side * side) { i -> modules[(i / side) / scale][(i % side) / scale] }
        val was = align
        align(EscPos.Align.CENTER)
        place(MonoImage(side, side, bits))
        align(was)
    }

    /** The receipt as one picture: [backdrops] under everything, at their place on the paper ([pictures] gives them). */
    fun render(backdrops: List<Backdrop>, pictures: (url: String, width: Int, opacity: Float) -> MonoImage?): MonoImage {
        val height = y.coerceAtLeast(1)
        val bits = BooleanArray(dots * height)
        fun paste(image: MonoImage, left: Int, top: Int) {
            for (row in 0 until image.height) {
                val py = top + row
                if (py !in 0 until height) continue
                for (col in 0 until image.width) {
                    val px = left + col
                    if (px in 0 until dots && image.black(col, row)) bits[py * dots + px] = true
                }
            }
        }
        backdrops.forEach { backdrop ->
            val width = (dots * backdrop.width.coerceIn(0.05f, 1f)).toInt()
            val image = pictures(backdrop.imageUrl, width, backdrop.opacity) ?: return@forEach
            paste(image, (backdrop.x * dots).toInt() - image.width / 2, (backdrop.y * height).toInt() - image.height / 2)
        }
        placed.forEach { paste(it.image, it.x, it.y) }
        return MonoImage(dots, height, bits)
    }
}

/** [text] in the printer's look (fixed-width letters, each [cellWidth] × [cellHeight] dots), as dots: drawn by each platform. */
expect fun textDots(text: String, cellWidth: Int, cellHeight: Int, bold: Boolean): MonoImage
