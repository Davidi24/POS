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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogActionStatus
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogStatusBody
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuFormDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuFormFieldPair
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.launch
import kotlin.math.round
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val ItemEditorOlive = Color(0xFF4F7942)
private val ItemEditorInk = Color(0xFF242522)
private val ItemEditorMuted = Color(0xFF71736E)
private val ItemEditorBorder = Color(0xFFE2E3DE)
private val ItemEditorSurface = Color(0xFFF7F7F5)
private val ItemEditorPlaceholder = Color(0xFFC7C8C2)

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
    val id: String? = null
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
    val available: Boolean,
    val ingredients: List<DraftIngredient> = emptyList()
)

private data class MockIngredient(
    val id: String,
    val name: String,
    val defaultUnit: String,
    val category: String
)

private data class SelectedIngredientRow(
    val id: String,
    val name: String,
    val quantity: String,
    val unit: String
)

private data class OptionChoiceInput(
    val name: String,
    val priceDelta: String
)

private val MockIngredients = listOf(
    MockIngredient("tomato", "Tomato", "g", "Produce"),
    MockIngredient("basil", "Fresh Basil", "g", "Produce"),
    MockIngredient("mozzarella", "Mozzarella", "g", "Dairy"),
    MockIngredient("olive_oil", "Olive Oil", "ml", "Pantry"),
    MockIngredient("garlic", "Garlic", "g", "Produce"),
    MockIngredient("flour", "00 Flour", "g", "Pantry"),
    MockIngredient("parmesan", "Parmesan", "g", "Dairy"),
    MockIngredient("black_pepper", "Black Pepper", "g", "Spices"),
    MockIngredient("salt", "Salt", "g", "Spices"),
    MockIngredient("chicken_breast", "Chicken Breast", "g", "Meat"),
    MockIngredient("ground_beef", "Ground Beef", "g", "Meat"),
    MockIngredient("onion", "Onion", "g", "Produce"),
    MockIngredient("butter", "Butter", "g", "Dairy"),
    MockIngredient("heavy_cream", "Heavy Cream", "ml", "Dairy"),
    MockIngredient("sugar", "Sugar", "g", "Pantry")
)

