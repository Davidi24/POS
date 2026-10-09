package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter

// Forms and dialogs shared by the newer screens: one dialog frame, buttons, text inputs with their limits and
// messages, dropdowns, switches and check boxes. Same sizes and colours as the booking and shift forms.

internal object FormColors {
    val Border = Color(0xFFE3DED8)
    val Placeholder = Color(0xFF9A9B97)
}

/**
 * The dialog frame: title (and an optional line under it), scrolling content, buttons at the bottom.
 * While [busy] it can't be closed by tapping outside.
 */
@Composable
internal fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    busy: Boolean = false,
    maxWidth: Dp = 620.dp,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier.padding(16.dp).widthIn(max = maxWidth).fillMaxWidth().heightIn(max = 820.dp),
            shape = RoundedCornerShape(16.dp), color = Color.White
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, fontFamily = Inter(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Kit.Ink)
                        subtitle?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted) }
                    }
                    IconButton(onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "Close", tint = Kit.Ink) }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
                buttons?.let {
                    KitDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = it
                    )
                }
            }
        }
    }
}

enum class ButtonStyle { PRIMARY, SECONDARY, DANGER }

@Composable
internal fun KitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val container = when (style) {
        ButtonStyle.PRIMARY -> Kit.Green
        ButtonStyle.SECONDARY -> Color.White
        ButtonStyle.DANGER -> Kit.Danger
    }
    val content = if (style == ButtonStyle.SECONDARY) Kit.Ink else Color.White
    Button(
        onClick, modifier.heightIn(min = 44.dp), enabled = enabled && !loading, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = if (style == ButtonStyle.SECONDARY) Color.White else container.copy(alpha = 0.4f),
            disabledContentColor = if (style == ButtonStyle.SECONDARY) Kit.Faint else Color.White
        ),
        border = if (style == ButtonStyle.SECONDARY) BorderStroke(1.dp, FormColors.Border) else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = content, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, maxLines = 1)
    }
}

/** Asks before something that can't be undone easily. */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    busy: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    AppDialog(title, onDismiss, busy = busy, maxWidth = 480.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY, enabled = !busy)
        KitButton(confirmText, onConfirm, style = if (danger) ButtonStyle.DANGER else ButtonStyle.PRIMARY, loading = busy)
    }) {
        Text(message, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
        extra?.invoke(this)
    }
}

/** Label (with * or "optional"), the field, then its problem or a hint. */
@Composable
internal fun FieldBlock(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    optional: Boolean = false,
    problem: String? = null,
    hint: String? = null,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            if (required) Text(" *", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Danger)
            if (optional) Text(" (optional)", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
            Spacer(Modifier.weight(1f))
            trailing?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint) }
        }
        content()
        when {
            problem != null -> Text(problem, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger)
            hint != null -> Text(hint, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted)
        }
    }
}

/**
 * A text field in the form style. [maxLength] shows a counter and stops typing a little past the limit, so the
 * person sees the "too long" message instead of silently losing text.
 */
@Composable
internal fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    required: Boolean = false,
    optional: Boolean = false,
    problem: String? = null,
    hint: String? = null,
    icon: ImageVector? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
    suffix: String? = null
) {
    var reveal by remember { mutableStateOf(false) }
    val counter = maxLength?.takeIf { !singleLine || value.length > maxLength * 0.8 }?.let { "${value.length}/$it" }
    val field: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (singleLine) 46.dp else (22 * minLines + 24).dp)
                .clip(RoundedCornerShape(8.dp)).background(if (enabled) Color.White else Kit.Canvas)
                .border(1.dp, if (problem != null) Kit.Danger else FormColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 11.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon?.let { Icon(it, null, Modifier.size(18.dp), tint = Kit.Ink) }
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val clean = if (singleLine) raw.replace(Regex("[\\r\\n\\t]+"), " ") else raw
                    onChange(maxLength?.let { clean.take(it + 20) } ?: clean)
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else minLines,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
                visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (enabled) Kit.Ink else Kit.Muted),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, fontFamily = Inter(), fontSize = 13.sp, color = FormColors.Placeholder, maxLines = 1)
                        inner()
                    }
                }
            )
            suffix?.let { Text(it, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted) }
            if (password) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable { reveal = !reveal }, contentAlignment = Alignment.Center) {
                    Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "Hide" else "Show",
                        Modifier.size(18.dp), tint = Kit.Muted)
                }
            }
        }
    }
    if (label == null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            field()
            problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
        }
    } else {
        FieldBlock(label, modifier, required, optional, problem, hint, counter) { field() }
    }
}

