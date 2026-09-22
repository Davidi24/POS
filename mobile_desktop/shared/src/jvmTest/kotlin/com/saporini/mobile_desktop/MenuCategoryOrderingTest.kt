package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.pos.menu.ui.MenuCategory
import com.saporini.mobile_desktop.pos.menu.ui.section.inFilterOrder
import com.saporini.mobile_desktop.pos.menu.ui.section.withAllFilterAt
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuCategoryOrderingTest {
    private fun categories(vararg names: String) = names.map { MenuCategory(name = it) }

    @Test fun allCanStayBeforeOrBetweenSections() {
        assertEquals(listOf("All", "Mains", "Uncategorized"),
            categories("Uncategorized", "All", "Mains").inFilterOrder().map { it.name })
        assertEquals(listOf("Mains", "All", "Desserts", "Uncategorized"),
            categories("Mains", "All", "Uncategorized", "Desserts").inFilterOrder().map { it.name })
    }

    @Test fun savedPositionSurvivesRebuildingFilters() {
        val real = categories("Mains", "Desserts", "Uncategorized")
        assertEquals(listOf("Mains", "All", "Desserts", "Uncategorized"),
            real.withAllFilterAt(1).map { it.name })
        assertEquals(listOf("Mains", "Desserts", "All", "Uncategorized"),
            real.withAllFilterAt(null).map { it.name })
        assertEquals(listOf("Mains", "All", "Uncategorized"),
            categories("Mains", "Uncategorized").withAllFilterAt(99).map { it.name })
    }
}
