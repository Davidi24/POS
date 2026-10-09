@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.admin

import com.saporini.mobile_desktop.admin.audit.AuditEntryDto
import com.saporini.mobile_desktop.admin.audit.AuditLogApi
import com.saporini.mobile_desktop.admin.audit.AuditLogRepository
import com.saporini.mobile_desktop.admin.audit.AuditLogScreenModel
import com.saporini.mobile_desktop.admin.audit.AuditPageDto
import com.saporini.mobile_desktop.admin.devices.DeviceAssignmentDto
import com.saporini.mobile_desktop.admin.devices.DeviceAssignmentRequestDto
import com.saporini.mobile_desktop.admin.devices.DeviceDraft
import com.saporini.mobile_desktop.admin.devices.DeviceDto
import com.saporini.mobile_desktop.admin.devices.DeviceRequestDto
import com.saporini.mobile_desktop.admin.devices.DeviceStatusRequestDto
import com.saporini.mobile_desktop.admin.devices.DevicesApi
import com.saporini.mobile_desktop.admin.devices.DevicesRepository
import com.saporini.mobile_desktop.admin.devices.DevicesScreenModel
import com.saporini.mobile_desktop.admin.devices.PairingCodeDto
import com.saporini.mobile_desktop.admin.devices.deviceProblem
import com.saporini.mobile_desktop.admin.devices.deviceRequest
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun device(id: String, name: String, type: String = "TERMINAL", online: Boolean = false) =
    DeviceDto(id = id, branchId = "branch-1", code = id.uppercase(), name = name, deviceType = type, status = "ACTIVE", online = online)

private class FakeDevices : DevicesRepository {
    val devices = mutableListOf(device("till", "Till", online = true), device("tab", "Tablet", "TABLET"))
    val printers = mutableListOf(device("prn", "Kitchen printer", "PRINTER").copy(branchId = null, printerConnectionType = "NETWORK", paperWidthMm = 80))
    val calls = mutableListOf<String>()
    val requests = mutableListOf<DeviceRequestDto>()
    var failure: Exception? = null
    var detailFailure: Exception? = null
    var assignments = mutableListOf(DeviceAssignmentDto("a1", "till", assignmentType = "BRANCH", branchId = "branch-1", active = true, assignedAt = "2026-10-01T10:00:00Z"))

    override suspend fun devices(restaurantId: String, branchId: String): List<DeviceDto> {
        calls += "devices $branchId"; failure?.let { throw it }
        return devices.filter { it.branchId == branchId }
    }

    override suspend fun saveDevice(restaurantId: String, branchId: String, deviceId: String?, request: DeviceRequestDto): DeviceDto {
        requests += request; calls += "save device $branchId $deviceId"
        return DeviceDto(deviceId ?: "new", branchId, request.code, request.name, request.deviceType, status = request.status)
    }

    override suspend fun setStatus(restaurantId: String, branchId: String, deviceId: String, request: DeviceStatusRequestDto): DeviceDto {
        calls += "status $deviceId ${request.status} ${request.active} ${request.online}"
        return devices.first { it.id == deviceId }.copy(status = request.status, active = request.active, online = request.online)
    }

    override suspend fun deleteDevice(restaurantId: String, branchId: String, deviceId: String) { calls += "delete device $deviceId" }
    override suspend fun printers(restaurantId: String): List<DeviceDto> { calls += "printers"; return printers.toList() }

    override suspend fun savePrinter(restaurantId: String, printerId: String?, request: DeviceRequestDto): DeviceDto {
        requests += request; calls += "save printer $printerId"
        return DeviceDto(printerId ?: "prn-new", null, request.code, request.name, "PRINTER", printerConnectionType = request.printerConnectionType)
    }

    override suspend fun deletePrinter(restaurantId: String, printerId: String) { calls += "delete printer $printerId" }

    override suspend fun pairingCodes(restaurantId: String, deviceId: String): List<PairingCodeDto> {
        detailFailure?.let { throw it }
        return listOf(PairingCodeDto("old", deviceId, "EXPIRED", createdAt = "2026-10-01T09:00:00Z"))
    }

