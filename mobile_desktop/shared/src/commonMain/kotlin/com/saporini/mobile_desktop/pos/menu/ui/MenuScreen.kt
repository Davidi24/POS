package com.saporini.mobile_desktop.pos.menu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BrunchDining
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.Liquor
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.components.AnimatedStatusIcon
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.core.ui.PlatformBackHandler
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu as DomainMenu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem as DomainMenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection as DomainMenuSection
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuItemInput
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuVariantInput
import com.saporini.mobile_desktop.pos.menu.ui.item.AddItemCard
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftIngredient
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftOptionGroup
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftVariant
import com.saporini.mobile_desktop.pos.menu.ui.item.EditableMenuItem
import com.saporini.mobile_desktop.pos.menu.ui.item.ItemDetailDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.ItemEditorDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItem
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItemCard
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItemCardSkeletonGrid
import com.saporini.mobile_desktop.pos.menu.ui.item.OptionsEditorDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.VariantEditorDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.phoneMenuItemHeight
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogActionStatus
import com.saporini.mobile_desktop.pos.menu.ui.menu.DialogStatusBody
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuCoverUi
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuEditorDialog
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import com.saporini.mobile_desktop.pos.menu.ui.section.CategoryButtons
import com.saporini.mobile_desktop.pos.menu.ui.section.OrderTypeDialog
import com.saporini.mobile_desktop.pos.menu.ui.section.PhoneCategoryFilterRow
import com.saporini.mobile_desktop.pos.menu.ui.section.SectionFilterSkeleton
import com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog
import com.saporini.mobile_desktop.pos.menu.ui.section.SectionManagerDialog
import com.saporini.mobile_desktop.pos.menu.ui.section.SectionManagerMode
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val ActiveOlive = Color(0xFF94A27F)
private val TextInk = Color(0xFF222426)
private val MutedInk = Color(0xFF747572)
private val Border = Color(0xFFE8E5E1)
private val ToastSuccessGreen = Color(0xFF6FCF87)
private val ToastErrorRed = Color(0xFFE5695D)

internal enum class MenuCategoryIcon(val icon: ImageVector, val label: String) {
    RESTAURANT_MENU(Icons.Outlined.RestaurantMenu, "General"),
    RESTAURANT(Icons.Outlined.Restaurant, "Dining"),
    LOCAL_PIZZA(Icons.Outlined.LocalPizza, "Pizza"),
    LUNCH_DINING(Icons.Outlined.LunchDining, "Sandwiches"),
    DINNER_DINING(Icons.Outlined.DinnerDining, "Pasta / Rice"),
    BRUNCH_DINING(Icons.Outlined.BrunchDining, "Eggs / Brunch"),
    RAMEN_DINING(Icons.Outlined.RamenDining, "Noodles / Soup"),
    SET_MEAL(Icons.Outlined.SetMeal, "Seafood"),
    BAKERY_DINING(Icons.Outlined.BakeryDining, "Bakery"),
    CAKE(Icons.Outlined.Cake, "Desserts"),
    ICECREAM(Icons.Outlined.Icecream, "Ice Cream"),
    LOCAL_CAFE(Icons.Outlined.LocalCafe, "Coffee"),
    LOCAL_BAR(Icons.Outlined.LocalBar, "Cocktails"),
    LOCAL_DRINK(Icons.Outlined.LocalDrink, "Beverages"),
    LIQUOR(Icons.Outlined.Liquor, "Wine / Liquor")
}

internal data class MenuCategory(
    val name: String,
    val icon: MenuCategoryIcon = MenuCategoryIcon.RESTAURANT_MENU,
    val id: String? = null
)

private fun DomainMenuSection.toMenuCategory(): MenuCategory =
    MenuCategory(name = name, id = id)

// The backend wants a raw Double; the UI only ever shows a formatted
// string. Both directions avoid java.util.Formatter (not available on
// every Kotlin target) by sticking to plain arithmetic.
private fun formatPrice(basePrice: Double): String {
    val cents = (basePrice * 100).roundToInt()
    val dollars = cents / 100
    val remainder = (cents % 100).let { if (it < 0) -it else it }
    return "$" + dollars + "." + remainder.toString().padStart(2, '0')
}

private fun parsePrice(label: String): Double =
    label.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0

/**
 * The backend stores each ingredient as a single descriptive string.
 * Fold the editor's separate quantity/unit fields into that name here;
 * round-tripped ingredients come back with quantity/unit blank (see
 * [toUiMenuItem]) since the raw string can't be split back apart.
 */
