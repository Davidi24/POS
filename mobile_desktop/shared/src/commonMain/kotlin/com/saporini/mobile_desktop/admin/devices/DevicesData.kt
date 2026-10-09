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

/** Device kinds a branch can register (printers live on their own list). "TERMINAL" is the server's name for a till. */
val DEVICE_TYPES = listOf("TERMINAL", "TABLET", "KDS", "CUSTOMER_DISPLAY", "PAYMENT_TERMINAL")
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
    val online: Boolean = false,
    val lastSeenAt: String? = null,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val notes: String? = null,
    val printerConnectionType: String? = null,
    val paperWidthMm: Int? = null,
    val printerIp: String? = null,
    val printerPort: Int? = null,
    val autoCut: Boolean? = null,
    val cashDrawerKickEnabled: Boolean? = null,
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
    val cashDrawerKickEnabled: Boolean? = null
)

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

interface DevicesRepository {
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
