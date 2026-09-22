package com.saporini.mobile_desktop.pos.menu.ui.section

import com.saporini.mobile_desktop.pos.menu.domain.model.withUncategorizedLast
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory

/** Only Uncategorized is pinned; All keeps its chosen position. */
internal fun List<MenuCategory>.inFilterOrder(): List<MenuCategory> =
    withUncategorizedLast { it.name }

internal fun List<MenuCategory>.withAllFilterAt(position: Int?): List<MenuCategory> {
    val realSections = filterNot { it.name == "All" }.inFilterOrder().toMutableList()
    realSections.add((position ?: realSections.size).coerceIn(0, realSections.size), MenuCategory(name = "All"))
    return realSections.inFilterOrder()
}