private fun List<DraftIngredient>.toBackendIngredients(): List<String> =
    map { ingredient ->
        val amount = "${ingredient.quantity} ${ingredient.unit}".trim()
        if (amount.isBlank()) ingredient.name else "${ingredient.name} ($amount)"
    }

private fun DomainMenuItem.toUiMenuItem(sectionId: String, category: String): MenuItem =
    MenuItem(
        name = name,
        description = description.orEmpty(),
        price = formatPrice(basePrice),
        category = category,
        available = available,
        sku = sku,
        ingredients = ingredients.map { DraftIngredient(name = it, quantity = "", unit = "") },
        variants = variants.map {
            DraftVariant(name = it.name, priceDeltaLabel = formatPrice(it.priceDelta), id = it.id)
        },
        // Option-group choices need a separate per-group fetch (getOptionItems)
        // the backend doesn't return nested in this call -- left empty for now.
        optionGroups = optionGroups.map { group ->
            DraftOptionGroup(name = group.name, required = group.isEffectivelyRequired, choices = emptyList())
        },
        id = id,
        sectionId = sectionId,
        basePrice = basePrice,
        displayOrder = displayOrder,
        optionGroupLinkIds = optionGroups.map { it.linkId }
    )

/** Transient success/error feedback shown after an action completes. */
private data class ActionToast(val message: String, val isError: Boolean)

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier
) {
    val screenModel = koinInject<MenuScreenModel>()
    val menuState by screenModel.state.collectAsState()
    var showMenuEditor by remember { mutableStateOf(false) }
    var menuBeingEdited by remember { mutableStateOf<DomainMenu?>(null) }
    var menuStatus by remember { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    val selectedMenu = menuState.selectedMenu

    PlatformBackHandler(enabled = selectedMenu != null && !showMenuEditor) {
        screenModel.closeMenu()
    }

    // createMenu/updateMenu/deleteMenu predate the Result<T> pattern used
    // elsewhere in this screen model -- they only invoke their onSuccess
    // callback on success and otherwise just set menuState.errorMessage.
    // Bridge that into the editor dialog's own loading/success/error
    // takeover (matching the auth screens' animation) instead of a toast.
    LaunchedEffect(menuState.errorMessage) {
        val message = menuState.errorMessage
        val loading = menuStatus as? DialogActionStatus.Loading
        if (message != null && loading != null) {
            menuStatus = DialogActionStatus.Failed(
                title = if (loading.label == "Deleting") "Couldn't delete menu" else "Couldn't save menu",
                message = message
            )
            screenModel.clearError()
        }
    }

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
                profileInitials = listOfNotNull(
                    currentUser?.firstName?.trim()?.firstOrNull(),
                    currentUser?.lastName?.trim()?.firstOrNull()
                ).joinToString("").uppercase().ifEmpty { "?" },
                onOpenMenu = screenModel::openMenu,
                onAddMenu = {
                    screenModel.clearError()
                    menuBeingEdited = null
                    menuStatus = DialogActionStatus.Idle
                    showMenuEditor = true
                },
                onEditMenu = { menu ->
                    screenModel.clearError()
                    menuBeingEdited = menu
                    menuStatus = DialogActionStatus.Idle
                    showMenuEditor = true
                },
                onRetry = screenModel::loadMenus,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MenuDetailsContent(
                menu = selectedMenu,
                canManageMenus = canManageMenus,
                onBack = screenModel::closeMenu,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showMenuEditor) {
            MenuEditorDialog(
                menu = menuBeingEdited,
                existingColors = menuState.menus.mapNotNull { it.color }.toSet(),
                status = menuStatus,
                onRetry = { menuStatus = DialogActionStatus.Idle },
                onSuccessSettled = {
                    showMenuEditor = false
                    menuBeingEdited = null
                    menuStatus = DialogActionStatus.Idle
                },
                onDismiss = {
                    showMenuEditor = false
                    menuBeingEdited = null
                    menuStatus = DialogActionStatus.Idle
                    screenModel.clearError()
                },
                onDeleteMenu = {
                    val deletedId = menuBeingEdited?.id
                    val deletedName = menuBeingEdited?.name
                    if (deletedId != null) {
                        menuStatus = DialogActionStatus.Loading("Deleting")
                        screenModel.deleteMenu(deletedId) {
                            menuStatus = DialogActionStatus.Success("${deletedName ?: "Menu"} deleted")
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
                    menuStatus = DialogActionStatus.Loading(if (editingMenu == null) "Creating" else "Saving")
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
                            menuStatus = DialogActionStatus.Success("$name created")
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
                            menuStatus = DialogActionStatus.Success("$name updated")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionResultToast(
    toast: ActionToast?,
    modifier: Modifier = Modifier
) {
    val visible = toast != null
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "action-toast-alpha"
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (toast != null) {
                key(toast) {
                    AnimatedStatusIcon(
                        icon = if (toast.isError) Icons.Outlined.Close else Icons.Outlined.Check,
                        color = if (toast.isError) ToastErrorRed else ToastSuccessGreen,
                        size = 26.dp,
                        strokeWidth = 1.5.dp
                    )
                }
                Text(
                    text = toast.message,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

/** Shown instead of the item grid/AddItemCard when a section (or "All") has zero items. */
@Composable
private fun MenuItemsEmptyState(
    canManage: Boolean,
    hasRealSection: Boolean,
    onAddItem: () -> Unit,
    onManageSections: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFF3F3F1)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.RestaurantMenu,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MutedInk
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (hasRealSection) "No items in this section yet" else "No sections yet",
            fontFamily = Inter(),
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = TextInk,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasRealSection) {
                "Add your first item to get this section started."
            } else {
                "Create a section before you can add menu items."
            },
            fontFamily = Inter(),
            fontSize = 13.sp,
            color = MutedInk,
            textAlign = TextAlign.Center
        )
        if (canManage) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = if (hasRealSection) onAddItem else onManageSections,
                colors = ButtonDefaults.buttonColors(containerColor = ActiveOlive),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasRealSection) "Add Item" else "Add Section",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun MenuDetailsContent(
    menu: DomainMenu,
    canManageMenus: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenModel = koinInject<MenuScreenModel>()
    val scope = rememberCoroutineScope()
    var selectedCategory by remember(menu.id) { mutableStateOf("All") }
    var sections by remember(menu.id, menu.sections) {
        mutableStateOf(
            listOf(MenuCategory(name = "All")) +
                    menu.sections
                        .filter { it.active }
                        .sortedBy { it.displayOrder }
                        .map { it.toMenuCategory() }
        )
    }
    var showOrderTypeDialog by remember(menu.id) { mutableStateOf(false) }
    var sectionManagerMode by remember(menu.id) { mutableStateOf<SectionManagerMode?>(null) }
    var searchQuery by remember(menu.id) { mutableStateOf("") }
    var selectedSearchItemName by remember(menu.id) { mutableStateOf<String?>(null) }
    var localItems by remember(menu.id, menu.sections) {
        mutableStateOf(
            menu.sections.flatMap { section ->
                section.items.map { it.toUiMenuItem(sectionId = section.id, category = section.name) }
            }
        )
    }
    var showAddItemDialog by remember(menu.id) { mutableStateOf(false) }
    var itemBeingEdited by remember(menu.id) { mutableStateOf<MenuItem?>(null) }
    var itemBeingViewed by remember(menu.id) { mutableStateOf<MenuItem?>(null) }
    var itemPendingDelete by remember(menu.id) { mutableStateOf<MenuItem?>(null) }
    var showItemEditor by remember(menu.id) { mutableStateOf(false) }
    var showVariantEditor by remember(menu.id) { mutableStateOf(false) }
    var showOptionsEditor by remember(menu.id) { mutableStateOf(false) }
    var isReorderingItems by remember(menu.id) { mutableStateOf(false) }

    // Shared by the add/edit item dialogs (only one is ever open at a time)
    // and the delete confirmation, driving the same loading/success/error
    // takeover as the menu editor instead of a corner toast.
    var itemDialogStatus by remember(menu.id) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    var deleteItemStatus by remember(menu.id) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }
    var optionsDialogStatus by remember(menu.id) { mutableStateOf<DialogActionStatus>(DialogActionStatus.Idle) }

    // Still a fixed delay rather than tracking the real openMenu() detail
    // fetch (sections/items already load for real, see toUiMenuItem above) --
    // there's no dedicated "detail loading" flag on MenuUiState yet to key
    // this off of. Menus with 0 real sections would need one to do this
    // properly; harmless in the meantime since it's cosmetic only.
    var isLoadingMenuContent by remember(menu.id) { mutableStateOf(true) }
    LaunchedEffect(menu.id) {
        delay(700)
        isLoadingMenuContent = false
    }

    var detailToast by remember(menu.id) { mutableStateOf<ActionToast?>(null) }
    LaunchedEffect(detailToast) {
        if (detailToast != null) {
            delay(2200)
            detailToast = null
        }
    }

    // An item always belongs to a real section server-side -- with no
    // section to attach to there is nowhere to persist it, so item
    // creation is blocked until at least one real section exists.
    val hasRealSection = sections.any { it.id != null && it.name != "All" }
    fun requireSectionThenOpenAddItem() {
        if (hasRealSection) {
            itemDialogStatus = DialogActionStatus.Idle
            showAddItemDialog = true
        } else {
            detailToast = ActionToast("Create a section before adding items", isError = true)
        }
    }

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
                .padding(
                    start = if (isPhone) 16.dp else 22.dp,
                    end = if (isPhone) 16.dp else 22.dp,
                    top = if (isWidePhone) 8.dp else 18.dp,
                    bottom = 96.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isWidePhone) 10.dp else 18.dp)
        ) {
            if (isPhone) {
                val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    MenuSearchBox(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        items = localItems,
                        itemName = { it.name },
                        itemCategory = { it.category },
                        itemPrice = { it.price },
                        itemAvailable = { it.available },
                        onSuggestionClick = { item ->
                            searchQuery = item.name
                            selectedCategory = item.category.takeIf { cat -> sections.any { it.name == cat } } ?: "All"
                            selectedSearchItemName = item.name
                        },
                        modifier = fieldModifier
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
                    if (canManageMenus) {
                        TextButton(
                            onClick = { requireSectionThenOpenAddItem() },
                            enabled = !isReorderingItems,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .background(ActiveOlive, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Add Item",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                if (!isWidePhone) {
                    searchField(Modifier.fillMaxWidth())
                }
                if (isLoadingMenuContent) {
                    SectionFilterSkeleton(
                        isPhone = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 18.dp)
                    )
                } else {
                    PhoneCategoryFilterRow(
                        sections = sections,
                        selectedCategory = selectedCategory,
                        isReorderingItems = isReorderingItems,
                        canManage = canManageMenus,
                        onSelect = { selectedCategory = it },
                        onManageSections = { sectionManagerMode = SectionManagerMode.EDIT }
                    )
                }
                if (isReorderingItems) {
                    Text(
                        "Hold an item, then drag to reorder.",
                        fontFamily = Inter(),
                        fontSize = 12.sp,
                        color = MutedInk
                    )
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

                    if (isLoadingMenuContent) {
                        SectionFilterSkeleton(isPhone = false)
                    } else {
                        CategoryButtons(
                            items = sections,
                            selected = selectedCategory,
                            canManage = canManageMenus,
                            onSelected = { if (!isReorderingItems) selectedCategory = it },
                            onManageSections = { sectionManagerMode = SectionManagerMode.EDIT }
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    MenuSearchBox(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        items = localItems,
                        itemName = { it.name },
                        itemCategory = { it.category },
                        itemPrice = { it.price },
                        itemAvailable = { it.available },
                        onSuggestionClick = { item ->
                            searchQuery = item.name
                            selectedCategory = item.category.takeIf { cat -> sections.any { it.name == cat } } ?: "All"
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

            if (isLoadingMenuContent) {
                MenuItemCardSkeletonGrid(
                    columns = columns,
                    isPhone = isPhone,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isReorderingItems) {
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
            } else if (visibleItems.isEmpty() && (!hasRealSection || !canManageMenus)) {
                MenuItemsEmptyState(
                    canManage = canManageMenus,
                    hasRealSection = hasRealSection,
                    onAddItem = { requireSectionThenOpenAddItem() },
                    onManageSections = { sectionManagerMode = SectionManagerMode.EDIT },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val rowSlots: List<MenuItem?> = if (!canManageMenus) {
                    visibleItems
                } else {
                    listOf(null) + visibleItems
                }

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
                                        onClick = { requireSectionThenOpenAddItem() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    MenuItemCard(
                                        name = item.name,
                                        description = item.description,
                                        price = item.price,
                                        category = item.category,
                                        available = item.available,
                                        selected = item.name == selectedSearchItemName,
                                        isPhone = isPhone,
                                        canEdit = canManageMenus,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onExpand = {
                                            itemBeingViewed = item
                                        },
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
                                        },
                                        onToggleAvailability = {
                                            val sectionId = item.sectionId
                                            val itemId = item.id
                                            if (canManageMenus && sectionId != null && itemId != null) {
                                                scope.launch {
                                                    screenModel.updateItemAvailability(
                                                        menu.id, sectionId, itemId, !item.available
                                                    ).fold(
                                                        onSuccess = { updated ->
                                                            localItems = localItems.map { current ->
                                                                if (current === item) {
                                                                    current.copy(available = updated.available)
                                                                } else {
                                                                    current
                                                                }
                                                            }
                                                        },
                                                        onFailure = { error ->
                                                            detailToast = ActionToast(
                                                                error.message ?: "Could not update availability",
                                                                isError = true
                                                            )
                                                        }
                                                    )
                                                }
                                            }
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
                status = itemDialogStatus,
                onRetry = { itemDialogStatus = DialogActionStatus.Idle },
                onSuccessSettled = {
                    showAddItemDialog = false
                    itemDialogStatus = DialogActionStatus.Idle
                },
                onDismiss = {
                    showAddItemDialog = false
                    itemDialogStatus = DialogActionStatus.Idle
                },
                onSave = { name, priceLabel, sku, description, imageFileName, available, ingredients ->
                    val targetSection = sections.firstOrNull { it.name == selectedCategory && it.id != null }
                        ?: sections.firstOrNull { it.name != "All" && it.id != null }
                    if (targetSection?.id == null) {
                        itemDialogStatus = DialogActionStatus.Failed(
                            title = "Create a section first",
                            message = "This menu has no sections yet, so there's nowhere to add an item."
                        )
                    } else {
                        val category = targetSection.name
                        val basePrice = parsePrice(priceLabel)
                        itemDialogStatus = DialogActionStatus.Loading("Creating")
                        scope.launch {
                            screenModel.createItem(
                                menuId = menu.id,
                                sectionId = targetSection.id,
                                input = MenuItemInput(
                                    sku = sku,
                                    name = name,
                                    description = description,
                                    basePrice = basePrice,
                                    available = available,
                                    displayOrder = localItems.count { it.category == category },
                                    ingredients = ingredients.toBackendIngredients()
                                )
                            ).fold(
                                onSuccess = { created ->
                                    localItems = localItems + created.toUiMenuItem(targetSection.id, category)
                                    itemDialogStatus = DialogActionStatus.Success("$name created")
                                },
                                onFailure = { error ->
                                    itemDialogStatus = DialogActionStatus.Failed(
                                        message = error.message ?: "Could not add $name"
                                    )
                                }
                            )
                        }
                    }
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
                    status = itemDialogStatus,
                    onRetry = { itemDialogStatus = DialogActionStatus.Idle },
                    onSuccessSettled = {
                        showItemEditor = false
                        itemBeingEdited = null
                        itemDialogStatus = DialogActionStatus.Idle
                    },
                    onDismiss = {
                        showItemEditor = false
                        itemBeingEdited = null
                        itemDialogStatus = DialogActionStatus.Idle
                    },
                    onDeleteItem = {
                        showItemEditor = false
                        itemBeingEdited = null
                        itemDialogStatus = DialogActionStatus.Idle
                        itemPendingDelete = editingItem
                        deleteItemStatus = DialogActionStatus.Idle
                    },
                    onSave = { name, priceLabel, sku, description, imageFileName, available, ingredients ->
                        val basePrice = parsePrice(priceLabel)
                        val sectionId = editingItem.sectionId
                        val itemId = editingItem.id
                        if (sectionId != null && itemId != null) {
                            itemDialogStatus = DialogActionStatus.Loading("Saving")
                            scope.launch {
                                screenModel.updateItem(
                                    menuId = menu.id,
                                    sectionId = sectionId,
                                    itemId = itemId,
                                    input = MenuItemInput(
                                        sku = sku,
                                        name = name,
                                        description = description,
                                        basePrice = basePrice,
                                        available = available,
                                        displayOrder = editingItem.displayOrder,
                                        ingredients = ingredients.toBackendIngredients()
                                    )
                                ).fold(
                                    onSuccess = { updated ->
                                        localItems = localItems.map { current ->
                                            if (current === editingItem) {
                                                updated.toUiMenuItem(sectionId, current.category)
                                            } else {
                                                current
                                            }
                                        }
                                        itemDialogStatus = DialogActionStatus.Success("$name updated")
                                    },
                                    onFailure = { error ->
                                        itemDialogStatus = DialogActionStatus.Failed(
                                            message = error.message ?: "Could not update $name"
                                        )
                                    }
                                )
                            }
                        } else {
                            localItems = localItems.map { current ->
                                if (current === editingItem) {
                                    current.copy(
                                        name = name,
                                        description = description.orEmpty(),
                                        price = priceLabel,
                                        available = available,
                                        sku = sku,
                                        imageFileName = imageFileName,
                                        ingredients = ingredients,
                                        basePrice = basePrice
                                    )
                                } else {
                                    current
                                }
                            }
                            itemDialogStatus = DialogActionStatus.Success("$name updated")
                        }
                    }
                )
            }
        }

        itemBeingViewed?.let { viewingItem ->
            ItemDetailDialog(
                item = viewingItem,
                onDismiss = { itemBeingViewed = null }
            )
        }

        itemPendingDelete?.let { deletingItem ->
            MenuNestedDialog(
                onDismissRequest = {
                    if (deleteItemStatus !is DialogActionStatus.Loading) {
                        itemPendingDelete = null
                        deleteItemStatus = DialogActionStatus.Idle
                    }
                },
                title = {
                    if (deleteItemStatus is DialogActionStatus.Idle) {
                        Text("Delete ${deletingItem.name}?", fontFamily = Inter(), fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    if (deleteItemStatus is DialogActionStatus.Idle) {
                        Text(
                            "This removes the item from the menu. This can't be undone.",
                            fontFamily = Inter()
                        )
                    } else {
                        DialogStatusBody(
                            status = deleteItemStatus,
                            onRetry = { deleteItemStatus = DialogActionStatus.Idle },
                            onCancel = {
                                itemPendingDelete = null
                                deleteItemStatus = DialogActionStatus.Idle
                            },
                            onSuccessSettled = {
                                itemPendingDelete = null
                                deleteItemStatus = DialogActionStatus.Idle
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    if (deleteItemStatus is DialogActionStatus.Idle) {
                        Button(
                            onClick = {
                                val sectionId = deletingItem.sectionId
                                val itemId = deletingItem.id
                                if (sectionId != null && itemId != null) {
                                    deleteItemStatus = DialogActionStatus.Loading("Deleting")
                                    scope.launch {
                                        screenModel.deleteItem(menu.id, sectionId, itemId).fold(
                                            onSuccess = {
                                                localItems = localItems.filterNot { it === deletingItem }
                                                deleteItemStatus = DialogActionStatus.Success("${deletingItem.name} deleted")
                                            },
                                            onFailure = { error ->
                                                deleteItemStatus = DialogActionStatus.Failed(
                                                    message = error.message ?: "Could not delete ${deletingItem.name}"
                                                )
                                            }
                                        )
                                    }
                                } else {
                                    localItems = localItems.filterNot { it === deletingItem }
                                    itemPendingDelete = null
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB13A2F))
                        ) {
                            Text("Delete", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    if (deleteItemStatus is DialogActionStatus.Idle) {
                        TextButton(onClick = { itemPendingDelete = null }) {
                            Text("Cancel", fontFamily = Inter(), color = MutedInk)
                        }
                    }
                }
            )
        }

        if (showVariantEditor) {
            itemBeingEdited?.let { editingItem ->
                val sectionId = editingItem.sectionId
                val itemId = editingItem.id
                VariantEditorDialog(
                    itemName = editingItem.name,
                    variants = editingItem.variants,
                    onDismiss = {
                        showVariantEditor = false
                        itemBeingEdited = null
                    },
                    onDoneEditing = { updatedVariants ->
                        showVariantEditor = false
                        itemBeingEdited = null
                        localItems = localItems.map { current ->
                            if (current === editingItem) current.copy(variants = updatedVariants) else current
                        }
                    },
                    onCreateVariant = { name, priceDelta, displayOrder ->
                        if (sectionId == null || itemId == null) {
                            Result.failure(IllegalStateException("Item must be saved before adding variants."))
                        } else {
                            screenModel.createVariant(
                                menuId = menu.id,
                                sectionId = sectionId,
                                itemId = itemId,
                                input = MenuVariantInput(name = name, priceDelta = priceDelta, displayOrder = displayOrder)
                            ).map { it.id }
                        }
                    },
                    onUpdateVariant = { variantId, name, priceDelta, displayOrder ->
                        if (sectionId == null || itemId == null) {
                            Result.failure(IllegalStateException("Item must be saved before editing variants."))
                        } else {
                            screenModel.updateVariant(
                                menuId = menu.id,
                                sectionId = sectionId,
                                itemId = itemId,
                                variantId = variantId,
                                input = MenuVariantInput(name = name, priceDelta = priceDelta, displayOrder = displayOrder)
                            ).map { }
                        }
                    },
                    onDeleteVariant = { variantId ->
                        if (sectionId == null || itemId == null) {
                            Result.failure(IllegalStateException("Item must be saved before deleting variants."))
                        } else {
                            screenModel.deleteVariant(menu.id, sectionId, itemId, variantId)
                        }
                    }
                )
            }
        }

        if (showOptionsEditor) {
            itemBeingEdited?.let { editingItem ->
                OptionsEditorDialog(
                    itemName = editingItem.name,
                    optionGroups = editingItem.optionGroups,
                    status = optionsDialogStatus,
                    onRetry = { optionsDialogStatus = DialogActionStatus.Idle },
                    onSuccessSettled = {
                        showOptionsEditor = false
                        itemBeingEdited = null
                        optionsDialogStatus = DialogActionStatus.Idle
                    },
                    onDismiss = {
                        showOptionsEditor = false
                        itemBeingEdited = null
                        optionsDialogStatus = DialogActionStatus.Idle
                    },
                    onSave = { updatedOptionGroups ->
                        val sectionId = editingItem.sectionId
                        val itemId = editingItem.id
                        if (sectionId != null && itemId != null) {
                            optionsDialogStatus = DialogActionStatus.Loading("Saving")
                            scope.launch {
                                screenModel.replaceItemOptionGroups(
                                    menuId = menu.id,
                                    sectionId = sectionId,
                                    itemId = itemId,
                                    previousLinkIds = editingItem.optionGroupLinkIds,
                                    groups = updatedOptionGroups
                                ).fold(
                                    onSuccess = {
                                        localItems = localItems.map { current ->
                                            if (current === editingItem) {
                                                current.copy(optionGroups = updatedOptionGroups)
                                            } else {
                                                current
                                            }
                                        }
                                        optionsDialogStatus = DialogActionStatus.Success("Options saved")
                                    },
                                    onFailure = { error ->
                                        optionsDialogStatus = DialogActionStatus.Failed(
                                            message = error.message ?: "Could not save options"
                                        )
                                    }
                                )
                            }
                        } else {
                            localItems = localItems.map { current ->
                                if (current === editingItem) {
                                    current.copy(optionGroups = updatedOptionGroups)
                                } else {
                                    current
                                }
                            }
                            optionsDialogStatus = DialogActionStatus.Success("Options saved")
                        }
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
                    val itemsInScope = if (selectedCategory == "All") {
                        localItems
                    } else {
                        localItems.filter { it.category == selectedCategory }
                    }
                    if (itemsInScope.size < 2) {
                        detailToast = ActionToast("Add at least 2 items in this section before you can change their order.", isError = true)
                    } else {
                        searchQuery = ""
                        selectedSearchItemName = null
                        isReorderingItems = true
                    }
                },
                onSections = {
                    showOrderTypeDialog = false
                    if (sections.count { it.name != "All" } < 2) {
                        detailToast = ActionToast("Add at least 2 sections before you can change their order.", isError = true)
                    } else {
                        sectionManagerMode = SectionManagerMode.REORDER
                    }
                }
            )
        }

        sectionManagerMode?.let { managerMode ->
            SectionManagerDialog(
                sections = sections,
                itemCounts = localItems.groupingBy { it.category }.eachCount(),
                mode = managerMode,
                onDismiss = { sectionManagerMode = null },
                onDoneEditing = { updatedSections ->
                    sectionManagerMode = null
                    sections = listOf(MenuCategory(name = "All")) + updatedSections.filterNot { it.name == "All" }
                    if (sections.none { it.name == selectedCategory }) selectedCategory = "All"
                },
                onSaveOrder = { updatedSections ->
                    sectionManagerMode = null
                    val menuId = menu.id
                    val toSave = updatedSections.filter { it.name != "All" }
                    scope.launch {
                        val outcomes = coroutineScope {
                            toSave.mapIndexed { index, category ->
                                async { category to screenModel.updateSection(menuId, category.id!!, category.name, index) }
                            }.awaitAll()
                        }
                        val failedNames = outcomes.filter { it.second.isFailure }.map { it.first.name }
                        sections = listOf(MenuCategory(name = "All")) + toSave
                        detailToast = if (failedNames.isEmpty()) {
                            ActionToast("Section order saved", isError = false)
                        } else {
                            ActionToast("Couldn't save order for: ${failedNames.distinct().joinToString(", ")}", isError = true)
                        }
                    }
                },
                onCreateSection = { name, displayOrder ->
                    screenModel.createSection(menu.id, name, displayOrder).map { it.id }
                },
                onRenameSection = { sectionId, name, displayOrder ->
                    val oldName = sections.firstOrNull { it.id == sectionId }?.name
                    screenModel.updateSection(menu.id, sectionId, name, displayOrder).map {
                        sections = sections.map { category -> if (category.id == sectionId) category.copy(name = name) else category }
                        if (oldName != null && oldName != name) {
                            localItems = localItems.map { item ->
                                if (item.category == oldName) item.copy(category = name) else item
                            }
                            if (selectedCategory == oldName) selectedCategory = name
                        }
                    }
                },
                onDeleteSection = { sectionId ->
                    screenModel.deleteSection(menu.id, sectionId).map {
                        sections = sections.filterNot { category -> category.id == sectionId }
                        if (selectedCategory !in sections.map { it.name }) selectedCategory = "All"
                    }
                }
            )
        }

        if (canManageMenus) {
            TextButton(
                onClick = {
                    if (isReorderingItems) {
                        isReorderingItems = false
                        val menuId = menu.id
                        val toUpdate = localItems.groupBy { it.sectionId }
                            .filterKeys { it != null }
                            .flatMap { (sectionId, itemsInSection) ->
                                itemsInSection.mapIndexedNotNull { index, item ->
                                    val itemId = item.id
                                    if (sectionId != null && itemId != null && item.displayOrder != index) {
                                        Triple(item, sectionId, index)
                                    } else {
                                        null
                                    }
                                }
                            }

                        if (toUpdate.isNotEmpty()) {
                            scope.launch {
                                val outcomes = coroutineScope {
                                    toUpdate.map { (item, sectionId, index) ->
                                        async {
                                            Triple(
                                                item,
                                                index,
                                                screenModel.updateItem(
                                                    menuId = menuId,
                                                    sectionId = sectionId,
                                                    itemId = item.id!!,
                                                    input = MenuItemInput(
                                                        sku = item.sku,
                                                        name = item.name,
                                                        description = item.description,
                                                        basePrice = item.basePrice,
                                                        available = item.available,
                                                        displayOrder = index,
                                                        ingredients = item.ingredients.toBackendIngredients()
                                                    )
                                                )
                                            )
                                        }
                                    }.awaitAll()
                                }

                                val failedNames = mutableListOf<String>()
                                val newOrderByItem = mutableMapOf<MenuItem, Int>()
                                outcomes.forEach { (item, index, result) ->
                                    result.fold(
                                        onSuccess = { newOrderByItem[item] = index },
                                        onFailure = { failedNames += item.name }
                                    )
                                }

                                localItems = localItems.map { item ->
                                    newOrderByItem[item]?.let { index -> item.copy(displayOrder = index) } ?: item
                                }

                                if (failedNames.isNotEmpty()) {
                                    detailToast = ActionToast(
                                        "Couldn't reorder: ${failedNames.joinToString(", ")}",
                                        isError = true
                                    )
                                }
                            }
                        }
                    } else {
                        showOrderTypeDialog = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isPhone) 12.dp else 18.dp)
                    .height(if (isPhone) 40.dp else 44.dp)
                    .shadow(12.dp, RoundedCornerShape(if (isPhone) 8.dp else 10.dp))
                    .clip(RoundedCornerShape(if (isPhone) 8.dp else 10.dp))
                    .background(if (isReorderingItems) TextInk else ActiveOlive),
                contentPadding = PaddingValues(horizontal = if (isPhone) 10.dp else 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Icon(
                    imageVector = if (isReorderingItems) Icons.Filled.Check else Icons.Outlined.DragIndicator,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 16.dp else 18.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(if (isPhone) 6.dp else 8.dp))
                Text(
                    text = if (isReorderingItems) "Save Order" else "Change Order",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isPhone) 12.sp else 13.sp,
                    color = Color.White
                )
            }
        }

        ActionResultToast(
            toast = detailToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        )
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
        val cardHeight = when {
            isPhone -> phoneMenuItemHeight(LocalDensity.current.fontScale)
            columns <= 2 -> 430.dp
            else -> 350.dp
        }
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
                        name = item.name,
                        description = item.description,
                        price = item.price,
                        category = item.category,
                        available = item.available,
                        selected = false,
                        isPhone = isPhone,
                        isReordering = true,
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
