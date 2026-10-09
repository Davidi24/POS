package com.saporini.mobile_desktop.gallery

import com.saporini.mobile_desktop.admin.people.CreateStaffRequestDto
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.admin.people.PermissionDto
import com.saporini.mobile_desktop.admin.people.RoleDto
import com.saporini.mobile_desktop.admin.people.RoleNameRequestDto
import com.saporini.mobile_desktop.admin.people.RolesScreenModel
import com.saporini.mobile_desktop.admin.people.StaffFilter
import com.saporini.mobile_desktop.admin.people.StaffPageDto
import com.saporini.mobile_desktop.admin.people.StaffScreenModel
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.admin.people.UpdateStaffRequestDto
import com.saporini.mobile_desktop.admin.people.ui.RolesContent
import com.saporini.mobile_desktop.admin.people.ui.UsersContent
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlin.test.Test

internal val GalleryRoles = listOf(
    RoleDto("r-manager", "MANAGER", "Manager", "Runs the floor and the team", rank = 30),
    RoleDto("r-waiter", "WAITER", "Waiter", "Takes orders and payments", rank = 50),
    RoleDto("r-kitchen", "KITCHEN", "Kitchen", "Cooks and runs the kitchen screen", rank = 55),
    RoleDto("r-host", "HOST", "Host", "Greets guests and seats bookings", rank = 60),
    RoleDto("r-owner", "OWNER", "Owner", rank = 10, isSystem = true, isProtected = true)
)

internal val GalleryPeople = listOf(
    StaffUserDto("u1", "giulia.rossi@saporini.it", "giulia.r", "Giulia", "Rossi", "+39 333 120 4455", true, true, roles = listOf("MANAGER")),
    StaffUserDto("u2", "marco.bianchi@saporini.it", "marco.b", "Marco", "Bianchi", null, true, true, roles = listOf("WAITER")),
    StaffUserDto("u3", "sara.conti@saporini.it", "sara.c", "Sara", "Conti", "+39 347 880 1200", true, false, roles = listOf("WAITER", "HOST")),
    StaffUserDto("u4", "luca.ferrari@saporini.it", "luca.f", "Luca", "Ferrari", null, true, true, roles = listOf("KITCHEN")),
    StaffUserDto("u5", "anna.esposito@saporini.it", "anna.e", "Anna", "Esposito", "+39 320 555 9087", true, true, roles = listOf("KITCHEN")),
    StaffUserDto("u6", "paolo.romano@saporini.it", "paolo.r", "Paolo", "Romano", null, false, true, roles = listOf("WAITER")),
    StaffUserDto("me", "owner@saporini.it", "owner", "Davide", "Keci", null, true, true, roles = listOf("OWNER"))
)

private val GalleryPermissions = listOf(
    PermissionDto("p1", "ORDER_READ", "See orders"), PermissionDto("p2", "ORDER_CREATE", "Take orders"),
    PermissionDto("p3", "ORDER_UPDATE", "Change orders"), PermissionDto("p4", "ORDER_VOID", "Void orders and cancel payments"),
    PermissionDto("p5", "ORDER_CLOSE", "Take payments"), PermissionDto("p6", "PAYMENT_REFUND", "Give refunds"),
    PermissionDto("p7", "KDS_READ", "See the kitchen screen"), PermissionDto("p8", "KDS_UPDATE", "Cook tickets"),
    PermissionDto("p9", "USERS_READ", "See staff"), PermissionDto("p10", "USERS_UPDATE", "Change staff"),
    PermissionDto("p11", "SETTINGS_READ", "See settings"), PermissionDto("p12", "SETTINGS_UPDATE", "Change settings"),
    PermissionDto("p13", "REPORTS_READ", "See statistics"), PermissionDto("p14", "POS_ACCESS", "Open POS")
)

