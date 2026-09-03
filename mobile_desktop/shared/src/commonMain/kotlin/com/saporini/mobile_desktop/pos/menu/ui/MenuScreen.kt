package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.core.ui.PlatformBackHandler
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu as DomainMenu
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val ActiveOlive = Color(0xFF94A27F)
private val TextInk = Color(0xFF222426)
private val MutedInk = Color(0xFF747572)
private val Border = Color(0xFFE8E5E1)
private val ItemDetailSurface = Color(0xFFF7F7F5)
private val ItemDetailEmptyText = Color(0xFFC7C8C2)
private val DefaultMenuSections = listOf("All", "Antipasti", "Pasta", "Pizza", "Dolci", "Beverages")

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier
) {
    val screenModel = koinInject<MenuScreenModel>()
    val menuState by screenModel.state.collectAsState()
    var showMenuEditor by remember { mutableStateOf(false) }
    var menuBeingEdited by remember { mutableStateOf<DomainMenu?>(null) }
    val selectedMenu = menuState.selectedMenu

    PlatformBackHandler(enabled = selectedMenu != null && !showMenuEditor) {
        screenModel.closeMenu()
    }

    // Local-only "deleted" state for the UI-only delete-menu flow -- no
    // backend call, just hides the card and plays a success toast/fade.
    var hiddenMenuIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deletingMenuId by remember { mutableStateOf<String?>(null) }
    var showDeleteToast by remember { mutableStateOf(false) }
    val deleteFlowScope = rememberCoroutineScope()

    // Menu create/edit/reorder is Manager rank or higher (Manager, Admin,
    // Co-Owner, Owner, Super Admin all carry MENUS_CREATE/MENUS_UPDATE;
    // Waiter and Kitchen do not) -- same permission-based gate PosScreen
    // uses for table-layout editing.
    val sessionManager = koinInject<SessionManager>()
    val currentUser by sessionManager.currentUser.collectAsState()
    val canManageMenus =
        "MENUS_UPDATE" in currentUser?.permissions.orEmpty() ||
            "MENUS_CREATE" in currentUser?.permissions.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedMenu == null) {
            MenuCoverUi(
                state = menuState,
                canManageMenus = canManageMenus,
                hiddenMenuIds = hiddenMenuIds,
                deletingMenuId = deletingMenuId,
                profileInitials = listOfNotNull(
                    currentUser?.firstName?.trim()?.firstOrNull(),
                    currentUser?.lastName?.trim()?.firstOrNull()
                ).joinToString("").uppercase().ifEmpty { "?" },
                onOpenMenu = screenModel::openMenu,
                onAddMenu = {
                    screenModel.clearError()
                    menuBeingEdited = null
                    showMenuEditor = true
                },
                onEditMenu = { menu ->
                    screenModel.clearError()
                    menuBeingEdited = menu
                    showMenuEditor = true
                },
                onRetry = screenModel::loadMenus,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MenuDetailsContent(
                menu = selectedMenu,
                onBack = screenModel::closeMenu,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showMenuEditor) {
            MenuEditorDialog(
                menu = menuBeingEdited,
                isSaving = menuState.isSaving,
                errorMessage = menuState.errorMessage,
                onDismiss = {
                    showMenuEditor = false
                    menuBeingEdited = null
                    screenModel.clearError()
                },
                onDeleteMenu = {
                    val deletedId = menuBeingEdited?.id
                    showMenuEditor = false
                    menuBeingEdited = null
                    screenModel.clearError()
                    if (deletedId != null) {
                        deletingMenuId = deletedId
                        showDeleteToast = true
                        deleteFlowScope.launch {
                            delay(420)
                            hiddenMenuIds = hiddenMenuIds + deletedId
                            deletingMenuId = null
                            delay(1600)
                            showDeleteToast = false
                        }
                    }
                },
                onSave = {
                    name,
                    description,
                    active,
                    availableFrom,
                    availableUntil,
                    availableFromDate,
                    availableUntilDate,
                    color ->
                    val editingMenu = menuBeingEdited
                    if (editingMenu == null) {
                        screenModel.createMenu(
                            name = name,
                            description = description,
                            active = active,
                            availableFrom = availableFrom,
                            availableUntil = availableUntil,
                            availableFromDate = availableFromDate,
                            availableUntilDate = availableUntilDate,
                            color = color
                        ) {
                            showMenuEditor = false
                            menuBeingEdited = null
                        }
                    } else {
                        screenModel.updateMenu(
                            menuId = editingMenu.id,
                            name = name,
                            description = description,
                            active = active,
                            availableFrom = availableFrom,
                            availableUntil = availableUntil,
                            availableFromDate = availableFromDate,
                            availableUntilDate = availableUntilDate,
                            color = color
                        ) {
                            showMenuEditor = false
                            menuBeingEdited = null
                        }
                    }
                }
            )
        }

        MenuDeletedToast(
            visible = showDeleteToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        )
    }
}

