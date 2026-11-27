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

actual object PrinterLink {

    actual suspend fun sendNetwork(host: String, port: Int, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 4_000)
            socket.soTimeout = 8_000
            socket.getOutputStream().apply { write(bytes); flush() }
        }
        Unit
    }

    // A phone or tablet has no installed printers to send to: its printers are network ones.
    actual suspend fun sendSystem(name: String, bytes: ByteArray): Unit = error("Printers installed on a computer print only from that computer.")

    actual fun systemPrinters(): List<String> = emptyList()

    actual suspend fun findOnNetwork(): List<String> = withContext(Dispatchers.IO) {
        coroutineScope {
            val prefixes = runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
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
    }

    @android.annotation.SuppressLint("MissingPermission")
    actual fun bluetoothPrinters(): List<BluetoothPrinter> = runCatching {
        @Suppress("DEPRECATION")
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        adapter.bondedDevices.orEmpty().map { BluetoothPrinter(it.name ?: it.address, it.address) }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    @android.annotation.SuppressLint("MissingPermission")
    actual suspend fun sendBluetooth(address: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: error("This device has no Bluetooth.")
        val device = adapter.getRemoteDevice(address)
        // The serial port profile receipt printers speak.
        val socket = device.createRfcommSocketToServiceRecord(java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        runCatching { adapter.cancelDiscovery() }
        socket.use {
            it.connect()
            it.outputStream.apply { write(bytes); flush() }
            // Give a slow printer a moment to take everything before the link closes.
            kotlinx.coroutines.delay(400)
        }
        Unit
    }

    actual suspend fun reachNetwork(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 2_500) }; true }.getOrDefault(false)
    }

    actual fun systemPrinterProblem(name: String): String? = "Printers installed on a computer print only from that computer."

    @android.annotation.SuppressLint("MissingPermission")
    actual fun bluetoothProblem(address: String): String? = try {
        @Suppress("DEPRECATION")
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        when {
            adapter == null -> "This device has no Bluetooth."
            !adapter.isEnabled -> "Bluetooth is off on this device."
            adapter.bondedDevices.orEmpty().none { it.address.equals(address, ignoreCase = true) } -> "It isn't paired with this device any more."
            else -> null
        }
    } catch (e: SecurityException) {
        "Allow “Nearby devices” for the app on this device."
    }
}
