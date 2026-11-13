package com.saporini.mobile_desktop.platform

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.session.ActingRestaurant
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.isSuperAdmin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 48

enum class RestaurantFilter(val label: String, val server: String?) { ALL("All", null), OPEN("Open", "open"), CLOSED("Closed or suspended", "closed") }

/** A restaurant being created (step 1; its owner is step 2). */
data class NewRestaurantDraft(
    val name: String = "",
    val legalName: String = "",
    val currency: String = "EUR",
    val timezone: String = "Europe/Tirane",
    val firstBranchName: String = "Main",
    val email: String = "",
    val phone: String = "",
    // The card's cover picture, uploaded right after the restaurant is created.
    val cover: PickedPicture? = null,
    val showProblems: Boolean = false
)

fun newRestaurantProblems(draft: NewRestaurantDraft): Map<String, String> = buildMap {
    lengthProblem(draft.name, MAX_RESTAURANT_NAME, true, "the restaurant's name")?.let { put("name", it) }
    lengthProblem(draft.legalName, MAX_LEGAL_NAME, true, "the legal name")?.let { put("legalName", it) }
    if (draft.currency.isBlank()) put("currency", "Choose a currency")
    if (draft.timezone.isBlank()) put("timezone", "Choose a time zone")
    lengthProblem(draft.firstBranchName, MAX_BRANCH_NAME)?.let { put("branch", it) }
    // Guests see both on the booking pages and in their emails.
    emailProblem(draft.email, required = true)?.let { put("email", it) }
    (if (draft.phone.isBlank()) "Enter a phone number" else phoneProblem(draft.phone))?.let { put("phone", it) }
}

enum class EditTab(val label: String) { RESTAURANT("Restaurant"), OWNER("Owner") }

/** Where the person wanted to go when they still had changes on the open tab. */
sealed interface EditLeave {
    data class Tab(val tab: EditTab) : EditLeave
    data object Close : EditLeave
}

/**
 * Editing a restaurant from its card, in two tabs saved by two different calls: the restaurant (cover picture, name,
 * contact) and its owner (correcting them, or adding one). Leaving a tab with changes asks to save or discard them.
 */
data class EditRestaurantStep(
    val card: RestaurantCardDto,
    val tab: EditTab = EditTab.RESTAURANT,
    val restaurant: RestaurantDto? = null,
    val ownerPerson: com.saporini.mobile_desktop.admin.people.StaffUserDto? = null,
    val loading: Boolean = true,
    val name: String = card.name,
    val legalName: String = "",
    val phone: String = "",
    val email: String = "",
    val cover: PickedPicture? = null,
    val removeCover: Boolean = false,
    val owner: OwnerDraft = OwnerDraft(),
    val leaving: EditLeave? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val showProblems: Boolean = false
) {
    val restaurantChanged: Boolean
        get() = restaurant != null && (name.trim() != restaurant.name || legalName.trim() != restaurant.legalName ||
            phone.trim() != restaurant.phone.orEmpty() || email.trim() != restaurant.email.orEmpty() || cover != null ||
            (removeCover && card.coverUrl != null))

    val ownerChanged: Boolean
        get() = ownerPerson?.let { person ->
            owner.firstName.trim() != person.firstName || owner.lastName.trim() != person.lastName ||
                owner.email.trim().lowercase() != person.email.lowercase() || owner.phone.trim() != person.phone.orEmpty()
        } ?: listOf(owner.firstName, owner.lastName, owner.email, owner.phone).any { it.isNotBlank() }

    /** Something on the open tab isn't saved yet. */
    val changed: Boolean get() = if (tab == EditTab.RESTAURANT) restaurantChanged else ownerChanged

    /** The email of an owner who already set up their account is theirs to change. */
    val ownerEmailLocked: Boolean get() = ownerPerson?.emailVerified == true

    /** Back to what's saved, for the open tab. */
    fun discardTab(): EditRestaurantStep = when (tab) {
        EditTab.RESTAURANT -> copy(name = restaurant?.name ?: name, legalName = restaurant?.legalName.orEmpty(), phone = restaurant?.phone.orEmpty(),
            email = restaurant?.email.orEmpty(), cover = null, removeCover = false, showProblems = false, error = null)
        EditTab.OWNER -> copy(owner = ownerDraftOf(ownerPerson), showProblems = false, error = null)
    }
}

