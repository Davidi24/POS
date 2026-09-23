package com.saporini.mobile_desktop.orders

import com.saporini.mobile_desktop.pos.orders.data.api.OrderCatalogApi
import com.saporini.mobile_desktop.pos.orders.data.repository.DefaultOrderCatalogRepository
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OrderCatalogTest {
    @Test fun fetchesRealOptionChoicesAndPreservesUnboundedLimits() = runTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            assertEquals("true", request.url.parameters[if (paths.size == 1) "includeOptionGroups" else "includeItems"])
            respond(if (paths.size == 1) menu else group, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(orderJson) } }
        try {
            val choices = DefaultOrderCatalogRepository(OrderCatalogApi(client) { "http://localhost" }).getItemChoices("restaurant-1", "menu-1", "item-1")
            assertEquals(listOf("/menus/menu-1", "/option-groups/group-1"), paths)
            assertNull(choices.groups.single().maximum)
            assertEquals(1, choices.groups.single().minimum)
            assertEquals("option-1", choices.groups.single().availableChoices.single().id)
            assertFailsWith<IllegalArgumentException> { choices.toLineItemInput(1) }
            assertFailsWith<IllegalArgumentException> { choices.toLineItemInput(1, variantId = "unknown") }
            val input = choices.toLineItemInput(2, options = listOf(CreateOrderItemOptionInput("option-1")))
            assertEquals(2, input.quantity)
            assertEquals("item-1", input.menuItemId)
        } finally { client.close() }
    }

    @Test fun refusesMenuFromAnotherRestaurant() = runTest {
        val client = HttpClient(MockEngine { respond(menu, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json(orderJson) }
        }
        try {
            assertFailsWith<IllegalArgumentException> {
                DefaultOrderCatalogRepository(OrderCatalogApi(client) { "http://localhost" }).getMenu("other", "menu-1")
            }
        } finally { client.close() }
    }

    private val menu = """{
      "id":"menu-1","name":"Lunch","active":true,"restaurant":{"id":"restaurant-1"},
      "sections":[{"id":"section-1","name":"Mains","active":true,"items":[{
        "id":"item-1","name":"Burger","available":true,"basePrice":12.30,
        "optionGroups":[{"linkId":"link-1","optionGroupId":"group-1","name":"Sauce","active":true,
          "minSelect":null,"maxSelect":null,"required":true}]
      }]}]
    }"""
    private val group = """{
      "id":"group-1","restaurantId":"restaurant-1","name":"Sauce","active":true,
      "minSelect":null,"maxSelect":null,"required":true,
      "items":[{"id":"option-1","optionGroupId":"group-1","name":"Mayo","available":true,"priceDelta":0.10}]
    }"""
}
