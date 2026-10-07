package com.saporini.mobile_desktop.pos.menu.data.dto

import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItem
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuItemOptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuPage
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuRestaurant
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuVariant
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenu
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuDish
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuSection
import com.saporini.mobile_desktop.pos.menu.domain.model.OnlineMenuSectionView
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroup
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroupType
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionItem

fun MenuPageResponseDto.toDomain(): MenuPage {
    return MenuPage(
        items = items.map { it.toDomain() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext,
        hasPrevious = hasPrevious
    )
}

fun MenuResponseDto.toDomain(): Menu {
    return Menu(
        id = id,
        restaurant = restaurant?.toDomain(),
        code = code,
        name = name,
        description = description,
        active = active,
        displayOrder = displayOrder,
        availableFrom = availableFrom,
        availableUntil = availableUntil,
        availableFromDate = availableFromDate,
        availableUntilDate = availableUntilDate,
        color = color,
        itemCount = itemCount,
        createdBy = createdBy,
        updatedBy = updatedBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        allFilterPosition = allFilterPosition,
        sections = sections.map { it.toDomain() },
        special = special
    )
}

fun MenuRestaurantDto.toDomain(): MenuRestaurant {
    return MenuRestaurant(
        id = id,
        code = code,
        name = name
    )
}

fun MenuSectionDto.toDomain(): MenuSection {
    return MenuSection(
        id = id,
        name = name,
        description = description,
        active = active,
        displayOrder = displayOrder,
        items = items.map { it.toDomain() }
    )
}

fun MenuItemDto.toDomain(): MenuItem {
    return MenuItem(
        id = id,
        sku = sku,
        name = name,
        description = description,
        basePrice = basePrice,
        imageUrl = imageUrl,
        available = available,
        sendToKitchen = sendToKitchen,
        showOnline = showOnline,
        onlineSectionId = onlineSectionId,
        onlineSectionName = onlineSectionName,
        displayOrder = displayOrder,
        ingredients = ingredients,
        variants = variants.map { it.toDomain() },
        optionGroups = optionGroups.map { it.toDomain() },
        orderBeforeHours = orderBeforeHours,
        occasionCodes = occasionCodes
    )
}

fun MenuVariantDto.toDomain(): MenuVariant {
    return MenuVariant(
        id = id,
        name = name,
        sku = sku,
        priceDelta = priceDelta,
        isDefault = isDefault,
        active = active,
        displayOrder = displayOrder
    )
}

fun MenuItemOptionGroupDto.toDomain(): MenuItemOptionGroup {
    return MenuItemOptionGroup(
        linkId = linkId,
        optionGroupId = optionGroupId,
        name = name,
        description = description,
        active = active,
        displayOrder = displayOrder,
        minSelect = minSelect,
        maxSelect = maxSelect,
        required = required,
        minSelectOverride = minSelectOverride,
        maxSelectOverride = maxSelectOverride,
        requiredOverride = requiredOverride
    )
}

fun OptionGroupTypeDto.toDomain(): OptionGroupType {
    return OptionGroupType(
        id = id,
        code = code,
        name = name,
        description = description
    )
}

fun OptionGroupDto.toDomain(): OptionGroup {
    return OptionGroup(
        id = id,
        restaurantId = restaurantId,
        type = type?.toDomain(),
        name = name,
        description = description,
        minSelect = minSelect,
        maxSelect = maxSelect,
        required = required,
        active = active,
        displayOrder = displayOrder,
        items = items.map { it.toDomain() }
    )
}

fun OptionItemDto.toDomain(): OptionItem {
    return OptionItem(
        id = id,
        optionGroupId = optionGroupId,
        code = code,
        name = name,
        priceDelta = priceDelta,
        available = available,
        displayOrder = displayOrder,
        inventoryRecipeId = inventoryRecipeId,
        inventoryRecipeQuantity = inventoryRecipeQuantity
    )
}

fun OnlineMenuSectionDto.toDomain(): OnlineMenuSection =
    OnlineMenuSection(id = id, name = name, displayOrder = displayOrder, itemCount = itemCount)

fun OnlineMenuDto.toDomain(): OnlineMenu = OnlineMenu(
    sections = sections.map { section ->
        OnlineMenuSectionView(
            id = section.id,
            name = section.name,
            items = section.items.map { item ->
                OnlineMenuDish(
                    id = item.id,
                    sku = item.sku,
                    name = item.name,
                    description = item.description,
                    basePrice = item.basePrice,
                    imageUrl = item.imageUrl,
                    ingredients = item.ingredients,
                    available = item.available,
                    sendToKitchen = item.sendToKitchen,
                    displayOrder = item.displayOrder,
                    menuName = item.menuName,
                    menuSectionName = item.menuSectionName,
                    visible = item.visible,
                    hiddenReason = item.hiddenReason
                )
            }
        )
    }
)
