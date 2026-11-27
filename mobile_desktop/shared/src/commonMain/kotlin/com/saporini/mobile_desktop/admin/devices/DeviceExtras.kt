package com.saporini.mobile_desktop.admin.devices

import androidx.compose.runtime.mutableStateListOf

/**
 * Devices the server doesn't keep. Since 2026-11-27 none: cash drawers are devices on the server (plugged into a printer) and
 * barcode scanners need nothing set up (user: "you just connect it"); kept empty so older screens' checks still read.
 */
internal object DeviceExtras {
    const val SCANNER = "SCANNER"
    const val CASH_DRAWER = "CASH_DRAWER"

    val items = mutableStateListOf<DeviceDto>()

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
