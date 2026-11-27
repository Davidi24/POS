package com.saporini.mobile_desktop.admin.devices

import com.saporini.mobile_desktop.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Admin Hub → Devices: tills, tablets, kitchen screens and payment terminals of a branch, the restaurant's
// printers, and the one-time codes that pair a new device.

/** Device kinds a branch can register (printers live on their own list). "POS_TERMINAL" is the server's name for a till. */
val DEVICE_TYPES = listOf("POS_TERMINAL", "TABLET", "KDS", "CUSTOMER_DISPLAY", "PAYMENT_TERMINAL")
val DEVICE_STATUSES = listOf("PROVISIONING", "ACTIVE", "INACTIVE", "BLOCKED", "MAINTENANCE", "RETIRED")
val PRINTER_CONNECTIONS = listOf("NETWORK", "USB", "BLUETOOTH", "SERIAL")

@Serializable
data class DeviceDto(
    val id: String,
    val branchId: String? = null,
    val code: String,
    val name: String,
    val deviceType: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val platform: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val status: String = "PROVISIONING",
    val active: Boolean = true,
    // Heard from in the last few minutes (a till or tablet sends a heartbeat every minute).
    val online: Boolean = false,
    // Connected with a pairing code (and not moved to another device since), and when.
    val connected: Boolean = false,
    val connectedAt: String? = null,
    // Which device it's connected on, as that device said ("Linux computer (fedora)", "Linux 6.17").
    val connectedDeviceName: String? = null,
    val connectedDeviceSystem: String? = null,
    val lastSeenAt: String? = null,
    val ipAddress: String? = null,
    // Where its address is ("Tirana, Albania"), from the server's place database (2026-11-25).
    val place: String? = null,
    val macAddress: String? = null,
    val notes: String? = null,
    val printerConnectionType: String? = null,
    val paperWidthMm: Int? = null,
    val printerIp: String? = null,
    val printerPort: Int? = null,
    val autoCut: Boolean? = null,
    val cashDrawerKickEnabled: Boolean? = null,
    // A printer installed on a computer: its name there and the till it's plugged into (2026-11-27).
    val systemPrinterName: String? = null,
    val hostDeviceId: String? = null,
    // RECEIPT, KITCHEN, BAR, SMALL, DOCUMENT or OTHER (2026-11-27).
    val printerRole: String? = null,
    // Whether a printer answers (tills check every minute): ONLINE, OFFLINE or UNCHECKED; when, by which till, and why not.
    val printerState: String? = null,
    val printerReachedAt: String? = null,
    val printerCheckedAt: String? = null,
    val printerCheckedBy: String? = null,
    val printerProblem: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class DeviceRequestDto(
    val branchId: String? = null,
    val code: String,
    val name: String,
    val deviceType: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val platform: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val status: String,
    val active: Boolean,
    val online: Boolean,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val notes: String? = null,
    val printerConnectionType: String? = null,
    val paperWidthMm: Int? = null,
    val printerIp: String? = null,
    val printerPort: Int? = null,
    val autoCut: Boolean? = null,
    val cashDrawerKickEnabled: Boolean? = null,
    val systemPrinterName: String? = null,
    val hostDeviceId: String? = null,
    val printerRole: String? = null
)

/** A till or kitchen station a printer can print for, and the printer it prints on now. */
@Serializable
data class PrintTargetDto(val id: String, val name: String = "", val branchName: String? = null, val printerId: String? = null,
                          val printerName: String? = null)

/** What a printer prints (`/printers/{p}/jobs`): the tills whose receipts and the kitchen stations whose tickets. */
@Serializable
data class PrinterJobsDto(
    val receiptTillIds: List<String> = emptyList(),
    val kitchenStationIds: List<String> = emptyList(),
    val tills: List<PrintTargetDto> = emptyList(),
    val stations: List<PrintTargetDto> = emptyList()
)

@Serializable
data class SetPrinterJobsBody(val receiptTillIds: List<String>, val kitchenStationIds: List<String>)

/** The kinds of printers "Add a printer" offers, each filling in its usual settings (user, 2026-11-27). */
enum class PrinterPreset(val role: String, val title: String, val line: String, val defaultName: String, val paperMm: Int, val cut: Boolean,
                         val drawer: Boolean) {
    RECEIPT("RECEIPT", "Receipt printer", "Bills and receipts at the till; opens the cash drawer", "Receipt printer", 80, true, true),
    KITCHEN("KITCHEN", "Kitchen printer", "Tickets for a kitchen station", "Kitchen printer", 80, true, false),
    BAR("BAR", "Bar printer", "Drink tickets at the bar", "Bar printer", 80, true, false),
    SMALL("SMALL", "Small receipt printer", "58 mm receipts, often a portable one", "Small receipt printer", 58, true, false),
    DOCUMENT("DOCUMENT", "Document printer", "A4 reports and documents, like Statistics", "Document printer", 210, false, false),
    OTHER("OTHER", "Other printer", "Set everything yourself", "Printer", 80, true, false)
}

/** What a printer's role is called. */
fun printerRoleLabel(role: String?): String = PrinterPreset.entries.firstOrNull { it.role == role }?.title ?: "Other printer"

@Serializable
data class DeviceStatusRequestDto(val status: String, val active: Boolean, val online: Boolean)

@Serializable
data class PairingCodeDto(
    val id: String,
    val deviceId: String,
    val state: String? = null,
    val active: Boolean = false,
    // Only present right after the code is made; the server never shows it again.
    val pairingToken: String? = null,
    val expiresAt: String? = null,
    val usedAt: String? = null,
    val revokedAt: String? = null,
    val createdAt: String? = null,
    val createdByDisplayName: String? = null
)

@Serializable
data class PairingCodeRequestDto(val ttlMinutes: Int? = null)

@Serializable
data class DeviceAssignmentDto(
    val id: String,
    val deviceId: String,
    val branchId: String? = null,
    val branchName: String? = null,
    val userId: String? = null,
    val userEmail: String? = null,
    val userDisplayName: String? = null,
    val assignmentType: String,
    val assignedAt: String? = null,
    val unassignedAt: String? = null,
    val active: Boolean = false,
    val assignedByDisplayName: String? = null,
    val notes: String? = null
)

@Serializable
data class DeviceAssignmentRequestDto(val assignmentType: String, val branchId: String? = null, val userId: String? = null, val notes: String? = null)

/** Who can sign in on a till or tablet (user, 2026-11-25): [everyone] when nothing is chosen. */
@Serializable
data class DeviceSignInView(
    val deviceId: String,
    val everyone: Boolean = true,
    val roles: List<SignInRole> = emptyList(),
    val people: List<SignInPerson> = emptyList(),
    val roleChoices: List<SignInRole> = emptyList(),
    val alwaysAllowed: List<String> = emptyList(),
    // The roles the person editing sees, each with its people (the Users rule): the form's role bar.
    val tabs: List<SignInTab> = emptyList()
)

/** A role as a tab of the who-can-sign-in form, with its people; [always] = they sign in on any device anyway. */
@Serializable
data class SignInTab(val id: String, val code: String = "", val name: String, val picture: String? = null, val always: Boolean = false,
                     val people: List<SignInPerson> = emptyList())

@Serializable
data class SignInRole(val id: String, val code: String = "", val name: String, val picture: String? = null)

@Serializable
data class SignInPerson(val id: String, val name: String, val photoUrl: String? = null)

@Serializable
data class SetDeviceSignInBody(val roleIds: List<String>, val userIds: List<String>)

@Serializable
data class MoveDeviceBody(val branchId: String)

interface DevicesRepository {
    suspend fun signIn(restaurantId: String, deviceId: String): DeviceSignInView = throw UnsupportedOperationException("Not here")
    suspend fun setSignIn(restaurantId: String, deviceId: String, roleIds: List<String>, userIds: List<String>): DeviceSignInView =
        throw UnsupportedOperationException("Not here")
    /** The device stops being connected: where it was connected it stops working, and it can be connected again. */
    suspend fun disconnect(restaurantId: String, deviceId: String): Unit = throw UnsupportedOperationException("Not here")
    suspend fun printerJobs(restaurantId: String, printerId: String): PrinterJobsDto = throw UnsupportedOperationException("Not here")
    suspend fun setPrinterJobs(restaurantId: String, printerId: String, body: SetPrinterJobsBody): PrinterJobsDto =
        throw UnsupportedOperationException("Not here")
    suspend fun moveDevice(restaurantId: String, branchId: String, deviceId: String, toBranchId: String): DeviceDto =
        throw UnsupportedOperationException("Not here")

    suspend fun devices(restaurantId: String, branchId: String): List<DeviceDto>
    suspend fun saveDevice(restaurantId: String, branchId: String, deviceId: String?, request: DeviceRequestDto): DeviceDto
    suspend fun setStatus(restaurantId: String, branchId: String, deviceId: String, request: DeviceStatusRequestDto): DeviceDto
    suspend fun deleteDevice(restaurantId: String, branchId: String, deviceId: String)

    suspend fun printers(restaurantId: String): List<DeviceDto>
    suspend fun savePrinter(restaurantId: String, printerId: String?, request: DeviceRequestDto): DeviceDto
    suspend fun deletePrinter(restaurantId: String, printerId: String)

    suspend fun pairingCodes(restaurantId: String, deviceId: String): List<PairingCodeDto>
    suspend fun newPairingCode(restaurantId: String, deviceId: String, ttlMinutes: Int?): PairingCodeDto
    suspend fun revokePairingCode(restaurantId: String, deviceId: String, codeId: String): PairingCodeDto

    suspend fun assignments(restaurantId: String, deviceId: String): List<DeviceAssignmentDto>
    suspend fun assign(restaurantId: String, deviceId: String, request: DeviceAssignmentRequestDto): DeviceAssignmentDto
    suspend fun unassign(restaurantId: String, deviceId: String, assignmentId: String): DeviceAssignmentDto
}

class DevicesApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : DevicesRepository {

    private fun id(value: String) = value.encodeURLPathPart()
    private fun restaurant(restaurantId: String) = "${baseUrlProvider().trimEnd('/')}/restaurants/${id(restaurantId)}"
    private fun branchDevices(restaurantId: String, branchId: String) = "${restaurant(restaurantId)}/branches/${id(branchId)}/devices"
    private fun device(restaurantId: String, deviceId: String) = "${restaurant(restaurantId)}/devices/${id(deviceId)}"

    override suspend fun devices(restaurantId: String, branchId: String): List<DeviceDto> = client.get(branchDevices(restaurantId, branchId)).body()

    override suspend fun signIn(restaurantId: String, deviceId: String): DeviceSignInView = client.get("${device(restaurantId, deviceId)}/sign-in").body()

    override suspend fun setSignIn(restaurantId: String, deviceId: String, roleIds: List<String>, userIds: List<String>): DeviceSignInView =
        client.put("${device(restaurantId, deviceId)}/sign-in") { contentType(ContentType.Application.Json); setBody(SetDeviceSignInBody(roleIds, userIds)) }.body()

    override suspend fun disconnect(restaurantId: String, deviceId: String) {
        client.post("${device(restaurantId, deviceId)}/disconnect")
    }

    override suspend fun printerJobs(restaurantId: String, printerId: String): PrinterJobsDto =
        client.get("${restaurant(restaurantId)}/printers/${id(printerId)}/jobs").body()

    override suspend fun setPrinterJobs(restaurantId: String, printerId: String, body: SetPrinterJobsBody): PrinterJobsDto =
        client.put("${restaurant(restaurantId)}/printers/${id(printerId)}/jobs") { contentType(ContentType.Application.Json); setBody(body) }.body()

    override suspend fun moveDevice(restaurantId: String, branchId: String, deviceId: String, toBranchId: String): DeviceDto =
        client.patch("${branchDevices(restaurantId, branchId)}/${id(deviceId)}/branch") { contentType(ContentType.Application.Json); setBody(MoveDeviceBody(toBranchId)) }.body()

    override suspend fun saveDevice(restaurantId: String, branchId: String, deviceId: String?, request: DeviceRequestDto): DeviceDto =
        if (deviceId == null) client.post(branchDevices(restaurantId, branchId)) { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put("${branchDevices(restaurantId, branchId)}/${id(deviceId)}") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun setStatus(restaurantId: String, branchId: String, deviceId: String, request: DeviceStatusRequestDto): DeviceDto =
        client.patch("${branchDevices(restaurantId, branchId)}/${id(deviceId)}/status") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun deleteDevice(restaurantId: String, branchId: String, deviceId: String) {
        client.delete("${branchDevices(restaurantId, branchId)}/${id(deviceId)}")
    }

    override suspend fun printers(restaurantId: String): List<DeviceDto> = client.get("${restaurant(restaurantId)}/settings/printers").body()

    override suspend fun savePrinter(restaurantId: String, printerId: String?, request: DeviceRequestDto): DeviceDto =
        if (printerId == null) client.post("${restaurant(restaurantId)}/settings/printers") { contentType(ContentType.Application.Json); setBody(request) }.body()
        else client.put("${restaurant(restaurantId)}/settings/printers/${id(printerId)}") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun deletePrinter(restaurantId: String, printerId: String) {
        client.delete("${restaurant(restaurantId)}/settings/printers/${id(printerId)}")
    }

    override suspend fun pairingCodes(restaurantId: String, deviceId: String): List<PairingCodeDto> =
        client.get("${device(restaurantId, deviceId)}/pairing-tokens").body()

    override suspend fun newPairingCode(restaurantId: String, deviceId: String, ttlMinutes: Int?): PairingCodeDto =
        client.post("${device(restaurantId, deviceId)}/pairing-tokens") { contentType(ContentType.Application.Json); setBody(PairingCodeRequestDto(ttlMinutes)) }.body()

    override suspend fun revokePairingCode(restaurantId: String, deviceId: String, codeId: String): PairingCodeDto =
        client.post("${device(restaurantId, deviceId)}/pairing-tokens/${id(codeId)}/revoke").body()

    override suspend fun assignments(restaurantId: String, deviceId: String): List<DeviceAssignmentDto> =
        client.get("${device(restaurantId, deviceId)}/assignments").body()

    override suspend fun assign(restaurantId: String, deviceId: String, request: DeviceAssignmentRequestDto): DeviceAssignmentDto =
        client.post("${device(restaurantId, deviceId)}/assignments") { contentType(ContentType.Application.Json); setBody(request) }.body()

    override suspend fun unassign(restaurantId: String, deviceId: String, assignmentId: String): DeviceAssignmentDto =
        client.post("${device(restaurantId, deviceId)}/assignments/${id(assignmentId)}/unassign").body()
}
