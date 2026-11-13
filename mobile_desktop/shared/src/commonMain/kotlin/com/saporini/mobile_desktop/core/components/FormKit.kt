package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.coerceAtLeast
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
    // A window that keeps one size while what's in it changes (steps of the shift planner); never taller than the screen.
    height: Dp? = null,
    // One step back inside the window: a plain arrow before the title (user, 2026-10-31: "the back arrow is at the top").
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints {
            // On a phone the dialog takes nearly the whole width and its sides are slimmer, so fields keep their room.
            // this.maxWidth is the window's width (the parameter of the same name is the dialog's own limit).
            val narrow = this.maxWidth < NarrowDialogWidth
            val side = if (narrow) 18.dp else 24.dp
            Surface(
                modifier.padding(if (narrow) 10.dp else 16.dp).widthIn(max = maxWidth).fillMaxWidth()
                    .then(if (height != null) Modifier.heightIn(max = height).fillMaxHeight() else Modifier.heightIn(max = 820.dp)),
                shape = RoundedCornerShape(16.dp), color = Color.White
            ) {
                CompositionLocalProvider(LocalNarrowDialog provides narrow) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) side - 12.dp else side, end = if (narrow) 6.dp else 12.dp, top = if (narrow) 14.dp else 18.dp),
                            verticalAlignment = Alignment.Top) {
                            onBack?.let { IconButton(it, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Kit.Ink) } }
                            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(title, fontFamily = Inter(), fontSize = if (narrow) 18.sp else 20.sp, fontWeight = FontWeight.Bold, color = Kit.Ink)
                                subtitle?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted) }
                            }
                            IconButton(onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "Close", tint = Kit.Ink) }
                        }
                        val scroll = rememberScrollState()
                        val body: @Composable BoxScope.() -> Unit = {
                            Column(
                                Modifier.verticalScroll(scroll).padding(horizontal = side, vertical = DialogContentPadding),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                content = content
                            )
                            Box(Modifier.matchParentSize().padding(end = if (narrow) 4.dp else 6.dp, top = 4.dp, bottom = 4.dp)) { EdgeScrollbar(scroll) }
                        }
                        if (height == null) Box(Modifier.weight(1f, fill = false), content = body)
                        else BoxWithConstraints(Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalDialogRoom provides (maxHeight - DialogContentPadding * 2).coerceAtLeast(0.dp)) { body() }
                        }
                        buttons?.let {
                            KitDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = side, vertical = if (narrow) 12.dp else 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                content = it
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Height of compact fields and filter buttons (phone filter rows). */
internal val CompactFieldHeight = 38.dp

/** Below this window width a dialog is "narrow" (a phone): slimmer sides, and [FieldPair]s stack. */
private val NarrowDialogWidth = 480.dp

/** True inside an [AppDialog] shown on a phone-wide window. */
internal val LocalNarrowDialog = staticCompositionLocalOf { false }

private val DialogContentPadding = 14.dp

/** The height an [AppDialog] with a fixed `height` has for its content, so a step with little in it can sit in the middle. */
internal val LocalDialogRoom = staticCompositionLocalOf<Dp?> { null }

/** DANGER is a filled red button; DANGER_OUTLINE is white with red text and border (e.g. "Cancel order" next to a green button). */
enum class ButtonStyle { PRIMARY, SECONDARY, DANGER, DANGER_OUTLINE, PRIMARY_OUTLINE }

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
        ButtonStyle.DANGER_OUTLINE -> Color.White
        ButtonStyle.PRIMARY_OUTLINE -> Color.White
    }
    val outlined = style == ButtonStyle.SECONDARY || style == ButtonStyle.DANGER_OUTLINE || style == ButtonStyle.PRIMARY_OUTLINE
    val content = when (style) {
        ButtonStyle.SECONDARY -> Kit.Ink; ButtonStyle.DANGER_OUTLINE -> Kit.Danger; ButtonStyle.PRIMARY_OUTLINE -> Kit.Green; else -> Color.White
    }
    Button(
        onClick, modifier.heightIn(min = 44.dp), enabled = enabled && !loading, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = if (outlined) Color.White else container.copy(alpha = 0.4f),
            disabledContentColor = if (outlined) Kit.Faint else Color.White
        ),
        border = when (style) {
            ButtonStyle.SECONDARY -> BorderStroke(1.dp, FormColors.Border)
            ButtonStyle.DANGER_OUTLINE -> BorderStroke(1.dp, Kit.Danger.copy(alpha = if (enabled) 0.55f else 0.25f))
            // Green outline: a quieter way forward next to Back, e.g. "Skip for now".
            ButtonStyle.PRIMARY_OUTLINE -> BorderStroke(1.5.dp, Kit.Green.copy(alpha = if (enabled) 0.8f else 0.3f))
            else -> null
        },
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
    placeholder: String = "Choose",
    // A smaller field for filter rows on a phone.
    compact: Boolean = false,
    // An icon (in its colour) for each choice in the list; the field then shows the chosen one's icon too.
    iconOf: ((T) -> ImageVector)? = null,
    iconColorOf: ((T) -> Color)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val field: @Composable () -> Unit = {
        Box {
            Row(
                Modifier.fillMaxWidth().height(if (compact) CompactFieldHeight else 46.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) Color.White else Kit.Canvas)
                    .border(1.dp, if (problem != null) Kit.Danger else FormColors.Border, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { expanded = true }.padding(horizontal = if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
            ) {
                val chosenIcon = if (value != null && iconOf != null) iconOf(value) else icon
                chosenIcon?.let {
                    Icon(it, null, Modifier.size(if (compact) 16.dp else 18.dp),
                        tint = if (value != null && iconOf != null) iconColorOf?.invoke(value) ?: Kit.Ink else Kit.Ink)
                }
                val text = value?.let(labelOf)
                Text(text ?: placeholder, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (compact) 12.sp else 13.sp,
                    color = if (text == null) FormColors.Placeholder else if (enabled) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.background(Color.White).heightIn(max = 320.dp)) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Text(labelOf(choice), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                                color = if (choice == value) Kit.Green else Kit.Ink)
                        },
                        onClick = { onSelect(choice); expanded = false },
                        leadingIcon = iconOf?.let { iconFor ->
                            {
                                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                                    .background((iconColorOf?.invoke(choice) ?: Kit.Ink).copy(alpha = 0.12f)), Alignment.Center) {
                                    Icon(iconFor(choice), null, Modifier.size(16.dp), tint = iconColorOf?.invoke(choice) ?: Kit.Ink)
                                }
                            }
                        }
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

/** A switch; with no [onChange] it only shows its state, in its normal colours (not greyed out like a disabled one). */
@Composable
internal fun KitSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, enabled: Boolean = true, compact: Boolean = false) {
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
internal fun FieldPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, wide: Boolean = !LocalNarrowDialog.current) {
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
    maxShown: Int = 60,
    // A smaller field for filter rows on a phone.
    compact: Boolean = false,
    // A picture for each choice (drawn at the size given): in every row of the list, and in the field for the chosen one.
    leadingOf: (@Composable (T, Dp) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val field: @Composable () -> Unit = {
        Box {
            Row(
                Modifier.fillMaxWidth().height(if (compact) CompactFieldHeight else 46.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) Color.White else Kit.Canvas)
                    .border(1.dp, if (problem != null) Kit.Danger else FormColors.Border, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { query = ""; expanded = true }.padding(horizontal = if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
            ) {
                if (value != null && leadingOf != null) leadingOf(value, if (compact) 22.dp else 28.dp)
                else icon?.let { Icon(it, null, Modifier.size(if (compact) 16.dp else 18.dp), tint = Kit.Ink) }
                val text = value?.let(labelOf)
                Text(text ?: placeholder, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = if (compact) 12.sp else 13.sp,
                    color = if (text == null) FormColors.Placeholder else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Kit.Ink)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.background(Color.White).widthIn(min = 280.dp)) {
                // The search box stays put; only the choices under it scroll.
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
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                matches.take(maxShown).forEach { choice ->
                    // Plain rows (not menu items, whose text style spaces letters out): name, detail, a tick on the chosen one.
                    val chosen = choice == value
                    Row(
                        Modifier.widthIn(min = 280.dp).fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (chosen) Kit.GreenSoft else Color.Transparent)
                            .clickable { onSelect(choice); expanded = false }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        leadingOf?.invoke(choice, 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(labelOf(choice), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
                                color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            detailOf?.invoke(choice)?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (chosen) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Kit.Green)
                    }
                }
                if (matches.size > maxShown) {
                    Text("${matches.size - maxShown} more — keep typing to narrow down", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontFamily = Inter(), fontSize = 11.sp, color = Kit.Faint)
                }
                }
            }
        }
    }
    if (label == null) Box(modifier) { field() } else FieldBlock(label, modifier, required, problem = problem, hint = hint) { field() }
}
