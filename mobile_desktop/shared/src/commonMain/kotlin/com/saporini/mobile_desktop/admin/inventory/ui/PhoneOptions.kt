package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.CompactFieldHeight
import com.saporini.mobile_desktop.core.theme.Inter

/** Height of a phone's search field and the square buttons beside it. */
internal val PhoneControlHeight = 40.dp

// On a phone the side panels (sort, show, needs a look...) don't fit next to the list, so they fold into one square
// button beside the search. A small green dot counts the filters that are on.

@Composable
internal fun PhoneOptionsButton(icon: ImageVector, description: String, active: Int, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) =
    PhoneOptionsButton(icon, description, active, PhoneControlHeight, content)

/** The same button at another height (44 dp next to a tablet page header's controls). */
@Composable
internal fun PhoneOptionsButton(icon: ImageVector, description: String, active: Int, size: Dp,
                                content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, modifier = Modifier.size(size), shape = RoundedCornerShape(8.dp),
            color = if (active > 0) Kit.GreenSoft else Color.White, border = BorderStroke(1.dp, if (active > 0) Kit.Green else Kit.Border)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, description, Modifier.size(19.dp), tint = if (active > 0) Kit.Green else Kit.Ink)
            }
        }
        if (active > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(17.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                Text("$active", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White, maxLines = 1)
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(Color.White).width(286.dp).heightIn(max = 520.dp)) {
            content { open = false }
        }
    }
}

/**
 * A compact dropdown button for a phone's filter row ("All places ▾", "Everything ▾"): what is chosen, an optional count,
 * and a menu of choices. Green while it narrows the list.
 */
@Composable
internal fun PhoneFilterButton(icon: ImageVector, label: String, modifier: Modifier, active: Boolean = false, count: Int? = null,
                               content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(CompactFieldHeight), shape = RoundedCornerShape(8.dp),
            color = if (active) Kit.GreenSoft else Color.White, border = BorderStroke(1.dp, if (active) Kit.Green else Kit.Border)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(icon, null, Modifier.size(16.dp), tint = if (active) Kit.Green else Kit.Ink)
                Text(label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp,
                    color = if (active) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                count?.let { Text("$it", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = if (active) Kit.Green else Kit.Ink)
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(Color.White).width(270.dp).heightIn(max = 460.dp)) {
            content { open = false }
        }
    }
}

/** A small heading inside the options menu. */
@Composable
internal fun OptionGroup(title: String) {
    Text(title.uppercase(), Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.Bold,
        fontSize = 10.sp, letterSpacing = 0.6.sp, color = Kit.Muted)
}

/** One choice: icon in its colour, the label, a count, and a tick on the chosen one. */
@Composable
internal fun OptionLine(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, color: Color = Kit.Green, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Kit.GreenSoft else Color.Transparent)
        .clickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        icon?.let { Icon(it, null, Modifier.size(17.dp), tint = color) }
        Text(text, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
            letterSpacing = 0.sp, color = if (selected) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        count?.let { Text("$it", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted) }
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Kit.Green)
    }
}
