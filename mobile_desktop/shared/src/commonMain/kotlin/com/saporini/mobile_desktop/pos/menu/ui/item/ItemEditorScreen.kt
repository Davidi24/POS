package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.admin.inventory.RecipeDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishIngredientRequestDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishRecipeDto
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuSection
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogActionStatus
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogStatusBody
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuFormDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuFormFieldPair
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import kotlin.math.round
import mobile_desktop.shared.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

private val ItemEditorOlive = Color(0xFF4F7942)
private val ItemEditorInk = Color(0xFF242522)
private val ItemEditorMuted = Color(0xFF71736E)
private val ItemEditorBorder = Color(0xFFE2E3DE)
private val ItemEditorSurface = Color(0xFFF7F7F5)
private val ItemEditorPlaceholder = Color(0xFFC7C8C2)

private fun isValidInventoryRecipeQuantity(text: String): Boolean {
    val normalized = text.trim()
    if (!Regex("^[0-9]{1,9}(\\.[0-9]{1,3})?$").matches(normalized)) return false
    return normalized.toDoubleOrNull()?.let { it > 0.0 } == true
}

data class DraftIngredient(
    val name: String,
    val quantity: String,
    val unit: String
)

data class DraftVariant(
    val name: String,
    val priceDeltaLabel: String,
    val id: String? = null
)

data class DraftOptionChoice(
    val name: String,
    val priceDeltaLabel: String,
    val id: String? = null,
    val inventoryRecipeId: String? = null,
    val inventoryRecipeQuantity: Double? = null
)

data class DraftOptionGroup(
    val name: String,
    val required: Boolean,
    val choices: List<DraftOptionChoice>,
    val linkId: String? = null,
    val optionGroupId: String? = null
)

data class EditableMenuItem(
    val name: String,
    val priceLabel: String,
    val sku: String?,
    val description: String?,
    val imageFileName: String?,
    // The dish's stored photo, shown in the editor until it's changed or removed.
    val imageUrl: String? = null,
    val available: Boolean,
    val sendToKitchen: Boolean = true,
    val showOnline: Boolean = false,
    val onlineSectionId: String? = null,
    val onlineSectionName: String? = null,
    val ingredients: List<DraftIngredient> = emptyList()
)

// Where "Show in online menu" puts the dish: an existing online section (id), or one by name (created if new).
internal data class OnlinePlacement(
    val show: Boolean,
    val sectionId: String? = null,
    val sectionName: String? = null
)

private data class OptionChoiceInput(
    val name: String,
    val priceDelta: String,
    val inventoryRecipeId: String? = null,
    val inventoryRecipeQuantity: String = ""
)

// A setting with a short explanation that follows the switch, e.g. "Send to kitchen".
@Composable
private fun ItemEditorSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ItemEditorInk)
                Text(text = description, fontFamily = Inter(), fontSize = 12.sp, color = ItemEditorMuted)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ItemEditorOlive
                )
            )
        }
        if (extra != null) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ItemEditorBorder)
            extra()
        }
    }
}

// Asked when "Show in online menu" is switched on: which online section the dish goes into. Three kinds of choice:
// the section named like the dish's own section (reused if the online menu has it, created otherwise), any existing
// online section, or a new name (which still reuses an existing section if the name matches one).
private sealed interface OnlineSectionPick {
    data object Same : OnlineSectionPick
    data class Existing(val section: OnlineMenuSection) : OnlineSectionPick
    data object New : OnlineSectionPick
}

@Composable
private fun OnlineSectionChooserDialog(
    dishName: String,
    staffSectionName: String?,
    currentOnlineSectionId: String?,
    currentOnlineSectionName: String?,
    loadSections: suspend () -> Result<List<OnlineMenuSection>>,
    onDismiss: () -> Unit,
    onConfirm: (sectionId: String?, sectionName: String) -> Unit
) {
    var sections by remember { mutableStateOf<List<OnlineMenuSection>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        loadError = null
        loadSections().fold(
            onSuccess = { sections = it },
            onFailure = { loadError = it.message ?: "Couldn't load the online menu" }
        )
    }

    val sameName = staffSectionName?.trim()?.takeIf { it.isNotEmpty() }
    val sameExisting = sameName?.let { wanted -> sections?.firstOrNull { it.name.equals(wanted, ignoreCase = true) } }
    // The same-named section already has its own choice above, so the list shows every other one.
    val otherSections = sections.orEmpty().filter { it.id != sameExisting?.id }
    var pick by remember { mutableStateOf<OnlineSectionPick?>(null) }
    var customName by remember { mutableStateOf("") }
    // Once the sections are in: start on where the dish is now, else the same-named section, else the first one.
    LaunchedEffect(sections) {
        val loaded = sections ?: return@LaunchedEffect
        if (pick != null) return@LaunchedEffect
        val current = loaded.firstOrNull { it.id == currentOnlineSectionId }
            ?: currentOnlineSectionName?.let { name -> loaded.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        pick = when {
            current != null && current.id == sameExisting?.id -> OnlineSectionPick.Same
            current != null -> OnlineSectionPick.Existing(current)
            sameName != null -> OnlineSectionPick.Same
            loaded.isNotEmpty() -> OnlineSectionPick.Existing(loaded.first())
            else -> OnlineSectionPick.New
        }
    }
    val customTrimmed = customName.trim()
    val customExisting = sections?.firstOrNull { it.name.equals(customTrimmed, ignoreCase = true) }
    val canConfirm = sections != null && when (val chosen = pick) {
        OnlineSectionPick.Same -> sameName != null
        is OnlineSectionPick.Existing -> true
        OnlineSectionPick.New -> customTrimmed.isNotEmpty()
        null -> false
    }

    MenuNestedDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = ItemEditorInk,
        textContentColor = ItemEditorMuted,
        title = {
            Text(
                if (currentOnlineSectionName == null) "Add to online menu" else "Online section",
                fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ItemEditorInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose the online section for $dishName.",
                    fontFamily = Inter(),
                    fontSize = 13.sp,
                    color = ItemEditorMuted
                )
                val loaded = sections
                when {
                    loadError != null -> {
                        Text(loadError.orEmpty(), fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFFB13A2F))
                        TextButton(onClick = { attempt++ }) {
                            Text("Try again", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = ItemEditorOlive)
                        }
                    }
                    loaded == null -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = ItemEditorOlive, strokeWidth = 2.dp)
                    }
                    else -> {
                        if (sameName != null) {
                            OnlineSectionChoice(
                                selected = pick == OnlineSectionPick.Same,
                                title = if (sameExisting != null) "Same section as here: ${sameExisting.name}" else "Create \u201C$sameName\u201D in the online menu",
                                subtitle = if (sameExisting != null) sectionCountLabel(sameExisting) else "Same name as its section here",
                                onClick = { pick = OnlineSectionPick.Same }
                            )
                        }
                        if (otherSections.isNotEmpty()) {
                            ItemEditorFieldLabel(text = "Existing online sections")
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                otherSections.forEach { section ->
                                    OnlineSectionChoice(
                                        selected = (pick as? OnlineSectionPick.Existing)?.section?.id == section.id,
                                        title = section.name,
                                        subtitle = sectionCountLabel(section),
                                        onClick = { pick = OnlineSectionPick.Existing(section) }
                                    )
                                }
                            }
                        }
                        OnlineSectionChoice(
                            selected = pick == OnlineSectionPick.New,
                            title = "New section",
                            subtitle = "Type a name for a new online section",
                            onClick = { pick = OnlineSectionPick.New }
                        )
                        if (pick == OnlineSectionPick.New) {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { if (it.length <= 150) customName = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. Chef's favourites") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = itemEditorOutlinedTextFieldColors()
                            )
                            if (customExisting != null) {
                                Text(
                                    text = "The online menu already has \u201C${customExisting.name}\u201D, so the dish goes there",
                                    fontFamily = Inter(),
                                    fontSize = 12.sp,
                                    color = ItemEditorMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (val chosen = pick) {
                        OnlineSectionPick.Same -> if (sameName != null) onConfirm(sameExisting?.id, sameExisting?.name ?: sameName)
                        is OnlineSectionPick.Existing -> onConfirm(chosen.section.id, chosen.section.name)
                        OnlineSectionPick.New -> onConfirm(customExisting?.id, customExisting?.name ?: customTrimmed)
                        null -> Unit
                    }
                },
                enabled = canConfirm,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive)
            ) {
                Text(
                    if (currentOnlineSectionName == null) "Add to online menu" else "Save",
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = ItemEditorMuted)
            }
        }
    )
}