/** A dropdown field: shows the chosen label (or [placeholder] while nothing is chosen) and lists the choices. */
@Composable
internal fun <T> SelectInput(
    value: T?,
    choices: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    required: Boolean = false,
    problem: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    placeholder: String = "Choose"
) {
    var expanded by remember { mutableStateOf(false) }
    val field: @Composable () -> Unit = {
        Box {
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) Color.White else Kit.Canvas)
                    .border(1.dp, if (problem != null) Kit.Danger else FormColors.Border, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { expanded = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                icon?.let { Icon(it, null, Modifier.size(18.dp), tint = Kit.Ink) }
                val text = value?.let(labelOf)
                Text(text ?: placeholder, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (text == null) FormColors.Placeholder else if (enabled) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.background(Color.White).heightIn(max = 320.dp)) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Text(labelOf(choice), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = if (choice == value) Kit.Green else Kit.Ink)
                        },
                        onClick = { onSelect(choice); expanded = false }
                    )
                }
            }
        }
    }
    if (label == null) Box(modifier) { field() } else FieldBlock(label, modifier, required, problem = problem, hint = hint) { field() }
}

/** A row with a title, a line of detail and a switch on the right. */
@Composable
internal fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (enabled) Kit.Ink else Kit.Muted)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted) }
        }
        KitSwitch(checked, onChange, enabled)
    }
}

@Composable
internal fun KitSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, compact: Boolean = false) {
    if (compact) {
        // A smaller switch for dense lists; the whole row stays the touch target.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
            Box(Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer(scaleX = 0.72f, scaleY = 0.72f)) { KitSwitch(checked, onChange, enabled) }
            }
        }
        return
    }
    Switch(
        checked, onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Kit.Green, checkedBorderColor = Kit.Green,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD5D8D3), uncheckedBorderColor = Color(0xFFD5D8D3)
        )
    )
}

enum class CheckState { ON, OFF, SOME }

/** A check box row; [CheckState.SOME] shows a dash (for "some of this group"). */
@Composable
internal fun CheckRow(
    label: String,
    state: CheckState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    strong: Boolean = false
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onToggle).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            when (state) { CheckState.ON -> Icons.Outlined.CheckBox; CheckState.OFF -> Icons.Outlined.CheckBoxOutlineBlank; CheckState.SOME -> Icons.Outlined.IndeterminateCheckBox },
            null, Modifier.size(20.dp),
            tint = when { !enabled -> Kit.Faint; state == CheckState.OFF -> Kit.Muted; else -> Kit.Green }
        )
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Inter(), fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp,
                color = if (enabled) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Two fields side by side on wide dialogs, one under the other on narrow ones. */
@Composable
internal fun FieldPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, wide: Boolean = true) {
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    } else {
        first(Modifier.fillMaxWidth())
        second(Modifier.fillMaxWidth())
    }
}

/** Small round option chips for picking one value (tips, quick amounts, durations). */
@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Kit.Green else Color.White)
            .border(1.dp, if (selected) Kit.Green else FormColors.Border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = when { selected -> Color.White; enabled -> Kit.Ink; else -> Kit.Faint }, maxLines = 1)
    }
}

/**
 * A dropdown with a search box, for long lists (stock items, dishes). Shows at most [maxShown] matches so it stays
 * quick; typing narrows the list.
 */
@Composable
internal fun <T> SearchableSelect(
    value: T?,
    choices: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    detailOf: ((T) -> String?)? = null,
    icon: ImageVector? = null,
    required: Boolean = false,
    problem: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    placeholder: String = "Choose",
    maxShown: Int = 60
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val field: @Composable () -> Unit = {
        Box {
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) Color.White else Kit.Canvas)
                    .border(1.dp, if (problem != null) Kit.Danger else FormColors.Border, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { query = ""; expanded = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                icon?.let { Icon(it, null, Modifier.size(18.dp), tint = Kit.Ink) }
                val text = value?.let(labelOf)
                Text(text ?: placeholder, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (text == null) FormColors.Placeholder else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.background(Color.White).widthIn(min = 280.dp).heightIn(max = 380.dp)) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    SearchField(query, { query = it }, Modifier.widthIn(min = 260.dp), placeholder = "Type to search", height = 40.dp)
                }
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val matches = choices.filter { choice ->
                    val text = (labelOf(choice) + " " + (detailOf?.invoke(choice) ?: "")).lowercase()
                    words.all { it in text }
                }
                if (matches.isEmpty()) {
                    Text("Nothing matches", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
                matches.take(maxShown).forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(labelOf(choice), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    color = if (choice == value) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                detailOf?.invoke(choice)?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                        onClick = { onSelect(choice); expanded = false }
                    )
                }
                if (matches.size > maxShown) {
                    Text("${matches.size - maxShown} more — keep typing to narrow down", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
                }
            }
        }
    }
    if (label == null) Box(modifier) { field() } else FieldBlock(label, modifier, required, problem = problem, hint = hint) { field() }
}
