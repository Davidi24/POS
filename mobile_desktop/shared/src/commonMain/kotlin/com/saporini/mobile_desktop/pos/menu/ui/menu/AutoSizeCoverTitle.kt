package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.CormorantGaramond

/** Measure before drawing so narrow covers retain both title lines without a size flash. */
@Composable
internal fun AutoSizeCoverTitle(
    text: String,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    letterSpacing: TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val family = CormorantGaramond()
    BoxWithConstraints(modifier) {
        var size = maxFontSize
        fun style() = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            lineHeight = size * 0.98f,
            letterSpacing = letterSpacing,
            color = color,
            textAlign = TextAlign.Center
        )
        while (size > minFontSize && measurer.measure(
                text = text,
                style = style(),
                constraints = Constraints(maxWidth = constraints.maxWidth),
                maxLines = 2
            ).hasVisualOverflow) {
            size = (size.value - 1f).coerceAtLeast(minFontSize.value).sp
        }
        Text(text = text, style = style(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}
