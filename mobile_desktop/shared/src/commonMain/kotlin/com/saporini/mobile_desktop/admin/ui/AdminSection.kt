package com.saporini.mobile_desktop.admin.ui

/**
 * The Admin Hub's places, each shown only to the people who may use it (user, 2026-11-19: an admin gets shifts, users,
 * settings and the audit log; salary, devices and restaurant info are the owner's unless they give them).
 */
enum class AdminSection(val label: String, val permission: String) {
    SHIFTS("Shifts", "SHIFT_READ"),
    // Hours worked, hourly wages, tips and pay: its own page, apart from the shift schedule.
    PAY("Salary", "SALARY_MANAGE"),
    DEVICES("Devices", "DEVICES_MANAGE"),
    SETTINGS("Settings", "SETTINGS_READ"),
    USERS("Users", "USERS_READ"),
    AUDIT_LOGS("Audit log", "SETTINGS_AUDIT"),
    // The restaurant's own details the owner keeps up to date (phone, addresses, logo, receipt texts).
    RESTAURANT_INFO("Restaurant info", "RESTAURANTS_UPDATE");

    companion object {
        /** Bar order: Inventory and Suppliers are their own workspace, so the Admin Hub starts with Shifts. */
        val BarOrder = listOf(SHIFTS, PAY, USERS, SETTINGS, DEVICES, AUDIT_LOGS, RESTAURANT_INFO)

        fun allowed(permissions: Collection<String>): List<AdminSection> = BarOrder.filter { it.permission in permissions }
    }
}
