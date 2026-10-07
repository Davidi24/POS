@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.admin

import com.saporini.mobile_desktop.admin.people.CreateStaffRequestDto
import com.saporini.mobile_desktop.admin.people.PeopleApi
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.admin.people.PermissionDto
import com.saporini.mobile_desktop.admin.people.RoleDto
import com.saporini.mobile_desktop.admin.people.RoleForm
import com.saporini.mobile_desktop.admin.people.RoleNameRequestDto
import com.saporini.mobile_desktop.admin.people.RolesScreenModel
import com.saporini.mobile_desktop.admin.people.StaffDraft
import com.saporini.mobile_desktop.admin.people.StaffField
import com.saporini.mobile_desktop.admin.people.StaffFilter
import com.saporini.mobile_desktop.admin.people.StaffPageDto
import com.saporini.mobile_desktop.admin.people.StaffScreenModel
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.admin.people.UpdateStaffRequestDto
import com.saporini.mobile_desktop.admin.people.canSavePermissions
import com.saporini.mobile_desktop.admin.people.roleProblem
import com.saporini.mobile_desktop.admin.people.staffProblems
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun person(n: Int, active: Boolean = true, roles: List<String> = listOf("WAITER")) = StaffUserDto(
    id = "u$n", email = "p$n@pos.example", username = "p$n", firstName = "Person", lastName = "$n", isActive = active, roles = roles
)

private val WAITER = RoleDto("r-waiter", "WAITER", "Waiter", rank = 50)
private val MANAGER = RoleDto("r-manager", "MANAGER", "Manager", rank = 20)
private val CUSTOM = RoleDto("r-custom", "HOST", "Host", rank = 60, isSystem = false)
private val SYSTEM = RoleDto("r-owner", "OWNER", "Owner", rank = 10, isSystem = true, isProtected = true)
private val READ = PermissionDto("p-read", "ORDER_READ", "Read orders")
private val CREATE = PermissionDto("p-create", "ORDER_CREATE", "Create orders")
private val AUDIT = PermissionDto("p-audit", "SETTINGS_AUDIT", "Audit")

private class FakePeople : PeopleRepository {
    var people = (1..95).map { person(it) }.toMutableList()
    val staffCalls = mutableListOf<Pair<StaffFilter, Int>>()
    val created = mutableListOf<CreateStaffRequestDto>()
    val updated = mutableListOf<Pair<String, UpdateStaffRequestDto>>()
    val replaced = mutableListOf<Pair<String, List<String>>>()
    val resets = mutableListOf<String>()
    val removed = mutableListOf<String>()
    var rolesOfPerson = mutableMapOf<String, List<RoleDto>>()
    var failure: Exception? = null
    var roles = mutableListOf(MANAGER, WAITER, CUSTOM, SYSTEM)
    val rolePermissions = mutableMapOf("r-custom" to listOf(READ), "r-waiter" to listOf(READ, CREATE), "r-owner" to listOf(READ, AUDIT))
    val permissionWrites = mutableListOf<Pair<String, List<String>>>()
    val roleWrites = mutableListOf<String>()

    override suspend fun staff(filter: StaffFilter, page: Int, size: Int): StaffPageDto {
        staffCalls += filter to page; failure?.let { throw it }
        val matching = people.filter { (filter.active == null || it.isActive == filter.active) && it.displayName.contains(filter.search, ignoreCase = true) }
        val items = matching.drop(page * size).take(size)
        return StaffPageDto(items, page, size, matching.size.toLong(), (matching.size + size - 1) / size, (page + 1) * size < matching.size)
    }

    override suspend fun person(id: String) = people.first { it.id == id }

    override suspend fun create(request: CreateStaffRequestDto): StaffUserDto {
        failure?.let { throw it }
        created += request
        val made = StaffUserDto("new-${created.size}", request.email, request.username, request.firstName, request.lastName, roles = listOf("WAITER"))
        people.add(0, made)
        return made
    }

    override suspend fun update(id: String, request: UpdateStaffRequestDto): StaffUserDto {
        failure?.let { throw it }
        updated += id to request
        val saved = people.first { it.id == id }.copy(firstName = request.firstName, lastName = request.lastName, phone = request.phone, isActive = request.isActive)
        people.replaceAll { if (it.id == id) saved else it }
        return saved
    }

    override suspend fun remove(id: String) {
        failure?.let { throw it }
        removed += id; people.removeAll { it.id == id }
    }