@Composable
private fun MenuDeletedToast(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "menu-deleted-toast-alpha"
    )

    if (!visible && alpha <= 0f) return

    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF232422))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Text(
                text = "Menu deleted successfully",
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MenuDetailsContent(
    menu: DomainMenu,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember(menu.id) { mutableStateOf("All") }
    var sections by remember(menu.id) { mutableStateOf(DefaultMenuSections) }
    var showOrderTypeDialog by remember(menu.id) { mutableStateOf(false) }
    var sectionManagerMode by remember(menu.id) { mutableStateOf<SectionManagerMode?>(null) }
    var searchQuery by remember(menu.id) { mutableStateOf("") }
    var selectedSearchItemName by remember(menu.id) { mutableStateOf<String?>(null) }
    var localItems by remember(menu.id) { mutableStateOf(menuItems) }
    var showAddItemDialog by remember(menu.id) { mutableStateOf(false) }
    var itemBeingEdited by remember(menu.id) { mutableStateOf<MenuItem?>(null) }
    var showItemEditor by remember(menu.id) { mutableStateOf(false) }
    var showVariantEditor by remember(menu.id) { mutableStateOf(false) }
    var showOptionsEditor by remember(menu.id) { mutableStateOf(false) }
    var isReorderingItems by remember(menu.id) { mutableStateOf(false) }

    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = if (isPhone) 16.dp else 22.dp, end = if (isPhone) 16.dp else 22.dp, top = if (isWidePhone) 8.dp else 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(if (isWidePhone) 10.dp else 18.dp)
        ) {
        if (isPhone) {
            val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
                SearchBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSuggestionClick = { item ->
                        searchQuery = item.name
                        selectedCategory = item.category.takeIf { it in sections } ?: "All"
                        selectedSearchItemName = item.name
                    },
                    modifier = fieldModifier,
                    items = localItems
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to all menus", tint = TextInk)
                }
                Text(
                    text = menu.name,
                    modifier = Modifier.weight(if (isWidePhone) 0.7f else 1f),
                    fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = TextInk, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (isWidePhone) {
                    searchField(Modifier.weight(1.2f))
                }
                TextButton(
                    onClick = { showAddItemDialog = true },
                    enabled = !isReorderingItems,
                    modifier = Modifier.heightIn(min = 44.dp).background(ActiveOlive, RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Item", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            if (!isWidePhone) {
                searchField(Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { category ->
                    val selected = selectedCategory == category
                    TextButton(
                        onClick = { if (!isReorderingItems) selectedCategory = category },
                        modifier = Modifier.heightIn(min = 44.dp)
                            .background(if (selected) ActiveOlive else Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, if (selected) ActiveOlive else Border, RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Color.White else TextInk)
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = TextInk
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(category, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                IconButton(
                    onClick = { sectionManagerMode = SectionManagerMode.EDIT },
                    enabled = !isReorderingItems,
                    modifier = Modifier.size(44.dp).background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit menu sections",
                        modifier = Modifier.size(18.dp),
                        tint = if (isReorderingItems) TextInk.copy(alpha = 0.35f) else TextInk
                    )
                }
            }
            if (isReorderingItems) {
                Text("Hold an item, then drag to reorder.", fontFamily = Inter(), fontSize = 12.sp, color = MutedInk)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to all menus",
                        modifier = Modifier.size(30.dp),
                        tint = TextInk
                    )
                }

                CategoryButtons(
                    items = sections,
                    selected = selectedCategory,
                    onSelected = { if (!isReorderingItems) selectedCategory = it },
                    onManageSections = { sectionManagerMode = SectionManagerMode.EDIT }
                )

                Spacer(Modifier.weight(1f))
                SearchBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSuggestionClick = { item ->
                        searchQuery = item.name
                        selectedCategory = item.category.takeIf { it in sections } ?: "All"
                        selectedSearchItemName = item.name
                    },
                    modifier = Modifier.width(360.dp)
                )
            }

        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                isPhone -> 1
                maxWidth < 900.dp -> 2
                else -> 4
            }
            val visibleItems = if (selectedCategory == "All") {
                localItems
            } else {
                localItems.filter { it.category == selectedCategory }
            }

            if (isReorderingItems) {
                ReorderableMenuItemGrid(
                    items = visibleItems,
                    columns = columns,
                    isPhone = isPhone,
                    onReorder = { reorderedItems ->
                        if (selectedCategory == "All") {
                            localItems = reorderedItems
                        } else {
                            val reorderedIterator = reorderedItems.iterator()
                            localItems = localItems.map { current ->
                                if (current.category == selectedCategory && reorderedIterator.hasNext()) {
                                    reorderedIterator.next()
                                } else {
                                    current
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val rowSlots: List<MenuItem?> = if (isPhone) visibleItems else listOf(null) + visibleItems

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowSlots.chunked(columns).forEach { rowChunk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            rowChunk.forEach { item ->
                                if (item == null) {
                                    AddItemCard(
                                        onClick = { showAddItemDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    MenuItemCard(
                                        item = item,
                                        isPhone = isPhone,
                                        selected = item.name == selectedSearchItemName,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onEditMenuItem = {
                                            itemBeingEdited = item
                                            showItemEditor = true
                                        },
                                        onEditVariants = {
                                            itemBeingEdited = item
                                            showVariantEditor = true
                                        },
                                        onEditOptions = {
                                            itemBeingEdited = item
                                            showOptionsEditor = true
                                        }
                                    )
                                }
                            }
                            repeat(columns - rowChunk.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (showAddItemDialog) {
            ItemEditorDialog(
                onDismiss = { showAddItemDialog = false },
                onSave = { name, priceLabel, sku, description, imageFileName, available, ingredients ->
                    val category = selectedCategory.takeIf { it != "All" } ?: sections.firstOrNull { it != "All" } ?: "Uncategorized"
                    localItems = localItems + MenuItem(
                        name = name,
                        description = description.orEmpty(),
                        price = priceLabel,
                        category = category,
                        available = available,
                        sku = sku,
                        imageFileName = imageFileName,
                        ingredients = ingredients
                    )
                    showAddItemDialog = false
                }
            )
        }

        if (showItemEditor) {
            itemBeingEdited?.let { editingItem ->
                ItemEditorDialog(
                    existingItem = EditableMenuItem(
                        name = editingItem.name,
                        priceLabel = editingItem.price,
                        sku = editingItem.sku,
                        description = editingItem.description.takeIf { it.isNotBlank() },
                        imageFileName = editingItem.imageFileName,
                        available = editingItem.available,
                        ingredients = editingItem.ingredients
                    ),
                    onDismiss = {
                        showItemEditor = false
                        itemBeingEdited = null
                    },
                    onSave = { name, priceLabel, sku, description, imageFileName, available, ingredients ->
                        localItems = localItems.map { current ->
                            if (current === editingItem) {
                                current.copy(
                                    name = name,
                                    description = description.orEmpty(),
                                    price = priceLabel,
                                    available = available,
                                    sku = sku,
                                    imageFileName = imageFileName,
                                    ingredients = ingredients
                                )
                            } else {
                                current
                            }
                        }
                        showItemEditor = false
                        itemBeingEdited = null
                    }
                )
            }
        }

        if (showVariantEditor) {
            itemBeingEdited?.let { editingItem ->
                VariantEditorDialog(
                    itemName = editingItem.name,
                    variants = editingItem.variants,
                    onDismiss = {
                        showVariantEditor = false
                        itemBeingEdited = null
                    },
                    onSave = { updatedVariants ->
                        localItems = localItems.map { current ->
                            if (current === editingItem) {
                                current.copy(variants = updatedVariants)
                            } else {
                                current
                            }
                        }
                        showVariantEditor = false
                        itemBeingEdited = null
                    }
                )
            }
        }

        if (showOptionsEditor) {
            itemBeingEdited?.let { editingItem ->
                OptionsEditorDialog(
                    itemName = editingItem.name,
                    optionGroups = editingItem.optionGroups,
                    onDismiss = {
                        showOptionsEditor = false
                        itemBeingEdited = null
                    },
                    onSave = { updatedOptionGroups ->
                        localItems = localItems.map { current ->
                            if (current === editingItem) {
                                current.copy(optionGroups = updatedOptionGroups)
                            } else {
                                current
                            }
                        }
                        showOptionsEditor = false
                        itemBeingEdited = null
                    }
                )
            }
        }

        }

        if (showOrderTypeDialog) {
            OrderTypeDialog(
                onDismiss = { showOrderTypeDialog = false },
                onMenuItems = {
                    showOrderTypeDialog = false
                    selectedCategory = "All"
                    searchQuery = ""
                    selectedSearchItemName = null
                    isReorderingItems = true
                },
                onSections = {
                    showOrderTypeDialog = false
                    sectionManagerMode = SectionManagerMode.REORDER
                }
            )
        }

        sectionManagerMode?.let { managerMode ->
            SectionManagerDialog(
                sections = sections,
                itemCounts = localItems.groupingBy { it.category }.eachCount(),
                mode = managerMode,
                onDismiss = { sectionManagerMode = null },
                onSave = { updatedSections, renames ->
                    sections = updatedSections
                    localItems = localItems.map { item ->
                        renames[item.category]?.let { renamedCategory ->
                            item.copy(category = renamedCategory)
                        } ?: item
                    }
                    selectedCategory = renames[selectedCategory] ?: selectedCategory
                    if (selectedCategory !in updatedSections) selectedCategory = "All"
                    sectionManagerMode = null
                }
            )
        }

        TextButton(
            onClick = {
                if (isReorderingItems) {
                    isReorderingItems = false
                } else {
                    showOrderTypeDialog = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(28.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(if (isReorderingItems) TextInk else ActiveOlive),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) {
            Icon(
                imageVector = if (isReorderingItems) Icons.Filled.Check else Icons.Outlined.DragIndicator,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isReorderingItems) "Save Order" else "Change Order",
                fontFamily = Inter(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
    items: List<MenuItem> = menuItems
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(query, expanded, items) {
        if (query.isBlank() || !expanded) {
            emptyList()
        } else {
            items
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(5)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val suggestionWidth = maxWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = Color(0xFF303236)
            )
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                value = query,
                onValueChange = { value ->
                    onQueryChange(value)
                    expanded = value.isNotBlank()
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp,
                    color = TextInk
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search menu items",
                            fontFamily = Inter(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = 0.sp,
                            color = Color(0xFF91918F)
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (suggestions.isNotEmpty()) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(suggestionWidth)
                    .background(Color.White),
                properties = PopupProperties(focusable = false)
            ) {
                suggestions.forEach { item ->
                    SearchSuggestionRow(
                        item = item,
                        onClick = {
                            expanded = false
                            onSuggestionClick(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    item: MenuItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Res.drawable.auth_login_img),
            contentDescription = item.name,
            modifier = Modifier
                .size(width = 58.dp, height = 42.dp)
                .clip(RoundedCornerShape(6.dp))
                .graphicsLayer(alpha = if (item.available) 1f else 0.45f),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.sp,
                color = TextInk,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = item.category,
                fontFamily = Inter(),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.sp,
                color = MutedInk,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = item.price,
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
            color = TextInk
        )
    }
}

@Composable
private fun CategoryButtons(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onManageSections: () -> Unit
) {
    val visibleCategoryLimit = 5
    var overflowExpanded by remember { mutableStateOf(false) }
    var promotedCategory by remember { mutableStateOf<String?>(null) }
    val hasOverflow = items.size > visibleCategoryLimit
    val fixedItems = if (hasOverflow) {
        items.take(visibleCategoryLimit - 1)
    } else {
        items
    }
    val visibleTail = if (hasOverflow) {
        selected.takeIf { it in items && it !in fixedItems }
            ?: promotedCategory?.takeIf { it in items && it !in fixedItems }
            ?: items[visibleCategoryLimit - 1]
    } else {
        null
    }
    val visibleItems = if (hasOverflow) fixedItems + listOfNotNull(visibleTail) else items
    val overflowItems = items.filterNot { it in visibleItems }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        visibleItems.forEach { item ->
            val isSelected = item == selected
            TextButton(
                onClick = { onSelected(item) },
                modifier = Modifier
                    .height(46.dp)
                    .width(if (item == "All") 78.dp else 128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ActiveOlive else Color.White)
                    .border(1.dp, Border, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) Color.White else TextInk
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon(item),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TextInk
                    )
                    Text(
                        text = item,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (hasOverflow) {
            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "More categories",
                        modifier = Modifier.size(24.dp),
                        tint = TextInk
                    )
                }

                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    offset = DpOffset(x = 0.dp, y = 8.dp),
                    modifier = Modifier.background(Color.White),
                    shape = RoundedCornerShape(10.dp),
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp
                ) {
                    overflowItems.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item,
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = TextInk
                                )
                            },
                            onClick = {
                                promotedCategory = item
                                overflowExpanded = false
                                onSelected(item)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = categoryIcon(item),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = TextInk
                                )
                            }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onManageSections,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White).border(1.dp, Border, RoundedCornerShape(8.dp))
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit menu sections",
                modifier = Modifier.size(19.dp),
                tint = TextInk
            )
        }
    }
}

@Composable
private fun ReorderableMenuItemGrid(
    items: List<MenuItem>,
    columns: Int,
    isPhone: Boolean = false,
    onReorder: (List<MenuItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalGap = 14.dp
    val verticalGap = 14.dp
    var draggingItem by remember { mutableStateOf<MenuItem?>(null) }
    var draggedPosition by remember { mutableStateOf(Offset.Zero) }
    val latestItems by rememberUpdatedState(items)

    BoxWithConstraints(
        modifier = modifier
    ) {
        val cardWidth = (maxWidth - horizontalGap * (columns - 1)) / columns
        val cardHeight = if (isPhone) phoneMenuItemHeight(LocalDensity.current.fontScale) else if (columns <= 2) 430.dp else 350.dp
        val rowCount = (items.size + columns - 1) / columns
        val contentHeight = if (rowCount == 0) 0.dp else cardHeight * rowCount + verticalGap * (rowCount - 1)
        val density = LocalDensity.current
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val horizontalGapPx = with(density) { horizontalGap.toPx() }
        val verticalGapPx = with(density) { verticalGap.toPx() }

        fun slotPosition(index: Int): Offset {
            val column = index % columns
            val row = index / columns
            return Offset(
                x = column * (cardWidthPx + horizontalGapPx),
                y = row * (cardHeightPx + verticalGapPx)
            )
        }

        fun nearestSlotIndex(position: Offset): Int? {
            if (latestItems.isEmpty()) return null
            val draggedCenter = position + Offset(cardWidthPx / 2f, cardHeightPx / 2f)
            return latestItems.indices.minByOrNull { index ->
                val slotCenter = slotPosition(index) + Offset(cardWidthPx / 2f, cardHeightPx / 2f)
                (slotCenter - draggedCenter).getDistance()
            }
        }

        Box(Modifier.fillMaxWidth().height(contentHeight)) {
            items.forEachIndexed { itemIndex, item ->
                key(item) {
                    val slot = slotPosition(itemIndex)
                    val targetOffset = IntOffset(slot.x.roundToInt(), slot.y.roundToInt())
                    val animatedOffset by animateIntOffsetAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "menu-item-slot-${item.name}"
                    )
                    val isDragging = draggingItem === item

                    MenuItemCard(
                        item = item,
                        selected = false,
                        isReordering = true,
                        isPhone = isPhone,
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .zIndex(if (isDragging) 10f else 0f)
                            .offset {
                                if (isDragging) {
                                    IntOffset(
                                        draggedPosition.x.roundToInt(),
                                        draggedPosition.y.roundToInt()
                                    )
                                } else {
                                    animatedOffset
                                }
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    scaleX = 1.025f
                                    scaleY = 1.025f
                                    shadowElevation = 22.dp.toPx()
                                }
                            }
                            .pointerInput(item, cardWidthPx, cardHeightPx, isPhone) {
                                detectMenuItemDrag(
                                    isPhone = isPhone,
                                    onDragStart = {
                                        val currentIndex = latestItems.indexOfFirst { it === item }
                                        if (currentIndex >= 0) {
                                            draggedPosition = slotPosition(currentIndex)
                                            draggingItem = item
                                        }
                                    },
                                    onDragEnd = { draggingItem = null },
                                    onDragCancel = { draggingItem = null },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggingItem !== item) return@detectMenuItemDrag

                                        draggedPosition += dragAmount
                                        val currentOrder = latestItems
                                        val fromIndex = currentOrder.indexOfFirst { it === item }
                                        val toIndex = nearestSlotIndex(draggedPosition)

                                        if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                            val reordered = currentOrder.toMutableList()
                                            val dragged = reordered.removeAt(fromIndex)
                                            reordered.add(toIndex, dragged)
                                            onReorder(reordered)
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

private fun phoneMenuItemHeight(fontScale: Float) = 164.dp * fontScale.coerceAtLeast(1f)

@Composable
private fun PhoneMenuItemCard(
    item: MenuItem,
    selected: Boolean,
    modifier: Modifier,
    isReordering: Boolean,
    onExpand: () -> Unit,
    onEditMenuItem: () -> Unit,
    onEditVariants: () -> Unit,
    onEditOptions: () -> Unit
) {
    val contentAlpha = if (item.available) 1f else 0.5f
    var editMenuOpen by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .height(phoneMenuItemHeight(LocalDensity.current.fontScale))
            .clip(cardShape)
            .background(Color.White)
            .border(if (selected || isReordering) 2.dp else 1.dp, if (selected || isReordering) ActiveOlive else Border, cardShape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.36f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
        ) {
            Image(
                painter = painterResource(Res.drawable.auth_login_img),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = contentAlpha),
                contentScale = ContentScale.Crop
            )
            AvailabilityButton(
                available = item.available,
                compact = true,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                item.category.uppercase(),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.5.sp,
                color = MutedInk.copy(alpha = contentAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.name,
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, lineHeight = 20.sp,
                color = TextInk.copy(alpha = contentAlpha),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.description,
                fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp,
                color = MutedInk.copy(alpha = contentAlpha),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    item.price,
                    modifier = Modifier.weight(1f),
                    fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, color = TextInk.copy(alpha = contentAlpha),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (!isReordering) {
                    if (item.available) {
                        Box {
                            PhoneMenuItemAction(Icons.Outlined.Edit, "Edit item") { editMenuOpen = true }
                            DropdownMenu(
                                expanded = editMenuOpen,
                                onDismissRequest = { editMenuOpen = false },
                                offset = DpOffset(0.dp, 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                containerColor = Color.White,
                                tonalElevation = 0.dp,
                                shadowElevation = 6.dp
                            ) {
                                listOf(
                                    "Menu Item" to onEditMenuItem,
                                    "Variant" to onEditVariants,
                                    "Option" to onEditOptions
                                ).forEach { (label, action) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                                        },
                                        onClick = {
                                            editMenuOpen = false
                                            action()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    PhoneMenuItemAction(Icons.Outlined.ZoomOutMap, "View item details", onExpand)
                }
            }
        }
    }
}

@Composable
private fun PhoneMenuItemAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(Color(0xFFF7F7F5), RoundedCornerShape(7.dp))
                .border(1.dp, Border, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, modifier = Modifier.size(15.dp), tint = TextInk)
        }
    }
}

@Composable
private fun MenuItemCard(
    item: MenuItem,
    selected: Boolean,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier,
    isReordering: Boolean = false,
    onEditMenuItem: () -> Unit = { },
    onEditVariants: () -> Unit = { },
    onEditOptions: () -> Unit = { }
) {
    val contentAlpha = if (item.available) 1f else 0.42f
    var showDescription by remember { mutableStateOf(false) }

    if (isPhone) {
        PhoneMenuItemCard(
            item = item,
            selected = selected,
            modifier = modifier,
            isReordering = isReordering,
            onExpand = { showDescription = true },
            onEditMenuItem = onEditMenuItem,
            onEditVariants = onEditVariants,
            onEditOptions = onEditOptions
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    width = if (selected || isReordering) 2.dp else 1.dp,
                    color = if (selected || isReordering) ActiveOlive else Border,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Column {
                Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isPhone) 1.45f else 2.12f)
                    .clip(RoundedCornerShape(6.dp))
                ) {
                    Image(
                        painter = painterResource(Res.drawable.auth_login_img),
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = contentAlpha),
                        contentScale = ContentScale.Crop
                    )

                    AvailabilityButton(
                        available = item.available,
                        compact = isPhone,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(if (isPhone) 6.dp else 10.dp)
                    )

                    if (item.available && !isReordering) {
                        var showEditMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (isPhone) 6.dp else 10.dp)
                        ) {
                            EditItemButton(
                                onClick = { showEditMenu = true },
                                isPhone = isPhone
                            )

                            DropdownMenu(
                                expanded = showEditMenu,
                                onDismissRequest = { showEditMenu = false },
                                offset = DpOffset(x = 0.dp, y = 8.dp),
                                modifier = Modifier.background(Color.White),
                                shape = RoundedCornerShape(10.dp),
                                containerColor = Color.White,
                                tonalElevation = 0.dp,
                                shadowElevation = 6.dp
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Menu Item",
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextInk
                                        )
                                    },
                                    onClick = {
                                        showEditMenu = false
                                        onEditMenuItem()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Variant",
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextInk
                                        )
                                    },
                                    onClick = {
                                        showEditMenu = false
                                        onEditVariants()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Option",
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextInk
                                        )
                                    },
                                    onClick = {
                                        showEditMenu = false
                                        onEditOptions()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = item.name,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isPhone) 15.sp else 18.sp,
                    letterSpacing = 0.sp,
                    color = TextInk.copy(alpha = contentAlpha),
                    maxLines = if (isPhone) 2 else 1,
                    softWrap = isPhone,
                    overflow = if (isPhone) TextOverflow.Ellipsis else TextOverflow.Clip
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 0.sp,
                    color = MutedInk.copy(alpha = contentAlpha),
                    minLines = 2,
                    maxLines = 2
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = item.price,
                    modifier = if (isPhone && !isReordering) Modifier.padding(end = 44.dp) else Modifier,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isPhone) 15.sp else 17.sp,
                    letterSpacing = 0.sp,
                    color = TextInk.copy(alpha = contentAlpha)
                )
            }

            if (!isReordering) {
                ExpandImageButton(
                    onClick = { showDescription = true },
                    isPhone = isPhone,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 0.dp)
                )
            }
        }
    }

    if (showDescription) {
        ItemDetailDialog(
            item = item,
            onDismiss = { showDescription = false }
        )
    }
}

@Composable
private fun ItemDetailDialog(
    item: MenuItem,
    onDismiss: () -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        if (isPhone) {
            PhoneItemDetails(item = item, onDismiss = onDismiss)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .widthIn(min = 760.dp, max = 980.dp)
                        .fillMaxHeight(0.84f)
                        .shadow(28.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .background(Color(0xFF171914))
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.auth_login_img),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        AvailabilityButton(
                            available = item.available,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(18.dp)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.62f))
                                .padding(horizontal = 22.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "MENU PREVIEW",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                            Text(
                                text = item.category,
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "ITEM DETAILS",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp,
                                    color = ActiveOlive
                                )
                                Text(
                                    text = "Menu item overview",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = MutedInk
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(50))
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                    tint = TextInk
                                )
                            }
                        }

                        HorizontalDivider(color = Border)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 25.sp,
                                        lineHeight = 29.sp,
                                        color = TextInk
                                    )
                                    Text(
                                        text = item.category + (item.sku?.let { "  â€¢  SKU $it" } ?: ""),
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MutedInk
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF1F4EC))
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        text = item.price,
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = TextInk
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    .padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ItemDetailSectionLabel(text = "Description")
                                Text(
                                    text = item.description.ifBlank { "No description added." },
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = if (item.description.isNotBlank()) MutedInk else ItemDetailEmptyText
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ItemDetailSurface)
                                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    ItemDetailSectionLabel(text = "Recipe / Ingredients")
                                    if (item.ingredients.isEmpty()) {
                                        Text(
                                            text = "No ingredients added.",
                                            fontFamily = Inter(),
                                            fontSize = 13.sp,
                                            color = ItemDetailEmptyText
                                        )
                                    } else {
                                        item.ingredients.forEach { ingredient ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White)
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = ingredient.name,
                                                    modifier = Modifier.weight(1f),
                                                    fontFamily = Inter(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = TextInk
                                                )
                                                Text(
                                                    text = "${ingredient.quantity} ${ingredient.unit}".trim(),
                                                    fontFamily = Inter(),
                                                    fontSize = 11.sp,
                                                    color = MutedInk
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ItemDetailSurface)
                                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    ItemDetailSectionLabel(text = "Variants")
                                    if (item.variants.isEmpty()) {
                                        Text(
                                            text = "No variants added.",
                                            fontFamily = Inter(),
                                            fontSize = 13.sp,
                                            color = ItemDetailEmptyText
                                        )
                                    } else {
                                        item.variants.forEach { variant ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White)
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = variant.name,
                                                    modifier = Modifier.weight(1f),
                                                    fontFamily = Inter(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = TextInk
                                                )
                                                if (variant.priceDeltaLabel.isNotBlank()) {
                                                    Text(
                                                        text = variant.priceDeltaLabel,
                                                        fontFamily = Inter(),
                                                        fontSize = 11.sp,
                                                        color = MutedInk
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                ItemDetailSectionLabel(text = "Options")
                                if (item.optionGroups.isEmpty()) {
                                    Text(
                                        text = "No options added.",
                                        fontFamily = Inter(),
                                        fontSize = 13.sp,
                                        color = ItemDetailEmptyText
                                    )
                                } else {
                                    item.optionGroups.forEach { group ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .padding(horizontal = 11.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                    fontSize = 12.sp,
                                                    color = TextInk
                                                )
                                                Text(
                                                    text = if (group.required) "Required" else "Optional",
                                                    fontFamily = Inter(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.sp,
                                                    color = if (group.required) ActiveOlive else MutedInk
                                                )
                                            }
                                            Text(
                                                text = group.choices.joinToString(separator = "  â€¢  ") { choice ->
                                                    if (choice.priceDeltaLabel.isNotBlank()) {
                                                        "${choice.name} (${choice.priceDeltaLabel})"
                                                    } else {
                                                        choice.name
                                                    }
                                                },
                                fontFamily = Inter(),
                                                fontSize = 11.sp,
                                                color = MutedInk
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneItemDetails(item: MenuItem, onDismiss: () -> Unit) {
    val isWidePhone = isWidePhoneWindow()
    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (isWidePhone) 48.dp else 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to menu items", tint = TextInk)
            }
            Text("Item details", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextInk)
        }
        HorizontalDivider(color = Border)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(if (isWidePhone) 144.dp else 190.dp).clip(RoundedCornerShape(12.dp))) {
                Image(
                    painter = painterResource(Res.drawable.auth_login_img),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                AvailabilityButton(
                    available = item.available,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, lineHeight = 29.sp, color = TextInk
                )
                Text(
                    item.category + (item.sku?.let { "  •  SKU $it" } ?: ""),
                    fontFamily = Inter(), fontSize = 12.sp, color = MutedInk
                )
                Text(item.price, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = TextInk)
            }
            PhoneItemDetailSection("Description") {
                PhoneItemDetailText(item.description.ifBlank { "No description added." })
            }
            PhoneItemDetailSection("Recipe / Ingredients") {
                if (item.ingredients.isEmpty()) {
                    PhoneItemDetailText("No ingredients added.")
                } else {
                    item.ingredients.forEach { ingredient ->
                        PhoneItemDetailText("${ingredient.name} — ${ingredient.quantity} ${ingredient.unit}")
                    }
                }
            }
            PhoneItemDetailSection("Variants") {
                if (item.variants.isEmpty()) {
                    PhoneItemDetailText("No variants added.")
                } else {
                    item.variants.forEach { variant ->
                        PhoneItemDetailText(
                            variant.name + variant.priceDeltaLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        )
                    }
                }
            }
            PhoneItemDetailSection("Options") {
                if (item.optionGroups.isEmpty()) {
                    PhoneItemDetailText("No options added.")
                } else {
                    item.optionGroups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                group.name + if (group.required) " · Required" else " · Optional",
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk
                            )
                            group.choices.forEach { choice ->
                                PhoneItemDetailText(
                                    choice.name + choice.priceDeltaLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
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
private fun PhoneItemDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ItemDetailSurface, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemDetailSectionLabel(title)
        content()
    }
}

@Composable
private fun PhoneItemDetailText(text: String) {
    Text(text, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = MutedInk)
}

@Composable
private fun ItemDetailSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = Inter(),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = MutedInk
    )
}

@Composable
private fun AvailabilityButton(
    available: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (available) Color.White else Color(0xFF6D6D6D))
            .clickable {}
            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (available) "Available" else "Unavailable",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 10.sp else 13.sp,
            letterSpacing = 0.sp,
            color = if (available) ActiveOlive else Color.White,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ExpandImageButton(
    onClick: () -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(if (isPhone) 40.dp else 29.dp)
            .shadow(2.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.92f))
    ) {
        Icon(
            imageVector = Icons.Outlined.ZoomOutMap,
            contentDescription = "Expand image",
            modifier = Modifier.size(18.dp),
            tint = TextInk
        )
    }
}

@Composable
private fun EditItemButton(
    onClick: () -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(if (isPhone) 40.dp else 29.dp)
            .shadow(2.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.92f))
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit item",
            modifier = Modifier.size(16.dp),
            tint = TextInk
        )
    }
}

@Composable
private fun AddItemCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .padding(40.dp)
            .clip(shape)
            .background(Color(0xFFF3F3F1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFFA7A9A4),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(13f, 9f))
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = ActiveOlive
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ADD NEW ITEM",
                fontFamily = CormorantGaramond(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF666864)
            )
        }
    }
}

internal fun categoryIcon(category: String): ImageVector = when (category) {
    "All" -> Icons.Outlined.RestaurantMenu
    "Antipasti" -> Icons.Outlined.Restaurant
    "Pasta" -> Icons.Outlined.Restaurant
    "Pizza" -> Icons.Outlined.LocalPizza
    "Dolci" -> Icons.Outlined.Cake
    "Beverages" -> Icons.Outlined.LocalDrink
    else -> Icons.Outlined.RestaurantMenu
}

private data class MenuItem(
    val name: String,
    val description: String,
    val price: String,
    val category: String,
    val available: Boolean,
    val sku: String? = null,
    val imageFileName: String? = null,
    val ingredients: List<DraftIngredient> = emptyList(),
    val variants: List<DraftVariant> = emptyList(),
    val optionGroups: List<DraftOptionGroup> = emptyList()
)

private val menuItems = listOf(
    MenuItem("Burrata con Pomodorini", "Creamy burrata with heirloom cherry tomatoes, basil, and olive oil.", "\$14.00", "Antipasti", true),
    MenuItem("Bruschetta al Pomodoro", "Grilled sourdough topped with ripe tomatoes, garlic, basil, and olive oil.", "\$10.00", "Antipasti", true),
    MenuItem("Prosciutto e Melone", "Thinly sliced prosciutto di Parma with sweet melon.", "\$13.00", "Antipasti", true),
    MenuItem("Carpaccio di Manzo", "Thinly sliced beef tenderloin with arugula, parmesan, and lemon.", "\$15.00", "Antipasti", false),
    MenuItem("Spaghetti alla Carbonara", "Classic Roman pasta with eggs, Pecorino cheese, guanciale, and black pepper.", "\$18.00", "Pasta", true),
    MenuItem("Lasagna alla Bolognese", "Layers of pasta, slow-cooked meat sauce, bechamel, and melted mozzarella.", "from \$20.00", "Pasta", true),
    MenuItem("Risotto ai Funghi", "Creamy Arborio rice with wild mushrooms, white wine, and parmesan.", "\$19.00", "Pasta", true),
    MenuItem("Pizza Quattro Formaggi", "Mozzarella, gorgonzola, fontina, parmesan, and ricotta.", "\$16.00", "Pizza", false),
    MenuItem("Pizza Margherita", "San Marzano tomato sauce, fior di latte mozzarella, fresh basil, and olive oil.", "\$15.00", "Pizza", true),
    MenuItem("Tiramisu", "Classic Italian dessert with mascarpone cream and espresso-soaked ladyfingers.", "\$8.50", "Dolci", true),
    MenuItem("Panna Cotta", "Silky vanilla panna cotta with mixed berry compote.", "\$7.50", "Dolci", true),
    MenuItem("Lemon Soda", "Refreshing sparkling lemon soda with a hint of mint.", "\$4.50", "Beverages", true)
)

private suspend fun PointerInputScope.detectMenuItemDrag(
    isPhone: Boolean,
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    if (isPhone) {
        detectDragGesturesAfterLongPress(
            onDragStart = onDragStart, onDragEnd = onDragEnd, onDragCancel = onDragCancel, onDrag = onDrag
        )
    } else {
        detectDragGestures(
            onDragStart = onDragStart, onDragEnd = onDragEnd, onDragCancel = onDragCancel, onDrag = onDrag
        )
    }
}
