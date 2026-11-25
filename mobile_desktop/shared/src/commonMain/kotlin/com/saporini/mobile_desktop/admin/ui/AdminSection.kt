package com.saporini.mobile_desktop.admin.ui

/**
 * The Admin Hub's places, each shown only to the people who may use it (user, 2026-11-19: an admin gets shifts, users,
 * settings and the audit log; salary, devices and restaurant info are the owner's unless they give them).
 */
enum class AdminSection(val label: String, val permission: String, val alsoWith: String? = null) {
    SHIFTS("Shifts", "SHIFT_READ"),
    // Hours worked, hourly wages, tips and pay: its own page, apart from the shift schedule.
    PAY("Salary", "SALARY_MANAGE"),
    // Connect devices is enough to connect tills, tablets and screens there (user, 2026-11-25); Manage devices does the rest.
    DEVICES("Devices", "DEVICES_CONNECT", "DEVICES_MANAGE"),
    SETTINGS("Settings", "SETTINGS_READ"),
    USERS("Users", "USERS_READ"),
    AUDIT_LOGS("Audit log", "SETTINGS_AUDIT"),
    // The restaurant's own details the owner keeps up to date (phone, addresses, logo, receipt texts), and each branch's
    // opening hours: someone with only "Change opening hours" (a manager) sees just those.
    RESTAURANT_INFO("Restaurant info", "RESTAURANTS_UPDATE", "HOURS_UPDATE"),
    // The restaurant's branches as cards, each with its own page: only when looking at every branch (step 25).
    BRANCHES("Branches", "RESTAURANTS_READ");

    companion object {
        /** Bar order: Inventory and Suppliers are their own workspace, so the Admin Hub starts with Shifts. */
        val BarOrder = listOf(SHIFTS, PAY, USERS, SETTINGS, DEVICES, AUDIT_LOGS, RESTAURANT_INFO)

        fun allowed(permissions: Collection<String>): List<AdminSection> = BarOrder.filter { it.permission in permissions || (it.alsoWith != null && it.alsoWith in permissions) }

        /**
         * The places shown (user, 2026-11-23, step 25): at a branch its staff, devices and shifts (with settings, the audit
         * log and restaurant info); for every branch the users and roles, the branches, settings, the audit log and the
         * restaurant. Without the branch switcher ([scoped] false) everything as before.
         */
        fun shown(permissions: Collection<String>, scoped: Boolean, all: Boolean): List<AdminSection> {
            val order = when {
                !scoped -> BarOrder
                all -> listOf(USERS, BRANCHES, SETTINGS, AUDIT_LOGS, RESTAURANT_INFO)
                else -> listOf(SHIFTS, PAY, USERS, DEVICES, SETTINGS, AUDIT_LOGS, RESTAURANT_INFO)
            }
            return order.filter { it.permission in permissions || (it.alsoWith != null && it.alsoWith in permissions) }
        }
    }
}
