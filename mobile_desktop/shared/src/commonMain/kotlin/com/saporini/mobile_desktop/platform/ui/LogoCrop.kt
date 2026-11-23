package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.files.encodePng
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val MaxZoom = 4f
private const val LogoOut = 512

/**
 * Placing a new logo before it's saved, like a profile picture (user, 2026-11-23: "I can zoom a little or not, like adding a
 * profile picture on LinkedIn"): the picture in the square, a zoom from the whole picture (100 %) to 400 %, dragged to its
 * place. What's inside the square is saved as a 512 px PNG (see-through where the picture doesn't reach).
 */
@Composable
internal fun LogoCropDialog(image: ImageBitmap, busy: Boolean, onCancel: () -> Unit, onUse: (ByteArray) -> Unit) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var pan by remember(image) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableFloatStateOf(0f) }
    fun clamp(p: Offset, z: Float): Offset {
        if (frame <= 0f) return p
        val fit = min(frame / image.width, frame / image.height)
        val w = image.width * fit * z
        val h = image.height * fit * z
        val maxX = abs(w - frame) / 2
        val maxY = abs(h - frame) / 2
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }
    AppDialog("Place your logo", onCancel, busy = busy, maxWidth = 520.dp,
        subtitle = "Zoom in if you want and drag it into place. What's inside the square is your logo.",
        buttons = {
            KitButton("Cancel", onCancel, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton("Use this logo", { if (frame > 0f) onUse(cropLogo(image, zoom, pan, frame)) }, icon = Icons.Outlined.Check, loading = busy)
        }) {
        val side = if (LocalNarrowDialog.current) 260.dp else 320.dp
        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            Box(
                Modifier.size(side).clip(RoundedCornerShape(18.dp)).checkerboard().border(1.dp, Kit.Border, RoundedCornerShape(18.dp))
                    .onSizeChanged { frame = it.width.toFloat() }
                    .pointerInput(image) { detectDragGestures { change, drag -> change.consume(); pan = clamp(pan + drag, zoom) } }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val fit = min(size.width / image.width, size.height / image.height)
                    val w = image.width * fit * zoom
                    val h = image.height * fit * zoom
                    drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset((size.width / 2 - w / 2 + pan.x).roundToInt(), (size.height / 2 - h / 2 + pan.y).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.High)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoomStep(Icons.Outlined.ZoomOut, "Zoom out", zoom > 1f) { zoom = (zoom - 0.25f).coerceAtLeast(1f); pan = clamp(pan, zoom) }
            Slider(zoom, { zoom = it; pan = clamp(pan, it) }, Modifier.weight(1f), valueRange = 1f..MaxZoom,
                colors = SliderDefaults.colors(thumbColor = Kit.Green, activeTrackColor = Kit.Green, inactiveTrackColor = Kit.Border))
            ZoomStep(Icons.Outlined.ZoomIn, "Zoom in", zoom < MaxZoom) { zoom = (zoom + 0.25f).coerceAtMost(MaxZoom); pan = clamp(pan, zoom) }
            Text("${(zoom * 100).roundToInt()}%", Modifier.width(48.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                color = Kit.Ink, textAlign = TextAlign.End, translate = false)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Drag the picture to move it.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
            LinkText("The whole picture") { zoom = 1f; pan = Offset.Zero }
        }
    }
}

@Composable
private fun ZoomStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(icon, label, Modifier.size(19.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}

/** Light squares behind the picture, so see-through parts show as such. */
internal fun Modifier.checkerboard(): Modifier = drawBehind {
    val cell = 10.dp.toPx()
    drawRect(Color.White)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(Color(0xFFEFF2EE), Offset(x, y), Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

/** The square as it's shown, at 512 px, as PNG. */
private fun cropLogo(image: ImageBitmap, zoom: Float, pan: Offset, frame: Float): ByteArray {
    val out = ImageBitmap(LogoOut, LogoOut)
    val k = LogoOut / frame
    val fit = min(frame / image.width, frame / image.height)
    val w = image.width * fit * zoom * k
    val h = image.height * fit * zoom * k
    val left = LogoOut / 2f - w / 2 + pan.x * k
    val top = LogoOut / 2f - h / 2 + pan.y * k
    androidx.compose.ui.graphics.Canvas(out).drawImageRect(image, IntOffset.Zero, IntSize(image.width, image.height),
        IntOffset(left.roundToInt(), top.roundToInt()), IntSize(w.roundToInt(), h.roundToInt()), Paint().apply { filterQuality = FilterQuality.High })
    return encodePng(out)
}
