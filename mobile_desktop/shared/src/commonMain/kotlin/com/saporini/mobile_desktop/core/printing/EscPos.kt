package com.saporini.mobile_desktop.core.printing

/**
 * What receipt and kitchen printers understand (ESC/POS, the common language of thermal printers): text in sizes, bold,
 * alignment, lines, QR codes, barcodes, the cut and the cash drawer. A 58 mm paper fits 32 characters a line, 80 mm 48.
 * Text goes as Windows-1252 (code page 16), which has the letters of the four languages (ë, ç, ñ, à…) and €.
 */
class EscPos(paperWidthMm: Int) : ReceiptSink {

    enum class Align(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }

    /** Characters a line holds at normal size. */
    val columns: Int = if (paperWidthMm in 1..60) 32 else 48

    private var widthScale = 1
    private val out = ByteBuilder()

    init {
        raw(0x1B, 0x40)        // start fresh
        raw(0x1B, 0x74, 16)    // code page Windows-1252
    }

    /** Characters a line holds now (half when letters are double wide). */
    override val columnsNow: Int get() = columns / widthScale

    override fun align(align: Align) = apply { raw(0x1B, 0x61, align.code) }

    override fun bold(on: Boolean) = apply { raw(0x1B, 0x45, if (on) 1 else 0) }

    /** The second colour (red on two-colour printers; others print black). */
    fun red(on: Boolean) = apply { raw(0x1B, 0x72, if (on) 1 else 0) }

    /** Letter size, 1 to 4 times as wide and as tall. */
    override fun size(width: Int, height: Int) = apply {
        val w = width.coerceIn(1, 4)
        val h = height.coerceIn(1, 4)
        widthScale = w
        raw(0x1D, 0x21, ((w - 1) shl 4) or (h - 1))
    }

    fun text(text: String) = apply { out.add(encode(text)) }

    /** One line; longer text wraps at word breaks. */
    override fun line(text: String) = apply {
        if (text.isEmpty()) raw(0x0A) else wrap(text, columnsNow).forEach { text(it); raw(0x0A) }
    }

    /** A line across the paper made of [char] (dashes, dots, stars…). */
    override fun rule(char: Char) = apply { line(char.toString().repeat(columnsNow)) }

    /** [left] at the left and [right] at the right of one line ("2 x Pizza ......... 18.00"). */
    override fun columns(left: String, right: String) = apply {
        val room = columnsNow - right.length - 1
        if (room < 4) { line(left); align(Align.RIGHT).line(right).align(Align.LEFT); return@apply }
        val lines = wrap(left, room)
        lines.forEachIndexed { index, part ->
            if (index == lines.lastIndex) line(part.padEnd(room) + " " + right) else line(part)
        }
    }

    override fun feed(lines: Int) = apply { raw(0x1B, 0x64, lines.coerceIn(0, 255)) }

    /** Feeds the paper out and cuts it (a printer without a cutter just feeds). */
    fun cut() = apply { feed(4); raw(0x1D, 0x56, 66, 0) }

    /** Opens the cash drawer plugged into the printer. */
    fun openDrawer() = apply { raw(0x1B, 0x70, 0, 25, 250) }

