package com.saporini.mobile_desktop.admin.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

private val Green = Color(0xFF4F7942)
private val GreenSoft = Color(0xFFEEF3EB)
private val Ink = Color(0xFF222426)
private val Muted = Color(0xFF747572)
private val Border = Color(0xFFE3E6E1)
private val Danger = Color(0xFFB13A2F)
private val PageBg = Color(0xFFF7F8F6)

private const val UPDATE_PERMISSION = "SETTINGS_UPDATE"

// Admin Hub → Settings: a grid of categories; each opens its own page.
@Composable
internal fun AdminSettings(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf<SettingsCategory?>(null) }
    Box(modifier.background(PageBg)) {
        val category = open
        if (category == null) SettingsGrid(onOpen = { open = it }, modifier = Modifier.fillMaxSize())
        else SettingsPage(category, onBack = { open = null }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SettingsGrid(onOpen: (SettingsCategory) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val columns = when {
            maxWidth >= 1100.dp -> 4
            maxWidth >= 760.dp -> 3
            maxWidth >= 480.dp -> 2
            else -> 1
        }
        val side = if (maxWidth < 600.dp) 16.dp else 28.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = side, end = side, top = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink)
                Text("Choose what you want to set up.", fontFamily = Inter(), fontSize = 13.sp, color = Muted)
            }
            SettingsCategory.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { category -> CategoryCard(category, onClick = { onOpen(category) }, modifier = Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: SettingsCategory, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.heightIn(min = 208.dp).clip(shape)
            .background(if (hovered && category.available) Color(0xFFFBFCFA) else Color.White)
            .border(1.dp, if (hovered && category.available) Green.copy(alpha = 0.45f) else Border, shape)
            .then(
                if (category.available) Modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                else Modifier
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Image(
                painterResource(category.image), null,
                Modifier.size(84.dp).alpha(if (category.available) 1f else 0.45f),
                contentScale = ContentScale.Fit
            )
            if (!category.available) {
                Text(
                    "Later",
                    Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Color(0xFFF1F2F0))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Muted
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = if (category.available) Ink else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (category.available) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = if (hovered) Green else Muted)
            }
        }
        Text(category.description, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 17.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsPage(category: SettingsCategory, onBack: () -> Unit, modifier: Modifier) {
    val api = koinInject<SettingsApi>()
    val session = koinInject<SessionManager>()
    val user by session.currentUser.collectAsState()
    val scope = user?.restaurantId?.takeIf(String::isNotBlank)?.let { SettingsScope(it, user?.defaultBranchId) }
    val canEdit = UPDATE_PERMISSION in user?.permissions.orEmpty()
    val sections = remember(category) { category.sections() }
    val sources = remember(sections) { sections.flatMap { section -> section.fields.map { it.source } }.toSet() }
    var values by remember(category) { mutableStateOf<SettingsValues?>(null) }
    var loadError by remember(category) { mutableStateOf<String?>(null) }
    var saveError by remember(category) { mutableStateOf<String?>(null) }
    var savedNote by remember(category) { mutableStateOf(false) }
    var saving by remember(category) { mutableStateOf(false) }
    var reload by remember(category) { mutableIntStateOf(0) }
    var confirmLeave by remember(category) { mutableStateOf(false) }
    // Bumped on save/discard so every input shows the stored value again.
    var generation by remember(category) { mutableIntStateOf(0) }
    val coroutines = rememberCoroutineScope()

    LaunchedEffect(category, scope, reload) {
        if (sources.isEmpty() || scope == null) return@LaunchedEffect
        loadError = null
        try {
            values = SettingsValues(sources.associateWith { api.loadSource(it, scope) }, emptyMap())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadError = error.message ?: "Couldn't load the settings."
        }
    }

    fun leave() {
        if (values?.changed == true) confirmLeave = true else onBack()
    }

    fun save() {
        val current = values ?: return
        if (scope == null || category.problem(current) != null) return
        saving = true
        saveError = null
        coroutines.launch {
            try {
                val loaded = current.loaded.toMutableMap()
                try {
                    for (group in SaveGroups) {
                        val edited = current.edits[group.source].orEmpty()
                        if (edited.keys.none { it in group.keys }) continue
                        val all = current.current(group.source) ?: continue
                        val body = if (group.source == SettingsSource.RESERVATION_RULE) all
                        else JsonObject(group.keys.associateWith { all[it] ?: JsonNull })
                        loaded[group.source] = group.save(api, scope, body)
                    }
                } finally {
                    // Keep what the server already accepted, even if a later group failed.
                    values = current.copy(loaded = loaded)
                }
                values = SettingsValues(loaded, emptyMap())
                generation++
                savedNote = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                saveError = error.message ?: "Couldn't save. Please try again."
            } finally {
                saving = false
            }
        }
    }

    BoxWithConstraints(modifier) {
        val side = if (maxWidth < 600.dp) 16.dp else 28.dp
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = side - 8.dp, end = side, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to settings", tint = Ink) }
                Image(painterResource(category.image), null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text(category.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
                    Text(category.description, fontFamily = Inter(), fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val loadedValues = values
                when {
                    sections.isEmpty() -> ComingSoon(category, Modifier.align(Alignment.Center).padding(24.dp))
                    scope == null -> CenterMessage("Sign in with an assigned restaurant to change its settings.", Modifier.align(Alignment.Center))
                    loadError != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CenterMessage(loadError.orEmpty(), Modifier)
                        TextButton(onClick = { reload++ }) { Text("Try again", fontFamily = Inter(), color = Green) }
                    }
                    loadedValues == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(26.dp), color = Green, strokeWidth = 2.dp)
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(start = side, end = side, top = 4.dp, bottom = if (loadedValues.changed) 96.dp else 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (!canEdit) InfoBanner("You can see these settings, but only people with permission to change settings can edit them.")
                            key(generation) {
                                sections.forEach { section ->
                                    SectionCard(section, loadedValues, canEdit) { source, key, element ->
                                        values = loadedValues.set(source, key, element)
                                        savedNote = false
                                        saveError = null
                                    }
                                }
                            }
                            if (savedNote) InfoBanner("Saved.")
                            // Occasions and event nights have their own lists and save on their own.
                            if (category == SettingsCategory.RESERVATIONS) OccasionsAndEventsSettings(scope.restaurantId, canEdit)
                        }
                    }
                }
                val current = values
                if (current != null && current.changed && canEdit) {
                    SaveBar(
                        saving = saving,
                        problem = category.problem(current),
                        error = saveError,
                        onDiscard = { values = SettingsValues(current.loaded, emptyMap()); saveError = null; generation++ },
                        onSave = ::save,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = side, vertical = 14.dp)
                    )
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave without saving?", fontFamily = Inter(), fontWeight = FontWeight.Bold) },
            text = { Text("Your changes on this page will be lost.", fontFamily = Inter()) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Leave", fontFamily = Inter(), color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay", fontFamily = Inter(), color = Green) } },
            containerColor = Color.White
        )
    }
}

