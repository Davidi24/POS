package com.saporini.mobile_desktop.pos.menu.data.repository

import com.saporini.mobile_desktop.pos.menu.data.api.MenuApi
import com.saporini.mobile_desktop.pos.menu.data.dto.*
import com.saporini.mobile_desktop.pos.menu.domain.model.*
import com.saporini.mobile_desktop.pos.menu.domain.repository.*

class DefaultMenuRepository(
    private val api: MenuApi
) : MenuRepository {

    // Menus

    override suspend fun getMenus(
        restaurantId: String?,
        active: Boolean?,
        search: String?,
        page: Int,
        size: Int,
        sortBy: String,
        direction: String
    ): MenuPage {
        return api.getMenus(
            restaurantId = restaurantId,
            active = active,
            search = search,
            page = page,
            size = size,
            sortBy = sortBy,
            direction = direction
        ).toDomain()
    }

    override suspend fun createMenu(
        input: CreateMenuInput
    ): Menu {
        return api.createMenu(
            request = CreateMenuRequestDto(
                restaurantId = input.restaurantId,
                code = input.code,
                name = input.name,
                description = input.description,
                active = input.active,
                displayOrder = input.displayOrder,
                availableFrom = input.availableFrom,
                availableUntil = input.availableUntil,
                availableFromDate = input.availableFromDate,
                availableUntilDate = input.availableUntilDate,
                color = input.color
            )
        ).toDomain()
    }

    override suspend fun getMenu(
        menuId: String,
        includeSections: Boolean,
        includeItems: Boolean,
        includeVariants: Boolean,
        includeOptionGroups: Boolean
    ): Menu {
        return api.getMenu(
            menuId = menuId,
            includeSections = includeSections,
            includeItems = includeItems,
            includeVariants = includeVariants,
            includeOptionGroups = includeOptionGroups
        ).toDomain()
    }

    override suspend fun updateMenu(
        menuId: String,
        input: UpdateMenuInput
    ): Menu {
        return api.updateMenu(
            menuId = menuId,
            request = UpdateMenuRequestDto(
                code = input.code,
                name = input.name,
                description = input.description,
                active = input.active,
                displayOrder = input.displayOrder,
                availableFrom = input.availableFrom,
                availableUntil = input.availableUntil,
                availableFromDate = input.availableFromDate,
                availableUntilDate = input.availableUntilDate,
                color = input.color
            )
        ).toDomain()
    }

    override suspend fun deleteMenu(menuId: String) {
        api.deleteMenu(menuId)
    }

    // Menu sections

    override suspend fun createSection(
        menuId: String,
        input: MenuSectionInput
    ): MenuSection {
        return api.createSection(
            menuId = menuId,
            request = CreateMenuSectionRequestDto(
                name = input.name,
                description = input.description,
                active = input.active,
                displayOrder = input.displayOrder
            )
        ).toDomain()
    }

    override suspend fun updateSection(
        menuId: String,
        sectionId: String,
        input: MenuSectionInput
    ): MenuSection {
        return api.updateSection(
            menuId = menuId,
            sectionId = sectionId,
            request = UpdateMenuSectionRequestDto(
                name = input.name,
                description = input.description,
                active = input.active,
                displayOrder = input.displayOrder
            )
        ).toDomain()
    }

    override suspend fun deleteSection(
        menuId: String,
        sectionId: String
    ) {
        api.deleteSection(menuId = menuId, sectionId = sectionId)
    }

    // Menu items

    override suspend fun createItem(
        menuId: String,
        sectionId: String,
        input: MenuItemInput
    ): MenuItem {
        return api.createItem(
            menuId = menuId,
            sectionId = sectionId,
            request = CreateMenuItemRequestDto(
                sku = input.sku,
                name = input.name,
                description = input.description,
                basePrice = input.basePrice,
                imageUrl = input.imageUrl,
                available = input.available,
                displayOrder = input.displayOrder,
                ingredients = input.ingredients
            )
        ).toDomain()
    }

    override suspend fun updateItem(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: MenuItemInput
    ): MenuItem {
        return api.updateItem(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            request = UpdateMenuItemRequestDto(
                sku = input.sku,
                name = input.name,
                description = input.description,
                basePrice = input.basePrice,
                imageUrl = input.imageUrl,
                available = input.available,
                displayOrder = input.displayOrder,
                ingredients = input.ingredients
            )
        ).toDomain()
    }

    override suspend fun updateItemAvailability(
        menuId: String,
        sectionId: String,
        itemId: String,
        available: Boolean
    ): MenuItem {
        return api.updateItemAvailability(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            request = UpdateMenuItemAvailabilityRequestDto(
                available = available
            )
        ).toDomain()
    }

    override suspend fun deleteItem(
        menuId: String,
        sectionId: String,
        itemId: String
    ) {
        api.deleteItem(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId
        )
    }

    // Menu variants

    override suspend fun createVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: MenuVariantInput
    ): MenuVariant {
        return api.createVariant(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            request = CreateMenuVariantRequestDto(
                name = input.name,
                sku = input.sku,
                priceDelta = input.priceDelta,
                isDefault = input.isDefault,
                active = input.active,
                displayOrder = input.displayOrder
            )
        ).toDomain()
    }

    override suspend fun updateVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String,
        input: MenuVariantInput
    ): MenuVariant {
        return api.updateVariant(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            variantId = variantId,
            request = UpdateMenuVariantRequestDto(
                name = input.name,
                sku = input.sku,
                priceDelta = input.priceDelta,
                isDefault = input.isDefault,
                active = input.active,
                displayOrder = input.displayOrder
            )
        ).toDomain()
    }

    override suspend fun deleteVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String
    ) {
        api.deleteVariant(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            variantId = variantId
        )
    }

    // Menu item option-group links

    override suspend fun createItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        input: CreateMenuItemOptionGroupInput
    ): MenuItemOptionGroup {
        return api.createItemOptionGroup(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            request = CreateMenuItemOptionGroupRequestDto(
                optionGroupId = input.optionGroupId,
                displayOrder = input.displayOrder,
                minSelectOverride = input.minSelectOverride,
                maxSelectOverride = input.maxSelectOverride,
                requiredOverride = input.requiredOverride
            )
        ).toDomain()
    }

    override suspend fun deleteItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        linkId: String
    ) {
        api.deleteItemOptionGroup(
            menuId = menuId,
            sectionId = sectionId,
            itemId = itemId,
            linkId = linkId
        )
    }

    // Option-group types

    override suspend fun getOptionGroupTypes(
        search: String?
    ): List<OptionGroupType> {
        return api.getOptionGroupTypes(search = search)
            .map { it.toDomain() }
    }

    override suspend fun createOptionGroupType(
        input: OptionGroupTypeInput
    ): OptionGroupType {
        return api.createOptionGroupType(
            request = CreateOptionGroupTypeRequestDto(
                code = input.code,
                name = input.name,
                description = input.description
            )
        ).toDomain()
    }

    // Option groups

    override suspend fun createOptionGroup(
        input: CreateOptionGroupInput
    ): OptionGroup {
        return api.createOptionGroup(
            request = CreateOptionGroupRequestDto(
                restaurantId = input.restaurantId,
                typeId = input.typeId,
                name = input.name,
                description = input.description,
                minSelect = input.minSelect,
                maxSelect = input.maxSelect,
                required = input.required,
                active = input.active,
                displayOrder = input.displayOrder
            )
        ).toDomain()
    }

    // Option items

    override suspend fun createOptionItem(
        groupId: String,
        input: OptionItemInput
    ): OptionItem {
        return api.createOptionItem(
            groupId = groupId,
            request = CreateOptionItemRequestDto(
                code = input.code,
                name = input.name,
                priceDelta = input.priceDelta,
                available = input.available,
                displayOrder = input.displayOrder
            )
        ).toDomain()
    }
}
