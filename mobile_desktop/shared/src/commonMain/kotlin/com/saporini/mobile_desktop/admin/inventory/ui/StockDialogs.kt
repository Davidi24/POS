package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.BulkMoveDraft
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.MAX_STOCK_TEXT
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.admin.inventory.StockMoveKind
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.SelectInput
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.plainNumber
import com.saporini.mobile_desktop.core.format.unitShort
import com.saporini.mobile_desktop.core.theme.Inter

/** The same change for every picked stock line: an amount per line, one "to" place for moves, one reason. */
@Composable
internal fun BulkMoveDialog(bulk: BulkMoveDraft, state: InventoryState, model: InventoryScreenModel) {
    val look = moveKindLook(bulk.kind)
    val needsReason = bulk.kind == StockMoveKind.WASTE || bulk.kind == StockMoveKind.ADJUST
    AppDialog(
        "${changeTitle(bulk.kind)} · ${bulk.lines.size} ${if (bulk.lines.size == 1) "line" else "lines"}", model::cancelBulk,
        subtitle = "Each line is saved as its own stock change.", busy = state.saving, maxWidth = 680.dp,
        buttons = {
            KitButton("Cancel", model::cancelBulk, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("${changeTitle(bulk.kind)} ${bulk.lines.size}", model::saveBulk, icon = look.icon, loading = state.saving,
                style = if (bulk.kind == StockMoveKind.WASTE) ButtonStyle.DANGER else ButtonStyle.PRIMARY)
        }
    ) {
        val narrow = LocalNarrowDialog.current
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        // The kind can still be switched here.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChangeKinds.forEach { kind ->
                val l = moveKindLook(kind)
                val chosen = kind == bulk.kind
                Surface(onClick = { model.changeBulk { it.copy(kind = kind) } }, enabled = !state.saving, modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(10.dp), color = if (chosen) l.color.copy(alpha = 0.1f) else Color.White,
                    border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) l.color else Kit.Border)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(l.icon, null, Modifier.size(19.dp), tint = l.color)
                        Text(changeTitle(kind), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (chosen) l.color else Kit.Ink)
                    }
                }
            }
        }
        if (bulk.kind == StockMoveKind.TRANSFER) {
            SelectInput<StockLocationDto?>(state.location(bulk.toLocationId), state.activeLocations, { it?.name ?: "" },
                { v -> model.changeBulk { it.copy(toLocationId = v?.id) } }, label = "Move them all to", required = true, icon = Icons.Outlined.Storefront,
                placeholder = "Choose a place", hint = "A line can't move to the place it's already in.")
        }
        // One line per picked stock line: picture, where, how much is there, and the amount for this change.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bulk.lines.forEach { line ->
                val item = state.item(line.itemId)
                val level = state.levels.firstOrNull { it.inventoryItemId == line.itemId && it.locationId == line.locationId }
                val unit = unitShort(item?.baseUnit)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas)
                    .border(1.dp, if (line.problem != null) Kit.Danger.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val amount: @Composable (Modifier) -> Unit = { m ->
                        AmountField(line.quantityText, unit, signed = bulk.kind == StockMoveKind.ADJUST, enabled = !state.saving, modifier = m, fill = narrow) { value ->
                            model.changeBulk { b -> b.copy(lines = b.lines.map { if (it == line) it.copy(quantityText = value, problem = null) else it }) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (item != null) StockItemThumb(item, 36.dp) else ItemThumb(null, null, 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(item?.name ?: "Item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                                maxLines = if (narrow) 2 else 1, overflow = TextOverflow.Ellipsis)
                            Text("${state.location(line.locationId)?.name ?: "Place"} · ${level?.let { "${plainNumber(it.onHandQuantity)} $unit there" } ?: "none there"}",
                                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!narrow) amount(Modifier)
                    }
                    // On a phone the amount gets a line of its own, as wide as the card, so the name isn't cut short.
                    if (narrow) amount(Modifier.fillMaxWidth().padding(top = 4.dp))
                    line.problem?.let { Text(it, Modifier.padding(start = if (narrow) 0.dp else 46.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Danger) }
                }
            }
        }
        TextInput(bulk.reason, { v -> model.changeBulk { it.copy(reason = v) } }, label = "Reason", required = needsReason, optional = !needsReason,
            singleLine = false, minLines = 2, icon = Icons.AutoMirrored.Outlined.Notes, maxLength = MAX_STOCK_TEXT,
            placeholder = when (bulk.kind) {
                StockMoveKind.RECEIVE -> "Delivery note 81"
                StockMoveKind.WASTE -> "Out of date, dropped, burnt…"
                StockMoveKind.TRANSFER -> "Prep for dinner"
                StockMoveKind.RETURN -> "Damaged on arrival"
                StockMoveKind.ADJUST -> "Scale was off"
            }, hint = "The same reason goes on every line.")
    }
}

/** A small amount box with − and + on its sides (whole steps), for lists of lines; [fill] stretches the number to the box's width. */
@Composable
internal fun AmountField(value: String, unit: String, signed: Boolean, enabled: Boolean, modifier: Modifier = Modifier, fill: Boolean = false,
                         onChange: (String) -> Unit) {
    val step = { by: Int ->
        val now = value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val next = now + by
        onChange(if (!signed && next < 0) "0" else trimmed(next))
    }
    Row(modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Outlined.Remove, "Less", enabled) { step(-1) }
        BasicTextField(value, { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || (signed && ch == '-') }.take(12)) },
            if (fill) Modifier.weight(1f) else Modifier.width(64.dp), enabled = enabled, singleLine = true,
            textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Kit.Green), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text("0", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Faint)
                    field()
                }
            })
        Text(unit, Modifier.padding(end = 4.dp), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1)
        StepButton(Icons.Outlined.Add, "More", enabled) { step(1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(16.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}
