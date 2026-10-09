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
    width: Dp = 100.dp
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(width).height(RightEdgeActionButtonHeight),
        shape = RightEdgeShape,
        color = Color(0xFF4F7942).copy(alpha = if (enabled) 1f else .5f),
        contentColor = Color.White
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End)
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp, letterSpacing = 0.sp, maxLines = 1)
        }
    }
}
