package com.saporini.mobile_desktop.admin.devices

import androidx.compose.runtime.mutableStateListOf

/**
 * MOCK until the backend round: barcode scanners and cash drawers. The server only knows tills, tablets, kitchen screens,
 * guest displays, card terminals and printers, so these live in memory here (lost when the app closes). A scanner types
 * into a focused field like a keyboard; a cash drawer is plugged into a receipt printer that opens it.
 * Production pass: SCANNER and CASH_DRAWER device types on the server (a drawer linked to its printer).
 */
internal object DeviceExtras {
    const val SCANNER = "SCANNER"
    const val CASH_DRAWER = "CASH_DRAWER"

    val items = mutableStateListOf(
        DeviceDto("mock-scan-1", code = "SCAN-1", name = "Stock scanner", deviceType = SCANNER, manufacturer = "Zebra", model = "DS2208",
            status = "ACTIVE", online = true, notes = "USB, in the store room. Used for counts and deliveries."),
        DeviceDto("mock-scan-2", code = "SCAN-2", name = "Bar scanner", deviceType = SCANNER, manufacturer = "Honeywell", model = "Voyager 1472g",
            status = "PROVISIONING", notes = "Bluetooth, new."),
        DeviceDto("mock-drawer-1", code = "DRW-1", name = "Front cash drawer", deviceType = CASH_DRAWER, manufacturer = "APG", model = "Vasario",
            status = "ACTIVE", online = true, notes = "Plugged into the Receipt printer."),
        DeviceDto("mock-drawer-2", code = "DRW-2", name = "Bar cash drawer", deviceType = CASH_DRAWER, manufacturer = "APG", model = "Vasario",
            status = "ACTIVE", online = true, notes = "Plugged into the Bar printer.")
    )

    fun isExtra(id: String?) = id?.startsWith("mock-") == true

    fun add(type: String, name: String, code: String, maker: String, model: String, notes: String): DeviceDto {
        val device = DeviceDto("mock-${type.lowercase()}-${items.size + 1}-${code.lowercase()}", code = code.trim().uppercase(), name = name.trim(),
            deviceType = type, manufacturer = maker.trim().ifBlank { null }, model = model.trim().ifBlank { null }, status = "PROVISIONING",
            notes = notes.trim().ifBlank { null })
        items += device
        return device
    }

    fun confirm(id: String) = update(id) { it.copy(status = "ACTIVE", online = true) }
    fun setStatus(id: String, status: String) = update(id) { it.copy(status = status, online = status == "ACTIVE") }
    fun remove(id: String) { items.removeAll { it.id == id } }

    fun suggestion(type: String): Pair<String, String> {
        val prefix = if (type == SCANNER) "SCAN" else "DRW"
        val label = if (type == SCANNER) "Scanner" else "Cash drawer"
        val taken = items.map { it.code.uppercase() }.toSet()
        val next = generateSequence(1) { it + 1 }.first { "$prefix-$it" !in taken }
        return "$prefix-$next" to "$label $next"
    }

    private fun update(id: String, change: (DeviceDto) -> DeviceDto) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = change(items[index])
    }
}
