package com.saporini.mobile_desktop

import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.pos.menu.ui.menu.menuContentColumns
import com.saporini.mobile_desktop.pos.menu.ui.menu.menuDialogWidth
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class MenuLayoutMetricsTest {
    @Test
    fun cardsFitFromSmallPhonesThroughUltrawideDisplays() {
        for (width in listOf(288, 328, 398, 720, 840, 900, 1280, 1920, 2560, 3840)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                val columns = menuContentColumns(width.dp, 280.dp, 14.dp, scale)
                val cardWidth = (width - 14f * (columns - 1)) / columns
                assertTrue(columns >= 1)
                assertTrue(cardWidth > 0)
                assertTrue(columns == 1 || cardWidth >= 280f * scale)
            }
        }
        assertEquals(1, menuContentColumns(328.dp, 280.dp, 14.dp))
        assertEquals(3, menuContentColumns(900.dp, 280.dp, 14.dp))
        assertTrue(menuContentColumns(3440.dp, 280.dp, 14.dp) > 6)
    }

    @Test
    fun dialogsStayWithinAvailableSpaceAndRespectTheirMaximumWidth() {
        for (width in listOf(280, 560, 792, 1280, 2560, 3840)) {
            val actual = menuDialogWidth(width.dp, 0.55f, 920.dp)
            assertTrue(actual <= width.dp && actual <= 920.dp)
            assertTrue(actual >= minOf(width.dp, 560.dp))
        }
    }
}
