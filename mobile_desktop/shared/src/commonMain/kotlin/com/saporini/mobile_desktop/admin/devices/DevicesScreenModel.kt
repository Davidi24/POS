package com.saporini.mobile_desktop.admin.devices

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val MAX_DEVICE_CODE = 50
const val MAX_DEVICE_NAME = 100
const val MAX_DEVICE_NOTES = 2000
/** The server refuses pairing codes that live longer than its maximum (24 hours by default). */
const val MAX_PAIRING_MINUTES = 24 * 60

/** A device or printer being added ([id] null) or edited. Numbers are kept as typed. */
data class DeviceDraft(
    val id: String? = null,
    val printer: Boolean = false,
    val code: String = "",
    val name: String = "",
    val deviceType: String = "POS_TERMINAL",
    val manufacturer: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val status: String = "PROVISIONING",
    val active: Boolean = true,
    val online: Boolean = false,
    val ipAddress: String = "",
    val macAddress: String = "",
    val notes: String = "",
    val connection: String = "NETWORK",
    val paperWidthText: String = "80",
    val printerIp: String = "",
    val printerPortText: String = "9100",
    val autoCut: Boolean = true,
    val cashDrawerKick: Boolean = false,
    // Kept as the server sent them; the Admin Hub doesn't edit what the device reports about itself.
    val platform: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null
)

fun deviceDraftOf(device: DeviceDto, printer: Boolean) = DeviceDraft(
    id = device.id, printer = printer, code = device.code, name = device.name, deviceType = device.deviceType,
    manufacturer = device.manufacturer.orEmpty(), model = device.model.orEmpty(), serialNumber = device.serialNumber.orEmpty(),
    status = device.status, active = device.active, online = device.online, ipAddress = device.ipAddress.orEmpty(),
    macAddress = device.macAddress.orEmpty(), notes = device.notes.orEmpty(), connection = device.printerConnectionType ?: "NETWORK",
    paperWidthText = device.paperWidthMm?.toString() ?: "80", printerIp = device.printerIp.orEmpty(),
    printerPortText = device.printerPort?.toString().orEmpty(), autoCut = device.autoCut ?: true,
    cashDrawerKick = device.cashDrawerKickEnabled ?: false, platform = device.platform, osVersion = device.osVersion, appVersion = device.appVersion
)

private val IP_TEXT = Regex("^[0-9A-Fa-f:.]*$")

/** The problem with [draft], or null when it can be saved (the server's limits). */
fun deviceProblem(draft: DeviceDraft): String? {
    val code = draft.code.trim()
    val name = draft.name.trim()
    return when {
        code.isEmpty() -> "Enter a short code (e.g. TILL-1)"
        code.length > MAX_DEVICE_CODE -> "The code can be at most $MAX_DEVICE_CODE characters"
        name.isEmpty() -> "Enter a name"
        name.length > MAX_DEVICE_NAME -> "The name can be at most $MAX_DEVICE_NAME characters"
        !draft.printer && draft.deviceType !in DEVICE_TYPES -> "Choose what kind of device this is"
        draft.status !in DEVICE_STATUSES -> "Choose a status"
        listOf(draft.manufacturer, draft.model, draft.serialNumber).any { it.trim().length > 100 } -> "Maker, model and serial can be at most 100 characters"
        draft.ipAddress.trim().length > 45 || !IP_TEXT.matches(draft.ipAddress.trim()) -> "This isn't an IP address"
        draft.macAddress.trim().length > 50 -> "The MAC address can be at most 50 characters"
        draft.notes.trim().length > MAX_DEVICE_NOTES -> "Notes can be at most $MAX_DEVICE_NOTES characters"
        draft.printer && draft.connection !in PRINTER_CONNECTIONS -> "Choose how the printer is connected"
        draft.printer && draft.paperWidthText.trim().toIntOrNull()?.takeIf { it in 1..500 } == null -> "Paper width must be 1–500 mm"
        draft.printer && (draft.printerIp.trim().length > 45 || !IP_TEXT.matches(draft.printerIp.trim())) -> "This isn't a printer IP address"
        draft.printer && draft.connection == "NETWORK" && draft.printerIp.isBlank() -> "Enter the printer's IP address"
        draft.printer && draft.printerPortText.isNotBlank() && draft.printerPortText.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null ->
            "The port must be 1–65535"
        else -> null
    }
}

