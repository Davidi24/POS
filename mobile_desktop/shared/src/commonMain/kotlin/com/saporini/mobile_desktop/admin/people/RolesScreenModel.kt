package com.saporini.mobile_desktop.admin.people

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A role being named: a new one, a renamed one, or a copy of [sourceId]. */
data class RoleForm(val roleId: String? = null, val sourceId: String? = null, val name: String = "", val description: String = "") {
    val mode: Mode get() = when {
        sourceId != null -> Mode.COPY
        roleId != null -> Mode.RENAME
        else -> Mode.NEW
    }

    enum class Mode { NEW, RENAME, COPY }
}

data class RolesState(
    val restaurantId: String? = null,
    val myPermissions: Set<String> = emptySet(),
    val superAdmin: Boolean = false,
    val canRead: Boolean = false,
    val canCreate: Boolean = false,
    val canUpdate: Boolean = false,
    val canDelete: Boolean = false,
    val canAssign: Boolean = false,
    val roles: List<RoleDto> = emptyList(),
    val catalog: List<PermissionDto> = emptyList(),
    val selectedId: String? = null,
    // What the selected role has on the server, and what the editor shows (equal until something is ticked).
    val savedPermissionIds: Set<String> = emptySet(),
    val permissionIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loadingRole: Boolean = false,
    val saving: Boolean = false,
    val form: RoleForm? = null,
    val confirmDelete: String? = null,
    val stale: Boolean = true,
    val notice: String? = null,
    val error: String? = null
) {
    val selected: RoleDto? get() = roles.firstOrNull { it.id == selectedId }
    val dirty: Boolean get() = permissionIds != savedPermissionIds
    private val codesById: Map<String, String> get() = catalog.associate { it.id to it.code }

    /** Built-in roles are fixed; custom roles can be changed by people who hold every permission they would give. */
    fun editable(role: RoleDto?): Boolean = role != null && !role.isSystem && !role.isProtected

    fun canEditPermissions(): Boolean = canAssign && editable(selected) &&
        canSavePermissions(savedPermissionIds.mapNotNull { codesById[it] }.toSet(), myPermissions, superAdmin)

    /** The editor's ticks, grouped by area; a permission the signed-in person lacks can't be given. */
    val groups: List<Pair<String, List<PermissionChoice>>> get() = permissionGroups(catalog.map {
        PermissionChoice(it, it.id in permissionIds, superAdmin || it.code in myPermissions)
    })
}

/**
 * Admin Hub → Permissions: the restaurant's roles, what each may do, and custom roles (new, copied from another,
 * renamed, switched off, removed). Built-in roles are read-only; permission changes are saved together.
 */
