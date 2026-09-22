package com.saporini.mobile_desktop.pos.menu.ui.item

internal data class MenuItem(
    val name: String,
    val description: String,
    val price: String,
    val category: String,
    val available: Boolean,
    val sku: String? = null,
    val imageFileName: String? = null,
    val ingredients: List<DraftIngredient> = emptyList(),
    val variants: List<DraftVariant> = emptyList(),
    val optionGroups: List<DraftOptionGroup> = emptyList(),
    // Backend identity -- null for items that only exist locally (not yet
    // created on the server). basePrice is the raw number the backend
    // wants; `price` above stays the display string the UI already uses.
    val id: String? = null,
    val sectionId: String? = null,
    val basePrice: Double = 0.0,
    val displayOrder: Int = 0
)
