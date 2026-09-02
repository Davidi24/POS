package com.saporini.mobile_desktop.pos.menu.data.api

import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.pos.menu.data.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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

    suspend fun updateMenuStatus(
        menuId: String,
        request: UpdateMenuStatusRequestDto
    ): MenuResponseDto {
        return client.patch(endpoint("/menus/$menuId/status")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteMenu(
        menuId: String
    ) {
        client.delete(endpoint("/menus/$menuId"))
    }

    // Menu sections

    suspend fun getSections(
        menuId: String,
        active: Boolean? = null,
        includeItems: Boolean = false
    ): List<MenuSectionDto> {
        return client.get(endpoint("/menus/$menuId/sections")) {
            active?.let {
                parameter("active", it)
            }

            parameter("includeItems", includeItems)
        }.body()
    }

    suspend fun getSection(
        menuId: String,
        sectionId: String,
        includeItems: Boolean = false
    ): MenuSectionDto {
        return client.get(
            endpoint("/menus/$menuId/sections/$sectionId")
        ) {
            parameter("includeItems", includeItems)
        }.body()
    }

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

    suspend fun updateSectionStatus(
        menuId: String,
        sectionId: String,
        request: UpdateMenuSectionStatusRequestDto
    ): MenuSectionDto {
        return client.patch(
            endpoint("/menus/$menuId/sections/$sectionId/status")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteSection(
        menuId: String,
        sectionId: String
    ) {
        client.delete(
            endpoint("/menus/$menuId/sections/$sectionId")
        )
    }

    // Menu items

    suspend fun getItems(
        menuId: String,
        sectionId: String,
        available: Boolean? = null,
        includeVariants: Boolean = false,
        includeOptionGroups: Boolean = false
    ): List<MenuItemDto> {
        return client.get(
            endpoint("/menus/$menuId/sections/$sectionId/items")
        ) {
            available?.let {
                parameter("available", it)
            }

            parameter("includeVariants", includeVariants)
            parameter("includeOptionGroups", includeOptionGroups)
        }.body()
    }

    suspend fun getItem(
        menuId: String,
        sectionId: String,
        itemId: String,
        includeVariants: Boolean = false,
        includeOptionGroups: Boolean = false
    ): MenuItemDto {
        return client.get(
            endpoint(
                "/menus/$menuId/sections/$sectionId/items/$itemId"
            )
        ) {
            parameter("includeVariants", includeVariants)
            parameter("includeOptionGroups", includeOptionGroups)
        }.body()
    }

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

    suspend fun getVariants(
        menuId: String,
        sectionId: String,
        itemId: String
    ): List<MenuVariantDto> {
        return client.get(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/variants"
            )
        ).body()
    }

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

    suspend fun getItemOptionGroups(
        menuId: String,
        sectionId: String,
        itemId: String
    ): List<MenuItemOptionGroupDto> {
        return client.get(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/option-groups"
            )
        ).body()
    }

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

    suspend fun updateItemOptionGroup(
        menuId: String,
        sectionId: String,
        itemId: String,
        linkId: String,
        request: UpdateMenuItemOptionGroupRequestDto
    ): MenuItemOptionGroupDto {
        return client.put(
            endpoint(
                "/menus/$menuId/sections/$sectionId" +
                        "/items/$itemId/option-groups/$linkId"
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

    suspend fun updateOptionGroupType(
        typeId: String,
        request: UpdateOptionGroupTypeRequestDto
    ): OptionGroupTypeDto {
        return client.put(
            endpoint("/option-group-types/$typeId")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteOptionGroupType(
        typeId: String
    ) {
        client.delete(
            endpoint("/option-group-types/$typeId")
        )
    }

    // Option groups

    suspend fun getOptionGroups(
        restaurantId: String,
        typeId: String? = null,
        active: Boolean? = null,
        search: String? = null,
        includeItems: Boolean = false
    ): List<OptionGroupDto> {
        return client.get(endpoint("/option-groups")) {
            parameter("restaurantId", restaurantId)

            typeId?.let {
                parameter("typeId", it)
            }

            active?.let {
                parameter("active", it)
            }

            search?.takeIf { it.isNotBlank() }?.let {
                parameter("search", it)
            }

            parameter("includeItems", includeItems)
        }.body()
    }

    suspend fun getOptionGroup(
        groupId: String,
        includeItems: Boolean = false
    ): OptionGroupDto {
        return client.get(
            endpoint("/option-groups/$groupId")
        ) {
            parameter("includeItems", includeItems)
        }.body()
    }

    suspend fun createOptionGroup(
        request: CreateOptionGroupRequestDto
    ): OptionGroupDto {
        return client.post(endpoint("/option-groups")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOptionGroup(
        groupId: String,
        request: UpdateOptionGroupRequestDto
    ): OptionGroupDto {
        return client.put(
            endpoint("/option-groups/$groupId")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOptionGroupStatus(
        groupId: String,
        request: UpdateOptionGroupStatusRequestDto
    ): OptionGroupDto {
        return client.patch(
            endpoint("/option-groups/$groupId/status")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteOptionGroup(
        groupId: String
    ) {
        client.delete(
            endpoint("/option-groups/$groupId")
        )
    }

    // Option items

    suspend fun getOptionItems(
        groupId: String,
        available: Boolean? = null
    ): List<OptionItemDto> {
        return client.get(
            endpoint("/option-groups/$groupId/items")
        ) {
            available?.let {
                parameter("available", it)
            }
        }.body()
    }

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
        return client.put(
            endpoint("/option-groups/$groupId/items/$itemId")
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateOptionItemAvailability(
        groupId: String,
        itemId: String,
        request: UpdateOptionItemAvailabilityRequestDto
    ): OptionItemDto {
        return client.patch(
            endpoint(
                "/option-groups/$groupId/items/$itemId/availability"
            )
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteOptionItem(
        groupId: String,
        itemId: String
    ) {
        client.delete(
            endpoint("/option-groups/$groupId/items/$itemId")
        )
    }
}