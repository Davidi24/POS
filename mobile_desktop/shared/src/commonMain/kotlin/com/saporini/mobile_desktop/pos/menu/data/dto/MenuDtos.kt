package com.saporini.mobile_desktop.pos.menu.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MenuPageResponseDto(
    val items: List<MenuResponseDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

@Serializable
data class MenuResponseDto(
    val allFilterPosition: Int? = null,
    val id: String,
    val restaurant: MenuRestaurantDto? = null,
    val code: String,
    val name: String,
    val description: String? = null,
    val active: Boolean,
    val displayOrder: Int = 0,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val availableFromDate: String? = null,
    val availableUntilDate: String? = null,
    val color: String? = null,
    val special: Boolean = false,
    val itemCount: Int? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val sections: List<MenuSectionDto> = emptyList()
)

@Serializable
data class MenuRestaurantDto(
    val id: String,
    val code: String,
    val name: String
)

@Serializable
data class MenuSectionDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val active: Boolean,
    val displayOrder: Int = 0,
    val items: List<MenuItemDto> = emptyList()
)

@Serializable
data class MenuItemDto(
    val id: String,
    val sku: String? = null,
    val name: String,
    val description: String? = null,
    val basePrice: Double,
    val imageUrl: String? = null,
    val available: Boolean,
    val sendToKitchen: Boolean = true,
    val showOnline: Boolean = false,
    val onlineSectionId: String? = null,
    val onlineSectionName: String? = null,
    val displayOrder: Int = 0,
    val ingredients: List<String> = emptyList(),
    val orderBeforeHours: Int? = null,
    val occasionCodes: List<String> = emptyList(),
    val variants: List<MenuVariantDto> = emptyList(),
    val optionGroups: List<MenuItemOptionGroupDto> = emptyList()
)

@Serializable
data class MenuVariantDto(
    val id: String,
    val name: String,
    val sku: String? = null,
    val priceDelta: Double,
    @SerialName("default")
    val isDefault: Boolean,
    val active: Boolean,
    val displayOrder: Int = 0
)

@Serializable
data class MenuItemOptionGroupDto(
    val linkId: String,
    val optionGroupId: String,
    val name: String,
    val description: String? = null,
    val active: Boolean,
    val displayOrder: Int = 0,
    val minSelect: Int = 0,
    val maxSelect: Int = 0,
    val required: Boolean = false,
    val minSelectOverride: Int? = null,
    val maxSelectOverride: Int? = null,
    val requiredOverride: Boolean? = null
)

@Serializable
data class OptionGroupTypeDto(
    val id: String,
    val code: String,
    val name: String,
    val description: String? = null
)

@Serializable
data class OptionGroupDto(
    val id: String,
    val restaurantId: String,
    val type: OptionGroupTypeDto? = null,
    val name: String,
    val description: String? = null,
    val minSelect: Int = 0,
    val maxSelect: Int = 0,
    val required: Boolean = false,
    val active: Boolean,
    val displayOrder: Int = 0,
    val items: List<OptionItemDto> = emptyList()
)

@Serializable
data class OptionItemDto(
    val id: String,
    val optionGroupId: String,
    val code: String? = null,
    val name: String,
    val priceDelta: Double,
    val available: Boolean,
    val displayOrder: Int = 0,
    val inventoryRecipeId: String? = null,
    val inventoryRecipeQuantity: Double? = null
)

// Online menu: its own sections, filled with dishes that point at them.
@Serializable
data class OnlineMenuSectionDto(
    val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val itemCount: Long = 0
)

@Serializable
data class OnlineMenuDto(
    val date: String? = null,
    val sections: List<OnlineMenuSectionViewDto> = emptyList()
)

@Serializable
data class OnlineMenuSectionViewDto(
    val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val items: List<OnlineMenuItemDto> = emptyList()
)

@Serializable
data class OnlineMenuItemDto(
    val id: String,
    val sku: String? = null,
    val name: String,
    val description: String? = null,
    val basePrice: Double = 0.0,
    val imageUrl: String? = null,
    val ingredients: List<String> = emptyList(),
    val available: Boolean = true,
    val sendToKitchen: Boolean = true,
    val displayOrder: Int = 0,
    val menuName: String? = null,
    val menuSectionName: String? = null,
    val visible: Boolean = true,
    val hiddenReason: String? = null
)

@Serializable
data class ReorderOnlineMenuItemsRequestDto(
    val itemIds: List<String>
)
