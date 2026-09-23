package com.saporini.mobile_desktop.pos.orders.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderCatalogPageDto(
    val items: List<OrderCatalogMenuDto>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int, val hasNext: Boolean, val hasPrevious: Boolean
)
@Serializable
data class OrderCatalogRestaurantDto(val id: String)
@Serializable
data class OrderCatalogMenuDto(
    val id: String, val name: String, val active: Boolean,
    val restaurant: OrderCatalogRestaurantDto? = null,
    val availableFrom: String? = null, val availableUntil: String? = null,
    val availableFromDate: String? = null, val availableUntilDate: String? = null,
    val sections: List<OrderCatalogSectionDto>? = null
)
@Serializable
data class OrderCatalogSectionDto(
    val id: String, val name: String, val active: Boolean,
    val displayOrder: Int = 0, val items: List<OrderCatalogItemDto>? = null
)
@Serializable
data class OrderCatalogItemDto(
    val id: String, val name: String, val basePrice: OrderDecimal, val available: Boolean,
    val description: String? = null, val imageUrl: String? = null, val displayOrder: Int = 0,
    val ingredients: List<String>? = null,
    val variants: List<OrderCatalogVariantDto>? = null,
    val optionGroups: List<OrderCatalogOptionLinkDto>? = null,
    val sku: String? = null
)
@Serializable
data class OrderCatalogVariantDto(
    val id: String, val name: String, val priceDelta: OrderDecimal, val active: Boolean,
    @SerialName("default") val isDefault: Boolean = false, val displayOrder: Int = 0
)
@Serializable
data class OrderCatalogOptionLinkDto(
    val linkId: String, val optionGroupId: String, val name: String, val active: Boolean,
    val displayOrder: Int = 0, val minSelect: Int? = null, val maxSelect: Int? = null,
    val required: Boolean = false, val minSelectOverride: Int? = null,
    val maxSelectOverride: Int? = null, val requiredOverride: Boolean? = null
)
@Serializable
data class OrderChoiceGroupDto(
    val id: String, val restaurantId: String, val name: String, val active: Boolean,
    val minSelect: Int? = null, val maxSelect: Int? = null, val required: Boolean = false,
    val items: List<OrderChoiceDto>? = null
)
@Serializable
data class OrderChoiceDto(
    val id: String, val optionGroupId: String, val name: String,
    val priceDelta: OrderDecimal, val available: Boolean, val displayOrder: Int = 0
)
