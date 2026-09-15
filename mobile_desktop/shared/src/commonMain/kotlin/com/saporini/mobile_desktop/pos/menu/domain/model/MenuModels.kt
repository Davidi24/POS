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
    val sections: List<MenuSection>
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
    val optionGroups: List<MenuItemOptionGroup>
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
    val displayOrder: Int
)
