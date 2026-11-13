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
    // The event night of an event menu.
    val event: MenuEventDto? = null,
    val shownToWaiters: Boolean = true,
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

@Serializable
data class MenuEventDto(
    val id: String,
    val name: String,
    val icon: String = "",
    val startDate: String,
    val endDate: String,
    val active: Boolean = true,
    val specialMenuOnly: Boolean = false
)

// GET /restaurants/{id}/events, as the menu editor needs it.
@Serializable
data class MenuEventNightDto(
    val id: String,
    val name: String,
    val icon: String = "",
    val startDate: String,
    val endDate: String,
    val menuId: String? = null,
    val menuName: String? = null,
    val active: Boolean? = null,
    val specialMenuOnly: Boolean? = null
)

// POST/PUT /restaurants/{id}/events from the menu editor. Every field but menuId is always sent; an edit must send
// the event's menuId, or the server takes the menu off the event.
@Serializable
data class MenuEventNightRequestDto(
    val name: String,
    val icon: String,
    val startDate: String,
    val endDate: String,
    val specialMenuOnly: Boolean,
    val active: Boolean,
    val menuId: String? = null
)

// GET/PUT /menus/waiter-view: which menus waiters see.
@Serializable
data class MenuWaiterViewDto(
    val specialMenusOnly: Boolean = false,
    val shownMenuIds: List<String> = emptyList()
)

// Every field is always sent.
@Serializable
data class MenuWaiterViewRequestDto(
    val restaurantId: String,
    val specialMenusOnly: Boolean,
    val shownMenuIds: List<String>
)

// PUT /menus/order: the menus in the order they're shown.
@Serializable
data class MenuOrderRequestDto(
    val restaurantId: String,
    val menuIds: List<String>
)

// ---- A dish's ingredients from stock (its recipe underneath) ----

/** What one of [unit] costs, for an ingredient that can be measured in it. */
@Serializable
data class UnitCostDto(val unit: String, val cost: Double = 0.0)

/** Something a dish can be made with: a STOCK item or a shared PREPARATION (sauce, dough). */
@Serializable
data class IngredientChoiceDto(
    val kind: String,
    val id: String,
    val name: String,
    val group: String = "",
    val baseUnit: String,
    val units: List<UnitCostDto> = emptyList(),
    // Its photo (the stock item's, or the sub-recipe item's), when it has one.
    val imageUrl: String? = null
)

@Serializable
data class DishIngredientDto(
    val kind: String,
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String
)

/** The dish's ingredients from stock and what one portion costs ([recipeId] null: it takes nothing from stock). */
@Serializable
data class DishRecipeDto(
    val recipeId: String? = null,
    val ingredients: List<DishIngredientDto> = emptyList(),
    val cost: Double = 0.0,
    val price: Double = 0.0
)

/** One ingredient to save: exactly one of [inventoryItemId] and [preparationId]. */
@Serializable
data class DishIngredientRequestDto(
    val inventoryItemId: String? = null,
    val preparationId: String? = null,
    val quantity: Double,
    val unit: String
)

@Serializable
data class DishRecipeRequestDto(val ingredients: List<DishIngredientRequestDto>)
