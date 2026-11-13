package com.saporini.mobile_desktop.admin.ui

enum class AdminSection(val label: String) {
    SHIFTS("Shifts"),
    // Hours worked, hourly wages, tips and pay: its own page, apart from the shift schedule.
    PAY("Salary"),
    DEVICES("Devices"),
    SETTINGS("Settings"),
    USERS("Users"),
    AUDIT_LOGS("Audit Logs"),
    // The restaurant's own details the owner keeps up to date (phone, addresses, logo, receipt texts).
    RESTAURANT_INFO("Restaurant info")
}
