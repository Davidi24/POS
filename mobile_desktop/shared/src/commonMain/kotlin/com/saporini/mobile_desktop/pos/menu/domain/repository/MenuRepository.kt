package com.saporini.mobile_desktop.pos.menu.domain.repository

import com.saporini.mobile_desktop.pos.menu.data.dto.DishIngredientRequestDto
import com.saporini.mobile_desktop.pos.menu.data.dto.DishRecipeDto
import com.saporini.mobile_desktop.pos.menu.data.dto.IngredientChoiceDto
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNight
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNightInput
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItemOptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuPage
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuVariant
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuWaiterView
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenu
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroupType
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionItem
import com.saporini.mobile_desktop.pos.menu.domain.model.SpecialMenuKind

data class CreateMenuInput(
    val restaurantId: String,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val active: Boolean = true,
    val displayOrder: Int = 0,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val availableFromDate: String? = null,
    val availableUntilDate: String? = null,
    val color: String? = null,
    val special: Boolean = false,
    // With special: what it's for; an EVENT menu needs eventId.
    val specialKind: SpecialMenuKind? = null,
    val eventId: String? = null,
    // Show in POS (to waiters and at the till).
    val shownToWaiters: Boolean = true
)

data class UpdateMenuInput(
    val allFilterPosition: Int? = null,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val active: Boolean,
    val displayOrder: Int,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val availableFromDate: String? = null,
    val availableUntilDate: String? = null,
    val color: String? = null,
    // Null keeps it as it is.
    val special: Boolean? = null,
    // With special = true: what it's for (null keeps it); an EVENT menu needs eventId.
    val specialKind: SpecialMenuKind? = null,
    val eventId: String? = null,
    // Show in POS (to waiters and at the till); null keeps it.
    val shownToWaiters: Boolean? = null
)

data class MenuSectionInput(
    val name: String,
    val description: String? = null,
    val active: Boolean = true,
    val displayOrder: Int = 0
)

data class MenuItemInput(
    val sku: String? = null,
    val name: String,
    val description: String? = null,
    val basePrice: Double,
    val imageUrl: String? = null,
    val available: Boolean = true,
    // Off for counter items like a cola. Null leaves an existing item's setting unchanged.
    val sendToKitchen: Boolean? = null,
    // On: the dish appears in the customer-facing online menu. Null leaves an existing item's setting unchanged.
    val showOnline: Boolean? = null,
    // Which online section: an existing one by id, or by name (reused if it exists, created otherwise).
    val onlineSectionId: String? = null,
    val onlineSectionName: String? = null,
    val displayOrder: Int = 0,
    val ingredients: List<String> = emptyList(),
    val sectionId: String? = null,
    // Special-menu extras. Leave occasionCodes null to keep both as they are.
    val orderBeforeHours: Int? = null,
    val occasionCodes: List<String>? = null
)

data class MenuVariantInput(
    val name: String,
    val sku: String? = null,
    val priceDelta: Double = 0.0,
    val isDefault: Boolean = false,
    val active: Boolean = true,
    val displayOrder: Int = 0
)

data class CreateMenuItemOptionGroupInput(
    val optionGroupId: String,
    val displayOrder: Int = 0,
    val minSelectOverride: Int? = null,
    val maxSelectOverride: Int? = null,
    val requiredOverride: Boolean? = null
)

data class OptionGroupTypeInput(
    val code: String? = null,
    val name: String,
    val description: String? = null
)

data class CreateOptionGroupInput(
    val restaurantId: String,
    val typeId: String,
    val name: String,
    val description: String? = null,
    val minSelect: Int = 0,
    val maxSelect: Int = 0,
    val required: Boolean = false,
    val active: Boolean = true,
    val displayOrder: Int = 0
)

data class OptionItemInput(
    val code: String? = null,
    val name: String,
    val priceDelta: Double = 0.0,
    val available: Boolean = true,
    val displayOrder: Int = 0,
    val inventoryRecipeId: String? = null,
    val inventoryRecipeQuantity: Double? = null
)

interface MenuRepository {

    // Online menu
    suspend fun getOnlineMenu(restaurantId: String): OnlineMenu
    suspend fun getOnlineMenuSections(restaurantId: String): List<OnlineMenuSection>
    suspend fun renameOnlineMenuSection(restaurantId: String, sectionId: String, name: String): OnlineMenuSection
    suspend fun reorderOnlineMenuSections(restaurantId: String, sectionIds: List<String>): List<OnlineMenuSection>
    suspend fun reorderOnlineMenuItems(restaurantId: String, sectionId: String, itemIds: List<String>): OnlineMenu
    suspend fun deleteOnlineMenuSection(restaurantId: String, sectionId: String)


    // Menus

    suspend fun getMenus(
        restaurantId: String? = null,
        active: Boolean? = null,
        search: String? = null,
        page: Int = 0,
        size: Int = 20,
        sortBy: String = "displayOrder",
        direction: String = "asc"
    ): MenuPage

    suspend fun createMenu(
        input: CreateMenuInput
    ): Menu

    // What waiters see right now (schedule, "Menus shown", event nights), decided by the server.
    suspend fun getMenusAvailableNow(restaurantId: String): List<Menu> =
        getMenus(restaurantId = restaurantId, active = true, size = 100).items

