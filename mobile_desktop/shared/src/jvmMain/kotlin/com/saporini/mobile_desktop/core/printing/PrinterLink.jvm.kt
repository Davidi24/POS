package com.saporini.mobile_desktop.core.printing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

actual object PrinterLink {

    actual suspend fun sendNetwork(host: String, port: Int, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 4_000)
            socket.soTimeout = 8_000
            socket.getOutputStream().apply { write(bytes); flush() }
        }
        Unit
    }

    actual suspend fun sendSystem(name: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val service = PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == name }
            ?: error("The printer $name isn't on this computer.")
        // Raw bytes (ESC/POS) straight to the printer, as receipt printers expect.
        service.createPrintJob().print(SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null)
    }

    actual fun systemPrinters(): List<String> =
        runCatching { PrintServiceLookup.lookupPrintServices(null, null).map { it.name }.sorted() }.getOrDefault(emptyList())

    actual suspend fun findOnNetwork(): List<String> = withContext(Dispatchers.IO) { scan() }

    // A computer's Bluetooth printers are paired in its own settings and then print as printers on this computer.
    actual fun bluetoothPrinters(): List<BluetoothPrinter> = emptyList()

    actual suspend fun sendBluetooth(address: String, bytes: ByteArray): Unit =
        error("On a computer, pair the printer in its settings: it then shows as a printer on this computer.")

    actual suspend fun reachNetwork(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 2_500) }; true }.getOrDefault(false)
    }

    actual fun systemPrinterProblem(name: String): String? {
        val service = runCatching { PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == name } }.getOrNull()
            ?: return "$name isn't on this computer any more."
        val accepting = runCatching { service.getAttribute(javax.print.attribute.standard.PrinterIsAcceptingJobs::class.java) }.getOrNull()
        return if (accepting == javax.print.attribute.standard.PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) "$name isn't taking prints." else null
    }

    actual fun bluetoothProblem(address: String): String? = "Bluetooth printers print from the phone or tablet they're paired with."
}

/** Every address of this device's own /24 networks, tried on port 9100 at once (a printer answers within a moment). */
internal suspend fun scan(): List<String> = coroutineScope {
    val prefixes = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress.substringBeforeLast('.') }
            .distinct()
    }.getOrDefault(emptyList())
    prefixes.flatMap { prefix ->
        (1..254).map { host ->
            async(Dispatchers.IO) {
                val address = "$prefix.$host"
                runCatching { Socket().use { it.connect(InetSocketAddress(address, 9100), 600) }; address }.getOrNull()
            }
        }
    }.awaitAll().filterNotNull()
}
