package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** A QR code's squares, row by row (true = dark), or null when [text] can't be one. Made with ZXing on each platform. */
expect fun qrModules(text: String): List<BooleanArray>?

/** A Code 128 barcode's bars from left to right (true = dark), or null. */
expect fun barcodeBars(text: String): BooleanArray?

/** A real QR code of [text], [size] wide, drawn square by square. */
@Composable
internal fun QrCode(text: String, size: Dp, color: Color = Color(0xFF222426), modifier: Modifier = Modifier) {
    val modules = remember(text) { qrModules(text) } ?: return
    Canvas(modifier.size(size)) {
        val cell = this.size.width / modules.size
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) drawRect(color, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** A real Code 128 barcode of [text], spread over the width it's given. */
@Composable
internal fun Barcode(text: String, modifier: Modifier, color: Color = Color(0xFF222426)) {
    val bars = remember(text) { barcodeBars(text) } ?: return
    Canvas(modifier) {
        val bar = this.size.width / bars.size
        bars.forEachIndexed { x, dark -> if (dark) drawRect(color, Offset(x * bar, 0f), Size(bar + 0.3f, this.size.height)) }
    }
}
