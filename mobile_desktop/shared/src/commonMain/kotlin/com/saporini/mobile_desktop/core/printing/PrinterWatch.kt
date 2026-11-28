package com.saporini.mobile_desktop.core.printing

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.TillConnection
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Whether the printers answer (user, 2026-11-27: "build it"): a till or tablet with the app open checks, once a minute, the
 * restaurant's network printers and the ones plugged into or paired with it, and tells the server, so Devices shows a printer
 * that's off as Offline before the next order. Nothing is printed. Its own client with the device's secret, like the heartbeat,
 * so it goes on while the till is locked.
 */
object PrinterWatch {

    @Serializable
    internal data class ToCheck(val id: String, val name: String = "", val role: String? = null, val connectionType: String? = null,
                               val ip: String? = null, val port: Int? = null, val systemPrinterName: String? = null)

    @Serializable
    internal data class Check(val printerId: String, val reachable: Boolean, val problem: String? = null)

    @Serializable
    private data class Checks(val checks: List<Check>)

    private const val EVERY_MS = 60_000L

    private val client by lazy {
        HttpClient {
            install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 15_000; socketTimeoutMillis = 15_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            expectSuccess = true
        }
    }

    /** Checks [till]'s printers every minute while it's this device's connection (the caller's loop is cancelled otherwise). */
    suspend fun run(till: TillConnection) {
        val base = "${ApiConfig.BASE_URL.trimEnd('/')}/public/devices/${till.deviceId.encodeURLPathPart()}/printer-checks"
        while (true) {
            runCatching {
                val printers = client.get(base) { header("X-Device-Secret", till.secret) }.body<List<ToCheck>>()
                if (printers.isNotEmpty()) {
                    val checks = coroutineScope { printers.map { printer -> async { check(printer) } }.awaitAll() }
                    client.post(base) {
                        header("X-Device-Secret", till.secret)
                        contentType(ContentType.Application.Json)
                        setBody(Checks(checks))
                    }
                }
            }
            delay(EVERY_MS)
        }
    }

    internal suspend fun check(printer: ToCheck): Check {
        val problem: String? = when {
            printer.connectionType == "NETWORK" && !printer.ip.isNullOrBlank() -> {
                val port = printer.port ?: 9100
                // An office printer may only take prints on IPP's port.
                val answers = PrinterLink.reachNetwork(printer.ip, port) || (printer.role == "DOCUMENT" && port != 631 && PrinterLink.reachNetwork(printer.ip, 631))
                if (answers) null else "No answer at ${printer.ip}"
            }
            printer.connectionType == "BLUETOOTH" && !printer.systemPrinterName.isNullOrBlank() -> PrinterLink.bluetoothProblem(printer.systemPrinterName)
            !printer.systemPrinterName.isNullOrBlank() -> PrinterLink.systemPrinterProblem(printer.systemPrinterName)
            else -> "It has no address yet."
        }
        return Check(printer.id, problem == null, problem)
    }
}