@Composable
private fun SectionCard(
    section: SettingsSection,
    values: SettingsValues,
    canEdit: Boolean,
    onChange: (SettingsSource, String, kotlinx.serialization.json.JsonElement) -> Unit
) {
    val shown = section.fields.filter { field -> field.shownWhen?.invoke(values) ?: true }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(section.title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
            section.note?.let { Text(it, Modifier.padding(top = 2.dp), fontFamily = Inter(), fontSize = 12.sp, color = Muted) }
            shown.forEachIndexed { index, field ->
                if (index > 0) HorizontalDivider(color = Color(0xFFF0F1EE))
                FieldRow(field, values, canEdit) { element -> onChange(field.source, field.key, element) }
            }
        }
    }
}

@Composable
private fun FieldRow(field: SettingField, values: SettingsValues, canEdit: Boolean, onChange: (kotlinx.serialization.json.JsonElement) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(field.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Ink)
            field.hint?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Muted) }
        }
        when (field) {
            is ToggleField -> Switch(
                checked = values.bool(field.source, field.key),
                onCheckedChange = { onChange(JsonPrimitive(it)) },
                enabled = canEdit,
                colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE6E8E4), uncheckedBorderColor = Color(0xFFD5D8D2))
            )
            is NumberField -> Stepper(
                value = values.text(field.source, field.key)?.toIntOrNull() ?: field.min,
                unit = field.unit, min = field.min, max = field.max, step = field.step, enabled = canEdit,
                onChange = { onChange(JsonPrimitive(it)) }
            )
            is DecimalField -> SmallInput(
                text = values.text(field.source, field.key).orEmpty(),
                width = 120.dp, suffix = field.suffix, enabled = canEdit, keyboard = KeyboardType.Decimal,
                accept = { it.isEmpty() || it.matches(Regex("\\d{0,8}([.,]\\d{0,2})?")) },
                sameValue = { typed, stored -> typed.replace(',', '.').toDoubleOrNull() == stored.toDoubleOrNull() },
                onChange = { raw ->
                    val number = raw.replace(',', '.').toDoubleOrNull()
                    onChange(if (number == null) JsonNull else JsonPrimitive(number))
                }
            )
            is TextSettingField -> SmallInput(
                text = values.text(field.source, field.key).orEmpty(),
                width = if (field.multiline) 320.dp else 160.dp, placeholder = field.placeholder, enabled = canEdit,
                singleLine = !field.multiline, accept = { it.length <= field.maxLength },
                onChange = { onChange(JsonPrimitive(it)) }
            )
            is ChoiceField -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val current = values.text(field.source, field.key)
                field.options.forEach { (value, label) ->
                    val picked = current == value
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (picked) GreenSoft else Color.White)
                            .border(if (picked) 1.5.dp else 1.dp, if (picked) Green else Border, RoundedCornerShape(8.dp))
                            .then(if (canEdit) Modifier.clickable { onChange(JsonPrimitive(value)) } else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        fontFamily = Inter(), fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp,
                        color = if (picked) Green else Ink
                    )
                }
            }
            is TimeField -> {
                // The server sends "15:00:00"; staff type "15:00".
                val shown = values.text(field.source, field.key)?.take(5).orEmpty()
                SmallInput(
                    text = shown, width = 90.dp, placeholder = "15:00", enabled = canEdit,
                    accept = { it.length <= 5 && it.all { c -> c.isDigit() || c == ':' } },
                    onChange = { raw ->
                        // Stored with seconds, like the server sends it, so typing the old time back counts as unchanged.
                        if (raw.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) onChange(JsonPrimitive("$raw:00"))
                    }
                )
            }
        }
    }
}