class RolesScreenModel(
    private val repository: PeopleRepository,
    session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(RolesState())
    val state: StateFlow<RolesState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    private var roleRevision = 0L
    // Changes when someone else signs in; a write's answer for the previous person is dropped.
    private var signIn = 0L
    private var active = false
    private var loadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                signIn++
                loadJob?.cancel()
                val permissions = user?.takeIf { it.isActive }?.permissions.orEmpty().toSet()
                mutable.value = RolesState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    myPermissions = permissions,
                    superAdmin = user?.roles.orEmpty().contains(SUPER_ADMIN),
                    canRead = "ROLES_READ" in permissions,
                    canCreate = "ROLES_CREATE" in permissions,
                    canUpdate = "ROLES_UPDATE" in permissions,
                    canDelete = "ROLES_DELETE" in permissions,
                    canAssign = "ROLES_ASSIGN_PERMISSIONS" in permissions
                )
                if (active) load()
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false, loadingRole = false) }
        }
    }

    fun refresh() = load()

    private fun load() {
        val current = state.value
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val roles = repository.roles().sortedWith(compareBy<RoleDto> { it.rank ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() })
                val catalog = if (current.catalog.isEmpty()) repository.permissions() else current.catalog
                if (token != revision) return@launch
                mutable.update { state ->
                    val keep = state.selectedId?.takeIf { id -> roles.any { it.id == id } }
                    state.copy(roles = roles, catalog = catalog, stale = false, error = null,
                        selectedId = keep ?: roles.firstOrNull()?.id)
                }
                val selected = state.value.selectedId
                // Ticks still being edited are kept; otherwise the role is read again.
                if (selected != null && !(selected == current.selectedId && current.dirty)) loadRole(selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(stale = true, error = adminMessage(e, write = false), canRead = it.canRead && !isDenied(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    /** Shows [roleId]'s permissions. Unsaved ticks on the previous role are dropped. */
    fun select(roleId: String) {
        if (state.value.roles.none { it.id == roleId }) return
        mutable.update { it.copy(selectedId = roleId, savedPermissionIds = emptySet(), permissionIds = emptySet(), error = null, notice = null) }
        loadRole(roleId)
    }

    private fun loadRole(roleId: String) {
        val token = ++roleRevision
        mutable.update { it.copy(loadingRole = true) }
        work.launch {
            try {
                val granted = repository.rolePermissions(roleId).map { it.id }.toSet()
                if (token == roleRevision && state.value.selectedId == roleId) {
                    mutable.update { it.copy(savedPermissionIds = granted, permissionIds = granted) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == roleRevision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            } finally {
                if (token == roleRevision) mutable.update { it.copy(loadingRole = false) }
            }
        }
    }

    // ---- Permission ticks ----

    fun toggle(permissionId: String) = tick(listOf(permissionId), on = permissionId !in state.value.permissionIds)

    /** Ticks or clears every permission of an area at once (only the ones the signed-in person may give). */
    fun toggleGroup(group: String, on: Boolean) =
        tick(state.value.catalog.filter { it.code.substringBefore('_') == group }.map { it.id }, on)

    private fun tick(ids: List<String>, on: Boolean) {
        val current = state.value
        if (current.loadingRole || !current.canEditPermissions()) {
            if (!current.loadingRole) mutable.update { it.copy(error = whyLocked(it)) }
            return
        }
        val allowed = current.catalog.filter { it.id in ids && (current.superAdmin || it.code in current.myPermissions) }.map { it.id }
        if (allowed.isEmpty()) return
        mutable.update { it.copy(permissionIds = if (on) it.permissionIds + allowed else it.permissionIds - allowed.toSet(), notice = null) }
    }

    fun discardChanges() = mutable.update { it.copy(permissionIds = it.savedPermissionIds) }

    fun savePermissions() {
        val current = state.value
        val roleId = current.selectedId ?: return
        if (!current.dirty || current.saving) return
        if (!current.canEditPermissions()) {
            mutable.update { it.copy(error = whyLocked(it)) }
            return
        }
        val ids = current.permissionIds
        write(onStale = { loadRole(roleId) }) {
            val saved = repository.replacePermissions(roleId, ids.sorted()).map { it.id }.toSet()
            mutable.update {
                if (it.selectedId == roleId) it.copy(savedPermissionIds = saved, permissionIds = saved, notice = "Permissions saved") else it
            }
        }
    }

    private fun whyLocked(state: RolesState): String = when {
        !state.canAssign -> "You can't change what roles may do"
        state.selected?.isSystem == true || state.selected?.isProtected == true -> "Built-in roles can't be changed. Copy it to make your own."
        else -> "This role has permissions you don't have, so only someone above you can change it"
    }

    // ---- Custom roles ----

    fun startNew() = openForm(RoleForm(), state.value.canCreate)

    fun startRename(roleId: String) {
        val role = state.value.roles.firstOrNull { it.id == roleId } ?: return
        if (!state.value.editable(role)) {
            mutable.update { it.copy(error = "Built-in roles can't be renamed") }
            return
        }
        openForm(RoleForm(roleId = roleId, name = role.name, description = role.description.orEmpty()), state.value.canUpdate)
    }

    fun startCopy(roleId: String) {
        val role = state.value.roles.firstOrNull { it.id == roleId } ?: return
        val name = "${role.name} (copy)".take(MAX_ROLE_NAME)
        openForm(RoleForm(sourceId = roleId, name = name, description = role.description.orEmpty()), state.value.canCreate)
    }

    private fun openForm(form: RoleForm, allowed: Boolean) {
        if (!allowed) {
            mutable.update { it.copy(error = "You don't have permission to do this.") }
            return
        }
        mutable.update { it.copy(form = form, error = null, notice = null) }
    }

    fun formName(text: String) = mutable.update { it.copy(form = it.form?.copy(name = text.take(MAX_ROLE_NAME + 20))) }

    fun formDescription(text: String) = mutable.update { it.copy(form = it.form?.copy(description = text.take(MAX_ROLE_DESCRIPTION + 20))) }

    fun cancelForm() = mutable.update { it.copy(form = null) }

    fun saveForm() {
        val current = state.value
        val form = current.form ?: return
        if (current.saving) return
        roleProblem(form.name, form.description)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        val name = form.name.trim()
        if (current.roles.any { it.name.equals(name, ignoreCase = true) && it.id != form.roleId }) {
            mutable.update { it.copy(error = "A role called $name already exists") }
            return
        }
        val request = RoleNameRequestDto(name, form.description.trim().takeIf { it.isNotEmpty() })
        write(onStale = ::load) {
            val saved = when (form.mode) {
                RoleForm.Mode.NEW -> repository.createRole(request)
                RoleForm.Mode.RENAME -> repository.renameRole(form.roleId!!, request)
                RoleForm.Mode.COPY -> repository.cloneRole(form.sourceId!!, request)
            }
            mutable.update { state ->
                val roles = if (state.roles.any { it.id == saved.id }) state.roles.map { if (it.id == saved.id) saved else it } else state.roles + saved
                state.copy(roles = roles, form = null, notice = if (form.mode == RoleForm.Mode.RENAME) "Saved" else "${saved.name} was added")
            }
            if (form.mode != RoleForm.Mode.RENAME) select(saved.id)
        }
    }

    /** Switches a custom role on or off; people keep it, but a switched-off role grants nothing. */
    fun setRoleActive(roleId: String, active: Boolean) {
        val current = state.value
        val role = current.roles.firstOrNull { it.id == roleId } ?: return
        if (!current.canUpdate || !current.editable(role)) {
            mutable.update { it.copy(error = if (!current.canUpdate) "You don't have permission to do this." else "Built-in roles are always on") }
            return
        }
        if (role.isActive == active) return
        write(onStale = ::load) {
            val saved = repository.setRoleActive(roleId, active)
            mutable.update { state -> state.copy(roles = state.roles.map { if (it.id == saved.id) saved else it }) }
        }
    }

    fun askDelete(roleId: String) {
        val current = state.value
        val role = current.roles.firstOrNull { it.id == roleId } ?: return
        if (!current.canDelete || !current.editable(role)) {
            mutable.update { it.copy(error = if (!current.canDelete) "You don't have permission to do this." else "Built-in roles can't be removed") }
            return
        }
        mutable.update { it.copy(confirmDelete = roleId) }
    }

    fun dismissDelete() = mutable.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val roleId = state.value.confirmDelete ?: return
        mutable.update { it.copy(confirmDelete = null) }
        write(onStale = ::load) {
            repository.deleteRole(roleId)
            mutable.update { state ->
                val roles = state.roles.filterNot { it.id == roleId }
                val wasSelected = state.selectedId == roleId
                state.copy(roles = roles, notice = "The role was removed",
                    selectedId = if (wasSelected) roles.firstOrNull()?.id else state.selectedId,
                    savedPermissionIds = if (wasSelected) emptySet() else state.savedPermissionIds,
                    permissionIds = if (wasSelected) emptySet() else state.permissionIds)
            }
            if (state.value.selectedId != null && state.value.savedPermissionIds.isEmpty()) loadRole(state.value.selectedId!!)
        }
    }

    private fun write(onStale: () -> Unit, action: suspend () -> Unit) {
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, notice = null) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = adminMessage(e, write = true)) }
                    if (isStale(e)) onStale()
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        roleRevision++
        active = false
        work.cancel()
    }
}
