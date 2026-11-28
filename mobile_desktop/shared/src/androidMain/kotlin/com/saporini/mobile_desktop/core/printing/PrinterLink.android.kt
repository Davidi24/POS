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

    actual fun pairedDevices(): List<BluetoothPrinter> = bluetoothPrinters()

    actual val canScanBluetooth: Boolean = true

    @Suppress("DEPRECATION")
    private fun adapter(): android.bluetooth.BluetoothAdapter? = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

    actual suspend fun bluetoothPower(): BluetoothPower {
        val adapter = adapter() ?: return BluetoothPower.NONE
        return if (adapter.isEnabled) BluetoothPower.ON else BluetoothPower.OFF
    }

    // A phone or tablet asks the person to switch it on (the screen does that).
    actual suspend fun turnOnBluetooth(): Boolean = false

    @android.annotation.SuppressLint("MissingPermission")
    actual suspend fun scanBluetooth(seconds: Int): List<NearbyDevice> {
        val adapter = adapter() ?: return emptyList()
        val context = com.saporini.mobile_desktop.core.session.TokenPersistence.appContext
        val found = java.util.concurrent.ConcurrentHashMap<String, NearbyDevice>()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: android.content.Intent) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE) ?: return
                val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
                found[device.address] = NearbyDevice(name, device.address, device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED)
            }
        }
        return try {
            adapter.bondedDevices.orEmpty().forEach { found[it.address] = NearbyDevice(it.name ?: it.address, it.address, true) }
            context.registerReceiver(receiver, android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND))
            adapter.startDiscovery()
            kotlinx.coroutines.delay(seconds * 1000L)
            found.values.sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))
        } catch (e: SecurityException) {
            emptyList()
        } finally {
            runCatching { adapter.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    actual suspend fun pairBluetooth(address: String): String? = try {
        val device = adapter()?.getRemoteDevice(address) ?: return "This device has no Bluetooth."
        if (device.bondState != android.bluetooth.BluetoothDevice.BOND_BONDED) {
            device.createBond()
            var waited = 0
            while (device.bondState != android.bluetooth.BluetoothDevice.BOND_BONDED && waited < 30_000) {
                kotlinx.coroutines.delay(500)
                waited += 500
            }
        }
        if (device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) null
        else "Couldn't pair with it. Put it in pairing mode, keep it close and try again."
    } catch (e: SecurityException) {
        "Let this device use Bluetooth first."
    }

    // USB devices plugged into this tablet (a card terminal by cable), by their names.
    actual fun cablePorts(): List<BluetoothPrinter> = runCatching {
        val usb = com.saporini.mobile_desktop.core.session.TokenPersistence.appContext.getSystemService(android.content.Context.USB_SERVICE)
            as android.hardware.usb.UsbManager
        usb.deviceList.values.map { device ->
            val name = listOfNotNull(device.manufacturerName, device.productName).joinToString(" ").ifBlank { device.deviceName }
            BluetoothPrinter(name, device.deviceName)
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
}
