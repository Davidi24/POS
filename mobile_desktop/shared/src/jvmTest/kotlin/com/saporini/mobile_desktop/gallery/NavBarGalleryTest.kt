package com.saporini.mobile_desktop.gallery

import androidx.compose.foundation.layout.Column
import com.saporini.mobile_desktop.kds.ui.KdsSection
import com.saporini.mobile_desktop.kds.ui.KdsTopBar
import com.saporini.mobile_desktop.pos.ui.shell.NavEntry
import com.saporini.mobile_desktop.pos.ui.shell.PosBottomBar
import com.saporini.mobile_desktop.pos.ui.PosSection
import com.saporini.mobile_desktop.pos.ui.shell.PosTopBar
import com.saporini.mobile_desktop.pos.ui.shell.WorkspaceNavTopBar
import com.saporini.mobile_desktop.workspace.ui.WorkspacePage
import kotlin.test.Test

// Every workspace uses the POS bar: three fixed places, the one picked from More, then More.
class NavBarGalleryTest {
    @Test
    fun bars() = gallery {
        // The bars' notification bell looks for its NotificationCenter in Koin and does without one.
        org.koin.core.context.startKoin { }
        try {
        render("navbar-pos", height = 120, required = listOf("Tables", "Reservations", "Menu", "More"), absent = listOf("History")) {
            PosTopBar(PosSection.TABLES, {}, {}, logo = {})
        }
        // A place opened from More takes the fourth spot; Menu goes back into More.
        render("navbar-pos-history", height = 120, required = listOf("History", "More"), absent = listOf("Menu")) {
            PosTopBar(PosSection.HISTORY, {}, {}, logo = {})
        }
        render("navbar-kds", height = 120, required = listOf("Tickets", "History"), absent = listOf("More")) {
            KdsTopBar(KdsSection.TICKETS, {}, {})
        }
        val restaurant = WorkspacePage.RESTAURANT.tabs.mapIndexed { index, tab -> NavEntry(index, tab.label, tab.icon, tab.filledIcon) }
        render("navbar-restaurant", height = 120, required = listOf("Details", "More")) {
            Column {
                WorkspaceNavTopBar(restaurant, fixed = 3, selected = 0, onSelect = {}, onLogout = {})
            }
        }
        render("navbar-pos-phone", width = 412, height = 120, required = listOf("Tables", "Menu", "More")) {
            PosBottomBar(PosSection.TABLES, {}, {})
        }
        } finally {
            org.koin.core.context.stopKoin()
        }
    }
}
