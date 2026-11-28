package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.admin.audit.AuditEntryDto
import com.saporini.mobile_desktop.admin.audit.AuditLogRepository
import com.saporini.mobile_desktop.admin.audit.AuditLogScreenModel
import com.saporini.mobile_desktop.admin.audit.AuditPageDto
import com.saporini.mobile_desktop.admin.audit.ui.AuditLogContent
import com.saporini.mobile_desktop.admin.devices.DeviceAssignmentDto
import com.saporini.mobile_desktop.admin.devices.DeviceAssignmentRequestDto
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DeviceRequestDto
import com.saporini.mobile_desktop.admin.devices.DeviceStatusRequestDto
import com.saporini.mobile_desktop.admin.devices.DevicesRepository
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.PairingCodeDto
import com.saporini.mobile_desktop.admin.devices.ui.DevicesContent
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private fun ago(minutes: Int) = (Clock.System.now() - minutes.minutes).toString()

private class GalleryAudit : AuditLogRepository {
    val entries = listOf(
        AuditEntryDto("a1", entityType = "SETTINGS_PAYMENTS", action = "UPDATE", message = "Tips switched on, suggestions 5, 10 and 15%", actorUserId = "u1", actorName = "Giulia Rossi", occurredAt = ago(4)),
        AuditEntryDto("a2", entityType = "SETTINGS_RESERVATIONS", action = "UPDATE", message = "Booking length changed from 2 h to 2 h 15 min", actorUserId = "me", actorName = "Davide Keci", occurredAt = ago(52)),
        AuditEntryDto("a3", entityType = "SETTINGS_RECEIPT", action = "UPDATE", message = "Receipt footer: “Grazie e arrivederci!”", actorUserId = "u1", actorName = "Giulia Rossi", occurredAt = ago(130)),
        AuditEntryDto("a4", entityType = "PRINTER", action = "CREATE", message = "Printer “Kitchen printer” added", actorUserId = "me", actorName = "Davide Keci", occurredAt = ago(60 * 26)),
        AuditEntryDto("a5", entityType = "SETTINGS_FRAUD", action = "UPDATE", message = "Big discounts flagged from 30% (was 25%)", actorUserId = "me", actorName = "Davide Keci", occurredAt = ago(60 * 27)),
        AuditEntryDto("a6", entityType = "RESERVATION_RULE", action = "DELETE", message = "Rule “Sunday lunch” removed", actorUserId = "u1", actorName = "Giulia Rossi", occurredAt = ago(60 * 50)),
        AuditEntryDto("a7", entityType = "SETTINGS", action = "RESET", message = "Order settings reset to defaults", actorUserId = null, actorName = null, occurredAt = ago(60 * 75))
    )
    override suspend fun entries(restaurantId: String, page: Int, size: Int) = AuditPageDto(entries, 0, size, 48, 1, false)
}

internal class GalleryDevices : DevicesRepository {
    val devices = listOf(
        DeviceDto("d1", "branch-1", "TILL-1", "Front till", "POS_TERMINAL", "Sunmi", "T2s", status = "ACTIVE", online = true, appVersion = "1.4.0", ipAddress = "192.168.1.21", platform = "Android", osVersion = "13"),
        DeviceDto("d2", "branch-1", "TAB-1", "Terrace tablet", "TABLET", "Samsung", "Tab A9", status = "ACTIVE", online = false, lastSeenAt = ago(35)),
        DeviceDto("d3", "branch-1", "KDS-1", "Grill screen", "KDS", status = "ACTIVE", online = true),
        DeviceDto("d4", "branch-1", "KDS-2", "Pastry screen", "KDS", status = "MAINTENANCE", online = false, lastSeenAt = ago(60 * 30)),
        DeviceDto("d5", "branch-1", "PAY-1", "Card terminal bar", "PAYMENT_TERMINAL", status = "PROVISIONING")
    )
    val printers = listOf(
        DeviceDto("p1", null, "PRN-1", "Kitchen printer", "PRINTER", "Epson", "TM-m30III", status = "ACTIVE", printerConnectionType = "NETWORK", printerIp = "192.168.1.50", printerPort = 9100, paperWidthMm = 80, autoCut = true),
        DeviceDto("p2", null, "PRN-2", "Bar receipts", "PRINTER", status = "ACTIVE", printerConnectionType = "USB", paperWidthMm = 58, cashDrawerKickEnabled = true)
    )
    override suspend fun devices(restaurantId: String, branchId: String) = devices
    override suspend fun saveDevice(restaurantId: String, branchId: String, deviceId: String?, request: DeviceRequestDto) = devices.first()
    override suspend fun setStatus(restaurantId: String, branchId: String, deviceId: String, request: DeviceStatusRequestDto) = devices.first()
    override suspend fun deleteDevice(restaurantId: String, branchId: String, deviceId: String) {}
    override suspend fun printers(restaurantId: String) = printers
    override suspend fun savePrinter(restaurantId: String, printerId: String?, request: DeviceRequestDto) = printers.first()
    override suspend fun deletePrinter(restaurantId: String, printerId: String) {}
    override suspend fun pairingCodes(restaurantId: String, deviceId: String) = listOf(
        PairingCodeDto("c1", deviceId, "ACTIVE", true, expiresAt = ago(-12), createdAt = ago(3), createdByDisplayName = "Giulia Rossi"),
        PairingCodeDto("c0", deviceId, "USED", false, usedAt = ago(60 * 24 * 3), createdAt = ago(60 * 24 * 3 + 5), createdByDisplayName = "Davide Keci"))
    override suspend fun newPairingCode(restaurantId: String, deviceId: String, ttlMinutes: Int?) = PairingCodeDto("c2", deviceId, "ACTIVE", true, "X7QK-2MPA-9RTL", ago(-15))
    override suspend fun revokePairingCode(restaurantId: String, deviceId: String, codeId: String) = PairingCodeDto(codeId, deviceId, "REVOKED")
    override suspend fun assignments(restaurantId: String, deviceId: String) = listOf(
        DeviceAssignmentDto("as1", deviceId, "branch-1", "Centro", assignmentType = "BRANCH", assignedAt = ago(60 * 24 * 10), active = true, assignedByDisplayName = "Davide Keci"))
    override suspend fun assign(restaurantId: String, deviceId: String, request: DeviceAssignmentRequestDto) = assignments(restaurantId, deviceId).first()
    override suspend fun unassign(restaurantId: String, deviceId: String, assignmentId: String) = assignments(restaurantId, deviceId).first()
}

