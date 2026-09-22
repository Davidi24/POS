package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.core.theme.Inter

private val ActiveOlive = Color(0xFF4F7942)

@Composable
internal fun AddItemCard(
    onClick: () -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .then(if (isPhone) {
                Modifier.height(88.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)).padding(vertical = 4.dp)
            } else {
                Modifier.aspectRatio(1.05f).fillMaxSize().padding(40.dp)
            })
            .clip(shape)
            .background(Color(0xFFF3F3F1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFFA7A9A4),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(13f, 9f))
                )
            )
        }

        if (isPhone) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(24.dp), tint = ActiveOlive)
                Text("ADD NEW ITEM", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = ActiveOlive)
            }
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = ActiveOlive
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ADD NEW ITEM",
                fontFamily = CormorantGaramond(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF666864)
            )
        }
    }
}
