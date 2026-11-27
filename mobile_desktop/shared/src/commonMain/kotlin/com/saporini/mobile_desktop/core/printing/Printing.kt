package com.saporini.mobile_desktop.core.printing

import androidx.compose.ui.graphics.ImageBitmap
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.platform.ReceiptDesignResponseDto
import com.saporini.mobile_desktop.platform.ReceiptKind
import com.saporini.mobile_desktop.platform.ReceiptLook
import com.saporini.mobile_desktop.platform.receiptLookOf
import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A printer as the app sends to it (`PrintingDtos.Printer`). */
@Serializable
data class PrintPrinter(
    val id: String,
    val name: String,
    val role: String? = null,
    val connectionType: String? = null,
    val ip: String? = null,
    val port: Int? = null,
    // A printer on a computer: its name there; a Bluetooth printer: its address.
    val systemPrinterName: String? = null,
    // The till or tablet it's plugged into (USB, Bluetooth): the others print on it through that device.
    val hostDeviceId: String? = null,
    val hostDeviceName: String? = null,
    val paperWidthMm: Int = 80,
    val autoCut: Boolean = true,
    val cashDrawerKick: Boolean = false,
    val usable: Boolean = true
) {
    /** Where it is, in a few words ("192.168.1.50", "on Bar till"). */
    val where: String? get() = ip?.takeIf { connectionType == "NETWORK" } ?: hostDeviceName?.let { "on $it" } ?: systemPrinterName
}

@Serializable
data class StationPrinters(val stationId: String, val stationName: String = "", val printers: List<PrintPrinter> = emptyList())

/** What this device prints where, and when things print by themselves (`GET /restaurants/{r}/print-setup`). */
@Serializable
data class PrintSetup(
    val deviceId: String? = null,
    val receiptPrinters: List<PrintPrinter> = emptyList(),
    val kitchen: List<StationPrinters> = emptyList(),
    val printers: List<PrintPrinter> = emptyList(),
    val printKitchenOnSend: Boolean = true,
    val printBillOnRequest: Boolean = true,
    val printReceiptAfterPayment: String = "ASK"
)

/** A receipt waiting for "Print the receipt?", or for a printer to be picked from [choices]. */
data class ReceiptQuestion(val orderId: String, val kind: ReceiptKind, val choices: List<PrintPrinter> = emptyList())

@Serializable
private data class QueueBody(val data: String)

@Serializable
private data class Queued(val id: String, val status: String = "WAITING", val problem: String? = null)

/**
 * Printing from the device the app runs on (user, 2026-11-27): kitchen tickets when an order is sent (on the printer that prints
 * each station's tickets), the preliminary bill when the bill is asked for, the fiscal receipt after the last payment (always,
 * after asking, or never), the drawer on cash, all on the printer chosen for this till on the printer's own page. Network
 * printers print from any device; a printer plugged into a till or tablet (USB, Bluetooth) prints there, and the others leave
 * it the ticket through the server ([PrintRelay] prints it).
 */
