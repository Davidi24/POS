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
    // A special menu: booking extras or an event night's menu.
    val special: Boolean = false,
    // Set for an event menu: the event night it belongs to (its days are the menu's days).
    val event: MenuEvent? = null,
    // Shown to waiters in Menu and at the till (Menu → "Menus shown").
    val shownToWaiters: Boolean = true
) {
    // Special menus without an event are the booking extras (cakes, flowers…) guests add to a booking.
    val isBookingExtras: Boolean get() = special && event == null
    val specialKind: SpecialMenuKind? get() = when {
        !special -> null
        event != null -> SpecialMenuKind.EVENT
        else -> SpecialMenuKind.BOOKING_EXTRAS
    }
}

// Which menus waiters see (Menu → "Menus shown"): the menus marked shown. With "special menus only", just the special
// menus on the day one is on; on other days the marked menus (so waiters never see nothing).
data class MenuWaiterView(
    val specialMenusOnly: Boolean = false,
    val shownMenuIds: Set<String> = emptySet()
) {
    fun visibleMenus(menus: List<Menu>, today: kotlinx.datetime.LocalDate): List<Menu> {
        val active = menus.filter { it.active }
        if (specialMenusOnly) {
            val specialToday = active.filter { it.special && it.isOnDay(today) }
            if (specialToday.isNotEmpty()) return specialToday
        }
        return active.filter { it.shownToWaiters }
    }
}

// Whether the menu's dates include this day (no dates: every day).
fun Menu.isOnDay(day: kotlinx.datetime.LocalDate): Boolean {
    val from = availableFromDate?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
    val until = availableUntilDate?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
    return (from == null || day >= from) && (until == null || day <= until)
}

// What a special menu is for: booking extras (any day, no event) or one event night's menu.
enum class SpecialMenuKind { BOOKING_EXTRAS, EVENT }

data class MenuEvent(
    val id: String,
    val name: String,
    val icon: String,
    val startDate: String,
    val endDate: String,
    val active: Boolean = true,
    val specialMenuOnly: Boolean = false
)

// A new event night added from the menu editor.
data class MenuEventNightInput(
    val name: String,
    val icon: String,
    val startDate: String,
    val endDate: String,
    val specialMenuOnly: Boolean = false
)

// An event night as the menu editor lists it, with the menu it already has (if any).
data class MenuEventNight(
    val id: String,
    val name: String,
    val icon: String,
    val startDate: String,
    val endDate: String,
    val menuId: String? = null,
    val menuName: String? = null,
    val active: Boolean = true,
    val specialMenuOnly: Boolean = false
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