private val IngredientCategories = listOf(
    "All", "Produce", "Dairy", "Meat", "Pantry", "Spices"
)

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
    onDismiss: () -> Unit,
    onDeleteItem: (() -> Unit)? = null,
    onSave: (
        name: String,
        priceLabel: String,
        sku: String?,
        description: String?,
        imageFileName: String?,
        available: Boolean,
        ingredients: List<DraftIngredient>,
        sectionId: String?
    ) -> Unit
) {
    val isEditing = existingItem != null
    val movableSections = remember(sections) { sections.filter { it.name != "All" && it.id != null } }
    var selectedSectionId by remember(currentSectionId) { mutableStateOf(currentSectionId) }

    var name by remember { mutableStateOf(existingItem?.name.orEmpty()) }
    var basePrice by remember { mutableStateOf(existingItem?.priceLabel?.let { extractPriceValue(it) }.orEmpty()) }
    var sku by remember { mutableStateOf(existingItem?.sku.orEmpty()) }
    var description by remember { mutableStateOf(existingItem?.description.orEmpty()) }
    var available by remember { mutableStateOf(existingItem?.available ?: true) }
    var selectedImageName by remember { mutableStateOf(existingItem?.imageFileName) }
    var ingredientSearchOpen by remember { mutableStateOf(false) }
    var pendingIngredient by remember { mutableStateOf<MockIngredient?>(null) }
    var selectedIngredients by remember {
        mutableStateOf(
            existingItem?.ingredients?.map { draft ->
                val matchedId = MockIngredients.firstOrNull { it.name.equals(draft.name, ignoreCase = true) }?.id
                    ?: draft.name.lowercase().replace(" ", "_")
                SelectedIngredientRow(id = matchedId, name = draft.name, quantity = draft.quantity, unit = draft.unit)
            } ?: emptyList<SelectedIngredientRow>()
        )
    }

    val priceIsValid = isValidItemPrice(basePrice)
    val canSave = name.isNotBlank() && priceIsValid

    val formScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) {
            scope.launch {
                selectedImageName = file.name
            }
        }
    }

    LaunchedEffect(selectedIngredients.size) {
        if (selectedIngredients.isNotEmpty()) {
            formScrollState.animateScrollTo(formScrollState.maxValue)
        }
    }

    MenuFormDialog(
        title = if (isEditing) "Edit Item" else "Add New Item",
        subtitle = if (!isEditing) sectionName?.let { "Adding to $it" } else null,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                name.trim(),
                formatItemPrice(basePrice),
                sku.trim().takeIf { it.isNotEmpty() },
                description.trim().takeIf { it.isNotEmpty() },
                selectedImageName,
                available,
                selectedIngredients.map { DraftIngredient(it.name, it.quantity, it.unit) },
                if (isEditing && selectedSectionId != currentSectionId) selectedSectionId else null
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
            if (selectedImageName != null) {
                Image(
                    painter = painterResource(Res.drawable.auth_login_img),
                    contentDescription = "Current item photo",
                    modifier = Modifier
                        .size(width = 64.dp, height = 52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(ItemEditorSurface)
                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                    .clickable { imagePicker.launch() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ItemEditorOlive
                )
                Text(
                    text = if (selectedImageName != null) "Change Image" else "Import Image",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = ItemEditorOlive
                )
            }
            if (!isPhone) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = selectedImageName ?: "No image selected",
                    fontFamily = Inter(),
                    fontSize = 12.sp,
                    color = if (selectedImageName != null) ItemEditorInk else ItemEditorMuted,
                    maxLines = 1
                )
            }
        }

        if (isPhone) {
            Text(
                text = selectedImageName ?: "No image selected",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = Inter(),
                fontSize = 12.sp,
                color = ItemEditorMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        ItemEditorFieldLabel(text = "Recipe / Ingredients")

        if (selectedIngredients.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedIngredients.forEach { ingredient ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.auth_login_img),
                            contentDescription = ingredient.name,
                            modifier = Modifier
                                .size(width = 44.dp, height = 36.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = ingredient.name,
                            modifier = Modifier.weight(1f),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorInk
                        )
                        Text(
                            text = "${ingredient.quantity} ${ingredient.unit}".trim(),
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = ItemEditorMuted
                        )
                        Spacer(Modifier.width(10.dp))
                        IconButton(
                            onClick = {
                                selectedIngredients = selectedIngredients.filterNot { it.id == ingredient.id }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove ingredient",
                                modifier = Modifier.size(15.dp),
                                tint = ItemEditorMuted
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(ItemEditorSurface)
                .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                .clickable { ingredientSearchOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ItemEditorOlive
            )
            Text(
                text = "Add Ingredient",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = ItemEditorOlive
            )
        }
    }

    if (ingredientSearchOpen) {
        IngredientCommandPalette(
            alreadySelectedIds = selectedIngredients.map { it.id }.toSet(),
            onDismiss = { ingredientSearchOpen = false },
            onSelect = { suggestion ->
                ingredientSearchOpen = false
                pendingIngredient = suggestion
            }
        )
    }

    pendingIngredient?.let { ingredient ->
        var popupQuantity by remember(ingredient) { mutableStateOf("") }
        // Locked to the ingredient's inventory unit — there's no unit-conversion logic
        // anywhere in the app, so letting a recipe use a different unit than the one
        // Inventory tracks stock in would silently break stock deduction, cost-per-dish,
        // and low-stock alerts. If unit conversion gets built later, this can reopen.
        val popupUnit = ingredient.defaultUnit

        MenuNestedDialog(
            onDismissRequest = { pendingIngredient = null },
            containerColor = Color.White,
            titleContentColor = ItemEditorInk,
            textContentColor = ItemEditorMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.auth_login_img),
                        contentDescription = ingredient.name,
                        modifier = Modifier
                            .size(width = 44.dp, height = 36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = ingredient.name,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ItemEditorInk
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the quantity used in this recipe.",
                        fontFamily = Inter(),
                        fontSize = 13.sp,
                        color = ItemEditorMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ItemEditorFieldLabel(text = "Quantity", required = true)
                            OutlinedTextField(
                                value = popupQuantity,
                                onValueChange = { popupQuantity = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Qty") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = itemEditorOutlinedTextFieldColors()
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ItemEditorFieldLabel(text = "Unit", required = true)
                            // Read-only — set by this ingredient's Inventory unit, not
                            // editable here (see note above).
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ItemEditorSurface)
                                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = popupUnit,
                                    modifier = Modifier.weight(1f),
                                    fontFamily = Inter(),
                                    fontSize = 14.sp,
                                    color = ItemEditorMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedIngredients = selectedIngredients + SelectedIngredientRow(
                            id = ingredient.id,
                            name = ingredient.name,
                            quantity = popupQuantity,
                            unit = popupUnit
                        )
                        pendingIngredient = null
                    },
                    enabled = popupQuantity.isNotBlank(),
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
            },
            dismissButton = {
                TextButton(onClick = { pendingIngredient = null }) {
                    Text(
                        text = "Cancel",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorMuted
                    )
                }
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
                OptionChoiceInput(choice.name, priceDeltaInputValue(choice.priceDeltaLabel))
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
    val duplicateGroup = trimmedName.isNotBlank() &&
        existingGroups.any { it !== group && it.name.equals(trimmedName, ignoreCase = true) }
    val choiceNames = validChoices.map { it.name.trim().lowercase() }
    val duplicateChoice = choiceNames.size != choiceNames.distinct().size
    val canSave = trimmedName.length in 2..60 && validChoices.isNotEmpty() &&
        choicesValid && !duplicateGroup && !duplicateChoice
    val validationMessage = when {
        trimmedName.isEmpty() -> "Option group name is required."
        trimmedName.length < 2 -> "Use at least 2 characters for the group name."
        duplicateGroup -> "An option group with this name already exists."
        validChoices.isEmpty() -> "Add at least one choice."
        duplicateChoice -> "Choice names in the same group must be unique."
        !choicesValid -> "Use valid price adjustments, for example +2.00 or -1.50."
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
                    priceDeltaLabel = formatPriceDelta(choice.priceDelta)
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
    onRemove: () -> Unit
) {
    if (isPhone) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
    } else {
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
            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove choice",
                    modifier = Modifier.size(15.dp),
                    tint = ItemEditorMuted
                )
            }
        }
    }
}

@Composable
private fun IngredientCommandPalette(
    alreadySelectedIds: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (MockIngredient) -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val focusRequester = remember { FocusRequester() }
    val results = remember(query, alreadySelectedIds, selectedCategory) {
        val available = MockIngredients.filter { it.id !in alreadySelectedIds }
        val inCategory = if (selectedCategory == "All") {
            available
        } else {
            available.filter { it.category == selectedCategory }
        }
        if (query.isBlank()) {
            inCategory
        } else {
            inCategory.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isPhone) 16.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(minOf(maxWidth, 560.dp))
                    .heightIn(max = if (isPhone) minOf(480.dp, maxHeight * 0.9f) else 480.dp)
                    .shadow(24.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ItemEditorMuted
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .padding(vertical = 14.dp),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = Inter(),
                            fontSize = 15.sp,
                            color = ItemEditorInk
                        ),
                        cursorBrush = SolidColor(ItemEditorInk),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search inventory ingredients",
                                    fontFamily = Inter(),
                                    fontSize = 15.sp,
                                    color = ItemEditorPlaceholder
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                HorizontalDivider(color = ItemEditorBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IngredientCategories.forEach { category ->
                        val isSelected = category == selectedCategory
                        Text(
                            text = category,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) ItemEditorOlive else ItemEditorSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) ItemEditorOlive else ItemEditorBorder,
                                    RoundedCornerShape(50)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else ItemEditorInk
                        )
                    }
                }

                HorizontalDivider(color = ItemEditorBorder)

                if (results.isEmpty()) {
                    Text(
                        text = "Ingredient not found? Manage ingredients in Inventory →",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { })
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ItemEditorOlive
                    )
                } else {
                    Text(
                        text = "INGREDIENTS",
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ItemEditorMuted
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        results.forEach { ingredient ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(ingredient) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.auth_login_img),
                                    contentDescription = ingredient.name,
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 36.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = ingredient.name,
                                    modifier = Modifier.weight(1f),
                                    fontFamily = Inter(),
                                    fontSize = 14.sp,
                                    color = ItemEditorInk
                                )
                                Text(
                                    text = ingredient.defaultUnit,
                                    fontFamily = Inter(),
                                    fontSize = 12.sp,
                                    color = ItemEditorMuted
                                )
                            }
                        }
                    }
                }
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