private fun sectionCountLabel(section: OnlineMenuSection): String = when (section.itemCount) {
    0L -> "No dishes yet"
    1L -> "1 dish"
    else -> "${section.itemCount} dishes"
}

@Composable
private fun OnlineSectionChoice(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) ItemEditorOlive else ItemEditorBorder, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = ItemEditorOlive))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ItemEditorInk)
            Text(subtitle, fontFamily = Inter(), fontSize = 12.sp, color = ItemEditorMuted)
        }
    }
}

@Composable
internal fun ItemEditorDialog(
    existingItem: EditableMenuItem? = null,
    // Which section a new item lands in — shown as a subtitle so it's clear before
    // saving; irrelevant (and unused) once editing an item that already has one.
    sectionName: String? = null,
    // Only used while editing: lets the item be moved to a different section.
    sections: List<MenuCategory> = emptyList(),
    currentSectionId: String? = null,
    status: DialogActionStatus = DialogActionStatus.Idle,
    onRetry: () -> Unit = {},
    onSuccessSettled: () -> Unit = {},
    // Loads the online menu's sections for the "Show in online menu" chooser.
    loadOnlineSections: suspend () -> Result<List<OnlineMenuSection>> = { Result.success(emptyList()) },
    // The stock items and preparations dishes can be made with, and (for a saved dish) its amounts from stock.
    loadIngredientChoices: suspend () -> Result<List<IngredientChoiceDto>> = { Result.success(emptyList()) },
    loadDishRecipe: (suspend () -> Result<DishRecipeDto>)? = null,
    onDismiss: () -> Unit,
    onDeleteItem: (() -> Unit)? = null,
    onSave: (
        name: String,
        priceLabel: String,
        sku: String?,
        description: String?,
        photo: PhotoChange,
        available: Boolean,
        sendToKitchen: Boolean,
        online: OnlinePlacement,
        ingredients: List<DraftIngredient>,
        sectionId: String?,
        // The dish's ingredients from stock to save as its recipe; null leaves the recipe as it is.
        stock: List<DishIngredientRequestDto>?
    ) -> Unit,
    // Items in a special menu (occasion extras): shown only when not null, reported just before onSave.
    extras: ItemExtras? = null,
    occasions: List<Pair<String, String>> = emptyList(),
    onExtrasChange: (ItemExtras) -> Unit = {}
) {
    val isEditing = existingItem != null
    var extraHours by remember { mutableStateOf(extras?.orderBeforeHours?.toString().orEmpty()) }
    var extraOccasions by remember { mutableStateOf(extras?.occasionCodes.orEmpty()) }
    val movableSections = remember(sections) { sections.filter { it.name != "All" && it.id != null } }
    var selectedSectionId by remember(currentSectionId) { mutableStateOf(currentSectionId) }

    var name by remember { mutableStateOf(existingItem?.name.orEmpty()) }
    var basePrice by remember { mutableStateOf(existingItem?.priceLabel?.let { extractPriceValue(it) }.orEmpty()) }
    var sku by remember { mutableStateOf(existingItem?.sku.orEmpty()) }
    var description by remember { mutableStateOf(existingItem?.description.orEmpty()) }
    var available by remember { mutableStateOf(existingItem?.available ?: true) }
    var sendToKitchen by remember { mutableStateOf(existingItem?.sendToKitchen ?: true) }
    var showOnline by remember { mutableStateOf(existingItem?.showOnline ?: false) }
    var onlineSectionId by remember { mutableStateOf(existingItem?.onlineSectionId) }
    var onlineSectionName by remember { mutableStateOf(existingItem?.onlineSectionName) }
    var onlineChooserOpen by remember { mutableStateOf(false) }
    // The dish's own section here, which the online chooser offers as the default section name.
    val staffSectionName = movableSections.firstOrNull { it.id == selectedSectionId }?.name ?: sectionName
    // The photo: kept, replaced by a picked file, or removed. Checked here (type, size, really a picture) so a bad
    // file is caught before anything is saved.
    var photoChange by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val pickedPhoto = photoChange as? PhotoChange.Replace
    val pickedBitmap = remember(pickedPhoto) { pickedPhoto?.bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    val keptPhotoUrl = existingItem?.imageUrl.takeIf { photoChange == PhotoChange.Keep }
    val hasPhoto = pickedPhoto != null || keptPhotoUrl != null
    val photoLabel = pickedPhoto?.fileName ?: if (keptPhotoUrl != null) "Current photo" else "No photo"
    // The ingredients: names guests see, with the amounts from stock (the dish's recipe) put on the ones that have them.
    var selectedIngredients by remember {
        mutableStateOf(existingItem?.ingredients.orEmpty().map { IngredientRow(name = it.name) })
    }
    var recipeLoad by remember { mutableStateOf(if (loadDishRecipe == null) DishRecipeLoad.None else DishRecipeLoad.Loading) }
    // What the server had, so an unchanged recipe isn't saved again.
    var loadedStock by remember { mutableStateOf<List<DishIngredientRequestDto>?>(null) }
    var ingredientChoices by remember { mutableStateOf<List<IngredientChoiceDto>?>(null) }
    var ingredientChoicesError by remember { mutableStateOf<String?>(null) }
    var choicesAttempt by remember { mutableStateOf(0) }
    var ingredientSearchOpen by remember { mutableStateOf(false) }
    // Choosing the amount of a newly picked ingredient, or changing one already listed.
    var amountFor by remember { mutableStateOf<Pair<IngredientChoiceDto, IngredientRow?>?>(null) }
    LaunchedEffect(choicesAttempt) {
        ingredientChoicesError = null
        loadIngredientChoices().fold(
            onSuccess = { ingredientChoices = it },
            onFailure = { ingredientChoicesError = it.message ?: "the server didn't answer" }
        )
    }
    LaunchedEffect(Unit) {
        val load = loadDishRecipe ?: return@LaunchedEffect
        load().fold(
            onSuccess = { recipe ->
                selectedIngredients = mergeRecipe(selectedIngredients, recipe)
                loadedStock = stockRequest(selectedIngredients)
                recipeLoad = DishRecipeLoad.Loaded
            },
            onFailure = { recipeLoad = DishRecipeLoad.Failed(it.message ?: "the server didn't answer") }
        )
    }

    val priceIsValid = isValidItemPrice(basePrice)
    val canSave = name.isNotBlank() && priceIsValid

    val formScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) {
            scope.launch {
                val contentType = photoContentType(file.name)
                if (contentType == null) {
                    photoError = "Use a PNG, JPEG or WebP photo."
                    return@launch
                }
                val bytes = runCatching { file.readBytes() }.getOrNull()
                photoError = when {
                    bytes == null -> "This photo couldn't be read. Try another one."
                    bytes.size > MaxPhotoBytes -> "This photo is too big. Use one under 5 MB."
                    runCatching { bytes.decodeToImageBitmap() }.isFailure -> "This file can't be opened as a photo."
                    else -> null
                }
                if (photoError == null && bytes != null) {
                    photoChange = PhotoChange.Replace(bytes, file.name, contentType)
                }
            }
        }
    }

    // After adding an ingredient, show the end of the form where it landed (not when the dish's amounts load).
    var ingredientsAdded by remember { mutableStateOf(0) }
    LaunchedEffect(ingredientsAdded) {
        if (ingredientsAdded > 0) {
            formScrollState.animateScrollTo(formScrollState.maxValue)
        }
    }

    MenuFormDialog(
        title = if (isEditing) "Edit Item" else "Add New Item",
        subtitle = if (!isEditing) sectionName?.let { "Adding to $it" } else null,
        onDismiss = onDismiss,
        onSave = {
            if (extras != null) {
                onExtrasChange(ItemExtras(extraHours.toIntOrNull()?.coerceIn(0, 720), extraOccasions))
            }
            onSave(
                name.trim(),
                formatItemPrice(basePrice),
                sku.trim().takeIf { it.isNotEmpty() },
                description.trim().takeIf { it.isNotEmpty() },
                photoChange,
                available,
                sendToKitchen,
                OnlinePlacement(
                    show = showOnline,
                    sectionId = onlineSectionId.takeIf { showOnline },
                    sectionName = onlineSectionName.takeIf { showOnline }
                ),
                selectedIngredients.map { DraftIngredient(it.name.trim(), it.quantity, it.unit) },
                if (isEditing && selectedSectionId != currentSectionId) selectedSectionId else null,
                // Only once the dish's own amounts are known (a new dish has none), and only when they changed.
                when (recipeLoad) {
                    DishRecipeLoad.None -> stockRequest(selectedIngredients).takeIf { it.isNotEmpty() }
                    DishRecipeLoad.Loaded -> stockRequest(selectedIngredients).takeIf { it != loadedStock }
                    else -> null
                }
            )
        },
        saveLabel = if (isEditing) "Save Changes" else "Add Item",
        canSave = canSave,
        onDelete = onDeleteItem,
        desktopWidth = 0.6f,
        desktopHeight = 0.9f,
        desktopMaxWidth = 620.dp,
        scrollState = formScrollState,
        status = status,
        onRetry = onRetry,
        onSuccessSettled = onSuccessSettled
    ) { isPhone ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemEditorFieldLabel(text = "Item name", required = true)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (available) "Available" else "Unavailable",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (available) ItemEditorOlive else ItemEditorMuted
            )
            Spacer(Modifier.width(9.dp))
            Switch(
                checked = available,
                onCheckedChange = { available = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ItemEditorOlive
                )
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("For example: Spaghetti alla Carbonara") },
            singleLine = true,
            shape = RoundedCornerShape(9.dp),
            colors = itemEditorOutlinedTextFieldColors()
        )

        if (isEditing && movableSections.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemEditorFieldLabel(text = "Category")
                var categoryMenuOpen by remember { mutableStateOf(false) }
                val selectedSectionName = movableSections.firstOrNull { it.id == selectedSectionId }?.name
                    ?: sectionName.orEmpty()
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White)
                            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                            .clickable { categoryMenuOpen = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedSectionName,
                            modifier = Modifier.weight(1f),
                            fontFamily = Inter(),
                            fontSize = 14.sp,
                            color = ItemEditorInk,
                            // Row is a fixed 56dp: without this a long section name
                            // wraps and gets clipped mid-line.
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = ItemEditorMuted
                        )
                    }
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        movableSections.forEach { section ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        section.name,
                                        fontFamily = Inter(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    selectedSectionId = section.id
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        MenuFormFieldPair(
            isPhone = isPhone,
            first = { fieldModifier ->
                Column(modifier = fieldModifier) {
                    ItemEditorFieldLabel(text = "Price", required = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = basePrice,
                        onValueChange = { basePrice = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = "$",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                color = ItemEditorMuted
                            )
                        },
                        singleLine = true,
                        isError = basePrice.isNotBlank() && !priceIsValid,
                        shape = RoundedCornerShape(9.dp),
                        colors = itemEditorOutlinedTextFieldColors()
                    )
                }
            },
            second = { fieldModifier ->
                Column(modifier = fieldModifier) {
                    ItemEditorFieldLabel(text = "SKU")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { if (it.length <= 80) sku = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. PZA-MARG-12") },
                        singleLine = true,
                        shape = RoundedCornerShape(9.dp),
                        colors = itemEditorOutlinedTextFieldColors()
                    )
                }
            }
        )

        if (basePrice.isNotBlank() && !priceIsValid) {
            Text(
                text = "Enter a valid price, for example 14.00.",
                fontFamily = Inter(),
                fontSize = 12.sp,
                color = Color(0xFFB13A2F)
            )
        }

        ItemEditorSwitchRow(
            title = "Send to kitchen",
            description = if (sendToKitchen) {
                "Shows on the kitchen screen when the order is sent"
            } else {
                "Served directly, like a bottled drink. Never goes to the kitchen"
            },
            checked = sendToKitchen,
            onCheckedChange = { sendToKitchen = it }
        )
        if (extras != null) {
            ItemExtrasFields(
                hours = extraHours,
                onHoursChange = { extraHours = it.filter(Char::isDigit).take(3) },
                occasions = occasions,
                picked = extraOccasions,
                onToggle = { code -> extraOccasions = if (code in extraOccasions) extraOccasions - code else extraOccasions + code }
            )
        }
        ItemEditorSwitchRow(
            title = "Show in online menu",
            description = if (showOnline) {
                "Customers see it in the online menu and can order it online"
            } else {
                "Only staff see it, in the POS menu"
            },
            checked = showOnline,
            onCheckedChange = { on ->
                if (on) {
                    // Turning it on first asks which online section it goes into.
                    onlineChooserOpen = true
                } else {
                    showOnline = false
                    onlineSectionId = null
                    onlineSectionName = null
                }
            },
            extra = if (showOnline && onlineSectionName != null) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Online section: ",
                            fontFamily = Inter(),
                            fontSize = 13.sp,
                            color = ItemEditorMuted
                        )
                        Text(
                            text = onlineSectionName.orEmpty(),
                            modifier = Modifier.weight(1f, fill = false),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onlineChooserOpen = true }) {
                            Text("Change", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ItemEditorOlive)
                        }
                    }
                }
            } else {
                null
            }
        )
        if (onlineChooserOpen) {
            OnlineSectionChooserDialog(
                dishName = name.trim().ifEmpty { "this dish" },
                staffSectionName = staffSectionName,
                currentOnlineSectionId = onlineSectionId,
                currentOnlineSectionName = onlineSectionName,
                loadSections = loadOnlineSections,
                onDismiss = { onlineChooserOpen = false },
                onConfirm = { id, chosenName ->
                    showOnline = true
                    onlineSectionId = id
                    onlineSectionName = chosenName
                    onlineChooserOpen = false
                }
            )
        }

        ItemEditorFieldLabel(text = "Description")
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Describe the dish") },
            minLines = 4,
            maxLines = 6,
            shape = RoundedCornerShape(9.dp),
            colors = itemEditorOutlinedTextFieldColors()
        )

        ItemEditorFieldLabel(text = "Item photo")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The photo itself: the picked file, the stored photo, or the "no photo" tile.
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(8.dp))
            ) {
                val picked = pickedBitmap
                if (picked != null) {
                    Image(picked, "New item photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    MenuPhoto(url = keptPhotoUrl, contentDescription = "Current item photo", modifier = Modifier.fillMaxSize(), iconSize = 22.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                            .clickable { imagePicker.launch() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ItemEditorOlive
                        )
                        Text(
                            text = if (hasPhoto) "Change photo" else "Add photo",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorOlive
                        )
                    }
                    if (hasPhoto) {
                        Text(
                            text = "Remove",
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    photoError = null
                                    photoChange = if (existingItem?.imageUrl != null) PhotoChange.Remove else PhotoChange.Keep
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFFB13A2F)
                        )
                    }
                }
                Text(
                    text = photoError ?: (photoLabel + if (pickedPhoto != null) " · uploads when you save" else ""),
                    fontFamily = Inter(),
                    fontSize = 12.sp,
                    color = if (photoError != null) Color(0xFFB13A2F) else ItemEditorMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (photoError == null && !hasPhoto) {
                    Text(
                        text = "PNG, JPEG or WebP, up to 5 MB.",
                        fontFamily = Inter(),
                        fontSize = 11.sp,
                        color = ItemEditorMuted
                    )
                }
            }
        }

        ItemEditorFieldLabel(text = "Recipe / Ingredients")
        DishIngredientsSection(
            rows = selectedIngredients,
            load = recipeLoad,
            choices = ingredientChoices,
            price = basePrice.trim().toDoubleOrNull(),
            onAdd = { ingredientSearchOpen = true },
            onEdit = { row ->
                val choice = ingredientChoices?.firstOrNull { it.key == row.key }
                    ?: IngredientChoiceDto(row.kind.orEmpty(), row.refId.orEmpty(), row.name, baseUnit = row.unit,
                        units = listOf(com.saporini.mobile_desktop.pos.menu.data.dto.UnitCostDto(row.unit)))
                amountFor = choice to row
            },
            onRemove = { row -> selectedIngredients = selectedIngredients.filterNot { it.key == row.key } }
        )
    }

    if (ingredientSearchOpen) {
        IngredientPickerDialog(
            choices = ingredientChoices,
            choicesError = ingredientChoicesError,
            // Amounts from stock that couldn't be loaded can't be changed; names still can.
            stockAllowed = recipeLoad !is DishRecipeLoad.Failed && recipeLoad != DishRecipeLoad.Loading,
            alreadyAdded = selectedIngredients.map { it.key }.toSet(),
            onRetry = { choicesAttempt++ },
            onDismiss = { ingredientSearchOpen = false },
            onPick = { choice ->
                ingredientSearchOpen = false
                amountFor = choice to null
            },
            onPickName = { typed ->
                ingredientSearchOpen = false
                selectedIngredients = selectedIngredients + IngredientRow(name = typed)
                ingredientsAdded++
            }
        )
    }

    amountFor?.let { (choice, row) ->
        IngredientAmountDialog(
            name = choice.name,
            units = choice.units.ifEmpty { listOf(com.saporini.mobile_desktop.pos.menu.data.dto.UnitCostDto(choice.baseUnit)) },
            startQuantity = row?.quantity.orEmpty(),
            startUnit = row?.unit?.takeIf { it.isNotBlank() } ?: preferredUnit(choice),
            saveText = if (row == null) "Add" else "Save",
            onDismiss = { amountFor = null },
            onSave = { quantity, unit ->
                val next = IngredientRow(choice.name, choice.kind, choice.id, quantity, unit)
                selectedIngredients = if (row == null) selectedIngredients + next
                    else selectedIngredients.map { if (it.key == row.key) next else it }
                if (row == null) ingredientsAdded++
                amountFor = null
            }
        )
    }
}

