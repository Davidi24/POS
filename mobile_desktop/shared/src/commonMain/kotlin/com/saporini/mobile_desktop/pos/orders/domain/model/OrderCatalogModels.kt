package com.saporini.mobile_desktop.pos.orders.domain.model

data class OrderCatalogPage(
    val items: List<OrderCatalogMenu>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int, val hasNext: Boolean, val hasPrevious: Boolean
)

data class OrderCatalogRestaurant(val id: String)

data class OrderCatalogMenu(
    val id: String, val name: String, val active: Boolean,
    val restaurant: OrderCatalogRestaurant? = null,
    val availableFrom: String? = null, val availableUntil: String? = null,
    val availableFromDate: String? = null, val availableUntilDate: String? = null,
    val sections: List<OrderCatalogSection>? = null
)

data class OrderCatalogSection(
    val id: String, val name: String, val active: Boolean,
    val displayOrder: Int = 0, val items: List<OrderCatalogItem>? = null
)

data class OrderCatalogItem(
    val id: String, val name: String, val basePrice: OrderDecimal, val available: Boolean,
    val description: String? = null, val imageUrl: String? = null, val displayOrder: Int = 0,
    val ingredients: List<String>? = null,
    val variants: List<OrderCatalogVariant>? = null,
    val optionGroups: List<OrderCatalogOptionLink>? = null,
    val sku: String? = null
)

data class OrderCatalogVariant(
    val id: String, val name: String, val priceDelta: OrderDecimal, val active: Boolean,
    val isDefault: Boolean = false, val displayOrder: Int = 0
)

data class OrderCatalogOptionLink(
    val linkId: String, val optionGroupId: String, val name: String, val active: Boolean,
    val displayOrder: Int = 0, val minSelect: Int? = null, val maxSelect: Int? = null,
    val required: Boolean = false, val minSelectOverride: Int? = null,
    val maxSelectOverride: Int? = null, val requiredOverride: Boolean? = null
)

data class OrderChoiceGroup(
    val id: String, val restaurantId: String, val name: String, val active: Boolean,
    val minSelect: Int? = null, val maxSelect: Int? = null, val required: Boolean = false,
    val items: List<OrderChoice>? = null
)

data class OrderChoice(
    val id: String, val optionGroupId: String, val name: String,
    val priceDelta: OrderDecimal, val available: Boolean, val displayOrder: Int = 0
)

data class OrderItemChoices(
    val item: OrderCatalogItem,
    val groups: List<OrderConfiguredChoiceGroup>
)

data class OrderConfiguredChoiceGroup(
    val link: OrderCatalogOptionLink,
    val group: OrderChoiceGroup
) {
    val minimum: Int get() = maxOf(link.minSelectOverride ?: group.minSelect ?: 0,
        if (link.requiredOverride ?: group.required) 1 else 0)
    val maximum: Int? get() = link.maxSelectOverride ?: group.maxSelect
    val availableChoices: List<OrderChoice> get() = group.items.orEmpty().filter { it.available }.sortedBy { it.displayOrder }
}
