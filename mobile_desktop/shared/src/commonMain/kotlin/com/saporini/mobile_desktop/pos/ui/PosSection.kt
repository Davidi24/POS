package com.saporini.mobile_desktop.pos.ui

/** POS's places, each shown only with its own "See …" permission (user, 2026-11-19: "all nav bars with chosen"). */
enum class PosSection(val label: String, val permission: String) {
    TABLES("Tables", "TABLES_READ"),
    ORDERS("Orders", "ORDER_READ"),
    RESERVATIONS("Reservations", "RESERVATION_READ"),
    MENU("Menu", "MENUS_READ"),
    KITCHEN_STATUS("Kitchen Status", "KITCHEN_STATUS_READ"),
    SHIFT("My shift", "SHIFT_SELF"),
    MY_SALES("My Sales", "SALES_SELF"),
    HISTORY("History", "ORDER_HISTORY_READ");

    companion object {
        /** The places [permissions] open, in bar order. */
        fun allowed(permissions: Collection<String>): List<PosSection> = entries.filter { it.permission in permissions }
    }
}