@Composable
internal fun VariantEditorDialog(
    itemName: String,
    variants: List<DraftVariant>,
    onDismiss: () -> Unit,
    onDoneEditing: (List<DraftVariant>) -> Unit,
    onCreateVariant: suspend (name: String, priceDelta: Double, displayOrder: Int) -> Result<String>,
    onUpdateVariant: suspend (variantId: String, name: String, priceDelta: Double, displayOrder: Int) -> Result<Unit>,
    onDeleteVariant: suspend (variantId: String) -> Result<Unit>
) {
    var currentVariants by remember { mutableStateOf(variants) }
    var addVariantOpen by remember { mutableStateOf(false) }
    var variantBeingEdited by remember { mutableStateOf<DraftVariant?>(null) }
    var variantToDelete by remember { mutableStateOf<DraftVariant?>(null) }

    MenuFormDialog(
        title = "Variants",
        subtitle = itemName,
        onDismiss = { onDoneEditing(currentVariants) },
        onSave = { onDoneEditing(currentVariants) },
        saveLabel = "Done",
        showCancel = false,
        desktopWidth = 0.46f,
        desktopHeight = 0.72f,
        desktopMaxWidth = 480.dp
    ) { _ ->
        if (currentVariants.isEmpty()) {
            Text(
                text = "No variants yet. Add a size or configuration option below.",
                fontFamily = Inter(),
                fontSize = 13.sp,
                color = ItemEditorMuted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentVariants.forEach { variant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = variant.name,
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = ItemEditorInk
                            )
                            if (variant.priceDeltaLabel.isNotBlank()) {
                                Text(
                                    text = variant.priceDeltaLabel,
                                    fontFamily = Inter(),
                                    fontSize = 12.sp,
                                    color = ItemEditorMuted
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, ItemEditorBorder, RoundedCornerShape(8.dp))
                                .clickable { variantBeingEdited = variant },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit ${variant.name}",
                                modifier = Modifier.size(15.dp),
                                tint = ItemEditorInk
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFB13A2F))
                                .clickable { variantToDelete = variant },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete ${variant.name}",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Outlined, matching the "+" add button used for sections — not a filled
        // color, so it doesn't compete with the primary Done action.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
                .border(1.dp, ItemEditorInk, RoundedCornerShape(9.dp))
                .clickable { addVariantOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ItemEditorInk
            )
            Text(
                text = "Add Variant",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = ItemEditorInk
            )
        }
    }

    if (addVariantOpen) {
        AddVariantDialog(
            existingVariants = currentVariants,
            displayOrder = currentVariants.size,
            onDismiss = { addVariantOpen = false },
            onCreateVariant = onCreateVariant,
            onAdded = { added ->
                currentVariants = currentVariants + added
                addVariantOpen = false
            }
        )
    }

    variantBeingEdited?.let { variant ->
        EditVariantDialog(
            variant = variant,
            existingVariants = currentVariants,
            displayOrder = currentVariants.indexOfFirst { it === variant }.coerceAtLeast(0),
            onDismiss = { variantBeingEdited = null },
            onUpdateVariant = onUpdateVariant,
            onUpdated = { updated ->
                currentVariants = currentVariants.map { if (it === variant) updated else it }
                variantBeingEdited = null
            }
        )
    }

    variantToDelete?.let { variant ->
        DeleteVariantDialog(
            variant = variant,
            onDismiss = { variantToDelete = null },
            onDeleteVariant = onDeleteVariant,
            onDeleted = {
                currentVariants = currentVariants.filterNot { it === variant }
                variantToDelete = null
            }
        )
    }
}