internal class GalleryPeopleRepository : PeopleRepository {
    override suspend fun staff(filter: StaffFilter, page: Int, size: Int): StaffPageDto {
        val matching = GalleryPeople.filter { (filter.active == null || it.isActive == filter.active) && (filter.roleCode == null || filter.roleCode in it.roles) }
        return StaffPageDto(matching.drop(page * size).take(size), page, size, matching.size.toLong(), 1, false)
    }
    override suspend fun person(id: String) = GalleryPeople.first { it.id == id }
    override suspend fun create(request: CreateStaffRequestDto) = GalleryPeople.first()
    override suspend fun update(id: String, request: UpdateStaffRequestDto) = GalleryPeople.first { it.id == id }
    override suspend fun remove(id: String) {}
    override suspend fun rolesOf(id: String) = GalleryRoles.take(1)
    override suspend fun replaceRoles(id: String, roleIds: List<String>) = GalleryPeople.first { it.id == id }
    override suspend fun sendPasswordReset(id: String) {}
    override suspend fun assignableRoles() = GalleryRoles.filterNot { it.isSystem }
    override suspend fun roles() = GalleryRoles
    override suspend fun permissions() = GalleryPermissions
    override suspend fun rolePermissions(roleId: String) = GalleryPermissions.take(if (roleId == "r-waiter") 5 else 9)
    override suspend fun createRole(request: RoleNameRequestDto) = GalleryRoles.first()
    override suspend fun renameRole(roleId: String, request: RoleNameRequestDto) = GalleryRoles.first()
    override suspend fun setRoleActive(roleId: String, active: Boolean) = GalleryRoles.first()
    override suspend fun deleteRole(roleId: String) {}
    override suspend fun cloneRole(roleId: String, request: RoleNameRequestDto) = GalleryRoles.first()
    override suspend fun replacePermissions(roleId: String, permissionIds: List<String>) = GalleryPermissions
}

internal val AdminPermissions = listOf(
    "USERS_READ", "USERS_CREATE", "USERS_UPDATE", "USERS_DELETE", "ROLES_READ", "ROLES_CREATE", "ROLES_UPDATE", "ROLES_DELETE",
    "ROLES_ASSIGN_PERMISSIONS", "SETTINGS_READ", "SETTINGS_UPDATE", "SETTINGS_AUDIT", "ORDER_READ", "ORDER_CREATE", "ORDER_UPDATE",
    "ORDER_VOID", "ORDER_CLOSE", "PAYMENT_REFUND", "KDS_READ", "KDS_UPDATE", "REPORTS_READ", "FRAUD_READ", "FRAUD_REVIEW", "POS_ACCESS",
    "MENUS_READ", "MENUS_UPDATE"
)

internal fun gallerySession() = SessionManager().apply { signIn(user(id = "me", permissions = AdminPermissions)) }

class AdminPeopleGalleryTest {
    @Test
    fun users() = gallery {
        val model = StaffScreenModel(GalleryPeopleRepository(), gallerySession())
        model.setActive(true); settle(); model.loadCounts(); settle()
        render("admin-users", required = listOf("Users", "Giulia Rossi", "Waiter", "Biggest team")) {
            val state by model.state.collectAsState()
            UsersContent(state, model)
        }
        render("admin-users-tablet", width = 900, height = 1000) {
            val state by model.state.collectAsState()
            UsersContent(state, model)
        }
        model.startEdit("u2"); settle()
        render("admin-users-editor", required = listOf("Edit Marco Bianchi", "Roles")) {
            val state by model.state.collectAsState()
            UsersContent(state, model)
        }
        model.onDispose()
    }

    @Test
    fun roles() = gallery {
        val model = RolesScreenModel(GalleryPeopleRepository(), gallerySession())
        model.setActive(true); settle()
        model.select("r-waiter"); settle()
        render("admin-roles", required = listOf("Permissions", "Waiter", "Orders")) {
            val state by model.state.collectAsState()
            RolesContent(state, model)
        }
        model.onDispose()
    }
}
