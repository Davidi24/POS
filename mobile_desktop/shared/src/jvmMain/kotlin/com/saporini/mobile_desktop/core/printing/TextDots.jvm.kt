package com.saporini.mobile_desktop.core.printing

import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

// Java2D: a fixed-width font stretched so each letter fills its cell, then black where it's dark enough.
actual fun textDots(text: String, cellWidth: Int, cellHeight: Int, bold: Boolean): MonoImage {
    val width = (text.length * cellWidth).coerceAtLeast(1)
    val image = BufferedImage(width, cellHeight, BufferedImage.TYPE_BYTE_GRAY)
    val g = image.createGraphics()
    try {
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, width, cellHeight)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val font = Font(Font.MONOSPACED, if (bold) Font.BOLD else Font.PLAIN, 20)
        g.font = font
        val metrics = g.fontMetrics
        val advance = metrics.charWidth('M').coerceAtLeast(1)
        val tall = (metrics.ascent + metrics.descent).coerceAtLeast(1)
        g.scale(cellWidth.toDouble() / advance, cellHeight * 0.92 / tall)
        g.color = java.awt.Color.BLACK
        g.drawString(text, 0, metrics.ascent)
    } finally {
        g.dispose()
    }
    val raster = image.raster
    val bits = BooleanArray(width * cellHeight) { i -> raster.getSample(i % width, i / width, 0) < 128 }
    return MonoImage(width, cellHeight, bits)
}
