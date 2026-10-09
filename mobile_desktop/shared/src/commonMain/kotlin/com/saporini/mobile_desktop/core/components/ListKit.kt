package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.flow.distinctUntilChanged

// Lists and tables of the newer screens: a white bordered panel, a column header, clickable rows and the "⋮" menu
// of row actions. Long lists ask for their next page by themselves when the end comes into view.

/** The white bordered box a list or table sits in. */
@Composable
internal fun ListPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(content = content)
    }
}

internal data class TableColumn(val title: String, val weight: Float, val alignEnd: Boolean = false)

/** The grey header row of a table; rows below should use the same weights. */
@Composable
internal fun TableHeader(columns: List<TableColumn>, modifier: Modifier = Modifier, trailingSpace: Boolean = true) {
    Row(
        modifier.fillMaxWidth().background(Kit.Canvas).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        columns.forEach { column ->
            Text(
                column.title.uppercase(), Modifier.weight(column.weight), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 0.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start
            )
        }
        if (trailingSpace) Box(Modifier.size(32.dp))
    }
    KitDivider()
}

/** One clickable table row with a bottom line; [selected] tints it. */
@Composable
internal fun TableRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Column {
        Row(
            modifier.fillMaxWidth().background(if (selected) Kit.GreenSoft else Color.White)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        KitDivider()
    }
}

internal data class RowAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val enabled: Boolean = true, val onClick: () -> Unit)

/** "⋮" button that opens the row's actions. Hidden when there is nothing to do. */
@Composable
internal fun RowActionsMenu(actions: List<RowAction>, busy: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(32.dp)) {
        if (actions.isEmpty()) return@Box
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = !busy) { open = true }, contentAlignment = Alignment.Center) {
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.MoreVert, "More actions", Modifier.size(19.dp), tint = Kit.Ink)
        }
        DropdownMenu(open, { open = false }, Modifier.background(Color.White)) {
            actions.forEach { action ->
                DropdownMenuItem(
                    enabled = action.enabled,
                    leadingIcon = { Icon(action.icon, null, Modifier.size(18.dp), tint = if (!action.enabled) Kit.Faint else if (action.danger) Kit.Danger else Kit.Ink) },
                    text = {
                        Text(action.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (!action.enabled) Kit.Faint else if (action.danger) Kit.Danger else Kit.Ink)
                    },
                    onClick = { open = false; action.onClick() }
                )
            }
        }
    }
}

/** Two lines in a cell: a bold title and a muted detail. */
@Composable
internal fun CellText(title: String, detail: String? = null, modifier: Modifier = Modifier, strong: Boolean = true, titleColor: Color = Kit.Ink, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(title, fontFamily = Inter(), fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp, color = titleColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A small square with an icon in a soft colour, used at the start of list rows. */
@Composable
internal fun IconTile(icon: ImageVector, color: Color = Kit.Green, size: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.53f), tint = color)
    }
}

/** A bordered card for one item of a list on phones (where table columns don't fit). */
@Composable
internal fun ListCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Kit.GreenSoft else Color.White)
            .border(1.dp, if (selected) Kit.Green.copy(alpha = 0.4f) else Kit.RowBorder, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Calls [onLoadMore] when the list is scrolled near its end and more is available. */
@Composable
internal fun LoadMoreWhenNearEnd(listState: LazyListState, hasMore: Boolean, busy: Boolean, threshold: Int = 6, onLoadMore: () -> Unit) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= listState.layoutInfo.totalItemsCount - threshold
        }
    }
    LaunchedEffect(listState, hasMore, busy) {
        snapshotFlow { nearEnd }.distinctUntilChanged().collect { if (it && hasMore && !busy) onLoadMore() }
    }
}
