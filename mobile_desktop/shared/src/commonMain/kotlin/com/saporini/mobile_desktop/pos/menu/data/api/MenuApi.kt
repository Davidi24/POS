package com.saporini.mobile_desktop.pos.menu.data.api

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.menu.data.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

class MenuApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = {
        ApiConfig.BASE_URL
    }
) {

    private fun endpoint(path: String): String {
        return "${baseUrlProvider().trimEnd('/')}$path"
    }

    // Menus

    suspend fun getMenus(
        restaurantId: String? = null,
        active: Boolean? = null,
        search: String? = null,
        page: Int = 0,
        size: Int = 20,
        sortBy: String = "displayOrder",
        direction: String = "asc"
    ): MenuPageResponseDto {
        return client.get(endpoint("/menus")) {
            restaurantId?.let {
                parameter("restaurantId", it)
            }

            active?.let {
                parameter("active", it)
            }

            search?.takeIf { it.isNotBlank() }?.let {
                parameter("search", it)
            }

            parameter("page", page)
            parameter("size", size)
            parameter("sortBy", sortBy)
            parameter("direction", direction)
        }.body()
    }

    // The menus on right now for waiters: scheduled for this day and time, chosen in "Menus shown", and only the event
    // menu on a special-menu-only event night. The server decides; one page holds them all.
    suspend fun getMenusAvailableNow(restaurantId: String): MenuPageResponseDto =
        client.get(endpoint("/menus/available-now")) { parameter("restaurantId", restaurantId) }.body()

    suspend fun saveMenuOrder(request: MenuOrderRequestDto) {
        client.put(endpoint("/menus/order")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getWaiterView(restaurantId: String): MenuWaiterViewDto =
        client.get(endpoint("/menus/waiter-view")) { parameter("restaurantId", restaurantId) }.body()

    suspend fun saveWaiterView(request: MenuWaiterViewRequestDto): MenuWaiterViewDto =
        client.put(endpoint("/menus/waiter-view")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // The restaurant's event nights (an event menu belongs to one of them).
    suspend fun getEventNights(restaurantId: String): List<MenuEventNightDto> {
        return client.get(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/events")).body()
    }

    suspend fun updateEventNight(restaurantId: String, eventId: String, request: MenuEventNightRequestDto): MenuEventNightDto {
        return client.put(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/events/${eventId.encodeURLPathPart()}")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteEventNight(restaurantId: String, eventId: String) {
        client.delete(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/events/${eventId.encodeURLPathPart()}"))
    }

    suspend fun createEventNight(restaurantId: String, request: MenuEventNightRequestDto): MenuEventNightDto {
        return client.post(endpoint("/restaurants/${restaurantId.encodeURLPathPart()}/events")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun createMenu(
        request: CreateMenuRequestDto
    ): MenuResponseDto {
        return client.post(endpoint("/menus")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getMenu(
        menuId: String,
        includeSections: Boolean = false,
        includeItems: Boolean = false,
        includeVariants: Boolean = false,
        includeOptionGroups: Boolean = false
    ): MenuResponseDto {
        return client.get(endpoint("/menus/$menuId")) {
            parameter("includeSections", includeSections)
            parameter("includeItems", includeItems)
            parameter("includeVariants", includeVariants)
            parameter("includeOptionGroups", includeOptionGroups)
        }.body()
    }

    suspend fun updateMenu(
        menuId: String,
        request: UpdateMenuRequestDto
    ): MenuResponseDto {
        return client.put(endpoint("/menus/$menuId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteMenu(
        menuId: String,
        deleteItems: Boolean = false
    ) {
        client.delete(endpoint("/menus/$menuId")) {
            parameter("deleteItems", deleteItems)
        }
    }

    // Menu sections

    suspend fun createSection(
        menuId: String,
        request: CreateMenuSectionRequestDto
    ): MenuSectionDto {
        return client.post(
            endpoint("/menus/$menuId/sections")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateSection(
        menuId: String,
        sectionId: String,
        request: UpdateMenuSectionRequestDto
    ): MenuSectionDto {
        return client.put(
            endpoint("/menus/$menuId/sections/$sectionId")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteSection(
        menuId: String,
        sectionId: String,
        deleteItems: Boolean = false
    ) {
        client.delete(
            endpoint("/menus/$menuId/sections/$sectionId")
        ) {
            parameter("deleteItems", deleteItems)
        }
    }

    // Menu items

    suspend fun importItems(menuId: String, sectionId: String, request: ImportMenuItemsRequestDto): List<MenuItemDto> =
        client.post(endpoint("/menus/$menuId/sections/$sectionId/items/import")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun createItem(
        menuId: String,
        sectionId: String,
        request: CreateMenuItemRequestDto
    ): MenuItemDto {
        return client.post(
            endpoint("/menus/$menuId/sections/$sectionId/items")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateItem(
        menuId: String,
        sectionId: String,
        itemId: String,
        request: UpdateMenuItemRequestDto
    ): MenuItemDto {
        return client.put(
            endpoint(
                "/menus/$menuId/sections/$sectionId/items/$itemId"
            )
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    // The dish's photo (PNG, JPEG or WebP, up to 5 MB); replaces any earlier one.
    suspend fun uploadItemImage(
        menuId: String,
        sectionId: String,
        itemId: String,
        imageBytes: ByteArray,
        fileName: String,
        contentType: String
    ): MenuItemDto {
        return client.put(endpoint("/menus/$menuId/sections/$sectionId/items/$itemId/image")) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = imageBytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                            }
                        )
                    }
                )
            )
        }.body()
    }

    suspend fun removeItemImage(menuId: String, sectionId: String, itemId: String): MenuItemDto =
        client.delete(endpoint("/menus/$menuId/sections/$sectionId/items/$itemId/image")).body()

    // The dish's ingredients from stock (its recipe), and what it can be made with.
    suspend fun getDishRecipe(menuId: String, sectionId: String, itemId: String): DishRecipeDto =
        client.get(endpoint("/menus/$menuId/sections/$sectionId/items/$itemId/recipe")).body()

    suspend fun saveDishRecipe(menuId: String, sectionId: String, itemId: String, request: DishRecipeRequestDto): DishRecipeDto =
        client.put(endpoint("/menus/$menuId/sections/$sectionId/items/$itemId/recipe")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun ingredientChoices(menuId: String): List<IngredientChoiceDto> =
        client.get(endpoint("/menus/$menuId/ingredient-choices")).body()

    suspend fun updateItemAvailability(
        menuId: String,
        sectionId: String,
        itemId: String,
        request: UpdateMenuItemAvailabilityRequestDto
    ): MenuItemDto {
        return client.patch(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/availability"
            )
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteItem(
        menuId: String,
        sectionId: String,
        itemId: String
    ) {
        client.delete(
            endpoint(
                "/menus/$menuId/sections/$sectionId/items/$itemId"
            )
        )
    }

    // Menu variants

    suspend fun createVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        request: CreateMenuVariantRequestDto
    ): MenuVariantDto {
        return client.post(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/variants"
            )
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String,
        request: UpdateMenuVariantRequestDto
    ): MenuVariantDto {
        return client.put(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/variants/$variantId"
            )
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteVariant(
        menuId: String,
        sectionId: String,
        itemId: String,
        variantId: String
    ) {
        client.delete(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/variants/$variantId"
            )
        )
    }

    // Menu item option-group links

    suspend fun createItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        request: CreateMenuItemOptionGroupRequestDto
    ): MenuItemOptionGroupDto {
        return client.post(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/option-groups"
            )
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        linkId: String
    ) {
        client.delete(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/option-groups/$linkId"
            )
        )
    }

    // Option-group types

    suspend fun getOptionGroupTypes(
        search: String? = null
    ): List<OptionGroupTypeDto> {
        return client.get(endpoint("/option-group-types")) {
            search?.takeIf { it.isNotBlank() }?.let {
                parameter("search", it)
            }
        }.body()
    }

    suspend fun createOptionGroupType(
        request: CreateOptionGroupTypeRequestDto
    ): OptionGroupTypeDto {
        return client.post(endpoint("/option-group-types")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    // Option groups

    suspend fun createOptionGroup(
        request: CreateOptionGroupRequestDto
    ): OptionGroupDto {
        return client.post(endpoint("/option-groups")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getOptionGroup(groupId: String, includeItems: Boolean = false): OptionGroupDto =
        client.get(endpoint("/option-groups/$groupId")) {
            parameter("includeItems", includeItems)
        }.body()

    suspend fun deleteOptionGroup(groupId: String) {
        client.delete(endpoint("/option-groups/$groupId"))
    }

    // Option items

    suspend fun createOptionItem(
        groupId: String,
        request: CreateOptionItemRequestDto
    ): OptionItemDto {
        return client.post(
            endpoint("/option-groups/$groupId/items")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOptionItem(
        groupId: String,
        itemId: String,
        request: UpdateOptionItemRequestDto
    ): OptionItemDto {
        return client.put(endpoint("/option-groups/$groupId/items/$itemId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteOptionItem(groupId: String, itemId: String) {
        client.delete(endpoint("/option-groups/$groupId/items/$itemId"))
    }

    // Online menu

    suspend fun getOnlineMenu(restaurantId: String): OnlineMenuDto =
        client.get(endpoint("/restaurants/$restaurantId/online-menu")).body()

    suspend fun getOnlineMenuSections(restaurantId: String): List<OnlineMenuSectionDto> =
        client.get(endpoint("/restaurants/$restaurantId/online-menu/sections")).body()

    suspend fun renameOnlineMenuSection(restaurantId: String, sectionId: String, request: RenameOnlineMenuSectionRequestDto): OnlineMenuSectionDto =
        client.patch(endpoint("/restaurants/$restaurantId/online-menu/sections/$sectionId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun reorderOnlineMenuSections(restaurantId: String, request: ReorderOnlineMenuSectionsRequestDto): List<OnlineMenuSectionDto> =
        client.put(endpoint("/restaurants/$restaurantId/online-menu/sections/order")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun reorderOnlineMenuItems(restaurantId: String, sectionId: String, request: ReorderOnlineMenuItemsRequestDto): OnlineMenuDto =
        client.put(endpoint("/restaurants/$restaurantId/online-menu/sections/$sectionId/items/order")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteOnlineMenuSection(restaurantId: String, sectionId: String) {
        client.delete(endpoint("/restaurants/$restaurantId/online-menu/sections/$sectionId"))
    }
}
