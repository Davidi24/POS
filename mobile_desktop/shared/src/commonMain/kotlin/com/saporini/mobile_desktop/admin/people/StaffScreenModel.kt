package com.saporini.mobile_desktop.admin.people

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.ADMIN_PAGE_SIZE
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StaffState(
    val restaurantId: String? = null,
    val myId: String? = null,
    // The signed-in person's role codes (Users shows only the roles at their level or below).
    val myRoles: List<String> = emptyList(),
    val myBranchId: String? = null,
    val canRead: Boolean = false,
    val canCreate: Boolean = false,
    val canUpdate: Boolean = false,
    val canDelete: Boolean = false,
    val filter: StaffFilter = StaffFilter(active = true),
    val people: List<StaffUserDto> = emptyList(),
    val pages: Int = 0,
    // The page of people shown (Users → a role's people go page by page, PEOPLE_PAGE_SIZE at a time).
    val page: Int = 0,
    // How many people a page holds: as many as fit the list (the screen says), 10 until it does.
    val pageSize: Int = PEOPLE_PAGE_SIZE,
    val total: Long = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val stale: Boolean = true,
    // Roles this person may hand out (the server's choice: below their own, inside their restaurant).
    val assignableRoles: List<RoleDto> = emptyList(),
    val draft: StaffDraft? = null,
    val problems: Map<StaffField, String> = emptyMap(),
    val saving: Boolean = false,
    // Ids being changed by a quick action (switch on/off, password reset, remove).
    val busy: Set<String> = emptySet(),
    val confirmRemove: String? = null,
    // Whole-team numbers from the server (not from the loaded pages), for the summary cards and filters.
    val counts: StaffCounts? = null,
    // Per role code: how many have it (active, switched off) and the first few names, for the role cards.
    val roleStats: Map<String, RoleStats> = emptyMap(),
    // The role cards' numbers have been asked for once (loaded or not), so the page can stop showing its loading shapes.
    val roleStatsLoaded: Boolean = false,
    // The signed-in person's permissions (a person's own permissions can only gain what they have themselves).
    val myPermissions: Set<String> = emptySet(),
    val superAdmin: Boolean = false,
    // A person's own permissions being changed (Users → a role → a person's Permissions).
    val personPermissions: PersonPermissionsEditor? = null,
    val notice: String? = null,
    val error: String? = null
) {
    fun canEdit(person: StaffUserDto): Boolean = canUpdate && person.id != myId
    fun canRemove(person: StaffUserDto): Boolean = canDelete && person.id != myId
}

/**
 * One person's own permissions in the editor: what their roles give them ([fromRoles]), what they had when it opened
 * ([saved]) and what's ticked now ([ticked]).
 */
data class PersonPermissionsEditor(
    val personId: String,
    val name: String,
    val roleName: String,
    val fromRoles: Set<String> = emptySet(),
    val saved: Set<String> = emptySet(),
    val ticked: Set<String> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
) {
    val dirty: Boolean get() = ticked != saved
}

/** How many people a role's page shows at a time. */
const val PEOPLE_PAGE_SIZE = 10

/** One role's people for its card: how many are active and switched off, and the first few names (for the faces). */
data class RoleStats(val active: Long, val inactive: Long, val names: List<String>) {
    val everyone: Long get() = active + inactive
}

/** How many people are active, switched off, and in each role (by role code); null when not loaded. */
data class StaffCounts(val active: Long, val inactive: Long, val byRole: Map<String, Long>) {
    val everyone: Long get() = active + inactive
}

/**
 * Admin Hub → Users: the staff list (search, active/inactive, by role; paged, and a refresh reloads every page
 * already shown), adding and editing people, their roles, switching them on/off, password resets and removal.
 * Nobody edits or removes themselves here; the server enforces the same and keeps people inside their restaurant.
 */
