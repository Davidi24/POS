package com.saporini.mobile_desktop.platform

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.people.CreateStaffRequestDto
import com.saporini.mobile_desktop.admin.people.MAX_PASSWORD
import com.saporini.mobile_desktop.admin.people.MIN_PASSWORD
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.admin.people.StaffFilter
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.admin.people.UpdateStaffRequestDto
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.isSuperAdmin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val CO_SUPER_ADMIN_ROLE = "CO_SUPER_ADMIN"

data class NewCoSuperAdminDraft(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val username: String = "",
    val usernameTouched: Boolean = false,
    val password: String = "",
    val showProblems: Boolean = false
)

fun coSuperAdminProblems(draft: NewCoSuperAdminDraft): Map<String, String> = buildMap {
    lengthProblem(draft.firstName, 50, true, "a first name")?.let { put("firstName", it) }
    lengthProblem(draft.lastName, 50, true, "a last name")?.let { put("lastName", it) }
    emailProblem(draft.email, required = true)?.let { put("email", it) }
    usernameProblem(draft.username)?.let { put("username", it) }
    when {
        draft.password.length < MIN_PASSWORD -> put("password", "At least $MIN_PASSWORD characters")
        draft.password.length > MAX_PASSWORD -> put("password", "At most $MAX_PASSWORD characters")
    }
}

/** The Super Admin's ticks for one Co-Super-Admin, being edited. */
data class PermissionTicks(
    val person: StaffUserDto,
    val permissions: List<CoSuperAdminPermissionDto> = emptyList(),
    val granted: Set<String> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
) {
    val changed: Boolean get() = granted != permissions.filter { it.granted }.map { it.code }.toSet()
}

/** A support admin's restaurants being chosen: every restaurant, or some; and whether they may remove owners. */
data class AdminAccessEditor(
    val adminId: String,
    val name: String,
    val allRestaurants: Boolean = false,
    val mayRemoveOwners: Boolean = false,
    val chosen: Set<String> = emptySet(),
    val restaurants: List<RestaurantRefDto> = emptyList(),
    val search: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
)

data class CoSuperAdminsState(
    val allowed: Boolean = false,
    // Support admins (made by the Super Admin) or restaurant admins (made by an owner) on the Admins page (user, 2026-11-19).
    val view: String = "SUPPORT",
    val cards: List<AdminCardDto> = emptyList(),
    val access: AdminAccessEditor? = null,
    val people: List<StaffUserDto> = emptyList(),
    // How many permissions each person has on, out of how many (for the cards).
    val allowedCounts: Map<String, Pair<Int, Int>> = emptyMap(),
    val roleId: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val draft: NewCoSuperAdminDraft? = null,
    val saving: Boolean = false,
    val ticks: PermissionTicks? = null,
    val confirmRemove: StaffUserDto? = null,
    // People being changed by a quick action (switch off, password email, remove).
    val busy: Set<String> = emptySet(),
    val notice: String? = null,
    val error: String? = null
)

/**
 * The Super Admin's Co-Super-Admins: adding one (they get an email to confirm, then sign in with the temporary
 * password), the permissions each one keeps, switching one off, and removing one.
 */
