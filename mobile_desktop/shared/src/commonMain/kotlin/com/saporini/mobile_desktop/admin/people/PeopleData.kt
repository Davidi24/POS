package com.saporini.mobile_desktop.admin.people

import com.saporini.mobile_desktop.core.network.ApiConfig
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
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Admin Hub → Users and Permissions. The server keeps everyone inside the signed-in person's restaurant.

@Serializable
data class StaffUserDto(
    val id: String,
    val email: String,
    val username: String,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String? = null,
    val isActive: Boolean = true,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val roles: List<String> = emptyList(),
    val restaurantId: String? = null,
    val defaultBranchId: String? = null
) {
    val displayName: String get() = "$firstName $lastName".trim().ifEmpty { username }
}

@Serializable
data class StaffPageDto(
    val items: List<StaffUserDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

@Serializable
data class RoleDto(
    val id: String,
    val code: String,
    val name: String,
    val description: String? = null,
    val rank: Long? = null,
    val isSystem: Boolean = false,
    val isActive: Boolean = true,
    val isAssignable: Boolean = true,
    val isProtected: Boolean = false
)

@Serializable
data class PermissionDto(val id: String, val code: String, val name: String, val description: String? = null)

@Serializable
data class CreateStaffRequestDto(
    val email: String,
    val username: String,
    val temporaryPassword: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val roleId: String,
    val defaultBranchId: String? = null
)

@Serializable
data class UpdateStaffRequestDto(
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val isActive: Boolean,
    val defaultBranchId: String? = null
)

@Serializable
data class ReplaceRolesRequestDto(val roleIds: List<String>)

@Serializable
data class RoleNameRequestDto(val name: String, val description: String? = null)

@Serializable
data class RoleStatusRequestDto(val isActive: Boolean)

@Serializable
data class ReplacePermissionsRequestDto(val permissionIds: List<String>)

data class StaffFilter(val search: String = "", val active: Boolean? = null, val roleCode: String? = null)

interface PeopleRepository {
    suspend fun staff(filter: StaffFilter, page: Int, size: Int): StaffPageDto
    suspend fun person(id: String): StaffUserDto
    suspend fun create(request: CreateStaffRequestDto): StaffUserDto
    suspend fun update(id: String, request: UpdateStaffRequestDto): StaffUserDto
    suspend fun remove(id: String)
    suspend fun rolesOf(id: String): List<RoleDto>
    suspend fun replaceRoles(id: String, roleIds: List<String>): StaffUserDto
    suspend fun sendPasswordReset(id: String)
    suspend fun assignableRoles(): List<RoleDto>

    suspend fun roles(): List<RoleDto>
    suspend fun permissions(): List<PermissionDto>
    suspend fun rolePermissions(roleId: String): List<PermissionDto>
    suspend fun createRole(request: RoleNameRequestDto): RoleDto
    suspend fun renameRole(roleId: String, request: RoleNameRequestDto): RoleDto
    suspend fun setRoleActive(roleId: String, active: Boolean): RoleDto
    suspend fun deleteRole(roleId: String)
    suspend fun cloneRole(roleId: String, request: RoleNameRequestDto): RoleDto
    suspend fun replacePermissions(roleId: String, permissionIds: List<String>): List<PermissionDto>
}

class PeopleApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : PeopleRepository {

    private fun url(path: String) = "${baseUrlProvider().trimEnd('/')}$path"
    private fun id(value: String) = value.encodeURLPathPart()

    override suspend fun staff(filter: StaffFilter, page: Int, size: Int): StaffPageDto = client.get(url("/users")) {
        filter.search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it) }
        filter.active?.let { parameter("active", it) }
        filter.roleCode?.let { parameter("roleCode", it) }
        parameter("page", page)
        parameter("size", size)
        parameter("sortBy", "firstName")
        parameter("direction", "asc")
    }.body()

    override suspend fun person(id: String): StaffUserDto = client.get(url("/users/${id(id)}")).body()

    override suspend fun create(request: CreateStaffRequestDto): StaffUserDto = client.post(url("/auth/register")) {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()

    override suspend fun update(id: String, request: UpdateStaffRequestDto): StaffUserDto = client.put(url("/users/${id(id)}")) {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()

    override suspend fun remove(id: String) {
        client.delete(url("/users/${id(id)}"))
    }

    override suspend fun rolesOf(id: String): List<RoleDto> = client.get(url("/users/${id(id)}/roles")).body()

    override suspend fun replaceRoles(id: String, roleIds: List<String>): StaffUserDto = client.put(url("/users/${id(id)}/roles")) {
        contentType(ContentType.Application.Json); setBody(ReplaceRolesRequestDto(roleIds))
    }.body()

    override suspend fun sendPasswordReset(id: String) {
        client.post(url("/users/${id(id)}/reset-password")) { contentType(ContentType.Application.Json); setBody(mapOf("channel" to "EMAIL")) }
    }

    override suspend fun assignableRoles(): List<RoleDto> = client.get(url("/roles/assignable")).body()

    override suspend fun roles(): List<RoleDto> = client.get(url("/roles")).body()

    override suspend fun permissions(): List<PermissionDto> = client.get(url("/permissions")).body()

    override suspend fun rolePermissions(roleId: String): List<PermissionDto> = client.get(url("/roles/${id(roleId)}/permissions")).body()

    override suspend fun createRole(request: RoleNameRequestDto): RoleDto = client.post(url("/roles")) {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()

    override suspend fun renameRole(roleId: String, request: RoleNameRequestDto): RoleDto = client.put(url("/roles/${id(roleId)}")) {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()

    override suspend fun setRoleActive(roleId: String, active: Boolean): RoleDto = client.patch(url("/roles/${id(roleId)}/status")) {
        contentType(ContentType.Application.Json); setBody(RoleStatusRequestDto(active))
    }.body()

    override suspend fun deleteRole(roleId: String) {
        client.delete(url("/roles/${id(roleId)}"))
    }

    override suspend fun cloneRole(roleId: String, request: RoleNameRequestDto): RoleDto = client.post(url("/roles/${id(roleId)}/clone")) {
        contentType(ContentType.Application.Json); setBody(request)
    }.body()

    override suspend fun replacePermissions(roleId: String, permissionIds: List<String>): List<PermissionDto> =
        client.put(url("/roles/${id(roleId)}/permissions")) {
            contentType(ContentType.Application.Json); setBody(ReplacePermissionsRequestDto(permissionIds))
        }.body()
}