class AdminSystemGalleryTest {
    @Test
    fun audit() = gallery {
        val model = AuditLogScreenModel(GalleryAudit(), gallerySession())
        model.setActive(true); settle()
        render("admin-audit", required = listOf("Audit log", "Giulia Rossi", "Tips switched on", "Kind of change", "Fold all days")) {
            val state by model.state.collectAsState()
            AuditLogContent(state, model)
        }
        render("admin-audit-phone", 390, 900) {
            val state by model.state.collectAsState()
            AuditLogContent(state, model)
        }
        // The responsive pass: phones (360, 412), small tablets (600, 768) and a tablet (1024).
        listOf(360 to 900, 412 to 900, 600 to 960, 768 to 1000, 1024 to 900).forEach { (width, height) ->
            render("size-audit-$width", width, height, required = listOf("Audit log", "Tips switched on", "Search changes")) {
                val state by model.state.collectAsState()
                AuditLogContent(state, model)
            }
        }
        // Only one person's changes, picked in "Who changed things".
        model.actor("Giulia Rossi")
        render("admin-audit-filtered", required = listOf("Who changed things", "Tips switched on")) {
            val state by model.state.collectAsState()
            AuditLogContent(state, model)
        }
        model.onDispose()
    }

    @Test
    fun devices() = gallery {
        val model = DevicesScreenModel(GalleryDevices(), gallerySession())
        model.setActive(true); settle()
        listOf("admin-devices" to (1440 to 1000), "admin-devices-tablet" to (1024 to 1300), "admin-devices-phone" to (390 to 1100)).forEach { (name, size) ->
            // The phone card shows a number bubble instead of the words "to confirm".
            render(name, size.first, size.second, required = listOf("Devices", "Tills", "Printers", "Cash drawers") + if (name.endsWith("phone")) emptyList() else listOf("to confirm")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model)
            }
        }
        // A kind's own page: its devices to open, edit or remove (like a role in Users).
        listOf("admin-devices-kind" to (1440 to 1000), "admin-devices-kind-phone" to (390 to 1100)).forEach { (name, size) ->
            render(name, size.first, size.second, required = listOf("Tablets & screens", "Grill screen", "Add a tablet")) {
                val state by model.state.collectAsState()
                DevicesContent(state, model, startKind = com.saporini.mobile_desktop.admin.devices.ui.DeviceKind.TABLET)
            }
        }
        render("admin-devices-kind-waiting", 1440, 1000, required = listOf("Card terminals", "To confirm", "Card terminal bar", "Confirm")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model, startKind = com.saporini.mobile_desktop.admin.devices.ui.DeviceKind.CARD)
        }
        render("admin-devices-add", 1440, 1000, required = listOf("What are you adding?", "A cash drawer")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model, startAdding = true)
        }
        model.select("d1"); settle()
        render("admin-devices-detail", 1440, 1300, required = listOf("Pairing codes", "Who uses it")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model, startOpen = "d1")
        }
        model.select("d5"); settle()
        render("admin-devices-confirm", 1440, 1100, required = listOf("Confirm Card terminal bar", "Make a pairing code")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model, startConfirming = "d5")
        }
        model.newPairingCode(60); settle()
        render("admin-devices-confirm-code", 1440, 1100, required = listOf("X7QK-2MPA-9RTL")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model, startConfirming = "d5")
        }
        render("admin-devices-confirm-scanner", 1440, 1100, required = listOf("Test scan")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model, startConfirming = "mock-scan-2")
        }
        model.onDispose()
    }
}
