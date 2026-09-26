package com.saporini.mobile_desktop.pos.menu.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenu
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuDish
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuSectionView
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategoryIcon
import com.saporini.mobile_desktop.pos.menu.ui.MenuScreenModel
import com.saporini.mobile_desktop.pos.menu.ui.ReorderableMenuItemGrid
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftIngredient
import com.saporini.mobile_desktop.pos.menu.ui.item.ItemDetailDialog
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItem
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItemCard
import com.saporini.mobile_desktop.pos.menu.ui.item.MenuItemCardSkeletonGrid
import com.saporini.mobile_desktop.pos.menu.ui.MenuSearchBox
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import com.saporini.mobile_desktop.pos.menu.ui.menu.menuContentColumns
import com.saporini.mobile_desktop.pos.menu.ui.section.CategoryButtons
import com.saporini.mobile_desktop.pos.menu.ui.section.PhoneCategoryFilterRow
import com.saporini.mobile_desktop.pos.menu.ui.section.SectionFilterSkeleton
import com.saporini.mobile_desktop.pos.menu.ui.section.SectionManagerDialog
import com.saporini.mobile_desktop.pos.menu.ui.section.SectionManagerMode
import com.saporini.mobile_desktop.pos.menu.ui.section.inFilterOrder
import com.saporini.mobile_desktop.pos.menu.ui.section.withAllFilterAt
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Ink = Color(0xFF222426)
private val Muted = Color(0xFF747572)
private val Olive = Color(0xFF4F7942)
private val Border = Color(0xFFE8E5E1)
private val ErrorRed = Color(0xFFB13A2F)