@Composable
private fun AddVariantDialog(
    existingVariants: List<DraftVariant>,
    displayOrder: Int,
    onDismiss: () -> Unit,
    onCreateVariant: suspend (name: String, priceDelta: Double, displayOrder: Int) -> Result<String>,
    onAdded: (DraftVariant) -> Unit
) {
    val scope = rememberCoroutineScope()
    var variantName by remember { mutableStateOf("") }
    var variantPriceDelta by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    var addedVariant by remember { mutableStateOf<DraftVariant?>(null) }
    val trimmedName = variantName.trim()
    val priceValid = isValidPriceDelta(variantPriceDelta)
    val isDuplicate = trimmedName.isNotEmpty() &&
        existingVariants.any { it.name.equals(trimmedName, ignoreCase = true) }
    val valid = trimmedName.isNotEmpty() && priceValid && !isDuplicate
    val showValidationError = attemptedSubmit && !valid
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle

    fun submit() {
        if (!valid) {
            attemptedSubmit = true
        } else if (!isBusy) {
            status = DialogActionStatus.Loading("Adding")
            scope.launch {
                onCreateVariant(trimmedName, parsePriceDelta(variantPriceDelta), displayOrder).fold(
                    onSuccess = { newId ->
                        addedVariant = DraftVariant(trimmedName, formatPriceDelta(variantPriceDelta), newId)
                        status = DialogActionStatus.Success("$trimmedName added")
                    },
                    onFailure = { error ->
                        status = DialogActionStatus.Failed(message = error.message ?: "Could not create this variant.")
                    }
                )
            }
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        containerColor = Color.White,
        titleContentColor = ItemEditorInk,
        textContentColor = ItemEditorMuted,
        title = {
            Text(
                text = "Add Variant",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = ItemEditorInk
            )
        },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "A variant is a different size or configuration of this item, like a portion size.",
                        fontFamily = Inter(),
                        fontSize = 13.sp,
                        color = ItemEditorMuted
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemEditorFieldLabel(text = "Variant Name", required = true)
                        OutlinedTextField(
                            value = variantName,
                            onValueChange = { variantName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Large") },
                            singleLine = true,
                            isError = showValidationError && (trimmedName.isEmpty() || isDuplicate),
                            shape = RoundedCornerShape(8.dp),
                            colors = itemEditorOutlinedTextFieldColors()
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemEditorFieldLabel(text = "Price Adjustment")
                        OutlinedTextField(
                            value = variantPriceDelta,
                            onValueChange = { variantPriceDelta = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. +2.00") },
                            singleLine = true,
                            isError = variantPriceDelta.isNotBlank() && !priceValid,
                            shape = RoundedCornerShape(8.dp),
                            colors = itemEditorOutlinedTextFieldColors()
                        )
                    }
                    if (showValidationError) {
                        Text(
                            text = when {
                                trimmedName.isEmpty() -> "Variant name is required."
                                isDuplicate -> "A variant with this name already exists."
                                !priceValid -> "Enter a valid amount, for example +2.00 or -1.50."
                                else -> ""
                            },
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = Color(0xFFB13A2F)
                        )
                    }
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = { addedVariant?.let(onAdded) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive)
                ) {
                    Text(
                        text = "Add",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorMuted
                    )
                }
            }
        }
    )
}

