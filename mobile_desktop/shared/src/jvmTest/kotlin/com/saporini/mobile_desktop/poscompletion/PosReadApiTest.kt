package com.saporini.mobile_desktop.poscompletion
import com.saporini.mobile_desktop.pos.history.*
import com.saporini.mobile_desktop.pos.sales.*
import com.saporini.mobile_desktop.pos.orders.data.api.OrderApi
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderStatus
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*
class PosReadApiTest {
    private val json=Json { ignoreUnknownKeys=true }
    private val scope=OrdersScope("u","r","b")
    @Test fun historyPassesPaginationAndEveryFilter()=runTest {
        val client=HttpClient(MockEngine { r ->
            assertEquals("/restaurants/r/branches/b/orders/history/page",r.url.encodedPath)
            assertEquals("2",r.url.parameters["page"]);assertEquals("40",r.url.parameters["size"]);assertEquals("CLOSED",r.url.parameters["status"])
            assertEquals("staff",r.url.parameters["staffId"]);assertEquals("customer",r.url.parameters["customerId"]);assertEquals("100% soup",r.url.parameters["search"])
            respond("""{"items":[],"page":2,"size":40,"totalElements":80,"hasNext":false}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }) { install(ContentNegotiation){json(json)} }
        try { val repo=ApiOrderHistoryRepository(client,OrderApi(client)){"http://localhost"};assertEquals(80L,repo.page(scope,HistoryFilter(status=OrderStatus.CLOSED,staffId="staff",customerId="customer",search="100% soup"),2,40).totalElements) }finally{client.close()}
    }
    @Test fun salesPassesDateAndShiftAndKeepsExactDecimal()=runTest {
        val client=HttpClient(MockEngine { r ->
            assertEquals("/restaurants/r/branches/b/sales/mine",r.url.encodedPath);assertEquals("2026-09-25",r.url.parameters["date"]);assertEquals("shift",r.url.parameters["shiftId"])
            respond(json.encodeToString(salesReport(scope,SalesFilter(shiftId="shift")).copy(currencies=listOf(SalesTotals("EUR",sales=com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal("9007199254740993.25"))))),headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }) {install(ContentNegotiation){json(json)}}
        try{val repo=ApiMySalesRepository(client,OrderApi(client)){"http://localhost"};assertEquals("9007199254740993.25",repo.report(scope,SalesFilter(LocalDate(2026,9,25),shiftId="shift")).currencies.single().sales.value)}finally{client.close()}
    }
    @Test fun orderEventsActuallyCrossKtorSseCoroutineBoundary()=runBlocking {
        val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/restaurants/r/branches/b/orders/events") { exchange ->
            exchange.responseHeaders.add("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0)
            exchange.responseBody.use{it.write("event: connected\ndata: {}\n\nevent: orders-changed\ndata: {}\n\n".toByteArray());it.flush()}
        };server.start()
        val client=HttpClient(io.ktor.client.engine.okhttp.OkHttp){install(SSE);install(io.ktor.client.plugins.HttpTimeout)}
        try{val result=withTimeout(10_000){OrderApi(client){"http://127.0.0.1:${server.address.port}"}.observeOrderChanges("r","b").take(2).toList()};assertEquals(2,result.size)}finally{client.close();server.stop(0)}
    }
}
