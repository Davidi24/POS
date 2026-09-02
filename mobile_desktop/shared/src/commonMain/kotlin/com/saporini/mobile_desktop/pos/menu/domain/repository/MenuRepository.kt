package com.saporini.mobile_desktop.pos.menu.domain.repository

import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItemOptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuPage
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuVariant
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroupType
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionItem

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
    val color: String? = null
)

data class UpdateMenuInput(
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val active: Boolean,
    val displayOrder: Int,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val availableFromDate: String? = null,
    val availableUntilDate: String? = null,
    val color: String? = null
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
    val displayOrder: Int = 0
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

data class UpdateMenuItemOptionGroupInput(
    val displayOrder: Int,
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

data class UpdateOptionGroupInput(
    val typeId: String,
    val name: String,
    val description: String? = null,
    val minSelect: Int = 0,
    val maxSelect: Int = 0,
    val required: Boolean,
    val active: Boolean,
    val displayOrder: Int
)

data class OptionItemInput(
    val code: String? = null,
    val name: String,
    val priceDelta: Double = 0.0,
    val available: Boolean = true,
    val displayOrder: Int = 0
)

interface MenuRepository {

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

    suspend fun updateMenuStatus(
        menuId: String,
        active: Boolean
    ): Menu

    suspend fun deleteMenu(
        menuId: String
    )

    // Menu sections

    suspend fun getSections(
        menuId: String,
        active: Boolean? = null,
        includeItems: Boolean = false
    ): List<MenuSection>

    suspend fun getSection(
        menuId: String,
        sectionId: String,
        includeItems: Boolean = false
    ): MenuSection

    suspend fun createSection(
        menuId: String,
        input: MenuSectionInput
    ): MenuSection

    suspend fun updateSection(
        menuId: String,
        sectionId: String,
        input: MenuSectionInput
    ): MenuSection

    suspend fun updateSectionStatus(
        menuId: String,
        sectionId: String,
        active: Boolean
    ): MenuSection

    suspend fun deleteSection(
        menuId: String,
        sectionId: String
    )

    // Menu items

    suspend fun getItems(
        menuId: String,
        sectionId: String,
        available: Boolean? = null,
        includeVariants: Boolean = false,
        includeOptionGroups: Boolean = false
    ): List<MenuItem>

    suspend fun getItem(
        menuId: String,
        sectionId: String,
        itemId: String,
        includeVariants: Boolean = false,
        includeOptionGroups: Boolean = false
    ): MenuItem

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

    suspend fun updateItemAvailability(
        menuId: String,
        sectionId: String,
        itemId: String,
        available: Boolean
    ): MenuItem

    suspend fun deleteItem(
        menuId: String,
        sectionId: String,
        itemId: String
    )

    // Menu variants

    suspend fun getVariants(
        menuId: String,
        sectionId: String,
        itemId: String
    ): List<MenuVariant>

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

    suspend fun getItemOptionGroups(
        menuId: String,
        sectionId: String,
        itemId: String
    ): List<MenuItemOptionGroup>

    suspend fun createItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: CreateMenuItemOptionGroupInput
    ): MenuItemOptionGroup

    suspend fun updateItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        linkId: String,
        input: UpdateMenuItemOptionGroupInput
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

    suspend fun updateOptionGroupType(
        typeId: String,
        input: OptionGroupTypeInput
    ): OptionGroupType

    suspend fun deleteOptionGroupType(
        typeId: String
    )

    // Option groups

    suspend fun getOptionGroups(
        restaurantId: String,
        typeId: String? = null,
        active: Boolean? = null,
        search: String? = null,
        includeItems: Boolean = false
    ): List<OptionGroup>

    suspend fun getOptionGroup(
        groupId: String,
        includeItems: Boolean = false
    ): OptionGroup

    suspend fun createOptionGroup(
        input: CreateOptionGroupInput
    ): OptionGroup

    suspend fun updateOptionGroup(
        groupId: String,
        input: UpdateOptionGroupInput
    ): OptionGroup

    suspend fun updateOptionGroupStatus(
        groupId: String,
        active: Boolean
    ): OptionGroup

    suspend fun deleteOptionGroup(
        groupId: String
    )

    // Option items

    suspend fun getOptionItems(
        groupId: String,
        available: Boolean? = null
    ): List<OptionItem>

    suspend fun createOptionItem(
        groupId: String,
        input: OptionItemInput
    ): OptionItem

    suspend fun updateOptionItem(
        groupId: String,
        itemId: String,
        input: OptionItemInput
    ): OptionItem

    suspend fun updateOptionItemAvailability(
        groupId: String,
        itemId: String,
        available: Boolean
    ): OptionItem

    suspend fun deleteOptionItem(
        groupId: String,
        itemId: String
    )
}
