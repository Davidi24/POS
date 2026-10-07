package com.saporini.mobile_desktop.kds

import com.saporini.mobile_desktop.kds.data.KdsApi
import com.saporini.mobile_desktop.kds.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

class KdsApiTest {
    private val scope = KdsScope("u", "r", "b")
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun boardAndDeviceRequestsUseBranchScopeAndExcludeHistory() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            seen += request.url.encodedPath
            if(request.url.encodedPath.endsWith("/display")) {
                assertEquals("device", request.url.parameters["deviceId"]); respond(json.encodeToString(KdsBoard("s", deviceId = "device")), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                assertEquals("false", request.url.parameters["includeCompleted"]); assertEquals("station", request.url.parameters["stationId"])
                respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { install(ContentNegotiation) { json(json) } }
        try { val api = KdsApi(client) { "http://localhost" }; api.board(scope, "station"); api.display(scope, "device")
            assertEquals(listOf("/restaurants/r/branches/b/kds/board", "/restaurants/r/branches/b/kds/display"), seen)
        } finally { client.close() }
    }
    @Test fun everyTicketAndItemActionHasTheCorrectEndpointAndBody() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            val body = json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("Chef note", body["note"]?.jsonPrimitive?.content); assertEquals("Checked", body["reason"]?.jsonPrimitive?.content)
            val index = requests++; val action = KdsAction.entries[index / 2]
            assertEquals("/restaurants/r/branches/b/kds/tickets/t${if(index % 2 == 1) "/items/i" else ""}/${action.path}", request.url.encodedPath)
            respond(json.encodeToString(kdsTicket()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        try { val api = KdsApi(client) { "http://localhost" }
            KdsAction.entries.forEach { action -> api.action(scope, "t", null, action, KdsActionInput("Chef note", "Checked")); api.action(scope, "t", "i", action, KdsActionInput("Chef note", "Checked")) }
            assertEquals(8, requests)
        } finally { client.close() }
    }
    @Test fun historySendsUtcRangeAndPaginationAndDecodesTotals() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/restaurants/r/branches/b/kds/history", request.url.encodedPath)
            assertEquals("2026-09-27T10:00:00Z", request.url.parameters["from"]); assertEquals("2026-09-28T10:00:00Z", request.url.parameters["to"])
            assertEquals("CANCELLED", request.url.parameters["status"]); assertEquals("2", request.url.parameters["page"]); assertEquals("30", request.url.parameters["size"])
            assertEquals("s", request.url.parameters["stationId"])
            respond(json.encodeToString(KdsTicketPage(listOf(kdsTicket(status = "CANCELLED")), 2, 30, 61, 3, false)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        try { val result = KdsApi(client) { "http://localhost" }.history(scope, KdsHistoryFilter(testNow, Instant.parse("2026-09-28T10:00:00Z"), "CANCELLED"), "s", null, 2)
            assertEquals(61, result.totalElements); assertFalse(result.hasNext); assertEquals("CANCELLED", result.items.single().status)
        } finally { client.close() }
    }
    @Test fun stationUpdateSendsEditableRoutingFieldsAndPreservesFlags() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method); assertEquals("/restaurants/r/branches/b/kds/stations/s", request.url.encodedPath)
            val body = json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertFalse(body.getValue("active").jsonPrimitive.boolean); assertFalse(body.getValue("acceptsScheduledOrders").jsonPrimitive.boolean)
            assertEquals("device", body.getValue("deviceId").jsonPrimitive.content)
            assertFalse(body.getValue("routings").jsonArray.single().jsonObject.containsKey("id"))
            respond(json.encodeToString(KdsStation("s", "r", "b", name = "Grill")), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        try { KdsApi(client) { "http://localhost" }.saveStation(scope, "s", KdsStation("s", "r", "b", name = "Grill", active = false, acceptsScheduledOrders = false, deviceId = "device", routings = listOf(KdsRouting("dish", id = "route"))).toInput()) }
        finally { client.close() }
    }
    @Test fun modifiersAndUnknownStatusesSurviveTheContract() {
        val item = json.decodeFromString<KdsTicketItem>("""{"id":"i","status":"NEW_STATUS","variantNameSnapshot":"Large","optionsPerUnit":false,"modifiers":[{"name":"No peanuts","quantity":2,"notes":"Allergy"}],"futureField":true}""")
        assertEquals("NEW_STATUS", item.status); assertEquals("Large", item.variantNameSnapshot); assertFalse(item.optionsPerUnit)
        assertEquals(2, item.modifiers.single().quantity); assertEquals("Allergy", item.modifiers.single().notes)
        assertTrue(json.decodeFromString<KdsTicketItem>("""{"id":"old"}""").modifiers.isEmpty())
    }
    @Test fun liveStreamUsesKdsPermissionEndpointAndInvalidatesOnConnectAndChange() = kotlinx.coroutines.runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val path = kotlinx.coroutines.CompletableDeferred<String>()
        server.createContext("/") { exchange ->
            path.complete(exchange.requestURI.path)
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write("event: connected\ndata: b\n\nevent: heartbeat\ndata: b\n\nevent: orders-changed\ndata: b\n\n".toByteArray())
                output.flush()
            }
        }
        server.start()
        val client = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(SSE)
            install(io.ktor.client.plugins.HttpTimeout)
        }
        try {
            val events = kotlinx.coroutines.withTimeout(10_000) {
                KdsApi(client) { "http://127.0.0.1:${server.address.port}" }.changes(scope).take(2).toList()
            }
            assertEquals("/restaurants/r/branches/b/kds/events", path.await())
            assertEquals(listOf(KdsLiveEvent.CONNECTED, KdsLiveEvent.CHANGED), events)
        } finally { client.close(); server.stop(0) }
    }
}