@Composable
private fun EditVariantDialog(
    variant: DraftVariant,
    existingVariants: List<DraftVariant>,
    displayOrder: Int,
    onDismiss: () -> Unit,
    onUpdateVariant: suspend (variantId: String, name: String, priceDelta: Double, displayOrder: Int) -> Result<Unit>,
    onUpdated: (DraftVariant) -> Unit
) {
    val scope = rememberCoroutineScope()
    var variantName by remember(variant) { mutableStateOf(variant.name) }
    var variantPriceDelta by remember(variant) { mutableStateOf(priceDeltaInputValue(variant.priceDeltaLabel)) }
    var attemptedSubmit by remember(variant) { mutableStateOf(false) }
    var status by remember(variant) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val trimmedName = variantName.trim()
    val priceValid = isValidPriceDelta(variantPriceDelta)
    val isDuplicate = trimmedName.isNotEmpty() &&
        existingVariants.any { it !== variant && it.name.equals(trimmedName, ignoreCase = true) }
    val valid = trimmedName.isNotEmpty() && priceValid && !isDuplicate
    val showValidationError = attemptedSubmit && !valid
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle

    fun submit() {
        if (!valid) {
            attemptedSubmit = true
        } else if (!isBusy) {
            status = DialogActionStatus.Loading("Saving")
            scope.launch {
                onUpdateVariant(variant.id!!, trimmedName, parsePriceDelta(variantPriceDelta), displayOrder).fold(
                    onSuccess = { status = DialogActionStatus.Success("$trimmedName saved") },
                    onFailure = { error ->
                        status = DialogActionStatus.Failed(message = error.message ?: "Could not save this variant.")
                    }
                )
            }
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        containerColor = Color.White,
        titleContentColor = ItemEditorInk,
        textContentColor = ItemEditorMuted,
        title = {
            Text(
                text = "Edit Variant",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = ItemEditorInk
            )
        },
        text = {
            if (isIdle) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemEditorFieldLabel(text = "Variant Name", required = true)
                        OutlinedTextField(
                            value = variantName,
                            onValueChange = { variantName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = showValidationError && (trimmedName.isEmpty() || isDuplicate),
                            shape = RoundedCornerShape(8.dp),
                            colors = itemEditorOutlinedTextFieldColors()
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemEditorFieldLabel(text = "Price Adjustment")
                        OutlinedTextField(
                            value = variantPriceDelta,
                            onValueChange = { variantPriceDelta = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. +2.00") },
                            singleLine = true,
                            isError = variantPriceDelta.isNotBlank() && !priceValid,
                            shape = RoundedCornerShape(8.dp),
                            colors = itemEditorOutlinedTextFieldColors()
                        )
                    }
                    if (showValidationError) {
                        Text(
                            text = when {
                                trimmedName.isEmpty() -> "Variant name is required."
                                isDuplicate -> "A variant with this name already exists."
                                !priceValid -> "Enter a valid amount, for example +2.00 or -1.50."
                                else -> ""
                            },
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = Color(0xFFB13A2F)
                        )
                    }
                }
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = {
                        onUpdated(DraftVariant(trimmedName, formatPriceDelta(variantPriceDelta), variant.id))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive)
                ) {
                    Text(
                        text = "Save",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorMuted
                    )
                }
            }
        }
    )
}