class CoSuperAdminsScreenModel(
    private val people: PeopleRepository,
    private val platform: PlatformRepository,
    session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(CoSuperAdminsState(allowed = isSuperAdmin(session.currentUser.value)))
    val state: StateFlow<CoSuperAdminsState> = mutable.asStateFlow()
    private var revision = 0L
    private var loadJob: Job? = null
    private var active = false

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false) }
        }
    }

    fun refresh() = load()

    private fun load() {
        if (!active || !state.value.allowed) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val (list, roles) = coroutineScope {
                    val roles = async { people.assignableRoles() }
                    people.staff(StaffFilter(roleCode = CO_SUPER_ADMIN_ROLE), 0, 100) to roles.await()
                }
                if (token != revision) return@launch
                mutable.update {
                    it.copy(people = list.items.sortedBy { person -> person.displayName.lowercase() },
                        roleId = roles.firstOrNull { role -> role.code == CO_SUPER_ADMIN_ROLE }?.id, loaded = true, error = null)
                }
                // Each card shows how much that person may do; a count that can't be read is simply left out.
                val counts = coroutineScope {
                    list.items.map { person ->
                        async { person.id to runCatching { platform.coSuperAdminPermissions(person.id) }.getOrNull() }
                    }.map { it.await() }
                }.mapNotNull { (id, permissions) -> permissions?.let { id to (it.count { p -> p.granted } to it.size) } }.toMap()
                if (token == revision) mutable.update { it.copy(allowedCounts = counts) }
                val cards = runCatching { platform.admins() }.getOrNull()
                if (token == revision && cards != null) mutable.update { it.copy(cards = cards) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    fun showView(view: String) = mutable.update { it.copy(view = view) }

    // ---- A support admin's restaurants ----

    fun openAccess(person: StaffUserDto) {
        mutable.update { it.copy(access = AdminAccessEditor(person.id, person.displayName), error = null) }
        work.launch {
            try {
                val (access, restaurants) = coroutineScope {
                    val access = async { platform.supportAdminAccess(person.id) }
                    val all = async {
                        val first = platform.cards("", null, 0, 100)
                        first.items.map { RestaurantRefDto(it.id, it.name) }
                    }
                    access.await() to all.await()
                }
                mutable.update { state ->
                    val editor = state.access?.takeIf { it.adminId == person.id } ?: return@update state
                    state.copy(access = editor.copy(allRestaurants = access.allRestaurants, mayRemoveOwners = access.mayRemoveOwners,
                        chosen = access.restaurants.map { it.id }.toSet(), restaurants = restaurants, loading = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(access = state.access?.copy(loading = false, error = platformMessage(e, write = false))) }
            }
        }
    }

    fun editAccess(change: (AdminAccessEditor) -> AdminAccessEditor) = mutable.update { state ->
        state.copy(access = state.access?.let { if (it.loading || it.saving) it else change(it) })
    }

    fun closeAccess() = mutable.update { it.copy(access = null) }

    fun saveAccess() {
        val editor = state.value.access ?: return
        if (editor.loading || editor.saving) return
        mutable.update { it.copy(access = editor.copy(saving = true, error = null)) }
        work.launch {
            try {
                platform.setSupportAdminAccess(editor.adminId, UpdateSupportAdminAccessDto(editor.allRestaurants, editor.mayRemoveOwners,
                    if (editor.allRestaurants) emptyList() else editor.chosen.toList()))
                mutable.update { it.copy(access = null, notice = "${editor.name}'s restaurants were saved") }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(access = state.access?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- Adding ----

    fun startAdd() = mutable.update { it.copy(draft = NewCoSuperAdminDraft(), notice = null, error = null) }

    fun editDraft(change: (NewCoSuperAdminDraft) -> NewCoSuperAdminDraft) = mutable.update { state ->
        val draft = state.draft ?: return@update state
        var next = change(draft)
        if (next.email != draft.email && !next.usernameTouched) next = next.copy(username = usernameFrom(next.email))
        state.copy(draft = next)
    }

    fun cancelAdd() {
        if (!state.value.saving) mutable.update { it.copy(draft = null) }
    }

    fun saveAdd() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.saving) return
        if (coSuperAdminProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(draft = draft.copy(showProblems = true)) }
            return
        }
        val roleId = current.roleId ?: run {
            mutable.update { it.copy(error = "The Co-Super-Admin role isn't available. Refresh and try again.") }
            return
        }
        mutable.update { it.copy(saving = true, error = null) }
        work.launch {
            try {
                val created = people.create(CreateStaffRequestDto(
                    email = draft.email.trim(), username = draft.username.trim(), temporaryPassword = draft.password,
                    firstName = draft.firstName.trim(), lastName = draft.lastName.trim(), roleId = roleId
                ))
                mutable.update {
                    it.copy(draft = null, people = (it.people + created).sortedBy { person -> person.displayName.lowercase() },
                        notice = "${created.displayName} was added. They get an email to confirm it, then sign in with the temporary password. " +
                            "Choose below what they can do.")
                }
                openPermissions(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    // ---- Permissions ----

    fun openPermissions(person: StaffUserDto) {
        mutable.update { it.copy(ticks = PermissionTicks(person)) }
        work.launch {
            try {
                val permissions = platform.coSuperAdminPermissions(person.id)
                mutable.update { state ->
                    val ticks = state.ticks?.takeIf { it.person.id == person.id } ?: return@update state
                    state.copy(ticks = ticks.copy(permissions = permissions, granted = permissions.filter { it.granted }.map { it.code }.toSet(), loading = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(ticks = state.ticks?.copy(loading = false, error = platformMessage(e, write = false))) }
            }
        }
    }

    fun toggle(code: String) = mutable.update { state ->
        val ticks = state.ticks ?: return@update state
        state.copy(ticks = ticks.copy(granted = if (code in ticks.granted) ticks.granted - code else ticks.granted + code))
    }

    /** Ticks or unticks every permission of one area. */
    fun toggleArea(area: String) = mutable.update { state ->
        val ticks = state.ticks ?: return@update state
        val codes = ticks.permissions.filter { permissionArea(it.code) == area }.map { it.code }.toSet()
        val allOn = ticks.granted.containsAll(codes)
        state.copy(ticks = ticks.copy(granted = if (allOn) ticks.granted - codes else ticks.granted + codes))
    }

    fun setAll(on: Boolean) = mutable.update { state ->
        val ticks = state.ticks ?: return@update state
        state.copy(ticks = ticks.copy(granted = if (on) ticks.permissions.map { it.code }.toSet() else emptySet()))
    }

    fun closePermissions() {
        if (state.value.ticks?.saving != true) mutable.update { it.copy(ticks = null) }
    }

    fun savePermissions() {
        val ticks = state.value.ticks ?: return
        if (ticks.saving || ticks.loading) return
        mutable.update { it.copy(ticks = ticks.copy(saving = true, error = null)) }
        work.launch {
            try {
                platform.setCoSuperAdminPermissions(ticks.person.id, ticks.granted.toList())
                mutable.update {
                    it.copy(ticks = null, allowedCounts = it.allowedCounts + (ticks.person.id to (ticks.granted.size to ticks.permissions.size)), notice = "${ticks.person.displayName} can now do ${ticks.granted.size} of ${ticks.permissions.size} things. " +
                        "It applies from their next click.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(ticks = state.ticks?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- Quick actions ----

    fun setPersonActive(person: StaffUserDto, active: Boolean) = quick(person, if (active) "${person.displayName} can sign in again" else
        "${person.displayName} can't sign in until you switch them on") {
        val saved = people.update(person.id, UpdateStaffRequestDto(person.firstName, person.lastName, person.phone, active, null))
        mutable.update { state -> state.copy(people = state.people.map { if (it.id == saved.id) saved else it }) }
    }

    fun sendPasswordEmail(person: StaffUserDto) = quick(person, "A password reset email was sent to ${person.email}") {
        people.sendPasswordReset(person.id)
    }

    fun askRemove(person: StaffUserDto) = mutable.update { it.copy(confirmRemove = person) }

    fun dismissRemove() = mutable.update { it.copy(confirmRemove = null) }

    fun confirmRemove() {
        val person = state.value.confirmRemove ?: return
        mutable.update { it.copy(confirmRemove = null) }
        quick(person, "${person.displayName} was removed and can no longer sign in") {
            people.remove(person.id)
            mutable.update { state -> state.copy(people = state.people.filter { it.id != person.id }) }
        }
    }

    private fun quick(person: StaffUserDto, notice: String, action: suspend () -> Unit) {
        if (person.id in state.value.busy) return
        mutable.update { it.copy(busy = it.busy + person.id, error = null, notice = null) }
        work.launch {
            try {
                action()
                mutable.update { it.copy(notice = notice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(busy = it.busy - person.id) }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }
}