    // The order menus are shown in (first = shown first).
    suspend fun saveMenuOrder(restaurantId: String, menuIds: List<String>): Unit =
        throw UnsupportedOperationException("Changing the menu order isn't available here")

    // Which menus waiters see (Menu → "Menus shown").
    suspend fun getWaiterView(restaurantId: String): MenuWaiterView = MenuWaiterView()

    suspend fun saveWaiterView(restaurantId: String, view: MenuWaiterView): MenuWaiterView =
        throw UnsupportedOperationException("Choosing the waiters' menus isn't available here")

    // The restaurant's event nights, for linking an event menu (each says which menu it already has).
    suspend fun getEventNights(restaurantId: String): List<MenuEventNight> = emptyList()

    // Adds an event night from the menu editor (the menu links to it when the menu saves).
    suspend fun createEventNight(restaurantId: String, input: MenuEventNightInput): MenuEventNight =
        throw UnsupportedOperationException("Adding events isn't available here")

    // Changes an event night's name, icon or days, keeping its menu (whose days follow the event's).
    suspend fun updateEventNight(restaurantId: String, event: MenuEventNight, input: MenuEventNightInput): MenuEventNight =
        throw UnsupportedOperationException("Changing events isn't available here")

    // Deletes an event night; the server switches off the menu it had.
    suspend fun deleteEventNight(restaurantId: String, eventId: String): Unit =
        throw UnsupportedOperationException("Deleting events isn't available here")

    suspend fun getMenu(
        menuId: String,
        includeSections: Boolean = false,
        includeItems: Boolean = false,
        includeVariants: Boolean = false,
        includeOptionGroups: Boolean = false
    ): Menu

    suspend fun updateMenu(
        menuId: String,
        input: UpdateMenuInput
    ): Menu

    suspend fun deleteMenu(
        menuId: String,
        deleteItems: Boolean = false
    )

    // Menu sections

    suspend fun createSection(
        menuId: String,
        input: MenuSectionInput
    ): MenuSection

    suspend fun updateSection(
        menuId: String,
        sectionId: String,
        input: MenuSectionInput
    ): MenuSection

    suspend fun deleteSection(
        menuId: String,
        sectionId: String,
        deleteItems: Boolean = false
    )

    // Menu items

    suspend fun createItem(
        menuId: String,
        sectionId: String,
        input: MenuItemInput
    ): MenuItem

    suspend fun updateItem(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: MenuItemInput
    ): MenuItem

    // Copies of dishes from other menus, each with its own price.
    suspend fun importItems(menuId: String, sectionId: String, itemIds: List<String>): List<MenuItem>

    suspend fun updateItemAvailability(
        menuId: String,
        sectionId: String,
        itemId: String,
        available: Boolean
    ): MenuItem

    // The dish's photo: uploaded to the server's storage (replacing any earlier one), or removed.
    suspend fun uploadItemImage(
        menuId: String,
        sectionId: String,
        itemId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): MenuItem = throw UnsupportedOperationException("Photo uploads aren't available here")

    suspend fun removeItemImage(menuId: String, sectionId: String, itemId: String): MenuItem =
        throw UnsupportedOperationException("Photo uploads aren't available here")

    // The dish's ingredients from stock (its recipe underneath), and the stock items and preparations to pick from.
    suspend fun getDishRecipe(menuId: String, sectionId: String, itemId: String): DishRecipeDto =
        throw UnsupportedOperationException("Dish ingredients aren't available here")

    suspend fun saveDishRecipe(menuId: String, sectionId: String, itemId: String, ingredients: List<DishIngredientRequestDto>): DishRecipeDto =
        throw UnsupportedOperationException("Dish ingredients aren't available here")

    suspend fun ingredientChoices(menuId: String): List<IngredientChoiceDto> =
        throw UnsupportedOperationException("Dish ingredients aren't available here")

    suspend fun deleteItem(
        menuId: String,
        sectionId: String,
        itemId: String
    )

    // Menu variants

    suspend fun createVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: MenuVariantInput
    ): MenuVariant

    suspend fun updateVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String,
        input: MenuVariantInput
    ): MenuVariant

    suspend fun deleteVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String
    )

    // Menu item option-group links

    suspend fun createItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: CreateMenuItemOptionGroupInput
    ): MenuItemOptionGroup

    suspend fun deleteItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        linkId: String
    )

    // Option-group types

    suspend fun getOptionGroupTypes(
        search: String? = null
    ): List<OptionGroupType>

    suspend fun createOptionGroupType(
        input: OptionGroupTypeInput
    ): OptionGroupType

    // Option groups

    suspend fun createOptionGroup(
        input: CreateOptionGroupInput
    ): OptionGroup

    suspend fun getOptionGroup(groupId: String, includeItems: Boolean = false): OptionGroup

    suspend fun deleteOptionGroup(
        groupId: String
    )

    // Option items

    suspend fun createOptionItem(
        groupId: String,
        input: OptionItemInput
    ): OptionItem

    suspend fun updateOptionItem(
        groupId: String,
        itemId: String,
        input: OptionItemInput
    ): OptionItem

    suspend fun deleteOptionItem(
        groupId: String,
        itemId: String
    )
}
