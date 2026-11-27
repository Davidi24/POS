package com.saporini.mobile_desktop.core.printing

/**
 * How the app reaches receipt and kitchen printers (user, 2026-11-27). The till prints itself, since the server is on the
 * internet and can't reach printers inside the restaurant: over the network (most kitchen and receipt printers listen on port
 * 9100) or, on a computer, through a printer installed there (USB).
 */
expect object PrinterLink {
    /** Sends [bytes] to a network printer; throws when it can't be reached. */
    suspend fun sendNetwork(host: String, port: Int, bytes: ByteArray)

    /** Sends [bytes] as they are to a printer installed on this computer, by its name; throws when it isn't there. */
    suspend fun sendSystem(name: String, bytes: ByteArray)

    /** The printers installed on this computer (none on a phone or tablet). */
    fun systemPrinters(): List<String>

    /** Printers answering on port 9100 in this device's network, as addresses ("192.168.1.50"). */
    suspend fun findOnNetwork(): List<String>

    /** Bluetooth printers paired with this phone or tablet (none on a computer: there they're in its printer list). */
    fun bluetoothPrinters(): List<BluetoothPrinter>

    /** Sends [bytes] to a paired Bluetooth printer by its address ("AA:BB:CC:DD:EE:FF"). */
    suspend fun sendBluetooth(address: String, bytes: ByteArray)

    /** Whether a network printer answers on [port] (it's opened and closed, nothing is printed). */
    suspend fun reachNetwork(host: String, port: Int): Boolean

    /** Why the printer installed here as [name] can't print now, or null when it can. */
    fun systemPrinterProblem(name: String): String?

    /** Why the paired Bluetooth printer at [address] can't print from here now, or null when it can. */
    fun bluetoothProblem(address: String): String?
}

/** A paired Bluetooth device: its name and address. */
data class BluetoothPrinter(val name: String, val address: String)