class Printing(
    private val client: HttpClient,
    private val session: SessionManager,
    private val baseUrl: () -> String = { ApiConfig.BASE_URL }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var setup: PrintSetup? = null
    private var setupFor: String? = null
    private var setupAt: Instant = Instant.DISTANT_PAST
    private val printedItems = mutableSetOf<String>()
    private val pendingKitchen = mutableMapOf<String, Job>()
    private val pictures = mutableMapOf<String, ImageBitmap?>()

    private val _notice = MutableStateFlow<PrintNotice?>(null)
    /** The last printing problem (or "printed"), for a toast. */
    val notice: StateFlow<PrintNotice?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    private val _question = MutableStateFlow<ReceiptQuestion?>(null)
    /** A receipt asking "Print the receipt?" or which printer, for the app's window. */
    val question: StateFlow<ReceiptQuestion?> = _question.asStateFlow()
    fun dismissQuestion() { _question.value = null }

    private fun restaurantId(): String? = session.currentUser.value?.restaurantId?.takeIf { it.isNotBlank() }
    private fun root(restaurantId: String) = "${baseUrl().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}"
    private val hereId: String? get() = TokenStore.till.value?.deviceId

    /** What this device prints where; asked again after a minute (or when [fresh]). */
    suspend fun setup(fresh: Boolean = false): PrintSetup? {
        val restaurantId = restaurantId() ?: return null
        val device = hereId
        val key = "$restaurantId/$device"
        val now = Clock.System.now()
        if (!fresh && setup != null && setupFor == key && now - setupAt < 60.seconds) return setup
        return runCatching {
            client.get("${root(restaurantId)}/print-setup") { device?.let { parameter("deviceId", it) } }.body<PrintSetup>()
        }.onSuccess { setup = it; setupFor = key; setupAt = now }.getOrNull() ?: setup
    }

    /**
     * Sends [bytes] to [printer]: straight over the network; on this device for a printer on this computer or paired here;
     * through the server to the device it's plugged into otherwise. Answers whether it printed; [quiet] keeps the toast for
     * the caller.
     */
    suspend fun send(printer: PrintPrinter, bytes: ByteArray, quiet: Boolean = false): Boolean {
        val problem: String? = try {
            when {
                printer.connectionType == "NETWORK" && !printer.ip.isNullOrBlank() -> { PrinterLink.sendNetwork(printer.ip, printer.port ?: 9100, bytes); null }
                printer.hostDeviceId != null && printer.hostDeviceId != hereId -> relay(printer, bytes)
                printer.connectionType == "BLUETOOTH" && !printer.systemPrinterName.isNullOrBlank() -> { PrinterLink.sendBluetooth(printer.systemPrinterName, bytes); null }
                !printer.systemPrinterName.isNullOrBlank() -> { PrinterLink.sendSystem(printer.systemPrinterName, bytes); null }
                else -> "${printer.name} has no address yet. Set it up in Devices."
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "${printer.name} didn't print. Check it's on and connected."
        }
        if (problem != null && !quiet) _notice.value = PrintNotice(problem = true, text = problem)
        return problem == null
    }

    /** On the 1st of [printers] that prints; says so when the 1st didn't and a later one did. */
    private suspend fun sendFirstThatPrints(printers: List<PrintPrinter>, bytes: ByteArray): Boolean {
        printers.forEachIndexed { index, printer ->
            if (send(printer, bytes, quiet = index < printers.lastIndex)) {
                if (index > 0) _notice.value = PrintNotice(problem = true, text = "${printers.first().name} didn't print, so it went to ${printer.name}.")
                return true
            }
        }
        return false
    }

    /** Leaves [bytes] for the device [printer] is plugged into and waits for it to print (about 2 minutes at most). */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun relay(printer: PrintPrinter, bytes: ByteArray): String? {
        val restaurantId = restaurantId() ?: return "Sign in again to print."
        val queued = client.post("${root(restaurantId)}/printers/${printer.id.encodeURLPathPart()}/queue") {
            contentType(ContentType.Application.Json)
            setBody(QueueBody(Base64.encode(bytes)))
        }.body<Queued>()
        val until = Clock.System.now() + 3.minutes
        while (Clock.System.now() < until) {
            delay(2_000)
            val now = runCatching { client.get("${root(restaurantId)}/print-queue/${queued.id.encodeURLPathPart()}").body<Queued>() }.getOrNull() ?: continue
            when (now.status) {
                "DONE" -> return null
                "FAILED" -> return now.problem ?: "${printer.name} didn't print."
            }
        }
        return "${printer.name} didn't print. Is the app open on ${printer.hostDeviceName ?: "the device it's plugged into"}?"
    }

    /** A test page on [printer]: an A4 page on a document printer, a slip on the others. */
    suspend fun testPage(printer: PrintPrinter): Boolean {
        if (printer.role == "DOCUMENT") return documentTest(printer)
        val ok = send(printer, testPage(printer.name, printer.paperWidthMm, session.currentUser.value?.restaurantName, printer.autoCut))
        if (ok) _notice.value = PrintNotice(problem = false, text = "A test page went to ${printer.name}.")
        return ok
    }

    /**
     * The order was sent to the kitchen from here: each station's new dishes print on its printer, a moment later so several
     * dishes sent one after another come out on one ticket.
     */
    fun kitchenSent(orderId: String) {
        pendingKitchen.remove(orderId)?.cancel()
        pendingKitchen[orderId] = scope.launch {
            delay(1_500)
            pendingKitchen.remove(orderId)
            runCatching { printKitchen(orderId) }
        }
    }

    private suspend fun printKitchen(orderId: String) {
        val restaurantId = restaurantId() ?: return
        val setup = setup() ?: return
        if (!setup.printKitchenOnSend || setup.kitchen.isEmpty()) return
        val tickets = client.get("${root(restaurantId)}/orders/${orderId.encodeURLPathPart()}/kds/tickets").body<List<KdsTicket>>()
        // The dishes just sent: those fired within moments of the newest, not printed here before.
        val fired = tickets.flatMap { it.items }.mapNotNull { (it.firedAt ?: it.createdAt)?.let { at -> runCatching { Instant.parse(at) }.getOrNull() } }
        val newest = fired.maxOrNull() ?: return
        val from = newest - 20.seconds
        tickets.forEach { ticket ->
            val printers = setup.kitchen.firstOrNull { it.stationId == ticket.stationId }?.printers?.filter { it.usable }.orEmpty()
            if (printers.isEmpty()) return@forEach
            val items = ticket.items.filter { item ->
                item.id !in printedItems && item.status != "CANCELLED" &&
                    ((item.firedAt ?: item.createdAt)?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { it >= from } ?: false)
            }
            if (items.isEmpty()) return@forEach
            val paper = printers.first()
            if (sendFirstThatPrints(printers, kitchenTicket(ticket, items, paper.paperWidthMm, paper.autoCut, RestaurantTime.zone))) {
                printedItems += items.map { it.id }
            }
        }
    }

    /** A kitchen ticket again (the kitchen display's Print), on its station's printer. */
    fun reprint(ticket: KdsTicket) {
        scope.launch {
            val setup = setup() ?: return@launch
            val printers = setup.kitchen.firstOrNull { it.stationId == ticket.stationId }?.printers?.filter { it.usable }.orEmpty()
            if (printers.isEmpty()) {
                _notice.value = PrintNotice(problem = true, text = "${ticket.stationName ?: "This station"} has no printer. Choose one in Settings → Devices & printing.")
                return@launch
            }
            val items = ticket.items.filter { it.status != "CANCELLED" }
            val paper = printers.first()
            if (sendFirstThatPrints(printers, kitchenTicket(ticket, items, paper.paperWidthMm, paper.autoCut, RestaurantTime.zone))) {
                _notice.value = PrintNotice(problem = false, text = "Printed.")
            }
        }
    }

    /** The bill was asked for: the preliminary bill prints when that's switched on. */
    fun billRequested(orderId: String) {
        scope.launch {
            val setup = setup() ?: return@launch
            if (setup.printBillOnRequest) printReceipt(orderId, ReceiptKind.PRELIMINARY, setup)
        }
    }

    /**
     * A payment was taken here: cash opens the drawer; the last payment prints the fiscal receipt (always, after asking, or
     * never, as set in the Admin Hub).
     */
    fun paid(orderId: String, method: String, settled: Boolean) {
        scope.launch {
            val setup = setup() ?: return@launch
            val drawer = setup.receiptPrinters.firstOrNull { it.cashDrawerKick && it.usable }
            val cash = method == "CASH"
            if (!settled) {
                if (cash && drawer != null) openDrawer(drawer)
                return@launch
            }
            when (setup.printReceiptAfterPayment) {
                "ALWAYS" -> printReceipt(orderId, ReceiptKind.FISCAL, setup, openDrawer = cash)
                "ASK" -> {
                    if (cash && drawer != null) openDrawer(drawer)
                    // A device with no receipt printer of its own gets one question: which printer, or don't print.
                    val own = setup.receiptPrinters.filter { it.usable }
                    val choices = if (own.isNotEmpty()) emptyList() else setup.printers.filter { it.usable && it.role != "DOCUMENT" }
                    _question.value = ReceiptQuestion(orderId, ReceiptKind.FISCAL, choices)
                }
                else -> if (cash && drawer != null) openDrawer(drawer)
            }
        }
    }

    /** "Print" chosen in the receipt question, or a printer picked from the list. */
    fun printAnswer(question: ReceiptQuestion, printer: PrintPrinter? = null) {
        _question.value = null
        scope.launch {
            val setup = setup() ?: return@launch
            printReceipt(question.orderId, question.kind, setup, chosen = printer)
        }
    }

    /**
     * Prints [kind] of the order: on [chosen]; else on this till's printer; a device with no printer of its own asks which one.
     */
    suspend fun printReceipt(orderId: String, kind: ReceiptKind, setup: PrintSetup, chosen: PrintPrinter? = null, openDrawer: Boolean = false): Boolean {
        val restaurantId = restaurantId() ?: return false
        val own = setup.receiptPrinters.filter { it.usable }
        val printers = when {
            chosen != null -> listOf(chosen)
            own.isNotEmpty() -> own
            else -> {
                val choices = own.ifEmpty { setup.printers.filter { it.usable && it.role != "DOCUMENT" } }
                if (choices.isEmpty()) _notice.value = PrintNotice(problem = true, text = "There's no printer yet. Add one in Admin Hub → Devices → Printers.")
                else _question.value = ReceiptQuestion(orderId, kind, choices)
                return false
            }
        }
        val data = runCatching { client.get("${root(restaurantId)}/orders/${orderId.encodeURLPathPart()}/receipt").body<ReceiptDto>() }.getOrNull()
        if (data == null) {
            _notice.value = PrintNotice(problem = true, text = "Couldn't load the receipt. Try again.")
            return false
        }
        val look = runCatching { receiptLookOf(client.get("${root(restaurantId)}/receipt-design").body<ReceiptDesignResponseDto>().design) }.getOrNull()
            ?: ReceiptLook()
        val design = look.of(kind)
        // The logo and pictures, fetched before the receipt is put together.
        val urls = listOfNotNull(data.logoUrl) + design.blocks.mapNotNull { it.imageUrl }
        urls.distinct().forEach { url -> if (url !in pictures) pictures[url] = loadPicture(url) }
        val paper = printers.first()
        val bytes = receipt(design, kind, data, paper.paperWidthMm, paper.autoCut, openDrawer && paper.cashDrawerKick, RestaurantTime.zone) { url, width, opacity ->
            pictures[url]?.let { dotsOf(it, width, opacity) }
        }
        var ok = true
        repeat(data.copies.coerceIn(1, 3)) { if (ok) ok = sendFirstThatPrints(printers, bytes) }
        return ok
    }

    private suspend fun loadPicture(url: String): ImageBitmap? = runCatching {
        val full = if (url.startsWith("http")) url else baseUrl().trimEnd('/') + "/" + url.trimStart('/')
        client.get(full).readRawBytes().decodeToImageBitmap()
    }.getOrNull()

    private suspend fun documentTest(printer: PrintPrinter): Boolean {
        val target = when {
            printer.connectionType == "NETWORK" && !printer.ip.isNullOrBlank() -> DocumentTarget.OnNetwork(printer)
            !printer.systemPrinterName.isNullOrBlank() && printer.systemPrinterName in PrinterLink.systemPrinters() -> DocumentTarget.OnComputer(printer.systemPrinterName)
            else -> {
                _notice.value = PrintNotice(problem = true, text = "Print the test page from the computer ${printer.name} is installed on.")
                return false
            }
        }
        val page = documentTestPage(printer.name, session.currentUser.value?.restaurantName)
        val ok = printDocumentOn(target, "Test page", listOf(page)) == com.saporini.mobile_desktop.core.files.PrintOutcome.PRINTED
        _notice.value = if (ok) PrintNotice(problem = false, text = "A test page went to ${printer.name}.")
        else PrintNotice(problem = true, text = "${printer.name} didn't print. Check it's on and connected.")
        return ok
    }

    /** Opens the cash drawer plugged into [printer]. */
    suspend fun openDrawer(printer: PrintPrinter): Boolean = send(printer, EscPos(printer.paperWidthMm).openDrawer().bytes())
}

/** [image] as printer dots, [width] dots wide. */
internal fun dotsOf(image: ImageBitmap, width: Int, opacity: Float): MonoImage {
    val pixels = IntArray(image.width * image.height)
    image.readPixels(pixels)
    return MonoImage.of(pixels, image.width, image.height, width, opacity)
}

/** A printing message for a toast: a problem (red) or that it printed (green). */
data class PrintNotice(val problem: Boolean, val text: String)

/**
 * Where the till's flows tell printing what happened (sending to the kitchen, asking for the bill, a payment), without each
 * screen holding the printing service. Set when the app starts.
 */
object PrintHooks {
    var printing: Printing? = null
    fun kitchenSent(orderId: String) { printing?.kitchenSent(orderId) }
    fun billRequested(orderId: String) { printing?.billRequested(orderId) }
    fun paid(orderId: String, method: String, settled: Boolean) { printing?.paid(orderId, method, settled) }
    fun reprint(ticket: KdsTicket) { printing?.reprint(ticket) }
}
