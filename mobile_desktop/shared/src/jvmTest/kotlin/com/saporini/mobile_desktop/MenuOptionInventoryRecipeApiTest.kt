package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.pos.menu.data.api.MenuApi
import com.saporini.mobile_desktop.pos.menu.data.repository.DefaultMenuRepository
import com.saporini.mobile_desktop.pos.menu.domain.repository.OptionItemInput
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionItem
import com.saporini.mobile_desktop.pos.menu.domain.model.OptionGroup
import com.saporini.mobile_desktop.pos.menu.ui.item.DraftOptionGroup
import com.saporini.mobile_desktop.pos.menu.ui.toDraftOptionGroup
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuOptionInventoryRecipeApiTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun client(handler: suspend (HttpRequestData) -> String) = HttpClient(MockEngine { request ->
        respond(handler(request), headers = headers)
    }) { install(ContentNegotiation) { json(this@MenuOptionInventoryRecipeApiTest.json) } }

    @Test
    fun updatingAnOptionItemSendsAndReadsItsInventoryRecipeMapping() = runTest {
        val recipeResponse = """{"id":"option-1","optionGroupId":"group-1","code":"EXTRA_CHEESE","name":"Extra cheese","priceDelta":1.5,"available":true,"displayOrder":2,"inventoryRecipeId":"recipe-9","inventoryRecipeQuantity":0.25}"""
        val client = client { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/option-groups/group-1/items/option-1", request.url.encodedPath)
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"inventoryRecipeId\":\"recipe-9\""), body)
            assertTrue(body.contains("\"inventoryRecipeQuantity\":0.25"), body)
            recipeResponse
        }
        try {
            val option = DefaultMenuRepository(MenuApi(client) { "http://localhost" }).updateOptionItem(
                groupId = "group-1",
                itemId = "option-1",
                input = OptionItemInput(
                    code = "EXTRA_CHEESE",
                    name = "Extra cheese",
                    priceDelta = 1.5,
                    available = true,
                    displayOrder = 2,
                    inventoryRecipeId = "recipe-9",
                    inventoryRecipeQuantity = 0.25
                )
            )
            assertEquals("recipe-9", option.inventoryRecipeId)
            assertEquals(0.25, option.inventoryRecipeQuantity)
            assertEquals("Extra cheese", option.name)
        } finally {
            client.close()
        }
    }

    @Test
    fun loadingAnOptionGroupIncludesRecipeLinkedChoices() = runTest {
        val response = """{"id":"group-1","restaurantId":"restaurant-1","name":"Sides","active":true,"items":[{"id":"option-1","optionGroupId":"group-1","name":"Extra cheese","priceDelta":1.5,"available":true,"displayOrder":0,"inventoryRecipeId":"recipe-9","inventoryRecipeQuantity":0.25}]}"""
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/option-groups/group-1", request.url.encodedPath)
            assertEquals("true", request.url.parameters["includeItems"])
            response
        }
        try {
            val group = DefaultMenuRepository(MenuApi(client) { "http://localhost" })
                .getOptionGroup("group-1", includeItems = true)
            assertEquals("Sides", group.name)
            assertEquals("recipe-9", group.items.single().inventoryRecipeId)
            assertEquals(0.25, group.items.single().inventoryRecipeQuantity)

            val draft = group.toDraftOptionGroup(
                DraftOptionGroup(name = "old name", required = false, choices = emptyList(), optionGroupId = group.id)
            )
            assertEquals("Sides", draft.name)
            assertEquals("recipe-9", draft.choices.single().inventoryRecipeId)
            assertEquals(0.25, draft.choices.single().inventoryRecipeQuantity)
        } finally {
            client.close()
        }
    }

    @Test
    fun mappedOptionChoiceKeepsModifierRecipeWhenEditing() {
        val group = OptionGroup(
            id = "group-1",
            restaurantId = "restaurant-1",
            type = null,
            name = "Toppings",
            description = null,
            minSelect = 0,
            maxSelect = 1,
            required = false,
            active = true,
            displayOrder = 0,
            items = listOf(
                OptionItem(
                    id = "item-1",
                    optionGroupId = "group-1",
                    code = null,
                    name = "Extra sauce",
                    priceDelta = 0.5,
                    available = true,
                    displayOrder = 0,
                    inventoryRecipeId = "recipe-2",
                    inventoryRecipeQuantity = 0.125
                )
            )
        )
        val draft = group.toDraftOptionGroup(
            DraftOptionGroup(name = group.name, required = group.required, choices = emptyList(), optionGroupId = group.id)
        )
        assertEquals("recipe-2", draft.choices.single().inventoryRecipeId)
        assertEquals(0.125, draft.choices.single().inventoryRecipeQuantity)
    }
}
