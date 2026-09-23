package com.saporini.mobile_desktop.pos.orders.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.*

internal fun OrderCatalogPageDto.toDomain(): OrderCatalogPage = OrderCatalogPage(
    items = items.map { it.toDomain() },
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
    hasNext = hasNext,
    hasPrevious = hasPrevious
)

internal fun OrderCatalogRestaurantDto.toDomain(): OrderCatalogRestaurant = OrderCatalogRestaurant(
    id = id
)

internal fun OrderCatalogMenuDto.toDomain(): OrderCatalogMenu = OrderCatalogMenu(
    id = id,
    name = name,
    active = active,
    restaurant = restaurant?.toDomain(),
    availableFrom = availableFrom,
    availableUntil = availableUntil,
    availableFromDate = availableFromDate,
    availableUntilDate = availableUntilDate,
    sections = sections?.map { it.toDomain() }
)

internal fun OrderCatalogSectionDto.toDomain(): OrderCatalogSection = OrderCatalogSection(
    id = id,
    name = name,
    active = active,
    displayOrder = displayOrder,
    items = items?.map { it.toDomain() }
)

internal fun OrderCatalogItemDto.toDomain(): OrderCatalogItem = OrderCatalogItem(
    id = id,
    name = name,
    basePrice = basePrice,
    available = available,
    description = description,
    imageUrl = imageUrl,
    displayOrder = displayOrder,
    ingredients = ingredients,
    variants = variants?.map { it.toDomain() },
    optionGroups = optionGroups?.map { it.toDomain() },
    sku = sku
)

internal fun OrderCatalogVariantDto.toDomain(): OrderCatalogVariant = OrderCatalogVariant(
    id = id,
    name = name,
    priceDelta = priceDelta,
    active = active,
    isDefault = isDefault,
    displayOrder = displayOrder
)

internal fun OrderCatalogOptionLinkDto.toDomain(): OrderCatalogOptionLink = OrderCatalogOptionLink(
    linkId = linkId,
    optionGroupId = optionGroupId,
    name = name,
    active = active,
    displayOrder = displayOrder,
    minSelect = minSelect,
    maxSelect = maxSelect,
    required = required,
    minSelectOverride = minSelectOverride,
    maxSelectOverride = maxSelectOverride,
    requiredOverride = requiredOverride
)

internal fun OrderChoiceGroupDto.toDomain(): OrderChoiceGroup = OrderChoiceGroup(
    id = id,
    restaurantId = restaurantId,
    name = name,
    active = active,
    minSelect = minSelect,
    maxSelect = maxSelect,
    required = required,
    items = items?.map { it.toDomain() }
)

internal fun OrderChoiceDto.toDomain(): OrderChoice = OrderChoice(
    id = id,
    optionGroupId = optionGroupId,
    name = name,
    priceDelta = priceDelta,
    available = available,
    displayOrder = displayOrder
)
