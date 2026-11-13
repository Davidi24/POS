package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.theme.Inter

// Small pieces of the super admins' screens that the shared kits don't have.

/** A soft box with an icon and a few lines of explanation (wraps, unlike the one-line InfoBox). */
@Composable
internal fun NoteBox(icon: ImageVector, text: String, modifier: Modifier = Modifier, tone: Color = Kit.Ink) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (tone == Kit.Ink) Kit.Tint else tone.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = if (tone == Kit.Ink) Kit.Green else tone)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Ink)
    }
}

/** A list of reasons ("2 open orders") under a title, in the warning colour. */
@Composable
internal fun ReasonList(title: String, reasons: List<String>, tone: Color = Kit.Amber, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.08f)).border(1.dp, tone.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tone)
        reasons.forEach { reason ->
            Text("•  $reason", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, color = Kit.Ink)
        }
    }
}

/** "#4F7942" → a colour, or null when it isn't one. */
internal fun colorOf(hex: String?): Color? {
    val text = hex?.trim()?.removePrefix("#") ?: return null
    val full = when (text.length) {
        3 -> text.map { "$it$it" }.joinToString("")
        6 -> text
        else -> return null
    }
    return full.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/** A small square showing a colour (a dashed-looking empty square when there's none). */
@Composable
internal fun ColorSwatch(hex: String?, size: androidx.compose.ui.unit.Dp = 30.dp) {
    val color = colorOf(hex)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(color ?: Color.White).border(1.dp, Kit.Border, RoundedCornerShape(8.dp))
    )
}