@Composable
private fun DeleteVariantDialog(
    variant: DraftVariant,
    onDismiss: () -> Unit,
    onDeleteVariant: suspend (variantId: String) -> Result<Unit>,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        containerColor = Color.White,
        titleContentColor = ItemEditorInk,
        textContentColor = ItemEditorMuted,
        title = {
            Text(
                text = "Delete ${variant.name}?",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = ItemEditorInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (isIdle) {
                Text(
                    text = "Are you sure you want to delete this variant?",
                    fontFamily = Inter(),
                    fontSize = 13.sp,
                    color = ItemEditorMuted
                )
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = onDeleted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = {
                        status = DialogActionStatus.Loading("Deleting")
                        scope.launch {
                            onDeleteVariant(variant.id!!).fold(
                                onSuccess = { status = DialogActionStatus.Removed("${variant.name} deleted") },
                                onFailure = { error ->
                                    status = DialogActionStatus.Failed(message = error.message ?: "Could not delete this variant.")
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                ) {
                    Text(
                        text = "Delete",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorMuted
                    )
                }
            }
        }
    )
}

@Composable
private fun DeleteOptionGroupDialog(
    group: DraftOptionGroup,
    onDismiss: () -> Unit,
    onDeleteGroup: suspend (group: DraftOptionGroup) -> Result<Unit>,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val isIdle = status is DialogActionStatus.Idle

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        containerColor = Color.White,
        titleContentColor = ItemEditorInk,
        textContentColor = ItemEditorMuted,
        title = {
            Text(
                text = "Delete ${group.name}?",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = ItemEditorInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (isIdle) {
                Text(
                    text = "Are you sure you want to delete this option group?",
                    fontFamily = Inter(),
                    fontSize = 13.sp,
                    color = ItemEditorMuted
                )
            } else {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = onDeleted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = {
                        status = DialogActionStatus.Loading("Deleting")
                        scope.launch {
                            onDeleteGroup(group).fold(
                                onSuccess = { status = DialogActionStatus.Removed("${group.name} deleted") },
                                onFailure = { error ->
                                    status = DialogActionStatus.Failed(message = error.message ?: "Could not delete this option group.")
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                ) {
                    Text(
                        text = "Delete",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorMuted
                    )
                }
            }
        }
    )
}

@Composable
internal fun OptionsEditorDialog(
    itemName: String,
    optionGroups: List<DraftOptionGroup>,
    inventoryRecipes: List<RecipeDto>,
    onDismiss: () -> Unit,
    onDoneEditing: (List<DraftOptionGroup>) -> Unit,
    onCreateGroup: suspend (group: DraftOptionGroup, displayOrder: Int) -> Result<DraftOptionGroup>,
    onUpdateGroup: suspend (existing: DraftOptionGroup, updated: DraftOptionGroup, displayOrder: Int) -> Result<DraftOptionGroup>,
    onDeleteGroup: suspend (group: DraftOptionGroup) -> Result<Unit>
) {
    var currentOptionGroups by remember { mutableStateOf(optionGroups) }
    var addOptionGroupOpen by remember { mutableStateOf(false) }
    var optionGroupBeingEdited by remember { mutableStateOf<DraftOptionGroup?>(null) }
    var optionGroupToDelete by remember { mutableStateOf<DraftOptionGroup?>(null) }

    MenuFormDialog(
        title = "Options",
        subtitle = itemName,
        onDismiss = { onDoneEditing(currentOptionGroups) },
        onSave = { onDoneEditing(currentOptionGroups) },
        saveLabel = "Done",
        showCancel = false,
        desktopWidth = 0.46f,
        desktopHeight = 0.72f,
        desktopMaxWidth = 480.dp
    ) { _ ->
        if (currentOptionGroups.isEmpty()) {
            Text(
                text = "No option groups yet. Add a group of choices below, like sauces or sides.",
                fontFamily = Inter(),
                fontSize = 13.sp,
                color = ItemEditorMuted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentOptionGroups.forEach { group ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.name,
                                modifier = Modifier.weight(1f),
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = ItemEditorInk
                            )
                            Text(
                                text = if (group.required) "Required" else "Optional",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (group.required) ItemEditorOlive else ItemEditorMuted
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(8.dp))
                                    .clickable { optionGroupBeingEdited = group },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit ${group.name}",
                                    modifier = Modifier.size(15.dp),
                                    tint = ItemEditorInk
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFB13A2F))
                                    .clickable { optionGroupToDelete = group },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove option group",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Text(
                            text = group.choices.joinToString(separator = "  •  ") { choice ->
                                if (choice.priceDeltaLabel.isNotBlank()) {
                                    "${choice.name} (${choice.priceDeltaLabel})"
                                } else {
                                    choice.name
                                }
                            },
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = ItemEditorMuted
                        )
                    }
                }
            }
        }

        // Outlined, matching the "+" add button used for sections — not a filled
        // color, so it doesn't compete with the primary Done action.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
                .border(1.dp, ItemEditorInk, RoundedCornerShape(9.dp))
                .clickable { addOptionGroupOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ItemEditorInk
            )
            Text(
                text = "Add Options",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = ItemEditorInk
            )
        }
    }

    if (addOptionGroupOpen) {
        OptionGroupEditorDialog(
            title = "Add option group",
            existingGroups = currentOptionGroups,
            displayOrder = currentOptionGroups.size,
            inventoryRecipes = inventoryRecipes,
            onDismiss = { addOptionGroupOpen = false },
            onSubmit = { draft, displayOrder -> onCreateGroup(draft, displayOrder) },
            onSaved = { newGroup ->
                currentOptionGroups = currentOptionGroups + newGroup
                addOptionGroupOpen = false
            }
        )
    }

    optionGroupBeingEdited?.let { editingGroup ->
        OptionGroupEditorDialog(
            title = "Edit option group",
            group = editingGroup,
            existingGroups = currentOptionGroups,
            displayOrder = currentOptionGroups.indexOfFirst { it === editingGroup }.coerceAtLeast(0),
            inventoryRecipes = inventoryRecipes,
            onDismiss = { optionGroupBeingEdited = null },
            onSubmit = { draft, displayOrder -> onUpdateGroup(editingGroup, draft, displayOrder) },
            onSaved = { updatedGroup ->
                currentOptionGroups = currentOptionGroups.map {
                    if (it === editingGroup) updatedGroup else it
                }
                optionGroupBeingEdited = null
            }
        )
    }

    optionGroupToDelete?.let { group ->
        DeleteOptionGroupDialog(
            group = group,
            onDismiss = { optionGroupToDelete = null },
            onDeleteGroup = onDeleteGroup,
            onDeleted = {
                currentOptionGroups = currentOptionGroups.filterNot { it === group }
                optionGroupToDelete = null
            }
        )
    }
}

