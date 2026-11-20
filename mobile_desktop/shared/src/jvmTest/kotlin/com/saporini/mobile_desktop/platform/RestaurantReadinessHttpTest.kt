@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.platform

import com.saporini.mobile_desktop.auth.data.dto.ApplicantRequest
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationRequest
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.apply.ApplicationStatusScreenModel
import com.saporini.mobile_desktop.auth.ui.apply.ApplyRestaurantScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.network.createHttpClient
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app's real HTTP client against a local server: the paths, query parameters and JSON bodies of the Restaurant
 * checklist's calls, and how refusals reach the screens.
 */
class RestaurantReadinessHttpTest {
    private data class Seen(val method: String, val path: String, val query: Map<String, String>, val body: String)
    private class Answer(val status: Int, val json: String)

    private val seen = mutableListOf<Seen>()
    private var answer: (Seen) -> Answer = { Answer(200, "{}") }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val query = exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate { part ->
                URLDecoder.decode(part.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(part.substringAfter('=', ""), Charsets.UTF_8)
            }
            val request = Seen(exchange.requestMethod, exchange.requestURI.path, query, exchange.requestBody.bufferedReader().use { it.readText() })
            seen += request
            val reply = answer(request)
            val bytes = reply.json.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val client = createHttpClient(SessionManager())
    private val api = PlatformApi(client) { base }
    private val auth = AuthRepository(client, { listOf(base) }, {})

    @BeforeTest fun before() {
        TokenStore.clear()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun after() {
        Dispatchers.resetMain()
        client.close()
        runCatching { server.stop(0) }
    }

    private fun body(index: Int = seen.lastIndex): JsonObject = Json.parseToJsonElement(seen[index].body).jsonObject

    @Test fun branchCardsAskForOnePageWithTheSearchAndReadTheTotals() = runBlocking {
        answer = { Answer(200, """{"items":[{"id":"b1","name":"North","code":"N","status":"ACTIVE","active":true,"city":"Durres","staff":3,"devices":1}],
            "page":1,"size":24,"totalElements":25,"totalPages":2,"hasNext":false,"allBranches":140,"openBranches":120}""") }
        val page = api.branchCards("r", " nor ", 1, 24)
        assertEquals("GET", seen.single().method)
        assertEquals("/restaurants/r/branches/cards", seen.single().path)
        assertEquals(mapOf("search" to "nor", "page" to "1", "size" to "24"), seen.single().query)
        assertEquals(listOf("North"), page.items.map { it.name })
        assertEquals(140, page.allBranches)
        assertEquals(120, page.openBranches)
        assertFalse(page.hasNext)

        api.branchCards("r", "", 0, 2)
        assertEquals(mapOf("page" to "0", "size" to "2"), seen.last().query)
    }

    @Test fun oneBranchIsReadOnItsOwnAndAMissingOneIsA404() = runBlocking {
        answer = { if (it.path.endsWith("/gone")) Answer(404, """{"message":"Branch not found"}""") else Answer(200, """{"id":"b","name":"Main","status":"ACTIVE","isActive":true}""") }
        assertEquals("Main", api.branch("r", "b").name)
        assertEquals("/restaurants/r/branches/b", seen.last().path)
        val missing = assertFailsWith<ApiException> { api.branch("r", "gone") }
        assertEquals(404, missing.status)
    }

    @Test fun aTransferSendsOnlyTheChosenKindOfOwnerAndAlwaysTheVersion() = runBlocking {
        answer = { Answer(200, """{"id":"t","newOwnerId":"u2","newOwnerName":"Maria Manager","previousOwnerDeactivated":true,"sessionsRevoked":2}""") }
        val done = api.transferOwnership("r", OwnerTransferRequestDto(newOwnerUserId = "u2", expectedVersion = 4, reason = "Sold"))
        assertEquals("POST", seen.last().method)
        assertEquals("/restaurants/r/owner/transfer", seen.last().path)
        assertEquals(setOf("newOwnerUserId", "expectedVersion", "reason"), body().keys)
        assertEquals("4", body()["expectedVersion"]!!.jsonPrimitive.content)
        assertEquals(2, done.sessionsRevoked)

        api.transferOwnership("r", OwnerTransferRequestDto(newOwner = OwnerInviteDto("n@new.example", "nina", "Nina", "New"), expectedVersion = 0))
        assertEquals(setOf("newOwner", "expectedVersion"), body().keys)
        assertEquals(setOf("email", "username", "firstName", "lastName"), body()["newOwner"]!!.jsonObject.keys)
        assertEquals("0", body()["expectedVersion"]!!.jsonPrimitive.content)
    }

    @Test fun aRefusedTransferReachesTheScreenWithTheServersReason() = runBlocking {
        answer = { Answer(409, """{"status":409,"message":"The restaurant changed since you opened it. Review the owner again before transferring."}""") }
        val refused = assertFailsWith<ApiException> { api.transferOwnership("r", OwnerTransferRequestDto(newOwnerUserId = "u", expectedVersion = 1)) }
        assertEquals(409, refused.status)
        assertTrue(refused.message.contains("Review the owner again"))
    }

    @Test fun candidatesAndHistoryAreReadFromTheOwnerEndpoints() = runBlocking {
        answer = {
            if (it.path.endsWith("/candidates")) Answer(200, """[{"id":"u","firstName":"Maria","lastName":"Manager","email":"m@x.example","username":"maria","roles":["MANAGER"]}]""")
            else Answer(200, """[{"id":"t","previousOwnerName":"Olga Owner","newOwnerId":"u","newOwnerName":"Maria Manager","transferredAt":"2026-10-24T10:00:00Z"}]""")
        }
        assertEquals("Maria Manager", api.ownerCandidates("r", " mar ").single().displayName)
        assertEquals(mapOf("search" to "mar"), seen.last().query)
        api.ownerCandidates("r", "")
        assertEquals(emptyMap(), seen.last().query)
        assertEquals("Olga Owner", api.ownerTransfers("r").single().previousOwnerName)
        assertEquals("/restaurants/r/owner/transfers", seen.last().path)
    }

    @Test fun theReviewQueueAndDecisionsUseTheirOwnPaths() = runBlocking {
        answer = {
            if (it.method == "GET") Answer(200, """{"items":[{"id":"a","name":"Bistro","status":"PENDING","owner":{"firstName":"Ana","email":"ana@x.example"}}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}""")
            else Answer(200, """{"id":"a","name":"Bistro","status":"REJECTED","rejectionReason":"No licence"}""")
        }
        val page = api.applications("PENDING", " bis ", 0, 20)
        assertEquals("/platform/restaurant-applications", seen.last().path)
        assertEquals(mapOf("status" to "PENDING", "search" to "bis", "page" to "0", "size" to "20"), seen.last().query)
        assertEquals("Ana", page.items.single().owner?.displayName)

        api.reviewApplication("a", ReviewApplicationRequestDto("REJECT", "No licence"))
        assertEquals("PATCH", seen.last().method)
        assertEquals("/restaurants/registrations/a/review", seen.last().path)
        assertEquals("No licence", body()["rejectionReason"]!!.jsonPrimitive.content)
        api.reviewApplication("a", ReviewApplicationRequestDto("APPROVE"))
        assertEquals(setOf("decision"), body().keys)
    }

    @Test fun anApplicantAppliesAndChecksTheStatusWithoutSigningIn() = runBlocking {
        answer = {
            if (it.path.endsWith("/status")) Answer(200, """{"id":"a","name":"Bistro","status":"REJECTED","rejectionReason":"No licence","message":"Not approved"}""")
            else Answer(201, """{"id":"a","name":"Bistro","status":"PENDING","statusCode":"7KQ2M-X9D4T-PW3HB-R8N6C","ownerEmail":"ana@x.example"}""")
        }
        val receipt = auth.applyForRestaurant(RestaurantApplicationRequest("Bistro", "Bistro LLC", "hi@bistro.example", "+355691234567", "EUR",
            "Europe/Tirane", ApplicantRequest("ana@x.example", "ana", "Ana", "Owner")))
        assertEquals("/restaurants/registrations", seen.last().path)
        assertEquals("hi@bistro.example", body()["email"]!!.jsonPrimitive.content)
        assertEquals("+355691234567", body()["phone"]!!.jsonPrimitive.content)
        assertEquals("ana", body()["owner"]!!.jsonObject["username"]!!.jsonPrimitive.content)
        assertNull(seen.last().query["code"])
        assertEquals("7KQ2M-X9D4T-PW3HB-R8N6C", receipt.statusCode)

        val status = auth.applicationStatus(" 7KQ2M-X9D4T-PW3HB-R8N6C ")
        assertEquals("/restaurants/registrations/status", seen.last().path)
        // The code goes in the body, never in the address.
        assertTrue(seen.last().query.isEmpty())
        assertEquals("7KQ2M-X9D4T-PW3HB-R8N6C", body()["code"]!!.jsonPrimitive.content)
        assertEquals("No licence", status.rejectionReason)
    }

    @Test fun theApplyScreenSendsAValidApplicationAndShowsTheLimitMessage() {
        val model = ApplyRestaurantScreenModel(auth)
        model.send()
        assertTrue(model.state.value.draft.showProblems)
        assertTrue(seen.isEmpty())

        model.edit {
            it.copy(name = "Bistro", email = "hi@bistro.example", phone = "+355691234567", firstName = "Ana", lastName = "Owner", ownerEmail = "ana.owner@x.example")
        }
        assertEquals("Bistro", model.state.value.draft.legalName)
        assertEquals("ana.owner", model.state.value.draft.username)

        answer = { Answer(429, """{"status":429,"message":"Too many applications were sent from here. Try again later."}""") }
        model.send()
        waitFor { !model.state.value.sending }
        assertEquals("Too many applications were sent from here. Try again later.", model.state.value.error)
        assertNull(model.state.value.receipt)

        answer = { Answer(201, """{"id":"a","name":"Bistro","statusCode":"7KQ2M-X9D4T-PW3HB-R8N6C","ownerEmail":"ana.owner@x.example"}""") }
        model.send()
        waitFor { model.state.value.receipt != null }
        assertEquals("7KQ2M-X9D4T-PW3HB-R8N6C", model.state.value.receipt?.statusCode)
        // Sent once it's received: a second press does nothing.
        val requests = seen.size
        model.send()
        assertEquals(requests, seen.size)
    }

    @Test fun theStatusScreenChecksTheCodeFormatFirstAndExplainsAnUnknownCode() {
        val model = ApplicationStatusScreenModel(auth)
        model.changeCode("12345")
        model.check()
        assertNotNull(model.state.value.error)
        assertTrue(seen.isEmpty())

        answer = { Answer(404, """{"status":404,"message":"No application has this code."}""") }
        model.changeCode("7KQ2M-X9D4T-PW3HB-R8N6C")
        model.check()
        waitFor { !model.state.value.checking }
        assertTrue(model.state.value.error.orEmpty().startsWith("No application has this code"))

        answer = { Answer(200, """{"id":"a","name":"Bistro","status":"APPROVED","message":"Check your email"}""") }
        model.check()
        waitFor { model.state.value.view != null }
        assertEquals("APPROVED", model.state.value.view?.status)
        assertNull(model.state.value.error)
    }

    @Test fun anUnreachableServerGivesTheConnectionMessage() {
        server.stop(0)
        val model = ApplicationStatusScreenModel(AuthRepository(client, { listOf("http://127.0.0.1:1") }, {}))
        model.changeCode("7KQ2M-X9D4T-PW3HB-R8N6C")
        model.check()
        waitFor { !model.state.value.checking }
        assertTrue(model.state.value.error.orEmpty().contains("Couldn't reach the server"), model.state.value.error)
    }

    // The screen models run on the real client; wait (briefly) for the answer to come back.
    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue(condition(), "timed out")
    }
}