fun deviceRequest(draft: DeviceDraft, branchId: String?) = DeviceRequestDto(
    branchId = branchId,
    code = draft.code.trim(),
    name = draft.name.trim(),
    deviceType = if (draft.printer) "PRINTER" else draft.deviceType,
    manufacturer = draft.manufacturer.trim().ifEmpty { null },
    model = draft.model.trim().ifEmpty { null },
    serialNumber = draft.serialNumber.trim().ifEmpty { null },
    platform = draft.platform,
    osVersion = draft.osVersion,
    appVersion = draft.appVersion,
    status = draft.status,
    active = draft.active,
    online = draft.online,
    ipAddress = draft.ipAddress.trim().ifEmpty { null },
    macAddress = draft.macAddress.trim().ifEmpty { null },
    notes = draft.notes.trim().ifEmpty { null },
    // Printer details only go with printers; the server refuses them on other devices.
    printerConnectionType = draft.connection.takeIf { draft.printer },
    paperWidthMm = draft.paperWidthText.trim().toIntOrNull().takeIf { draft.printer },
    printerIp = draft.printerIp.trim().ifEmpty { null }.takeIf { draft.printer },
    printerPort = draft.printerPortText.trim().toIntOrNull().takeIf { draft.printer },
    autoCut = draft.autoCut.takeIf { draft.printer },
    cashDrawerKickEnabled = draft.cashDrawerKick.takeIf { draft.printer }
)

data class DevicesState(
    val restaurantId: String? = null,
    val branchId: String? = null,
    val canRead: Boolean = false,
    // Manage devices: add, change, remove, confirm (the server's DEVICES_MANAGE).
    val canEdit: Boolean = false,
    // Connect devices (user, 2026-11-25): tills, tablets and screens on this device or with a code, nothing else.
    val canConnect: Boolean = false,
    // Printers belong to the restaurant's settings; someone who only connects devices doesn't see them.
    val canSeePrinters: Boolean = false,
    val devices: List<DeviceDto> = emptyList(),
    val printers: List<DeviceDto> = emptyList(),
    val selectedId: String? = null,
    val pairingCodes: List<PairingCodeDto> = emptyList(),
    val assignments: List<DeviceAssignmentDto> = emptyList(),
    // Who can sign in on the open till or tablet (null while loading or for other devices).
    val signIn: DeviceSignInView? = null,
    // Works in every branch (BRANCHES_ALL or a platform admin): only they move a device to another branch.
    val everyBranch: Boolean = false,
    // Signed in with a PIN: connecting devices needs the password (user, 2026-11-25).
    val pinSignIn: Boolean = false,
    // The restaurant's branches, to move a device (only loaded for whoever manages devices and works in every branch).
    val branchChoices: List<com.saporini.mobile_desktop.platform.BranchDto> = emptyList(),
    // A new pairing code to show once; it can't be read again.
    val freshCode: PairingCodeDto? = null,
    // Devices → Enter a code: the window is open, and why the last code didn't work (shown inside it).
    val enteringCode: Boolean = false,
    val codeError: String? = null,
    // The code was typed and waits for the person who made it to agree (user, 2026-11-25).
    val connectWait: ConnectWait? = null,
    // The device just added, so the screen can go straight on to confirming it.
    val justAdded: String? = null,
    val draft: DeviceDraft? = null,
    val confirmDelete: String? = null,
    val loading: Boolean = false,
    val loadingDetail: Boolean = false,
    val saving: Boolean = false,
    val stale: Boolean = true,
    val notice: String? = null,
    val error: String? = null
) {
    val selected: DeviceDto? get() = (devices + printers).firstOrNull { it.id == selectedId }
    fun isPrinter(id: String?): Boolean = printers.any { it.id == id }
    val onlineCount: Int get() = devices.count { it.online }
    /** Whether [device] can be connected (a code made, or this device made it) by this person. */
    fun mayConnect(device: DeviceDto?): Boolean = device != null && !pinSignIn && (canEdit || (canConnect && device.deviceType in CONNECTABLE_TYPES))
    /** Why [mayConnect] says no, in one line. */
    val connectRefusal: String get() = if (pinSignIn) "Sign in with your password to connect devices." else "Ask someone who connects devices to do it."
}

