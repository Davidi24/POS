package com.saporini.mobile_desktop.pos.orders.domain.model

/** Validate the current catalog selection before producing a server-priced line item. */
fun OrderItemChoices.toLineItemInput(
    quantity: Int,
    variantId: String? = null,
    options: List<CreateOrderItemOptionInput> = emptyList(),
    notes: String? = null
): CreateOrderLineItemInput {
    require(item.available) { "This item is unavailable" }
    require(quantity > 0) { "Quantity must be greater than zero" }
    require(variantId == null || item.variants.orEmpty().any { it.id == variantId && it.active }) { "Choose an available variant" }
    require(options.map { it.optionItemId }.distinct().size == options.size) { "Duplicate options" }
    val availableIds = groups.flatMap { it.availableChoices }.map { it.id }.toSet()
    require(options.all { it.optionItemId in availableIds && (it.quantity ?: 1) > 0 }) { "An option is no longer available" }
    groups.forEach { configured ->
        val ids = configured.availableChoices.map { it.id }.toSet()
        val count = options.count { it.optionItemId in ids }
        require(count >= configured.minimum) { "Choose at least ${configured.minimum} option(s) from ${configured.group.name}" }
        require(configured.maximum == null || count <= configured.maximum!!) { "Too many options for ${configured.group.name}" }
    }
    return CreateOrderLineItemInput(menuItemId = item.id, variantId = variantId, quantity = quantity,
        notes = notes?.trim()?.takeIf { it.isNotEmpty() }, options = options)
}
