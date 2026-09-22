package com.saporini.mobile_desktop.pos.menu.ui.section

import com.saporini.mobile_desktop.pos.menu.domain.model.withUncategorizedLast
import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory

/** Real sections first, fallback last among sections, aggregate filter last of all. */
internal fun List<MenuCategory>.inFilterOrder(): List<MenuCategory> =
    filterNot { it.name == "All" }.withUncategorizedLast { it.name } +
        filter { it.name == "All" }
