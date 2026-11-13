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
import com.saporini.mobile_desktop.admin.people.ui.UsersContent
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlin.test.Test

// Ranks as on the server: the higher, the more a role may do.
internal val GalleryRoles = listOf(
    RoleDto("r-manager", "MANAGER", "Manager", "Runs the floor and the team", rank = 20_000),
    RoleDto("r-waiter", "WAITER", "Waiter", "Takes orders and payments", rank = 10_000),
    RoleDto("r-kitchen", "KITCHEN", "Kitchen", "Cooks and runs the kitchen screen", rank = 5_000),
    RoleDto("r-host", "HOST", "Host", "Greets guests and seats bookings", rank = 9_999),
    RoleDto("r-owner", "OWNER", "Owner", rank = 50_000, isSystem = true, isProtected = true),
    RoleDto("r-co-owner", "CO_OWNER", "Co-Owner", "Shares ownership with limited restrictions", rank = 40_000, isSystem = true)
)

internal val GalleryPeople = listOf(
    StaffUserDto("u1", "giulia.rossi@saporini.it", "giulia.r", "Giulia", "Rossi", "+39 333 120 4455", true, true, roles = listOf("MANAGER")),
    StaffUserDto("u2", "marco.bianchi@saporini.it", "marco.b", "Marco", "Bianchi", null, true, true, roles = listOf("WAITER")),
    StaffUserDto("u3", "sara.conti@saporini.it", "sara.c", "Sara", "Conti", "+39 347 880 1200", true, false, roles = listOf("WAITER", "HOST")),
    StaffUserDto("u4", "luca.ferrari@saporini.it", "luca.f", "Luca", "Ferrari", null, true, true, roles = listOf("KITCHEN")),
    StaffUserDto("u5", "anna.esposito@saporini.it", "anna.e", "Anna", "Esposito", "+39 320 555 9087", true, true, roles = listOf("KITCHEN")),
    StaffUserDto("u6", "paolo.romano@saporini.it", "paolo.r", "Paolo", "Romano", null, false, true, roles = listOf("WAITER")),
    StaffUserDto("me", "owner@saporini.it", "owner", "Davide", "Keci", null, true, true, roles = listOf("OWNER")),
    StaffUserDto("u7", "elena.galli@saporini.it", "elena.g", "Elena", "Galli", null, true, true, roles = listOf("CO_OWNER"))
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
    override suspend fun assignableRoles() = GalleryRoles.filterNot { it.isProtected }
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
    "MENUS_READ", "MENUS_UPDATE", "INVENTORY_ACCESS", "INVENTORY_READ", "INVENTORY_UPDATE"
)

internal fun gallerySession() = SessionManager().apply { signIn(user(id = "me", permissions = AdminPermissions)) }

class AdminPeopleGalleryTest {
    // Admin Hub → Users in the Salary look: the role cards, a role's people and what it can do, and the editor.
    @Test
    fun users() = gallery {
        val session = gallerySession()
        val model = StaffScreenModel(GalleryPeopleRepository(), session)
        val roles = RolesScreenModel(GalleryPeopleRepository(), session)
        model.setActive(true); roles.setActive(true); settle()
        model.loadCounts(); model.loadRoleStats(GalleryRoles.map { it.code }); settle()
        listOf("desktop" to (1440 to 960), "tablet" to (1024 to 1100), "phone" to (390 to 1300)).forEach { (name, size) ->
            render("admin-users-$name", size.first, size.second, required = listOf("Users", "Waiter", "Manager", "Owners")) {
                val state by model.state.collectAsState()
                val rolesState by roles.state.collectAsState()
                UsersContent(state, model, rolesState, roles)
            }
        }
        listOf("desktop" to (1440 to 960), "phone" to (390 to 900)).forEach { (name, size) ->
            render("admin-users-role-$name", size.first, size.second, required = listOf("Marco Bianchi", "Add waiter")) {
                val state by model.state.collectAsState()
                val rolesState by roles.state.collectAsState()
                UsersContent(state, model, rolesState, roles, startRole = "WAITER")
            }
        }
        // A new role: name, a picture to pick and whether it's paid (user, 2026-10-31).
        roles.startNew(); settle()
        listOf("desktop" to (1440 to 1100), "phone" to (390 to 1300)).forEach { (name, size) ->
            render("admin-users-new-role-$name", size.first, size.second, required = listOf("New role", "Gets paid", "Driver")) {
                val state by model.state.collectAsState()
                val rolesState by roles.state.collectAsState()
                UsersContent(state, model, rolesState, roles)
            }
        }
        roles.cancelForm(); settle()
        render("admin-users-owners", required = listOf("Owners", "Co-Owner", "Elena Galli")) {
            val state by model.state.collectAsState()
            val rolesState by roles.state.collectAsState()
            UsersContent(state, model, rolesState, roles, startRole = "CO_OWNER")
        }
        render("admin-users-role-permissions", required = listOf("Add waiter")) {
            val state by model.state.collectAsState()
            val rolesState by roles.state.collectAsState()
            UsersContent(state, model, rolesState, roles, startRole = "WAITER", startOnPermissions = true)
        }
        // The responsive pass: phones (360, 412), small tablets (600, 768), a tablet (1024) and a laptop (1280).
        listOf(360 to 900, 412 to 900, 600 to 1000, 768 to 1000, 1024 to 900, 1280 to 900).forEach { (width, height) ->
            render("size-users-$width", width, height, required = listOf("Users", "Waiter", "Owners")) {
                val state by model.state.collectAsState()
                val rolesState by roles.state.collectAsState()
                UsersContent(state, model, rolesState, roles)
            }
        }
        listOf(360, 600, 768, 1024).forEach { width ->
            render("size-users-role-$width", width, 800, required = listOf("Marco Bianchi", "Add waiter")) {
                val state by model.state.collectAsState()
                val rolesState by roles.state.collectAsState()
                UsersContent(state, model, rolesState, roles, startRole = "WAITER")
            }
            render("size-users-perms-$width", width, 1300, required = listOf("Add waiter")) {
                val state by model.state.collectAsState()
                val rolesState by roles.state.collectAsState()
                UsersContent(state, model, rolesState, roles, startRole = "WAITER", startOnPermissions = true)
            }
        }
        // A built-in role can't be changed: the note with "Make a copy", on a small phone.
        render("size-users-perms-builtin-360", 360, 1100, required = listOf()) {
            val state by model.state.collectAsState()
            val rolesState by roles.state.collectAsState()
            UsersContent(state, model, rolesState, roles, startRole = "CO_OWNER", startOnPermissions = true)
        }
        // Back on the waiters (the renders above left the list on Co-Owner), so Marco is there to edit.
        model.role("WAITER"); settle()
        model.startEdit("u2"); settle()
        render("admin-users-editor", required = listOf("Edit Marco Bianchi", "Access", "Waiter")) {
            val state by model.state.collectAsState()
            val rolesState by roles.state.collectAsState()
            UsersContent(state, model, rolesState, roles, startRole = "WAITER")
        }
        model.onDispose(); roles.onDispose()
    }
}