    override suspend fun rolesOf(id: String) = rolesOfPerson[id] ?: listOf(WAITER)

    override suspend fun replaceRoles(id: String, roleIds: List<String>): StaffUserDto {
        replaced += id to roleIds
        return people.first { it.id == id }.copy(roles = roles.filter { it.id in roleIds }.map { it.code })
    }

    override suspend fun sendPasswordReset(id: String) {
        failure?.let { throw it }
        resets += id
    }

    override suspend fun assignableRoles() = listOf(WAITER, CUSTOM, RoleDto("r-off", "OFF", "Off", isActive = false))
    override suspend fun roles(): List<RoleDto> { failure?.let { throw it }; return roles.toList() }
    override suspend fun permissions() = listOf(READ, CREATE, AUDIT)
    override suspend fun rolePermissions(roleId: String) = rolePermissions[roleId].orEmpty()

    override suspend fun createRole(request: RoleNameRequestDto): RoleDto {
        failure?.let { throw it }
        roleWrites += "create ${request.name}"
        return RoleDto("r-${roles.size}", request.name.uppercase(), request.name, request.description, rank = 70).also { roles += it }
    }

    override suspend fun renameRole(roleId: String, request: RoleNameRequestDto): RoleDto {
        roleWrites += "rename $roleId ${request.name}"
        val saved = roles.first { it.id == roleId }.copy(name = request.name, description = request.description)
        roles.replaceAll { if (it.id == roleId) saved else it }
        return saved
    }

    override suspend fun setRoleActive(roleId: String, active: Boolean): RoleDto {
        roleWrites += "active $roleId $active"
        return roles.first { it.id == roleId }.copy(isActive = active)
    }

    override suspend fun deleteRole(roleId: String) {
        failure?.let { throw it }
        roleWrites += "delete $roleId"; roles.removeAll { it.id == roleId }
    }

    override suspend fun cloneRole(roleId: String, request: RoleNameRequestDto): RoleDto {
        roleWrites += "clone $roleId ${request.name}"
        val copy = RoleDto("r-copy", "COPY", request.name, rank = 80)
        roles += copy
        rolePermissions["r-copy"] = rolePermissions[roleId].orEmpty()
        return copy
    }

    override suspend fun replacePermissions(roleId: String, permissionIds: List<String>): List<PermissionDto> {
        failure?.let { throw it }
        permissionWrites += roleId to permissionIds
        return listOf(READ, CREATE, AUDIT).filter { it.id in permissionIds }.also { rolePermissions[roleId] = it }
    }
}

class PeopleRulesTest {
    private val valid = StaffDraft(email = "a@b.it", username = "anna.b", temporaryPassword = "longenough", firstName = "Anna",
        lastName = "Bianchi", roleIds = setOf("r"))

    @Test
    fun aCompleteNewPersonHasNoProblems() = assertEquals(emptyMap(), staffProblems(valid))

    @Test
    fun everyFieldIsCheckedAgainstTheServersLimits() {
        val problems = staffProblems(StaffDraft(email = "x".repeat(140) + "@b.it" + "x".repeat(10), username = "a b", temporaryPassword = "short",
            firstName = " ", lastName = "L".repeat(51), phone = "call me", roleIds = emptySet()))
        assertEquals(setOf(StaffField.EMAIL, StaffField.USERNAME, StaffField.PASSWORD, StaffField.FIRST_NAME, StaffField.LAST_NAME,
            StaffField.PHONE, StaffField.ROLES), problems.keys)
        assertEquals("This isn't an email address", staffProblems(valid.copy(email = "not-an-email"))[StaffField.EMAIL])
        assertEquals("At least 3 characters", staffProblems(valid.copy(username = "ab"))[StaffField.USERNAME])
        assertEquals("At most 50 characters", staffProblems(valid.copy(username = "a".repeat(51)))[StaffField.USERNAME])
        assertEquals("At most 100 characters", staffProblems(valid.copy(temporaryPassword = "p".repeat(101)))[StaffField.PASSWORD])
        assertEquals("At most 50 roles", staffProblems(valid.copy(roleIds = (1..51).map { "r$it" }.toSet()))[StaffField.ROLES])
    }

    @Test
    fun namesAtTheLimitUnicodeAndPasswordSpacesAreFine() {
        val draft = valid.copy(firstName = "Ž".repeat(50), lastName = "  O'Brien-Núñez 🙂  ", temporaryPassword = "        ", phone = "+39 (333) 123-4567")
        assertEquals(emptyMap(), staffProblems(draft))
    }