@Composable
private fun OptionGroupEditorDialog(
    title: String,
    existingGroups: List<DraftOptionGroup>,
    displayOrder: Int,
    inventoryRecipes: List<RecipeDto>,
    onDismiss: () -> Unit,
    onSubmit: suspend (draft: DraftOptionGroup, displayOrder: Int) -> Result<DraftOptionGroup>,
    onSaved: (DraftOptionGroup) -> Unit,
    group: DraftOptionGroup? = null
) {
    val isPhoneLayout = isPhoneMenuWindow()
    val scope = rememberCoroutineScope()
    var groupName by remember(group) { mutableStateOf(group?.name.orEmpty()) }
    var groupRequired by remember(group) { mutableStateOf(group?.required ?: true) }
    var choiceDrafts by remember(group) {
        mutableStateOf(
            group?.choices?.map { choice ->
                OptionChoiceInput(
                    name = choice.name,
                    priceDelta = priceDeltaInputValue(choice.priceDeltaLabel),
                    inventoryRecipeId = choice.inventoryRecipeId,
                    inventoryRecipeQuantity = choice.inventoryRecipeQuantity?.toString().orEmpty()
                )
            }?.takeIf { it.isNotEmpty() } ?: listOf(OptionChoiceInput("", ""))
        )
    }
    var attemptedSubmit by remember(group) { mutableStateOf(false) }
    var status by remember(group) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    var savedGroup by remember(group) { mutableStateOf<DraftOptionGroup?>(null) }
    val isBusy = status is DialogActionStatus.Loading
    val isIdle = status is DialogActionStatus.Idle
    val trimmedName = groupName.trim()
    val validChoices = choiceDrafts.filter { it.name.isNotBlank() }
    val choicesValid = choiceDrafts.all { isValidPriceDelta(it.priceDelta) }
    val recipeQuantitiesValid = choiceDrafts.all { choice ->
        choice.inventoryRecipeId == null || isValidInventoryRecipeQuantity(choice.inventoryRecipeQuantity)
    }
    val duplicateGroup = trimmedName.isNotBlank() &&
        existingGroups.any { it !== group && it.name.equals(trimmedName, ignoreCase = true) }
    val choiceNames = validChoices.map { it.name.trim().lowercase() }
    val duplicateChoice = choiceNames.size != choiceNames.distinct().size
    val canSave = trimmedName.length in 2..60 && validChoices.isNotEmpty() &&
        choicesValid && recipeQuantitiesValid && !duplicateGroup && !duplicateChoice
    val validationMessage = when {
        trimmedName.isEmpty() -> "Option group name is required."
        trimmedName.length < 2 -> "Use at least 2 characters for the group name."
        duplicateGroup -> "An option group with this name already exists."
        validChoices.isEmpty() -> "Add at least one choice."
        duplicateChoice -> "Choice names in the same group must be unique."
        !choicesValid -> "Use valid price adjustments, for example +2.00 or -1.50."
        !recipeQuantitiesValid -> "Enter a positive amount for every option that uses a preparation."
        else -> null
    }

    fun saveGroup() {
        if (!canSave) {
            attemptedSubmit = true
            return
        }
        if (isBusy) return
        val draft = DraftOptionGroup(
            name = trimmedName,
            required = groupRequired,
            choices = validChoices.map { choice ->
                DraftOptionChoice(
                    name = choice.name.trim(),
                    priceDeltaLabel = formatPriceDelta(choice.priceDelta),
                    inventoryRecipeId = choice.inventoryRecipeId,
                    inventoryRecipeQuantity = choice.inventoryRecipeQuantity.trim().toDoubleOrNull()
                )
            }
        )
        status = DialogActionStatus.Loading(if (group == null) "Adding" else "Saving")
        scope.launch {
            onSubmit(draft, displayOrder).fold(
                onSuccess = { saved ->
                    savedGroup = saved
                    status = DialogActionStatus.Success("$trimmedName ${if (group == null) "added" else "saved"}")
                },
                onFailure = { error ->
                    status = DialogActionStatus.Failed(
                        message = error.message ?: "Could not save this option group."
                    )
                }
            )
        }
    }

    MenuNestedDialog(
        onDismissRequest = { if (isIdle) onDismiss() },
        containerColor = Color.White,
        titleContentColor = ItemEditorInk,
        textContentColor = ItemEditorMuted,
        title = {
            Text(
                text = title,
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = ItemEditorInk
            )
        },
        text = {
            if (!isIdle) {
                DialogStatusBody(
                    status = status,
                    onRetry = { status = DialogActionStatus.Idle },
                    onCancel = onDismiss,
                    onSuccessSettled = { savedGroup?.let(onSaved) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
            Column(
                modifier = if (isPhoneLayout) {
                    Modifier
                } else {
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create a choice group guests can pick from, like sauces, sides, or doneness.",
                    fontFamily = Inter(),
                    fontSize = 13.sp,
                    color = ItemEditorMuted
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemEditorFieldLabel(text = "Group Name", required = true)
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { if (it.length <= 60) groupName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Choose your sauce") },
                        singleLine = true,
                        isError = attemptedSubmit && (trimmedName.length < 2 || duplicateGroup),
                        shape = RoundedCornerShape(8.dp),
                        colors = itemEditorOutlinedTextFieldColors()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(ItemEditorSurface)
                        .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (groupRequired) "Required" else "Optional",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorInk
                        )
                        Text(
                            text = if (groupRequired) {
                                "Guests must choose from this group."
                            } else {
                                "Guests may skip this group."
                            },
                            fontFamily = Inter(),
                            fontSize = 11.sp,
                            color = ItemEditorMuted
                        )
                    }
                    Switch(
                        checked = groupRequired,
                        onCheckedChange = { groupRequired = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ItemEditorOlive
                        )
                    )
                }

                ItemEditorFieldLabel(text = "Choices", required = true)

                choiceDrafts.forEachIndexed { index, choice ->
                    OptionChoiceEditorRow(
                        choice = choice,
                        isPhone = isPhoneLayout,
                        canRemove = choiceDrafts.size > 1,
                        onNameChange = { newValue ->
                            choiceDrafts = choiceDrafts.toMutableList().also {
                                it[index] = it[index].copy(name = newValue)
                            }
                        },
                        onPriceChange = { newValue ->
                            choiceDrafts = choiceDrafts.toMutableList().also {
                                it[index] = it[index].copy(priceDelta = newValue)
                            }
                        },
                        inventoryRecipes = inventoryRecipes,
                        onInventoryRecipeChange = { recipe ->
                            choiceDrafts = choiceDrafts.toMutableList().also {
                                val current = it[index]
                                it[index] = current.copy(
                                    inventoryRecipeId = recipe?.id,
                                    inventoryRecipeQuantity = if (recipe == null) "" else {
                                        current.inventoryRecipeQuantity.ifBlank { recipe.yieldQuantity?.value.orEmpty() }
                                    }
                                )
                            }
                        },
                        onInventoryRecipeQuantityChange = { newValue ->
                            choiceDrafts = choiceDrafts.toMutableList().also {
                                it[index] = it[index].copy(inventoryRecipeQuantity = newValue)
                            }
                        },
                        onRemove = {
                            if (choiceDrafts.size > 1) {
                                choiceDrafts = choiceDrafts.filterIndexed { i, _ -> i != index }
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ItemEditorOlive)
                        .clickable { choiceDrafts = choiceDrafts + OptionChoiceInput("", "") }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Add another choice",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                if (attemptedSubmit && validationMessage != null) {
                    Text(
                        text = validationMessage,
                        fontFamily = Inter(),
                        fontSize = 12.sp,
                        color = Color(0xFFB13A2F)
                    )
                }
            }
            }
        },
        confirmButton = {
            if (isIdle) {
                Button(
                    onClick = { saveGroup() },
                    enabled = canSave || !attemptedSubmit,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive)
                ) {
                    Text(
                        text = if (group == null) "Add" else "Save",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            if (isIdle) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorMuted
                    )
                }
            }
        }
    )
}

@Composable
private fun OptionChoiceEditorRow(
    choice: OptionChoiceInput,
    isPhone: Boolean,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    inventoryRecipes: List<RecipeDto>,
    onInventoryRecipeChange: (RecipeDto?) -> Unit,
    onInventoryRecipeQuantityChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    var recipeMenuOpen by remember(choice.inventoryRecipeId) { mutableStateOf(false) }
    val selectedRecipe = inventoryRecipes.firstOrNull { it.id == choice.inventoryRecipeId }
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isPhone) {
            OutlinedTextField(
                value = choice.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Choice name") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = itemEditorOutlinedTextFieldColors()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = choice.priceDelta,
                    onValueChange = onPriceChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Price adjustment (+0.00)") },
                    singleLine = true,
                    isError = choice.priceDelta.isNotBlank() && !isValidPriceDelta(choice.priceDelta),
                    shape = RoundedCornerShape(8.dp),
                    colors = itemEditorOutlinedTextFieldColors()
                )
                IconButton(
                    onClick = onRemove,
                    enabled = canRemove,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Outlined.Close, "Remove choice", tint = ItemEditorMuted)
                }
            }
        }
        if (!isPhone) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = choice.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1.4f),
                    placeholder = { Text("Choice name") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = itemEditorOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = choice.priceDelta,
                    onValueChange = onPriceChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("+0.00") },
                    singleLine = true,
                    isError = choice.priceDelta.isNotBlank() && !isValidPriceDelta(choice.priceDelta),
                    shape = RoundedCornerShape(8.dp),
                    colors = itemEditorOutlinedTextFieldColors()
                )
                IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, "Remove choice", modifier = Modifier.size(15.dp), tint = ItemEditorMuted)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { recipeMenuOpen = true }) {
                    Text(
                        text = when {
                            selectedRecipe != null -> "Uses ${selectedRecipe.name}"
                            choice.inventoryRecipeId != null -> "Linked preparation unavailable · select or clear"
                            else -> "No preparation"
                        },
                        fontFamily = Inter(),
                        color = ItemEditorOlive
                    )
                }
                DropdownMenu(expanded = recipeMenuOpen, onDismissRequest = { recipeMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("No preparation") },
                        onClick = { recipeMenuOpen = false; onInventoryRecipeChange(null) }
                    )
                    inventoryRecipes.forEach { recipe ->
                        DropdownMenuItem(
                            text = { Text("${recipe.name} · ${recipe.yieldUnit.orEmpty()}") },
                            onClick = { recipeMenuOpen = false; onInventoryRecipeChange(recipe) }
                        )
                    }
                    if (inventoryRecipes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No active preparations (Admin Hub → Inventory → Preparations)") },
                            enabled = false,
                            onClick = { recipeMenuOpen = false }
                        )
                    }
                }
            }
            if (selectedRecipe != null) {
                OutlinedTextField(
                    value = choice.inventoryRecipeQuantity,
                    onValueChange = onInventoryRecipeQuantityChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Usage (${selectedRecipe.yieldUnit.orEmpty()})") },
                    singleLine = true,
                    isError = choice.inventoryRecipeQuantity.isNotBlank()
                            && !isValidInventoryRecipeQuantity(choice.inventoryRecipeQuantity),
                    shape = RoundedCornerShape(8.dp),
                    colors = itemEditorOutlinedTextFieldColors()
                )
            }
        }
    }
}

