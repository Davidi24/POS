package com.saporini.mobile_desktop.admin.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Going into Inventory from a scan made elsewhere (user, 2026-11-27): [scan] opens Stock with what that barcode is (and its
 * changes), [newItem] opens a new item with the barcode filled in. Inventory takes them once it has loaded.
 */
object InventoryJump {
    var scan by mutableStateOf<String?>(null)
    var newItem by mutableStateOf<String?>(null)
}