// Uses the same section chips and dish cards as a regular menu. Its only actions are
// renaming/reordering online sections and reordering dishes inside those sections.
@Composable
internal fun OnlineMenuContent(
    model: MenuScreenModel,
    canManageMenus: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isPhone = isPhoneMenuWindow()
    var menu by remember { mutableStateOf<OnlineMenu?>(null) }
    var localItems by remember { mutableStateOf(emptyList<MenuItem>()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchItemName by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var isReorderingItems by remember { mutableStateOf(false) }
    var sectionManagerMode by remember { mutableStateOf<SectionManagerMode?>(null) }
    var itemBeingViewed by remember { mutableStateOf<MenuItem?>(null) }

    fun applyMenu(value: OnlineMenu) {
        menu = value
        localItems = value.sections.flatMap { section -> section.items.map { it.toUi(section) } }
        val firstSection = value.sections.firstOrNull()?.name
        if (selectedCategory != "All" && value.sections.none { it.name == selectedCategory }) {
            selectedCategory = firstSection ?: "All"
        }
        loading = false
        loadError = null
    }

    LaunchedEffect(reload) {
        loading = true
        model.loadOnlineMenu().fold(
            onSuccess = ::applyMenu,
            onFailure = {
                loading = false
                loadError = it.message ?: "Couldn't load the online menu"
            }
        )
    }

    val currentMenu = menu
    val categories = remember(currentMenu) {
        currentMenu?.sections.orEmpty().map { MenuCategory(it.name, MenuCategoryIcon.RESTAURANT_MENU, it.id) }
            .withAllFilterAt(0)
    }
    val filteredByCategory = if (selectedCategory == "All") localItems else localItems.filter { it.category == selectedCategory }
    val visibleItems = if (searchQuery.isBlank()) filteredByCategory else filteredByCategory.filter { item ->
        item.name.contains(searchQuery.trim(), ignoreCase = true) || item.ingredients.any { it.name.contains(searchQuery.trim(), true) }
    }
    val reorderableItemCount = if (selectedCategory == "All") {
        localItems.size
    } else {
        localItems.count { it.category == selectedCategory }
    }
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
                selectedCategory = item.category
                selectedSearchItemName = item.name
            },
            modifier = fieldModifier
        )
    }

    Box(modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = if (isPhone) 16.dp else 22.dp, end = if (isPhone) 16.dp else 22.dp, top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (isPhone) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to menus", tint = Ink) }
                    Icon(Icons.Outlined.Public, null, Modifier.padding(end = 2.dp), tint = Olive)
                    Text("Online menu", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink, maxLines = 1)
                }
                searchField(Modifier.fillMaxWidth())
                if (loading) SectionFilterSkeleton(isPhone = true, modifier = Modifier.fillMaxWidth())
                else PhoneCategoryFilterRow(
                    sections = categories,
                    selectedCategory = selectedCategory,
                    isReorderingItems = isReorderingItems,
                    canManage = canManageMenus,
                    onSelect = { selectedCategory = it; searchQuery = "" },
                    onManageSections = { sectionManagerMode = SectionManagerMode.EDIT }
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to menus", tint = Ink) }
                    Icon(Icons.Outlined.Public, null, Modifier.padding(end = 2.dp), tint = Olive)
                    Text("Online menu", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink, maxLines = 1)
                    Text("Website preview", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1f)) {
                        if (loading) SectionFilterSkeleton(isPhone = false)
                        else CategoryButtons(
                            items = categories,
                            selected = selectedCategory,
                            canManage = canManageMenus,
                            onSelected = { if (!isReorderingItems) { selectedCategory = it; searchQuery = "" } },
                            onManageSections = { sectionManagerMode = SectionManagerMode.EDIT }
                        )
                    }
                    searchField(Modifier.width(240.dp))
                }
            }

            actionError?.let { message ->
                Text(message, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ErrorRed.copy(alpha = 0.08f)).padding(12.dp), fontFamily = Inter(), fontSize = 13.sp, color = ErrorRed)
            }

            if (loading && menu == null) {
                MenuItemCardSkeletonGrid(columns = if (isPhone) 1 else 3, isPhone = isPhone, modifier = Modifier.fillMaxWidth())
            } else if (loadError != null && menu == null) {
                Text(loadError.orEmpty(), Modifier.fillMaxWidth().padding(32.dp), fontFamily = Inter(), fontSize = 14.sp, color = ErrorRed, textAlign = TextAlign.Center)
                TextButton(onClick = { reload++ }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Try again", color = Olive) }
            } else if (currentMenu?.sections.isNullOrEmpty()) {
                Text("Nothing is online yet. Switch on “Show in online menu” in a dish form to add it here.", Modifier.fillMaxWidth().padding(32.dp), fontFamily = Inter(), fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
            } else {
                if (isReorderingItems) {
                    Text("Hold and drag dishes to change their order.", fontFamily = Inter(), fontSize = 12.sp, color = Muted)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = menuContentColumns(maxWidth, 280.dp, 14.dp, LocalDensity.current.fontScale)
                    if (isReorderingItems) {
                        ReorderableMenuItemGrid(
                            items = visibleItems,
                            columns = columns,
                            isPhone = isPhone,
                            onReorder = { reordered ->
                                if (selectedCategory == "All") localItems = reordered
                                else {
                                    val reorderedIterator = reordered.iterator()
                                    localItems = localItems.map { existing ->
                                        if (existing.category == selectedCategory && reorderedIterator.hasNext()) reorderedIterator.next() else existing
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (visibleItems.isEmpty()) {
                        Text(if (searchQuery.isBlank()) "No dishes in this section yet." else "No dishes match your search.", Modifier.fillMaxWidth().padding(32.dp), fontFamily = Inter(), fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            visibleItems.chunked(columns).forEach { rowItems ->
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    rowItems.forEach { item ->
                                        MenuItemCard(
                                            name = item.name,
                                            description = item.description,
                                            ingredients = item.ingredients.map { it.name },
                                            price = item.price,
                                            category = item.category,
                                            available = item.available,
                                            selected = item.name == selectedSearchItemName,
                                            isPhone = isPhone,
                                            canEdit = false,
                                            availabilityEditable = false,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            onExpand = { itemBeingViewed = item }
                                        )
                                    }
                                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (canManageMenus && currentMenu != null && currentMenu.sections.isNotEmpty() && reorderableItemCount >= 2) {
            TextButton(
                onClick = {
                    if (isReorderingItems) {
                        isReorderingItems = false
                        busy = true
                        actionError = null
                        scope.launch {
                            val grouped = localItems.filter { it.sectionId != null }.groupBy { it.sectionId!! }
                            var failure: Throwable? = null
                            for ((sectionId, dishes) in grouped) {
                                val ids = dishes.mapNotNull { it.id }
                                if (ids.size != dishes.size) continue
                                model.reorderOnlineMenuItems(sectionId, ids).onFailure { failure = it }
                            }
                            if (failure == null) model.loadOnlineMenu().fold(::applyMenu, { failure = it })
                            if (failure != null) actionError = failure?.message ?: "Couldn't save dish order"
                            busy = false
                        }
                    } else {
                        searchQuery = ""
                        selectedSearchItemName = null
                        isReorderingItems = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(if (isPhone) 12.dp else 18.dp)
                    .height(if (isPhone) 40.dp else 44.dp).shadow(12.dp, RoundedCornerShape(9.dp))
                    .clip(RoundedCornerShape(9.dp)).background(if (isReorderingItems) Ink else Olive),
                contentPadding = PaddingValues(horizontal = if (isPhone) 10.dp else 16.dp),
                enabled = !busy
            ) {
                Icon(Icons.Outlined.DragIndicator, null, Modifier.padding(end = 7.dp).height(18.dp), tint = Color.White)
                Text(if (isReorderingItems) "Save order" else "Change items position", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (isPhone) 12.sp else 13.sp, color = Color.White)
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(14.dp).height(20.dp), color = Olive, strokeWidth = 2.dp)
    }

    sectionManagerMode?.let { mode ->
        val sectionCategories = (currentMenu?.sections.orEmpty().map { MenuCategory(it.name, MenuCategoryIcon.RESTAURANT_MENU, it.id) } + MenuCategory("All"))
            .withAllFilterAt(0)
        SectionManagerDialog(
            sections = sectionCategories,
            itemCounts = localItems.groupingBy { it.category }.eachCount(),
            mode = mode,
            allowCreateDelete = false,
            onDismiss = { sectionManagerMode = null },
            onChangeOrder = { sectionManagerMode = SectionManagerMode.REORDER },
            onDoneEditing = { sectionManagerMode = null },
            onSaveOrder = { updated ->
                sectionManagerMode = null
                val sectionIds = updated.mapNotNull { it.id }
                scope.launch {
                    busy = true
                    actionError = null
                    model.reorderOnlineMenuSections(sectionIds).fold(
                        onSuccess = { model.loadOnlineMenu().fold(::applyMenu, { actionError = it.message ?: "Couldn't refresh the online menu" }) },
                        onFailure = { actionError = it.message ?: "Couldn't save section order" }
                    )
                    busy = false
                }
            },
            onCreateSection = { _, _ -> Result.failure(IllegalStateException("Online sections are created from dish forms.")) },
            onRenameSection = { sectionId, name, _ ->
                model.renameOnlineMenuSection(sectionId, name).map { renamed ->
                    val updated = currentMenu?.sections.orEmpty().map { section -> if (section.id == sectionId) section.copy(name = renamed.name) else section }
                    currentMenu?.let { applyMenu(it.copy(sections = updated)) }
                    if (selectedCategory != "All" && categories.any { it.id == sectionId }) selectedCategory = renamed.name
                }
            },
            onDeleteSection = { _, _ -> Result.failure(IllegalStateException("Online sections are removed when their last dish is taken offline.")) }
        )
    }
    itemBeingViewed?.let { ItemDetailDialog(it) { itemBeingViewed = null } }
}

private fun OnlineMenuDish.toUi(section: OnlineMenuSectionView): MenuItem {
    val cardDescription = buildList {
        description?.takeIf(String::isNotBlank)?.let(::add)
        if (!visible) add("Hidden: ${hiddenReason ?: "not visible on the website today"}")
    }.joinToString(" · ")
    return MenuItem(
        name = name,
        description = cardDescription,
        price = "$" + ((basePrice * 100).roundToInt() / 100) + "." + ((basePrice * 100).roundToInt() % 100).let { if (it < 0) -it else it }.toString().padStart(2, '0'),
        category = section.name,
        available = visible,
        sendToKitchen = sendToKitchen,
        showOnline = true,
        sku = sku,
        ingredients = ingredients.map { DraftIngredient(it, "", "") },
        id = id,
        sectionId = section.id,
        basePrice = basePrice,
        displayOrder = displayOrder
    )
}