// Minus / number / plus, with the number typeable. Keeps the value within min..max.
@Composable
private fun Stepper(value: Int, unit: String, min: Int, max: Int, step: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton(Icons.Outlined.Remove, "Less", enabled && value > min) { onChange((value - step).coerceIn(min, max)) }
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter(Char::isDigit).take(4)
                text = digits
                digits.toIntOrNull()?.let { if (it in min..max) onChange(it) }
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Green),
            modifier = Modifier.width(58.dp).onFocusChanged { if (!it.isFocused) text = value.toString() }
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFFF6F7F5))
                .border(1.dp, Border, RoundedCornerShape(8.dp)).padding(vertical = 8.dp)
        )
        StepButton(Icons.Outlined.Add, "More", enabled && value < max) { onChange((value + step).coerceIn(min, max)) }
        if (unit.isNotEmpty()) Text(unit, Modifier.widthIn(min = 30.dp), fontFamily = Inter(), fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (enabled) Color.White else Color(0xFFF3F4F2))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, Modifier.size(16.dp), tint = if (enabled) Ink else Color(0xFFB9BCB5))
    }
}

@Composable
private fun SmallInput(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onChange: (String) -> Unit,
    accept: (String) -> Boolean = { true },
    suffix: String = "",
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    sameValue: (typed: String, stored: String) -> Boolean = { typed, stored -> typed == stored }
) {
    var draft by remember { mutableStateOf(text) }
    // Follow the stored value when it changes from outside, but never rewrite what's being typed.
    LaunchedEffect(text) { if (!sameValue(draft, text)) draft = text }
    Row(
        Modifier.width(width).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF6F7F5)).border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (draft.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontFamily = Inter(), fontSize = 13.sp, color = Color(0xFFA3A5A0))
            }
            BasicTextField(
                value = draft,
                onValueChange = { raw -> if (accept(raw)) { draft = raw; onChange(raw) } },
                enabled = enabled,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                textStyle = TextStyle(fontFamily = Inter(), fontSize = 13.sp, color = Ink),
                cursorBrush = SolidColor(Green),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (suffix.isNotEmpty()) Text(suffix, Modifier.padding(start = 4.dp), fontFamily = Inter(), fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun SaveBar(saving: Boolean, problem: String?, error: String?, onDiscard: () -> Unit, onSave: () -> Unit, modifier: Modifier) {
    val message = problem ?: error
    Surface(
        modifier.widthIn(max = 820.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message ?: "You have unsaved changes",
                Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = if (message != null) Danger else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onDiscard, enabled = !saving) { Text("Discard", fontFamily = Inter(), color = Muted) }
            Spacer(Modifier.width(6.dp))
            Surface(onClick = onSave, enabled = !saving && problem == null, shape = RoundedCornerShape(10.dp), color = if (problem == null) Green else Green.copy(alpha = 0.4f)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (saving) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(if (saving) "Saving…" else "Save changes", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GreenSoft).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = Green)
        Text(text, fontFamily = Inter(), fontSize = 12.sp, color = Ink)
    }
}

@Composable
private fun CenterMessage(text: String, modifier: Modifier) {
    Text(text, modifier.padding(16.dp), fontFamily = Inter(), fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
}

// Categories with nothing to set yet.
@Composable
private fun ComingSoon(category: SettingsCategory, modifier: Modifier) {
    Column(modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(category.image), null, Modifier.size(120.dp), contentScale = ContentScale.Fit)
        Text("Nothing to set here yet", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
        Text(
            when (category) {
                SettingsCategory.DEVICES -> "Printers and where each ticket prints are coming next."
                SettingsCategory.ONLINE_BOOKING -> "This comes with the website."
                else -> "We'll decide together what goes here."
            },
            fontFamily = Inter(), fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center
        )
    }
}