fun ownerDraftOf(person: com.saporini.mobile_desktop.admin.people.StaffUserDto?) =
    person?.let { OwnerDraft(it.firstName, it.lastName, it.email, it.phone.orEmpty(), it.username, usernameTouched = true) } ?: OwnerDraft()

fun editProblems(step: EditRestaurantStep): Map<String, String> = buildMap {
    if (step.tab == EditTab.RESTAURANT) {
        lengthProblem(step.name, MAX_RESTAURANT_NAME, true, "the restaurant's name")?.let { put("name", it) }
        lengthProblem(step.legalName, MAX_LEGAL_NAME, true, "the legal name")?.let { put("legalName", it) }
        emailProblem(step.email, required = true)?.let { put("email", it) }
        (if (step.phone.isBlank()) "Enter a phone number" else phoneProblem(step.phone))?.let { put("phone", it) }
    } else {
        ownerProblems(step.owner).forEach { (key, value) -> put("owner.$key", value) }
    }
}

/** Totals over every restaurant, for the welcome banner ([partial] while not every page is loaded). */
data class ListSummary(val restaurants: Long, val open: Int, val branches: Long, val staff: Long, val partial: Boolean)

data class RestaurantListState(
    val superAdmin: Boolean = false,
    val canRead: Boolean = false,
    val canManage: Boolean = false,
    val canCreate: Boolean = false,
    val firstName: String = "",
    val cards: List<RestaurantCardDto> = emptyList(),
    val summary: ListSummary? = null,
    val total: Long = 0,
    val page: Int = 0,
    val hasNext: Boolean = false,
    val search: String = "",
    val filter: RestaurantFilter = RestaurantFilter.ALL,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val draft: NewRestaurantDraft? = null,
    // Between the two steps: "Restaurant created" shows for a moment before the owner step.
    val created: RestaurantDto? = null,
    // Step 2 of creating a restaurant, or adding the owner of one that has none.
    val owner: OwnerStep? = null,
    // "+ Add branch" on a restaurant's card.
    val branch: BranchStep? = null,
    // The pen on a restaurant's card.
    val edit: EditRestaurantStep? = null,
    val saving: Boolean = false,
    // The restaurant being opened (its branches are loading).
    val entering: String? = null,
    // Highlighted for a moment after it was created.
    val justCreated: String? = null,
    // Restaurants whose cover picture is being changed.
    val coverBusy: Set<String> = emptySet(),
    // "Change position": the cards being dragged into a new order, not saved yet.
    val reorder: List<RestaurantCardDto>? = null,
    val savingOrder: Boolean = false,
    // Restaurant applications waiting for review (shown on the welcome banner).
    val pendingApplications: Long = 0,
    val notice: String? = null,
    val error: String? = null
)

/**
 * The super admins' home: every restaurant, creating a new one (with its owner's invitation), and entering one. A
 * restaurant with several open branches asks which branch first.
 */
