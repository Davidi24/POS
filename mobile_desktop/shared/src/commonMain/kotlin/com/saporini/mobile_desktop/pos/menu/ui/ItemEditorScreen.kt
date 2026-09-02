package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.launch
import kotlin.math.round
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val ItemEditorOlive = Color(0xFF94A27F)
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
    val priceDeltaLabel: String
)

data class DraftOptionChoice(
    val name: String,
    val priceDeltaLabel: String
)

data class DraftOptionGroup(
    val name: String,
    val required: Boolean,
    val choices: List<DraftOptionChoice>
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

private val ItemEditorUnitOptions = listOf(
    "g", "kg", "ml", "l", "pcs", "oz", "lb", "tbsp", "tsp"
)

private val IngredientCategories = listOf(
    "All", "Produce", "Dairy", "Meat", "Pantry", "Spices"
)

@Composable
fun ItemEditorDialog(
    existingItem: EditableMenuItem? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        priceLabel: String,
        sku: String?,
        description: String?,
        imageFileName: String?,
        available: Boolean,
        ingredients: List<DraftIngredient>
    ) -> Unit
) {
    val isEditing = existingItem != null

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.9f)
                    .widthIn(max = 620.dp)
                    .shadow(24.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Item" else "Add New Item",
                        modifier = Modifier.weight(1f),
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF232422)
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close item editor",
                            modifier = Modifier.size(18.dp),
                            tint = ItemEditorInk
                        )
                    }
                }

                HorizontalDivider(color = ItemEditorBorder)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(formScrollState)
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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

                        Column(modifier = Modifier.weight(1f)) {
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
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = selectedImageName ?: "No image selected",
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = if (selectedImageName != null) ItemEditorInk else ItemEditorMuted,
                            maxLines = 1
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

                HorizontalDivider(color = ItemEditorBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            color = ItemEditorMuted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSave(
                                name.trim(),
                                formatItemPrice(basePrice),
                                sku.trim().takeIf { it.isNotEmpty() },
                                description.trim().takeIf { it.isNotEmpty() },
                                selectedImageName,
                                available,
                                selectedIngredients.map { row ->
                                    DraftIngredient(row.name, row.quantity, row.unit)
                                }
                            )
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isEditing) "Save Changes" else "Add Item",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
        var popupUnit by remember(ingredient) { mutableStateOf(ingredient.defaultUnit) }
        var unitDropdownOpen by remember(ingredient) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingIngredient = null },
            shape = RoundedCornerShape(16.dp),
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                    .clickable { unitDropdownOpen = !unitDropdownOpen }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = popupUnit,
                                    modifier = Modifier.weight(1f),
                                    fontFamily = Inter(),
                                    fontSize = 14.sp,
                                    color = ItemEditorInk
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Choose unit",
                                    modifier = Modifier.size(18.dp),
                                    tint = ItemEditorMuted
                                )
                            }
                        }
                    }
                    if (unitDropdownOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ItemEditorSurface)
                                .border(1.dp, ItemEditorBorder, RoundedCornerShape(8.dp))
                                .padding(vertical = 4.dp)
                        ) {
                            ItemEditorUnitOptions.forEach { unitOption ->
                                Text(
                                    text = unitOption,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            popupUnit = unitOption
                                            unitDropdownOpen = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    fontFamily = Inter(),
                                    fontSize = 14.sp,
                                    color = if (unitOption == popupUnit) ItemEditorOlive else ItemEditorInk
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedIngredients = selectedIngredients + SelectedIngredientRow(
                            id = ingredient.id,
                            name = ingredient.name,
                            quantity = popupQuantity,
                            unit = popupUnit
                        )
                        pendingIngredient = null
                    },
                    enabled = popupQuantity.isNotBlank()
                ) {
                    Text(
                        text = "Add",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorOlive
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
fun VariantEditorDialog(
    itemName: String,
    variants: List<DraftVariant>,
    onDismiss: () -> Unit,
    onSave: (List<DraftVariant>) -> Unit
) {
    var currentVariants by remember { mutableStateOf(variants) }
    var addVariantOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .fillMaxHeight(0.72f)
                    .widthIn(max = 480.dp)
                    .shadow(24.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Variants",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF232422)
                        )
                        Text(
                            text = itemName,
                            fontFamily = Inter(),
                            fontSize = 13.sp,
                            color = ItemEditorMuted,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close variant editor",
                            modifier = Modifier.size(18.dp),
                            tint = ItemEditorInk
                        )
                    }
                }

                HorizontalDivider(color = ItemEditorBorder)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                    Text(
                                        text = variant.name,
                                        modifier = Modifier.weight(1f),
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
                                    Spacer(Modifier.width(10.dp))
                                    IconButton(
                                        onClick = {
                                            currentVariants = currentVariants.filterNot { it.name == variant.name }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Remove variant",
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
                            .clickable { addVariantOpen = true }
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
                            text = "Add Variant",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorOlive
                        )
                    }
                }

                HorizontalDivider(color = ItemEditorBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            color = ItemEditorMuted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSave(currentVariants) },
                        colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Save Changes",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (addVariantOpen) {
        var variantName by remember { mutableStateOf("") }
        var variantPriceDelta by remember { mutableStateOf("") }
        val variantPriceValid = isValidPriceDelta(variantPriceDelta)

        AlertDialog(
            onDismissRequest = { addVariantOpen = false },
            shape = RoundedCornerShape(16.dp),
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
                            isError = variantPriceDelta.isNotBlank() && !variantPriceValid,
                            shape = RoundedCornerShape(8.dp),
                            colors = itemEditorOutlinedTextFieldColors()
                        )
                    }
                    if (variantPriceDelta.isNotBlank() && !variantPriceValid) {
                        Text(
                            text = "Enter a valid amount, for example +2.00 or -1.50.",
                            fontFamily = Inter(),
                            fontSize = 12.sp,
                            color = Color(0xFFB13A2F)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentVariants = currentVariants + DraftVariant(
                            name = variantName.trim(),
                            priceDeltaLabel = formatPriceDelta(variantPriceDelta)
                        )
                        addVariantOpen = false
                    },
                    enabled = variantName.isNotBlank() && variantPriceValid
                ) {
                    Text(
                        text = "Add",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorOlive
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { addVariantOpen = false }) {
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
fun OptionsEditorDialog(
    itemName: String,
    optionGroups: List<DraftOptionGroup>,
    onDismiss: () -> Unit,
    onSave: (List<DraftOptionGroup>) -> Unit
) {
    var currentOptionGroups by remember { mutableStateOf(optionGroups) }
    var addOptionGroupOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .fillMaxHeight(0.72f)
                    .widthIn(max = 480.dp)
                    .shadow(24.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, ItemEditorBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Options",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF232422)
                        )
                        Text(
                            text = itemName,
                            fontFamily = Inter(),
                            fontSize = 13.sp,
                            color = ItemEditorMuted,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close options editor",
                            modifier = Modifier.size(18.dp),
                            tint = ItemEditorInk
                        )
                    }
                }

                HorizontalDivider(color = ItemEditorBorder)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                        IconButton(
                                            onClick = {
                                                currentOptionGroups = currentOptionGroups.filterNot { it.name == group.name }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Remove option group",
                                                modifier = Modifier.size(15.dp),
                                                tint = ItemEditorMuted
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

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(ItemEditorSurface)
                            .border(1.dp, ItemEditorBorder, RoundedCornerShape(9.dp))
                            .clickable { addOptionGroupOpen = true }
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
                            text = "Add Options",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorOlive
                        )
                    }
                }

                HorizontalDivider(color = ItemEditorBorder)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            color = ItemEditorMuted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSave(currentOptionGroups) },
                        colors = ButtonDefaults.buttonColors(containerColor = ItemEditorOlive),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Save Changes",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (addOptionGroupOpen) {
        var groupName by remember { mutableStateOf("") }
        var groupRequired by remember { mutableStateOf(true) }
        var choiceDrafts by remember { mutableStateOf(listOf(OptionChoiceInput("", ""))) }
        val validChoices = choiceDrafts.filter { it.name.isNotBlank() }
        val choicesValid = choiceDrafts.all { isValidPriceDelta(it.priceDelta) }
        val canAddGroup = groupName.isNotBlank() && validChoices.isNotEmpty() && choicesValid

        AlertDialog(
            onDismissRequest = { addOptionGroupOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = ItemEditorInk,
            textContentColor = ItemEditorMuted,
            title = {
                Text(
                    text = "Add Options",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ItemEditorInk
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "A group of choices the guest picks from, like sauces or sides.",
                        fontFamily = Inter(),
                        fontSize = 13.sp,
                        color = ItemEditorMuted
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemEditorFieldLabel(text = "Group Name", required = true)
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Choose your sauce") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = itemEditorOutlinedTextFieldColors()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (groupRequired) "Required" else "Optional",
                            modifier = Modifier.weight(1f),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ItemEditorInk
                        )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = choice.name,
                                onValueChange = { newValue ->
                                    choiceDrafts = choiceDrafts.toMutableList().also {
                                        it[index] = it[index].copy(name = newValue)
                                    }
                                },
                                modifier = Modifier.weight(1.4f),
                                placeholder = { Text("Choice name") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = itemEditorOutlinedTextFieldColors()
                            )
                            OutlinedTextField(
                                value = choice.priceDelta,
                                onValueChange = { newValue ->
                                    choiceDrafts = choiceDrafts.toMutableList().also {
                                        it[index] = it[index].copy(priceDelta = newValue)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("+0.00") },
                                singleLine = true,
                                isError = choice.priceDelta.isNotBlank() && !isValidPriceDelta(choice.priceDelta),
                                shape = RoundedCornerShape(8.dp),
                                colors = itemEditorOutlinedTextFieldColors()
                            )
                            IconButton(
                                onClick = {
                                    if (choiceDrafts.size > 1) {
                                        choiceDrafts = choiceDrafts.filterIndexed { i, _ -> i != index }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
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

                    Row(
                        modifier = Modifier
                            .clickable { choiceDrafts = choiceDrafts + OptionChoiceInput("", "") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ItemEditorOlive
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add another choice",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = ItemEditorOlive
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentOptionGroups = currentOptionGroups + DraftOptionGroup(
                            name = groupName.trim(),
                            required = groupRequired,
                            choices = validChoices.map { choice ->
                                DraftOptionChoice(
                                    name = choice.name.trim(),
                                    priceDeltaLabel = formatPriceDelta(choice.priceDelta)
                                )
                            }
                        )
                        addOptionGroupOpen = false
                    },
                    enabled = canAddGroup
                ) {
                    Text(
                        text = "Add",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        color = ItemEditorOlive
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { addOptionGroupOpen = false }) {
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
private fun IngredientCommandPalette(
    alreadySelectedIds: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (MockIngredient) -> Unit
) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 480.dp)
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
