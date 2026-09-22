package com.saporini.mobile_desktop.pos.menu.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateMenuRequestDto(
    val restaurantId: String,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val active: Boolean? = null,
    val displayOrder: Int? = null,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val availableFromDate: String? = null,
    val availableUntilDate: String? = null,
    val color: String? = null
)

@Serializable
data class UpdateMenuRequestDto(
    val allFilterPosition: Int? = null,
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

@Serializable
data class UpdateMenuStatusRequestDto(
    val active: Boolean
)

@Serializable
data class CreateMenuSectionRequestDto(
    val name: String,
    val description: String? = null,
    val active: Boolean? = null,
    val displayOrder: Int? = null
)

@Serializable
data class UpdateMenuSectionRequestDto(
    val name: String,
    val description: String? = null,
    val active: Boolean,
    val displayOrder: Int
)

@Serializable
data class UpdateMenuSectionStatusRequestDto(
    val active: Boolean
)

@Serializable
data class CreateMenuItemRequestDto(
    val sku: String? = null,
    val name: String,
    val description: String? = null,
    val basePrice: Double,
    val imageUrl: String? = null,
    val available: Boolean? = null,
    val displayOrder: Int? = null,
    val ingredients: List<String> = emptyList()
)

@Serializable
data class UpdateMenuItemRequestDto(
    val sku: String? = null,
    val name: String,
    val description: String? = null,
    val basePrice: Double,
    val imageUrl: String? = null,
    val available: Boolean,
    val displayOrder: Int,
    val ingredients: List<String> = emptyList(),
    val sectionId: String? = null
)

@Serializable
data class UpdateMenuItemAvailabilityRequestDto(
    val available: Boolean
)

@Serializable
data class CreateMenuVariantRequestDto(
    val name: String,
    val sku: String? = null,
    val priceDelta: Double? = null,
    @SerialName("default")
    val isDefault: Boolean? = null,
    val active: Boolean? = null,
    val displayOrder: Int? = null
)

@Serializable
data class UpdateMenuVariantRequestDto(
    val name: String,
    val sku: String? = null,
    val priceDelta: Double? = null,
    @SerialName("default")
    val isDefault: Boolean,
    val active: Boolean,
    val displayOrder: Int
)

@Serializable
data class CreateMenuItemOptionGroupRequestDto(
    val optionGroupId: String,
    val displayOrder: Int? = null,
    val minSelectOverride: Int? = null,
    val maxSelectOverride: Int? = null,
    val requiredOverride: Boolean? = null
)

@Serializable
data class UpdateMenuItemOptionGroupRequestDto(
    val displayOrder: Int,
    val minSelectOverride: Int? = null,
    val maxSelectOverride: Int? = null,
    val requiredOverride: Boolean? = null
)

@Serializable
data class CreateOptionGroupTypeRequestDto(
    val code: String? = null,
    val name: String,
    val description: String? = null
)

@Serializable
data class UpdateOptionGroupTypeRequestDto(
    val code: String? = null,
    val name: String,
    val description: String? = null
)

@Serializable
data class CreateOptionGroupRequestDto(
    val restaurantId: String,
    val typeId: String,
    val name: String,
    val description: String? = null,
    val minSelect: Int? = null,
    val maxSelect: Int? = null,
    val required: Boolean? = null,
    val active: Boolean? = null,
    val displayOrder: Int? = null
)

@Serializable
data class UpdateOptionGroupRequestDto(
    val typeId: String,
    val name: String,
    val description: String? = null,
    val minSelect: Int? = null,
    val maxSelect: Int? = null,
    val required: Boolean,
    val active: Boolean,
    val displayOrder: Int
)

@Serializable
data class UpdateOptionGroupStatusRequestDto(
    val active: Boolean
)

@Serializable
data class CreateOptionItemRequestDto(
    val code: String? = null,
    val name: String,
    val priceDelta: Double? = null,
    val available: Boolean? = null,
    val displayOrder: Int? = null
)

@Serializable
data class UpdateOptionItemRequestDto(
    val code: String? = null,
    val name: String,
    val priceDelta: Double? = null,
    val available: Boolean,
    val displayOrder: Int
)

@Serializable
data class UpdateOptionItemAvailabilityRequestDto(
    val available: Boolean
)
