package com.saporini.mobile_desktop.pos.menu.domain.model

data class MenuPage(
    val items: List<Menu>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

data class Menu(
    val id: String,
    val restaurant: MenuRestaurant?,
    val code: String,
    val name: String,
    val description: String?,
    val active: Boolean,
    val displayOrder: Int,
    val availableFrom: String?,
    val availableUntil: String?,
    val availableFromDate: String?,
    val availableUntilDate: String?,
    val color: String?,
    val itemCount: Int?,
    val createdBy: String?,
    val updatedBy: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val sections: List<MenuSection>,
    val allFilterPosition: Int? = null,
    // A special menu: occasion extras or an event night's menu.
    val special: Boolean = false
)

data class MenuRestaurant(
    val id: String,
    val code: String,
    val name: String
)

data class MenuSection(
    val id: String,
    val name: String,
    val description: String?,
    val active: Boolean,
    val displayOrder: Int,
    val items: List<MenuItem>
)

data class MenuItem(
    val id: String,
    val sku: String?,
    val name: String,
    val description: String?,
    val basePrice: Double,
    val imageUrl: String?,
    val available: Boolean,
    val displayOrder: Int,
    val ingredients: List<String>,
    val variants: List<MenuVariant>,
    val optionGroups: List<MenuItemOptionGroup>,
    // False for counter items like a cola: served directly, never sent to the kitchen.
    val sendToKitchen: Boolean = true,
    // True when customers see it in the online menu; otherwise it's staff menu only.
    val showOnline: Boolean = false,
    // The online menu section it sits in, when it's online.
    val onlineSectionId: String? = null,
    val onlineSectionName: String? = null,
    // Extras in a special menu: order at least this many hours ahead, and the occasions they're offered for.
    val orderBeforeHours: Int? = null,
    val occasionCodes: List<String> = emptyList()
)

data class MenuVariant(
    val id: String,
    val name: String,
    val sku: String?,
    val priceDelta: Double,
    val isDefault: Boolean,
    val active: Boolean,
    val displayOrder: Int
)

data class MenuItemOptionGroup(
    val linkId: String,
    val optionGroupId: String,
    val name: String,
    val description: String?,
    val active: Boolean,
    val displayOrder: Int,
    val minSelect: Int,
    val maxSelect: Int,
    val required: Boolean,
    val minSelectOverride: Int?,
    val maxSelectOverride: Int?,
    val requiredOverride: Boolean?
) {
    val effectiveMinSelect: Int
        get() = minSelectOverride ?: minSelect

    val effectiveMaxSelect: Int
        get() = maxSelectOverride ?: maxSelect

    val isEffectivelyRequired: Boolean
        get() = requiredOverride ?: required
}

data class OptionGroupType(
    val id: String,
    val code: String,
    val name: String,
    val description: String?
)

data class OptionGroup(
    val id: String,
    val restaurantId: String,
    val type: OptionGroupType?,
    val name: String,
    val description: String?,
    val minSelect: Int,
    val maxSelect: Int,
    val required: Boolean,
    val active: Boolean,
    val displayOrder: Int,
    val items: List<OptionItem>
)

data class OptionItem(
    val id: String,
    val optionGroupId: String,
    val code: String?,
    val name: String,
    val priceDelta: Double,
    val available: Boolean,
    val displayOrder: Int,
    val inventoryRecipeId: String? = null,
    val inventoryRecipeQuantity: Double? = null
)

// The online menu (website): its own sections, filled with dishes that point at them.
data class OnlineMenuSection(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val itemCount: Long
)

data class OnlineMenu(
    val sections: List<OnlineMenuSectionView>
)

data class OnlineMenuSectionView(
    val id: String,
    val name: String,
    val items: List<OnlineMenuDish>
)

data class OnlineMenuDish(
    val id: String,
    val sku: String?,
    val name: String,
    val description: String?,
    val basePrice: Double,
    val imageUrl: String?,
    val ingredients: List<String>,
    val available: Boolean,
    val sendToKitchen: Boolean,
    val displayOrder: Int,
    // Where it lives in the staff menus, e.g. "Dinner · Pasta".
    val menuName: String?,
    val menuSectionName: String?,
    // False when customers can't see it right now (e.g. sold out).
    val visible: Boolean,
    val hiddenReason: String?
)
