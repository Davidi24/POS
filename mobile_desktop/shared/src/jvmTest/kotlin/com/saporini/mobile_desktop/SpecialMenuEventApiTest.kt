package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.pos.menu.data.api.MenuApi
import com.saporini.mobile_desktop.pos.menu.data.repository.DefaultMenuRepository
import com.saporini.mobile_desktop.pos.menu.domain.model.MenuEventNightInput
import com.saporini.mobile_desktop.pos.menu.domain.model.SpecialMenuKind
import com.saporini.mobile_desktop.pos.menu.domain.repository.UpdateMenuInput
import com.saporini.mobile_desktop.pos.menu.ui.menu.eventDaysText
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A special menu is booking extras (no event) or one event night's menu; the app sends and reads that link.
class SpecialMenuEventApiTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun client(handler: suspend (HttpRequestData) -> String) = HttpClient(MockEngine { request ->
        respond(handler(request), headers = headers)
    }) { install(ContentNegotiation) { json(this@SpecialMenuEventApiTest.json) } }

    private val eventMenuResponse = """{"id":"menu-1","code":"VALENTINES","name":"Valentine's","active":true,"special":true,
        "availableFromDate":"2027-02-14","availableUntilDate":"2027-02-14",
        "event":{"id":"event-1","name":"Valentine's","icon":"❤️","startDate":"2027-02-14","endDate":"2027-02-14","active":true,"specialMenuOnly":true}}"""

    @Test
    fun anEventMenuSendsItsEventAndReadsItBack() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/menus/menu-1", request.url.encodedPath)
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"special\":true"), body)
            assertTrue(body.contains("\"specialKind\":\"EVENT\""), body)
            assertTrue(body.contains("\"eventId\":\"event-1\""), body)
            eventMenuResponse
        }
        try {
            val menu = DefaultMenuRepository(MenuApi(client) { "http://localhost" }).updateMenu(
                menuId = "menu-1",
                input = UpdateMenuInput(
                    name = "Valentine's", active = true, displayOrder = 0,
                    special = true, specialKind = SpecialMenuKind.EVENT, eventId = "event-1"
                )
            )
            assertEquals("event-1", menu.event?.id)
            assertEquals("❤️", menu.event?.icon)
            assertEquals(SpecialMenuKind.EVENT, menu.specialKind)
            assertFalse(menu.isBookingExtras)
        } finally {
            client.close()
        }
    }

    @Test
    fun aSpecialMenuWithoutEventIsBookingExtras() = runTest {
        val client = client { """{"id":"menu-2","code":"EXTRAS","name":"Extras","active":true,"special":true}""" }
        try {
            val menu = DefaultMenuRepository(MenuApi(client) { "http://localhost" }).getMenu("menu-2")
            assertNull(menu.event)
            assertTrue(menu.isBookingExtras)
            assertEquals(SpecialMenuKind.BOOKING_EXTRAS, menu.specialKind)
        } finally {
            client.close()
        }
    }

    @Test
    fun eventNightsSayWhichMenuTheyAlreadyHave() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/restaurants/restaurant-1/events", request.url.encodedPath)
            """[{"id":"event-1","name":"Valentine's","icon":"❤️","startDate":"2027-02-14","endDate":"2027-02-14","menuId":"menu-1","menuName":"Valentine's","active":true},
                {"id":"event-2","name":"Carnival","icon":"🎭","startDate":"2027-03-01","endDate":"2027-03-02"}]"""
        }
        try {
            val events = DefaultMenuRepository(MenuApi(client) { "http://localhost" }).getEventNights("restaurant-1")
            assertEquals(listOf("event-1", "event-2"), events.map { it.id })
            assertEquals("menu-1", events[0].menuId)
            assertNull(events[1].menuId)
            assertTrue(events[1].active)
        } finally {
            client.close()
        }
    }

    @Test
    fun addingAnEventFromTheMenuEditorSendsEveryField() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/restaurants/restaurant-1/events", request.url.encodedPath)
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"name\":\"Valentine's\""), body)
            assertTrue(body.contains("\"startDate\":\"2027-02-14\""), body)
            assertTrue(body.contains("\"endDate\":\"2027-02-14\""), body)
            // Defaults are left out of the app's JSON, so these must be sent on purpose.
            assertTrue(body.contains("\"specialMenuOnly\":false"), body)
            assertTrue(body.contains("\"active\":true"), body)
            """{"id":"event-9","name":"Valentine's","icon":"❤️","startDate":"2027-02-14","endDate":"2027-02-14","specialMenuOnly":false,"active":true}"""
        }
        try {
            val event = DefaultMenuRepository(MenuApi(client) { "http://localhost" }).createEventNight(
                "restaurant-1",
                MenuEventNightInput(name = "Valentine's", icon = "❤️", startDate = "2027-02-14", endDate = "2027-02-14")
            )
            assertEquals("event-9", event.id)
            assertNull(event.menuId)
        } finally {
            client.close()
        }
    }

    @Test
    fun eventDaysReadNaturally() {
        assertEquals("14 Feb 2027", eventDaysText("2027-02-14", "2027-02-14"))
        assertEquals("1 Mar – 2 Mar 2027", eventDaysText("2027-03-01", "2027-03-02"))
    }
}
