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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A till or tablet with a printer plugged in (USB) or paired (Bluetooth) prints for the other devices (user, 2026-11-27): they
 * leave their tickets on the server, and this device looks for them every few seconds, signed in or not (its own connection
 * proves it), prints them and says how it went. Its own client, without the sign-in, like the heartbeat.
 */
object PrintRelay {

    @Serializable
    private data class HostJob(val id: String, val printerId: String, val connectionType: String? = null, val systemPrinterName: String? = null,
                               val data: String)

    @Serializable
    private data class HostResult(val ok: Boolean, val problem: String? = null)

    private val client by lazy {
        HttpClient {
            install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 10_000; socketTimeoutMillis = 10_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            expectSuccess = true
        }
    }

    /** Looks for prints for [till] while it's this device's connection (the caller's loop is cancelled otherwise). */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun run(till: TillConnection) {
        val base = "${ApiConfig.BASE_URL.trimEnd('/')}/public/devices/${till.deviceId.encodeURLPathPart()}/print-queue"
        while (true) {
            val jobs = runCatching { client.get(base) { header("X-Device-Secret", till.secret) }.body<List<HostJob>>() }.getOrDefault(emptyList())
            for (job in jobs) {
                val problem = runCatching {
                    val bytes = Base64.decode(job.data)
                    val target = job.systemPrinterName ?: error("No printer name")
                    if (job.connectionType == "BLUETOOTH") PrinterLink.sendBluetooth(target, bytes) else PrinterLink.sendSystem(target, bytes)
                }.exceptionOrNull()?.let { "It didn't print here. Check the printer is on and plugged in." }
                runCatching {
                    client.post("$base/${job.id.encodeURLPathPart()}") {
                        header("X-Device-Secret", till.secret)
                        contentType(ContentType.Application.Json)
                        setBody(HostResult(problem == null, problem))
                    }
                }
            }
            delay(if (jobs.isEmpty()) 3_000 else 300)
        }
    }
}
