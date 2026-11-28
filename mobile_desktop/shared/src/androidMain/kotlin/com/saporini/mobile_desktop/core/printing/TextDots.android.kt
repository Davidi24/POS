package com.saporini.mobile_desktop.core.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

// Android: a fixed-width font stretched so each letter fills its cell, then black where it's dark enough.
actual fun textDots(text: String, cellWidth: Int, cellHeight: Int, bold: Boolean): MonoImage {
    val width = (text.length * cellWidth).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, cellHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textSize = 20f
    }
    val advance = paint.measureText("M").coerceAtLeast(1f)
    val metrics = paint.fontMetrics
    val tall = (metrics.descent - metrics.ascent).coerceAtLeast(1f)
    canvas.scale(cellWidth / advance, cellHeight * 0.92f / tall)
    canvas.drawText(text, 0f, -metrics.ascent, paint)
    val pixels = IntArray(width * cellHeight)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, cellHeight)
    bitmap.recycle()
    val bits = BooleanArray(pixels.size) { i -> (pixels[i] and 0xFF) < 128 }
    return MonoImage(width, cellHeight, bits)
}
