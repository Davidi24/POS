package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter

val RightEdgeActionButtonHeight = 84.dp

/** The smaller tab on phones (user, 2026-10-30: "smaller on the phone, they are big"). */
val CompactEdgeActionButtonHeight = 60.dp

/** The tab's height: [compact] on phones. */
fun edgeActionButtonHeight(compact: Boolean): Dp = if (compact) CompactEdgeActionButtonHeight else RightEdgeActionButtonHeight

// Mirrors the Orders edge button on the right, with the same dimensions and typography.
private val RightEdgeShape = GenericShape { size, _ ->
    val shoulder = size.height * .25f
    val end = size.width - shoulder
    moveTo(size.width, 0f)
    cubicTo(size.width, shoulder * .7f, size.width * .78f, shoulder, size.width * .58f, shoulder)
    lineTo(size.width - end, shoulder)
    cubicTo(size.width - end - shoulder * .55f, shoulder, 0f, shoulder * 1.45f, 0f, size.height * .5f)
    cubicTo(0f, size.height * .5f + shoulder * .55f, size.width - end - shoulder * .55f, size.height - shoulder, size.width - end, size.height - shoulder)
    lineTo(size.width * .58f, size.height - shoulder)
    cubicTo(size.width * .78f, size.height - shoulder, size.width, size.height - shoulder * .7f, size.width, size.height)
    close()
}

@Composable
fun RightEdgeActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 100.dp,
    compact: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(width).height(edgeActionButtonHeight(compact)),
        shape = RightEdgeShape,
        color = Color(0xFF4F7942).copy(alpha = if (enabled) 1f else .5f),
        contentColor = Color.White
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp, Alignment.End)
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(if (compact) 13.dp else 16.dp))
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 10.sp else 11.sp, letterSpacing = 0.sp, maxLines = 1)
        }
    }
}

// The same shape mirrored, for a button on the window's left edge (Suppliers → Add supplier, user 2026-10-30).
private val LeftEdgeShape = GenericShape { size, _ ->
    val shoulder = size.height * .25f
    val end = size.width - shoulder
    val w = size.width
    moveTo(0f, 0f)
    cubicTo(0f, shoulder * .7f, w - w * .78f, shoulder, w - w * .58f, shoulder)
    lineTo(end, shoulder)
    cubicTo(end + shoulder * .55f, shoulder, w, shoulder * 1.45f, w, size.height * .5f)
    cubicTo(w, size.height * .5f + shoulder * .55f, end + shoulder * .55f, size.height - shoulder, end, size.height - shoulder)
    lineTo(w - w * .58f, size.height - shoulder)
    cubicTo(w - w * .78f, size.height - shoulder, 0f, size.height - shoulder * .7f, 0f, size.height)
    close()
}

/** [RightEdgeActionButton] on the left edge: the tab grows out of the window's left side. */
@Composable
fun LeftEdgeActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    compact: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(width).height(edgeActionButtonHeight(compact)),
        shape = LeftEdgeShape,
        color = Color(0xFF4F7942).copy(alpha = if (enabled) 1f else .5f),
        contentColor = Color.White
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = if (compact) 8.dp else 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp, Alignment.Start)
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(if (compact) 13.dp else 16.dp))
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 10.sp else 11.sp, letterSpacing = 0.sp, maxLines = 1)
        }
    }
}