@Composable
private fun ItemEditorFieldLabel(
    text: String,
    required: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = ItemEditorInk
        )
        if (required) {
            Text(
                text = " *",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFD6453D)
            )
        }
    }
}

@Composable
private fun itemEditorOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Black,
    unfocusedBorderColor = Color.Black,
    errorBorderColor = Color(0xFFB13A2F),
    focusedPlaceholderColor = ItemEditorPlaceholder,
    unfocusedPlaceholderColor = ItemEditorPlaceholder
)

private fun isValidItemPrice(value: String): Boolean {
    return Regex("^\\d+(\\.\\d{1,2})?$").matches(value.trim())
}

private fun formatItemPrice(raw: String): String {
    val value = raw.trim().toDoubleOrNull() ?: 0.0
    val cents = round(value * 100).toLong()
    val dollars = cents / 100
    val remainder = if (cents % 100 < 0) -(cents % 100) else cents % 100
    val centsText = if (remainder < 10) "0$remainder" else "$remainder"
    return "$$dollars.$centsText"
}

private fun extractPriceValue(priceLabel: String): String {
    return Regex("\\d+(\\.\\d{1,2})?").find(priceLabel)?.value.orEmpty()
}

private fun isValidPriceDelta(value: String): Boolean {
    if (value.isBlank()) return true
    return Regex("^[+-]?\\d+(\\.\\d{1,2})?$").matches(value.trim())
}

private fun parsePriceDelta(raw: String): Double {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return 0.0
    val negative = trimmed.startsWith("-")
    val numberPart = trimmed.removePrefix("+").removePrefix("-")
    val value = numberPart.toDoubleOrNull() ?: return 0.0
    return if (negative) -value else value
}

private fun formatPriceDelta(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val negative = trimmed.startsWith("-")
    val numberPart = trimmed.removePrefix("+").removePrefix("-")
    val value = numberPart.toDoubleOrNull() ?: return ""
    val cents = round(value * 100).toLong()
    val dollars = cents / 100
    val remainder = if (cents % 100 < 0) -(cents % 100) else cents % 100
    val centsText = if (remainder < 10) "0$remainder" else "$remainder"
    val sign = if (negative) "-" else "+"
    return "$sign$$dollars.$centsText"
}

private fun priceDeltaInputValue(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isBlank()) return ""
    val sign = when {
        trimmed.startsWith("-") -> "-"
        trimmed.startsWith("+") -> "+"
        else -> ""
    }
    val number = trimmed.filter { it.isDigit() || it == '.' }
    return if (number.isBlank()) "" else sign + number
}

// Occasion extras (a cake, decoration…): how early they must be ordered, and which occasions offer them.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ItemExtrasFields(
    hours: String,
    onHoursChange: (String) -> Unit,
    occasions: List<Pair<String, String>>,
    picked: List<String>,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Occasion extra", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF222426))
        OutlinedTextField(
            value = hours,
            onValueChange = onHoursChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Order at least … hours ahead (empty: any time)") },
            placeholder = { Text("e.g. 48 for a cake") },
            singleLine = true,
            shape = RoundedCornerShape(9.dp),
            colors = itemEditorOutlinedTextFieldColors()
        )
        if (occasions.isNotEmpty()) {
            Text("Offered for (none picked: every occasion)", fontFamily = Inter(), fontSize = 12.sp, color = Color(0xFF747572))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                occasions.forEach { (code, label) ->
                    val selected = code in picked
                    androidx.compose.material3.Surface(
                        onClick = { onToggle(code) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color(0xFF4F7942) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF4F7942) else Color(0xFFE3E6E1))
                    ) {
                        Text(
                            label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            color = if (selected) Color.White else Color(0xFF222426)
                        )
                    }
                }
            }
        }
    }
}