class StaffScreenModel(
    private val repository: PeopleRepository,
    session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(StaffState())
    val state: StateFlow<StaffState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    // Changes when someone else signs in; a write's answer for the previous person is dropped.
    private var signIn = 0L
    private var active = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                signIn++
                loadJob?.cancel()
                val permissions = user?.takeIf { it.isActive }?.permissions.orEmpty().toSet()
                mutable.value = StaffState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    myId = user?.id,
                    myRoles = user?.roles.orEmpty(),
                    myBranchId = user?.defaultBranchId,
                    canRead = "USERS_READ" in permissions,
                    canCreate = "USERS_CREATE" in permissions,
                    canUpdate = "USERS_UPDATE" in permissions,
                    canDelete = "USERS_DELETE" in permissions,
                    myPermissions = permissions,
                    superAdmin = user?.roles.orEmpty().contains(SUPER_ADMIN)
                )
                if (active) load(keepPages = false)
            }
        }
    }

    /** The screen is shown (true) or hidden (false); loading only happens while it is shown. */
    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load(keepPages = true) else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    /** Searches by name, email or username; the server is asked once typing pauses. */
    fun search(text: String) {
        val clean = text.take(MAX_EMAIL)
        if (clean == state.value.filter.search) return
        searchJob?.cancel()
        mutable.update { it.copy(filter = it.filter.copy(search = clean)) }
        searchJob = work.launch {
            delay(SEARCH_DELAY_MILLIS)
            // The list shown stays until the answer comes, then changes in place: no loading shapes while typing (user,
            // 2026-10-31: "it shows the data, then starts loading").
            load(keepPages = false)
        }
    }

    fun showActive(active: Boolean?) = restart { it.copy(filter = it.filter.copy(active = active)) }

    fun role(roleCode: String?) = restart { it.copy(filter = it.filter.copy(roleCode = roleCode)) }

    private fun restart(change: (StaffState) -> StaffState) {
        val before = state.value.filter
        mutable.update(change)
        if (state.value.filter == before) return
        searchJob?.cancel()
        reload()
    }

    private fun reload() {
        revision++
        mutable.update { it.copy(people = emptyList(), pages = 0, page = 0, total = 0, hasNext = false, stale = true, error = null) }
        load(keepPages = false)
    }

    /** A page holds [size] people (as many as fit the screen); the first person shown stays on the page shown. */
    fun setPageSize(size: Int) {
        val current = state.value
        if (size < 1 || size == current.pageSize) return
        mutable.update { it.copy(pageSize = size, page = (current.page * current.pageSize) / size) }
        if (active && current.filter.roleCode != null) load(keepPages = true)
    }

    /** Shows another page of people. */
    fun goToPage(page: Int) {
        val current = state.value
        if (page < 0 || page == current.page || current.loading) return
        mutable.update { it.copy(page = page) }
        load(keepPages = true)
    }

    fun refresh() = load(keepPages = true)

    private fun load(keepPages: Boolean) {
        val current = state.value
        if (!active || !current.canRead || current.restaurantId == null) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                // One page of people (the one shown); a page that emptied (someone left) steps back to the last one.
                var page = if (keepPages) current.page else 0
                val size = current.pageSize
                var result = repository.staff(current.filter, page, size)
                if (result.items.isEmpty() && page > 0 && result.totalElements > 0) {
                    page = ((result.totalElements - 1) / size).toInt()
                    result = repository.staff(current.filter, page, size)
                }
                if (token != revision) return@launch
                mutable.update {
                    it.copy(people = result.items.distinctBy { person -> person.id }, page = page, pages = page + 1, total = result.totalElements,
                        hasNext = result.hasNext, stale = false, error = null)
                }
                if (current.canCreate || current.canUpdate) loadRoles(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(stale = true, error = adminMessage(e, write = false), canRead = it.canRead && !isDenied(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun loadRoles(token: Long) {
        try {
            val roles = repository.assignableRoles().filter { it.isActive && it.isAssignable && it.code !in PlatformRoleCodes }
            if (token == revision) mutable.update { it.copy(assignableRoles = roles) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Without the role list a person can still be edited; their roles just stay as they are.
        }
    }

    /** The next page, near the end of the list. */
    fun loadMore() {
        val current = state.value
        if (!active || current.loading || current.loadingMore || !current.hasNext) return
        val token = revision
        mutable.update { it.copy(loadingMore = true) }
        work.launch {
            try {
                val result = repository.staff(current.filter, current.pages, ADMIN_PAGE_SIZE)
                if (token == revision) mutable.update {
                    it.copy(people = (it.people + result.items).distinctBy { person -> person.id }, pages = it.pages + 1,
                        total = result.totalElements, hasNext = result.hasNext, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loadingMore = false) }
            }
        }
    }

    // ---- Adding and editing ----

    /** Opens the editor for a new person, with [roleId] already ticked when it's a role this person may give. */
    fun startNew(roleId: String? = null) {
        val current = state.value
        if (!current.canCreate) {
            mutable.update { it.copy(error = "You can't add staff") }
            return
        }
        val roles = setOfNotNull(roleId?.takeIf { id -> current.assignableRoles.any { it.id == id } })
        mutable.update { it.copy(draft = StaffDraft(defaultBranchId = it.myBranchId, roleIds = roles), problems = emptyMap(), error = null, notice = null) }
    }

    /** Opens [personId] for editing; their current roles are read from the server. */
    fun startEdit(personId: String) {
        val current = state.value
        val person = current.people.firstOrNull { it.id == personId } ?: return
        if (!current.canEdit(person)) {
            mutable.update { it.copy(error = if (person.id == current.myId) "Change your own details in your profile" else "You can't edit staff") }
            return
        }
        val token = signIn
        mutable.update { it.copy(draft = draftOf(person, emptySet()), problems = emptyMap(), error = null, notice = null, busy = it.busy + personId) }
        work.launch {
            try {
                val roles = repository.rolesOf(personId)
                if (token == signIn) mutable.update { state ->
                    state.copy(draft = state.draft?.takeIf { it.id == personId }?.copy(roleIds = roles.map { it.id }.toSet()) ?: state.draft)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == signIn) mutable.update { state -> state.copy(error = adminMessage(e, write = false), draft = state.draft?.takeUnless { it.id == personId }) }
            } finally {
                mutable.update { it.copy(busy = it.busy - personId) }
            }
        }
    }

    fun edit(change: (StaffDraft) -> StaffDraft) = mutable.update { state ->
        val draft = state.draft ?: return@update state
        val next = change(draft).let { it.copy(id = draft.id) }
        // Problems shown so far follow the typing; new ones only appear when saving.
        state.copy(draft = next, problems = staffProblems(next).filterKeys { it in state.problems })
    }

    fun toggleRole(roleId: String) = edit { draft ->
        draft.copy(roleIds = if (roleId in draft.roleIds) draft.roleIds - roleId else draft.roleIds + roleId)
    }

    fun cancelEdit() = mutable.update { it.copy(draft = null, problems = emptyMap()) }

    // ---- One person's own permissions ----

    /** Opens [personId]'s permissions: what their roles give them, with what's switched on or off for them alone. */
    fun openPermissions(personId: String, roleName: String) {
        val current = state.value
        val person = current.people.firstOrNull { it.id == personId } ?: return
        if (!current.canEdit(person)) return
        val token = signIn
        mutable.update { it.copy(personPermissions = PersonPermissionsEditor(personId, person.displayName, roleName), error = null, notice = null) }
        work.launch {
            try {
                val loaded = repository.personPermissions(personId)
                if (token == signIn) mutable.update { state ->
                    val editor = state.personPermissions?.takeIf { it.personId == personId } ?: return@update state
                    state.copy(personPermissions = editor.copy(fromRoles = loaded.fromRoles.toSet(), saved = loaded.permissions.toSet(),
                        ticked = loaded.permissions.toSet(), loading = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == signIn) mutable.update { state ->
                    state.copy(personPermissions = state.personPermissions?.takeIf { it.personId == personId }
                        ?.copy(loading = false, error = adminMessage(e, write = false)))
                }
            }
        }
    }

    /** Switches [codes] on or off for the person (one permission, or a whole area). */
    fun tickPersonPermissions(codes: List<String>, on: Boolean) = mutable.update { state ->
        val editor = state.personPermissions ?: return@update state
        if (editor.loading || editor.saving) return@update state
        // Nobody gives what they don't have themselves; taking it away is fine.
        val allowed = codes.filter { !on || state.superAdmin || it in state.myPermissions || it in editor.saved }
        state.copy(personPermissions = editor.copy(ticked = if (on) editor.ticked + allowed else editor.ticked - allowed.toSet(), error = null))
    }

    /** Back to what their roles give them, nothing extra and nothing taken away. */
    fun resetPersonPermissions() = mutable.update { state ->
        val editor = state.personPermissions ?: return@update state
        state.copy(personPermissions = editor.copy(ticked = editor.fromRoles, error = null))
    }

    fun savePersonPermissions() {
        val editor = state.value.personPermissions ?: return
        if (editor.loading || editor.saving || !editor.dirty) return
        val token = signIn
        mutable.update { it.copy(personPermissions = editor.copy(saving = true, error = null)) }
        work.launch {
            try {
                repository.savePersonPermissions(editor.personId, editor.ticked.sorted())
                if (token == signIn) mutable.update { it.copy(personPermissions = null, notice = "${editor.name}'s permissions were saved") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == signIn) mutable.update { state ->
                    state.copy(personPermissions = state.personPermissions?.copy(saving = false, error = adminMessage(e, write = true)))
                }
            }
        }
    }

    fun closePermissions() = mutable.update { it.copy(personPermissions = null) }

    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        if (current.saving) return
        if (draft.id != null && draft.id in current.busy) {
            mutable.update { it.copy(error = "Still loading this person's roles") }
            return
        }
        val problems = staffProblems(draft)
        if (problems.isNotEmpty()) {
            mutable.update { it.copy(problems = problems, error = "Check the highlighted fields") }
            return
        }
        if (draft.isNew && !current.canCreate || !draft.isNew && !current.canUpdate) {
            mutable.update { it.copy(error = "You don't have permission to do this.") }
            return
        }
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, problems = emptyMap()) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    val saved = if (draft.isNew) create(draft) else update(draft)
                    if (token != signIn) return@withLock
                    mutable.update { state ->
                        val matches = matchesFilter(saved, state.filter)
                        val known = state.people.any { it.id == saved.id }
                        state.copy(
                            draft = null,
                            people = when {
                                known && matches -> state.people.map { if (it.id == saved.id) saved else it }
                                known -> state.people.filterNot { it.id == saved.id }
                                matches -> listOf(saved) + state.people
                                else -> state.people
                            },
                            total = state.total + (if (!known && matches) 1 else 0) - (if (known && !matches) 1 else 0),
                            notice = if (draft.isNew) "${saved.displayName} was added" else "Saved"
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = adminMessage(e, write = true)) }
                    if (isStale(e)) refresh()
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    private suspend fun create(draft: StaffDraft): StaffUserDto {
        val roles = draft.roleIds.sorted()
        val created = repository.create(
            CreateStaffRequestDto(
                email = draft.email.trim(),
                username = draft.username.trim(),
                temporaryPassword = draft.temporaryPassword,
                firstName = draft.firstName.trim(),
                lastName = draft.lastName.trim(),
                phone = draft.phone.trim().takeIf { it.isNotEmpty() },
                roleId = roles.first(),
                defaultBranchId = draft.defaultBranchId
            )
        )
        // The account takes one role when it is made; any others follow.
        return if (roles.size > 1) repository.replaceRoles(created.id, roles) else created
    }

    private suspend fun update(draft: StaffDraft): StaffUserDto {
        val id = draft.id ?: error("Editing needs a person")
        val updated = repository.update(
            id,
            UpdateStaffRequestDto(
                firstName = draft.firstName.trim(),
                lastName = draft.lastName.trim(),
                phone = draft.phone.trim().takeIf { it.isNotEmpty() },
                isActive = draft.active,
                defaultBranchId = draft.defaultBranchId
            )
        )
        val before = repository.rolesOf(id).map { it.id }.toSet()
        return if (before != draft.roleIds) repository.replaceRoles(id, draft.roleIds.sorted()) else updated
    }

    // ---- Quick actions on a row ----

    /** Switches someone on or off without opening the editor (inactive people can't sign in). */
    fun setActive(personId: String, active: Boolean) {
        val current = state.value
        val person = current.people.firstOrNull { it.id == personId } ?: return
        if (!current.canEdit(person)) {
            mutable.update { it.copy(error = if (person.id == current.myId) "You can't switch yourself off" else "You can't edit staff") }
            return
        }
        if (person.isActive == active) return
        quick(personId, notice = if (active) "${person.displayName} can sign in again" else "${person.displayName} can no longer sign in") {
            val saved = repository.update(personId, UpdateStaffRequestDto(person.firstName, person.lastName, person.phone, active, person.defaultBranchId))
            mutable.update { state ->
                val people = if (matchesFilter(saved, state.filter)) state.people.map { if (it.id == saved.id) saved else it }
                else state.people.filterNot { it.id == saved.id }
                state.copy(people = people, total = state.total - (state.people.size - people.size))
            }
        }
    }

    fun sendPasswordReset(personId: String) {
        val current = state.value
        val person = current.people.firstOrNull { it.id == personId } ?: return
        if (!current.canEdit(person)) {
            mutable.update { it.copy(error = if (person.id == current.myId) "Change your own password in your profile" else "You can't edit staff") }
            return
        }
        quick(personId, notice = "A password reset email was sent to ${person.email}") { repository.sendPasswordReset(personId) }
    }

    fun askRemove(personId: String) {
        val current = state.value
        val person = current.people.firstOrNull { it.id == personId } ?: return
        if (!current.canRemove(person)) {
            mutable.update { it.copy(error = if (person.id == current.myId) "You can't remove yourself" else "You can't remove staff") }
            return
        }
        mutable.update { it.copy(confirmRemove = personId) }
    }

    fun dismissRemove() = mutable.update { it.copy(confirmRemove = null) }

    fun confirmRemove() {
        val personId = state.value.confirmRemove ?: return
        val person = state.value.people.firstOrNull { it.id == personId }
        mutable.update { it.copy(confirmRemove = null) }
        quick(personId, notice = "${person?.displayName ?: "The person"} was removed") {
            repository.remove(personId)
            mutable.update { state ->
                val people = state.people.filterNot { it.id == personId }
                state.copy(people = people, total = (state.total - (state.people.size - people.size)).coerceAtLeast(0))
            }
        }
    }

    private fun quick(personId: String, notice: String, action: suspend () -> Unit) {
        if (personId in state.value.busy) return
        val token = signIn
        mutable.update { it.copy(busy = it.busy + personId, error = null, notice = null) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    action()
                    if (token == signIn) mutable.update { it.copy(notice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = adminMessage(e, write = true)) }
                    if (isStale(e)) refresh()
                } finally {
                    mutable.update { it.copy(busy = it.busy - personId) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    /**
     * Reads the team's totals (active, switched off, per role) with one tiny page each, so the cards and filters
     * show real numbers however much of the list is loaded. Failures leave the previous numbers.
     */
    fun loadCounts() {
        val current = state.value
        if (!current.canRead || current.restaurantId == null) return
        val token = signIn
        work.launch {
            try {
                val active = repository.staff(StaffFilter(active = true), 0, 1).totalElements
                val inactive = repository.staff(StaffFilter(active = false), 0, 1).totalElements
                val roles = state.value.assignableRoles.associate { role ->
                    role.code to repository.staff(StaffFilter(active = true, roleCode = role.code), 0, 1).totalElements
                }
                if (token == signIn) mutable.update { it.copy(counts = StaffCounts(active, inactive, roles)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The numbers are a summary; the list itself reports problems.
            }
        }
    }

    /**
     * Reads each role's people for the role cards: the active ones (with the first few names) and the switched-off
     * ones, a tiny page each. Failures leave the previous numbers.
     */
    fun loadRoleStats(roleCodes: List<String>) {
        val current = state.value
        if (!current.canRead || roleCodes.isEmpty()) return
        if (current.restaurantId == null) {
            mutable.update { it.copy(roleStatsLoaded = true) }
            return
        }
        val token = signIn
        work.launch {
            try {
                val stats = coroutineScope {
                    roleCodes.distinct().map { code ->
                        async {
                            val active = repository.staff(StaffFilter(active = true, roleCode = code), 0, ROLE_FACES)
                            val inactive = repository.staff(StaffFilter(active = false, roleCode = code), 0, 1).totalElements
                            code to RoleStats(active.totalElements, inactive, active.items.map { it.displayName })
                        }
                    }.awaitAll().toMap()
                }
                if (token == signIn) mutable.update { it.copy(roleStats = it.roleStats + stats, roleStatsLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The cards are a summary; the list itself reports problems.
                if (token == signIn) mutable.update { it.copy(roleStatsLoaded = true) }
            }
        }
    }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }

    companion object {
        const val SEARCH_DELAY_MILLIS = 300L
        private const val ROLE_FACES = 5

        internal fun draftOf(person: StaffUserDto, roleIds: Set<String>) = StaffDraft(
            id = person.id,
            email = person.email,
            username = person.username,
            firstName = person.firstName,
            lastName = person.lastName,
            phone = person.phone.orEmpty(),
            active = person.isActive,
            roleIds = roleIds,
            defaultBranchId = person.defaultBranchId
        )

        /** Whether [person] still belongs in a list filtered by [filter] (search is left to the server). */
        internal fun matchesFilter(person: StaffUserDto, filter: StaffFilter): Boolean =
            (filter.active == null || person.isActive == filter.active) &&
                (filter.roleCode == null || filter.roleCode in person.roles)
    }
}
