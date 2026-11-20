package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.pos.menu.domain.model.withUncategorizedLast
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuSectionOrderingTest {
    @Test
    fun fallbackStaysLastWithoutChangingTheOtherSectionsOrder() {
        assertEquals(
            listOf("All", "Desserts", "Mains", "uncategorized"),
            listOf("All", "uncategorized", "Desserts", "Mains").withUncategorizedLast { it }
        )
    }
}