    override suspend fun newPairingCode(restaurantId: String, deviceId: String, ttlMinutes: Int?): PairingCodeDto {
        calls += "code $deviceId $ttlMinutes"
        return PairingCodeDto("new", deviceId, "ACTIVE", active = true, pairingToken = "SECRET-123", createdAt = "2026-10-06T09:00:00Z")
    }

    override suspend fun revokePairingCode(restaurantId: String, deviceId: String, codeId: String): PairingCodeDto {
        calls += "revoke $codeId"
        return PairingCodeDto(codeId, deviceId, "REVOKED", active = false, revokedAt = "2026-10-06T09:05:00Z")
    }

    override suspend fun assignments(restaurantId: String, deviceId: String) = assignments.filter { it.deviceId == deviceId }

    override suspend fun assign(restaurantId: String, deviceId: String, request: DeviceAssignmentRequestDto): DeviceAssignmentDto {
        calls += "assign $deviceId ${request.assignmentType} ${request.userId}"
        assignments.replaceAll { it.copy(active = false) }
        val made = DeviceAssignmentDto("a2", deviceId, userId = request.userId, assignmentType = request.assignmentType, active = true, assignedAt = "2026-10-06T10:00:00Z")
        assignments += made
        return made
    }

    override suspend fun unassign(restaurantId: String, deviceId: String, assignmentId: String): DeviceAssignmentDto {
        calls += "unassign $assignmentId"
        return assignments.first { it.id == assignmentId }.copy(active = false, unassignedAt = "2026-10-06T11:00:00Z")
    }
}

class DeviceRulesTest {
    private val till = DeviceDraft(code = "TILL-1", name = "Front till")

    @Test
    fun devicesAndPrintersAreCheckedAgainstTheServersLimits() {
        assertNull(deviceProblem(till))
        assertEquals("Enter a short code (e.g. TILL-1)", deviceProblem(till.copy(code = "  ")))
        assertEquals("The code can be at most 50 characters", deviceProblem(till.copy(code = "C".repeat(51))))
        assertEquals("The name can be at most 100 characters", deviceProblem(till.copy(name = "N".repeat(101))))
        assertEquals("Choose what kind of device this is", deviceProblem(till.copy(deviceType = "PRINTER")))
        assertEquals("This isn't an IP address", deviceProblem(till.copy(ipAddress = "my laptop")))
        assertNull(deviceProblem(till.copy(ipAddress = "fe80::1ff:fe23:4567:890a")))
        assertEquals("Notes can be at most 2000 characters", deviceProblem(till.copy(notes = "n".repeat(2001))))
        val printer = DeviceDraft(printer = true, code = "PRN", name = "Bar", printerIp = "192.168.1.50")
        assertNull(deviceProblem(printer))
        assertEquals("Paper width must be 1–500 mm", deviceProblem(printer.copy(paperWidthText = "0")))
        assertEquals("Paper width must be 1–500 mm", deviceProblem(printer.copy(paperWidthText = "wide")))
        assertEquals("Enter the printer's IP address", deviceProblem(printer.copy(printerIp = " ")))
        assertNull(deviceProblem(printer.copy(printerIp = "", connection = "USB")))
        assertEquals("The port must be 1–65535", deviceProblem(printer.copy(printerPortText = "70000")))
    }

    @Test
    fun printerDetailsOnlyGoWithPrinters() {
        val request = deviceRequest(till.copy(printerIp = "1.2.3.4", code = " TILL-1 ", manufacturer = " "), "branch-1")
        assertEquals("TILL-1", request.code)
        assertNull(request.manufacturer)
        assertNull(request.printerIp)
        assertNull(request.paperWidthMm)
        assertEquals("branch-1", request.branchId)
        val printer = deviceRequest(DeviceDraft(printer = true, code = "P", name = "P", printerIp = "10.0.0.9"), null)
        assertEquals("PRINTER", printer.deviceType)
        assertEquals(80, printer.paperWidthMm)
        assertEquals(9100, printer.printerPort)
        assertEquals("NETWORK", printer.printerConnectionType)
    }
}

class DevicesScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakeDevices = FakeDevices(),
        permissions: List<String> = listOf("SETTINGS_READ", "SETTINGS_UPDATE"),
        block: suspend TestScope.(DevicesScreenModel, FakeDevices) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = DevicesScreenModel(repo, session)
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun loadsTheBranchsDevicesAndTheRestaurantsPrinters() = runTest(dispatcher) {
        check { model, repo ->
            assertEquals(listOf("Tablet", "Till"), model.state.value.devices.map { it.name })
            assertEquals(listOf("prn"), model.state.value.printers.map { it.id })
            assertEquals(1, model.state.value.onlineCount)
            assertTrue("devices branch-1" in repo.calls)
        }
    }

    @Test
    fun aFailedLoadIsShownNotThrown() = runTest(dispatcher) {
        val repo = FakeDevices().apply { failure = java.io.IOException("offline") }
        check(repo) { model, _ ->
            assertTrue(model.state.value.stale)
            assertEquals("Could not load. Check the connection and try again.", model.state.value.error)
        }
    }

    @Test
    fun aNewPairingCodeIsShownOnceAndCanBeRevoked() = runTest(dispatcher) {
        check { model, repo ->
            model.select("till"); runCurrent()
            assertEquals(listOf("old"), model.state.value.pairingCodes.map { it.id })
            model.newPairingCode(0)
            assertEquals("A pairing code can last 1 minute to 24 hours", model.state.value.error)
            model.newPairingCode(30); runCurrent()
            assertEquals("SECRET-123", model.state.value.freshCode?.pairingToken)
            // The list never keeps the secret.
            assertNull(model.state.value.pairingCodes.first().pairingToken)
            model.dismissFreshCode()
            assertNull(model.state.value.freshCode)
            model.revokePairingCode("new"); runCurrent()
            assertEquals("REVOKED", model.state.value.pairingCodes.first { it.id == "new" }.state)
            assertTrue("code till 30" in repo.calls && "revoke new" in repo.calls)
        }
    }

    @Test
    fun aDeviceGoesToOnePersonOrBranchAtATime() = runTest(dispatcher) {
        check { model, repo ->
            model.select("till"); runCurrent()
            model.assign(userId = "u1", branchId = "branch-1")
            assertEquals("Choose a person or a branch", model.state.value.error)
            model.assign(userId = "u1", branchId = null); runCurrent()
            assertEquals(listOf("a2", "a1"), model.state.value.assignments.map { it.id })
            assertEquals(listOf(true, false), model.state.value.assignments.map { it.active })
            model.unassign("a2"); runCurrent()
            assertFalse(model.state.value.assignments.first { it.id == "a2" }.active)
            assertTrue("unassign a2" in repo.calls)
        }
    }

    @Test
    fun blockingADeviceAlsoMarksItUnusable() = runTest(dispatcher) {
        check { model, repo ->
            model.setStatus("till", "BLOCKED"); runCurrent()
            assertTrue("status till BLOCKED false false" in repo.calls)
            assertEquals("BLOCKED", model.state.value.devices.first { it.id == "till" }.status)
            model.setStatus("till", "WHATEVER"); runCurrent()
            assertEquals(1, repo.calls.count { it.startsWith("status") })
        }
    }

    @Test
    fun devicesAndPrintersAreSavedOnTheirOwnLists() = runTest(dispatcher) {
        check { model, repo ->
            model.newDevice()
            model.change { it.copy(code = "KDS-1", name = "Grill screen", deviceType = "KDS", printerIp = "1.1.1.1") }
            model.save(); runCurrent()
            assertTrue("save device branch-1 null" in repo.calls)
            assertNull(repo.requests.last().printerIp)
            assertEquals(listOf("Grill screen", "Tablet", "Till"), model.state.value.devices.map { it.name })

            model.newPrinter()
            model.change { it.copy(code = "PRN-2", name = "Bar printer", printerIp = "192.168.0.20", printer = false) }
            assertTrue(model.state.value.draft!!.printer)
            model.save(); runCurrent()
            assertTrue("save printer null" in repo.calls)
            assertNull(repo.requests.last().branchId)
            assertEquals(2, model.state.value.printers.size)

            model.edit("prn")
            assertTrue(model.state.value.draft!!.printer)
            model.cancelEdit()
            model.askDelete("prn"); model.confirmDelete(); runCurrent()
            assertTrue("delete printer prn" in repo.calls)
            model.askDelete("tab"); model.confirmDelete(); runCurrent()
            assertTrue("delete device tab" in repo.calls)
            assertEquals(listOf("Grill screen", "Till"), model.state.value.devices.map { it.name })
        }
    }

    @Test
    fun switchingBranchesReloadsOnlyThatBranch() = runTest(dispatcher) {
        check { model, repo ->
            model.select("till"); runCurrent()
            model.branch("branch-2"); runCurrent()
            assertTrue("devices branch-2" in repo.calls)
            assertTrue(model.state.value.devices.isEmpty())
            assertNull(model.state.value.selectedId)
            assertEquals(1, model.state.value.printers.size)
        }
    }

    @Test
    fun readOnlyPeopleCantChangeDevices() = runTest(dispatcher) {
        check(permissions = listOf("SETTINGS_READ")) { model, repo ->
            model.newDevice()
            assertNull(model.state.value.draft)
            model.select("till"); runCurrent()
            model.newPairingCode(); runCurrent()
            model.setStatus("till", "BLOCKED"); runCurrent()
            assertEquals("You can only look at devices", model.state.value.error)
            assertTrue(repo.calls.none { it.startsWith("code") || it.startsWith("status") })
        }
    }
}

