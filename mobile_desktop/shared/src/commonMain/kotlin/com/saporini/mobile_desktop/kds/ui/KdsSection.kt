package com.saporini.mobile_desktop.kds.ui

/**
 * The kitchen display's places, each shown only with its own permission (user, 2026-11-19: "the kitchen sees maybe only
 * the tickets, or Upcoming"). My shift is where kitchen staff clock in.
 */
enum class KdsSection(val label: String, val permission: String) {
    TICKETS("Tickets", "KDS_READ"),
    UPCOMING("Upcoming", "KDS_UPCOMING_READ"),
    MENU("Menu", "KDS_MENU_READ"),
    HISTORY("History", "KDS_HISTORY_READ"),
    SHIFT("My shift", "SHIFT_SELF");

    companion object {
        fun allowed(permissions: Collection<String>): List<KdsSection> = entries.filter { it.permission in permissions }
    }
}