    /** A QR code of [data], its dots [module] wide (1–16). */
    override fun qr(data: String, module: Int) = apply {
        val bytes = data.encodeToByteArray()
        raw(0x1D, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0)              // model 2
        raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x43, module.coerceIn(1, 16)) // size
        raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x45, 0x31)                  // error correction M
        val length = bytes.size + 3
        raw(0x1D, 0x28, 0x6B, length and 0xFF, (length shr 8) and 0xFF, 0x31, 0x50, 0x30)
        out.add(bytes)
        raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30)                  // print it
    }

    /** A Code 128 barcode with its digits under it. */
    override fun barcode(data: String) = apply {
        val clean = data.filter { it.code in 32..126 }.take(40)
        if (clean.isEmpty()) return@apply
        raw(0x1D, 0x68, 80)   // height
        raw(0x1D, 0x77, 2)    // bar width
        raw(0x1D, 0x48, 2)    // digits below
        val payload = "{B$clean".encodeToByteArray()
        raw(0x1D, 0x6B, 73, payload.size)
        out.add(payload)
        raw(0x0A)
    }

    /** A black-and-white picture ([image]), printed as dots, in bands the printers take. */
    override fun image(image: MonoImage) = apply {
        val widthBytes = (image.width + 7) / 8
        var top = 0
        while (top < image.height) {
            val rows = minOf(128, image.height - top)
            raw(0x1D, 0x76, 0x30, 0, widthBytes and 0xFF, (widthBytes shr 8) and 0xFF, rows and 0xFF, (rows shr 8) and 0xFF)
            val band = ByteArray(widthBytes * rows)
            for (y in 0 until rows) for (x in 0 until image.width) {
                if (image.black(x, top + y)) band[y * widthBytes + x / 8] = (band[y * widthBytes + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
            }
            out.add(band)
            top += rows
        }
    }

    /** Dots across the paper: 384 on 58 mm, 576 on 80 mm. */
    override val dots: Int = if (paperWidthMm in 1..60) 384 else 576

    fun bytes(): ByteArray = out.toByteArray()

    private fun raw(vararg codes: Int) { codes.forEach { out.add(it.toByte()) } }

    companion object {
        /** Text as Windows-1252; what it can't show becomes a close letter or "?". */
        fun encode(text: String): ByteArray {
            val bytes = ByteArray(text.length)
            text.forEachIndexed { index, char ->
                bytes[index] = when (char) {
                    '€' -> 0x80.toByte()
                    '‘', '’' -> '\''.code.toByte()
                    '“', '”', '„' -> '"'.code.toByte()
                    '–', '—', '−' -> '-'.code.toByte()
                    '…' -> 0x85.toByte()
                    '·', '•' -> 0xB7.toByte()
                    else -> if (char.code in 0x20..0x7E || char.code in 0xA0..0xFF) char.code.toByte() else '?'.code.toByte()
                }
            }
            return bytes
        }

        /** [text] cut into lines of at most [width] characters, at spaces where it can. */
        fun wrap(text: String, width: Int): List<String> {
            if (width <= 0) return listOf(text)
            val lines = mutableListOf<String>()
            text.split('\n').forEach { paragraph ->
                var current = ""
                paragraph.split(' ').forEach { word ->
                    var piece = word
                    while (piece.length > width) {
                        if (current.isNotEmpty()) { lines += current; current = "" }
                        lines += piece.take(width)
                        piece = piece.drop(width)
                    }
                    current = when {
                        current.isEmpty() -> piece
                        current.length + 1 + piece.length <= width -> "$current $piece"
                        else -> { lines += current; piece }
                    }
                }
                lines += current
            }
            return lines
        }
    }
}

/** A growing list of bytes. */
private class ByteBuilder {
    private var buffer = ByteArray(1024)
    private var size = 0

    fun add(byte: Byte) {
        ensure(1)
        buffer[size++] = byte
    }

    fun add(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }
}


/** A picture as printer dots: [width] × [height], black where [bits] says so (row by row). */
class MonoImage(val width: Int, val height: Int, private val bits: BooleanArray) {
    fun black(x: Int, y: Int): Boolean = bits[y * width + x]

    companion object {
        /**
         * [argb] pixels ([sourceWidth] × [sourceHeight]) shrunk to [width] dots wide, see-through as white, and turned to black
         * and white with error diffusion (Floyd–Steinberg), so photos keep their shades and logos stay sharp.
         */
        fun of(argb: IntArray, sourceWidth: Int, sourceHeight: Int, width: Int, opacity: Float = 1f): MonoImage {
            val w = width.coerceAtLeast(8)
            val h = (sourceHeight.toLong() * w / sourceWidth.coerceAtLeast(1)).toInt().coerceIn(1, 2400)
            val gray = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val sx = (x.toLong() * sourceWidth / w).toInt().coerceIn(0, sourceWidth - 1)
                val sy = (y.toLong() * sourceHeight / h).toInt().coerceIn(0, sourceHeight - 1)
                val p = argb[sy * sourceWidth + sx]
                val a = ((p ushr 24) and 0xFF) / 255f * opacity.coerceIn(0f, 1f)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                gray[y * w + x] = lum * a + 255f * (1 - a)
            }
            val bits = BooleanArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val old = gray[i]
                val black = old < 128f
                bits[i] = black
                val error = old - if (black) 0f else 255f
                if (x + 1 < w) gray[i + 1] += error * 7 / 16
                if (y + 1 < h) {
                    if (x > 0) gray[i + w - 1] += error * 3 / 16
                    gray[i + w] += error * 5 / 16
                    if (x + 1 < w) gray[i + w + 1] += error * 1 / 16
                }
            }
            return MonoImage(w, h, bits)
        }
    }
}