private class FakeAudit(var total: Int = 120) : AuditLogRepository {
    val pages = mutableListOf<Int>()
    var failure: Exception? = null

    override suspend fun entries(restaurantId: String, page: Int, size: Int): AuditPageDto {
        pages += page; failure?.let { throw it }
        val all = (0 until total).map {
            AuditEntryDto("e$it", entityType = if (it % 2 == 0) "SETTINGS_RECEIPT" else "SETTINGS_PAYMENTS",
                message = "Change $it", actorName = if (it % 3 == 0) "Anna" else "Marco")
        }
        val items = all.drop(page * size).take(size)
        return AuditPageDto(items, page, size, total.toLong(), (total + size - 1) / size, (page + 1) * size < total)
    }
}

class AuditLogScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakeAudit = FakeAudit(),
        permissions: List<String> = listOf("SETTINGS_AUDIT"),
        block: suspend TestScope.(AuditLogScreenModel, FakeAudit) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = AuditLogScreenModel(repo, session)
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun pagesOnAndARefreshKeepsThePlace() = runTest(dispatcher) {
        check { model, repo ->
            assertEquals(50, model.state.value.entries.size)
            model.loadMore(); runCurrent()
            model.loadMore(); runCurrent()
            assertEquals(120, model.state.value.entries.size)
            assertFalse(model.state.value.hasNext)
            repo.pages.clear()
            model.refresh(); runCurrent()
            assertEquals(listOf(0, 1, 2), repo.pages)
            assertEquals(120, model.state.value.entries.size)
        }
    }

    @Test
    fun filtersNarrowWhatIsLoaded() = runTest(dispatcher) {
        check { model, _ ->
            assertEquals(listOf("SETTINGS_PAYMENTS", "SETTINGS_RECEIPT"), model.state.value.areas)
            model.area("SETTINGS_PAYMENTS")
            assertTrue(model.state.value.visible.all { it.entityType == "SETTINGS_PAYMENTS" })
            model.search(" anna ")
            assertTrue(model.state.value.visible.all { it.actorName == "Anna" && it.entityType == "SETTINGS_PAYMENTS" })
            assertTrue(model.state.value.visible.isNotEmpty())
        }
    }

    @Test
    fun withoutTheAuditPermissionNothingLoadsAndADenialStopsReading() = runTest(dispatcher) {
        check(permissions = listOf("SETTINGS_READ")) { model, repo ->
            assertTrue(repo.pages.isEmpty())
            assertFalse(model.state.value.canRead)
        }
        val denied = FakeAudit().apply { failure = ApiException(403, "Access denied") }
        check(denied) { model, _ ->
            assertFalse(model.state.value.canRead)
            assertEquals("You don't have permission to do this.", model.state.value.error)
        }
    }

    @Test
    fun anEmptyLogIsJustEmpty() = runTest(dispatcher) {
        check(FakeAudit(total = 0)) { model, _ ->
            assertTrue(model.state.value.entries.isEmpty())
            assertFalse(model.state.value.hasNext)
            assertNull(model.state.value.error)
        }
    }
}

class DevicesAndAuditApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun pathsAndBodies() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as? TextContent)?.text
            seen += "${request.method.value} ${request.url.encodedPath}?${request.url.encodedQuery}" + (body?.let { " $it" } ?: "")
            val path = request.url.encodedPath
            val response = when {
                path.endsWith("/audit-logs") -> """{"items":[{"id":"e","actorName":"Anna","message":"Changed tips"}],"totalElements":1}"""
                path.endsWith("/pairing-tokens") && request.method.value == "POST" -> """{"id":"t","deviceId":"d","pairingToken":"ABC"}"""
                path.contains("/pairing-tokens/") -> """{"id":"t","deviceId":"d","state":"REVOKED"}"""
                path.endsWith("/assignments") && request.method.value == "GET" -> "[]"
                path.contains("/assignments") -> """{"id":"a","deviceId":"d","assignmentType":"USER"}"""
                request.method.value == "DELETE" -> ""
                path.endsWith("/devices") || path.endsWith("/printers") -> if (request.method.value == "GET") "[]" else """{"id":"d","code":"C","name":"N","deviceType":"TERMINAL"}"""
                else -> """{"id":"d","code":"C","name":"N","deviceType":"TERMINAL","status":"BLOCKED"}"""
            }
            if (response.isEmpty()) respond("", HttpStatusCode.NoContent) else respond(response, headers = jsonHeaders)
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val audit = AuditLogApi(client) { "http://localhost/" }.entries("r", 2, 50)
            assertEquals("Anna", audit.items.single().actorName)
            val api = DevicesApi(client) { "http://localhost" }
            api.devices("r", "b")
            api.saveDevice("r", "b", null, deviceRequest(DeviceDraft(code = "C", name = "N"), "b"))
            api.setStatus("r", "b", "d", DeviceStatusRequestDto("BLOCKED", active = false, online = false))
            api.deleteDevice("r", "b", "d")
            api.savePrinter("r", "p", deviceRequest(DeviceDraft(printer = true, code = "P", name = "P", printerIp = "10.0.0.2"), null))
            assertEquals("ABC", api.newPairingCode("r", "d", 15).pairingToken)
            api.revokePairingCode("r", "d", "t")
            api.assign("r", "d", DeviceAssignmentRequestDto("USER", userId = "u"))
            api.unassign("r", "d", "a")
            assertTrue(seen.any { it.startsWith("GET /restaurants/r/settings/audit-logs?page=2&size=50") }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/branches/b/devices?") && "\"status\":\"PROVISIONING\"" in it && "\"active\":true" in it && "\"online\":false" in it }, seen.toString())
            assertTrue(seen.any { it == "PATCH /restaurants/r/branches/b/devices/d/status? {\"status\":\"BLOCKED\",\"active\":false,\"online\":false}" }, seen.toString())
            assertTrue("DELETE /restaurants/r/branches/b/devices/d?" in seen, seen.toString())
            assertTrue(seen.any { it.startsWith("PUT /restaurants/r/settings/printers/p?") && "\"deviceType\":\"PRINTER\"" in it && "\"paperWidthMm\":80" in it }, seen.toString())
            assertTrue(seen.any { it == "POST /restaurants/r/devices/d/pairing-tokens? {\"ttlMinutes\":15}" }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/devices/d/pairing-tokens/t/revoke") }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /restaurants/r/devices/d/assignments/a/unassign") }, seen.toString())
        } finally {
            client.close()
        }
    }
}
