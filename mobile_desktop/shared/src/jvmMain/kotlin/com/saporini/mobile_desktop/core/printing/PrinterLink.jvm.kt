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

    // Linux: `bluetoothctl devices Paired` ("Device AA:BB:CC:DD:EE:FF Name"); macOS and Windows: none (pair it on the tablet).
    actual fun pairedDevices(): List<BluetoothPrinter> = runCatching {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return emptyList()
        val process = ProcessBuilder("bluetoothctl", "devices", "Paired").redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        lines.mapNotNull { line ->
            val parts = line.trim().split(" ", limit = 3)
            if (parts.size >= 2 && parts[0] == "Device") BluetoothPrinter(parts.getOrNull(2)?.trim().orEmpty().ifEmpty { parts[1] }, parts[1]) else null
        }.distinctBy { it.address }
    }.getOrDefault(emptyList())

    // Bluetooth through `bluetoothctl` on Linux (BlueZ); macOS and Windows pair in their own settings.
    private val linux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    private fun ctl(vararg args: String, seconds: Long = 15): List<String>? = runCatching {
        val process = ProcessBuilder(listOf("bluetoothctl") + args).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)
        lines
    }.getOrNull()

    private fun devicesOf(lines: List<String>?): List<Pair<String, String>> = lines.orEmpty().mapNotNull { line ->
        val parts = line.trim().split(" ", limit = 3)
        if (parts.size >= 2 && parts[0] == "Device") parts[1] to parts.getOrNull(2)?.trim().orEmpty() else null
    }

    actual val canScanBluetooth: Boolean by lazy { linux && ctl("--version", seconds = 3) != null }

    actual suspend fun bluetoothPower(): BluetoothPower = withContext(Dispatchers.IO) {
        if (!canScanBluetooth) return@withContext BluetoothPower.NONE
        val show = ctl("show", seconds = 5).orEmpty()
        when {
            show.isEmpty() || show.any { it.contains("No default controller") } -> BluetoothPower.NONE
            show.any { it.trim() == "Powered: yes" } -> BluetoothPower.ON
            else -> BluetoothPower.OFF
        }
    }

    actual suspend fun turnOnBluetooth(): Boolean = withContext(Dispatchers.IO) {
        runCatching { ProcessBuilder("rfkill", "unblock", "bluetooth").redirectErrorStream(true).start().waitFor(3, java.util.concurrent.TimeUnit.SECONDS) }
        ctl("power", "on", seconds = 8)
        bluetoothPower() == BluetoothPower.ON
    }

    actual suspend fun scanBluetooth(seconds: Int): List<NearbyDevice> = withContext(Dispatchers.IO) {
        if (!canScanBluetooth) return@withContext emptyList()
        ctl("--timeout", seconds.toString(), "scan", "on", seconds = seconds + 5L)
        val paired = devicesOf(ctl("devices", "Paired", seconds = 5)).map { it.first }.toSet()
        devicesOf(ctl("devices", seconds = 5))
            // Devices without a name show their address with dashes: nothing to recognise them by.
            .filter { (address, name) -> name.isNotBlank() && name.replace('-', ':') != address }
            .map { (address, name) -> NearbyDevice(name, address, address in paired) }
            .distinctBy { it.address }
            .sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))
    }

    actual suspend fun pairBluetooth(address: String): String? = withContext(Dispatchers.IO) {
        if (address in devicesOf(ctl("devices", "Paired", seconds = 5)).map { it.first }) return@withContext null
        val out = ctl("--timeout", "25", "pair", address, seconds = 30).orEmpty().joinToString("\n")
        val paired = out.contains("Pairing successful") || out.contains("AlreadyExists") ||
            address in devicesOf(ctl("devices", "Paired", seconds = 5)).map { it.first }
        if (!paired) return@withContext "Couldn't pair with it. Put it in pairing mode, keep it close and try again."
        ctl("trust", address, seconds = 5)
        null
    }

    // Serial ports: Linux /dev/serial/by-id (named) or ttyUSB/ttyACM, macOS /dev/cu.usb*, Windows the registry's SERIALCOMM.
    actual fun cablePorts(): List<BluetoothPrinter> = runCatching {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            os.contains("win") -> {
                val process = ProcessBuilder("reg", "query", "HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM").redirectErrorStream(true).start()
                val lines = process.inputStream.bufferedReader().readLines()
                process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                lines.mapNotNull { line ->
                    val cells = line.trim().split(Regex("\\s+"))
                    if (cells.size >= 3 && cells[1] == "REG_SZ") BluetoothPrinter("${cells.last()} · ${cells.first().substringAfterLast('\\')}", cells.last()) else null
                }
            }
            os.contains("mac") -> java.io.File("/dev").listFiles().orEmpty().filter { it.name.startsWith("cu.usb") }
                .map { BluetoothPrinter(it.name.removePrefix("cu."), it.absolutePath) }
            else -> {
                val named = java.io.File("/dev/serial/by-id").listFiles().orEmpty()
                    .map { BluetoothPrinter(it.name.removePrefix("usb-").substringBeforeLast("-if").replace('_', ' '), it.canonicalPath) }
                named.ifEmpty {
                    java.io.File("/dev").listFiles().orEmpty().filter { it.name.startsWith("ttyUSB") || it.name.startsWith("ttyACM") }
                        .map { BluetoothPrinter(it.name, it.absolutePath) }
                }
            }
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
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