class RestaurantListScreenModel(
    private val repository: PlatformRepository,
    private val people: com.saporini.mobile_desktop.admin.people.PeopleRepository,
    private val session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
    private val mutable = MutableStateFlow(RestaurantListState())
    val state: StateFlow<RestaurantListState> = mutable.asStateFlow()
    private var revision = 0L
    private var loadJob: Job? = null
    private var entryJob: Job? = null
    private var active = false

    init {
        work.watchRestaurantPermissions(session) { active }
        work.launch {
            session.currentUser.collectLatest { user ->
                actions.cancel()
                actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
                revision++
                val access = restaurantAccess(user)
                mutable.value = RestaurantListState(
                    superAdmin = isSuperAdmin(user),
                    canRead = com.saporini.mobile_desktop.core.session.isPlatformAdmin(user) && access.read,
                    canManage = access.manage,
                    canCreate = access.create,
                    firstName = user?.firstName?.trim().orEmpty().ifBlank { user?.username.orEmpty() }
                )
                if (active) load(reset = true)
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load(reset = true) else {
            revision++
            loadJob?.cancel()
            entryJob?.cancel()
            mutable.update { it.copy(entering = null, loading = false, loadingMore = false) }
        }
    }

    fun refresh() { if (state.value.reorder == null) load(reset = true) }

    fun search(text: String) {
        if (text == state.value.search) return
        if (state.value.reorder != null) return
        entryJob?.cancel()
        mutable.update { it.copy(entering = null, search = text.take(100), cards = emptyList(), page = 0, hasNext = false, loaded = false, error = null) }
        load(reset = true)
    }

    fun filter(filter: RestaurantFilter) {
        if (filter == state.value.filter) return
        if (state.value.reorder != null) return
        entryJob?.cancel()
        mutable.update { it.copy(entering = null, filter = filter, cards = emptyList(), page = 0, hasNext = false, loaded = false, error = null) }
        load(reset = true)
    }

    fun loadMore() {
        val current = state.value
        if (!current.hasNext || current.loading || current.loadingMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (!active || !state.value.canRead) return
        val current = state.value
        val token = ++revision
        loadJob?.cancel()
        loadJob = actions.launch {
            mutable.update { if (reset) it.copy(loading = true) else it.copy(loadingMore = true) }
            try {
                session.requireRestaurantAccess { it.read }
                // A refresh reloads every page already shown, so the list keeps its place.
                val pages = if (reset) (current.page + 1).coerceAtLeast(1) else 1
                val first = if (reset) 0 else current.page + 1
                val loaded = mutableListOf<RestaurantCardDto>()
                var last: PageDto<RestaurantCardDto>? = null
                for (page in first until first + pages) {
                    val result = repository.cards(current.search, current.filter.server, page, PAGE_SIZE)
                    loaded += result.items
                    last = result
                    if (!result.hasNext) break
                }
                // How many applications wait for review; the list works without it.
                val pending = if (reset) runCatching { repository.applications("PENDING", "", 0, 1).totalElements }.getOrNull() else null
                if (token != revision) return@launch
                mutable.update { state ->
                    val cards = if (reset) loaded else (state.cards + loaded).distinctBy { it.id }
                    val total = last?.totalElements ?: cards.size.toLong()
                    val hasNext = last?.hasNext ?: false
                    // The banner counts every restaurant, so only an unfiltered list updates it.
                    val summary = if (current.filter == RestaurantFilter.ALL && current.search.isBlank()) {
                        ListSummary(total, cards.count { it.open }, cards.sumOf { it.branches }, cards.sumOf { it.staff }, hasNext)
                    } else state.summary
                    state.copy(cards = cards, summary = summary, total = total, page = last?.page ?: 0, hasNext = hasNext, loaded = true, error = null,
                        pendingApplications = pending ?: state.pendingApplications)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false, loadingMore = false) }
            }
        }
    }

    // ---- Creating a restaurant ----

    fun startCreate() {
        if (!state.value.canCreate) return
        mutable.update { it.copy(draft = NewRestaurantDraft(), notice = null, error = null) }
    }

    fun editDraft(change: (NewRestaurantDraft) -> NewRestaurantDraft) = mutable.update { state ->
        val draft = state.draft ?: return@update state
        var next = change(draft)
        // The legal name starts as the name until it's typed by hand.
        if (next.name != draft.name && (draft.legalName.isBlank() || draft.legalName == draft.name)) next = next.copy(legalName = next.name)
        state.copy(draft = next)
    }

    fun cancelCreate() {
        if (!state.value.saving) mutable.update { it.copy(draft = null) }
    }

    fun saveCreate() {
        if (!state.value.canCreate) return
        val draft = state.value.draft ?: return
        if (state.value.saving) return
        if (newRestaurantProblems(draft).isNotEmpty()) {
            mutable.update { it.copy(draft = draft.copy(showProblems = true)) }
            return
        }
        val request = CreateRestaurantRequestDto(
            name = draft.name.trim(),
            legalName = draft.legalName.trim(),
            email = draft.email.trim().ifBlank { null },
            phone = draft.phone.trim().ifBlank { null },
            currency = draft.currency,
            timezone = draft.timezone,
            firstBranchName = draft.firstBranchName.trim().ifBlank { null }
        )
        mutable.update { it.copy(saving = true, error = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.create }
                val created = repository.createRestaurant(request)
                draft.cover?.takeIf { state.value.canManage }?.let { cover ->
                    // The restaurant exists either way; a picture that didn't upload can be added from its card.
                    runCatching { repository.uploadCover(created.id, null, cover.bytes, cover.fileName, cover.contentType) }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            mutable.update { it.copy(error = "${created.name} was created, but its picture couldn't be saved. Add it from its card.") }
                        }
                }
                // "Restaurant created" for a moment, then step 2: its owner. The list shows the new card once the setup closes.
                mutable.update { it.copy(draft = null, created = created) }
                load(reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    fun clearJustCreated() = mutable.update { it.copy(justCreated = null) }

    /** After the "Restaurant created" moment: on to its owner. */
    fun continueToOwner() = mutable.update { state ->
        val created = state.created ?: return@update state
        state.copy(created = null, owner = if (state.canManage) OwnerStep(created.id, created.name, afterCreate = true) else null, justCreated = created.id)
    }

    // ---- Editing a restaurant from its card ----

    fun startEdit(card: RestaurantCardDto) {
        if (!state.value.canManage) return
        mutable.update { it.copy(edit = EditRestaurantStep(card), notice = null, error = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val restaurant = repository.restaurant(card.id)
                val owner = restaurant.ownerUserId?.let { id -> runCatching { people.person(id) }.getOrNull() }
                mutable.update { state ->
                    val step = state.edit?.takeIf { it.card.id == card.id } ?: return@update state
                    state.copy(edit = step.copy(restaurant = restaurant, ownerPerson = owner, loading = false).discardRestaurant().copy(owner = ownerDraftOf(owner)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(edit = state.edit?.copy(loading = false, error = platformMessage(e, write = false))) }
            }
        }
    }

    private fun EditRestaurantStep.discardRestaurant() = copy(tab = EditTab.RESTAURANT).discardTab().copy(tab = tab)

    fun changeEdit(change: (EditRestaurantStep) -> EditRestaurantStep) = mutable.update { state ->
        state.edit?.let { state.copy(edit = change(it)) } ?: state
    }

    fun editOwnerFields(change: (OwnerDraft) -> OwnerDraft) = changeEdit { it.copy(owner = it.owner.edit(change)) }

    /** Opens the other tab, or first asks to save or discard what changed on this one. */
    fun editTab(tab: EditTab) = mutable.update { state ->
        val step = state.edit ?: return@update state
        when {
            step.tab == tab || step.saving -> state
            step.changed -> state.copy(edit = step.copy(leaving = EditLeave.Tab(tab)))
            else -> state.copy(edit = step.copy(tab = tab, error = null, showProblems = false))
        }
    }

    /** Closes the dialog, or first asks to save or discard what changed. */
    fun cancelEdit() = mutable.update { state ->
        val step = state.edit ?: return@update state
        when {
            step.saving -> state
            step.changed && !step.loading -> state.copy(edit = step.copy(leaving = EditLeave.Close))
            else -> state.copy(edit = null)
        }
    }

    fun stayEditing() = changeEdit { it.copy(leaving = null) }

    fun discardAndLeave() = mutable.update { state ->
        val step = state.edit ?: return@update state
        state.copy(edit = leave(step.discardTab().copy(leaving = null), step.leaving))
    }

    fun saveAndLeave() {
        val step = state.value.edit ?: return
        saveTab(then = step.leaving)
    }

    /** "Save changes": saves the open tab and closes the dialog. */
    fun saveEdit() = saveTab(then = EditLeave.Close)

    private fun leave(step: EditRestaurantStep, to: EditLeave?): EditRestaurantStep? = when (to) {
        EditLeave.Close -> null
        is EditLeave.Tab -> step.copy(tab = to.tab, error = null, showProblems = false)
        null -> step
    }

    private fun saveTab(then: EditLeave?) {
        if (!state.value.canManage) return
        val step = state.value.edit ?: return
        val restaurant = step.restaurant ?: return
        if (step.saving || step.loading) return
        if (!step.changed) {
            mutable.update { it.copy(edit = leave(step.copy(leaving = null), then)) }
            return
        }
        if (editProblems(step).isNotEmpty()) {
            mutable.update { it.copy(edit = step.copy(showProblems = true, leaving = null)) }
            return
        }
        mutable.update { it.copy(edit = step.copy(saving = true, error = null, leaving = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val saved: EditRestaurantStep = if (step.tab == EditTab.RESTAURANT) {
                    val updated = repository.updateRestaurant(restaurant.id, UpdateRestaurantRequestDto(
                        name = step.name.trim(), legalName = step.legalName.trim(), code = restaurant.code, slug = restaurant.slug,
                        description = restaurant.description, email = step.email.trim(), phone = step.phone.trim(), website = restaurant.website,
                        currency = restaurant.currency, timezone = restaurant.timezone, ownerUserId = restaurant.ownerUserId,
                        isActive = restaurant.isActive, status = restaurant.status, expectedVersion = restaurant.version
                    ))
                    var coverUrl = step.card.coverUrl
                    step.cover?.let { coverUrl = repository.uploadCover(restaurant.id, null, it.bytes, it.fileName, it.contentType).coverUrl }
                    if (step.cover == null && step.removeCover && step.card.coverUrl != null) coverUrl = repository.removeCover(restaurant.id, null).coverUrl
                    step.copy(restaurant = updated, card = step.card.copy(coverUrl = coverUrl, name = updated.name)).discardRestaurant()
                } else {
                    val person = step.ownerPerson
                    val draft = step.owner
                    val ownerId = if (person != null) {
                        repository.updateOwner(restaurant.id, UpdateOwnerRequestDto(draft.firstName.trim(), draft.lastName.trim(),
                            draft.email.trim(), draft.phone.trim().ifBlank { null }))
                        person.id
                    } else {
                        session.requireRestaurantAccess { it.manage }
                        repository.createOwner(restaurant.id, draft.request()).ownerUserId
                    }
                    val fresh = ownerId?.let { runCatching { people.person(it) }.getOrNull() }
                    step.copy(ownerPerson = fresh, owner = ownerDraftOf(fresh), restaurant = restaurant.copy(ownerUserId = ownerId))
                }
                val notice = if (step.tab == EditTab.RESTAURANT) "${saved.name.trim()} was saved"
                    else if (step.ownerPerson == null) "${step.owner.firstName.trim()} is the owner now. They got an email to set a password."
                    else "The owner's details were saved"
                mutable.update { it.copy(edit = leave(saved.copy(saving = false), then), notice = notice, justCreated = restaurant.id) }
                load(reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(edit = state.edit?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- Adding a branch ----

    fun addBranch(card: RestaurantCardDto) {
        if (!state.value.canManage) return
        mutable.update { it.copy(branch = BranchStep(card.id, card.name, BranchDraft(city = card.city.orEmpty(), country = card.country.orEmpty())),
            notice = null, error = null) }
    }

    fun editBranch(change: (BranchDraft) -> BranchDraft) = mutable.update { state ->
        val step = state.branch ?: return@update state
        state.copy(branch = step.copy(draft = change(step.draft)))
    }

    fun cancelBranch() {
        if (state.value.branch?.saving != true) mutable.update { it.copy(branch = null) }
    }

    fun saveBranch() {
        if (!state.value.canManage) return
        val step = state.value.branch ?: return
        if (step.saving) return
        if (branchProblems(step.draft).isNotEmpty()) {
            mutable.update { it.copy(branch = step.copy(draft = step.draft.copy(showProblems = true))) }
            return
        }
        mutable.update { it.copy(branch = step.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val branch = repository.createBranchWithAddress(step.restaurantId, step.draft)
                mutable.update { it.copy(branch = null, notice = "${branch.name} was added to ${step.restaurantName}") }
                load(reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(branch = state.branch?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- The owner (step 2, or added later) ----

    fun addOwner(card: RestaurantCardDto) {
        if (!state.value.canManage) return
        mutable.update { it.copy(owner = OwnerStep(card.id, card.name), notice = null, error = null) }
    }

    fun editOwner(change: (OwnerDraft) -> OwnerDraft) = mutable.update { state ->
        val step = state.owner ?: return@update state
        state.copy(owner = step.copy(draft = step.draft.edit(change)))
    }

    /** Closes the owner step; after creating a restaurant its owner can still be added later from its card. */
    fun skipOwner() {
        val step = state.value.owner ?: return
        if (step.saving) return
        mutable.update {
            it.copy(owner = null, notice = if (step.afterCreate) "${step.restaurantName} was created. Add its owner whenever you're ready." else null,
                // Back on the list, scroll to the new restaurant and mark it.
                justCreated = if (step.afterCreate) step.restaurantId else it.justCreated)
        }
    }

    fun saveOwner() {
        if (!state.value.canManage) return
        val step = state.value.owner ?: return
        if (step.saving) return
        if (ownerProblems(step.draft).isNotEmpty()) {
            mutable.update { it.copy(owner = step.copy(draft = step.draft.copy(showProblems = true))) }
            return
        }
        mutable.update { it.copy(owner = step.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                repository.createOwner(step.restaurantId, step.draft.request())
                mutable.update {
                    it.copy(owner = null, notice = "${step.draft.firstName.trim()} is the owner of ${step.restaurantName}. They got an email to set a password.",
                        // Back on the list, scroll to the restaurant and mark it.
                        justCreated = step.restaurantId)
                }
                load(reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(owner = state.owner?.copy(saving = false, error = platformMessage(e, write = true))) }
            }
        }
    }

    // ---- Entering a restaurant ----

    /**
     * Opens [card]: straight in when it has a single branch, otherwise [onChooseBranch] shows its branches to choose
     * from. Calls [onEntered] once inside.
     */
    fun enter(card: RestaurantCardDto, onEntered: () -> Unit, onChooseBranch: (RestaurantCardDto) -> Unit) {
        if (!state.value.canRead) return
        if (state.value.entering != null || state.value.reorder != null) return
        mutable.update { it.copy(entering = card.id, error = null) }
        entryJob = actions.launch {
            try {
                session.requireRestaurantAccess { it.read }
                val restaurant = repository.restaurant(card.id)
                // The first page (open branches first) and the totals are enough to decide; the rest isn't loaded here.
                val branches = repository.branchCards(card.id, "", 0, 2)
                val count = branches.allBranches.toInt()
                if (!active) return@launch
                // Closed restaurants remain manageable, but never acquire an operational branch context.
                if (!restaurant.open || branches.openBranches == 0L) {
                    session.enterRestaurant(ActingRestaurant(card.id, restaurant.name, null, null, count))
                } else if (count == 1) {
                    val branch = branches.items.first { it.open }
                    session.enterRestaurant(ActingRestaurant(card.id, restaurant.name, branch.id, branch.name, 1))
                } else {
                    onChooseBranch(card.copy(name = restaurant.name, branches = branches.allBranches))
                    return@launch
                }
                onEntered()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                mutable.update { it.copy(entering = null) }
            }
        }
    }

    // ---- Cover pictures ----

    fun uploadCover(card: RestaurantCardDto, bytes: ByteArray, fileName: String, contentType: String) =
        changeCover(card, "${card.name} has a new picture") { repository.uploadCover(card.id, null, bytes, fileName, contentType) }

    fun removeCover(card: RestaurantCardDto) = changeCover(card, "${card.name}'s picture was removed") { repository.removeCover(card.id, null) }

    fun coverProblem(message: String) = mutable.update { it.copy(error = message, notice = null) }

    private fun changeCover(card: RestaurantCardDto, notice: String, action: suspend () -> CoverDto) {
        if (!state.value.canManage) return
        if (card.id in state.value.coverBusy) return
        mutable.update { it.copy(coverBusy = it.coverBusy + card.id, error = null, notice = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val cover = action()
                mutable.update { state ->
                    state.copy(cards = state.cards.map { if (it.id == card.id) it.copy(coverUrl = cover.coverUrl) else it }, notice = notice)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(coverBusy = it.coverBusy - card.id) }
            }
        }
    }

    // ---- Change position ----

    fun startReorder() {
        val current = state.value
        if (!current.canManage || current.loading || current.loadingMore) return
        if (current.cards.size > 1000) {
            mutable.update { it.copy(error = "Changing the order supports up to 1,000 restaurants.") }
            return
        }
        if (current.cards.size < 2) {
            mutable.update { it.copy(error = "Add at least 2 restaurants before changing their order.") }
            return
        }
        if (current.hasNext || current.search.isNotBlank() || current.filter != RestaurantFilter.ALL) {
            mutable.update { it.copy(error = "Show all restaurants (no search or filter) before changing their order.") }
            return
        }
        mutable.update { it.copy(reorder = it.cards, notice = null, error = null) }
    }

    fun moveCards(order: List<RestaurantCardDto>) = mutable.update {
        val current = it.reorder
        if (current == null || it.savingOrder || order.size != current.size || order.map { c -> c.id }.toSet() != current.map { c -> c.id }.toSet()) it
        else it.copy(reorder = order)
    }

    fun cancelReorder() {
        if (!state.value.savingOrder) mutable.update { it.copy(reorder = null) }
    }

    fun saveOrder() {
        if (!state.value.canManage) return
        val order = state.value.reorder ?: return
        if (state.value.savingOrder) return
        if (order.map { it.id } == state.value.cards.map { it.id }) {
            mutable.update { it.copy(reorder = null) }
            return
        }
        mutable.update { it.copy(savingOrder = true, error = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                repository.setRestaurantOrder(order.map { it.id })
                mutable.update { it.copy(cards = order, reorder = null, notice = "The new order was saved") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(savingOrder = false) }
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