/**
 * A code typed here, waiting for the person who made it to agree (user, 2026-11-25). [shownAt] is when [request]'s seconds left
 * were read (this device's clock), for the countdown; [ended] says why it stopped (refused, time's up), inside the window.
 */
data class ConnectWait(
    val request: ConnectRequestDto,
    val claim: String,
    val shownAt: Long,
    val ended: String? = null
) {
    fun secondsLeft(now: Long): Long = (request.secondsLeft - (now - shownAt) / 1000).coerceAtLeast(0)
}

/** Tills, tablets and screens: what "Connect devices" alone may connect (the server's network rule covers the same). */
// The server answers a till as "TERMINAL" (POS_TERMINAL when sent).
val CONNECTABLE_TYPES = setOf("POS_TERMINAL", "TERMINAL", "TABLET", "KDS", "CUSTOMER_DISPLAY")

/**
 * Admin Hub → Devices: the branch's devices and the restaurant's printers, adding/editing/removing them, their
 * status, the one-time codes that pair a device (shown once) and who or which branch a device is given to.
 */
class DevicesScreenModel(
    private val repository: DevicesRepository,
    session: SessionManager,
    // Makes the device the app runs on a till with a pairing code (user, 2026-11-18: PIN sign-in only on connected tills).
    // The code is typed in by the person signed in here, so the server knows who set it up (user, 2026-11-25).
    private val connectHere: (suspend (restaurantId: String, pairingToken: String) -> com.saporini.mobile_desktop.auth.data.repository.ConnectedHere)? = null,
    // The restaurant's branches (to move a device) and people to choose (who can sign in on it).
    private val branchesOf: (suspend (restaurantId: String) -> List<com.saporini.mobile_desktop.platform.BranchDto>)? = null,
    private val searchPeople: (suspend (query: String) -> List<SignInPerson>)? = null,
    // Enter a code: asks the code's maker to agree, then takes the connection (user, 2026-11-25).
    private val connector: DeviceConnector? = null
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    private var detailRevision = 0L
    private var signIn = 0L
    private var active = false
    private var loadJob: Job? = null
    private var waitJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                signIn++
                loadJob?.cancel()
                val permissions = user?.takeIf { it.isActive }?.permissions.orEmpty().toSet()
                mutable.value = DevicesState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    branchId = user?.defaultBranchId,
                    canRead = "SETTINGS_READ" in permissions || "DEVICES_CONNECT" in permissions || "DEVICES_MANAGE" in permissions,
                    canEdit = "DEVICES_MANAGE" in permissions,
                    canConnect = "DEVICES_CONNECT" in permissions || "DEVICES_MANAGE" in permissions,
                    canSeePrinters = "SETTINGS_READ" in permissions,
                    everyBranch = com.saporini.mobile_desktop.core.session.canSeeEveryBranch(user),
                    pinSignIn = com.saporini.mobile_desktop.core.session.PIN_SIGN_IN in permissions
                )
                if (active) load()
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false) }
        }
    }

    /** Shows another branch's devices (printers belong to the whole restaurant and stay). */
    fun branch(branchId: String) {
        if (state.value.branchId == branchId) return
        mutable.update { it.copy(branchId = branchId, devices = emptyList(), selectedId = null, pairingCodes = emptyList(), assignments = emptyList()) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                // coroutineScope: a failed call ends up in the catch below instead of escaping the screen.
                val (branchDevices, printers) = coroutineScope {
                    val devices = current.branchId?.let { branchId -> async { repository.devices(restaurantId, branchId) } }
                    val printers = if (current.canSeePrinters) repository.printers(restaurantId) else emptyList()
                    devices?.await().orEmpty() to printers
                }
                if (token != revision) return@launch
                val branches = if (current.canEdit && current.everyBranch) runCatching { branchesOf?.invoke(restaurantId) }.getOrNull().orEmpty() else emptyList()
                mutable.update { state ->
                    val all = branchDevices + printers
                    state.copy(
                        branchChoices = branches.filter { it.isActive },
                        devices = branchDevices.sortedBy { it.name.lowercase() },
                        printers = printers.sortedBy { it.name.lowercase() },
                        selectedId = state.selectedId?.takeIf { id -> all.any { it.id == id } },
                        stale = false,
                        error = null
                    )
                }
                state.value.selectedId?.let { loadDetail(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(stale = true, error = adminMessage(e, write = false), canRead = it.canRead && !isDenied(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    /** Opens one device: its pairing codes and who it is given to. */
    fun select(deviceId: String?) {
        mutable.update { it.copy(selectedId = deviceId, pairingCodes = emptyList(), assignments = emptyList(), signIn = null, freshCode = null) }
        if (deviceId != null) loadDetail(deviceId)
    }

    private fun loadDetail(deviceId: String) {
        val restaurantId = state.value.restaurantId ?: return
        val token = ++detailRevision
        mutable.update { it.copy(loadingDetail = true) }
        work.launch {
            try {
                val (loadedCodes, assignments) = coroutineScope {
                    val codes = async { repository.pairingCodes(restaurantId, deviceId) }
                    val assignments = repository.assignments(restaurantId, deviceId)
                    codes.await() to assignments
                }
                // Who can sign in: tills, tablets and screens only.
                val device = state.value.devices.firstOrNull { it.id == deviceId }
                val signIn = if (device?.deviceType in CONNECTABLE_TYPES) runCatching { repository.signIn(restaurantId, deviceId) }.getOrNull() else null
                if (token == detailRevision && state.value.selectedId == deviceId) {
                    mutable.update { it.copy(pairingCodes = loadedCodes.sortedByDescending { c -> c.createdAt.orEmpty() },
                        assignments = assignments.sortedByDescending { a -> a.assignedAt.orEmpty() }, signIn = signIn) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == detailRevision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            } finally {
                if (token == detailRevision) mutable.update { it.copy(loadingDetail = false) }
            }
        }
    }

    // ---- Adding and editing ----

    fun newDevice(type: String = "POS_TERMINAL") = needsEdit {
        if (state.value.branchId == null) {
            mutable.update { it.copy(error = "Choose a branch first") }
            return@needsEdit
        }
        val (code, name) = suggestion(type)
        mutable.update { it.copy(draft = DeviceDraft(deviceType = type, code = code, name = name), notice = null) }
    }

    fun newPrinter() = needsEdit {
        val (code, name) = suggestion("PRINTER")
        mutable.update { it.copy(draft = DeviceDraft(printer = true, deviceType = "PRINTER", code = code, name = name), notice = null) }
    }

    /** A free short code and a name for a new device of [type]: TAB-4 and "Tablet 4" when TAB-1…3 exist. */
    private fun suggestion(type: String): Pair<String, String> {
        val prefix = when (type) { "POS_TERMINAL" -> "TILL"; "TABLET" -> "TAB"; "KDS" -> "KDS"; "CUSTOMER_DISPLAY" -> "CD"; "PAYMENT_TERMINAL" -> "PAY"; else -> "PRN" }
        val label = when (type) { "POS_TERMINAL" -> "Till"; "TABLET" -> "Tablet"; "KDS" -> "Kitchen screen"; "CUSTOMER_DISPLAY" -> "Guest display"
            "PAYMENT_TERMINAL" -> "Card terminal"; else -> "Printer" }
        val taken = (state.value.devices + state.value.printers).map { it.code.uppercase() }.toSet()
        val next = generateSequence(1) { it + 1 }.first { "$prefix-$it" !in taken }
        return "$prefix-$next" to "$label $next"
    }

    fun edit(deviceId: String) = needsEdit {
        val current = state.value
        val device = (current.devices + current.printers).firstOrNull { it.id == deviceId } ?: return@needsEdit
        mutable.update { it.copy(draft = deviceDraftOf(device, current.isPrinter(deviceId)), notice = null) }
    }

    fun change(change: (DeviceDraft) -> DeviceDraft) = mutable.update { state ->
        val draft = state.draft ?: return@update state
        state.copy(draft = change(draft).copy(id = draft.id, printer = draft.printer))
    }

    fun cancelEdit() = mutable.update { it.copy(draft = null) }

    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        deviceProblem(draft)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        val branchId = current.branchId
        if (!draft.printer && branchId == null) {
            mutable.update { it.copy(error = "Choose a branch first") }
            return
        }
        // Printers belong to the restaurant; a device to the branch whose list is open.
        val request = deviceRequest(draft, if (draft.printer) null else branchId)
        write("${draft.name.trim()} was saved") { restaurantId ->
            val saved = if (draft.printer) repository.savePrinter(restaurantId, draft.id, request)
            else repository.saveDevice(restaurantId, branchId!!, draft.id, request)
            mutable.update { state ->
                fun List<DeviceDto>.put() = (if (any { it.id == saved.id }) map { if (it.id == saved.id) saved else it } else this + saved).sortedBy { it.name.lowercase() }
                val added = saved.id.takeIf { draft.id == null }
                if (draft.printer) state.copy(printers = state.printers.put(), draft = null, justAdded = added)
                else state.copy(devices = state.devices.put(), draft = null, justAdded = added)
            }
        }
    }

    /** Changes a device's status (e.g. blocks a lost tablet). Inactive, blocked and retired devices can't sign in. */
    fun setStatus(deviceId: String, status: String) {
        val current = state.value
        val device = current.devices.firstOrNull { it.id == deviceId } ?: return
        val branchId = current.branchId ?: return
        if (status !in DEVICE_STATUSES || status == device.status) return
        needsEdit {
            val usable = status == "ACTIVE" || status == "PROVISIONING" || status == "MAINTENANCE"
            write(null) { restaurantId ->
                val saved = repository.setStatus(restaurantId, branchId, deviceId, DeviceStatusRequestDto(status, usable, device.online && usable))
                mutable.update { state -> state.copy(devices = state.devices.map { if (it.id == saved.id) saved else it }) }
            }
        }
    }

    /** A printer's state (in use, in repair, switched off …), saved with the rest of the printer. */
    fun setPrinterStatus(printerId: String, status: String) {
        val printer = state.value.printers.firstOrNull { it.id == printerId } ?: return
        if (status !in DEVICE_STATUSES || status == printer.status) return
        needsEdit {
            val usable = status == "ACTIVE" || status == "PROVISIONING" || status == "MAINTENANCE"
            val request = deviceRequest(deviceDraftOf(printer, true).copy(status = status, active = usable), null)
            write(null) { restaurantId ->
                val saved = repository.savePrinter(restaurantId, printerId, request)
                mutable.update { state -> state.copy(printers = state.printers.map { if (it.id == saved.id) saved else it }) }
            }
        }
    }

    fun clearJustAdded() = mutable.update { it.copy(justAdded = null) }

    /** Confirms a device that was set up: it works, so it's in use from now on. */
    fun confirm(deviceId: String) {
        val current = state.value
        if (current.isPrinter(deviceId)) {
            val printer = current.printers.firstOrNull { it.id == deviceId } ?: return
            needsEdit {
                val request = deviceRequest(deviceDraftOf(printer, true).copy(status = "ACTIVE", active = true), null)
                write("${printer.name} is confirmed") { restaurantId ->
                    val saved = repository.savePrinter(restaurantId, deviceId, request)
                    mutable.update { state -> state.copy(printers = state.printers.map { if (it.id == saved.id) saved else it }) }
                }
            }
            return
        }
        val device = current.devices.firstOrNull { it.id == deviceId } ?: return
        val branchId = current.branchId ?: return
        needsEdit {
            write("${device.name} is confirmed") { restaurantId ->
                val saved = repository.setStatus(restaurantId, branchId, deviceId, DeviceStatusRequestDto("ACTIVE", true, device.online))
                mutable.update { state -> state.copy(devices = state.devices.map { if (it.id == saved.id) saved else it }) }
            }
        }
    }

    fun askDelete(deviceId: String) = needsEdit { mutable.update { it.copy(confirmDelete = deviceId) } }

    fun dismissDelete() = mutable.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val current = state.value
        val deviceId = current.confirmDelete ?: return
        mutable.update { it.copy(confirmDelete = null) }
        val printer = current.isPrinter(deviceId)
        val branchId = current.branchId
        write("Removed") { restaurantId ->
            if (printer) repository.deletePrinter(restaurantId, deviceId) else repository.deleteDevice(restaurantId, branchId ?: return@write, deviceId)
            mutable.update { state ->
                state.copy(devices = state.devices.filterNot { it.id == deviceId }, printers = state.printers.filterNot { it.id == deviceId },
                    selectedId = state.selectedId.takeUnless { it == deviceId })
            }
        }
    }

    // ---- Pairing and assignments ----

    /** Makes a one-time pairing code for the open device; it is shown once in [DevicesState.freshCode]. */
    fun newPairingCode(minutes: Int? = null) {
        val deviceId = state.value.selectedId ?: return
        if (minutes != null && minutes !in 1..MAX_PAIRING_MINUTES) {
            mutable.update { it.copy(error = "A pairing code can last 1 minute to ${MAX_PAIRING_MINUTES / 60} hours") }
            return
        }
        needsConnect {
            write(null) { restaurantId ->
                val code = repository.newPairingCode(restaurantId, deviceId, minutes)
                ConnectWatch.codeMade(code.expiresAt?.let { runCatching { kotlin.time.Instant.parse(it).toEpochMilliseconds() }.getOrNull() })
                mutable.update { state ->
                    if (state.selectedId != deviceId) state
                    else state.copy(freshCode = code, pairingCodes = listOf(code.copy(pairingToken = null)) + state.pairingCodes.filterNot { it.id == code.id })
                }
            }
        }
    }

    /** Makes the device the app runs on the open till or tablet: a one-time code, used here at once. */
    fun connectThisDevice() {
        val deviceId = state.value.selectedId ?: return
        val connect = connectHere ?: return
        needsConnect {
            write(null) { restaurantId ->
                val code = repository.newPairingCode(restaurantId, deviceId, 5)
                val connected = connect(restaurantId, code.pairingToken ?: error("No pairing code came back"))
                val name = connected.till.name
                mutable.update {
                    it.copy(notice = if (connected.networkSaved) "This device is $name now. This Wi-Fi is saved as the restaurant's network."
                    else "This device is $name now. Staff sign in here with their PIN.")
                }
                load()
            }
        }
    }

    /** This device stops being a till (the PIN pad goes; the device itself stays in the list). */
    fun disconnectThisDevice() {
        val name = com.saporini.mobile_desktop.core.session.TokenStore.till.value?.name ?: return
        com.saporini.mobile_desktop.core.session.TokenStore.forgetTill()
        mutable.update { it.copy(notice = "This device is no longer $name.") }
    }

    fun dismissFreshCode() = mutable.update { it.copy(freshCode = null) }

    fun openEnterCode() = mutable.update { it.copy(enteringCode = true, codeError = null, error = null, notice = null) }

    fun closeEnterCode() {
        if (state.value.saving) return
        cancelWait()
        mutable.update { it.copy(enteringCode = false, codeError = null) }
    }

    /**
     * Devices → Enter a code: the device the app runs on asks to become the till or tablet the code was made for, and waits
     * for the person who made the code to agree (user, 2026-11-25: "it goes to the person who generated the code"). Once they
     * agree it takes the connection; why it didn't work stays inside the window.
     */
    fun enterCode(code: String) {
        val connector = connector ?: return
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (current.saving || current.connectWait != null || code.isBlank()) return
        if ((!current.canConnect && !current.canEdit) || current.pinSignIn) {
            mutable.update { it.copy(codeError = current.connectRefusal) }
            return
        }
        val token = signIn
        mutable.update { it.copy(saving = true, codeError = null) }
        work.launch {
            try {
                val asked = connector.ask(restaurantId, code.trim())
                if (token != signIn) return@launch
                mutable.update { it.copy(connectWait = ConnectWait(asked.request, asked.claimToken, nowMs())) }
                followWait(restaurantId, asked.request.id, asked.claimToken, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == signIn) mutable.update { it.copy(codeError = adminMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    /** Looks every two seconds whether the code's maker answered; agreed = this device takes the connection. */
    private fun followWait(restaurantId: String, requestId: String, claim: String, token: Long) {
        val connector = connector ?: return
        waitJob?.cancel()
        waitJob = work.launch {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                if (token != signIn) return@launch
                val request = try {
                    connector.get(restaurantId, requestId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue // a missed look; the next one tries again
                }
                val wait = state.value.connectWait?.takeIf { it.request.id == requestId && it.ended == null } ?: return@launch
                when (request.status) {
                    "WAITING" -> mutable.update { it.copy(connectWait = wait.copy(request = request, shownAt = nowMs())) }
                    "AGREED" -> {
                        try {
                            val connected = connector.claim(restaurantId, requestId, claim)
                            if (token != signIn) return@launch
                            com.saporini.mobile_desktop.core.session.TokenStore.connectTill(connected.till)
                            val name = connected.till.name
                            mutable.update {
                                it.copy(enteringCode = false, connectWait = null,
                                    notice = if (connected.networkSaved) "This device is $name now. This Wi-Fi is saved as the restaurant's network."
                                    else "This device is $name now. Staff sign in here with their PIN.")
                            }
                            load()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            mutable.update { it.copy(connectWait = wait.copy(request = request, ended = adminMessage(e, write = true))) }
                        }
                        return@launch
                    }
                    "REFUSED" -> {
                        mutable.update { it.copy(connectWait = wait.copy(request = request,
                            ended = "${request.madeBy ?: "The person who made the code"} said no, so the code was ended. Ask for a new one.")) }
                        return@launch
                    }
                    "EXPIRED" -> {
                        mutable.update { it.copy(connectWait = wait.copy(request = request,
                            ended = "Nobody agreed in time. Try again, or ask for a new code.")) }
                        return@launch
                    }
                    else -> {
                        mutable.update { it.copy(connectWait = null) }
                        return@launch
                    }
                }
            }
        }
    }

    fun clearCodeError() = mutable.update { it.copy(codeError = null) }

    /** Stops waiting (Cancel, or Try again after it ended): back to typing a code. */
    fun cancelWait() {
        val wait = state.value.connectWait ?: return
        waitJob?.cancel()
        mutable.update { it.copy(connectWait = null) }
        val restaurantId = state.value.restaurantId ?: return
        val connector = connector ?: return
        if (wait.ended == null) work.launch { runCatching { connector.cancel(restaurantId, wait.request.id) } }
    }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /** Who can sign in on the open till or tablet: nothing chosen = everyone at its branch. */
    fun saveSignIn(roleIds: List<String>, userIds: List<String>) {
        val deviceId = state.value.selectedId ?: return
        needsEdit {
            write("Saved") { restaurantId ->
                val saved = repository.setSignIn(restaurantId, deviceId, roleIds, userIds)
                mutable.update { if (it.selectedId == deviceId) it.copy(signIn = saved) else it }
            }
        }
    }

    /** People to choose for a device, by name (empty without a search). */
    suspend fun findPeople(query: String): List<SignInPerson> =
        if (query.isBlank()) emptyList() else runCatching { searchPeople?.invoke(query.trim()) }.getOrNull().orEmpty()

    /** Moves the open device to another branch: it leaves this branch's list. */
    fun moveTo(toBranchId: String) {
        val current = state.value
        val deviceId = current.selectedId ?: return
        val branchId = current.branchId ?: return
        if (toBranchId == branchId) return
        val name = current.branchChoices.firstOrNull { it.id == toBranchId }?.name
        needsEdit {
            write(null) { restaurantId ->
                val moved = repository.moveDevice(restaurantId, branchId, deviceId, toBranchId)
                mutable.update { state ->
                    state.copy(devices = state.devices.filterNot { it.id == deviceId }, selectedId = null,
                        notice = if (name != null) "${moved.name} works at $name now." else "${moved.name} was moved.")
                }
            }
        }
    }

    fun revokePairingCode(codeId: String) {
        val deviceId = state.value.selectedId ?: return
        needsEdit {
            write("The code can no longer be used") { restaurantId ->
                val revoked = repository.revokePairingCode(restaurantId, deviceId, codeId)
                mutable.update { state ->
                    state.copy(pairingCodes = state.pairingCodes.map { if (it.id == revoked.id) revoked.copy(pairingToken = null) else it },
                        freshCode = state.freshCode?.takeUnless { it.id == codeId })
                }
            }
        }
    }

    /** Gives the open device to a person (userId) or to a branch (branchId). */
    fun assign(userId: String?, branchId: String?, notes: String = "") {
        val deviceId = state.value.selectedId ?: return
        if ((userId == null) == (branchId == null)) {
            mutable.update { it.copy(error = "Choose a person or a branch") }
            return
        }
        if (notes.trim().length > 1000) {
            mutable.update { it.copy(error = "Notes can be at most 1000 characters") }
            return
        }
        val request = DeviceAssignmentRequestDto(if (userId != null) "USER" else "BRANCH", branchId, userId, notes.trim().ifEmpty { null })
        needsEdit {
            write("Assigned") { restaurantId ->
                repository.assign(restaurantId, deviceId, request)
                // Giving it to someone new ends earlier assignments on the server; read them again.
                val assignments = repository.assignments(restaurantId, deviceId)
                mutable.update { if (it.selectedId == deviceId) it.copy(assignments = assignments.sortedByDescending { a -> a.assignedAt.orEmpty() }) else it }
            }
        }
    }

    fun unassign(assignmentId: String) {
        val deviceId = state.value.selectedId ?: return
        needsEdit {
            write(null) { restaurantId ->
                val ended = repository.unassign(restaurantId, deviceId, assignmentId)
                mutable.update { state -> state.copy(assignments = state.assignments.map { if (it.id == ended.id) ended else it }) }
            }
        }
    }

    // ---- Plumbing ----

    /** Making a code or connecting this device: "Manage devices", or "Connect devices" for a till, tablet or screen. */
    private inline fun needsConnect(action: () -> Unit) {
        if (!state.value.mayConnect(state.value.selected)) {
            mutable.update { it.copy(error = "Ask someone who manages devices to connect this one.") }
            return
        }
        action()
    }

    private inline fun needsEdit(action: () -> Unit) {
        if (!state.value.canEdit) {
            mutable.update { it.copy(error = "You can only look at devices") }
            return
        }
        action()
    }

    private fun write(notice: String?, action: suspend (restaurantId: String) -> Unit) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (current.saving) return
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, notice = null) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    action(restaurantId)
                    if (notice != null && token == signIn) mutable.update { it.copy(notice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = adminMessage(e, write = true)) }
                    if (isStale(e)) load()
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        detailRevision++
        active = false
        work.cancel()
    }
}