    @Test
    fun editingSomeoneDoesNotAskForEmailUsernameOrPassword() {
        assertEquals(emptyMap(), staffProblems(StaffDraft(id = "u1", firstName = "A", lastName = "B", roleIds = setOf("r"))))
    }

    @Test
    fun roleNamesAndPermissionLimits() {
        assertEquals("Enter a role name", roleProblem("   ", ""))
        assertEquals("The name can be at most 100 characters", roleProblem("R".repeat(101), ""))
        assertEquals("The description can be at most 1000 characters", roleProblem("Host", "d".repeat(1001)))
        assertNull(roleProblem("R".repeat(100), "d".repeat(1000)))
        assertTrue(canSavePermissions(setOf("A"), setOf("A", "B"), superAdmin = false))
        assertFalse(canSavePermissions(setOf("A", "C"), setOf("A", "B"), superAdmin = false))
        assertTrue(canSavePermissions(setOf("A", "C"), emptySet(), superAdmin = true))
    }
}

class StaffScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakePeople = FakePeople(),
        permissions: List<String> = listOf("USERS_READ", "USERS_CREATE", "USERS_UPDATE", "USERS_DELETE"),
        block: suspend TestScope.(StaffScreenModel, FakePeople) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(id = "me", permissions = permissions)) }
        val model = StaffScreenModel(repo, session)
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun loadsActiveStaffAndPagesOnWithoutDuplicates() = runTest(dispatcher) {
        check { model, repo ->
            assertEquals(40, model.state.value.people.size)
            assertEquals(95, model.state.value.total)
            assertEquals(true, model.state.value.filter.active)
            model.loadMore(); runCurrent()
            model.loadMore(); runCurrent()
            assertEquals(95, model.state.value.people.size)
            assertFalse(model.state.value.hasNext)
            model.loadMore(); runCurrent()
            assertEquals(listOf(0, 1, 2), repo.staffCalls.map { it.second })
        }
    }

    @Test
    fun aRefreshReloadsEveryPageAlreadyShown() = runTest(dispatcher) {
        check { model, repo ->
            model.loadMore(); runCurrent()
            repo.staffCalls.clear()
            model.refresh(); runCurrent()
            assertEquals(listOf(0, 1), repo.staffCalls.map { it.second })
            assertEquals(80, model.state.value.people.size)
        }
    }

    @Test
    fun searchWaitsForTypingToPauseAndStartsFromTheFirstPage() = runTest(dispatcher) {
        check { model, repo ->
            model.loadMore(); runCurrent()
            repo.staffCalls.clear()
            model.search("Person 1"); model.search("Person 12"); runCurrent()
            assertTrue(repo.staffCalls.isEmpty())
            advanceTimeBy(StaffScreenModel.SEARCH_DELAY_MILLIS + 1); runCurrent()
            assertEquals(listOf(StaffFilter("Person 12", true) to 0), repo.staffCalls)
            assertEquals(listOf("u12"), model.state.value.people.map { it.id })
        }
    }

    @Test
    fun withoutReadPermissionNothingIsLoaded() = runTest(dispatcher) {
        check(permissions = listOf("ORDER_READ")) { model, repo ->
            assertTrue(repo.staffCalls.isEmpty())
            assertFalse(model.state.value.canRead)
            model.startNew()
            assertNull(model.state.value.draft)
            assertEquals("You can't add staff", model.state.value.error)
        }
    }

    @Test
    fun aForbiddenAnswerTurnsReadingOff() = runTest(dispatcher) {
        val repo = FakePeople().apply { failure = ApiException(403, "Access denied") }
        check(repo) { model, _ ->
            assertFalse(model.state.value.canRead)
            assertEquals("You don't have permission to do this.", model.state.value.error)
        }
    }

    @Test
    fun addingSomeoneChecksFieldsSendsTrimmedValuesAndExtraRoles() = runTest(dispatcher) {
        check { model, repo ->
            model.startNew()
            model.save(); runCurrent()
            assertTrue(repo.created.isEmpty())
            assertEquals("Check the highlighted fields", model.state.value.error)
            assertTrue(StaffField.EMAIL in model.state.value.problems)
            model.edit { it.copy(email = " new@pos.example ", username = " new.one ", temporaryPassword = " secret 1 ", firstName = " Nina ",
                lastName = " Rossi ", phone = "  ") }
            model.toggleRole(WAITER.id); model.toggleRole(CUSTOM.id)
            // Fixing a field clears its message while typing.
            assertFalse(StaffField.EMAIL in model.state.value.problems)
            model.save(); runCurrent()
            val sent = repo.created.single()
            assertEquals("new@pos.example", sent.email)
            assertEquals("new.one", sent.username)
            assertEquals(" secret 1 ", sent.temporaryPassword)
            assertEquals("Nina", sent.firstName)
            assertNull(sent.phone)
            assertEquals("branch-1", sent.defaultBranchId)
            assertEquals(listOf("new-1" to listOf(CUSTOM.id, WAITER.id).sorted()), repo.replaced)
            assertNull(model.state.value.draft)
            assertEquals("new-1", model.state.value.people.first().id)
            assertEquals(96, model.state.value.total)
            assertEquals("Nina Rossi was added", model.state.value.notice)
        }
    }

    @Test
    fun assignableRolesLeaveOutSwitchedOffOnes() = runTest(dispatcher) {
        check { model, _ ->
            assertEquals(listOf(WAITER.id, CUSTOM.id), model.state.value.assignableRoles.map { it.id })
        }
    }

    @Test
    fun editingReadsCurrentRolesAndOnlyReplacesThemWhenChanged() = runTest(dispatcher) {
        check { model, repo ->
            model.startEdit("u3"); runCurrent()
            assertEquals(setOf(WAITER.id), model.state.value.draft?.roleIds)
            model.edit { it.copy(firstName = "Changed", id = "someone-else") }
            assertEquals("u3", model.state.value.draft?.id)
            model.save(); runCurrent()
            assertEquals("Changed", repo.updated.single().second.firstName)
            assertTrue(repo.replaced.isEmpty())
            assertEquals("Changed", model.state.value.people.first { it.id == "u3" }.firstName)

            model.startEdit("u4"); runCurrent()
            model.toggleRole(MANAGER.id)
            model.save(); runCurrent()
            assertEquals(listOf("u4" to listOf(MANAGER.id, WAITER.id).sorted()), repo.replaced)
        }
    }

    @Test
    fun nobodyEditsSwitchesOffOrRemovesThemselves() = runTest(dispatcher) {
        val repo = FakePeople().apply { people.add(0, person(0).copy(id = "me", firstName = "Aaa")) }
        check(repo) { model, _ ->
            model.startEdit("me"); runCurrent()
            assertNull(model.state.value.draft)
            model.setActive("me", false); runCurrent()
            model.askRemove("me")
            assertNull(model.state.value.confirmRemove)
            model.sendPasswordReset("me"); runCurrent()
            assertTrue(repo.updated.isEmpty() && repo.removed.isEmpty() && repo.resets.isEmpty())
            assertFalse(model.state.value.canEdit(model.state.value.people.first { it.id == "me" }))
        }
    }

    @Test
    fun switchingSomeoneOffMovesThemOutOfTheActiveList() = runTest(dispatcher) {
        check { model, repo ->
            model.setActive("u5", false); runCurrent()
            assertEquals(false, repo.updated.single().second.isActive)
            assertNull(model.state.value.people.firstOrNull { it.id == "u5" })
            assertEquals(94, model.state.value.total)
            assertEquals("Person 5 can no longer sign in", model.state.value.notice)
        }
    }

    @Test
    fun removingAsksFirstAndAPasswordResetSaysWhereItWent() = runTest(dispatcher) {
        check { model, repo ->
            model.askRemove("u7")
            assertEquals("u7", model.state.value.confirmRemove)
            model.dismissRemove()
            assertTrue(repo.removed.isEmpty())
            model.askRemove("u7"); model.confirmRemove(); runCurrent()
            assertEquals(listOf("u7"), repo.removed)
            assertNull(model.state.value.people.firstOrNull { it.id == "u7" })
            model.sendPasswordReset("u8"); runCurrent()
            assertEquals(listOf("u8"), repo.resets)
            assertEquals("A password reset email was sent to p8@pos.example", model.state.value.notice)
        }
    }

    @Test
    fun aFailedWriteExplainsAndAStaleOneReloads() = runTest(dispatcher) {
        check { model, repo ->
            repo.failure = ApiException(409, "Email already exists")
            model.startNew()
            model.edit { StaffDraft(email = "a@b.it", username = "abc", temporaryPassword = "12345678", firstName = "A", lastName = "B", roleIds = setOf("r")) }
            model.save(); runCurrent()
            assertEquals("Email already exists", model.state.value.error)
            assertNotNull(model.state.value.draft)
            assertFalse(model.state.value.saving)
            repo.failure = java.io.IOException("offline")
            model.save(); runCurrent()
            assertEquals("No answer from the server. Refresh to see whether the change was saved.", model.state.value.error)
        }
    }

    @Test
    fun aRefreshDuringASaveDoesNotLoseTheAnswer() = runTest(dispatcher) {
        check { model, repo ->
            model.startEdit("u2"); runCurrent()
            model.edit { it.copy(lastName = "Saved") }
            model.save()
            model.refresh()
            runCurrent()
            assertNull(model.state.value.draft)
            assertFalse(model.state.value.saving)
            assertEquals("Saved", repo.people.first { it.id == "u2" }.lastName)
        }
    }

    @Test
    fun signingOutClearsEverything() = runTest(dispatcher) {
        val repo = FakePeople()
        val session = SessionManager().apply { signIn(user(id = "me", permissions = listOf("USERS_READ"))) }
        val model = StaffScreenModel(repo, session)
        runCurrent(); model.setActive(true); runCurrent()
        assertEquals(40, model.state.value.people.size)
        session.signOut(); runCurrent()
        assertTrue(model.state.value.people.isEmpty())
        assertFalse(model.state.value.canRead)
        model.onDispose(); runCurrent()
    }
}

class RolesScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakePeople = FakePeople(),
        permissions: List<String> = listOf("ROLES_READ", "ROLES_CREATE", "ROLES_UPDATE", "ROLES_DELETE", "ROLES_ASSIGN_PERMISSIONS", "ORDER_READ", "ORDER_CREATE"),
        roles: List<String> = emptyList(),
        block: suspend TestScope.(RolesScreenModel, FakePeople) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions).copy(roles = roles)) }
        val model = RolesScreenModel(repo, session)
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model, repo)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun rolesComeInRankOrderAndTheFirstIsOpened() = runTest(dispatcher) {
        check { model, _ ->
            assertEquals(listOf("r-owner", "r-manager", "r-waiter", "r-custom"), model.state.value.roles.map { it.id })
            assertEquals("r-owner", model.state.value.selectedId)
            assertEquals(setOf("p-read", "p-audit"), model.state.value.savedPermissionIds)
        }
    }

    @Test
    fun builtInRolesAreReadOnly() = runTest(dispatcher) {
        check { model, repo ->
            model.toggle("p-create")
            assertEquals("Built-in roles can't be changed. Copy it to make your own.", model.state.value.error)
            model.startRename("r-owner")
            assertNull(model.state.value.form)
            model.askDelete("r-owner")
            assertNull(model.state.value.confirmDelete)
            assertTrue(repo.permissionWrites.isEmpty())
        }
    }

    @Test
    fun ticksOnlyGivePermissionsYouHaveAndSaveTogether() = runTest(dispatcher) {
        check { model, repo ->
            model.select("r-custom"); runCurrent()
            assertTrue(model.state.value.canEditPermissions())
            model.toggle("p-audit")
            assertFalse(model.state.value.dirty)
            model.toggleGroup("ORDER", on = true)
            assertTrue(model.state.value.dirty)
            assertEquals(setOf("p-read", "p-create"), model.state.value.permissionIds)
            val audit = model.state.value.groups.first { it.first == "SETTINGS" }.second.single()
            assertFalse(audit.grantable)
            model.savePermissions(); runCurrent()
            assertEquals(listOf("r-custom" to listOf("p-create", "p-read")), repo.permissionWrites)
            assertFalse(model.state.value.dirty)
            assertEquals("Permissions saved", model.state.value.notice)
        }
    }

    @Test
    fun aRoleHoldingPermissionsYouLackIsLocked() = runTest(dispatcher) {
        val repo = FakePeople().apply { rolePermissions["r-custom"] = listOf(READ, AUDIT) }
        check(repo) { model, _ ->
            model.select("r-custom"); runCurrent()
            assertFalse(model.state.value.canEditPermissions())
            model.toggle("p-create")
            assertEquals("This role has permissions you don't have, so only someone above you can change it", model.state.value.error)
        }
    }

    @Test
    fun aSuperAdminMayGiveAnything() = runTest(dispatcher) {
        check(roles = listOf("SUPER_ADMIN")) { model, repo ->
            model.select("r-custom"); runCurrent()
            model.toggle("p-audit")
            model.savePermissions(); runCurrent()
            assertEquals(listOf("r-custom" to listOf("p-audit", "p-read")), repo.permissionWrites)
        }
    }

    @Test
    fun discardingAndSwitchingRolesDropsUnsavedTicks() = runTest(dispatcher) {
        check { model, _ ->
            model.select("r-custom"); runCurrent()
            model.toggle("p-create")
            model.discardChanges()
            assertFalse(model.state.value.dirty)
            model.toggle("p-create")
            model.select("r-waiter"); runCurrent()
            assertEquals(setOf("p-read", "p-create"), model.state.value.permissionIds)
            assertFalse(model.state.value.dirty)
        }
    }

    @Test
    fun aRefreshKeepsTicksBeingEdited() = runTest(dispatcher) {
        check { model, _ ->
            model.select("r-custom"); runCurrent()
            model.toggle("p-create")
            model.refresh(); runCurrent()
            assertTrue(model.state.value.dirty)
            assertEquals(setOf("p-read", "p-create"), model.state.value.permissionIds)
        }
    }

    @Test
    fun newCopiedRenamedSwitchedOffAndRemovedRoles() = runTest(dispatcher) {
        check { model, repo ->
            model.startNew()
            model.formName("   ")
            model.saveForm(); runCurrent()
            assertEquals("Enter a role name", model.state.value.error)
            model.formName("waiter")
            model.saveForm(); runCurrent()
            assertEquals("A role called waiter already exists", model.state.value.error)
            model.formName("  Sommelier 🍷 ")
            model.formDescription(" Wine ")
            model.saveForm(); runCurrent()
            assertEquals("create Sommelier 🍷", repo.roleWrites.last())
            val created = model.state.value.roles.last()
            assertEquals("Sommelier 🍷", created.name)
            assertEquals(created.id, model.state.value.selectedId)

            model.startCopy("r-waiter")
            assertEquals(RoleForm.Mode.COPY, model.state.value.form?.mode)
            assertEquals("Waiter (copy)", model.state.value.form?.name)
            model.saveForm(); runCurrent()
            assertEquals("clone r-waiter Waiter (copy)", repo.roleWrites.last())
            assertEquals("r-copy", model.state.value.selectedId)
            assertEquals(setOf("p-read", "p-create"), model.state.value.savedPermissionIds)

            model.startRename("r-custom")
            model.formName("Greeter")
            model.saveForm(); runCurrent()
            assertEquals("Greeter", model.state.value.roles.first { it.id == "r-custom" }.name)

            model.setRoleActive("r-custom", false); runCurrent()
            assertFalse(model.state.value.roles.first { it.id == "r-custom" }.isActive)

            model.askDelete("r-custom"); model.confirmDelete(); runCurrent()
            assertNull(model.state.value.roles.firstOrNull { it.id == "r-custom" })
        }
    }

    @Test
    fun deletingTheOpenRoleOpensAnother() = runTest(dispatcher) {
        check { model, _ ->
            model.select("r-custom"); runCurrent()
            model.askDelete("r-custom"); model.confirmDelete(); runCurrent()
            assertEquals("r-owner", model.state.value.selectedId)
            assertEquals(setOf("p-read", "p-audit"), model.state.value.savedPermissionIds)
        }
    }

    @Test
    fun withoutRightsNothingChanges() = runTest(dispatcher) {
        check(permissions = listOf("ROLES_READ")) { model, repo ->
            model.select("r-custom"); runCurrent()
            model.toggle("p-read")
            assertEquals("You can't change what roles may do", model.state.value.error)
            model.startNew()
            assertNull(model.state.value.form)
            model.setRoleActive("r-custom", false); runCurrent()
            model.askDelete("r-custom")
            assertNull(model.state.value.confirmDelete)
            assertTrue(repo.roleWrites.isEmpty())
        }
    }

    @Test
    fun aMissingRoleOnSaveReloadsTheList() = runTest(dispatcher) {
        check { model, repo ->
            model.select("r-custom"); runCurrent()
            model.toggle("p-create")
            repo.failure = ApiException(404, "Role not found")
            model.savePermissions(); runCurrent()
            assertEquals("This no longer exists. It may have been removed on another device.", model.state.value.error)
            assertFalse(model.state.value.saving)
        }
    }
}

class PeopleApiTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val personJson = """{"id":"u1","email":"a@b.it","username":"ab","firstName":"A","lastName":"B","isActive":true,"roles":["WAITER"],"restaurantId":"r","defaultBranchId":"b"}"""

    private fun client(handler: suspend (io.ktor.client.request.HttpRequestData) -> String) = HttpClient(MockEngine { request ->
        val body = handler(request)
        if (body.isEmpty()) respond("", HttpStatusCode.NoContent) else respond(body, headers = jsonHeaders)
    }) { install(ContentNegotiation) { json(json) } }

    @Test
    fun listsSearchesAndWritesUseTheServersPathsAndFields() = runTest {
        val seen = mutableListOf<String>()
        val client = client { request ->
            val body = (request.body as? TextContent)?.text
            seen += "${request.method.value} ${request.url.encodedPath}" + (body?.let { " $it" } ?: "")
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/users" -> {
                    assertEquals("Ann Lee", request.url.parameters["search"])
                    assertEquals("false", request.url.parameters["active"])
                    assertEquals("3", request.url.parameters["page"])
                    assertEquals("40", request.url.parameters["size"])
                    assertNull(request.url.parameters["roleCode"])
                    """{"items":[$personJson],"page":3,"size":40,"totalElements":121,"totalPages":4,"hasNext":false}"""
                }
                request.url.encodedPath.endsWith("/roles") && request.method == HttpMethod.Get -> """[{"id":"r1","code":"WAITER","name":"Waiter"}]"""
                request.method == HttpMethod.Delete || request.url.encodedPath.endsWith("/reset-password") -> ""
                request.url.encodedPath.endsWith("/permissions") -> """[{"id":"p1","code":"ORDER_READ","name":"Read"}]"""
                request.url.encodedPath.startsWith("/roles") -> """{"id":"r9","code":"HOST","name":"Host"}"""
                else -> personJson
            }
        }
        try {
            val api = PeopleApi(client) { "http://localhost/" }
            val page = api.staff(StaffFilter(search = "  Ann Lee ", active = false), 3, 40)
            assertEquals(121, page.totalElements)
            assertEquals("b", page.items.single().defaultBranchId)
            api.create(CreateStaffRequestDto("a@b.it", "ab", "secret12", "A", "B", roleId = "r1", defaultBranchId = "b"))
            api.update("u1", UpdateStaffRequestDto("A", "B", null, isActive = false))
            api.replaceRoles("u1", listOf("r1", "r2"))
            api.sendPasswordReset("u1")
            api.remove("u1")
            api.rolesOf("u1")
            api.replacePermissions("r9", listOf("p1"))
            api.setRoleActive("r9", false)
            api.cloneRole("r1", RoleNameRequestDto("Host"))
            api.deleteRole("r9")
            assertTrue(seen.any { it.startsWith("POST /auth/register") && "\"roleId\":\"r1\"" in it && "\"defaultBranchId\":\"b\"" in it }, seen.toString())
            assertTrue(seen.any { it.startsWith("PUT /users/u1 ") && "\"isActive\":false" in it }, seen.toString())
            assertTrue(seen.any { it == "PUT /users/u1/roles {\"roleIds\":[\"r1\",\"r2\"]}" }, seen.toString())
            assertTrue(seen.any { it == "POST /users/u1/reset-password {\"channel\":\"EMAIL\"}" }, seen.toString())
            assertTrue("DELETE /users/u1" in seen)
            assertTrue(seen.any { it == "PUT /roles/r9/permissions {\"permissionIds\":[\"p1\"]}" }, seen.toString())
            assertTrue(seen.any { it == "PATCH /roles/r9/status {\"isActive\":false}" }, seen.toString())
            assertTrue(seen.any { it.startsWith("POST /roles/r1/clone") }, seen.toString())
            assertTrue("DELETE /roles/r9" in seen)
        } finally {
            client.close()
        }
    }

    @Test
    fun oddIdsAreEncodedInThePath() = runTest {
        val client = client { request ->
            assertEquals("/users/a%2Fb%20c", request.url.encodedPath)
            personJson
        }
        try {
            PeopleApi(client) { "http://localhost" }.person("a/b c")
        } finally {
            client.close()
        }
    }
}
