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

private class GalleryDevices : DevicesRepository {
    val devices = listOf(
        DeviceDto("d1", "branch-1", "TILL-1", "Front till", "TERMINAL", "Sunmi", "T2s", status = "ACTIVE", online = true, appVersion = "1.4.0", ipAddress = "192.168.1.21", platform = "Android", osVersion = "13"),
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
        render("admin-audit", required = listOf("Audit log", "Giulia Rossi", "Tips switched on")) {
            val state by model.state.collectAsState()
            AuditLogContent(state, model)
        }
        model.onDispose()
    }

    @Test
    fun devices() = gallery {
        val model = DevicesScreenModel(GalleryDevices(), gallerySession())
        model.setActive(true); settle()
        render("admin-devices", required = listOf("Devices", "Front till", "Kitchen printer")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model)
        }
        model.select("d1"); settle()
        render("admin-devices-detail", required = listOf("Pairing codes", "Who uses it")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model)
        }
        model.newPairingCode(15); settle()
        render("admin-devices-code", required = listOf("X7QK-2MPA-9RTL")) {
            val state by model.state.collectAsState()
            DevicesContent(state, model)
        }
        model.onDispose()
    }
}
